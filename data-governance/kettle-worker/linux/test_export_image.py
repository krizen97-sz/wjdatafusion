"""Synthetic offline format/hash tests; no network, Docker, or rootfs extraction."""
import copy
import gzip
import hashlib
import importlib.util
import io
import json
from pathlib import Path
import tarfile
import tempfile
import unittest
import urllib.request

SPEC = importlib.util.spec_from_file_location('export_image', Path(__file__).with_name('export_image.py'))
exporter = importlib.util.module_from_spec(SPEC); SPEC.loader.exec_module(exporter)


def encoded(value): return json.dumps(value, separators=(',', ':')).encode()
def checksum(value): return 'sha256:' + hashlib.sha256(value).hexdigest()


class ExportTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(); self.addCleanup(self.temp.cleanup); self.root = Path(self.temp.name)
        content = io.BytesIO()
        with tarfile.open(fileobj=content, mode='w') as tar:
            # Even a hostile layer member stays bytes: it is never extracted onto this machine.
            item = tarfile.TarInfo('../../synthetic-escape'); item.size = 9; tar.addfile(item, io.BytesIO(b'synthetic'))
        self.layer = content.getvalue(); self.compressed = gzip.compress(self.layer, mtime=0)
        self.diff_id = checksum(self.layer); self.blob = self.root / 'layer.blob'; self.blob.write_bytes(self.compressed)
        self.descriptor = {'digest': checksum(self.compressed), 'size': len(self.compressed), 'mediaType': 'application/vnd.oci.image.layer.v1.tar+gzip'}
        self.config = encoded({'os': 'linux', 'architecture': 'amd64', 'rootfs': {'type': 'layers', 'diff_ids': [self.diff_id]}})
        self.manifest = encoded({'schemaVersion': 2, 'config': {'digest': checksum(self.config), 'size': len(self.config)}, 'layers': [self.descriptor]})
        self.index = encoded({'manifests': [{'digest': checksum(self.manifest), 'size': len(self.manifest), 'platform': {'os': 'linux', 'architecture': 'amd64'}}]})
        self.lock = {'configDigest': checksum(self.config), 'image': 'docker.io/library/eclipse-temurin@' + checksum(self.manifest), 'indexDigest': checksum(self.index), 'platform': 'linux/amd64'}
    def test_complete_chain_and_load_archive_readback(self):
        exporter.verify_metadata(self.lock, self.index, self.manifest, self.config)
        path = exporter.inflate(self.blob, self.descriptor, self.diff_id, self.root / 'layer.tar')
        archive = self.root / 'image.tar.gz'
        first = exporter.archive_image(self.lock, self.config, [(self.diff_id, path)], archive)
        self.assertEqual(first, exporter.verify_archive(self.lock, archive))
        other = self.root / 'same.tar.gz'
        self.assertEqual(first, exporter.archive_image(self.lock, self.config, [(self.diff_id, path)], other))
        self.assertFalse((self.root / 'synthetic-escape').exists())
        with tarfile.open(archive) as tar:
            manifest = json.load(tar.extractfile('manifest.json'))[0]
            self.assertEqual([], manifest['RepoTags']); self.assertEqual(3, len(tar.getmembers()))
    def test_each_metadata_digest_is_verified(self):
        original = [self.index, self.manifest, self.config]
        for position in range(3):
            broken = original.copy(); broken[position] += b' '
            with self.assertRaises(RuntimeError): exporter.verify_metadata(self.lock, *broken)
    def test_compressed_and_uncompressed_digests_are_independent(self):
        wrong = copy.deepcopy(self.descriptor); wrong['digest'] = 'sha256:' + '0' * 64
        with self.assertRaises(RuntimeError): exporter.inflate(self.blob, wrong, self.diff_id, self.root / 'out.tar')
        with self.assertRaises(RuntimeError): exporter.inflate(self.blob, self.descriptor, 'sha256:' + '0' * 64, self.root / 'out.tar')
        self.assertFalse((self.root / 'out.tar').exists())
    def test_cached_layer_is_reverified(self):
        target = self.root / 'out.tar'; target.write_bytes(b'changed')
        with self.assertRaises(RuntimeError): exporter.inflate(self.blob, self.descriptor, self.diff_id, target)
    def test_archive_rejects_changed_config_layer_and_order(self):
        path = self.root / 'out.tar'; path.write_bytes(self.layer)
        with self.assertRaises(RuntimeError): exporter.archive_image(self.lock, self.config + b' ', [(self.diff_id, path)], self.root / 'a.tar.gz')
        with self.assertRaises(RuntimeError): exporter.archive_image(self.lock, self.config, [], self.root / 'b.tar.gz')
        path.write_bytes(b'changed')
        with self.assertRaises(RuntimeError): exporter.archive_image(self.lock, self.config, [(self.diff_id, path)], self.root / 'c.tar.gz')
    def test_digest_cannot_inject_archive_paths(self):
        for value in ['sha256:../../escape', 'sha256:' + '0' * 64 + '/file', 'latest', 'sha256:' + '0' * 63]:
            with self.assertRaises(RuntimeError): exporter.hex_digest(value)
    def test_linked_blob_cache_and_inflated_target_are_rejected(self):
        target = self.root / 'linked'; target.symlink_to(self.blob)
        with self.assertRaises(RuntimeError): exporter.Registry().fetch('blobs', self.descriptor['digest'], target)
        with self.assertRaises(RuntimeError): exporter.inflate(self.blob, self.descriptor, self.diff_id, target)
    def test_registry_redirect_keeps_https_and_drops_cross_host_auth(self):
        request = urllib.request.Request('https://registry-1.docker.io/v2/blobs/test', headers={'Authorization': 'Bearer synthetic-only'})
        handler = exporter.HTTPSRedirect()
        redirected = handler.redirect_request(request, None, 307, '', {}, 'https://production.cloudflare.docker.com/synthetic')
        self.assertFalse(redirected.has_header('Authorization'))
        with self.assertRaises(RuntimeError): handler.redirect_request(request, None, 307, '', {}, 'http://example.invalid/')


if __name__ == '__main__': unittest.main()
