import hashlib
import json
from pathlib import Path
import sys
import tempfile
import unittest
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from hdi_platform_adapters import verify_platform_adapters, install_platform_adapters
from hdi_core_protocol import ADAPTERS, ProtocolError


class PlatformAdapterTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name).resolve()
        self.package, self.bundle = self.root / 'platform', self.root / 'original'
        self.package.mkdir(); self.bundle.mkdir()
        self.files = []
        def write(relative, value, origin='platform-registration'):
            path = self.package / relative; path.parent.mkdir(exist_ok=True, parents=True); path.write_bytes(value)
            self.files.append({'path': relative, 'bytes': len(value), 'sha256': hashlib.sha256(value).hexdigest(), 'origin': origin})
        write('lib/rynew-mysql-jdbc-adapter.jar', b'platform-jdbc-subclasses', 'platform-build')
        dependency = self.bundle / 'instance/lib/jdbc.jar'; dependency.parent.mkdir(parents=True); dependency.write_bytes(b'original-jdbc-algorithms')
        dep = {'path': 'instance/lib/jdbc.jar', 'bytes': dependency.stat().st_size, 'sha256': hashlib.sha256(dependency.read_bytes()).hexdigest()}
        (self.bundle / 'bundle-manifest.json').write_text(json.dumps({'sourceArchiveSha256': 'a' * 64, 'files': [dep]}))
        plugins = []
        for name in ('mysqlInput', 'mysqlOutput'):
            write(f'plugins/{name}/adapter.properties', name.encode())
            write(f'plugins/{name}/etl_plugin.json', b'{}')
            write(f'src/com/rynew/hdi/adapter/{name[0].upper() + name[1:]}.java', b'// source', 'platform-source')
            plugins.append({'adapterName': name, 'adapterVersion': '2.0.0', 'adapterType': ADAPTERS[name][0],
                'className': ADAPTERS[name][1], 'dbType': 2, 'properties': f'plugins/{name}/adapter.properties',
                'descriptor': f'plugins/{name}/etl_plugin.json', 'jar': 'lib/rynew-mysql-jdbc-adapter.jar',
                'dependencies': [{'bundlePath': dep['path'], 'bytes': dep['bytes'], 'sha256': dep['sha256'], 'target': 'lib/jdbc.jar'}]})
        self.data = {'schemaVersion': 1, 'kind': 'rynew-hdi-jdbc-platform-adapters', 'sourceArchiveSha256': 'a' * 64,
                     'plugins': plugins, 'files': self.files}
        self.persist()

    def persist(self):
        raw = json.dumps(self.data).encode(); (self.package / 'platform-adapter-manifest.json').write_bytes(raw)
        self.sha = hashlib.sha256(raw).hexdigest()

    def tearDown(self):
        self.temp.cleanup()

    def test_separate_receipt_installs_only_platform_subclass_and_original_dependencies(self):
        target = self.root / 'engine'; target.mkdir()
        install_platform_adapters(self.package, self.bundle, self.sha, target)
        for name in ('mysqlInput', 'mysqlOutput'):
            plugin = target / ('etl-' + name + '-2.0.0')
            self.assertEqual((plugin / 'lib/jdbc.jar').read_bytes(), b'original-jdbc-algorithms')
            self.assertEqual((plugin / ('etl-' + name + '-2.0.0.jar')).read_bytes(), b'platform-jdbc-subclasses')
        with self.assertRaises(FileExistsError):
            install_platform_adapters(self.package, self.bundle, self.sha, target)

    def test_manifest_tamper_dependency_change_and_extra_jar_fail_closed(self):
        with self.assertRaisesRegex(ProtocolError, 'MANIFEST_MISMATCH'):
            verify_platform_adapters(self.package, self.bundle, '0' * 64)
        (self.package / 'extra.jar').write_bytes(b'foreign')
        with self.assertRaisesRegex(ProtocolError, 'UNRECORDED_FILE'):
            verify_platform_adapters(self.package, self.bundle, self.sha)
        (self.package / 'extra.jar').unlink()
        (self.bundle / 'instance/lib/jdbc.jar').write_bytes(b'changed')
        with self.assertRaisesRegex(ProtocolError, 'DEPENDENCY_MISMATCH'):
            verify_platform_adapters(self.package, self.bundle, self.sha)

    def test_traversal_duplicate_targets_and_wrong_archive_are_rejected(self):
        original = json.loads(json.dumps(self.data))
        for modify in [lambda d: d['plugins'][0]['dependencies'][0].update(target='../bad.jar'),
                       lambda d: d['plugins'][0]['dependencies'].append(d['plugins'][0]['dependencies'][0].copy()),
                       lambda d: d.update(sourceArchiveSha256='b' * 64)]:
            self.data = json.loads(json.dumps(original)); modify(self.data); self.persist()
            with self.assertRaises(ProtocolError):
                verify_platform_adapters(self.package, self.bundle, self.sha)

    def test_linked_platform_jar_is_rejected_even_with_matching_digest(self):
        path = self.package / 'lib/rynew-mysql-jdbc-adapter.jar'
        outside = self.root / 'outside.jar'; path.rename(outside); path.symlink_to(outside)
        with self.assertRaisesRegex(ProtocolError, 'PATH_REJECTED'):
            verify_platform_adapters(self.package, self.bundle, self.sha)
