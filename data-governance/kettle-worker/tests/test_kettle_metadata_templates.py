"""Original metadata allocation/readback only; no transformations or jobs are executed."""
import importlib.util
import os
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET

spec = importlib.util.spec_from_file_location('fixtures', Path(__file__).with_name('test_kettle_worker.py'))
fixtures = importlib.util.module_from_spec(spec)
spec.loader.exec_module(fixtures)


@unittest.skipUnless(os.environ.get('KETTLE_WORKER_RUNTIME'), 'Requires prepared original metadata classes')
class NativeMetadataTemplateTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.worker = fixtures.module.Worker(os.environ['KETTLE_WORKER_RUNTIME'])
        cls.capabilities = cls.worker.capabilities()
        cls.steps = {step['id']: step for step in cls.capabilities['steps']}

    def xml(self, entry, key):
        return ET.fromstring('<settings>' + entry[key] + '</settings>')

    def test_sort_rows_and_table_output_preserve_empty_original_defaults(self):
        expected = {'SortRows': {'name', 'ascending', 'case_sensitive', 'presorted'}, 'TableOutput': {'column_name', 'stream_name'}}
        for identifier, field_columns in expected.items():
            entry = self.steps[identifier]
            original, template = self.xml(entry, 'defaultXml'), self.xml(entry, 'configurationTemplateXml')
            self.assertEqual(len(original.findall('./fields/field')), 0)
            self.assertEqual(len(template.findall('./fields/field')), 1)
            self.assertTrue(field_columns <= {field.tag for field in template.find('./fields/field')})
            self.assertTrue(entry['allocationApplied'])
            self.assertEqual(entry['allocationMethod'], 'allocate(int)')
            self.assertEqual(entry['allocationArguments'], [1])
            self.assertTrue(entry['configurationTemplateReadback'])
            for scalar in original:
                if scalar.tag != 'fields':
                    self.assertEqual(template.findtext(scalar.tag), original.findtext(scalar.tag))
            transformation = ET.Element('transformation')
            fixtures.put(transformation, 'info/name', 'Metadata-only ' + identifier)
            step = ET.SubElement(transformation, 'step')
            for key, value in {'name': identifier, 'type': identifier, 'copies': '1', 'GUI/draw': 'Y'}.items():
                fixtures.put(step, key, value)
            for child in template:
                step.append(child)
            loaded = self.worker.validate(ET.tostring(transformation, encoding='unicode'))
            self.assertTrue(loaded['valid'], loaded)
            self.assertEqual(loaded['nodes'][0]['classSource'], 'kettle-6.1.0.7.36.jar')

    def test_select_values_three_native_arrays_and_boolean_encoding(self):
        entry = self.steps['SelectValues']
        original, template = self.xml(entry, 'defaultXml'), self.xml(entry, 'configurationTemplateXml')
        self.assertEqual(entry['allocationMethod'], 'allocate(int,int,int)')
        self.assertEqual(entry['allocationArguments'], [1, 1, 1])
        self.assertTrue(entry['configurationTemplateReadback'])
        for tag in ['field', 'remove', 'meta']:
            self.assertEqual(len(original.findall('./fields/' + tag)), 0)
            self.assertEqual(len(template.findall('./fields/' + tag)), 1)
        self.assertEqual(template.findtext('./fields/select_unspecified'), 'N')
        self.assertEqual(template.findtext('./fields/meta/date_format_lenient'), 'false')
        self.assertEqual(template.findtext('./fields/meta/lenient_string_to_number'), 'false')
        self.assertEqual(self.xml(self.steps['SortRows'], 'configurationTemplateXml').findtext('./fields/field/ascending'), 'N')
        self.assertEqual(self.xml(self.steps['TableOutput'], 'configurationTemplateXml').findtext('./use_batch'), 'Y')

    def test_failed_or_unavailable_allocation_keeps_original_xml(self):
        entries = self.capabilities['steps'] + self.capabilities['jobs']
        failed = [entry for entry in entries if str(entry.get('allocationReason', '')).startswith('NATIVE_')]
        unavailable = [entry for entry in entries if entry.get('allocationReason') == 'NO_SAFE_PUBLIC_ALLOCATE_METHOD']
        self.assertTrue(failed)
        self.assertTrue(unavailable)
        for entry in failed + unavailable:
            self.assertFalse(entry['allocationApplied'])
            self.assertEqual(entry['configurationTemplateXml'], entry['defaultXml'])
        for entry in entries:
            if entry.get('allocationApplied'):
                self.assertTrue(1 <= len(entry['allocationArguments']) <= 6)
                self.assertEqual(set(entry['allocationArguments']), {1})
                self.assertTrue(entry['configurationTemplateReadback'])

    def test_job_templates_are_metadata_only(self):
        allocated = [entry for entry in self.capabilities['jobs'] if entry.get('allocationApplied')]
        self.assertTrue(allocated)
        for entry in allocated:
            self.assertTrue(entry['configurationTemplateReadback'])
            ET.fromstring('<entry>' + entry['configurationTemplateXml'] + '</entry>')
        operations = [run for run in self.worker.runs.values() if not run.get('restored')]
        self.assertTrue(all(run['operation'] in {'capabilities', 'validate'} for run in operations))
        self.assertFalse(any(event.get('type') == 'state' and event.get('state') == 'RUNNING' for run in operations for event in run['events']))


if __name__ == '__main__':
    unittest.main(verbosity=2)
