"""Network acceptance script tests with fake Docker/listeners; no real socket or service operation."""
import copy
import importlib.util
import io
import json
import os
from pathlib import Path
import socket
import sys
import threading
import time
import unittest
from unittest import mock
import xml.etree.ElementTree as ET

ASSETS = Path(__file__).resolve().parents[1]
def load(name, file):
    spec = importlib.util.spec_from_file_location(name, file)
    module = importlib.util.module_from_spec(spec); sys.modules[name] = module; spec.loader.exec_module(module); return module
smoke = load('network_smoke', ASSETS / 'network_smoke.py')
base = load('linux_fixture_tests', ASSETS / 'test_kettle_linux_runtime.py')


def successful_evidence():
    probes = [dict(name='allowed', host=smoke.HOST, port=39090, protocol='tcp', connected=True, acknowledged=True),
              dict(name='denied', host=smoke.HOST, port=39091, protocol='tcp', connected=False, error='connect timeout'),
              dict(name='dns-tcp', host='127.0.0.11', port=53, protocol='tcp', connected=False, error='connect timeout'),
              dict(name='dns-udp', host='127.0.0.11', port=53, protocol='udp', sent=True, response=False, error='receive timeout')]
    proof = {'nonce': '7' * 64,
             'listeners': [dict(connections=1, acknowledgements=1, errors=[]), dict(connections=0, acknowledgements=0, errors=[])],
             'before': {'counters': {p: dict(packets=0, comment=p) for p in ['tcp', 'udp']}},
             'after': {'counters': {p: dict(packets=2, comment=p) for p in ['tcp', 'udp']}}}
    return proof, {'nonce': proof['nonce'], 'probes': probes}


class NetworkTests(unittest.TestCase):
    def setUp(self):
        self.base = base.LinuxTests(); self.base.setUp(); self.addCleanup(self.base.doCleanups)
        self.config = self.base.config(endpoints=[{'host': smoke.HOST, 'port': 39090}])
    def test_plan_does_not_call_docker_or_bind_socket(self):
        with mock.patch.object(smoke.socket, 'socket', side_effect=AssertionError('Network operation in plan')):
            operation = smoke.prepare(self.config)
            runner = base.FakeRunner(); controller = smoke.module.LinuxRuntime(self.config, runner=runner)
            plan = smoke.plan_network(controller, operation)
        self.assertEqual([], runner.calls); self.assertFalse(plan['executed'])
        self.assertEqual([[smoke.HOST, 39090], [smoke.HOST, 39091]], plan['listenerBindings'])
        self.assertEqual([(smoke.HOST, 39090)], plan['adapter']['endpoints'])
        self.assertEqual(2, len(plan['dnsCounterRules']))
        self.assertNotIn('ACCEPT', str(plan['dnsCounterRules'])); self.assertNotIn('-F', str(plan))
        self.assertEqual(['RowGenerator', 'ScriptValueMod', 'TextFileOutput'], [node.text for node in ET.parse(operation / 'transformation.ktr').findall('./step/type')])
        self.assertEqual('1', ET.parse(operation / 'transformation.ktr').findtext('./step[type="RowGenerator"]/limit'))
    def test_config_refuses_missing_extra_or_unreviewed_endpoints(self):
        for endpoints in [[], [{'host': smoke.HOST, 'port': 39091}], [{'host': smoke.HOST, 'port': 39090}, {'host': '10.0.0.1', 'port': 5432}]]:
            with self.assertRaises(RuntimeError): smoke.checked_config(self.base.config(endpoints=endpoints))
    def test_dns_rules_match_original_tuple_before_docker_dns_nat(self):
        for protocol, rule in smoke.dns_rules({'nonce': '7' * 64}).items():
            self.assertIn('--ctorigdst', rule); self.assertIn('127.0.0.11', rule)
            self.assertIn('--ctorigdstport', rule); self.assertIn('53', rule); self.assertIn('--ctdir', rule)
            self.assertEqual('DROP', rule[-1]); self.assertNotIn('--dport', rule)
    def test_kernel_counters_require_the_exact_owned_comment(self):
        rules = smoke.dns_rules({'nonce': '7' * 64})
        raw = '\n'.join('  2 120 DROP ' + protocol + ' -- * * 0.0.0.0/0 0.0.0.0/0 /* ' + rule[rule.index('--comment') + 1] + ' */' for protocol, rule in rules.items())
        self.assertEqual(2, smoke.parse_counters(raw, rules)['udp']['packets'])
        numeric = raw.replace('DROP tcp ', 'DROP 6 ').replace('DROP udp ', 'DROP 17 ')
        self.assertEqual(2, smoke.parse_counters(numeric, rules)['tcp']['packets'])
        self.assertEqual(2, smoke.parse_counters(numeric, rules)['udp']['packets'])
        with self.assertRaises(RuntimeError): smoke.parse_counters(numeric.replace('DROP 17 ', 'DROP 6 '), rules)
        with self.assertRaises(RuntimeError): smoke.parse_counters(numeric.replace('DROP 6 ', 'DROP 0 '), rules)
        with self.assertRaises(RuntimeError): smoke.parse_counters(raw.replace('rynew-network-smoke:', 'someone-else:'), rules)
        with self.assertRaises(RuntimeError): smoke.parse_counters(raw + '\n' + raw, rules)
    def test_dns_instrumentation_uses_only_pinned_owned_namespace(self):
        record = {'nonce': '7' * 64, 'namespaceChain': 'RYK_owned_synthetic'}
        rules = smoke.dns_rules(record)
        raw = '\n'.join('  0 0 DROP ' + protocol + ' -- * * 0.0.0.0/0 0.0.0.0/0 /* ' + rule[rule.index('--comment') + 1] + ' */' for protocol, rule in rules.items())
        controller = mock.Mock(); controller.config = self.config
        controller._inspect.return_value = {'Id': 'a' * 64, 'State': {'Pid': 2345}}
        controller.namespace = base.fake_namespace
        calls = []
        def run(argv, pass_fds=()):
            self.assertEqual((99,), pass_fds); self.assertEqual('--net=/proc/self/fd/99', argv[1])
            self.assertEqual(record['namespaceChain'], argv[7]); calls.append(argv[6])
            return mock.Mock(stdout=raw if argv[6] == '-L' else '-N RYK_owned_synthetic\n')
        controller.runner.run.side_effect = run
        observed = smoke.namespace_snapshot(controller, record, install=True)
        self.assertEqual(['-I', '-I', '-S', '-C', '-C', '-L'], calls)
        self.assertEqual(0, observed['counters']['udp']['packets']); controller._inspect.assert_called_once_with(record)
        incomplete = {}
        with mock.patch.object(smoke, 'parse_counters', side_effect=RuntimeError('synthetic counter format mismatch')):
            with self.assertRaises(RuntimeError): smoke.namespace_snapshot(controller, record, observation=incomplete)
        self.assertEqual(raw, incomplete['rawCounters']); self.assertEqual('-N RYK_owned_synthetic\n', incomplete['rawRules'])
    def test_udp_timeout_without_kernel_drop_is_not_success(self):
        proof, result = successful_evidence(); self.assertEqual({'tcp': 2, 'udp': 2}, smoke.verify(proof, result))
        proof['after']['counters']['udp']['packets'] = 0
        with self.assertRaises(RuntimeError): smoke.verify(proof, result)
    def test_false_positive_connections_nonce_or_target_are_rejected(self):
        for kind in ['denied', 'duplicates', 'nonce', 'target', 'udp-response']:
            proof, result = successful_evidence()
            if kind == 'denied': proof['listeners'][1]['connections'] = 1
            elif kind == 'duplicates': proof['listeners'][0]['connections'] = 2
            elif kind == 'nonce': result['nonce'] = '8' * 64
            elif kind == 'target': result['probes'][0]['host'] = '10.0.0.1'
            else: result['probes'][3]['response'] = True
            with self.subTest(kind=kind), self.assertRaises(RuntimeError): smoke.verify(proof, result)
    def test_ack_ownership_is_set_before_publication_and_path_cannot_escape(self):
        operation = smoke.prepare(self.config); original = os.replace; seen = []
        def replace(source, target):
            info = Path(source).stat(); seen.append(Path(target).name)
            self.assertEqual((self.config.uid, self.config.gid, 0o600), (info.st_uid, info.st_gid, info.st_mode & 0o777)); original(source, target)
        with mock.patch.object(smoke.module.os, 'replace', side_effect=replace):
            smoke.publish_ack(self.config, operation, '7' * 64, 'GO')
            smoke.publish_ack(self.config, operation, '7' * 64, 'OBSERVED')
        self.assertEqual(['network-probe.go', 'network-observed.go'], seen)
        with self.assertRaises(RuntimeError): smoke.publish_ack(self.config, self.base.worker, '7' * 64, 'GO')
        with self.assertRaises(RuntimeError): smoke.publish_ack(self.config, operation, '7' * 64, '../escape')
    def test_status_nonce_phase_and_symlink_are_checked(self):
        path = self.base.op / 'status.json'; path.write_text(json.dumps({'nonce': '7' * 64, 'phase': 'ready'}))
        self.assertEqual('ready', smoke.read_status(path, '7' * 64, 'ready')['phase'])
        with self.assertRaises(RuntimeError): smoke.read_status(path, '8' * 64, 'ready')
        path.unlink(); path.symlink_to(self.base.worker / 'manifest.json')
        with self.assertRaises(OSError): smoke.read_status(path, '7' * 64, 'ready')
    def test_occupied_fixture_port_fails_without_reuse_and_closes_socket(self):
        class Occupied:
            closed = False
            def bind(self, address):
                self.address = address; raise OSError('Address already in use')
            def close(self): self.closed = True
        fixture = Occupied()
        with self.assertRaises(OSError):
            with smoke.Listener(39090, '7' * 64, socket_factory=lambda *args: fixture): pass
        self.assertEqual((smoke.HOST, 39090), fixture.address); self.assertTrue(fixture.closed)
    def test_listener_serves_only_exact_nonce_once_and_always_joins(self):
        class Connection:
            def __enter__(self): return self
            def __exit__(self, *args): pass
            def settimeout(self, value): pass
            def recv(self, size): return ('KETTLE_NETWORK_SMOKE:' + '7' * 64 + '\n').encode()
            def sendall(self, data): self.response = data
        connection = Connection()
        class FixtureSocket:
            def __init__(self): self.count = 0; self.closed = False
            def bind(self, address): self.address = address
            def listen(self, size): pass
            def settimeout(self, value): pass
            def accept(self):
                self.count += 1
                if self.count == 1: return connection, ('synthetic-peer', 1)
                time.sleep(0.001); raise socket.timeout()
            def close(self): self.closed = True
        fixture = FixtureSocket()
        with smoke.Listener(39090, '7' * 64, socket_factory=lambda *args: fixture) as listener:
            deadline = time.monotonic() + 1
            while listener.acknowledgements < 1 and time.monotonic() < deadline: time.sleep(0.001)
            self.assertEqual(1, listener.connections); self.assertEqual(1, listener.acknowledgements)
        self.assertEqual(('ACK:' + '7' * 64 + '\n').encode(), connection.response)
        self.assertTrue(fixture.closed); self.assertFalse(listener.thread.is_alive())
    def _mock_execution(self, fail=False, delayed_stop=False):
        config = self.base.config(endpoints=[{'host': smoke.HOST, 'port': 39090}], stage_run_owner=True)
        operation = smoke.prepare(config)
        proof, result = successful_evidence()
        adapter = {'nonce': proof['nonce']}; identity = {'containerId': 'a' * 64, 'sourceHash': 'b' * 64, 'nonce': proof['nonce']}
        handle = mock.Mock(); handle.identity.return_value = identity
        handle.stdout = io.StringIO('{"type":"terminal","state":"SUCCEEDED"}\n'); handle.wait.return_value = 0
        controller = mock.Mock(); controller.launch.return_value = handle; controller._read.return_value = adapter
        controller.identity_for_run.return_value = identity; controller.status.return_value = {'running': False}
        if delayed_stop:
            stop = threading.Event(); state = {'running': True}
            def delayed_events():
                yield '{"type":"state","state":"RUNNING"}\n'
                if not stop.wait(timeout=2): raise RuntimeError('Synthetic stop never arrived')
                yield '{"type":"terminal","state":"STOPPED"}\n'
            def request_stop(*args, **kwargs): state['running'] = False; stop.set()
            handle.stdout = delayed_events(); controller.status.side_effect = lambda *args: dict(state)
            controller.request_stop.side_effect = request_stop
        controller.cleanup.return_value = {'cleaned': True}; controller.recover.return_value = {'resubmitted': False}
        controller.runner.run.return_value = mock.Mock(stdout=json.dumps([{'ifname': 'docker0', 'addr_info': [{'local': smoke.HOST}]}]))
        (operation / 'output/network-result.json').write_text(json.dumps(result))
        listeners = []
        class FakeListener:
            thread = None
            def __init__(self, port, nonce): self.port = port; self.closed = False; listeners.append(self)
            def __enter__(self): return self
            def __exit__(self, *args): self.closed = True
            def snapshot(self): return copy.deepcopy(proof['listeners'][0 if self.port == 39090 else 1])
        def snapshot(controller, record, install=False, observation=None):
            observation.update({'rawRules': '-N RYK_exact_fixture\n', 'rawCounters': 'synthetic malformed exact-owned DNS counters\n'})
            if fail: raise RuntimeError('synthetic namespace counter format failure')
            observation.update(proof['before' if install else 'after']); return observation
        with mock.patch.object(smoke.sys, 'platform', 'linux'), mock.patch.object(smoke.os, 'geteuid', return_value=0), \
             mock.patch.object(smoke, 'Listener', FakeListener), mock.patch.object(smoke, 'await_status', side_effect=[{'phase': 'ready'}, dict(result, phase='finished')]), \
             mock.patch.object(smoke, 'namespace_snapshot', side_effect=snapshot):
            actual = smoke.execute(config, operation, {'adapter': adapter}, controller)
        self.assertTrue(all(listener.closed for listener in listeners)); self.assertTrue(actual['listenersClosed'])
        self.assertTrue((operation / 'network-acceptance.json').is_file())
        return actual, controller
    def test_mock_full_success_requires_native_result_and_scoped_cleanup(self):
        proof, controller = self._mock_execution()
        self.assertTrue(proof['verified']); self.assertEqual('SUCCEEDED', proof['nativeState'])
        controller.cleanup.assert_called_once()
        self.assertEqual({'tcp': 2, 'udp': 2}, proof['dnsDropPacketDeltas'])
    def test_mock_namespace_failure_preserves_exact_journal_and_closes_listeners(self):
        proof, controller = self._mock_execution(fail=True)
        self.assertFalse(proof['verified']); self.assertTrue(proof['recoveryRequired'])
        self.assertIn('/linux-network-smoke-', proof['journal']); controller.cleanup.assert_not_called()
        controller.recover.assert_called_once()
        saved = json.loads((Path(proof['operationDir']) / 'network-observation.json').read_text())
        self.assertEqual('synthetic malformed exact-owned DNS counters\n', saved['before']['rawCounters'])
        self.assertEqual('-N RYK_exact_fixture\n', saved['before']['rawRules'])
    def test_failure_reader_drains_stop_events_before_logs_are_closed(self):
        proof, controller = self._mock_execution(fail=True, delayed_stop=True)
        self.assertFalse(proof['verified']); self.assertTrue(proof['eventReaderDrained'])
        self.assertEqual([], proof['eventReaderErrors']); self.assertEqual(2, proof['eventCount'])
        events = [json.loads(line) for line in (Path(proof['operationDir']) / 'events.ndjson').read_text().splitlines()]
        self.assertEqual(['RUNNING', 'STOPPED'], [event['state'] for event in events])
        controller.request_stop.assert_called_once()
    def test_offline_csv_input_is_separate_from_work_dir_output(self):
        tree = ET.parse(ASSETS / 'fixtures/smoke.ktr')
        self.assertEqual('${INPUT_DIR}/smoke.csv', tree.findtext('./step[type="CsvInput"]/filename'))


if __name__ == '__main__': unittest.main()
