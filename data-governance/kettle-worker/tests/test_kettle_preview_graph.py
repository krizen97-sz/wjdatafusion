"""Native preview subgraphs: keep branch semantics while excluding original output steps."""
import copy
import importlib.util
import os
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET

spec = importlib.util.spec_from_file_location('fixtures', Path(__file__).with_name('test_kettle_worker.py'))
fixtures = importlib.util.module_from_spec(spec)
spec.loader.exec_module(fixtures)


def branch_xml(catalog, kind):
    root = ET.fromstring(fixtures.fixture(catalog))
    writer = next(step for step in root.findall('step') if step.findtext('name') == 'file-output')
    alternate = copy.deepcopy(writer)
    fixtures.put(alternate, 'name', 'unselected-output')
    fixtures.put(alternate, 'file/name', '${WORK_DIR}/unselected')
    root.append(alternate)
    if kind == 'fanout':
        # Preserve the order of unselected then selected hops for native round-robin distribution.
        order = root.find('order')
        first = ET.Element('hop')
        for key, value in {'from': 'file-input', 'to': 'unselected-output', 'enabled': 'Y'}.items():
            fixtures.put(first, key, value)
        order.insert(0, first)
        return ET.tostring(root, encoding='unicode')
    router = ET.SubElement(root, 'step')
    for key, value in {'name': 'router', 'type': kind, 'copies': '1', 'distribute': 'Y', 'GUI/draw': 'Y'}.items():
        fixtures.put(router, key, value)
    if kind == 'FilterRows':
        for key, value in {'send_true_to': 'native-script', 'send_false_to': 'unselected-output', 'compare/condition/negated': 'N', 'compare/condition/leftvalue': 'name', 'compare/condition/function': '=', 'compare/condition/rightvalue': '', 'compare/condition/value/name': 'constant', 'compare/condition/value/type': 'String', 'compare/condition/value/text': 'alice', 'compare/condition/value/length': '-1', 'compare/condition/value/precision': '-1', 'compare/condition/value/isnull': 'N'}.items():
            fixtures.put(router, key, value)
    else:
        fallback = copy.deepcopy(writer)
        fixtures.put(fallback, 'name', 'default-output')
        fixtures.put(fallback, 'file/name', '${WORK_DIR}/default')
        root.append(fallback)
        for key, value in {'fieldname': 'name', 'use_contains': 'N', 'case_value_type': 'String', 'default_target_step': 'default-output'}.items():
            fixtures.put(router, key, value)
        cases = ET.SubElement(router, 'cases')
        for value, target in [('alice', 'native-script'), ('bob', 'unselected-output')]:
            case = ET.SubElement(cases, 'case')
            fixtures.put(case, 'value', value)
            fixtures.put(case, 'target_step', target)
    for hop in root.findall('./order/hop'):
        if hop.findtext('from') == 'file-input':
            fixtures.put(hop, 'to', 'router')
    destinations = ['native-script', 'unselected-output'] + (['default-output'] if kind == 'SwitchCase' else [])
    for destination in destinations:
        hop = ET.SubElement(root.find('order'), 'hop')
        for key, value in {'from': 'router', 'to': destination, 'enabled': 'Y'}.items():
            fixtures.put(hop, key, value)
    return ET.tostring(root, encoding='unicode')


@unittest.skipUnless(os.environ.get('KETTLE_WORKER_RUNTIME'), 'Requires prepared original runtime')
class NativePreviewGraphTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.worker = fixtures.module.Worker(os.environ['KETTLE_WORKER_RUNTIME'])
        cls.catalog = cls.worker.capabilities()

    def run_case(self, xml, target=''):
        identifier = self.worker.launch('run', xml, preview_step=target, row_limit=20, input_files=[{'name': 'input.csv', 'content': 'name\nalice\nbob\nalice\ncarol\n'}])
        result = self.worker.wait(identifier)
        events = self.worker.runs[identifier]['events']
        return identifier, result, events

    def rows(self, events, node):
        return [event['fields'] for event in events if event['type'] == 'row' and event['node'] == node and event['direction'] == 'written']

    def assert_no_original_output(self, result):
        self.assertFalse(any(node['node'] in {'file-output', 'unselected-output', 'default-output'} for node in result.get('nodes', [])))
        self.assertFalse(any(file['role'] == 'output' for file in result['files']))

    def test_linear_preview_has_exactly_two_original_metrics_and_run_still_writes(self):
        xml = fixtures.fixture(self.catalog)
        identifier, result, events = self.run_case(xml, 'native-script')
        self.assertEqual(result['state'], 'SUCCEEDED', events)
        self.assertEqual({node['node'] for node in result['nodes']}, {'file-input', 'native-script'})
        self.assert_no_original_output(result)
        self.assertEqual({node['name'] for node in result['previewProjection']['excludedOriginalNodes']}, {'file-output'})
        self.assertEqual(result['previewProjection']['collectors'], [])
        self.assertEqual((Path(self.worker.runs[identifier]['directory']) / 'transformation.ktr').read_text(), xml)
        _, full, _ = self.run_case(xml)
        self.assertEqual(full['state'], 'SUCCEEDED')
        fd, _ = self.worker.open_file(full['id'], 'result.csv')
        with os.fdopen(fd) as stream:
            self.assertEqual(stream.read().splitlines(), ['name,greeting', 'alice,ALICE!', 'bob,BOB!', 'alice,ALICE!', 'carol,CAROL!'])

    def test_filter_rows_selected_branch_preserves_only_matching_rows(self):
        xml = branch_xml(self.catalog, 'FilterRows')
        _, result, events = self.run_case(xml, 'native-script')
        self.assertEqual(result['state'], 'SUCCEEDED', events)
        self.assertEqual([row['greeting'] for row in self.rows(events, 'native-script')], ['ALICE!', 'ALICE!'])
        self.assert_no_original_output(result)
        collectors = [node for node in result['nodes'] if node['previewCollector']]
        self.assertEqual(len(collectors), 1)
        self.assertEqual(collectors[0]['pluginId'], 'Dummy')
        self.assertEqual(collectors[0]['read'], 2)

    def test_switch_case_selected_branch_and_router_itself_are_safe(self):
        xml = branch_xml(self.catalog, 'SwitchCase')
        _, selected, selected_events = self.run_case(xml, 'native-script')
        self.assertEqual(selected['state'], 'SUCCEEDED', selected_events)
        self.assertEqual([row['greeting'] for row in self.rows(selected_events, 'native-script')], ['ALICE!', 'ALICE!'])
        self.assert_no_original_output(selected)
        self.assertEqual(len(selected['previewProjection']['collectors']), 2)
        _, router, router_events = self.run_case(xml, 'router')
        self.assertEqual(router['state'], 'SUCCEEDED', router_events)
        self.assertEqual([row['name'] for row in self.rows(router_events, 'router')], ['alice', 'bob', 'alice', 'carol'])
        self.assert_no_original_output(router)
        self.assertEqual(len(router['previewProjection']['collectors']), 3)
        self.assertEqual({node['node'] for node in router['nodes'] if not node['previewCollector']}, {'file-input', 'router'})

    def test_native_fanout_distribution_keeps_selected_branch_rows(self):
        xml = branch_xml(self.catalog, 'fanout')
        _, full, full_events = self.run_case(xml)
        self.assertEqual(full['state'], 'SUCCEEDED', full_events)
        _, preview, preview_events = self.run_case(xml, 'native-script')
        self.assertEqual(preview['state'], 'SUCCEEDED', preview_events)
        self.assertEqual(self.rows(preview_events, 'native-script'), self.rows(full_events, 'native-script'))
        self.assert_no_original_output(preview)
        self.assertEqual(len(preview['previewProjection']['collectors']), 1)

    def test_output_target_preview_is_rejected_before_any_step_starts(self):
        _, result, events = self.run_case(fixtures.fixture(self.catalog), 'file-output')
        self.assertEqual(result['state'], 'FAILED')
        self.assertFalse(any(event['type'] == 'state' and event.get('state') in {'PREPARING', 'RUNNING'} for event in events))
        self.assertFalse(any(file['role'] == 'output' for file in result['files']))
        self.assertTrue(any('require an explicit run' in event.get('message', '') for event in events))


if __name__ == '__main__':
    unittest.main(verbosity=2)
