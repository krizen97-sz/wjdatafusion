#!/usr/bin/env python3
"""Verified, relocatable original Kettle bundle; private child of the RYNEW backend."""
import argparse
import contextlib
import fcntl
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import re
import shutil
import signal
import stat
import subprocess
import sys
import threading

sys.dont_write_bytecode = True
import kettle_worker as broker

OWNER = 'rynew-bundled-kettle-v1'
MANIFEST = 'bundle-manifest.json'
SCRIPTS = ('bundled_kettle_runtime.py', 'kettle_worker.py', 'kettle_linux_runtime.py')


def safe_relative(value):
    if not isinstance(value, str) or not value or '\\' in value or ':' in value:
        raise ValueError('Bundle paths must be bounded relative POSIX paths')
    path = PurePosixPath(value)
    if path.is_absolute() or str(path) != value or any(part in {'.', '..'} for part in path.parts):
        raise ValueError('Bundle paths must be bounded relative POSIX paths')
    return path


def regular_file(root, relative):
    relative = safe_relative(relative)
    path = root
    for part in relative.parts:
        path = path / part
        if path.is_symlink():
            raise ValueError('Bundle paths cannot contain symbolic links')
    if not path.is_file() or not stat.S_ISREG(path.stat().st_mode):
        raise ValueError('Bundle artifact is not a regular file: ' + str(relative))
    return path


def verify_bundle(bundle):
    bundle = Path(bundle).resolve(strict=True)
    manifest_path = regular_file(bundle, MANIFEST)
    manifest = json.loads(manifest_path.read_text())
    if manifest.get('schemaVersion') != 1 or manifest.get('engine') != 'original-kettle' or manifest.get('javaMajor') != 17:
        raise ValueError('Unsupported original Kettle bundle format')
    records = manifest.get('files')
    if not isinstance(records, list) or not records:
        raise ValueError('Bundle file inventory is required')
    paths = set()
    for record in records:
        value = record['path']
        safe_relative(value)
        if value in paths or not (value.startswith('lib/') or value.startswith('classes/') or value in SCRIPTS):
            raise ValueError('Unexpected or duplicate bundle artifact')
        paths.add(value)
        if not re.fullmatch(r'[a-f0-9]{64}', record.get('sha256', '')) or broker.file_digest(regular_file(bundle, value)) != record['sha256']:
            raise ValueError('Bundle artifact checksum mismatch: ' + value)
    expected = paths | {MANIFEST}
    actual = {str(p.relative_to(bundle)) for p in bundle.rglob('*') if p.is_file() or p.is_symlink()}
    if expected != actual:
        raise ValueError('Bundle contains unlisted or missing files')
    classpath = manifest.get('classpath')
    jars = {p for p in paths if p.startswith('lib/')}
    if not isinstance(classpath, list) or classpath[:1] != ['classes'] or len(classpath) != len(set(classpath)) or set(classpath[1:]) != jars:
        raise ValueError('Bundle classpath must explicitly include each verified library exactly once')
    if 'lib/kettle-6.1.0.7.36.jar' not in jars or 'classes/KettleWorker.class' not in paths or not set(SCRIPTS) <= paths:
        raise ValueError('Original engine or compiled worker is missing')
    return manifest, broker.file_digest(manifest_path)


def check_java(java_home):
    java_home = Path(java_home).resolve(strict=True)
    java = java_home / 'bin/java'
    if not java.is_file() or not os.access(java, os.X_OK):
        raise ValueError('Explicit Java home does not contain executable bin/java')
    result = subprocess.run([str(java), '-version'], capture_output=True, text=True, timeout=15, env={'PATH': '/usr/bin:/bin'})
    if result.returncode or not re.search(r'(?:openjdk|java) version "17(?:[.\-"]|$)', result.stderr + result.stdout):
        raise ValueError('Bundled Kettle requires an explicitly configured Java 17 runtime')
    return java_home


def install_runtime(bundle, runtime, java_home):
    """Only immutable engine artifacts are copied; never source credentials/tasks/history."""
    bundle = Path(bundle).resolve(strict=True)
    manifest, digest = verify_bundle(bundle)
    runtime = Path(runtime).resolve(strict=True)
    if bundle == runtime or bundle in runtime.parents or runtime in bundle.parents:
        raise ValueError('Bundle and private runtime must be separate trees')
    marker = runtime / 'bundle-install.json'
    if marker.exists():
        if marker.is_symlink() or broker.read_private_json(marker).get('bundleSha256') != digest:
            raise ValueError('Runtime belongs to a different bundle; select a new private runtime directory')
    else:
        for name in ('lib', 'classes', 'manifest.json'):
            if (runtime / name).exists() or (runtime / name).is_symlink():
                raise ValueError('Refusing to adopt unverified existing engine artifacts')
        for record in manifest['files']:
            if not record['path'].startswith(('lib/', 'classes/')):
                continue
            target = runtime / record['path']
            target.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
            shutil.copyfile(regular_file(bundle, record['path']), target)
            target.chmod(0o600)
        broker.atomic_private_json(marker, {'owner': OWNER, 'bundleSha256': digest})
    for record in manifest['files']:
        if record['path'].startswith(('lib/', 'classes/')) and broker.file_digest(regular_file(runtime, record['path'])) != record['sha256']:
            raise ValueError('Installed engine artifact checksum mismatch')
    expected_artifacts = {r['path'] for r in manifest['files'] if r['path'].startswith(('lib/', 'classes/'))}
    actual_artifacts = {str(p.relative_to(runtime)) for name in ('lib', 'classes') for p in (runtime / name).rglob('*') if p.is_file() or p.is_symlink()}
    if expected_artifacts != actual_artifacts:
        raise ValueError('Installed engine contains unlisted or missing artifacts')
    runtime_manifest = {'javaHome': str(java_home), 'classpath': os.pathsep.join(str(runtime / p) for p in manifest['classpath']), 'libraries': manifest.get('libraries', []), 'bundleSha256': digest}
    broker.atomic_private_json(runtime / 'manifest.json', runtime_manifest)
    return runtime_manifest


@contextlib.contextmanager
def owned_runtime(value):
    raw = Path(value).absolute()
    if any(p.is_symlink() for p in (raw, *raw.parents)):
        raise ValueError('Private runtime path must not contain symbolic links')
    if len(raw.parts) < 4:
        raise ValueError('Private runtime must be a bounded application directory')
    if raw.exists() and (raw.stat().st_uid != os.getuid() or stat.S_IMODE(raw.stat().st_mode) & 0o077):
        raise ValueError('Existing private runtime must be owned by this user with mode 0700')
    raw.mkdir(parents=True, exist_ok=True, mode=0o700)
    owner = raw / '.bundle-owner.json'
    if not owner.exists():
        existing = list(raw.iterdir())
        if any(p.name not in {'.platform-owner.lock', 'runtime.log'} or p.is_symlink() or not p.is_file() or p.stat().st_uid != os.getuid() or stat.S_IMODE(p.stat().st_mode) & 0o077 for p in existing):
            raise ValueError('Refusing to adopt a nonempty runtime without bundle ownership')
        broker.atomic_private_json(owner, {'owner': OWNER})
    if broker.read_private_json(owner).get('owner') != OWNER:
        raise ValueError('Runtime owner does not match')
    if (raw / '.shutdown-unconfirmed.json').exists():
        raise RuntimeError('Previous shutdown was not confirmed; inspect owned run records before any restart')
    lock_fd = os.open(raw / '.bundle.lock', os.O_CREAT | os.O_RDWR | os.O_NOFOLLOW, 0o600)
    try:
        fcntl.flock(lock_fd, fcntl.LOCK_EX | fcntl.LOCK_NB)
        yield raw.resolve()
    finally:
        os.close(lock_fd)


class BubblewrapSandbox:
    """No host networking, no daemon, no unisolated fallback; only operation files writable."""
    kind = 'linux-bubblewrap'

    def __init__(self, runtime, java_home, executable=None):
        self.runtime, self.java_home = Path(runtime).resolve(), Path(java_home).resolve()
        self.executable = executable or shutil.which('bwrap')
        if not self.executable:
            raise RuntimeError('bubblewrap is required on Linux; unisolated execution is refused')

    def command(self, java_command, operation):
        operation = Path(operation).resolve()
        if self.runtime not in operation.parents or operation == self.runtime:
            raise ValueError('Sandbox operation must be inside the owned runtime')
        cmd = [self.executable, '--unshare-all', '--die-with-parent', '--new-session', '--cap-drop', 'ALL', '--clearenv', '--setenv', 'PATH', '/usr/bin:/bin', '--setenv', 'HOME', str(operation / 'home'), '--setenv', 'LANG', 'C.UTF-8', '--proc', '/proc', '--dev', '/dev', '--tmpfs', '/tmp']
        # Bind only the OS runtime libraries, never /, /home, /root, /etc or the broker state.
        for name in ('/usr/lib', '/usr/lib64', '/lib', '/lib64', '/usr/share/zoneinfo', '/etc/localtime'):
            path = Path(name)
            if path.exists():
                cmd += ['--ro-bind', str(path.resolve()), name]
        for path in (self.java_home, self.runtime / 'lib', self.runtime / 'classes'):
            cmd += ['--ro-bind', str(path), str(path)]
        cmd += ['--bind', str(operation), str(operation), '--chdir', str(operation), '--', *map(str, java_command)]
        return cmd

    def probe(self):
        import tempfile
        with tempfile.TemporaryDirectory(prefix='sandbox-probe-', dir=self.runtime) as folder:
            result = subprocess.run(self.command([self.java_home / 'bin/java', '-version'], folder), stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=20)
            if result.returncode:
                raise RuntimeError('bubblewrap isolation probe failed; check installed bwrap/user namespace support; execution refused')


def serve_owned(worker, port):
    stop_requested = threading.Event()
    server_ready = threading.Event()
    holder = {}

    def stop():
        if stop_requested.is_set():
            return
        stop_requested.set()
        with worker.launch_lock:
            worker.closing = True
        server_ready.wait()
        holder['server'].shutdown()

    def parent_watch():
        try:
            while sys.stdin.buffer.read(1):
                pass
        finally:
            stop()

    def ready(server):
        holder['server'] = server
        server_ready.set()
        threading.Thread(target=parent_watch, name='kettle-parent-lifetime', daemon=True).start()

    def signal_stop(_signum, _frame):
        threading.Thread(target=stop, daemon=True).start()

    old = {s: signal.signal(s, signal_stop) for s in (signal.SIGTERM, signal.SIGINT)}
    try:
        broker.serve(worker, port, ready=ready)
    finally:
        confirmed = worker.close_owned()
        if not confirmed:
            broker.atomic_private_json(worker.runtime / '.shutdown-unconfirmed.json', {'owner': OWNER, 'state': broker.RECOVERY_REQUIRED, 'message': 'Owned child termination or result finalization was not confirmed; no automatic restart or replay'})
        for signum, handler in old.items():
            signal.signal(signum, handler)
        if not confirmed:
            raise RuntimeError('Owned child shutdown was not confirmed; runtime requires recovery review')


def main():
    os.umask(0o077)
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--bundle', required=True, type=Path)
    parser.add_argument('--runtime', required=True, type=Path)
    parser.add_argument('--java-home', required=True, type=Path)
    parser.add_argument('--port', type=int, default=0)
    parser.add_argument('--timeout', type=int, default=120)
    args = parser.parse_args()
    if not 0 <= args.port <= 65535 or not 1 <= args.timeout <= 3600:
        parser.error('Invalid port or operation timeout')
    try:
        java_home = check_java(args.java_home)
        with owned_runtime(args.runtime) as runtime:
            install_runtime(args.bundle, runtime, java_home)
            sandbox = None
            if sys.platform == 'linux':
                sandbox = BubblewrapSandbox(runtime, java_home)
                sandbox.probe()
            worker = broker.Worker(runtime, timeout=args.timeout, local_sandbox=sandbox)
            serve_owned(worker, args.port)
    except Exception as error:
        print('Bundled Kettle startup/shutdown failed: ' + str(error), file=sys.stderr, flush=True)
        return 1
    return 0


if __name__ == '__main__':
    sys.exit(main())
