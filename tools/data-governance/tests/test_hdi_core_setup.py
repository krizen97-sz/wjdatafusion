"""Installation ownership/no-overwrite tests; runtime execution has separate proofs."""
import argparse
import contextlib
import io
import json
import os
from pathlib import Path
import sys
import tempfile
import subprocess
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import hdi_core_setup as setup


class SetupTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name).resolve()
        self.bundle = self.root / 'bundle'
        self.bundle.mkdir()
        (self.bundle / 'bundle-manifest.json').write_text('{}')
        self.java = self.root / 'jdk'
        self.java.mkdir()
        self.compiler = self.root / 'source/tools/data-governance/compiler.py'
        self.compiler.parent.mkdir(parents=True)
        self.compiler.write_text('# source only')
        self.compiler_source = self.root / 'source/data-governance/hdi-native/compiler/OriginalTaskCompiler.java'
        self.compiler_source.parent.mkdir(parents=True)
        self.compiler_source.write_text('// compiler source')
        self.password = self.root / 'redis-password'
        self.password.write_text('never-print-or-copy-this-password')
        self.password.chmod(0o600)
        self.args = argparse.Namespace(install_root=self.root / 'install', bundle=self.bundle,
            java_home=self.java, redis_password_file=self.password, redis_port=6379,
            redis_database=2, compiler_launcher=self.compiler, verify_only=False)
        self.preflight = patch.object(setup, 'preflight').start()
        self.build = patch.object(setup, 'build_control', side_effect=self.fake_build).start()
        self.umask = os.umask(0o077)

    def tearDown(self):
        os.umask(self.umask)
        patch.stopall()
        self.temp.cleanup()

    def fake_build(self, bundle, java, output, log):
        output.mkdir(mode=0o700)
        jar = output / 'rynew-hdi-core-control.jar'
        jar.write_bytes(b'test-build-output')
        setup.new_json(output / 'build-evidence.json', {
            'sourceSha256': setup.sha(setup.SOURCE), 'jarSha256': setup.sha(jar)})
        log.write_text('test-build')

    def test_new_installation_is_private_and_only_references_supplied_password(self):
        result = setup.prepare(self.args)
        self.assertEqual(result['status'], 'PREPARED')
        self.assertFalse(result['servicesStarted'])
        config = json.loads(Path(result['workerConfig']).read_text())
        self.assertEqual(config['redis']['passwordFile'], str(self.password))
        self.assertEqual(config['redis']['host'], '127.0.0.1')
        self.assertNotIn('tls', config)
        self.assertNotIn('zookeeper', config)
        self.assertEqual(config['javaRuntimeMode'], 'normal')
        for path in self.args.install_root.rglob('*'):
            self.assertEqual(path.stat().st_mode & 0o777, 0o700 if path.is_dir() else 0o600)
            if path.is_file():
                self.assertNotIn(self.password.read_bytes(), path.read_bytes())
        self.assertNotIn(self.password.read_text(), json.dumps(result))
        self.assertEqual(result['compilerArgv'][-2:], ['--runtime', str(self.args.install_root / 'compile')])

    def test_repeat_only_verifies_and_preserves_active_state(self):
        first = setup.prepare(self.args)
        state = self.args.install_root / 'state/running.json'
        state.write_text('existing runtime evidence')
        snapshot = {str(p): (p.read_bytes(), p.stat().st_mtime_ns) for p in self.args.install_root.rglob('*') if p.is_file()}
        second = setup.prepare(self.args)
        self.assertEqual(second['status'], 'VERIFIED')
        self.assertEqual(first['workerArgv'], second['workerArgv'])
        self.assertEqual(snapshot, {str(p): (p.read_bytes(), p.stat().st_mtime_ns) for p in self.args.install_root.rglob('*') if p.is_file()})
        self.build.assert_called_once()

    def test_changed_config_is_conflict_without_overwrite(self):
        setup.prepare(self.args)
        config = self.args.install_root / 'private/worker.json'
        config.write_text('changed private configuration')
        with self.assertRaisesRegex(setup.SetupError, 'EXISTING_INSTALLATION_CONFLICT'):
            setup.prepare(self.args)
        self.assertEqual(config.read_text(), 'changed private configuration')
        self.build.assert_called_once()

    def test_changed_input_is_conflict_without_new_build(self):
        setup.prepare(self.args)
        self.args.redis_database = 3
        with self.assertRaisesRegex(setup.SetupError, 'EXISTING_INSTALLATION_CONFLICT'):
            setup.prepare(self.args)
        self.build.assert_called_once()

    def test_verify_only_never_creates_missing_installation(self):
        self.args.verify_only = True
        with self.assertRaisesRegex(setup.SetupError, 'INSTALLATION_NOT_FOUND'):
            setup.prepare(self.args)
        self.assertFalse(self.args.install_root.exists())
        self.build.assert_not_called()

    def test_missing_compiler_java_source_is_rejected_before_writes(self):
        self.compiler_source.unlink()
        with self.assertRaisesRegex(setup.SetupError, 'INTEGRATED_SOURCE_FILES_REQUIRED'):
            setup.prepare(self.args)
        self.assertFalse(self.args.install_root.exists())

    def test_compiler_source_revision_change_is_detected(self):
        setup.prepare(self.args)
        self.compiler_source.write_text('// different compiler revision')
        with self.assertRaisesRegex(setup.SetupError, 'EXISTING_INSTALLATION_CONFLICT'):
            setup.prepare(self.args)
        self.build.assert_called_once()

    def test_foreign_directory_is_not_adopted_or_modified(self):
        self.args.install_root.mkdir(mode=0o700)
        file = self.args.install_root / 'somebody-elses-file'
        file.write_text('preserve')
        with self.assertRaises((OSError, setup.SetupError)):
            setup.prepare(self.args)
        self.assertEqual(list(self.args.install_root.iterdir()), [file])
        self.build.assert_not_called()

    def test_symlink_root_or_password_is_rejected(self):
        self.args.install_root.symlink_to(self.root / 'other')
        with self.assertRaisesRegex(setup.SetupError, 'SYMLINK'):
            setup.prepare(self.args)
        self.args.install_root.unlink()
        linked = self.root / 'password-link'
        linked.symlink_to(self.password)
        self.args.redis_password_file = linked
        with self.assertRaisesRegex(setup.SetupError, 'SYMLINK'):
            setup.prepare(self.args)

    def test_public_password_and_repository_state_are_rejected_before_writes(self):
        self.password.chmod(0o644)
        with self.assertRaisesRegex(setup.SetupError, 'PRIVATE_REDIS_PASSWORD_FILE_REQUIRED'):
            setup.prepare(self.args)
        self.password.chmod(0o600)
        (self.root / '.git').write_text('gitdir: elsewhere')
        with self.assertRaisesRegex(setup.SetupError, 'INSTALL_ROOT_MUST_BE_OUTSIDE_CHECKOUT'):
            setup.prepare(self.args)
        self.assertFalse(self.args.install_root.exists())

    def test_build_failure_does_not_publish_config_or_replace_partial_directory(self):
        self.build.side_effect = setup.SetupError('CONTROL_BRIDGE_BUILD_FAILED')
        with self.assertRaisesRegex(setup.SetupError, 'CONTROL_BRIDGE_BUILD_FAILED'):
            setup.prepare(self.args)
        self.assertFalse((self.args.install_root / 'private/worker.json').exists())
        self.assertFalse((self.args.install_root / 'private/setup-receipt.json').exists())
        with self.assertRaises((OSError, setup.SetupError)):
            setup.prepare(self.args)
        self.build.assert_called_once()

    def test_errors_never_echo_exception_or_password(self):
        argv = ['--install-root', str(self.args.install_root), '--bundle', str(self.bundle),
                '--java-home', str(self.java), '--redis-password-file', str(self.password),
                '--redis-database', '2', '--compiler-launcher', str(self.compiler)]
        self.preflight.side_effect = ValueError(self.password.read_text())
        out = io.StringIO()
        with contextlib.redirect_stdout(out):
            result = setup.main(argv)
        self.assertEqual(result, 2)
        self.assertEqual(json.loads(out.getvalue())['code'], 'INSTALLATION_PREPARE_FAILED')
        self.assertNotIn(self.password.read_text(), out.getvalue())


class PreflightTests(unittest.TestCase):
    def test_wrong_os_and_architecture_are_rejected_before_commands(self):
        with patch.object(setup.sys, 'platform', 'darwin'), patch.object(setup.subprocess, 'run') as run:
            with self.assertRaisesRegex(setup.SetupError, 'LINUX_X86_64_REQUIRED'):
                setup.preflight(Path('/unused'), Path('/unused'))
            run.assert_not_called()
        with patch.object(setup.sys, 'platform', 'linux'), patch.object(setup.platform, 'machine', return_value='aarch64'):
            with self.assertRaisesRegex(setup.SetupError, 'LINUX_X86_64_REQUIRED'):
                setup.preflight(Path('/unused'), Path('/unused'))

    def test_java_17_is_not_silently_accepted(self):
        response = subprocess.CompletedProcess([], 0, '', 'os.arch = amd64\nopenjdk version "17.0.1"')
        with patch.object(setup.sys, 'platform', 'linux'), patch.object(setup.platform, 'machine', return_value='x86_64'), \
                patch.object(setup.os, 'access', return_value=True), patch.object(setup.subprocess, 'run', return_value=response):
            with self.assertRaisesRegex(setup.SetupError, 'JAVA_11_X86_64_REQUIRED'):
                setup.preflight(Path('/unused'), Path('/unused'))


if __name__ == '__main__':
    unittest.main()
