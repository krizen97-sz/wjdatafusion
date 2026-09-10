#!/usr/bin/env python3
"""Install a verified new bundle only into the idle, owned local NiFi runtime."""
import argparse
from datetime import datetime
import hashlib
import json
from pathlib import Path
import shutil
import ssl
import subprocess
import sys
import urllib.parse
import urllib.request
from verify_nar import verify

if __name__ == '__main__':
    p=argparse.ArgumentParser();p.add_argument('--runtime-root',type=Path,required=True);p.add_argument('--nar',type=Path,required=True);p.add_argument('--version',required=True);a=p.parse_args()
    runtime=a.runtime_root.resolve();source=a.nar.resolve();marker=json.loads((runtime/'runtime.json').read_text())
    assert marker['owner']=='rynew-data-governance-runtime' and marker['runtimeRoot']==str(runtime)
    report=verify(source,a.version);assert source.name=='governance-nifi-nar-'+a.version+'.nar'
    home=Path(marker['nifiHome']).resolve();assert home.is_relative_to(runtime)
    destination=home/'lib'/source.name
    assert not destination.exists(), 'Bundle versions are immutable; use a new version, do not overwrite'
    credentials_file=runtime/'private/nifi-credentials.json';assert credentials_file.stat().st_mode&0o077==0
    credentials=json.loads(credentials_file.read_text());base=credentials['baseUrl'].rstrip('/')
    assert urllib.parse.urlparse(base).netloc in {'localhost:9443','127.0.0.1:9443'}
    if not base.endswith('/nifi-api'):base+='/nifi-api'
    context=ssl.create_default_context(cafile=credentials['caCert'])
    request=urllib.request.Request(base+'/access/token',data=urllib.parse.urlencode({'username':credentials['username'],'password':credentials['password']}).encode(),headers={'Content-Type':'application/x-www-form-urlencoded'})
    with urllib.request.urlopen(request,context=context,timeout=15) as response:token=response.read().decode()
    with urllib.request.urlopen(urllib.request.Request(base+'/flow/process-groups/root/status',headers={'Authorization':'Bearer '+token}),context=context,timeout=15) as response:status=json.load(response)['processGroupStatus']['aggregateSnapshot']
    assert status['activeThreadCount']==0 and status['flowFilesQueued']==0,'NiFi must be idle before installation'
    controller=Path(__file__).with_name('runtime_ctl.py');command=[sys.executable,str(controller),'--runtime-root',str(runtime)]
    subprocess.run(command+['stop','nifi'],check=True)
    backup=runtime/'backups'/('compat-'+a.version+'-'+datetime.now().strftime('%Y%m%d-%H%M%S'));backup.mkdir(mode=0o700)
    for name in ['flow.json.gz','nifi.properties']:
        target=backup/name;shutil.copyfile(home/'conf'/name,target);target.chmod(0o600)
    shutil.copyfile(source,destination);destination.chmod(0o644)
    assert hashlib.sha256(destination.read_bytes()).hexdigest()==report['sha256']
    report.update({'installed':str(destination),'backup':str(backup),'oldBundlesPreserved':True})
    evidence=backup/'installation.json';evidence.write_text(json.dumps(report,indent=2));evidence.chmod(0o600)
    subprocess.run(command+['start','nifi'],check=True)
    print(json.dumps(report,indent=2))
