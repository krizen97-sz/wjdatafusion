import json
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import kettle_business_fixture as fixture
import run_kettle_business_fixture as runner


class ExecutionBoundaryTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory(prefix='rynew-native-execution-test-')
        self.root = Path(self.temporary.name)
        (self.root / 'prepared').mkdir()
        runner.write_json(self.root / 'owner.json', {'owner': runner.OWNER, 'root': str(self.root),
                            'state': 'PREPARED_NOT_SUBMITTED', 'runs': {}})
        files = {'ordinary.ktr': '<transformation><step><name>synthetic</name><type>Dummy</type></step></transformation>',
                 'ordinary-composed-fixture.kjb': '<job><entries><entry><type>TRANS</type><filename>${INPUT_DIR}/ordinary.ktr</filename></entry></entries></job>'}
        for name, content in files.items():
            (self.root / 'prepared' / name).write_text(content)
        self.manifest = {'files': {name: {'sha256': fixture.sha(content)} for name, content in files.items()},
                         'graphs': {'ordinary': {'pluginTypes': ['Dummy']}}}
        runner.write_json(self.root / 'prepared/manifest.json', self.manifest)

    def tearDown(self):
        self.temporary.cleanup()

    def test_no_original_graph_request_before_broker_ready_and_no_changed_inputs(self):
        with patch.object(runner, 'worker_request') as request:
            with self.assertRaises(RuntimeError):
                runner.run(self.root, 'ordinary')
            request.assert_not_called()
            self.assertFalse((self.root / 'ordinary-run-journal.json').exists())
            (self.root / 'prepared/ordinary.ktr').write_text('<transformation changed="yes"/>')
            with self.assertRaises(RuntimeError):
                runner.run(self.root, 'ordinary', broker_ready=True)
            request.assert_not_called()

    def test_submit_intent_precedes_network_and_unknown_submission_is_never_replayed(self):
        worker_root = self.root / 'worker'
        worker_root.mkdir()
        (worker_root / 'manifest.json').write_text('{}')
        calls = []
        def request(method, path, body=None):
            calls.append((method, path))
            journal = json.loads((self.root / 'ordinary-run-journal.json').read_text())
            if path == '/capabilities':
                return {'steps': [{'id': 'Dummy', 'className': 'synthetic.DummyMeta', 'loadable': True}]}
            if method == 'PUT':
                self.assertEqual('PREPARING', journal['state'])
                self.assertEqual(fixture.sha(body['xml']), journal['xmlSha256'])
                self.assertEqual('ordinary.ktr', body['inputFiles'][0]['name'])
                return {'validation': {'valid': True}}
            if method == 'POST':
                self.assertEqual('SUBMITTING', journal['state'])
                self.assertEqual(body['runId'], journal['runId'])
                raise TimeoutError('synthetic unknown acceptance')
            return {'state': 'FAILED', 'finishedAt': 1, 'exitCode': 1} if '/events?' not in path else {'events': [], 'nextCursor': 0}
        with patch.object(runner, 'WORKER', worker_root), patch.object(runner, 'worker_request', side_effect=request), \
                patch.object(runner, 'probe', return_value={'result': {'ordinary': {'zookeeperOffset': '0', 'owners': []}, 'endOffsets': {'ordinary': 10000}}}), \
                patch.object(runner, 'capture_ftp', return_value=self.root):
            with self.assertRaises(RuntimeError):
                runner.run(self.root, 'ordinary', broker_ready=True)
            journal = json.loads((self.root / 'ordinary-run-journal.json').read_text())
            self.assertEqual('SUBMISSION_UNKNOWN', journal['state'])
            self.assertEqual(1, sum(method == 'POST' for method, path in calls))
            before = len(calls)
            with self.assertRaises(RuntimeError):
                runner.run(self.root, 'ordinary', broker_ready=True)
            self.assertEqual(before, len(calls))
            with self.assertRaises(RuntimeError):
                runner.run(self.root, 'ordinary', resume=True)
            self.assertEqual(1, sum(method == 'POST' for method, path in calls))

    def test_consumed_or_owned_initial_group_is_rejected_before_worker_requests(self):
        for offset, owners in [('7', []), ('0', ['0'])]:
            with self.subTest(offset=offset, owners=owners):
                journal = self.root / 'ordinary-run-journal.json'
                if journal.exists():
                    journal.unlink()
                audit = {'result': {'ordinary': {'zookeeperOffset': offset, 'owners': owners}, 'endOffsets': {'ordinary': 10000}}}
                with patch.object(runner, 'probe', return_value=audit), patch.object(runner, 'worker_request') as request:
                    with self.assertRaises(RuntimeError):
                        runner.run(self.root, 'ordinary', broker_ready=True)
                    request.assert_not_called()

    def test_credential_endpoint_and_existing_database_name_guards(self):
        runtime = self.root / 'local-runtime'
        (runtime / 'private').mkdir(parents=True)
        path = runtime / 'private/postgres-credentials.json'
        runner.write_json(path, {'host': '192.0.2.1', 'port': 15432, 'username': 'synthetic', 'password': 'synthetic', 'database': 'postgres'})
        os.chmod(path, 0o600)
        with patch.object(runner, 'RUNTIME', runtime), patch.object(runner.subprocess, 'run') as execute:
            with self.assertRaises(RuntimeError):
                runner.credentials('postgres', 15432)
            with self.assertRaises(RuntimeError):
                runner.database_command('unowned_business_database', 'SELECT 1;', config={'host': '127.0.0.1', 'port': 15432, 'database': 'postgres'})
            execute.assert_not_called()


if __name__ == '__main__':
    unittest.main()
