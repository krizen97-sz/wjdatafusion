#!/usr/bin/env python3
"""Build and invoke the original HDI metadata compiler in a private one-shot JVM.

The caller is the platform backend, not a browser. Task identity, revision and
output-root policy must be resolved before writing its JSON input. Original JARs
are supplied locally and checked against the bundle's original-archive manifest.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile

SOURCE = Path(__file__).resolve().parents[2] / 'data-governance/hdi-native/compiler/OriginalTaskCompiler.java'
ARCHIVE_SHA = '3bd2ef065255e9e19119b1e2ade294b32b59d47c051a0a10981496766f052859'
MAX_BYTES = 8 * 1024 * 1024


def digest(path: Path) -> str:
    checksum = hashlib.sha256()
    with path.open('rb') as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b''):
            checksum.update(chunk)
    return checksum.hexdigest()


def regular(root: Path, relative: str) -> Path:
    candidate = root / relative
    if Path(relative).is_absolute() or '..' in Path(relative).parts:
        raise ValueError('Invalid bundle manifest path')
    for path in (candidate, *candidate.parents):
        if path.is_symlink():
            raise ValueError('Bundle symbolic links are forbidden')
        if path == root:
            break
    if not candidate.is_file():
        raise ValueError('Required original bundle file is missing')
    return candidate


def classpath(bundle: Path) -> list[Path]:
    manifest = json.loads(regular(bundle, 'bundle-manifest.json').read_text('utf-8'))
    if manifest.get('sourceArchiveSha256') != ARCHIVE_SHA or manifest.get('engine') != 'original-detl':
        raise ValueError('Unsupported original bundle provenance')
    records = manifest.get('files', [])
    paths: dict[str, dict] = {}
    for row in records:
        if row['path'] in paths:
            raise ValueError('Duplicate bundle manifest entry')
        paths[row['path']] = row
    selected = ['task-service/dEtlv2.jar', 'instance/etl-core-3.3.0.jar']
    for folder in ('task-service/lib', 'task-service/ext/normal', 'instance/lib'):
        selected.extend(sorted(name for name in paths if str(Path(name).parent) == folder and name.endswith('.jar')))
    for plugin in ('dataGeneratorInput', 'fieldMapping', 'fileOutput'):
        selected.append(f'instance/etl-{plugin}-2.0.0/adapter.properties')
        selected.append(f'instance/etl-{plugin}-2.0.0/etl_plugin.json')
    jars = []
    for name in selected:
        record = paths.get(name)
        if not record or record.get('source', {}).get('archiveSha256') != ARCHIVE_SHA:
            raise ValueError('Original file provenance is missing')
        path = regular(bundle, name)
        if path.stat().st_size != record['bytes'] or digest(path) != record['sha256']:
            raise ValueError('Original bundle checksum mismatch')
        if name.endswith('.jar'):
            jars.append(path)
    # Never use wildcards: an unmanifested JAR must not enter the classpath.
    return jars


def execute(bundle: Path, java_home: Path, request: bytes, runtime: Path, timeout: int = 45) -> tuple[int, bytes]:
    if len(request) > MAX_BYTES:
        raise ValueError('Compiler request exceeds 8 MiB')
    bundle = bundle.absolute()
    jars = classpath(bundle)
    for command in ('java', 'javac'):
        if not (java_home / 'bin' / command).is_file():
            raise ValueError('An explicit local JDK with java and javac is required')
    if not runtime.is_dir() or runtime.is_symlink() or runtime.stat().st_uid != os.getuid() or runtime.stat().st_mode & 0o077:
        raise ValueError('Runtime must be an existing owned private 0700 directory')
    with tempfile.TemporaryDirectory(prefix='compiler-', dir=runtime) as working:
        directory = Path(working)
        cp = os.pathsep.join(map(str, jars))
        environment = {key: value for key, value in os.environ.items() if key not in (
            'JAVA_TOOL_OPTIONS', '_JAVA_OPTIONS', 'JDK_JAVA_OPTIONS', 'CLASSPATH', 'DYLD_INSERT_LIBRARIES')}
        build = subprocess.run([str(java_home / 'bin/javac'), '--release', '11', '-encoding', 'UTF-8', '-cp', cp,
                                '-d', str(directory), str(SOURCE)], cwd=directory, capture_output=True, timeout=timeout, env=environment)
        if build.returncode:
            raise ValueError('Compiler adapter build failed')
        completed = subprocess.run([str(java_home / 'bin/java'), '-Xms32m', '-Xmx256m', '-cp', str(directory) + os.pathsep + cp,
                                    'OriginalTaskCompiler', str(bundle)], cwd=directory, input=request,
                                   stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=timeout, env=environment)
        # Vendor logs can include graph values; do not forward their unfiltered stderr.
        if len(completed.stdout) > 32 * 1024 * 1024:
            raise ValueError('Compiler response exceeds 32 MiB')
        result = json.loads(completed.stdout)
        if not isinstance(result, dict) or not isinstance(result.get('ok'), bool):
            raise ValueError('Invalid compiler protocol response')
        return completed.returncode, completed.stdout


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--bundle', required=True, type=Path)
    parser.add_argument('--java-home', required=True, type=Path)
    parser.add_argument('--runtime', required=True, type=Path)
    parser.add_argument('--timeout', type=int, default=45)
    args = parser.parse_args()
    try:
        if not 1 <= args.timeout <= 120:
            raise ValueError('Timeout must be 1..120 seconds')
        code, result = execute(args.bundle, args.java_home, sys.stdin.buffer.read(MAX_BYTES + 1), args.runtime, args.timeout)
        sys.stdout.buffer.write(result)
        return code
    except (ValueError, OSError, subprocess.SubprocessError) as error:
        print(json.dumps({'ok': False, 'protocolVersion': 1, 'code': 'COMPILER_UNAVAILABLE',
                          'message': 'Original compiler cannot complete this request'}))
        print('compiler launcher failure type=' + type(error).__name__, file=sys.stderr)
        return 2


if __name__ == '__main__':
    raise SystemExit(main())
