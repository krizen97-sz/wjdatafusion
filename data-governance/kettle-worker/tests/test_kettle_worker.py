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
            for key, value in {'filename': '${WORK_DIR}/input.csv', 'encoding': 'UTF-8', 'lazy_conversion': 'N'}.items():
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
        validation = self.worker.validate(fixture(self.catalog))
        self.assertTrue(validation['valid'], validation)
        script = next(n for n in validation['nodes'] if n['name'] == 'native-script')
        self.assertIn({'name': 'greeting', 'type': 'String'}, script['fields'])

    def test_native_file_input_script_file_output(self):
        identifier = self.start()
        result = self.worker.wait(identifier)
        self.assertEqual(result['state'], 'SUCCEEDED', self.worker.runs[identifier]['events'])
        output = Path(self.worker.runs[identifier]['directory']) / 'output/result.csv'
        self.assertEqual(output.read_text().splitlines(), ['name,greeting', 'alice,ALICE!', '张三,张三!', 'bob,BOB!'])
        events = self.worker.runs[identifier]['events']
        self.assertEqual(len([e for e in events if e['type'] == 'row' and e['node'] == 'native-script' and e['direction'] == 'written']), 3)
        self.assertEqual(next(n for n in result['nodes'] if n['node'] == 'file-output')['read'], 3)

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
            saved = request('/transformations/' + identifier, 'PUT', {'xml': fixture(self.catalog)})
            self.assertTrue(saved['validation']['valid'])
            run = request('/runs', 'POST', {'transformationId': identifier, 'runId': identifier, 'inputFiles': [{'name': 'input.csv', 'contentBase64': base64.b64encode('name\nalice\n'.encode()).decode()}]})
            deadline = time.monotonic() + 15
            while run['state'] not in module.TERMINAL and time.monotonic() < deadline:
                time.sleep(.05)
                run = request('/runs/' + identifier)
            self.assertEqual(run['state'], 'SUCCEEDED')
            data = request('/runs/' + identifier + '/files/result.csv', raw=True)
            self.assertEqual(data, b'name,greeting\nalice,ALICE!\n')
            output = next(f for f in run['files'] if f['name'] == 'result.csv')
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
