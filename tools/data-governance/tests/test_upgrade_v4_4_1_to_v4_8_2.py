#!/usr/bin/env python3
"""Opt-in real MySQL migration tests, strictly on the owned loopback test runtime.

Usage: python3 tools/data-governance/tests/test_upgrade_v4_4_1_to_v4_8_2.py \
  --runtime-root /absolute/path/to/rynew-runtime/data-governance
No production database is read or changed. Each test creates/drops its own UUID schema.
"""
import argparse
import json
from pathlib import Path
import re
import subprocess
import unittest
import uuid

ROOT = Path(__file__).resolve().parents[3]
SQL = ROOT / 'WDF100.0/sql/data_governance_upgrade_20260916_v4_4_1_to_v4_8_2_all.sql'
ORIGINAL = ROOT / 'WDF100.0/sql/data_governance_upgrade_20260909_v4_5_0.sql'
VERIFY = ROOT / 'WDF100.0/sql/data_governance_verify_20260916_v4_8_2.sql'
TABLES = ('sys_menu', 'sys_role', 'sys_role_menu', 'sup_upgrade_untouched')


class UpgradeTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        runtime = cls.runtime.resolve()
        credentials = json.loads((runtime / 'private/mysql-credentials.json').read_text())
        assert credentials['host'] == '127.0.0.1' and int(credentials['port']) == 13306
        cls.command = ['/usr/local/mysql/bin/mysql', '--defaults-extra-file=' + str(runtime / 'private/mysql-client.cnf'),
                       '--protocol=TCP', '--host=127.0.0.1', '--port=13306', '--default-character-set=utf8mb4',
                       '--batch', '--skip-column-names']
        probe = subprocess.run(cls.command, input='SELECT @@datadir, @@port;', text=True, capture_output=True, check=True)
        datadir, port = probe.stdout.strip().split('\t')
        assert Path(datadir).resolve() == (runtime / 'mysql/data').resolve() and port == '13306'
        source = (ROOT / 'WDF100.0/sql/ry_20260320.sql').read_text(encoding='utf-8-sig')
        cls.schema = '\n'.join(re.search(r'(?ims)^create table ' + name + r'\s*\(.*?^\)[^;]*;', source).group(0)
                               for name in ('sys_menu', 'sys_role', 'sys_role_menu'))

    def execute(self, sql, database=True, force=False):
        command = self.command + (['--force'] if force else []) + ([self.database] if database else [])
        return subprocess.run(command, input=sql, text=True, capture_output=True, timeout=30)

    def query(self, sql, database=True):
        result = self.execute(sql, database)
        self.assertEqual(result.returncode, 0, result.stderr)
        return result.stdout.strip()

    def setUp(self):
        self.database = 'rynew_gov_upgrade_test_' + uuid.uuid4().hex
        self.assertEqual(self.query("SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name='" + self.database + "';", False), '0')
        self.query('CREATE DATABASE ' + self.database + ' CHARACTER SET utf8mb4;', False)
        self.addCleanup(self.drop_owned_database, self.database)
        self.query(self.schema + """
          INSERT INTO sys_role(role_id,role_name,role_key,role_sort,status,del_flag) VALUES(1,'test admin','admin',1,'0','0'),(2,'test common','common',2,'0','0');
          INSERT INTO sys_menu(menu_id,menu_name,parent_id,path,menu_type) VALUES(999,'untouched',0,'unrelated','M');
          INSERT INTO sys_role_menu VALUES(2,999);
          CREATE TABLE sup_upgrade_untouched(id INT PRIMARY KEY, payload VARCHAR(200)) ENGINE=InnoDB;
          INSERT INTO sup_upgrade_untouched VALUES(1,'existing business sentinel');
        """)
        self.initial = self.snapshot()

    def drop_owned_database(self, database):
        self.assertRegex(database, r'^rynew_gov_upgrade_test_[0-9a-f]{32}$')
        self.query('DROP DATABASE ' + database, False)

    def snapshot(self):
        return {table: self.query('SELECT * FROM ' + table + ' ORDER BY 1,2;') for table in TABLES}

    def upgrade(self):
        result = self.execute(SQL.read_text())
        self.assertEqual(result.returncode, 0, result.stderr + '\n' + result.stdout)
        self.assertIn('UPGRADE_OK', result.stdout)
        self.assertEqual(self.query('SELECT COUNT(*) FROM sys_menu'), '7')
        self.assertEqual(self.query('SELECT COUNT(*) FROM sys_role_menu WHERE role_id=1'), '6')
        self.assertEqual(self.query('SELECT COUNT(*) FROM sys_role_menu WHERE role_id=2'), '1')
        self.assertEqual(self.query('SELECT * FROM sup_upgrade_untouched ORDER BY 1,2'), self.initial['sup_upgrade_untouched'])
        self.assertEqual(self.query('SELECT * FROM sys_role ORDER BY 1,2'), self.initial['sys_role'])
        self.assertEqual(self.query('SELECT menu_name,path FROM sys_menu WHERE menu_id=999'), 'untouched\tunrelated')
        before_verify = self.snapshot()
        verification = self.query(VERIFY.read_text())
        self.assertNotIn('CHECK_REQUIRED', verification)
        self.assertEqual(self.snapshot(), before_verify, 'verification must be read-only')

    def test_fresh_and_repeat_are_identical(self):
        self.upgrade()
        snapshot = self.snapshot()
        self.upgrade()
        self.assertEqual(self.snapshot(), snapshot)

    def test_original_upgrade_already_applied_is_noop(self):
        self.query(ORIGINAL.read_text())
        snapshot = self.snapshot()
        self.upgrade()
        self.assertEqual(self.snapshot(), snapshot)

    def test_partial_valid_upgrade_is_completed(self):
        self.query("INSERT INTO sys_menu(menu_name,parent_id,path,route_name,menu_type,is_frame) VALUES('kept custom title',0,'governance','DataGovernance','M',1)")
        self.upgrade()
        self.assertEqual(self.query("SELECT menu_name FROM sys_menu WHERE path='governance'"), 'kept custom title')

    def test_custom_children_hidden_state_and_role_grants_are_preserved(self):
        self.upgrade()
        self.query("""SET @w=(SELECT menu_id FROM sys_menu WHERE component='governance/workspace/index');
          UPDATE sys_menu SET status='1',visible='1' WHERE menu_id=@w;
          INSERT INTO sys_menu(menu_name,parent_id,path,menu_type,perms) VALUES('custom',@w,'#','F','governance:custom:danger');
          INSERT INTO sys_role_menu SELECT 2,menu_id FROM sys_menu WHERE menu_id=@w;""")
        snapshot = self.snapshot()
        result = self.execute(SQL.read_text())
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(self.snapshot(), snapshot)
        self.assertIn('PRESERVED_HIDDEN_OR_DISABLED', self.query(VERIFY.read_text()))
        self.assertEqual(self.snapshot(), snapshot)
        self.assertEqual(self.query("SELECT COUNT(*) FROM sys_role_menu rm JOIN sys_menu m ON m.menu_id=rm.menu_id WHERE rm.role_id=1 AND m.perms='governance:custom:danger'"), '0')

    def test_identity_conflicts_abort_without_permanent_changes(self):
        cases = [
            "UPDATE sys_menu SET component='wrong/component' WHERE path='workspace'",
            "INSERT INTO sys_menu(menu_name,parent_id,path,menu_type) VALUES('duplicate',0,'governance','M')",
            "INSERT INTO sys_menu(menu_name,parent_id,path,menu_type,perms) VALUES('wrong parent',999,'#','F','governance:flow:edit')",
            "UPDATE sys_menu SET parent_id=999 WHERE perms='governance:project:add'",
            "UPDATE sys_menu SET menu_type=NULL WHERE path='governance'",
            "UPDATE sys_menu SET route_name='DataGovernanceWorkspace' WHERE menu_id=999",
            "UPDATE sys_role SET role_key='unexpected' WHERE role_id=1",
            "UPDATE sys_role SET status='1' WHERE role_id=1",
            "ALTER TABLE sys_role_menu ENGINE=MyISAM",
            "ALTER TABLE sys_menu DROP COLUMN route_name",
            "INSERT INTO sys_menu(menu_name,parent_id,path,menu_type,perms) SELECT 'duplicate button',parent_id,'#','F',perms FROM sys_menu WHERE perms='governance:flow:test'",
        ]
        self.upgrade()
        for change in cases:
            with self.subTest(change=change):
                # Each case owns a new schema; cleanup captures its exact name.
                self.setUp()
                self.upgrade()
                self.query(change)
                before = self.snapshot()
                result = self.execute(SQL.read_text())
                self.assertNotEqual(result.returncode, 0)
                self.assertIn('BLOCKED', result.stdout)
                self.assertEqual(self.snapshot(), before)

    def test_forced_precheck_failure_still_does_not_write(self):
        self.query("UPDATE sys_role SET role_key='not-admin' WHERE role_id=1")
        before = self.snapshot()
        result = self.execute(SQL.read_text(), force=True)
        self.assertIn('UPGRADE_BLOCKED', result.stdout)
        self.assertIn('Duplicate entry', result.stderr)
        self.assertEqual(self.snapshot(), before)

    def test_failed_dml_rolls_back_created_menus(self):
        self.query("CREATE TRIGGER reject_test_grants BEFORE INSERT ON sys_role_menu FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='deliberate test failure'")
        before = self.snapshot()
        result = self.execute(SQL.read_text())
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('deliberate test failure', result.stderr)
        self.assertEqual(self.snapshot(), before)


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--runtime-root', required=True, type=Path)
    args, remaining = parser.parse_known_args()
    UpgradeTest.runtime = args.runtime_root
    unittest.main(argv=[__file__] + remaining, verbosity=2)
