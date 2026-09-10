#!/usr/bin/env python3
"""Real local Kafka receipt -> published NiFi processing -> FTP -> explicit offset confirmation."""
import argparse
import json
from pathlib import Path
import ssl
import subprocess
import time
import urllib.request
import uuid

def main():
    p=argparse.ArgumentParser();p.add_argument('--runtime',type=Path,required=True);p.add_argument('--app-runtime',type=Path,required=True);args=p.parse_args()
    runtime,app=args.runtime.resolve(),args.app_runtime.resolve();repo=Path(__file__).resolve().parents[2]
    assert json.loads((app/'private/app-owner.json').read_text())=={'owner':'rynew-data-governance-app','root':str(app)}
    fixture=json.loads((runtime/'kafka/evidence/latest-smoke.json').read_text());assert fixture['syntheticOnly'] and fixture['seed']['topic'].startswith('rynew-governance-smoke-')
    context=ssl.create_default_context(cafile=str(runtime/'private/gateway-ca.pem'));token=None
    def call(path,method='GET',body=None,ok=True):
        headers={'Content-Type':'application/json'}
        if token:headers['Authorization']='Bearer '+token
        request=urllib.request.Request('https://localhost:10443/prod-api'+('' if path=='/login' else '/governance')+path,method=method,headers=headers,data=json.dumps(body,ensure_ascii=False).encode() if body is not None else None)
        with urllib.request.urlopen(request,context=context,timeout=35) as r:d=json.load(r)
        assert (d.get('code')==200)==ok, 'Unexpected response at '+path
        return d
    login=app/'private/app-login.json';assert login.stat().st_mode&0o077==0
    token=call('/login','POST',{**json.loads(login.read_text()),'code':'','uuid':''})['token'];owner=call('/kafka-status')['data']['ownerId']
    run_key=uuid.uuid4().hex[:12];group=f'rynew-governance-{owner}-pipeline-{run_key}'
    profile=call('/kafka-profiles','POST',{'name':'Kafka真实批次流水线 '+run_key,'bootstrapServers':['127.0.0.1:19092'],'topic':fixture['seed']['topic'],'groupId':group})['data']
    kafka_home=next((runtime/'kafka/apps').glob('kafka_*'))
    java=runtime/'jdks/jdk-21.0.12.1+1/Contents/Home/bin/java'
    def offsets():
        command=[str(java),'-cp',str(kafka_home/'libs/*'),str(repo/'tools/data-governance/KafkaOffsetAudit.java'),fixture['clusterId'],group]
        result=subprocess.run(command,capture_output=True,text=True,timeout=20);assert result.returncode==0,'Offset audit failed'
        return json.loads(next(line for line in reversed(result.stdout.splitlines()) if line.startswith('{')))
    assert offsets()=={}
    receipt=call('/kafka-profiles/'+profile['id']+'/receive','POST')['data'];assert receipt['status']=='RECEIVED' and receipt['recordCount']==6
    call('/kafka-receipts/'+receipt['id']+'/commit','POST',ok=False);assert offsets()=={}
    project=call('/projects','POST',{'name':'Kafka实际流水线 '+run_key,'description':'实际broker批次、冻结处理配置、完整FTP与显式消费确认'})['data']
    flow=call('/flows','POST',{'projectId':project['id'],'name':'Kafka消息到协议文本','templateId':'blank'})['data']
    kinds={item['id']:item for item in call('/design/node-types')['data']}
    nodes={}
    for index,key in enumerate(['source','record-transform','delimited','capture']):
        kind=kinds[key];properties=dict(kind['properties'])
        if key=='record-transform':properties={'Operations':json.dumps([{'op':'get','path':'/k_message','output':'message','type':'STRING'},{'op':'constant','output':'picture','value':''}])}
        if key=='delimited':properties['Filename Prefix']='kafka_pipeline'
        nodes[key]=call('/flows/'+flow['id']+'/design/nodes','POST',{'type':kind['type'],'name':{'source':'批次输入','record-transform':'保留原消息','delimited':'协议文件','capture':'结果观察'}[key],'role':kind['role'],'position':{'x':index*300,'y':180},'properties':properties})['data']['id']
    edges=[('source','record-transform','success'),('record-transform','delimited','success'),('record-transform','capture','empty'),('record-transform','capture','failure'),('delimited','capture','success'),('delimited','capture','empty'),('delimited','capture','failure')]
    for source,target,relation in edges:call('/flows/'+flow['id']+'/design/connections','POST',{'sourceId':nodes[source],'targetId':nodes[target],'relationship':relation})
    release=call('/releases','POST',{'flowId':flow['id'],'name':'V1 Kafka处理配置','inputJson':'[{"k_message":"fixed-preview-sample"}]','parameters':{}})['data']
    ftp=next(item for item in call('/ftp-connections')['data'] if item['name']=='本地FTP完整批次')
    execution=call('/kafka-receipts/'+receipt['id']+'/execute','POST',{'releaseId':release['id'],'deliveryConnectionId':ftp['id']})['data']
    deadline=time.monotonic()+120
    while execution['status'] in {'RUN_PLANNED','RUNNING','DELIVERING'} and time.monotonic()<deadline:
        time.sleep(.5);execution=call('/kafka-receipts/'+receipt['id']+'/execution')['data']
    assert execution['status']=='READY_TO_ACK', 'Pipeline failed; inspect private execution '+receipt['id']
    assert execution['releaseHash']==release['definitionHash'] and execution['executionHash']!=execution['releaseHash']
    actual_run=call('/test-runs/'+execution['runId'])['data'];assert actual_run['status']=='SUCCEEDED' and actual_run['cleanupConfirmed'] and actual_run['artifactsManifestAvailable']
    delivery=call('/deliveries/'+execution['deliveryId'])['data'];assert delivery['status']=='DELIVERED'
    assert offsets()=={}, 'Processing or delivery must not silently acknowledge Kafka'
    acknowledged=call('/kafka-receipts/'+receipt['id']+'/commit','POST')['data'];assert acknowledged['status']=='COMMITTED' and not acknowledged['leaseHeld']
    assert offsets()=={'0':'3','1':'3'}
    assert call('/kafka-receipts/'+receipt['id']+'/commit','POST')['data']['status']=='COMMITTED'
    empty=call('/kafka-profiles/'+profile['id']+'/receive','POST')['data'];assert empty['status']=='EMPTY' and empty['recordCount']==0
    call('/kafka-receipts/'+empty['id']+'/release','POST')
    result={'profileId':profile['id'],'groupId':group,'receiptId':receipt['id'],'projectId':project['id'],'flowId':flow['id'],'releaseId':release['id'],'runId':execution['runId'],'deliveryId':execution['deliveryId'],'records':6,'realSourceInputUsed':True,'immutableReleaseUnchanged':True,'distinctExecutionHash':True,'deliveredBeforeAcknowledgement':True,'offsetsAfterExplicitCommit':{'0':'3','1':'3'},'nextReceiveEmpty':True}
    (app/'evidence/kafka-pipeline-smoke.json').write_text(json.dumps(result,ensure_ascii=False,indent=2));print(json.dumps(result,ensure_ascii=False,indent=2))

if __name__=='__main__':main()
