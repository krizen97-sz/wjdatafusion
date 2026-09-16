"""Original-engine proof; only an explicitly prepared, independent runtime may be used."""
import base64
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import subprocess
import time
import unittest
import urllib.error
import urllib.request
import xml.etree.ElementTree as ET

spec = importlib.util.spec_from_file_location('operation_fixtures', Path(__file__).with_name('test_kettle_worker.py'))
fixtures = importlib.util.module_from_spec(spec)
spec.loader.exec_module(fixtures)


def job_xml():
    job = ET.Element('job')
    fixtures.put(job, 'name', 'Synthetic timeout contract')
    entries = ET.SubElement(job, 'entries')
    for values in [
        {'name': 'START', 'type': 'SPECIAL', 'start': 'Y', 'dummy': 'N', 'repeat': 'N', 'parallel': 'N', 'draw': 'Y', 'nr': '0'},
        {'name': 'Original child', 'type': 'TRANS', 'specification_method': 'filename', 'filename': '${INPUT_DIR}/child.ktr', 'wait_until_finished': 'Y', 'cluster': 'N', 'slave_server_name': '', 'parallel': 'N', 'draw': 'Y', 'nr': '0', 'parameters/pass_all_parameters': 'Y'},
    ]:
        entry = ET.SubElement(entries, 'entry')
        for key, value in values.items():
            fixtures.put(entry, key, value)
    for key, value in {'from': 'START', 'to': 'Original child', 'from_nr': '0', 'to_nr': '0', 'enabled': 'Y', 'evaluation': 'Y', 'unconditional': 'Y'}.items():
        fixtures.put(job, 'hops/hop/' + key, value)
    return ET.tostring(job, encoding='unicode')


@unittest.skipUnless(os.environ.get('KETTLE_WORKER_RUNTIME') and os.sys.platform == 'darwin', 'Requires independent macOS Seatbelt original engine runtime')
class OperationContextNativeTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.runtime = Path(os.environ['KETTLE_WORKER_RUNTIME']).resolve()
        cls.worker = fixtures.module.Worker(cls.runtime, timeout=150)
        cls.catalog = cls.worker.capabilities()

    def test_http_validation_stages_uploaded_files_without_execution(self):
        worker_script = fixtures.REPO / 'tools/data-governance/kettle_worker.py'
        broker = subprocess.Popen([os.sys.executable, str(worker_script), 'serve', '--runtime', str(self.runtime), '--port', '0', '--timeout', '30'], stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
        try:
            url = json.loads(broker.stdout.readline())['url']
            def request(path, method, body):
                req = urllib.request.Request(url + path, json.dumps(body).encode(),
                    {'Authorization': 'Bearer ' + self.worker.token, 'Content-Type': 'application/json'}, method=method)
                with urllib.request.urlopen(req, timeout=35) as response:
                    return json.load(response)
            sample = 'name\nalice\n张三\n'.encode()
            binary = bytes(range(256))
            inputs = [{'name': 'input.csv', 'contentBase64': base64.b64encode(sample).decode()},
                      {'name': '上传样本.bin', 'contentBase64': base64.b64encode(binary).decode()}]
            xml = fixtures.fixture(self.catalog)
            before = set(self.worker.records.iterdir())
            result = request('/transformations/validate', 'POST', {'xml': xml, 'inputFiles': inputs})
            self.assertTrue(result['valid'], result)
            self.assertEqual(result['validationScope'], 'metadata-only')
            created = set(self.worker.records.iterdir()) - before
            self.assertEqual(len(created), 1)
            record_dir = created.pop()
            intent = json.loads((record_dir / 'intent.json').read_text())
            self.assertEqual(intent['operation'], 'validate')
            self.assertEqual(intent['executionTimeoutSeconds'], 30)
            operation = self.worker.operations / record_dir.name
            self.assertEqual((operation / 'input/input.csv').read_bytes(), sample)
            self.assertEqual((operation / 'input/上传样本.bin').read_bytes(), binary)
            self.assertEqual(list((operation / 'output').iterdir()), [])
            events = [json.loads(line) for line in (record_dir / 'events.ndjson').read_text().splitlines()]
            self.assertFalse(any(event['type'] in {'row', 'job-entry'} or event.get('state') == 'RUNNING' for event in events))
            self.assertEqual({f['name']: f['sha256'] for f in intent['inputs']},
                {'input.csv': hashlib.sha256(sample).hexdigest(), '上传样本.bin': hashlib.sha256(binary).hexdigest()})
            with self.assertRaises(urllib.error.HTTPError) as bad:
                request('/transformations/validate', 'POST', {'xml': xml, 'inputFiles': [{'name': '../escape', 'content': 'bad'}]})
            self.assertEqual(bad.exception.code, 400)
            before = set(self.worker.records.iterdir())
            loaded = request('/transformations/offline-context-proof', 'PUT', {'xml': xml, 'inputFiles': inputs})
            self.assertEqual(loaded['validation']['validationScope'], 'xml-load')
            load_dir = (set(self.worker.records.iterdir()) - before).pop()
            self.assertEqual(json.loads((load_dir / 'intent.json').read_text())['operation'], 'load')
            profile = (self.worker.operations / load_dir.name / 'sandbox.sb').read_text()
            self.assertNotIn('network-outbound', profile)
            proof = {'scope': 'real-original-engine-http', 'validationRunId': record_dir.name, 'loadRunId': load_dir.name,
                     'validationScope': result['validationScope'], 'inputsStaged': 2, 'outputFiles': 0, 'offlineLoad': True}
            (self.runtime / 'validation-context-proof.json').write_text(json.dumps(proof, indent=2))
        finally:
            broker.terminate()
            broker.wait(timeout=5)
            broker.stdout.close()
            broker.stderr.close()

    def test_job_reports_budget_above_old_900_second_clamp(self):
        worker = fixtures.module.Worker(self.runtime, timeout=1200)
        inputs = [{'name': 'child.ktr', 'content': fixtures.fixture(self.catalog)},
                  {'name': 'input.csv', 'content': 'name\nalice\n'}]
        identifier = worker.launch('job', job_xml(), input_files=inputs)
        result = worker.wait(identifier)
        self.assertEqual(result['state'], 'SUCCEEDED', worker.runs[identifier]['events'])
        self.assertEqual(result['executionTimeoutSeconds'], 1200)
        policy = next(event for event in worker.runs[identifier]['events'] if event['type'] == 'execution-policy')
        self.assertEqual(policy['executionTimeoutSeconds'], 1200)
        (self.runtime / 'timeout-upper-range-proof.json').write_text(json.dumps({'runId': identifier, 'state': result['state'], 'executionTimeoutSeconds': 1200}, indent=2))

    def test_job_timeout_uses_broker_budget_and_preserves_partial_state(self):
        worker = fixtures.module.Worker(self.runtime, timeout=8)
        inputs = [{'name': 'child.ktr', 'content': fixtures.fixture(self.catalog, delay=True)},
                  {'name': 'input.csv', 'content': 'name\n' + 'alice\n' * 30}]
        identifier = worker.launch('job', job_xml(), input_files=inputs)
        result = worker.wait(identifier)
        self.assertEqual(result['state'], 'TIMED_OUT', worker.runs[identifier]['events'])
        self.assertEqual(result['executionTimeoutSeconds'], 8)
        policy = next(event for event in worker.runs[identifier]['events'] if event['type'] == 'execution-policy')
        self.assertEqual(policy['executionTimeoutSeconds'], 8)
        self.assertTrue(all(file['partial'] for file in result['files']))
        self.assertEqual(sum(event['type'] == 'job-entry' and event.get('phase') == 'BEFORE' and event.get('pluginId') == 'TRANS' for event in worker.runs[identifier]['events']), 1)
        (self.runtime / 'timeout-stop-proof.json').write_text(json.dumps({'runId': identifier, 'state': result['state'], 'executionTimeoutSeconds': 8}, indent=2))

    @unittest.skipUnless(os.environ.get('KETTLE_LONG_TIMEOUT_PROOF') == '1', 'Opt-in proof takes more than two minutes')
    def test_job_completes_after_old_120_second_limit_with_frozen_150_second_budget(self):
        inputs = [{'name': 'child.ktr', 'content': fixtures.fixture(self.catalog, delay=True)},
                  {'name': 'input.csv', 'content': 'name\n' + 'alice\n' * 123}]
        start = time.monotonic()
        identifier = self.worker.launch('job', job_xml(), input_files=inputs)
        result = self.worker.wait(identifier)
        elapsed = time.monotonic() - start
        self.assertEqual(result['state'], 'SUCCEEDED', self.worker.runs[identifier]['events'])
        self.assertGreater(elapsed, 120)
        self.assertLess(elapsed, 150)
        self.assertEqual(result['executionTimeoutSeconds'], 150)
        output = self.worker.operations / identifier / 'output/result.csv'
        self.assertEqual(len(output.read_text().splitlines()), 124)
        self.assertEqual(sum(event['type'] == 'job-entry' and event.get('phase') == 'BEFORE' and event.get('pluginId') == 'TRANS' for event in self.worker.runs[identifier]['events']), 1)
        (self.runtime / 'timeout-long-job-proof.json').write_text(json.dumps({'runId': identifier, 'state': result['state'],
            'executionTimeoutSeconds': 150, 'elapsedSeconds': elapsed, 'outputRows': 123, 'sha256': hashlib.sha256(output.read_bytes()).hexdigest()}, indent=2))


if __name__ == '__main__':
    unittest.main(verbosity=2)
