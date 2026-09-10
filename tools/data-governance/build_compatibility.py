#!/usr/bin/env python3
"""Always build from clean generated staging, then verify the actual NAR dependency graph."""
import argparse
import os
from pathlib import Path
import subprocess
import xml.etree.ElementTree as ET
from verify_nar import verify

if __name__ == '__main__':
    p = argparse.ArgumentParser(); p.add_argument('--java-home', type=Path, required=True); args = p.parse_args()
    root = Path(__file__).resolve().parents[2]; module = root / 'data-governance/compatibility'
    java = args.java_home.resolve(); assert (java / 'bin/java').is_file()
    version = ET.parse(module / 'pom.xml').getroot().findtext('{http://maven.apache.org/POM/4.0.0}version')
    subprocess.run(['mvn', '-f', str(module / 'pom.xml'), 'clean', 'verify'], cwd=root, env={**os.environ, 'JAVA_HOME': str(java)}, check=True)
    import json
    print(json.dumps(verify(module / 'nifi-nar/target' / ('governance-nifi-nar-' + version + '.nar'), version), indent=2))
