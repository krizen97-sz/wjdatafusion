#!/usr/bin/env python3
"""Exercise only generated files on this task's loopback FTP fixture."""
import argparse
import ftplib
import io
import json
import uuid
from runtime_ctl import read_private_json, runtime_root

parser = argparse.ArgumentParser()
parser.add_argument('--runtime-root', required=True)
args = parser.parse_args()
root, _ = runtime_root(args.runtime_root)
credentials = read_private_json(root, 'ftp-credentials.json')
name = 'runtime-smoke-' + uuid.uuid4().hex
payload = b'RYNEW isolated FTP fixture\n\x00\x1f\xff\n'
with ftplib.FTP() as ftp:
    ftp.connect('127.0.0.1', 2121, timeout=5)
    ftp.login(credentials['username'], credentials['password'])
    ftp.storbinary('STOR ' + name + '.temp', io.BytesIO(payload))
    ftp.rename(name + '.temp', name + '.bin')
    result = io.BytesIO()
    ftp.retrbinary('RETR ' + name + '.bin', result.write)
    matched = result.getvalue() == payload
    ftp.delete(name + '.bin')
    assert matched, 'local fixture content mismatch'
print(json.dumps({'service': 'ftp', 'store_rename_retrieve_delete': True, 'binary_equal': matched, 'bytes': len(payload)}))
