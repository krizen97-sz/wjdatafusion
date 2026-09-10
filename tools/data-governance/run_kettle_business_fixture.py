#!/usr/bin/env python3
"""Explicit, bounded localhost-only preparation/execution of original Kettle fixtures.

prepare mutates only a new uniquely named fixture DB/topics/groups/FTP directories.
run submits once, after explicit broker-ready selection; resume only reads that run.
No production configuration is accepted and no process is stopped by this runner.
"""
import argparse
import base64
import ftplib
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import time
import urllib.error
import urllib.request
import uuid
import kettle_business_fixture as fixture

BASE = Path('/Users/krizen/Documents/Code/projects/2026projects/rynew-runtime')
RUNTIME = BASE / 'data-governance'
KAFKA = BASE / 'data-governance-kettle-v2/kafka-legacy'
WORKER = BASE / 'data-governance-kettle-worker-v2'
OWNER = 'rynew-original-kettle-business-acceptance'
BROKER = 'http://127.0.0.1:19162'
PSQL = Path('/opt/homebrew/opt/postgresql@17/bin/psql')


def require(condition, message):
    if not condition:
        raise RuntimeError(message)


def write_json(path, value):
    data = (json.dumps(value, ensure_ascii=False, indent=2) + '\n').encode()
    temporary = path.with_name('.' + path.name + '.' + uuid.uuid4().hex)
    fixture.private_write(temporary, data)
    os.replace(temporary, path)


def credentials(name, port):
    path = RUNTIME / 'private' / (name + '-credentials.json')
    require(path.is_file() and not path.is_symlink() and not path.stat().st_mode & 0o077, 'Private fixture credentials file required')
    data = json.loads(path.read_text())
    require(data['host'] == '127.0.0.1' and int(data['port']) == port, 'Non-local fixture endpoint refused')
    return data


class LocalFtp(ftplib.FTP):
    def makepasv(self):
        host, port = super().makepasv()
        require(host == '127.0.0.1' and 22100 <= port <= 22109, 'Unexpected FTP passive endpoint')
        return host, port


def ftp_connection():
    config = credentials('ftp', 2121)
    require(Path(config['root']).resolve() == (RUNTIME / 'ftp/files').resolve(), 'FTP fixture root ownership mismatch')
    client = LocalFtp()
    client.connect('127.0.0.1', 2121, timeout=15)
    client.login(config['username'], config['password'])
    return client


def database_command(database, sql, *, config=None):
    config = config or credentials('postgres', 15432)
    require(database == config['database'] or re.fullmatch(r'rynew_kettle_fixture_[a-z][a-z0-9_]{2,31}', database), 'Unexpected database')
    env = {**os.environ, 'PGPASSWORD': config['password']}
    command = [str(PSQL), '-X', '-qAt', '-v', 'ON_ERROR_STOP=1', '-h', '127.0.0.1', '-p', '15432',
               '-U', config['username'], '-d', database]
    result = subprocess.run(command, input=sql, text=True, capture_output=True, env=env, timeout=45)
    require(result.returncode == 0, 'Local fixture PostgreSQL command failed; no SQL or credentials printed')
    return result.stdout.strip()


def probe_setup(root):
    owner = json.loads((KAFKA / 'owner.json').read_text())
    require(owner['owner'] == 'rynew-kettle-legacy-kafka-fixture' and Path(owner['root']).resolve() == KAFKA.resolve(), 'Legacy Kafka ownership mismatch')
    java = Path(owner['javaHome'])
    homes = list((KAFKA / 'apps').glob('*/libs'))
    require(len(homes) == 1, 'Ambiguous official Kafka client libraries')
    classes = root / 'probe-classes'
    classes.mkdir(mode=0o700)
    source = Path(__file__).with_name('KettleBusinessFixtureProbe.java')
    fixture.private_write(root / 'KettleBusinessFixtureProbe.java', source.read_bytes())
    result = subprocess.run([str(java / 'bin/javac'), '-proc:none', '-encoding', 'UTF-8', '-cp', str(homes[0] / '*'),
                             '-d', str(classes), str(source)], capture_output=True, timeout=45)
    fixture.private_write(root / 'probe-compile.log', result.stdout + result.stderr)
    require(result.returncode == 0, 'Dedicated Kafka probe compilation failed; inspect private compile log')
    info = {'java': str(java / 'bin/java'), 'classpath': str(classes) + os.pathsep + str(homes[0] / '*'),
            'sourceSha256': fixture.sha(source.read_bytes()), 'classSha256': fixture.sha((classes / 'KettleBusinessFixtureProbe.class').read_bytes()),
            'expectedClusterId': owner['clusterId']}
    write_json(root / 'probe.json', info)


def probe(root, action, *arguments):
    info = json.loads((root / 'probe.json').read_text())
    require(fixture.sha((root / 'probe-classes/KettleBusinessFixtureProbe.class').read_bytes()) == info['classSha256'], 'Fixture probe class changed')
    argv = [info['java'], '-Xms64m', '-Xmx256m', '-XX:ActiveProcessorCount=2', '-Djava.net.preferIPv4Stack=true',
            '-Dlog4j.configuration=file:' + str(KAFKA / 'private/log4j.properties'), '-cp', info['classpath'],
            'KettleBusinessFixtureProbe', action, str(root / 'prepared'), *map(str, arguments)]
    result = subprocess.run(argv, capture_output=True, timeout=120)
    fixture.private_write(root / (action + '-client-' + uuid.uuid4().hex[:8] + '.log'), result.stderr)
    require(result.returncode == 0, 'Dedicated Kafka client failed; inspect private client log; never reseed automatically')
    data = json.loads(result.stdout)
    require(data['health']['clusterId'] == info['expectedClusterId'], 'Kafka cluster identity changed')
    return data


def owned(root):
    root = Path(root).absolute()
    require(root.is_dir() and not root.is_symlink(), 'Owned acceptance directory required')
    marker = json.loads((root / 'owner.json').read_text())
    require(marker['owner'] == OWNER and marker['root'] == str(root), 'Unknown acceptance directory')
    return root, marker


def prepare(root):
    root = Path(root).absolute()
    require(not root.exists() and not any((parent / '.git').exists() for parent in root.resolve().parents), 'New private runtime directory outside Git required')
    root.mkdir(parents=True, mode=0o700)
    slug = 'business_' + uuid.uuid4().hex[:12]
    marker = {'owner': OWNER, 'root': str(root), 'slug': slug, 'state': 'PREPARING', 'createdAt': time.time(),
              'mutations': [], 'runs': {}, 'database': 'rynew_kettle_fixture_' + slug}
    write_json(root / 'owner.json', marker)
    pg, ftp = credentials('postgres', 15432), credentials('ftp', 2121)
    actual = database_command(pg['database'], 'SHOW data_directory;', config=pg)
    require(Path(actual).resolve() == (RUNTIME / 'postgres/data').resolve(), 'PostgreSQL fixture data directory mismatch')
    manifest = fixture.prepare('/Volumes/KINGSTON/datai', root / 'prepared', slug, pg['username'], pg['password'], ftp['username'], ftp['password'])
    marker['state'] = 'FIXTURES_GENERATED'
    write_json(root / 'owner.json', marker)
    probe_setup(root)
    # Only a new dedicated database. CREATE fails if another task already owns the name.
    marker['pendingMutation'] = 'create-database'
    write_json(root / 'owner.json', marker)
    database_command(pg['database'], 'CREATE DATABASE "' + marker['database'] + '";', config=pg)
    marker['mutations'].append({'type': 'database', 'name': marker['database']})
    write_json(root / 'owner.json', marker)
    database_command(marker['database'], (root / 'prepared/lookup-fixture.sql').read_text(), config=pg)
    counts = {}
    for table in manifest['tables']:
        counts[table] = int(database_command(marker['database'], 'SELECT count(*) FROM public.' + table + ';', config=pg))
    write_json(root / 'database-evidence.json', {'database': marker['database'], 'tables': counts, 'host': '127.0.0.1', 'port': 15432})
    marker['pendingMutation'] = 'create-owned-ftp-directories'
    write_json(root / 'owner.json', marker)
    with ftp_connection() as client:
        client.mkd('/' + slug)
        for kind in ['ordinary', 'illegal']:
            client.mkd('/' + slug + '/' + kind)
    marker['mutations'].append({'type': 'ftp-directories', 'path': '/' + slug, 'serverProcessOwnedByParent': True})
    marker['pendingMutation'] = 'create-topics-and-prime-groups-and-seed'
    marker['topics'] = manifest['topics']
    marker['groups'] = manifest['groups']
    write_json(root / 'owner.json', marker)
    seed = probe(root, 'prepare')
    write_json(root / 'kafka-seed-evidence.json', seed)
    marker['mutations'].append({'type': 'kafka-topics-groups', 'topics': manifest['topics'], 'groups': manifest['groups']})
    marker.pop('pendingMutation', None)
    marker['state'] = 'PREPARED_NOT_SUBMITTED'
    write_json(root / 'owner.json', marker)
    return {'state': marker['state'], 'slug': slug, 'tables': counts, 'seedCounts': seed['result']['seeded']}


def worker_request(method, path, body=None):
    require(path.startswith('/') and '?' not in path.split('/', 2)[1], 'Unexpected broker request')
    token_file = WORKER / '.broker-token'
    require(token_file.is_file() and not token_file.is_symlink() and not token_file.stat().st_mode & 0o077, 'Private broker token required')
    token = token_file.read_text().strip()
    headers = {'Authorization': 'Bearer ' + token}
    data = None
    if body is not None:
        data = fixture.compact(body).encode()
        headers['Content-Type'] = 'application/json'
    request = urllib.request.Request(BROKER + path, data=data, headers=headers, method=method)
    class NoRedirect(urllib.request.HTTPRedirectHandler):
        def redirect_request(self, *args, **kwargs):
            raise RuntimeError('Broker redirect refused')
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}), NoRedirect())
    try:
        with opener.open(request, timeout=150) as response:
            data = response.read(24 * 1024 * 1024 + 1)
            require(len(data) <= 24 * 1024 * 1024, 'Broker response exceeded capture bound')
            return json.loads(data)
    except urllib.error.HTTPError as error:
        raise RuntimeError('Local broker HTTP ' + str(error.code)) from None


def capture_ftp(root, kind, manifest):
    destination = root / (kind + '-ftp-retr')
    require(not destination.exists(), 'FTP evidence already exists; never overwrite it')
    destination.mkdir(mode=0o700)
    remote = '/' + manifest['database'].removeprefix('rynew_kettle_fixture_') + '/' + kind
    transcript = []
    with ftp_connection() as client:
        client.cwd(remote)
        try:
            names = client.nlst()
        except ftplib.error_perm as error:
            if str(error).startswith('550'):
                names = []
            else:
                raise RuntimeError('FTP listing failed') from None
        for name in sorted(names):
            require(name == Path(name).name and '..' not in name and not any(ord(c) < 32 for c in name), 'FTP returned an unsafe name')
            path = destination / name
            total = 0
            digest = hashlib.sha256()
            descriptor = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
            with os.fdopen(descriptor, 'wb') as output:
                def accept(chunk):
                    nonlocal total
                    total += len(chunk)
                    require(total <= 64 * 1024 * 1024, 'FTP file exceeded capture bound')
                    digest.update(chunk)
                    output.write(chunk)
                client.retrbinary('RETR ' + name, accept)
            transcript.append({'name': name, 'bytes': total, 'sha256': digest.hexdigest(), 'method': 'FTP RETR binary'})
    write_json(root / (kind + '-ftp-evidence.json'), {'remoteDirectory': remote, 'passivePorts': [22100, 22109], 'files': transcript})
    return destination


def run(root, kind, resume=False, broker_ready=False):
    root, marker = owned(root)
    require(kind in ('ordinary', 'illegal'), 'Expected original graph kind')
    manifest = json.loads((root / 'prepared/manifest.json').read_text())
    for name, info in manifest['files'].items():
        require(fixture.sha((root / 'prepared' / name).read_bytes()) == info['sha256'], 'Prepared graph or synthetic data changed')
    journal_path = root / (kind + '-run-journal.json')
    if resume:
        require(journal_path.exists(), 'No existing run to reconcile')
        journal = json.loads(journal_path.read_text())
    else:
        require(broker_ready, 'Explicit confirmation of the upgraded INPUT_DIR/timezone broker is required before any graph submission')
        require(marker['state'] in ('PREPARED_NOT_SUBMITTED', 'EXECUTION_STARTED') and not journal_path.exists(), 'This graph already has an execution intent; use read-only resume')
        job = (root / 'prepared' / ('ordinary-composed-fixture.kjb' if kind == 'ordinary' else 'illegal.kjb')).read_text()
        child = (root / 'prepared' / (kind + '.ktr')).read_bytes()
        run_id, job_id = str(uuid.uuid4()), str(uuid.uuid4())
        inputs = [{'name': kind + '.ktr', 'contentBase64': base64.b64encode(child).decode()}]
        journal = {'owner': OWNER, 'kind': kind, 'runId': run_id, 'jobId': job_id, 'state': 'PREPARING',
                   'createdAt': time.time(), 'xmlSha256': fixture.sha(job), 'childXmlSha256': fixture.sha(child),
                   'inputFingerprint': fixture.sha(fixture.compact(inputs)), 'executionTimeZone': 'Asia/Shanghai', 'eventsCursor': 0}
        write_json(journal_path, journal)
        marker['state'] = 'EXECUTION_STARTED'
        marker['runs'][kind] = run_id
        write_json(root / 'owner.json', marker)
        before = probe(root, 'audit')
        write_json(root / (kind + '-offsets-before.json'), before)
        offsets = before['result']
        require(offsets[kind]['zookeeperOffset'] == '0' and not offsets[kind]['owners']
                and offsets['endOffsets'][kind] == (10000 if kind == 'ordinary' else 200),
                'Dedicated fixture initial offsets changed or another consumer owns the group; no graph submitted')
        if kind == 'illegal':
            require(offsets['endOffsets']['egress'] == 0, 'Dedicated egress topic is no longer empty; no graph submitted')
        capabilities = worker_request('GET', '/capabilities')
        types = set(manifest['graphs'][kind]['pluginTypes'])
        classes = [{k: item[k] for k in ['id', 'aliases', 'className', 'loadable', 'executionSupported', 'descriptor', 'source', 'sourceJar', 'classSource'] if k in item}
                   for item in capabilities.get('steps', []) if types.intersection(item.get('id', '').split(',') + item.get('aliases', []))]
        require(len(classes) >= len(types) and all(item.get('loadable') for item in classes), 'A required original business plugin is not loadable')
        bridge_classes = {str(path.relative_to(WORKER / 'classes')): fixture.sha(path.read_bytes()) for path in (WORKER / 'classes').rglob('*.class')}
        native_manifest = json.loads((WORKER / 'manifest.json').read_text())
        write_json(root / (kind + '-sourceclasses.json'), {'steps': classes, 'workerManifestSha256': fixture.sha((WORKER / 'manifest.json').read_bytes()),
                   'bridgeClassSha256': bridge_classes, 'libraries': native_manifest.get('libraries', [])})
        try:
            prepared = worker_request('PUT', '/jobs/' + job_id, {'xml': job, 'inputFiles': inputs})
            write_json(root / (kind + '-native-validation.json'), prepared)
            require(prepared.get('validation', {}).get('valid') is True, 'Native original Job validation failed; no run submitted')
            journal['state'] = 'SUBMITTING'
            write_json(journal_path, journal)
            result = worker_request('POST', '/runs', {'jobId': job_id, 'runId': run_id, 'mode': 'run'})
            journal['state'] = 'ACCEPTED'
            write_json(root / (kind + '-initial-run.json'), result)
            write_json(journal_path, journal)
        except Exception:
            journal['state'] = 'SUBMISSION_UNKNOWN' if journal['state'] == 'SUBMITTING' else 'PREPARATION_FAILED'
            write_json(journal_path, journal)
            raise RuntimeError('Graph preparation/submission was not confirmed; retain the journal and use read-only reconciliation') from None
    run_id = journal['runId']
    deadline = time.monotonic() + 210
    while time.monotonic() < deadline:
        result = worker_request('GET', '/runs/' + run_id)
        write_json(root / (kind + '-latest-run.json'), result)
        event_response = worker_request('GET', '/runs/' + run_id + '/events?after=' + str(journal['eventsCursor']))
        events = event_response.get('events', [])
        if events:
            with (root / (kind + '-events.ndjson')).open('a', encoding='utf-8') as output:
                for event in events:
                    output.write(fixture.compact(event) + '\n')
        journal['eventsCursor'] = event_response.get('nextCursor', journal['eventsCursor'])
        journal['state'] = result['state']
        write_json(journal_path, journal)
        if result['state'] in ('SUCCEEDED', 'FAILED', 'STOPPED', 'TIMED_OUT', 'INTERRUPTED') and (result.get('finishedAt') is not None or result.get('finalized') is True):
            break
        time.sleep(1)
    else:
        raise RuntimeError('Original job did not finalize inside the bounded observation window; no repeat submission or forced stop performed')
    write_json(root / (kind + '-offsets-after.json'), probe(root, 'audit'))
    destination = capture_ftp(root, kind, manifest)
    egress = None
    if kind == 'illegal':
        egress = root / 'illegal-egress.ndjson'
        write_json(root / 'illegal-egress-capture.json', probe(root, 'capture', egress))
    require(result['state'] == 'SUCCEEDED' and result.get('exitCode') == 0, 'Original job did not succeed; preserved terminal/events/offsets/FTP evidence, inspect without changing processing rules')
    validation = fixture.verify_outputs(root / 'prepared', kind, destination, egress)
    write_json(root / (kind + '-artifact-validation.json'), validation)
    return {'state': result['state'], 'runId': run_id, 'kind': kind, 'fileCount': validation['fileCount'],
            'fileRows': validation['fileRows'], 'kafkaRows': validation['kafkaRows']}


def main():
    os.umask(0o077)
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('action', choices=['prepare', 'run', 'resume'])
    parser.add_argument('--root', required=True)
    parser.add_argument('--kind', choices=['ordinary', 'illegal'])
    parser.add_argument('--broker-ready', action='store_true')
    args = parser.parse_args()
    result = prepare(args.root) if args.action == 'prepare' else run(args.root, args.kind, args.action == 'resume', args.broker_ready)
    print(json.dumps(result, ensure_ascii=False))


if __name__ == '__main__':
    main()
