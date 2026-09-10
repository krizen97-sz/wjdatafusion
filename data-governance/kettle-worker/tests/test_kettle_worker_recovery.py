"""Real broker restart acceptance; all processes/files belong to a new private runtime."""
import importlib.util
import json
import os
from pathlib import Path
import subprocess
import sys
import time
import unittest
import urllib.error
import urllib.request
import uuid

spec = importlib.util.spec_from_file_location('fixtures', Path(__file__).with_name('test_kettle_worker.py'))
fixtures = importlib.util.module_from_spec(spec)
spec.loader.exec_module(fixtures)
module = fixtures.module


@unittest.skipUnless(os.environ.get('KETTLE_WORKER_RUNTIME'), 'Requires explicitly prepared original runtime')
class BrokerRecoveryTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        source = Path(os.environ['KETTLE_WORKER_RUNTIME']).resolve()
        cls.root = module.private_dir(source / 'recovery-tests' / uuid.uuid4().hex)
        for name in ['lib', 'classes']:
            (cls.root / name).symlink_to((source / name).resolve(), target_is_directory=True)
        (cls.root / 'manifest.json').write_text((source / 'manifest.json').read_text())
        cls.broker = None
        cls.start_broker()
        cls.catalog = cls.request('/capabilities')
        cls.evidence = {'runtime': str(cls.root), 'completed': {}, 'interrupted': {}}

    @classmethod
    def start_broker(cls):
        cls.log = (cls.root / ('broker-' + uuid.uuid4().hex + '.log')).open('w')
        cls.broker = subprocess.Popen([sys.executable, str(fixtures.REPO / 'tools/data-governance/kettle_worker.py'), 'serve', '--runtime', str(cls.root), '--port', '0', '--timeout', '60'], stdout=subprocess.PIPE, stderr=cls.log, text=True)
        line = cls.broker.stdout.readline()
        if not line:
            raise AssertionError('Private recovery broker failed to start')
        cls.url = json.loads(line)['url']
        cls.token = (cls.root / '.broker-token').read_text().strip()

    @classmethod
    def end_broker(cls, crash=False):
        if cls.broker and cls.broker.poll() is None:
            if crash:
                cls.broker.kill()
            else:
                cls.broker.terminate()
            cls.broker.wait(timeout=10)
            cls.broker.stdout.close()
            cls.log.close()
        cls.broker = None

    @classmethod
    def tearDownClass(cls):
        cls.end_broker()
        (cls.root / 'recovery-acceptance.json').write_text(json.dumps(cls.evidence, indent=2))

    @classmethod
    def request(cls, path, method='GET', body=None, raw=False):
        headers = {'Authorization': 'Bearer ' + cls.token, 'Content-Type': 'application/json'}
        request = urllib.request.Request(cls.url + path, None if body is None else json.dumps(body).encode(), headers, method=method)
        with urllib.request.urlopen(request, timeout=20) as response:
            content = response.read()
            return content if raw else json.loads(content)

    def prepare_run(self, delay=False):
        identifier = 'recovery-' + uuid.uuid4().hex
        saved = self.request('/transformations/' + identifier, 'PUT', {'xml': fixtures.fixture(self.catalog, delay=delay)})
        self.assertTrue(saved['validation']['valid'])
        body = {'transformationId': identifier, 'runId': identifier, 'inputFiles': [{'name': 'input.csv', 'content': 'name\n' + ('alice\n' * (100 if delay else 1))}]}
        self.request('/runs', 'POST', body)
        return identifier, body

    def until(self, identifier, predicate):
        deadline = time.monotonic() + 15
        while time.monotonic() < deadline:
            state = self.request('/runs/' + identifier)
            if predicate(state):
                return state
            time.sleep(.03)
        self.fail('Private run did not reach expected state')

    def test_completed_run_restores_events_bytes_and_idempotency(self):
        identifier, body = self.prepare_run()
        before = self.until(identifier, lambda value: value.get('finalized') and value['state'] == 'SUCCEEDED')
        data = self.request('/runs/' + identifier + '/files/result.csv', raw=True)
        events = self.request('/runs/' + identifier + '/events')['events']
        record = self.root / 'run-records' / identifier / 'record.json'
        metadata = record.read_bytes()
        modified = record.stat().st_mtime_ns
        self.end_broker()
        self.start_broker()
        restored = self.request('/runs/' + identifier)
        self.assertEqual(restored['state'], 'SUCCEEDED')
        self.assertTrue(restored['restored'])
        self.assertEqual(self.request('/runs/' + identifier + '/events')['events'], events)
        self.assertEqual(self.request('/runs/' + identifier + '/files/result.csv', raw=True), data)
        repeated = self.request('/runs', 'POST', body)
        self.assertEqual(repeated['id'], identifier)
        self.assertEqual(repeated['spawnedAt'], before['spawnedAt'])
        self.assertEqual(record.read_bytes(), metadata)
        self.assertEqual(record.stat().st_mtime_ns, modified)
        with self.assertRaises(urllib.error.HTTPError) as conflict:
            self.request('/runs', 'POST', dict(body, rowLimit=2))
        self.assertEqual(conflict.exception.code, 400)
        self.evidence['completed'] = {'id': identifier, 'restoredState': restored['state'], 'eventsEqual': True, 'bytesEqual': True, 'idempotentNoNewProcess': True, 'recordUnchanged': True}

    def test_crashed_inflight_run_is_never_replayed(self):
        identifier, body = self.prepare_run(delay=True)
        self.until(identifier, lambda value: value['state'] == 'RUNNING')
        record_path = self.root / 'run-records' / identifier / 'record.json'
        original_record = json.loads(record_path.read_text())
        self.end_broker(crash=True)
        self.start_broker()
        state = self.request('/runs/' + identifier)
        self.assertEqual(state['state'], 'RECOVERY_REQUIRED')
        self.assertFalse(state['finalized'])
        with self.assertRaises(urllib.error.HTTPError) as replay:
            self.request('/runs', 'POST', body)
        self.assertEqual(replay.exception.code, 400)
        self.assertEqual(json.loads(record_path.read_text())['spawnedAt'], original_record['spawnedAt'])
        stopped = self.request('/runs/' + identifier + '/stop', 'POST', {})
        self.assertEqual(stopped['state'], 'RECOVERY_REQUIRED')
        intent = json.loads((record_path.parent / 'intent.json').read_text())
        self.assertEqual((self.root / 'operations' / identifier / 'control.stop').read_text(), 'STOP:' + intent['launchNonce'])
        self.assertTrue(all(file['partial'] for file in stopped['files']))
        self.evidence['interrupted'] = {'id': identifier, 'state': state['state'], 'replayRejected': True, 'newProcessStarted': False, 'nonceBoundStopFile': True, 'partialOutputs': True}

    def test_stop_immediately_after_spawn_uses_owned_control(self):
        identifier, _ = self.prepare_run(delay=True)
        self.request('/runs/' + identifier + '/stop', 'POST', {})
        finished = self.until(identifier, lambda value: value.get('finalized'))
        self.assertEqual(finished['state'], 'STOPPED')
        self.assertFalse(finished.get('forcedStop', False))
        self.evidence['earlyStop'] = {'id': identifier, 'state': finished['state'], 'forced': False}

    def test_corrupt_reserved_identity_cannot_start_or_signal_a_process(self):
        identifier = 'reserved-' + uuid.uuid4().hex
        self.request('/transformations/' + identifier, 'PUT', {'xml': fixtures.fixture(self.catalog)})
        operation = module.private_dir(self.root / 'operations' / identifier)
        output = module.private_dir(operation / 'output')
        (output / 'keep.txt').write_text('synthetic-owned-marker')
        records = module.private_dir(self.root / 'run-records' / identifier)
        module.atomic_private_json(records / 'intent.json', {'schemaVersion': 1, 'id': identifier, 'fingerprint': '0' * 64, 'launchNonce': '1' * 64})
        (records / 'record.json').write_text('{corrupt-reserved-record')
        (records / 'record.json').chmod(0o600)
        self.end_broker()
        self.start_broker()
        self.assertEqual(self.request('/runs/' + identifier)['state'], 'RECOVERY_REQUIRED')
        with self.assertRaises(urllib.error.HTTPError) as replay:
            self.request('/runs', 'POST', {'transformationId': identifier, 'runId': identifier})
        self.assertEqual(replay.exception.code, 400)
        with self.assertRaises(urllib.error.HTTPError) as stop:
            self.request('/runs/' + identifier + '/stop', 'POST', {})
        self.assertEqual(stop.exception.code, 400)
        self.assertEqual((output / 'keep.txt').read_text(), 'synthetic-owned-marker')
        self.assertEqual((records / 'record.json').read_text(), '{corrupt-reserved-record')
        self.evidence['corruptReservation'] = {'id': identifier, 'state': 'RECOVERY_REQUIRED', 'replayRejected': True, 'unverifiedStopRejected': True, 'originalFilesUnchanged': True}


if __name__ == '__main__':
    unittest.main(verbosity=2)
