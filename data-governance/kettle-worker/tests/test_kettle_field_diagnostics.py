"""Native field failure observations and explicit metadata-only validation scope."""
import importlib.util
import os
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET

spec = importlib.util.spec_from_file_location('fixtures', Path(__file__).with_name('test_kettle_worker.py'))
fixtures = importlib.util.module_from_spec(spec)
spec.loader.exec_module(fixtures)


@unittest.skipUnless(os.environ.get('KETTLE_WORKER_RUNTIME'), 'Requires prepared original metadata/runtime')
class NativeFieldDiagnosticTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.worker = fixtures.module.Worker(os.environ['KETTLE_WORKER_RUNTIME'])
        cls.catalog = cls.worker.capabilities()

    def test_lazy_string_replacement_has_explicit_field_error_not_fake_null(self):
        root = ET.fromstring(fixtures.fixture(self.catalog))
        source = next(step for step in root.findall('step') if step.findtext('type') == 'CsvInput')
        fixtures.put(source, 'lazy_conversion', 'Y')
        script = next(step for step in root.findall('step') if step.findtext('type') == 'ScriptValueMod')
        fixtures.put(script, 'jsScripts/jsScript/jsScript_script', 'name = name.toUpperCase();')
        fixtures.put(script, 'fields/field/name', 'name')
        fixtures.put(script, 'fields/field/replace', 'Y')
        destination = next(step for step in root.findall('step') if step.findtext('type') == 'TextFileOutput')
        fixtures.put(destination, 'type', 'Dummy')
        identifier = self.worker.launch('run', ET.tostring(root, encoding='unicode'), input_files=[{'name': 'input.csv', 'content': 'name\nalice\n'}])
        outcome = self.worker.wait(identifier)
        self.assertEqual(outcome['state'], 'SUCCEEDED')  # Observation must not rewrite the original engine result.
        row = next(event for event in self.worker.runs[identifier]['events'] if event['type'] == 'row' and event['node'] == 'native-script' and event['direction'] == 'written')
        self.assertNotIn('name', row['fields'])
        error = next(error for error in row['fieldErrors'] if error['name'] == 'name')
        self.assertEqual(error['type'], 'String')
        self.assertTrue(error['errorClass'])
        self.assertTrue(error['message'])
        self.assertLessEqual(len(error['message']), 1024)

    def test_native_long_values_stay_exact_decimal_strings(self):
        root = ET.Element('transformation')
        fixtures.put(root, 'info/name', 'Synthetic native long literals')
        step = ET.SubElement(root, 'step')
        for key, value in {'name': 'native-integers', 'type': 'DataGrid', 'copies': '1', 'GUI/draw': 'Y', 'fields/field/name': 'integer_value', 'fields/field/type': 'Integer', 'fields/field/length': '-1', 'fields/field/precision': '-1', 'fields/field/format': '0'}.items():
            fixtures.put(step, key, value)
        data = ET.SubElement(step, 'data')
        values = ['9007199254740993', '9223372036854775807']
        for value in values:
            line = ET.SubElement(data, 'line')
            fixtures.put(line, 'item', value)
        identifier = self.worker.launch('run', ET.tostring(root, encoding='unicode'))
        outcome = self.worker.wait(identifier)
        self.assertEqual(outcome['state'], 'SUCCEEDED')
        rows = [event for event in self.worker.runs[identifier]['events'] if event['type'] == 'row' and event['direction'] == 'written']
        self.assertEqual([row['fields']['integer_value'] for row in rows], values)
        self.assertTrue(all(not row['fieldErrors'] for row in rows))

    def test_metadata_loaded_is_not_a_claim_that_fields_or_step_checks_passed(self):
        root = ET.fromstring(fixtures.fixture(self.catalog))
        script = next(step for step in root.findall('step') if step.findtext('type') == 'ScriptValueMod')
        name = script.findtext('name')
        script.clear()
        for key, value in {'name': name, 'type': 'TableInput', 'copies': '1', 'GUI/draw': 'Y', 'connection': 'missing_synthetic_connection', 'sql': 'SELECT 1'}.items():
            fixtures.put(script, key, value)
        connection = ET.SubElement(root, 'connection')
        for key, value in {'name': 'missing_synthetic_connection', 'server': '127.0.0.1', 'type': 'POSTGRESQL', 'access': 'Native', 'database': 'synthetic', 'port': '9', 'username': 'synthetic-user', 'password': 'synthetic-password'}.items():
            fixtures.put(connection, key, value)
        xml = ET.tostring(root, encoding='unicode')
        result = self.worker.validate(xml)
        self.assertEqual(result['validationScope'], 'metadata-only')
        self.assertTrue(result['metadataLoaded'])
        self.assertFalse(result['fieldsResolved'])
        self.assertFalse(result['valid'])
        self.assertTrue(result['fieldDiagnostics'])
        self.assertTrue(all(len(error['message']) <= 1024 for error in result['fieldDiagnostics']))
        self.assertNotIn('synthetic-password', str(result))
        saved = self.worker.save('metadata-diagnostic-' + str(__import__('time').time_ns()), xml)
        self.assertIn('id', saved)  # Incomplete field discovery may be saved, without any implicit field queries.
        self.assertTrue(saved['validation']['valid'])
        self.assertEqual(saved['validation']['validationScope'], 'xml-load')
        self.assertFalse(saved['validation']['fieldsRequested'])
        self.assertTrue(all('fields' not in node for node in saved['validation']['nodes']))


if __name__ == '__main__':
    unittest.main(verbosity=2)
