#!/usr/bin/env python3
"""Linux-only, opt-in Docker launcher for the unchanged KettleWorker NDJSON protocol.

Importing or planning never calls Docker. Live execution requires reviewed private configuration.
"""
import argparse
from contextlib import contextmanager
from dataclasses import dataclass
import hashlib
import ipaddress
import json
import os
from pathlib import Path
import re
import secrets
import shlex
import stat
import subprocess
import sys
import threading
import time
import uuid

LINUX_ASSETS = Path(__file__).resolve().parents[2] / 'data-governance/kettle-worker/linux'
IMAGE = json.loads((LINUX_ASSETS / 'image-lock.json').read_text())
LABEL = 'io.rynew.kettle.'
OPERATIONS = {'run', 'validate', 'capabilities', 'job', 'job-validate'}


def require(condition, message):
    if not condition: raise RuntimeError(message)


def digest(path):
    h = hashlib.sha256()
    with Path(path).open('rb') as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b''): h.update(block)
    return h.hexdigest()


def path_checked(value, *, directory=True, exists=True):
    path = Path(value).absolute()
    require(not any(c in str(path) for c in '\x00\r\n,'), 'Path contains Docker mount delimiters or control characters')
    require(all(not p.is_symlink() for p in [path, *path.parents]), 'Symbolic link path is not accepted')
    if exists: require(path.is_dir() if directory else path.is_file(), 'Required path is missing')
    return path.resolve()


def private_read(path):
    fd = os.open(path, os.O_RDONLY | os.O_NOFOLLOW)
    with os.fdopen(fd) as stream:
        info = os.fstat(stream.fileno())
        require(stat.S_ISREG(info.st_mode) and stat.S_IMODE(info.st_mode) == 0o600 and info.st_uid == os.geteuid(),
                'Configuration/journal must be a regular mode-600 file owned by the controller')
        require(info.st_size <= 4 * 1024 * 1024, 'Private configuration/journal is too large')
        return json.load(stream)


def atomic_write(path, content, mode=0o600):
    path = Path(path); require(not path.is_symlink(), 'Refusing linked output')
    temporary = path.with_name('.' + path.name + '.' + uuid.uuid4().hex)
    fd = os.open(temporary, os.O_WRONLY | os.O_CREAT | os.O_EXCL, mode)
    try:
        with os.fdopen(fd, 'w') as stream:
            stream.write(content); stream.flush(); os.fsync(stream.fileno())
        os.replace(temporary, path)
        parent = os.open(path.parent, os.O_RDONLY)
        try: os.fsync(parent)
        finally: os.close(parent)
    finally:
        if temporary.exists(): temporary.unlink()


@dataclass(frozen=True)
class Config:
    instance_id: str
    worker_root: Path
    operations_root: Path
    state_root: Path
    uid: int
    gid: int
    execution_enabled: bool = False
    endpoints: tuple = ()
    subnet_pool: str = '172.30.0.0/16'
    memory_mb: int = 512
    cpus: float = 1.0
    pids_limit: int = 256
    file_size_mb: int = 64
    image: str = IMAGE['image']
    platform: str = IMAGE['platform']
    docker: str = '/usr/bin/docker'
    iptables: str = '/usr/sbin/iptables'
    ip6tables: str = '/usr/sbin/ip6tables'
    nsenter: str = '/usr/bin/nsenter'

    def __post_init__(self):
        require(bool(re.fullmatch(r'[a-f0-9]{32}', self.instance_id)), 'instance_id must be a stable random 32-hex identifier')
        require(type(self.uid) is int and 1 <= self.uid <= 65534 and type(self.gid) is int and 1 <= self.gid <= 65534,
                'Container must use an explicit non-root uid/gid')
        require(type(self.execution_enabled) is bool, 'execution_enabled must be boolean')
        require(self.image == IMAGE['image'] and self.platform == IMAGE['platform'], 'Only the reviewed digest/platform is supported')
        require(type(self.memory_mb) is int and 256 <= self.memory_mb <= 4096, 'Memory limit outside supported range')
        require(type(self.cpus) in (float, int) and 0.25 <= self.cpus <= 4, 'CPU limit outside supported range')
        require(type(self.pids_limit) is int and 64 <= self.pids_limit <= 1024, 'PID limit outside supported range')
        require(type(self.file_size_mb) is int and 1 <= self.file_size_mb <= 256, 'File size limit outside supported range')
        pool = ipaddress.IPv4Network(self.subnet_pool, strict=True)
        require(pool.is_private and 16 <= pool.prefixlen <= 24, 'Use a reviewed private /16 to /24 subnet pool')
        normalized = []
        require(isinstance(self.endpoints, (tuple, list)) and len(self.endpoints) <= 32, 'At most 32 trusted endpoints')
        for endpoint in self.endpoints:
            require(isinstance(endpoint, dict) and set(endpoint) == {'host', 'port'}, 'Endpoint needs only host and port')
            address = ipaddress.IPv4Address(endpoint['host']); port = endpoint['port']
            require(str(address) == endpoint['host'] and not (address.is_loopback or address.is_multicast or address.is_unspecified)
                    and address != ipaddress.IPv4Address('255.255.255.255'), 'Use an explicit reachable IPv4; container loopback is not the host')
            require(type(port) is int and 1 <= port <= 65535, 'Endpoint port must be an integer')
            normalized.append((str(address), port))
        object.__setattr__(self, 'endpoints', tuple(sorted(set(normalized))))
        for name in ['worker_root', 'operations_root', 'state_root']:
            object.__setattr__(self, name, path_checked(getattr(self, name), exists=name != 'state_root'))
        for binary in ['docker', 'iptables', 'ip6tables', 'nsenter']:
            require(bool(re.fullmatch(r'/[A-Za-z0-9_./-]+', getattr(self, binary))), 'Command binary must be an absolute path, never a shell command')

    @classmethod
    def load(cls, path):
        return cls(**private_read(path))


class Runner:
    def run(self, argv, *, check=True, pass_fds=()):
        result = subprocess.run(argv, capture_output=True, text=True, timeout=30, pass_fds=pass_fds,
                                env={'PATH': '/usr/sbin:/usr/bin:/sbin:/bin', 'LANG': 'C.UTF-8'})
        if check and result.returncode: raise RuntimeError('Container control command failed: ' + argv[0] + ' ' + argv[1])
        return result

    def popen(self, argv, stderr=None):
        return subprocess.Popen(argv, stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=stderr or subprocess.PIPE,
                                text=True, bufsize=1, start_new_session=True,
                                env={'PATH': '/usr/sbin:/usr/bin:/sbin:/bin', 'LANG': 'C.UTF-8'})


@contextmanager
def pinned_netns(container):
    """Open the confirmed container namespace once; a recycled PID cannot redirect later commands."""
    pid = container['State']['Pid']; base = Path('/proc') / str(pid)
    require(type(pid) is int and pid > 1, 'Container namespace PID missing')
    before = (base / 'stat').read_text().rsplit(')', 1)[1].split()[19]
    require(container['Id'] in (base / 'cgroup').read_text(), 'PID does not belong to the recorded container')
    fd = os.open(base / 'ns/net', os.O_RDONLY)
    try:
        after = (base / 'stat').read_text().rsplit(')', 1)[1].split()[19]
        require(before == after and container['Id'] in (base / 'cgroup').read_text(), 'Container PID changed while pinning namespace')
        yield fd
    finally: os.close(fd)


class LinuxRuntime:
    def __init__(self, config, *, runner=None, namespace=None, host_platform=None):
        self.config = config; self.runner = runner or Runner(); self.namespace = namespace or pinned_netns
        self.host_platform = host_platform or sys.platform

    def docker(self, *args):
        # Never inherit DOCKER_HOST/DOCKER_CONTEXT or connect to a remote Docker daemon.
        return [self.config.docker, '--host', 'unix:///var/run/docker.sock', *args]

    def _live(self):
        require(self.host_platform == 'linux', 'Linux Docker isolation is unavailable; no unisolated fallback')
        require(self.config.execution_enabled, 'Execution disabled; use plan until the host/runtime is reviewed')

    def _artifacts(self):
        root = self.config.worker_root
        manifest = json.loads(path_checked(root / 'manifest.json', directory=False).read_text())
        expected = {item['file']: item['sha256'] for item in manifest['libraries']}
        for item in manifest.get('runtimeExtensions', []): expected[item['file']] = item['sha256']
        require((root / 'classes/KettleWorker.class').is_file(), 'The original Java protocol entry must be compiled')
        header = (root / 'classes/KettleWorker.class').read_bytes()[:8]
        require(len(header) == 8 and header[:4] == b'\xca\xfe\xba\xbe' and int.from_bytes(header[6:8], 'big') <= 61,
                'Worker classes must target Java 17 or earlier for this locked image')
        parts = ['/opt/rynew/classes']; evidence = {}
        for entry in manifest['classpath'].split(os.pathsep):
            original = Path(entry)
            require('..' not in original.parts, 'Classpath traversal is not accepted')
            if original.name == 'classes': continue
            # Rebase the reviewed classpath order; never use an old Mac absolute path as a Linux mount source.
            require(original.parent.name == 'lib' and original.suffix == '.jar' and original.name in expected,
                    'Classpath is outside the reviewed artifact manifest')
            path = path_checked(root / 'lib' / original.name, directory=False)
            require(digest(path) == expected[path.name], 'Original JAR hash mismatch')
            parts.append('/opt/rynew/lib/' + path.name); evidence['lib/' + path.name] = expected[path.name]
        require(len(parts) > 1, 'No original JAR classpath')
        for path in sorted((root / 'classes').rglob('*')):
            require(not path.is_symlink(), 'Compiled artifact cannot be linked')
            if path.is_file(): evidence['classes/' + str(path.relative_to(root / 'classes'))] = digest(path)
        evidence['gate.sh'] = digest(LINUX_ASSETS / 'gate.sh'); evidence['image'] = self.config.image
        return ':'.join(parts), hashlib.sha256(json.dumps(evidence, sort_keys=True).encode()).hexdigest()

    def plan(self, operation_dir, operation, preview_step='', row_limit=20, launch_id=None):
        cfg = self.config; op = path_checked(operation_dir)
        require(op.parent == cfg.operations_root and re.fullmatch(r'[A-Za-z0-9_-]{1,80}', op.name), 'Operation must be a direct owned run directory')
        require(not any(op == p or op in p.parents or p in op.parents for p in [cfg.worker_root, cfg.state_root]), 'Operation cannot overlap artifacts or controller journals')
        require(operation in OPERATIONS and type(row_limit) is int and 1 <= row_limit <= 200, 'Unsupported operation or row limit')
        require(isinstance(preview_step, str) and len(preview_step) <= 200 and not any(ord(c) < 32 for c in preview_step), 'Invalid preview step name')
        nonce = launch_id or secrets.token_hex(32); require(bool(re.fullmatch(r'[a-f0-9]{64}', nonce)), 'Invalid launch nonce')
        for path in op.rglob('*'):
            require(not path.is_symlink() and (path.is_dir() or path.is_file()), 'Operation assets must be regular files/directories, never links or special files')
            if path.is_file(): require(path.stat().st_nlink == 1, 'Operation assets cannot alias files outside the run through hard links')
        require(not (op / '.rynew-java-ready').exists() and not (op / 'control.stop').exists(), 'Operation control files already exist')
        classpath, source_hash = self._artifacts()
        endpoints = cfg.endpoints if operation in {'run', 'job'} else ()
        short = hashlib.sha256((cfg.instance_id + ':' + op.name + ':' + nonce).encode()).hexdigest()[:20]
        network = 'ryk-' + short; chain = 'RYK_' + short; bridge = 'rk-' + short[:12]
        pool = ipaddress.IPv4Network(cfg.subnet_pool); size = pool.num_addresses // 8
        subnet = ipaddress.IPv4Network((int(pool.network_address) + int(short, 16) % size * 8, 29))
        address = str(subnet.network_address + 2)
        require(not any(ipaddress.IPv4Address(host) in subnet for host, port in endpoints), 'Endpoint overlaps the per-run container subnet')
        labels = {LABEL + 'managed': 'linux-v2', LABEL + 'instance': cfg.instance_id, LABEL + 'run': op.name,
                  LABEL + 'nonce': nonce, LABEL + 'source': source_hash}
        label_args = [part for key, value in labels.items() for part in ['--label', key + '=' + value]]
        work = '/work/' + op.name
        mounts = [(str(cfg.worker_root / 'classes'), '/opt/rynew/classes', False),
                  (str(cfg.worker_root / 'lib'), '/opt/rynew/lib', False), (str(op), work, True)]
        java = [IMAGE['java'], '-Xmx' + str(cfg.memory_mb // 2) + 'm', '-XX:+PerfDisableSharedMem', '-Djava.awt.headless=true',
                '-Djava.net.preferIPv4Stack=true', '-Duser.timezone=UTC', '-Duser.home=' + work + '/home', '-DKETTLE_HOME=' + work + '/home',
                '-DKETTLE_JNDI_ROOT=' + work + '/home', '-DKETTLE_PLUGIN_BASE_FOLDERS=' + work + '/home/empty-plugins',
                '-DKETTLE_SYSTEM_HOSTNAME=isolated-kettle-worker', '-Djava.io.tmpdir=' + work + '/tmp',
                '-Dgovernance.worker.launch.id=' + nonce, '-cp', classpath, 'KettleWorker', work, operation, preview_step, str(row_limit)]
        create = self.docker('create', '--pull=never', '--platform', cfg.platform, '--name', 'ryk-' + short,
            '--interactive', '--read-only', '--user', str(cfg.uid) + ':' + str(cfg.gid), '--cap-drop=ALL',
            '--security-opt=no-new-privileges:true', '--restart=no', '--ipc=private', '--pids-limit', str(cfg.pids_limit),
            '--memory', str(cfg.memory_mb) + 'm', '--memory-swap', str(cfg.memory_mb) + 'm', '--cpus', str(cfg.cpus),
            '--ulimit', 'nofile=1024:1024', '--ulimit', 'fsize=' + str(cfg.file_size_mb * 1048576) + ':' + str(cfg.file_size_mb * 1048576),
            '--tmpfs', '/tmp:rw,nosuid,nodev,noexec,size=64m', '--log-driver=local', '--log-opt=max-size=10m', '--log-opt=max-file=2',
            '--env', 'HOME=' + work + '/home', '--env', 'KETTLE_HOME=' + work + '/home', '--env', 'LANG=C.UTF-8',
            '--env', 'JAVA_TOOL_OPTIONS=', '--env', '_JAVA_OPTIONS=', '--env', 'JDK_JAVA_OPTIONS=',
            '--network', network if endpoints else 'none', '--workdir=' + work, '--entrypoint=/bin/sh', *label_args)
        if endpoints: create += ['--ip', address]
        for source, destination, writable in mounts:
            create += ['--mount', 'type=bind,src=' + source + ',dst=' + destination + ('' if writable else ',readonly')]
        create += [cfg.image, '-c', (LINUX_ASSETS / 'gate.sh').read_text(), 'rynew-gate', nonce, work, *java]
        comment = 'rynew-kettle:' + cfg.instance_id + ':' + short
        rules = [['-d', host + '/32', '-p', 'tcp', '-m', 'tcp', '--dport', str(port), '-m', 'comment', '--comment', comment, '-j', 'RETURN'] for host, port in endpoints]
        rules += [['-m', 'comment', '--comment', comment, '-j', 'DROP']]
        jump = ['-i', bridge, '-s', address + '/32', '-m', 'comment', '--comment', comment, '-j', chain]
        record = {'version': 1, 'runId': op.name, 'operationDir': str(op), 'operation': operation, 'nonce': nonce,
                'sourceHash': source_hash, 'image': cfg.image, 'labels': labels, 'mounts': mounts, 'javaArgv': java,
                'containerCreate': create, 'networkName': network if endpoints else None, 'networkId': None, 'containerId': None,
                'subnet': str(subnet), 'containerIp': address, 'bridgeInterface': bridge, 'hostChain': chain, 'namespaceChain': chain,
                'endpoints': list(endpoints), 'hostRules': rules if endpoints else [], 'hostJump': jump,
                'labelArgs': label_args, 'state': 'PLANNED', 'hostChainCreated': False, 'installedHostRules': [],
                'hostJumpInstalled': False, 'javaReleased': False, 'containerRemoved': False, 'networkRemoved': False,
                'order': ['verify image/artifacts', 'create owned network if needed', 'create gated container',
                          'install scoped DOCKER-USER chain if needed', 'start gate and pin container namespace',
                          'install namespace IPv4 allowlist and IPv6 deny chain', 'release Java gate', 'stream original NDJSON',
                          'wait for container exit before scoped cleanup']}
        network_create = self.docker('network', 'create', '--driver=bridge', '--ipv6=false', '--subnet', record['subnet'],
            '--opt', 'com.docker.network.bridge.enable_icc=false', '--opt', 'com.docker.network.bridge.name=' + bridge,
            *label_args, network)
        host_policy = [self._iptables('-N', chain)] + [self._iptables('-A', chain, *rule) for rule in rules] + [self._iptables('-I', 'DOCKER-USER', '1', *jump)]
        ns_policy = []
        for binary, entries in [(cfg.iptables, rules), (cfg.ip6tables, [rules[-1]])]:
            prefix = [cfg.nsenter, '--net=/proc/self/fd/<PINNED_NETNS_FD>', '--', binary, '-w', '5']
            ns_policy += [[*prefix, '-N', chain], *[[*prefix, '-A', chain, *rule] for rule in entries], [*prefix, '-I', 'OUTPUT', '1', '-j', chain]]
        record['commandPlan'] = {'imageInspect': self.docker('image', 'inspect', cfg.image),
            'networkCreate': network_create if endpoints else None, 'containerCreate': create,
            'hostFirewall': host_policy if endpoints else [], 'attachGate': self.docker('start', '--attach', '--interactive', '<CONTAINER_ID>'),
            'namespaceFirewall': ns_policy if endpoints else [], 'releaseGateFile': str(op / '.rynew-java-ready'),
            'cleanupRequires': 'Exited container; matching labels, mounts, source hash, network peers and exact owned firewall rules'}
        return record

    def _save(self, record, *, create=False):
        root = self.config.state_root; path_checked(root, exists=False); root.mkdir(parents=True, exist_ok=True, mode=0o700)
        marker = root / 'owner.json'
        expected = {'instance': self.config.instance_id, 'root': str(root)}
        if marker.exists(): require(private_read(marker) == expected, 'Controller journal ownership mismatch')
        else: atomic_write(marker, json.dumps(expected))
        record['updatedAt'] = time.time(); target = root / (record['runId'] + '.json')
        if create:
            # Concurrent controllers cannot create two containers for the same logical run.
            fd = os.open(target, os.O_WRONLY | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW, 0o600)
            with os.fdopen(fd, 'w') as stream:
                stream.write(json.dumps(record, indent=2)); stream.flush(); os.fsync(stream.fileno())
        else: atomic_write(target, json.dumps(record, indent=2))

    def _read(self, run_id):
        require(bool(re.fullmatch(r'[A-Za-z0-9_-]{1,80}', run_id)), 'Invalid run identifier')
        require(private_read(self.config.state_root / 'owner.json') == {'instance': self.config.instance_id, 'root': str(self.config.state_root)}, 'Unknown journal owner')
        record = private_read(self.config.state_root / (run_id + '.json'))
        require(record['runId'] == run_id and record['labels'].get(LABEL + 'instance') == self.config.instance_id, 'Journal identity differs')
        return record

    def _inspect(self, record):
        require(bool(re.fullmatch(r'[a-f0-9]{64}', record.get('containerId') or '')), 'No confirmed container id')
        container = json.loads(self.runner.run(self.docker('inspect', record['containerId'])).stdout)[0]
        require(container['Id'] == record['containerId'] and all(container['Config'].get('Labels', {}).get(k) == v for k, v in record['labels'].items()), 'Container identity/labels differ')
        require(self._same_image(container['Config']['Image'], record['image']) and container['Config']['User'] == str(self.config.uid) + ':' + str(self.config.gid), 'Container image/user differs')
        host = container['HostConfig']; require(host['ReadonlyRootfs'] and not host.get('Privileged') and 'ALL' in host.get('CapDrop', []) and not host.get('CapAdd'), 'Container isolation was changed')
        security = host.get('SecurityOpt', [])
        require(len(security) == 1 and security[0] in {'no-new-privileges', 'no-new-privileges:true'} and not host.get('PortBindings'), 'Container privilege or published ports changed')
        require(host.get('PidMode', '') in {'', 'private'} and host.get('IpcMode', '') in {'', 'private'}
                and not host.get('Devices') and not host.get('DeviceRequests'), 'Container shares host namespaces or devices')
        require(all(m.get('Type') == 'bind' or m.get('Type') == 'tmpfs' and m.get('Destination') == '/tmp' for m in container['Mounts']),
                'Unreviewed volume/device mount')
        actual = sorted((m['Source'], m['Destination'], m['RW']) for m in container['Mounts'] if m.get('Type') == 'bind')
        require(actual == sorted(tuple(m) for m in record['mounts']), 'Container bind mounts differ')
        expected_network = record['networkName'] or 'none'
        require(host['NetworkMode'] == expected_network, 'Container network changed')
        if record.get('startedAt') and container['State'].get('Running'):
            require(container['State']['StartedAt'] == record['startedAt'], 'Container restarted outside the controller')
        return container

    def _network(self, record):
        data = json.loads(self.runner.run(self.docker('network', 'inspect', record['networkId'] or record['networkName'])).stdout)[0]
        if record.get('networkId'): require(data['Id'] == record['networkId'], 'Network id differs')
        require(all(data.get('Labels', {}).get(k) == v for k, v in record['labels'].items()) and data['Name'] == record['networkName'], 'Network labels differ')
        require(data['Driver'] == 'bridge' and not data.get('EnableIPv6') and data['IPAM']['Config'][0]['Subnet'] == record['subnet'], 'Network configuration differs')
        require(data.get('Options', {}).get('com.docker.network.bridge.name') == record['bridgeInterface'], 'Network bridge interface differs')
        require(set(data.get('Containers', {})).issubset({record.get('containerId')}), 'Network contains an unowned container')
        return data

    def _iptables(self, *args): return [self.config.iptables, '-w', '5', *args]

    @staticmethod
    def _same_image(value, expected):
        if '@sha256:' not in value: return False
        repo, checksum = value.split('@', 1)
        return repo in {'eclipse-temurin', 'library/eclipse-temurin', 'docker.io/eclipse-temurin', 'docker.io/library/eclipse-temurin'} and checksum == expected.split('@', 1)[1]

    def launch(self, operation_dir, operation, preview_step='', row_limit=20, launch_id=None, *, stderr=None):
        self._live(); record = self.plan(operation_dir, operation, preview_step, row_limit, launch_id)
        require(not (self.config.state_root / (record['runId'] + '.json')).exists(), 'Existing run requires reconciliation; never recreate it')
        self._save(record, create=True)
        attached = None
        try:
            image = json.loads(self.runner.run(self.docker('image', 'inspect', record['image'])).stdout)[0]
            require(any(self._same_image(value, record['image']) for value in image.get('RepoDigests', [])) and image['Os'] == 'linux' and image['Architecture'] == 'amd64', 'Pinned image is not preloaded for Linux amd64')
            for name in ['home', 'tmp', 'output']: (Path(record['operationDir']) / name).mkdir(exist_ok=True, mode=0o700)
            if record['endpoints']:
                # The existing DOCKER-USER hook must already exist; never create global policy or change the daemon.
                self.runner.run(self._iptables('-S', 'DOCKER-USER'))
                require(not Path('/sys/class/net', record['bridgeInterface']).exists(), 'Bridge interface already exists; refusing adoption')
                result = self.runner.run(record['commandPlan']['networkCreate'])
                record['networkId'] = result.stdout.strip()
                require(bool(re.fullmatch(r'[a-f0-9]{64}', record['networkId'])), 'Unconfirmed network id')
                self._network(record); record['state'] = 'NETWORK_CREATED'; self._save(record)
            record['containerId'] = self.runner.run(record['containerCreate']).stdout.strip(); self._inspect(record)
            record['state'] = 'CONTAINER_CREATED'; self._save(record)
            if record['endpoints']:
                self.runner.run(self._iptables('-N', record['hostChain'])); record['hostChainCreated'] = True; self._save(record)
                for rule in record['hostRules']:
                    self.runner.run(self._iptables('-A', record['hostChain'], *rule)); record['installedHostRules'].append(rule); self._save(record)
                self.runner.run(self._iptables('-I', 'DOCKER-USER', '1', *record['hostJump'])); record['hostJumpInstalled'] = True; self._save(record)
            attached = self.runner.popen(self.docker('start', '--attach', '--interactive', record['containerId']), stderr=stderr)
            deadline = time.monotonic() + 20
            while True:
                container = self._inspect(record)
                if container['State'].get('Running'): break
                require(time.monotonic() < deadline and attached.poll() is None, 'Gate container failed to start')
                time.sleep(0.1)
            record['startedAt'] = container['State']['StartedAt']; record['state'] = 'GATE_WAIT'; self._save(record)
            if record['endpoints']:
                self._network(record)
                network = container['NetworkSettings']['Networks'][record['networkName']]
                require(network['IPAddress'] == record['containerIp'], 'Container IP differs from firewall source')
                with self.namespace(container) as fd:
                    prefix = [self.config.nsenter, '--net=/proc/self/fd/' + str(fd), '--']
                    for binary, rules in [(self.config.iptables, record['hostRules']), (self.config.ip6tables, [record['hostRules'][-1]])]:
                        self.runner.run([*prefix, binary, '-w', '5', '-N', record['namespaceChain']], pass_fds=(fd,))
                        for rule in rules: self.runner.run([*prefix, binary, '-w', '5', '-A', record['namespaceChain'], *rule], pass_fds=(fd,))
                        self.runner.run([*prefix, binary, '-w', '5', '-I', 'OUTPUT', '1', '-j', record['namespaceChain']], pass_fds=(fd,))
            # No Java or XML-controlled action has run before both network boundaries are installed.
            self._inspect(record); record['state'] = 'POLICY_READY'; self._save(record)
            atomic_write(Path(record['operationDir']) / '.rynew-java-ready', record['nonce'] + '\n', 0o444)
            record['javaReleased'] = True; record['state'] = 'JAVA_RELEASED'; self._save(record)
            return ContainerProcess(self, record['runId'], attached)
        except Exception:
            record['state'] = 'RECOVERY_REQUIRED'; self._save(record)
            # Never guess whether a failed Docker command created a resource, nor re-run Java on recovery.
            raise

    def status(self, run_id):
        self._live(); record = self._read(run_id)
        if record.get('containerRemoved'):
            return {'runId': run_id, 'containerId': record['containerId'], 'sourceHash': record['sourceHash'],
                    'running': False, 'exitCode': record.get('containerExitCode'), 'state': record['state'], 'containerRemoved': True}
        container = self._inspect(record)
        return {'runId': run_id, 'containerId': record['containerId'], 'sourceHash': record['sourceHash'],
                'running': bool(container['State']['Running']), 'exitCode': container['State'].get('ExitCode'),
                'state': record['state'], 'recoveryRequired': record['state'] == 'RECOVERY_REQUIRED'}

    def recover(self, run_id):
        self._live(); record = self._read(run_id)
        if not record.get('containerId'):
            return {'runId': run_id, 'state': 'RECOVERY_REQUIRED', 'message': 'Container creation was not acknowledged; inspect exact recorded labels before manual reconciliation'}
        if record.get('containerRemoved'):
            if record.get('networkName') and not record.get('networkRemoved'): self._network(record)
            return {'runId': run_id, 'state': record['state'], 'containerRemoved': True, 'resubmitted': False,
                    'message': 'Continue scoped cleanup only; container removal was already acknowledged'}
        container = self._inspect(record)
        record['state'] = 'RECOVERY_REQUIRED'; self._save(record)
        return {'runId': run_id, 'containerId': container['Id'], 'state': 'RECOVERY_REQUIRED',
                'running': container['State']['Running'], 'exitCode': container['State'].get('ExitCode'), 'resubmitted': False}

    def request_stop(self, run_id, *, force=False):
        self._live(); record = self._read(run_id); container = self._inspect(record)
        if not container['State']['Running']: return self.status(run_id)
        if force:
            # A compromised operation directory cannot block the trusted Docker kill path by linking control.stop.
            self._inspect(record); self.runner.run(self.docker('kill', '--signal=KILL', record['containerId']))
        else: atomic_write(Path(record['operationDir']) / 'control.stop', 'STOP:' + record['nonce'] + '\n', 0o444)
        record['state'] = 'STOP_REQUESTED'; self._save(record); return {'runId': run_id, 'stopRequested': True, 'forced': force}

    def cleanup(self, run_id):
        self._live(); record = self._read(run_id)
        if record['state'] == 'CLEANED': return {'runId': run_id, 'cleaned': True, 'operationFilesPreserved': True}
        if not record.get('containerRemoved'):
            container = self._inspect(record)
            require(not container['State']['Running'], 'Stop and confirm the owned container before cleanup')
            record['containerExitCode'] = container['State'].get('ExitCode')
        if record.get('networkName') and not record.get('networkRemoved'): self._network(record)
        if record['hostChainCreated']:
            current = self.runner.run(self._iptables('-S', record['hostChain'])).stdout.splitlines()
            expected = [['-N', record['hostChain']]] + [['-A', record['hostChain'], *r] for r in record['installedHostRules']]
            require([shlex.split(line) for line in current] == expected, 'Firewall chain contains unknown rules; manual reconciliation required')
        # Remove the stopped container before removing any host egress filter.
        if not record.get('containerRemoved'):
            self.runner.run(self.docker('rm', record['containerId'])); record['containerRemoved'] = True; self._save(record)
        if record['hostChainCreated']:
            if record['hostJumpInstalled']:
                self.runner.run(self._iptables('-D', 'DOCKER-USER', *record['hostJump'])); record['hostJumpInstalled'] = False; self._save(record)
            for rule in list(reversed(record['installedHostRules'])):
                self.runner.run(self._iptables('-D', record['hostChain'], *rule)); record['installedHostRules'].remove(rule); self._save(record)
            self.runner.run(self._iptables('-X', record['hostChain'])); record['hostChainCreated'] = False; self._save(record)
        if record.get('networkName') and not record.get('networkRemoved'):
            self._network(record); self.runner.run(self.docker('network', 'rm', record['networkId'])); record['networkRemoved'] = True; self._save(record)
        record['state'] = 'CLEANED'; self._save(record)
        return {'runId': run_id, 'cleaned': True, 'operationFilesPreserved': True}


class ContainerProcess:
    """Popen-shaped handle. Completion follows the owned container, not the attach CLI's lifetime."""
    def __init__(self, runtime, run_id, attached):
        self.runtime = runtime; self.run_id = run_id; self.attached = attached
        self.stdin = attached.stdin; self.stdout = attached.stdout; self.stderr = attached.stderr; self.pid = attached.pid
        self.container_id = runtime._read(run_id)['containerId']; self.returncode = None
    def poll(self):
        state = self.runtime.status(self.run_id)
        if not state['running']: self.returncode = state['exitCode']
        return self.returncode
    def wait(self, timeout=None):
        deadline = None if timeout is None else time.monotonic() + timeout
        while self.poll() is None:
            if deadline is not None and time.monotonic() >= deadline: raise subprocess.TimeoutExpired('owned Docker container', timeout)
            time.sleep(0.1)
        return self.returncode
    def terminate(self): self.runtime.request_stop(self.run_id)
    def kill(self): self.runtime.request_stop(self.run_id, force=True)
    def identity(self):
        record = self.runtime._read(self.run_id)
        self.runtime._inspect(record)
        return {'kind': 'docker', 'containerId': self.container_id, 'sourceHash': record['sourceHash'], 'nonce': record['nonce']}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('action', choices=['plan', 'launch', 'status', 'stop', 'cleanup', 'recover'])
    parser.add_argument('--config', required=True, type=Path); parser.add_argument('--run-dir', type=Path)
    parser.add_argument('--operation', choices=sorted(OPERATIONS), default='run'); parser.add_argument('--preview-step', default='')
    parser.add_argument('--row-limit', type=int, default=20); parser.add_argument('--run-id')
    args = parser.parse_args(); runtime = LinuxRuntime(Config.load(args.config))
    if args.action == 'plan': result = runtime.plan(args.run_dir, args.operation, args.preview_step, args.row_limit)
    elif args.action == 'launch':
        handle = runtime.launch(args.run_dir, args.operation, args.preview_step, args.row_limit, stderr=sys.stderr)
        def input_control():
            for line in sys.stdin:
                if line.strip() == 'STOP': handle.terminate()
        threading.Thread(target=input_control, daemon=True).start()
        try:
            for line in handle.stdout: sys.stdout.write(line); sys.stdout.flush()
            raise SystemExit(handle.wait())
        except KeyboardInterrupt: handle.terminate(); raise
    else: result = getattr(runtime, 'request_stop' if args.action == 'stop' else args.action)(args.run_id)
    print(json.dumps(result, indent=2))


if __name__ == '__main__': main()
