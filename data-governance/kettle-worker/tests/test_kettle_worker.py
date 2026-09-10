"""Synthetic-only integration acceptance. Set KETTLE_WORKER_RUNTIME after prepare."""
import importlib.util
import base64
import hashlib
import json
import os
from pathlib import Path
import subprocess
import tempfile
import time
import threading
import urllib.request
import urllib.error
from urllib.parse import quote
import unittest
import xml.etree.ElementTree as ET

REPO = Path(__file__).resolve().parents[3]
spec = importlib.util.spec_from_file_location('kettle_worker', REPO / 'tools/data-governance/kettle_worker.py')
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


def put(node, path, value):
    current = node
    for name in path.split('/'):
        found = current.find(name)
        current = found if found is not None else ET.SubElement(current, name)
    current.text = str(value)


def fixture(catalog, delay=False, broken=False):
    entries = {x['id']: x for x in catalog['steps']}
    trans = ET.Element('transformation')
    for key, value in {'name': 'synthetic-native-worker', 'trans_type': 'Normal', 'size_rowset': '100', 'capture_step_performance': 'N', 'feedback_shown': 'N'}.items():
        put(trans, 'info/' + key, value)
    order = ET.SubElement(trans, 'order')
    sequence = [('CsvInput', 'file-input'), ('ScriptValueMod', 'native-script')]
    if delay:
        sequence.append(('Delay', 'slow-step'))
    sequence.append(('TextFileOutput', 'file-output'))
    for index, (plugin, name) in enumerate(sequence):
        step = ET.SubElement(trans, 'step')
        for key, value in {'name': name, 'type': plugin, 'copies': '1', 'distribute': 'Y', 'partitioning/method': 'none', 'GUI/xloc': str(index * 200), 'GUI/yloc': '100', 'GUI/draw': 'Y'}.items():
            put(step, key, value)
        for child in ET.fromstring('<settings>' + entries[plugin]['defaultXml'] + '</settings>'):
            step.append(child)
        if plugin == 'CsvInput':
            for key, value in {'filename': '${INPUT_DIR}/input.csv', 'encoding': 'UTF-8', 'lazy_conversion': 'N'}.items():
                put(step, key, value)
            put(step, 'fields/field/name', 'name')
        elif plugin == 'ScriptValueMod':
            put(step, 'compatible', 'N')
            put(step, 'optimizationLevel', '9')
            scripts = step.find('jsScripts')
            if scripts is None:
                scripts = ET.SubElement(step, 'jsScripts')
            scripts.clear()
            script = ET.SubElement(scripts, 'jsScript')
            for key, value in {'jsScript_type': '0', 'jsScript_name': 'Synthetic transform', 'jsScript_script': 'var greeting = name.toUpperCase() + "!";' if not broken else 'throw new Error("synthetic-engine-failure");'}.items():
                put(script, key, value)
            fields = step.find('fields')
            if fields is None:
                fields = ET.SubElement(step, 'fields')
            fields.clear()
            field = ET.SubElement(fields, 'field')
            for key, value in {'name': 'greeting', 'rename': '', 'type': 'String', 'length': '-1', 'precision': '-1', 'replace': 'N'}.items():
                put(field, key, value)
        elif plugin == 'Delay':
            put(step, 'timeout', '1')
            put(step, 'scaletime', 'seconds')
        elif plugin == 'TextFileOutput':
            for key, value in {'file/name': '${WORK_DIR}/result', 'file/extention': 'csv', 'file/do_not_open_new_file_init': 'Y', 'file/rename_file_name': 'N', 'separator': ',', 'encoding': 'UTF-8', 'format': 'UNIX', 'enclosure': ''}.items():
                put(step, key, value)
        if index:
            hop = ET.SubElement(order, 'hop')
            for key, value in {'from': sequence[index - 1][1], 'to': name, 'enabled': 'Y'}.items():
                put(hop, key, value)
    return ET.tostring(trans, encoding='unicode')


class StructuralTests(unittest.TestCase):
    def test_safe_unicode_filenames(self):
        self.assertEqual(module.validate_filename('车辆数据 2026.csv'), '车辆数据 2026.csv')
        for name in ['../secret', 'dir/file', 'dir\\file', '..', ' ', 'a\x00b', 'a\x85b']:
            with self.assertRaises(ValueError):
                module.validate_filename(name)

    def test_reject_dtd_and_unknown_hops(self):
        with self.assertRaises(ValueError):
            module.Worker.validate_xml('<!DOCTYPE a [<!ENTITY p SYSTEM "file:///etc/passwd">]><transformation/>')
        with self.assertRaises(ValueError):
            module.Worker.validate_xml('<transformation><step><name>a</name></step><order><hop><from>a</from><to>b</to></hop></order></transformation>')


@unittest.skipUnless(os.environ.get('KETTLE_WORKER_RUNTIME'), 'Requires explicitly prepared original engine runtime')
class NativeTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.worker = module.Worker(os.environ['KETTLE_WORKER_RUNTIME'], timeout=20)
        cls.catalog = cls.worker.capabilities()

    def start(self, **kwargs):
        xml = fixture(self.catalog, kwargs.pop('delay', False), kwargs.pop('broken', False))
        return self.worker.launch('run', xml, input_files=[{'name': 'input.csv', 'content': 'name\nalice\n张三\nbob\n'}], **kwargs)

    def test_original_plugins_and_fields(self):
        steps = {s['id']: s for s in self.catalog['steps']}
        for name in ['CsvInput', 'ScriptValueMod', 'TextFileOutput', 'KafkaConsumer', 'KafkaProducer', 'JsonInput', 'DBLookup', 'FilterRows']:
            self.assertTrue(steps[name]['loadable'], name)
            self.assertEqual(steps[name]['classSource'], 'kettle-6.1.0.7.36.jar')
        jobs = {entry['id']: entry for entry in self.catalog['jobs']}
        for name in ['SPECIAL', 'TRANS', 'FTP_PUT']:
            self.assertTrue(jobs[name]['loadable'])
            self.assertTrue(jobs[name]['executionSupported'])
            self.assertIn('defaultXml', jobs[name])
            self.assertEqual(jobs[name]['classSource'], 'kettle-6.1.0.7.36.jar')
        validation = self.worker.validate(fixture(self.catalog))
        self.assertTrue(validation['valid'], validation)
        script = next(n for n in validation['nodes'] if n['name'] == 'native-script')
        self.assertEqual(next(field for field in script['fields'] if field['name'] == 'greeting')['type'], 'String')
        self.assertTrue(all({'length', 'precision', 'origin'} <= field.keys() for field in script['fields']))
        self.assertEqual([field['name'] for field in script['inputFields']], ['name'])
        self.assertTrue(all({'length', 'precision', 'origin'} <= field.keys() for field in script['inputFields']))
        self.assertEqual(next(n for n in validation['nodes'] if n['name'] == 'file-input')['inputFields'], [])
        branched = ET.fromstring(fixture(self.catalog))
        unrelated = ET.SubElement(branched, 'step')
        for key, value in {'name': 'unrelated-source', 'type': 'DataGrid', 'copies': '1', 'GUI/draw': 'Y', 'fields/field/name': 'unrelated', 'fields/field/type': 'String', 'fields/field/length': '-1', 'fields/field/precision': '-1'}.items():
            put(unrelated, key, value)
        ET.SubElement(unrelated, 'data')
        inspected = self.worker.validate(ET.tostring(branched, encoding='unicode'))
        actual_input = next(n for n in inspected['nodes'] if n['name'] == 'native-script')['inputFields']
        self.assertEqual([field['name'] for field in actual_input], ['name'])

    def test_native_file_input_script_file_output(self):
        identifier = self.start()
        result = self.worker.wait(identifier)
        self.assertEqual(result['state'], 'SUCCEEDED', self.worker.runs[identifier]['events'])
        output = Path(self.worker.runs[identifier]['directory']) / 'output/result.csv'
        self.assertEqual(output.read_text().splitlines(), ['name,greeting', 'alice,ALICE!', '张三,张三!', 'bob,BOB!'])
        events = self.worker.runs[identifier]['events']
        self.assertEqual(len([e for e in events if e['type'] == 'row' and e['node'] == 'native-script' and e['direction'] == 'written']), 3)
        self.assertEqual(next(n for n in result['nodes'] if n['node'] == 'file-output')['read'], 3)

    def test_uploaded_definitions_and_sources_never_enter_output_directory(self):
        identifier = self.worker.launch('run', fixture(self.catalog), input_files=[{'name': 'input.csv', 'content': 'name\nalice\n'}, {'name': 'private-child.ktr', 'content': '<synthetic><password>fixture-only-secret</password></synthetic>'}])
        result = self.worker.wait(identifier)
        self.assertEqual(result['state'], 'SUCCEEDED')
        directory = Path(self.worker.runs[identifier]['directory'])
        self.assertTrue((directory / 'input/input.csv').is_file())
        self.assertTrue((directory / 'input/private-child.ktr').is_file())
        self.assertEqual({path.name for path in (directory / 'output').iterdir()}, {'result.csv'})
        self.assertEqual(result['inputLayout'], 'separate')
        self.assertEqual({item['name'] for item in result['inputs']}, {'input.csv', 'private-child.ktr'})
        self.assertTrue(all(item['role'] == 'output' for item in result['files']))
        for name in ['input.csv', 'private-child.ktr']:
            with self.assertRaises(FileNotFoundError):
                self.worker.open_file(identifier, name)

    def test_same_input_output_basename_preserves_source_and_is_real_output(self):
        original = 'name\nalice\n'
        identifier = self.worker.launch('run', fixture(self.catalog).replace('${WORK_DIR}/result', '${WORK_DIR}/input'), input_files=[{'name': 'input.csv', 'content': original}])
        result = self.worker.wait(identifier)
        self.assertEqual(result['state'], 'SUCCEEDED')
        directory = Path(self.worker.runs[identifier]['directory'])
        self.assertEqual((directory / 'input/input.csv').read_text(), original)
        self.assertEqual(result['files'][0]['name'], 'input.csv')
        self.assertEqual(result['files'][0]['role'], 'output')
        self.assertNotEqual(result['files'][0]['sha256'], result['inputs'][0]['sha256'])
        fd, _ = self.worker.open_file(identifier, 'input.csv')
        with os.fdopen(fd) as stream:
            self.assertEqual(stream.read(), 'name,greeting\nalice,ALICE!\n')

    def test_native_default_parameter_controls_output_filename(self):
        transformation = ET.fromstring(fixture(self.catalog).replace('${WORK_DIR}/result', '${WORK_DIR}/${file_prefix}'))
        for key, value in {'name': 'file_prefix', 'default_value': '原生默认前缀', 'description': 'Synthetic native parameter proof'}.items():
            put(transformation, 'info/parameters/parameter/' + key, value)
        identifier = self.worker.launch('run', ET.tostring(transformation, encoding='unicode'), input_files=[{'name': 'input.csv', 'content': 'name\nalice\n'}])
        result = self.worker.wait(identifier)
        self.assertEqual(result['state'], 'SUCCEEDED')
        self.assertEqual({file['name'] for file in result['files']}, {'原生默认前缀.csv'})
        fd, _ = self.worker.open_file(identifier, '原生默认前缀.csv')
        with os.fdopen(fd) as stream:
            self.assertEqual(stream.read(), 'name,greeting\nalice,ALICE!\n')

    def test_reserved_parameters_cannot_replace_input_or_output_roots(self):
        for parameter in ['WORK_DIR', 'INPUT_DIR', 'JOB_DIR']:
            transformation = ET.fromstring(fixture(self.catalog))
            put(transformation, 'info/parameters/parameter/name', parameter)
            put(transformation, 'info/parameters/parameter/default_value', '/synthetic-not-permitted')
            result = self.worker.validate(ET.tostring(transformation, encoding='unicode'))
            self.assertFalse(result['valid'])
            self.assertTrue(any('Reserved execution-directory parameter' in event.get('message', '') for event in result['errors']))

    def test_legacy_input_roles_are_kept_and_not_downloadable_as_outputs(self):
        with tempfile.TemporaryDirectory(dir=self.worker.runtime) as directory_name:
            directory = Path(directory_name)
            (directory / 'output').mkdir()
            (directory / 'output/source.ktr').write_text('synthetic-private-source')
            (directory / 'output/result.csv').write_text('synthetic-output')
            identifier = 'legacy-layout-' + str(time.time_ns())
            self.worker.runs[identifier] = {'id': identifier, 'state': 'SUCCEEDED', 'directory': str(directory), 'process': None, 'events': [], 'inputNames': ['source.ktr']}
            try:
                files = self.worker.snapshot(identifier)['files']
                self.assertEqual(next(item for item in files if item['name'] == 'source.ktr')['role'], 'input')
                self.assertEqual(next(item for item in files if item['name'] == 'result.csv')['role'], 'output')
                with self.assertRaises(ValueError):
                    self.worker.open_file(identifier, 'source.ktr')
            finally:
                self.worker.runs.pop(identifier)

    def test_preview_removes_downstream_output_and_limits_samples(self):
        identifier = self.start(preview_step='native-script', row_limit=2)
        result = self.worker.wait(identifier)
        self.assertEqual(result['state'], 'PREVIEW_COMPLETE', self.worker.runs[identifier]['events'])
        output = Path(self.worker.runs[identifier]['directory']) / 'output/result.csv'
        self.assertFalse(output.exists())
        samples = [e for e in self.worker.runs[identifier]['events'] if e['type'] == 'row' and e['node'] == 'native-script' and e['direction'] == 'written']
        self.assertEqual(len(samples), 2)
        self.assertEqual(samples[0]['fields']['greeting'], 'ALICE!')

    def test_stop_active_native_transformation(self):
        identifier = self.start(delay=True)
        deadline = time.monotonic() + 8
        while self.worker.snapshot(identifier)['state'] != 'RUNNING' and time.monotonic() < deadline:
            time.sleep(.03)
        self.worker.stop(identifier)
        result = self.worker.wait(identifier)
        self.assertEqual(result['state'], 'STOPPED', self.worker.runs[identifier]['events'])
        self.assertEqual(result['errors'], 0)

    def test_engine_errors_override_result_boolean(self):
        identifier = self.start(broken=True)
        result = self.worker.wait(identifier)
        self.assertEqual(result['state'], 'FAILED')
        self.assertGreater(result['errors'], 0)
        self.assertTrue(any(event['type'] == 'log' and event.get('level') == 'ERROR' for event in self.worker.runs[identifier]['events']))

    def test_run_count_is_not_limited_by_sampling(self):
        identifier = self.worker.launch('run', fixture(self.catalog), row_limit=2, input_files=[{'name': 'input.csv', 'content': 'name\n' + '\n'.join('item' + str(i) for i in range(350)) + '\n'}])
        result = self.worker.wait(identifier)
        self.assertEqual(result['state'], 'SUCCEEDED')
        self.assertEqual(next(n for n in result['nodes'] if n['node'] == 'file-output')['read'], 350)
        self.assertEqual(len((Path(self.worker.runs[identifier]['directory']) / 'output/result.csv').read_text().splitlines()), 351)
        self.assertEqual(len([e for e in self.worker.runs[identifier]['events'] if e['type'] == 'row' and e['node'] == 'native-script' and e['direction'] == 'written']), 2)

    def test_run_idempotency_and_immutable_definition(self):
        identifier = 'test-immutable-' + str(time.time_ns())
        xml = fixture(self.catalog)
        inputs = [{'name': 'input.csv', 'content': 'name\nalice\n'}]
        first = self.worker.launch('run', xml, run_id=identifier, input_files=inputs)
        again = self.worker.launch('run', xml, run_id=identifier, input_files=inputs)
        self.assertEqual(first, again)
        with self.assertRaises(ValueError):
            self.worker.launch('run', xml.replace('ALWAYS_ABSENT', 'unused') + '\n', run_id=identifier, input_files=inputs)
        self.worker.wait(identifier)
        self.assertEqual((Path(self.worker.runs[identifier]['directory']) / 'transformation.ktr').read_text(), xml)

    def test_input_names_cannot_collide_on_macos(self):
        for names in [('A.csv', 'a.csv'), ('Café.csv', 'Cafe\u0301.csv')]:
            with self.assertRaises(ValueError):
                self.worker.launch('run', fixture(self.catalog), input_files=[{'name': name, 'content': 'x'} for name in names])

    def test_kafka_preview_changes_only_effective_execution_copy(self):
        for group, auto in [('original-business-group', 'false'), ('other-business-group', 'true')]:
            transformation = ET.Element('transformation')
            put(transformation, 'info/name', 'Synthetic isolated Kafka preview metadata')
            consumer = ET.SubElement(transformation, 'step')
            for key, value in {'name': 'consumer', 'type': 'KafkaConsumer', 'copies': '1', 'GUI/draw': 'Y', 'KAFKA/group.id': group, 'KAFKA/auto.commit.enable': auto}.items():
                put(consumer, key, value)
            original = ET.tostring(transformation, encoding='unicode')
            saved_id = 'preview-metadata-' + str(time.time_ns())
            self.assertTrue(self.worker.save(saved_id, original)['validation']['valid'])
            identifier = self.worker.launch('run', original, preview_step='consumer')
            self.worker.wait(identifier)  # No endpoint/topic is configured; native startup must fail offline.
            events = self.worker.runs[identifier]['events']
            override = next(event for event in events if event['type'] == 'preview-override')
            self.assertEqual(override['originalGroup'], group)
            self.assertEqual(override['originalAutoCommit'], auto)
            self.assertRegex(override['effectiveGroup'], r'^kettle-v2-[a-f0-9]{32}-preview$')
            self.assertFalse(override['effectiveAutoCommit'])
            directory = Path(self.worker.runs[identifier]['directory'])
            self.assertEqual((directory / 'transformation.ktr').read_text(), original)
            self.assertEqual((self.worker.store / (saved_id + '.ktr')).read_text(), original)
            effective = ET.fromstring((directory / 'transformation.effective.ktr').read_text())
            self.assertEqual(effective.findtext('./step/KAFKA/group.id'), override['effectiveGroup'])
            self.assertEqual(effective.findtext('./step/KAFKA/auto.commit.enable'), 'false')
            self.assertEqual(override['originalXmlSha'], hashlib.sha256(original.encode()).hexdigest())
            self.assertNotEqual(override['originalXmlSha'], override['effectiveXmlSha'])
            self.assertEqual(override['effectiveXmlSha'], hashlib.sha256((directory / 'transformation.effective.ktr').read_bytes()).hexdigest())
            normal = self.worker.launch('run', original)
            normal_result = self.worker.wait(normal)
            self.assertEqual(normal_result['originalXmlSha'], normal_result['effectiveXmlSha'])
            self.assertFalse(any(event['type'] == 'preview-override' for event in self.worker.runs[normal]['events']))

    def test_original_write_to_log_basic_is_visible_and_redacted(self):
        transformation = ET.fromstring(fixture(self.catalog))
        logger = ET.SubElement(transformation, 'step')
        for key, value in {'name': 'original-basic-log', 'type': 'WriteToLog', 'copies': '1', 'GUI/draw': 'Y', 'loglevel': 'log_level_basic', 'displayHeader': 'N', 'limitRows': 'Y', 'limitRowsNumber': '1', 'logmessage': 'KETTLE_BASIC_VISIBLE synthetic-log-secret', 'fields/field/name': 'greeting'}.items():
            put(logger, key, value)
        connection = ET.SubElement(transformation, 'connection')
        for key, value in {'name': 'unused-secret-metadata', 'type': 'POSTGRESQL', 'access': 'Native', 'database': 'unused', 'username': 'unused', 'password': 'synthetic-log-secret'}.items():
            put(connection, key, value)
        for hop in transformation.findall('./order/hop'):
            if hop.findtext('to') == 'file-output':
                put(hop, 'to', 'original-basic-log')
        hop = ET.SubElement(transformation.find('order'), 'hop')
        for key, value in {'from': 'original-basic-log', 'to': 'file-output', 'enabled': 'Y'}.items():
            put(hop, key, value)
        identifier = self.worker.launch('run', ET.tostring(transformation, encoding='unicode'), input_files=[{'name': 'input.csv', 'content': 'name\nalice\n'}])
        result = self.worker.wait(identifier)
        self.assertEqual(result['state'], 'SUCCEEDED')
        logs = [event for event in self.worker.runs[identifier]['events'] if event['type'] == 'log']
        self.assertTrue(any(event['level'] == 'BASIC' and event['node'] == 'original-basic-log' and 'KETTLE_BASIC_VISIBLE' in event['message'] for event in logs), logs)
        self.assertTrue(all(event.get('channel') for event in logs))
        self.assertFalse(any('synthetic-log-secret' in event['message'] for event in logs))
        self.assertLessEqual(len(logs), 500)

    def test_trusted_endpoint_policy_permissions_and_scope(self):
        policy = self.worker.runtime / ('test-policy-' + str(time.time_ns()) + '.json')
        policy.write_text(json.dumps({'endpoints': [{'host': '127.0.0.1', 'port': 15432}]}))
        policy.chmod(0o600)
        configured = module.Worker(self.worker.runtime, network_policy=policy)
        self.assertEqual(configured.endpoints, [{'host': '127.0.0.1', 'port': 15432}])
        policy.chmod(0o644)
        with self.assertRaises(ValueError):
            module.Worker(self.worker.runtime, network_policy=policy)
        with self.assertRaises(ValueError):
            module.Worker(self.worker.runtime, allow_endpoints=['192.0.2.1:15432'])

    def test_native_job_broker_bridge(self):
        if not (self.worker.runtime / 'classes/NativeJobExecutor.class').exists():
            self.skipTest('NativeJobExecutor is a separate required integration commit')
        job = ET.Element('job')
        put(job, 'name', 'Synthetic broker child job')
        entries = ET.SubElement(job, 'entries')
        for values in [
            {'name': 'START', 'type': 'SPECIAL', 'start': 'Y', 'dummy': 'N', 'repeat': 'N', 'parallel': 'N', 'draw': 'Y', 'nr': '0'},
            {'name': 'Original child transformation', 'type': 'TRANS', 'specification_method': 'filename', 'filename': '${INPUT_DIR}/child.ktr', 'wait_until_finished': 'Y', 'cluster': 'N', 'slave_server_name': '', 'parallel': 'N', 'draw': 'Y', 'nr': '0', 'parameters/pass_all_parameters': 'Y'},
        ]:
            entry = ET.SubElement(entries, 'entry')
            for key, value in values.items():
                put(entry, key, value)
        for key, value in {'from': 'START', 'to': 'Original child transformation', 'from_nr': '0', 'to_nr': '0', 'enabled': 'Y', 'evaluation': 'Y', 'unconditional': 'Y'}.items():
            put(job, 'hops/hop/' + key, value)
        xml = ET.tostring(job, encoding='unicode')
        inputs = [{'name': 'child.ktr', 'content': fixture(self.catalog)}, {'name': 'input.csv', 'content': 'name\nalice\n'}]
        validation = self.worker.validate_job(xml, inputs)
        self.assertTrue(validation['valid'], validation)
        identifier = self.worker.launch('job', xml, input_files=inputs)
        result = self.worker.wait(identifier)
        self.assertEqual(result['state'], 'SUCCEEDED', self.worker.runs[identifier]['events'])
        self.assertEqual((Path(self.worker.runs[identifier]['directory']) / 'output/result.csv').read_text(), 'name,greeting\nalice,ALICE!\n')

    def test_authenticated_http_binary_upload_and_download(self):
        broker = subprocess.Popen([os.sys.executable, str(REPO / 'tools/data-governance/kettle_worker.py'), 'serve', '--runtime', str(self.worker.runtime), '--port', '0', '--timeout', '20'], stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
        try:
            url = json.loads(broker.stdout.readline())['url']
            def request(path, method='GET', body=None, headers=None, raw=False):
                actual = {'Authorization': 'Bearer ' + self.worker.token, 'Content-Type': 'application/json'}
                actual.update(headers or {})
                req = urllib.request.Request(url + path, None if body is None else json.dumps(body).encode(), actual, method=method)
                with urllib.request.urlopen(req, timeout=15) as response:
                    content = response.read()
                    return content if raw else json.loads(content)
            with self.assertRaises(urllib.error.HTTPError) as missing:
                request('/health', headers={'Authorization': ''})
            self.assertEqual(missing.exception.code, 401)
            with self.assertRaises(urllib.error.HTTPError) as origin:
                request('/health', headers={'Origin': 'https://untrusted.example'})
            self.assertEqual(origin.exception.code, 400)
            self.assertEqual(request('/health')['status'], 'UP')
            identifier = 'http-' + str(time.time_ns())
            saved = request('/transformations/' + identifier, 'PUT', {'xml': fixture(self.catalog).replace('/input.csv', '/输入数据.csv').replace('/result', '/输出结果')})
            self.assertTrue(saved['validation']['valid'])
            run = request('/runs', 'POST', {'transformationId': identifier, 'runId': identifier, 'inputFiles': [{'name': '输入数据.csv', 'contentBase64': base64.b64encode('name\nalice\n'.encode()).decode()}]})
            deadline = time.monotonic() + 15
            while run['state'] not in module.TERMINAL and time.monotonic() < deadline:
                time.sleep(.05)
                run = request('/runs/' + identifier)
            self.assertEqual(run['state'], 'SUCCEEDED')
            data = request('/runs/' + identifier + '/files/' + quote('输出结果.csv'), raw=True)
            self.assertEqual(data, b'name,greeting\nalice,ALICE!\n')
            output = next(f for f in run['files'] if f['name'] == '输出结果.csv')
            self.assertEqual(output['sha256'], hashlib.sha256(data).hexdigest())
            self.assertFalse(output['partial'])
            events = request('/runs/' + identifier + '/events?after=0')['events']
            self.assertTrue(any(e['type'] == 'row' and e.get('fieldsMeta') for e in events))
        finally:
            broker.terminate()
            broker.wait(timeout=5)
            broker.stdout.close()
            broker.stderr.close()

    def test_os_sandbox_blocks_file_read_write_and_network(self):
        operation = module.private_dir(self.worker.operations / ('probe-' + str(time.time_ns())))
        outside = module.private_dir(self.worker.operations / ('outside-' + str(time.time_ns())))
        sentinel = outside / 'synthetic-private.txt'
        sentinel.write_text('synthetic-never-readable')
        identifier = self.worker.launch('probe', preview_step=str(outside))
        self.worker.wait(identifier)
        proof = next(e for e in self.worker.runs[identifier]['events'] if e['type'] == 'sandbox-proof')
        for key in ['readBlocked', 'writeBlocked', 'networkBlocked']:
            self.assertTrue(proof[key], proof)
        self.assertFalse(proof['securityManagerInstalled'])


if __name__ == '__main__':
    unittest.main(verbosity=2)
