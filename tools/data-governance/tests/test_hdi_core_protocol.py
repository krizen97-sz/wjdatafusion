import base64
import copy
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import tempfile
import unittest

spec = importlib.util.spec_from_file_location('protocol', Path(__file__).resolve().parents[1] / 'hdi_core_protocol.py')
p = importlib.util.module_from_spec(spec)
spec.loader.exec_module(p)


class ProtocolTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name).resolve()

    def tearDown(self):
        self.temp.cleanup()

    def request(self):
        return {'runId': '11111111-1111-4111-8111-111111111111', 'ownerId': '1',
                'businessTaskId': 'task-abc', 'revision': '1', 'nativeFlowId': '21001',
                'nativeInstanceId': '21002', 'execId': '21003', 'workspaceId': '100',
                'metadataIdBase': '21101', 'mode': 'test'}

    def test_management_uuid_is_not_native_exec_id(self):
        reserved = p.reservation(self.request())
        self.assertNotEqual(reserved['runId'], reserved['execId'])
        self.assertEqual(reserved['execId'], '21003')

    def test_rejects_original_group_int_overflow(self):
        value = self.request()
        value['workspaceId'] = '2147483648'
        with self.assertRaises(p.ProtocolError):
            p.reservation(value)

    def test_rejects_native_long_overflow_and_float(self):
        for value in ('9223372036854775808', 9007199254740992.0, True):
            with self.assertRaises(p.ProtocolError):
                p.native_id(value)

    def test_checks_exact_native_bytes_hash_without_reserializing(self):
        raw = b'{ "id" : 21001, "oper_type": 1, "oper_value": 0 }\n'
        data = {'base64': base64.b64encode(raw).decode(), 'sha256': hashlib.sha256(raw).hexdigest()}
        self.assertEqual(p.native_blob(data)[0], raw)
        data['sha256'] = '0' * 64
        with self.assertRaisesRegex(p.ProtocolError, 'NATIVE_HASH_MISMATCH'):
            p.native_blob(data)

    def test_duplicate_native_keys_are_rejected(self):
        with self.assertRaisesRegex(p.ProtocolError, 'DUPLICATE_JSON_KEY'):
            p.strict_json(b'{"id": 1, "id": 2}')

    def test_output_cannot_escape_or_follow_symlinks(self):
        output = self.root / 'outputs'
        output.mkdir()
        (output / 'linked').symlink_to(self.root, target_is_directory=True)
        for value in (str(output / '../outside.csv'), str(output / 'linked/file.csv'), '/tmp/foreign.csv'):
            with self.assertRaises(p.ProtocolError):
                p.below_output(value, output)
        self.assertEqual(p.below_output(str(output / 'nested/file.csv'), output), output / 'nested/file.csv')

    def test_manifest_rejects_symlink_root_and_hardlink(self):
        output = self.root / 'outputs'
        output.mkdir()
        secret = self.root / 'private'
        secret.write_text('private')
        link = self.root / 'linked'
        link.symlink_to(output, target_is_directory=True)
        with self.assertRaises(p.ProtocolError):
            p.results_manifest(link)
        os.link(secret, output / 'not-an-output')
        with self.assertRaises(p.ProtocolError):
            p.results_manifest(output)

    def test_manifest_contains_only_relative_name_and_verified_bytes(self):
        file = self.root / 'result.csv'
        file.write_bytes(b'value\n3\n')
        result = p.results_manifest(self.root)
        self.assertEqual(result[0]['name'], 'result.csv')
        self.assertEqual(result[0]['sha256'], hashlib.sha256(file.read_bytes()).hexdigest())
        self.assertEqual(result[0]['bytes'], 8)

    def test_atomic_state_stays_private_and_preserves_prior_on_serialization_error(self):
        path = self.root / 'state.json'
        p.atomic_json(path, {'state': 'SUBMITTING'})
        self.assertEqual(path.stat().st_mode & 0o777, 0o600)
        with self.assertRaises(ValueError):
            p.atomic_json(path, {'invalid': float('nan')})
        self.assertEqual(json.loads(p.private_file(path)), {'state': 'SUBMITTING'})

    def connection(self, kind='postgresql'):
        value = {'connectionId': '41', 'connectionRevision': '3', 'ownerId': '1', 'kind': kind,
                 'endpoints': [{'host': '127.0.0.1', 'address': '127.0.0.1', 'port': 15432}]}
        if kind == 'ftp':
            value['endpoints'][0]['port'] = 2121
            value['passivePorts'] = {'start': 22100, 'end': 22109}
        return value

    def test_frozen_connections_have_exact_owner_and_no_dns_or_wildcard_resolution(self):
        self.assertEqual(p.connection_bindings([self.connection()], '1'), [self.connection()])
        for address in ('localhost', '127.000.0.1', '0.0.0.0', '::1', '224.0.0.1', '169.254.169.254'):
            value = self.connection()
            value['endpoints'][0].update(host=address, address=address)
            with self.assertRaises(p.ProtocolError):
                p.connection_bindings([value], '1')
        with self.assertRaisesRegex(p.ProtocolError, 'CONNECTOR_OWNER_MISMATCH'):
            p.connection_bindings([self.connection()], '2')
        with self.assertRaises(p.ProtocolError):
            p.connection_bindings([{**self.connection(), 'password': 'not-a-public-field'}], '1')

    def test_ftp_passive_network_is_explicit_bounded_and_cannot_capture_core_ports(self):
        binding = self.connection('ftp')
        targets = p.connector_targets(p.connection_bindings([binding], '1'))
        self.assertEqual({item['port'] for item in targets}, {2121, *range(22100, 22110)})
        for passive in (None, {'start': 1, 'end': 65535}, {'start': 2179, 'end': 2184},
                        {'start': 22110, 'end': 22100}, {'start': True, 'end': 5}):
            value = {**binding, 'passivePorts': passive}
            with self.assertRaises(p.ProtocolError):
                p.connection_bindings([value], '1')
        for port in (2180, 2181, 2182, 2183):
            value = copy.deepcopy(binding)
            value['endpoints'][0]['port'] = port
            with self.assertRaises(p.ProtocolError):
                p.connection_bindings([value], '1')

    def test_kafka_brokers_are_all_pinned_and_duplicate_grants_are_rejected(self):
        value = self.connection('kafka')
        value['endpoints'] = [{'host': '192.0.2.10', 'address': '192.0.2.10', 'port': 9092},
                              {'host': '192.0.2.11', 'address': '192.0.2.11', 'port': 9092}]
        self.assertEqual(len(p.connector_targets(p.connection_bindings([value], '1'))), 2)
        with self.assertRaises(p.ProtocolError):
            p.connection_bindings([value, value], '1')
        value['endpoints'].append(value['endpoints'][0])
        with self.assertRaisesRegex(p.ProtocolError, 'DUPLICATE_CONNECTOR_ENDPOINT'):
            p.connection_bindings([value], '1')

    def test_reservation_binds_connections_but_never_exposes_secret_values(self):
        request = {**self.request(), 'connectionBindings': [self.connection()], 'redactionValues': ['secret']}
        self.assertEqual(p.reservation(request)['connectionBindings'], [self.connection()])
        self.assertNotIn('redactionValues', p.reservation(request))
        self.assertEqual(p.redaction_values(['short', 'longer-secret', 'short']), ['longer-secret', 'short'])
        for values in ([''], ['x' * 4097], [None], 'not-list'):
            with self.assertRaises(p.ProtocolError):
                p.redaction_values(values)

    def test_native_datasource_must_match_its_owned_connection_and_original_url(self):
        binding = self.connection()
        source = {'id': 41, 'db_type': 16, 'db_ip': '127.0.0.1', 'db_port': '15432',
                  'db_sid': 'postgres', 'db_url': 'jdbc:postgresql://127.0.0.1:15432/postgres'}
        module = {'config': {'datasource': [source], 'datasource_param': []}}
        self.assertEqual(p.validate_datasource_grant(module, 'postgresql', [binding]), binding)
        for patch in ({'id': 42}, {'db_ip': '192.0.2.10'}, {'db_port': '5432'},
                      {'db_url': 'jdbc:postgresql://127.0.0.1:15432/postgres?socketFactory=Other'}):
            changed = {'config': {'datasource': [{**source, **patch}]}}
            with self.assertRaises(p.ProtocolError):
                p.validate_datasource_grant(changed, 'postgresql', [binding])
        module['config']['datasource_param'] = [{'param_name': 'password', 'param_value': 'override'}]
        with self.assertRaisesRegex(p.ProtocolError, 'CONNECTOR_PARAMETERS_REJECTED'):
            p.validate_datasource_grant(module, 'postgresql', [binding])

    def test_original_ftp_joint_url_keeps_its_directory_slashes(self):
        binding = self.connection('ftp')
        for directory in ('', '/', '/incoming'):
            source = {'id': 41, 'db_type': 18, 'db_ip': '127.0.0.1', 'db_port': '2121',
                      'db_sid': directory, 'db_url': 'ftp://127.0.0.1:2121' + ('/' + directory if directory else '')}
            self.assertEqual(p.validate_datasource_grant({'config': {'datasource': [source]}}, 'ftp', [binding]), binding)

    def test_kafka_configuration_cannot_replace_pinned_brokers_or_load_custom_serializers(self):
        binding = self.connection('kafka')
        source = {'id': 41, 'db_type': 8, 'db_ip': '127.0.0.1', 'db_port': '15432', 'db_url': 'kafka2://127.0.0.1:15432'}
        for key in ('etl.kafka.bootstrap.servers', 'etl.kafka.value.deserializer', 'etl.kafka.sasl.jaas.config'):
            module = {'config': {'datasource': [source], 'adapter_param': [{'param_name': key, 'param_value': 'untrusted'}]}}
            with self.assertRaisesRegex(p.ProtocolError, 'CONNECTOR_PARAMETERS_REJECTED'):
                p.validate_datasource_grant(module, 'kafka', [binding])

    def test_database_primary_keys_are_not_misclassified_as_dynamic_file_names(self):
        record = p.reservation({**self.request(), 'connectionBindings': [self.connection()]})
        outputs = self.root / 'outputs'; outputs.mkdir()
        record['outputsDir'] = str(outputs)
        module = {'module_info': {'flow_id': 21001, 'module_instance_id': 21002, 'adapter_version': '2.0.0'},
                  'adapter': {'adapter_name': 'postgresqlOutput', 'adapter_type': 'output', 'language_type': 1,
                    'adapter_class_name': p.ADAPTERS['postgresqlOutput'][1]},
                  'config': {'datasource': [{'id': 41, 'db_type': 16, 'db_ip': '127.0.0.1', 'db_port': '15432',
                    'db_sid': 'postgres', 'db_url': 'jdbc:postgresql://127.0.0.1:15432/postgres',
                    'dst_table_field': [{'field_name': 'id', 'field_key_flag': 1}]}]}}
        def blob(value):
            raw = json.dumps(value).encode()
            return {'base64': base64.b64encode(raw).decode(), 'sha256': hashlib.sha256(raw).hexdigest()}
        result = {'ok': True, 'protocolVersion': 1, 'businessTaskId': record['businessTaskId'],
                  'ownerId': '1', 'revision': '1', 'flowId': '21001', 'workspaceId': '100',
                  'instanceId': '21002', 'execId': '21003', 'snapshotSha256': 'a' * 64,
                  'metadataSnapshot': {'metadataIdBase': '21101', 'allocatedThrough': '21102',
                    'outputsDir': str(outputs), 'connectionBindings': [self.connection()]},
                  'flowjson': blob({'flow': {'id': 21001, 'group_id': 100}, 'streams': [{'modules': [module]}]}),
                  'schedule': blob({'id': 21001, 'exec_id': '21003', 'oper_type': 1, 'oper_value': 1,
                    'strategy_flow': {'id': 21001, 'group_id': 100}}),
                  'stop': blob({'id': 21001, 'oper_type': 1, 'oper_value': 0})}
        self.assertEqual(set(p.validate_compiled(result, record)), {'flowjson', 'schedule', 'stop'})
        module['adapter'].update(adapter_name='fileOutput', adapter_class_name=p.ADAPTERS['fileOutput'][1])
        result['flowjson'] = blob({'flow': {'id': 21001, 'group_id': 100}, 'streams': [{'modules': [module]}]})
        with self.assertRaisesRegex(p.ProtocolError, 'DYNAMIC_FILENAME_NOT_SUPPORTED'):
            p.validate_compiled(result, record)


if __name__ == '__main__':
    unittest.main()
