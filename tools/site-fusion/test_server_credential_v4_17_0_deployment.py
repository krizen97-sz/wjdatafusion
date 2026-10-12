#!/usr/bin/env python3
"""Validate deployment checks on an owned, TCP-disabled temporary MySQL only."""
from pathlib import Path
import argparse
import re
import subprocess
import unittest
import uuid

ROOT = Path(__file__).resolve().parents[2]
MIGRATE = (ROOT / 'WDF100.0/sql/support_upgrade_20260929_server_credential_password_pool_v4_17_0.sql').read_text()
CHECK = (ROOT / 'WDF100.0/sql/support_check_20261012_server_credential_v4_16_0_to_v4_17_0.sql').read_text()

class DeployCheck(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        assert re.fullmatch(r'/tmp/rynew-v4170-pool-[a-zA-Z0-9]+/mysql.sock', cls.socket)
        cls.cmd = ['/usr/local/mysql/bin/mysql','--no-defaults','--protocol=SOCKET','--socket='+cls.socket,
                   '--user=root','--batch','--raw','--skip-column-names','--default-character-set=utf8mb4']
        result = subprocess.run(cls.cmd,input='SELECT @@datadir,@@skip_networking;',capture_output=True,text=True,check=True)
        datadir,networking=result.stdout.strip().split('\t')
        assert Path(datadir).resolve()==(Path(cls.socket).parent/'data').resolve() and networking=='1'
        def source(file): return subprocess.check_output(['git','show','cd08046:'+file],cwd=ROOT,text=True)
        support,system=source('WDF100.0/sql/support_v1.sql'),source('WDF100.0/sql/ry_20260320.sql')
        cls.tables=['sup_site','sup_server','sup_server_credential','sys_menu','sys_role_menu']
        cls.schema='\n'.join(re.search(r'(?im)^CREATE TABLE (?:IF NOT EXISTS )?'+t+r'\s*\([\s\S]*?^\)[^;]*;',system if t.startswith('sys_') else support).group(0) for t in cls.tables)
        cls.v416=source('WDF100.0/sql/support_upgrade_20260929_server_credential_scan_v4_16_0.sql')
    def execute(self, sql, database=True):
        return subprocess.run(self.cmd+([self.db] if database else []),input=sql,text=True,capture_output=True,timeout=20)
    def q(self,sql,database=True):
        r=self.execute(sql,database);self.assertEqual(r.returncode,0,r.stderr);return r.stdout
    def setUp(self):
        self.db='rynew_v4170_deploy_'+uuid.uuid4().hex
        self.q('CREATE DATABASE '+self.db+' CHARACTER SET utf8mb4;',False)
        self.addCleanup(lambda db=self.db:self.q('DROP DATABASE '+db,False))
        self.q(self.schema+"INSERT INTO sys_menu(menu_id,menu_name,parent_id,path,menu_type,perms) VALUES(2203,'servers',0,'server','C','support:server:list');"+self.v416+"""
INSERT INTO sup_site(site_id,site_name) VALUES(1,'fixture');
INSERT INTO sup_server(server_id,site_id,server_name,server_address) VALUES(1,1,'fixture','192.0.2.1');
INSERT INTO sup_server_scan_credential(site_id,credential_name,login_mode,hik_password_cipher,root_password_cipher)
VALUES(1,'pair','HIK_SU_ROOT','synthetic-hik','synthetic-root'),(1,'direct','ROOT_DIRECT','','synthetic-direct');
""")
    def snapshot(self):
        return {t:self.q('SELECT * FROM '+t+' ORDER BY 1') for t in self.tables+['sup_server_scan_credential','sup_server_scan_profile']}
    def test_pre_and_post_reports_are_read_only_and_do_not_expose_ciphers(self):
        before=self.snapshot();pre=self.q(CHECK)
        self.assertIn('NOT_APPLIED',pre);self.assertNotIn('STOP:',pre);self.assertEqual(self.snapshot(),before)
        self.q(MIGRATE);before=self.snapshot();post=self.q(CHECK)
        self.assertNotIn('STOP:',post);self.assertNotIn('NOT_APPLIED',post);self.assertEqual(self.snapshot(),before)
        for value in ['synthetic-hik','synthetic-root','synthetic-direct']:
            self.assertNotIn(value,pre+post)
        self.assertEqual(self.q('SELECT COUNT(*) FROM sup_server_scan_credential').strip(),'3')
    def test_check_reports_missing_scan_permission_without_repairing_it(self):
        self.q("DELETE FROM sys_menu WHERE perms='support:server:scan'")
        before=self.snapshot();self.assertIn('STOP: missing or incompatible v4.16 scan permission',self.q(CHECK));self.assertEqual(before,self.snapshot())
    def test_missing_profile_rejects_upgrade(self):
        self.q('DROP TABLE sup_server_scan_profile')
        before=self.q('SELECT * FROM sup_server_scan_credential ORDER BY 1')
        r=self.execute(MIGRATE);self.assertNotEqual(r.returncode,0);self.assertIn('Complete v4.16.0 scan schema required',r.stderr)
        self.assertEqual(before,self.q('SELECT * FROM sup_server_scan_credential ORDER BY 1'))
    def test_non_innodb_profile_rejects_upgrade(self):
        self.q('ALTER TABLE sup_server_scan_profile DROP FOREIGN KEY fk_server_scan_profile_server; ALTER TABLE sup_server_scan_profile ENGINE=MyISAM;')
        before=self.snapshot();r=self.execute(MIGRATE);self.assertNotEqual(r.returncode,0);self.assertIn('Complete v4.16.0 scan schema required',r.stderr);self.assertEqual(before,self.snapshot())
    def test_custom_trigger_rejects_upgrade(self):
        self.q('CREATE TRIGGER fixture_pool_trigger BEFORE INSERT ON sup_server_scan_credential FOR EACH ROW SET NEW.credential_name=NEW.credential_name')
        before=self.snapshot();r=self.execute(MIGRATE);self.assertNotEqual(r.returncode,0);self.assertIn('Review custom password pool triggers',r.stderr);self.assertEqual(before,self.snapshot())

if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--socket',required=True);args,rest=p.parse_known_args();DeployCheck.socket=args.socket
    unittest.main(argv=[__file__]+rest,verbosity=2)
