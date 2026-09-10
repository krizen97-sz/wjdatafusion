#!/usr/bin/env python3
"""Export the reviewed official image without Docker, mounting, or extracting its rootfs."""
import argparse
from concurrent.futures import ThreadPoolExecutor
import gzip
import hashlib
import io
import json
import os
from pathlib import Path
import re
import ssl
import tarfile
import time
import urllib.parse
import urllib.request

REGISTRY = 'https://registry-1.docker.io/v2/library/eclipse-temurin/'
TOKEN = 'https://auth.docker.io/token?service=registry.docker.io&scope=repository:library/eclipse-temurin:pull'
GZIP_TYPES = {'application/vnd.oci.image.layer.v1.tar+gzip', 'application/vnd.docker.image.rootfs.diff.tar.gzip'}
TAR_TYPES = {'application/vnd.oci.image.layer.v1.tar', 'application/vnd.docker.image.rootfs.diff.tar'}


def require(value, message):
    if not value: raise RuntimeError(message)


def hex_digest(value):
    require(isinstance(value, str) and re.fullmatch(r'sha256:[a-f0-9]{64}', value), 'Expected a complete sha256 digest')
    return value[7:]


def sha(path):
    result = hashlib.sha256()
    with Path(path).open('rb') as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b''): result.update(block)
    return 'sha256:' + result.hexdigest()


class HTTPSRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        require(urllib.parse.urlsplit(newurl).scheme == 'https', 'Refusing non-HTTPS registry redirect')
        redirected = super().redirect_request(req, fp, code, msg, headers, newurl)
        if urllib.parse.urlsplit(req.full_url).netloc != urllib.parse.urlsplit(newurl).netloc:
            redirected.remove_header('Authorization')
        return redirected


class Registry:
    def __init__(self):
        context = ssl.create_default_context(cafile='/etc/ssl/cert.pem' if Path('/etc/ssl/cert.pem').exists() else None)
        self.opener = urllib.request.build_opener(HTTPSRedirect(), urllib.request.HTTPSHandler(context=context))

    def fetch(self, kind, checksum, target, size=None):
        hex_digest(checksum)
        require(kind in {'manifests', 'blobs'}, 'Unknown registry object kind')
        target = Path(target)
        require(not target.is_symlink(), 'Refusing linked cached object')
        if target.exists():
            require(sha(target) == checksum and (size is None or target.stat().st_size == size), 'Cached object checksum/size differs')
            return target
        partial = target.with_suffix('.partial')
        require(not partial.exists() and not partial.is_symlink(), 'Remove or audit the incomplete object before retrying')
        for attempt in range(4):
            try:
                # Anonymous pull token stays in memory and is never written or logged.
                with self.opener.open(TOKEN, timeout=60) as response: token = json.load(response)['token']
                request = urllib.request.Request(REGISTRY + kind + '/' + checksum, headers={
                    'Authorization': 'Bearer ' + token,
                    'Accept': 'application/vnd.oci.image.index.v1+json, application/vnd.oci.image.manifest.v1+json, application/vnd.docker.distribution.manifest.v2+json, application/vnd.docker.distribution.manifest.list.v2+json'})
                actual = hashlib.sha256(); length = 0
                with self.opener.open(request, timeout=60) as response, partial.open('xb') as output:
                    while True:
                        block = response.read(1024 * 1024)
                        if not block: break
                        length += len(block)
                        require(length <= (size if size is not None else 4 * 1024 * 1024), 'Registry object exceeds locked size')
                        actual.update(block); output.write(block)
                    output.flush(); os.fsync(output.fileno())
                require('sha256:' + actual.hexdigest() == checksum, 'Registry object SHA256 differs')
                require(size is None or length == size, 'Registry object size differs')
                partial.rename(target)
                print(json.dumps({'verifiedObject': checksum, 'bytes': length}), flush=True)
                return target
            except Exception:
                if partial.exists(): partial.unlink()
                if attempt == 3: raise RuntimeError('Official registry download failed for ' + checksum) from None
                time.sleep(attempt + 1)


def verify_metadata(lock, index_bytes, manifest_bytes, config_bytes):
    manifest_digest = lock['image'].split('@', 1)[1]
    for content, expected in [(index_bytes, lock['indexDigest']), (manifest_bytes, manifest_digest), (config_bytes, lock['configDigest'])]:
        require('sha256:' + hashlib.sha256(content).hexdigest() == expected, 'Metadata checksum differs')
    index, manifest, config = map(json.loads, (index_bytes, manifest_bytes, config_bytes))
    require(lock['platform'] == 'linux/amd64' and config['os'] == 'linux' and config['architecture'] == 'amd64', 'Image must be the reviewed Linux amd64 platform')
    selected = [m for m in index['manifests'] if m['digest'] == manifest_digest and m.get('platform', {}).get('os') == 'linux' and m.get('platform', {}).get('architecture') == 'amd64']
    require(len(selected) == 1 and selected[0]['size'] == len(manifest_bytes), 'Locked platform is not present exactly once in the official index')
    require(manifest['schemaVersion'] == 2 and manifest['config']['digest'] == lock['configDigest'] and manifest['config']['size'] == len(config_bytes), 'Manifest config descriptor differs')
    require(config['rootfs']['type'] == 'layers' and len(manifest['layers']) == len(config['rootfs']['diff_ids']), 'Layer/diff ID count differs')
    for layer, diff_id in zip(manifest['layers'], config['rootfs']['diff_ids']):
        hex_digest(layer['digest']); hex_digest(diff_id)
        require(type(layer['size']) is int and 0 < layer['size'] <= 1024 * 1024 * 1024, 'Layer descriptor size outside bounds')
        require(layer['mediaType'] in GZIP_TYPES | TAR_TYPES and not layer.get('urls'), 'Unsupported or external layer descriptor')
    return manifest, config


def inflate(blob, descriptor, diff_id, target):
    require(sha(blob) == descriptor['digest'] and blob.stat().st_size == descriptor['size'], 'Compressed layer checksum/size differs')
    require(not target.is_symlink(), 'Refusing linked inflated layer')
    if target.exists():
        require(sha(target) == diff_id, 'Cached diff ID differs'); return target
    partial = target.with_suffix('.partial')
    require(not partial.exists() and not partial.is_symlink(), 'Unexpected incomplete layer')
    opener = gzip.open if descriptor['mediaType'] in GZIP_TYPES else open
    actual = hashlib.sha256(); count = 0
    try:
        with opener(blob, 'rb') as source, partial.open('xb') as output:
            for block in iter(lambda: source.read(1024 * 1024), b''):
                count += len(block); require(count <= 4 * 1024 * 1024 * 1024, 'Uncompressed layer exceeds 4 GiB limit')
                actual.update(block); output.write(block)
            output.flush(); os.fsync(output.fileno())
        require('sha256:' + actual.hexdigest() == diff_id, 'Uncompressed rootfs diff ID differs')
        partial.rename(target)
        return target
    finally:
        if partial.exists(): partial.unlink()


def archive_image(lock, config_bytes, layers, target):
    require('sha256:' + hashlib.sha256(config_bytes).hexdigest() == lock['configDigest'], 'Archive config is not the locked official config')
    require([diff_id for diff_id, path in layers] == json.loads(config_bytes)['rootfs']['diff_ids'], 'Archive layer order differs from rootfs')
    config_name = hex_digest(lock['configDigest']) + '.json'
    members = [hex_digest(diff_id) + '/layer.tar' for diff_id, path in layers]
    manifest = json.dumps([{'Config': config_name, 'RepoTags': [], 'Layers': members}], separators=(',', ':')).encode()
    require(not target.exists() and not target.is_symlink(), 'Archive already exists; preserve immutable delivery')
    partial = target.with_suffix('.partial')
    require(not partial.exists() and not partial.is_symlink(), 'Unexpected incomplete archive; audit it before retrying')
    try:
        with partial.open('xb') as raw, gzip.GzipFile(filename='', fileobj=raw, mode='wb', mtime=0) as compressed, tarfile.open(fileobj=compressed, mode='w|') as archive:
            def add(name, length, stream):
                info = tarfile.TarInfo(name); info.size = length; info.mode = 0o644; info.mtime = 0
                archive.addfile(info, stream)
            add('manifest.json', len(manifest), io.BytesIO(manifest))
            add(config_name, len(config_bytes), io.BytesIO(config_bytes))
            for name, (diff_id, path) in zip(members, layers):
                require(sha(path) == diff_id, 'Layer changed before archive creation')
                with path.open('rb') as stream: add(name, path.stat().st_size, stream)
        partial.rename(target)
    finally:
        if partial.exists(): partial.unlink()
    return sha(target)


def verify_archive(lock, target):
    """Read back every outer member; never unpack any file or execute a layer."""
    with tarfile.open(target, 'r|gz') as archive:
        entry = archive.next(); require(entry and entry.name == 'manifest.json' and entry.isfile() and entry.size <= 4096, 'Invalid archive manifest member')
        manifests = json.load(archive.extractfile(entry))
        require(len(manifests) == 1 and manifests[0]['RepoTags'] == [], 'Archive must contain exactly one untagged image')
        manifest = manifests[0]
        entry = archive.next()
        require(entry and entry.isfile() and entry.name == manifest['Config'] == hex_digest(lock['configDigest']) + '.json' and entry.size <= 4 * 1024 * 1024, 'Invalid archive config member')
        config_bytes = archive.extractfile(entry).read()
        require('sha256:' + hashlib.sha256(config_bytes).hexdigest() == lock['configDigest'], 'Archive config SHA differs')
        config = json.loads(config_bytes)
        require(config['os'] == 'linux' and config['architecture'] == 'amd64', 'Archive platform differs')
        diff_ids = config['rootfs']['diff_ids']
        require(manifest['Layers'] == [hex_digest(value) + '/layer.tar' for value in diff_ids], 'Archive layer names/order differ')
        for name, diff_id in zip(manifest['Layers'], diff_ids):
            entry = archive.next(); require(entry and entry.isfile() and entry.name == name, 'Invalid archive layer member')
            actual = hashlib.sha256()
            source = archive.extractfile(entry)
            for block in iter(lambda: source.read(1024 * 1024), b''): actual.update(block)
            require('sha256:' + actual.hexdigest() == diff_id, 'Archive readback rootfs diff ID differs')
        require(archive.next() is None, 'Unexpected archive members')
    return sha(target)


def export(output):
    lock = json.loads(Path(__file__).with_name('image-lock.json').read_text())
    require(lock['registry'] == REGISTRY and lock['image'].startswith('docker.io/library/eclipse-temurin@sha256:'), 'Only reviewed official registry is supported')
    output = Path(output).absolute()
    require(not any(p.is_symlink() for p in [output, *output.parents]), 'Output must not traverse symbolic links')
    output.mkdir(parents=True, exist_ok=True, mode=0o700)
    cache = output / 'verified-objects'; cache.mkdir(exist_ok=True, mode=0o700)
    require(not cache.is_symlink(), 'Linked object directory')
    registry = Registry()
    index = registry.fetch('manifests', lock['indexDigest'], cache / (hex_digest(lock['indexDigest']) + '.json'))
    manifest = registry.fetch('manifests', lock['image'].split('@')[1], cache / (hex_digest(lock['image'].split('@')[1]) + '.json'))
    descriptor = json.loads(manifest.read_bytes())['config']
    require(descriptor['digest'] == lock['configDigest'], 'Config descriptor is not locked')
    config = registry.fetch('blobs', lock['configDigest'], cache / (hex_digest(lock['configDigest']) + '.json'), descriptor['size'])
    manifest_data, config_data = verify_metadata(lock, index.read_bytes(), manifest.read_bytes(), config.read_bytes())
    def layer(pair):
        descriptor, diff_id = pair
        blob = registry.fetch('blobs', descriptor['digest'], cache / (hex_digest(descriptor['digest']) + '.blob'), descriptor['size'])
        inflated = inflate(blob, descriptor, diff_id, cache / (hex_digest(diff_id) + '.tar'))
        return diff_id, inflated
    with ThreadPoolExecutor(max_workers=3) as pool:
        layers = list(pool.map(layer, zip(manifest_data['layers'], config_data['rootfs']['diff_ids'])))
    target = output / 'eclipse-temurin-17-jre-jammy-linux-amd64.docker.tar.gz'
    archive_sha = archive_image(lock, config.read_bytes(), layers, target)
    require(verify_archive(lock, target) == archive_sha, 'Archive SHA changed during readback')
    result = {'image': lock['image'], 'platform': lock['platform'], 'configDigest': lock['configDigest'], 'indexDigest': lock['indexDigest'],
              'archive': target.name, 'archiveSha256': archive_sha, 'archiveBytes': target.stat().st_size,
              'layers': [{'compressedDigest': item['digest'], 'compressedBytes': item['size'], 'diffId': diff_id, 'uncompressedBytes': path.stat().st_size}
                         for item, (diff_id, path) in zip(manifest_data['layers'], layers)],
              'archiveReadbackVerified': True, 'rootfsExtracted': False, 'dockerInvoked': False, 'imageExecuted': False, 'dockerLoadVerified': False,
              'expectedLoadedImageId': lock['configDigest'], 'repoDigestsRequired': False,
              'loadCommand': ['docker', 'image', 'load', '--input', target.name]}
    (output / 'verification.json').write_text(json.dumps(result, indent=2) + '\n')
    (output / 'SHA256SUMS').write_text(archive_sha[7:] + '  ' + target.name + '\n')
    print(json.dumps(result, indent=2))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__); parser.add_argument('--output', required=True)
    export(parser.parse_args().output)
