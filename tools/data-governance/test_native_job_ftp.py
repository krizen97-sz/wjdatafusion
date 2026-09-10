#!/usr/bin/env python3
"""Opt-in original Kettle Job proof. Creates only synthetic XML and its own loopback FTP fixture."""
import argparse
import ftplib
import hashlib
import io
import json
import logging
import os
from pathlib import Path
import queue
import secrets
import signal
import socket
import subprocess
import sys
import threading
import time
import uuid
import xml.etree.ElementTree as ET


def private_json(path, value):
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2))
    path.chmod(0o600)


def digest(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def element(parent, tag, text=None, **children):
    child = ET.SubElement(parent, tag)
    if text is not None:
        child.text = str(text)
    for key, value in children.items():
        element(child, key, value)
    return child


def text_output(parent, filename):
    step = element(parent, 'step', name='Write file', type='TextFileOutput', copies=1, distribute='Y',
                   separator='|', enclosure='', header='Y', footer='N', format='UNIX', compression='None',
                   encoding='UTF-8', endedLine='', fileNameInField='N', create_parent_folder='Y')
    element(step, 'file', name=filename, is_command='N', servlet_output='N', do_not_open_new_file_init='N',
            rename_file_name='N', extention='txt', append='N', split='N', haspartno='N', add_date='N',
            add_time='N', SpecifyFormat='N', add_to_result_filenames='Y', pad='N', fast_dump='N',
            splitevery=0, max_wait_time_ms=0)
    fields = element(step, 'fields')
    for name in ['id', 'message']:
        element(fields, 'field', name=name, type='String', format='', trim_type='none', length=-1, precision=-1)


def transformation(mode):
    trans = ET.Element('transformation')
    element(trans, 'info', name='Synthetic ' + mode, size_rowset=100)
    order = element(trans, 'order')
    source = element(trans, 'step', name='Synthetic input', type='DataGrid', copies=1, distribute='Y')
    fields = element(source, 'fields')
    for name in ['id', 'message']:
        element(fields, 'field', name=name, type='String', length=-1, precision=-1)
    data = element(source, 'data')
    for i in range(100 if mode == 'stop' else 3):
        line = element(data, 'line')
        element(line, 'item', str(i + 1))
        element(line, 'item', '原生作业合成验证-' + str(i + 1))
    if mode == 'failure':
        element(trans, 'step', name='Intentional failure', type='Abort', copies=1, distribute='Y',
                row_threshold=0, message='SYNTHETIC_EXPECTED_FAILURE', always_log_rows='N')
        element(order, 'hop', **{'from': 'Synthetic input', 'to': 'Intentional failure', 'enabled': 'Y'})
    else:
        previous = 'Synthetic input'
        if mode == 'stop':
            element(trans, 'step', name='Bounded delay', type='Delay', copies=1, distribute='Y', timeout=1, scaletime='seconds')
            element(order, 'hop', **{'from': previous, 'to': 'Bounded delay', 'enabled': 'Y'})
            previous = 'Bounded delay'
        text_output(trans, '${WORK_DIR}/' + mode)
        element(order, 'hop', **{'from': previous, 'to': 'Write file', 'enabled': 'Y'})
    return ET.tostring(trans, encoding='unicode')


def job_xml(mode, config):
    job = ET.Element('job')
    element(job, 'name', 'Native Job ' + mode)
    entries = element(job, 'entries')
    element(entries, 'entry', name='START', type='SPECIAL', start='Y', dummy='N', repeat='N', schedulerType=0,
            intervalSeconds=0, intervalMinutes=0, hour=12, minutes=0, weekDay=1, DayOfMonth=1, parallel='N', draw='Y', nr=0)
    child = element(entries, 'entry', name='Run original transformation', type='TRANS', specification_method='filename',
                    filename='${INPUT_DIR}/synthetic.ktr', transname='', trans_object_id='', arg_from_previous='N',
                    params_from_previous='N', exec_per_row='N', clear_rows='N', clear_files='N', set_logfile='N',
                    logfile='', logext='log', add_date='N', add_time='N', loglevel='Basic', cluster='N',
                    slave_server_name='', set_append_logfile='N', wait_until_finished='Y', follow_abort_remote='N',
                    create_parent_folder='N', logging_remote_work='N', parallel='N', draw='Y', nr=0)
    element(child, 'parameters', pass_all_parameters='Y')
    element(entries, 'entry', name='Upload synthetic file', type='FTP_PUT', servername='127.0.0.1',
            serverport=config['controlPort'], username=config['username'], password=config['password'],
            remoteDirectory=mode, localDirectory='${WORK_DIR}', wildcard=r'.*', binary='Y', timeout=5000,
            remove='N', rename='N', renameSuffix='.tmp', only_new='N', active='N', control_encoding='UTF-8',
            proxy_host='', proxy_port='', proxy_username='', proxy_password='', socksproxy_host='', socksproxy_port='',
            socksproxy_username='', socksproxy_password='', parallel='N', draw='Y', nr=0)
    hops = element(job, 'hops')
    element(hops, 'hop', **{'from': 'START', 'to': 'Run original transformation', 'from_nr': 0, 'to_nr': 0,
                           'enabled': 'Y', 'evaluation': 'Y', 'unconditional': 'Y'})
    element(hops, 'hop', **{'from': 'Run original transformation', 'to': 'Upload synthetic file', 'from_nr': 0, 'to_nr': 0,
                           'enabled': 'Y', 'evaluation': 'Y', 'unconditional': 'N'})
    return ET.tostring(job, encoding='unicode')


def serve_fixture(config_path):
    from pyftpdlib.authorizers import DummyAuthorizer
    from pyftpdlib.handlers import FTPHandler
    from pyftpdlib.servers import FTPServer
    config = json.loads(config_path.read_text())
    authorizer = DummyAuthorizer()
    authorizer.add_user(config['username'], config['password'], config['filesRoot'], perm='elradfmwMT')
    logging.disable(logging.CRITICAL)

    class Handler(FTPHandler):
        passive_ports = config['passivePorts']
        masquerade_address = '127.0.0.1'

        def on_file_received(self, filename):
            event = {'path': str(Path(filename).relative_to(config['filesRoot'])), 'sha256': digest(filename)}
            with Path(config['eventsFile']).open('a') as out:
                out.write(json.dumps(event) + '\n')

    Handler.authorizer = authorizer
    server = FTPServer(('127.0.0.1', 0), Handler)
    private_json(Path(config['readyFile']), {'pid': os.getpid(), 'controlPort': server.socket.getsockname()[1],
                                          'passivePorts': config['passivePorts']})
    server.serve_forever(timeout=0.1)


def sandbox(runtime, operation, classes, java_home, ports):
    literal = lambda path: json.dumps(str(Path(path).resolve()))
    reads = [runtime / 'lib', operation, classes, java_home, Path('/System'), Path('/usr/lib'), Path('/usr/share'),
             Path('/private/var/db/timezone')]
    lines = ['(version 1)', '(allow default)', '(deny network*)', '(deny process-fork)',
             '(deny mach-lookup (global-name "com.apple.mDNSResponder"))', '(deny file-read*)', '(deny file-write*)']
    lines += ['(allow file-read* (subpath ' + literal(p) + '))' for p in reads]
    lines += ['(allow file-read-metadata)',
              '(allow file-read* (literal "/") (literal "/dev/random") (literal "/dev/urandom") (literal "/dev/null") (literal "/private/etc/localtime"))',
              '(allow file-write* (subpath ' + literal(operation) + ') (literal "/dev/null"))']
    # Seatbelt requires the literal "localhost" token here, not an IPv4 address.
    lines += ['(allow network-outbound (remote tcp4 "localhost:' + str(port) + '"))' for port in ports]
    return '\n'.join(lines) + '\n'


def private_operation(base, name):
    operation = base / name
    operation.mkdir(mode=0o700)
    for folder in ['home', 'tmp', 'input', 'output']:
        (operation / folder).mkdir(mode=0o700)
    return operation


def java_command(runtime, classes, manifest, operation, ports, main, arguments):
    profile = operation / 'sandbox.sb'
    java_home = Path(manifest['javaHome'])
    profile.write_text(sandbox(runtime, operation, classes, java_home, ports))
    jars = [str(p) for p in map(Path, manifest['classpath'].split(os.pathsep)) if p.suffix == '.jar']
    return ['/usr/bin/sandbox-exec', '-f', str(profile), str(java_home / 'bin/java'), '-Xmx256m',
            '-XX:+PerfDisableSharedMem', '-Djava.awt.headless=true', '-Djava.net.preferIPv4Stack=true', '-Duser.timezone=UTC',
            '-DKETTLE_SYSTEM_HOSTNAME=native-job-proof', '-Duser.home=' + str(operation / 'home'),
            '-DKETTLE_HOME=' + str(operation / 'home'), '-DKETTLE_JNDI_ROOT=' + str(operation / 'home'),
            '-DKETTLE_PLUGIN_BASE_FOLDERS=' + str(operation / 'home/empty-plugins'),
            '-Djava.io.tmpdir=' + str(operation / 'tmp'), '-Dgovernance.job.timeout.seconds=25',
            '-cp', os.pathsep.join([str(classes)] + jars), main] + arguments


def process_env(operation):
    return {'PATH': '/usr/bin:/bin', 'HOME': str(operation / 'home'), 'LANG': 'en_US.UTF-8',
            'KETTLE_HOME': str(operation / 'home')}


def run_case(runtime, classes, manifest, base, mode, config, operation_name=None, worker_operation='job', ftp_directory=None, expected_exit=0):
    operation = private_operation(base, operation_name or mode)
    (operation / 'input/synthetic.ktr').write_text(transformation(mode))
    (operation / 'input/private-input.csv').write_text('SYNTHETIC_INPUT_ONLY_DO_NOT_UPLOAD\n')
    if mode == 'failure':
        (operation / 'output/failure-canary.txt').write_text('Must never reach FTP after failed transformation.\n')
    job = ET.fromstring(job_xml(mode, config))
    if ftp_directory is not None: job.find('./entries/entry[type="FTP_PUT"]/localDirectory').text = ftp_directory
    (operation / 'transformation.kjb').write_text(ET.tostring(job, encoding='unicode'))
    ports = [config['controlPort']] + config['passivePorts']
    command = java_command(runtime, classes, manifest, operation, ports, 'KettleWorker', [str(operation), worker_operation])
    messages = queue.Queue()
    events = []
    stop_sent = False
    with (operation / 'stderr.log').open('w') as stderr:
        process = subprocess.Popen(command, cwd=operation, env=process_env(operation), stdin=subprocess.PIPE,
                                   stdout=subprocess.PIPE, stderr=stderr, text=True, bufsize=1, start_new_session=True)
        private_json(operation / 'process.json', {'pid': process.pid, 'startedAt': time.time()})

        def receive():
            for line in process.stdout:
                messages.put(line)
            messages.put(None)

        reader = threading.Thread(target=receive, daemon=True)
        reader.start()
        deadline = time.monotonic() + 40
        try:
            with (operation / 'events.jsonl').open('w') as event_file:
                while time.monotonic() < deadline:
                    try:
                        line = messages.get(timeout=0.2)
                    except queue.Empty:
                        continue
                    if line is None:
                        break
                    assert config['password'] not in line, 'Synthetic secret appeared in worker events'
                    event_file.write(line); event_file.flush()
                    event = json.loads(line)
                    events.append(event)
                    if mode == 'stop' and event.get('type') == 'job-transformation' and event.get('state') == 'RUNNING' and not stop_sent:
                        time.sleep(0.2)
                        process.stdin.write('STOP\n'); process.stdin.flush(); stop_sent = True
                else:
                    process.send_signal(signal.SIGQUIT)
                    time.sleep(0.2)
                    raise AssertionError('Native Job exceeded proof deadline')
            process.wait(timeout=5)
        finally:
            if process.poll() is None:
                process.terminate()
                try:
                    process.wait(timeout=5)
                except subprocess.TimeoutExpired:
                    process.kill(); process.wait(timeout=5)
        assert process.returncode == expected_exit, 'Worker exit differs; inspect private stderr/events: ' + str(operation)
    assert config['password'] not in (operation / 'stderr.log').read_text()
    return operation, events, stop_sent


PROBE = r'''
import java.nio.file.*; import java.net.*; import java.io.*;
public class NativeJobSandboxProbe {
  public static void main(String[] args) throws Exception {
    boolean readDenied=false,writeDenied=false,networkDenied=false,allowedConnected=false;
    try { Files.readString(Path.of(args[0])); } catch(IOException|SecurityException e) {readDenied=true;}
    try { Files.writeString(Path.of(args[0]+".attempt"),"must be denied"); } catch(IOException|SecurityException e) {writeDenied=true;}
    try(Socket socket=new Socket()) {socket.connect(new InetSocketAddress("127.0.0.1",Integer.parseInt(args[1])),1000);}
    catch(IOException|SecurityException e){networkDenied=true;}
    try(Socket socket=new Socket()) {socket.connect(new InetSocketAddress("127.0.0.1",Integer.parseInt(args[2])),1000);allowedConnected=true;}
    System.out.println("{\"readDenied\":"+readDenied+",\"writeDenied\":"+writeDenied+",\"networkDenied\":"+networkDenied+",\"allowedConnected\":"+allowedConnected+"}");
    if(!readDenied||!writeDenied||!networkDenied||!allowedConnected)System.exit(1);
  }
}
'''


def proof(args):
    os.umask(0o077)
    runtime = args.worker_runtime.resolve()
    manifest = json.loads((runtime / 'manifest.json').read_text())
    for item in manifest['libraries']:
        assert digest(runtime / 'lib' / item['file']) == item['sha256'], 'Original library provenance mismatch'
    base = args.output.resolve() / ('proof-' + uuid.uuid4().hex)
    base.mkdir(parents=True, mode=0o700)
    private_json(base / 'owner.json', {'owner': 'rynew-native-job-proof', 'root': str(base)})
    classes = base / 'classes'; classes.mkdir(mode=0o700)
    probe = base / 'NativeJobSandboxProbe.java'; probe.write_text(PROBE)
    repo = Path(__file__).resolve().parents[2]
    executor = repo / 'data-governance/kettle-worker/src/NativeJobExecutor.java'
    worker_source = args.worker_source or repo / 'data-governance/kettle-worker/src/KettleWorker.java'
    command = [str(Path(manifest['javaHome']) / 'bin/javac'), '-proc:none', '-encoding', 'UTF-8',
               '-classpath', manifest['classpath'], '-d', str(classes), str(worker_source), str(executor), str(repo / 'data-governance/kettle-worker/src/NativeFtpBatchDelivery.java'), str(probe)]
    result = subprocess.run(command, capture_output=True, text=True)
    (base / 'compile.log').write_text(result.stdout + result.stderr)
    assert result.returncode == 0, 'Compile failed; inspect ' + str(base / 'compile.log')
    fixture = base / 'ftp'; fixture.mkdir(mode=0o700)
    files = fixture / 'files'; files.mkdir(mode=0o700)
    for name in ['success', 'failure', 'stop']:
        (files / name).mkdir(mode=0o700)
    # Reserve new loopback ports before assigning the fixture's passive data listeners.
    reservations = []
    for _ in range(4):
        listener = socket.socket(); listener.bind(('127.0.0.1', 0)); reservations.append(listener)
    passive = [s.getsockname()[1] for s in reservations]
    config = {'username': 'native-job-proof', 'password': secrets.token_urlsafe(24), 'filesRoot': str(files),
              'eventsFile': str(fixture / 'transfers.jsonl'), 'readyFile': str(fixture / 'ready.json'), 'passivePorts': passive}
    config_path = fixture / 'private.json'; private_json(config_path, config)
    for listener in reservations:
        listener.close()
    with (fixture / 'server.log').open('w') as log:
        # Preserve the venv entry path; resolving its symlink would select the system interpreter.
        process = subprocess.Popen([str(args.ftp_python.absolute()), str(Path(__file__).resolve()), '--serve', str(config_path)],
                                   stdout=log, stderr=log, start_new_session=True, env={'PATH': '/usr/bin:/bin', 'HOME': str(fixture)})
        try:
            deadline = time.monotonic() + 10
            while not Path(config['readyFile']).exists():
                assert process.poll() is None and time.monotonic() < deadline, 'FTP fixture did not start'
                time.sleep(0.1)
            ready = json.loads(Path(config['readyFile']).read_text()); assert ready['pid'] == process.pid
            config['controlPort'] = ready['controlPort']
            operations = base / 'operations'; operations.mkdir(mode=0o700)
            probe_op = private_operation(operations, 'sandbox-probe')
            protected = base / 'protected-marker'; protected.write_text('Synthetic data outside allowed operation')
            with socket.socket() as forbidden:
                forbidden.bind(('127.0.0.1', 0)); forbidden.listen(1); forbidden.settimeout(0.3)
                cmd = java_command(runtime, classes, manifest, probe_op, [config['controlPort']] + passive,
                                   'NativeJobSandboxProbe', [str(protected), str(forbidden.getsockname()[1]), str(config['controlPort'])])
                probe_result = subprocess.run(cmd, cwd=probe_op, env=process_env(probe_op), capture_output=True, text=True, timeout=10)
                assert probe_result.returncode == 0, 'OS sandbox probe failed: ' + probe_result.stderr
                sandbox_result = json.loads(probe_result.stdout)
                try:
                    accepted, _ = forbidden.accept(); accepted.close(); raise AssertionError('Unallowed loopback connection was accepted')
                except socket.timeout:
                    pass
            reports = {}
            for mode in ['success', 'failure', 'stop']:
                operation, events, stop_sent = run_case(runtime, classes, manifest, operations, mode, config)
                terminal = [e for e in events if e.get('type') == 'terminal']
                assert len(terminal) == 1, 'Exactly one terminal event required'
                terminal = terminal[0]
                assert terminal['state'] == {'success': 'SUCCEEDED', 'failure': 'FAILED', 'stop': 'STOPPED'}[mode]
                assert terminal['jobFinished'] and terminal['childrenFinished'], 'Terminal preceded native completion'
                ftp_started = [e for e in events if e.get('type') == 'job-entry' and e.get('phase') == 'BEFORE' and e.get('pluginId') == 'FTP_PUT']
                assert bool(ftp_started) == (mode == 'success'), 'FTP success-hop gate violated'
                assert any(e.get('type') == 'log' for e in events), 'Original logs were not captured'
                if mode == 'success':
                    local = operation / 'output/success.txt'
                    expected = ('id|message\n' + ''.join(str(i) + '|原生作业合成验证-' + str(i) + '\n' for i in range(1, 4))).encode()
                    assert local.read_bytes() == expected, 'Original TextFileOutput bytes differ'
                    with ftplib.FTP() as ftp:
                        ftp.connect('127.0.0.1', config['controlPort'], timeout=5); ftp.login(config['username'], config['password'])
                        received = io.BytesIO(); ftp.retrbinary('RETR success/success.txt', received.write)
                    assert received.getvalue() == expected, 'FTP RETR differs from original output'
                    assert sorted(p.name for p in (files / mode).iterdir()) == ['success.txt'], 'FTP wildcard uploaded an input or child definition'
                    assert sorted(p.name for p in (operation / 'output').iterdir()) == ['success.txt'], 'Inputs were mixed into output'
                    reports[mode] = {'terminal': terminal, 'bytes': len(expected), 'sha256': hashlib.sha256(expected).hexdigest(), 'ftpReadbackEqual': True, 'wildcardAllOnlyOutput': True}
                else:
                    assert not list((files / mode).iterdir()), 'Failed/stopped Job sent a file'
                    reports[mode] = {'terminal': terminal, 'ftpEntriesExecuted': len(ftp_started), 'remoteDirectoryEmpty': True,
                                     'stopRequested': stop_sent, 'afterEntries': [e for e in events if e.get('type') == 'job-entry' and e.get('phase') == 'AFTER']}
            _, validation, _ = run_case(runtime, classes, manifest, operations, 'success', config,
                                         operation_name='validate', worker_operation='job-validate')
            assert any(e.get('type') == 'validation' and e.get('valid') for e in validation)
            rejected_directories = []
            for index, directory in enumerate(['${INPUT_DIR}', '${JOB_DIR}', '']):
                _, invalid, _ = run_case(runtime, classes, manifest, operations, 'success', config,
                    operation_name='reject-directory-' + str(index), worker_operation='job-validate', ftp_directory=directory, expected_exit=1)
                assert any(e.get('type') == 'terminal' and e.get('state') == 'FAILED' for e in invalid)
                assert not any(e.get('type') == 'job-entry' for e in invalid)
                rejected_directories.append(directory or '(empty)')
            report = {'rejectedFtpDirectories': rejected_directories, 'passed': True, 'root': str(base), 'sourceSha256': digest(executor), 'workerSourceSha256': digest(worker_source),
                      'originalLibraryCount': len(manifest['libraries']), 'sandbox': sandbox_result,
                      'fixture': ready, 'cases': reports, 'validation': True}
            private_json(base / 'acceptance.json', report)
        finally:
            if process.poll() is None:
                process.terminate(); process.wait(timeout=10)
    report['fixtureStopped'] = process.poll() is not None
    private_json(base / 'acceptance.json', report)
    print(json.dumps({'passed': report['passed'], 'evidence': str(base / 'acceptance.json'), 'fixtureStopped': report['fixtureStopped'],
                      'successBytes': reports['success']['bytes'], 'failureFtpEntries': reports['failure']['ftpEntriesExecuted'],
                      'stopFtpEntries': reports['stop']['ftpEntriesExecuted']}, indent=2))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--serve', type=Path)
    parser.add_argument('--worker-runtime', type=Path)
    parser.add_argument('--worker-source', type=Path)
    parser.add_argument('--ftp-python', type=Path)
    parser.add_argument('--output', type=Path)
    arguments = parser.parse_args()
    if arguments.serve:
        serve_fixture(arguments.serve)
    else:
        if not all([arguments.worker_runtime, arguments.ftp_python, arguments.output]):
            parser.error('--worker-runtime, --ftp-python and --output are required for the opt-in proof')
        proof(arguments)
