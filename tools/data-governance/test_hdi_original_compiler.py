#!/usr/bin/env python3
"""Contract tests against the local original SDK, never a remote service.

Run with HDI_ORIGINAL_BUNDLE and HDI_COMPILER_JAVA_HOME set to verified local
paths. The integration cases compile the real original generators once.
"""
import base64
import copy
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

MODULE = Path(__file__).with_name('hdi_original_compiler.py')
spec = importlib.util.spec_from_file_location('hdi_original_compiler', MODULE)
launcher = importlib.util.module_from_spec(spec)
spec.loader.exec_module(launcher)


def fixture_visual(value='platform revision one'):
    field = dict(fieldName='message', fieldType='string', fieldSize=64, fieldPrecision=0,
                 fieldKeyFlag=0, fieldOrder=1, fieldDisplayName='message', fieldExpression=value)
    output = {**field, 'fieldName': 'mapped_message', 'fieldExpression': 'mapped_message',
              'fieldDisplayName': 'mapped_message', 'fieldSrcType': 0, 'enableFlag': 1, 'used': True}
    return {'flowVo': {'flowName': 'platform_task', 'flowDisplayName': '平台真实任务编译'},
            'panelUIInfo': {'scale': 1}, 'streamVoList': [], 'paramVoList': [],
            'nodes': {
                '110': {'id': 110, 'adapterName': 'dataGeneratorInput', 'adapterType': 'input',
                        'adapterData': [{**field, 'ruleType': 'FIXED_VALUE'}], 'streamFieldVoList': [field],
                        'adapterParamVoList': [{'paramName': 'etl.plugin.input.generator.data.size', 'paramValue': '3'}]},
                '120': {'id': 120, 'adapterName': 'fieldMapping', 'adapterType': 'preproc',
                        'transformFieldVoList': [{'fieldSrcName': 'message', 'fieldDstName': 'mapped_message',
                                                 'fieldSrcType': 'string', 'fieldDstType': 'string', 'processType': 'move'}]},
                '130': {'id': 130, 'adapterName': 'fileOutput', 'adapterType': 'output',
                        'adapterData': {'sourceType': 'local', 'localDir': '/平台输出',
                                        'sourcePathType': 1, 'fileType': 'csv', 'colSplitter': ',',
                                        'encoding': 'UTF-8', 'addHeaderLine': 'true', 'isAsyncGenerateText': 'false'},
                        'dstTableFieldVoList': [output],
                        'adapterParamVoList': [{'paramName': 'etl.file.maxRow', 'paramValue': '10'},
                                               {'paramName': 'etl.file.flushTime', 'paramValue': '1000'}]}},
            'links': [{'source': 110, 'target': 120, 'connectStatus': 0}, {'source': 120, 'target': 130, 'connectStatus': 0}]}


def original_ui_visual():
    """Sanitized shape from the original browser's three-node form, not a hand-filled DB row.

    The original form omits processType/fieldExpression/enableFlag, uses blank
    numeric precision and keeps Element control names in adapter parameter DTOs.
    """
    visual = fixture_visual('browser original core')
    field = dict(fieldName='message', fieldType='varchar', fieldSize=64, fieldPrecision='',
                 fieldKeyFlag=0, fieldExpression='browser original core', ruleType='FIXED_VALUE',
                 fieldRangeMin='', fieldRangeMax='')
    visual['nodes']['110']['adapterData'] = [field]
    visual['nodes']['110']['streamFieldVoList'] = [{**field, 'fieldDisplayName': 'message', 'fieldAlias': 'message'}]
    visual['nodes']['110']['adapterParamVoList'][0].update(valueType='el-input', minValue=None, maxValue=None)
    visual['nodes']['120']['transformFieldVoList'] = [dict(
        fieldSrcName='message', fieldDstName='message', fieldSrcDisplayName='message', fieldDstDisplayName='message',
        fieldSrcType='varchar', fieldDstType='varchar', fieldSrcSize=64, fieldDstSize=64, fieldSrcPrecision='', fieldDstPrecision='')]
    node = visual['nodes']['130']
    node['adapterData'].update(isAsyncGenerateText=False, databaseSid='', absoluteDir='', relativeDir='',
                               numOfJson=1, imgField='', arrayPath='', backFill='false', jsonTemplate='')
    node['dstTableFieldVoList'] = [dict(fieldName='message', fieldDisplayName='message', fieldSize=64,
                                      fieldType='varchar', fieldPrecision='', isNew=None, isUpdate=None, fieldKeyFlag=0)]
    node['adapterParamVoList'] = [dict(paramName=name, paramValue=value, valueType=control, minValue=None, maxValue=None)
                                 for name, value, control in [
        ('etl.file.isUpload', 'false', 'el-switch'),
        ('etl.file.rowSplitChar', ',', 'el-input'), ('etl.file.retrytimes', '5', 'el-input'),
        ('etl.file.maxRow', '100', 'el-input'), ('etl.file.flushTime', '10000', 'el-input')]]
    visual.update(status=True, saveFlowStatus=0)
    return visual


def connector_visual(input_plugin='dataGeneratorInput', output_plugin='fileOutput'):
    visual = fixture_visual('native connector value')
    first, last = visual['nodes']['110'], visual['nodes']['130']
    connections = {}
    for plugin, node, connection_id, kind, db_type, port in [
            (input_plugin, first, '71', 'postgresql' if input_plugin.startswith('postgresql') else 'kafka' if input_plugin.startswith('kafka') else 'ftp', 16 if input_plugin.startswith('postgresql') else 8 if input_plugin.startswith('kafka') else 18, 15432 if input_plugin.startswith('postgresql') else 19092 if input_plugin.startswith('kafka') else 2121),
            (output_plugin, last, '72', 'postgresql' if output_plugin.startswith('postgresql') else 'kafka' if output_plugin.startswith('kafka') else 'ftp', 16 if output_plugin.startswith('postgresql') else 8 if output_plugin.startswith('kafka') else 18, 15432 if output_plugin.startswith('postgresql') else 19092 if output_plugin.startswith('kafka') else 2121)]:
        if plugin == 'dataGeneratorInput' or plugin == 'fileOutput':
            continue
        node['adapterName'] = 'fileOutput' if plugin == 'ftpFileOutput' else plugin
        node['dbId'] = int(connection_id)
        node['adapterParamVoList'] = []
        node['adapterData'] = {}
        connections[connection_id] = {'connectionRevision':'1','datasourceParamVoList':[], 'datasource':{
            'id':int(connection_id),'name':'isolated-original-'+kind,'dbType':db_type,'databaseIp':'127.0.0.1','databasePort':str(port),
            'databaseSid':'hdi_fixture','databaseUsername':'hdi_fixture','databasePassword':'fixture-only-not-a-secret',
            'dbUrl':('jdbc:postgresql://127.0.0.1:'+str(port)+'/hdi_fixture') if kind=='postgresql' else ('kafka2://' if kind=='kafka' else 'ftp://')+'127.0.0.1:'+str(port)+('' if kind=='kafka' else '/hdi_fixture'),
            'ext1':'1' if kind=='ftp' else '', 'ext2':'ftp' if kind=='ftp' else '', 'ext3':'','inOutFlag':0}}
        if node is first:
            node['srcTableUseVoList'] = [{'id':90,'tableId':91,'srcDbId':int(connection_id),'tableName':'source_rows' if kind=='postgresql' else 'hdi_source',
                'tableOwner':'public','tableSchem':'public','tableAlias':'a1','tableOrder':1,'tableUseFlag':1,'tableViewFlag':0,'incrType':0,'partFlag':0}]
            node['streamFieldVoList'][0].update(fieldType='varchar',fieldExpression='message' if kind=='postgresql' else '',fieldSrcName='message',srcTableType=1,srcTableId=91,tableAlias='a1')
            if plugin == 'fileInput':
                node['adapterData'] = {'sourceType':'ftp','sourcePathType':2,'absoluteDir':'/inbox','localDir':'/平台输出',
                    'fileType':'csv','encoding':'UTF-8','colSplitter':',','beginRow':'2','incrMode':'time','maskFilename':'.*\\.csv','parsedData':'true'}
            elif plugin == 'kafkaInput':
                node['adapterParamVoList']=[{'paramName':'etl.kafka.auto.offset.reset','paramValue':'earliest'},{'paramName':'format','paramValue':'json'}]
        else:
            if plugin == 'ftpFileOutput':
                node['adapterData'] = {'sourceType':'ftp','sourcePathType':2,'absoluteDir':'/outbox','localDir':'/平台输出',
                    'fileType':'csv','encoding':'UTF-8','colSplitter':',','addHeaderLine':'true','isAsyncGenerateText':'false'}
            node['dstTableVo'] = {'id':92,'dbId':int(connection_id),'tableName':'target_rows' if kind=='postgresql' else 'hdi_target',
                'tableOwner':'public','messageFormat':'json','messageField':'','writeOperation':'INSERT','dataCleansing':'0','addEtlColumn':0,'enableFlag':1}
            if plugin in ['postgresqlOutput','kafkaV2Output']:
                node['adapterParamVoList']=[{'paramName':'etl.plugin.partial.failed.stop.enable','paramValue':'true'}]
            if plugin == 'postgresqlOutput':
                # Original JDBC binds values by fieldName; align the upstream original mapping.
                visual['nodes']['120']['transformFieldVoList'][0]['fieldDstName']='message'
                node['dstTableFieldVoList'][0].update(fieldName='message',fieldExpression='message',fieldDisplayName='message')
    return visual, connections


class LauncherUnitTests(unittest.TestCase):
    def test_traversal_and_symlink_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            with self.assertRaises(ValueError):
                launcher.regular(root, '../escape')
            (root / 'original').write_text('x')
            (root / 'alias').symlink_to(root / 'original')
            with self.assertRaises(ValueError):
                launcher.regular(root, 'alias')

    def test_unrecognized_provenance_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / 'bundle-manifest.json').write_text('{}')
            with self.assertRaises(ValueError):
                launcher.classpath(root)


@unittest.skipUnless(os.environ.get('HDI_ORIGINAL_BUNDLE') and os.environ.get('HDI_COMPILER_JAVA_HOME'), 'original local SDK not configured')
class OriginalSdkTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.bundle = Path(os.environ['HDI_ORIGINAL_BUNDLE'])
        cls.java = Path(os.environ['HDI_COMPILER_JAVA_HOME'])
        cls.temporary = tempfile.TemporaryDirectory(prefix='original-compiler-tests-')
        cls.root = Path(cls.temporary.name).resolve()
        jars = launcher.classpath(cls.bundle)
        cls.cp = os.pathsep.join([str(cls.root), *map(str, jars)])
        subprocess.run([str(cls.java / 'bin/javac'), '--release', '11', '-encoding', 'UTF-8', '-cp', cls.cp,
                        '-d', str(cls.root), str(launcher.SOURCE)], capture_output=True, check=True)
        cls.context = dict(businessTaskId='17', revision='2', ownerId='4', workspaceId='1', projectCode='rynew-platform')

    @classmethod
    def tearDownClass(cls):
        cls.temporary.cleanup()

    def invoke(self, payload):
        raw = payload if isinstance(payload, str) else json.dumps(payload, ensure_ascii=False)
        result = subprocess.run([str(self.java / 'bin/java'), '-Xmx256m', '-cp', self.cp,
                                 'OriginalTaskCompiler', str(self.bundle)], input=raw.encode(),
                                cwd=self.root, capture_output=True, timeout=25)
        parsed = json.loads(result.stdout)
        self.assertEqual(result.returncode == 0, parsed['ok'])
        return parsed

    def prepare(self, visual=None):
        return self.invoke(dict(operation='prepare', context=self.context, visual=visual or fixture_visual()))

    def compile_request(self, prepared=None, offset=0):
        prepared = prepared or self.prepare()
        return dict(operation='compile', snapshotJson=prepared['snapshotJson'], snapshotSha256=prepared['snapshotSha256'],
                    runContext=dict(businessTaskId='17', revision='2', nativeFlowId=str(21001+offset),
                                    nativeInstanceId=str(21002+offset), metadataIdBase=str(22000+offset),
                                    execId=str(21003+offset), outputsDir=str(self.root / ('outputs-' + str(offset))),
                                    authorizedOutputRoot=str(self.root), mode='test'))

    def prepare_connectors(self, visual, connections):
        refs = {key:value['connectionRevision'] for key,value in connections.items()}
        return self.invoke(dict(operation='prepare', context=self.context, visual=visual, trustedConnectionRefs=refs))

    def test_original_connector_generators_preserve_database_ftp_and_kafka_metadata(self):
        if not (self.bundle/'instance/etl-postgresqlInput-2.0.0').is_dir():
            self.skipTest('Extended original connector bundle required')
        for input_plugin, output_plugin in [('postgresqlInput','fileOutput'),('dataGeneratorInput','postgresqlOutput'),('fileInput','fileOutput'),('kafkaInput','fileOutput'),('dataGeneratorInput','kafkaV2Output'),('dataGeneratorInput','ftpFileOutput')]:
            with self.subTest(input=input_plugin, output=output_plugin):
                visual, connections = connector_visual(input_plugin,output_plugin)
                prepared=self.prepare_connectors(visual,connections); self.assertTrue(prepared['ok'],prepared)
                request=self.compile_request(prepared);request['runContext']['connections']=connections
                result=self.invoke(request);self.assertTrue(result['ok'],result)
                native=json.loads(base64.b64decode(result['flowjson']['base64']))
                modules=native['streams'][0]['modules']
                self.assertEqual([m['adapter']['adapter_name'] for m in modules],[input_plugin,'fieldMapping','fileOutput' if output_plugin=='ftpFileOutput' else output_plugin])
                connected=modules[0] if input_plugin!='dataGeneratorInput' else modules[-1]
                source=connected['config']['datasource'][0]
                self.assertEqual(source['db_ip'],'127.0.0.1')
                self.assertEqual(source['db_password'],'fixture-only-not-a-secret')
                self.assertNotIn('fixture-only-not-a-secret',prepared['snapshotJson'])
                if input_plugin=='postgresqlInput':
                    self.assertEqual(source['etl_stream_field'][0]['field_expression'],'message')
                    self.assertEqual(source['etl_stream_field'][0]['src_table_id'],source['src_table'][0]['id'])
                if output_plugin!='fileOutput':
                    self.assertEqual(str(source['dst_table']['data_cleansing']),'0')
                    params={x['param_name']:x['param_value'] for x in connected['config']['adapter_param']}
                    self.assertEqual(params['etl.plugin.extend.fields'],'false')
                    if output_plugin in ['postgresqlOutput','kafkaV2Output']:
                        self.assertEqual(params['etl.plugin.partial.failed.stop.enable'],'true')



    def test_browser_connection_passwords_are_not_frozen_in_task_snapshots(self):
        visual, connections=connector_visual('fileInput','fileOutput')
        visual['nodes']['110']['ftpConfig']={'id':71,'databasePassword':'browser-must-not-store-this','password':'nor-this','databaseIp':'127.0.0.1'}
        prepared=self.prepare_connectors(visual,connections); self.assertTrue(prepared['ok'],prepared)
        self.assertNotIn('browser-must-not-store-this',prepared['snapshotJson'])
        self.assertNotIn('nor-this',prepared['snapshotJson'])
        self.assertEqual(prepared['canonicalVisual']['nodes']['110']['ftpConfig'],{'id':71,'databaseIp':'127.0.0.1'})
        self.assertEqual(json.loads(prepared['snapshotJson'])['connectionRefs'],{'71':'1'})

    def test_invalid_connector_dto_is_rejected_before_native_execution(self):
        cases=[]
        visual,connections=connector_visual('postgresqlInput','fileOutput')
        visual['nodes']['110']['streamFieldVoList'][0]['fieldExpression']='upper(message)'
        cases.append((visual,connections))
        for output_plugin in ['postgresqlOutput','ftpFileOutput','kafkaV2Output']:
            visual,connections=connector_visual('dataGeneratorInput',output_plugin)
            visual['nodes']['130']['dstTableVo']['dataCleansing']='false'
            cases.append((visual,connections))
        visual,connections=connector_visual('dataGeneratorInput','postgresqlOutput')
        visual['nodes']['130']['dstTableVo']['writeOperation']='insert'
        cases.append((visual,connections))
        visual,connections=connector_visual('dataGeneratorInput','postgresqlOutput')
        visual['nodes']['120']['transformFieldVoList'][0]['fieldDstName']='mapped_message'
        visual['nodes']['130']['dstTableFieldVoList'][0]['fieldExpression']='mapped_message'
        cases.append((visual,connections))
        for visual,connections in cases:
            prepared=self.prepare_connectors(visual,connections);self.assertTrue(prepared['ok'],prepared)
            request=self.compile_request(prepared);request['runContext']['connections']=connections
            result=self.invoke(request);self.assertFalse(result['ok']);self.assertEqual(result['code'],'UNSUPPORTED_GRAPH')

    def test_original_browser_database_node_without_adapter_data_compiles(self):
        visual,connections=connector_visual('postgresqlInput','fileOutput')
        visual['nodes']['110'].pop('adapterData')
        visual['nodes']['110']['srcTableUseVoList'][0]['incrType']=''
        visual['nodes'].pop('120')
        visual['links']=[{'source':110,'target':130,'connectStatus':0}]
        visual['nodes']['130']['dstTableFieldVoList'][0].update(fieldName='message',fieldExpression='message')
        prepared=self.prepare_connectors(visual,connections);self.assertTrue(prepared['ok'],prepared)
        request=self.compile_request(prepared);request['runContext']['connections']=connections
        result=self.invoke(request);self.assertTrue(result['ok'],result)

    def test_original_stream_save_and_synchronous_file_response_fields(self):
        visual,connections=connector_visual('fileInput','fileOutput')
        field=visual['nodes']['110']['streamFieldVoList'][0]
        field.pop('fieldExpression');field.pop('fieldOrder')
        visual['nodes']['110'].pop('srcTableUseVoList')
        visual['nodes'].pop('120');visual['links']=[{'source':110,'target':130}]
        out=visual['nodes']['130'];out['dstTableFieldVoList'][0].update(fieldName='message',fieldExpression='message')
        for name in ['_RESULT_CODE_','_RESULT_MSG_','_RESULT_DATA_']:
            out['dstTableFieldVoList'].append({'fieldName':name,'fieldType':'string','fieldSize':'','fieldPrecision':'','fieldKeyFlag':'','columnRemarks':'1','isNew':True})
        prepared=self.prepare_connectors(visual,connections);self.assertTrue(prepared['ok'],prepared)
        request=self.compile_request(prepared);request['runContext']['connections']=connections
        result=self.invoke(request);self.assertTrue(result['ok'],result)
        native=json.loads(base64.b64decode(result['flowjson']['base64']));modules=native['streams'][0]['modules']
        fields=modules[0]['config']['datasource'][0]['etl_stream_field']
        self.assertEqual(fields[0]['field_expression'],'message')
        self.assertEqual(fields[0]['field_name'],'message')
        self.assertNotIn('field_order',fields[0])
        output=modules[1]['config']['datasource'][0]['dst_table_field']
        self.assertEqual([f['field_name'] for f in output],['message','_RESULT_CODE_','_RESULT_MSG_','_RESULT_DATA_'])
        self.assertEqual([f['field_order'] for f in output],[0,1,2,3])
        for field in out['dstTableFieldVoList'][1:]:field['passThrough']=1
        prepared=self.prepare_connectors(visual,connections);request=self.compile_request(prepared);request['runContext']['connections']=connections
        ignored=self.invoke(request);self.assertTrue(ignored['ok'],ignored)
        ignored_native=json.loads(base64.b64decode(ignored['flowjson']['base64']))
        self.assertEqual([f['field_name'] for f in ignored_native['streams'][0]['modules'][1]['config']['datasource'][0]['dst_table_field']],['message'])
        runtime_output=list(ignored['metadataSnapshot']['runtimeVisual']['nodes'].values())[-1]
        self.assertEqual(len(runtime_output['dstTableFieldVoList']),4)
        self.assertEqual([f['passThrough'] for f in runtime_output['dstTableFieldVoList'][1:]],[1,1,1])
        out['dstTableFieldVoList'][-1]['fieldName']='_RESULT_UNSUPPORTED_'
        prepared=self.prepare_connectors(visual,connections);request=self.compile_request(prepared);request['runContext']['connections']=connections
        self.assertFalse(self.invoke(request)['ok'])

    def test_direct_original_output_keeps_inherited_expression_metadata(self):
        visual,connections=connector_visual('dataGeneratorInput','postgresqlOutput')
        visual['nodes']['130']['dstTableFieldVoList'][0].update(fieldExpression='browser original postgres')
        prepared=self.prepare_connectors(visual,connections);request=self.compile_request(prepared);request['runContext']['connections']=connections
        result=self.invoke(request);self.assertTrue(result['ok'],result)
        native=json.loads(base64.b64decode(result['flowjson']['base64']))
        field=native['streams'][0]['modules'][-1]['config']['datasource'][0]['dst_table_field'][0]
        self.assertEqual(field['field_name'],'message');self.assertEqual(field['field_expression'],'browser original postgres')
        visual['nodes']['130']['dstTableFieldVoList'][0]['fieldSrcType']=1
        prepared=self.prepare_connectors(visual,connections);request=self.compile_request(prepared);request['runContext']['connections']=connections
        self.assertFalse(self.invoke(request)['ok'])

    def test_original_ftp_output_has_no_target_table_or_visible_local_cache_field(self):
        visual,connections=connector_visual('dataGeneratorInput','ftpFileOutput')
        visual['nodes']['130'].pop('dstTableVo')
        visual['nodes']['130']['adapterData']['localDir']=''
        prepared=self.prepare_connectors(visual,connections);request=self.compile_request(prepared);request['runContext']['connections']=connections
        result=self.invoke(request);self.assertTrue(result['ok'],result)
        native=json.loads(base64.b64decode(result['flowjson']['base64']))
        output=native['streams'][0]['modules'][-1]['config']
        self.assertEqual(output['datasource'][0]['dst_table'],{'stream_id':int(request['runContext']['metadataIdBase'])})
        self.assertEqual(output['datasource'][0]['id'],72)
        params={p['param_name']:p['param_value'] for p in output['adapter_param']}
        self.assertEqual(params['etl.file.localDir'],request['runContext']['outputsDir'])
        self.assertEqual(params['etl.file.serverDir'],'/outbox')

    def test_native_connector_switch_values_follow_the_original_catalog(self):
        visual,connections=connector_visual('kafkaInput','fileOutput')
        visual['nodes']['110']['adapterParamVoList'].append({'paramName':'etl.kafka.enable.auto.commit','paramValue':False})
        prepared=self.prepare_connectors(visual,connections);request=self.compile_request(prepared);request['runContext']['connections']=connections
        result=self.invoke(request);self.assertTrue(result['ok'],result)
        native=json.loads(base64.b64decode(result['flowjson']['base64']))
        params={p['param_name']:p['param_value'] for p in native['streams'][0]['modules'][0]['config']['adapter_param']}
        self.assertEqual(params['etl.kafka.enable.auto.commit'],'false')
        visual['nodes']['110']['adapterParamVoList'][-1]['paramValue']='not-a-boolean'
        prepared=self.prepare_connectors(visual,connections);request=self.compile_request(prepared);request['runContext']['connections']=connections
        self.assertFalse(self.invoke(request)['ok'])

    def test_primary_keys_are_retained_for_database_and_queue_and_nonprimary_file_targets(self):
        for output in ['postgresqlOutput','kafkaV2Output','fileOutput']:
            visual,connections=connector_visual('dataGeneratorInput',output)
            visual['nodes']['110']['streamFieldVoList'][0]['fieldKeyFlag']=1
            if output!='fileOutput':visual['nodes']['130']['dstTableFieldVoList'][0]['fieldKeyFlag']=1
            prepared=self.prepare_connectors(visual,connections);request=self.compile_request(prepared);request['runContext']['connections']=connections
            result=self.invoke(request);self.assertTrue(result['ok'],result)

    def test_kafka_uses_original_json_and_string_wire_values(self):
        for format_name, accepted in [('json',True),('string',True),('text',False),('avro',False)]:
            visual,connections=connector_visual('dataGeneratorInput','kafkaV2Output')
            visual['nodes']['130']['dstTableVo'].update(messageFormat=format_name,messageField='mapped_message')
            prepared=self.prepare_connectors(visual,connections);request=self.compile_request(prepared);request['runContext']['connections']=connections
            self.assertEqual(self.invoke(request)['ok'],accepted,format_name)

    def test_frozen_connector_revision_and_identity_cannot_be_replaced(self):
        visual, connections=connector_visual('postgresqlInput','fileOutput')
        prepared=self.prepare_connectors(visual,connections);self.assertTrue(prepared['ok'],prepared)
        for change in ['revision','id','type']:
            request=self.compile_request(prepared); request['runContext']['connections']=copy.deepcopy(connections)
            if change=='revision':request['runContext']['connections']['71']['connectionRevision']='2'
            elif change=='id':request['runContext']['connections']['71']['datasource']['id']=72
            else:request['runContext']['connections']['71']['datasource']['dbType']=18
            self.assertFalse(self.invoke(request)['ok'])
        missing=self.invoke(dict(operation='prepare',context=self.context,visual=visual))
        self.assertFalse(missing['ok'])

    def test_prepare_canonical_snapshot_preserves_original_dto(self):
        result = self.prepare()
        self.assertTrue(result['ok'])
        self.assertNotIn('panelUIInfo', result['canonicalVisual'])
        self.assertEqual({'scale': 1}, result['canonicalVisual']['panelUiInfo'])
        self.assertEqual(17, result['canonicalVisual']['flowVo']['id'])
        self.assertEqual(hashlib.sha256(result['snapshotJson'].encode()).hexdigest(), result['snapshotSha256'])

    def test_empty_draft_saves_but_cannot_execute(self):
        prepared = self.prepare({'flowVo': {}, 'nodes': {}, 'links': []})
        self.assertTrue(prepared['ok'])
        self.assertFalse(self.invoke(self.compile_request(prepared))['ok'])

    def test_original_generators_compile_actual_three_node_revision(self):
        result = self.invoke(self.compile_request())
        self.assertTrue(result['ok'], result)
        payload = base64.b64decode(result['flowjson']['base64'])
        self.assertEqual(hashlib.sha256(payload).hexdigest(), result['flowjson']['sha256'])
        self.assertIn(b'platform revision one', payload)
        self.assertIn(b'mapped_message', payload)
        self.assertNotIn(b'/browser/untrusted/path', payload)
        schedule = json.loads(base64.b64decode(result['schedule']['base64']))
        self.assertEqual('21003', schedule['exec_id'])
        self.assertEqual(21001, schedule['id'])
        self.assertEqual(21001, schedule['strategy_flow']['id'])
        stop = json.loads(base64.b64decode(result['stop']['base64']))
        self.assertEqual(21001, stop['id'])
        self.assertEqual(0, stop['oper_value'])
        self.assertEqual(['110', '120', '130'], [row['visualNodeId'] for row in result['nodeMappings']])
        self.assertEqual(3, len({row['moduleId'] for row in result['nodeMappings']}))

    def test_changed_config_and_per_run_ids_change_original_payload(self):
        first = self.invoke(self.compile_request())
        second = self.invoke(self.compile_request(self.prepare(fixture_visual('second saved value')), 10000))
        self.assertTrue(first['ok'] and second['ok'], [first, second])
        self.assertNotEqual(first['flowjson']['sha256'], second['flowjson']['sha256'])
        self.assertIn(b'second saved value', base64.b64decode(second['flowjson']['base64']))
        self.assertTrue({row['moduleId'] for row in first['nodeMappings']}.isdisjoint({row['moduleId'] for row in second['nodeMappings']}))

    def test_original_browser_default_mapping_and_output_dto_compile_without_invented_fields(self):
        prepared = self.prepare(original_ui_visual())
        result = self.invoke(self.compile_request(prepared))
        self.assertTrue(result['ok'], result)
        native = json.loads(base64.b64decode(result['flowjson']['base64']))
        modules = {m['adapter']['adapter_name']: m['config'] for m in native['streams'][0]['modules']}
        self.assertEqual([], modules['fieldMapping']['fields'])  # Original service emits no work for passthrough.
        field = modules['fileOutput']['datasource'][0]['dst_table_field'][0]
        self.assertEqual('message', field['field_name'])
        self.assertNotIn('field_expression', field)
        self.assertEqual(1, field['enable_flag'])
        self.assertEqual(0, field['field_order'])  # Original DstTableFieldService.batchInsert ordering.
        params = {p['param_name']: p['param_value'] for p in modules['fileOutput']['adapter_param']}
        self.assertEqual('csv', params['etl.file.fileType'])
        self.assertEqual('false', params['etl.file.isUpload'])
        self.assertEqual('local', params['etl.file.serverType'])
        original = prepared['canonicalVisual']['nodes']
        self.assertNotIn('processType', original['120']['transformFieldVoList'][0])
        self.assertNotIn('enableFlag', original['130']['dstTableFieldVoList'][0])

    def test_original_mapping_rename_uses_native_null_process_type_default(self):
        visual = original_ui_visual()
        # Exact edited field shape observed after the original browser saved a rename.
        # Neither the original form nor TransformFieldService adds processType="move".
        visual['nodes']['120']['transformFieldVoList'][0].update(
            fieldDstName='mapped_message', fieldDstDisplayName='mapped_message',
            duplicatedDstName=False, fieldSyncStatus='sync', columnType='varchar', condExpression='')
        visual['nodes']['130']['dstTableFieldVoList'][0].update(
            fieldName='mapped_message', fieldDisplayName='mapped_message',
            fieldSyncStatus='sync', columnType='varchar', condExpression='')
        prepared = self.prepare(visual)
        self.assertNotIn('processType', prepared['canonicalVisual']['nodes']['120']['transformFieldVoList'][0])
        result = self.invoke(self.compile_request(prepared))
        self.assertTrue(result['ok'], result)
        native = json.loads(base64.b64decode(result['flowjson']['base64']))
        fields = native['streams'][0]['modules'][1]['config']['fields']
        self.assertEqual(1, len(fields))
        mapping = fields[0]
        self.assertEqual('message', mapping['field_src_name'])
        self.assertEqual('mapped_message', mapping['field_target_name'])
        self.assertNotIn('field_src_reserve', mapping)
        self.assertNotIn('cond_expression', mapping)
        output = native['streams'][0]['modules'][2]['config']['datasource'][0]['dst_table_field'][0]
        self.assertEqual('mapped_message', output['field_name'])
        self.assertNotIn('field_expression', output)

    def test_original_form_cannot_silently_lose_selected_format_to_legacy_parameter(self):
        visual = original_ui_visual()
        visual['nodes']['130']['adapterParamVoList'].append(dict(paramName='etl.file.fileType', paramValue='txt', valueType='el-select'))
        self.assertTrue(self.prepare(visual)['ok'])  # The saved draft is preserved for correction.
        rejected = self.invoke(self.compile_request(self.prepare(visual)))
        self.assertFalse(rejected['ok'])
        self.assertEqual('UNSUPPORTED_GRAPH', rejected['code'])
        self.assertEqual('FILE_FORMAT_CONFLICT', rejected['reason'])
        visual['nodes']['130']['adapterParamVoList'][-1]['paramValue'] = 'csv'
        result = self.invoke(self.compile_request(self.prepare(visual)))
        self.assertTrue(result['ok'], result)

    def test_missing_output_expression_does_not_allow_unknown_fields_or_fel(self):
        visual = original_ui_visual()
        visual['nodes']['130']['dstTableFieldVoList'][0]['fieldName'] = 'missing'
        self.assertFalse(self.invoke(self.compile_request(self.prepare(visual)))['ok'])
        visual = original_ui_visual()
        visual['nodes']['130']['dstTableFieldVoList'][0].update(fieldExpression='runtime.exec()',fieldSrcType=1)
        self.assertFalse(self.invoke(self.compile_request(self.prepare(visual)))['ok'])

    def test_original_merged_output_rejects_legacy_ftp_before_worker_submission(self):
        visual = original_ui_visual()
        visual['nodes']['130']['adapterParamVoList'].append(dict(paramName='etl.file.serverType', paramValue='Ftp', valueType='el-select'))
        prepared = self.prepare(visual)
        self.assertTrue(prepared['ok'])  # Valid editable draft, never rewritten to local.
        self.assertEqual('Ftp', prepared['canonicalVisual']['nodes']['130']['adapterParamVoList'][-1]['paramValue'])
        result = self.invoke(self.compile_request(prepared))
        self.assertFalse(result['ok'])
        self.assertEqual('UNSUPPORTED_GRAPH', result['code'])
        self.assertEqual('LOCAL_OUTPUT_REQUIRED', result['reason'])
        self.assertNotIn('flowjson', result)
        visual['nodes']['130']['adapterParamVoList'][-1]['paramValue'] = 'local'
        compiled = self.invoke(self.compile_request(self.prepare(visual)))
        self.assertTrue(compiled['ok'], compiled)
        native = json.loads(base64.b64decode(compiled['flowjson']['base64']))
        params = {p['param_name']: p['param_value'] for p in native['streams'][0]['modules'][2]['config']['adapter_param']}
        self.assertEqual('local', params['etl.file.serverType'])
        self.assertEqual('false', params['etl.file.isUpload'])

    def test_snapshot_tampering_and_identity_mismatch_rejected(self):
        request = self.compile_request()
        request['snapshotJson'] += ' '
        self.assertFalse(self.invoke(request)['ok'])
        request = self.compile_request(); request['runContext']['businessTaskId'] = '18'
        self.assertFalse(self.invoke(request)['ok'])

    def test_unsupported_topology_or_plugin_is_explicit(self):
        for mutation in ('branch', 'cycle', 'plugin'):
            visual = fixture_visual()
            if mutation == 'branch': visual['links'].append(dict(source=110, target=130))
            elif mutation == 'cycle': visual['links'].append(dict(source=130, target=110))
            else: visual['nodes']['120']['adapterName'] = 'javascript'
            result = self.prepare(visual)
            self.assertFalse(result['ok'])
            self.assertEqual('UNSUPPORTED_GRAPH', result['code'])

    def test_output_escape_and_external_source_rejected(self):
        request = self.compile_request(); request['runContext']['outputsDir'] = '/outside-root'
        self.assertFalse(self.invoke(request)['ok'])
        visual = fixture_visual(); visual['nodes']['130']['adapterData']['sourceType'] = 'ftp'
        prepared = self.prepare(visual); self.assertTrue(prepared['ok'])
        self.assertEqual('INVALID_INPUT', self.invoke(self.compile_request(prepared))['code'])

    def test_unbounded_rows_save_but_do_not_compile(self):
        visual = fixture_visual(); visual['nodes']['110']['adapterParamVoList'][0]['paramValue'] = '-1'
        prepared = self.prepare(visual); self.assertTrue(prepared['ok'])
        self.assertFalse(self.invoke(self.compile_request(prepared))['ok'])

    def test_duplicate_and_fastjson_type_directives_rejected(self):
        self.assertFalse(self.invoke('{"operation":"prepare","operation":"compile"}')['ok'])
        self.assertFalse(self.invoke('{"operation":"prepare"} {}')['ok'])
        visual = fixture_visual(); visual['nodes']['110']['@type'] = 'java.lang.Runtime'
        self.assertFalse(self.prepare(visual)['ok'])

    def test_parameter_path_is_rebound_and_enabled_network_rejected(self):
        visual = fixture_visual()
        visual['nodes']['130']['adapterParamVoList'].append({'paramName': 'etl.file.localDir', 'paramValue': '/平台输出'})
        compiled = self.invoke(self.compile_request(self.prepare(visual)))
        self.assertTrue(compiled['ok'], compiled)
        self.assertNotIn('/平台输出'.encode(), base64.b64decode(compiled['flowjson']['base64']))
        visual['nodes']['130']['adapterParamVoList'][-1]['paramValue'] = ''
        self.assertTrue(self.invoke(self.compile_request(self.prepare(visual)))['ok'])
        visual['nodes']['130']['adapterParamVoList'].append({'paramName': 'etl.file.isUpload', 'paramValue': 'true'})
        self.assertEqual('UNSUPPORTED_GRAPH', self.invoke(self.compile_request(self.prepare(visual)))['code'])

    def test_run_and_debug_require_explicit_exact_managed_directory(self):
        for path in ['', '/tmp/unapproved', '/平台输出xxx', '/平台输出/', 'platform://managed-output']:
            visual = fixture_visual(); visual['nodes']['130']['adapterData']['localDir'] = path
            prepared = self.prepare(visual); self.assertTrue(prepared['ok'])
            for mode in ['test', 'run']:
                request = self.compile_request(prepared); request['runContext']['mode'] = mode
                self.assertEqual('UNSUPPORTED_GRAPH', self.invoke(request)['code'])
        visual = fixture_visual()
        visual['nodes']['130']['adapterData']['absoluteDir'] = '/some/host/path'
        self.assertEqual('UNSUPPORTED_GRAPH', self.invoke(self.compile_request(self.prepare(visual)))['code'])

    def test_original_month_week_day_schedule_compiler(self):
        for unit, interval, cron in [(2, '15', '6 5 4 15 * ?'), (3, '2,4', '6 5 4 ? * 2,4'), (4, '1', '6 5 4 * * ?')]:
            config = dict(scheduleType=2, unit=unit, timeInterval=interval, executeTime='04:05:06', executeTimeExactDate='12:00:00', flowIncrStatus=1, startFullImmediately=0)
            response = self.invoke(dict(operation='schedule', scheduleConfig=config, zoneId='Asia/Shanghai'))
            self.assertTrue(response['ok'], response)
            self.assertEqual(config, response['scheduleConfig'])
            self.assertEqual([cron], response['cronExpressions'])

    def test_original_manual_schedule_has_no_automatic_trigger(self):
        config = dict(scheduleType=1, flowIncrStatus=1, start=0, startFullImmediately=0)
        response = self.invoke(dict(operation='schedule', scheduleConfig=config, zoneId='Asia/Shanghai'))
        self.assertTrue(response['ok'], response)
        self.assertEqual('MANUAL', response['mode']); self.assertEqual([], response['cronExpressions'])
        self.assertEqual(1, response['nativeSchedule']['execType']); self.assertEqual(1, response['nativeSchedule']['flowIncrStatus'])

    def test_original_calendar_builder_preserves_non_cron_hour_and_minute_intervals(self):
        for unit, interval, name in [(5, '36', 'HOUR'), (6, '90', 'MINUTE')]:
            config = dict(scheduleType=2, unit=unit, timeInterval=interval, executeTime='12:00:00', flowIncrStatus=1, startFullImmediately=0)
            response = self.invoke(dict(operation='schedule', scheduleConfig=config, zoneId='Asia/Shanghai'))
            self.assertTrue(response['ok'], response)
            self.assertEqual('CALENDAR', response['mode']); self.assertEqual([], response['cronExpressions'])
            self.assertEqual(dict(interval=int(interval), unit=name), response['calendar'])
            self.assertEqual(interval + '#' + str(unit), response['nativeExpression'])

    def test_unimplemented_schedule_semantics_are_rejected(self):
        base = dict(scheduleType=2, unit=4, timeInterval='1', executeTime='04:05:06', flowIncrStatus=1, startFullImmediately=0)
        for changes in [dict(unit=7), dict(scheduleType=3, intervalTime=60), dict(scheduleType=4, dependencyFlowId=17),
                        dict(excludeStartTime='02:00:00'), dict(startFullImmediately=1), dict(flowIncrStatus=2), dict(executeTime='25:00:00')]:
            result = self.invoke(dict(operation='schedule', scheduleConfig={**base, **changes}, zoneId='Asia/Shanghai'))
            self.assertFalse(result['ok'])

    def test_run_block_overlap_and_symlink_output_rejected(self):
        request = self.compile_request(); request['runContext']['nativeFlowId'] = '22002'
        self.assertFalse(self.invoke(request)['ok'])
        target = self.root / 'actual-output'; target.mkdir(exist_ok=True)
        alias = self.root / 'output-alias'; alias.symlink_to(target)
        request = self.compile_request(); request['runContext']['outputsDir'] = str(alias / 'child')
        self.assertFalse(self.invoke(request)['ok'])

    def test_trusted_foreign_worker_path_uses_lexical_checks_not_compiler_host_filesystem(self):
        target = self.root / 'local-target'; target.mkdir(exist_ok=True)
        alias = self.root / 'foreign-path-name'; alias.symlink_to(target)
        request = self.compile_request(); request['runContext'].update(
            outputsDir=str(alias / 'outputs'), authorizedOutputRoot=str(alias), outputRootSource='worker-reserve')
        self.assertTrue(self.invoke(request)['ok'])
        request['runContext']['outputsDir'] = str(alias / '..' / 'escape')
        self.assertFalse(self.invoke(request)['ok'])

    def test_launcher_protocol_with_verified_bundle(self):
        payload = json.dumps(dict(operation='prepare', context=self.context, visual=fixture_visual())).encode()
        code, raw = launcher.execute(self.bundle, self.java, payload, self.root)
        self.assertEqual(0, code)
        self.assertTrue(json.loads(raw)['ok'])

    def test_original_filename_sources_cannot_escape_private_output(self):
        for field_list, node in [('streamFieldVoList', '110'), ('dstTableFieldVoList', '130')]:
            visual = fixture_visual('../../outside')
            visual['nodes'][node][field_list][0]['fieldKeyFlag'] = 1
            prepared = self.prepare(visual)
            self.assertTrue(prepared['ok'])
            self.assertEqual('UNSUPPORTED_GRAPH', self.invoke(self.compile_request(prepared))['code'])
        for name, value in [('etl.file.fileNameFromFlow', 'mapped_message'),
                            ('etl.fileOutput.subPathInFtpOrSftp', '../../escape'),
                            ('etl.plugin.fileOutput.generateFileByName', 'true'),
                            ('etl.fileOutput.temporary.extension', '../../escape')]:
            visual = fixture_visual()
            visual['nodes']['130']['adapterParamVoList'].append({'paramName': name, 'paramValue': value})
            self.assertEqual('UNSUPPORTED_GRAPH', self.invoke(self.compile_request(self.prepare(visual)))['code'])
        visual = fixture_visual(); visual['nodes']['130']['adapterData']['customExtension'] = '../../escape'
        self.assertEqual('UNSUPPORTED_GRAPH', self.invoke(self.compile_request(self.prepare(visual)))['code'])
        request = self.compile_request(); request['runContext']['outputsDir'] = str(self.root / '${mapped_message}')
        self.assertFalse(self.invoke(request)['ok'])

    def test_original_inactive_fieldmapping_defaults_are_not_external_configuration(self):
        visual = fixture_visual()
        visual['nodes']['120']['adapterParamVoList'] = [
            {'paramName': 'etl.plugin.fel.redis.connect', 'paramValue': 'redis://ip:port/db'},
            {'paramName': 'etl.plugin.fel.redis.password', 'paramValue': ''}]
        self.assertTrue(self.invoke(self.compile_request(self.prepare(visual)))['ok'])
        visual['nodes']['120']['adapterParamVoList'][0]['paramValue'] = 'redis://external:6379/0'
        self.assertEqual('UNSUPPORTED_GRAPH', self.invoke(self.compile_request(self.prepare(visual)))['code'])

    def test_original_ui_task_defaults_and_explicit_changes_reach_flow_parameters(self):
        catalog = json.loads((self.bundle / 'instance/conf/task_config.json').read_text())['configParam']
        visual = fixture_visual()
        visual['paramVoList'] = [{'paramName': row['name'], 'paramValue': str(row['defaultValue']), 'valueType': row['type']}
                               for row in catalog]
        prepared = self.prepare(visual); compiled = self.invoke(self.compile_request(prepared))
        self.assertTrue(compiled['ok'], compiled)
        graph = json.loads(base64.b64decode(compiled['flowjson']['base64']))
        parameters = {row['param_name']: row['param_value'] for row in graph['flow']['flow_param']}
        self.assertEqual({row['name']: str(row['defaultValue']) for row in catalog}, parameters)
        for row in visual['paramVoList']:
            if row['paramName'] == 'etl.task.log.level': row['paramValue'] = 'DEBUG'
        changed = self.invoke(self.compile_request(self.prepare(visual)))
        self.assertTrue(changed['ok'], changed)
        changed_graph = json.loads(base64.b64decode(changed['flowjson']['base64']))
        self.assertEqual('DEBUG', {row['param_name']: row['param_value'] for row in changed_graph['flow']['flow_param']}['etl.task.log.level'])

    def test_task_parameter_external_expression_and_unimplemented_trigger_rejected(self):
        for parameter in [dict(paramName='etl.task.log.level', paramValue='INFO', globalParamKey='remote-value'),
                          dict(paramName='etl.task.retry.interval', paramValue='10', valueType='el-input-fel'),
                          dict(paramName='etl.task.failed.data.retry.cron', paramValue='0 * * * * ?'),
                          dict(paramName='unknown-task-parameter', paramValue='1')]:
            visual = fixture_visual(); visual['paramVoList'] = [parameter]
            self.assertEqual('UNSUPPORTED_GRAPH', self.invoke(self.compile_request(self.prepare(visual)))['code'])


if __name__ == '__main__':
    unittest.main()
