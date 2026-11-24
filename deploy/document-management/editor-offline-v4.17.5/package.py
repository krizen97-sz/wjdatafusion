#!/usr/bin/env python3
"""Assemble the verified editor release. Never read site credentials or a running service."""
import argparse
import gzip
import hashlib
import io
import json
from pathlib import Path
import subprocess
import tarfile


ROOT = Path(__file__).resolve().parents[3]
KIT = Path(__file__).resolve().parent
NAME = 'RYNEW-v4.17.5-offline-upgrade'
APP_COMMIT = '078744adef32a02a04f340cf4df8ca589560d41b'
DATABASE_FILES = (
    'support_check_20260929_server_credential_v4_8_4_to_v4_15_1.sql',
    'support_upgrade_20260929_server_credential_v4_8_4_to_v4_15_1_all.sql',
    'support_check_20260929_server_credential_v4_15_1_to_v4_16_0.sql',
    'support_upgrade_20260929_server_credential_v4_15_1_to_v4_16_0_all.sql',
    'support_check_20261012_server_credential_v4_16_0_to_v4_17_0.sql',
    'support_upgrade_20260929_server_credential_password_pool_v4_17_0.sql',
    'data_governance_check_20261026_v4_17_0_to_v4_17_2.sql',
    'data_governance_upgrade_20261026_v4_17_0_to_v4_17_2_all.sql',
)


def checksum(stream):
    digest = hashlib.sha256()
    for block in iter(lambda: stream.read(1024 * 1024), b''):
        digest.update(block)
    return digest.hexdigest()


def file_hash(path):
    with path.open('rb') as stream:
        return checksum(stream)


def git(*args):
    return subprocess.check_output(['git', *args], cwd=ROOT).decode().strip()


def package(payload, audit_path, out):
    if git('status', '--porcelain'):
        raise ValueError('Build the release kit from a clean committed checkout')
    if git('diff', '--name-only', APP_COMMIT, 'HEAD', '--', 'WDF100.0', 'RuoYi-Vue3-master'):
        raise ValueError('The application source differs from v4.17.5')
    audit = json.loads(audit_path.read_text())
    paths = {'README.md': KIT/'README.md', 'ENGINE.md': KIT/'ENGINE.md', 'bin/ops.py': KIT/'ops.py',
             'db/README.md': KIT/'database.md', 'payload/wjdatafusion-admin.jar': payload/'wjdatafusion-admin.jar',
             'evidence/payload-audit.json': audit_path,
             'evidence/payload-verification.json': audit_path.with_name('payload-verification.json')}
    for path in (KIT/'config').iterdir():
        if path.is_file():
            paths['config/'+path.name] = path
    for name in DATABASE_FILES:
        paths['db/previous/'+name] = ROOT/'WDF100.0/sql'/name
    for path in (payload/'dist').rglob('*'):
        if path.is_symlink():
            raise ValueError('Unexpected symlink in dist: ' + str(path))
        if path.is_file():
            paths['payload/'+path.relative_to(payload).as_posix()] = path
    hashes = {name: file_hash(path) for name, path in sorted(paths.items())}
    if (audit['sourceCommit'] != APP_COMMIT or audit['sourceStatus'] or
        hashes['payload/wjdatafusion-admin.jar'] != audit['deliveryJarSha256'] or
        hashes['payload/dist/index.html'] != audit['frontendIndexSha256'] or
        sum(k.startswith('payload/dist/') for k in hashes) != audit['frontendFiles']):
        raise ValueError('Build audit does not match the packaged artifact set')
    manifest = {'version': 'v4.17.5', 'applicationCommit': APP_COMMIT, 'packagingCommit': git('rev-parse', 'HEAD'),
                'jarSha256': audit['deliveryJarSha256'], 'indexSha256': audit['frontendIndexSha256'],
                'frontendFiles': audit['frontendFiles'], 'platformSqlFromV4172': [],
                'optionalEngine': 'RYNEW-Docs-9.4.0-linux-amd64-offline.tar'}
    metadata = {'RELEASE.json': (json.dumps(manifest, ensure_ascii=False, indent=2) + '\n').encode()}
    hashes['RELEASE.json'] = hashlib.sha256(metadata['RELEASE.json']).hexdigest()
    metadata['SHA256SUMS'] = ''.join(f'{h}  {p}\n' for p,h in sorted(hashes.items())).encode()
    out.parent.mkdir(parents=True, exist_ok=True)
    with out.open('xb') as raw, gzip.GzipFile(fileobj=raw, filename='', mode='wb',mtime=0,compresslevel=3) as zipped:
        with tarfile.open(fileobj=zipped,mode='w|') as tar:
            for name in sorted(set(paths)|set(metadata)):
                stream = io.BytesIO(metadata[name]) if name in metadata else paths[name].open('rb')
                with stream:
                    info = tarfile.TarInfo(NAME+'/'+name)
                    info.size = len(metadata[name]) if name in metadata else paths[name].stat().st_size
                    info.mode = 0o755 if name.startswith('bin/') else 0o644
                    tar.addfile(info,stream)
    with tarfile.open(out,'r|gz') as tar:
        seen = set()
        for entry in tar:
            name = entry.name[len(NAME)+1:]
            if not entry.name.startswith(NAME+'/') or not entry.isfile() or name in seen:
                raise ValueError('Invalid archive member: '+entry.name)
            expected = hashlib.sha256(metadata[name]).hexdigest() if name in metadata else hashes[name]
            if checksum(tar.extractfile(entry)) != expected:
                raise ValueError('Packaged checksum differs: '+name)
            seen.add(name)
        if seen != set(paths)|set(metadata):
            raise ValueError('Incomplete archive')
    print(json.dumps({**manifest, 'archiveSha256':file_hash(out),'archiveBytes':out.stat().st_size}, indent=2))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--payload', required=True, type=Path)
    parser.add_argument('--audit', required=True, type=Path)
    parser.add_argument('--out', required=True, type=Path)
    args = parser.parse_args()
    package(args.payload, args.audit, args.out)
