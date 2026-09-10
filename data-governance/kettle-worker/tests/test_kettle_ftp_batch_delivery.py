"""Opt-in real original FTP_PUT batches against a new private loopback FTP server."""
import argparse
import ftplib
import hashlib
import importlib.util
import io
import json
import logging
import os
from pathlib import Path
import secrets
import socket
import subprocess
import sys
import time
import uuid

REPO = Path(__file__).resolve().parents[3]


def private_json(path, value):
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2))
    path.chmod(0o600)


def serve(path):
    from pyftpdlib.authorizers import DummyAuthorizer
    from pyftpdlib.handlers import FTPHandler
    from pyftpdlib.servers import FTPServer
    config = json.loads(path.read_text())
    authorizer = DummyAuthorizer()
    authorizer.add_user(config['username'], config['password'], config['root'], perm='elradfmwMT')
    logging.disable(logging.CRITICAL)

    class Handler(FTPHandler):
        passive_ports = config['passivePorts']
        masquerade_address = '127.0.0.1'

        def ftp_STOR(self, file, mode='w'):
            relative = Path(file).relative_to(config['root'])
            if relative.parts[0] == 'failure' and relative.name == 'file-100.csv':
                self.respond('550 SYNTHETIC_BATCH_FAILURE')
                return None
            return super().ftp_STOR(file, mode)

        def on_file_received(self, filename):
            with Path(config['events']).open('a') as output:
                output.write(json.dumps({'path': str(Path(filename).relative_to(config['root'])), 'sha256': hashlib.sha256(Path(filename).read_bytes()).hexdigest()}) + '\n')

    Handler.authorizer = authorizer
    server = FTPServer(('127.0.0.1', 0), Handler)
    private_json(Path(config['ready']), {'pid': os.getpid(), 'port': server.socket.getsockname()[1]})
    server.serve_forever(timeout=.1)


def main():
    runtime = Path(os.environ['KETTLE_WORKER_RUNTIME']).resolve()
    manifest = json.loads((runtime / 'manifest.json').read_text())
    ftp_python = Path(os.environ['KETTLE_FTP_PYTHON']).absolute()
    spec = importlib.util.spec_from_file_location('worker', REPO / 'tools/data-governance/kettle_worker.py')
    worker = importlib.util.module_from_spec(spec); spec.loader.exec_module(worker)
    base = runtime / 'ftp-batch-proof' / uuid.uuid4().hex
    base.mkdir(parents=True, mode=0o700)
    received = base / 'received'; received.mkdir(mode=0o700)
    classes = runtime / 'classes'
    sources = [REPO / 'data-governance/kettle-worker/src/NativeFtpBatchDelivery.java', Path(__file__).with_name('NativeFtpBatchDeliveryProof.java')]
    subprocess.run([manifest['javaHome'] + '/bin/javac', '-encoding', 'UTF-8', '-cp', manifest['classpath'], '-d', str(classes), *map(str, sources)], check=True, capture_output=True)
    reservations = []
    for _ in range(4):
        connection = socket.socket(); connection.bind(('127.0.0.1', 0)); reservations.append(connection)
    ports = [connection.getsockname()[1] for connection in reservations]
    config = {'username': 'synthetic-batches', 'password': secrets.token_urlsafe(24), 'root': str(received), 'passivePorts': ports, 'events': str(base / 'transfers.jsonl'), 'ready': str(base / 'ready.json')}
    configuration = base / 'fixture-private.json'; private_json(configuration, config)
    for connection in reservations: connection.close()
    results = []
    with (base / 'server.log').open('w') as log:
        server = subprocess.Popen([str(ftp_python), str(Path(__file__).resolve()), '--serve', str(configuration)], stdout=log, stderr=log, env={'PATH': '/usr/bin:/bin', 'HOME': str(base)}, start_new_session=True)
        try:
            deadline = time.monotonic() + 10
            while not Path(config['ready']).is_file():
                if server.poll() is not None or time.monotonic() > deadline: raise AssertionError('Owned FTP server did not start')
                time.sleep(.05)
            ready = json.loads(Path(config['ready']).read_text()); assert ready['pid'] == server.pid
            config['port'] = ready['port']
            expected = {f'file-{number:03d}.csv': f'id,text\n{number},原插件分批验证-{number}\n'.encode() for number in range(161)}
            for mode in ['keep', 'remove', 'onlynew', 'rename', 'failure', 'stop', 'changed', 'symlink', 'hardlink']:
                operation = base / ('operation-' + mode); operation.mkdir(mode=0o700)
                for folder in ['home', 'tmp', 'input', 'output']: (operation / folder).mkdir(mode=0o700)
                for name, content in expected.items(): (operation / 'output' / name).write_bytes(content)
                (operation / 'input/private-child.ktr').write_text('<job><password>SYNTHETIC_INPUT_SECRET</password></job>')
                (operation / 'output/ignored.txt').write_text('NATIVE_WILDCARD_EXCLUDED')
                (operation / 'output/ignored-directory').mkdir()
                (operation / 'output/ignored-directory/nested.csv').write_text('NATIVE_NON_RECURSIVE')
                (received / mode).mkdir()
                if mode == 'onlynew':
                    for name in list(expected)[:3]: (received / mode / name).write_bytes(b'EXISTING_NATIVE_ONLY_NEW_PROBE')
                if mode == 'symlink': (operation / 'output/unsafe.csv').symlink_to(operation / 'input/private-child.ktr')
                if mode == 'hardlink': os.link(operation / 'output/file-000.csv', operation / 'output/hardlink.csv')
                settings = operation / 'fixture.properties'
                settings.write_text('\n'.join(f'{key}={value}' for key, value in {'mode': mode, 'port': config['port'], 'username': config['username'], 'password': config['password']}.items()))
                settings.chmod(0o600)
                profile = operation / 'sandbox.sb'; profile.write_text(worker.sandbox_profile(runtime, operation, Path(manifest['javaHome']), [config['port'], *ports]))
                command = ['/usr/bin/sandbox-exec', '-f', str(profile), manifest['javaHome'] + '/bin/java', '-Xmx384m', '-XX:+PerfDisableSharedMem', '-Djava.net.preferIPv4Stack=true', '-Djava.awt.headless=true', '-Duser.timezone=Asia/Shanghai', '-DKETTLE_SYSTEM_HOSTNAME=isolated-kettle-worker', '-Duser.home=' + str(operation / 'home'), '-DKETTLE_HOME=' + str(operation / 'home'), '-Djava.io.tmpdir=' + str(operation / 'tmp'), '-cp', manifest['classpath'], 'NativeFtpBatchDeliveryProof', str(operation)]
                with (operation / 'engine.log').open('w') as log, (operation / 'events.ndjson').open('w') as events:
                    process = subprocess.run(command, cwd=operation, stdout=events, stderr=log, env={'PATH': '/usr/bin:/bin', 'HOME': str(operation / 'home'), 'LANG': 'en_US.UTF-8'}, timeout=90)
                assert process.returncode == 0, 'Native proof JVM failed: ' + str(operation)
                events = [json.loads(line) for line in (operation / 'events.ndjson').read_text().splitlines()]
                proof = next(event for event in events if event['type'] == 'proof')
                assert proof['originalEntryRestored']
                if mode in {'symlink', 'hardlink'}:
                    assert proof['preparationRejected'] and not list((received / mode).iterdir())
                    results.append(proof); continue
                assert proof['sameResultObject']
                journal = json.loads((operation / proof['manifest']).read_text())
                assert journal['maxFilesPerPass'] == 80 and journal['automaticReplay'] is False
                assert len(journal['files']) == 161 and all(batch['files'] <= 80 for batch in journal['batches'])
                remote = {}
                with ftplib.FTP() as ftp:
                    ftp.connect('127.0.0.1', config['port'], timeout=10); ftp.login(config['username'], config['password'])
                    ftp.cwd(mode)
                    for name in ftp.nlst():
                        content = io.BytesIO(); ftp.retrbinary('RETR ' + name, content.write)
                        remote[Path(name).name] = hashlib.sha256(content.getvalue()).hexdigest()
                expected_hashes = {name: hashlib.sha256(content).hexdigest() for name, content in expected.items()}
                assert all(expected_hashes.get(name) == digest for name, digest in remote.items()), 'Remote bytes differed from synthetic sources'
                transfers = [json.loads(line) for line in Path(config['events']).read_text().splitlines() if line.strip()]
                transfers = [item for item in transfers if item['path'].split('/')[0] == mode]
                if mode in {'keep', 'remove', 'onlynew', 'rename'}:
                    assert proof['resultBoolean'] and proof['errors'] == 0 and proof['state'] == 'COMPLETED'
                    assert proof['passes'] == 3 and proof['nativeProcessedFiles'] == 161 and proof['nativeFileCounter'] == 161
                    assert remote == expected_hashes and len(transfers) == 161
                    assert [batch['files'] for batch in journal['batches']] == [80, 80, 1]
                    assert len(list((operation / 'output').glob('*.csv'))) == (0 if mode == 'remove' else 161)
                    if mode != 'remove': assert all((operation / 'output' / name).read_bytes() == content for name, content in expected.items())
                else:
                    assert not proof['resultBoolean'] and proof['state'] in {'STOPPED', 'FAILED'}
                    assert proof['pendingFiles'] > 0 and len(remote) < 161
                    if mode == 'failure': assert proof['passes'] == 2 and proof['errors'] > 0 and all(int(name[5:8]) < 160 for name in remote)
                    if mode == 'stop': assert proof['passes'] == 1 and proof['stopped'] and len(remote) == 80
                    if mode == 'changed':
                        assert proof['passes'] == 1 and proof['errors'] > 0 and len(remote) == 80
                        assert (operation / 'output/file-000.csv').read_text() == 'CHANGED_AFTER_NATIVE_TRANSFER\n'
                        assert journal['files'][0]['sourceState'] == 'CHANGED_PRESERVED'
                assert (operation / 'input/private-child.ktr').exists() and (operation / 'output/ignored.txt').exists()
                assert not any(name.startswith('pass-') for name in os.listdir(operation / 'output'))
                proof.update(remoteRetrievedAndHashVerifiedFiles=len(remote), duplicateTransfers=len(transfers) - len(remote))
                results.append(proof)
                print(json.dumps({'mode': mode, 'passed': True, 'remoteFiles': len(remote), 'passes': proof['passes']}, ensure_ascii=False), flush=True)
        finally:
            if server.poll() is None: server.terminate(); server.wait(timeout=10)
    report = {'passed': True, 'originalClass': 'org.pentaho.di.job.entries.ftpput.JobEntryFTPPUT', 'cases': results, 'ownedFixtureStopped': server.poll() is not None, 'onlyNewObservation': 'Original archive deleted and replaced the three existing remote files even with only_new enabled; helper preserved that original option and behavior.'}
    private_json(base / 'acceptance.json', report)
    print(json.dumps({'passed': True, 'evidence': str(base / 'acceptance.json'), 'cases': len(results)}))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(); parser.add_argument('--serve', type=Path); options = parser.parse_args()
    if options.serve: serve(options.serve)
    else: main()
