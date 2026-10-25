"""Real original-SDK metadata guard tests; no ETL task, message writes, or group offsets."""
import importlib.util
import json
import os
from pathlib import Path
import select
import socket
import subprocess
import tempfile
import unittest

HERE = Path(__file__).resolve().parent
spec = importlib.util.spec_from_file_location('launcher', HERE / 'hdi_original_compiler.py')
launcher = importlib.util.module_from_spec(spec)
spec.loader.exec_module(launcher)


@unittest.skipUnless(os.getenv('HDI_ORIGINAL_BUNDLE') and os.getenv('HDI_COMPILER_JAVA_HOME'), 'Original SDK not configured')
class MetadataGuardTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.temporary = tempfile.TemporaryDirectory(prefix='hdi-metadata-guard-')
        cls.root = Path(cls.temporary.name).resolve()
        cls.bundle = Path(os.environ['HDI_ORIGINAL_BUNDLE'])
        cls.java = Path(os.environ['HDI_COMPILER_JAVA_HOME'])
        cls.cp = os.pathsep.join([str(cls.root), *map(str, launcher.classpath(cls.bundle))])
        directory = launcher.SOURCE.parent
        sources = [launcher.SOURCE, directory / 'OriginalConnectionForms.java',
                   directory / 'tests/KafkaMetadataBrokerFixture.java', directory / 'tests/MetadataGuardContract.java']
        result = subprocess.run([str(cls.java / 'bin/javac'), '--release', '11', '-cp', cls.cp, '-d', str(cls.root), *map(str, sources)], capture_output=True, text=True)
        if result.returncode:
            raise AssertionError(result.stderr)

    @classmethod
    def tearDownClass(cls):
        cls.temporary.cleanup()

    def invoke(self, bootstrap, allowed, action='test'):
        request = {'operation': 'connectionMetadata', 'datasourceType': 'Kafka',
                   'parameters': {'action': action, 'bootstrapServers': bootstrap, 'allowedEndpoints': allowed}}
        result = subprocess.run([str(self.java / 'bin/java'), '-Xmx256m', '-cp', self.cp,
                                 'OriginalTaskCompiler', str(self.bundle)], input=json.dumps(request),
                                capture_output=True, text=True, cwd=self.root, timeout=25)
        return result.returncode, json.loads(result.stdout)

    def fixture_line(self, process):
        self.assertTrue(select.select([process.stdout], [], [], 10)[0], 'Fixture response timed out')
        return json.loads(process.stdout.readline())

    def test_advertised_endpoint_is_blocked_before_connection_or_bytes(self):
        process = subprocess.Popen([str(self.java / 'bin/java'), '-cp', self.cp,
            'org.apache.kafka.common.requests.KafkaMetadataBrokerFixture'], stdin=subprocess.PIPE,
            stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, cwd=self.root)
        try:
            ports = self.fixture_line(process)
            bootstrap, advertised = ('127.0.0.1:' + str(ports[key]) for key in ('bootstrapPort', 'advertisedPort'))
            code, accepted = self.invoke(bootstrap, [bootstrap, advertised])
            self.assertEqual(code, 0); self.assertTrue(accepted['metadata']['available'], accepted)
            self.assertTrue(accepted['metadata']['guardActive'])
            process.stdin.write('stats\n'); process.stdin.flush(); before = self.fixture_line(process)
            self.assertGreater(before['connections'], 0); self.assertGreater(before['bytes'], 0)
            code, denied = self.invoke(bootstrap, [bootstrap])
            self.assertEqual(code, 0); self.assertFalse(denied['metadata']['available'])
            self.assertEqual(denied['metadata']['reason'], 'METADATA_ENDPOINT_DENIED')
            process.stdin.write('stats\n'); process.stdin.flush(); after = self.fixture_line(process)
            self.assertEqual(after, before, 'Unapproved advertised endpoint received a connection or bytes')
            if os.getenv('HDI_METADATA_GUARD_EVIDENCE'):
                Path(os.environ['HDI_METADATA_GUARD_EVIDENCE']).write_text(json.dumps({
                    'allowedControl': accepted['metadata'], 'deniedResponse': denied['metadata'],
                    'advertisedEndpointBeforeDenial': before, 'advertisedEndpointAfterDenial': after,
                    'unauthorizedConnectionDelta': after['connections'] - before['connections'],
                    'unauthorizedBytesDelta': after['bytes'] - before['bytes'],
                    'originalKafkaClient': '3.9.1', 'javaMajor': 17,
                }, indent=2))
        finally:
            process.stdin.close()
            try: process.wait(timeout=5)
            except subprocess.TimeoutExpired: process.kill(); process.wait(timeout=5)
            process.stdout.close(); process.stderr.close()

    def test_guard_cannot_be_removed_and_rejects_dns_and_unapproved_socket(self):
        with socket.socket() as allowed, socket.socket() as denied:
            allowed.bind(('127.0.0.1', 0)); allowed.listen()
            denied.bind(('127.0.0.1', 0)); denied.listen(); denied.settimeout(.2)
            result = subprocess.run([str(self.java / 'bin/java'), '-cp', self.cp, 'MetadataGuardContract',
                str(allowed.getsockname()[1]), str(denied.getsockname()[1])], capture_output=True, text=True, cwd=self.root, timeout=10)
            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertIn('GUARD_BOUNDARY_CONFIRMED', result.stdout)
            with self.assertRaises(socket.timeout): denied.accept()

    def test_hostnames_wildcards_extra_keys_and_outside_bootstrap_are_rejected(self):
        for bootstrap, allowed in [('localhost:9092', ['localhost:9092']), ('127.0.0.1:9092', ['127.0.0.1:*']),
                                   ('127.0.0.1:9092', ['127.0.0.1:9093'])]:
            code, result = self.invoke(bootstrap, allowed)
            self.assertNotEqual(code, 0); self.assertFalse(result['ok'])

    @unittest.skipUnless(os.getenv('HDI_KAFKA_FIXTURE'), 'Owned real Kafka fixture not requested')
    def test_owned_real_broker_test_and_topic_list_succeed(self):
        for action in ('test', 'topics'):
            code, result = self.invoke('127.0.0.1:19092', ['127.0.0.1:19092'], action)
            self.assertEqual(code, 0); self.assertTrue(result['metadata']['available'], result)
            self.assertTrue(result['metadata']['guardActive'])
            if action == 'topics': self.assertIn('hdi-connectors-20261025-output', result['metadata']['topics'])


if __name__ == '__main__':
    unittest.main()
