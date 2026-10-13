#!/usr/bin/env python3
"""Private fixed-destination byte bridges and Linux namespace supervisor.

No ETL transformations or vendor authentication are implemented here. The original
Core runs in a new network/PID namespace; only explicitly configured dependencies
are reachable through private Unix sockets. There is no host-network fallback.
"""
from __future__ import annotations

import argparse
import json
import os
from pathlib import Path
import signal
import socket
import subprocess
import sys
import threading
import time


class ByteBridge:
    """A bounded transparent stream bridge to one immutable destination."""

    def __init__(self, family, address, target_family, target, max_connections=32):
        self.family, self.address = family, address
        self.target_family, self.target = target_family, target
        self.stopped = threading.Event()
        self.slots = threading.BoundedSemaphore(max_connections)
        self.lock = threading.Lock()
        self.connections = set()
        self.threads = set()
        self.listener = socket.socket(family, socket.SOCK_STREAM)
        try:
            if family == socket.AF_UNIX:
                path = Path(address)
                if path.exists() or path.is_symlink() or len(os.fsencode(path)) > 100:
                    raise ValueError('Unix bridge path must be new and at most 100 bytes')
                if path.parent.stat().st_uid != os.getuid() or path.parent.stat().st_mode & 0o077:
                    raise ValueError('Unix bridge requires a private owned parent')
            self.listener.bind(address)
            if family == socket.AF_UNIX:
                os.chmod(address, 0o600)
                self.socket_inode = os.lstat(address).st_ino
            self.listener.listen(32)
            self.listener.settimeout(.2)
        except BaseException:
            self.listener.close()
            raise
        self.accept_thread = threading.Thread(target=self._accept, daemon=True)
        self.accept_thread.start()

    @staticmethod
    def _close(sock):
        try:
            sock.shutdown(socket.SHUT_RDWR)
        except OSError:
            pass
        sock.close()

    def _accept(self):
        while not self.stopped.is_set():
            try:
                source, _ = self.listener.accept()
            except socket.timeout:
                continue
            except OSError:
                break
            if not self.slots.acquire(False):
                source.close()
                continue
            thread = threading.Thread(target=self._connection, args=(source,), daemon=True)
            with self.lock:
                self.threads.add(thread)
                self.connections.add(source)
            thread.start()

    def _connection(self, source):
        target = socket.socket(self.target_family, socket.SOCK_STREAM)
        with self.lock:
            self.connections.add(target)
        try:
            if self.stopped.is_set():
                return
            target.settimeout(3)
            target.connect(self.target)
            target.settimeout(None)
            if self.stopped.is_set():
                return

            def pump(reader, writer):
                try:
                    while not self.stopped.is_set():
                        data = reader.recv(65536)
                        if not data:
                            break
                        writer.sendall(data)
                except OSError:
                    pass
                finally:
                    try:
                        writer.shutdown(socket.SHUT_WR)
                    except OSError:
                        pass

            reverse = threading.Thread(target=pump, args=(target, source), daemon=True)
            reverse.start()
            pump(source, target)
            reverse.join()
        except OSError:
            pass
        finally:
            self._close(source)
            self._close(target)
            with self.lock:
                self.connections.discard(source)
                self.connections.discard(target)
                self.threads.discard(threading.current_thread())
            self.slots.release()

    def close(self, deadline=None):
        if deadline is None:
            deadline = time.monotonic() + 5
        self.stopped.set()
        self.listener.close()
        self.accept_thread.join(timeout=max(0, min(1, deadline - time.monotonic())))
        with self.lock:
            sockets, threads = list(self.connections), list(self.threads)
        for connection in sockets:
            self._close(connection)
        deadline = min(deadline, time.monotonic() + 4)
        for thread in threads:
            thread.join(timeout=max(0, deadline - time.monotonic()))
        if self.family == socket.AF_UNIX:
            try:
                if os.lstat(self.address).st_ino == self.socket_inode:
                    os.unlink(self.address)
            except FileNotFoundError:
                pass
        if self.accept_thread.is_alive() or any(thread.is_alive() for thread in threads):
            raise RuntimeError('Dependency bridge close was not confirmed')


def namespace_supervisor(config_path):
    if sys.platform != 'linux':
        raise RuntimeError('Original Core isolation requires Linux')
    path = Path(config_path)
    if path.is_symlink() or not path.is_file() or path.stat().st_mode & 0o077:
        raise ValueError('Namespace config must be a private regular file')
    config = json.loads(path.read_text())
    netns = os.readlink('/proc/self/ns/net')
    if netns == config['parentNetworkNamespace'] or os.getpid() != 1:
        raise RuntimeError('Separate network and PID namespaces are mandatory')
    subprocess.run(['ip', 'link', 'set', 'lo', 'up'], check=True,
                   stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    if sorted(name for _, name in socket.if_nameindex()) != ['lo']:
        raise RuntimeError('Core namespace contains a nonloopback interface')
    import resource
    # Original DiskStore maps a 256 MiB segment. Output files have a separate,
    # smaller aggregate monitor; this process-wide cap also applies to scratch.
    file_limit = config.get('maximumFileBytes', 512 * 1024 * 1024)
    if type(file_limit) is not int or not 1024 <= file_limit <= 1024 * 1024 * 1024:
        raise ValueError('Invalid Core file-size limit')
    resource.setrlimit(resource.RLIMIT_FSIZE, (file_limit, file_limit))
    resource.setrlimit(resource.RLIMIT_CORE, (0, 0))
    bridges, process, stream = [], None, None
    stop = threading.Event()
    for signum in (signal.SIGTERM, signal.SIGINT, signal.SIGHUP):
        signal.signal(signum, lambda _signal, _frame: stop.set())

    def control():
        # EOF is an ownership lease: loss of the platform worker ends the Core.
        try:
            for line in sys.stdin:
                if line.strip() == 'HALT':
                    break
        finally:
            stop.set()

    def event(**value):
        print(json.dumps(value, separators=(',', ':')), flush=True)

    thread = threading.Thread(target=control, daemon=True)
    thread.start()
    try:
        for proxy in config['dependencies']:
            bridges.append(ByteBridge(socket.AF_INET, ('127.0.0.1', proxy['port']),
                                      socket.AF_UNIX, proxy['unixSocket']))
        stream = open(config['coreLog'], 'ab', buffering=0)
        process = subprocess.Popen(config['command'], cwd=config['cwd'], env=config['environment'],
                                   stdin=subprocess.DEVNULL, stdout=stream, stderr=stream)
        event(event='CORE_STARTED', pid=str(process.pid), networkNamespace=netns,
              networkInterfaces=['lo'], parentNetworkNamespace=config['parentNetworkNamespace'])
        announced = False
        while not stop.wait(.2):
            code = process.poll()
            if code is not None:
                event(event='CORE_EXITED', exitCode=code)
                return code
            if not announced:
                listening = True
                for port in config['corePorts']:
                    try:
                        with socket.create_connection(('127.0.0.1', port), timeout=.1):
                            pass
                    except OSError:
                        listening = False
                if listening:
                    announced = True
                    # This event is deliberately not a business READY assertion.
                    event(event='CORE_PORTS_LISTENING', ports=config['corePorts'])
    finally:
        forced = False
        if process is not None and process.poll() is None:
            process.terminate()
            try:
                process.wait(timeout=config.get('stopGraceSeconds', 10))
            except subprocess.TimeoutExpired:
                forced = True
                process.kill()
                process.wait(timeout=5)
        for bridge in reversed(bridges):
            bridge.close()
        if stream is not None:
            stream.close()
        event(event='CORE_CLOSED', exitCode=process.poll() if process else None,
              forced=forced, finalized=process is None or process.poll() is not None)
    return 0


def leased_exec(config_path, parent_pid):
    """Linux parent-death signal survives exec and prevents an orphan JVM."""
    import ctypes
    if sys.platform != 'linux':
        raise RuntimeError('Linux process ownership is required')
    libc = ctypes.CDLL(None, use_errno=True)
    if libc.prctl(1, signal.SIGKILL, 0, 0, 0) != 0 or os.getppid() != parent_pid:
        raise RuntimeError('Coordinator parent ownership was lost')
    config = json.loads(Path(config_path).read_text())
    os.execv(config['command'][0], config['command'])


def leased_supervisor(config_path):
    path = Path(config_path)
    if path.is_symlink() or not path.is_file() or path.stat().st_mode & 0o077:
        raise ValueError('Coordinator config must be a private regular file')
    config = json.loads(path.read_text())
    stopped = threading.Event()
    for signum in (signal.SIGTERM, signal.SIGINT, signal.SIGHUP):
        signal.signal(signum, lambda _s, _f: stopped.set())
    def lease():
        try:
            for line in sys.stdin:
                if line.strip() == 'HALT':
                    break
        finally:
            stopped.set()
    process = None
    with open(config['log'], 'ab', buffering=0) as stream:
        try:
            process = subprocess.Popen([sys.executable, str(Path(__file__).resolve()),
                '--leased-exec', str(path), '--parent-pid', str(os.getpid())],
                stdin=subprocess.DEVNULL, stdout=stream, stderr=stream)
            threading.Thread(target=lease, daemon=True).start()
            print(json.dumps({'event': 'COORDINATOR_STARTED', 'pid': str(process.pid)}), flush=True)
            while not stopped.wait(.2):
                if process.poll() is not None:
                    return process.returncode
        finally:
            if process is not None and process.poll() is None:
                process.terminate()
                try:
                    process.wait(timeout=10)
                except subprocess.TimeoutExpired:
                    process.kill()
                    process.wait(timeout=5)
            print(json.dumps({'event': 'COORDINATOR_CLOSED',
                              'finalized': process is None or process.poll() is not None}), flush=True)
    return 0


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    group = parser.add_mutually_exclusive_group(required=True)
    group.add_argument('--namespace-config')
    group.add_argument('--leased-config')
    group.add_argument('--leased-exec')
    parser.add_argument('--parent-pid', type=int)
    args = parser.parse_args()
    try:
        if args.leased_exec:
            leased_exec(args.leased_exec, args.parent_pid)
        elif args.leased_config:
            raise SystemExit(leased_supervisor(args.leased_config))
        else:
            raise SystemExit(namespace_supervisor(args.namespace_config))
    except Exception as error:
        print(json.dumps({'event': 'ISOLATION_FAILED', 'errorType': type(error).__name__}), flush=True)
        raise SystemExit(2)
