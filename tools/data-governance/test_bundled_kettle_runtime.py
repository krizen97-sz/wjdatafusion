import io
import importlib.util
import select
import sys
import time
import urllib.request
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import threading
import unittest
from unittest.mock import Mock, patch

import bundled_kettle_runtime as bundled
import package_kettle_runtime as packer
import kettle_worker as broker


def fixture_bundle(root):
    (root / 'lib').mkdir(parents=True)
    (root / 'classes').mkdir()
    (root / 'lib/kettle-6.1.0.7.36.jar').write_bytes(b'original-test-fixture')
    (root / 'classes/KettleWorker.class').write_bytes(b'compiled-test-fixture')
    for script in bundled.SCRIPTS:
        (root / script).write_text('# test fixture')
    files = [{'path': str(p.relative_to(root)), 'sha256': broker.file_digest(p)} for p in root.rglob('*') if p.is_file()]
    document = {'schemaVersion': 1, 'engine': 'original-kettle', 'javaMajor': 17, 'classpath': ['classes', 'lib/kettle-6.1.0.7.36.jar'], 'files': files}
    (root / bundled.MANIFEST).write_text(json.dumps(document))
    return document


class BundleArtifactTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name).resolve()
        self.bundle = self.root / 'bundle'
        self.manifest = fixture_bundle(self.bundle)

    def tearDown(self):
        self.temp.cleanup()

    def test_relocated_bundle_rebuilds_absolute_paths_without_credentials_or_history(self):
        relocated = self.root / 'relocated'
        shutil.move(self.bundle, relocated)
        with bundled.owned_runtime(self.root / 'private') as state:
            rebuilt = bundled.install_runtime(relocated, state, Path('/explicit/java'))
            self.assertEqual(str(state / 'classes') + os.pathsep + str(state / 'lib/kettle-6.1.0.7.36.jar'), rebuilt['classpath'])
            self.assertNotIn(str(self.bundle), json.dumps(rebuilt))
            self.assertFalse((state / '.broker-token').exists())
            self.assertFalse((state / 'run-records').exists())
            bundled.install_runtime(relocated, state, Path('/explicit/java'))

    def test_changed_artifact_rejected_before_install(self):
        (self.bundle / 'classes/KettleWorker.class').write_bytes(b'changed')
        with self.assertRaisesRegex(ValueError, 'checksum'):
            bundled.verify_bundle(self.bundle)

    def test_unlisted_jars_and_private_state_are_rejected(self):
        for name in ('lib/untracked.jar', '.broker-token'):
            path = self.bundle / name
            path.write_text('must not package')
            with self.assertRaisesRegex(ValueError, 'unlisted'):
                bundled.verify_bundle(self.bundle)
            path.unlink()

    def test_escape_and_duplicate_manifest_paths_are_rejected(self):
        for value in ('../secret', '/etc/passwd', 'lib/../secret', 'lib\\secret'):
            document = dict(self.manifest, files=[{'path': value, 'sha256': 'a' * 64}])
            (self.bundle / bundled.MANIFEST).write_text(json.dumps(document))
            with self.assertRaises(ValueError):
                bundled.verify_bundle(self.bundle)
        document = dict(self.manifest, files=self.manifest['files'] * 2)
        (self.bundle / bundled.MANIFEST).write_text(json.dumps(document))
        with self.assertRaisesRegex(ValueError, 'duplicate'):
            bundled.verify_bundle(self.bundle)

    def test_symbolic_artifacts_and_unverified_classpath_are_rejected(self):
        target = self.bundle / 'classes/KettleWorker.class'
        target.unlink()
        target.symlink_to('/etc/passwd')
        with self.assertRaisesRegex(ValueError, 'symbolic'):
            bundled.verify_bundle(self.bundle)
        target.unlink()
        target.write_bytes(b'compiled-test-fixture')
        document = dict(self.manifest, classpath=['classes', '/tmp/unknown.jar'])
        (self.bundle / bundled.MANIFEST).write_text(json.dumps(document))
        with self.assertRaisesRegex(ValueError, 'classpath'):
            bundled.verify_bundle(self.bundle)

    def test_runtime_excludes_foreign_dirs_and_is_exclusive(self):
        foreign = self.root / 'foreign'
        foreign.mkdir(mode=0o700)
        (foreign / 'business-record').write_text('existing')
        with self.assertRaisesRegex(ValueError, 'nonempty'):
            with bundled.owned_runtime(foreign):
                pass
        state = self.root / 'private'
        state.mkdir(mode=0o700)
        lock = state / '.platform-owner.lock'
        lock.write_text('')
        lock.chmod(0o600)
        log = state / 'runtime.log'
        log.write_text('parent diagnostics')
        log.chmod(0o644)
        with self.assertRaisesRegex(ValueError, 'nonempty'):
            with bundled.owned_runtime(state):
                pass
        log.chmod(0o600)
        with bundled.owned_runtime(state):
            with self.assertRaises(BlockingIOError):
                with bundled.owned_runtime(state):
                    pass

    def test_uncertain_shutdown_marker_refuses_restart(self):
        state = self.root / 'private'
        with bundled.owned_runtime(state):
            broker.atomic_private_json(state / '.shutdown-unconfirmed.json', {'state': broker.RECOVERY_REQUIRED})
        with self.assertRaisesRegex(RuntimeError, 'Previous shutdown'):
            with bundled.owned_runtime(state):
                pass

    def test_extra_installed_class_is_rejected(self):
        with bundled.owned_runtime(self.root / 'private') as state:
            bundled.install_runtime(self.bundle, state, Path('/java'))
            (state / 'classes/Unlisted.class').write_text('extra')
            with self.assertRaisesRegex(ValueError, 'unlisted'):
                bundled.install_runtime(self.bundle, state, Path('/java'))

    def test_installed_artifact_tamper_and_changed_version_are_rejected(self):
        with bundled.owned_runtime(self.root / 'private') as state:
            bundled.install_runtime(self.bundle, state, Path('/java'))
            (state / 'classes/KettleWorker.class').write_text('changed')
            with self.assertRaisesRegex(ValueError, 'checksum'):
                bundled.install_runtime(self.bundle, state, Path('/java'))
            document = dict(self.manifest, extraVersion='2')
            (self.bundle / bundled.MANIFEST).write_text(json.dumps(document))
            with self.assertRaisesRegex(ValueError, 'different bundle'):
                bundled.install_runtime(self.bundle, state, Path('/java'))

    def test_packaging_verifies_source_libraries_and_preserves_order(self):
        source = self.root / 'source'
        (source / 'lib').mkdir(parents=True)
        (source / 'classes').mkdir()
        jar = source / 'lib/kettle-6.1.0.7.36.jar'
        jar.write_bytes(b'original')
        (source / '.broker-token').write_text('private-source-token')
        (source / 'manifest.json').write_text(json.dumps({'classpath': str(source / 'classes') + os.pathsep + str(jar), 'libraries': [{'file': jar.name, 'sha256': broker.file_digest(jar)}]}))
        ordered, records = packer.copy_verified_libraries(source, self.root / 'copied-lib')
        self.assertEqual([jar.name], ordered)
        self.assertEqual(broker.file_digest(jar), records[0]['sha256'])
        self.assertEqual([jar.name], [p.name for p in (self.root / 'copied-lib').iterdir()])
        jar.write_bytes(b'tampered')
        with self.assertRaisesRegex(ValueError, 'checksum'):
            packer.copy_verified_libraries(source, self.root / 'tamper-lib')


class LinuxIsolationAndShutdownTest(unittest.TestCase):
    def test_linux_requires_bubblewrap_without_fallback(self):
        with patch.object(bundled.shutil, 'which', return_value=None):
            with self.assertRaisesRegex(RuntimeError, 'unisolated'):
                bundled.BubblewrapSandbox('/private/state', '/java17')

    def test_bubblewrap_command_denies_network_and_only_binds_current_operation(self):
        sandbox = bundled.BubblewrapSandbox('/private/state', '/java17', '/usr/bin/bwrap')
        cmd = sandbox.command(['/java17/bin/java', '-version'], '/private/state/operations/run-1')
        self.assertIn('--unshare-all', cmd)
        self.assertIn('--die-with-parent', cmd)
        self.assertIn('--clearenv', cmd)
        self.assertNotIn('--share-net', cmd)
        writes = [cmd[i + 1] for i, value in enumerate(cmd) if value == '--bind']
        self.assertEqual(['/private/state/operations/run-1'], writes)
        self.assertNotIn('/private/state', cmd)
        self.assertEqual(['/java17/bin/java', '-version'], cmd[cmd.index('--') + 1:])
        with self.assertRaises(ValueError):
            sandbox.command(['/java17/bin/java'], '/outside')

    def test_bubblewrap_probe_failure_prevents_worker_start(self):
        with tempfile.TemporaryDirectory() as folder:
            sandbox = bundled.BubblewrapSandbox(folder, '/java17', '/usr/bin/bwrap')
            with patch.object(bundled.subprocess, 'run', return_value=Mock(returncode=1)):
                with self.assertRaisesRegex(RuntimeError, 'isolation probe failed'):
                    sandbox.probe()

    def test_parent_shutdown_controls_only_owned_live_children_and_blocks_new_launch(self):
        worker = object.__new__(broker.Worker)
        worker.launch_lock = threading.RLock()
        worker.closing = False
        child = Mock()
        child.poll.side_effect = [None, None, 0, 0, 0]
        worker.runs = {'owned': {'id': 'owned', 'process': child, 'finalized': True}, 'uncertain': {'id': 'uncertain', 'process': None, 'spawnedPid': 99999, 'state': broker.RECOVERY_REQUIRED}}
        worker.stop = Mock()
        self.assertTrue(worker.close_owned())
        worker.stop.assert_called_once_with('owned')
        with self.assertRaisesRegex(RuntimeError, 'shutting down'):
            worker.launch('run')

    def test_one_control_failure_does_not_abandon_other_owned_children(self):
        worker = object.__new__(broker.Worker)
        worker.launch_lock = threading.RLock()
        first, second = Mock(), Mock()
        first.poll.return_value = second.poll.return_value = None
        worker.runs = {'a': {'id': 'a', 'process': first, 'finalized': False}, 'b': {'id': 'b', 'process': second, 'finalized': False}}
        worker.stop = Mock(side_effect=[FileNotFoundError(), None])
        worker._request_control, worker._persist = Mock(), Mock()
        self.assertFalse(worker.close_owned(grace_seconds=0))
        self.assertEqual(['a', 'b'], [c.args[0] for c in worker.stop.call_args_list])
        worker._request_control.assert_called_once_with(worker.runs['a'], 'HALT')
        self.assertEqual(2, worker._persist.call_count)

    def test_exited_child_without_finalization_is_not_reported_clean(self):
        worker = object.__new__(broker.Worker)
        worker.launch_lock = threading.RLock()
        child = Mock()
        child.poll.return_value = 0
        worker.runs = {'owned': {'id': 'owned', 'process': child, 'finalized': False}}
        worker.stop, worker._persist = Mock(), Mock()
        self.assertFalse(worker.close_owned(grace_seconds=0))
        worker.stop.assert_not_called()
        self.assertEqual(broker.RECOVERY_REQUIRED, worker.runs['owned']['state'])

    def test_unconfirmed_shutdown_records_recovery_without_pid_signals(self):
        worker = object.__new__(broker.Worker)
        worker.launch_lock = threading.RLock()
        child = Mock()
        child.poll.return_value = None
        worker.runs = {'owned': {'id': 'owned', 'process': child, 'finalized': False}}
        worker.stop, worker._persist = Mock(), Mock()
        with patch.object(os, 'kill') as kill:
            self.assertFalse(worker.close_owned(grace_seconds=0))
            kill.assert_not_called()
        self.assertEqual(broker.RECOVERY_REQUIRED, worker.runs['owned']['state'])
        worker._persist.assert_called_once()


@unittest.skipUnless(os.environ.get('KETTLE_BUNDLE'), 'Opt-in original bundled engine acceptance')
class BundledOriginalEngineAcceptance(unittest.TestCase):
    def test_relocation_file_pipeline_preview_and_parent_eof_stops_owned_run(self):
        bundle = Path(os.environ['KETTLE_BUNDLE']).resolve()
        java = Path(os.environ.get('KETTLE_JAVA_HOME', '/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home'))
        fixture_path = Path(__file__).resolve().parents[2] / 'data-governance/kettle-worker/tests/test_kettle_worker.py'
        spec = importlib.util.spec_from_file_location('bundled_fixture', fixture_path)
        fixture = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(fixture)
        with tempfile.TemporaryDirectory(prefix='rynew-bundled-native-') as temporary:
            base = Path(temporary).resolve()
            relocated = base / 'relocated-bundle'
            shutil.copytree(bundle, relocated)
            runtime = base / 'private-state'
            with (base / 'broker.stderr').open('w+') as log:
                process = subprocess.Popen([sys.executable, str(relocated / 'bundled_kettle_runtime.py'), '--bundle', str(relocated), '--runtime', str(runtime), '--java-home', str(java), '--port', '0', '--timeout', '120'], stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=log, text=True)
                try:
                    self.assertTrue(select.select([process.stdout], [], [], 60)[0], 'Broker readiness timeout')
                    line = process.stdout.readline()
                    if not line:
                        log.flush(); log.seek(0)
                        self.fail('Broker failed before readiness: ' + log.read())
                    ready = json.loads(line)
                    self.assertEqual('original-kettle', ready['engine'])
                    token = (runtime / '.broker-token').read_text().strip()
                    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
                    def request(method, route, body=None, binary=False):
                        data = None if body is None else json.dumps(body).encode()
                        req = urllib.request.Request(ready['url'] + route, data=data, method=method, headers={'Authorization': 'Bearer ' + token, 'Content-Type': 'application/json'})
                        with opener.open(req, timeout=45) as response:
                            content = response.read()
                            return content if binary else json.loads(content)
                    self.assertEqual('deny-all', request('GET', '/health')['runtimePolicy']['mode'])
                    catalog = request('GET', '/capabilities')
                    xml = fixture.fixture(catalog)
                    inputs = [{'name': 'input.csv', 'content': 'name\nalice\n张三\nbob\n'}]
                    saved = request('PUT', '/transformations/bundled-proof', {'xml': xml, 'inputFiles': inputs})
                    self.assertEqual('bundled-proof', saved['id'])
                    result = request('POST', '/runs', {'transformationId': 'bundled-proof', 'inputFiles': inputs})
                    def wait_run(identifier):
                        deadline = time.monotonic() + 45
                        while time.monotonic() < deadline:
                            result = request('GET', '/runs/' + identifier)
                            if result.get('finalized'):
                                return result
                            time.sleep(0.1)
                        self.fail('Run did not finalize')
                    result = wait_run(result['id'])
                    self.assertEqual('SUCCEEDED', result['state'], result)
                    output = request('GET', '/runs/' + result['id'] + '/files/result.csv', binary=True).decode()
                    self.assertEqual(['name,greeting', 'alice,ALICE!', '张三,张三!', 'bob,BOB!'], output.splitlines())
                    preview = request('POST', '/runs', {'transformationId': 'bundled-proof', 'mode': 'preview', 'previewStep': 'native-script', 'rowLimit': 2, 'inputFiles': inputs})
                    preview = wait_run(preview['id'])
                    self.assertIn(preview['state'], ('SUCCEEDED', 'PREVIEW_COMPLETE'))
                    self.assertFalse(preview.get('files'))
                    request('PUT', '/transformations/bundled-stop-proof', {'xml': fixture.fixture(catalog, delay=True), 'inputFiles': inputs})
                    slow = request('POST', '/runs', {'transformationId': 'bundled-stop-proof', 'inputFiles': [{'name': 'input.csv', 'content': 'name\n' + 'alice\n' * 60}]})
                    time.sleep(1)
                    process.stdin.close()
                    self.assertEqual(0, process.wait(timeout=20))
                    record = json.loads((runtime / 'run-records' / slow['id'] / 'record.json').read_text())
                    self.assertEqual('STOPPED', record['state'], record)
                    self.assertTrue(record['finalized'])
                    self.assertNotEqual(token, 'private-source-token')
                finally:
                    if not process.stdin.closed:
                        process.stdin.close()
                    process.wait(timeout=20)
                    process.stdout.close()


if __name__ == '__main__':
    unittest.main()
