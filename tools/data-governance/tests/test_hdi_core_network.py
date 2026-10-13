import importlib.util
import os
from pathlib import Path
import socket
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


if __name__ == '__main__':
    unittest.main()
