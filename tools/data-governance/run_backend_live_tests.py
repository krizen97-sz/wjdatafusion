#!/usr/bin/env python3
"""Run governance tests inside a disposable owned NiFi subtree and local database fixtures."""
import argparse
import json
import os
from pathlib import Path
import ssl
import subprocess
import urllib.error
import urllib.parse
import urllib.request
import uuid


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--runtime', type=Path, required=True)
    parser.add_argument('--app-runtime', type=Path, required=True)
    parser.add_argument('--with-kafka', action='store_true')
    args = parser.parse_args()
    runtime, app = args.runtime.resolve(), args.app_runtime.resolve()
    assert json.loads((runtime / 'runtime.json').read_text())['owner'] == 'rynew-data-governance-runtime'
    assert json.loads((app / 'private/app-owner.json').read_text()) == {'owner': 'rynew-data-governance-app', 'root': str(app)}
    if args.with_kafka:
        check = subprocess.run([os.sys.executable, str(Path(__file__).with_name('kafka_dev.py')), '--runtime-root', str(runtime), 'health'], capture_output=True, text=True, timeout=30)
        assert check.returncode == 0, 'Owned Kafka runtime is not healthy'
    credential_path = runtime / 'private/nifi-credentials.json'
    assert credential_path.stat().st_mode & 0o077 == 0
    credentials = json.loads(credential_path.read_text())
    base = credentials['baseUrl'].rstrip('/')
    assert urllib.parse.urlparse(base).netloc in {'localhost:9443', '127.0.0.1:9443'}
    if not base.endswith('/nifi-api'):
        base += '/nifi-api'
    context = ssl.create_default_context(cafile=credentials['caCert'])
    request = urllib.request.Request(base + '/access/token', data=urllib.parse.urlencode({
        'username': credentials['username'], 'password': credentials['password']}).encode(), headers={'Content-Type': 'application/x-www-form-urlencoded'})
    with urllib.request.urlopen(request, context=context, timeout=20) as response:
        token = response.read().decode()
    def call(method, path, body=None):
        request = urllib.request.Request(base + path, method=method, headers={'Content-Type': 'application/json', 'Authorization': 'Bearer ' + token},
            data=json.dumps(body).encode() if body is not None else None)
        with urllib.request.urlopen(request, context=context, timeout=30) as response:
            return json.load(response)
    parent = json.loads((app / 'nifi-binding.json').read_text())['rootGroupId']
    marker = 'RYNEW_BACKEND_SUITE:' + str(uuid.uuid4())
    group = call('POST', '/process-groups/' + parent + '/process-groups', {'revision': {'version': 0},
        'component': {'name': '治理后端独立回归', 'comments': marker, 'position': {'x': 0, 'y': 0}}})['component']['id']
    status, cleaned = 1, False
    try:
        environment = {**os.environ, 'JAVA_HOME': '/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home',
            'GOVERNANCE_PG_CREDENTIALS': str(runtime / 'private/postgres-credentials.json'),
            'GOVERNANCE_FTP_CREDENTIALS': str(runtime / 'private/ftp-credentials.json')}
        command = ['mvn', '-f', 'WDF100.0/pom.xml', '-pl', 'wjdatafusion-manage', '-am', '-Dtest=DataGovernance*Test',
            '-Dsurefire.failIfNoSpecifiedTests=false', '-Dgovernance.nifi.smoke=true', '-Dgovernance.nifi.baseUrl=' + base,
            '-Dgovernance.nifi.rootGroupId=' + group, '-Dgovernance.nifi.credentialsFile=' + str(credential_path), 'test']
        if args.with_kafka: command.insert(-1, '-Dgovernance.kafka.smoke=true')
        log = app / 'evidence/backend-full-live-tests.log'
        with log.open('w') as output:
            status = subprocess.run(command, cwd=Path(__file__).resolve().parents[2], env=environment, stdout=output, stderr=subprocess.STDOUT).returncode
    finally:
        current = call('GET', '/process-groups/' + group)
        assert current['component']['comments'] == marker and current['component']['parentGroupId'] == parent
        aggregate = call('GET', '/flow/process-groups/' + group + '/status')['processGroupStatus']['aggregateSnapshot']
        assert aggregate['activeThreadCount'] == 0 and aggregate['flowFilesQueued'] == 0, 'Owned test subtree still needs cleanup'
        call('DELETE', '/process-groups/' + group + '?version=' + str(current['revision']['version']) + '&disconnectedNodeAcknowledged=false')
        try:
            call('GET', '/process-groups/' + group)
        except urllib.error.HTTPError as error:
            assert error.code == 404
            cleaned = True
        assert cleaned
        evidence = {'exitCode': status, 'ownedSubtree': group, 'cleanupConfirmed': cleaned}
        (app / 'evidence/backend-full-live-tests.json').write_text(json.dumps(evidence, indent=2))
        print(json.dumps(evidence))
    raise SystemExit(status)


if __name__ == '__main__':
    main()
