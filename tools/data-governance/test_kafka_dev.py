#!/usr/bin/env python3
"""Offline safety checks; no real Kafka or other service is started or stopped."""
import importlib.util
import json
import os
from pathlib import Path
import signal
import socket
import tarfile
import tempfile
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location('kafka_dev', Path(__file__).with_name('kafka_dev.py'))
kafka = importlib.util.module_from_spec(spec)
spec.loader.exec_module(kafka)


class KafkaSafetyTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.root = Path(self.temporary.name).resolve()
        self.java = self.root / 'jdk'
        (self.java / 'bin').mkdir(parents=True)
        (self.java / 'bin/java').write_text('fixture only')
        (self.java / 'bin/javac').write_text('fixture only')
        (self.java / 'release').write_text('JAVA_VERSION="21.0.1"\n')
        kafka.save_json(self.root / 'runtime.json', {'owner': kafka.PARENT_OWNER, 'runtimeRoot': str(self.root), 'javaHome': str(self.java)})

    def tearDown(self):
        self.temporary.cleanup()

    def owned(self):
        return kafka.owned_runtime(self.root)

    def test_unknown_directory_is_not_adopted(self):
        home = self.root / 'kafka'
        home.mkdir()
        sentinel = home / 'existing-data'
        sentinel.write_text('preserve')
        with self.assertRaises(RuntimeError):
            self.owned()
        self.assertEqual('preserve', sentinel.read_text())
        self.assertFalse((home / 'owner.json').exists())

    def test_parent_owner_and_java_version_are_checked(self):
        kafka.save_json(self.root / 'runtime.json', {'owner': 'other', 'runtimeRoot': str(self.root), 'javaHome': str(self.java)})
        with self.assertRaises(RuntimeError):
            self.owned()
        kafka.save_json(self.root / 'runtime.json', {'owner': kafka.PARENT_OWNER, 'runtimeRoot': str(self.root), 'javaHome': str(self.java)})
        (self.java / 'release').write_text('JAVA_VERSION="17.0.1"\n')
        with self.assertRaises(RuntimeError):
            self.owned()

    def test_symlink_components_are_refused(self):
        _, home, _, _ = self.owned()
        outside = self.root / 'outside'
        outside.mkdir()
        (home / 'data').symlink_to(outside)
        with self.assertRaises(RuntimeError):
            self.owned()
        self.assertEqual([], list(outside.iterdir()))

    def test_control_operations_are_mutually_exclusive(self):
        _, home, _, _ = self.owned()
        with kafka.control_lock(home):
            with self.assertRaises(RuntimeError):
                with kafka.control_lock(home):
                    pass

    def test_format_requires_new_empty_owned_data(self):
        _, home, _, marker = self.owned()
        marker.update(clusterId='A' * 22, formatted=False)
        self.assertFalse(kafka.validate_data(home, marker, permit_new=True))
        (home / 'data/unexpected').write_text('keep')
        with self.assertRaises(RuntimeError):
            kafka.validate_data(home, marker, permit_new=True)
        self.assertEqual('keep', (home / 'data/unexpected').read_text())

    def test_missing_formatted_data_and_wrong_cluster_are_never_reformatted(self):
        _, home, _, marker = self.owned()
        marker.update(clusterId='A' * 22, formatted=True)
        with self.assertRaises(RuntimeError):
            kafka.validate_data(home, marker, permit_new=True)
        (home / 'data').mkdir()
        (home / 'data/meta.properties').write_text('cluster.id=' + 'B' * 22 + '\nnode.id=1\n')
        with self.assertRaises(RuntimeError):
            kafka.validate_data(home, marker, permit_new=True)

    def test_existing_matching_data_is_only_validated(self):
        _, home, _, marker = self.owned()
        marker.update(clusterId='A' * 22, formatted=True)
        (home / 'data').mkdir()
        content = 'cluster.id=' + 'A' * 22 + '\nnode.id=1\n'
        metadata = home / 'data/meta.properties'
        metadata.write_text(content)
        before = metadata.stat().st_mtime_ns
        self.assertTrue(kafka.validate_data(home, marker, permit_new=True))
        self.assertEqual(before, metadata.stat().st_mtime_ns)

    def test_pid_reuse_cannot_signal_unknown_process(self):
        _, home, _, marker = self.owned()
        kafka.save_json(home / 'process.json', {'pid': 12345, 'identity': 'old identity'})
        with patch.object(kafka, 'process_identity', return_value='different process'), patch.object(kafka.os, 'kill') as kill:
            with self.assertRaises(RuntimeError):
                kafka.stop(home, marker)
            kill.assert_not_called()

    def test_owned_stop_uses_one_sigterm_and_preserves_data(self):
        _, home, _, marker = self.owned()
        identity = 'start /java -Drynew.governance.kafka=' + marker['instanceId'] + ' -cp ' + str(home) + '/libs/* kafka.Kafka server.properties'
        kafka.save_json(home / 'process.json', {'pid': 12345, 'identity': identity})
        (home / 'data').mkdir()
        (home / 'data/sentinel').write_text('preserve')
        with patch.object(kafka, 'process_identity', side_effect=[identity, identity, None]), patch.object(kafka.os, 'kill') as kill:
            self.assertTrue(kafka.stop(home, marker)['stopped'])
            kill.assert_called_once_with(12345, signal.SIGTERM)
        self.assertEqual('preserve', (home / 'data/sentinel').read_text())

    def test_port_conflict_is_refused_without_signal(self):
        with socket.socket() as listener:
            listener.bind(('127.0.0.1', 0))
            listener.listen()
            with patch.object(kafka.os, 'kill') as kill:
                with self.assertRaises(RuntimeError):
                    kafka.free_port(listener.getsockname()[1])
                kill.assert_not_called()

    def test_config_is_pinned_to_loopback_and_owned_data(self):
        _, home, _, _ = self.owned()
        content = kafka.config_text(home)
        self.assertIn('advertised.listeners=PLAINTEXT://127.0.0.1:19092', content)
        self.assertIn('controller.quorum.bootstrap.servers=127.0.0.1:19093', content)
        self.assertNotIn('controller.quorum.voters=', content)
        kafka.secure_write(home / 'private/server.properties', content.replace('127.0.0.1:19092', '0.0.0.0:19092'))
        with self.assertRaises(RuntimeError):
            kafka.validate_config(home)

    def test_artifact_urls_paths_and_special_members_are_rejected(self):
        for url in ['http://downloads.apache.org/kafka/x', 'https://example.invalid/kafka/x', 'https://downloads.apache.org@example.invalid/kafka/x', 'https://downloads.apache.org/nifi/x']:
            with self.assertRaises(RuntimeError):
                kafka.official_url(url)
        for name, kind in [('../escape', tarfile.REGTYPE), ('kafka/link', tarfile.SYMTYPE), ('/kafka/file', tarfile.REGTYPE), ('kafka/fifo', tarfile.FIFOTYPE)]:
            member = tarfile.TarInfo(name)
            member.type = kind
            with self.assertRaises(RuntimeError):
                kafka.validate_tar([member], 'kafka')

    def test_child_environment_does_not_change_global_java_or_path(self):
        with patch.dict(os.environ, {'JAVA_TOOL_OPTIONS': 'unapproved', 'JDK_JAVA_OPTIONS': 'unapproved', 'PATH': 'unchanged'}):
            env = kafka.child_environment(self.java)
            self.assertNotIn('JAVA_TOOL_OPTIONS', env)
            self.assertNotIn('JDK_JAVA_OPTIONS', env)
            self.assertEqual('unchanged', env['PATH'])
            self.assertEqual('unapproved', os.environ['JAVA_TOOL_OPTIONS'])

    def test_server_classpath_does_not_include_probe_classes(self):
        _, home, java, marker = self.owned()
        package = home / 'apps/kafka'
        server = kafka.java_command(java, home, package, marker, 'kafka.Kafka')
        self.assertEqual(str(package / 'libs/*'), server[server.index('-cp') + 1])
        probe = kafka.java_command(java, home, package, marker, 'KafkaDevProbe')
        self.assertTrue(probe[probe.index('-cp') + 1].startswith(str(home / 'tools/classes') + os.pathsep))


if __name__ == '__main__':
    unittest.main()
