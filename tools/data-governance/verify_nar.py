#!/usr/bin/env python3
"""Reject stale or conflicting governance classes inside a supposedly versioned NAR."""
import argparse
import hashlib
from io import BytesIO
from pathlib import Path
from zipfile import ZipFile


def verify(path, version):
    classes, components = {}, {}
    with ZipFile(path) as nar:
        for name in nar.namelist():
            if not name.endswith('.jar'):
                continue
            with ZipFile(BytesIO(nar.read(name))) as jar:
                for entry in jar.namelist():
                    if entry.startswith('com/hm/governance/') and entry.endswith('.class'):
                        if entry in classes:
                            raise ValueError('Duplicate governance class across bundled JARs: ' + entry)
                        classes[entry] = name
                    if entry.startswith('META-INF/maven/com.hm.governance/') and entry.endswith('/pom.properties'):
                        props = dict(line.split('=', 1) for line in jar.read(entry).decode().splitlines() if '=' in line and not line.startswith('#'))
                        artifact = props.get('artifactId')
                        if artifact in components or props.get('version') != version:
                            raise ValueError('Stale or duplicate governance dependency: ' + str(artifact))
                        components[artifact] = props.get('version')
        if set(components) != {'governance-compatibility-core', 'governance-nifi-processors'}:
            raise ValueError('Expected exactly one matching core and processor dependency')
        if not classes:
            raise ValueError('No governance implementation classes found')
    return {'version': version, 'components': components, 'governanceClasses': len(classes), 'sha256': hashlib.sha256(Path(path).read_bytes()).hexdigest()}


if __name__ == '__main__':
    parser = argparse.ArgumentParser(); parser.add_argument('nar', type=Path); parser.add_argument('--version', required=True); args = parser.parse_args()
    import json
    print(json.dumps(verify(args.nar, args.version), indent=2))
