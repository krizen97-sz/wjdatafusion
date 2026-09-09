#!/usr/bin/env python3
"""Read-only/denied-request verification of the owned local designer API."""
import argparse
import json
from pathlib import Path
import ssl
import urllib.error
import urllib.request


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--runtime', type=Path, required=True)
    parser.add_argument('--app-runtime', type=Path, required=True)
    args = parser.parse_args()
    runtime, app = args.runtime.resolve(), args.app_runtime.resolve()
    owner = json.loads((app / 'private/app-owner.json').read_text())
    if owner != {'owner': 'rynew-data-governance-app', 'root': str(app)}:
        raise RuntimeError('Dedicated application ownership required')
    context = ssl.create_default_context(cafile=str(runtime / 'private/gateway-ca.pem'))

    def call(path, method='GET', body=None, token=None):
        headers = {'Content-Type': 'application/json'}
        if token:
            headers['Authorization'] = 'Bearer ' + token
        request = urllib.request.Request('https://localhost:10443/prod-api' + path,
            data=json.dumps(body).encode() if body is not None else None, method=method, headers=headers)
        try:
            with urllib.request.urlopen(request, context=context, timeout=20) as response:
                return json.load(response)
        except urllib.error.HTTPError as error:
            return {'code': error.code}

    def login(filename):
        path = app / 'private' / filename
        if path.stat().st_mode & 0o077:
            raise RuntimeError('Credential file permissions must be 600')
        response = call('/login', 'POST', {**json.loads(path.read_text()), 'code': '', 'uuid': ''})
        if response.get('code') != 200:
            raise RuntimeError('Local test login was rejected')
        return response['token']

    admin, viewer = login('app-login.json'), login('viewer-login.json')
    flow_id = json.loads((app / 'evidence/app-smoke.json').read_text())['flows'][0]['id']
    base = '/governance/flows/' + flow_id + '/design'
    before = call(base, token=admin)['data']
    node, edge = before['nodes'][0], before['connections'][0]
    checks = [
        ('createNode', base + '/nodes', 'POST', {}),
        ('updateNode', base + '/nodes/' + node['id'], 'PUT', {'version': -1, 'name': 'denied'}),
        ('deleteNode', base + '/nodes/' + node['id'] + '?version=-1', 'DELETE', None),
        ('createConnection', base + '/connections', 'POST', {}),
        ('deleteConnection', base + '/connections/' + edge['id'] + '?version=-1', 'DELETE', None),
    ]
    denied = {name: call(path, method, body, viewer).get('code') == 403 for name, path, method, body in checks}
    assert all(denied.values()), 'A viewer mutation was not denied before validation'
    assert call(base).get('code') == 401
    assert call(base, token=viewer).get('code') == 200
    assert call('/governance/design/node-types', token=viewer).get('code') == 200
    after = call(base, token=admin)['data']
    for kind in ('nodes', 'connections'):
        assert sorted(before[kind], key=lambda item: item['id']) == sorted(after[kind], key=lambda item: item['id']), 'Denied requests changed the flow'
    result = {'anonymousDenied': True, 'viewerCanReadDesign': True, 'viewerCanReadNodeTypes': True,
        'viewerMutationsDenied': denied, 'flowUnchanged': True, 'credentialsChanged': False}
    (app / 'evidence/modern-designer-permissions.json').write_text(json.dumps(result, indent=2))
    print(json.dumps(result))


if __name__ == '__main__':
    main()
