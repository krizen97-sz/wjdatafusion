import importlib.util
import json
from pathlib import Path
import tempfile
import unittest

spec = importlib.util.spec_from_file_location('hdi_proof', Path(__file__).with_name('hdi_original_execution_proof.py'))
proof = importlib.util.module_from_spec(spec)
spec.loader.exec_module(proof)


class OriginalBundleGuardTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name).resolve()
        (self.root / 'instance').mkdir()
        jar = self.root / 'instance/original.jar'
        jar.write_bytes(b'original-test-fixture')
        self.manifest = {'engine': 'original-detl', 'sourceArchiveSha256': proof.ARCHIVE_SHA,
                         'runtimeConfigurationIncluded': False, 'activationLicenseIncluded': False,
                         'files': [{'path': 'instance/original.jar', 'sha256': proof.digest(jar)}]}
        self.save()

    def tearDown(self):
        self.temp.cleanup()

    def save(self):
        (self.root / 'bundle-manifest.json').write_text(json.dumps(self.manifest))

    def test_original_program_only_inventory_passes(self):
        self.assertEqual('original-detl', proof.verify_bundle(self.root)['engine'])

    def test_tampered_or_unlisted_artifacts_are_rejected(self):
        jar = self.root / 'instance/original.jar'
        jar.write_bytes(b'changed')
        with self.assertRaisesRegex(ValueError, 'integrity'):
            proof.verify_bundle(self.root)
        jar.write_bytes(b'original-test-fixture')
        (self.root / 'instance/.broker-token').write_text('not-to-be-copied')
        with self.assertRaisesRegex(ValueError, 'unlisted'):
            proof.verify_bundle(self.root)

    def test_runtime_configuration_and_activation_files_are_rejected(self):
        for name in ('runtimeConfigurationIncluded', 'activationLicenseIncluded'):
            self.manifest[name] = True
            self.save()
            with self.assertRaisesRegex(ValueError, 'program-only'):
                proof.verify_bundle(self.root)
            self.manifest[name] = False

    def test_private_keystores_are_refused_even_when_listed(self):
        p = self.root / 'instance/private.keystore'
        p.write_bytes(b'synthetic-not-a-real-private-key')
        self.manifest['files'].append({'path': 'instance/private.keystore', 'sha256': proof.digest(p)})
        self.save()
        with self.assertRaisesRegex(ValueError, 'Private key'):
            proof.verify_bundle(self.root)

    def test_symlinked_library_is_rejected(self):
        original = self.root / 'instance/original.jar'
        original.unlink()
        original.symlink_to('/etc/hosts')
        with self.assertRaisesRegex(ValueError, 'integrity'):
            proof.verify_bundle(self.root)

    def test_original_public_code_signing_certificate_is_preserved(self):
        p = self.root / 'task-service/config/signverify/CodeSignRoot.crt'
        p.parent.mkdir(parents=True)
        p.write_bytes(b'synthetic-public-certificate')
        self.manifest['files'].append({'path': str(p.relative_to(self.root)), 'sha256': proof.digest(p)})
        self.save()
        proof.verify_bundle(self.root)


if __name__ == '__main__':
    unittest.main()
