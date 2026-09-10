"""Explicit read-only VALUES query against an authorized isolated PostgreSQL fixture."""
import importlib.util
import json
import os
from pathlib import Path
import sys
import copy
import uuid
import xml.etree.ElementTree as ET

spec = importlib.util.spec_from_file_location('fixtures', Path(__file__).with_name('test_kettle_worker.py'))
fixtures = importlib.util.module_from_spec(spec)
spec.loader.exec_module(fixtures)


def main():
    runtime = os.environ['KETTLE_WORKER_RUNTIME']
    credentials = json.loads(Path(os.environ['KETTLE_POSTGRES_CREDENTIALS']).read_text())
    if credentials['host'] != '127.0.0.1' or int(credentials['port']) != 15432:
        raise ValueError('This test permits only the authorized isolated 127.0.0.1:15432 fixture')
    worker = fixtures.module.Worker(runtime, timeout=20, allow_endpoints=['127.0.0.1:15432'])
    catalog = worker.capabilities()
    transformation = ET.fromstring(fixtures.fixture(catalog))
    connection = ET.SubElement(transformation, 'connection')
    for key, value in {'name': 'synthetic_pg', 'server': credentials['host'], 'type': 'POSTGRESQL', 'access': 'Native', 'database': credentials['database'], 'port': credentials['port'], 'username': credentials['username'], 'password': credentials['password']}.items():
        fixtures.put(connection, key, value)
    source = next(step for step in transformation.findall('step') if step.findtext('name') == 'file-input')
    source.clear()
    for key, value in {'name': 'file-input', 'type': 'TableInput', 'copies': '1', 'distribute': 'Y', 'partitioning/method': 'none'}.items():
        fixtures.put(source, key, value)
    plugin = next(step for step in catalog['steps'] if step['id'] == 'TableInput')
    for child in ET.fromstring('<settings>' + plugin['defaultXml'] + '</settings>'):
        source.append(child)
    fixtures.put(source, 'connection', 'synthetic_pg')
    fixtures.put(source, 'sql', "SELECT * FROM (VALUES ('alice',1),('bob',2)) AS fixture(name,n)")
    # The original writer allocates by JDBC field length; PostgreSQL TEXT is unbounded.
    # Configure original SelectValues to normalize widths to variable-length String (-1).
    select = ET.SubElement(transformation, 'step')
    for key, value in {'name': 'bounded-output-fields', 'type': 'SelectValues', 'copies': '1', 'distribute': 'Y', 'partitioning/method': 'none', 'fields/select_unspecified': 'Y'}.items():
        fixtures.put(select, key, value)
    for name in ['name', 'n', 'greeting']:
        field = ET.SubElement(select.find('fields'), 'meta')
        for key, value in {'name': name, 'rename': name, 'type': 'String', 'length': '-1', 'precision': '-1', 'storage_type': 'normal'}.items():
            fixtures.put(field, key, value)
    for hop in transformation.findall('./order/hop'):
        if hop.findtext('to') == 'file-output':
            fixtures.put(hop, 'to', 'bounded-output-fields')
    hop = ET.SubElement(transformation.find('order'), 'hop')
    for key, value in {'from': 'bounded-output-fields', 'to': 'file-output', 'enabled': 'Y'}.items():
        fixtures.put(hop, key, value)
    schema = 'kettle_v2_' + uuid.uuid4().hex[:16] if os.environ.get('KETTLE_POSTGRES_LOOKUP_PROOF') == '1' else None
    def execute_sql(sql):
        sql_trans = ET.Element('transformation')
        fixtures.put(sql_trans, 'info/name', 'Synthetic isolated schema lifecycle')
        sql_trans.append(copy.deepcopy(connection))
        step = ET.SubElement(sql_trans, 'step')
        for key, value in {'name': 'Owned schema SQL', 'type': 'ExecSQL', 'copies': '1', 'distribute': 'Y', 'GUI/draw': 'Y', 'connection': 'synthetic_pg', 'execute_each_row': 'N', 'single_statement': 'N', 'replace_variables': 'N', 'sql': sql}.items():
            fixtures.put(step, key, value)
        run_id = worker.launch('run', ET.tostring(sql_trans, encoding='unicode'))
        outcome = worker.wait(run_id)
        if outcome['state'] != 'SUCCEEDED':
            raise AssertionError('Owned schema lifecycle failed: ' + run_id)
        return run_id
    cleanup = None
    try:
        if schema:
            execute_sql('CREATE SCHEMA ' + schema + '; CREATE TABLE ' + schema + '.fixture_lookup (n integer primary key, label varchar(40)); INSERT INTO ' + schema + ".fixture_lookup VALUES (1,'first'),(2,'second')")
            lookup = ET.SubElement(transformation, 'step')
            for key, value in {'name': 'original-db-lookup', 'type': 'DBLookup', 'copies': '1', 'distribute': 'Y', 'connection': 'synthetic_pg', 'cache': 'N', 'lookup/schema': schema, 'lookup/table': 'fixture_lookup', 'lookup/fail_on_multiple': 'Y', 'lookup/eat_row_on_failure': 'N', 'lookup/key/name': 'n', 'lookup/key/field': 'n', 'lookup/key/condition': '=', 'lookup/key/name2': '', 'lookup/value/name': 'label', 'lookup/value/rename': 'label', 'lookup/value/default': '', 'lookup/value/type': 'String'}.items():
                fixtures.put(lookup, key, value)
            for hop in transformation.findall('./order/hop'):
                if hop.findtext('to') == 'bounded-output-fields':
                    fixtures.put(hop, 'to', 'original-db-lookup')
            hop = ET.SubElement(transformation.find('order'), 'hop')
            for key, value in {'from': 'original-db-lookup', 'to': 'bounded-output-fields', 'enabled': 'Y'}.items():
                fixtures.put(hop, key, value)
            field = ET.SubElement(select.find('fields'), 'meta')
            for key, value in {'name': 'label', 'rename': 'label', 'type': 'String', 'length': '-1', 'precision': '-1', 'storage_type': 'normal'}.items():
                fixtures.put(field, key, value)
        xml = ET.tostring(transformation, encoding='unicode')
        identifier = worker.launch('run', xml)
        result = worker.wait(identifier)
    finally:
        if schema:
            cleanup = execute_sql('DROP SCHEMA IF EXISTS ' + schema + ' CASCADE')
    events = worker.runs[identifier]['events']
    summary = {'runId': identifier, 'state': result['state'], 'errors': result.get('errors'), 'inputQueryReadOnly': True, 'endpoint': '127.0.0.1:15432', 'stepClassSource': plugin['classSource']}
    if result['state'] == 'SUCCEEDED':
        output = Path(worker.runs[identifier]['directory']) / 'output/result.csv'
        lines = output.read_text().splitlines()
        expected = ['name,n,greeting,label', 'alice,1,ALICE!,first', 'bob,2,BOB!,second'] if schema else ['name,n,greeting', 'alice,1,ALICE!', 'bob,2,BOB!']
        assert lines == expected, 'Unexpected synthetic PostgreSQL result'
        summary.update({'rows': 2, 'exactReadback': True, 'normalizedOutputMetadataViaOriginalSelectValues': True})
        if schema:
            summary.update({'nativeLookup': True, 'temporarySchema': schema, 'cleanupRunId': cleanup, 'schemaCleanupSucceeded': True})
    else:
        log = (Path(worker.runs[identifier]['directory']) / 'engine.log').read_text()
        # Report the driver failure category only; never emit passwords, usernames or complete JDBC logs.
        summary['legacyScramUnsupported'] = 'authentication type 10 is not supported' in log or '不支援 10 验证类型' in log
        summary['terminalErrorClasses'] = [e.get('errorClass') for e in events if e['type'] == 'terminal' and e.get('errorClass')]
    (Path(runtime) / 'postgres-acceptance.json').write_text(json.dumps(summary, indent=2))
    print(json.dumps(summary))
    return 0 if result['state'] == 'SUCCEEDED' else 1


if __name__ == '__main__':
    sys.exit(main())
