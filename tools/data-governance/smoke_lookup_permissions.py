#!/usr/bin/env python3
"""Verify local connection/publication/scheduling permissions without valid mutation payloads."""
import argparse
import json
from pathlib import Path
import ssl
import urllib.request
import urllib.error


def main():
    parser = argparse.ArgumentParser(); parser.add_argument('--runtime', type=Path, required=True); parser.add_argument('--app-runtime', type=Path, required=True)
    args = parser.parse_args(); runtime, app = args.runtime.resolve(), args.app_runtime.resolve()
    assert json.loads((app / 'private/app-owner.json').read_text()) == {'owner': 'rynew-data-governance-app', 'root': str(app)}
    context = ssl.create_default_context(cafile=str(runtime / 'private/gateway-ca.pem'))
    def call(path, method='GET', body=None, token=None):
        headers = {'Content-Type': 'application/json'}
        if token: headers['Authorization'] = 'Bearer ' + token
        request = urllib.request.Request('https://localhost:10443/prod-api' + path, method=method, headers=headers,
            data=json.dumps(body).encode() if body is not None else None)
        try:
            with urllib.request.urlopen(request, context=context, timeout=15) as response: return json.load(response)
        except urllib.error.HTTPError as error: return {'code': error.code}
    def login(name):
        path = app / 'private' / name
        assert not path.stat().st_mode & 0o077
        return call('/login', 'POST', {**json.loads(path.read_text()), 'code': '', 'uuid': ''})['token']
    admin, viewer = login('app-login.json'), login('viewer-login.json')
    fixture = json.loads((app / 'evidence/lookup-scheduler-smoke.json').read_text())
    connection, release, schedule = fixture['connectionId'], fixture['releaseId'], fixture['scheduleId']
    before = call('/governance/schedules', token=admin)['data']
    requests = [
        ('connectionList', '/governance/connections', 'GET', None),
        # Supply the primitive port so deserialization reaches the permission interceptor.
        # Zero still prevents a valid network target if an authorization defect is discovered.
        ('connectionCreate', '/governance/connections', 'POST', {'port': 0}),
        ('connectionEdit', '/governance/connections/' + connection, 'PUT', {'revision': -1, 'port': 0}),
        ('connectionTest', '/governance/connections/' + connection + '/test', 'POST', None),
        ('snapshotRead', '/governance/connections/' + connection + '/snapshot', 'POST', {}),
        ('publish', '/governance/releases', 'POST', {}),
        ('scheduleCreate', '/governance/schedules', 'POST', {}),
        ('scheduleEdit', '/governance/schedules/' + schedule, 'PUT', {'revision': -1}),
        ('scheduleState', '/governance/schedules/' + schedule + '/state', 'POST', {'enabled': True, 'revision': -1}),
        ('scheduleRun', '/governance/schedules/' + schedule + '/run', 'POST', None),
        ('scheduleRecover', '/governance/schedules/' + schedule + '/recover', 'POST', None),
    ]
    denied = {name: call(path, method, body, viewer).get('code') for name, path, method, body in requests}
    assert all(value == 403 for value in denied.values()), 'Unexpected denial codes: ' + json.dumps(denied)
    assert call('/governance/connections').get('code') == 401
    assert call('/governance/releases', token=viewer)['data'] == []
    assert call('/governance/schedules', token=viewer)['data'] == []
    detail = call('/governance/releases/' + release, token=viewer)
    assert detail.get('code') != 200 and not detail.get('data')
    after = call('/governance/schedules', token=admin)['data']
    assert sorted(before, key=lambda row: row['id']) == sorted(after, key=lambda row: row['id'])
    result = {'viewerDenied': denied, 'anonymousDenied': True, 'ownerIsolation': True, 'schedulesUnchanged': True}
    (app / 'evidence/lookup-scheduler-permissions.json').write_text(json.dumps(result, indent=2)); print(json.dumps(result))


if __name__ == '__main__': main()
