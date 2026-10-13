import base64
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


if __name__ == '__main__':
    unittest.main()
