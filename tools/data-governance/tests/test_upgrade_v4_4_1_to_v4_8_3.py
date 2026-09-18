#!/usr/bin/env python3
"""Real MySQL v4.8.3 cumulative upgrade tests; owned UUID schemas only.

Reuses the audited v4.8.2 scenarios and adds document baseline/zero-limit checks.
No production database is read or changed; runtime identity is asserted by the
base harness before any test database is created.
"""
import argparse
from pathlib import Path
import re
import unittest

import test_upgrade_v4_4_1_to_v4_8_2 as baseline

ROOT = Path(__file__).resolve().parents[3]
PREVIOUS = baseline.SQL
baseline.SQL = ROOT / 'WDF100.0/sql/data_governance_upgrade_20260918_v4_4_1_to_v4_8_3_all.sql'
baseline.VERIFY = ROOT / 'WDF100.0/sql/data_governance_verify_20260918_v4_8_3.sql'
TABLES = baseline.TABLES + ('doc_user_quota',)


class UpgradeTest(baseline.UpgradeTest):
    @classmethod
    def setUpClass(cls):
        super().setUpClass()
        source = (ROOT / 'WDF100.0/sql/document_management_v3_9_19_archive_quota_20260816.sql').read_text()
        quota_schema = re.search(r'(?ims)^CREATE TABLE IF NOT EXISTS doc_user_quota\s*\(.*?^\)[^;]*;', source).group(0)
        cls.schema += '\n' + quota_schema + """
          INSERT INTO doc_user_quota(user_id,quota_bytes,max_upload_bytes,create_by,update_by)
          VALUES(1,1073741824,104857600,'existing admin','existing admin'),
                (2,2147483648,0,'existing owner','existing owner'),
                (3,524288000,52428800,'legacy limit','legacy limit');
        """

    def snapshot(self):
        result = {}
        for table in TABLES:
            exists = self.query("SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='" + table + "'")
            result[table] = self.query('SELECT * FROM ' + table + ' ORDER BY 1,2;') if exists == '1' else '[ABSENT]'
        return result

    def upgrade(self):
        before = self.snapshot()['doc_user_quota']
        super().upgrade()
        self.assertEqual(self.snapshot()['doc_user_quota'], before, 'upgrade must not change any user quota')

    def test_previous_cumulative_upgrade_is_noop(self):
        self.query(PREVIOUS.read_text())
        before = self.snapshot()
        self.upgrade()
        self.assertEqual(self.snapshot(), before)

    def test_zero_positive_and_legacy_limits_are_preserved(self):
        self.assertEqual(self.query('SELECT max_upload_bytes FROM doc_user_quota ORDER BY user_id'),
                         '104857600\n0\n52428800')
        self.upgrade()
        self.assertEqual(self.snapshot()['doc_user_quota'], self.initial['doc_user_quota'])
        # Only this owned fixture is written: proves native BIGINT accepts 0.
        self.query('UPDATE doc_user_quota SET max_upload_bytes=0 WHERE user_id=1')
        self.assertEqual(self.query('SELECT max_upload_bytes FROM doc_user_quota WHERE user_id=1'), '0')
        self.upgrade()

    def test_unsigned_bigint_supports_zero_and_is_preserved(self):
        self.query('ALTER TABLE doc_user_quota MODIFY max_upload_bytes BIGINT UNSIGNED NOT NULL DEFAULT 104857600')
        self.upgrade()
        self.assertEqual(self.query('SELECT max_upload_bytes FROM doc_user_quota WHERE user_id=2'), '0')

    def test_document_incomplete_baseline_and_restrictions_block_without_writes(self):
        cases = [
            'DROP TABLE doc_user_quota',
            'ALTER TABLE doc_user_quota DROP COLUMN update_by',
            'ALTER TABLE doc_user_quota DROP COLUMN max_upload_bytes',
            'ALTER TABLE doc_user_quota MODIFY max_upload_bytes INT NOT NULL DEFAULT 104857600',
            'ALTER TABLE doc_user_quota ENGINE=MyISAM',
            'UPDATE doc_user_quota SET max_upload_bytes=-1 WHERE user_id=1',
            'UPDATE doc_user_quota SET quota_bytes=0 WHERE user_id=1',
            'ALTER TABLE doc_user_quota ADD CONSTRAINT custom_quota_check CHECK (max_upload_bytes>=0)',
            "CREATE TRIGGER custom_quota_insert BEFORE INSERT ON doc_user_quota FOR EACH ROW SET NEW.max_upload_bytes=104857600",
            "CREATE TRIGGER custom_quota_update BEFORE UPDATE ON doc_user_quota FOR EACH ROW SET NEW.max_upload_bytes=104857600",
        ]
        for change in cases:
            with self.subTest(change=change):
                self.setUp()
                self.query(change)
                before = self.snapshot()
                result = self.execute(baseline.SQL.read_text())
                self.assertNotEqual(result.returncode, 0)
                self.assertIn('BLOCKED', result.stdout)
                self.assertEqual(self.snapshot(), before, 'blocked document preflight must not write menus or quotas')

    def test_verification_handles_missing_document_baseline_read_only(self):
        self.upgrade()
        self.query('DROP TABLE doc_user_quota')
        before = self.snapshot()
        output = self.query(baseline.VERIFY.read_text())
        self.assertIn('CHECK_REQUIRED', output)
        self.assertIn('完整 v4.4.1 文档基线缺失', output)
        self.assertEqual(self.snapshot(), before)


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--runtime-root', required=True, type=Path)
    args, remaining = parser.parse_known_args()
    UpgradeTest.runtime = args.runtime_root
    unittest.main(argv=[__file__] + remaining, verbosity=2)
