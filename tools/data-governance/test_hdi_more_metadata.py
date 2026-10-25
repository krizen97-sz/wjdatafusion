"""Original MySQL/RabbitMQ/SFTP clients cannot contact an unapproved listener."""
import importlib.util
import json
import os
from pathlib import Path
import socket
import subprocess
import tempfile
import unittest

HERE = Path(__file__).resolve().parent
spec = importlib.util.spec_from_file_location('launcher', HERE / 'hdi_original_compiler.py')
launcher = importlib.util.module_from_spec(spec)
spec.loader.exec_module(launcher)


@unittest.skipUnless(os.getenv('HDI_ORIGINAL_BUNDLE') and os.getenv('HDI_COMPILER_JAVA_HOME'), 'Original SDK not configured')
class MoreMetadataGuardTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.temporary = tempfile.TemporaryDirectory(prefix='hdi-more-metadata-guard-')
        cls.root = Path(cls.temporary.name).resolve()
        cls.bundle = Path(os.environ['HDI_ORIGINAL_BUNDLE'])
        cls.java = Path(os.environ['HDI_COMPILER_JAVA_HOME'])
        cls.cp = os.pathsep.join([str(cls.root), *map(str, launcher.classpath(cls.bundle))])
        sources = [launcher.SOURCE, launcher.SOURCE.with_name('OriginalConnectionForms.java'),
                   launcher.SOURCE.parent / 'tests/MoreMetadataClientGuard.java']
        result = subprocess.run([str(cls.java / 'bin/javac'), '--release', '11', '-cp', cls.cp,
                                 '-d', str(cls.root), *map(str, sources)], capture_output=True, text=True)
        if result.returncode:
            raise AssertionError(result.stderr)

    @classmethod
    def tearDownClass(cls):
        cls.temporary.cleanup()

    def invoke(self, request):
        completed = subprocess.run([str(self.java / 'bin/java'), '-Xmx256m', '-cp', self.cp,
                                    'OriginalTaskCompiler', str(self.bundle)], input=json.dumps(request),
                                   capture_output=True, text=True, cwd=self.root, timeout=25)
        return completed.returncode, json.loads(completed.stdout)

    def test_original_clients_are_denied_before_tcp_connect_or_first_byte(self):
        observations = []
        for client in ('MySQL', 'RabbitMQ', 'SFTP'):
            with self.subTest(client=client), socket.socket() as allowed, socket.socket() as denied:
                allowed.bind(('127.0.0.1', 0)); allowed.listen()
                denied.bind(('127.0.0.1', 0)); denied.listen(); denied.settimeout(.25)
                completed = subprocess.run([str(self.java / 'bin/java'), '-Xmx256m', '-cp', self.cp,
                    'MoreMetadataClientGuard', client, str(allowed.getsockname()[1]), str(denied.getsockname()[1])],
                    capture_output=True, text=True, cwd=self.root, timeout=25)
                self.assertEqual(completed.returncode, 0, completed.stderr)
                self.assertIn('ORIGINAL_CLIENT_ENDPOINT_DENIED ' + client, completed.stdout)
                with self.assertRaises(socket.timeout): denied.accept()
                self.assertNotIn('probe-secret-not-a-real-credential', completed.stdout + completed.stderr)
                observations.append({'client': client, 'denied': True, 'connections': 0, 'bytes': 0})
        if os.getenv('HDI_MORE_METADATA_GUARD_EVIDENCE'):
            Path(os.environ['HDI_MORE_METADATA_GUARD_EVIDENCE']).write_text(json.dumps(observations, indent=2))

    def test_mismatched_request_scope_is_rejected_before_client_creation(self):
        for client, db_type in (('MySQL', 2), ('RabbitMQ', 120), ('FTP', 18)):
            with self.subTest(client=client), socket.socket() as allowed, socket.socket() as denied:
                allowed.bind(('127.0.0.1', 0)); allowed.listen()
                denied.bind(('127.0.0.1', 0)); denied.listen(); denied.settimeout(.25)
                request = {'operation': 'connectionMetadata', 'datasourceType': client, 'parameters': {
                    'action': 'test', 'query': {}, 'allowedEndpoints': ['127.0.0.1:' + str(allowed.getsockname()[1])],
                    'datasource': {'dbType': db_type, 'databaseIp': '127.0.0.1',
                                   'databasePort': str(denied.getsockname()[1]), 'databasePassword': 'scope-secret'}}}
                code, response = self.invoke(request)
                self.assertNotEqual(code, 0); self.assertFalse(response['ok'])
                self.assertNotIn('scope-secret', json.dumps(response))
                with self.assertRaises(socket.timeout): denied.accept()


if __name__ == '__main__':
    unittest.main()
