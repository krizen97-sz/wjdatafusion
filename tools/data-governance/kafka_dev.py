#!/usr/bin/env python3
"""Manage only the owned Kafka fixture on 127.0.0.1:19092/19093; no reset command."""
import argparse
import base64
from contextlib import contextmanager
from datetime import datetime, timezone
import fcntl
import hashlib
import json
import os
from pathlib import Path
import re
import signal
import socket
import ssl
import stat
import subprocess
import sys
import tarfile
import time
import urllib.parse
import urllib.request
import uuid

OWNER = 'rynew-data-governance-kafka'
PARENT_OWNER = 'rynew-data-governance-runtime'
BROKER_PORT, CONTROLLER_PORT = 19092, 19093
SCRIPT_ROOT = Path(__file__).resolve().parent
CHILDREN = {}


def require(condition, message):
    if not condition:
        raise RuntimeError(message)


def read_json(path):
    require(path.is_file() and not path.is_symlink() and path.stat().st_size <= 4 * 1024 * 1024, 'Invalid owned metadata file')
    require(stat.S_IMODE(path.stat().st_mode) == 0o600, 'Owned metadata file must have mode 600')
    return json.loads(path.read_text())


def secure_write(path, content):
    require(not path.is_symlink() and not path.parent.is_symlink(), 'Owned output cannot be a symlink')
    path.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
    temporary = path.with_name('.' + path.name + '.' + uuid.uuid4().hex + '.tmp')
    fd = os.open(temporary, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    try:
        with os.fdopen(fd, 'wb') as stream:
            stream.write(content if isinstance(content, bytes) else content.encode())
            stream.flush()
            os.fsync(stream.fileno())
        os.replace(temporary, path)
    finally:
        if temporary.exists():
            temporary.unlink()


def save_json(path, value):
    secure_write(path, json.dumps(value, ensure_ascii=False, indent=2) + '\n')


def file_hash(path, algorithm='sha256'):
    digest = hashlib.new(algorithm)
    with path.open('rb') as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b''):
            digest.update(block)
    return digest.hexdigest()


def owned_runtime(value):
    supplied = Path(value).expanduser().absolute()
    root = supplied.resolve()
    require(not supplied.is_symlink() and root not in {Path('/'), Path.home()}, 'Unsafe runtime root')
    parent = read_json(root / 'runtime.json')
    require(parent.get('owner') == PARENT_OWNER and parent.get('runtimeRoot') == str(root), 'Parent runtime ownership mismatch')
    java = Path(parent['javaHome']).resolve()
    require(java.is_relative_to(root) and (java / 'bin/java').is_file() and (java / 'bin/javac').is_file(), 'JDK is outside the owned runtime')
    require(re.search(r'^JAVA_VERSION="21(?:\.|["+])', (java / 'release').read_text(), re.M), 'Owned Java 21 is required')
    home = root / 'kafka'
    require(not home.is_symlink(), 'Kafka directory cannot be a symlink')
    if not home.exists():
        home.mkdir(mode=0o700)
        save_json(home / 'owner.json', {'owner': OWNER, 'runtimeRoot': str(root), 'serviceRoot': str(home), 'instanceId': uuid.uuid4().hex})
    marker = read_json(home / 'owner.json')
    require(marker.get('owner') == OWNER and marker.get('runtimeRoot') == str(root) and marker.get('serviceRoot') == str(home)
            and re.fullmatch('[0-9a-f]{32}', marker.get('instanceId', '')), 'Kafka ownership mismatch; existing directory is not adopted')
    require(stat.S_IMODE(home.stat().st_mode) == 0o700, 'Kafka root must have mode 700')
    for relative in ['downloads', 'apps', 'private', 'tools', 'tools/classes', 'evidence', 'data']:
        component = home / relative
        if component.exists() or component.is_symlink():
            require(component.is_dir() and not component.is_symlink() and component.resolve().is_relative_to(home), 'Kafka component directory escapes the owned root')
    return root, home, java, marker


@contextmanager
def control_lock(home):
    path = home / '.control.lock'
    require(not path.is_symlink(), 'Control lock cannot be a symlink')
    fd = os.open(path, os.O_RDWR | os.O_CREAT, 0o600)
    try:
        try:
            fcntl.flock(fd, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError:
            raise RuntimeError('Another Kafka control operation is active') from None
        yield
    finally:
        os.close(fd)


def process_identity(pid):
    if not isinstance(pid, int) or pid <= 1:
        return None
    if pid in CHILDREN and CHILDREN[pid].poll() is not None:
        CHILDREN.pop(pid, None)
        return None
    state = subprocess.run(['/bin/ps', '-p', str(pid), '-o', 'stat='], capture_output=True, text=True)
    if state.returncode != 0 or state.stdout.strip().startswith('Z'):
        return None
    result = subprocess.run(['/bin/ps', '-p', str(pid), '-o', 'lstart=', '-o', 'command='], capture_output=True, text=True)
    return result.stdout.strip() if result.returncode == 0 else None


def assert_owned_process(record, home, marker):
    identity = process_identity(record.get('pid'))
    if identity is None:
        return False
    token = '-Drynew.governance.kafka=' + marker['instanceId']
    require(identity == record.get('identity') and str(home) in identity and token in identity and ' kafka.Kafka ' in identity,
            'PID identity changed; refusing to signal an unknown process')
    return True


def free_port(port):
    result = subprocess.run(['/usr/sbin/lsof', '-nP', '-iTCP:' + str(port), '-sTCP:LISTEN', '-Fp'], capture_output=True, text=True)
    require(result.returncode in {0, 1}, 'Unable to inspect TCP port ownership')
    require(not result.stdout.strip(), 'Required TCP port already has a listener; no process will be killed')
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as listener:
        try:
            listener.bind(('127.0.0.1', port))
        except OSError:
            raise RuntimeError('Required loopback port is already occupied; no process will be killed') from None


def listeners(pid):
    result = subprocess.run(['/usr/sbin/lsof', '-nP', '-a', '-p', str(pid), '-iTCP', '-sTCP:LISTEN', '-Fn'], capture_output=True, text=True)
    require(result.returncode in {0, 1}, 'Unable to inspect Kafka listener ownership')
    return sorted(line[1:] for line in result.stdout.splitlines() if line.startswith('n'))


def child_environment(java):
    env = os.environ.copy()
    for key in ['JAVA_TOOL_OPTIONS', '_JAVA_OPTIONS', 'JDK_JAVA_OPTIONS', 'CLASSPATH', 'KAFKA_OPTS', 'KAFKA_HEAP_OPTS',
                'KAFKA_JVM_PERFORMANCE_OPTS', 'JMX_PORT', 'LD_PRELOAD', 'DYLD_INSERT_LIBRARIES']:
        env.pop(key, None)
    env['JAVA_HOME'] = str(java)
    return env


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, request, fp, code, msg, headers, newurl):
        raise RuntimeError('Artifact redirect refused; update the official lock explicitly')


def official_url(url):
    parsed = urllib.parse.urlsplit(url)
    require(parsed.scheme == 'https' and parsed.hostname == 'downloads.apache.org' and parsed.port in {None, 443}
            and not parsed.username and not parsed.password and not parsed.query and not parsed.fragment and parsed.path.startswith('/kafka/'), 'Only locked official HTTPS Kafka downloads are allowed')
    return url


def download(url, path, limit):
    official_url(url)
    if path.exists():
        require(path.is_file() and not path.is_symlink() and path.stat().st_size <= limit, 'Invalid cached download')
        return
    path.parent.mkdir(mode=0o700, exist_ok=True)
    # Use the existing OS public CA bundle if this Python distribution has no installed default bundle.
    bundle = Path('/etc/ssl/cert.pem')
    context = ssl.create_default_context(cafile=str(bundle) if bundle.is_file() else None)
    opener = urllib.request.build_opener(NoRedirect(), urllib.request.HTTPSHandler(context=context))
    temporary = path.with_name(path.name + '.part-' + uuid.uuid4().hex)
    fd = os.open(temporary, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    try:
        with os.fdopen(fd, 'wb') as stream, opener.open(url, timeout=30) as response:
            require(response.status == 200, 'Official download was not successful')
            count = 0
            while block := response.read(1024 * 1024):
                count += len(block)
                require(count <= limit, 'Official download exceeds locked size bound')
                stream.write(block)
            stream.flush()
            os.fsync(stream.fileno())
        os.replace(temporary, path)
    finally:
        if temporary.exists():
            temporary.unlink()


def verify_archive(root, home, lock):
    downloads = home / 'downloads'
    download(lock['url'], downloads / lock['filename'], 256 * 1024 * 1024)
    download(lock['sha512_url'], downloads / (lock['filename'] + '.sha512'), 16384)
    download(lock['signature_url'], downloads / (lock['filename'] + '.asc'), 16384)
    download(lock['keys_url'], downloads / 'KEYS', 4 * 1024 * 1024)
    checksum_text = (downloads / (lock['filename'] + '.sha512')).read_text()
    words = re.findall(r'\b[0-9a-fA-F]{8}\b', checksum_text)
    official = ''.join(words).lower() if len(words) == 16 else next(iter(re.findall(r'\b[0-9a-fA-F]{128}\b', checksum_text)), '').lower()
    archive = downloads / lock['filename']
    actual = file_hash(archive, 'sha512')
    require(official == lock['sha512'] == actual, 'Official/pinned archive SHA512 mismatch; execution refused')
    python = root / 'tools/pgp-venv/bin/python'
    require(python.is_file(), 'Existing isolated PGPy verification environment is required')
    verification = r'''
import json,re,sys,warnings
from pathlib import Path
from pgpy import PGPKey,PGPSignature
archive,signature,keys,fingerprint=sys.argv[1:]
with warnings.catch_warnings(record=True) as notices:
 warnings.simplefilter('always')
 sig=PGPSignature.from_file(signature)
 signer=None
 for block in re.findall(r'-----BEGIN PGP PUBLIC KEY BLOCK-----.*?-----END PGP PUBLIC KEY BLOCK-----',Path(keys).read_text(),re.S):
  key,_=PGPKey.from_blob(block)
  if str(key.fingerprint)==fingerprint:signer=key;break
 if signer is None or not bool(signer.verify(Path(archive).read_bytes(),sig)):raise SystemExit(2)
 print(json.dumps({'signatureVerified':True,'signerFingerprint':fingerprint,'verificationWarningCount':len(notices)}))
'''
    result = subprocess.run([str(python), '-c', verification, str(archive), str(downloads / (lock['filename'] + '.asc')), str(downloads / 'KEYS'), lock['signer_fingerprint']], capture_output=True, text=True, timeout=60)
    require(result.returncode == 0, 'Pinned official detached signature verification failed')
    evidence = {'version': lock['version'], 'filename': lock['filename'], 'byteSize': archive.stat().st_size, 'sha512': actual,
                'officialChecksumMatched': True, 'checkedAt': datetime.now(timezone.utc).isoformat(), **json.loads(result.stdout),
                'trustBoundary': 'Official HTTPS-published key with pinned fingerprint; no independent web-of-trust or revocation claim.'}
    save_json(home / 'evidence/artifact-verification.json', evidence)
    return archive


def validate_tar(members, prefix):
    require(len(members) <= 4000 and sum(member.size for member in members) <= 512 * 1024 * 1024, 'Archive extraction bound exceeded')
    names = set()
    for member in members:
        path = Path(member.name)
        require(not path.is_absolute() and '..' not in path.parts and path.parts and path.parts[0] == prefix and '\\' not in member.name, 'Archive path escapes locked directory')
        require(member.isdir() or member.isfile(), 'Archive links or special files are refused')
        require(member.name not in names, 'Duplicate archive path')
        names.add(member.name)


def inventory(directory):
    result = {}
    require(directory.is_dir() and not directory.is_symlink(), 'Kafka installation is missing or linked')
    for path in sorted(directory.rglob('*')):
        require(not path.is_symlink(), 'Kafka installation contains a link')
        if path.is_file():
            result[str(path.relative_to(directory))] = file_hash(path)
    return result


def install(home, archive, lock):
    target = home / 'apps' / lock['directory']
    manifest = home / 'installation.json'
    if target.exists():
        installed = read_json(manifest)
        require(installed['version'] == lock['version'] and installed['archiveSha512'] == lock['sha512'] and installed['files'] == inventory(target), 'Installed Kafka differs from verified artifact')
        return target
    parent = target.parent
    parent.mkdir(mode=0o700, exist_ok=True)
    staging = parent / ('.extract-' + uuid.uuid4().hex)
    staging.mkdir(mode=0o700)
    with tarfile.open(archive, 'r:gz') as tar:
        members = tar.getmembers()
        validate_tar(members, lock['directory'])
        tar.extractall(staging, members=members, filter='data')
    os.replace(staging / lock['directory'], target)
    staging.rmdir()
    save_json(manifest, {'version': lock['version'], 'archiveSha512': lock['sha512'], 'files': inventory(target)})
    return target


def config_text(home):
    values = {'process.roles': 'broker,controller', 'node.id': '1',
              'listeners': 'PLAINTEXT://127.0.0.1:19092,CONTROLLER://127.0.0.1:19093',
              'advertised.listeners': 'PLAINTEXT://127.0.0.1:19092', 'controller.quorum.bootstrap.servers': '127.0.0.1:19093',
              'listener.security.protocol.map': 'PLAINTEXT:PLAINTEXT,CONTROLLER:PLAINTEXT', 'controller.listener.names': 'CONTROLLER', 'inter.broker.listener.name': 'PLAINTEXT',
              'log.dirs': str(home / 'data'), 'num.network.threads': '2', 'num.io.threads': '2', 'background.threads': '2', 'num.replica.fetchers': '1',
              'num.recovery.threads.per.data.dir': '1', 'num.partitions': '2', 'default.replication.factor': '1', 'min.insync.replicas': '1',
              'offsets.topic.replication.factor': '1', 'offsets.topic.num.partitions': '2', 'transaction.state.log.replication.factor': '1', 'transaction.state.log.min.isr': '1',
              'group.initial.rebalance.delay.ms': '0', 'auto.create.topics.enable': 'false', 'log.retention.hours': '168', 'log.segment.bytes': '16777216',
              'log.index.size.max.bytes': '1048576', 'message.max.bytes': '1048576', 'socket.request.max.bytes': '2097152', 'replica.fetch.max.bytes': '2097152'}
    return '\n'.join(key + '=' + value for key, value in sorted(values.items())) + '\n'


def validate_config(home):
    path = home / 'private/server.properties'
    require(path.is_file() and not path.is_symlink() and path.read_text() == config_text(home), 'Kafka config changed; refusing unapproved listeners, directories or options')


def java_command(java, home, kafka_home, marker, main, args=()):
    classpath = str(kafka_home / 'libs/*')
    if main == 'KafkaDevProbe':
        classpath = str(home / 'tools/classes') + os.pathsep + classpath
    return [str(java / 'bin/java'), '-Xms128m', '-Xmx384m', '-XX:MaxDirectMemorySize=128m', '-XX:ActiveProcessorCount=2',
            '-Drynew.governance.kafka=' + marker['instanceId'], '-Dlog4j2.configurationFile=' + str(home / 'private/log4j2.properties'),
            '-cp', classpath, main, *args]


def execute(java, home, command, name, timeout=30):
    result = subprocess.run(command, cwd=home, env=child_environment(java), capture_output=True, timeout=timeout)
    secure_write(home / 'private' / (name + '.log'), result.stdout + result.stderr)
    require(result.returncode == 0, name + ' failed; inspect the owned private log')
    return result.stdout.decode('utf-8')


def validate_data(home, marker, permit_new=False):
    data = home / 'data'
    require(not data.is_symlink() and data.resolve().parent == home.resolve(), 'Kafka data path escapes owned directory')
    cluster = marker.get('clusterId')
    require(isinstance(cluster, str) and re.fullmatch('[A-Za-z0-9_-]{22}', cluster), 'Owned cluster ID missing')
    if not data.exists():
        require(permit_new and not marker.get('formatted'), 'Existing Kafka data directory is missing; automatic reformat refused')
        data.mkdir(mode=0o700)
    if not list(data.iterdir()):
        require(permit_new and not marker.get('formatted'), 'Existing cluster is empty; automatic reformat refused')
        return False
    metadata = data / 'meta.properties'
    require(metadata.is_file() and not metadata.is_symlink(), 'Nonempty data without metadata is never formatted')
    props = dict(line.split('=', 1) for line in metadata.read_text().splitlines() if '=' in line and not line.lstrip().startswith('#'))
    require(props.get('cluster.id') == cluster and props.get('node.id') == '1', 'Kafka cluster or node identity differs')
    return True


def prepare(root, home, java, marker, lock):
    archive = verify_archive(root, home, lock)
    kafka_home = install(home, archive, lock)
    config = home / 'private/server.properties'
    if config.exists():
        validate_config(home)
    else:
        secure_write(config, config_text(home))
    secure_write(home / 'private/log4j2.properties', 'status=error\nname=KafkaDev\nappender.console.type=Console\nappender.console.name=STDERR\nappender.console.target=SYSTEM_ERR\nappender.console.layout.type=PatternLayout\nappender.console.layout.pattern=%d{ISO8601} %-5p %c - %m%n\nrootLogger.level=warn\nrootLogger.appenderRef.stderr.ref=STDERR\n')
    source = SCRIPT_ROOT / 'KafkaDevProbe.java'
    copied = home / 'tools/KafkaDevProbe.java'
    secure_write(copied, source.read_bytes())
    (home / 'tools/classes').mkdir(mode=0o700, exist_ok=True)
    execute(java, home, [str(java / 'bin/javac'), '-proc:none', '-cp', str(kafka_home / 'libs/*'), '-d', str(home / 'tools/classes'), str(copied)], 'compile-probe')
    save_json(home / 'probe.json', {'sourceSha256': file_hash(copied), 'files': inventory(home / 'tools/classes')})
    if 'clusterId' not in marker:
        require(not (home / 'data').exists(), 'Pre-existing data is not adopted when creating cluster identity')
        marker['clusterId'] = base64.urlsafe_b64encode(uuid.uuid4().bytes).decode().rstrip('=')
        marker['formatted'] = False
        save_json(home / 'owner.json', marker)
    if not validate_data(home, marker, permit_new=True):
        execute(java, home, java_command(java, home, kafka_home, marker, 'kafka.tools.StorageTool', ['format', '--standalone', '--cluster-id', marker['clusterId'], '--config', str(config)]), 'format-new-storage')
        require(validate_data(home, marker), 'Formatted Kafka metadata was not confirmed')
    marker['formatted'] = True
    marker['version'] = lock['version']
    save_json(home / 'owner.json', marker)
    save_json(home / 'evidence/prepared.json', {'version': lock['version'], 'clusterId': marker['clusterId'], 'configSha256': file_hash(config), 'probeSourceSha256': file_hash(copied), 'loopbackOnly': True})
    return {'prepared': True, 'version': lock['version'], 'clusterId': marker['clusterId'], 'dataPreserved': True}


def probe(home, java, marker, lock, action='health', run_id=None):
    process = read_json(home / 'process.json')
    require(assert_owned_process(process, home, marker), 'No live owned Kafka process')
    bound = listeners(process['pid'])
    require(bound == ['127.0.0.1:19092', '127.0.0.1:19093'], 'Kafka does not have exactly the two permitted loopback listeners')
    validate_config(home)
    validate_data(home, marker)
    expected_probe = read_json(home / 'probe.json')
    require(expected_probe['sourceSha256'] == file_hash(home / 'tools/KafkaDevProbe.java') and expected_probe['files'] == inventory(home / 'tools/classes'), 'Compiled Kafka probe was modified')
    installed = read_json(home / 'installation.json')
    require(installed['version'] == lock['version'] and installed['archiveSha512'] == lock['sha512'] and installed['files'] == inventory(home / 'apps' / lock['directory']), 'Kafka probe dependencies were modified')
    args = [action, marker['clusterId']] + ([run_id] if run_id else [])
    output = execute(java, home, java_command(java, home, home / 'apps' / lock['directory'], marker, 'KafkaDevProbe', args), 'probe-' + action, timeout=55)
    result = json.loads(output)
    result['listeners'] = bound
    return result


def start(home, java, marker, lock):
    path = home / 'process.json'
    if path.exists() and assert_owned_process(read_json(path), home, marker):
        return {'started': False, 'alreadyRunning': True, 'pid': read_json(path)['pid'], 'health': probe(home, java, marker, lock)}
    validate_config(home)
    validate_data(home, marker)
    installation = read_json(home / 'installation.json')
    require(installation['archiveSha512'] == lock['sha512'] and installation['files'] == inventory(home / 'apps' / lock['directory']), 'Kafka installation was modified')
    for port in [BROKER_PORT, CONTROLLER_PORT]:
        free_port(port)
    command = java_command(java, home, home / 'apps' / lock['directory'], marker, 'kafka.Kafka', [str(home / 'private/server.properties')])
    log = home / 'private/server.log'
    fd = os.open(log, os.O_WRONLY | os.O_CREAT | os.O_APPEND, 0o600)
    with os.fdopen(fd, 'ab') as output:
        process = subprocess.Popen(command, cwd=home, env=child_environment(java), stdout=output, stderr=output, start_new_session=True)
    CHILDREN[process.pid] = process
    time.sleep(0.2)
    identity = process_identity(process.pid)
    require(identity and ' kafka.Kafka ' in identity and '-Drynew.governance.kafka=' + marker['instanceId'] in identity, 'Kafka did not start with the expected Java identity')
    save_json(path, {'pid': process.pid, 'identity': identity, 'instanceId': marker['instanceId'], 'startedAt': datetime.now(timezone.utc).isoformat()})
    deadline = time.monotonic() + 45
    while time.monotonic() < deadline:
        require(assert_owned_process(read_json(path), home, marker), 'Owned Kafka exited during startup')
        try:
            healthy = probe(home, java, marker, lock)
            return {'started': True, 'pid': process.pid, 'health': healthy}
        except (RuntimeError, subprocess.TimeoutExpired):
            time.sleep(0.5)
    raise RuntimeError('Kafka health was not confirmed; the owned process was left for inspection')


def stop(home, marker):
    path = home / 'process.json'
    if not path.exists():
        return {'stopped': False, 'reason': 'no-owned-process-record'}
    record = read_json(path)
    if not assert_owned_process(record, home, marker):
        return {'stopped': False, 'reason': 'owned-process-not-running'}
    # Revalidate immediately before the only signal. Never search-and-kill by port or executable name.
    require(assert_owned_process(record, home, marker), 'Owned process exited before signal')
    os.kill(record['pid'], signal.SIGTERM)
    deadline = time.monotonic() + 40
    while time.monotonic() < deadline:
        if not assert_owned_process(record, home, marker):
            save_json(home / 'evidence/last-stop.json', {'pid': record['pid'], 'stoppedAt': datetime.now(timezone.utc).isoformat(), 'signal': 'SIGTERM', 'dataDeleted': False})
            return {'stopped': True, 'pid': record['pid'], 'dataPreserved': True}
        time.sleep(0.2)
    raise RuntimeError('Owned Kafka has not stopped; no forced or unknown-process kill was attempted')


def status(home, marker):
    path = home / 'process.json'
    if not path.exists():
        return {'running': False, 'version': marker.get('version'), 'broker': '127.0.0.1:19092', 'controller': '127.0.0.1:19093'}
    record = read_json(path)
    return {'running': assert_owned_process(record, home, marker), 'pid': record['pid'], 'version': marker.get('version'), 'broker': '127.0.0.1:19092', 'controller': '127.0.0.1:19093'}


def smoke(home, java, marker, lock):
    before = start(home, java, marker, lock)
    run_id = uuid.uuid4().hex
    seeded = probe(home, java, marker, lock, 'seed', run_id)
    stopped = stop(home, marker)
    require(stopped.get('stopped'), 'Owned Kafka restart stop was not confirmed')
    after = start(home, java, marker, lock)
    verified = probe(home, java, marker, lock, 'verify', run_id)
    require(before['pid'] != after['pid'], 'Restart did not create a new process')
    result = {'version': lock['version'], 'runId': run_id, 'clusterId': marker['clusterId'], 'oldPid': before['pid'], 'pid': after['pid'], 'brokerRestarted': True,
              'seed': seeded, 'afterRestart': verified, 'originalBrokersContacted': False, 'syntheticOnly': True, 'checkedAt': datetime.now(timezone.utc).isoformat()}
    save_json(home / 'evidence' / ('smoke-' + run_id + '.json'), result)
    save_json(home / 'evidence/latest-smoke.json', result)
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('action', choices=['prepare', 'start', 'stop', 'status', 'health', 'smoke'])
    parser.add_argument('--runtime-root', type=Path, required=True)
    args = parser.parse_args()
    root, home, java, marker = owned_runtime(args.runtime_root)
    lock = json.loads((SCRIPT_ROOT / 'kafka-artifact-lock.json').read_text())
    if args.action == 'status':
        result = status(home, marker)
    elif args.action == 'health':
        require(status(home, marker)['running'], 'No live owned Kafka process')
        result = probe(home, java, marker, lock)
    else:
        with control_lock(home):
            if args.action == 'prepare':
                result = prepare(root, home, java, marker, lock)
            elif args.action == 'start':
                result = start(home, java, marker, lock)
            elif args.action == 'stop':
                result = stop(home, marker)
            else:
                result = smoke(home, java, marker, lock)
    print(json.dumps(result, ensure_ascii=False, indent=2))


if __name__ == '__main__':
    try:
        main()
    except Exception as error:
        print(json.dumps({'success': False, 'errorType': type(error).__name__, 'message': str(error) if isinstance(error, RuntimeError) else 'Kafka runtime operation failed; inspect owned private evidence'}, ensure_ascii=False), file=sys.stderr)
        sys.exit(1)
