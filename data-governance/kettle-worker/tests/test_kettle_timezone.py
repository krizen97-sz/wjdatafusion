"""Actual original ScriptValueMod/Date formatting under frozen execution timezones."""
import importlib.util
import os
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET

spec = importlib.util.spec_from_file_location('fixtures', Path(__file__).with_name('test_kettle_worker.py'))
fixtures = importlib.util.module_from_spec(spec)
spec.loader.exec_module(fixtures)


def date_transformation(catalog, zone=None):
    root = ET.fromstring(fixtures.fixture(catalog))
    if zone is not None:
        root.set('data-rynew-timezone', zone)
    script = next(step for step in root.findall('step') if step.findtext('type') == 'ScriptValueMod')
    script.find('./jsScripts/jsScript/jsScript_script').text = 'var greeting = String(new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date(java.lang.Long.parseLong(name))));'
    return ET.tostring(root, encoding='unicode')


class TimezoneValidationTests(unittest.TestCase):
    def test_fixed_default_and_reject_ambiguous_or_injected_zones(self):
        self.assertEqual(fixtures.module.execution_time_zone('<transformation/>'), 'Asia/Shanghai')
        for value in ['', ' ', 'CST', 'EST', 'GMT+08:00', '+08:00', 'Asia/../UTC', 'UTC -Xmx8g', 'Invalid/Nowhere']:
            root = ET.Element('transformation', {'data-rynew-timezone': value})
            with self.assertRaises(ValueError):
                fixtures.module.execution_time_zone(ET.tostring(root, encoding='unicode'))
        self.assertEqual(fixtures.module.execution_time_zone('<job data-rynew-timezone="UTC"/>'), 'UTC')


@unittest.skipUnless(os.environ.get('KETTLE_WORKER_RUNTIME'), 'Requires prepared original Java runtime')
class NativeTimezoneTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.worker = fixtures.module.Worker(os.environ['KETTLE_WORKER_RUNTIME'])
        cls.catalog = cls.worker.capabilities()

    def run_dates(self, xml, operation='run', inputs=None):
        assets = [{'name': 'input.csv', 'content': 'name\n-1\n86399000\n'}] + (inputs or [])
        identifier = self.worker.launch(operation, xml, input_files=assets)
        result = self.worker.wait(identifier)
        self.assertEqual(result['state'], 'SUCCEEDED', self.worker.runs[identifier]['events'])
        directory = Path(self.worker.runs[identifier]['directory'])
        frozen = directory / ('transformation.kjb' if operation == 'job' else 'transformation.ktr')
        self.assertEqual(frozen.read_text(), xml)
        return result, (directory / 'output/result.csv').read_text().splitlines()

    def test_two_critical_epochs_have_real_utc_and_shanghai_dates(self):
        expected = {'UTC': ['name,greeting', '-1,1969-12-31 23:59:59', '86399000,1970-01-01 23:59:59'], 'Asia/Shanghai': ['name,greeting', '-1,1970-01-01 07:59:59', '86399000,1970-01-02 07:59:59']}
        for zone, lines in expected.items():
            result, actual = self.run_dates(date_transformation(self.catalog, zone))
            self.assertEqual(actual, lines)
            self.assertEqual(result['executionTimeZone'], zone)
            self.assertEqual(result['effectiveTimeZone'], zone)
        default_result, default_lines = self.run_dates(date_transformation(self.catalog))
        self.assertEqual(default_lines, expected['Asia/Shanghai'])
        self.assertEqual(default_result['executionTimeZone'], 'Asia/Shanghai')

    def test_child_transformation_inherits_parent_job_timezone(self):
        if not (self.worker.artifacts_root / 'classes/NativeJobExecutor.class').exists():
            self.skipTest('Native Job executor is a separate integration dependency')
        root = ET.Element('job', {'data-rynew-timezone': 'Asia/Shanghai'})
        fixtures.put(root, 'name', 'Synthetic parent timezone')
        entries = ET.SubElement(root, 'entries')
        for values in [
            {'name': 'START', 'type': 'SPECIAL', 'start': 'Y', 'dummy': 'N', 'repeat': 'N', 'draw': 'Y', 'nr': '0'},
            {'name': 'Original child', 'type': 'TRANS', 'specification_method': 'filename', 'filename': '${INPUT_DIR}/child.ktr', 'wait_until_finished': 'Y', 'cluster': 'N', 'slave_server_name': '', 'draw': 'Y', 'nr': '0', 'parameters/pass_all_parameters': 'Y'},
        ]:
            entry = ET.SubElement(entries, 'entry')
            for key, value in values.items():
                fixtures.put(entry, key, value)
        for key, value in {'from': 'START', 'to': 'Original child', 'from_nr': '0', 'to_nr': '0', 'enabled': 'Y', 'evaluation': 'Y', 'unconditional': 'Y'}.items():
            fixtures.put(root, 'hops/hop/' + key, value)
        child = date_transformation(self.catalog, 'UTC')
        result, lines = self.run_dates(ET.tostring(root, encoding='unicode'), 'job', [{'name': 'child.ktr', 'content': child}])
        self.assertEqual(result['effectiveTimeZone'], 'Asia/Shanghai')
        self.assertEqual(lines, ['name,greeting', '-1,1970-01-01 07:59:59', '86399000,1970-01-02 07:59:59'])
        self.assertEqual((Path(self.worker.runs[result['id']]['directory']) / 'input/child.ktr').read_text(), child)


if __name__ == '__main__':
    unittest.main(verbosity=2)
