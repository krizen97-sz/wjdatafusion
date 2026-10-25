#!/usr/bin/env python3
"""Build the explicitly authorized platform MySQL dialect selection; never rewrite vendor JARs."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import struct
import subprocess
import tempfile
import zipfile

from hdi_native_bundle import verify as verify_original
from hdi_original_compiler import ARCHIVE_SHA, regular

ROOT = Path(__file__).resolve().parents[2]
SOURCE_ROOT = ROOT / 'data-governance/hdi-native/platform-adapters/mysql/src'
MANIFEST = 'platform-adapter-manifest.json'
KIND = 'rynew-hdi-jdbc-platform-adapters'
JAR = 'lib/rynew-mysql-jdbc-adapter.jar'
PLUGIN_NAMES = ('mysqlInput', 'mysqlOutput')


def digest(data):
    return hashlib.sha256(data).hexdigest()


def class_utf8(data):
    """Read only JVM constant-pool UTF8 entries; do not infer runtime support from absence."""
    if data[:4] != b'\xca\xfe\xba\xbe':
        raise ValueError('Invalid original JVM class')
    count = struct.unpack_from('>H', data, 8)[0]
    offset, index, result = 10, 1, set()
    sizes = {3:4, 4:4, 5:8, 6:8, 7:2, 8:2, 9:4, 10:4, 11:4, 12:4, 15:3, 16:2, 17:4, 18:4, 19:2, 20:2}
    while index < count:
        tag = data[offset]; offset += 1
        if tag == 1:
            size = struct.unpack_from('>H', data, offset)[0]; offset += 2
            result.add(data[offset:offset+size].decode('utf-8', errors='replace')); offset += size
        else:
            if tag not in sizes:
                raise ValueError('Unknown JVM constant-pool entry')
            offset += sizes[tag]
            if tag in (5, 6): index += 1
        index += 1
    return result


def verify(platform, bundle):
    platform, bundle = Path(platform).absolute(), Path(bundle).absolute()
    manifest = json.loads(regular(platform, MANIFEST).read_text())
    if manifest.get('schemaVersion') != 1 or manifest.get('kind') != KIND or manifest.get('sourceArchiveSha256') != ARCHIVE_SHA:
        raise ValueError('Unknown platform adapter provenance')
    records = {r['path']: r for r in manifest['files']}
    if len(records) != len(manifest['files']): raise ValueError('Duplicate platform artifact')
    for name, record in records.items():
        data = regular(platform, name).read_bytes()
        if len(data) != record['bytes'] or digest(data) != record['sha256']: raise ValueError('Platform artifact digest mismatch')
    actual = {str(p.relative_to(platform)) for p in platform.rglob('*') if p.is_file() or p.is_symlink()}
    if actual != set(records) | {MANIFEST}: raise ValueError('Unlisted platform artifact')
    original = json.loads(regular(bundle, 'bundle-manifest.json').read_text())
    originals = {r['path']:r for r in original['files']}
    if original.get('sourceArchiveSha256') != ARCHIVE_SHA: raise ValueError('Wrong original bundle')
    if {p['adapterName'] for p in manifest['plugins']} != set(PLUGIN_NAMES) or len(manifest['plugins']) != 2:
        raise ValueError('Unexpected platform plugin')
    for plugin in manifest['plugins']:
        name = plugin['adapterName']; suffix = 'Input' if name.endswith('Input') else 'Output'
        expected = dict(adapterVersion='2.0.0', adapterType=suffix.lower(), dbType=2,
                        className='com.rynew.hdi.adapter.Mysql'+suffix, jar=JAR,
                        properties=f'plugins/{name}/adapter.properties', descriptor=f'plugins/{name}/etl_plugin.json')
        if any(plugin.get(k) != v for k,v in expected.items()): raise ValueError('Unexpected MySQL registration')
        if any(plugin[key] not in records for key in ('properties','descriptor','jar')): raise ValueError('Unlisted plugin registration')
        seen = set()
        for dep in plugin['dependencies']:
            if dep['target'] != 'lib/'+Path(dep['bundlePath']).name or dep['target'] in seen: raise ValueError('Unsafe dependency destination')
            seen.add(dep['target']); row = originals.get(dep['bundlePath'])
            if not row or any(row[k] != dep[k] for k in ('sha256','bytes')): raise ValueError('Original dependency provenance mismatch')
            data = regular(bundle, dep['bundlePath']).read_bytes()
            if len(data) != dep['bytes'] or digest(data) != dep['sha256']: raise ValueError('Original dependency digest mismatch')
    return manifest


def build(bundle, java_home, output):
    bundle, java_home, output = Path(bundle).absolute(), Path(java_home).absolute(), Path(output).absolute()
    verify_original(bundle)
    if output.exists(): raise ValueError('Platform output already exists; build into a new directory')
    original = json.loads((bundle/'bundle-manifest.json').read_text())
    if original['sourceArchiveSha256'] != ARCHIVE_SHA: raise ValueError('Wrong original package')
    originals = {r['path']:r for r in original['files']}
    files, plugins, audits, writes, dependencies = [], [], {}, {}, {}
    for name in PLUGIN_NAMES:
        suffix = 'Input' if name.endswith('Input') else 'Output'
        parent = 'postgresql'+suffix
        prefix = f'instance/etl-{parent}-2.0.0/'
        paths = sorted(p for p in originals if p.startswith(prefix+'lib/') and p.endswith('.jar'))
        paths.append('task-service/lib/mariadb-java-client-2.7.5.jar')
        deps = [dict(bundlePath=p, target='lib/'+Path(p).name, sha256=originals[p]['sha256'], bytes=originals[p]['bytes']) for p in paths]
        dependencies[name] = deps
        references = {}
        # Inventory every original dependency class, including cross-JAR helper references.
        for dep in deps:
            with zipfile.ZipFile(bundle/dep['bundlePath']) as archive:
                for entry in archive.namelist():
                    if entry.endswith('.class'):
                        for value in class_utf8(archive.read(entry)):
                            if value.startswith(('etl.', 'isScram')):
                                references.setdefault(value, []).append(dict(jar=dep['bundlePath'], classFile=entry))
        descriptor_path = prefix+'etl_plugin.json'
        original_descriptor = json.loads((bundle/descriptor_path).read_text())
        parameters, audit = [], []
        for parameter in original_descriptor['adapterParams']:
            refs = references.get(parameter['name'], [])
            retained = bool(refs)
            if retained: parameters.append(parameter)
            audit.append(dict(name=parameter['name'], included=retained, originalDescriptor=descriptor_path,
                              originalDescriptorSha256=originals[descriptor_path]['sha256'], references=refs,
                              note='Original parameter definition preserved' if retained else 'Not exposed in this minimal profile; absence of a matching constant is not a claim of unsupported vendor behavior'))
        audits[name] = audit
        class_name = 'com.rynew.hdi.adapter.Mysql'+suffix
        display = 'MySQL'+('输入' if suffix=='Input' else '输出')+'（原JDBC适配）'
        descriptor = dict(adapterName=name, adapterDisplayName=display, adapterType=suffix.lower(), adapterComment=display,
                          className=class_name, version='2.0.0', adapterDatabaseType='MySql', adapterLanguge='java', adapterParams=parameters)
        properties = dict(adapterName=name, adapterDisplayName=display, adapterType=suffix.lower(), className=class_name,
                          version='2.0.0', adapterDatabaseType='MySql', adapterWithFel='false', adapterLanguage='java',
                          adapterComment=display, pluginResourceType='external', pluginFieldSyncMode='add_no_src')
        # java.util.Properties.load(InputStream) consumes ISO-8859-1 unicode escapes.
        props = ''.join(k+'='+''.join(c if ord(c)<128 else '\\u%04x'%ord(c) for c in v)+'\n' for k,v in properties.items())
        writes[f'plugins/{name}/adapter.properties'] = (props.encode(), 'platform-registration')
        writes[f'plugins/{name}/etl_plugin.json'] = (json.dumps(descriptor,ensure_ascii=False,indent=2).encode(), 'platform-registration')
        plugins.append(dict(adapterName=name, adapterVersion='2.0.0', adapterType=suffix.lower(), dbType=2, className=class_name,
                            properties=f'plugins/{name}/adapter.properties', descriptor=f'plugins/{name}/etl_plugin.json', jar=JAR, dependencies=deps))
    source_paths = sorted(SOURCE_ROOT.rglob('*.java'))
    if len(source_paths) != 2: raise ValueError('Expected exactly two dialect sources')
    with tempfile.TemporaryDirectory(prefix='hdi-mysql-build-') as temp:
        classes = Path(temp)/'classes'; classes.mkdir()
        cp = os.pathsep.join(str(bundle/p) for p in sorted({d['bundlePath'] for ds in dependencies.values() for d in ds}))
        completed = subprocess.run([str(java_home/'bin/javac'), '--release','11','-encoding','UTF-8','-cp',cp,'-d',str(classes),*map(str,source_paths)],capture_output=True)
        if completed.returncode: raise ValueError('Platform dialect compilation failed: '+completed.stderr.decode(errors='replace')[-2000:])
        jar_path = Path(temp)/'adapter.jar'
        with zipfile.ZipFile(jar_path,'w',zipfile.ZIP_DEFLATED) as archive:
            for f in sorted(classes.rglob('*.class')):
                info=zipfile.ZipInfo(str(f.relative_to(classes)),date_time=(1980,1,1,0,0,0));info.external_attr=0o100644<<16
                archive.writestr(info,f.read_bytes())
        writes[JAR]=(jar_path.read_bytes(),'platform-build')
    for source in source_paths: writes['src/'+str(source.relative_to(SOURCE_ROOT))]=(source.read_bytes(),'platform-source')
    writes['parameter-provenance.json']=(json.dumps(audits,ensure_ascii=False,indent=2).encode(),'platform-audit')
    java_version=subprocess.run([str(java_home/'bin/javac'),'-version'],capture_output=True,text=True,check=True).stdout.strip()
    output.mkdir(parents=True)
    for name,(data,origin) in writes.items():
        target=output/name;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(data)
        files.append(dict(path=name,sha256=digest(data),bytes=len(data),origin=origin))
    manifest=dict(schemaVersion=1,kind=KIND,sourceArchiveSha256=ARCHIVE_SHA,plugins=plugins,files=sorted(files,key=lambda f:f['path']),
                  build=dict(javaVersion=java_version,release=11,overrides=['getDriverClassName','getJdbcUrlPrefix','getCaseChar'],vendorJarsModified=False))
    (output/MANIFEST).write_text(json.dumps(manifest,ensure_ascii=False,indent=2)+'\n')
    verify(output,bundle)
    return dict(path=str(output),manifestSha256=digest((output/MANIFEST).read_bytes()),jarSha256=digest((output/JAR).read_bytes()),plugins=list(PLUGIN_NAMES))


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--bundle',type=Path,required=True);parser.add_argument('--java-home',type=Path);parser.add_argument('--output',type=Path);parser.add_argument('--verify',type=Path)
    args=parser.parse_args()
    if args.verify:
        manifest=verify(args.verify,args.bundle); result=dict(verified=True,plugins=[p['adapterName'] for p in manifest['plugins']])
    else:
        if not args.output or not args.java_home: parser.error('--output and --java-home are required for build')
        result=build(args.bundle,args.java_home,args.output)
    print(json.dumps(result,ensure_ascii=False))


if __name__=='__main__': main()
