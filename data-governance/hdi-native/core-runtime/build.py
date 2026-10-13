#!/usr/bin/env python3
"""Build the small original-protocol control client against original Core jars."""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess


def build(core_home, java_home, output):
    core_home = core_home.resolve(strict=True)
    java_home = java_home.resolve(strict=True)
    output = output.absolute()
    if output.exists():
        raise ValueError('Build output must be new')
    source = Path(__file__).resolve().parent / 'src/CoreMetadataBridge.java'
    output.mkdir(parents=True, mode=0o700)
    classes = output / 'classes'
    classes.mkdir()
    # Core ships its own ZooKeeper client; do not take the management server's client.
    cp = str(core_home / 'lib/zookeeper-hw-hw-ei.jar') + ':' + str(core_home / 'lib/*')
    subprocess.run([str(java_home / 'bin/javac'), '--release', '11', '-encoding', 'UTF-8',
                    '-cp', cp, '-d', str(classes), str(source)], check=True)
    jar = output / 'rynew-hdi-core-control.jar'
    subprocess.run([str(java_home / 'bin/jar'), '--create', '--file', str(jar),
                    '-C', str(classes), '.'], check=True)
    receipt = {'protocolVersion': 1, 'javaRelease': 11,
               'sourceSha256': hashlib.sha256(source.read_bytes()).hexdigest(),
               'jarSha256': hashlib.sha256(jar.read_bytes()).hexdigest()}
    (output / 'build-evidence.json').write_text(json.dumps(receipt, indent=2) + '\n')
    print(json.dumps({'jar': str(jar), **receipt}))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--core-home', type=Path, required=True)
    parser.add_argument('--java-home', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    build(args.core_home, args.java_home, args.output)
