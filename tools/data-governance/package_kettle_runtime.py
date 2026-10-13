#!/usr/bin/env python3
"""Build an offline relocatable bundle from an explicit original archive or verified runtime."""
import argparse
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile

sys.dont_write_bytecode = True
import kettle_worker as broker
import bundled_kettle_runtime as bundled


def copy_verified_libraries(source, destination):
    source = Path(source).resolve(strict=True)
    manifest = json.loads((source / 'manifest.json').read_text())
    records = manifest.get('libraries', []) + manifest.get('runtimeExtensions', [])
    expected = {}
    for record in records:
        name = broker.validate_filename(record['file'])
        if not name.endswith('.jar') or name in expected:
            raise ValueError('Invalid or duplicate source library')
        path = bundled.regular_file(source, 'lib/' + name)
        if broker.file_digest(path) != record['sha256']:
            raise ValueError('Source runtime library checksum mismatch: ' + name)
        expected[name] = path
    entries = manifest['classpath'].split(os.pathsep)
    if not entries or Path(entries[0]).resolve() != source / 'classes':
        raise ValueError('Source classpath must start with its own compiled classes')
    ordered = []
    for value in entries[1:]:
        path = Path(value)
        if path.name not in expected or path.resolve() != expected[path.name].resolve() or path.name in ordered:
            raise ValueError('Source classpath contains an unverified or duplicate library')
        ordered.append(path.name)
    if set(ordered) != set(expected) or 'kettle-6.1.0.7.36.jar' not in expected:
        raise ValueError('Source original engine/classpath provenance is incomplete')
    destination.mkdir(mode=0o700)
    for name in ordered:
        shutil.copyfile(expected[name], destination / name)
    return ordered, [{'file': name, 'sha256': broker.file_digest(destination / name)} for name in ordered]


def package(output, java_home, archive=None, source_runtime=None):
    if bool(archive) == bool(source_runtime):
        raise ValueError('Choose exactly one explicit original archive or verified runtime')
    output = Path(output).absolute()
    if output.exists() or output.is_symlink():
        raise ValueError('Output must be a new bundle directory')
    java_home = bundled.check_java(java_home)
    if not (java_home / 'bin/javac').is_file():
        raise ValueError('Packaging requires JDK 17 javac')
    output.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix='.kettle-package-', dir=output.parent) as folder:
        staging = Path(folder)
        if archive:
            prepared = staging / 'prepared'
            with bundled.contextlib.redirect_stdout(sys.stderr):
                broker.prepare(archive, prepared, java_home)
            source_runtime = prepared
        root = staging / 'bundle'
        root.mkdir(mode=0o700)
        ordered, provenance = copy_verified_libraries(source_runtime, root / 'lib')
        classes = root / 'classes'
        classes.mkdir(mode=0o700)
        # Recompile the reviewed sources so stale classes never enter the bundle.
        sources = sorted(broker.SOURCE.glob('*.java'))
        if not sources:
            raise ValueError('Reviewed worker Java sources are missing')
        cp = os.pathsep.join(str(root / 'lib' / name) for name in ordered)
        subprocess.run([str(java_home / 'bin/javac'), '-encoding', 'UTF-8', '-cp', cp, '-d', str(classes), *map(str, sources)], stdout=sys.stderr, stderr=sys.stderr, check=True)
        for name in bundled.SCRIPTS:
            shutil.copyfile(Path(__file__).with_name(name), root / name)
        records = [{'path': str(path.relative_to(root)), 'sha256': broker.file_digest(path)} for path in sorted(root.rglob('*')) if path.is_file()]
        manifest = {'schemaVersion': 1, 'engine': 'original-kettle', 'javaMajor': 17, 'networkPolicy': 'deny-all', 'classpath': ['classes'] + ['lib/' + name for name in ordered], 'libraries': provenance, 'workerSources': [{'file': p.name, 'sha256': broker.file_digest(p)} for p in sources], 'files': records}
        (root / bundled.MANIFEST).write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + '\n')
        for path in root.rglob('*'):
            path.chmod(0o755 if path.is_dir() else 0o644)
        root.chmod(0o755)
        bundled.verify_bundle(root)
        root.rename(output)
    return {'bundle': str(output), 'engine': 'original-kettle', 'libraries': len(ordered), 'manifestSha256': broker.file_digest(output / bundled.MANIFEST)}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    source = parser.add_mutually_exclusive_group(required=True)
    source.add_argument('--archive', type=Path)
    source.add_argument('--source-runtime', type=Path)
    parser.add_argument('--java-home', required=True, type=Path)
    parser.add_argument('--output', required=True, type=Path)
    args = parser.parse_args()
    print(json.dumps(package(args.output, args.java_home, args.archive, args.source_runtime)))


if __name__ == '__main__':
    main()
