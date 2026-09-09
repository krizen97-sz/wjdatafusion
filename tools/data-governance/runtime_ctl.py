#!/usr/bin/env python3
"""Manage only the isolated RYNEW data-governance development runtime."""
import argparse
import ftplib
import json
import os
from pathlib import Path
import secrets
import re
import signal
import socket
import ssl
import subprocess
import sys
import time
import urllib.parse
import urllib.request

OWNER = 'rynew-data-governance-runtime'
PORTS = {'nifi': 9443, 'postgres': 15432, 'ftp': 2121, 'mysql': 13306, 'redis': 16379}


def read_json(path):
    return json.loads(path.read_text())


def save_json(path, value):
    path.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
    tmp = path.with_suffix(path.suffix + '.tmp')
    fd = os.open(tmp, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
    with os.fdopen(fd, 'w') as stream:
        json.dump(value, stream, indent=2)
    tmp.replace(path)
    path.chmod(0o600)


def runtime_root(value):
    root = Path(value).expanduser().resolve()
    marker = read_json(root / 'runtime.json')
    if marker.get('owner') != OWNER or marker.get('runtimeRoot') != str(root):
        raise RuntimeError('runtime owner marker mismatch')
    if root == Path.home() or root == Path('/'):
        raise RuntimeError('unsafe runtime root')
    for key in ('nifiHome', 'javaHome'):
        if key not in marker or not Path(marker[key]).resolve().is_relative_to(root):
            raise RuntimeError('runtime component path escapes owned root')
    return root, marker


def read_private_json(root, filename):
    path = root / 'private' / filename
    if not path.resolve().is_relative_to((root / 'private').resolve()) or path.stat().st_mode & 0o077:
        raise RuntimeError('private credential path or mode is unsafe')
    return read_json(path)


def process_identity(pid):
    if not isinstance(pid, int) or pid <= 1:
        return None
    result = subprocess.run(['/bin/ps', '-p', str(pid), '-o', 'lstart=', '-o', 'command='], capture_output=True, text=True)
    return result.stdout.strip() if result.returncode == 0 else None


def assert_owned_process(record, root):
    current = process_identity(record.get('pid'))
    if not current:
        return False
    if not record.get('token') or current != record.get('identity') or str(root) not in current or record['token'] not in current:
        raise RuntimeError('PID ownership check failed; refusing to signal process')
    return True


def free_port(port):
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as sock:
        sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        sock.bind(('127.0.0.1', port))


def command(root, argv, name, env=None):
    with (root / 'private' / (name + '.log')).open('ab') as log:
        result = subprocess.run(argv, stdout=log, stderr=log, env=env)
    if result.returncode:
        raise RuntimeError(name + ' failed; inspect private log locally')


def launch(root, name, argv, token, env=None, cwd=None):
    state_path = root / 'services.json'
    state = read_json(state_path) if state_path.exists() else {}
    if name in state and assert_owned_process(state[name], root):
        return {'service': name, 'already_running': True, 'pid': state[name]['pid']}
    free_port(PORTS[name])
    with (root / 'private' / (name + '-daemon.log')).open('ab') as log:
        proc = subprocess.Popen(argv, cwd=cwd, env=env, stdout=log, stderr=log, start_new_session=True)
    time.sleep(0.25)
    identity = process_identity(proc.pid)
    if not identity or str(root) not in identity or token not in identity:
        raise RuntimeError(name + ' did not start with expected identity; no unknown process was stopped')
    state[name] = {'pid': proc.pid, 'identity': identity, 'token': token, 'port': PORTS[name]}
    save_json(state_path, state)
    return {'service': name, 'started': True, 'pid': proc.pid}


def assert_owned_directory(root, directory):
    if not directory.resolve().is_relative_to(root.resolve()):
        raise RuntimeError('service directory escapes runtime root')
    marker = read_json(directory / 'owner.json')
    if marker.get('owner') != OWNER or marker.get('runtimeRoot') != str(root):
        raise RuntimeError('service directory owner mismatch')


def prepare_mysql(root):
    home = root / 'mysql'
    if (home / 'data').exists():
        if not (home / 'owner.json').exists():
            raise RuntimeError('refusing to adopt pre-existing MySQL datadir')
        assert_owned_directory(root, home)
        return
    home.mkdir(mode=0o700, exist_ok=True)
    password = secrets.token_urlsafe(36)
    save_json(root / 'private/mysql-credentials.json', {'host': '127.0.0.1', 'port': PORTS['mysql'], 'username': 'root', 'password': password, 'socket': str(home / 'mysql.sock')})
    config = '[client]\nuser=root\npassword=' + password + '\nprotocol=SOCKET\nsocket=' + str(home / 'mysql.sock') + '\n'
    (root / 'private/mysql-client.cnf').write_text(config)
    (root / 'private/mysql-client.cnf').chmod(0o600)
    (home / 'my.cnf').write_text('[mysqld]\nbasedir=/usr/local/mysql\ndatadir=' + str(home / 'data') + '\nsocket=' + str(home / 'mysql.sock') + '\nport=13306\nbind-address=127.0.0.1\nmysqlx=0\npid-file=' + str(home / 'mysqld.pid') + '\nlog-error=' + str(root / 'private/mysql-error.log') + '\ninnodb_buffer_pool_size=128M\nmax_connections=40\n')
    (home / 'my.cnf').chmod(0o600)
    command(root, ['/usr/local/mysql/bin/mysqld', '--defaults-file=' + str(home / 'my.cnf'), '--initialize'], 'mysql-initialize')
    init = root / 'private/mysql-initial-password.sql'
    init.write_text("ALTER USER 'root'@'localhost' IDENTIFIED BY '" + password + "';\n")
    init.chmod(0o600)
    save_json(home / 'owner.json', {'owner': OWNER, 'runtimeRoot': str(root)})


def prepare_redis(root):
    home = root / 'redis'
    home.mkdir(mode=0o700, exist_ok=True)
    credential = root / 'private/redis-credentials.json'
    if credential.exists():
        return
    password = secrets.token_urlsafe(36)
    save_json(credential, {'host': '127.0.0.1', 'port': PORTS['redis'], 'password': password})
    (home / 'redis.conf').write_text('bind 127.0.0.1\nport 16379\nprotected-mode yes\nset-proc-title no\ndaemonize no\ndir ' + str(home) + '\nrequirepass ' + password + '\nappendonly yes\npidfile ' + str(home / 'redis.pid') + '\nlogfile ' + str(root / 'private/redis-server.log') + '\n')
    (home / 'redis.conf').chmod(0o600)


def prepare_postgres(root):
    home = root / 'postgres'
    if (home / 'data').exists():
        if not (home / 'owner.json').exists():
            raise RuntimeError('refusing to adopt pre-existing PostgreSQL datadir')
        assert_owned_directory(root, home)
        return
    home.mkdir(mode=0o700, exist_ok=True)
    (home / 'socket').mkdir(mode=0o700, exist_ok=True)
    password = secrets.token_urlsafe(36)
    save_json(root / 'private/postgres-credentials.json', {'host': '127.0.0.1', 'port': PORTS['postgres'], 'username': 'dg_fixture', 'password': password, 'database': 'postgres'})
    pw = root / 'private/postgres-password.txt'
    pw.write_text(password)
    pw.chmod(0o600)
    command(root, ['/opt/homebrew/opt/postgresql@17/bin/initdb', '-D', str(home / 'data'), '-U', 'dg_fixture', '--pwfile=' + str(pw), '--auth-host=scram-sha-256', '--auth-local=scram-sha-256', '--encoding=UTF8', '--locale=C'], 'postgres-initdb')
    with (home / 'data/postgresql.conf').open('a') as stream:
        stream.write("\nlisten_addresses='127.0.0.1'\nport=15432\nunix_socket_directories=''\nshared_buffers='64MB'\nmax_connections=30\n")
    pgpass = root / 'private/pgpass'
    pgpass.write_text('127.0.0.1:15432:*:dg_fixture:' + password + '\n')
    pgpass.chmod(0o600)
    save_json(home / 'owner.json', {'owner': OWNER, 'runtimeRoot': str(root)})


def prepare_ftp(root):
    home = root / 'ftp/files'
    home.mkdir(mode=0o700, parents=True, exist_ok=True)
    credential = root / 'private/ftp-credentials.json'
    if not credential.exists():
        save_json(credential, {'host': '127.0.0.1', 'port': PORTS['ftp'], 'username': 'dg_fixture', 'password': secrets.token_urlsafe(36), 'root': str(home)})
    target = root / 'bin/ftp_fixture_server.py'
    source = Path(__file__).with_name('ftp_fixture_server.py')
    target.write_text(source.read_text())
    target.chmod(0o700)


def start(root, marker, name):
    if name == 'nifi':
        env = os.environ.copy()
        env['JAVA_HOME'] = marker['javaHome']
        return launch(root, name, [marker['nifiHome'] + '/bin/nifi.sh', 'run'], marker['nifiHome'], env=env, cwd=marker['nifiHome'])
    if name == 'mysql':
        prepare_mysql(root)
        cfg = str(root / 'mysql/my.cnf')
        argv = ['/usr/local/mysql/bin/mysqld', '--defaults-file=' + cfg]
        init = root / 'private/mysql-initial-password.sql'
        if init.exists():
            argv.append('--init-file=' + str(init))
        return launch(root, name, argv, cfg)
    if name == 'redis':
        prepare_redis(root)
        cfg = str(root / 'redis/redis.conf')
        return launch(root, name, ['/opt/homebrew/bin/redis-server', cfg], cfg)
    if name == 'postgres':
        prepare_postgres(root)
        data = str(root / 'postgres/data')
        return launch(root, name, ['/opt/homebrew/opt/postgresql@17/bin/postgres', '-D', data], data)
    if name == 'ftp':
        prepare_ftp(root)
        script = str(root / 'bin/ftp_fixture_server.py')
        return launch(root, name, [str(root / 'tools/ftp-venv/bin/python'), script, '--runtime-root', str(root)], script)
    raise RuntimeError('unknown service')


def stop(root, marker, name):
    path = root / 'services.json'
    state = read_json(path) if path.exists() else {}
    record = state.get(name)
    if not record or not assert_owned_process(record, root):
        return {'service': name, 'stopped': False, 'reason': 'no-live-owned-process'}
    if name == 'nifi':
        env = os.environ.copy()
        env['JAVA_HOME'] = marker['javaHome']
        command(root, [marker['nifiHome'] + '/bin/nifi.sh', 'stop'], 'nifi-stop', env)
    elif name == 'postgres':
        command(root, ['/opt/homebrew/opt/postgresql@17/bin/pg_ctl', '-D', str(root / 'postgres/data'), '-m', 'fast', '-w', 'stop'], 'postgres-stop')
    else:
        os.kill(record['pid'], signal.SIGTERM)
    deadline = time.monotonic() + 40
    while time.monotonic() < deadline:
        if not process_identity(record['pid']):
            state.pop(name, None)
            save_json(path, state)
            return {'service': name, 'stopped': True}
        time.sleep(0.2)
    raise RuntimeError(name + ' shutdown not confirmed; no forced or port-based kill attempted')


def health(root, name):
    try:
        if name == 'nifi':
            c = read_private_json(root, 'nifi-credentials.json')
            parsed = urllib.parse.urlsplit(c['baseUrl'])
            if parsed.scheme != 'https' or parsed.hostname not in ('localhost', '127.0.0.1') or parsed.port != PORTS[name]:
                raise RuntimeError('non-local NiFi URL refused')
            ctx = ssl.create_default_context(cafile=c['caCert'])
            body = urllib.parse.urlencode({'username': c['username'], 'password': c['password']}).encode()
            request = urllib.request.Request(c['baseUrl'] + '/nifi-api/access/token', data=body, headers={'Content-Type': 'application/x-www-form-urlencoded'})
            with urllib.request.urlopen(request, context=ctx, timeout=10) as response:
                token = response.read().decode()
            request = urllib.request.Request(c['baseUrl'] + '/nifi-api/flow/about', headers={'Authorization': 'Bearer ' + token})
            with urllib.request.urlopen(request, context=ctx, timeout=10) as response:
                about = json.load(response)
            with urllib.request.urlopen(c['baseUrl'] + '/nifi/', context=ctx, timeout=10) as response:
                ui_status = response.status
            return {'service': name, 'healthy': True, 'authenticated_api': True, 'ui_http_status': ui_status, 'version': about.get('about', {}).get('version')}
        if name == 'mysql':
            p = subprocess.run(['/usr/local/mysql/bin/mysql', '--defaults-file=' + str(root / 'private/mysql-client.cnf'), '-Nse', 'SELECT VERSION()'], capture_output=True, text=True, timeout=10)
            if p.returncode:
                raise RuntimeError('mysql-authentication-or-query-failed')
            init = root / 'private/mysql-initial-password.sql'
            if init.exists():
                init.unlink()
            error_log = root / 'private/mysql-error.log'
            if error_log.exists():
                redacted = re.sub(r'(?m)^.*temporary password.*$', '[REDACTED temporary initialization credential]', error_log.read_text(errors='replace'))
                error_log.write_text(redacted)
            return {'service': name, 'healthy': True, 'version': p.stdout.strip()}
        if name == 'postgres':
            env = os.environ.copy()
            env['PGPASSFILE'] = str(root / 'private/pgpass')
            p = subprocess.run(['/opt/homebrew/opt/postgresql@17/bin/psql', '-h', '127.0.0.1', '-p', '15432', '-U', 'dg_fixture', '-d', 'postgres', '-Atc', 'SHOW server_version'], env=env, capture_output=True, text=True, timeout=10)
            if p.returncode:
                raise RuntimeError('postgres-authentication-or-query-failed')
            return {'service': name, 'healthy': True, 'version': p.stdout.strip()}
        if name == 'redis':
            c = read_private_json(root, 'redis-credentials.json')
            with socket.create_connection(('127.0.0.1', PORTS[name]), timeout=5) as sock:
                pw = c['password'].encode()
                sock.sendall(b'*2\r\n$4\r\nAUTH\r\n$' + str(len(pw)).encode() + b'\r\n' + pw + b'\r\n')
                if sock.recv(1024) != b'+OK\r\n':
                    raise RuntimeError('redis-authentication-failed')
                sock.sendall(b'*1\r\n$4\r\nPING\r\n')
                if sock.recv(1024) != b'+PONG\r\n':
                    raise RuntimeError('redis-ping-failed')
            return {'service': name, 'healthy': True, 'authenticated_ping': True}
        if name == 'ftp':
            c = read_private_json(root, 'ftp-credentials.json')
            with ftplib.FTP() as ftp:
                ftp.connect('127.0.0.1', PORTS[name], timeout=5)
                ftp.login(c['username'], c['password'])
                ftp.voidcmd('NOOP')
            return {'service': name, 'healthy': True, 'authenticated_noop': True}
    except Exception as error:
        return {'service': name, 'healthy': False, 'error_type': type(error).__name__}
    return {'service': name, 'healthy': False}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--runtime-root', required=True)
    parser.add_argument('action', choices=['start', 'stop', 'health', 'status'])
    parser.add_argument('service', choices=list(PORTS) + ['all'])
    args = parser.parse_args()
    os.umask(0o077)
    root, marker = runtime_root(args.runtime_root)
    names = list(PORTS) if args.service == 'all' else [args.service]
    results = []
    for name in names:
        if args.action == 'start':
            results.append(start(root, marker, name))
        elif args.action == 'stop':
            results.append(stop(root, marker, name))
        elif args.action == 'status':
            path = root / 'services.json'
            state = read_json(path) if path.exists() else {}
            record = state.get(name)
            owned = bool(record and assert_owned_process(record, root))
            results.append({'service': name, 'owned_process_running': owned, 'pid': record['pid'] if owned else None, 'port': PORTS[name]})
        else:
            results.append(health(root, name))
    print(json.dumps(results, ensure_ascii=False))
    if args.action == 'health' and any(not item['healthy'] for item in results):
        return 1
    return 0


if __name__ == '__main__':
    try:
        sys.exit(main())
    except Exception as error:
        print(json.dumps({'error_type': type(error).__name__, 'message': 'Operation failed; no credentials are printed. Review private runtime logs.'}), file=sys.stderr)
        sys.exit(1)
