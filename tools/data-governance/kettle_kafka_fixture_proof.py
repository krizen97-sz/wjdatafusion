#!/usr/bin/env python3
"""Run original KafkaConsumer -> ScriptValueMod -> KafkaProducer against the owned legacy fixture."""
import argparse
import importlib.util
import json
import os
from pathlib import Path
import shutil
import subprocess
import time
import uuid
import xml.etree.ElementTree as ET
import kettle_kafka_fixture as fixture
from kafka_dev import file_hash, save_json, require


def tag(parent, kind, value=None, **values):
    element = ET.SubElement(parent, kind)
    if value is not None: element.text = str(value)
    for key, text in values.items(): tag(element, key, text)
    return element


def transformation(prefix, group, count, *, preview=False, continuous=False):
    trans = ET.Element('transformation'); tag(trans, 'info', name='Original Kafka round trip', size_rowset=100)
    order = tag(trans, 'order')
    source = tag(trans, 'step', name='Original Kafka input', type='KafkaConsumer', copies=1, distribute='Y',
                 TOPIC=prefix+'-input', FIELD='k_message', KEY_FIELD='k_key', LIMIT=0 if continuous else count,
                 TIMEOUT=0 if continuous else 1000)
    if not continuous: tag(source, 'STOPONEMPTYTOPIC', 'true')
    kafka = tag(source, 'KAFKA')
    for key, value in {'zookeeper.connect':'127.0.0.1:22181', 'group.id':group, 'auto.offset.reset':'smallest',
                       'consumer.id':group+'-client', 'client.id':group+'-client',
                       'auto.commit.enable':'false', 'auto.commit.interval.ms':'100',
                       'zookeeper.connection.timeout.ms':'6000','zookeeper.session.timeout.ms':'6000',
                       'consumer.timeout.ms':'1000','offsets.storage':'zookeeper','dual.commit.enabled':'false'}.items():
        tag(kafka, key, value)
    step = tag(trans, 'step', name='Original script processing', type='ScriptValueMod', copies=1, distribute='Y', compatible='N', optimizationLevel=0)
    script = tag(tag(step, 'jsScripts'), 'jsScript', jsScript_type=0, jsScript_name='Synthetic suffix',
                 jsScript_script='var forwarded = String(k_message) + "|original-step";')
    tag(tag(step, 'fields'), 'field', name='forwarded', rename='', type='String', length=-1, precision=-1, replace='N')
    target = tag(trans, 'step', name='Original Kafka output', type='KafkaProducer', copies=1, distribute='Y',
                 TOPIC=prefix+'-output', FIELD='forwarded')
    options = tag(target, 'KAFKA')
    for key, value in {'metadata.broker.list':'127.0.0.1:29092','request.required.acks':'1',
                       'serializer.class':'kafka.serializer.DefaultEncoder','producer.type':'sync',
                       'request.timeout.ms':'5000','message.send.max.retries':'0'}.items():tag(options,key,value)
    tag(order, 'hop', **{'from':'Original Kafka input','to':'Original script processing','enabled':'Y'})
    tag(order, 'hop', **{'from':'Original script processing','to':'Original Kafka output','enabled':'Y'})
    return ET.tostring(trans,encoding='unicode')


def module(path):
    spec = importlib.util.spec_from_file_location('native_worker_under_test', path)
    loaded = importlib.util.module_from_spec(spec); spec.loader.exec_module(loaded); return loaded


def clone_worker(root, source, sources):
    manifest = json.loads((source/'manifest.json').read_text())
    target = root/'worker'
    if target.exists():
        require(json.loads((target/'proof-owner.json').read_text())['owner']=='kettle-legacy-proof-worker','Unknown worker directory')
    else:
        target.mkdir(mode=0o700); (target/'lib').mkdir(mode=0o700); (target/'classes').mkdir(mode=0o700)
        save_json(target/'proof-owner.json',{'owner':'kettle-legacy-proof-worker'})
    original_names = {item['file'] for item in manifest['libraries']}
    for item in manifest['libraries']:
        original=source/'lib'/item['file']; require(file_hash(original)==item['sha256'],'Original library changed')
        copied=target/'lib'/item['file']
        if not copied.exists():shutil.copy2(original,copied)
        require(file_hash(copied)==item['sha256'],'Proof library changed')
    cp=[str(target/'classes')]+[str(target/'lib'/Path(path).name) for path in manifest['classpath'].split(os.pathsep)
                              if Path(path).name in original_names]
    manifest['classpath']=os.pathsep.join(cp);manifest.pop('runtimeExtensions',None)
    java=Path(manifest['javaHome'])
    require(not (target/'classes').is_symlink(),'Proof classes directory cannot be linked')
    for old_class in (target/'classes').rglob('*.class'):
        require(old_class.is_file() and not old_class.is_symlink(),'Unexpected compiled class entry')
        old_class.unlink()
    command=[str(java/'bin/javac'),'-proc:none','-encoding','UTF-8','-cp',manifest['classpath'],'-d',str(target/'classes')]
    command += [str(p) for p in sorted(sources.glob('*.java'))]
    result=subprocess.run(command,capture_output=True,text=True)
    (root/'logs/worker-compile.log').write_text(result.stdout+result.stderr)
    require(result.returncode==0,'Worker compile failed')
    save_json(target/'manifest.json',manifest)
    save_json(target/'proof-sources.json',{p.name:file_hash(p) for p in sources.glob('*.java')})
    return target


def main():
    os.umask(0o077)
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--runtime',type=Path,required=True)
    parser.add_argument('--worker-runtime',type=Path,required=True)
    parser.add_argument('--worker-module',type=Path,required=True)
    parser.add_argument('--worker-sources',type=Path,required=True)
    args=parser.parse_args(); root,marker,java=fixture.owned(args.runtime)
    fixture.probe(root,marker,java,'health')
    worker_root=clone_worker(root,args.worker_runtime,args.worker_sources)
    native=module(args.worker_module)
    policy=root/'private/worker-network.json'
    save_json(policy,{'endpoints':[{'host':'127.0.0.1','port':p} for p in fixture.PORTS.values()]})
    worker=native.Worker(worker_root,timeout=35,network_policy=policy)
    prefix='kettle-v2-'+uuid.uuid4().hex
    proof=root/'evidence'/prefix;proof.mkdir(mode=0o700)
    seed=fixture.probe(root,marker,java,'seed',prefix,'8');save_json(proof/'seed.json',seed)
    group=prefix+'-run';preview_group=prefix+'-preview';stop_group=prefix+'-stop-preview'
    before=fixture.probe(root,marker,java,'audit',group,prefix+'-input');save_json(proof/'before-run.json',before)
    run_id=worker.launch('run',transformation(prefix,group,5),run_id=prefix+'-run')
    result=worker.wait(run_id);save_json(proof/'run.json',result)
    require(result['state']=='SUCCEEDED','Original Kafka pipeline did not succeed; inspect '+str(worker_root/'operations'/run_id))
    read=fixture.probe(root,marker,java,'read',prefix+'-output','5');save_json(proof/'output-readback.json',read)
    require([row['value'] for row in read['records']]==[value+'|original-step' for value in seed['records'][:5]], 'Producer target differs')
    require(read['endOffset']==5,'Finite batch sent more than five messages')
    after=fixture.probe(root,marker,java,'audit',group,prefix+'-input');save_json(proof/'after-run.json',after)
    require(after['zookeeperOffsets'].get('0')=='5','First batch did not commit exactly five consumed messages')
    tail_id=worker.launch('run',transformation(prefix,group,3),run_id=prefix+'-tail')
    tail=worker.wait(tail_id);save_json(proof/'tail-run.json',tail)
    require(tail['state']=='SUCCEEDED','Second bounded batch did not succeed')
    read=fixture.probe(root,marker,java,'read',prefix+'-output','8');save_json(proof/'full-readback.json',read)
    require([row['value'] for row in read['records']]==[value+'|original-step' for value in seed['records']] and read['endOffset']==8,
            'Bounded batches lost or duplicated the unprocessed tail')
    failed_group=prefix+'-failure-run'
    invalid=transformation(prefix,failed_group,2).replace('kafka.serializer.DefaultEncoder','kafka.serializer.StringEncoder')
    failed_id=worker.launch('run',invalid,run_id=prefix+'-producer-failure')
    failed=worker.wait(failed_id);save_json(proof/'failed-producer.json',failed)
    require(failed['state']=='FAILED' and failed.get('errors',0)>0,'Intentional producer error was not visible')
    failed_offsets=fixture.probe(root,marker,java,'audit',failed_group,prefix+'-input');save_json(proof/'failed-offsets.json',failed_offsets)
    require(not failed_offsets['brokerOffsets'] and not failed_offsets['zookeeperOffsets'],'Failed manual-commit run advanced source offsets')
    prior_preview=fixture.probe(root,marker,java,'audit',preview_group,prefix+'-input')
    preview_id=worker.launch('run',transformation(prefix,preview_group,2,preview=True),preview_step='Original script processing',row_limit=2,run_id=prefix+'-preview')
    preview=worker.wait(preview_id);save_json(proof/'preview.json',preview)
    require(preview['state'] in {'SUCCEEDED','PREVIEW_COMPLETE'}, 'Preview did not complete')
    require(any(event.get('type')=='preview-offset-commit-blocked' for event in worker.runs[preview_id]['events']),
            'Original completion attempted no observable guarded commit')
    after_preview=fixture.probe(root,marker,java,'audit',preview_group,prefix+'-input')
    save_json(proof/'preview-offsets.json',{'before':prior_preview,'after':after_preview})
    require(not after_preview['brokerOffsets'] and not after_preview['zookeeperOffsets'], 'Preview committed offsets')
    require(not after_preview['zookeeperConsumerOwners'],'Preview left consumer ownership behind')
    repeated=fixture.probe(root,marker,java,'read',prefix+'-output','8')
    require(repeated['endOffset']==8,'Preview wrote to output topic')
    stop_id=worker.launch('run',transformation(prefix,stop_group,0,preview=True,continuous=True),preview_step='Original script processing',row_limit=100,run_id=prefix+'-stop')
    deadline=time.monotonic()+12
    while time.monotonic()<deadline:
        events=worker.runs[stop_id]['events']
        if any(e.get('type')=='row' for e in events):break
        if 'finishedAt' in worker.runs[stop_id]:break
        time.sleep(0.1)
    worker.stop(stop_id);stopped=worker.wait(stop_id);save_json(proof/'stop.json',stopped)
    require(stopped['state']=='STOPPED' and not worker.runs[stop_id].get('forcedStop'),'Original consumer stop was not graceful')
    require(any(event.get('type')=='preview-offset-commit-blocked' for event in worker.runs[stop_id]['events']),
            'Stopped preview did not demonstrate its explicit commit guard')
    after_stop=fixture.probe(root,marker,java,'audit',stop_group,prefix+'-input');save_json(proof/'stop-offsets.json',after_stop)
    require(not after_stop['brokerOffsets'] and not after_stop['zookeeperOffsets'],'Stopped preview committed offsets')
    require(not after_stop['zookeeperConsumerOwners'],'Stopped preview left consumer ownership behind')
    report={'passed':True,'prefix':prefix,'records':8,'batchSizes':[5,3],'originalPipeline':result,'tailRun':tail,'preview':preview,'stop':stopped,
            'before':before,'after':after,'previewOffsets':after_preview,'stoppedOffsets':after_stop,
            'failedProducer':failed,'failedOffsets':failed_offsets,'outputReadback':read,
            'autoCommitEnabled':False,'fixtureVersion':fixture.LOCK['version'],'workerRoot':str(worker_root),
            'proofScriptSha256':file_hash(Path(__file__))}
    save_json(proof/'acceptance.json',report)
    print(json.dumps({'passed':True,'records':8,'batchSizes':[5,3],'evidence':str(proof/'acceptance.json'),'previewCommitted':False,'stopForced':False},indent=2))


if __name__=='__main__':main()
