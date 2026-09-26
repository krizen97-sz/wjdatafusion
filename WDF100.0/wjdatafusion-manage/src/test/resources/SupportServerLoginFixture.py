"""Loopback-only SSH protocol fixture, not an operating-system/PAM emulator."""
import logging
import re
import socket
import sys
import threading
import time
import paramiko

logging.disable(logging.CRITICAL)
mode = sys.argv[1] if len(sys.argv) > 1 else "normal"
key = paramiko.ECDSAKey.generate()

class Server(paramiko.ServerInterface):
    def __init__(self):
        self.user = None
    def get_allowed_auths(self, username):
        return "password"
    def check_auth_password(self, username, password):
        if (username, password) in (("hik", "hik-test"), ("root", "root-test")):
            self.user = username
            return paramiko.AUTH_SUCCESSFUL
        return paramiko.AUTH_FAILED
    def check_channel_request(self, kind, channel_id):
        return paramiko.OPEN_SUCCEEDED if kind == "session" else paramiko.OPEN_FAILED_ADMINISTRATIVELY_PROHIBITED
    def check_channel_pty_request(self, *args):
        return True
    def check_channel_exec_request(self, channel, command):
        text = command.decode("utf-8")
        marker = re.search(r"__RY_[a-f0-9]+_", text)
        if not marker:
            return False
        def respond():
            try:
                if "su - root" in text:
                    if mode != "passwordless":
                        channel.send(b"Password: ")
                        supplied = b""
                        channel.settimeout(5)
                        while not supplied.endswith(b"\n") and len(supplied) < 256:
                            supplied += channel.recv(256)
                        if supplied != b"root-test\n":
                            channel.send(b"su: Authentication failure\r\n")
                            channel.send_exit_status(1)
                            return
                    uid = 0
                else:
                    uid = 0 if self.user == "root" else 1000
                channel.send((marker.group(0) + str(uid) + "__\r\n").encode())
                channel.send_exit_status(0)
            finally:
                time.sleep(0.05)
                channel.close()
        threading.Thread(target=respond, daemon=True).start()
        return True

def serve(client):
    transport = paramiko.Transport(client)
    transport.add_server_key(key)
    try:
        transport.start_server(server=Server())
        channels = []
        while transport.is_active():
            channel = transport.accept(0.1)
            if channel is not None:
                channels.append(channel)
            channels = [item for item in channels if not item.closed]
    except Exception:
        pass
    finally:
        transport.close()

listener = socket.socket()
listener.bind(("127.0.0.1", 0))
listener.listen(8)
print(listener.getsockname()[1], flush=True)
while True:
    client, _ = listener.accept()
    threading.Thread(target=serve, args=(client,), daemon=True).start()
