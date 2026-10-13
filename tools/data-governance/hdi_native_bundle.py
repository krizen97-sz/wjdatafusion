#!/usr/bin/env python3
"""Select byte-exact original DETL task assets. This tool neither configures nor runs an engine."""
import argparse
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import re
import stat
import tempfile
import zipfile

ROOT = Path(__file__).resolve().parents[2]
SOURCE_MANIFEST = ROOT / 'data-governance/hdi-native/manifest.detl-3.3.0.json'
INSTANCE = 'bin/instance/'
PUBLIC = 'bin/detl/config/public/'
CORE = {'adapter.properties', 'etl-core-3.3.0.jar', 'version.txt', 'felfunction/etl_fel_capability_set.json'}
PUBLIC_SUFFIXES = {'.js', '.css', '.svg', '.png', '.jpg', '.jpeg', '.gif', '.ico', '.icns', '.woff', '.woff2', '.ttf', '.eot', '.json', '.txt'}
PLUGIN_META = {'adapter.properties', 'etl_plugin.json', 'version.txt', 'mybatis-config.xml', 'script.template'}
NOTICE = 'META-INF/Open Source Software Licenses.txt'
MAX_FILE_BYTES = 512 * 1024 * 1024
MAX_TOTAL_BYTES = 6 * 1024 * 1024 * 1024


def digest(stream):
    result = hashlib.sha256()
    while block := stream.read(1024 * 1024):
        result.update(block)
    return result.hexdigest()


def file_digest(path):
    with Path(path).open('rb') as stream:
        return digest(stream)


def relative(value):
    if not isinstance(value, str) or not value or '\\' in value or ':' in value or '%' in value or any(ord(c) < 32 or ord(c) == 127 for c in value):
        raise ValueError('Invalid relative artifact path')
    path = PurePosixPath(value)
    if path.is_absolute() or str(path) != value or any(p in {'.', '..'} for p in path.parts):
        raise ValueError('Artifact path escapes its bounded root')
    return path


def real_directory(path, create=False):
    path = Path(path).absolute()
    for parent in (path, *path.parents):
        if parent.is_symlink():
            raise ValueError('Artifact paths must not contain symbolic links')
    if create:
        path.mkdir(parents=True, exist_ok=True)
    if not path.is_dir():
        raise ValueError('Artifact root is not a directory')
    return path.resolve()


def excluded(value):
    path = relative(value)
    parts = {part.casefold() for part in path.parts}
    name = path.name.casefold()
    return (any(part.startswith('.') for part in path.parts)
            or bool(parts & {'conf', 'certs', 'credentials', 'credential', 'private', 'logs', 'log', 'license', 'licenses'})
            or path.suffix.casefold() in {'.pem', '.key', '.p12', '.pfx', '.jks', '.keystore', '.lic', '.license', '.log', '.bak', '.map'}
            or name in {'application.properties', 'application.yml', 'application.yaml', 'front.properties'})


def archive_index(archive):
    entries = {}
    total = 0
    for info in archive.infolist():
        name = info.filename[:-1] if info.is_dir() else info.filename
        relative(name)
        if name in entries:
            raise ValueError('Duplicate ZIP entry: ' + name)
        unix_type = stat.S_IFMT(info.external_attr >> 16)
        if unix_type not in {0, stat.S_IFREG, stat.S_IFDIR} or stat.S_ISLNK(info.external_attr >> 16):
            raise ValueError('ZIP links and special files are forbidden: ' + name)
        if info.flag_bits & 1 or info.file_size > MAX_FILE_BYTES:
            raise ValueError('Encrypted or oversized ZIP entry: ' + name)
        total += info.file_size
        if total > MAX_TOTAL_BYTES or len(entries) >= 20000:
            raise ValueError('ZIP inventory exceeds bounded limits')
        entries[name] = info
    return entries


def load_policy(source_manifest):
    policy_path = Path(source_manifest)
    policy = json.loads(policy_path.read_text())
    if policy.get('schemaVersion') != 1 or not isinstance(policy.get('archives'), dict):
        raise ValueError('Unsupported source allowlist')
    static_name = str(relative(policy['taskStaticManifest']))
    static_path = policy_path.parent / static_name
    if static_path.is_symlink() or file_digest(static_path) != policy['taskStaticManifestSha256']:
        raise ValueError('Task static inventory checksum mismatch')
    candidate = json.loads(static_path.read_text())
    seen, records, total = set(), [], 0
    for record in candidate['files']:
        path = str(relative(record['path']))
        if path in seen or excluded(path) or Path(path).suffix.casefold() not in PUBLIC_SUFFIXES:
            raise ValueError('Unexpected task static artifact: ' + path)
        if not re.fullmatch('[a-f0-9]{64}', record.get('sha256', '')) or type(record.get('bytes')) is not int or not 0 <= record['bytes'] <= MAX_FILE_BYTES:
            raise ValueError('Invalid task static inventory record')
        seen.add(path); records.append(record); total += record['bytes']
    if len(records) != candidate['fileCount'] or total != candidate['bytes']:
        raise ValueError('Task static inventory totals mismatch')
    return policy, candidate


def plugins_in(entries):
    return sorted({p[len(INSTANCE):].split('/')[0] for p in entries
                   if p.startswith(INSTANCE + 'etl-') and p.count('/') >= 3 and p.endswith('/adapter.properties')})


def plugin_file(plugin, tail):
    if excluded(tail):
        return False
    if tail in PLUGIN_META or tail == plugin + '.jar':
        return True
    path = PurePosixPath(tail)
    return ((tail.startswith('lib/') and path.suffix in {'.jar', '.pom'})
            or (tail.startswith('config/i18n/') and path.suffix == '.properties')
            or (tail.startswith('icon/') and path.suffix in PUBLIC_SUFFIXES)
            or (tail.startswith('plugin-web-config/') and path.suffix in PUBLIC_SUFFIXES)
            or tail == 'META-INF/services/javax.servlet.ServletContainerInitializer')


def select_files(entries, plugins, task):
    selected = {}
    skipped = []
    def select(source, destination, role, expected=None):
        info = entries.get(source)
        if info is None or info.is_dir():
            raise ValueError('Required original artifact is missing: ' + source)
        if destination in selected:
            raise ValueError('Duplicate bundle destination')
        relative(destination)
        selected[destination] = (source, info, role, expected)
    for source, info in entries.items():
        if info.is_dir() or not source.startswith(INSTANCE):
            continue
        tail = source[len(INSTANCE):]
        if tail in CORE or (tail.startswith('lib/') and PurePosixPath(tail).suffix == '.jar'):
            select(source, 'instance/' + tail, 'original-engine')
        else:
            plugin, _, rest = tail.partition('/')
            if plugin in plugins:
                if plugin_file(plugin, rest):
                    select(source, 'instance/' + tail, 'original-plugin')
                else:
                    skipped.append(source)
    for tail in CORE:
        if 'instance/' + tail not in selected:
            raise ValueError('Required original core artifact is missing: ' + tail)
    if not any(p.startswith('instance/lib/') for p in selected):
        raise ValueError('Original engine libraries are missing')
    for plugin in plugins:
        # Some original external-form plugins (for example nullOutput) have no etl_plugin.json.
        for required in ('adapter.properties', plugin + '.jar'):
            if 'instance/' + plugin + '/' + required not in selected:
                raise ValueError('Incomplete original plugin: ' + plugin)
    for record in task['files']:
        select(PUBLIC + record['path'], 'public/' + record['path'], 'original-task-ui', record)
    if NOTICE in entries:
        select(NOTICE, 'notices/Open Source Software Licenses.txt', 'copyright-notice')
    return selected, sorted(skipped)


def package(archive_path, output, plugins=(), all_plugins=False, source_manifest=SOURCE_MANIFEST):
    policy, task = load_policy(source_manifest)
    output = Path(output).absolute()
    if output.exists() or output.is_symlink():
        raise ValueError('Output must be a new bundle directory')
    if not plugins and not all_plugins:
        raise ValueError('Select original plugins explicitly with --plugin or --all-plugins')
    if plugins and all_plugins:
        raise ValueError('Choose an explicit plugin list or all plugins, not both')
    for value in plugins:
        if not re.fullmatch(r'etl-[A-Za-z0-9_-]+-[0-9.]+', value):
            raise ValueError('Invalid original plugin directory name')
    if len(plugins) != len(set(plugins)):
        raise ValueError('Duplicate plugin selection')
    archive_path = Path(archive_path)
    if archive_path.is_symlink():
        raise ValueError('Archive must be a regular original file')
    with archive_path.open('rb') as stream:
        initial = os.fstat(stream.fileno())
        if not stat.S_ISREG(initial.st_mode):
            raise ValueError('Archive must be a regular file')
        checksum = digest(stream)
        release = policy['archives'].get(checksum)
        if release is None or release['bytes'] != initial.st_size:
            raise ValueError('Original ZIP SHA-256 is not on the reviewed allowlist')
        if release['frontendVersion'] != task['sourceVersion']:
            raise ValueError('Task frontend and original archive versions differ')
        stream.seek(0)
        with zipfile.ZipFile(stream) as archive:
            entries = archive_index(archive)
            available = plugins_in(entries)
            selected_plugins = available if all_plugins else sorted(plugins)
            if not set(selected_plugins) <= set(available):
                raise ValueError('Requested plugin is absent from the original ZIP')
            selected, skipped = select_files(entries, selected_plugins, task)
            parent = real_directory(output.parent, create=True)
            with tempfile.TemporaryDirectory(prefix='.hdi-original-', dir=parent) as temporary:
                staging = Path(temporary) / 'bundle'; staging.mkdir(mode=0o755)
                records = []
                for destination, (source, info, role, expected) in sorted(selected.items()):
                    path = staging / destination; path.parent.mkdir(parents=True, exist_ok=True)
                    result = hashlib.sha256(); count = 0
                    with archive.open(info) as content, path.open('xb') as target:
                        while block := content.read(1024 * 1024):
                            count += len(block)
                            if count > info.file_size or count > MAX_FILE_BYTES:
                                raise ValueError('Archive entry exceeds declared size')
                            result.update(block); target.write(block)
                    value = result.hexdigest()
                    if count != info.file_size or expected and (count != expected['bytes'] or value != expected['sha256']):
                        raise ValueError('Original task artifact checksum mismatch: ' + source)
                    path.chmod(0o644)
                    records.append({'path': destination, 'bytes': count, 'sha256': value, 'role': role,
                                    'source': {'archiveSha256': checksum, 'entry': source}})
                final = os.fstat(stream.fileno())
                if (initial.st_size, initial.st_mtime_ns, initial.st_ctime_ns) != (final.st_size, final.st_mtime_ns, final.st_ctime_ns):
                    raise ValueError('Archive changed during packaging')
                manifest = {'schemaVersion': 1, 'engine': 'original-detl', 'sourceArchiveSha256': checksum,
                            'sourceArchiveBytes': initial.st_size, 'frontendVersion': task['sourceVersion'],
                            'taskManifestSha256': policy['taskStaticManifestSha256'], 'plugins': selected_plugins,
                            'runtimeConfigurationIncluded': False, 'activationLicenseIncluded': False, 'executionVerified': False,
                            'excludedSelectedPluginFiles': skipped, 'files': records}
                (staging / 'bundle-manifest.json').write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + '\n')
                verify(staging)
                if output.exists() or output.is_symlink():
                    raise ValueError('Output appeared while packaging')
                staging.rename(output)
    return {'bundle': str(output), 'archiveSha256': checksum, 'files': len(records), 'plugins': selected_plugins,
            'bytes': sum(r['bytes'] for r in records), 'executionVerified': False}


def verify(bundle):
    bundle = real_directory(bundle)
    manifest_path = bundle / 'bundle-manifest.json'
    if manifest_path.is_symlink():
        raise ValueError('Bundle manifest must not be a symbolic link')
    manifest = json.loads(manifest_path.read_text())
    if manifest.get('schemaVersion') != 1 or manifest.get('engine') != 'original-detl':
        raise ValueError('Unsupported original bundle manifest')
    known = {'bundle-manifest.json'}
    for record in manifest['files']:
        name = str(relative(record['path']))
        if name in known:
            raise ValueError('Duplicate bundle inventory path')
        known.add(name); path = bundle
        for part in PurePosixPath(name).parts:
            path = path / part
            if path.is_symlink():
                raise ValueError('Bundle artifact is a symbolic link')
        if not path.is_file() or path.stat().st_size != record['bytes'] or file_digest(path) != record['sha256']:
            raise ValueError('Bundle artifact verification failed: ' + name)
    actual = {str(p.relative_to(bundle)) for p in bundle.rglob('*') if p.is_file() or p.is_symlink()}
    if actual != known:
        raise ValueError('Bundle contains missing or unlisted artifacts')
    return {'files': len(known) - 1, 'archiveSha256': manifest['sourceArchiveSha256'], 'verified': True}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--archive', type=Path)
    parser.add_argument('--output', type=Path)
    parser.add_argument('--plugin', action='append', default=[])
    parser.add_argument('--all-plugins', action='store_true')
    parser.add_argument('--verify', type=Path)
    args = parser.parse_args()
    if args.verify:
        if args.archive or args.output or args.plugin or args.all_plugins:
            parser.error('--verify cannot be mixed with packaging arguments')
        result = verify(args.verify)
    else:
        if not args.archive or not args.output:
            parser.error('--archive and --output are required')
        result = package(args.archive, args.output, args.plugin, args.all_plugins)
    print(json.dumps(result, ensure_ascii=False))


if __name__ == '__main__':
    main()
