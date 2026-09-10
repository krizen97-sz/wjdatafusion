#!/usr/bin/env python3
"""Verify actual Cron trigger -> frozen processing -> full FTP delivery; leave the plan paused."""
import argparse
import json
from pathlib import Path
import ssl
import time
import urllib.request

def main():
    p=argparse.ArgumentParser();p.add_argument('--runtime',type=Path,required=True);p.add_argument('--app-runtime',type=Path,required=True);a=p.parse_args()
    runtime,app=a.runtime.resolve(),a.app_runtime.resolve();assert json.loads((app/'private/app-owner.json').read_text())=={'owner':'rynew-data-governance-app','root':str(app)}
    context=ssl.create_default_context(cafile=str(runtime/'private/gateway-ca.pem'));token=None
    def call(path,method='GET',body=None):
        headers={'Content-Type':'application/json'}
        if token:headers['Authorization']='Bearer '+token
        r=urllib.request.Request('https://localhost:10443/prod-api'+('' if path=='/login' else '/governance')+path,headers=headers,method=method,data=json.dumps(body).encode() if body is not None else None)
        with urllib.request.urlopen(r,context=context,timeout=30) as response:d=json.load(response)
        assert d.get('code')==200,'Local API rejected '+path
        return d
    token=call('/login','POST',{**json.loads((app/'private/app-login.json').read_text()),'code':'','uuid':''})['token']
    source=json.loads((app/'evidence/kafka-pipeline-smoke.json').read_text())
    ftp=next(p for p in call('/ftp-connections')['data'] if p['name']=='本地FTP完整批次')
    plan=call('/schedules','POST',{'name':'真实定时处理与交付验证','releaseId':source['releaseId'],'cron':'0/10 * * * * ?','timeZone':'Asia/Shanghai','deliveryConnectionId':ftp['id']})['data']
    assert not plan['enabled'] and plan['deliveryConnectionId']==ftp['id'] and plan['deliveryTargetFingerprint']
    try:
        call('/schedules/'+plan['id']+'/state','POST',{'enabled':True,'revision':plan['revision']})
        deadline=time.monotonic()+90
        while time.monotonic()<deadline:
            plan=next(p for p in call('/schedules')['data'] if p['id']==plan['id'])
            if plan['deliveryStatus']=='DELIVERED' and not plan['activeRunId']:break
            if plan['recoveryRequired']:raise AssertionError('Schedule needs recovery; inspect plan '+plan['id'])
            time.sleep(.3)
        assert plan['deliveryStatus']=='DELIVERED' and not plan['activeRunId']
        job=call('/deliveries/'+plan['deliveryId'])['data'];assert job['status']=='DELIVERED' and job['runId']==plan['lastRunId']
        run=call('/test-runs/'+plan['lastRunId'])['data'];assert run['status']=='SUCCEEDED' and run['cleanupConfirmed'] and run['artifactsManifestAvailable']
        evidence={'scheduleId':plan['id'],'runId':plan['lastRunId'],'deliveryId':plan['deliveryId'],'realCronTriggered':True,'deliveryConfirmedBeforeOccupancyReleased':True,'completeArtifactCount':run['artifactCount'],'cleanupConfirmed':True}
    finally:
        plan=next(p for p in call('/schedules')['data'] if p['id']==plan['id'])
        paused=call('/schedules/'+plan['id']+'/state','POST',{'enabled':False,'revision':plan['revision']})['data'];assert not paused['enabled']
    evidence['pausedAfterTest']=True;(app/'evidence/scheduled-delivery-smoke.json').write_text(json.dumps(evidence,indent=2));print(json.dumps(evidence,indent=2))

if __name__=='__main__':main()
