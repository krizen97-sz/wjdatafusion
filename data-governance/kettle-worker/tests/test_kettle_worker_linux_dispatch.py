"""Dispatcher contract tests only. No Docker, iptables, nsenter or original engine is run."""
import hashlib
import importlib.util
import io
import json
from pathlib import Path
import sys
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import patch

REPO = Path(__file__).resolve().parents[3]
spec = importlib.util.spec_from_file_location('worker_dispatch', REPO / 'tools/data-governance/kettle_worker.py')
worker_module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(worker_module)
XML = '<transformation><info><name>mock-only</name></info><step><name>mock</name><type>Dummy</type></step></transformation>'


class FakeConfig:
    @classmethod
    def load(cls, path):
        value = worker_module.read_private_json(path)
        for key in ['worker_root', 'operations_root', 'state_root']:
            value[key] = Path(value[key]).resolve()
        value['endpoints'] = tuple((entry['host'], entry['port']) for entry in value.get('endpoints', []))
        return SimpleNamespace(**value)


class FakeContainer:
    def __init__(self, runtime, identifier, events):
        self.runtime, self.identifier = runtime, identifier
        self.stdin = io.StringIO()
        self.stdout = io.StringIO(''.join(json.dumps(event) + '\n' for event in events))
        self.pid = 999999  # Attach-client identifier only; must never become a JVM PID.

    def identity(self):
        return self.runtime.identity_for_run(self.identifier)

    def poll(self):
        return 0

    def wait(self, timeout=None):
        return 0


class FakeLinuxRuntime:
    journals = {}
    calls = []
    launch_failure = False
    omit_terminal = False

    def __init__(self, config):
        self.config = config

    def launch(self, operation_dir, operation, preview_step='', row_limit=20, launch_id=None, stderr=None):
        identifier = operation_dir.name
        self.calls.append(('launch', identifier, operation, preview_step, row_limit, launch_id))
        self.journals[identifier] = {'kind': 'docker', 'containerId': hashlib.sha256(identifier.encode()).hexdigest(), 'sourceHash': 'a' * 64, 'nonce': launch_id, 'state': 'EXITED', 'running': False, 'exitCode': 0}
        if self.launch_failure:
            self.journals[identifier]['running'] = True
            raise RuntimeError('Mock container launch acknowledgement lost')
        if operation == 'capabilities':
            events = [{'type': 'capabilities', 'steps': [], 'jobs': [], 'engine': 'mock'}]
        elif operation in {'validate', 'job-validate'}:
            events = [{'type': 'validation', 'valid': True, 'nodes': []}]
        else:
            (operation_dir / 'output/result.txt').write_text('mock-container-artifact')
            events = [{'type': 'state', 'state': 'RUNNING'}]
            if not self.omit_terminal:
                events.append({'type': 'terminal', 'state': 'SUCCEEDED', 'errors': 0, 'nodes': []})
        return FakeContainer(self, identifier, events)

    def identity_for_run(self, identifier):
        self.calls.append(('identity', identifier))
        record = self.journals[identifier]
        return {key: record[key] for key in ['kind', 'containerId', 'sourceHash', 'nonce']}

    def status(self, identifier):
        self.calls.append(('status', identifier))
        return dict(self.journals[identifier])

    def recover(self, identifier):
        self.calls.append(('recover', identifier))
        return dict(self.journals[identifier], state='RECOVERY_REQUIRED', resubmitted=False)

    def request_stop(self, identifier, force=False, expected_nonce=None):
        if expected_nonce != self.journals[identifier]['nonce']:
            raise ValueError('Mock nonce mismatch')
        self.calls.append(('stop', identifier, force, expected_nonce))
        self.journals[identifier]['running'] = False
        return {'stopRequested': True, 'forced': force}


class LinuxDispatcherTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.root = Path(self.temporary.name).resolve()
        self.broker = self.root / 'broker'
        self.artifacts = self.root / 'artifacts'
        self.operations = self.root / 'operations'
        self.journal = self.root / 'container-journal'
        for directory in [self.broker, self.artifacts, self.operations, self.journal]:
            directory.mkdir(mode=0o700)
        (self.artifacts / 'manifest.json').write_text(json.dumps({'javaHome': '/nonexistent/mac-java-home', 'classpath': 'unused-in-linux', 'libraries': []}))
        self.configuration = self.root / 'linux.json'
        self.value = {'instance_id': 'b' * 32, 'worker_root': str(self.artifacts), 'operations_root': str(self.operations), 'state_root': str(self.journal), 'uid': 1000, 'gid': 1000, 'execution_enabled': True, 'endpoints': [{'host': '192.0.2.10', 'port': 5432}]}
        self.save_config()
        FakeLinuxRuntime.journals, FakeLinuxRuntime.calls = {}, []
        FakeLinuxRuntime.launch_failure = FakeLinuxRuntime.omit_terminal = False
        self.platform = patch.object(worker_module.sys, 'platform', 'linux')
        self.loader = patch.object(worker_module, 'load_linux_adapter', return_value=SimpleNamespace(Config=FakeConfig, LinuxRuntime=FakeLinuxRuntime))
        self.popen = patch.object(worker_module.subprocess, 'Popen', side_effect=AssertionError('No host process may run in dispatcher unit tests'))
        self.kill = patch.object(worker_module.os, 'kill', side_effect=AssertionError('Bare PID signalling is forbidden'))
        for mocked in [self.platform, self.loader, self.popen, self.kill]:
            mocked.start()

    def tearDown(self):
        for mocked in [self.kill, self.popen, self.loader, self.platform]:
            mocked.stop()
        self.temporary.cleanup()

    def save_config(self):
        worker_module.atomic_private_json(self.configuration, self.value)

    def worker(self):
        return worker_module.Worker(self.broker, linux_config=self.configuration)

    def run_complete(self):
        worker = self.worker()
        identifier = worker.launch('run', XML, run_id='mock-run')
        result = worker.wait(identifier)
        return worker, identifier, result

    def test_linux_without_private_configuration_fails_closed(self):
        with self.assertRaisesRegex(RuntimeError, '--linux-config'):
            worker_module.Worker(self.broker)
        self.configuration.chmod(0o644)
        with self.assertRaises(ValueError):
            self.worker()
        self.assertEqual(FakeLinuxRuntime.calls, [])

    def test_disabled_configuration_preserves_readonly_mode_but_cannot_launch(self):
        self.value['execution_enabled'] = False
        self.save_config()
        worker = self.worker()
        with self.assertRaisesRegex(RuntimeError, 'disabled'):
            worker.launch('run', XML, run_id='disabled')
        self.assertFalse((self.operations / 'disabled').exists())
        self.assertEqual(FakeLinuxRuntime.calls, [])

    def test_linux_rejects_mac_network_flags_and_overlapping_roots(self):
        with self.assertRaises(ValueError):
            worker_module.Worker(self.broker, linux_config=self.configuration, allow_endpoints=['127.0.0.1:15432'])
        self.value['worker_root'] = str(self.broker)
        self.save_config()
        with self.assertRaisesRegex(ValueError, 'separate'):
            self.worker()

    def test_dispatch_uses_container_identity_and_never_sandbox_exec(self):
        worker, identifier, result = self.run_complete()
        self.assertEqual(result['state'], 'SUCCEEDED')
        self.assertEqual(worker.artifacts_root, self.artifacts)
        self.assertEqual(worker.operations, self.operations)
        self.assertEqual(worker.endpoints, [{'host': '192.0.2.10', 'port': 5432}])
        self.assertEqual(result['runtimeKind'], 'linux-docker')
        self.assertEqual(result['runtimeIdentity']['kind'], 'docker')
        self.assertNotIn('nonce', result['runtimeIdentity'])
        record = json.loads((worker.records / identifier / 'record.json').read_text())
        self.assertNotIn('spawnedPid', record)
        self.assertFalse((self.operations / identifier / 'sandbox.sb').exists())
        self.assertEqual(record['runtimeIdentity']['nonce'], record['launchNonce'])

    def test_completed_restart_delegates_readonly_status_without_relaunch(self):
        worker, identifier, _ = self.run_complete()
        before = sum(call[0] == 'launch' for call in FakeLinuxRuntime.calls)
        restored = self.worker()
        self.assertEqual(restored.snapshot(identifier)['state'], 'SUCCEEDED')
        self.assertTrue(restored.snapshot(identifier)['restored'])
        self.assertIn(('status', identifier), FakeLinuxRuntime.calls)
        self.assertNotIn(('recover', identifier), FakeLinuxRuntime.calls)
        self.assertEqual(restored.launch('run', XML, run_id=identifier), identifier)
        self.assertEqual(sum(call[0] == 'launch' for call in FakeLinuxRuntime.calls), before)
        fd, _ = restored.open_file(identifier, 'result.txt')
        with worker_module.os.fdopen(fd) as stream:
            self.assertEqual(stream.read(), 'mock-container-artifact')

    def test_execution_disabled_after_completion_still_allows_readonly_recovery(self):
        _, identifier, _ = self.run_complete()
        self.value['execution_enabled'] = False
        self.save_config()
        restored = self.worker()
        self.assertEqual(restored.snapshot(identifier)['state'], 'SUCCEEDED')
        self.assertEqual(restored.launch('run', XML, run_id=identifier), identifier)
        with self.assertRaisesRegex(RuntimeError, 'disabled'):
            restored.launch('run', XML, run_id='new-disabled-run')
        self.assertEqual(sum(call[0] == 'launch' for call in FakeLinuxRuntime.calls), 1)

    def test_incomplete_restart_uses_journal_and_nonce_stop_not_pid(self):
        worker, identifier, _ = self.run_complete()
        record_path = worker.records / identifier / 'record.json'
        record = json.loads(record_path.read_text())
        record.update(finalized=False, state='RUNNING')
        worker_module.atomic_private_json(record_path, record)
        FakeLinuxRuntime.journals[identifier]['running'] = True
        restored = self.worker()
        self.assertEqual(restored.snapshot(identifier)['state'], 'RECOVERY_REQUIRED')
        self.assertIn(('recover', identifier), FakeLinuxRuntime.calls)
        stopped = restored.stop(identifier)
        self.assertEqual(stopped['state'], 'RECOVERY_REQUIRED')
        self.assertIn(('stop', identifier, False, record['launchNonce']), FakeLinuxRuntime.calls)
        with self.assertRaises(ValueError):
            restored.launch('run', XML, run_id=identifier)

    def test_changed_container_nonce_cannot_be_adopted_or_stopped(self):
        _, identifier, _ = self.run_complete()
        FakeLinuxRuntime.journals[identifier]['nonce'] = 'f' * 64
        restored = self.worker()
        self.assertEqual(restored.snapshot(identifier)['state'], 'RECOVERY_REQUIRED')
        with self.assertRaises(ValueError):
            restored.stop(identifier)
        self.assertFalse(any(call[0] == 'stop' for call in FakeLinuxRuntime.calls))

    def test_ambiguous_container_launch_is_recovery_required_never_replayed(self):
        worker = self.worker()
        FakeLinuxRuntime.launch_failure = True
        identifier = worker.launch('run', XML, run_id='ambiguous')
        self.assertEqual(worker.snapshot(identifier)['state'], 'RECOVERY_REQUIRED')
        self.assertFalse(worker.snapshot(identifier)['finalized'])
        self.assertIn(('recover', identifier), FakeLinuxRuntime.calls)
        with self.assertRaises(ValueError):
            worker.launch('run', XML, run_id=identifier)
        self.assertEqual(sum(call[0] == 'launch' for call in FakeLinuxRuntime.calls), 1)

    def test_container_exit_without_native_terminal_remains_unknown(self):
        FakeLinuxRuntime.omit_terminal = True
        worker, identifier, result = self.run_complete()
        self.assertEqual(result['state'], 'RECOVERY_REQUIRED')
        self.assertFalse(result['finalized'])
        with self.assertRaises(ValueError):
            worker.launch('run', XML, run_id=identifier)


if __name__ == '__main__':
    unittest.main(verbosity=2)
