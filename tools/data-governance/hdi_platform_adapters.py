"""Verify separately owned JDBC subclasses; never add them to original provenance."""
import hashlib
from pathlib import Path, PurePosixPath
import re
import shutil
import stat

from hdi_core_protocol import ADAPTERS, ProtocolError, strict_json


def digest(path):
    value = hashlib.sha256()
    with Path(path).open('rb') as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b''):
            value.update(block)
    return value.hexdigest()


def member(root, relative):
    if (not isinstance(relative, str) or not relative or '\\' in relative
            or PurePosixPath(relative).is_absolute() or '..' in PurePosixPath(relative).parts):
        raise ProtocolError('PLATFORM_ADAPTER_PATH_REJECTED')
    path = Path(root) / relative
    if any(p.is_symlink() for p in (path, *path.parents)) or not path.is_file() or path.stat().st_nlink != 1:
        raise ProtocolError('PLATFORM_ADAPTER_PATH_REJECTED')
    return path


def verify_platform_adapters(root, bundle, expected_sha):
    root, bundle = Path(root).absolute(), Path(bundle).absolute()
    manifest_file = member(root, 'platform-adapter-manifest.json')
    if not re.fullmatch('[0-9a-f]{64}', str(expected_sha)) or digest(manifest_file) != expected_sha:
        raise ProtocolError('PLATFORM_ADAPTER_MANIFEST_MISMATCH')
    data = strict_json(manifest_file.read_bytes())
    original = strict_json(member(bundle, 'bundle-manifest.json').read_bytes())
    if (data.get('schemaVersion') != 1 or data.get('kind') != 'rynew-hdi-jdbc-platform-adapters'
            or data.get('sourceArchiveSha256') != original.get('sourceArchiveSha256')):
        raise ProtocolError('PLATFORM_ADAPTER_PROVENANCE_MISMATCH')
    entries = data.get('files')
    if not isinstance(entries, list) or not 1 <= len(entries) <= 32:
        raise ProtocolError('PLATFORM_ADAPTER_FILES_REQUIRED')
    files = {}
    for entry in entries:
        path = member(root, entry.get('path'))
        if entry['path'] in files or path.stat().st_size != entry.get('bytes') or digest(path) != entry.get('sha256'):
            raise ProtocolError('PLATFORM_ADAPTER_FILE_MISMATCH')
        files[entry['path']] = entry
    actual = {p.relative_to(root).as_posix() for p in root.rglob('*') if p.is_file()}
    if actual != set(files) | {'platform-adapter-manifest.json'} or any(p.is_symlink() for p in root.rglob('*')):
        raise ProtocolError('PLATFORM_ADAPTER_UNRECORDED_FILE')
    originals = {entry['path']: entry for entry in original['files']}
    plugins = data.get('plugins')
    if not isinstance(plugins, list) or len(plugins) != 2 or {p.get('adapterName') for p in plugins} != {'mysqlInput', 'mysqlOutput'}:
        raise ProtocolError('PLATFORM_ADAPTER_PLUGIN_REJECTED')
    for plugin in plugins:
        name = plugin['adapterName']
        if (plugin.get('adapterVersion') != '2.0.0' or plugin.get('adapterType') != ADAPTERS[name][0]
                or plugin.get('className') != ADAPTERS[name][1] or plugin.get('dbType') != 2):
            raise ProtocolError('PLATFORM_ADAPTER_PLUGIN_REJECTED')
        required = {'properties': f'plugins/{name}/adapter.properties', 'descriptor': f'plugins/{name}/etl_plugin.json',
                    'jar': 'lib/rynew-mysql-jdbc-adapter.jar'}
        if any(plugin.get(key) != value or value not in files for key, value in required.items()):
            raise ProtocolError('PLATFORM_ADAPTER_PLUGIN_REJECTED')
        source = f'src/com/rynew/hdi/adapter/{name[0].upper() + name[1:]}.java'
        if source not in files or files[source].get('origin') != 'platform-source':
            raise ProtocolError('PLATFORM_ADAPTER_SOURCE_REQUIRED')
        dependencies, targets = plugin.get('dependencies'), set()
        if not isinstance(dependencies, list) or not 1 <= len(dependencies) <= 256:
            raise ProtocolError('PLATFORM_ADAPTER_DEPENDENCIES_REQUIRED')
        for dependency in dependencies:
            relative, target = dependency.get('bundlePath'), dependency.get('target')
            if not isinstance(target, str) or not re.fullmatch(r'lib/[A-Za-z0-9_.+-]+\.jar', target) or target in targets:
                raise ProtocolError('PLATFORM_ADAPTER_DEPENDENCY_REJECTED')
            targets.add(target)
            path = member(bundle, relative)
            evidence = originals.get(relative, {})
            if (path.suffix != '.jar' or dependency.get('sha256') != evidence.get('sha256')
                    or dependency.get('bytes') != evidence.get('bytes') or path.stat().st_size != dependency['bytes']
                    or digest(path) != dependency['sha256']):
                raise ProtocolError('PLATFORM_ADAPTER_DEPENDENCY_MISMATCH')
    return data


def install_platform_adapters(root, bundle, expected_sha, engine):
    data = verify_platform_adapters(root, bundle, expected_sha)
    for plugin in data['plugins']:
        target = Path(engine) / ('etl-' + plugin['adapterName'] + '-2.0.0')
        target.mkdir(mode=0o700)  # Refuse collisions with any original/plugin directory.
        (target / 'lib').mkdir(mode=0o700)
        for key, name in [('properties', 'adapter.properties'), ('descriptor', 'etl_plugin.json'),
                          ('jar', 'etl-' + plugin['adapterName'] + '-2.0.0.jar')]:
            shutil.copyfile(member(root, plugin[key]), target / name)
        for entry in plugin['dependencies']:
            shutil.copyfile(member(bundle, entry['bundlePath']), target / entry['target'])
