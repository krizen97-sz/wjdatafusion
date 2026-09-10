#!/usr/bin/env python3
"""Local broker for original Kettle; every engine invocation requires OS isolation."""
import argparse
import base64
import hashlib
import json
import os
from pathlib import Path
import re
import secrets
import shutil
import subprocess
import stat
import sys
import threading
import time
import uuid
import zipfile
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlparse, parse_qs, unquote
import xml.etree.ElementTree as ET

SOURCE = Path(__file__).resolve().parents[2] / 'data-governance/kettle-worker/src'
JAVA_HOME = Path('/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home')
TERMINAL = {'SUCCEEDED', 'FAILED', 'STOPPED', 'PREVIEW_COMPLETE', 'TIMED_OUT'}
MAX_XML = 2 * 1024 * 1024
MAX_REQUEST = 24 * 1024 * 1024


def private_dir(path):
    path.mkdir(parents=True, exist_ok=True, mode=0o700)
    path.chmod(0o700)
    return path.resolve()


def prepare(archive, runtime, java_home=JAVA_HOME):
    runtime = private_dir(Path(runtime))
    lib = private_dir(runtime / 'lib')
    classes = private_dir(runtime / 'classes')
    provenance = []
    with zipfile.ZipFile(archive) as package:
        for item in package.infolist():
            marker = '/data-integration-hikvision/lib/'
            if marker not in item.filename:
                continue
            name = item.filename.split(marker, 1)[1]
            if '/' in name or not name.endswith('.jar'):
                continue
            data = package.read(item)
            (lib / name).write_bytes(data)
            (lib / name).chmod(0o600)
            provenance.append({'file': name, 'archiveEntry': item.filename, 'sha256': hashlib.sha256(data).hexdigest()})
    if not (lib / 'kettle-6.1.0.7.36.jar').is_file():
        raise ValueError('Expected original Hikvision custom Kettle library is missing')
    jars = sorted(lib.glob('*.jar'), key=lambda p: (p.name != 'kettle-6.1.0.7.36.jar', p.name))
    cp = os.pathsep.join(map(str, [classes] + jars))
    subprocess.run([str(java_home / 'bin/javac'), '-encoding', 'UTF-8', '-cp', cp, '-d', str(classes)] + [str(p) for p in SOURCE.glob('*.java')], check=True)
    manifest = {'javaHome': str(java_home), 'classpath': cp, 'archive': str(Path(archive).resolve()), 'libraries': provenance}
    (runtime / 'manifest.json').write_text(json.dumps(manifest, indent=2))
    (runtime / 'manifest.json').chmod(0o600)
    return {'runtime': str(runtime), 'libraries': len(provenance), 'compiled': True}


def sandbox_profile(runtime, operation, java_home):
    # OS-level controls also apply to JNI/native code. No external file roots or network grants.
    def literal(path):
        return json.dumps(str(Path(path).resolve()))
    reads = [runtime / 'lib', runtime / 'classes', operation, java_home, Path('/System'), Path('/usr/lib'), Path('/usr/share'), Path('/private/var/db/timezone')]
    return '\n'.join(['(version 1)', '(allow default)', '(deny network*)', '(deny process-fork)', '(deny mach-lookup (global-name "com.apple.mDNSResponder"))', '(deny file-read*)', '(deny file-write*)'] +
                     ['(allow file-read* (subpath ' + literal(path) + '))' for path in reads] +
                     ['(allow file-read-metadata)', '(allow file-read* (literal "/") (literal "/dev/random") (literal "/dev/urandom") (literal "/dev/null") (literal "/private/etc/localtime"))',
                      '(allow file-write* (subpath ' + literal(operation) + ') (literal "/dev/null"))'])


class Worker:
    def __init__(self, runtime, timeout=120):
        self.runtime = Path(runtime).resolve()
        self.manifest = json.loads((self.runtime / 'manifest.json').read_text())
        self.java_home = Path(self.manifest['javaHome'])
        if sys.platform != 'darwin' or not Path('/usr/bin/sandbox-exec').is_file():
            raise RuntimeError('No supported OS sandbox. Refusing to run original engine. Linux requires a separate container worker.')
        self.store = private_dir(self.runtime / 'transformations')
        self.operations = private_dir(self.runtime / 'operations')
        self.runs = {}
        self.lock = threading.RLock()
        self.launch_lock = threading.RLock()
        self.timeout = timeout
        self.catalog = None
        self.file_hashes = {}
        token_file = self.runtime / '.broker-token'
        if not token_file.exists():
            token_file.write_text(secrets.token_urlsafe(40))
            token_file.chmod(0o600)
        self.token = token_file.read_text().strip()

    @staticmethod
    def validate_xml(xml):
        if not isinstance(xml, str) or len(xml.encode()) > MAX_XML:
            raise ValueError('XML must be a string of at most 2 MiB')
        if re.search(r'<!\s*(DOCTYPE|ENTITY)', xml, re.I):
            raise ValueError('DTD and external entities are forbidden')
        tree = ET.fromstring(xml)
        if tree.tag != 'transformation':
            raise ValueError('Only transformation XML is accepted')
        names = [s.findtext('name') for s in tree.findall('step')]
        if not names or None in names or len(names) != len(set(names)):
            raise ValueError('Steps require unique, nonempty names')
        for hop in tree.findall('./order/hop'):
            if hop.findtext('from') not in names or hop.findtext('to') not in names:
                raise ValueError('Hop refers to an unknown step')
        return tree

    def launch(self, *args, **kwargs):
        with self.launch_lock:
            return self._launch(*args, **kwargs)

    def _launch(self, operation, xml=None, preview_step='', row_limit=20, run_id=None, input_files=None):
        run_id = run_id or uuid.uuid4().hex
        if not re.fullmatch(r'[A-Za-z0-9_-]{1,80}', run_id):
            raise ValueError('Invalid run id')
        input_files = input_files or []
        if len(input_files) > 20:
            raise ValueError('At most 20 input files are accepted')
        decoded_files = []
        for item in input_files:
            if not re.fullmatch(r'[A-Za-z0-9_.-]{1,100}', item['name']) or item['name'] in {'.', '..'}:
                raise ValueError('Input files require safe flat names')
            if ('content' in item) == ('contentBase64' in item):
                raise ValueError('Provide exactly one of content or contentBase64')
            data = item['content'].encode('utf-8') if 'content' in item and isinstance(item['content'], str) else base64.b64decode(item['contentBase64'], validate=True)
            if len(data) > MAX_XML:
                raise ValueError('Each input file is limited to 2 MiB')
            decoded_files.append((item['name'], data))
        if sum(len(data) for _, data in decoded_files) > 16 * 1024 * 1024:
            raise ValueError('Total input file bytes exceed 16 MiB')
        if len(set(name for name, _ in decoded_files)) != len(decoded_files):
            raise ValueError('Duplicate input filename')
        fingerprint = hashlib.sha256(json.dumps([operation, xml, preview_step, row_limit, input_files], sort_keys=True).encode()).hexdigest()
        with self.lock:
            if run_id in self.runs:
                if self.runs[run_id]['fingerprint'] != fingerprint:
                    raise ValueError('runId already exists with different immutable content')
                return run_id
        if (self.operations / run_id).exists():
            raise ValueError('runId already exists on disk; use a new id')
        if sum(r['process'].poll() is None for r in self.runs.values()) >= 4:
            raise ValueError('Worker is at its four-process concurrency limit')
        directory = private_dir(self.operations / run_id)
        for folder in ['home', 'tmp', 'output']:
            private_dir(directory / folder)
        for name, data in decoded_files:
            (directory / 'output' / name).write_bytes(data)
        if xml is not None:
            self.validate_xml(xml)
            (directory / 'transformation.ktr').write_text(xml)
        profile = directory / 'sandbox.sb'
        profile.write_text(sandbox_profile(self.runtime, directory, self.java_home))
        cmd = ['/usr/bin/sandbox-exec', '-f', str(profile), str(self.java_home / 'bin/java'), '-Xmx384m', '-XX:+PerfDisableSharedMem', '-Djava.awt.headless=true', '-Duser.timezone=UTC', '-DKETTLE_SYSTEM_HOSTNAME=isolated-kettle-worker', '-Duser.home=' + str(directory / 'home'), '-DKETTLE_HOME=' + str(directory / 'home'), '-DKETTLE_JNDI_ROOT=' + str(directory / 'home'), '-DKETTLE_PLUGIN_BASE_FOLDERS=' + str(directory / 'home/empty-plugins'), '-Djava.io.tmpdir=' + str(directory / 'tmp'), '-cp', self.manifest['classpath'], 'KettleWorker', str(directory), operation, preview_step, str(row_limit)]
        log = (directory / 'engine.log').open('w')
        process = subprocess.Popen(cmd, cwd=directory, env={'PATH': '/usr/bin:/bin', 'HOME': str(directory / 'home'), 'LANG': 'en_US.UTF-8', 'KETTLE_HOME': str(directory / 'home')}, stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=log, text=True, bufsize=1, start_new_session=True)
        run = {'id': run_id, 'state': 'STARTING', 'mode': 'preview' if preview_step else operation, 'fingerprint': fingerprint, 'events': [], 'directory': str(directory), 'process': process, 'createdAt': time.time(), 'nodes': [], 'inputNames': [name for name, _ in decoded_files]}
        with self.lock:
            self.runs[run_id] = run
        def collect():
            try:
                with (directory / 'events.ndjson').open('w') as evidence:
                    for line in process.stdout:
                        try:
                            event = json.loads(line)
                        except json.JSONDecodeError:
                            continue
                        with self.lock:
                            event['seq'] = len(run['events']) + 1
                            run['events'].append(event)
                            if 'state' in event:
                                run['state'] = event['state']
                            for key in ['nodes', 'errors', 'previewTruncated']:
                                if key in event:
                                    run[key] = event[key]
                        evidence.write(json.dumps(event, ensure_ascii=False) + '\n')
                        evidence.flush()
                code = process.wait()
                with self.lock:
                    run['exitCode'] = code
                    if run.get('timeoutRequested'):
                        run['state'] = 'TIMED_OUT'
                    elif run.get('forcedStop'):
                        run['state'] = 'STOPPED'
                    elif run['state'] not in TERMINAL:
                        run['state'] = 'SUCCEEDED' if code == 0 and any(e['type'] in {'validation', 'capabilities'} for e in run['events']) else 'FAILED'
                    if not any(e['type'] == 'terminal' and e.get('state') == run['state'] for e in run['events']) and operation in {'run', 'job'}:
                        event = {'seq': len(run['events']) + 1, 'time': int(time.time() * 1000), 'type': 'terminal', 'state': run['state'], 'errors': run.get('errors', 1 if run['state'] == 'FAILED' else 0), 'exitCode': code, 'forced': run.get('forcedStop', False)}
                        run['events'].append(event)
                        with (directory / 'events.ndjson').open('a') as evidence:
                            evidence.write(json.dumps(event) + '\n')
                    run['finishedAt'] = time.time()
            finally:
                process.stdout.close()
                process.stdin.close()
                log.close()
        threading.Thread(target=collect, daemon=True).start()
        def watchdog():
            try:
                process.wait(timeout=self.timeout)
            except subprocess.TimeoutExpired:
                run['timeoutRequested'] = True
                self.stop(run_id)
                try:
                    process.wait(timeout=3)
                except subprocess.TimeoutExpired:
                    process.kill()
        threading.Thread(target=watchdog, daemon=True).start()
        return run_id

    def wait(self, run_id):
        while 'finishedAt' not in self.runs[run_id]:
            time.sleep(0.03)
        return self.snapshot(run_id)

    def snapshot(self, run_id):
        with self.lock:
            run = self.runs[run_id]
            result = {k: v for k, v in run.items() if k not in {'process', 'events', 'directory', 'inputNames'}}
            result['eventCount'] = len(run['events'])
            result['files'] = []
            output = Path(run['directory']) / 'output'
            if not output.is_symlink():
                for path in output.iterdir():
                    try:
                        fd, info = self.open_file(run_id, path.name)
                    except (OSError, ValueError):
                        continue
                    with os.fdopen(fd, 'rb') as stream:
                        cache_key = (run_id, path.name, info.st_ino, info.st_size, info.st_mtime_ns)
                        if cache_key not in self.file_hashes:
                            digest = hashlib.sha256()
                            for block in iter(lambda: stream.read(65536), b''):
                                digest.update(block)
                            self.file_hashes[cache_key] = digest.hexdigest()
                    result['files'].append({'name': path.name, 'bytes': info.st_size, 'sha256': self.file_hashes[cache_key], 'role': 'input' if path.name in run['inputNames'] else 'output', 'partial': run['state'] != 'SUCCEEDED'})
            return result

    def open_file(self, run_id, name):
        if not name or name in {'.', '..'} or '/' in name or '\\' in name or '\x00' in name:
            raise ValueError('File name must be a basename')
        directory = Path(self.runs[run_id]['directory']) / 'output'
        dir_fd = os.open(directory, os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW)
        try:
            fd = os.open(name, os.O_RDONLY | os.O_NOFOLLOW, dir_fd=dir_fd)
        finally:
            os.close(dir_fd)
        info = os.fstat(fd)
        if not stat.S_ISREG(info.st_mode):
            os.close(fd)
            raise ValueError('Only regular files may be downloaded')
        return fd, info

    def stop(self, run_id):
        run = self.runs[run_id]
        if run['process'].poll() is None:
            try:
                run['process'].stdin.write('STOP\n')
                run['process'].stdin.flush()
                run['state'] = 'STOPPING'
            except BrokenPipeError:
                pass
            def force_after_grace():
                try:
                    run['process'].wait(timeout=5)
                except subprocess.TimeoutExpired:
                    run['forcedStop'] = True
                    run['process'].kill()
            threading.Thread(target=force_after_grace, daemon=True).start()
        return self.snapshot(run_id)

    def validate(self, xml):
        result = self.wait(self.launch('validate', xml))
        event = next((e for e in self.runs[result['id']]['events'] if e['type'] == 'validation'), None)
        return event or {'valid': False, 'state': result['state'], 'errors': [e for e in self.runs[result['id']]['events'] if e['type'] == 'terminal']}

    def capabilities(self):
        if self.catalog is None:
            result = self.wait(self.launch('capabilities'))
            self.catalog = next((e for e in self.runs[result['id']]['events'] if e['type'] == 'capabilities'), None)
            if self.catalog is None:
                raise RuntimeError('Original engine discovery failed; inspect private engine.log')
        return self.catalog

    def save(self, identifier, xml):
        if not re.fullmatch(r'[A-Za-z0-9_-]{1,80}', identifier):
            raise ValueError('Invalid transformation id')
        validation = self.validate(xml)
        if not validation.get('valid'):
            return validation
        digest = hashlib.sha256(xml.encode()).hexdigest()
        path = self.store / (identifier + '.ktr')
        temporary = path.with_suffix('.' + uuid.uuid4().hex + '.tmp')
        temporary.write_text(xml)
        temporary.chmod(0o600)
        temporary.replace(path)
        return {'id': identifier, 'sha256': digest, 'validation': validation}


def serve(worker, port):
    class Handler(BaseHTTPRequestHandler):
        def log_message(self, *_):
            pass

        def handle_request(self):
            try:
                if self.headers.get('Origin'):
                    raise ValueError('Direct browser origins are disabled; use the authenticated RYNEW backend proxy')
                if not secrets.compare_digest(self.headers.get('Authorization', ''), 'Bearer ' + worker.token):
                    self.send_response(401)
                    self.end_headers()
                    return
                url = urlparse(self.path)
                parts = [p for p in url.path.split('/') if p]
                length = int(self.headers.get('Content-Length', '0'))
                if length < 0 or length > MAX_REQUEST:
                    raise ValueError('Request too large')
                body = json.loads(self.rfile.read(length) or '{}') if length else {}
                if self.command == 'GET' and parts == ['health']:
                    result = {'status': 'UP', 'engine': 'original-kettle', 'sandbox': 'macos-seatbelt', 'protocolVersion': 1}
                elif self.command == 'GET' and parts == ['capabilities']:
                    result = worker.capabilities()
                elif self.command == 'POST' and parts == ['transformations', 'validate']:
                    result = worker.validate(body['xml'])
                elif self.command == 'PUT' and len(parts) == 2 and parts[0] == 'transformations':
                    result = worker.save(parts[1], body['xml'])
                elif self.command == 'POST' and parts == ['runs']:
                    identifier = body['transformationId']
                    if not re.fullmatch(r'[A-Za-z0-9_-]{1,80}', identifier):
                        raise ValueError('Invalid transformation id')
                    mode = body.get('mode', 'run')
                    if mode not in {'run', 'preview'}:
                        raise ValueError('mode must be run or preview')
                    target = body.get('previewStep', '') if mode == 'preview' else ''
                    if mode == 'preview' and not target:
                        raise ValueError('previewStep is required')
                    limit = int(body.get('rowLimit', 20))
                    if not 1 <= limit <= 200:
                        raise ValueError('rowLimit must be between 1 and 200')
                    xml = (worker.store / (identifier + '.ktr')).read_text()
                    result = worker.snapshot(worker.launch('run', xml, target, limit, body.get('runId'), body.get('inputFiles')))
                elif len(parts) >= 2 and parts[0] == 'runs':
                    identifier = parts[1]
                    if len(parts) == 2 and self.command == 'GET':
                        result = worker.snapshot(identifier)
                    elif parts[2:] == ['events'] and self.command == 'GET':
                        after = int(parse_qs(url.query).get('after', ['0'])[0])
                        if after < 0:
                            raise ValueError('after must be nonnegative')
                        with worker.lock:
                            events = worker.runs[identifier]['events'][after:after + 1000]
                        result = {'events': events, 'nextCursor': events[-1]['seq'] if events else after, 'state': worker.snapshot(identifier)['state']}
                    elif parts[2:] == ['stop'] and self.command == 'POST':
                        result = worker.stop(identifier)
                    elif len(parts) == 4 and parts[2] == 'files' and self.command == 'GET':
                        filename = unquote(parts[3])
                        fd, info = worker.open_file(identifier, filename)
                        with os.fdopen(fd, 'rb') as stream:
                            self.send_response(200)
                            self.send_header('Content-Type', 'application/octet-stream')
                            self.send_header('Content-Length', str(info.st_size))
                            self.send_header('Cache-Control', 'no-store')
                            self.send_header('X-Kettle-Partial', str(worker.runs[identifier]['state'] != 'SUCCEEDED').lower())
                            self.end_headers()
                            shutil.copyfileobj(stream, self.wfile, 65536)
                        return
                    else:
                        raise KeyError('Unknown endpoint')
                else:
                    raise KeyError('Unknown endpoint')
                status = 200
            except (ValueError, ET.ParseError) as error:
                status, result = 400, {'error': str(error)}
            except (KeyError, FileNotFoundError):
                status, result = 404, {'error': 'Resource not found'}
            except Exception as error:
                status, result = 500, {'error': type(error).__name__}
            data = json.dumps(result, ensure_ascii=False).encode()
            self.send_response(status)
            self.send_header('Content-Type', 'application/json; charset=utf-8')
            self.send_header('Content-Length', str(len(data)))
            self.send_header('Cache-Control', 'no-store')
            self.end_headers()
            self.wfile.write(data)
        do_GET = do_POST = do_PUT = handle_request
    server = ThreadingHTTPServer(('127.0.0.1', port), Handler)
    print(json.dumps({'url': 'http://127.0.0.1:' + str(server.server_port), 'engine': 'original-kettle', 'sandbox': 'macos-seatbelt'}), flush=True)
    server.serve_forever()


def main():
    os.umask(0o077)
    parser = argparse.ArgumentParser()
    parser.add_argument('command', choices=['prepare', 'serve', 'capabilities'])
    parser.add_argument('--runtime', required=True)
    parser.add_argument('--archive')
    parser.add_argument('--port', type=int, default=19162)
    parser.add_argument('--timeout', type=int, default=120, help='Per operation seconds; enforced by broker watchdog')
    args = parser.parse_args()
    if args.command == 'prepare':
        if not args.archive:
            parser.error('--archive is required')
        print(json.dumps(prepare(args.archive, args.runtime)))
    elif args.command == 'serve':
        if not 1 <= args.timeout <= 3600:
            parser.error('--timeout must be between 1 and 3600')
        serve(Worker(args.runtime, timeout=args.timeout), args.port)
    else:
        print(json.dumps(Worker(args.runtime).capabilities(), ensure_ascii=False))


if __name__ == '__main__':
    main()
