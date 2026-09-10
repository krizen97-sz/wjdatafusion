#!/usr/bin/env python3
"""Bounded synthetic Docker-network acceptance. Default is plan; only --execute opens listeners/runs Docker."""
import argparse
from contextlib import ExitStack
import importlib.util
import json
import os
from pathlib import Path
import re
import socket
import stat
import sys
import threading
import time
import uuid
import xml.etree.ElementTree as ET

ASSETS = Path(__file__).resolve().parent
SPEC = importlib.util.spec_from_file_location('kettle_linux_runtime', ASSETS.parents[2] / 'tools/data-governance/kettle_linux_runtime.py')
module = importlib.util.module_from_spec(SPEC); sys.modules[SPEC.name] = module; SPEC.loader.exec_module(module)
HOST = '172.17.0.1'
PORTS = (39090, 39091)


def checked_config(config):
    module.require(config.endpoints == ((HOST, PORTS[0]),), 'Network smoke requires exactly the trusted docker0 endpoint 172.17.0.1:39090; no other endpoint may be authorized')
    return config


def prepare(config):
    checked_config(config)
    operation = config.operations_root / ('linux-network-smoke-' + uuid.uuid4().hex)
    operation.mkdir(mode=0o700)
    for name in ['output', 'home', 'tmp']: (operation / name).mkdir(mode=0o700)
    tree = ET.parse(ASSETS / 'fixtures/network-smoke.ktr')
    tree.find('./step[type="ScriptValueMod"]/jsScripts/jsScript/jsScript_script').text = (ASSETS / 'fixtures/network-smoke.js').read_text()
    tree.write(operation / 'transformation.ktr', encoding='UTF-8', xml_declaration=True)
    (operation / 'transformation.ktr').chmod(0o600)
    return operation


def dns_rules(record):
    # Docker redirects embedded DNS :53 in nat OUTPUT before filter OUTPUT. Match conntrack's
    # ORIGINAL tuple, not the translated ephemeral destination port.
    return {protocol: ['-p', protocol, '-m', 'conntrack', '--ctorigdst', '127.0.0.11', '--ctorigdstport', '53', '--ctdir', 'ORIGINAL', '-m', 'comment',
                       '--comment', 'rynew-network-smoke:' + record['nonce'][:20] + ':dns-' + protocol, '-j', 'DROP']
            for protocol in ['tcp', 'udp']}


def plan_network(controller, operation):
    record = controller.plan(operation, 'run')
    prefix = [controller.config.nsenter, '--net=/proc/self/fd/<PINNED_NETNS_FD>', '--', controller.config.iptables, '-w', '5']
    return {'executed': False, 'operationDir': str(operation), 'adapter': record,
            'listenerBindings': [[HOST, port] for port in PORTS], 'listenerLifetime': 'Only this foreground invocation; try/finally closes and joins both listeners',
            'dnsCounterRules': [[*prefix, '-I', record['namespaceChain'], '1', *rule] for rule in dns_rules(record).values()],
            'dnsCounterRead': [*prefix, '-L', record['namespaceChain'], '-n', '-v', '-x'],
            'sequence': ['bind both unused docker0 ports', 'launch through reviewed adapter policy and gate',
                         'wait for synthetic script ready', 'install/read back own namespace DNS DROP rules; snapshot counters',
                         'publish owned GO file', 'Java probes allowed/denied/DNS TCP/UDP',
                         'wait for script finished; snapshot counters', 'publish owned OBSERVED file',
                         'original TextFileOutput and native terminal', 'verify listener counts/result/counters',
                         'adapter cleanup only exact owned resources', 'close both fixture listeners'],
            'acceptance': {'allowedConnections': 1, 'allowedNonceAcknowledgements': 1, 'deniedConnections': 0,
                           'dnsTcpDropDeltaAtLeast': 1, 'dnsUdpDropDeltaAtLeast': 1, 'nativeState': 'SUCCEEDED'},
            'failure': 'Stop only confirmed own container; keep exact adapter journal/resources for reconciliation; never flush/prune or retry same run'}


class Listener:
    def __init__(self, port, nonce, socket_factory=socket.socket):
        module.require(port in PORTS and re.fullmatch(r'[a-f0-9]{64}', nonce), 'Unreviewed fixture listener')
        self.port = port; self.nonce = nonce; self.factory = socket_factory
        self.socket = None; self.thread = None; self.stopping = threading.Event()
        self.connections = 0; self.acknowledgements = 0; self.errors = []
    def __enter__(self):
        self.socket = self.factory(socket.AF_INET, socket.SOCK_STREAM)
        try:
            # Do not reuse another process's listener, bind wildcard, or start a background service.
            self.socket.bind((HOST, self.port)); self.socket.listen(4); self.socket.settimeout(0.2)
            self.thread = threading.Thread(target=self._serve, name='owned-kettle-fixture-' + str(self.port), daemon=True)
            self.thread.start(); return self
        except Exception:
            self.socket.close(); raise
    def _serve(self):
        while not self.stopping.is_set():
            try: connection, peer = self.socket.accept()
            except socket.timeout: continue
            except OSError as error:
                if not self.stopping.is_set(): self.errors.append(type(error).__name__)
                return
            self.connections += 1
            with connection:
                connection.settimeout(2)
                try:
                    data = b''
                    while b'\n' not in data and len(data) <= 1024:
                        block = connection.recv(1024)
                        if not block: break
                        data += block
                    if self.port == PORTS[0] and data == ('KETTLE_NETWORK_SMOKE:' + self.nonce + '\n').encode():
                        connection.sendall(('ACK:' + self.nonce + '\n').encode()); self.acknowledgements += 1
                except OSError as error: self.errors.append(type(error).__name__)
    def snapshot(self):
        return {'host': HOST, 'port': self.port, 'connections': self.connections, 'acknowledgements': self.acknowledgements, 'errors': list(self.errors)}
    def __exit__(self, *args):
        self.stopping.set()
        if self.socket: self.socket.close()
        if self.thread: self.thread.join(timeout=3)
        module.require(not self.thread or not self.thread.is_alive(), 'Fixture listener did not exit')


def read_status(path, nonce, phase):
    if not path.exists(): return None
    fd = os.open(path, os.O_RDONLY | os.O_NOFOLLOW)
    with os.fdopen(fd) as source:
        info = os.fstat(source.fileno())
        module.require(stat.S_ISREG(info.st_mode) and info.st_size <= 16384, 'Unexpected synthetic status file')
        data = json.load(source)
    module.require(data.get('nonce') == nonce and data.get('phase') == phase, 'Synthetic status identity differs')
    return data


def await_status(operation, phase, nonce, handle, timeout=25):
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        data = read_status(operation / 'output' / ('network-' + phase + '.json'), nonce, phase)
        if data is not None: return data
        module.require(handle.poll() is None, 'Native container ended before the fixture checkpoint')
        time.sleep(0.1)
    raise RuntimeError('Synthetic ' + phase + ' checkpoint timed out')


def publish_ack(config, operation, nonce, phase):
    module.require(operation.parent == config.operations_root and phase in {'GO', 'OBSERVED'} and re.fullmatch(r'[a-f0-9]{64}', nonce), 'Unreviewed fixture acknowledgement')
    target = operation / 'output' / ('network-probe.go' if phase == 'GO' else 'network-observed.go')
    module.path_checked(target.parent)
    module.atomic_write(target, nonce + ':' + phase + '\n', 0o600, owner=(config.uid, config.gid))


def parse_counters(text, rules):
    found = {}
    for protocol, rule in rules.items():
        comment = rule[rule.index('--comment') + 1]
        lines = [line for line in text.splitlines() if '/* ' + comment + ' */' in line]
        module.require(len(lines) == 1, 'Missing/duplicate exact fixture DNS counter comment')
        columns = lines[0].split()
        module.require(len(columns) >= 9 and columns[0].isdigit() and columns[1].isdigit() and columns[2] == 'DROP' and columns[3] == protocol, 'Unexpected DNS counter rule format')
        found[protocol] = {'packets': int(columns[0]), 'bytes': int(columns[1]), 'comment': comment}
    return found


def namespace_snapshot(controller, record, install=False):
    container = controller._inspect(record); controller._network(record)
    rules = dns_rules(record)
    with controller.namespace(container) as fd:
        prefix = [controller.config.nsenter, '--net=/proc/self/fd/' + str(fd), '--', controller.config.iptables, '-w', '5']
        if install:
            for rule in rules.values(): controller.runner.run([*prefix, '-I', record['namespaceChain'], '1', *rule], pass_fds=(fd,))
        raw_rules = controller.runner.run([*prefix, '-S', record['namespaceChain']], pass_fds=(fd,)).stdout
        for rule in rules.values():
            # -C compares the actual rule semantically, avoiding differences in iptables -S formatting.
            controller.runner.run([*prefix, '-C', record['namespaceChain'], *rule], pass_fds=(fd,))
        raw = controller.runner.run([*prefix, '-L', record['namespaceChain'], '-n', '-v', '-x'], pass_fds=(fd,)).stdout
    return {'counters': parse_counters(raw, rules), 'rawCounters': raw, 'rawRules': raw_rules}


def verify(proof, result):
    nonce = proof['nonce']; probes = result.get('probes', [])
    module.require(result.get('nonce') == nonce and len(probes) == 4 and [p.get('name') for p in probes] == ['allowed', 'denied', 'dns-tcp', 'dns-udp'], 'Native output is not this exact synthetic probe set')
    expected = [(HOST, 39090, 'tcp'), (HOST, 39091, 'tcp'), ('127.0.0.11', 53, 'tcp'), ('127.0.0.11', 53, 'udp')]
    module.require([(p.get('host'), p.get('port'), p.get('protocol')) for p in probes] == expected, 'Native target evidence differs')
    module.require(probes[0].get('connected') is True and probes[0].get('acknowledged') is True, 'Allowed native socket did not complete the nonce roundtrip')
    module.require(probes[1].get('connected') is False and probes[1].get('error') and probes[2].get('connected') is False and probes[2].get('error'), 'Denied/DNS TCP socket was not rejected')
    module.require(probes[3].get('response') is False and probes[3].get('error'), 'DNS UDP received a response or had no rejection evidence')
    listeners = proof['listeners']
    module.require(listeners[0]['connections'] == 1 and listeners[0]['acknowledgements'] == 1 and not listeners[0]['errors'] and listeners[1]['connections'] == 0, 'Actual listener counts differ from the exact 1 allowed / 0 denied acceptance')
    deltas = {}
    for protocol in ['tcp', 'udp']:
        before = proof['before']['counters'][protocol]; after = proof['after']['counters'][protocol]
        module.require(before['comment'] == after['comment'], 'DNS counter identity changed')
        deltas[protocol] = after['packets'] - before['packets']
        module.require(deltas[protocol] >= 1, 'No kernel DROP packet evidence for DNS ' + protocol)
    return deltas


def execute(config, operation, plan, controller):
    module.require(sys.platform == 'linux' and os.geteuid() == 0 and config.execution_enabled and config.stage_run_owner,
                   'Network smoke needs the reviewed Linux root controller, execution_enabled=true and stage_run_owner=true')
    devices = json.loads(controller.runner.run(['/usr/sbin/ip', '-j', '-4', 'addr', 'show', 'dev', 'docker0']).stdout)
    module.require(any(item.get('ifname') == 'docker0' and any(addr.get('local') == HOST for addr in item.get('addr_info', [])) for item in devices), 'Expected docker0 IPv4 is absent; do not bind a different interface')
    record = plan['adapter']; nonce = record['nonce']; handle = None; reader = None; events = []; reader_errors = []; listeners = []
    proof = {'executed': True, 'verified': False, 'nonce': nonce, 'operationDir': str(operation), 'journal': str(config.state_root / (operation.name + '.json')),
             'docker0': HOST, 'listeners': [], 'cleanup': None}
    try:
        with ExitStack() as stack:
            for port in PORTS: listeners.append(stack.enter_context(Listener(port, nonce)))
            error_log = stack.enter_context((operation / 'engine.log').open('w'))
            event_log = stack.enter_context((operation / 'events.ndjson').open('w'))
            handle = controller.launch(operation, 'run', launch_id=nonce, stderr=error_log)
            record = controller._read(operation.name); proof['container'] = handle.identity()
            def read_events():
                try:
                    for line in handle.stdout:
                        module.require(len(events) < 1200 and len(line) <= 131072, 'Native event evidence limit exceeded')
                        event_log.write(line); event_log.flush(); events.append(json.loads(line))
                except Exception as error: reader_errors.append(str(error))
            reader = threading.Thread(target=read_events, name='owned-kettle-events', daemon=True); reader.start()
            await_status(operation, 'ready', nonce, handle)
            proof['before'] = namespace_snapshot(controller, record, install=True)
            module.atomic_write(operation / 'network-observation.json', json.dumps(proof, indent=2))
            publish_ack(config, operation, nonce, 'GO')
            finished = await_status(operation, 'finished', nonce, handle, timeout=15)
            proof['after'] = namespace_snapshot(controller, record)
            proof['listeners'] = [listener.snapshot() for listener in listeners]
            proof['dnsDropPacketDeltas'] = verify(proof, finished)
            publish_ack(config, operation, nonce, 'OBSERVED')
            code = handle.wait(timeout=30); reader.join(timeout=3)
            module.require(not reader.is_alive() and not reader_errors and code == 0 and any(e.get('type') == 'terminal' and e.get('state') == 'SUCCEEDED' for e in events), 'Original worker did not finish with native SUCCEEDED')
            result_path = operation / 'output/network-result.json'
            result = json.loads(result_path.read_text())
            module.require(result == {'nonce': nonce, 'probes': finished['probes']}, 'Original TextFileOutput bytes do not match observed Java probe results')
            proof['dnsDropPacketDeltas'] = verify(proof, result)
            proof['output'] = {'bytes': result_path.stat().st_size, 'sha256': module.digest(result_path)}
            proof['nativeState'] = 'SUCCEEDED'; proof['cleanup'] = controller.cleanup(operation.name); proof['verified'] = True
        # Verify the whole listener lifetime, including the final native output/cleanup interval.
        proof['listeners'] = [listener.snapshot() for listener in listeners]
        proof['dnsDropPacketDeltas'] = verify(proof, result)
    except Exception as error:
        proof['verified'] = False
        proof['error'] = type(error).__name__ + ': ' + str(error); proof['recoveryRequired'] = True
        # Reconcile only the nonce-bound resource, including a launch that failed after creating its container.
        try:
            identity = controller.identity_for_run(operation.name)
            module.require(identity['nonce'] == nonce, 'Refusing stop for a different launch identity')
            proof['container'] = identity
            if controller.status(operation.name)['running']:
                controller.request_stop(operation.name, expected_nonce=nonce)
                deadline = time.monotonic() + 3
                while controller.status(operation.name)['running'] and time.monotonic() < deadline: time.sleep(0.1)
                if controller.status(operation.name)['running']: controller.request_stop(operation.name, force=True, expected_nonce=nonce)
            proof['failureState'] = controller.recover(operation.name)
        except Exception as recovery_error: proof['recoveryError'] = type(recovery_error).__name__ + ': ' + str(recovery_error)
    finally:
        if reader: reader.join(timeout=3)
        proof['listenersClosed'] = all(not listener.thread or not listener.thread.is_alive() for listener in listeners)
        proof['eventCount'] = len(events); proof['eventReaderErrors'] = reader_errors
        module.atomic_write(operation / 'network-acceptance.json', json.dumps(proof, indent=2))
    return proof


def main():
    os.umask(0o077)
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--config', required=True, type=Path); parser.add_argument('--execute', action='store_true')
    args = parser.parse_args(); config = checked_config(module.Config.load(args.config))
    operation = prepare(config); controller = module.LinuxRuntime(config)
    plan = plan_network(controller, operation)
    module.atomic_write(operation / 'network-plan.json', json.dumps(plan, indent=2))
    proof = execute(config, operation, plan, controller) if args.execute else plan
    print(json.dumps(proof, indent=2))
    if args.execute and not proof['verified']: raise SystemExit(1)


if __name__ == '__main__': main()
