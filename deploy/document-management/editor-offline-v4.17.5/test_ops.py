"""Smoke test the bundled command line against an isolated, temporary offline site."""
import gzip
import hashlib
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
from pathlib import Path
import shutil
import stat
import subprocess
import sys
import tempfile
import threading
import unittest
import zipfile


def write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(data.encode() if isinstance(data,str) else data)


def app_jar(path, marker):
    path.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(path,'w') as archive:
        archive.writestr('BOOT-INF/classes/application.yml','app: test')
        archive.writestr('BOOT-INF/lib/wjdatafusion-manage-3.9.1.jar',marker)


class Health(BaseHTTPRequestHandler):
    def do_GET(self):
        self.send_response(200)
        self.end_headers()
        self.wfile.write(b'{"code":200}' if self.path=='/app' else b'true')

    def log_message(self,*args):
        pass


class OfflineUpgradeTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.server=ThreadingHTTPServer(('127.0.0.1',0),Health)
        thread=threading.Thread(target=cls.server.serve_forever, daemon=True)
        thread.start()
        cls.addClassCleanup(cls.server.server_close)
        cls.addClassCleanup(cls.server.shutdown)

    def setUp(self):
        tmp=tempfile.TemporaryDirectory()
        self.addCleanup(tmp.cleanup)
        self.tmp=Path(tmp.name).resolve()
        self.kit=self.tmp/'kit'
        self.cli=self.kit/'bin/ops.py'
        self.cli.parent.mkdir(parents=True)
        shutil.copy2(Path(__file__).with_name('ops.py'),self.cli)
        self.runtime=self.tmp/'runtime'
        self.jar=self.runtime/'app/rynew.jar'
        self.dist=self.runtime/'www/dist'
        self.env=self.runtime/'app/document.env'
        self.nginx=self.runtime/'conf/site.conf'
        self.candidate=self.kit/'config/nginx.conf'
        self.backups=self.runtime/'backups'
        self.backups.mkdir(mode=0o700,parents=True)
        self.original_env='# Existing production configuration\nexport OTHER_SECRET_KEY=keep-me\nDOCUMENT_MANAGEMENT_ONLY_OFFICE_SERVER_URL=http://old:8082\n'
        write(self.env,self.original_env)
        self.env.chmod(0o600)
        write(self.nginx,'location /prod-api/ { }')
        self.before_inode=self.nginx.stat().st_ino
        write(self.candidate,'location /onlyoffice/ { }')
        app_jar(self.jar,'previous')
        app_jar(self.kit/'payload/wjdatafusion-admin.jar','candidate')
        self.oldjar=self.jar.read_bytes()
        for path,data in {'index.html':'old index','index.html.gz':gzip.compress(b'old index'),
                          'favicon.png':'old icon','old-hash.js':'old controller'}.items():
            write(self.dist/path,data)
        for path,data in {'index.html':'new index','index.html.gz':gzip.compress(b'new index'),
                          'favicon.png':'new icon','assets/new-hash.js':'new controller'}.items():
            write(self.kit/'payload/dist'/path,data)
        self.manifest()
        self.helper=self.runtime/'control.py'
        write(self.helper,'''import json, pathlib, sys
root=pathlib.Path(__file__).parent
action=sys.argv[1]
if action=='nginx_test' and 'INVALID' in (root/'conf/site.conf').read_text():
    sys.exit(1)
if action=='start' and (root/'fail_next_start').exists():
    (root/'fail_next_start').unlink()
    sys.exit(1)
with (root/'calls').open('a') as f: f.write(action+'\\n')
''')
        base='http://127.0.0.1:'+str(self.server.server_port)
        self.site=self.tmp/'site.json'
        self.cfg={'jar':str(self.jar),'dist':str(self.dist),'env_file':str(self.env),
                  'nginx_conf':str(self.nginx),'nginx_candidate':str(self.candidate),
                  'backup_root':str(self.backups),'backend_health_url':base+'/app',
                  'editor_health_url':base+'/healthcheck','portal_editor_health_url':base+'/onlyoffice/healthcheck',
                  'editor_server_url':'http://127.0.0.1:8082',
                  'commands':{k:[sys.executable,str(self.helper),k] for k in ('start','stop','nginx_reload','nginx_test')}}

    def manifest(self):
        lines=[]
        for p in sorted(self.kit.rglob('*')):
            if p.is_file() and p.name!='SHA256SUMS':
                lines.append(hashlib.sha256(p.read_bytes()).hexdigest()+'  '+p.relative_to(self.kit).as_posix())
        write(self.kit/'SHA256SUMS','\n'.join(lines)+'\n')

    def invoke(self,cmd,*args,ok=True):
        write(self.site,json.dumps(self.cfg))
        result=subprocess.run([sys.executable,str(self.cli),cmd,'--site',str(self.site),*args],capture_output=True,text=True)
        self.assertEqual(result.returncode==0,ok,result.stdout+result.stderr)

    def check_restored(self):
        self.assertEqual(self.jar.read_bytes(),self.oldjar)
        self.assertEqual(self.env.read_text(),self.original_env)
        self.assertEqual(stat.S_IMODE(self.env.stat().st_mode),0o600)
        self.assertEqual(self.nginx.read_text(),'location /prod-api/ { }')
        self.assertEqual(self.nginx.stat().st_ino,self.before_inode)
        self.assertEqual((self.dist/'index.html').read_text(),'old index')
        self.assertEqual(gzip.decompress((self.dist/'index.html.gz').read_bytes()),b'old index')
        self.assertEqual((self.dist/'favicon.png').read_text(),'old icon')
        self.assertEqual((self.dist/'old-hash.js').read_text(),'old controller')

    def test_upgrade_and_repeat_rollback(self):
        self.invoke('preflight')
        self.check_restored()
        self.invoke('install',ok=False)
        self.assertEqual(list(self.backups.iterdir()),[])
        self.invoke('install','--maintenance-window')
        self.assertEqual(self.jar.read_bytes(),(self.kit/'payload/wjdatafusion-admin.jar').read_bytes())
        self.assertEqual((self.dist/'index.html').read_text(),'new index')
        self.assertEqual(gzip.decompress((self.dist/'index.html.gz').read_bytes()),b'new index')
        self.assertEqual(self.nginx.stat().st_ino,self.before_inode)
        self.assertIn('OTHER_SECRET_KEY=keep-me',self.env.read_text())
        self.assertIn('BROWSER_PATH=/onlyoffice',self.env.read_text())
        self.assertIn('PLUGINS_ENABLED=false',self.env.read_text())
        self.assertNotIn('http://old:8082',self.env.read_text())
        b=next(self.backups.iterdir())
        self.invoke('rollback','--backup',str(b),'--maintenance-window')
        calls=(self.runtime/'calls').read_text()
        self.invoke('rollback','--backup',str(b),'--maintenance-window')
        self.assertEqual(calls,(self.runtime/'calls').read_text())
        self.check_restored()

    def test_failed_start_restores_original_release_and_environment(self):
        write(self.runtime/'fail_next_start','once')
        self.invoke('install','--maintenance-window',ok=False)
        self.check_restored()
        state=json.loads((next(self.backups.iterdir())/'state.json').read_text())
        self.assertEqual(state['stage'],'restored')

    def test_invalid_nginx_restores_file_without_stopping_backend(self):
        write(self.candidate,'INVALID')
        self.manifest()
        self.invoke('install','--maintenance-window',ok=False)
        self.check_restored()
        self.assertNotIn('stop',(self.runtime/'calls').read_text())

    def test_drift_and_invalid_payload_do_not_mutate(self):
        write(self.kit/'payload/dist/assets/new-hash.js','tampered')
        self.invoke('preflight',ok=False)
        self.manifest()
        self.cfg['previous_sha256']={'jar':'0'*64}
        self.invoke('install','--maintenance-window',ok=False)
        self.assertEqual(list(self.backups.iterdir()),[])
        self.check_restored()

    def test_overlapping_and_symlinked_paths_rejected(self):
        self.cfg['backup_root']=str(self.dist)
        self.invoke('install','--maintenance-window',ok=False)
        self.cfg['backup_root']=str(self.backups)
        (self.dist/'assets').symlink_to(self.runtime/'app',target_is_directory=True)
        self.invoke('install','--maintenance-window',ok=False)
        self.assertEqual(list(self.backups.iterdir()),[])
        self.check_restored()

    def test_roll_back_only_the_recorded_release(self):
        self.invoke('install','--maintenance-window')
        b=next(self.backups.iterdir())
        write(self.env,self.env.read_text()+'SITE_EDIT=preserve\n')
        calls=(self.runtime/'calls').read_text()
        self.invoke('rollback','--backup',str(b),'--maintenance-window',ok=False)
        self.assertEqual(calls,(self.runtime/'calls').read_text())
        self.assertIn('SITE_EDIT=preserve',self.env.read_text())

    def test_interrupted_install_with_mixed_old_and_new_files(self):
        self.invoke('install','--maintenance-window')
        b=next(self.backups.iterdir())
        state=json.loads((b/'state.json').read_text())
        state['stage']='installing'
        write(b/'state.json',json.dumps(state))
        shutil.copy2(b/'previous.env',self.env)
        shutil.copy2(b/'dist/index.html',self.dist/'index.html')
        self.invoke('rollback','--backup',str(b),'--maintenance-window')
        self.check_restored()


if __name__=='__main__':
    unittest.main()
