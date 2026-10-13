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
                        'adapterData': {'sourceType': 'local', 'localDir': '/browser/untrusted/path',
                                        'sourcePathType': 1, 'fileType': 'csv', 'colSplitter': ',',
                                        'encoding': 'UTF-8', 'addHeaderLine': 'true', 'isAsyncGenerateText': 'false'},
                        'dstTableFieldVoList': [output],
                        'adapterParamVoList': [{'paramName': 'etl.file.maxRow', 'paramValue': '10'},
                                               {'paramName': 'etl.file.flushTime', 'paramValue': '1000'}]}},
            'links': [{'source': 110, 'target': 120, 'connectStatus': 0}, {'source': 120, 'target': 130, 'connectStatus': 0}]}


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
        self.assertEqual('UNSUPPORTED_GRAPH', self.invoke(self.compile_request(prepared))['code'])

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
        visual['nodes']['130']['adapterParamVoList'].append({'paramName': 'etl.file.localDir', 'paramValue': '/another/untrusted/path'})
        compiled = self.invoke(self.compile_request(self.prepare(visual)))
        self.assertTrue(compiled['ok'], compiled)
        self.assertNotIn(b'/another/untrusted/path', base64.b64decode(compiled['flowjson']['base64']))
        visual['nodes']['130']['adapterParamVoList'].append({'paramName': 'etl.file.isUpload', 'paramValue': 'true'})
        self.assertEqual('UNSUPPORTED_GRAPH', self.invoke(self.compile_request(self.prepare(visual)))['code'])

    def test_run_block_overlap_and_symlink_output_rejected(self):
        request = self.compile_request(); request['runContext']['nativeFlowId'] = '22002'
        self.assertFalse(self.invoke(request)['ok'])
        target = self.root / 'actual-output'; target.mkdir(exist_ok=True)
        alias = self.root / 'output-alias'; alias.symlink_to(target)
        request = self.compile_request(); request['runContext']['outputsDir'] = str(alias / 'child')
        self.assertFalse(self.invoke(request)['ok'])

    def test_launcher_protocol_with_verified_bundle(self):
        payload = json.dumps(dict(operation='prepare', context=self.context, visual=fixture_visual())).encode()
        code, raw = launcher.execute(self.bundle, self.java, payload, self.root)
        self.assertEqual(0, code)
        self.assertTrue(json.loads(raw)['ok'])

    def test_original_inactive_fieldmapping_defaults_are_not_external_configuration(self):
        visual = fixture_visual()
        visual['nodes']['120']['adapterParamVoList'] = [
            {'paramName': 'etl.plugin.fel.redis.connect', 'paramValue': 'redis://ip:port/db'},
            {'paramName': 'etl.plugin.fel.redis.password', 'paramValue': ''}]
        self.assertTrue(self.invoke(self.compile_request(self.prepare(visual)))['ok'])
        visual['nodes']['120']['adapterParamVoList'][0]['paramValue'] = 'redis://external:6379/0'
        self.assertEqual('UNSUPPORTED_GRAPH', self.invoke(self.compile_request(self.prepare(visual)))['code'])


if __name__ == '__main__':
    unittest.main()
