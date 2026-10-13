import os
from pathlib import Path
import sys
import tempfile
import threading
import time
import unittest
import xml.etree.ElementTree as ET

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from hdi_core_worker import RunFileBudget, core_logging, CORE_FILE_LIMIT
from hdi_core_protocol import ProtocolError


class RuntimeFilesTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name).resolve()
        for name in ('outputs', 'store', 'logs', 'private'):
            (self.root / name).mkdir()

    def tearDown(self):
        self.temp.cleanup()

    def sparse(self, name, size):
        with (self.root / name).open('wb') as stream:
            stream.truncate(size)

    def test_original_256m_scratch_segment_does_not_use_output_quota(self):
        self.sparse('store/segment', 256 * 1024 * 1024)
        (self.root / 'outputs/file.csv').write_text('header\nvalue\n')
        RunFileBudget(self.root, lambda: None).check()
        self.assertGreater(CORE_FILE_LIMIT, 256 * 1024 * 1024)

    def test_output_limit_halts_without_waiting_for_metadata_loop(self):
        halted = threading.Event()
        monitor = RunFileBudget(self.root, halted.set, interval=.01)
        monitor.thread.start()
        try:
            self.sparse('outputs/too-large.csv', 64 * 1024 * 1024 + 1)
            self.assertTrue(halted.wait(2), 'independent monitor did not enforce the output limit')
            self.assertEqual(monitor.error, 'OUTPUT_LIMIT')
        finally:
            monitor.close(time.monotonic() + 2)

    def test_scratch_aggregate_remains_bounded(self):
        for n in range(5):
            self.sparse('store/segment-' + str(n), 256 * 1024 * 1024)
        with self.assertRaisesRegex(ProtocolError, 'SCRATCH_LIMIT'):
            RunFileBudget(self.root, lambda: None).check()

    def test_log_aggregate_is_separate_and_bounded(self):
        self.sparse('logs/one.log', 33 * 1024 * 1024)
        self.sparse('logs/two.log', 33 * 1024 * 1024)
        with self.assertRaisesRegex(ProtocolError, 'LOG_LIMIT'):
            RunFileBudget(self.root, lambda: None).check()

    def test_output_file_count_is_checked_before_completion(self):
        for n in range(129):
            (self.root / 'outputs' / str(n)).touch()
        with self.assertRaisesRegex(ProtocolError, 'OUTPUT_LIMIT'):
            RunFileBudget(self.root, lambda: None).check()

    def test_symlink_is_rejected_without_traversing_other_directories(self):
        (self.root / 'outputs/escape').symlink_to(self.root.parent)
        with self.assertRaisesRegex(ProtocolError, 'OUTPUT_PATH_REJECTED'):
            RunFileBudget(self.root, lambda: None).check()

    def test_logging_retains_vendor_sift_and_relocates_paths(self):
        bundle = self.root / 'bundle'
        (bundle / 'instance').mkdir(parents=True)
        original = b'''<configuration>
          <property name="LOG_HOME" value="old"/><property name="DURABLE_LOG_HOME" value="/var/log/old"/>
          <appender name="STDOUT" class="ConsoleAppender"/>
          <appender name="SIFT" class="com.hikvision.idatafusion.etl.core.log.AsyncSiftingAppender">
            <discriminator class="original.Discriminator"/><sift><appender name="File-${taskName}">
            <file>${LOG_HOME}/${taskName}.log</file></appender></sift></appender>
          <logger name="original.task" additivity="false"><appender-ref ref="SIFT"/></logger>
          <root level="INFO"><appender-ref ref="SIFT"/></root></configuration>'''
        source = bundle / 'instance/logback.xml'
        source.write_bytes(original)
        path = core_logging(bundle, self.root)
        document = ET.parse(path).getroot()
        props = {x.get('name'): x.get('value') for x in document.findall('property')}
        self.assertEqual(props['LOG_HOME'], str(self.root / 'logs'))
        self.assertEqual(props['DURABLE_LOG_HOME'], str(self.root / 'logs/durable'))
        self.assertEqual(document.find("appender[@name='SIFT']/discriminator").get('class'), 'original.Discriminator')
        self.assertEqual(document.find("appender[@name='SIFT']/sift/appender/file").text, '${LOG_HOME}/${taskName}.log')
        for logger in document.findall('logger') + document.findall('root'):
            self.assertEqual({x.get('ref') for x in logger.findall('appender-ref')}, {'SIFT', 'STDOUT'})
        self.assertEqual(source.read_bytes(), original)
        self.assertFalse((self.root / 'logs/ETL_TASK_EXEC_STATUS_RECORD/task_exec_status_record.log').exists())


if __name__ == '__main__':
    unittest.main()
