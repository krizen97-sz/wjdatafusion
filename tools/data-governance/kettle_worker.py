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
import unicodedata
import zipfile
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlparse, parse_qs, unquote
import xml.etree.ElementTree as ET

SOURCE = Path(__file__).resolve().parents[2] / 'data-governance/kettle-worker/src'
JAVA_HOME = Path('/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home')
TERMINAL = {'SUCCEEDED', 'FAILED', 'STOPPED', 'PREVIEW_COMPLETE', 'TIMED_OUT'}
RECOVERY_REQUIRED = 'RECOVERY_REQUIRED'
MAX_XML = 2 * 1024 * 1024
MAX_INPUT_FILE = 8 * 1024 * 1024
MAX_REQUEST = 32 * 1024 * 1024


def private_dir(path):
    path.mkdir(parents=True, exist_ok=True, mode=0o700)
    path.chmod(0o700)
    return path.resolve()


def validate_filename(name):
    if not isinstance(name, str) or not name.strip() or name in {'.', '..'} or len(name.encode('utf-8')) > 240 or any(ord(char) < 32 or 127 <= ord(char) <= 159 or char in '/\\' for char in name) or Path(name).is_absolute():
        raise ValueError('File name must be a safe UTF-8 basename of at most 240 bytes')
    return name


def read_private_json(path):
    fd = os.open(path, os.O_RDONLY | os.O_NOFOLLOW)
    with os.fdopen(fd, 'r') as stream:
        info = os.fstat(stream.fileno())
        if info.st_uid != os.getuid() or stat.S_IMODE(info.st_mode) != 0o600:
            raise ValueError('Policy must be owned by the broker user with mode 0600')
        return json.load(stream)


def atomic_private_json(path, value):
    path = Path(path)
    temporary = path.with_name(path.name + '.' + uuid.uuid4().hex + '.tmp')
    fd = os.open(temporary, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(fd, 'w') as stream:
        json.dump(value, stream, ensure_ascii=False)
        stream.flush()
        os.fsync(stream.fileno())
    temporary.replace(path)
    directory = os.open(path.parent, os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW)
    try:
        os.fsync(directory)
    finally:
        os.close(directory)


def file_digest(path):
    digest = hashlib.sha256()
    with Path(path).open('rb') as stream:
        for block in iter(lambda: stream.read(65536), b''):
            digest.update(block)
    return digest.hexdigest()


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
    jars = sorted((lib / item['file'] for item in provenance), key=lambda p: (p.name != 'kettle-6.1.0.7.36.jar', p.name))
    cp = os.pathsep.join(map(str, [classes] + jars))
    subprocess.run([str(java_home / 'bin/javac'), '-encoding', 'UTF-8', '-cp', cp, '-d', str(classes)] + [str(p) for p in SOURCE.glob('*.java')], check=True)
    manifest = {'javaHome': str(java_home), 'classpath': cp, 'archive': str(Path(archive).resolve()), 'libraries': provenance, 'workerSources': [{'file': source.name, 'sha256': hashlib.sha256(source.read_bytes()).hexdigest()} for source in SOURCE.glob('*.java')]}
    (runtime / 'manifest.json').write_text(json.dumps(manifest, indent=2))
    (runtime / 'manifest.json').chmod(0o600)
    return {'runtime': str(runtime), 'libraries': len(provenance), 'compiled': True}


def install_postgres_jdbc(runtime, source):
    """Explicit compatibility extension; never change original Kettle Step jars."""
    runtime, source = Path(runtime).resolve(), Path(source).resolve()
    if not re.fullmatch(r'postgresql-[0-9.]+\.jar', source.name):
        raise ValueError('Only a specifically named PostgreSQL JDBC jar is accepted')
    data = source.read_bytes()
    checksum = source.with_suffix('.jar.sha1').read_text().strip().split()[0]
    if hashlib.sha1(data).hexdigest() != checksum:
        raise ValueError('Local Maven JDBC checksum mismatch')
    with zipfile.ZipFile(source) as jar:
        if any(name.startswith('org/pentaho/') for name in jar.namelist()):
            raise ValueError('JDBC extension must not replace original Kettle classes')
    destination = runtime / 'lib' / ('approved-' + source.name)
    destination.write_bytes(data)
    destination.chmod(0o600)
    manifest_path = runtime / 'manifest.json'
    manifest = json.loads(manifest_path.read_text())
    classpath = [item for item in manifest['classpath'].split(os.pathsep) if not Path(item).name.startswith('approved-postgresql-')]
    classpath.insert(1, str(destination))
    manifest['classpath'] = os.pathsep.join(classpath)
    record = {'kind': 'postgresql-jdbc', 'file': destination.name, 'source': str(source), 'sha256': hashlib.sha256(data).hexdigest(), 'localMavenSha1Verified': True, 'priority': 'before-original-archive-jdbc-after-worker-classes'}
    manifest['runtimeExtensions'] = [record]
    manifest_path.write_text(json.dumps(manifest, indent=2))
    return record


def sandbox_profile(runtime, operation, java_home, ftp_test_ports=()):
    # OS-level controls also apply to JNI/native code. No external file roots or network grants.
    def literal(path):
        return json.dumps(str(Path(path).resolve()))
    reads = [runtime / 'lib', runtime / 'classes', operation, java_home, Path('/System'), Path('/usr/lib'), Path('/usr/share'), Path('/private/var/db/timezone')]
    return '\n'.join(['(version 1)', '(allow default)', '(deny network*)', '(deny process-fork)', '(deny file-link)', '(deny mach-lookup (global-name "com.apple.mDNSResponder"))', '(deny file-read*)', '(deny file-write*)'] +
                     ['(allow file-read* (subpath ' + literal(path) + '))' for path in reads] +
                     ['(allow file-read-metadata)', '(allow file-read* (literal "/") (literal "/dev/random") (literal "/dev/urandom") (literal "/dev/null") (literal "/private/etc/localtime"))',
                      '(allow file-write* (subpath ' + literal(operation) + ') (literal "/dev/null"))'] +
                     ['(allow network-outbound (remote tcp4 "localhost:' + str(port) + '"))' for port in ftp_test_ports])


class Worker:
    def __init__(self, runtime, timeout=120, ftp_test_policy=None, network_policy=None, allow_endpoints=()):
        self.runtime = Path(runtime).resolve()
        self.manifest = json.loads((self.runtime / 'manifest.json').read_text())
        self.java_home = Path(self.manifest['javaHome'])
        if sys.platform != 'darwin' or not Path('/usr/bin/sandbox-exec').is_file():
            raise RuntimeError('No supported OS sandbox. Refusing to run original engine. Linux requires a separate container worker.')
        self.store = private_dir(self.runtime / 'transformations')
        self.operations = private_dir(self.runtime / 'operations')
        self.records = private_dir(self.runtime / 'run-records')
        self.runs = {}
        self.lock = threading.RLock()
        self.launch_lock = threading.RLock()
        self.timeout = timeout
        self.ftp_test_policy = Path(ftp_test_policy) if ftp_test_policy else None
        self.ftp_policy_snapshot = read_private_json(self.ftp_test_policy) if self.ftp_test_policy else None
        endpoints = list(allow_endpoints)
        if network_policy:
            policy = read_private_json(network_policy)
            if 'expiresAt' in policy and time.time() >= policy['expiresAt']:
                raise ValueError('Network policy has expired')
            endpoints.extend(policy.get('endpoints', []))
        self.endpoints = []
        for endpoint in endpoints:
            if isinstance(endpoint, str):
                host, port_text = endpoint.rsplit(':', 1)
                port = int(port_text)
            elif isinstance(endpoint, dict):
                host, port = endpoint.get('host'), endpoint.get('port')
            else:
                raise ValueError('Invalid network endpoint')
            if host != '127.0.0.1' or type(port) is not int or not 1024 <= port <= 65535:
                raise ValueError('macOS worker supports only exact 127.0.0.1 TCP ports 1024-65535; other endpoints are refused')
            if {'host': host, 'port': port} not in self.endpoints:
                self.endpoints.append({'host': host, 'port': port})
        if len(self.endpoints) > 32:
            raise ValueError('At most 32 explicit endpoints may be registered')
        self.catalog = None
        self.file_hashes = {}
        token_file = self.runtime / '.broker-token'
        if not token_file.exists():
            token_file.write_text(secrets.token_urlsafe(40))
            token_file.chmod(0o600)
        fd = os.open(token_file, os.O_RDONLY | os.O_NOFOLLOW)
        with os.fdopen(fd, 'r') as stream:
            info = os.fstat(stream.fileno())
            if info.st_uid != os.getuid() or stat.S_IMODE(info.st_mode) != 0o600:
                raise ValueError('Bearer token must be owned by the broker user with mode 0600')
            self.token = stream.read().strip()
        self._recover()

    def _persist(self, run, finalized=None):
        if finalized is not None:
            run['finalized'] = finalized
        record = {key: value for key, value in run.items() if key not in {'process', 'events', 'directory'}}
        record['schemaVersion'] = 1
        record['eventCount'] = len(run['events'])
        event_path = self.records / run['id'] / 'events.ndjson'
        if run.get('finalized') and event_path.is_file():
            record['eventsSha256'] = file_digest(event_path)
        atomic_private_json(self.records / run['id'] / 'record.json', record)

    def _append_event(self, run, event):
        with self.lock:
            event = dict(event)
            event['seq'] = len(run['events']) + 1
            event.setdefault('time', int(time.time() * 1000))
            run['events'].append(event)
            encoded = (json.dumps(event, ensure_ascii=False) + '\n').encode()
            path = self.records / run['id'] / 'events.ndjson'
            fd = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_APPEND | os.O_NOFOLLOW, 0o600)
            with os.fdopen(fd, 'ab') as evidence:
                evidence.write(encoded)
                evidence.flush()
                if event['type'] in {'state', 'terminal', 'recovery'}:
                    os.fsync(evidence.fileno())
            # Compatibility evidence copy is not trusted for new-protocol recovery.
            try:
                dir_fd = os.open(run['directory'], os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW)
                try:
                    mirror = os.open('events.ndjson', os.O_WRONLY | os.O_CREAT | os.O_APPEND | os.O_NOFOLLOW, 0o600, dir_fd=dir_fd)
                    with os.fdopen(mirror, 'ab') as stream:
                        stream.write(encoded)
                finally:
                    os.close(dir_fd)
            except OSError:
                pass
            return event

    def _recover(self):
        """No process is started or signalled here. Unknown effects remain explicitly unknown."""
        candidates = {path.name for parent in [self.records, self.operations] for path in parent.iterdir() if path.is_dir() and not path.is_symlink() and re.fullmatch(r'[A-Za-z0-9_-]{1,80}', path.name)}
        for identifier in sorted(candidates):
            record_dir, operation = self.records / identifier, self.operations / identifier
            if operation.is_symlink() or record_dir.is_symlink():
                continue
            trusted = record_dir.exists()
            metadata, intent, events = {}, {}, []
            damaged = False
            try:
                if trusted:
                    intent = read_private_json(record_dir / 'intent.json')
                    metadata = read_private_json(record_dir / 'record.json')
                    if intent.get('id') != identifier or metadata.get('id') != identifier or metadata.get('schemaVersion') != 1:
                        raise ValueError('Record identity mismatch')
                event_path = (record_dir if trusted else operation) / 'events.ndjson'
                if event_path.is_file() and not event_path.is_symlink():
                    with event_path.open() as stream:
                        for line in stream:
                            event = json.loads(line)
                            if event.get('seq') != len(events) + 1:
                                raise ValueError('Non-contiguous event stream')
                            events.append(event)
                if trusted and metadata.get('finalized') and (metadata.get('eventCount') != len(events) or metadata.get('eventsSha256') != file_digest(event_path)):
                    raise ValueError('Completed event evidence does not match durable record')
            except (OSError, ValueError, TypeError, AttributeError):
                damaged = True
                if not isinstance(intent, dict):
                    intent = {}
                if not isinstance(metadata, dict):
                    metadata = {}
            terminal = next((event for event in reversed(events) if event.get('type') == 'terminal'), None)
            safe_terminal = terminal and terminal.get('state') in TERMINAL
            completed = not damaged and ((trusted and metadata.get('finalized') and metadata.get('state') in TERMINAL) or (not trusted and safe_terminal))
            state = metadata.get('state') if trusted and completed else terminal['state'] if completed else RECOVERY_REQUIRED
            run = dict(metadata)
            run.update({'id': identifier, 'directory': str(operation), 'process': None, 'events': events, 'state': state, 'restored': True, 'replayAllowed': False, 'fingerprint': intent.get('fingerprint') if trusted and not damaged else None, 'launchNonce': intent.get('launchNonce') if trusted and not damaged else None, 'createdAt': intent.get('createdAt', operation.stat().st_mtime if operation.exists() else time.time()), 'mode': intent.get('mode', 'job' if (operation / 'transformation.kjb').exists() else 'run'), 'inputNames': intent.get('inputNames', []), 'nodes': metadata.get('nodes', []), 'finalized': bool(completed)})
            latest_counters = next((event['nodes'] for event in reversed(events) if event.get('type') in {'metrics', 'terminal', 'state'} and 'nodes' in event), None)
            if latest_counters is not None:
                run['nodes'] = latest_counters
            if completed:
                run.setdefault('finishedAt', metadata.get('finishedAt', (terminal or {}).get('time', time.time() * 1000) / 1000))
                if terminal:
                    for key in ['nodes', 'errors', 'previewTruncated']:
                        if key in terminal:
                            run[key] = terminal[key]
                if not trusted:
                    run['completionEvidence'] = 'legacy-native-terminal; immutable fingerprint unavailable'
            else:
                run['lastKnownState'] = metadata.get('state', 'UNKNOWN')
                run['recoveryReason'] = 'Durable record is incomplete or inconsistent; execution effects are unknown and automatic replay is forbidden'
                run['recoveredAt'] = time.time()
            self.runs[identifier] = run
            if not trusted:
                record_dir.mkdir(mode=0o700)
                atomic_private_json(record_dir / 'intent.json', {'schemaVersion': 1, 'id': identifier, 'legacy': True, 'fingerprint': None, 'createdAt': run['createdAt'], 'mode': run['mode'], 'inputNames': []})
                if events:
                    fd = os.open(record_dir / 'events.ndjson', os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
                    with os.fdopen(fd, 'w') as stream:
                        for event in events:
                            stream.write(json.dumps(event, ensure_ascii=False) + '\n')
                        stream.flush()
                        os.fsync(stream.fileno())
            # Trusted records are read-only during startup recovery. In particular,
            # another still-live supervisor may be finishing its owned child.
            if not trusted:
                self._persist(run)

    @staticmethod
    def validate_xml(xml, kind='transformation'):
        if not isinstance(xml, str) or len(xml.encode()) > MAX_XML:
            raise ValueError('XML must be a string of at most 2 MiB')
        if re.search(r'<!\s*(DOCTYPE|ENTITY)', xml, re.I):
            raise ValueError('DTD and external entities are forbidden')
        tree = ET.fromstring(xml)
        if tree.tag != kind:
            raise ValueError('Expected ' + kind + ' XML')
        if kind == 'job':
            return tree
        names = [s.findtext('name') for s in tree.findall('step')]
        if not names or None in names or len(names) != len(set(names)):
            raise ValueError('Steps require unique, nonempty names')
        for hop in tree.findall('./order/hop'):
            if hop.findtext('from') not in names or hop.findtext('to') not in names:
                raise ValueError('Hop refers to an unknown step')
        return tree

    def launch(self, *args, **kwargs):
        with self.launch_lock:
            before = set(self.runs)
            try:
                return self._launch(*args, **kwargs)
            except Exception:
                for identifier in set(self.runs) - before:
                    run = self.runs[identifier]
                    if run.get('process') is None and not run.get('finalized'):
                        run.update(state=RECOVERY_REQUIRED, recoveryReason='Launch preparation stopped after durable identity reservation; automatic replay is forbidden')
                        self._persist(run, finalized=False)
                raise

    def _launch(self, operation, xml=None, preview_step='', row_limit=20, run_id=None, input_files=None):
        run_id = run_id or uuid.uuid4().hex
        if not re.fullmatch(r'[A-Za-z0-9_-]{1,80}', run_id):
            raise ValueError('Invalid run id')
        input_files = input_files or []
        if len(input_files) > 20:
            raise ValueError('At most 20 input files are accepted')
        decoded_files = []
        for item in input_files:
            validate_filename(item['name'])
            if ('content' in item) == ('contentBase64' in item):
                raise ValueError('Provide exactly one of content or contentBase64')
            if not isinstance(item.get('content', item.get('contentBase64')), str):
                raise ValueError('Input file content must be a string')
            data = item['content'].encode('utf-8') if 'content' in item else base64.b64decode(item['contentBase64'], validate=True)
            if len(data) > MAX_INPUT_FILE:
                raise ValueError('Each input file is limited to 8 MiB')
            decoded_files.append((item['name'], data))
        if sum(len(data) for _, data in decoded_files) > 16 * 1024 * 1024:
            raise ValueError('Total input file bytes exceed 16 MiB')
        if len({unicodedata.normalize('NFC', name).casefold() for name, _ in decoded_files}) != len(decoded_files):
            raise ValueError('Input filenames collide after Unicode normalization and case folding')
        fingerprint = hashlib.sha256(json.dumps([operation, xml, preview_step, row_limit, input_files], sort_keys=True).encode()).hexdigest()
        with self.lock:
            if run_id in self.runs:
                existing = self.runs[run_id]
                if existing.get('state') == RECOVERY_REQUIRED or not existing.get('fingerprint'):
                    raise ValueError('Existing run has uncertain effects or no durable fingerprint; replay is forbidden')
                if existing['fingerprint'] != fingerprint:
                    raise ValueError('runId already exists with different immutable content')
                return run_id
        if (self.operations / run_id).exists() or (self.records / run_id).exists():
            raise ValueError('runId already exists on disk; use a new id')
        if sum(r.get('process') is not None and r['process'].poll() is None for r in self.runs.values()) >= 4:
            raise ValueError('Worker is at its four-process concurrency limit')
        if xml is not None:
            self.validate_xml(xml, 'job' if operation in {'job', 'job-validate'} else 'transformation')
        # Atomic reservations prevent two brokers from ever starting the same run ID.
        directory = self.operations / run_id
        directory.mkdir(mode=0o700)
        record_dir = self.records / run_id
        record_dir.mkdir(mode=0o700)
        created_at = time.time()
        nonce = secrets.token_hex(32)
        intent = {'schemaVersion': 1, 'id': run_id, 'fingerprint': fingerprint, 'operation': operation, 'mode': 'preview' if preview_step else operation, 'createdAt': created_at, 'inputNames': [name for name, _ in decoded_files], 'launchNonce': nonce}
        # This record is outside the engine's OS file grants and is durable before Popen.
        atomic_private_json(record_dir / 'intent.json', intent)
        run = dict(intent, state='INTENT_PERSISTED', events=[], directory=str(directory), process=None, nodes=[], finalized=False, restored=False)
        with self.lock:
            self.runs[run_id] = run
        self._persist(run)
        for folder in ['home', 'tmp', 'output']:
            private_dir(directory / folder)
        for name, data in decoded_files:
            (directory / 'output' / name).write_bytes(data)
        if xml is not None:
            (directory / ('transformation.kjb' if operation in {'job', 'job-validate'} else 'transformation.ktr')).write_text(xml)
        profile = directory / 'sandbox.sb'
        ftp_ports = [endpoint['port'] for endpoint in self.endpoints] if operation in {'run', 'job'} else []
        if operation == 'job' and self.ftp_test_policy:
            policy = self.ftp_policy_snapshot
            if policy.get('purpose') != 'synthetic-local-ftp' or not time.time() < policy.get('expiresAt', 0) <= time.time() + 3600:
                raise ValueError('FTP fixture policy is missing, expired or not explicitly synthetic')
            fixture_ports = policy.get('ports', [])
            if not isinstance(fixture_ports, list) or not 1 <= len(fixture_ports) <= 10 or any(type(port) is not int or not 1024 <= port <= 65535 for port in fixture_ports):
                raise ValueError('FTP fixture policy requires 1-10 explicit localhost ports')
            ftp_ports.extend(fixture_ports)
        profile.write_text(sandbox_profile(self.runtime, directory, self.java_home, ftp_ports))
        cmd = ['/usr/bin/sandbox-exec', '-f', str(profile), str(self.java_home / 'bin/java'), '-Xmx384m', '-XX:+PerfDisableSharedMem', '-Dgovernance.worker.launch.id=' + nonce, '-Djava.awt.headless=true', '-Djava.net.preferIPv4Stack=true', '-Duser.timezone=UTC', '-DKETTLE_SYSTEM_HOSTNAME=isolated-kettle-worker', '-Duser.home=' + str(directory / 'home'), '-DKETTLE_HOME=' + str(directory / 'home'), '-DKETTLE_JNDI_ROOT=' + str(directory / 'home'), '-DKETTLE_PLUGIN_BASE_FOLDERS=' + str(directory / 'home/empty-plugins'), '-Djava.io.tmpdir=' + str(directory / 'tmp'), '-cp', self.manifest['classpath'], 'KettleWorker', str(directory), operation, preview_step, str(row_limit)]
        run['commandSha256'] = hashlib.sha256(json.dumps(cmd).encode()).hexdigest()
        run['state'] = 'STARTING'
        self._persist(run)
        log = (directory / 'engine.log').open('w')
        try:
            process = subprocess.Popen(cmd, cwd=directory, env={'PATH': '/usr/bin:/bin', 'HOME': str(directory / 'home'), 'LANG': 'en_US.UTF-8', 'KETTLE_HOME': str(directory / 'home')}, stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=log, text=True, bufsize=1, start_new_session=True)
        except Exception as error:
            log.close()
            run.update(state='FAILED', errors=1, finishedAt=time.time(), launchFailed=True)
            self._append_event(run, {'type': 'terminal', 'state': 'FAILED', 'errors': 1, 'errorClass': type(error).__name__, 'message': 'Owned worker process could not be started'})
            self._persist(run, finalized=True)
            return run_id
        run.update(process=process, spawnedPid=process.pid, spawnedAt=time.time())
        self._persist(run)
        def collect():
            try:
                for line in process.stdout:
                    try:
                        event = json.loads(line)
                    except json.JSONDecodeError:
                        continue
                    with self.lock:
                        self._append_event(run, event)
                        # Child Job transformation states are not the containing Job's terminal state.
                        if event['type'] == 'state' and 'state' in event:
                            run['state'] = event['state']
                        elif event['type'] == 'terminal':
                            run['engineTerminal'] = event
                            run['finalizing'] = True
                        if event['type'] in {'metrics', 'state', 'terminal', 'validation'}:
                            for key in ['nodes', 'errors', 'previewTruncated']:
                                if key in event:
                                    run[key] = event[key]
                        if event['type'] in {'state', 'terminal'}:
                            self._persist(run)
                code = process.wait()
                with self.lock:
                    run['exitCode'] = code
                    if run.get('timeoutRequested'):
                        run['state'] = 'TIMED_OUT'
                    elif run.get('forcedStop'):
                        run['state'] = 'STOPPED'
                    elif code == 0 and run.get('engineTerminal', {}).get('state') in TERMINAL:
                        run['state'] = run['engineTerminal']['state']
                    else:
                        run['state'] = 'SUCCEEDED' if code == 0 and any(e['type'] in {'validation', 'capabilities', 'sandbox-proof'} for e in run['events']) else 'FAILED'
                    if not any(e['type'] == 'terminal' and e.get('state') == run['state'] for e in run['events']) and operation in {'run', 'job'}:
                        self._append_event(run, {'type': 'terminal', 'state': run['state'], 'errors': run.get('errors', 1 if run['state'] == 'FAILED' else 0), 'exitCode': code, 'forced': run.get('forcedStop', False)})
                    run['finishedAt'] = time.time()
                    run['finalizing'] = False
                    self._persist(run, finalized=True)
            except Exception as error:
                with self.lock:
                    run.update(state=RECOVERY_REQUIRED, recoveryReason='Worker supervision could not durably finalize the operation', supervisionError=type(error).__name__)
                    self._persist(run, finalized=False)
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
                    self._request_control(run, 'HALT')
        threading.Thread(target=watchdog, daemon=True).start()
        return run_id

    def wait(self, run_id):
        while 'finishedAt' not in self.runs[run_id] and self.runs[run_id]['state'] != RECOVERY_REQUIRED:
            time.sleep(0.03)
        return self.snapshot(run_id)

    def snapshot(self, run_id):
        with self.lock:
            run = self.runs[run_id]
            result = {k: v for k, v in run.items() if k not in {'process', 'events', 'directory', 'inputNames', 'launchNonce', 'engineTerminal', 'spawnedPid', 'commandSha256'}}
            result['eventCount'] = len(run['events'])
            result['files'] = []
            output = Path(run['directory']) / 'output'
            if output.is_dir() and not output.is_symlink():
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
        validate_filename(name)
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
        process = run.get('process')
        if run.get('finalized') and run['state'] in TERMINAL:
            return self.snapshot(run_id)
        if process is None:
            if not run.get('launchNonce'):
                raise ValueError('Recovered run has no verified control identity; automatic PID signalling is forbidden')
            self._request_control(run, 'STOP')
            run.update(stopRequestedAt=time.time(), stopDisposition='COOPERATIVE_REQUEST_WRITTEN', state=RECOVERY_REQUIRED)
            atomic_private_json(self.records / run['id'] / 'recovery-control.json', {'id': run['id'], 'command': 'STOP', 'requestedAt': run['stopRequestedAt'], 'disposition': run['stopDisposition']})
            return self.snapshot(run_id)
        if process.poll() is None:
            self._request_control(run, 'STOP')
            run['state'] = 'STOPPING'
            run['stopRequestedAt'] = time.time()
            self._persist(run)
            def force_after_grace():
                try:
                    process.wait(timeout=5)
                except subprocess.TimeoutExpired:
                    run['forcedStop'] = True
                    self._request_control(run, 'HALT')
                    try:
                        process.wait(timeout=3)
                    except subprocess.TimeoutExpired:
                        run.update(state=RECOVERY_REQUIRED, recoveryReason='Owned control channel did not confirm termination; no PID-based kill attempted')
                        self._persist(run, finalized=False)
            threading.Thread(target=force_after_grace, daemon=True).start()
        return self.snapshot(run_id)

    def _request_control(self, run, command):
        if command not in {'STOP', 'HALT'} or not re.fullmatch(r'[a-f0-9]{64}', run.get('launchNonce', '')):
            raise ValueError('Unverified worker control request')
        # The original Popen pipe is tied to this child across sandbox-exec -> JVM exec.
        # A recovered run never sends a signal to a saved PID; its nonce-bound stop file
        # can only be acted on by the JVM launched for this operation directory.
        process = run.get('process')
        if process is not None and process.poll() is None:
            try:
                process.stdin.write(command + '\n')
                process.stdin.flush()
            except (BrokenPipeError, ValueError):
                pass
        directory = os.open(run['directory'], os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW)
        temporary = 'control-' + uuid.uuid4().hex + '.tmp'
        try:
            fd = os.open(temporary, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600, dir_fd=directory)
            with os.fdopen(fd, 'w') as stream:
                stream.write(command + ':' + run['launchNonce'])
                stream.flush()
                os.fsync(stream.fileno())
            os.replace(temporary, 'control.stop', src_dir_fd=directory, dst_dir_fd=directory)
            os.fsync(directory)
        finally:
            os.close(directory)

    def validate(self, xml):
        result = self.wait(self.launch('validate', xml))
        event = next((e for e in self.runs[result['id']]['events'] if e['type'] == 'validation'), None)
        return event or {'valid': False, 'state': result['state'], 'errors': [e for e in self.runs[result['id']]['events'] if e['type'] == 'terminal']}

    def validate_job(self, xml, input_files=None):
        result = self.wait(self.launch('job-validate', xml, input_files=input_files))
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

    def save_job(self, identifier, xml, input_files=None):
        if not re.fullmatch(r'[A-Za-z0-9_-]{1,80}', identifier):
            raise ValueError('Invalid job id')
        validation = self.validate_job(xml, input_files)
        if not validation.get('valid'):
            return validation
        document = {'xml': xml, 'inputFiles': input_files or []}
        path = self.store / (identifier + '.job.json')
        temporary = path.with_suffix('.' + uuid.uuid4().hex + '.tmp')
        temporary.write_text(json.dumps(document))
        temporary.chmod(0o600)
        temporary.replace(path)
        return {'id': identifier, 'sha256': hashlib.sha256(xml.encode()).hexdigest(), 'validation': validation}


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
                    result = {'status': 'UP', 'engine': 'original-kettle', 'sandbox': 'macos-seatbelt', 'protocolVersion': 1, 'runtimePolicy': {'mode': 'registered-endpoints' if worker.endpoints else 'deny-all', 'endpoints': worker.endpoints, 'validationNetwork': 'deny-all', 'ftpFixturePolicy': worker.ftp_test_policy is not None}}
                elif self.command == 'GET' and parts == ['capabilities']:
                    result = worker.capabilities()
                elif self.command == 'POST' and parts == ['transformations', 'validate']:
                    result = worker.validate(body['xml'])
                elif self.command == 'PUT' and len(parts) == 2 and parts[0] == 'transformations':
                    result = worker.save(parts[1], body['xml'])
                elif self.command == 'POST' and parts == ['jobs', 'validate']:
                    result = worker.validate_job(body['xml'], body.get('inputFiles'))
                elif self.command == 'PUT' and len(parts) == 2 and parts[0] == 'jobs':
                    result = worker.save_job(parts[1], body['xml'], body.get('inputFiles'))
                elif self.command == 'POST' and parts == ['runs']:
                    if ('jobId' in body) == ('transformationId' in body):
                        raise ValueError('Provide exactly one jobId or transformationId')
                    is_job = 'jobId' in body
                    identifier = body['jobId'] if is_job else body['transformationId']
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
                    if is_job:
                        if mode != 'run':
                            raise ValueError('Job preview is not supported; preview its original child transformation')
                        document = json.loads((worker.store / (identifier + '.job.json')).read_text())
                        result = worker.snapshot(worker.launch('job', document['xml'], '', limit, body.get('runId'), body.get('inputFiles', document['inputFiles'])))
                    else:
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
    parser.add_argument('command', choices=['prepare', 'serve', 'capabilities', 'install-postgres-jdbc'])
    parser.add_argument('--runtime', required=True)
    parser.add_argument('--archive')
    parser.add_argument('--jdbc-jar')
    parser.add_argument('--port', type=int, default=19162)
    parser.add_argument('--timeout', type=int, default=120, help='Per operation seconds; enforced by broker watchdog')
    parser.add_argument('--ftp-test-policy', help='Explicit, expiring synthetic localhost FTP fixture policy file')
    parser.add_argument('--network-policy', help='Trusted broker-owned 0600 JSON file containing registered exact endpoints')
    parser.add_argument('--allow-endpoint', action='append', default=[], help='Explicit startup grant, e.g. 127.0.0.1:15432 (repeatable)')
    args = parser.parse_args()
    if args.command == 'prepare':
        if not args.archive:
            parser.error('--archive is required')
        print(json.dumps(prepare(args.archive, args.runtime)))
    elif args.command == 'install-postgres-jdbc':
        if not args.jdbc_jar:
            parser.error('--jdbc-jar is required')
        print(json.dumps(install_postgres_jdbc(args.runtime, args.jdbc_jar)))
    elif args.command == 'serve':
        if not 1 <= args.timeout <= 3600:
            parser.error('--timeout must be between 1 and 3600')
        serve(Worker(args.runtime, timeout=args.timeout, ftp_test_policy=args.ftp_test_policy, network_policy=args.network_policy, allow_endpoints=args.allow_endpoint), args.port)
    else:
        print(json.dumps(Worker(args.runtime).capabilities(), ensure_ascii=False))


if __name__ == '__main__':
    main()
