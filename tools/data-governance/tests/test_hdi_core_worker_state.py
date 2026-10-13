"""Private state/API boundaries only. These tests do not simulate ETL success."""
import importlib.util
import json
from pathlib import Path
import sys
import tempfile
import threading
import unittest
from unittest.mock import patch
import uuid

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import hdi_core_worker as module
from hdi_core_protocol import ProtocolError, results_manifest


class WorkerStateTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name).resolve()
        self.worker = module.Worker.__new__(module.Worker)
        self.worker.root = self.root
        self.worker.runs_root = self.root / 'runs'
        self.worker.runs_root.mkdir(mode=0o700)
        self.worker.guard = threading.RLock()
        self.worker.records, self.worker.tasks = {}, {}
        self.worker.closing = False
        self.worker.max_runs = 1
        self.worker.config = {}
        self.worker.java_mode = 'normal'
        self.request = {'runId': str(uuid.uuid4()), 'ownerId': '1', 'businessTaskId': '17',
                        'revision': '2', 'nativeFlowId': '21001', 'nativeInstanceId': '21002',
                        'execId': '21003', 'workspaceId': '1', 'metadataIdBase': '22000', 'mode': 'test'}

    def tearDown(self):
        self.temp.cleanup()

    def test_reservation_is_private_idempotent_and_real_output_is_a_child(self):
        first = self.worker.reserve(self.request)
        self.assertEqual(first, self.worker.reserve(self.request))
        self.assertEqual(Path(first['outputsDir']).parent, Path(first['authorizedOutputRoot']))
        self.assertTrue(Path(first['outputsDir']).is_dir())
        self.assertEqual((Path(first['outputsDir']).parent / 'state.json').stat().st_mode & 0o777, 0o600)
        self.assertEqual(first['state'], 'RESERVED')

    def test_different_owner_cannot_observe_or_cancel_run(self):
        self.worker.reserve(self.request)
        for op in ('status', 'cancel', 'logs', 'artifact'):
            with self.assertRaisesRegex(ProtocolError, 'NOT_FOUND'):
                self.worker.dispatch({'op': op, 'ownerId': '2', 'runId': self.request['runId']})

    def test_reserved_cancel_does_not_claim_original_execution(self):
        self.worker.reserve(self.request)
        result = self.worker.dispatch({'op': 'cancel', **self.request})
        self.assertEqual(result['state'], 'CANCELLED')
        self.assertTrue(result['finalized'])
        self.assertNotIn('nativeState', result)
        self.assertEqual(self.worker.tasks, {})

    def test_native_ids_remain_reserved_even_after_cancel(self):
        self.worker.reserve(self.request)
        self.worker.dispatch({'op': 'cancel', **self.request})
        with self.assertRaisesRegex(ProtocolError, 'NATIVE_IDS_ALREADY_RESERVED'):
            self.worker.reserve({**self.request, 'runId': str(uuid.uuid4())})

    def test_unknown_and_unconfirmed_terminal_states_occupy_capacity(self):
        self.worker.reserve(self.request)
        record = self.worker.records[self.request['runId']]
        for state, finalized in [('UNKNOWN', False), ('FAILED', False), ('RUNNING', False)]:
            record.update(state=state, finalized=finalized)
            self.assertEqual(self.worker.dispatch({'op': 'hello'})['capacity']['available'], 0)
        record.update(state='FAILED', finalized=True)
        self.assertEqual(self.worker.dispatch({'op': 'hello'})['capacity']['available'], 1)

    def test_failed_state_write_keeps_prior_acknowledged_state(self):
        self.worker.reserve(self.request)
        record = self.worker.records[self.request['runId']]
        with patch.object(module, 'atomic_json', side_effect=OSError('test disk failure')):
            with self.assertRaises(OSError):
                self.worker.persist(record, state='SUBMITTING')
        self.assertEqual(record['state'], 'RESERVED')
        self.assertEqual(json.loads((self.worker.runs_root / record['runId'] / 'state.json').read_text())['state'], 'RESERVED')

    def test_same_size_artifact_change_is_not_served(self):
        self.worker.reserve(self.request)
        record = self.worker.records[self.request['runId']]
        output = Path(record['outputsDir']) / 'sample.csv'
        output.write_bytes(b'aaa')
        record.update(state='SUCCEEDED', finalized=True, artifacts=results_manifest(record['outputsDir']))
        public = self.worker.view(record)
        self.assertNotIn('_fileIdentity', public['artifacts'][0])
        output.write_bytes(b'bbb')
        with self.assertRaisesRegex(ProtocolError, 'ARTIFACT_CHANGED'):
            self.worker.artifact(record, {'artifactId': public['artifacts'][0]['artifactId']})


if __name__ == '__main__':
    unittest.main()
