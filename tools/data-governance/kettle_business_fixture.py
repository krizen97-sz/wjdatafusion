#!/usr/bin/env python3
"""Prepare original native Kettle graphs and synthetic data offline; never connect or run.

Original XML is read only. Private generated XML retains original processing scripts,
including literal URL replacement rules, but no original passwords. Output must be
outside the checkout. SQL, Kafka records and jobs require separate review/execution.
"""
import argparse
from collections import Counter
import hashlib
import json
import os
from pathlib import Path
import re
import uuid
from xml.dom import minidom
from zipfile import ZipFile

FORMAT = 'RYNEW_ORIGINAL_KETTLE_BUSINESS_FIXTURE_V1'
SOURCES = {'ordinary': ('普通过车/851987.zip', 'transformation'),
           'ordinary_job': ('普通过车/851988.zip', 'job'),
           'illegal': ('违法数据/917552.zip', 'transformation'),
           'illegal_job': ('违法数据/917552.zip', 'job')}
EXPECTED_STEPS = {'ordinary': 18, 'illegal': 24}
SECRET = re.compile(r'password|passwd|secret|token|private.?key', re.I)
BROKER_KEYS = {'zookeeper.connect', 'metadata.broker.list', 'bootstrap.servers', 'group.id'}


def require(condition, message):
    if not condition:
        raise ValueError(message)


def compact(value):
    return json.dumps(value, ensure_ascii=False, separators=(',', ':'), allow_nan=False)


def sha(data):
    return hashlib.sha256(data if isinstance(data, bytes) else data.encode('utf-8')).hexdigest()


def children(node, name=None):
    return [] if node is None else [n for n in node.childNodes if n.nodeType == n.ELEMENT_NODE and (name is None or n.tagName == name)]


def at(node, path):
    for part in path.split('/'):
        matches = children(node, part)
        if not matches:
            return None
        node = matches[0]
    return node


def text(node, path=None):
    node = at(node, path) if path else node
    return '' if node is None else ''.join(n.data for n in node.childNodes if n.nodeType in (n.TEXT_NODE, n.CDATA_SECTION_NODE))


def put(node, path, value):
    for part in path.split('/'):
        matches = children(node, part)
        if not matches:
            element = node.ownerDocument.createElement(part)
            node.appendChild(element)
            node = element
        else:
            node = matches[0]
    for item in list(node.childNodes):
        node.removeChild(item)
    node.appendChild(node.ownerDocument.createTextNode(str(value)))


def parse(data):
    require(len(data) <= 2 * 1024 * 1024, 'Original XML exceeds the bounded import size')
    decoded = data.decode('utf-8-sig')
    require(not re.search(r'<!\s*(DOCTYPE|ENTITY)', decoded, re.I), 'DTD/entities are forbidden')
    return minidom.parseString(decoded)


def read_source(root, key):
    relative, kind = SOURCES[key]
    path = root / relative
    require(path.is_file() and not path.is_symlink(), 'Required original archive is unavailable')
    with ZipFile(path) as archive:
        require(len(archive.infolist()) <= 20, 'Too many archive members')
        matches = []
        for member in archive.infolist():
            require(member.file_size <= 2 * 1024 * 1024, 'Oversized archive member')
            if member.is_dir():
                continue
            document = parse(archive.read(member))
            if document.documentElement.tagName == kind:
                matches.append((document, member.CRC))
    require(len(matches) == 1, 'Expected exactly one original graph of this kind')
    document, crc = matches[0]
    return document, {'archive': path.name, 'archiveSha256': sha(path.read_bytes()), 'memberCrc32': f'{crc:08x}',
                      'xmlSha256': sha(document.toxml(encoding='utf-8'))}


def secrets(document):
    return {text(e) for e in document.getElementsByTagName('*') if SECRET.search(e.tagName) and text(e)}


def clear_original_secrets(document, replacements):
    for element in document.getElementsByTagName('*'):
        if SECRET.search(element.tagName):
            if text(element) not in replacements:
                for child in list(element.childNodes):
                    element.removeChild(child)
        for name in list(element.attributes.keys()):
            if SECRET.search(name):
                element.setAttribute(name, '')


def processing_digest(step):
    """Exclude only explicit environment bindings; preserve each native processing value."""
    clone = step.cloneNode(True)
    kind = text(clone, 'type')
    paths = ['connection'] if kind == 'DBLookup' else []
    if kind in ('KafkaConsumer', 'KafkaProducer'):
        paths += ['TOPIC'] + ['KAFKA/' + key for key in BROKER_KEYS]
    if kind == 'TextFileOutput':
        paths += ['file/name']
    for path in paths:
        node = at(clone, path)
        if node is not None:
            node.parentNode.removeChild(node)
    return sha(clone.toxml())


def platform_code(document):
    for step in document.getElementsByTagName('step'):
        if text(step, 'type') == 'Constant':
            for field in children(at(step, 'fields'), 'field'):
                if text(field, 'name') == 'platformIndexCode':
                    value = text(field, 'nullif')
                    require(bool(value), 'Original platform constant is missing')
                    return value
    raise ValueError('Original platform constant was not found')


def native_basename(value):
    name = value.replace('\\', '/').rstrip('/').split('/')[-1]
    require(name and '..' not in name and not re.search(r'[\x00-\x1f/\\]', name), 'Unsafe original output basename')
    return name


def prepare_transformation(original, kind, config):
    document = original.cloneNode(True)
    root = document.documentElement
    original_steps = children(original.documentElement, 'step')
    steps = children(root, 'step')
    require(len(steps) == EXPECTED_STEPS[kind], 'Original graph step count changed; inspect before preparing')
    renamed = {}
    for index, connection in enumerate(children(root, 'connection'), 1):
        require(text(connection, 'type') == 'POSTGRESQL', 'Unexpected original database type')
        renamed[text(connection, 'name')] = f'fixture_connection_{index}'
        for path, value in {'name': f'fixture_connection_{index}', 'server': '127.0.0.1', 'port': '15432',
                            'database': config['database'], 'username': config['pgUser'], 'password': config['pgPassword'],
                            'servername': '', 'data_tablespace': '', 'index_tablespace': ''}.items():
            put(connection, path, value)
        for attribute in connection.getElementsByTagName('attribute'):
            if text(attribute, 'code') == 'PORT_NUMBER':
                put(attribute, 'attribute', '15432')
    consumer = None
    for step in steps:
        plugin = text(step, 'type')
        require(not text(step, 'cluster_schema'), 'Clustered original step requires separate environment review')
        if plugin == 'DBLookup':
            require(text(step, 'lookup/schema') in ('', 'public'), 'Non-public original lookup schema needs explicit fixture mapping')
            require(text(step, 'connection') in renamed, 'Unknown DBLookup connection binding')
            put(step, 'connection', renamed[text(step, 'connection')])
        elif plugin == 'KafkaConsumer':
            consumer = step
            put(step, 'TOPIC', config['topics'][kind])
            put(step, 'KAFKA/group.id', config['groups'][kind])
            put(step, 'KAFKA/zookeeper.connect', '127.0.0.1:22181')
        elif plugin == 'KafkaProducer':
            put(step, 'TOPIC', config['topics']['egress'])
            put(step, 'KAFKA/metadata.broker.list', '127.0.0.1:29092')
        elif plugin == 'TextFileOutput':
            put(step, 'file/name', '${WORK_DIR}/' + native_basename(text(step, 'file/name')))
    require(consumer is not None, 'Original consumer was not found')
    clear_original_secrets(document, {config['pgPassword']})
    require(all(processing_digest(a) == processing_digest(b) for a, b in zip(original_steps, steps)),
            'A native processing configuration changed outside the environment allowlist')
    require(not any(s in document.toxml() for s in secrets(original)), 'An original secret survived environment replacement')
    return document, {'stepCount': len(steps), 'pluginTypes': sorted({text(s, 'type') for s in steps}),
                      'copies': [text(s, 'copies') for s in steps],
                      'processingSha256': [processing_digest(s) for s in steps],
                      'limit': int(text(consumer, 'LIMIT')), 'timeoutMs': int(text(consumer, 'TIMEOUT')),
                      'stopOnEmptyPresent': at(consumer, 'STOPONEMPTYTOPIC') is not None,
                      'autoOffsetReset': text(consumer, 'KAFKA/auto.offset.reset'),
                      'autoCommit': text(consumer, 'KAFKA/auto.commit.enable')}


def prepare_job(original, kind, config):
    document = original.cloneNode(True)
    trans_count = 0
    for entry in document.getElementsByTagName('entry'):
        if text(entry, 'type') == 'TRANS':
            trans_count += 1
            require(text(entry, 'cluster') != 'Y' and not text(entry, 'slave_server_name'), 'Remote original transformation execution is not permitted')
            child_id = config.get('childDefinitionId')
            put(entry, 'filename', '${INPUT_DIR}/' + (child_id + '.ktr' if child_id else 'illegal.ktr'))
            if child_id:
                entry.setAttribute('data-rynew-definition-id', child_id)
        if text(entry, 'type') == 'FTP_PUT':
            for path, value in {'servername': '127.0.0.1', 'serverport': '2121', 'username': config['ftpUser'],
                                'password': config['ftpPassword'], 'localDirectory': '${WORK_DIR}',
                                'remoteDirectory': '/' + config['slug'] + '/' + kind,
                                'proxy_host': '', 'proxy_port': '', 'proxy_username': '', 'proxy_password': '',
                                'socksproxy_host': '', 'socksproxy_port': '', 'socksproxy_username': '', 'socksproxy_password': ''}.items():
                put(entry, path, value)
    clear_original_secrets(document, {config['ftpPassword']})
    require(not any(s in document.toxml() for s in secrets(original)), 'An original job secret survived replacement')
    ftp = next(e for e in document.getElementsByTagName('entry') if text(e, 'type') == 'FTP_PUT')
    return document, {'entryCount': len(document.getElementsByTagName('entry')), 'transformationEntries': trans_count,
                      'apiChildBindingRequired': bool(trans_count and not config.get('childDefinitionId')),
                      'sameRunOutputAvailable': bool(trans_count),
                      'ftpExpectation': {key: text(ftp, key) for key in ['remoteDirectory', 'localDirectory', 'wildcard', 'remove', 'rename', 'binary']}}


def compose_ordinary_job(prepared_job, original_illegal_job):
    """Explicitly NEW fixture composition; never claim this was in the ordinary job."""
    document = prepared_job.cloneNode(True)
    root = document.documentElement
    put(root, 'name', '夹具新增-完整普通过车转换成功后上传')
    entries = at(root, 'entries')
    start = next(e for e in children(entries, 'entry') if text(e, 'type') == 'SPECIAL' and text(e, 'start') == 'Y')
    ftp = next(e for e in children(entries, 'entry') if text(e, 'type') == 'FTP_PUT')
    original_trans = next(e for e in original_illegal_job.getElementsByTagName('entry') if text(e, 'type') == 'TRANS')
    trans = document.importNode(original_trans, True)
    put(trans, 'name', '夹具-运行完整普通过车图')
    put(trans, 'filename', '${INPUT_DIR}/ordinary.ktr')
    entries.appendChild(trans)
    hops = at(root, 'hops')
    for child in list(hops.childNodes):
        hops.removeChild(child)
    for source, target, unconditional in [(start, trans, 'Y'), (trans, ftp, 'N')]:
        hop = document.createElement('hop')
        hops.appendChild(hop)
        for field, value in {'from': text(source, 'name'), 'to': text(target, 'name'), 'from_nr': '0', 'to_nr': '0',
                             'enabled': 'Y', 'evaluation': 'Y', 'unconditional': unconditional}.items():
            put(hop, field, value)
    return document


def cases(kind):
    common = [dict(id='accepted-sign-zero'), dict(id='accepted-sign-one', sign='1'),
              dict(id='secondary-drop', sign='2', route='drop'),
              dict(id='mapping-missing', crossing='missing-map'), dict(id='null-pictures', nullPictures=True)]
    if kind == 'ordinary':
        return common + [dict(id='redlist-one-drop', plate='TEST-RED-1', route='drop'),
                         dict(id='redlist-other-continues', plate='TEST-RED-9'),
                         dict(id='status-zero-continues', crossing='status-zero'),
                         dict(id='status-missing-continues', crossing='missing-status')]
    return common + [dict(id='at-cutoff-drop', passTime='2019-12-24T10:22:25+08:00', route='drop'),
                     dict(id='before-cutoff-drop', passTime='2019-12-24T10:22:24+08:00', route='drop'),
                     dict(id='invalid-date-drop', passTime='invalid-date', route='drop'),
                     dict(id='school-one-drop', camera='TEST-SCHOOL-1', route='drop'),
                     dict(id='school-other-continues', camera='TEST-SCHOOL-9'),
                     dict(id='whitelist-two-kafka', plate='TEST-WHITE-2', route='kafka'),
                     dict(id='whitelist-null-alarm-continues', plate='TEST-WHITE-NULL'),
                     dict(id='whitelist-other-continues', plate='TEST-WHITE-9'),
                     dict(id='status-zero-drop', crossing='status-zero', route='drop'),
                     dict(id='status-missing-drop', crossing='missing-status', route='drop')]


def message(kind, case, sequence):
    plate = case.get('plate', 'TEST-普通' if kind == 'ordinary' else 'TEST-违法')
    attrs = {'crossingIndexCode': case.get('crossing', 'accepted'), 'recognitionSign': case.get('sign', '0'),
             'platePicUrl': 'urn:rynew:fixture:plate', 'facePicUrl': 'urn:rynew:fixture:face',
             'cameraName': case.get('camera', 'TEST-CAMERA'), 'alarmType': 'TEST-ALARM',
             'passTime': case.get('passTime', '2019-12-24T10:22:26+08:00'),
             'crossingId': 'original-crossing', 'areaCode': 'original-region', 'regionIndexCode': 'original-region'}
    attrs.update({f'vehiclePicUrl{i}': f'urn:rynew:fixture:vehicle:{i}' for i in range(1, 7)})
    row = {'targetAttrs': attrs, 'targetPicUrl': 'urn:rynew:fixture:target',
           'target': [{'vehicle': {'plateNo': {'value': plate}}, 'targetSubUrl': 'urn:rynew:fixture:sub'}]}
    if case.get('nullPictures'):
        row['targetPicUrl'] = attrs['platePicUrl'] = attrs['facePicUrl'] = row['target'][0]['targetSubUrl'] = None
    return {'_fixture': {'id': f'{kind}:{case["id"]}:{sequence:05d}', 'case': case['id']},
            ('vehicleRcogResult' if kind == 'ordinary' else 'vehicleAlarmResult'): [row]}


def records_and_expectations(kind, limit):
    scenarios = cases(kind)
    require(limit >= len(scenarios), 'Original consumer limit cannot cover all fixtures')
    records, totals, counts = [], Counter(), Counter()
    for number in range(limit):
        case = scenarios[number] if number < len(scenarios) else scenarios[0]
        route = case.get('route', 'file')
        body = message(kind, case, number)
        records.append({'key': body['_fixture']['id'], 'caseId': case['id'], 'message': compact(body)})
        totals[route] += 1
        counts[case['id']] += 1
    expected = []
    for case in scenarios:
        crossing = case.get('crossing', 'accepted')
        expected.append({'caseId': case['id'], 'route': case.get('route', 'file'), 'count': counts[case['id']],
                         'crossingIndexCode': crossing if crossing == 'missing-status' else 'EXT-' + crossing,
                         'crossingId': '0' if crossing == 'missing-map' else 'LOCAL-' + crossing,
                         'region': 'original-region' if crossing == 'missing-map' else 'REGION-' + crossing,
                         'kafkaPayloadUnchanged': case.get('route') == 'kafka'})
    return records, {'recordCount': limit, 'routeCounts': dict(totals), 'cases': expected,
                     'assertionStatus': 'EXPECTED_NOT_EXECUTED', 'multiResultAndMatchingPrivateUrlRulesCovered': False}


def sql_literal(value):
    return 'NULL' if value is None else "'" + str(value).replace("'", "''") + "'"


def fixture_sql(config, platforms):
    tables = {'redlist': (['plateno', 'id'], [('TEST-RED-1', '1'), ('TEST-RED-9', '9')]),
              'qiuji': (['name', 'alarmtype', 'id'], [('TEST-SCHOOL-1', 'TEST-ALARM', '1'), ('TEST-SCHOOL-9', 'TEST-ALARM', '9')]),
              'whitelist': (['vehicleplate', 'alarmtype', 'id'], [('TEST-WHITE-2', 'TEST-ALARM', '2'), ('TEST-WHITE-NULL', None, '2'), ('TEST-WHITE-9', 'TEST-ALARM', '9')]),
              'xc_cross_csd_status': (['platform_index_code', 'cross_index_code', 'push_status', 'cross_external_code'], []),
              'xc_local_sync_cross': (['index_code', 'crossing_id', 'region_external_code'], [])}
    for platform in sorted(set(platforms)):
        for crossing in ['accepted', 'missing-map', 'status-zero']:
            tables['xc_cross_csd_status'][1].append((platform, crossing, '0' if crossing == 'status-zero' else '1', 'EXT-' + crossing))
    for crossing in ['accepted', 'status-zero', 'missing-status']:
        tables['xc_local_sync_cross'][1].append((crossing, 'LOCAL-' + crossing, 'REGION-' + crossing))
    lines = ['-- PREPARED ONLY. Run manually in a newly created isolated fixture database.', '\\set ON_ERROR_STOP on', 'BEGIN;',
             'DO $$ BEGIN IF current_database() <> ' + sql_literal(config['database']) + " THEN RAISE EXCEPTION 'Wrong fixture database'; END IF; END $$;"]
    for name, (columns, rows) in tables.items():
        lines.append('CREATE TABLE public.' + name + ' (' + ', '.join('"' + c + '" TEXT' for c in columns) + ');')
        for row in rows:
            lines.append('INSERT INTO public.' + name + ' VALUES (' + ', '.join(map(sql_literal, row)) + ');')
    lines.append('COMMIT;')
    return '\n'.join(lines) + '\n', {name: {'columns': columns, 'rows': len(rows)} for name, (columns, rows) in tables.items()}


def private_write(path, value):
    data = value if isinstance(value, bytes) else value.encode('utf-8')
    descriptor = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(descriptor, 'wb') as stream:
        stream.write(data)


def prepare(source_root, output, slug, pg_user='fixture_user', pg_password='${FIXTURE_PG_PASSWORD}',
            ftp_user='fixture_user', ftp_password='${FIXTURE_FTP_PASSWORD}', child_definition_id=None):
    require(re.fullmatch(r'[a-z][a-z0-9_]{2,31}', slug), 'Fixture slug must be 3-32 lowercase letters/digits/underscores')
    if child_definition_id:
        require(str(uuid.UUID(child_definition_id)) == child_definition_id, 'Child definition id must be a canonical UUID')
    source_root, output = Path(source_root).resolve(), Path(output).absolute()
    checkout = Path(__file__).resolve().parents[2]
    require(not output.resolve().is_relative_to(checkout), 'Generated original XML must stay outside the source checkout')
    require(not any((parent / '.git').exists() for parent in output.resolve().parents), 'Output cannot be inside any Git checkout')
    require(not output.exists(), 'Use a new private output directory; existing evidence is never overwritten')
    config = {'slug': slug, 'database': 'rynew_kettle_fixture_' + slug, 'pgUser': pg_user, 'pgPassword': pg_password,
              'ftpUser': ftp_user, 'ftpPassword': ftp_password, 'childDefinitionId': child_definition_id,
              'topics': {key: 'rynew-fixture-' + slug + '-' + key for key in ['ordinary', 'illegal', 'egress']},
              'groups': {key: 'rynew-fixture-' + slug + '-' + key for key in ['ordinary', 'illegal']}}
    original, sources = {}, {}
    for key in SOURCES:
        original[key], sources[key] = read_source(source_root, key)
    payloads, summaries, platforms = {}, {}, []
    for kind in ['ordinary', 'illegal']:
        document, summaries[kind] = prepare_transformation(original[kind], kind, config)
        payloads[kind + '.ktr'] = document.toxml(encoding='utf-8')
        platforms.append(platform_code(original[kind]))
        records, expected = records_and_expectations(kind, summaries[kind]['limit'])
        payloads[kind + '-messages.ndjson'] = ''.join(compact(record) + '\n' for record in records)
        payloads[kind + '-expected.json'] = json.dumps(expected, ensure_ascii=False, indent=2) + '\n'
        document, summaries[kind + '_job'] = prepare_job(original[kind + '_job'], kind, config)
        payloads[kind + '.kjb'] = document.toxml(encoding='utf-8')
        writer = next(s for s in children(parse(payloads[kind + '.ktr']).documentElement, 'step') if text(s, 'type') == 'TextFileOutput')
        basename = native_basename(text(writer, 'file/name'))
        wildcard = summaries[kind + '_job']['ftpExpectation']['wildcard']
        require(re.fullmatch(wildcard, basename + '.csv') is not None, 'Original output basename no longer matches its FTP wildcard')
        summaries[kind]['outputBasename'] = basename
        summaries[kind]['writer'] = {'header': text(writer, 'header'), 'separatorHex': '7c1f',
            'splitEvery': int(text(writer, 'file/splitevery')), 'maxWaitTimeMs': int(text(writer, 'file/max_wait_time_ms')),
            'fields': [text(f, 'name') for f in children(at(writer, 'fields'), 'field')]}
        if kind == 'ordinary':
            payloads['ordinary-composed-fixture.kjb'] = compose_ordinary_job(document, original['illegal_job']).toxml(encoding='utf-8')
    sql, table_summary = fixture_sql(config, platforms)
    payloads['lookup-fixture.sql'] = sql
    manifest = {'format': FORMAT, 'status': 'PREPARED_NOT_EXECUTED', 'sources': sources, 'graphs': summaries,
                'database': config['database'], 'topics': config['topics'], 'groups': config['groups'], 'tables': table_summary,
                'ordinaryComposition': {'file': 'ordinary-composed-fixture.kjb', 'addedByFixture': True,
                    'originalJobEntryCount': 2, 'composedEntryCount': 3, 'ftpGate': 'TRANS success only', 'apiChildBindingRequired': True},
                'offsetInitialState': {'performed': False, 'topicPartitionCount': 1, 'seedOffset': 0,
                    'ordinaryZookeeperPath': '/consumers/' + config['groups']['ordinary'] + '/offsets/' + config['topics']['ordinary'] + '/0',
                    'purpose': 'Deterministic fixture initial state; does not cover largest with a fresh group lacking offsets'},
                'network': {'postgres': '127.0.0.1:15432', 'kafka': '127.0.0.1:29092', 'zookeeper': '127.0.0.1:22181', 'ftp': '127.0.0.1:2121'},
                'credentialsBound': {'postgres': pg_password != '${FIXTURE_PG_PASSWORD}', 'ftp': ftp_password != '${FIXTURE_FTP_PASSWORD}'},
                'files': {name: {'sha256': sha(value), 'bytes': len(value if isinstance(value, bytes) else value.encode())} for name, value in payloads.items()},
                'environmentPatchAllowlist': ['connection name/server/port/PORT_NUMBER/database/username/password/tablespaces',
                    'DBLookup connection reference only', 'Kafka TOPIC, group.id, zookeeper.connect, metadata.broker.list only',
                    'TextFileOutput file/name directory only; basename retained to match original FTP wildcard',
                    'TRANS filename to INPUT_DIR and optional API definition-id binding',
                    'FTP local/remote directories, host/port/user/password and cleared proxy settings', 'Original secret-tag/attribute clearing'],
                'requiredBeforeExecution': ['Root/worker review', 'Dedicated empty database and three single-partition topics',
                    'Bind local fixture credentials (no original credentials)', 'Bind imported child definition UUID for platform Job API',
                    'Prime only the dedicated ordinary group offset 0, then preload exactly the original LIMIT records',
                    'Create dedicated FTP target directories; original FTP remove/rename flags remain enabled'],
                'notCovered': ['Original private URL rewrite matches', 'Multi-result target[i] shape', 'Production data equivalence',
                    'Ordinary standalone FTP job has no TRANS entry; separate runs do not share WORK_DIR']}
    output.mkdir(parents=True, mode=0o700)
    os.chmod(output, 0o700)
    for name, value in payloads.items():
        private_write(output / name, value)
    private_write(output / 'manifest.json', json.dumps(manifest, ensure_ascii=False, indent=2) + '\n')
    return manifest


def verify_outputs(prepared, kind, ftp_directory, egress_records=None):
    """Read a LOCAL copy/mount of the isolated FTP target plus captured Kafka records.

    This verifies data artifacts, not worker execution provenance or success gating.
    It never contacts FTP/Kafka and never emits row contents on failure.
    """
    require(kind in ('ordinary', 'illegal'), 'Expected ordinary or illegal fixture kind')
    prepared, ftp_directory = Path(prepared), Path(ftp_directory)
    manifest = json.loads((prepared / 'manifest.json').read_text())
    require(manifest['format'] == FORMAT, 'Unexpected fixture format')
    for name, info in manifest['files'].items():
        require(sha((prepared / name).read_bytes()) == info['sha256'], 'Prepared fixture evidence was changed')
    expected = json.loads((prepared / (kind + '-expected.json')).read_text())
    cases_by_id = {case['caseId']: case for case in expected['cases']}
    records = [json.loads(line) for line in (prepared / (kind + '-messages.ndjson')).read_text().splitlines()]
    inputs = {record['key']: record for record in records}
    want_file = {key for key, record in inputs.items() if cases_by_id[record['caseId']]['route'] == 'file'}
    want_kafka = {key for key, record in inputs.items() if cases_by_id[record['caseId']]['route'] == 'kafka'}
    require(ftp_directory.is_dir() and not ftp_directory.is_symlink(), 'Expected a local dedicated FTP target directory')
    files = sorted(ftp_directory.iterdir())
    wildcard = manifest['graphs'][kind + '_job']['ftpExpectation']['wildcard']
    require(files and all(path.is_file() and not path.is_symlink() and re.fullmatch(wildcard, path.name) for path in files),
            'FTP target contains no output or contains a non-output file (including input/config/temp files)')
    writer = manifest['graphs'][kind]['writer']
    delimiter = bytes.fromhex(writer['separatorHex'])
    capacity = writer['splitEvery'] - (1 if writer['header'] == 'Y' else 0)
    require(capacity > 0, 'Unsupported original writer rotation capacity')
    observed, file_rows, file_hashes = set(), [], []
    for path in files:
        require(path.stat().st_size <= 64 * 1024 * 1024, 'Fixture output file exceeds review limit')
        raw = path.read_bytes()
        lines = raw.split(b'\n')
        require(lines[-1] == b'', 'Output file does not end with a line terminator')
        lines = [line.removesuffix(b'\r') for line in lines[:-1]]
        if writer['header'] == 'Y':
            require(lines and lines[0].decode('utf-8').split(delimiter.decode()) == writer['fields'], 'Unexpected native file header')
            lines = lines[1:]
        require(0 < len(lines) <= capacity, 'Unexpected native header-inclusive file rotation')
        file_rows.append(len(lines))
        file_hashes.append(sha(raw))
        for line in lines:
            columns = line.decode('utf-8').split(delimiter.decode())
            require(len(columns) == 5, 'Unexpected output field count')
            body = json.loads(columns[0])
            key = body.get('_fixture', {}).get('id')
            require(key in want_file and key not in observed, 'Unexpected, duplicated or wrongly routed fixture record')
            observed.add(key)
            source = inputs[key]
            case = cases_by_id[source['caseId']]
            reference = json.loads(source['message'])
            root = 'vehicleRcogResult' if kind == 'ordinary' else 'vehicleAlarmResult'
            attrs = reference[root][0]['targetAttrs']
            attrs.update(crossingIndexCode=case['crossingIndexCode'], crossingId=case['crossingId'],
                         areaCode=case['region'], regionIndexCode=case['region'])
            require(body == reference, 'Native processed JSON differs from the prepared branch expectation')
            require(columns[1:3] == ['', ''], 'Native empty image-base64 fields changed')
            require(columns[3] == (reference[root][0]['targetPicUrl'] or '') and columns[4] == (attrs['platePicUrl'] or ''),
                    'Native picture output columns differ from message fields')
    require(observed == want_file, 'FTP output is missing expected file-branch records')
    if writer['maxWaitTimeMs'] == 0:
        require(len(files) == (len(want_file) + capacity - 1) // capacity, 'Unexpected native file count without timer rotation')
    seen_kafka = set()
    if want_kafka:
        require(egress_records is not None, 'An independent local capture of the dedicated Kafka egress topic is required')
        for line in Path(egress_records).read_text().splitlines():
            record = json.loads(line)
            message_value = record['message']
            key = json.loads(message_value).get('_fixture', {}).get('id')
            require(key in want_kafka and key not in seen_kafka, 'Unexpected or duplicated Kafka egress record')
            require(message_value == inputs[key]['message'], 'Whitelist Kafka branch changed the original payload bytes')
            seen_kafka.add(key)
        require(seen_kafka == want_kafka, 'Kafka egress is missing an expected whitelist record')
    return {'status': 'ARTIFACTS_VERIFIED', 'kind': kind, 'fileCount': len(files), 'fileRows': len(observed),
            'rowsPerFile': file_rows, 'fileSha256': file_hashes, 'kafkaRows': len(seen_kafka),
            'engineProvenanceAndFtpSuccessGateVerified': False}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source-root', default='/Volumes/KINGSTON/datai')
    parser.add_argument('--output', required=True)
    parser.add_argument('--slug')
    parser.add_argument('--pg-user', default='fixture_user')
    parser.add_argument('--pg-password-env', help='Optional environment variable containing a LOCAL fixture password')
    parser.add_argument('--ftp-user', default='fixture_user')
    parser.add_argument('--ftp-password-env', help='Optional environment variable containing a LOCAL fixture password')
    parser.add_argument('--child-definition-id')
    parser.add_argument('--verify-ftp-dir', help='Verify artifacts from this local dedicated FTP target; never connects')
    parser.add_argument('--kind', choices=['ordinary', 'illegal'])
    parser.add_argument('--egress-records', help='Independent captured Kafka NDJSON, each entry contains a message string')
    args = parser.parse_args()
    if args.verify_ftp_dir:
        print(json.dumps(verify_outputs(args.output, args.kind, args.verify_ftp_dir, args.egress_records), ensure_ascii=False))
        return
    require(args.slug is not None, '--slug is required when preparing fixtures')
    manifest = prepare(args.source_root, args.output, args.slug, args.pg_user,
                       os.environ[args.pg_password_env] if args.pg_password_env else '${FIXTURE_PG_PASSWORD}',
                       args.ftp_user, os.environ[args.ftp_password_env] if args.ftp_password_env else '${FIXTURE_FTP_PASSWORD}',
                       args.child_definition_id)
    print(json.dumps({'status': manifest['status'], 'graphs': {k: {f: v[f] for f in ['stepCount', 'entryCount'] if f in v} for k, v in manifest['graphs'].items()},
                      'fileCount': len(manifest['files']), 'tables': len(manifest['tables'])}, ensure_ascii=False))


if __name__ == '__main__':
    main()
