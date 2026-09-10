#!/usr/bin/env python3
"""Prepare synthetic files and print a plan; --execute is only for a separately reviewed Linux host."""
import argparse
import importlib.util
import json
import os
from pathlib import Path
import shutil
import sys
import uuid

ASSETS = Path(__file__).resolve().parent
SPEC = importlib.util.spec_from_file_location('kettle_linux_runtime', ASSETS.parents[2] / 'tools/data-governance/kettle_linux_runtime.py')
module = importlib.util.module_from_spec(SPEC); sys.modules[SPEC.name] = module; SPEC.loader.exec_module(module)


def main():
    os.umask(0o077)
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--config', required=True, type=Path); parser.add_argument('--execute', action='store_true')
    args = parser.parse_args(); config = module.Config.load(args.config)
    module.require(not config.endpoints, 'Offline smoke requires endpoints=[]')
    if args.execute:
        module.require(sys.platform == 'linux' and (os.geteuid() == config.uid or os.geteuid() == 0 and config.stage_run_owner),
                       'Live smoke needs the configured uid, or an explicitly approved root controller staging only this run')
    operation = config.operations_root / ('linux-smoke-' + uuid.uuid4().hex); operation.mkdir(mode=0o700)
    for directory in ['input', 'output', 'home', 'tmp']: (operation / directory).mkdir(mode=0o700)
    shutil.copyfile(ASSETS / 'fixtures/smoke.ktr', operation / 'transformation.ktr')
    shutil.copyfile(ASSETS / 'fixtures/smoke.csv', operation / 'input/smoke.csv')
    controller = module.LinuxRuntime(config)
    plan = controller.plan(operation, 'run'); module.atomic_write(operation / 'dry-run-plan.json', json.dumps(plan, indent=2))
    if not args.execute:
        print(json.dumps({'executed': False, 'syntheticDirectory': str(operation), 'plan': plan}, indent=2)); return
    events = []
    with (operation / 'engine.log').open('w') as error, (operation / 'events.ndjson').open('w') as log:
        handle = controller.launch(operation, 'run', stderr=error)
        for line in handle.stdout:
            log.write(line); log.flush(); events.append(json.loads(line))
        code = handle.wait(timeout=60)
    module.require(code == 0 and any(e.get('type') == 'terminal' and e.get('state') == 'SUCCEEDED' for e in events),
                   'Native execution did not report success; inspect owned evidence')
    result = operation / 'output/result.txt'; module.require(result.read_bytes() == (ASSETS / 'fixtures/expected.txt').read_bytes(), 'Output bytes differ')
    report = {'executed': True, 'verified': True, 'container': handle.identity(), 'network': 'none', 'bytes': result.stat().st_size,
              'sha256': module.digest(result), 'evidence': str(operation), 'cleanup': controller.cleanup(operation.name)}
    module.atomic_write(operation / 'acceptance.json', json.dumps(report, indent=2)); print(json.dumps(report, indent=2))


if __name__ == '__main__': main()
