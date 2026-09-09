#!/usr/bin/env python3
"""Verify the local PostgreSQL snapshot -> NiFi lookup -> real Cron batch loop."""
import argparse
import json
from pathlib import Path
import ssl
import time
import urllib.request
import urllib.error


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--runtime', type=Path, required=True)
    parser.add_argument('--app-runtime', type=Path, required=True)
    args = parser.parse_args()
    runtime, app = args.runtime.resolve(), args.app_runtime.resolve()
    if json.loads((app / 'private/app-owner.json').read_text()) != {'owner': 'rynew-data-governance-app', 'root': str(app)}:
        raise RuntimeError('Dedicated application ownership required')
    def private(name, root=app):
        path = root / 'private' / name
        if path.stat().st_mode & 0o077:
            raise RuntimeError('Private file permissions must be 600')
        return json.loads(path.read_text())
    context = ssl.create_default_context(cafile=str(runtime / 'private/gateway-ca.pem'))
    token = None
    def call(path, method='GET', body=None):
        headers = {'Content-Type': 'application/json'}
        if token:
            headers['Authorization'] = 'Bearer ' + token
        request = urllib.request.Request('https://localhost:10443/prod-api' + path,
            data=json.dumps(body, ensure_ascii=False).encode() if body is not None else None, method=method, headers=headers)
        try:
            with urllib.request.urlopen(request, context=context, timeout=30) as response:
                value = json.load(response)
        except urllib.error.HTTPError as error:
            raise RuntimeError('Local API HTTP ' + str(error.code)) from None
        if value.get('code') != 200:
            raise RuntimeError('Local API rejected ' + path + ': ' + str(value.get('msg', 'unknown')))
        return value
    token = call('/login', 'POST', {**private('app-login.json'), 'code': '', 'uuid': ''})['token']
    pg = private('postgres-credentials.json', runtime)
    assert pg['host'] == '127.0.0.1' and pg['port'] == 15432
    profiles = call('/governance/connections')['data']
    profile = next((item for item in profiles if item['name'] == '本地 PostgreSQL 字典'), None)
    if profile is None:
        profile = call('/governance/connections', 'POST', {**pg, 'name': '本地 PostgreSQL 字典', 'sslMode': 'disable'})['data']
    assert profile['passwordConfigured'] and 'password' not in profile and 'encryptedPassword' not in profile
    assert call('/governance/connections/' + profile['id'] + '/test', 'POST')['data']['readOnly']
    snapshot = call('/governance/connections/' + profile['id'] + '/snapshot', 'POST', {
        'schema': 'public', 'table': 'governance_demo_camera', 'columns': ['camera_code', 'platform_code', 'external_code', 'active'], 'orderBy': ['camera_code', 'platform_code']})['data']
    rows = json.loads(snapshot['rowsJson'])
    expected = next(item['external_code'] for item in rows if item['camera_code'] == 'CAM-001' and item['platform_code'] == 'DEMO')
    projects = call('/governance/projects')['data']
    project = next((item for item in projects if item['name'] == '查表与调度接口验收'), None)
    if project is None:
        project = call('/governance/projects', 'POST', {'name': '查表与调度接口验收', 'description': '独立合成数据、只读字典、冻结版本与真实Cron触发'})['data']
    flow = call('/governance/flows', 'POST', {'projectId': project['id'], 'name': '快照查表调度 ' + str(int(time.time())), 'templateId': 'lookup-safe-v1'})['data']
    design = call('/governance/flows/' + flow['id'] + '/design')['data']
    node = next(item for item in design['nodes'] if item['type'].endswith('.JsonLookupSnapshot'))
    properties = {**node['properties'], 'Lookup Rows': snapshot['rowsJson']}
    call('/governance/flows/' + flow['id'] + '/design/nodes/' + node['id'], 'PUT', {'version': node['version'], 'properties': properties})
    input_json = json.dumps([{'camera': 'CAM-001', 'platform': 'DEMO'}, {'camera': 'CAM-002', 'platform': 'DEMO'}, {'camera': 'MISSING', 'platform': 'DEMO'}])
    release = call('/governance/releases', 'POST', {'flowId': flow['id'], 'name': '数据库字典冻结 v1', 'inputJson': input_json, 'parameters': {}})['data']
    assert release['inputMode'] == 'FIXED_JSON_BATCH' and 'inputJson' not in release
    schedule = call('/governance/schedules', 'POST', {'name': '真实Cron字典验证', 'releaseId': release['id'], 'cron': '0/10 * * * * ?', 'timeZone': 'Asia/Shanghai'})['data']
    assert not schedule['enabled']
    schedule_id = schedule['id']
    run = None
    try:
        enabled = call('/governance/schedules/' + schedule_id + '/state', 'POST', {'enabled': True, 'revision': schedule['revision']})['data']
        assert enabled['enabled'] and enabled['nextRunAt']
        deadline = time.monotonic() + 60
        while time.monotonic() < deadline:
            schedule = next(item for item in call('/governance/schedules')['data'] if item['id'] == schedule_id)
            if schedule.get('lastRunId') and not schedule.get('activeRunId'):
                run = call('/governance/test-runs/' + schedule['lastRunId'])['data']
                break
            time.sleep(0.5)
        assert run is not None, 'Actual scheduler timer did not complete in time'
        assert run['status'] == 'SUCCEEDED' and run['cleanupConfirmed'], 'Scheduled lookup did not complete and clean up'
        assert run['definitionHash'] == release['definitionHash']
        output = json.loads(run['output'][0])
        assert [item['external_camera'] for item in output] == [expected, '0', '0']
        lookup = next(item for item in run['steps'] if item['type'].endswith('.JsonLookupSnapshot'))
        attributes = lookup['samples']['attributes'][0]
        assert attributes['governance.lookup.matched.records'] == '1'
        assert attributes['governance.lookup.unmatched.records'] == '2'
        assert attributes['governance.lookup.snapshot.sha256'] == snapshot['sha256']
    finally:
        current = next(item for item in call('/governance/schedules')['data'] if item['id'] == schedule_id)
        paused = call('/governance/schedules/' + schedule_id + '/state', 'POST', {'enabled': False, 'revision': current['revision']})['data']
        assert not paused['enabled']
    evidence = {'connectionId': profile['id'], 'postgresReadOnlyVerified': True, 'dictionaryRows': snapshot['rowCount'],
        'dictionaryHash': snapshot['sha256'], 'projectId': project['id'], 'flowId': flow['id'], 'releaseId': release['id'], 'scheduleId': schedule_id,
        'actualCronTriggered': True, 'schedulePausedAfterTest': True, 'runId': run['id'], 'status': run['status'], 'cleanupConfirmed': run['cleanupConfirmed'],
        'matchedRecords': 1, 'unmatchedRecords': 2, 'stringDefaultsPreserved': True}
    (app / 'evidence/lookup-scheduler-smoke.json').write_text(json.dumps(evidence, ensure_ascii=False, indent=2))
    print(json.dumps(evidence, ensure_ascii=False))


if __name__ == '__main__':
    main()
