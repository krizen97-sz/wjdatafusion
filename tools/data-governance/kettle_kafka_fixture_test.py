import json
from pathlib import Path
import tempfile
import socket
import unittest
from unittest.mock import patch
import kettle_kafka_fixture as fixture


class OwnershipTests(unittest.TestCase):
    def test_unknown_directory_not_adopted(self):
        with tempfile.TemporaryDirectory() as temp:
            root=Path(temp)/'kafka-legacy';root.mkdir()
            with self.assertRaises(RuntimeError):fixture.owned(root,Path('/nonexistent/java'))

    def test_runtime_root_requires_dedicated_name(self):
        with self.assertRaises(RuntimeError):fixture.owned(Path('/tmp/unrelated-service'))

    def test_pid_identity_change_refuses_signalling(self):
        record={'pid':123456,'identity':'old process'}
        with patch.object(fixture,'process_identity',return_value='another process'):
            with self.assertRaises(RuntimeError):fixture.matched(record,Path('/tmp/kafka-legacy'),{'instanceId':'test'},'kafka')

    def test_missing_process_is_not_owned_live(self):
        with patch.object(fixture,'process_identity',return_value=None):
            self.assertFalse(fixture.matched({'pid':123456,'identity':'gone'},Path('/tmp/kafka-legacy'),{'instanceId':'test'},'kafka'))

    def test_download_rejects_non_pinned_url_before_network(self):
        with self.assertRaises(RuntimeError):fixture.download('https://example.com/archive',Path('/tmp/unused'),100)

    def test_config_only_fixed_loopback_and_owned_data(self):
        config=fixture.configs(Path('/tmp/kafka-legacy'))
        self.assertIn('clientPortAddress=127.0.0.1',config['zookeeper.properties'])
        self.assertIn('admin.enableServer=false',config['zookeeper.properties'])
        self.assertIn('advertised.listeners=PLAINTEXT://127.0.0.1:29092',config['kafka.properties'])
        self.assertIn('log.dirs=/tmp/kafka-legacy/data/kafka',config['kafka.properties'])
        self.assertNotIn('19092',''.join(config.values()))

    def test_existing_listener_is_not_reused_or_killed(self):
        with socket.socket() as listener:
            listener.bind(('127.0.0.1',0));listener.listen(1)
            with self.assertRaises(RuntimeError):fixture.free_port(listener.getsockname()[1])
            self.assertGreater(listener.fileno(),-1)


if __name__=='__main__':unittest.main()
