#!/usr/bin/env python3
"""Prepare private synthetic business fixtures; API mutations require --apply/--test.

Only the owned localhost:10443 HTTPS application API is used. No KTR is executed,
no original connection is opened, and private URL literals are never printed.
"""
import argparse
from collections import Counter
from copy import deepcopy
from datetime import datetime, timedelta
import hashlib
import json
import os
from pathlib import Path
import re
import ssl
import stat
import sys
import tempfile
import time
import urllib.error
import urllib.request
import uuid

FORMAT = 'RYNEW_SYNTHETIC_BUSINESS_FIXTURES_V1'
BASE = 'https://localhost:10443/prod-api'
TYPE = {'source': 'org.apache.nifi.processors.standard.GenerateFlowFile',
        'capture': 'org.apache.nifi.processors.attributes.UpdateAttribute',
        'record-transform': 'com.hm.governance.nifi.JsonRecordTransform',
        'lookup': 'com.hm.governance.nifi.JsonLookupSnapshot',
        'delimited': 'com.hm.governance.nifi.DelimitedTextWriter'}
OPS = {'parse', 'get', 'constant', 'copy', 'remove', 'trim', 'replace', 'substring',
       'set', 'broadcast', 'serialize', 'dateGate', 'filter'}
TERMINAL = {'SUCCEEDED', 'EMPTY', 'FAILED', 'CANCELLED', 'TIMED_OUT', 'UNSUPPORTED', 'CLEANUP_REQUIRED'}


def compact(value):
    return json.dumps(value, ensure_ascii=False, separators=(',', ':'), allow_nan=False)


def digest(value):
    return hashlib.sha256(value if isinstance(value, bytes) else compact(value).encode()).hexdigest()


def require(condition, message):
    if not condition:
        raise RuntimeError(message)


def read_private(path, private):
    path = Path(path)
    require(not path.is_symlink() and path.is_file(), 'Private input must be a regular file')
    require(path.resolve().is_relative_to(private.resolve()), 'Private input must be inside the owned application private directory')
    require(stat.S_IMODE(path.stat().st_mode) == 0o600, 'Private input permissions must be 600')
    require(path.stat().st_size <= 4 * 1024 * 1024, 'Private input exceeds size limit')
    return json.loads(path.read_text())


def save_private(path, value):
    path = Path(path)
    require(not path.parent.is_symlink(), 'Private output directory cannot be a link')
    path.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
    os.chmod(path.parent, 0o700)
    require(not path.is_symlink(), 'Private output cannot be a link')
    temporary = path.with_name('.' + path.name + '.' + uuid.uuid4().hex + '.tmp')
    fd = os.open(temporary, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    try:
        with os.fdopen(fd, 'wb') as stream:
            stream.write((json.dumps(value, ensure_ascii=False, indent=2, allow_nan=False) + '\n').encode())
            stream.flush()
            os.fsync(stream.fileno())
        os.replace(temporary, path)
    finally:
        if temporary.exists():
            temporary.unlink()


def xml_value(node):
    if not node.get('children'):
        return node.get('text', '')
    result = {}
    for child in node['children']:
        value = xml_value(child)
        if child['tag'] in result:
            old = result[child['tag']]
            result[child['tag']] = (old if isinstance(old, list) else [old]) + [value]
        else:
            result[child['tag']] = value
    return result


def configuration(step):
    return {node['tag']: xml_value(node) for node in step['configuration']}


def items(value):
    return value if isinstance(value, list) else [value]


def pointer(json_path):
    require(isinstance(json_path, str) and json_path.startswith('$.'), 'Source JSONPath form is unsupported')
    tokens, offset = [], 1
    pattern = re.compile(r'\.([A-Za-z_$][A-Za-z0-9_$]*)|\[(0|[1-9][0-9]*)\]')
    for match in pattern.finditer(json_path, 1):
        require(match.start() == offset, 'Source JSONPath has unsupported syntax')
        tokens.append(match.group(1) if match.group(1) is not None else match.group(2))
        offset = match.end()
    require(offset == len(json_path) and 0 < len(tokens) <= 32, 'Source JSONPath could not be translated exactly')
    return '/' + '/'.join(token.replace('~', '~0').replace('/', '~1') for token in tokens)


def tokens(path):
    require(isinstance(path, str) and path.startswith('/'), 'JSON Pointer required')
    return [value.replace('~1', '/').replace('~0', '~') for value in path[1:].split('/')]


def get(root, path):
    current = root
    for token in tokens(path):
        if isinstance(current, dict):
            current = current.get(token)
        elif isinstance(current, list) and token.isdigit() and int(token) < len(current):
            current = current[int(token)]
        else:
            return None
    return current


def seed(root, path, value):
    parts = tokens(path)
    current = root
    for index, token in enumerate(parts):
        last = index == len(parts) - 1
        if isinstance(current, list):
            require(token.isdigit() and int(token) <= 16, 'Synthetic array seed exceeds limit')
            while len(current) <= int(token):
                current.append(None)
            if last:
                current[int(token)] = value
                return
            if current[int(token)] is None:
                current[int(token)] = [] if parts[index + 1].isdigit() else {}
            current = current[int(token)]
        else:
            require(isinstance(current, dict), 'Synthetic seed parent is not a container')
            if last:
                current[token] = value
                return
            current = current.setdefault(token, [] if parts[index + 1].isdigit() else {})


def single(steps, kind):
    found = [step for step in steps if step['type'] == kind]
    require(len(found) == 1, 'Expected one original component of the required kind')
    return found[0]


def filter_rule(field, value, keep=True):
    return {'op': 'filter', 'input': '/' + field, 'operator': 'EQ', 'value': value, 'keep': keep}


def string_field(field):
    return {'op': 'get', 'path': '/' + field, 'output': field, 'type': 'KETTLE_STRING', 'trim': 'NONE', 'missing': 'NULL'}


class FixtureBuilder:
    def __init__(self, business, source, operations):
        self.business = business
        self.normal = business == 'normal'
        self.steps = source['transformations'][0]['steps']
        self.source = source
        self.source_config = configuration(single(self.steps, 'KafkaConsumer'))
        self.message = self.source_config['FIELD']
        self.key = self.source_config['KEY_FIELD']
        self.fields = single(self.steps, 'JsonInput')['jsonFields']
        require(len(self.fields) == (12 if self.normal else 15), 'Original JSON field count changed')
        self.paths = {field['name']: pointer(field['path']) for field in self.fields}
        require(len(self.paths) == len(self.fields), 'Duplicate original JSON output field')
        self.result_array = '/' + tokens(next(iter(self.paths.values())))[0]
        self.output = single(self.steps, 'TextFileOutput')
        self.output_fields = [field['name'] for field in self.output['outputFields']]
        require(len(self.output_fields) == 5, 'Original wire field count changed')
        require(len(set(self.output_fields)) == 5 and all(',' not in field and not any(ord(char) < 32 for char in field) for field in self.output_fields), 'Original wire field names are unsupported')
        self.rewrite = deepcopy(operations[business + 'Rewrite'])
        self.date = deepcopy(operations['violationDate'])
        self.trim = deepcopy(operations['textOutputTrim'])
        self.lookups = {step['name']: step for step in self.steps if step['type'] == 'DBLookup'}
        self.routes = {step['name']: configuration(step) for step in self.steps if step['type'] == 'SwitchCase'}
        sign_steps = [config for config in self.routes.values() if {str(case['value']) for case in items(config['cases']['case'])} == {'0', '1'}]
        require(len(sign_steps) == 1, 'Original recognition route changed')
        self.sign = sign_steps[0]['fieldname']
        self.base64_fields = self.output_fields[1:3]
        constants = [configuration(step)['fields']['field'] for step in self.steps if step['type'] == 'Constant']
        platforms = [field['name'] for value in constants for field in items(value) if field['name'] not in self.base64_fields]
        require(len(platforms) == 1, 'Original platform constant mapping changed')
        self.platform = platforms[0]
        self.platform_value = 'SYNTHETIC_PLATFORM'
        self.mapping_name = next(name for name, step in self.lookups.items() if len(items(configuration(step)['lookup']['value'])) == 2 and len(items(configuration(step)['lookup']['key'])) == 2)
        self.cross_name = next(name for name, step in self.lookups.items() if len(items(configuration(step)['lookup']['value'])) == 2 and name != self.mapping_name)
        others = [name for name in self.lookups if name not in {self.mapping_name, self.cross_name}]
        if self.normal:
            require(len(others) == 1, 'Original normal rule lookup count changed')
            self.black_name = others[0]
        else:
            self.white_name = next(name for name in others if any(key['condition'] == 'IS NOT NULL' for key in items(configuration(self.lookups[name])['lookup']['key'])))
            self.camera_name = next(name for name in others if name != self.white_name)
        self.nodes, self.edges = [], []

    def lookup_input(self, name, position=0):
        keys = [key for key in items(configuration(self.lookups[name])['lookup']['key']) if key['condition'] == '=']
        return keys[position]['name']

    def lookup_output(self, name, position=0):
        return items(configuration(self.lookups[name])['lookup']['value'])[position]['rename']

    def cases(self):
        labels = (['file-standard', 'drop-recognition', 'drop-blacklist-id1', 'file-blacklist-id2', 'file-push-status0', 'file-mapping-missing', 'file-crossing-default', 'file-array-broadcast-whitespace'] if self.normal else
                  ['file-standard', 'drop-time-equal', 'drop-invalid-time', 'drop-camera-id1', 'simulated-kafka-id2', 'drop-recognition', 'drop-push-status0', 'file-crossing-default'])
        prefix = 'N' if self.normal else 'V'
        gate = next(rule for rule in self.date if rule['op'] == 'dateGate')
        threshold = datetime.strptime(gate['threshold'], '%Y/%m/%d %H:%M:%S')
        date_input = next(rule['input'][1:] for rule in self.date if rule['op'] == 'copy')
        offset = next(rule['find'] for rule in self.date if rule['op'] == 'replace' and re.fullmatch(r'[+-][0-9]{2}:[0-9]{2}', rule['find']))
        good_time = (threshold + timedelta(days=1)).strftime('%Y-%m-%dT%H:%M:%S') + offset
        plate = self.lookup_input(self.black_name if self.normal else self.white_name)
        crossing = self.lookup_input(self.cross_name)
        cases = []
        for number, label in enumerate(labels, 1):
            case_id = prefix + str(number).zfill(2)
            values = {field: 'SYNTHETIC_' + field for field in self.paths}
            values[self.sign] = '2' if label == 'drop-recognition' else str(number % 2)
            values[plate] = 'SYNTHETIC_VEHICLE_' + case_id
            values[crossing] = 'SYNTHETIC_CROSSING_' + case_id
            for field in self.paths:
                replacements = [rule for rule in self.rewrite if rule['op'] == 'replace' and rule['input'] == '/' + field]
                if replacements:
                    values[field] = 'http://' + replacements[0]['find'] + '/synthetic/' + case_id + '/' + field + '.jpg'
                elif 'PicUrl' in field or field.endswith('SubUrl'):
                    values[field] = 'https://fixture.invalid/synthetic/' + case_id + '/' + field + '.jpg'
            if not self.normal:
                values[self.lookup_input(self.camera_name)] = 'SYNTHETIC_CAMERA_' + case_id
                values[self.lookup_input(self.camera_name, 1)] = 'SYNTHETIC_ALARM'
                values[date_input] = threshold.strftime('%Y-%m-%dT%H:%M:%S') if label == 'drop-time-equal' else 'invalid-date' if label == 'drop-invalid-time' else good_time
            if label == 'file-array-broadcast-whitespace':
                for field in self.output_fields[-2:]:
                    values[field] = '  ' + values[field] + '  '
            document = {}
            for field, path in self.paths.items():
                seed(document, path, values[field])
            document['_fixture'] = {'id': case_id, 'synthetic': True}
            if label == 'file-array-broadcast-whitespace':
                array = get(document, self.result_array)
                array.append(deepcopy(array[0]))
                seed(array[1], '/targetAttrs/crossingIndexCode', 'SYNTHETIC_SECOND_RESULT')
            record = {self.message: compact(document), self.key: 'SYNTHETIC_KEY_' + case_id, 'fixtureCase': case_id, 'fixtureArrivalMillis': (number - 1) * 100}
            disposition = 'file' if label.startswith('file-') else 'simulated-kafka' if label.startswith('simulated-') else 'filtered'
            cases.append({'id': case_id, 'scenario': label, 'record': record, 'document': document, 'values': values, 'expectedDisposition': disposition})
        return cases

    def lookup_properties(self, name, cases):
        meta = configuration(self.lookups[name])['lookup']
        keys, values = items(meta['key']), items(meta['value'])
        require(all(key['condition'] in {'=', 'IS NOT NULL'} for key in keys), 'Original lookup operator is unsupported')
        require(all(value['type'] == 'String' and value['rename'] for value in values), 'Original lookup return schema changed')
        rows = []
        for case in cases:
            label = case['scenario']
            include = (name in {self.mapping_name, self.cross_name})
            if self.normal and name == self.black_name:
                include = label in {'drop-blacklist-id1', 'file-blacklist-id2'}
            if not self.normal and name == self.camera_name:
                include = label == 'drop-camera-id1'
            if not self.normal and name == self.white_name:
                include = label == 'simulated-kafka-id2'
            if name == self.mapping_name and label == 'file-mapping-missing':
                include = False
            if name == self.cross_name and label == 'file-crossing-default':
                include = False
            if not include:
                continue
            row = {key['field']: ('SYNTHETIC_PRESENT' if key['condition'] == 'IS NOT NULL' else self.platform_value if key['name'] == self.platform else case['values'][key['name']]) for key in keys}
            for index, value in enumerate(values):
                if name == self.mapping_name:
                    mapped = ('0' if 'status0' in label else '1') if index == 0 else 'SYNTHETIC_EXTERNAL_' + case['id']
                elif name == self.cross_name:
                    mapped = ('SYNTHETIC_RECORD_' if index == 0 else 'SYNTHETIC_REGION_') + case['id']
                else:
                    mapped = '2' if label in {'file-blacklist-id2', 'simulated-kafka-id2'} else '1'
                row[value['name']] = mapped
            rows.append(row)
        matches = [{'lookup': key['field'], 'type': 'STRING', 'operator': 'IS_NOT_NULL' if key['condition'] == 'IS NOT NULL' else 'EQ', **({'input': '/' + key['name']} if key['condition'] == '=' else {})} for key in keys]
        returns = [{'lookup': value['name'], 'output': value['rename'], 'default': value.get('default', '')} for value in values]
        return {'Lookup Rows': compact(rows), 'Match Fields': compact(matches), 'Return Fields': compact(returns), 'Missing Match': 'KEEP', 'Multiple Matches': 'FAIL'}

    def node(self, key, title, kind, properties, source_steps=()):
        index = len(self.nodes)
        self.nodes.append({'key': key, 'kind': kind, 'name': title, 'type': TYPE[kind], 'role': 'CAPTURE' if kind == 'capture' else 'PROCESSOR',
                           'position': {'x': 80 + (index % 6) * 230, 'y': 80 + (index // 6) * 250}, 'properties': properties, 'sourceSteps': list(source_steps)})

    def transform(self, key, title, rules, source_steps=()):
        self.node(key, title, 'record-transform', {'Operations': compact(rules)}, source_steps)

    def business_lookup(self, key, title, name, cases):
        self.node(key, title, 'lookup', self.lookup_properties(name, cases), [name])

    def connect(self, source, target, relationship='success'):
        self.edges.append({'source': source, 'target': target, 'relationship': relationship})

    def build(self):
        cases = self.cases()
        input_json = compact([case['record'] for case in cases])
        self.node('source', '合成批次输入（8 条）', 'source', {'Custom Text': input_json, 'Batch Size': '1', 'Data Format': 'Text', 'Unique FlowFiles': 'false'}, ['etl_source'])
        extract = [{'op': 'filter', 'input': '/' + self.message, 'operator': 'IS_NOT_NULL', 'keep': True},
                   {'op': 'parse', 'input': '/' + self.message, 'document': 'source_document', 'numberMode': 'KETTLE_JSON', 'onEmpty': 'NULL'}]
        extract += [{'op': 'get', 'document': 'source_document', 'path': path, 'output': field, 'type': 'KETTLE_STRING', 'trim': 'NONE', 'missing': 'NULL'} for field, path in self.paths.items()]
        if not self.normal:
            extract += self.date + [filter_rule(next(rule['output'] for rule in self.date if rule['op'] == 'dateGate'), True)]
        self.transform('extract', '原字段提取' if self.normal else '原字段提取与日期过滤', extract, [single(self.steps, 'JsonInput')['name']])
        allowed_rows = [{'allowed_sign': sign, 'fixture_platform': self.platform_value} for sign in ['0', '1']]
        allowed_properties = {'Lookup Rows': compact(allowed_rows), 'Match Fields': compact([{'input': '/' + self.sign, 'lookup': 'allowed_sign', 'type': 'STRING', 'operator': 'EQ'}]),
                              'Return Fields': compact([{'lookup': 'allowed_sign', 'output': self.sign}, {'lookup': 'fixture_platform', 'output': self.platform}]), 'Missing Match': 'DROP', 'Multiple Matches': 'FAIL'}
        if self.normal:
            self.node('sign', '识别标志 0 / 1 与平台常量', 'lookup', allowed_properties, ['recognitionSignFilter', 'addConstant'])
            self.business_lookup('black', '车辆规则字典', self.black_name, cases)
            field = self.lookup_output(self.black_name)
            self.transform('black-filter', '仅排除规则 id = 1', [string_field(field), filter_rule(field, '1', False)], ['Switch / Case 2'])
            self.business_lookup('mapping', '平台与卡口映射（均放行）', self.mapping_name, cases)
            self.business_lookup('crossing', '卡口与区域字典', self.cross_name, cases)
            chain = ['source', 'extract', 'sign', 'black', 'black-filter', 'mapping', 'crossing', 'rewrite', 'writer', 'capture']
        else:
            self.business_lookup('camera', '摄像机与告警规则字典', self.camera_name, cases)
            field = self.lookup_output(self.camera_name)
            self.transform('camera-filter', '仅排除摄像机规则 id = 1', [string_field(field), filter_rule(field, '1', False)], ['Switch / Case 2'])
            self.business_lookup('whitelist', '车辆白名单字典', self.white_name, cases)
            field = self.lookup_output(self.white_name)
            self.transform('kafka-sample', '白名单：模拟 Kafka 待发送', [string_field(field), filter_rule(field, '2'), {'op': 'constant', 'output': 'fixtureDelivery', 'value': 'SIMULATED_KAFKA_PENDING'}], ['Switch / Case 3', 'Apache Kafka Producer'])
            self.transform('not-whitelist', '非白名单进入文件支路', [string_field(field), filter_rule(field, '2', False)], ['Switch / Case 3'])
            self.node('sign', '识别标志 0 / 1 与平台常量', 'lookup', allowed_properties, ['recognitionSignFilter', 'addConstant'])
            self.business_lookup('mapping', '平台与卡口映射', self.mapping_name, cases)
            field = self.lookup_output(self.mapping_name)
            self.transform('push-filter', '仅允许推送状态 = 1', [string_field(field), filter_rule(field, '1')], ['Switch / Case'])
            self.business_lookup('crossing', '卡口与区域字典', self.cross_name, cases)
            chain = ['source', 'extract', 'camera', 'camera-filter', 'whitelist', 'not-whitelist', 'sign', 'mapping', 'push-filter', 'crossing', 'rewrite', 'writer', 'capture']
            self.connect('whitelist', 'kafka-sample')
            self.connect('kafka-sample', 'capture')
        returned = [value['rename'] for name in [self.mapping_name, self.cross_name] for value in items(configuration(self.lookups[name])['lookup']['value'])]
        rewrite = [string_field(field) for field in returned] + self.rewrite
        rewrite += [{'op': 'constant', 'output': field, 'value': ''} for field in self.base64_fields] + self.trim
        self.transform('rewrite', '消息改写、空图片列与输出裁剪', rewrite, ['JSReplaceJson', 'downPic_targetPicUrl_e', 'downPic_platePicUrl_e'])
        file_config = configuration(self.output)
        require(file_config['separator'] == '|$[1F]' and file_config['encoding'] == 'UTF-8' and file_config['format'] == 'UNIX', 'Original wire protocol changed')
        self.node('writer', '五列协议文本（合成文件名）', 'delimited', {'Field Order': ','.join(self.output_fields), 'Delimiter Hex': '7C 1F', 'Include Header': 'true',
                  'Split Limit': file_config['file']['splitevery'], 'Count Basis': 'KETTLE_HEADER_INCLUSIVE', 'Maximum File Age Millis': file_config['file']['max_wait_time_ms'],
                  'Arrival Time Field': 'fixtureArrivalMillis', 'Filename Prefix': 'synthetic-' + self.business}, [self.output['name']])
        self.node('capture', '完整产物与分支观察', 'capture', {})
        for source, target in zip(chain, chain[1:]):
            self.connect(source, target)
        for node in self.nodes:
            if node['kind'] not in {'source', 'capture'}:
                self.connect(node['key'], 'capture', 'empty')
                self.connect(node['key'], 'capture', 'failure')
        title = '普通过车' if self.normal else '违法告警'
        expected = {'fileCaseIds': [case['id'] for case in cases if case['expectedDisposition'] == 'file'], 'simulatedKafkaCaseIds': [case['id'] for case in cases if case['expectedDisposition'] == 'simulated-kafka'],
                    'filteredCaseIds': [case['id'] for case in cases if case['expectedDisposition'] == 'filtered'], 'fileFields': self.output_fields, 'delimiterHex': '7c1f', 'includeHeader': True,
                    'countBasis': 'KETTLE_HEADER_INCLUSIVE', 'splitLimit': int(file_config['file']['splitevery']), 'expectedPrimaryFileCount': 1, 'sourceMessageField': self.message,
                    'simulatedKafkaSourceHashes': {case['id']: digest(case['record'][self.message].encode()) for case in cases if case['expectedDisposition'] == 'simulated-kafka'}}
        expected['caseAssertions'] = self.expected_documents(cases)
        return {'key': self.business, 'name': title + ' · 原任务兼容合成验证', 'sourceMembers': self.source['sources'], 'nodes': self.nodes, 'edges': self.edges, 'inputJson': input_json,
                'cases': cases, 'expectations': expected, 'originalJsonFields': self.fields, 'originalFileConfiguration': file_config['file'],
                'scope': ['Synthetic messages and dictionary rows only; no actual business sample was supplied.', 'Kafka output is captured pending-send data, not a producer call; FTP is not invoked.',
                          'All seven business lookups remain independent; synthetic dictionaries have unique keys and use explicit FAIL for ambiguous matches.',
                          'Raw XML return defaults are retained literally; complete original database/cache/duplicate-selection behavior is not certified.',
                          'Original source JsonInput behavior uses KETTLE_STRING, missing=NULL, and no input trimming, based on direct original-class oracle.',
                          ('RHINO_DATE uses the verified native parser and the explicit engine timezone; extra date boundaries are covered by the separate native oracle.' if any(rule.get('parser') == 'RHINO_DATE' for rule in self.date) else 'The valid full date and invalid-text range is tested; strict parsing differs for overflow and incomplete dates.'),
                          'Header-inclusive row limits are configured; eight records do not exercise the 200-line boundary.',
                          'Filename prefix and arrival timestamps are synthetic. Original per-file clock naming and live stream arrival/lifecycle equivalence are not claimed; original max-wait value is retained with explicit fixture arrival timestamps.']}

    def expected_documents(self, cases):
        """Expected mappings come from the source lookup metadata and private reviewed literal rules."""
        assertions = {}
        for case in cases:
            if case['expectedDisposition'] != 'file':
                continue
            row = {**case['values'], self.platform: self.platform_value}
            for name in [self.mapping_name, self.cross_name]:
                config = self.lookup_properties(name, cases)
                matches = json.loads(config['Match Fields'])
                selected = [value for value in json.loads(config['Lookup Rows']) if all(row.get(match['input'][1:]) == value.get(match['lookup']) for match in matches)]
                require(len(selected) <= 1, 'Synthetic lookup unexpectedly ambiguous')
                for field in json.loads(config['Return Fields']):
                    row[field['output']] = selected[0].get(field['lookup']) if selected else field.get('default')
            for operation in self.rewrite:
                if operation['op'] == 'replace':
                    value = row.get(operation['input'][1:])
                    row[operation['output']] = value.replace(operation['find'], operation['replacement']) if value is not None else None
            writes = [{'array': operation['array'], 'path': operation['path'], 'outerIndex': operation.get('outerIndex', False), 'value': row.get(operation['input'][1:])}
                      for operation in self.rewrite if operation['op'] == 'broadcast' and (not operation.get('ifNotNull') or row.get(operation['input'][1:]) is not None)]
            changed_first = {write['array'] + '/0' + write['path'].replace('$index', '0') for write in writes}
            unchanged = {path: get(case['document'], path) for path in self.paths.values() if path not in changed_first}
            assertions[case['id']] = {'writes': writes, 'unchangedFirstResultPaths': unchanged, 'resultCount': len(get(case['document'], self.result_array))}
        return assertions


def validate_spec(spec):
    require(spec.get('format') == FORMAT and len(spec['flows']) == 2, 'Unexpected fixture format')
    for flow in spec['flows']:
        nodes = {node['key']: node for node in flow['nodes']}
        require(len(nodes) == len(flow['nodes']) and 2 <= len(nodes) <= 32 and len(flow['edges']) <= 96, 'Safe graph size or node identity violation')
        require(sum(node['kind'] == 'source' for node in nodes.values()) == 1 and sum(node['kind'] == 'capture' for node in nodes.values()) == 1, 'Exactly one source and capture required')
        rows = json.loads(flow['inputJson'])
        require(isinstance(rows, list) and 1 <= len(rows) <= 100 and all(isinstance(row, dict) for row in rows) and len(flow['inputJson'].encode()) <= 256 * 1024, 'Sample batch exceeds API limits')
        require(len(flow['cases']) == 8 and len({case['id'] for case in flow['cases']}) == 8, 'Eight distinct synthetic cases required')
        indegree = {key: 0 for key in nodes}
        for edge in flow['edges']:
            require(edge['source'] in nodes and edge['target'] in nodes and edge['source'] != edge['target'], 'Graph member mismatch')
            require(edge['source'] != 'capture' and edge['target'] != 'source' and edge['relationship'] in {'success', 'empty', 'failure'}, 'Unsafe edge')
            indegree[edge['target']] += 1
        ready = [key for key, count in indegree.items() if count == 0]
        require(ready == ['source'], 'Graph must have one source')
        for key in ready:
            for edge in flow['edges']:
                if edge['source'] == key:
                    indegree[edge['target']] -= 1
                    if indegree[edge['target']] == 0:
                        ready.append(edge['target'])
        require(len(ready) == len(nodes), 'Graph contains a cycle')
        require(len({(edge['source'], edge['target'], edge['relationship']) for edge in flow['edges']}) == len(flow['edges']), 'Duplicate edge')
        for node in nodes.values():
            require(node['type'] == TYPE[node['kind']], 'Unapproved processor type')
            if node['kind'] not in {'source', 'capture'}:
                for relation in ['empty', 'failure']:
                    require({'source': node['key'], 'target': 'capture', 'relationship': relation} in flow['edges'], 'Every empty/failure must be observed')
            if node['kind'] == 'record-transform':
                rules = json.loads(node['properties']['Operations'])
                require(1 <= len(rules) <= 64 and len(node['properties']['Operations'].encode()) <= 32768, 'Operation limits exceeded')
                require(all(isinstance(rule, dict) and rule.get('op') in OPS for rule in rules), 'Unapproved operation')
            if node['kind'] == 'lookup':
                require(len(json.loads(node['properties']['Lookup Rows'])) <= 1000, 'Dictionary size limit exceeded')
                require(all(len(value.encode()) <= 65536 for value in node['properties'].values()), 'Dictionary property byte limit exceeded')
        expected = flow['expectations']
        expected_ids = expected['fileCaseIds'] + expected['simulatedKafkaCaseIds'] + expected['filteredCaseIds']
        require(sorted(expected_ids) == sorted(case['id'] for case in flow['cases']), 'Every synthetic case needs exactly one expected branch')
    return spec


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, request, fp, code, msg, headers, newurl):
        raise RuntimeError('Local API redirect refused')


class LocalApi:
    def __init__(self, runtime, app):
        context = ssl.create_default_context(cafile=str(runtime / 'private/gateway-ca.pem'))
        self.opener = urllib.request.build_opener(urllib.request.ProxyHandler({}), NoRedirect(), urllib.request.HTTPSHandler(context=context))
        self.token = None
        login = read_private(app / 'private/app-login.json', app / 'private')
        self.token = self.call('/login', 'POST', {**login, 'code': '', 'uuid': ''})['token']

    def call(self, path, method='GET', body=None, binary=False):
        require(path.startswith('/governance/') or path == '/login', 'Unapproved local API path')
        require('..' not in path and '://' not in path and '#' not in path and '\\' not in path, 'Unsafe API path')
        headers = {'Content-Type': 'application/json'}
        if self.token:
            headers['Authorization'] = 'Bearer ' + self.token
        request = urllib.request.Request(BASE + path, data=compact(body).encode() if body is not None else None, method=method, headers=headers)
        try:
            with self.opener.open(request, timeout=30) as response:
                content = response.read(12 * 1024 * 1024 + 1)
                require(len(content) <= 12 * 1024 * 1024, 'Local API response exceeded bound')
        except urllib.error.HTTPError as error:
            raise RuntimeError('Local API HTTP ' + str(error.code)) from None
        except (urllib.error.URLError, TimeoutError, OSError):
            raise RuntimeError('Local API transport failed; mutation result may require inspection') from None
        if binary:
            return content
        value = json.loads(content)
        require(value.get('code') == 200, 'Local application rejected the request')
        return value


def checked_id(value):
    require(isinstance(value, str) and str(uuid.UUID(value)) == value, 'Unexpected application identifier')
    return value


def apply_spec(api, spec, path, app):
    fingerprint = digest(spec)
    state = read_private(path, app / 'private') if path.exists() else {'format': FORMAT, 'specHash': fingerprint, 'projectId': None, 'flows': {}}
    require(state['format'] == FORMAT and state['specHash'] == fingerprint, 'Binding provenance differs from this fixture')
    require(not state.get('mutationPending'), 'An earlier mutation outcome is uncertain; inspect owned local state before retry')
    def mutate(action, endpoint, payload):
        state['mutationPending'] = action
        save_private(path, state)
        result = api.call(endpoint, 'POST', payload)['data']
        state.pop('mutationPending', None)
        return result
    registry = api.call('/governance/design/node-types')['data']
    available = {(node['type'], node['role']) for node in registry}
    require(all((node['type'], node['role']) in available for flow in spec['flows'] for node in flow['nodes']), 'Required safe processor types are not registered')
    if not state['projectId']:
        project = mutate('create-project', '/governance/projects', {'name': '原任务兼容合成验证 ' + fingerprint[:8], 'description': FORMAT + ':' + fingerprint})
        state['projectId'] = checked_id(project['id'])
        save_private(path, state)
    for flow in spec['flows']:
        binding = state['flows'].setdefault(flow['key'], {'id': None, 'nodes': {}, 'edges': []})
        if not binding['id']:
            created = mutate('create-flow-' + flow['key'], '/governance/flows', {'projectId': state['projectId'], 'name': flow['name'], 'templateId': 'blank'})
            binding['id'] = checked_id(created['id'])
            save_private(path, state)
        base = '/governance/flows/' + checked_id(binding['id']) + '/design'
        current = api.call(base)['data']
        require(current['editable'], 'Owned fixture flow is not editable')
        known_ids = set(binding['nodes'].values())
        require({node['id'] for node in current['nodes']} == known_ids, 'Owned flow changed outside this fixture builder')
        def edge_signatures(design):
            reverse = {value: key for key, value in binding['nodes'].items()}
            result = []
            for edge in design['connections']:
                require(edge['sourceId'] in reverse and edge['targetId'] in reverse and len(edge['relationships']) == 1, 'Owned connection membership changed')
                result.append('|'.join([reverse[edge['sourceId']], edge['relationships'][0], reverse[edge['targetId']]]))
            return sorted(result)
        require(edge_signatures(current) == sorted(binding['edges']), 'Owned fixture connections were edited; refusing to overwrite')
        for node in flow['nodes']:
            if node['key'] in binding['nodes']:
                saved = next(value for value in current['nodes'] if value['id'] == binding['nodes'][node['key']])
                require(saved['type'] == node['type'] and saved['name'] == node['name'] and all(saved['properties'].get(key) == value for key, value in node['properties'].items()), 'Owned node configuration was edited; refusing to overwrite')
                continue
            payload = {key: node[key] for key in ['type', 'name', 'role', 'position', 'properties']}
            created = mutate('create-node-' + flow['key'] + '-' + node['key'], base + '/nodes', payload)
            binding['nodes'][node['key']] = checked_id(created['id'])
            save_private(path, state)
        for edge in flow['edges']:
            signature = '|'.join([edge['source'], edge['relationship'], edge['target']])
            if signature in binding['edges']:
                continue
            mutate('create-edge-' + flow['key'] + '-' + signature, base + '/connections', {'sourceId': binding['nodes'][edge['source']], 'targetId': binding['nodes'][edge['target']], 'relationship': edge['relationship']})
            binding['edges'].append(signature)
            save_private(path, state)
        final = api.call(base)['data']
        require(len(final['nodes']) == len(flow['nodes']) and len(final['connections']) == len(flow['edges']), 'Fixture graph count mismatch')
        require(edge_signatures(final) == sorted(binding['edges']), 'Final fixture wiring differs from the private specification')
    save_private(path, state)
    return state


def assert_outputs(flow, artifacts):
    expected = flow['expectations']
    delimiter = bytes.fromhex(expected['delimiterHex']).decode('ascii')
    header = delimiter.join(expected['fileFields'])
    file_ids, kafka_ids, file_count = [], [], 0
    original = {case['id']: case for case in flow['cases']}
    for content in artifacts:
        text = content.decode('utf-8', errors='strict')
        if text.startswith(header + '\n'):
            file_count += 1
            require(text.endswith('\n'), 'Wire file final newline missing')
            lines = text.split('\n')[1:-1]
            require(len(lines) <= expected['splitLimit'] - 1, 'Header-inclusive split limit exceeded')
            for line in lines:
                columns = line.split(delimiter)
                require(len(columns) == 5 and columns[1:3] == ['', ''], 'Wire field count or empty picture columns changed')
                document = json.loads(columns[0])
                case_id = document['_fixture']['id']
                require(case_id in expected['fileCaseIds'], 'Unexpected record reached file output')
                file_ids.append(case_id)
                for field, column in zip(expected['fileFields'][-2:], columns[-2:]):
                    path = next(pointer(item['path']) for item in flow['originalJsonFields'] if item['name'] == field)
                    value = get(document, path)
                    require(column == ('' if value is None else value.strip(' \t\r\n')), 'Wire URL columns differ from rewritten message fields')
                source = original[case_id]['document']
                require(document['_fixture'] == source['_fixture'], 'Synthetic source identity was not preserved')
                assertions = expected['caseAssertions'][case_id]
                for write in assertions['writes']:
                    array = get(document, write['array'])
                    require(isinstance(array, list) and len(array) == assertions['resultCount'], 'Result array shape changed')
                    for index, value in enumerate(array):
                        path = write['path'].replace('$index', str(index)) if write['outerIndex'] else write['path']
                        require(get(value, path) == write['value'], 'Lookup-derived or URL message rewrite differs from source contract')
                for path, value in assertions['unchangedFirstResultPaths'].items():
                    require(get(document, path) == value, 'An unrelated original JSON field changed')
        else:
            rows = json.loads(text)
            require(isinstance(rows, list), 'Captured non-wire output must be a record array')
            for row in rows:
                case_id = row.get('fixtureCase')
                require(case_id in expected['simulatedKafkaCaseIds'] and row.get('fixtureDelivery') == 'SIMULATED_KAFKA_PENDING', 'Unexpected record reached simulated Kafka capture')
                require(digest(row[expected['sourceMessageField']].encode()) == expected['simulatedKafkaSourceHashes'][case_id], 'Simulated Kafka branch did not preserve original message bytes')
                kafka_ids.append(case_id)
    require(sorted(file_ids) == sorted(expected['fileCaseIds']) and sorted(kafka_ids) == sorted(expected['simulatedKafkaCaseIds']), 'Expected branch records are missing or duplicated')
    require(file_count == expected['expectedPrimaryFileCount'], 'Unexpected wire file count')
    return {'fileRecords': len(file_ids), 'simulatedKafkaRecords': len(kafka_ids), 'wireFiles': file_count, 'sourceMessagesPreserved': True, 'wireProtocolChecked': True}


def run_tests(api, spec, state, output):
    results = []
    for flow in spec['flows']:
        flow_id = checked_id(state['flows'][flow['key']]['id'])
        run = api.call('/governance/flows/' + flow_id + '/tests', 'POST', {'inputJson': flow['inputJson'], 'parameters': {}})['data']
        deadline = time.monotonic() + 240
        while run['status'] not in TERMINAL and time.monotonic() < deadline:
            time.sleep(0.5)
            run = api.call('/governance/test-runs/' + checked_id(run['id']))['data']
        if run['status'] not in TERMINAL:
            run = api.call('/governance/test-runs/' + checked_id(run['id']) + '/cancel', 'POST')['data']
            stop_deadline = time.monotonic() + 30
            while run['status'] not in TERMINAL and time.monotonic() < stop_deadline:
                time.sleep(0.5)
                run = api.call('/governance/test-runs/' + checked_id(run['id']))['data']
        save_private(output / (flow['key'] + '-observed-run.json'), run)
        require(run['status'] == 'SUCCEEDED' and run['cleanupConfirmed'], 'Synthetic business test did not succeed and clean up; private run evidence saved')
        manifest = api.call('/governance/test-runs/' + checked_id(run['id']) + '/artifacts')['data']
        require(manifest['definitionHash'] == run['definitionHash'] and len(manifest['artifacts']) <= 100, 'Complete artifact manifest mismatch')
        contents = []
        for artifact in manifest['artifacts']:
            content = api.call('/governance/test-runs/' + checked_id(run['id']) + '/artifacts/' + checked_id(artifact['id']) + '/content', binary=True)
            require(len(content) == artifact['byteSize'] and digest(content) == artifact['sha256'], 'Complete artifact byte length or hash mismatch')
            contents.append(content)
        checked = assert_outputs(flow, contents)
        results.append({'flow': flow['key'], 'flowId': flow_id, 'runId': run['id'], 'status': run['status'], 'cleanupConfirmed': True, 'completeArtifactCount': len(contents), **checked})
    save_private(output / 'acceptance.json', {'format': FORMAT, 'results': results, 'syntheticOnly': True, 'originalExternalSystemsContacted': False})
    return results


def summary(spec):
    return {'format': FORMAT, 'syntheticOnly': True, 'flows': [{'key': flow['key'], 'nodes': len(flow['nodes']), 'edges': len(flow['edges']), 'records': len(flow['cases']),
             'inputBytes': len(flow['inputJson'].encode()), 'jsonFields': len(flow['originalJsonFields']), 'businessLookupCount': len([node for node in flow['nodes'] if node['kind'] == 'lookup' and node['key'] != 'sign']),
             'expectedBranches': dict(Counter(case['expectedDisposition'] for case in flow['cases']))} for flow in spec['flows']]}


def self_test(spec, app):
    """Offline negative checks, including resume/unknown-result guards and artifact tampering."""
    checks = []
    def rejects(action, label):
        try:
            action()
        except Exception:
            checks.append(label)
            return
        raise RuntimeError('Offline negative check unexpectedly accepted: ' + label)
    bad = deepcopy(spec)
    bad['flows'][0]['edges'].append({'source': 'writer', 'target': 'extract', 'relationship': 'success'})
    rejects(lambda: validate_spec(bad), 'cycle-rejected')
    bad_type = deepcopy(spec)
    bad_type['flows'][0]['nodes'][1]['type'] = 'unapproved.Processor'
    rejects(lambda: validate_spec(bad_type), 'unapproved-type-rejected')
    bad_rows = deepcopy(spec)
    bad_rows['flows'][0]['inputJson'] = compact([{}] * 101)
    rejects(lambda: validate_spec(bad_rows), 'oversize-input-rejected')
    missing_error = deepcopy(spec)
    missing_error['flows'][0]['edges'].remove(next(edge for edge in missing_error['flows'][0]['edges'] if edge['relationship'] == 'failure'))
    rejects(lambda: validate_spec(missing_error), 'unobserved-failure-rejected')
    class FakeApi:
        def __init__(self):
            self.flows, self.posts = {}, 0
        def call(self, path, method='GET', body=None, binary=False):
            if method == 'POST':
                self.posts += 1
            if path == '/governance/design/node-types':
                return {'data': [{'type': value, 'role': 'CAPTURE' if key == 'capture' else 'PROCESSOR'} for key, value in TYPE.items()]}
            if path == '/governance/projects':
                return {'data': {'id': str(uuid.uuid4())}}
            if path == '/governance/flows':
                key = str(uuid.uuid4())
                self.flows[key] = {'editable': True, 'nodes': [], 'connections': []}
                return {'data': {'id': key}}
            match = re.fullmatch(r'/governance/flows/([0-9a-f-]+)/design(?:/(nodes|connections))?', path)
            require(match is not None, 'Unexpected offline API operation')
            flow = self.flows[match.group(1)]
            if method == 'GET':
                return {'data': deepcopy(flow)}
            value = {**deepcopy(body), 'id': str(uuid.uuid4()), 'version': 0}
            if match.group(2) == 'nodes':
                flow['nodes'].append(value)
            else:
                value['relationships'] = [value.pop('relationship')]
                flow['connections'].append(value)
            return {'data': deepcopy(value)}
    with tempfile.TemporaryDirectory(prefix='business-helper-offline-', dir=app / 'private') as directory:
        binding = Path(directory) / 'bindings.json'
        api = FakeApi()
        state = apply_spec(api, spec, binding, app)
        posts = api.posts
        apply_spec(api, spec, binding, app)
        require(api.posts == posts, 'Offline resume created duplicate components')
        checks.append('repeat-apply-does-not-duplicate')
        flow = api.flows[state['flows']['normal']['id']]
        original_edge = deepcopy(flow['connections'][0])
        flow['connections'][0]['targetId'] = state['flows']['normal']['nodes']['writer']
        rejects(lambda: apply_spec(api, spec, binding, app), 'edited-wiring-rejected')
        require(api.posts == posts, 'Edited wiring check changed state')
        flow['connections'][0] = original_edge
        flow['nodes'][1]['name'] = 'locally edited'
        rejects(lambda: apply_spec(api, spec, binding, app), 'edited-node-rejected')
        class UncertainApi(FakeApi):
            def call(self, path, method='GET', body=None, binary=False):
                value = super().call(path, method, body, binary)
                if path == '/governance/projects':
                    raise RuntimeError('Synthetic lost mutation response')
                return value
        uncertain = UncertainApi()
        pending = Path(directory) / 'pending.json'
        rejects(lambda: apply_spec(uncertain, spec, pending, app), 'unknown-mutation-recorded')
        require(read_private(pending, app / 'private').get('mutationPending'), 'Unknown mutation was not recorded')
        old_posts = uncertain.posts
        rejects(lambda: apply_spec(uncertain, spec, pending, app), 'unknown-mutation-not-retried')
        require(uncertain.posts == old_posts, 'Unknown mutation was retried')
    for flow in spec['flows']:
        expected = flow['expectations']
        delimiter = bytes.fromhex(expected['delimiterHex']).decode('ascii')
        lines = [delimiter.join(expected['fileFields'])]
        kafka = []
        for case in flow['cases']:
            if case['expectedDisposition'] == 'file':
                document = deepcopy(case['document'])
                for write in expected['caseAssertions'][case['id']]['writes']:
                    for index, value in enumerate(get(document, write['array'])):
                        seed(value, write['path'].replace('$index', str(index)), write['value'])
                urls = [get(document, next(pointer(field['path']) for field in flow['originalJsonFields'] if field['name'] == name)) for name in expected['fileFields'][-2:]]
                lines.append(delimiter.join([compact(document), '', '', *['' if value is None else value.strip(' \t\r\n') for value in urls]]))
            elif case['expectedDisposition'] == 'simulated-kafka':
                kafka.append({**case['record'], 'fixtureDelivery': 'SIMULATED_KAFKA_PENDING'})
        artifacts = [('\n'.join(lines) + '\n').encode()]
        if kafka:
            artifacts.append(compact(kafka).encode())
        assert_outputs(flow, artifacts)
        checks.append(flow['key'] + '-artifact-assertions-accept-reference')
        altered = list(artifacts)
        altered[0] = ('\n'.join(lines[:1] + lines[2:]) + '\n').encode()
        rejects(lambda: assert_outputs(flow, altered), flow['key'] + '-missing-record-rejected')
        if kafka:
            kafka[0][expected['sourceMessageField']] += ' '
            rejects(lambda: assert_outputs(flow, [artifacts[0], compact(kafka).encode()]), 'kafka-body-byte-change-rejected')
    return {'checks': len(checks), 'passed': checks, 'realApiCalls': 0}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--runtime', type=Path, required=True)
    parser.add_argument('--app-runtime', type=Path, required=True)
    parser.add_argument('--contract', type=Path, required=True)
    parser.add_argument('--operations', type=Path, required=True)
    parser.add_argument('--apply', action='store_true', help='Explicitly create only owned local safe fixture flows')
    parser.add_argument('--test', action='store_true', help='After creating the owned flows, submit bounded synthetic tests and verify complete artifacts')
    parser.add_argument('--self-test', action='store_true', help='Run offline negative checks with an in-memory API; does not contact a service')
    args = parser.parse_args()
    runtime, app = args.runtime.resolve(), args.app_runtime.resolve()
    private = app / 'private'
    require(private.is_dir() and not private.is_symlink() and stat.S_IMODE(private.stat().st_mode) == 0o700, 'Owned private directory must be mode 700')
    owner = read_private(private / 'app-owner.json', private)
    require(owner == {'owner': 'rynew-data-governance-app', 'root': str(app)}, 'Dedicated application ownership required')
    contract = read_private(args.contract, private)
    operations = read_private(args.operations, private)
    require(contract.get('format') == 'RYNEW_ETL_EXACT_STATIC_CONTRACT_V1', 'Unexpected original contract format')
    require(operations.get('sourceContract') == args.contract.name, 'Operations do not reference the supplied original contract')
    spec = {'format': FORMAT, 'sourceContractHash': digest(args.contract.read_bytes()), 'operationsHash': digest(args.operations.read_bytes()),
            'requirements': {'compatibilityBundle': '1.2.3', 'safeGraphNodes': 32, 'safeGraphEdges': 96, 'inputRecords': 100, 'inputBytes': 262144, 'features': ['get.KETTLE_STRING', 'parse.KETTLE_JSON', 'parse.onEmpty.NULL', 'complete-run-artifacts']},
            'flows': [FixtureBuilder(business, contract['flows'][business], operations['operations']).build() for business in ['normal', 'violation']]}
    validate_spec(spec)
    target = private / ('business-fixtures-' + digest(spec)[:12])
    save_private(target / 'spec.json', spec)
    for flow in spec['flows']:
        save_private(target / (flow['key'] + '-input.json'), json.loads(flow['inputJson']))
    result = summary(spec)
    result.update({'privateSpec': str(target / 'spec.json'), 'offlineValidationPassed': True, 'apiCalled': False})
    if args.self_test:
        result['offlineChecks'] = self_test(spec, app)
    if args.apply or args.test:
        api = LocalApi(runtime, app)
        state = apply_spec(api, spec, target / 'bindings.json', app)
        result.update({'apiCalled': True, 'projectId': state['projectId'], 'flowIds': {key: value['id'] for key, value in state['flows'].items()}})
        if args.test:
            result['tests'] = run_tests(api, spec, state, target)
    print(json.dumps(result, ensure_ascii=False, indent=2))


if __name__ == '__main__':
    try:
        main()
    except Exception as error:
        # Never print API bodies, input records, operations, credentials, or underlying parser messages.
        print(json.dumps({'success': False, 'errorType': type(error).__name__, 'message': str(error) if isinstance(error, RuntimeError) else 'Fixture preparation or local API check failed'}, ensure_ascii=False), file=sys.stderr)
        sys.exit(1)
