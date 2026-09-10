#!/usr/bin/env python3
"""Owned Kafka 3.9.2 + ZooKeeper fixture on 127.0.0.1:29092/22181; no reset/adopt commands."""
import argparse
from concurrent.futures import ThreadPoolExecutor
import json
import os
from pathlib import Path
import re
import signal
import socket
import ssl
import subprocess
import time
import urllib.request
import uuid
from kafka_dev import (require, read_json, save_json, secure_write, file_hash, listeners,
                       process_identity, control_lock, inventory, install, NoRedirect)

OWNER = 'rynew-kettle-legacy-kafka-fixture'
LOCK = json.loads(Path(__file__).with_name('kettle_kafka_fixture_lock.json').read_text())
PORTS = {'zookeeper': 22181, 'kafka': 29092}
MAINS = {'zookeeper': 'org.apache.zookeeper.server.quorum.QuorumPeerMain', 'kafka': 'kafka.Kafka'}


def free_port(port):
    result = subprocess.run(['/usr/sbin/lsof', '-nP', '-iTCP:' + str(port), '-sTCP:LISTEN', '-Fp'], capture_output=True, text=True)
    require(result.returncode in {0, 1} and not result.stdout.strip(), 'Port has an existing listener; no process will be killed')
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as candidate:
        # A just-stopped owned listener can leave TIME_WAIT, which is not an active service.
        candidate.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        candidate.bind(('127.0.0.1', port))


def owned(path, java_home=None):
    supplied = Path(path).absolute(); root = supplied.resolve()
    require(not supplied.is_symlink() and root.name == 'kafka-legacy', 'Dedicated kafka-legacy directory required')
    if not root.exists():
        require(java_home is not None, 'Prepare must create the fixture before use')
        root.mkdir(parents=True, mode=0o700)
        save_json(root / 'owner.json', {'owner': OWNER, 'root': str(root), 'instanceId': uuid.uuid4().hex,
                                       'javaHome': str(Path(java_home).resolve())})
    marker = read_json(root / 'owner.json')
    require(marker.get('owner') == OWNER and marker.get('root') == str(root), 'Unknown directory is not adopted')
    java = Path(marker['javaHome'])
    require((java / 'bin/java').is_file() and (java / 'bin/javac').is_file(), 'Existing JDK required')
    require(re.search(r'^JAVA_VERSION="(?:17|21)(?:\.|["+])', (java / 'release').read_text(), re.M), 'Existing Java 17 or 21 required')
    for name in ['downloads', 'apps', 'private', 'logs', 'data', 'tools', 'tools/classes', 'evidence']:
        target = root / name
        require(not target.is_symlink(), 'Fixture subdirectory cannot be a link')
        target.mkdir(mode=0o700, exist_ok=True)
    return root, marker, java


def download(url, path, limit):
    permitted = {LOCK['url'], LOCK['url'] + '.sha512', LOCK['url'] + '.asc', LOCK['keysUrl']}
    require(url in permitted, 'Only pinned official URLs may be downloaded')
    if path.exists():
        require(not path.is_symlink() and path.stat().st_size <= limit, 'Invalid cached archive')
        return
    opener = urllib.request.build_opener(NoRedirect(), urllib.request.HTTPSHandler(context=ssl.create_default_context(cafile='/etc/ssl/cert.pem')))
    temporary = path.with_name(path.name + '.part-' + uuid.uuid4().hex)
    try:
        if url == LOCK['url']:
            # The official archive supports byte ranges. Verify every range and the assembled digest.
            size = LOCK['byteSize']; chunk_size = (size + 7) // 8
            parts = []
            def fetch(index):
                begin = index * chunk_size; end = min(size - 1, begin + chunk_size - 1)
                part = temporary.with_name(temporary.name + '.' + str(index)); parts.append(part)
                request = urllib.request.Request(url, headers={'Range': 'bytes=' + str(begin) + '-' + str(end)})
                with opener.open(request, timeout=40) as response, part.open('xb') as output:
                    require(response.status == 206 and response.headers.get('Content-Range') == 'bytes ' + str(begin) + '-' + str(end) + '/' + str(size), 'Unexpected archive range')
                    count = 0
                    while block := response.read(1024 * 1024):
                        count += len(block); require(count <= end - begin + 1, 'Range too large'); output.write(block)
                    require(count == end - begin + 1, 'Range truncated')
                return part
            try:
                with ThreadPoolExecutor(max_workers=8) as pool: ordered = list(pool.map(fetch, range(8)))
                with temporary.open('xb') as output:
                    for part in ordered:
                        with part.open('rb') as source:
                            while block := source.read(1024 * 1024): output.write(block)
                require(file_hash(temporary, 'sha512') == LOCK['sha512'], 'Assembled archive checksum differs')
                os.replace(temporary, path)
                return
            finally:
                for part in parts:
                    if part.exists(): part.unlink()
        with opener.open(url, timeout=40) as response, temporary.open('xb') as output:
            count = 0
            while block := response.read(1024 * 1024):
                count += len(block); require(count <= limit, 'Download too large'); output.write(block)
        os.replace(temporary, path)
    finally:
        if temporary.exists(): temporary.unlink()


def prepare(root, marker, java, pgp_python):
    for port in PORTS.values(): free_port(port)
    files = [(LOCK['url'], root / 'downloads' / LOCK['filename'], 160 * 1024 * 1024),
             (LOCK['url'] + '.sha512', root / 'downloads/SHA512', 16384),
             (LOCK['url'] + '.asc', root / 'downloads/signature.asc', 16384),
             (LOCK['keysUrl'], root / 'downloads/KEYS', 4 * 1024 * 1024)]
    with ThreadPoolExecutor(max_workers=4) as pool:
        list(pool.map(lambda args: download(*args), files))
    archive = files[0][1]
    official = ''.join(re.findall(r'\b[0-9A-Fa-f]{8}\b', files[1][1].read_text())).lower()
    require(official == LOCK['sha512'] == file_hash(archive, 'sha512'), 'Official/pinned/download hash mismatch')
    verifier = r'''
import json,re,sys,warnings
from pathlib import Path
from pgpy import PGPKey,PGPSignature
from pgpy.constants import SecurityIssues
from cryptography.hazmat.primitives import hashes
archive,signature,keys,fingerprint=sys.argv[1:]
with warnings.catch_warnings(record=True) as warnings_seen:
 warnings.simplefilter('always');sig=PGPSignature.from_file(signature);signer=None
 for block in re.findall(r'-----BEGIN PGP PUBLIC KEY BLOCK-----.*?-----END PGP PUBLIC KEY BLOCK-----',Path(keys).read_text(),re.S):
  key,_=PGPKey.from_blob(block)
  if str(key.fingerprint)==fingerprint:signer=key;break
 if signer is None:raise SystemExit(2)
 data=Path(archive).read_bytes();issues=signer.check_soundness(False)|signer.check_primitives()
 # A historical release remains verifiable after its signing key expires. Do not mask any other issue.
 if issues not in (SecurityIssues.OK,SecurityIssues.Expired):raise SystemExit(3)
 valid_at_signing=signer.created<=sig.created and (signer.expires_at is None or sig.created<signer.expires_at)
 if not valid_at_signing or not signer._key.verify(sig.hashdata(data),sig.__sig__,getattr(hashes,sig.hash_algorithm.name)()):raise SystemExit(4)
 print(json.dumps({'signatureVerified':True,'signatureCryptographicallyVerified':True,'signerFingerprint':fingerprint,
                   'validAtSigning':valid_at_signing,'signerExpiredNow':signer.is_expired,'signatureCreated':sig.created.isoformat(),
                   'signerExpiresAt':signer.expires_at.isoformat() if signer.expires_at else None,'warningCount':len(warnings_seen)}))
'''
    result = subprocess.run([str(Path(pgp_python).absolute()), '-c', verifier, str(archive), str(files[2][1]),
                             str(files[3][1]), LOCK['signerFingerprint']], capture_output=True, text=True, timeout=60)
    require(result.returncode == 0, 'Official detached signature failed')
    save_json(root / 'evidence/artifact-verification.json', {'version': LOCK['version'], 'sha512': LOCK['sha512'],
              'bytes': archive.stat().st_size, **json.loads(result.stdout), 'trustBoundary': 'Official HTTPS key; no independent web-of-trust/revocation claim'})
    home = install(root, archive, LOCK)
    for name, content in configs(root).items():
        target = root / 'private' / name
        require(not target.exists() or target.read_text() == content, 'Existing configuration differs; will not overwrite')
        if not target.exists(): secure_write(target, content)
    source = Path(__file__).with_name('kettle_kafka_fixture_probe.java')
    secure_write(root / 'tools/kettle_kafka_fixture_probe.java', source.read_bytes())
    result = subprocess.run([str(java / 'bin/javac'), '-proc:none', '-encoding', 'UTF-8', '-cp', str(home / 'libs/*'),
                             '-d', str(root / 'tools/classes'), str(root / 'tools/kettle_kafka_fixture_probe.java')], capture_output=True, text=True)
    secure_write(root / 'logs/probe-compile.log', result.stdout + result.stderr)
    require(result.returncode == 0, 'Probe compilation failed')
    save_json(root / 'probe-manifest.json', {'sourceSha256': file_hash(source), 'classes': inventory(root / 'tools/classes')})
    return {'prepared': True, 'version': LOCK['version'], 'ports': PORTS}


def configs(root):
    zk = {'dataDir': str(root / 'data/zookeeper'), 'clientPort': '22181', 'clientPortAddress': '127.0.0.1',
          'admin.enableServer': 'false', 'maxClientCnxns': '30', 'tickTime': '2000', 'autopurge.purgeInterval': '1'}
    kafka = {'broker.id': '0', 'listeners': 'PLAINTEXT://127.0.0.1:29092', 'advertised.listeners': 'PLAINTEXT://127.0.0.1:29092',
             'zookeeper.connect': '127.0.0.1:22181', 'log.dirs': str(root / 'data/kafka'), 'num.partitions': '1',
             'offsets.topic.replication.factor': '1', 'offsets.topic.num.partitions': '1', 'transaction.state.log.replication.factor': '1',
             'transaction.state.log.min.isr': '1', 'group.initial.rebalance.delay.ms': '0', 'auto.create.topics.enable': 'false',
             'num.network.threads': '2', 'num.io.threads': '2', 'log.message.downconversion.enable': 'true',
             'log.retention.hours': '24', 'log.segment.bytes': '16777216', 'zookeeper.connection.timeout.ms': '10000'}
    result = {name + '.properties': ''.join(k + '=' + v + '\n' for k, v in sorted(values.items()))
              for name, values in [('zookeeper', zk), ('kafka', kafka)]}
    result['log4j.properties'] = 'log4j.rootLogger=WARN,stderr\nlog4j.appender.stderr=org.apache.log4j.ConsoleAppender\nlog4j.appender.stderr.Target=System.err\nlog4j.appender.stderr.layout=org.apache.log4j.PatternLayout\nlog4j.appender.stderr.layout.ConversionPattern=%d %p %c %m%n\n'
    return result


def verify_install(root):
    manifest = read_json(root / 'installation.json'); home = root / 'apps' / LOCK['directory']
    require(manifest['archiveSha512'] == LOCK['sha512'] and inventory(home) == manifest['files'], 'Installed Kafka changed')
    for name, value in configs(root).items(): require((root / 'private' / name).read_text() == value, 'Configuration changed')
    probe = read_json(root / 'probe-manifest.json')
    require(probe['classes'] == inventory(root / 'tools/classes'), 'Probe class changed')
    return home


def command(root, marker, java, main, args=()):
    home = root / 'apps' / LOCK['directory']
    return [str(java / 'bin/java'), '-Xms64m', '-Xmx256m', '-XX:ActiveProcessorCount=2', '-Djava.net.preferIPv4Stack=true',
            '-Drynew.kettle.kafka.fixture=' + marker['instanceId'], '-Dlog4j.configuration=file:' + str(root / 'private/log4j.properties'),
            '-Dzookeeper.admin.enableServer=false', '-Dzookeeper.jmx.log4j.disable=true',
            '-cp', str(root / 'tools/classes') + os.pathsep + str(home / 'libs/*'), main, *args]


def environment(root, java):
    return {'PATH': '/usr/bin:/bin', 'HOME': str(root / 'private'), 'JAVA_HOME': str(java), 'LANG': 'en_US.UTF-8'}


def matched(record, root, marker, name):
    actual = process_identity(record['pid'])
    if actual is None: return False
    require(actual == record['identity'] and str(root) in actual and MAINS[name] in actual
            and '-Drynew.kettle.kafka.fixture=' + marker['instanceId'] in actual, 'PID identity differs; refusing signal')
    return True


def start(root, marker, java):
    verify_install(root)
    if marker.get('clusterId'):
        require((root / 'data/kafka/meta.properties').is_file() and (root / 'data/zookeeper/version-2').is_dir(),
                'Previously initialized fixture data is missing; refusing automatic recreation')
    for name in ['zookeeper', 'kafka']:
        record_path = root / 'private' / (name + '-process.json')
        if record_path.exists() and matched(read_json(record_path), root, marker, name):
            require(listeners(read_json(record_path)['pid']) == ['127.0.0.1:' + str(PORTS[name])], 'Unexpected listeners')
            continue
        free_port(PORTS[name])
        with (root / 'logs' / (name + '.log')).open('ab') as log:
            child = subprocess.Popen(command(root, marker, java, MAINS[name], [str(root / 'private' / (name + '.properties'))]),
                                     cwd=root, env=environment(root, java), stdout=log, stderr=log, start_new_session=True)
        time.sleep(0.2)
        identity = process_identity(child.pid); require(identity is not None, name + ' did not start')
        record = {'pid': child.pid, 'identity': identity}; save_json(record_path, record)
        deadline = time.monotonic() + 40
        while time.monotonic() < deadline:
            require(matched(record, root, marker, name), name + ' exited; inspect private log')
            if listeners(child.pid) == ['127.0.0.1:' + str(PORTS[name])]: break
            time.sleep(0.3)
        else: raise RuntimeError(name + ' failed to listen in time')
    health = probe(root, marker, java, 'health')
    require(not marker.get('clusterId') or marker['clusterId'] == health['clusterId'], 'Fixture cluster identity changed')
    marker['clusterId'] = health['clusterId']; save_json(root / 'owner.json', marker)
    save_json(root / 'evidence/latest-health.json', health)
    return health


def stop(root, marker):
    stopped = []
    for name in ['kafka', 'zookeeper']:
        path = root / 'private' / (name + '-process.json')
        if not path.exists(): continue
        record = read_json(path)
        if matched(record, root, marker, name):
            os.kill(record['pid'], signal.SIGTERM)
            deadline = time.monotonic() + 35
            while process_identity(record['pid']) is not None and time.monotonic() < deadline: time.sleep(0.3)
            require(process_identity(record['pid']) is None, 'Graceful stop incomplete; no forced kill')
        stopped.append(name)
    return {'stopped': stopped, 'dataPreserved': True}


def probe(root, marker, java, action, *args):
    verify_install(root)
    for name in MAINS:
        path = root / 'private' / (name + '-process.json')
        require(path.exists() and matched(read_json(path), root, marker, name), 'Probe requires both owned fixture processes')
        require(listeners(read_json(path)['pid']) == ['127.0.0.1:' + str(PORTS[name])], 'Probe listener identity changed')
    result = subprocess.run(command(root, marker, java, 'KettleKafkaFixtureProbe', [action, *args]), cwd=root,
                            env=environment(root, java), capture_output=True, text=True, timeout=35)
    secure_write(root / 'logs' / ('probe-' + action + '.log'), result.stderr)
    require(result.returncode == 0, 'Official Kafka probe failed; inspect private log')
    return json.loads(result.stdout)


def main():
    os.umask(0o077)
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('action', choices=['prepare', 'start', 'stop', 'status', 'probe'])
    parser.add_argument('--runtime', type=Path, required=True)
    parser.add_argument('--java-home', type=Path)
    parser.add_argument('--pgp-python', type=Path)
    parser.add_argument('--probe-args', nargs='+')
    args = parser.parse_args(); root, marker, java = owned(args.runtime, args.java_home if args.action == 'prepare' else None)
    with control_lock(root):
        if args.action == 'prepare': result = prepare(root, marker, java, args.pgp_python)
        elif args.action == 'start': result = start(root, marker, java)
        elif args.action == 'stop': result = stop(root, marker)
        elif args.action == 'probe': result = probe(root, marker, java, *args.probe_args)
        else:
            result = {name: matched(read_json(root / 'private' / (name + '-process.json')), root, marker, name)
                      if (root / 'private' / (name + '-process.json')).exists() else False for name in MAINS}
    print(json.dumps(result, ensure_ascii=False, indent=2))


if __name__ == '__main__': main()
