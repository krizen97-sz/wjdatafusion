import importlib.util
import json
from pathlib import Path
import tempfile
import socket
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location('runtime_ctl', Path(__file__).with_name('runtime_ctl.py'))
runtime = importlib.util.module_from_spec(spec)
spec.loader.exec_module(runtime)


class RuntimeSafetyTest(unittest.TestCase):
    def test_nifi_records_java_identity_after_shell_exec(self):
        root = Path('/private/runtime')
        shell = 'time /private/runtime/nifi/bin/nifi.sh run'
        java = 'time /private/runtime/jdk/bin/java -cp /private/runtime/nifi/lib org.apache.nifi.NiFi'
        with patch.object(runtime, 'process_identity', side_effect=[shell, java]), patch.object(runtime.time, 'sleep'):
            self.assertEqual(java, runtime.capture_launch_identity('nifi', 123, root, '/private/runtime/nifi'))

    def test_stop_refuses_changed_pid_before_command_or_signal(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder).resolve()
            record = {'pid': 123, 'identity': 'old ' + str(root), 'token': str(root)}
            (root / 'services.json').write_text(json.dumps({'ftp': record}))
            with patch.object(runtime, 'process_identity', return_value='new ' + str(root)), patch.object(runtime.os, 'kill') as kill, patch.object(runtime, 'command') as command:
                with self.assertRaises(RuntimeError):
                    runtime.stop(root, {}, 'ftp')
                kill.assert_not_called()
                command.assert_not_called()

    def test_free_port_refuses_existing_listener(self):
        with socket.socket() as listener:
            listener.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
            listener.bind(('127.0.0.1', 0))
            listener.listen()
            with self.assertRaises(OSError):
                runtime.free_port(listener.getsockname()[1])

    def test_changed_pid_identity_is_never_stopped(self):
        root = Path('/private/runtime')
        record = {'pid': 123, 'identity': 'old time process /private/runtime/service', 'token': '/private/runtime/service'}
        with patch.object(runtime, 'process_identity', return_value='new time process /private/runtime/service'), patch.object(runtime.os, 'kill') as kill:
            with self.assertRaises(RuntimeError):
                runtime.assert_owned_process(record, root)
            kill.assert_not_called()

    def test_matching_pid_without_owned_path_is_rejected(self):
        record = {'pid': 123, 'identity': 'same time /another/mysql', 'token': '/another/mysql'}
        with patch.object(runtime, 'process_identity', return_value=record['identity']):
            with self.assertRaises(RuntimeError):
                runtime.assert_owned_process(record, Path('/private/runtime'))

    def test_missing_process_is_not_adopted(self):
        with patch.object(runtime, 'process_identity', return_value=None):
            self.assertFalse(runtime.assert_owned_process({'pid': 123}, Path('/private/runtime')))

    def test_empty_ownership_token_is_rejected(self):
        value = 'time /private/runtime/process'
        with patch.object(runtime, 'process_identity', return_value=value):
            with self.assertRaises(RuntimeError):
                runtime.assert_owned_process({'pid': 123, 'identity': value, 'token': ''}, Path('/private/runtime'))

    def test_runtime_cannot_point_to_external_component(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder).resolve()
            (root / 'runtime.json').write_text(json.dumps({'owner': runtime.OWNER, 'runtimeRoot': str(root), 'nifiHome': '/another/nifi', 'javaHome': str(root / 'jdk')}))
            with self.assertRaises(RuntimeError):
                runtime.runtime_root(str(root))

    def test_credentials_require_private_permissions(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            (root / 'private').mkdir()
            path = root / 'private/test.json'
            path.write_text('{}')
            path.chmod(0o644)
            with self.assertRaises(RuntimeError):
                runtime.read_private_json(root, 'test.json')
            path.chmod(0o600)
            self.assertEqual({}, runtime.read_private_json(root, 'test.json'))

    def test_unowned_existing_database_is_never_initialized(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            (root / 'mysql/data').mkdir(parents=True)
            with patch.object(runtime, 'command') as command:
                with self.assertRaises(RuntimeError):
                    runtime.prepare_mysql(root)
                command.assert_not_called()

    def test_health_error_cannot_echo_credentials(self):
        with patch.object(runtime, 'read_private_json', side_effect=ValueError('secret-value-not-for-output')):
            result = runtime.health(Path('/private/runtime'), 'nifi')
        self.assertNotIn('secret-value-not-for-output', json.dumps(result))
        self.assertEqual('ValueError', result['error_type'])


if __name__ == '__main__':
    unittest.main()
