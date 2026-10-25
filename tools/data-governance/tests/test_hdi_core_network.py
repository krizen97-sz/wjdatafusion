import importlib.util
import os
from pathlib import Path
import socket
import json
import subprocess
import sys
import tempfile
import threading
import time
import unittest

SOURCE = Path(__file__).resolve().parents[1] / 'hdi_core_network.py'
spec = importlib.util.spec_from_file_location('hdi_core_network', SOURCE)
network = importlib.util.module_from_spec(spec)
spec.loader.exec_module(network)


class ByteBridgeTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix='hdi-net-')
        self.root = Path(self.temp.name)
        self.listener = socket.socket()
        self.listener.bind(('127.0.0.1', 0))
        self.listener.listen()
        self.connections = []
        self.bridges = []

    def tearDown(self):
        for bridge in reversed(self.bridges):
            bridge.close()
        for connection in self.connections:
            connection.close()
        self.listener.close()
        self.temp.cleanup()

    def bridge(self):
        bridge = network.ByteBridge(socket.AF_UNIX, str(self.root / 'db.sock'),
                                    socket.AF_INET, self.listener.getsockname())
        self.bridges.append(bridge)
        return bridge

    def test_stream_preserves_bytes_and_half_close(self):
        bridge = self.bridge()
        received = []

        def target():
            connection, _ = self.listener.accept()
            with connection:
                value = bytearray()
                while True:
                    block = connection.recv(8192)
                    if not block:
                        break
                    value.extend(block)
                received.append(bytes(value))
                connection.sendall(b'ack:' + bytes(value))

        thread = threading.Thread(target=target)
        thread.start()
        data = os.urandom(300000)
        with socket.socket(socket.AF_UNIX) as client:
            client.settimeout(3)
            client.connect(bridge.address)
            client.sendall(data)
            client.shutdown(socket.SHUT_WR)
            response = bytearray()
            while True:
                block = client.recv(8192)
                if not block:
                    break
                response.extend(block)
        thread.join(timeout=2)
        self.assertFalse(thread.is_alive())
        self.assertEqual(received, [data])
        self.assertEqual(bytes(response), b'ack:' + data)

    def test_close_ends_idle_connections_and_removes_only_owned_socket(self):
        bridge = self.bridge()
        client = socket.socket(socket.AF_UNIX)
        client.settimeout(2)
        client.connect(bridge.address)
        target, _ = self.listener.accept()
        self.connections += [client, target]
        started = time.monotonic()
        bridge.close()
        self.bridges.remove(bridge)
        self.assertLess(time.monotonic() - started, 5)
        self.assertFalse(Path(bridge.address).exists())
        self.assertEqual(client.recv(1), b'')
        self.assertEqual(target.recv(1), b'')

    def test_existing_socket_path_is_not_replaced(self):
        path = self.root / 'db.sock'
        path.write_text('owned by another process')
        with self.assertRaises(ValueError):
            self.bridge()
        self.assertEqual(path.read_text(), 'owned by another process')

    def test_public_parent_is_rejected(self):
        self.root.chmod(0o755)
        try:
            with self.assertRaises(ValueError):
                self.bridge()
        finally:
            self.root.chmod(0o700)

    def test_connection_limit_does_not_open_an_extra_target(self):
        bridge = network.ByteBridge(socket.AF_UNIX, str(self.root / 'db.sock'),
                                    socket.AF_INET, self.listener.getsockname(), max_connections=1)
        self.bridges.append(bridge)
        one = socket.socket(socket.AF_UNIX)
        one.connect(bridge.address)
        target, _ = self.listener.accept()
        two = socket.socket(socket.AF_UNIX)
        two.settimeout(2)
        two.connect(bridge.address)
        self.connections += [one, two, target]
        self.assertEqual(two.recv(1), b'')
        self.listener.settimeout(.15)
        with self.assertRaises(socket.timeout):
            self.listener.accept()


@unittest.skipUnless(sys.platform == 'linux', 'Linux parent-death ownership protocol')
class CoordinatorLeaseTests(unittest.TestCase):
    def run_case(self, kill_supervisor):
        with tempfile.TemporaryDirectory(prefix='hdi-coordinator-') as directory:
            root = Path(directory)
            config = root / 'owned.json'
            command = [sys.executable, '-c', 'import time; time.sleep(60)']
            config.write_text(json.dumps({'command': command, 'log': str(root / 'child.log')}))
            config.chmod(0o600)
            process = subprocess.Popen([sys.executable, str(SOURCE), '--leased-config', str(config)],
                                       stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
            try:
                started = json.loads(process.stdout.readline())
                self.assertEqual(started['event'], 'COORDINATOR_STARTED')
                child = Path('/proc') / started['pid']
                deadline = time.monotonic() + 5
                while time.monotonic() < deadline:
                    if command[-1].encode() in (child / 'cmdline').read_bytes():
                        break
                    time.sleep(.02)
                else:
                    self.fail('Child did not exec the held command')
                if kill_supervisor:
                    process.kill()
                else:
                    process.stdin.close()
                process.wait(timeout=18)
                deadline = time.monotonic() + 5
                while time.monotonic() < deadline:
                    try:
                        state = (child / 'stat').read_text().split(') ', 1)[1].split()[0]
                        if state == 'Z':
                            break
                    except (FileNotFoundError, ProcessLookupError):
                        break
                    time.sleep(.02)
                else:
                    self.fail('Original child survived loss of ownership')
            finally:
                if process.poll() is None:
                    process.kill()
                    process.wait(timeout=5)
                for stream in (process.stdin, process.stdout, process.stderr):
                    stream.close()

    def test_stdin_eof_closes_original_child(self):
        self.run_case(False)

    def test_supervisor_sigkill_cannot_orphan_original_child(self):
        self.run_case(True)


@unittest.skipUnless(sys.platform == 'linux', 'Linux connector network namespace')
class ConnectorNamespaceTests(unittest.TestCase):
    def test_only_granted_address_and_port_reach_the_original_byte_destination(self):
        probe = subprocess.run(['unshare', '--user', '--map-root-user', '--net', 'true'], capture_output=True)
        if probe.returncode:
            self.skipTest('Unprivileged network namespaces unavailable')
        with tempfile.TemporaryDirectory(prefix='hdi-connector-net-') as directory:
            root = Path(directory)
            with socket.socket() as target:
                target.bind(('127.0.0.1', 0))
                target.listen()
                target.settimeout(10)
                destination = target.getsockname()
                received = []
                def echo():
                    connection, _ = target.accept()
                    with connection:
                        value = connection.recv(100)
                        received.append(value)
                        connection.sendall(b'original:' + value)
                thread = threading.Thread(target=echo, daemon=True)
                thread.start()
                bridge = network.ByteBridge(socket.AF_UNIX, str(root / 'connector.sock'), socket.AF_INET, destination)
                child = root / 'probe.py'
                child.write_text('''import json,socket
value={"interfaces":[n for _,n in socket.if_nameindex()]}
with socket.create_connection(("192.0.2.55",15432),2) as s:
 s.sendall(b"preserved-wire-bytes");value["response"]=s.recv(100).decode()
try:
 s=socket.create_connection(("192.0.2.55",15433),.5);s.close();value["ungrantedPortReachable"]=True
except OSError:value["ungrantedPortReachable"]=False
print(json.dumps(value),flush=True)
''')
                config = root / 'namespace.json'
                config.write_text(json.dumps({'parentNetworkNamespace': os.readlink('/proc/self/ns/net'),
                    'dependencies': [{'address': '192.0.2.55', 'port': 15432, 'unixSocket': bridge.address}],
                    'coreLog': str(root / 'core.log'), 'corePorts': [], 'cwd': str(root),
                    'command': [sys.executable, str(child)], 'environment': {'PATH': '/usr/bin:/bin'},
                    'stopGraceSeconds': 1}))
                config.chmod(0o600)
                process = subprocess.Popen(['unshare', '--user', '--map-root-user', '--mount-proc', '--net',
                    '--pid', '--fork', '--kill-child=SIGKILL', sys.executable, str(SOURCE),
                    '--namespace-config', str(config)], stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
                try:
                    process.wait(timeout=15)
                    self.assertEqual(process.returncode, 0, process.stderr.read().decode())
                    observed = json.loads((root / 'core.log').read_text())
                    self.assertEqual(observed, {'interfaces': ['lo'], 'response': 'original:preserved-wire-bytes',
                                                'ungrantedPortReachable': False})
                    thread.join(timeout=1)
                    self.assertEqual(received, [b'preserved-wire-bytes'])
                    events = [json.loads(line) for line in process.stdout.read().splitlines()]
                    self.assertEqual(events[-1]['event'], 'CORE_CLOSED')
                    self.assertTrue(events[-1]['finalized'])
                finally:
                    if process.poll() is None:
                        process.kill(); process.wait(timeout=5)
                    for stream in (process.stdin, process.stdout, process.stderr):
                        stream.close()
                    bridge.close()


if __name__ == '__main__':
    unittest.main()
