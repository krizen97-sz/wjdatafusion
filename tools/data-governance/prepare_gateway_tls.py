#!/usr/bin/env python3
"""Issue a task-local CA and localhost server certificate. Never changes system trust."""
import argparse
import json
import os
from pathlib import Path
import subprocess
from runtime_ctl import runtime_root

parser = argparse.ArgumentParser()
parser.add_argument('--runtime-root', required=True)
args = parser.parse_args()
root, _ = runtime_root(args.runtime_root)
os.umask(0o077)
private = root / 'private'
outputs = ['gateway-ca-key.pem', 'gateway-ca.pem', 'gateway-key.pem', 'gateway-cert.pem']
if all((private / name).exists() for name in outputs):
    print(json.dumps({'certificates_already_present': True}))
    raise SystemExit(0)
if any((private / name).exists() for name in outputs):
    raise SystemExit('Partial private certificate setup exists; refusing to overwrite keys')
config = private / 'gateway-tls.cnf'
config.write_text('[req]\ndistinguished_name=dn\nprompt=no\n[dn]\nCN=localhost\nOU=RYNEW Local Data Governance\n[server_ext]\nbasicConstraints=critical,CA:FALSE\nkeyUsage=critical,digitalSignature,keyEncipherment\nextendedKeyUsage=serverAuth\nsubjectAltName=DNS:localhost,IP:127.0.0.1\nsubjectKeyIdentifier=hash\nauthorityKeyIdentifier=keyid:always\n[ca_ext]\nbasicConstraints=critical,CA:TRUE\nkeyUsage=critical,keyCertSign,cRLSign\nsubjectKeyIdentifier=hash\n')
ca_config = private / 'gateway-ca.cnf'
ca_config.write_text('[req]\ndistinguished_name=dn\nprompt=no\n[dn]\nCN=RYNEW Local Gateway CA\nOU=Local Development\n[ca_ext]\nbasicConstraints=critical,CA:TRUE\nkeyUsage=critical,keyCertSign,cRLSign\nsubjectKeyIdentifier=hash\n')
commands = [
    ['req', '-new', '-x509', '-newkey', 'rsa:3072', '-nodes', '-sha256', '-days', '365', '-config', str(ca_config), '-extensions', 'ca_ext', '-keyout', str(private / 'gateway-ca-key.pem'), '-out', str(private / 'gateway-ca.pem')],
    ['req', '-new', '-newkey', 'rsa:3072', '-nodes', '-sha256', '-config', str(config), '-keyout', str(private / 'gateway-key.pem'), '-out', str(private / 'gateway.csr')],
    ['x509', '-req', '-in', str(private / 'gateway.csr'), '-CA', str(private / 'gateway-ca.pem'), '-CAkey', str(private / 'gateway-ca-key.pem'), '-CAcreateserial', '-sha256', '-days', '365', '-extfile', str(config), '-extensions', 'server_ext', '-out', str(private / 'gateway-cert.pem')],
    ['verify', '-CAfile', str(private / 'gateway-ca.pem'), str(private / 'gateway-cert.pem')]
]
with (private / 'gateway-certificate-setup.log').open('ab') as log:
    for command in commands:
        subprocess.run(['/usr/bin/openssl'] + command, stdout=log, stderr=log, check=True)
for name in outputs:
    (private / name).chmod(0o600)
print(json.dumps({'local_ca_created': True, 'server_san': ['localhost', '127.0.0.1'], 'private_files_mode': '0600', 'system_trust_changed': False}))
