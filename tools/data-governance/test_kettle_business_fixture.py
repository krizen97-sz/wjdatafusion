#!/usr/bin/env python3
"""Offline assertions only: source configuration invariants and synthetic inputs, no engine."""
import json
import os
from pathlib import Path
import re
import tempfile
import unittest
import uuid

import kettle_business_fixture as fixture


class FixtureUnitTest(unittest.TestCase):
    def test_exact_original_limits_and_branch_expectations(self):
        for kind, limit, routes in [('ordinary', 10000, {'file': 9998, 'drop': 2}),
                                    ('illegal', 200, {'file': 192, 'drop': 7, 'kafka': 1})]:
            records, expected = fixture.records_and_expectations(kind, limit)
            self.assertEqual(limit, len(records))
            self.assertEqual(routes, expected['routeCounts'])
            self.assertEqual(limit, len({record['key'] for record in records}))
            for record in records:
                self.assertEqual(record['key'], json.loads(record['message'])['_fixture']['id'])
                self.assertNotRegex(record['message'], r'\b(?:\d{1,3}\.){3}\d{1,3}\b')
            self.assertEqual('EXPECTED_NOT_EXECUTED', expected['assertionStatus'])

    def test_sql_is_new_database_only_and_has_no_destructive_replacement(self):
        sql, tables = fixture.fixture_sql({'database': 'rynew_kettle_fixture_unit'}, ['synthetic-platform'])
        self.assertIn("current_database() <> 'rynew_kettle_fixture_unit'", sql)
        self.assertNotIn('DROP ', sql)
        self.assertNotIn('TRUNCATE ', sql)
        self.assertNotIn('IF NOT EXISTS', sql)
        self.assertEqual(5, len(tables))
        self.assertIn("'TEST-WHITE-NULL', NULL, '2'", sql)
        self.assertIn("'TEST-RED-9', '9'", sql)

    def test_dtd_invalid_slug_and_git_output_fail_before_writes(self):
        with self.assertRaises(ValueError):
            fixture.parse(b'<!DOCTYPE transformation [<!ENTITY x SYSTEM "file:///etc/passwd">]><transformation/>')
        with self.assertRaises(ValueError):
            fixture.prepare('/nonexistent', '/nonexistent', '../escape')
        with self.assertRaises(ValueError):
            fixture.prepare('/nonexistent', Path(__file__).parent / 'generated-test-not-created', 'unit')


@unittest.skipUnless(os.environ.get('KETTLE_ORIGINAL_ATTACHMENTS') == '1', 'Opt-in read-only original archive tests')
class OriginalAttachmentFixtureTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.temporary = tempfile.TemporaryDirectory(prefix='rynew-kettle-business-fixture-')
        cls.output = Path(cls.temporary.name) / 'private'
        cls.source = Path(os.environ.get('KETTLE_ORIGINAL_ROOT', '/Volumes/KINGSTON/datai'))
        cls.manifest = fixture.prepare(cls.source, cls.output, 'offline_test')

    @classmethod
    def tearDownClass(cls):
        cls.temporary.cleanup()

    def test_all_42_steps_copies_hops_scripts_and_unknown_config_survive(self):
        for kind in ['ordinary', 'illegal']:
            original, _ = fixture.read_source(self.source, kind)
            prepared = fixture.parse((self.output / (kind + '.ktr')).read_bytes())
            before = fixture.children(original.documentElement, 'step')
            after = fixture.children(prepared.documentElement, 'step')
            self.assertEqual(fixture.EXPECTED_STEPS[kind], len(after))
            self.assertEqual([fixture.processing_digest(s) for s in before], [fixture.processing_digest(s) for s in after])
            self.assertEqual(fixture.at(original.documentElement, 'order').toxml(), fixture.at(prepared.documentElement, 'order').toxml())
            self.assertEqual(['1'] * len(after), [fixture.text(s, 'copies') for s in after])
            self.assertFalse(any(secret in prepared.toxml() for secret in fixture.secrets(original)))
            for connection in fixture.children(prepared.documentElement, 'connection'):
                self.assertEqual('127.0.0.1', fixture.text(connection, 'server'))
                self.assertEqual('15432', fixture.text(connection, 'port'))
                for attribute in connection.getElementsByTagName('attribute'):
                    if fixture.text(attribute, 'code') == 'PORT_NUMBER':
                        self.assertEqual('15432', fixture.text(attribute, 'attribute'))
            for step in after:
                if fixture.text(step, 'type') == 'TextFileOutput':
                    self.assertTrue(fixture.text(step, 'file/name').startswith('${WORK_DIR}/'))
                    self.assertEqual('|$[1F]', fixture.text(step, 'separator'))
                    self.assertEqual('Y', fixture.text(step, 'file/rename_file_name'))

    def test_each_original_jsonpath_is_present_even_when_value_is_null(self):
        for kind in ['ordinary', 'illegal']:
            original, _ = fixture.read_source(self.source, kind)
            source = next(s for s in fixture.children(original.documentElement, 'step') if fixture.text(s, 'type') == 'JsonInput')
            paths = [fixture.text(f, 'path') for f in fixture.children(fixture.at(source, 'fields'), 'field')]
            self.assertTrue(paths)
            for case in fixture.cases(kind):
                message = fixture.message(kind, case, 0)
                for path in paths:
                    self.assertRegex(path, r'^\$\.[\w.\[\]0-9]+$')
                    current = message
                    for key, index in re.findall(r'(\w+)|\[(\d+)\]', path[2:]):
                        current = current[key] if key else current[int(index)]

    def test_original_consumer_and_producer_semantics_are_not_simplified(self):
        ordinary = self.manifest['graphs']['ordinary']
        illegal = self.manifest['graphs']['illegal']
        self.assertEqual(('largest', 'true', 10000, True), (ordinary['autoOffsetReset'], ordinary['autoCommit'], ordinary['limit'], ordinary['stopOnEmptyPresent']))
        self.assertEqual(('smallest', 'false', 200, False), (illegal['autoOffsetReset'], illegal['autoCommit'], illegal['limit'], illegal['stopOnEmptyPresent']))
        doc = fixture.parse((self.output / 'illegal.ktr').read_bytes())
        producer = next(s for s in fixture.children(doc.documentElement, 'step') if fixture.text(s, 'type') == 'KafkaProducer')
        self.assertEqual('kafka.serializer.DefaultEncoder', fixture.text(producer, 'KAFKA/serializer.class'))
        self.assertFalse(self.manifest['offsetInitialState']['performed'])

    def test_original_job_vs_new_composition_and_input_output_ftp_boundary(self):
        self.assertEqual(2, self.manifest['graphs']['ordinary_job']['entryCount'])
        self.assertEqual(0, self.manifest['graphs']['ordinary_job']['transformationEntries'])
        for filename in ['illegal.kjb', 'ordinary-composed-fixture.kjb']:
            document = fixture.parse((self.output / filename).read_bytes())
            entries = document.getElementsByTagName('entry')
            self.assertEqual(3, len(entries))
            trans = next(e for e in entries if fixture.text(e, 'type') == 'TRANS')
            self.assertTrue(fixture.text(trans, 'filename').startswith('${INPUT_DIR}/'))
            ftp = next(e for e in entries if fixture.text(e, 'type') == 'FTP_PUT')
            self.assertEqual('${WORK_DIR}', fixture.text(ftp, 'localDirectory'))
            self.assertEqual('Y', fixture.text(ftp, 'remove'))
            self.assertEqual('127.0.0.1', fixture.text(ftp, 'servername'))
        composed = fixture.parse((self.output / 'ordinary-composed-fixture.kjb').read_bytes())
        last_hop = composed.getElementsByTagName('hop')[-1]
        self.assertEqual('N', fixture.text(last_hop, 'unconditional'))
        self.assertEqual('Y', fixture.text(last_hop, 'evaluation'))
        for kind in ['ordinary', 'illegal']:
            expected = self.manifest['graphs'][kind + '_job']['ftpExpectation']
            basename = self.manifest['graphs'][kind]['outputBasename']
            self.assertRegex(basename + '.csv', expected['wildcard'])

    def test_native_lookup_contract_and_private_artifacts_are_reviewable(self):
        known = {'redlist', 'qiuji', 'whitelist', 'xc_cross_csd_status', 'xc_local_sync_cross'}
        observed = set()
        for kind in ['ordinary', 'illegal']:
            document = fixture.parse((self.output / (kind + '.ktr')).read_bytes())
            for step in fixture.children(document.documentElement, 'step'):
                if fixture.text(step, 'type') == 'DBLookup':
                    table = fixture.text(step, 'lookup/table')
                    observed.add(table)
                    columns = self.manifest['tables'][table]['columns']
                    for key in fixture.children(fixture.at(step, 'lookup'), 'key'):
                        self.assertIn(fixture.text(key, 'field'), columns)
                    for value in fixture.children(fixture.at(step, 'lookup'), 'value'):
                        self.assertIn(fixture.text(value, 'name'), columns)
        self.assertEqual(known, observed)
        for path in self.output.iterdir():
            self.assertEqual(0o600, path.stat().st_mode & 0o777)
            if path.name in self.manifest['files']:
                self.assertEqual(self.manifest['files'][path.name]['sha256'], fixture.sha(path.read_bytes()))
        self.assertEqual(0o700, self.output.stat().st_mode & 0o777)
        with self.assertRaises(ValueError):
            fixture.prepare(self.source, self.output, 'offline_test')

    def test_explicit_imported_definition_binding_uses_input_dir_uuid(self):
        original, _ = fixture.read_source(self.source, 'illegal_job')
        identity = str(uuid.uuid4())
        prepared, summary = fixture.prepare_job(original, 'illegal', {'slug': 'binding_test', 'childDefinitionId': identity,
                'ftpUser': 'fixture_user', 'ftpPassword': '${FIXTURE_FTP_PASSWORD}'})
        trans = next(e for e in prepared.getElementsByTagName('entry') if fixture.text(e, 'type') == 'TRANS')
        self.assertEqual('${INPUT_DIR}/' + identity + '.ktr', fixture.text(trans, 'filename'))
        self.assertEqual(identity, trans.getAttribute('data-rynew-definition-id'))
        self.assertFalse(summary['apiChildBindingRequired'])

    def test_artifact_verifier_rejects_missing_records_changed_payload_and_config_upload(self):
        # Artificial output tests the verifier, never serves as native execution evidence.
        target = Path(self.temporary.name) / 'unit-only-artificial-ftp'
        target.mkdir()
        expected = json.loads((self.output / 'illegal-expected.json').read_text())
        expectations = {c['caseId']: c for c in expected['cases']}
        records = [json.loads(line) for line in (self.output / 'illegal-messages.ndjson').read_text().splitlines()]
        rows, kafka = [], []
        for record in records:
            case = expectations[record['caseId']]
            if case['route'] == 'kafka':
                kafka.append(record)
            if case['route'] != 'file':
                continue
            body = json.loads(record['message'])
            attrs = body['vehicleAlarmResult'][0]['targetAttrs']
            attrs.update(crossingIndexCode=case['crossingIndexCode'], crossingId=case['crossingId'],
                         areaCode=case['region'], regionIndexCode=case['region'])
            rows.append('|\x1f'.join([fixture.compact(body), '', '', body['vehicleAlarmResult'][0]['targetPicUrl'] or '', attrs['platePicUrl'] or '']))
        header = '|\x1f'.join(self.manifest['graphs']['illegal']['writer']['fields'])
        output_file = target / (self.manifest['graphs']['illegal']['outputBasename'] + '.csv')
        content = header + '\n' + '\n'.join(rows) + '\n'
        output_file.write_text(content)
        egress_file = Path(self.temporary.name) / 'unit-only-artificial-egress.ndjson'
        egress_file.write_text(''.join(fixture.compact(row) + '\n' for row in kafka))
        result = fixture.verify_outputs(self.output, 'illegal', target, egress_file)
        self.assertEqual((192, 1), (result['fileRows'], result['kafkaRows']))
        self.assertFalse(result['engineProvenanceAndFtpSuccessGateVerified'])
        output_file.write_text(header + '\n' + '\n'.join(rows[:-1]) + '\n')
        with self.assertRaises(ValueError):
            fixture.verify_outputs(self.output, 'illegal', target, egress_file)
        output_file.write_text(content)
        kafka[0]['message'] += ' '
        egress_file.write_text(''.join(fixture.compact(row) + '\n' for row in kafka))
        with self.assertRaises(ValueError):
            fixture.verify_outputs(self.output, 'illegal', target, egress_file)
        (target / 'child.ktr').write_text('<transformation><password>synthetic-only</password></transformation>')
        with self.assertRaises(ValueError):
            fixture.verify_outputs(self.output, 'illegal', target, egress_file)


if __name__ == '__main__':
    unittest.main()
