#!/usr/bin/env python3
"""Prepare a private local installation; never start services or execute tasks."""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import platform
import re
import shutil
import stat
import subprocess
import sys

from hdi_original_execution_proof import verify_bundle
from hdi_core_protocol import private_file, strict_json
from hdi_platform_adapters import verify_platform_adapters

HERE = Path(__file__).resolve().parent
BUILD = HERE.parents[1] / 'data-governance/hdi-native/core-runtime/build.py'
SOURCE = BUILD.parent / 'src/CoreMetadataBridge.java'
RECEIPT_VERSION = 3


class SetupError(Exception):
    pass


def sha(path):
    value = hashlib.sha256()
    with Path(path).open('rb') as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b''):
            value.update(block)
    return value.hexdigest()


def plain_path(value):
    path = Path(value).absolute()
    if '..' in path.parts or any(item.is_symlink() for item in (path, *path.parents)):
        raise SetupError('SYMLINK_OR_NONCANONICAL_PATH')
    return path


def private_directory(path):
    info = plain_path(path).stat()
    if not stat.S_ISDIR(info.st_mode) or info.st_uid != os.getuid() or stat.S_IMODE(info.st_mode) != 0o700:
        raise SetupError('PRIVATE_DIRECTORY_REQUIRED')


def new_json(path, value):
    # O_EXCL is intentional: a retry must never replace a configuration or receipt.
    fd = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW, 0o600)
    with os.fdopen(fd, 'w') as stream:
        json.dump(value, stream, ensure_ascii=False, indent=2, allow_nan=False)
        stream.write('\n')
        stream.flush()
        os.fsync(stream.fileno())


def clean_environment():
    return {key: value for key, value in os.environ.items() if key not in (
        'JAVA_TOOL_OPTIONS', '_JAVA_OPTIONS', 'JDK_JAVA_OPTIONS', 'CLASSPATH',
        'LD_PRELOAD', 'DYLD_INSERT_LIBRARIES')}


def preflight(bundle, java_home):
    if sys.platform != 'linux' or platform.machine().lower() not in ('x86_64', 'amd64'):
        raise SetupError('LINUX_X86_64_REQUIRED')
    for name in ('java', 'javac', 'jar', 'keytool'):
        if not os.access(java_home / 'bin' / name, os.X_OK):
            raise SetupError('JDK_11_REQUIRED')
    version = subprocess.run([str(java_home / 'bin/java'), '-XshowSettings:properties', '-version'],
                             capture_output=True, text=True, timeout=15, env=clean_environment())
    output = version.stdout + version.stderr
    if version.returncode or not re.search(r'version "11\.', output) or not re.search(r'os.arch\s*=\s*(amd64|x86_64)\b', output):
        raise SetupError('JAVA_11_X86_64_REQUIRED')
    if not shutil.which('unshare') or not shutil.which('ip'):
        raise SetupError('LINUX_NAMESPACE_TOOLS_REQUIRED')
    parent = os.readlink('/proc/self/ns/net')
    result = subprocess.run(['unshare', '--user', '--map-root-user', '--mount-proc', '--net', '--pid',
        '--fork', '--kill-child=SIGKILL', sys.executable, '-c',
        'import os,socket; assert os.getpid()==1; assert [n for _,n in socket.if_nameindex()]==["lo"]; '
        'print(os.readlink("/proc/self/ns/net"))'], capture_output=True, text=True, timeout=10)
    if result.returncode or result.stdout.strip() == parent:
        raise SetupError('LINUX_NAMESPACE_UNAVAILABLE')
    verify_bundle(bundle)
    required = ['instance/etl-core-3.3.0.jar', 'instance/lib/zookeeper-hw-hw-ei.jar',
                'task-service/dEtlv2.jar', 'instance/conf/task_config.json']
    for plugin in ('dataGeneratorInput', 'fieldMapping', 'fileInput', 'fileOutput',
                   'postgresqlInput', 'postgresqlOutput', 'kafkaInput', 'kafkaV2Output'):
        required += [f'instance/etl-{plugin}-2.0.0/adapter.properties', f'instance/etl-{plugin}-2.0.0/etl_plugin.json']
    if not all((bundle / name).is_file() for name in required) or not list((bundle / 'zookeeper/lib').glob('zookeeper-*.jar')):
        raise SetupError('COMPLETE_CORE_COMPILER_BUNDLE_REQUIRED')


def compiler_preflight(java_home):
    """Compiler/metadata tools may reuse platform JDK 17; the native Core check remains Java 11."""
    for name in ('java', 'javac'):
        if not os.access(java_home / 'bin' / name, os.X_OK):
            raise SetupError('COMPILER_JDK_11_OR_17_REQUIRED')
    version = subprocess.run([str(java_home / 'bin/java'), '-version'],
        capture_output=True, text=True, timeout=15, env=clean_environment())
    if version.returncode or not re.search(r'version "(?:11|17)(?:\.|\")', version.stdout + version.stderr):
        raise SetupError('COMPILER_JAVA_11_OR_17_REQUIRED')


def build_control(bundle, java_home, output, log):
    with open(log, 'xb') as stream:
        result = subprocess.run([sys.executable, str(BUILD), '--core-home', str(bundle / 'instance'),
            '--java-home', str(java_home), '--output', str(output)], stdout=stream, stderr=stream,
            timeout=120, env=clean_environment())
    if result.returncode:
        raise SetupError('CONTROL_BRIDGE_BUILD_FAILED')


def prepare(args):
    root, bundle = plain_path(args.install_root), plain_path(args.bundle)
    java_home = Path(args.java_home).resolve(strict=True)
    compiler_java_home = Path(getattr(args, 'compiler_java_home', None) or java_home).resolve(strict=True)
    password_file = plain_path(args.redis_password_file)
    compiler = plain_path(args.compiler_launcher or HERE / 'hdi_original_compiler.py')
    compiler_source = plain_path(compiler.parents[2] / 'data-governance/hdi-native/compiler/OriginalTaskCompiler.java')
    connection_form_source = plain_path(compiler_source.parent / 'OriginalConnectionForms.java')
    if not all(path.is_file() for path in (compiler, compiler_source, connection_form_source, BUILD, SOURCE)):
        raise SetupError('INTEGRATED_SOURCE_FILES_REQUIRED')
    info = password_file.stat()
    if (not stat.S_ISREG(info.st_mode) or info.st_uid != os.getuid() or info.st_mode & 0o077
            or not 1 <= info.st_size <= 4096):
        raise SetupError('PRIVATE_REDIS_PASSWORD_FILE_REQUIRED')
    if type(args.redis_port) is not int or not 1024 <= args.redis_port <= 65535 or type(args.redis_database) is not int or not 0 <= args.redis_database <= 15:
        raise SetupError('INVALID_LOCAL_REDIS_ENDPOINT')
    if any((parent / '.git').exists() for parent in (root, *root.parents)):
        raise SetupError('INSTALL_ROOT_MUST_BE_OUTSIDE_CHECKOUT')
    preflight(bundle, java_home)
    if compiler_java_home != java_home:
        compiler_preflight(compiler_java_home)
    adapters = getattr(args, 'platform_adapters', None)
    adapter_config = None
    if adapters:
        adapters = plain_path(adapters)
        adapter_config = {'root': str(adapters), 'manifestSha256': sha(adapters / 'platform-adapter-manifest.json')}
        verify_platform_adapters(adapters, bundle, adapter_config['manifestSha256'])
    inputs = {'protocolVersion': 1, 'uid': os.getuid(), 'installRoot': str(root),
        'bundle': str(bundle), 'bundleManifestSha256': sha(bundle / 'bundle-manifest.json'),
        'javaHome': str(java_home), 'compilerJavaHome': str(compiler_java_home),
        'compilerLauncher': str(compiler), 'compilerLauncherSha256': sha(compiler),
        'compilerSourceSha256': sha(compiler_source),
        'connectionFormsSourceSha256': sha(connection_form_source),
        'workerLauncher': str(HERE / 'hdi_core_worker.py'), 'controlSourceSha256': sha(SOURCE),
        'redis': {'host': '127.0.0.1', 'port': args.redis_port, 'database': args.redis_database,
                  'passwordFile': str(password_file)}}
    if adapter_config:
        inputs['platformAdapters'] = adapter_config
    receipt_path = root / 'private/setup-receipt.json'
    config_path = root / 'private/worker.json'
    jar = root / 'control/rynew-hdi-core-control.jar'
    existing = root.exists()
    if existing:
        for path in (root, root / 'private', root / 'state', root / 'compile', root / 'control'):
            private_directory(path)
        receipt = strict_json(private_file(receipt_path))
        version = receipt.get('receiptVersion')
        if version is None or type(version) is int and version in (1, 2):
            raise SetupError('LEGACY_SETUP_RECEIPT_REQUIRES_NEW_INSTALLATION')
        if type(version) is not int or version != RECEIPT_VERSION:
            raise SetupError('UNSUPPORTED_SETUP_RECEIPT_VERSION')
        private_file(config_path)
        private_file(jar, 32 * 1024 * 1024)
        if receipt.get('inputs') != inputs or receipt.get('jarSha256') != sha(jar) or receipt.get('configSha256') != sha(config_path):
            raise SetupError('EXISTING_INSTALLATION_CONFLICT')
    else:
        if args.verify_only:
            raise SetupError('INSTALLATION_NOT_FOUND')
        # The caller chooses an existing parent. Do not create/chmod unrelated directories.
        if not root.parent.is_dir():
            raise SetupError('INSTALL_PARENT_MUST_EXIST')
        root.mkdir(mode=0o700)
        for name in ('private', 'state', 'compile'):
            (root / name).mkdir(mode=0o700)
        build_control(bundle, java_home, root / 'control', root / 'private/control-build.log')
        evidence = strict_json(private_file(root / 'control/build-evidence.json'))
        if evidence.get('sourceSha256') != inputs['controlSourceSha256'] or evidence.get('jarSha256') != sha(jar):
            raise SetupError('CONTROL_BUILD_EVIDENCE_MISMATCH')
        config = {'protocolVersion': 1, 'stateDir': str(root / 'state'), 'bundle': str(bundle),
            'javaHome': str(java_home), 'controlJar': str(jar), 'controlJarSha256': sha(jar),
            'redis': inputs['redis'], 'maxConcurrentRuns': 1, 'startupTimeoutSeconds': 120,
            'executionTimeoutSeconds': 300, 'stopTimeoutSeconds': 45, 'javaRuntimeMode': 'normal'}
        if adapter_config:
            config['platformAdapters'] = adapter_config
        new_json(config_path, config)
        new_json(receipt_path, {'receiptVersion': RECEIPT_VERSION, 'inputs': inputs,
            'jarSha256': sha(jar), 'configSha256': sha(config_path)})
    return {'protocolVersion': 1, 'ok': True, 'status': 'VERIFIED' if existing else 'PREPARED',
        'servicesStarted': False, 'redisConnectivityTested': False, 'workerConfig': str(config_path),
        'workerArgv': [sys.executable, str(HERE / 'hdi_core_worker.py'), '--config', str(config_path)],
        'compilerArgv': [sys.executable, str(compiler), '--bundle', str(bundle), '--java-home', str(compiler_java_home),
                         '--runtime', str(root / 'compile')] + (['--platform-adapters', adapter_config['root'],
                             '--platform-adapters-sha256', adapter_config['manifestSha256']] if adapter_config else [])}


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--install-root', required=True, type=Path)
    parser.add_argument('--bundle', required=True, type=Path)
    parser.add_argument('--platform-adapters', type=Path, help='Separately verified platform JDBC subclass package')
    parser.add_argument('--java-home', required=True, type=Path, help='Linux x86_64 JDK 11 for original Core and control bridge')
    parser.add_argument('--compiler-java-home', type=Path,
                        help='JDK 11 or 17 for original compiler/metadata; defaults to --java-home')
    parser.add_argument('--redis-password-file', required=True, type=Path)
    parser.add_argument('--redis-port', type=int, default=6379)
    parser.add_argument('--redis-database', type=int, required=True)
    parser.add_argument('--compiler-launcher', type=Path)
    parser.add_argument('--verify-only', action='store_true')
    args = parser.parse_args(argv)
    previous = os.umask(0o077)
    try:
        print(json.dumps(prepare(args), ensure_ascii=False))
        return 0
    except Exception as error:
        code = str(error) if isinstance(error, SetupError) else 'INSTALLATION_PREPARE_FAILED'
        print(json.dumps({'protocolVersion': 1, 'ok': False, 'code': code}))
        return 2
    finally:
        os.umask(previous)


if __name__ == '__main__':
    raise SystemExit(main())
