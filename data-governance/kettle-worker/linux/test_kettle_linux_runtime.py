"""Pure mock tests: this file never invokes Docker, iptables, nsenter, or a daemon."""
from contextlib import contextmanager
import copy
import hashlib
import importlib.util
import io
import json
import os
from pathlib import Path
import shlex
import subprocess
import sys
import tempfile
import unittest

REPO = Path(__file__).resolve().parents[3]
SPEC = importlib.util.spec_from_file_location('kettle_linux_runtime', REPO / 'tools/data-governance/kettle_linux_runtime.py')
runtime = importlib.util.module_from_spec(SPEC); sys.modules[SPEC.name] = runtime; SPEC.loader.exec_module(runtime)


class FakeProcess:
    def __init__(self):
        self.pid = 8765; self.stdin = io.StringIO(); self.stdout = io.StringIO('{"type":"terminal","state":"SUCCEEDED"}\n')
        self.stderr = io.StringIO(); self.exit = None
    def poll(self): return self.exit


class FakeRunner:
    def __init__(self):
        self.calls = []; self.container = None; self.network = None; self.attached = None
        self.host = {'DOCKER-USER': [['-m', 'comment', '--comment', 'existing-admin-rule', '-j', 'RETURN']]}
        self.ns = {}; self.fail = None; self.before_namespace = None
    def run(self, argv, check=True, pass_fds=()):
        self.calls.append(list(argv))
        if self.fail and self.fail(argv): raise RuntimeError('Injected command failure')
        if Path(argv[0]).name == 'docker': value = self.docker(argv[3:])
        else:
            namespace = Path(argv[0]).name == 'nsenter'
            if namespace:
                if self.before_namespace: self.before_namespace()
                assert pass_fds == (99,) and argv[1] == '--net=/proc/self/fd/99'
                family = Path(argv[3]).name; table = self.ns.setdefault(family, {'OUTPUT': []}); args = argv[6:]
            else: table = self.host; args = argv[3:]
            action, chain, *body = args
            if action == '-N':
                if chain in table: raise RuntimeError('Chain exists')
                table[chain] = []
            elif action == '-A': table[chain].append(body)
            elif action == '-I': table[chain].insert(int(body[0]) - 1, body[1:])
            elif action == '-D': table[chain].remove(body)
            elif action == '-X':
                assert not table[chain]; del table[chain]
            elif action != '-S': raise AssertionError(argv)
            value = '\n'.join([shlex.join(['-N', chain])] + [shlex.join(['-A', chain, *rule]) for rule in table[chain]]) if action == '-S' else ''
        return subprocess.CompletedProcess(argv, 0, value, '')
    def docker(self, args):
        if args[:2] == ['image', 'inspect']:
            return json.dumps([{'RepoDigests': [runtime.IMAGE['image'].replace('docker.io/library/', '')], 'Os': 'linux', 'Architecture': 'amd64'}])
        if args[:2] == ['network', 'create']:
            labels = dict(arg.split('=', 1) for i, arg in enumerate(args) if i and args[i - 1] == '--label')
            options = dict(arg.split('=', 1) for i, arg in enumerate(args) if i and args[i - 1] == '--opt')
            self.network = {'Id': 'b' * 64, 'Name': args[-1], 'Labels': labels, 'Driver': 'bridge', 'EnableIPv6': False,
                            'IPAM': {'Config': [{'Subnet': args[args.index('--subnet') + 1]}]}, 'Options': options, 'Containers': {}}
            return self.network['Id']
        if args[:2] == ['network', 'inspect']: return json.dumps([self.network])
        if args[:2] == ['network', 'rm']:
            assert not self.network['Containers']; self.network = None; return args[2]
        if args[0] == 'create':
            labels = dict(arg.split('=', 1) for i, arg in enumerate(args) if i and args[i - 1] == '--label')
            mounts = []
            for i, arg in enumerate(args):
                if i and args[i - 1] == '--mount':
                    parts = dict(item.split('=', 1) for item in arg.split(',') if '=' in item)
                    mounts.append({'Type': 'bind', 'Source': parts['src'], 'Destination': parts['dst'], 'RW': 'readonly' not in arg.split(',')})
            network = args[args.index('--network') + 1]
            self.container = {'Id': 'a' * 64, 'Config': {'Labels': labels, 'Image': runtime.IMAGE['image'], 'User': args[args.index('--user') + 1]},
                              'HostConfig': {'ReadonlyRootfs': True, 'Privileged': False, 'CapDrop': ['ALL'], 'SecurityOpt': ['no-new-privileges:true'],
                                             'PortBindings': {}, 'NetworkMode': network}, 'Mounts': mounts,
                              'State': {'Running': False, 'ExitCode': 0, 'Pid': 2345, 'StartedAt': 'one-start'},
                              'NetworkSettings': {'Networks': {network: {'IPAddress': args[args.index('--ip') + 1]}} if '--ip' in args else {'none': {}}}}
            return self.container['Id']
        if args[0] == 'inspect':
            if self.container is None: raise RuntimeError('Container missing')
            return json.dumps([self.container])
        if args[0] == 'kill': self.container['State'].update(Running=False, ExitCode=137); return self.container['Id']
        if args[0] == 'rm':
            assert not self.container['State']['Running']; self.container = None
            if self.network: self.network['Containers'] = {}
            return args[1]
        raise AssertionError(args)
    def popen(self, argv, stderr=None):
        self.calls.append(list(argv)); assert argv[3:6] == ['start', '--attach', '--interactive']
        self.container['State']['Running'] = True
        if self.network: self.network['Containers'] = {self.container['Id']: {}}
        self.attached = FakeProcess(); return self.attached


@contextmanager
def fake_namespace(container):
    assert container['Id'] == 'a' * 64 and container['State']['Pid'] == 2345
    yield 99


class LinuxTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(); self.addCleanup(self.temp.cleanup); self.base = Path(self.temp.name).resolve()
        self.worker = self.base / 'worker'; self.ops = self.base / 'operations'; self.state = self.base / 'journals'
        self.uid = os.geteuid() or 10001; self.gid = os.getegid() or 10001
        for path in [self.worker / 'lib', self.worker / 'classes', self.ops]: path.mkdir(parents=True)
        (self.worker / 'lib/original.jar').write_bytes(b'synthetic jar for command construction only')
        (self.worker / 'classes/KettleWorker.class').write_bytes(bytes.fromhex('cafebabe0000003d') + b'synthetic')
        self.manifest = {'libraries': [{'file': 'original.jar', 'sha256': runtime.digest(self.worker / 'lib/original.jar')}],
                         'classpath': str(self.worker / 'classes') + os.pathsep + str(self.worker / 'lib/original.jar')}
        (self.worker / 'manifest.json').write_text(json.dumps(self.manifest))
        self.op = self.ops / 'run-one'; self.op.mkdir(mode=0o700); (self.op / 'transformation.ktr').write_text('<transformation/>')
        (self.op / 'transformation.ktr').chmod(0o600)
    def config(self, **changes):
        values = dict(instance_id='1' * 32, worker_root=self.worker, operations_root=self.ops, state_root=self.state,
                      uid=self.uid, gid=self.gid, execution_enabled=True, stage_run_owner=os.geteuid() == 0)
        values.update(changes); return runtime.Config(**values)
    def controller(self, **changes):
        runner = FakeRunner(); return runtime.LinuxRuntime(self.config(**changes), runner=runner, namespace=fake_namespace, host_platform='linux'), runner
    def test_plan_is_read_only_and_defaults_to_no_network(self):
        controller, runner = self.controller(); plan = controller.plan(self.op, 'run')
        self.assertEqual([], runner.calls); self.assertFalse(self.state.exists()); self.assertEqual([], plan['endpoints'])
        self.assertEqual('none', plan['containerCreate'][plan['containerCreate'].index('--network') + 1])
        self.assertIn('--pull=never', plan['containerCreate']); self.assertIn('--cap-drop=ALL', plan['containerCreate'])
        self.assertIn('--read-only', plan['containerCreate']); self.assertIn('--security-opt=no-new-privileges:true', plan['containerCreate'])
        self.assertIn(str(self.uid) + ':' + str(self.gid), plan['containerCreate']); self.assertNotIn('--privileged', plan['containerCreate'])
        self.assertEqual(['/work/run-one', 'run', '', '20'], plan['javaArgv'][-4:]); self.assertNotIn('/var/run/docker.sock', str(plan['mounts']))
    def test_mounts_and_classpath_are_exact(self):
        controller, _ = self.controller(); plan = controller.plan(self.op, 'run')
        self.assertEqual(3, len(plan['mounts'])); self.assertEqual([False, False, True], [m[2] for m in plan['mounts']])
        self.assertIn('/opt/rynew/classes:/opt/rynew/lib/original.jar', plan['javaArgv'])
    def test_endpoint_injections_and_container_loopback_rejected(self):
        for host, port in [('127.0.0.1', 5432), ('::1', 5432), ('10.0.0.1/8', 5432), ('example.org', 443), ('10.0.0.1;id', 443), ('0.0.0.0', 1), ('10.0.0.1', '5432'), ('10.0.0.1', True)]:
            with self.subTest(host=host, port=port), self.assertRaises((RuntimeError, ValueError)):
                self.config(endpoints=[{'host': host, 'port': port}])
    def test_image_and_binary_injection_rejected(self):
        for value in ['eclipse-temurin:latest', runtime.IMAGE['image'] + ' --privileged', 'attacker@sha256:' + '0' * 64]:
            with self.assertRaises(RuntimeError): self.config(image=value)
        with self.assertRaises(RuntimeError): self.config(docker='/usr/bin/docker;touch /tmp/escape')
        with self.assertRaises(RuntimeError): self.config(uid=0)
    def test_xml_cannot_grant_network_or_extra_docker_arguments(self):
        (self.op / 'transformation.ktr').write_text('<transformation><network>host</network><privileged>true</privileged></transformation>')
        controller, runner = self.controller(); plan = controller.plan(self.op, 'run', '$(touch /tmp/no); --privileged')
        self.assertEqual([], plan['endpoints']); self.assertNotIn('--privileged', plan['containerCreate'])
        self.assertEqual('$(touch /tmp/no); --privileged', plan['javaArgv'][-2])
    def test_path_escape_and_links_rejected(self):
        controller, _ = self.controller()
        with self.assertRaises(RuntimeError): controller.plan(self.base, 'run')
        (self.op / 'escape').symlink_to(self.worker / 'manifest.json')
        with self.assertRaises(RuntimeError): controller.plan(self.op, 'run')
        with self.assertRaises(RuntimeError): self.config(state_root=self.base / 'journals,dst=/var/run/docker.sock')
    def test_changed_original_jar_rejected(self):
        (self.worker / 'lib/original.jar').write_bytes(b'changed')
        controller, runner = self.controller()
        with self.assertRaises(RuntimeError): controller.plan(self.op, 'run')
        self.assertEqual([], runner.calls)
    def test_reviewed_manifest_relocates_without_using_old_host_paths(self):
        self.manifest['classpath'] = '/old-mac/runtime/classes:/old-mac/runtime/lib/original.jar'
        (self.worker / 'manifest.json').write_text(json.dumps(self.manifest))
        controller, runner = self.controller(); plan = controller.plan(self.op, 'run')
        self.assertIn('/opt/rynew/classes:/opt/rynew/lib/original.jar', plan['javaArgv'])
        self.assertNotIn('/old-mac', str(plan)); self.assertEqual([], runner.calls)
    def test_unreviewed_classpath_and_traversal_rejected(self):
        for entry in ['/tmp/attacker.jar', '/tmp/../lib/original.jar']:
            self.manifest['classpath'] = str(self.worker / 'classes') + ':' + entry
            (self.worker / 'manifest.json').write_text(json.dumps(self.manifest))
            controller, runner = self.controller()
            with self.assertRaises(RuntimeError): controller.plan(self.op, 'run')
            self.assertEqual([], runner.calls)
    def test_validation_stays_offline_with_trusted_endpoints(self):
        controller, _ = self.controller(endpoints=[{'host': '10.20.30.40', 'port': 5432}])
        self.assertEqual([], controller.plan(self.op, 'validate')['endpoints'])
    def test_no_unisolated_fallback_on_mac_or_disabled_configuration(self):
        for platform, enabled in [('darwin', True), ('linux', False)]:
            runner = FakeRunner(); controller = runtime.LinuxRuntime(self.config(execution_enabled=enabled), runner=runner, host_platform=platform)
            with self.assertRaises(RuntimeError): controller.launch(self.op, 'run')
            self.assertEqual([], runner.calls)
    def test_private_config_requires_mode_and_owner(self):
        config = self.base / 'config.json'; config.write_text('{}'); config.chmod(0o644)
        with self.assertRaises(RuntimeError): runtime.Config.load(config)
    def test_none_launch_streams_original_protocol_and_control_nonce(self):
        controller, runner = self.controller(); handle = controller.launch(self.op, 'run', launch_id='2' * 64)
        self.assertEqual('{"type":"terminal","state":"SUCCEEDED"}\n', handle.stdout.read())
        self.assertEqual('2' * 64, (self.op / '.rynew-java-ready').read_text().strip())
        self.assertFalse(any(Path(c[0]).name in {'iptables', 'nsenter'} for c in runner.calls))
        handle.terminate(); self.assertEqual('STOP:' + '2' * 64, (self.op / 'control.stop').read_text().strip())
        self.assertIsNone(handle.poll()); runner.container['State'].update(Running=False, ExitCode=0)
        self.assertEqual(0, handle.wait(timeout=0)); controller.cleanup('run-one')
        self.assertTrue(self.op.exists()); self.assertEqual('CLEANED', controller._read('run-one')['state'])
        self.assertTrue(controller.cleanup('run-one')['cleaned'])
    def test_network_rules_complete_before_java_gate_is_released(self):
        controller, runner = self.controller(endpoints=[{'host': '10.20.30.40', 'port': 5432}])
        runner.before_namespace = lambda: self.assertFalse((self.op / '.rynew-java-ready').exists())
        controller.launch(self.op, 'run', launch_id='3' * 64)
        record = controller._read('run-one'); self.assertTrue(record['javaReleased'])
        self.assertIn('-i', record['hostJump']); self.assertIn(record['bridgeInterface'], record['hostJump'])
        for family in ['iptables', 'ip6tables']:
            self.assertEqual(['-j', record['namespaceChain']], runner.ns[family]['OUTPUT'][0])
            self.assertEqual('DROP', runner.ns[family][record['namespaceChain']][-1][-1])
        self.assertEqual(1, len(runner.ns['ip6tables'][record['namespaceChain']]))
        self.assertNotIn('ACCEPT', str(runner.host)); self.assertNotIn('-F', str(runner.calls)); self.assertNotIn('-P', str(runner.calls))
    def test_namespace_firewall_failure_never_releases_java(self):
        controller, runner = self.controller(endpoints=[{'host': '10.20.30.40', 'port': 5432}])
        runner.fail = lambda cmd: Path(cmd[0]).name == 'nsenter' and '-I' in cmd
        with self.assertRaises(RuntimeError): controller.launch(self.op, 'run')
        self.assertFalse((self.op / '.rynew-java-ready').exists()); self.assertFalse(controller._read('run-one')['javaReleased'])
        self.assertEqual('RECOVERY_REQUIRED', controller._read('run-one')['state'])
    def test_unknown_labels_block_stop_and_cleanup(self):
        controller, runner = self.controller(); controller.launch(self.op, 'run')
        runner.container['Config']['Labels'][runtime.LABEL + 'nonce'] = 'someone-else'
        count = len(runner.calls)
        with self.assertRaises(RuntimeError): controller.request_stop('run-one', force=True)
        with self.assertRaises(RuntimeError): controller.cleanup('run-one')
        self.assertFalse(any('kill' in c or 'rm' in c for c in runner.calls[count:]))
    def test_added_capability_or_disabled_privilege_guard_refused(self):
        controller, runner = self.controller(); controller.launch(self.op, 'run')
        runner.container['HostConfig']['SecurityOpt'] = ['no-new-privileges:false']
        with self.assertRaises(RuntimeError): controller.status('run-one')
        runner.container['HostConfig']['SecurityOpt'] = ['no-new-privileges:true']
        runner.container['HostConfig']['CapAdd'] = ['SYS_ADMIN']
        with self.assertRaises(RuntimeError): controller.status('run-one')
    def test_extra_named_volume_mount_refused(self):
        controller, runner = self.controller(); controller.launch(self.op, 'run')
        runner.container['Mounts'].append({'Type': 'volume', 'Destination': '/unreviewed', 'RW': True})
        with self.assertRaises(RuntimeError): controller.status('run-one')
    def test_cleanup_preserves_existing_admin_rules_and_other_networks(self):
        controller, runner = self.controller(endpoints=[{'host': '10.20.30.40', 'port': 5432}])
        existing = copy.deepcopy(runner.host['DOCKER-USER']); controller.launch(self.op, 'run')
        runner.container['State']['Running'] = False; controller.cleanup('run-one')
        self.assertEqual({'DOCKER-USER': existing}, runner.host); self.assertIsNone(runner.network)
    def test_unknown_firewall_rule_or_peer_container_refuses_cleanup(self):
        controller, runner = self.controller(endpoints=[{'host': '10.20.30.40', 'port': 5432}]); controller.launch(self.op, 'run')
        runner.container['State']['Running'] = False; chain = controller._read('run-one')['hostChain']
        runner.host[chain].append(['-j', 'ACCEPT'])
        with self.assertRaises(RuntimeError): controller.cleanup('run-one')
        self.assertIsNotNone(runner.container)
        runner.host[chain].pop(); runner.network['Containers']['c' * 64] = {}
        with self.assertRaises(RuntimeError): controller.cleanup('run-one')
    def test_recovery_is_read_only_and_never_starts_again(self):
        controller, runner = self.controller(); controller.launch(self.op, 'run'); count = len(runner.calls)
        result = controller.recover('run-one'); self.assertFalse(result['resubmitted']); self.assertTrue(result['running'])
        self.assertTrue(all(c[3] == 'inspect' for c in runner.calls[count:]))
        with self.assertRaises(RuntimeError): controller.launch(self.op, 'run')
    def test_attach_cli_exit_is_not_container_completion(self):
        controller, runner = self.controller(); handle = controller.launch(self.op, 'run'); runner.attached.exit = 1
        self.assertIsNone(handle.poll())
        with self.assertRaises(subprocess.TimeoutExpired): handle.wait(timeout=0)
    def test_force_kill_does_not_depend_on_mutable_control_file(self):
        controller, runner = self.controller(); handle = controller.launch(self.op, 'run')
        (self.op / 'control.stop').symlink_to(self.worker / 'manifest.json')
        handle.kill(); self.assertFalse(runner.container['State']['Running']); self.assertEqual(137, handle.poll())
    def test_exclusive_journal_creation_prevents_duplicate_submission(self):
        controller, runner = self.controller(); plan = controller.plan(self.op, 'run')
        controller._save(plan, create=True)
        with self.assertRaises(FileExistsError): controller._save(plan, create=True)
        with self.assertRaises(RuntimeError): controller.launch(self.op, 'run')
        self.assertEqual([], runner.calls)
    def test_unreadable_artifacts_and_unprepared_run_owner_fail_before_docker(self):
        controller, runner = self.controller(stage_run_owner=False)
        artifact = self.worker / 'lib/original.jar'; artifact.chmod(0)
        with self.assertRaises((RuntimeError, PermissionError)): controller.plan(self.op, 'run')
        artifact.chmod(0o644)
        controller, runner = self.controller(uid=65533 if self.uid != 65533 else 65532, stage_run_owner=False)
        with self.assertRaises(RuntimeError): controller.launch(self.op, 'run')
        self.assertEqual([], runner.calls)
    def test_explicit_staging_only_changes_this_run_permissions(self):
        self.op.chmod(0o775); (self.op / 'transformation.ktr').chmod(0o664)
        artifact_mode = (self.worker / 'lib/original.jar').stat().st_mode
        controller, runner = self.controller(stage_run_owner=True)
        controller.launch(self.op, 'run')
        self.assertEqual(0o700, self.op.stat().st_mode & 0o777)
        self.assertEqual(0o600, (self.op / 'transformation.ktr').stat().st_mode & 0o777)
        self.assertEqual(artifact_mode, (self.worker / 'lib/original.jar').stat().st_mode)
        self.assertTrue(controller._read('run-one')['ownershipStaged'])
    def test_stop_nonce_and_cleaned_identity_remain_bound_to_journal(self):
        controller, runner = self.controller(); controller.launch(self.op, 'run', launch_id='4' * 64)
        count = len(runner.calls)
        with self.assertRaises(RuntimeError): controller.request_stop('run-one', expected_nonce='5' * 64)
        self.assertEqual(count, len(runner.calls)); self.assertFalse((self.op / 'control.stop').exists())
        self.assertEqual('4' * 64, controller.identity_for_run('run-one')['nonce'])
        runner.container['State']['Running'] = False; controller.cleanup('run-one')
        self.assertEqual('4' * 64, controller.identity_for_run('run-one')['nonce'])
        self.assertTrue(controller.status('run-one')['containerRemoved'])
    def test_disabling_new_execution_does_not_disable_owned_stop(self):
        controller, runner = self.controller(); controller.launch(self.op, 'run')
        stopped_controller = runtime.LinuxRuntime(self.config(execution_enabled=False), runner=runner, namespace=fake_namespace, host_platform='linux')
        self.assertTrue(stopped_controller.request_stop('run-one')['stopRequested'])
    def test_external_container_restart_is_not_adopted(self):
        controller, runner = self.controller(); controller.launch(self.op, 'run'); runner.container['State']['StartedAt'] = 'unapproved-new-start'
        with self.assertRaises(RuntimeError): controller.recover('run-one')
    def test_cleanup_can_resume_after_acknowledged_container_removal(self):
        controller, runner = self.controller(endpoints=[{'host': '10.20.30.40', 'port': 5432}]); controller.launch(self.op, 'run')
        runner.container['State']['Running'] = False
        runner.fail = lambda cmd: Path(cmd[0]).name == 'iptables' and '-D' in cmd
        with self.assertRaises(RuntimeError): controller.cleanup('run-one')
        self.assertTrue(controller._read('run-one')['containerRemoved']); self.assertIsNone(runner.container)
        runner.fail = None; self.assertTrue(controller.cleanup('run-one')['cleaned'])


if __name__ == '__main__': unittest.main()
