#!/usr/bin/env python3
"""Full NiFi output -> persisted artifact -> FTP roundtrip; dedicated local fixtures only."""
import argparse
import hashlib
import json
from pathlib import Path
import ssl
import time
import urllib.request


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--runtime', type=Path, required=True)
    parser.add_argument('--app-runtime', type=Path, required=True)
    args = parser.parse_args()
    runtime, app = args.runtime.resolve(), args.app_runtime.resolve()
    assert json.loads((app / 'private/app-owner.json').read_text()) == {'owner': 'rynew-data-governance-app', 'root': str(app)}
    assert json.loads((runtime / 'runtime.json').read_text())['owner'] == 'rynew-data-governance-runtime'
    def private(name, base=app):
        path = base / 'private' / name
        assert path.stat().st_mode & 0o077 == 0
        return json.loads(path.read_text())
    context = ssl.create_default_context(cafile=str(runtime / 'private/gateway-ca.pem'))
    token = None
    def call(path, method='GET', body=None, raw=False, accepted=True):
        headers = {'Content-Type': 'application/json'}
        if token:
            headers['Authorization'] = 'Bearer ' + token
        request = urllib.request.Request('https://localhost:10443/prod-api' + path,
            data=json.dumps(body, ensure_ascii=False).encode() if body is not None else None, method=method, headers=headers)
        with urllib.request.urlopen(request, context=context, timeout=30) as response:
            result = response.read()
        if raw:
            return result
        value = json.loads(result)
        assert (value.get('code') == 200) == accepted, 'Local API acceptance mismatch: ' + path
        return value
    token = call('/login', 'POST', {**private('app-login.json'), 'code': '', 'uuid': ''})['token']
    projects = call('/governance/projects')['data']
    project = next((p for p in projects if p['name'] == '完整产物与交付验收'), None)
    if project is None:
        project = call('/governance/projects', 'POST', {'name': '完整产物与交付验收', 'description': '合成输入、完整文件与独立FTP回读核对'})['data']
    flows = call('/governance/flows?projectId=' + project['id'])['data']
    flow = next((f for f in flows if f['name'] == '完整协议文本批次'), None)
    if flow is None:
        flow = call('/governance/flows', 'POST', {'projectId': project['id'], 'name': '完整协议文本批次', 'templateId': 'delimited-safe-v1'})['data']
    def run(records, expected):
        result = call('/governance/flows/' + flow['id'] + '/tests', 'POST', {'inputJson': json.dumps(records, ensure_ascii=False), 'parameters': {}})['data']
        deadline = time.monotonic() + 90
        while result['status'] in {'RUNNING', 'QUEUED'} and time.monotonic() < deadline:
            time.sleep(.3)
            result = call('/governance/test-runs/' + result['id'])['data']
        assert result['status'] == expected and result['cleanupConfirmed']
        return result
    records = [{'message': '合成记录%03d-' % i + '字段内容' * 45, 'picture': ''} for i in range(75)]
    result = run(records, 'SUCCEEDED')
    assert result['artifactsManifestAvailable'] and result['artifactCount'] == 2
    manifest = call('/governance/test-runs/' + result['id'] + '/artifacts')['data']
    sizes, hashes, data_counts = [], [], []
    for artifact in manifest['artifacts']:
        data = call('/governance/test-runs/' + result['id'] + '/artifacts/' + artifact['id'] + '/content', raw=True)
        assert len(data) == artifact['byteSize'] and hashlib.sha256(data).hexdigest() == artifact['sha256']
        assert data.startswith(b'message|\x1fpicture\n')
        sizes.append(len(data)); hashes.append(artifact['sha256']); data_counts.append(data.count(b'\n') - 1)
    assert sizes[0] > 8192 and data_counts == [74, 1]
    assert len(result['output'][0]) < sizes[0], 'Preview must remain distinct from full artifact'
    ftp = private('ftp-credentials.json', runtime)
    assert Path(ftp['root']).resolve() == runtime / 'ftp/files'
    profiles = call('/governance/ftp-connections')['data']
    profile = next((p for p in profiles if p['name'] == '本地FTP完整批次'), None)
    if profile is None:
        profile = call('/governance/ftp-connections', 'POST', {'name': '本地FTP完整批次', 'host': '127.0.0.1', 'port': 2121,
            'username': ftp['username'], 'password': ftp['password'], 'directory': '/', 'transferMode': 'BINARY', 'controlEncoding': 'UTF-8'})['data']
    assert profile['host'] == '127.0.0.1' and profile['port'] == 2121
    assert call('/governance/ftp-connections/' + profile['id'] + '/test', 'POST')['data']['success']
    job = call('/governance/deliveries', 'POST', {'runId': result['id'], 'connectionId': profile['id']})['data']
    deadline = time.monotonic() + 90
    while job['status'] in {'QUEUED', 'RUNNING'} and time.monotonic() < deadline:
        time.sleep(.3)
        job = call('/governance/deliveries/' + job['id'])['data']
    assert job['status'] == 'DELIVERED' and all(e['status'] == 'VERIFIED' for e in job['entries'])
    duplicate = call('/governance/deliveries', 'POST', {'runId': result['id'], 'connectionId': profile['id']})['data']
    assert duplicate['id'] == job['id'] and duplicate['attempts'] == 1
    assert call('/governance/test-runs/' + result['id'] + '/artifacts')['data'] == manifest
    for status, batch in [('EMPTY', []), ('FAILED', [{'missing': 'fixture'}])]:
        rejected = run(batch, status)
        assert not rejected['artifactsManifestAvailable']
        call('/governance/deliveries', 'POST', {'runId': rejected['id'], 'connectionId': profile['id']}, accepted=False)
    evidence = {'projectId': project['id'], 'flowId': flow['id'], 'runId': result['id'], 'artifactSizes': sizes,
        'artifactHashes': hashes, 'dataSegments': data_counts, 'fullPayloadExceedsPreview': True, 'deliveryId': job['id'],
        'deliveryStatus': job['status'], 'remoteDirectory': job['remoteDirectory'], 'idempotentSubmit': True,
        'localArtifactsRetained': True, 'emptyAndFailedRunsCannotDeliver': True, 'cleanupConfirmed': True}
    (app / 'evidence/delivery-api-smoke.json').write_text(json.dumps(evidence, ensure_ascii=False, indent=2))
    print(json.dumps(evidence, ensure_ascii=False, indent=2))


if __name__ == '__main__':
    main()
