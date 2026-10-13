import hashlib
import importlib.util
import json
from pathlib import Path
import stat
import tempfile
import unittest
import zipfile

SPEC = importlib.util.spec_from_file_location('hdi_native_bundle', Path(__file__).resolve().parents[1] / 'hdi_native_bundle.py')
bundle = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(bundle)


class NativeBundleTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name).resolve()
        self.archive = self.root / 'original.zip'
        self.plugin = 'etl-fileInput-2.0.0'
        self.front = b'original frontend - no modifications'
        self.contents = {
            **{bundle.INSTANCE + value: ('core:' + value).encode() for value in bundle.CORE},
            bundle.INSTANCE + 'lib/original-dependency.jar': b'original-core-library',
            bundle.INSTANCE + self.plugin + '/' + self.plugin + '.jar': b'original-plugin-jar',
            bundle.INSTANCE + self.plugin + '/adapter.properties': b'adapterName=fileInput',
            bundle.INSTANCE + self.plugin + '/etl_plugin.json': b'{"original":true}',
            bundle.INSTANCE + self.plugin + '/lib/driver.jar': b'original-driver',
            bundle.INSTANCE + self.plugin + '/config/i18n/messages_zh_CN.properties': b'original labels',
            bundle.INSTANCE + self.plugin + '/plugin-web-config/config.json': b'{"originalForm":true}',
            bundle.INSTANCE + self.plugin + '/plugin-web-config/modules/input/index.js': b'original form',
            bundle.INSTANCE + self.plugin + '/keystore.p12': b'must never copy',
            bundle.INSTANCE + self.plugin + '/config/credentials.properties': b'must never copy',
            bundle.INSTANCE + 'conf/etl_instance.properties': b'must never copy',
            bundle.INSTANCE + 'certs/server.keystore': b'must never copy',
            'conf/license.lic': b'must never copy',
            'bin/detl/dEtlv2.jar': b'not the selected engine',
            **{bundle.TASK_SERVICE + name: ('task-service:' + name).encode()
               for name in bundle.TASK_SERVICE_RESOURCES},
            'bin/detl/lib/original-task-service.jar': b'original-task-service-dependency',
            'bin/detl/ext/normal/original-kafka.jar': b'original-x86-extension',
            'bin/detl/ext/huawei/arm-library.jar': b'not an x86 dependency',
            'bin/detl/config/application.properties': b'private or site configuration',
            'bin/detl/config/bootstrap.properties': b'private or site configuration',
            'bin/detl/config/certs/server.keystore': b'must never copy private keys',
            'bin/gateway/gateway.jar': b'not the task service',
            'bin/zookeeper/lib/zookeeper-3.8.4.jar': b'original-coordinator-library',
            'bin/zookeeper/conf/zoo.cfg': b'must not reuse site configuration',
            'bin/zookeeper/data/snapshot.1': b'must not reuse state',
            bundle.PUBLIC + 'detlModuleEntry.js': self.front,
            bundle.NOTICE: b'original copyright notice',
        }
        self.build_zip()
        task = {'sourceVersion': 'test-original-v1', 'fileCount': 1, 'bytes': len(self.front), 'files': [
            {'path': 'detlModuleEntry.js', 'bytes': len(self.front), 'sha256': hashlib.sha256(self.front).hexdigest()}]}
        self.task = self.root / 'manifest.task.json'
        self.task.write_text(json.dumps(task))
        self.policy = self.root / 'manifest.source.json'
        self.allow_current_zip()

    def tearDown(self):
        self.temp.cleanup()

    def build_zip(self, extra=None):
        with zipfile.ZipFile(self.archive, 'w', compression=zipfile.ZIP_DEFLATED) as archive:
            for name, content in self.contents.items():
                archive.writestr(name, content)
            if extra:
                extra(archive)

    def allow_current_zip(self):
        self.policy.write_text(json.dumps({'schemaVersion': 1, 'archives': {
            bundle.file_digest(self.archive): {'bytes': self.archive.stat().st_size, 'frontendVersion': 'test-original-v1'}},
            'taskStaticManifest': self.task.name, 'taskStaticManifestSha256': bundle.file_digest(self.task)}))

    def package(self, name='out', plugins=None, **kwargs):
        return bundle.package(self.archive, self.root / name, plugins=[self.plugin] if plugins is None else plugins, source_manifest=self.policy, **kwargs)

    def test_selected_originals_are_byte_exact_with_provenance_and_no_private_configuration(self):
        result = self.package()
        output = self.root / 'out'
        document = json.loads((output / 'bundle-manifest.json').read_text())
        self.assertFalse(result['executionVerified'])
        self.assertFalse(document['runtimeConfigurationIncluded'])
        self.assertFalse(document['activationLicenseIncluded'])
        self.assertEqual([self.plugin], document['plugins'])
        for record in document['files']:
            self.assertEqual(self.contents[record['source']['entry']], (output / record['path']).read_bytes())
            self.assertEqual(bundle.file_digest(self.archive), record['source']['archiveSha256'])
        self.assertTrue((output / 'public/detlModuleEntry.js').is_file())
        self.assertTrue((output / f'instance/{self.plugin}/plugin-web-config/config.json').is_file())
        self.assertTrue((output / f'instance/{self.plugin}/config/i18n/messages_zh_CN.properties').is_file())
        self.assertTrue((output / 'notices/Open Source Software Licenses.txt').is_file())
        for forbidden in ['instance/conf', 'instance/certs', f'instance/{self.plugin}/keystore.p12', f'instance/{self.plugin}/config/credentials.properties']:
            self.assertFalse((output / forbidden).exists())
        self.assertTrue(bundle.verify(output)['verified'])

    def test_unreviewed_archive_is_rejected_before_any_output(self):
        self.contents[bundle.INSTANCE + 'lib/original-dependency.jar'] = b'tampered'
        self.build_zip()
        with self.assertRaisesRegex(ValueError, 'allowlist'):
            self.package()
        self.assertFalse((self.root / 'out').exists())

    def test_task_manifest_tampering_and_frontend_mismatch_are_rejected(self):
        original = self.task.read_bytes()
        self.task.write_text('{}')
        with self.assertRaisesRegex(ValueError, 'checksum'):
            self.package()
        self.task.write_bytes(original)
        self.contents[bundle.PUBLIC + 'detlModuleEntry.js'] = b'wrong frontend'
        self.build_zip(); self.allow_current_zip()
        with self.assertRaisesRegex(ValueError, 'checksum'):
            self.package()
        self.assertFalse((self.root / 'out').exists())

    def test_zip_traversal_even_unselected_entries_is_rejected(self):
        for invalid in ['../escape', '/absolute', 'bin/../escape', 'bin\\escape', 'bin/%2e%2e/escape', 'bin//escape']:
            with self.subTest(invalid=invalid):
                self.build_zip(lambda archive: archive.writestr(invalid, b'bad')); self.allow_current_zip()
                with self.assertRaises(ValueError):
                    self.package()
                self.assertFalse((self.root / 'out').exists())

    def test_zip_symlink_and_duplicate_entries_are_rejected(self):
        info = zipfile.ZipInfo('bin/instance/linked.jar'); info.create_system = 3; info.external_attr = (stat.S_IFLNK | 0o777) << 16
        self.build_zip(lambda archive: archive.writestr(info, '/etc/passwd')); self.allow_current_zip()
        with self.assertRaisesRegex(ValueError, 'links'):
            self.package()
        import warnings
        with warnings.catch_warnings():
            warnings.simplefilter('ignore', UserWarning)
            self.build_zip(lambda archive: archive.writestr(bundle.INSTANCE + 'adapter.properties', b'duplicate'))
        self.allow_current_zip()
        with self.assertRaisesRegex(ValueError, 'Duplicate'):
            self.package()

    def test_existing_output_and_symlinked_parent_are_not_modified(self):
        output = self.root / 'out'; output.mkdir(); (output / 'existing').write_text('keep')
        with self.assertRaisesRegex(ValueError, 'new bundle'):
            self.package()
        self.assertEqual('keep', (output / 'existing').read_text())
        (self.root / 'link').symlink_to(output, target_is_directory=True)
        with self.assertRaisesRegex(ValueError, 'symbolic'):
            self.package(name='link/bundle')

    def test_explicit_selection_rejects_unknown_duplicate_and_missing_plugin(self):
        for plugins in [[], ['etl-absent-1.0'], [self.plugin, self.plugin], ['../escape']]:
            with self.subTest(plugins=plugins), self.assertRaises(ValueError):
                self.package(plugins=plugins)
        del self.contents[bundle.INSTANCE + self.plugin + '/' + self.plugin + '.jar']
        self.build_zip(); self.allow_current_zip()
        with self.assertRaisesRegex(ValueError, 'Incomplete'):
            self.package()

    def test_verify_detects_changed_missing_extra_and_linked_files(self):
        self.package(); root = self.root / 'out'; target = root / 'public/detlModuleEntry.js'
        target.write_bytes(b'changed')
        with self.assertRaisesRegex(ValueError, 'verification failed'):
            bundle.verify(root)
        target.write_bytes(self.front); (root / 'unlisted-secret').write_text('not allowed')
        with self.assertRaisesRegex(ValueError, 'unlisted'):
            bundle.verify(root)
        (root / 'unlisted-secret').unlink(); target.unlink(); target.symlink_to(self.archive)
        with self.assertRaisesRegex(ValueError, 'symbolic'):
            bundle.verify(root)

    def test_all_plugins_is_explicit_and_does_not_copy_unselected_server(self):
        self.package(plugins=[], all_plugins=True)
        self.assertFalse((self.root / 'out/detl/dEtlv2.jar').exists())
        self.assertEqual([self.plugin], json.loads((self.root / 'out/bundle-manifest.json').read_text())['plugins'])

    def test_original_external_form_plugin_does_not_require_a_synthetic_plugin_json(self):
        del self.contents[bundle.INSTANCE + self.plugin + '/etl_plugin.json']
        self.build_zip(); self.allow_current_zip(); self.package()
        self.assertFalse((self.root / 'out' / 'instance' / self.plugin / 'etl_plugin.json').exists())

    def test_task_service_is_explicit_and_does_not_include_product_stack_or_site_secrets(self):
        self.package(include_task_service=True)
        root = self.root / 'out'
        manifest = json.loads((root / 'bundle-manifest.json').read_text())
        self.assertTrue(manifest['taskServiceIncluded'])
        self.assertFalse(manifest['executionVerified'])
        for name in bundle.TASK_SERVICE_RESOURCES:
            self.assertEqual(self.contents[bundle.TASK_SERVICE + name], (root / 'task-service' / name).read_bytes())
        self.assertEqual(b'original-task-service-dependency', (root / 'task-service/lib/original-task-service.jar').read_bytes())
        self.assertEqual(b'original-x86-extension', (root / 'task-service/ext/normal/original-kafka.jar').read_bytes())
        self.assertFalse((root / 'task-service/ext/huawei/arm-library.jar').exists())
        for name in ['config/application.properties', 'config/bootstrap.properties', 'config/certs/server.keystore']:
            self.assertFalse((root / 'task-service' / name).exists())
        self.assertFalse((root / 'bin/gateway/gateway.jar').exists())
        self.assertTrue(bundle.verify(root)['verified'])

    def test_incomplete_task_service_is_rejected_before_publishing_any_bundle(self):
        for missing in ['bin/detl/dEtlv2.jar', 'bin/detl/lib/original-task-service.jar']:
            with self.subTest(missing=missing):
                value = self.contents.pop(missing)
                self.build_zip(); self.allow_current_zip()
                with self.assertRaises(ValueError):
                    self.package(include_task_service=True)
                self.assertFalse((self.root / 'out').exists())
                self.contents[missing] = value

    def test_coordinator_is_explicit_and_excludes_configuration_and_data(self):
        self.package(name='without')
        self.assertFalse((self.root / 'without/zookeeper').exists())
        self.package(include_coordinator=True)
        root = self.root / 'out'
        self.assertEqual(b'original-coordinator-library', (root / 'zookeeper/lib/zookeeper-3.8.4.jar').read_bytes())
        self.assertFalse((root / 'zookeeper/conf').exists())
        self.assertFalse((root / 'zookeeper/data').exists())
        self.assertTrue(json.loads((root / 'bundle-manifest.json').read_text())['coordinatorIncluded'])


if __name__ == '__main__':
    unittest.main()
