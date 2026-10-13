#!/usr/bin/env python3
"""Synthetic acceptance only: unmodified DETL generators, core and original input/processing/output plugins.

No production service or general graph compiler is implemented here. macOS only; the
process-specific bind shim is a test adapter, never a Linux deployment prescription.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import secrets
import select
import shutil
import socket
import subprocess
import sys
import time
import uuid
import zipfile

SOURCES = Path(__file__).resolve().parents[2] / 'data-governance/hdi-native/tests'
ARCHIVE_SHA = '3bd2ef065255e9e19119b1e2ade294b32b59d47c051a0a10981496766f052859'


def digest(path):
    h = hashlib.sha256()
    with Path(path).open('rb') as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b''):
            h.update(block)
    return h.hexdigest()


def verify_bundle(root):
    manifest = json.loads((root / 'bundle-manifest.json').read_text())
    if manifest.get('engine') != 'original-detl' or manifest.get('sourceArchiveSha256') != ARCHIVE_SHA:
        raise ValueError('This proof is scoped to the reviewed original DETL 3.3.0 archive')
    if manifest.get('runtimeConfigurationIncluded') is not False or manifest.get('activationLicenseIncluded') is not False:
        raise ValueError('The proof accepts program-only bundles without runtime credentials or activation files')
    declared = set()
    for entry in manifest['files']:
        name = PurePosixPath(entry['path'])
        if name.is_absolute() or '..' in name.parts or '\\' in str(name) or str(name) != entry['path'] or str(name) in declared:
            raise ValueError('Invalid bundle path')
        declared.add(str(name))
        if name.suffix.lower() in {'.keystore', '.jks', '.p12', '.pfx', '.key', '.pem'} or str(name).startswith('instance/certs/'):
            raise ValueError('Private key/certificate stores must not be copied from a vendor bundle')
        path = root / str(name)
        if path.is_symlink() or root not in path.resolve().parents or digest(path) != entry['sha256']:
            raise ValueError('Original bundle artifact integrity check failed: ' + str(name))
    actual = {str(p.relative_to(root)) for p in root.rglob('*') if p.is_file() or p.is_symlink()}
    if actual != declared | {'bundle-manifest.json'}:
        raise ValueError('Bundle contains unlisted files; refusing to copy possible runtime state')
    return manifest


def run(args):
    if sys.platform != 'darwin':
        raise RuntimeError('This is a macOS acceptance harness, not a Linux service launcher')
    if not 19000 <= args.port_base <= 65000:
        raise ValueError('Use an isolated test port base between 19000 and 65000')
    state = args.state_dir.absolute()
    if state.exists() or state.is_symlink():
        raise ValueError('State directory must be new; previous evidence is never overwritten')
    if any((p / '.git').exists() for p in state.parents):
        raise ValueError('Private generated state must be outside a Git checkout')
    if any(p.is_symlink() for p in state.parents):
        raise ValueError('State directory must not traverse symbolic links')
    bundle = args.bundle.resolve(strict=True)
    verify_bundle(bundle)
    with_mapping = getattr(args, 'field_mapping', False)
    if with_mapping and not (bundle / 'instance/etl-fieldMapping-2.0.0/adapter.properties').is_file():
        raise ValueError('The field mapping proof requires the original fieldMapping plugin')
    java_home = args.java_home.resolve(strict=True)
    java, keytool, javac = [str(java_home / 'bin' / name) for name in ('java', 'keytool', 'javac')]
    for path in (java, keytool, javac):
        if not os.access(path, os.X_OK):
            raise ValueError('An explicit local JDK containing java, javac and keytool is required')
    redis_bin = shutil.which(args.redis_executable)
    clang = shutil.which(args.clang)
    if not redis_bin or not clang:
        raise ValueError('Local redis-server and clang are required; nothing is installed automatically')
    coordinator = (args.zookeeper_home or bundle / 'zookeeper').resolve(strict=True)
    if not list((coordinator / 'lib').glob('zookeeper-*.jar')):
        raise ValueError('Original ZooKeeper Java distribution is required')
    original_logback = (args.logback_config or bundle / 'instance/logback.xml').resolve(strict=True)
    base = args.port_base
    for port in (base, base + 1, base + 2, base + 3, base + 7, base + 8):
        with socket.socket() as check:
            check.bind(('127.0.0.1', port))
    own_lan = args.self_address
    if not own_lan:
        own_lan = subprocess.run(['/usr/sbin/ipconfig', 'getifaddr', 'en0'], capture_output=True, text=True).stdout.strip()
    if not own_lan or own_lan.startswith('127.'):
        raise ValueError('A nonloopback address on this Mac is required to prove LAN rejection')
    # Bind proves this address belongs to this machine; never probe a remote host.
    with socket.socket() as check:
        check.bind((own_lan, 0))
    os.umask(0o077)
    state.mkdir(parents=True, mode=0o700)
    for name in ('private', 'logs', 'build', 'redis', 'zk', 'home', 'tmp', 'store', 'work', 'fel', 'fixture'):
        (state / name).mkdir(mode=0o700)
    engine = state / 'instance'
    shutil.copytree(bundle / 'instance', engine)
    immutable = {str(p.relative_to(engine)): digest(p) for p in engine.rglob('*.jar')}
    (state / 'original-jars.json').write_text(json.dumps(immutable, indent=2))
    build = state / 'build'
    proof = {'kind': 'original-detl-synthetic-acceptance', 'productionService': False, 'fieldMapping': with_mapping,
             'sourceArchiveSha256': ARCHIVE_SHA, 'network': 'only-owned-loopback-services',
             'ports': {'redis': base, 'zk': base + 1, 'data': base + 2, 'rpc': base + 3}, 'cases': []}
    children = []
    streams = []

    def command(argv, log_name):
        with (state / 'logs' / log_name).open('a') as log:
            subprocess.run(argv, stdout=log, stderr=log, check=True, timeout=60, cwd=state)

    def spawn(name, argv, env=None, cwd=None):
        stream = (state / 'logs' / (name + '.log')).open('w')
        streams.append(stream)
        process = subprocess.Popen(argv, stdin=subprocess.DEVNULL, stdout=stream, stderr=stream,
                                   env=env, cwd=cwd or state, start_new_session=True)
        children.append((name, process))
        return process

    def wait_port(port, process):
        deadline = time.monotonic() + 15
        while time.monotonic() < deadline:
            if process.poll() is not None:
                raise RuntimeError('Owned dependency exited; inspect private logs')
            try:
                with socket.create_connection(('127.0.0.1', port), timeout=.3):
                    return
            except OSError:
                time.sleep(.2)
        raise RuntimeError('Owned dependency did not listen before its deadline')

    try:
        flags = ['-DHDI_DATA_PORT=' + str(base + 2), '-DHDI_RPC_PORT=' + str(base + 3), '-DHDI_TEST_PORT=' + str(base + 7)]
        shim = build / 'loopback-bind.dylib'
        command([clang, '-dynamiclib', '-Wall', '-Wextra', *flags, '-o', str(shim), str(SOURCES / 'loopback_bind_probe.c')], 'compile.log')
        bind_fixture = build / 'bind-fixture'
        command([clang, '-Wall', '-Wextra', '-DHDI_TEST_PORT=' + str(base + 7), '-DHDI_DENIED_PORT=' + str(base + 8),
                 '-o', str(bind_fixture), str(SOURCES / 'bind_probe_server.c')], 'compile.log')
        bind_profile = state / 'private/bind.sb'
        bind_profile.write_text('(version 1)\n(allow default)\n(deny network*)\n'
                               '(allow network-bind (local tcp4 "localhost:' + str(base + 7) + '"))\n'
                               '(allow network-inbound (local tcp4 "localhost:' + str(base + 7) + '"))\n')
        fixture = subprocess.Popen(['/usr/bin/sandbox-exec', '-f', str(bind_profile), '/usr/bin/env',
                                    'DYLD_INSERT_LIBRARIES=' + str(shim), str(bind_fixture)],
                                   stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
        try:
            if not select.select([fixture.stdout], [], [], 5)[0]:
                raise RuntimeError('Bind-isolation fixture readiness timeout')
            line = fixture.stdout.readline().strip()
            if line != 'READY unauthorizedPortDenied=1':
                raise RuntimeError('Bind-isolation fixture rejected: ' + line)
            def connects(address):
                try:
                    with socket.create_connection((address, base + 7), timeout=2) as sock:
                        sock.settimeout(2)
                        return sock.recv(64) == b'owned-loopback-proof'
                except OSError:
                    return False
            proof['bindIsolation'] = {'loopbackAccepted': connects('127.0.0.1'), 'selfLanAccepted': connects(own_lan),
                                      'unauthorizedPortDenied': True, 'shimSha256': digest(shim)}
            if not proof['bindIsolation']['loopbackAccepted'] or proof['bindIsolation']['selfLanAccepted']:
                raise RuntimeError('Process-specific loopback isolation failed; engine not launched')
        finally:
            if fixture.poll() is None:
                fixture.terminate()
            fixture.wait(timeout=5)
            fixture.stdout.close()
            fixture.stderr.close()
        password = secrets.token_urlsafe(24)
        password_file = state / 'private/test-password'
        password_file.write_text(password)
        certs = engine / 'certs'
        certs.mkdir(exist_ok=True)
        secret_args = ['-storepass:file', str(password_file)]
        command([keytool, '-genkeypair', '-alias', 'probe', '-keyalg', 'RSA', '-keysize', '2048', '-dname', 'CN=localhost',
                 '-validity', '2', '-storetype', 'JKS', '-keystore', str(certs / 'server.keystore'), *secret_args,
                 '-keypass:file', str(password_file)], 'certificates.log')
        command([keytool, '-exportcert', '-alias', 'probe', '-keystore', str(certs / 'server.keystore'), *secret_args,
                 '-file', str(certs / 'server.crt')], 'certificates.log')
        command([keytool, '-importcert', '-noprompt', '-alias', 'probe', '-storetype', 'JKS',
                 '-keystore', str(certs / 'client.keystore'), *secret_args, '-file', str(certs / 'server.crt')], 'certificates.log')
        with zipfile.ZipFile(engine / 'etl-core-3.3.0.jar') as archive:
            defaults = archive.read('conf/etl_instance.properties').decode('utf-8')
        overrides = {'etl.host.name': '127.0.0.1', 'etl.host.port': base + 2, 'etl.rpc.host.port': base + 3,
                     'etl.instance.id': 900001, 'resource.connect.url': '127.0.0.1:' + str(base + 1),
                     'resource.connect.namespace': 'rynew-proof-' + uuid.uuid4().hex[:12],
                     'etl.redis.ip': '127.0.0.1', 'etl.redis.port': base, 'etl.redis.password': password,
                     'etl.redis.database': 0, 'etl.log.dir': state / 'logs/engine', 'etl.storage.datadir': state / 'store',
                     'etl.storage.medium': 'diskstore', 'etl.ssl.serverkeystore': '/certs/server.keystore',
                     'etl.ssl.clientkeystore': '/certs/client.keystore', 'etl.ssl.password': password,
                     'etl.ssl.enabled.protocols': 'TLSv1.2,TLSv1.3', 'etl.proxy.enable': 'false'}
        (engine / 'conf').mkdir(exist_ok=True)
        (engine / 'conf/etl_instance.properties').write_text(defaults + '\n' + '\n'.join(str(k) + '=' + str(v) for k, v in overrides.items()) + '\n')
        shutil.copyfile(original_logback, engine / 'logback.xml')
        (state / 'private/redis.conf').write_text('bind 127.0.0.1\nport ' + str(base) + '\nprotected-mode yes\nsave ""\nappendonly no\nrequirepass ' + password + '\ndir ' + str(state / 'redis') + '\n')
        (state / 'private/zoo.cfg').write_text('tickTime=2000\ndataDir=' + str(state / 'zk') + '\nclientPort=' + str(base + 1) + '\nclientPortAddress=127.0.0.1\nadmin.enableServer=false\n4lw.commands.whitelist=ruok\n')
        cp = os.pathsep.join([str(build), str(bundle / 'task-service/dEtlv2.jar'), str(bundle / 'task-service/lib/*'),
                              str(bundle / 'task-service/ext/normal/*'), str(engine / 'etl-core-3.3.0.jar'), str(engine / 'lib/*')])
        command([javac, '-encoding', 'UTF-8', '-cp', cp, '-d', str(build),
                 str(SOURCES / 'OriginalFlowCompiler.java'), str(SOURCES / 'OriginalMetadataClient.java')], 'compile.log')
        # This fixture talks to the core's control channel. Use that core's original
        # client library, not the management service's different Java 11 extension.
        client_cp = os.pathsep.join([str(engine / 'lib/zookeeper-hw-hw-ei.jar'), cp])
        client = [java, '-cp', client_cp, 'OriginalMetadataClient']
        def metadata(action):
            command(client + [action, str(state), str(base), overrides['resource.connect.namespace']], 'metadata.log')
        redis = spawn('redis', [redis_bin, str(state / 'private/redis.conf')])
        wait_port(base, redis)
        zk = spawn('zookeeper', [java, '-Xmx128m', '-Dzookeeper.admin.enableServer=false', '-cp',
                               str(coordinator / 'lib/*'), 'org.apache.zookeeper.server.quorum.QuorumPeerMain', str(state / 'private/zoo.cfg')])
        wait_port(base + 1, zk)
        profile = state / 'private/engine.sb'
        rules = ['(version 1)', '(allow default)', '(deny file-read*)', '(deny file-write*)', '(deny network*)',
                 '(deny process-fork)', '(deny file-link)']
        for path in (state, java_home, Path('/System'), Path('/usr/lib'), Path('/usr/share'), Path('/private/var/db/timezone')):
            rules.append('(allow file-read* (subpath ' + json.dumps(str(path.resolve())) + '))')
        rules += ['(allow file-read-metadata)', '(allow file-read* (literal "/") (literal "/dev/random") (literal "/dev/urandom") (literal "/dev/null") (literal "/private/etc/localtime"))',
                  '(allow file-write* (subpath ' + json.dumps(str(state)) + ') (literal "/dev/null"))']
        for port in (base, base + 1):
            rules.append('(allow network-outbound (remote tcp4 "localhost:' + str(port) + '"))')
        for port in (base + 2, base + 3):
            rules += ['(allow network-bind (local tcp4 "localhost:' + str(port) + '"))', '(allow network-inbound (local tcp4 "localhost:' + str(port) + '"))']
        profile.write_text('\n'.join(rules))
        process = None
        for index, (constant, format_name, expected_status) in enumerate([('原生执行验收', 'csv', 'finish'), ('原生配置变更', 'csv', 'finish'), ('无效配置不应输出', 'line', 'error')], 1):
            command([java, '-cp', cp, 'OriginalFlowCompiler', str(state / 'fixture'), constant, format_name] + (['fieldMapping'] if with_mapping else []), 'generator.log')
            metadata('seed')
            if process is None:
                env = {'PATH': '/usr/bin:/bin', 'HOME': str(state / 'home'), 'LANG': 'en_US.UTF-8',
                       'ETL_USER_DIR': str(engine), 'ETL_SSL_PASSWORD': password}
                process = spawn('engine', ['/usr/bin/sandbox-exec', '-f', str(profile), '/usr/bin/env',
                                          'DYLD_INSERT_LIBRARIES=' + str(shim), java, '-Xmx256m',
                                          '--add-opens=java.base/java.net=ALL-UNNAMED', '-Djava.net.preferIPv4Stack=true',
                                          '-Djava.io.tmpdir=' + str(state / 'tmp'), '-Duser.home=' + str(state / 'home'),
                                          '-cp', os.pathsep.join([str(engine), str(engine / 'etl-core-3.3.0.jar'), str(engine / 'lib/*')]),
                                          'com.hikvision.idatafusion.etl.core.EtlInstance'], env, engine)
                wait_port(base + 2, process)
                wait_port(base + 3, process)
                time.sleep(5)
            else:
                time.sleep(1)
            before = {p.name for p in (state / 'fixture/result').glob('*') if p.is_file()}
            metadata('start')
            metadata('await')
            status = json.loads((state / 'native-status.json').read_text())
            outputs = []
            for path in (state / 'fixture/result').glob('*'):
                if path.is_file() and path.name not in before:
                    outputs.append({'filename': path.name, 'bytes': path.stat().st_size, 'sha256': digest(path),
                                    'content': path.read_text(encoding='utf-8')})
            if status.get('status') != expected_status:
                raise AssertionError('Original task status did not match: ' + status.get('status', 'missing'))
            if expected_status == 'finish':
                if len(outputs) != 1 or outputs[0]['content'].splitlines() != ['mapped_message' if with_mapping else 'message'] + [constant] * 3:
                    raise AssertionError('Original plugin output does not match all three configured rows')
            elif outputs:
                raise AssertionError('Original rejected configuration published a new artifact')
            case = {'index': index, 'constant': constant, 'fileType': format_name, 'nativeTerminal': status, 'outputs': outputs}
            proof['cases'].append(case)
            for name in ('original-flowjson.json', 'original-schedule.json', 'generation-proof.json'):
                shutil.copyfile(state / 'fixture' / name, state / 'fixture' / (str(index) + '-' + name))
        proof['listeners'] = subprocess.run(['/usr/sbin/lsof', '-nP', '-a', '-p', str(process.pid), '-iTCP', '-sTCP:LISTEN'], capture_output=True, text=True).stdout
        if '127.0.0.1:' not in proof['listeners'] or '*:' in proof['listeners']:
            raise AssertionError('Original engine listener is not confined to loopback')
        proof['originalJarsUnchanged'] = all(digest(engine / name) == value for name, value in immutable.items())
        if not proof['originalJarsUnchanged']:
            raise AssertionError('Original engine artifact changed during acceptance')
        proof['passed'] = True
    finally:
        proof['ownedProcesses'] = {}
        for name, process in reversed(children):
            if process.poll() is None:
                process.terminate()
                try:
                    process.wait(timeout=15)
                except subprocess.TimeoutExpired:
                    proof.setdefault('unconfirmedStop', []).append(name)
            proof['ownedProcesses'][name] = {'exitCode': process.poll()}
        for stream in streams:
            stream.close()
        (state / 'proof.json').write_text(json.dumps(proof, ensure_ascii=False, indent=2))
    if proof.get('unconfirmedStop'):
        raise RuntimeError('Owned child did not confirm stop; inspect private state, do not restart automatically')
    print(json.dumps({'passed': proof.get('passed', False), 'cases': len(proof['cases']), 'proof': str(state / 'proof.json')}))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--bundle', required=True, type=Path)
    parser.add_argument('--state-dir', required=True, type=Path)
    parser.add_argument('--java-home', required=True, type=Path)
    parser.add_argument('--redis-executable', default='redis-server')
    parser.add_argument('--clang', default='clang')
    parser.add_argument('--zookeeper-home', type=Path)
    parser.add_argument('--logback-config', type=Path)
    parser.add_argument('--self-address', help='Nonloopback address owned by this Mac; defaults to active en0 address')
    parser.add_argument('--field-mapping', action='store_true', help='Exercise original fieldMapping rename between input and output')
    parser.add_argument('--port-base', type=int, default=19080)
    run(parser.parse_args())


if __name__ == '__main__':
    main()
