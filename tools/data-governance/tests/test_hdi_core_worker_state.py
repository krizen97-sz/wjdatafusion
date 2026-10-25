"""Private state/API boundaries only. These tests do not simulate ETL success."""
import importlib.util
import base64
import hashlib
import json
from pathlib import Path
import sys
import tempfile
import threading
import unittest
from unittest.mock import patch, Mock
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
        self.worker.stop_timeout = 45
        self.worker.shutdown_deadline = None
        self.worker.closed_result = None
        self.worker.coordinator = None
        self.worker.lock_fd = None
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

    def test_connection_grants_are_frozen_and_secret_values_live_only_in_private_file(self):
        binding = {'connectionId': '31', 'connectionRevision': '2', 'ownerId': '1', 'kind': 'postgresql',
                   'endpoints': [{'host': '127.0.0.1', 'address': '127.0.0.1', 'port': 15432}]}
        request = {**self.request, 'connectionBindings': [binding], 'redactionValues': ['test-secret-never-public']}
        result = self.worker.reserve(request)
        self.assertEqual(self.worker.reserve(request), result)
        self.assertEqual(result['connectionBindings'], [binding])
        path = Path(result['outputsDir']).parent
        self.assertNotIn('test-secret-never-public', (path / 'state.json').read_text())
        self.assertNotIn('test-secret-never-public', json.dumps(result))
        secret = path / 'private/connection-secrets.json'
        self.assertEqual(secret.stat().st_mode & 0o777, 0o600)
        self.assertEqual(json.loads(secret.read_text()), ['test-secret-never-public'])
        with self.assertRaisesRegex(ProtocolError, 'RESERVATION_CONFLICT'):
            self.worker.reserve({**request, 'redactionValues': ['different-secret']})
        with self.assertRaisesRegex(ProtocolError, 'RESERVATION_CONFLICT'):
            self.worker.reserve({**request, 'connectionBindings': [{**binding, 'connectionRevision': '3'}]})
        secret.write_text('["changed-secret"]')
        compiled = b'{"ok":true}'
        with patch.object(module, 'validate_compiled'), patch.object(module, 'NativeRun') as run:
            with self.assertRaisesRegex(ProtocolError, 'CONNECTOR_SECRETS_CHANGED'):
                self.worker.submit({**self.request, 'compiledBase64': base64.b64encode(compiled).decode(),
                                    'sha256': hashlib.sha256(compiled).hexdigest()})
            run.assert_not_called()
        self.assertEqual(self.worker.records[self.request['runId']]['state'], 'RESERVED')

    def test_mysql_compiled_package_must_match_installed_manifest_before_execution(self):
        binding = {'connectionId': '31', 'connectionRevision': '2', 'ownerId': '1', 'kind': 'mysql',
                   'endpoints': [{'host': '127.0.0.1', 'address': '127.0.0.1', 'port': 13306}]}
        self.worker.reserve({**self.request, 'connectionBindings': [binding]})
        def submit(metadata):
            raw = json.dumps({'metadataSnapshot': metadata}).encode()
            return self.worker.submit({**self.request, 'compiledBase64': base64.b64encode(raw).decode(),
                                       'sha256': hashlib.sha256(raw).hexdigest()})
        with patch.object(module, 'validate_compiled'), patch.object(module, 'NativeRun') as run:
            with self.assertRaisesRegex(ProtocolError, 'MYSQL_PLATFORM_ADAPTER_REQUIRED'):
                submit({})
            self.worker.platform_adapters = {'root': '/private/platform', 'manifestSha256': 'a' * 64}
            for metadata in ({}, {'platformAdapterManifestSha256': 'b' * 64}):
                with self.assertRaisesRegex(ProtocolError, 'MYSQL_PLATFORM_ADAPTER_MISMATCH'):
                    submit(metadata)
            run.assert_not_called()
            self.assertEqual(self.worker.records[self.request['runId']]['state'], 'RESERVED')
            submit({'platformAdapterManifestSha256': 'a' * 64})
            run.assert_called_once()

    def test_original_plain_connector_credentials_are_redacted_from_public_logs(self):
        self.worker.redis_password = 'owned-redis-password'
        self.worker.reserve(self.request)
        record = self.worker.records[self.request['runId']]
        task = module.NativeRun(self.worker, record, {}, ['original-ftp-password'])
        (task.root / 'logs/core.log').write_text('original adapter config={value:original-ftp-password}\n')
        task.log_progress(final=True)
        text = (task.root / 'logs/safe.log').read_text()
        self.assertNotIn('original-ftp-password', text)
        self.assertIn('<redacted>', text)

    def test_original_jdbc_warnings_never_change_engine_finish_or_invent_row_metrics(self):
        self.worker.redis_password = 'owned-redis-password'
        self.worker.reserve(self.request)
        record = self.worker.records[self.request['runId']]
        self.worker.persist(record, state='SUCCEEDED', nativeState='finish', finalized=True)
        graph = {'streams': [{'modules': [{'adapter': {'adapter_name': 'postgresqlOutput'}}]}]}
        task = module.NativeRun(self.worker, record, {'flowjson': {'base64': base64.b64encode(json.dumps(graph).encode()).decode()}})
        prefix = '2026-10-25 13:05:25.706 [etl-plugin-runner-21001-2] '
        lines = [prefix + 'ERROR c.h.idatafusion.etl.adapter.output.jdbc.JdbcOutput[927] - insert data (fields: message) failed.',
                 prefix + 'INFO c.h.idatafusion.etl.adapter.output.jdbc.JdbcOutput[939] - insert 0 records , skip 3 records ',
                 'java.sql.SQLException: do not expose this diagnostic payload']
        (task.root / 'logs/core.log').write_text('\n'.join(lines) + '\n')
        task.log_progress(final=True)
        view = self.worker.view(record)
        self.assertEqual(view['outputWarningCodes'], ['JDBC_OUTPUT_ERROR_OBSERVED', 'JDBC_OUTPUT_ROWS_SKIPPED'])
        self.assertEqual(view['state'], 'SUCCEEDED'); self.assertEqual(view['nativeState'], 'finish')
        self.assertNotIn('outputRows', view); self.assertNotIn('failedRows', view)
        self.assertNotIn('diagnostic payload', json.dumps(view))

    def test_jdbc_observations_ignore_success_other_flow_and_unrelated_vendor_errors(self):
        own = '2026-10-25 13:05:25.706 [etl-plugin-runner-21001-2] '
        success = own + 'INFO c.h.idatafusion.etl.adapter.output.jdbc.JdbcOutput[939] - insert 3 records , skip 0 records '
        other = success.replace('21001-2', '31001-2').replace('skip 0', 'skip 3')
        input_error = own + 'ERROR c.h.idatafusion.etl.adapter.input.jdbc.JdbcInput[927] - insert data (fields: message) failed.'
        cold_start = '2026-10-25 13:05:25.706 [Thread-6] ERROR c.h.idatafusion.etl.core.task.utils.TaskUtils[104] - history missing'
        self.assertEqual(module.jdbc_output_observations('\n'.join([success, other, input_error, cold_start]), '21001'), [])
        skipped = own + 'INFO com.hikvision.idatafusion.etl.adapter.output.jdbc.JdbcOutput[939] - insert 2 records , skip 1 records '
        self.assertEqual(module.jdbc_output_observations(skipped, '21001'), ['JDBC_OUTPUT_ROWS_SKIPPED'])
        self.assertEqual(module.jdbc_output_observations(skipped + ' value supplied by the source', '21001'), [])

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

    def test_explicit_cancel_persists_intent_before_signalling_original_stop(self):
        self.worker.reserve(self.request)
        record = self.worker.records[self.request['runId']]
        self.worker.persist(record, state='RUNNING')
        cancel = Mock()
        cancel.set.side_effect = lambda: self.assertTrue(json.loads((self.worker.runs_root / record['runId'] / 'state.json').read_text())['stopRequestedByUser'])
        self.worker.tasks[record['runId']] = Mock(cancel=cancel)
        self.worker.dispatch({'op': 'cancel', **self.request})
        cancel.set.assert_called_once()

    def test_confirmed_original_stop_partial_artifact_preserves_cancelled_status(self):
        self.worker.reserve(self.request)
        record = self.worker.records[self.request['runId']]
        (Path(record['outputsDir']) / 'sample.csv').write_bytes(b'field\nvalue\n')
        record.update(state='CANCELLED', nativeState='stop', finalized=True, stopRequestedByUser=True,
                      artifactCompleteness='PARTIAL', artifacts=results_manifest(record['outputsDir']))
        result = self.worker.artifact(record, {'artifactId': record['artifacts'][0]['artifactId']})
        self.assertEqual(base64.b64decode(result['base64']), b'field\nvalue\n')
        self.assertEqual(self.worker.view(record)['state'], 'CANCELLED')
        self.assertEqual(self.worker.view(record)['artifactCompleteness'], 'PARTIAL')
        self.assertIs(self.worker.view(record)['stopRequestedByUser'], True)
        (Path(record['outputsDir']) / 'sample.csv').write_bytes(b'field\nother\n')
        with self.assertRaisesRegex(ProtocolError, 'ARTIFACT_CHANGED'):
            self.worker.artifact(record, {'artifactId': record['artifacts'][0]['artifactId']})

    def test_partial_results_never_open_shutdown_timeout_failed_or_unconfirmed_states(self):
        self.worker.reserve(self.request)
        record = self.worker.records[self.request['runId']]
        original = {**record, 'state': 'CANCELLED', 'nativeState': 'stop', 'finalized': True,
                    'stopRequestedByUser': True, 'artifactCompleteness': 'PARTIAL'}
        for changed in ({'stopRequestedByUser': False}, {'stopRequestedByUser': None}, {'state': 'TIMED_OUT'},
                        {'state': 'FAILED'}, {'state': 'INTERRUPTED'}, {'nativeState': 'running'},
                        {'finalized': False}, {'errorCode': 'OUTPUT_LIMIT'}, {'errorCode': 'OUTPUT_VALIDATION_FAILED'},
                        {'errorCode': 'ORIGINAL_STOP_TIMEOUT_PROCESS_CLOSED'}, {'recoveryRequired': True}):
            candidate = {**original, **changed}
            self.assertFalse(module.explicit_original_stop(candidate))
            with self.assertRaisesRegex(ProtocolError, 'ARTIFACT_NOT_AVAILABLE'):
                self.worker.artifact(candidate, {'artifactId': 'unavailable'})
        with self.assertRaisesRegex(ProtocolError, 'ARTIFACT_NOT_AVAILABLE'):
            self.worker.artifact({**original, 'artifactCompleteness': None}, {'artifactId': 'unavailable'})

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


    def test_original_stop_default_and_bounds(self):
        self.assertEqual(module.stop_timeout({}), 45)
        self.assertEqual(module.stop_timeout({'stopTimeoutSeconds': 60}), 60)
        for value in (44, 61, True, '45', 45.0):
            with self.assertRaisesRegex(ProtocolError, 'INVALID_STOP_TIMEOUT'):
                module.stop_timeout({'stopTimeoutSeconds': value})

    def test_normal_cancel_retains_vendor_grace_but_shutdown_has_shared_budget(self):
        task = module.NativeRun.__new__(module.NativeRun)
        task.worker = self.worker
        self.assertEqual(task.original_stop_deadline(100), 145)
        self.worker.stop_timeout = 60
        self.assertEqual(task.original_stop_deadline(100), 160)
        self.worker.shutdown_deadline = 155
        self.assertEqual(task.original_stop_deadline(100), 140)

    def test_four_runs_share_one_shutdown_deadline(self):
        clock = [100.0]
        tasks = []
        timeouts = []
        for index in range(4):
            task = Mock()
            task.record = {'runId': str(index), 'finalized': True}
            task.thread.is_alive.return_value = False
            def join(timeout):
                self.assertTrue(all(item.cancel.set.called for item in tasks))
                timeouts.append(timeout)
                clock[0] += min(45, timeout)
            task.thread.join.side_effect = join
            tasks.append(task)
        self.worker.tasks = {str(i): task for i, task in enumerate(tasks)}
        self.worker.coordinator = Mock()
        with patch.object(module.time, 'monotonic', side_effect=lambda: clock[0]):
            self.worker.close()
        self.assertEqual(timeouts, [60, 15, 0, 0])
        self.worker.coordinator.stop.assert_called_once_with(grace=5, deadline=170)
        self.assertTrue(self.worker.closed_result)

    def test_unconfirmed_shutdown_keeps_recovery_barrier(self):
        task = Mock()
        task.record = {'runId': 'unconfirmed', 'finalized': False}
        task.thread.is_alive.return_value = True
        self.worker.tasks = {'unconfirmed': task}
        with self.assertRaisesRegex(ProtocolError, 'STOP_UNCONFIRMED'):
            self.worker.close()
        self.assertFalse(self.worker.closed_result)
        self.assertEqual(json.loads((self.root / '.shutdown-unconfirmed.json').read_text())['runs'], ['unconfirmed'])


    def test_failed_recovery_marker_write_does_not_release_memory_barrier(self):
        task = Mock()
        task.record = {'runId': 'unconfirmed', 'finalized': False}
        task.thread.is_alive.return_value = True
        self.worker.tasks = {'unconfirmed': task}
        with patch.object(module, 'atomic_json', side_effect=OSError('test full disk')):
            with self.assertRaises(OSError):
                self.worker.close()
        self.assertFalse(self.worker.closed_result)
        with self.assertRaisesRegex(ProtocolError, 'STOP_UNCONFIRMED'):
            self.worker.close()


if __name__ == '__main__':
    unittest.main()
