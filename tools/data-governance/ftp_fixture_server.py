#!/usr/bin/env python3
"""Loopback-only FTP fixture with a private, task-owned home directory."""
import argparse
import json
from pathlib import Path
from pyftpdlib.authorizers import DummyAuthorizer
from pyftpdlib.handlers import FTPHandler
from pyftpdlib.servers import FTPServer

parser = argparse.ArgumentParser()
parser.add_argument('--runtime-root', required=True)
args = parser.parse_args()
root = Path(args.runtime_root).resolve()
marker = json.loads((root / 'runtime.json').read_text())
assert marker.get('owner') == 'rynew-data-governance-runtime'
credentials = json.loads((root / 'private/ftp-credentials.json').read_text())
home = Path(credentials['root']).resolve()
assert home == root / 'ftp/files'
authorizer = DummyAuthorizer()
authorizer.add_user(credentials['username'], credentials['password'], str(home), perm='elradfmwMT')
handler = FTPHandler
handler.authorizer = authorizer
handler.passive_ports = range(22100, 22110)
server = FTPServer(('127.0.0.1', 2121), handler)
server.max_cons = 10
server.max_cons_per_ip = 10
server.serve_forever()
