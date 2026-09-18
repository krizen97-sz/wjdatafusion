#!/usr/bin/env python3
"""Create a source-only upgrade kit from an exact clean Git commit.

No application binaries, runtime state, private configuration or vendor archives
are included. The output directory must not already exist.
"""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess
import zipfile


ROOT = Path(__file__).resolve().parents[1]
GUIDE = 'deploy/document-management/upgrade-v4.4.1-to-v4.8.3/'
SQL = 'WDF100.0/sql/'
SQL_FILES = [
    'data_governance_upgrade_20260918_v4_4_1_to_v4_8_3_all.sql',
    'data_governance_verify_20260918_v4_8_3.sql',
    'data_governance_upgrade_20260918_v4_4_1_to_v4_8_3_README.md',
]
TOOLS = [
    'kettle_worker.py', 'kettle_linux_runtime.py', 'kettle_inventory.py',
    'build_compatibility.py', 'verify_nar.py', 'verify_artifacts.py',
    'artifact-lock.json', 'requirements-verification.txt',
]


def git(*args):
    return subprocess.check_output(['git', *args], cwd=ROOT)


def sha(data):
    return hashlib.sha256(data).hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output-dir', type=Path, required=True)
    args = parser.parse_args()
    commit = git('rev-parse', 'HEAD').decode().strip()
    if git('status', '--porcelain').strip():
        raise SystemExit('Use a clean committed checkout to build the kit')
    tracked = git('ls-tree', '-r', '--name-only', 'HEAD').decode().splitlines()
    selected = {SQL + name for name in SQL_FILES}
    selected.update('tools/data-governance/' + name for name in TOOLS)
    selected.update({
        'docs/data-governance/UPGRADE_v4.4.1_to_v4.8.3_VERIFICATION.md',
        'deploy/document-management/UPLOAD_LIMITS_v4.8.3.md',
        'deploy/document-management/nginx-datafusion-external.conf',
    })
    selected.update(path for path in tracked if path.startswith(GUIDE)
                    or (path.startswith('docs/DATA_GOVERNANCE') and path.endswith('.md'))
                    or path.startswith('data-governance/kettle-worker/')
                    or path.startswith('data-governance/compatibility/'))
    if not selected.issubset(set(tracked)):
        raise SystemExit('Required files missing from commit: ' + str(sorted(selected - set(tracked))))
    forbidden_parts = {'private', 'target', 'node_modules', '.git', 'state', 'downloads', '__pycache__'}
    files = {}
    for path in sorted(selected):
        if set(Path(path).parts) & forbidden_parts or path.endswith(('.jar', '.class', '.pyc', '.zip', '.tar.gz')):
            raise SystemExit('Unexpected binary/private file in source selection: ' + path)
        files[path] = git('show', commit + ':' + path)
    aliases = {
        'README.md': GUIDE + 'README.md',
        'ROLLBACK.md': GUIDE + 'ROLLBACK.md',
        'OFFLINE_DEPENDENCIES.md': GUIDE + 'OFFLINE_DEPENDENCIES.md',
        'config/application-upgrade.example.yml': GUIDE + 'config/application-upgrade.example.yml',
        'config/nginx-document-upload.location.conf': GUIDE + 'config/nginx-document-upload.location.conf',
        'config/kettle-linux.example.json': GUIDE + 'config/kettle-linux.example.json',
    }
    for destination, source in aliases.items():
        files[destination] = files[source]
    manifest = {
        'product': 'RYNEW', 'fromVersion': 'v4.4.1', 'toVersion': 'v4.8.3',
        'sourceCommit': commit, 'applicationBaseline': '7873ec8765ff4ca72d58127a7e283b4a3eb7299a',
        'sourceClean': True, 'kind': 'upgrade SQL and deployment documentation with engine source',
        'containsApplicationBinaries': False, 'containsCredentials': False,
        'containsVendorArchivesOrImages': False,
        'databaseExecutionOrder': [SQL + name for name in SQL_FILES[:2]],
        'files': {name: {'bytes': len(data), 'sha256': sha(data)} for name, data in sorted(files.items())},
    }
    files['MANIFEST.json'] = (json.dumps(manifest, ensure_ascii=False, indent=2) + '\n').encode()
    files['SHA256SUMS'] = ''.join(f'{sha(data)}  {name}\n' for name, data in sorted(files.items())).encode()
    output = args.output_dir.resolve()
    output.mkdir(parents=True, exist_ok=False)
    name = 'RYNEW-v4.4.1-to-v4.8.3-upgrade-kit'
    expanded = output / name
    for path, data in files.items():
        target = expanded / path
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(data)
    archive = output / (name + '.zip')
    with zipfile.ZipFile(archive, 'x', compression=zipfile.ZIP_DEFLATED) as package:
        for path, data in sorted(files.items()):
            info = zipfile.ZipInfo(name + '/' + path, date_time=(2026, 9, 18, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = 0o100644 << 16
            package.writestr(info, data)
    with zipfile.ZipFile(archive) as package:
        if package.testzip() is not None:
            raise SystemExit('ZIP CRC verification failed')
        for path, data in files.items():
            if package.read(name + '/' + path) != data:
                raise SystemExit('ZIP content mismatch: ' + path)
    archive_hash = sha(archive.read_bytes())
    (output / 'SHA256SUMS').write_text(f'{archive_hash}  {archive.name}\n')
    print(json.dumps({'commit': commit, 'files': len(files), 'archive': str(archive),
                      'bytes': archive.stat().st_size, 'sha256': archive_hash}, ensure_ascii=False))


if __name__ == '__main__':
    main()
