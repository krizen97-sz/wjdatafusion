#!/usr/bin/env python3
"""Owned original-DETL Core lifecycle. JSON-lines on stdio; no ETL algorithms."""
from __future__ import annotations

import argparse
import base64
import fcntl
import hashlib
import json
import os
from pathlib import Path
import queue
import re
import secrets
import shutil
import signal
import socket
import stat
import subprocess
import sys
import tempfile
import threading
import time
import uuid
import zipfile
import xml.etree.ElementTree as ET

from hdi_core_network import ByteBridge
from hdi_core_protocol import (ProtocolError, TERMINAL, atomic_json, atomic_bytes, below_output,
                               positive_id, private_file, reservation, results_manifest,
                               run_id, strict_json, validate_compiled)
from hdi_original_execution_proof import verify_bundle

MAX_LINE = 48 * 1024 * 1024
MAX_COMPILED = 32 * 1024 * 1024
ROOT = Path(__file__).resolve().parent
SHUTDOWN_SECONDS = 70
CORE_FILE_LIMIT = 512 * 1024 * 1024


def stop_timeout(config):
    value = config.get('stopTimeoutSeconds', 45)
    if type(value) is not int or not 45 <= value <= 60:
        raise ProtocolError('INVALID_STOP_TIMEOUT')
    return value

LOGGING = ('<configuration><appender name="STDERR" class="ch.qos.logback.core.ConsoleAppender">'
           '<target>System.err</target><encoder><pattern>%date %-5level [%thread] %logger '
           '- %msg%n%ex</pattern></encoder></appender><logger name="org.apache.zookeeper" '
           'level="WARN"/><logger name="org.apache.curator" level="WARN"/>'
           '<root level="INFO"><appender-ref ref="STDERR"/></root></configuration>')


def core_logging(bundle, run_root):
    """Keep original SIFT writers; relocate only this run's log destinations."""
    tree = ET.parse(bundle / 'instance/logback.xml')
    configuration = tree.getroot()
    properties = {item.get('name'): item for item in configuration.findall('property')}
    appenders = {item.get('name'): item for item in configuration.findall('appender')}
    if not {'LOG_HOME', 'DURABLE_LOG_HOME'} <= properties.keys() or not {'SIFT', 'STDOUT'} <= appenders.keys():
        raise ProtocolError('ORIGINAL_LOGGING_CONFIG_REQUIRED')
    properties['LOG_HOME'].set('value', str(run_root / 'logs'))
    properties['DURABLE_LOG_HOME'].set('value', str(run_root / 'logs/durable'))
    # The existing stdio sanitizer remains the platform log transport. Original
    # file appenders/discriminators also run, for the Core's own history readers.
    target = appenders['STDOUT'].find('target')
    if target is None:
        target = ET.SubElement(appenders['STDOUT'], 'target')
    target.text = 'System.err'
    for logger in [*configuration.findall('logger'), *configuration.findall('root')]:
        if not any(ref.get('ref') == 'STDOUT' for ref in logger.findall('appender-ref')):
            ET.SubElement(logger, 'appender-ref', {'ref': 'STDOUT'})
    path = run_root / 'private/core-logback.xml'
    tree.write(path, encoding='utf-8', xml_declaration=True)
    return path


class RunFileBudget:
    """Independent polling, so a blocked metadata request cannot defer limits."""
    LIMITS = (
        ('OUTPUT', ('outputs',), 64 * 1024 * 1024, 128),
        ('SCRATCH', ('store', 'tmp', 'work', 'fel', 'home'), 1024 * 1024 * 1024, 4096),
        ('LOG', ('logs',), 64 * 1024 * 1024, 1024),
    )

    def __init__(self, root, halt, interval=.2):
        self.root, self.halt, self.interval = Path(root), halt, interval
        self.stopped = threading.Event()
        self.error = None
        self.thread = threading.Thread(target=self.watch, daemon=True)

    def check(self):
        for name, folders, maximum, maximum_files in self.LIMITS:
            totals = [0, 0, 0]  # bytes, files, directories; logical size includes sparse mappings.

            def scan(parent_fd, child_name, depth):
                if depth > 64:
                    raise ProtocolError(name + '_LIMIT')
                try:
                    fd = os.open(child_name, os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW, dir_fd=parent_fd)
                except FileNotFoundError:
                    return  # A rotated/removed directory no longer consumes the quota.
                except OSError:
                    raise ProtocolError(name + '_PATH_REJECTED')
                try:
                    totals[2] += 1
                    if totals[2] > 4096:
                        raise ProtocolError(name + '_LIMIT')
                    with os.scandir(fd) as entries:
                        for entry in entries:
                            try:
                                info = entry.stat(follow_symlinks=False)
                            except FileNotFoundError:
                                continue
                            if stat.S_ISDIR(info.st_mode):
                                scan(fd, entry.name, depth + 1)
                            elif stat.S_ISREG(info.st_mode) and info.st_nlink == 1:
                                totals[0] += info.st_size
                                totals[1] += 1
                                if totals[0] > maximum or totals[1] > maximum_files:
                                    raise ProtocolError(name + '_LIMIT')
                            else:
                                raise ProtocolError(name + '_PATH_REJECTED')
                finally:
                    os.close(fd)

            fd = os.open(self.root, os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW)
            try:
                for folder in folders:
                    scan(fd, folder, 0)
            finally:
                os.close(fd)

    def watch(self):
        while not self.stopped.is_set():
            try:
                self.check()
            except Exception as failure:
                self.error = failure.code if isinstance(failure, ProtocolError) else 'RUNTIME_BUDGET_UNAVAILABLE'
                # Callback only targets the still-held namespace supervisor, not a saved PID.
                try:
                    self.halt()
                except ProcessLookupError:
                    pass
                return
            self.stopped.wait(self.interval)

    def close(self, deadline):
        self.stopped.set()
        if self.thread.ident is None:
            return
        self.thread.join(timeout=max(0, min(1, deadline - time.monotonic())))
        if self.thread.is_alive():
            raise ProtocolError('BUDGET_MONITOR_STOP_UNCONFIRMED')


def private_directory(path):
    path = Path(path).absolute()
    if any(p.is_symlink() for p in (path, *path.parents)):
        raise ProtocolError('PRIVATE_DIRECTORY_REQUIRED')
    path.mkdir(parents=True, mode=0o700, exist_ok=True)
    info = path.stat()
    if info.st_uid != os.getuid() or stat.S_IMODE(info.st_mode) != 0o700:
        raise ProtocolError('PRIVATE_DIRECTORY_REQUIRED')
    return path


def identity(pid):
    """Linux start ticks + exact argv; never treat a saved PID as ownership."""
    try:
        base = Path('/proc') / str(pid)
        data = (base / 'stat').read_text()
        rest = data[data.rfind(')') + 2:].split()
        return {'startTicks': rest[19], 'argvSha256': hashlib.sha256((base / 'cmdline').read_bytes()).hexdigest(),
                'uid': base.stat().st_uid}
    except (FileNotFoundError, ProcessLookupError):
        return None


class JsonChild:
    def __init__(self, argv, log, cwd=None):
        self.stream = open(log, 'ab', buffering=0)
        self.process = subprocess.Popen(argv, cwd=cwd, stdin=subprocess.PIPE, stdout=subprocess.PIPE,
                                        stderr=self.stream, bufsize=0, start_new_session=True)
        self.events = queue.Queue()
        self.lock = threading.Lock()
        self.closed = False
        self.record = {'pid': self.process.pid, 'identity': identity(self.process.pid)}

        def reader():
            while True:
                raw = self.process.stdout.readline(8 * 1024 * 1024 + 1)
                if not raw:
                    break
                if len(raw) > 8 * 1024 * 1024:
                    self.events.put({'event': 'INVALID_CHILD_PROTOCOL'})
                    break
                try:
                    self.events.put(strict_json(raw))
                except Exception:
                    self.events.put({'event': 'INVALID_CHILD_PROTOCOL'})
                    break
            self.events.put({'event': 'CHILD_EOF'})
        self.reader = threading.Thread(target=reader, daemon=True)
        self.reader.start()

    def receive(self, timeout):
        try:
            return self.events.get(timeout=timeout)
        except queue.Empty:
            raise ProtocolError('CHILD_RESPONSE_TIMEOUT') from None

    def call(self, op, **data):
        with self.lock:
            request = uuid.uuid4().hex
            if self.closed or self.process.poll() is not None:
                raise ProtocolError('CHILD_UNAVAILABLE')
            self.process.stdin.write((json.dumps({'requestId': request, 'op': op, **data}) + '\n').encode())
            self.process.stdin.flush()
            response = self.receive(20)
            if response.get('requestId') != request or response.get('ok') is not True:
                raise ProtocolError('ORIGINAL_CONTROL_REJECTED')
            return response['data']

    def stop(self, grace=20, deadline=None):
        if deadline is None:
            deadline = time.monotonic() + grace + 10
        with self.lock:
            if self.closed:
                return
            self.closed = True
            if self.process.poll() is None:
                try:
                    self.process.stdin.close()
                except OSError:
                    pass
        try:
            self.process.wait(timeout=max(0, min(grace, deadline - time.monotonic() - 2)))
        except subprocess.TimeoutExpired:
            self.process.terminate()
            try:
                self.process.wait(timeout=max(0, min(5, deadline - time.monotonic() - 1)))
            except subprocess.TimeoutExpired:
                self.process.kill()
                try:
                    self.process.wait(timeout=max(0, deadline - time.monotonic()))
                except subprocess.TimeoutExpired:
                    raise ProtocolError('STOP_UNCONFIRMED') from None
        finally:
            self.stream.close()
        if self.process.poll() is None:
            raise ProtocolError('STOP_UNCONFIRMED')


class Worker:
    def __init__(self, config):
        self.guard = threading.RLock()
        self.records, self.tasks = {}, {}
        self.closing = False
        self.closed_result = None
        self.shutdown_deadline = None
        self.lock_fd = None
        self.coordinator = self.coordinator_stream = None
        try:
            self.initialize(config)
        except BaseException:
            self.close()
            raise

    def initialize(self, config):
        if sys.platform != 'linux':
            raise ProtocolError('LINUX_ISOLATION_REQUIRED')
        import resource
        resource.setrlimit(resource.RLIMIT_CORE, (0, 0))
        os.umask(0o077)
        self.config = config
        self.stop_timeout = stop_timeout(config)
        self.root = private_directory(config['stateDir'])
        self.runs_root = private_directory(self.root / 'runs')
        self.lock_fd = os.open(self.root / '.worker.lock', os.O_CREAT | os.O_RDWR | os.O_NOFOLLOW, 0o600)
        info = os.fstat(self.lock_fd)
        if info.st_uid != os.getuid() or info.st_mode & 0o077:
            raise ProtocolError('PRIVATE_LOCK_REQUIRED')
        try:
            fcntl.flock(self.lock_fd, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError:
            os.close(self.lock_fd)
            self.lock_fd = None
            raise ProtocolError('WORKER_ALREADY_RUNNING') from None
        marker = self.root / '.worker-owner.json'
        expected_owner = {'engine': 'original-detl-core', 'protocolVersion': 1,
                          'root': str(self.root), 'uid': os.getuid()}
        if marker.exists():
            if strict_json(private_file(marker)) != expected_owner:
                raise ProtocolError('FOREIGN_RUNTIME_ROOT')
        else:
            if set(p.name for p in self.root.iterdir()) - {'.worker.lock', 'runs'} or any(self.runs_root.iterdir()):
                raise ProtocolError('FOREIGN_RUNTIME_ROOT')
            atomic_json(marker, expected_owner)
        if (self.root / '.shutdown-unconfirmed.json').exists():
            raise ProtocolError('RECOVERY_REQUIRED')
        self.guard = threading.RLock()
        self.records, self.tasks = {}, {}
        self.closing = False
        self.coordinator = None
        self.coordinator_stream = None
        self.bundle = Path(config['bundle']).resolve(strict=True)
        verify_bundle(self.bundle)
        self.java_home = Path(config['javaHome']).resolve(strict=True)
        self.java = str(self.java_home / 'bin/java')
        self.java_mode = config.get('javaRuntimeMode', 'normal')
        if self.java_mode not in ('normal', 'interpreted-validation'):
            raise ProtocolError('INVALID_JAVA_RUNTIME_MODE')
        self.java_options = ['-Xint'] if self.java_mode == 'interpreted-validation' else []
        version = subprocess.run([self.java, *self.java_options, '-version'], capture_output=True, text=True, timeout=15)
        if version.returncode or not re.search(r'version "11\.', version.stdout + version.stderr):
            raise ProtocolError('JAVA_11_REQUIRED')
        self.control_jar = Path(config['controlJar']).resolve(strict=True)
        if self.control_jar.suffix != '.jar' or not self.control_jar.is_file():
            raise ProtocolError('CONTROL_JAR_REQUIRED')
        if hashlib.sha256(self.control_jar.read_bytes()).hexdigest() != config.get('controlJarSha256'):
            raise ProtocolError('CONTROL_JAR_HASH_MISMATCH')
        parent_ns = os.readlink('/proc/self/ns/net')
        probe = subprocess.run(['unshare', '--user', '--map-root-user', '--mount-proc', '--net',
                                '--pid', '--fork', '--kill-child=SIGKILL', sys.executable, '-c',
                                'import os,socket; assert os.getpid()==1; '
                                'assert [n for _,n in socket.if_nameindex()]==["lo"]; '
                                'print(os.readlink("/proc/self/ns/net"))'],
                               capture_output=True, text=True, timeout=10)
        if probe.returncode or probe.stdout.strip() == parent_ns:
            raise ProtocolError('NAMESPACE_UNAVAILABLE')
        self.log_config = self.root / 'runtime-logback.xml'
        self.log_config.write_text(LOGGING)
        self.redis = dict(config['redis'])
        self.endpoint(self.redis)
        if type(self.redis.get('database')) is not int or not 0 <= self.redis['database'] <= 15:
            raise ProtocolError('REDIS_DATABASE_REQUIRED')
        self.redis_password = private_file(self.redis['passwordFile'], 4096).decode()
        if not self.redis_password:
            raise ProtocolError('REDIS_PASSWORD_REQUIRED')
        self.redis_probe()
        self.tls = None
        if config.get('tls') is not None:
            tls = config['tls']
            self.tls = {
                'server': private_file(tls['serverKeystore'], 1024 * 1024),
                'client': private_file(tls['clientKeystore'], 1024 * 1024),
                'password': private_file(tls['passwordFile'], 4096).decode(),
            }
            if not self.tls['server'] or not self.tls['client'] or not self.tls['password']:
                raise ProtocolError('PRIVATE_TLS_MATERIAL_REQUIRED')
        self.zk = config.get('zookeeper')
        if self.zk:
            self.zk = dict(self.zk)
            self.endpoint(self.zk)
            with socket.create_connection(('127.0.0.1', self.zk['port']), timeout=3):
                pass
        else:
            self.start_coordinator()
        self.max_runs = config.get('maxConcurrentRuns', 1)
        if type(self.max_runs) is not int or not 1 <= self.max_runs <= 4:
            raise ProtocolError('INVALID_CONCURRENCY')
        for key, default, minimum, maximum in [('startupTimeoutSeconds', 120, 5, 300),
                                                ('executionTimeoutSeconds', 300, 1, 86400)]:
            value = config.get(key, default)
            if type(value) is not int or not minimum <= value <= maximum:
                raise ProtocolError('INVALID_TIMEOUT')
        for path in self.runs_root.iterdir():
            if path.is_symlink() or not path.is_dir():
                raise ProtocolError('FOREIGN_RUNTIME_ENTRY')
            record = strict_json(private_file(path / 'state.json'))
            if run_id(record.get('runId')) != path.name:
                raise ProtocolError('FOREIGN_RUNTIME_ENTRY')
            if record['state'] not in TERMINAL | {'RESERVED'}:
                record.update(state='UNKNOWN', finalized=False, recoveryRequired=True, errorCode='RECOVERY_REQUIRED')
                atomic_json(path / 'state.json', record)
            self.records[path.name] = record

    @staticmethod
    def endpoint(value):
        if value.get('host') != '127.0.0.1' or type(value.get('port')) is not int or not 1024 <= value['port'] <= 65535:
            raise ProtocolError('LOOPBACK_DEPENDENCY_REQUIRED')

    def redis_probe(self):
        def request(sock, *parts):
            pieces = [str(p).encode() for p in parts]
            sock.sendall(b'*' + str(len(pieces)).encode() + b'\r\n' + b''.join(
                b'$' + str(len(p)).encode() + b'\r\n' + p + b'\r\n' for p in pieces))
            response = bytearray()
            while not response.endswith(b'\r\n') and len(response) < 4096:
                block = sock.recv(1)
                if not block:
                    break
                response.extend(block)
            return bytes(response)
        with socket.create_connection(('127.0.0.1', self.redis['port']), timeout=3) as sock:
            if (request(sock, 'AUTH', self.redis_password) != b'+OK\r\n'
                    or request(sock, 'SELECT', self.redis['database']) != b'+OK\r\n'
                    or request(sock, 'PING') != b'+PONG\r\n'):
                raise ProtocolError('REDIS_UNAVAILABLE')

    def start_coordinator(self):
        home = self.bundle / 'zookeeper'
        if not list((home / 'lib').glob('zookeeper-*.jar')):
            raise ProtocolError('BUNDLED_COORDINATOR_REQUIRED')
        state = private_directory(self.root / 'zookeeper')
        with socket.socket() as port:
            port.bind(('127.0.0.1', 0))
            number = port.getsockname()[1]
        path = state / 'zoo.cfg'
        path.write_text('tickTime=2000\ndataDir=' + str(state / 'data') + '\nclientPort=' + str(number)
                        + '\nclientPortAddress=127.0.0.1\nadmin.enableServer=false\n4lw.commands.whitelist=ruok\n')
        previous = self.root / 'coordinator-process.json'
        if previous.exists():
            record = strict_json(private_file(previous))
            if any(identity(item['pid']) == item['identity'] for item in record.values()):
                raise ProtocolError('COORDINATOR_RECOVERY_REQUIRED')
        command = [self.java, *self.java_options, '-Xmx128m',
            '-Dlogback.configurationFile=' + str(self.log_config), '-Dzookeeper.admin.enableServer=false',
            '-cp', str(home / 'lib/*'), 'org.apache.zookeeper.server.quorum.QuorumPeerMain', str(path)]
        leased = state / 'coordinator-owned.json'
        atomic_json(leased, {'command': command, 'log': str(state / 'coordinator.log')})
        self.coordinator = JsonChild([sys.executable, str(ROOT / 'hdi_core_network.py'),
                                      '--leased-config', str(leased)], state / 'supervisor.log')
        event = self.coordinator.receive(10)
        if event.get('event') != 'COORDINATOR_STARTED':
            raise ProtocolError('COORDINATOR_START_FAILED')
        child_pid = int(event['pid'])
        # leased-exec becomes the JVM without changing PID; readiness also checks exact socket ownership.
        deadline = time.monotonic() + 60
        while time.monotonic() < deadline:
            if self.coordinator.process.poll() is not None:
                raise ProtocolError('COORDINATOR_START_FAILED')
            # Confirm the listener belongs to this held JVM, not a pre-existing service.
            inodes = set()
            for fd in Path('/proc', str(child_pid), 'fd').iterdir():
                try:
                    target = os.readlink(fd)
                    if target.startswith('socket:['):
                        inodes.add(target[8:-1])
                except FileNotFoundError:
                    pass
            for family in ('tcp', 'tcp6'):
                for line in Path('/proc/net', family).read_text().splitlines()[1:]:
                    fields = line.split()
                    if fields[3] == '0A' and int(fields[1].split(':')[-1], 16) == number and fields[9] in inodes:
                        child_identity = identity(child_pid)
                        if child_identity is None:
                            raise ProtocolError('COORDINATOR_START_FAILED')
                        atomic_json(previous, {'supervisor': self.coordinator.record,
                                               'jvm': {'pid': child_pid, 'identity': child_identity}})
                        self.zk = {'host': '127.0.0.1', 'port': number}
                        return
            time.sleep(.2)
        raise ProtocolError('COORDINATOR_START_TIMEOUT')

    def persist(self, record, **changes):
        with self.guard:
            next_record = {**record, **changes, 'updatedAt': int(time.time() * 1000)}
            atomic_json(self.runs_root / record['runId'] / 'state.json', next_record)
            record.clear()
            record.update(next_record)

    @staticmethod
    def view(record):
        keys = ('runId', 'businessTaskId', 'revision', 'ownerId', 'nativeFlowId', 'nativeInstanceId',
                'execId', 'workspaceId', 'metadataIdBase', 'state', 'finalized', 'recoveryRequired',
                'nativeState', 'errorCode', 'outputsDir', 'authorizedOutputRoot', 'artifacts', 'updatedAt')
        result = {key: record[key] for key in keys if key in record}
        if 'artifacts' in result:
            result['artifacts'] = [{key: item[key] for key in ('artifactId', 'name', 'bytes', 'sha256')}
                                   for item in result['artifacts']]
        return result

    @staticmethod
    def occupies_slot(record):
        return record['state'] != 'RESERVED' and not (
            record['state'] in TERMINAL and record.get('finalized') is True)

    def get(self, request):
        key = run_id(request.get('runId'))
        owner = positive_id(request.get('ownerId'))
        record = self.records.get(key)
        if record is None or record['ownerId'] != owner:
            raise ProtocolError('NOT_FOUND')
        return record

    def reserve(self, request):
        values = reservation(request)
        with self.guard:
            if self.closing:
                raise ProtocolError('WORKER_CLOSING')
            previous = self.records.get(values['runId'])
            if previous:
                if any(previous.get(key) != value for key, value in values.items()):
                    raise ProtocolError('RESERVATION_CONFLICT')
                return self.view(previous)
            def ranges(value):
                return [(int(value[key]), int(value[key])) for key in ('nativeFlowId', 'nativeInstanceId', 'execId')] + [
                    (int(value['metadataIdBase']), int(value['metadataIdBase']) + 8191)]
            proposed = ranges(values)
            if any(a <= d and c <= b for old in self.records.values()
                   for a, b in proposed for c, d in ranges(old)):
                raise ProtocolError('NATIVE_IDS_ALREADY_RESERVED')
            run = self.runs_root / values['runId']
            if run.exists() or run.is_symlink():
                raise ProtocolError('RECOVERY_REQUIRED')
            run.mkdir(mode=0o700)
            for name in ('private', 'logs', 'outputs', 'work', 'fel', 'home', 'tmp', 'store'):
                (run / name).mkdir(mode=0o700)
            record = {**values, 'state': 'RESERVED', 'finalized': False, 'artifacts': [],
                      'outputsDir': str(run / 'outputs'), 'authorizedOutputRoot': str(run),
                      'createdAt': int(time.time() * 1000)}
            atomic_json(run / 'state.json', record)
            self.records[values['runId']] = record
            return self.view(record)

    def submit(self, request):
        with self.guard:
            record = self.get(request)
            encoded, expected = request.get('compiledBase64'), request.get('sha256')
            if not isinstance(encoded, str) or len(encoded) > MAX_COMPILED * 4 // 3 + 8:
                raise ProtocolError('COMPILED_LIMIT')
            try:
                data = base64.b64decode(encoded, validate=True)
            except ValueError:
                raise ProtocolError('INVALID_COMPILED_BASE64') from None
            sha = hashlib.sha256(data).hexdigest()
            if not data or len(data) > MAX_COMPILED or sha != expected:
                raise ProtocolError('COMPILED_HASH_MISMATCH')
            if record['state'] != 'RESERVED':
                if record.get('compiledSha256') != sha:
                    raise ProtocolError('SUBMISSION_CONFLICT')
                return self.view(record)
            if self.closing:
                raise ProtocolError('WORKER_CLOSING')
            active = [r for r in self.records.values() if self.occupies_slot(r)]
            if any(r['ownerId'] == record['ownerId'] for r in active) or len(active) >= self.max_runs:
                raise ProtocolError('WORKER_BUSY')
            compiled = strict_json(data)
            validate_compiled(compiled, record)
            path = self.runs_root / record['runId'] / 'private/compiled.json'
            if path.exists() and hashlib.sha256(private_file(path, MAX_COMPILED)).hexdigest() != sha:
                raise ProtocolError('SUBMISSION_CONFLICT')
            if not path.exists():
                atomic_bytes(path, data)
            self.persist(record, state='PREPARING', compiledSha256=sha)
            task = NativeRun(self, record, compiled)
            self.tasks[record['runId']] = task
            task.thread.start()
            return self.view(record)

    def dispatch(self, request):
        op = request.get('op')
        if op == 'hello':
            with self.guard:
                active = sum(self.occupies_slot(r) for r in self.records.values())
            return {'engine': 'original-detl', 'ready': not self.closing,
                    'capabilities': ['reserve', 'submit', 'status', 'cancel', 'logs', 'artifact', 'shutdown'],
                    'binding': {'id': str(self.config.get('bindingId', '1')), 'name': '平台内置原Core'},
                    'javaRuntimeMode': self.java_mode,
                    'stopTimeoutSeconds': self.stop_timeout, 'shutdownTimeoutSeconds': SHUTDOWN_SECONDS,
                    'capacity': {'limit': self.max_runs, 'active': active, 'available': max(0, self.max_runs - active)},
                    'outputPolicy': {'mode': 'managed', 'logicalRoot': '/平台输出',
                                     'description': '每次写执行器私有运行目录，完整产物通过平台下载'}}
        if op == 'reserve':
            return self.reserve(request)
        if op == 'submit':
            return self.submit(request)
        if op == 'shutdown':
            self.close()
            return {'stopped': True, 'finalized': True}
        with self.guard:
            record = self.get(request)
            if op == 'status':
                return self.view(record)
            if op == 'cancel':
                if record['state'] == 'RESERVED':
                    self.persist(record, state='CANCELLED', finalized=True)
                elif record['state'] not in TERMINAL:
                    if record['state'] == 'UNKNOWN':
                        raise ProtocolError('RECOVERY_REQUIRED')
                    self.tasks[record['runId']].cancel.set()
                return self.view(record)
            if op == 'logs':
                return self.logs(record, request)
            if op == 'artifact':
                return self.artifact(record, request)
            raise ProtocolError('UNKNOWN_OPERATION')

    def logs(self, record, request):
        path = self.runs_root / record['runId'] / 'logs/safe.log'
        cursor, limit = request.get('cursor', 0), request.get('maxBytes', 65536)
        if type(cursor) is not int or cursor < 0 or type(limit) is not int or not 4 <= limit <= 1024 * 1024:
            raise ProtocolError('INVALID_LOG_RANGE')
        if not path.exists():
            if cursor:
                raise ProtocolError('INVALID_LOG_RANGE')
            return {'text': '', 'cursor': 0, 'nextCursor': 0, 'eof': bool(record['finalized'])}
        with path.open('rb') as stream:
            size = os.fstat(stream.fileno()).st_size
            if cursor > size:
                raise ProtocolError('INVALID_LOG_RANGE')
            stream.seek(cursor)
            data = stream.read(limit)
        while data:
            try:
                text = data.decode('utf-8')
                break
            except UnicodeDecodeError as error:
                if error.start == 0:
                    raise ProtocolError('INVALID_LOG_CURSOR') from None
                data = data[:error.start]
        else:
            text = ''
        next_cursor = cursor + len(data)
        return {'text': text, 'cursor': cursor, 'nextCursor': next_cursor,
                'eof': next_cursor == size and bool(record['finalized'])}

    def artifact(self, record, request):
        if record['state'] != 'SUCCEEDED' or record.get('finalized') is not True:
            raise ProtocolError('ARTIFACT_NOT_AVAILABLE')
        item = next((a for a in record['artifacts'] if a['artifactId'] == request.get('artifactId')), None)
        if item is None:
            raise ProtocolError('NOT_FOUND')
        offset, limit = request.get('offset', 0), request.get('maxBytes', 262144)
        if type(offset) is not int or not 0 <= offset <= item['bytes'] or type(limit) is not int or not 1 <= limit <= 1048576:
            raise ProtocolError('INVALID_ARTIFACT_RANGE')
        path = below_output(str(Path(record['outputsDir']) / item['name']), record['outputsDir'])
        fd = os.open(path, os.O_RDONLY | os.O_NOFOLLOW)
        try:
            info = os.fstat(fd)
            if (not stat.S_ISREG(info.st_mode) or info.st_nlink != 1 or info.st_size != item['bytes']
                    or [info.st_dev, info.st_ino, info.st_mtime_ns, info.st_ctime_ns] != item.get('_fileIdentity')):
                raise ProtocolError('ARTIFACT_CHANGED')
            os.lseek(fd, offset, os.SEEK_SET)
            data = os.read(fd, limit)
        finally:
            os.close(fd)
        return {'artifactId': item['artifactId'], 'offset': offset, 'nextOffset': offset + len(data),
                'eof': offset + len(data) == item['bytes'], 'bytes': item['bytes'], 'sha256': item['sha256'],
                'base64': base64.b64encode(data).decode()}

    def close(self):
        if self.closed_result is True:
            return
        if self.closed_result is False:
            raise ProtocolError('STOP_UNCONFIRMED')
        self.closing = True
        deadline = time.monotonic() + SHUTDOWN_SECONDS
        self.shutdown_deadline = deadline - 10
        with self.guard:
            tasks = list(self.tasks.values())
        for task in tasks:
            task.cancel.set()
        for task in tasks:
            task.thread.join(timeout=max(0, self.shutdown_deadline - time.monotonic()))
        unfinished = [task.record['runId'] for task in tasks if task.thread.is_alive() or not task.record.get('finalized')]
        if self.coordinator is not None:
            try:
                self.coordinator.stop(grace=5, deadline=deadline)
            except Exception:
                unfinished.append('bundled-coordinator')
        if unfinished:
            self.closed_result = False
            atomic_json(self.root / '.shutdown-unconfirmed.json', {'runs': unfinished})
            raise ProtocolError('STOP_UNCONFIRMED')
        if self.lock_fd is not None:
            os.close(self.lock_fd)
            self.lock_fd = None
        self.closed_result = True


class NativeRun:
    def __init__(self, worker, record, compiled):
        self.worker, self.record, self.compiled = worker, record, compiled
        self.root = worker.runs_root / record['runId']
        self.cancel = threading.Event()
        self.thread = threading.Thread(target=self.execute, daemon=True)
        self.children, self.bridges = [], []
        self.secrets = [worker.redis_password]
        self.raw_cursor = 0

    def update(self, **changes):
        self.worker.persist(self.record, **changes)

    def original_stop_deadline(self, now):
        deadline = now + self.worker.stop_timeout
        if self.worker.shutdown_deadline is not None:
            # Shutdown is a process ownership deadline shared by all parallel runs.
            deadline = min(deadline, self.worker.shutdown_deadline - 15)
        return deadline

    def output_budget(self):
        total, count = 0, 0
        root = Path(self.record['outputsDir'])
        for path in root.rglob('*'):
            if path.is_symlink():
                raise ProtocolError('OUTPUT_SYMLINK_REJECTED')
            if path.is_file():
                count += 1
                total += path.stat().st_size
                if count > 128 or total > 64 * 1024 * 1024:
                    raise ProtocolError('OUTPUT_LIMIT')

    def log_progress(self, final=False):
        raw, safe = self.root / 'logs/core.log', self.root / 'logs/safe.log'
        if not raw.exists():
            return
        if raw.stat().st_size > 32 * 1024 * 1024:
            raise ProtocolError('LOG_LIMIT')
        with raw.open('rb') as source:
            source.seek(self.raw_cursor)
            data = source.read(32 * 1024 * 1024 if final else 1024 * 1024)
        if not final:
            end = data.rfind(b'\n') + 1
            if not end:
                if len(data) == 1024 * 1024:
                    raise ProtocolError('LOG_LINE_LIMIT')
                return
            data = data[:end]
        self.raw_cursor += len(data)
        text = data.decode('utf-8', errors='replace')
        for secret in sorted(self.secrets, key=len, reverse=True):
            if secret:
                text = text.replace(secret, '<redacted>')
        text = re.sub(r'(?i)(get token error, content is:).*', r'\1 <redacted>', text)
        text = re.sub(r'(?i)((?:password|passwd|secret|token|authorization)["\']?\s*[:=]\s*)[^\s,;]+', r'\1<redacted>', text)
        with safe.open('ab') as output:
            output.write(text.encode('utf-8'))

    def execute(self):
        native = None
        outcome, error, confirmed = 'FAILED', None, True
        socket_dir = None
        budget = None
        try:
            if self.cancel.is_set():
                outcome = 'CANCELLED'
                return
            engine = self.root / 'instance'
            shutil.copytree(self.worker.bundle / 'instance', engine)
            core_log_config = core_logging(self.worker.bundle, self.root)
            password = self.worker.tls['password'] if self.worker.tls else secrets.token_urlsafe(32)
            zk_password = secrets.token_urlsafe(32)
            zk_user = 'run' + secrets.token_hex(8)
            self.secrets += [password, zk_password]
            password_file = self.root / 'private/ssl-password'
            password_file.write_text(password)
            certs = engine / 'certs'
            certs.mkdir(exist_ok=True)
            keytool = str(self.worker.java_home / 'bin/keytool')
            commands = [
                ['-genkeypair', '-alias', 'core', '-keyalg', 'RSA', '-keysize', '2048', '-dname', 'CN=localhost',
                 '-validity', '2', '-storetype', 'JKS', '-keystore', str(certs / 'server.keystore'), '-keypass:file', str(password_file)],
                ['-exportcert', '-alias', 'core', '-keystore', str(certs / 'server.keystore'), '-file', str(certs / 'server.crt')],
                ['-importcert', '-noprompt', '-alias', 'core', '-storetype', 'JKS', '-keystore', str(certs / 'client.keystore'),
                 '-file', str(certs / 'server.crt')],
            ]
            if self.worker.tls:
                (certs / 'server.keystore').write_bytes(self.worker.tls['server'])
                (certs / 'client.keystore').write_bytes(self.worker.tls['client'])
            else:
                with (self.root / 'logs/certificates.log').open('ab') as log:
                    for args in commands:
                        subprocess.run([keytool, *['-J' + value for value in self.worker.java_options], *args, '-storepass:file', str(password_file)],
                                       stdout=log, stderr=log, timeout=60, check=True)
                        if self.cancel.is_set():
                            outcome = 'CANCELLED'
                            return
            namespace = 'rynew-core-' + self.record['ownerId'] + '-' + self.record['runId']
            metadata_config = {'namespace': namespace, 'ownerId': self.record['ownerId'],
                'execId': self.record['execId'], 'flowId': self.record['nativeFlowId'],
                'instanceId': self.record['nativeInstanceId'], 'workspaceId': self.record['workspaceId'],
                'zookeeper': '127.0.0.1:' + str(self.worker.zk['port']), 'zkUser': zk_user, 'zkPassword': zk_password}
            config_path = self.root / 'private/metadata.json'
            atomic_json(config_path, metadata_config)
            cp = ':'.join([str(self.worker.control_jar), str(engine / 'lib/zookeeper-hw-hw-ei.jar'), str(engine / 'lib/*')])
            metadata = JsonChild([self.worker.java, *self.worker.java_options, '-Xmx128m', '-Dlogback.configurationFile=' + str(self.worker.log_config),
                '-cp', cp, 'CoreMetadataBridge', str(config_path)], self.root / 'logs/metadata.log')
            self.children.append(metadata)
            if metadata.receive(30).get('event') != 'METADATA_CONNECTED':
                raise ProtocolError('COORDINATOR_UNAVAILABLE')
            instance = {'id': int(self.record['nativeInstanceId']), 'group_id': int(self.record['workspaceId']),
                'instance_name': 'platform-' + self.record['runId'], 'instance_display_name': '平台独立运行',
                'host': '127.0.0.1', 'port': 2182, 'rpc_port': 2183, 'enable_flag': 1,
                'adapter_root_path': str(engine), 'fel_root_path': str(self.root / 'fel'), 'work_dir': str(self.root / 'work')}
            metadata.call('seed', instance=instance, flowjson=self.compiled['flowjson'])
            with zipfile.ZipFile(engine / 'etl-core-3.3.0.jar') as archive:
                defaults = archive.read('conf/etl_instance.properties').decode('utf-8')
            properties = {'etl.host.name': '127.0.0.1', 'etl.host.port': 2182, 'etl.rpc.host.port': 2183,
                'etl.instance.id': self.record['nativeInstanceId'], 'resource.connect.url': '127.0.0.1:2181',
                'resource.connect.namespace': namespace, 'etl.redis.ip': '127.0.0.1', 'etl.redis.port': 2180,
                'etl.redis.password': self.worker.redis_password, 'etl.redis.database': self.worker.redis['database'],
                'etl.log.dir': self.root / 'logs', 'etl.storage.datadir': self.root / 'store', 'etl.storage.medium': 'diskstore',
                'etl.ssl.serverkeystore': '/certs/server.keystore', 'etl.ssl.clientkeystore': '/certs/client.keystore',
                'etl.ssl.password': password, 'etl.ssl.enabled.protocols': 'TLSv1.2,TLSv1.3', 'etl.proxy.enable': 'false',
                'etl.security.resource.connect.enabled': 'true', 'etl.security.resource.connect.type': 'acl',
                'etl.security.resource.connect.user': zk_user, 'etl.security.resource.connect.password': zk_password}
            (engine / 'conf').mkdir(exist_ok=True)
            def property_value(value):
                # Java properties interpret escapes and trim leading whitespace. Preserve raw credentials.
                return json.dumps(str(value), ensure_ascii=True)[1:-1].replace(' ', '\\ ')
            (engine / 'conf/etl_instance.properties').write_text(defaults + '\n' + '\n'.join(str(k) + '=' + property_value(v) for k, v in properties.items()) + '\n')
            socket_base = private_directory(Path('/tmp') / ('rynew-core-' + str(os.getuid())))
            socket_dir = Path(tempfile.mkdtemp(prefix=self.record['runId'][:8] + '-', dir=socket_base))
            for name, target in [('zk', self.worker.zk['port']), ('redis', self.worker.redis['port'])]:
                self.bridges.append(ByteBridge(socket.AF_UNIX, str(socket_dir / (name + '.sock')),
                                               socket.AF_INET, ('127.0.0.1', target)))
            isolated = {'parentNetworkNamespace': os.readlink('/proc/self/ns/net'),
                'maximumFileBytes': CORE_FILE_LIMIT,
                'dependencies': [{'port': 2181, 'unixSocket': str(socket_dir / 'zk.sock')},
                                 {'port': 2180, 'unixSocket': str(socket_dir / 'redis.sock')}],
                'coreLog': str(self.root / 'logs/core.log'), 'corePorts': [2182, 2183], 'stopGraceSeconds': 10,
                'cwd': str(engine), 'command': [self.worker.java, *self.worker.java_options, '-Xmx256m', '--add-opens=java.base/java.net=ALL-UNNAMED',
                    '-Dlogback.configurationFile=' + str(core_log_config), '-Djava.net.preferIPv4Stack=true',
                    '-Djava.io.tmpdir=' + str(self.root / 'tmp'), '-Duser.home=' + str(self.root / 'home'), '-cp',
                    ':'.join([str(engine), str(engine / 'etl-core-3.3.0.jar'), str(engine / 'lib/*')]),
                    'com.hikvision.idatafusion.etl.core.EtlInstance'],
                'environment': {'PATH': '/usr/bin:/bin', 'HOME': str(self.root / 'home'), 'LANG': 'C.UTF-8',
                                'ETL_USER_DIR': str(engine), 'ETL_SSL_PASSWORD': password}}
            namespace_path = self.root / 'private/namespace.json'
            atomic_json(namespace_path, isolated)
            launched = int(time.time() * 1000)
            self.update(state='STARTING', launchedAt=launched)
            core = JsonChild(['unshare', '--user', '--map-root-user', '--mount-proc', '--net', '--pid', '--fork',
                '--kill-child=SIGKILL', sys.executable, str(ROOT / 'hdi_core_network.py'), '--namespace-config', str(namespace_path)],
                self.root / 'logs/supervisor.log')
            self.children.append(core)
            def halt_for_budget():
                if core.process.poll() is None:
                    # unshare --kill-child=SIGKILL plus the private PID namespace
                    # closes this Core immediately; normal cancellation keeps its grace.
                    core.process.kill()
            budget = RunFileBudget(self.root, halt_for_budget)
            budget.thread.start()
            self.update(process=core.record)
            started = core.receive(15)
            if started.get('event') != 'CORE_STARTED':
                raise ProtocolError('CORE_START_FAILED')
            deadline = time.monotonic() + self.worker.config.get('startupTimeoutSeconds', 120)
            while time.monotonic() < deadline:
                if budget.error:
                    raise ProtocolError(budget.error)
                if self.cancel.is_set():
                    outcome = 'CANCELLED'
                    return
                if core.process.poll() is not None:
                    raise ProtocolError('CORE_EXITED_BEFORE_READY')
                observed = metadata.call('status')
                status = strict_json(observed.get('instance') or '{}')
                pid = strict_json(observed.get('pid') or '{}')
                self.log_progress()
                if status.get('status') == 5:
                    raise ProtocolError('ORIGINAL_CORE_ERROR')
                if (status.get('status') == 3 and str(status.get('id')) == self.record['nativeInstanceId']
                        and status.get('last_update_time', 0) >= launched and observed.get('pidEphemeral') is True
                        and str(pid.get('pid')) == started['pid'] and str(pid.get('instance_id')) == self.record['nativeInstanceId']
                        and pid.get('timestamp', 0) >= launched):
                    break
                time.sleep(.3)
            else:
                raise ProtocolError('CORE_READY_TIMEOUT')
            self.update(state='READY', nativeInstanceState=3)
            if self.cancel.is_set():
                outcome = 'CANCELLED'
                return
            # Persist before the only schedule publish. Any ambiguous response stops, never retries.
            self.update(state='SUBMITTING')
            metadata.call('submit', schedule=self.compiled['schedule'])
            self.update(state='RUNNING')
            deadline = time.monotonic() + self.worker.config.get('executionTimeoutSeconds', 300)
            stop_deadline = None
            while True:
                if budget.error:
                    raise ProtocolError(budget.error)
                if self.cancel.is_set() and stop_deadline is None:
                    self.update(state='STOPPING')
                    metadata.call('stop', stop=self.compiled['stop'])
                    stop_deadline = self.original_stop_deadline(time.monotonic())
                if stop_deadline is not None and self.worker.shutdown_deadline is not None:
                    stop_deadline = min(stop_deadline, self.worker.shutdown_deadline - 15)
                observed = metadata.call('status')
                value = strict_json(observed.get('execution') or '{}')
                self.log_progress()
                self.output_budget()
                if value.get('exec_id') == self.record['execId']:
                    self.update(nativeState=value.get('status'))
                    if value.get('status') in ('finish', 'error', 'stop'):
                        native = value
                        outcome = {'finish': 'SUCCEEDED', 'error': 'FAILED', 'stop': 'CANCELLED'}[value['status']]
                        break
                if core.process.poll() is not None:
                    raise ProtocolError('CORE_EXITED_DURING_RUN')
                if stop_deadline is not None and time.monotonic() >= stop_deadline:
                    outcome, error = 'CANCELLED', 'ORIGINAL_STOP_TIMEOUT_PROCESS_CLOSED'
                    break
                if time.monotonic() >= deadline:
                    outcome, error = 'TIMED_OUT', 'EXECUTION_TIMEOUT'
                    break
                time.sleep(.3)
        except Exception as failure:
            error = failure.code if isinstance(failure, ProtocolError) else type(failure).__name__
            if self.record['state'] in ('SUBMITTING', 'RUNNING', 'STOPPING'):
                outcome = 'INTERRUPTED'
        finally:
            cleanup_deadline = time.monotonic() + 25
            if self.worker.shutdown_deadline is not None:
                cleanup_deadline = min(cleanup_deadline, self.worker.shutdown_deadline)
            for child in reversed(self.children):
                try:
                    child.stop(deadline=cleanup_deadline - 3)
                except Exception:
                    confirmed = False
            for bridge in reversed(self.bridges):
                try:
                    bridge.close(deadline=cleanup_deadline)
                except Exception:
                    confirmed = False
            if socket_dir:
                try:
                    socket_dir.rmdir()
                except OSError:
                    pass
            artifacts = []
            try:
                self.log_progress(final=True)
            except Exception:
                if confirmed:
                    outcome, error = 'FAILED', 'LOG_LIMIT'
            if budget is not None:
                try:
                    budget.check()  # Include the terminal/close window and final safe-log append.
                except Exception as failure:
                    budget.error = failure.code if isinstance(failure, ProtocolError) else 'RUNTIME_BUDGET_UNAVAILABLE'
                try:
                    budget.close(cleanup_deadline)
                except Exception:
                    confirmed = False
                if budget.error:
                    outcome, error = 'FAILED', budget.error
            if outcome == 'SUCCEEDED' and confirmed:
                try:
                    artifacts = results_manifest(self.record['outputsDir'])
                except Exception:
                    outcome, error = 'FAILED', 'OUTPUT_VALIDATION_FAILED'
            try:
                if not confirmed:
                    outcome, error = 'UNKNOWN', 'STOP_UNCONFIRMED'
                self.update(state=outcome, finalized=confirmed, errorCode=error,
                            nativeState=native.get('status') if native else self.record.get('nativeState'),
                            artifacts=artifacts, recoveryRequired=not confirmed)
            except Exception:
                # Retain the previous durable intent. Do not pretend a failed final write completed.
                with self.worker.guard:
                    self.record.update(state='UNKNOWN', finalized=False, recoveryRequired=True, errorCode='FINALIZATION_FAILED')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--config', type=Path, required=True)
    args = parser.parse_args()
    worker = None
    try:
        worker = Worker(strict_json(private_file(args.config)))
        def terminate(_signal, _frame):
            if not worker.closing:
                raise KeyboardInterrupt()
        signal.signal(signal.SIGTERM, terminate)
        signal.signal(signal.SIGINT, terminate)
        while not worker.closing:
            raw = sys.stdin.buffer.readline(MAX_LINE + 1)
            if not raw:
                break
            response = {'protocolVersion': 1, 'requestId': None}
            try:
                if len(raw) > MAX_LINE:
                    raise ProtocolError('REQUEST_LIMIT')
                request = strict_json(raw)
                correlation = request.get('requestId')
                if not isinstance(correlation, str) or not re.fullmatch(r'[A-Za-z0-9_-]{1,128}', correlation):
                    raise ProtocolError('INVALID_REQUEST_ID')
                response['requestId'] = correlation
                if request.get('protocolVersion') != 1:
                    raise ProtocolError('PROTOCOL_VERSION')
                response.update(ok=True, data=worker.dispatch(request))
            except Exception as failure:
                response.update(ok=False, error={'code': failure.code if isinstance(failure, ProtocolError) else 'INTERNAL_ERROR'})
            print(json.dumps(response, ensure_ascii=False, separators=(',', ':')), flush=True)
            if len(raw) > MAX_LINE:
                break
    except KeyboardInterrupt:
        pass
    except Exception as failure:
        print(json.dumps({'event': 'WORKER_UNAVAILABLE', 'error': {'code': failure.code if isinstance(failure, ProtocolError) else type(failure).__name__}}), file=sys.stderr)
        return 2
    finally:
        if worker is not None:
            worker.close()
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
