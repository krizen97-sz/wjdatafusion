"""Opt-in real original FTP wildcard proof using only a new private loopback fixture."""
import ftplib
import hashlib
import importlib.util
import io
import json
import os
from pathlib import Path
import secrets
import socket
import subprocess
import sys
import time
import uuid
import xml.etree.ElementTree as ET

REPO = Path(__file__).resolve().parents[3]


def load_module(name, path):
    spec = importlib.util.spec_from_file_location(name, path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def main():
    worker_api = load_module('worker', REPO / 'tools/data-governance/kettle_worker.py')
    helper_path = Path(os.environ.get('KETTLE_FTP_FIXTURE_HELPER', str(REPO / 'tools/data-governance/test_native_job_ftp.py')))
    helper = load_module('native_ftp_fixture', helper_path)
    runtime = Path(os.environ['KETTLE_WORKER_RUNTIME']).resolve()
    ftp_python = Path(os.environ['KETTLE_FTP_PYTHON']).absolute()  # Preserve the venv symlink entry.
    base = worker_api.private_dir(runtime / 'input-output-ftp-proof' / uuid.uuid4().hex)
    files = worker_api.private_dir(base / 'received')
    worker_api.private_dir(files / 'success')
    reservations = []
    for _ in range(4):
        listener = socket.socket()
        listener.bind(('127.0.0.1', 0))
        reservations.append(listener)
    ports = [listener.getsockname()[1] for listener in reservations]
    config = {'username': 'input-output-proof', 'password': secrets.token_urlsafe(24), 'filesRoot': str(files), 'eventsFile': str(base / 'transfers.jsonl'), 'readyFile': str(base / 'ready.json'), 'passivePorts': ports}
    private = base / 'fixture-private.json'
    worker_api.atomic_private_json(private, config)
    for listener in reservations:
        listener.close()
    with (base / 'server.log').open('w') as log:
        server = subprocess.Popen([str(ftp_python), str(helper_path), '--serve', str(private)], stdout=log, stderr=log, env={'PATH': '/usr/bin:/bin', 'HOME': str(base)}, start_new_session=True)
        try:
            deadline = time.monotonic() + 10
            while not Path(config['readyFile']).is_file():
                if server.poll() is not None or time.monotonic() >= deadline:
                    raise AssertionError('Owned FTP fixture did not become ready')
                time.sleep(.05)
            ready = json.loads(Path(config['readyFile']).read_text())
            assert ready['pid'] == server.pid
            config['controlPort'] = ready['controlPort']
            policy = base / 'ftp-policy.json'
            worker_api.atomic_private_json(policy, {'purpose': 'synthetic-local-ftp', 'expiresAt': time.time() + 120, 'ports': [config['controlPort']] + ports})
            job = ET.fromstring(helper.job_xml('success', config).replace('${WORK_DIR}/synthetic.ktr', '${INPUT_DIR}/synthetic.ktr'))
            job.find("./entries/entry[type='FTP_PUT']/wildcard").text = '.*'
            worker = worker_api.Worker(runtime, timeout=30, ftp_test_policy=policy)
            original = ET.tostring(job, encoding='unicode')
            inputs = [{'name': 'synthetic.ktr', 'content': helper.transformation('success')}, {'name': 'input.csv', 'content': 'id,private_note\n1,INPUT_ONLY_MARKER\n'}, {'name': 'private-child.ktr', 'content': '<synthetic><password>INPUT_SECRET_MARKER</password></synthetic>'}]
            identifier = worker.launch('job', original, input_files=inputs)
            result = worker.wait(identifier)
            assert result['state'] == 'SUCCEEDED', 'Original FTP Job failed; inspect its private events'
            assert result['inputLayout'] == 'separate'
            assert {item['name'] for item in result['files']} == {'success.txt'}
            assert all(item['role'] == 'output' for item in result['files'])
            local = Path(worker.runs[identifier]['directory']) / 'output/success.txt'
            with ftplib.FTP() as ftp:
                ftp.connect('127.0.0.1', config['controlPort'], timeout=5)
                ftp.login(config['username'], config['password'])
                remote_names = ftp.nlst('success')
                received = io.BytesIO()
                ftp.retrbinary('RETR success/success.txt', received.write)
            assert {Path(name).name for name in remote_names} == {'success.txt'}
            assert received.getvalue() == local.read_bytes()
            assert b'INPUT_ONLY_MARKER' not in received.getvalue() and b'INPUT_SECRET_MARKER' not in received.getvalue()
            for item in inputs:
                try:
                    worker.open_file(identifier, item['name'])
                except FileNotFoundError:
                    pass
                else:
                    raise AssertionError('Input asset was exposed as a run output')
            report = {'passed': True, 'runId': identifier, 'wildcard': '.*', 'inputAssetCount': len(inputs), 'outputFiles': ['success.txt'], 'remoteFiles': sorted(Path(name).name for name in remote_names), 'ftpReadbackEqual': True, 'bytes': len(received.getvalue()), 'sha256': hashlib.sha256(received.getvalue()).hexdigest(), 'inputAssetDownloadsRejected': True}
        finally:
            if server.poll() is None:
                server.terminate()
                server.wait(timeout=10)
    report['ownedFixtureStopped'] = server.poll() is not None
    worker_api.atomic_private_json(base / 'acceptance.json', report)
    print(json.dumps(dict(report, evidence=str(base / 'acceptance.json')), ensure_ascii=False))


if __name__ == '__main__':
    main()
