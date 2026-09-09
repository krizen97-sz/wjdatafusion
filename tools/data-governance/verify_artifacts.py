#!/usr/bin/env python3
"""Verify pinned archives before executing them. Prints no runtime credentials."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import warnings

parser = argparse.ArgumentParser()
parser.add_argument('--runtime-root', required=True)
parser.add_argument('--hash-only', action='store_true')
args = parser.parse_args()
root = Path(args.runtime_root).expanduser().resolve()
lock = json.loads(Path(__file__).with_name('artifact-lock.json').read_text())
if not args.hash_only:
    from pgpy import PGPKey, PGPSignature
results = []
for artifact in lock['artifacts']:
    path = root / 'downloads' / artifact['filename']
    digest = hashlib.new(artifact['hash_algorithm'])
    with path.open('rb') as stream:
        for data in iter(lambda: stream.read(1024 * 1024), b''):
            digest.update(data)
    actual = digest.hexdigest()
    if actual != artifact['hash']:
        raise SystemExit(artifact['id'] + ': checksum mismatch; do not execute')
    result = {'artifact': artifact['filename'], 'checksum_match': True, 'hash_algorithm': artifact['hash_algorithm'], 'hash': actual}
    if not args.hash_only:
        signature = PGPSignature.from_file(str(root / 'downloads' / artifact['signature_file']))
        key_text = (root / 'downloads' / artifact['key_file']).read_text()
        signer = None
        with warnings.catch_warnings(record=True) as notices:
            warnings.simplefilter('always')
            for block in re.findall(r'-----BEGIN PGP PUBLIC KEY BLOCK-----.*?-----END PGP PUBLIC KEY BLOCK-----', key_text, re.S):
                key, _ = PGPKey.from_blob(block)
                if str(key.fingerprint) == artifact['signer_fingerprint']:
                    signer = key
                    break
            if signer is None or not bool(signer.verify(path.read_bytes(), signature)):
                raise SystemExit(artifact['id'] + ': detached signature failed; do not execute')
        result.update(signature_verified=True, signer_fingerprint=str(signer.fingerprint), pgpy_warning_count=len(notices))
    results.append(result)
print(json.dumps({'verification': results, 'trust_boundary': 'Pinned hashes and detached signatures against official HTTPS-published keys; independent web-of-trust and revocation validation not claimed.'}))
