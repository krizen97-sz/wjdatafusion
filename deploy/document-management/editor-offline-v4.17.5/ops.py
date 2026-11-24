#!/usr/bin/env python3
"""Offline RYNEW upgrade for existing Linux installations (Python 3.8+)."""
import argparse
import fcntl
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import stat
import subprocess
import sys
import tempfile
import time
import urllib.error
import urllib.request
from urllib.parse import urlsplit
import uuid
import zipfile


ROOT = Path(__file__).resolve().parents[1]
PAYLOAD = ROOT / 'payload'
ENTRY = ('index.html.gz', 'index.html')
LIVE_FILES = ('jar', 'env_file', 'nginx_conf')
BACKUP_NAMES = {'jar': 'previous.jar', 'env_file': 'previous.env', 'nginx_conf': 'previous.nginx.conf'}


def require(condition, reason):
    if not condition:
        raise ValueError(reason)


def digest(path):
    h = hashlib.sha256()
    with path.open('rb') as f:
        for block in iter(lambda: f.read(1024 * 1024), b''):
            h.update(block)
    return h.hexdigest()


def safe_path(value):
    p = Path(value)
    require(p.is_absolute() and p.parent != p and p.resolve() == p,
            'Use a canonical absolute path without symbolic links: ' + str(p))
    return p


def separate(paths):
    for i, a in enumerate(paths):
        for b in paths[i+1:]:
            require(a != b and a not in b.parents and b not in a.parents,
                    'Paths overlap: {} and {}'.format(a, b))


def checked_url(value, allow_path=True):
    u = urlsplit(value)
    require(u.scheme in ('http', 'https') and u.hostname and not u.username and not u.password
            and not u.query and not u.fragment and (allow_path or not u.path.rstrip('/'))
            and bool(re.fullmatch(r'[A-Za-z0-9_.:/\[\]-]+', value)),
            'Use a literal HTTP(S) site URL without credentials or query parameters')
    return value


def site_config(path):
    config = json.loads(path.read_text())
    for key in (*LIVE_FILES, 'dist', 'backup_root'):
        config[key] = safe_path(config[key])
    # Human-reviewed complete config, either from the kit or prepared separately.
    config['nginx_candidate'] = safe_path(config['nginx_candidate'])
    separate([ROOT, *[config[k] for k in LIVE_FILES], config['dist'], config['backup_root']])
    separate([config['nginx_candidate'], *[config[k] for k in LIVE_FILES], config['dist'],config['backup_root']])
    require(config['jar'].suffix == '.jar' and config['dist'].is_dir()
            and (config['dist'] / 'index.html').is_file(),
            'Choose the existing application JAR and the deployed Vue dist directory')
    for key in LIVE_FILES:
        msg = ('Prepare the site environment file with mode 0600 and configure the existing startup service to load it'
               if key == 'env_file' else 'Cannot read/write ' + key)
        require(config[key].is_file() and os.access(config[key], os.R_OK | os.W_OK), msg)
    for k in ('jar', 'env_file'):
        require(os.access(config[k].parent, os.W_OK), 'Cannot atomically replace ' + k)
    require(os.access(config['dist'], os.W_OK), 'Cannot update frontend directory')
    b = config['backup_root']
    require(b.is_dir() and b.stat().st_uid == os.geteuid() and b.stat().st_mode & 0o077 == 0
            and os.access(b,os.W_OK), 'backup_root must be an owned writable private directory (0700)')
    for key in ('stop', 'start', 'nginx_test', 'nginx_reload'):
        cmd = config['commands'][key]
        require(isinstance(cmd,list) and cmd and all(isinstance(a,str) and a for a in cmd)
                and Path(cmd[0]).is_absolute() and os.access(cmd[0],os.X_OK) and Path(cmd[0]).is_file(),
                'Invalid executable/argument list: commands.' + key)
    for key in ('backend_health_url', 'editor_health_url', 'portal_editor_health_url', 'editor_server_url'):
        config[key] = checked_url(config[key])
    require(re.fullmatch(r'(/[A-Za-z0-9_-]+)+',config.setdefault('browser_path','/onlyoffice')),
            'browser_path must be a same-origin path such as /onlyoffice')
    require(isinstance(config.setdefault('plugins_enabled',False), bool), 'plugins_enabled must be true or false')
    return config


def inventory():
    files = {}
    for line in (ROOT / 'SHA256SUMS').read_text().splitlines():
        match = re.fullmatch(r'([0-9a-f]{64})  (.+)',line)
        require(match, 'Invalid SHA256SUMS record')
        expected, name = match.groups()
        name = Path(name)
        require(not name.is_absolute() and '..' not in name.parts and name not in files, 'Invalid kit path')
        f = safe_path(ROOT / name)
        require(f.is_file() and digest(f) == expected, 'Checksum mismatch: ' + str(name))
        files[name] = expected
    jar = Path('payload/wjdatafusion-admin.jar')
    require(jar in files and Path('bin/ops.py') in files and Path('payload/dist/index.html') in files,
            'Missing required payload checksums')
    with zipfile.ZipFile(ROOT / jar) as z:
        require('BOOT-INF/classes/application.yml' in z.namelist() and any(
            n.startswith('BOOT-INF/lib/wjdatafusion-manage-') for n in z.namelist()),
            'Candidate is not a RYNEW application JAR')
    dist = PAYLOAD / 'dist'
    for p in dist.rglob('*'):
        require(not p.is_symlink() and (p.is_dir() or (p.is_file() and p.relative_to(ROOT) in files)),
                'Unexpected frontend file: '+str(p))
    return sorted(p.relative_to('payload/dist').as_posix() for p in files if p.parts[:2] == ('payload','dist'))


def sync_bytes(path, data, mode=0o600):
    # Temporary files always start private; publish contents only after a complete write.
    fd, filename = tempfile.mkstemp(dir=path.parent)
    try:
        with os.fdopen(fd,'wb') as f:
            f.write(data)
            f.flush()
            os.fsync(f.fileno())
        set_attrs(Path(filename), path if path.exists() else None, mode)
        os.replace(filename,path)
    finally:
        if os.path.exists(filename):
            os.unlink(filename)


def set_attrs(dest, reference, default_mode):
    if reference is not None:
        old = reference.stat()
        if os.geteuid() == 0:
            os.chown(dest, old.st_uid, old.st_gid)
        elif dest.stat().st_uid != old.st_uid or dest.stat().st_gid != old.st_gid:
            raise ValueError('Run as owner or root to preserve permissions: '+str(reference))
        default_mode = stat.S_IMODE(old.st_mode)
    os.chmod(dest,default_mode)


def publish_file(src, dest, inplace=False, default_mode=0o644):
    missing = []
    for parent in dest.parents:
        if parent.exists():
            break
        missing.append(parent)
    for parent in reversed(missing):
        parent.mkdir()
        parent.chmod(0o755)
    if inplace:
        with src.open('rb') as source, dest.open('wb') as target:
            shutil.copyfileobj(source,target)
            target.flush()
            os.fsync(target.fileno())
        return
    fd, staged = tempfile.mkstemp(dir=dest.parent)
    os.close(fd)
    try:
        shutil.copyfile(src,staged)
        set_attrs(Path(staged),dest if dest.is_file() else None,default_mode)
        os.replace(staged,dest)
    finally:
        if os.path.exists(staged):
            os.unlink(staged)


def next_env(config):
    values = {'SERVER_URL':config['editor_server_url'],'BROWSER_PATH':config['browser_path'],
              'PLUGINS_ENABLED':str(config['plugins_enabled']).lower()}
    text = config['env_file'].read_bytes().decode()
    prefix='DOCUMENT_MANAGEMENT_ONLY_OFFICE_'
    # Shell env files may have comments, blank lines or 'export KEY=...' entries.
    replaced = set()
    for k, v in values.items():
        key = prefix+k
        pattern = r'(?m)^([ \t]*(?:export[ \t]+)?'+key+r'=)[^\r\n]*'
        text,count = re.subn(pattern,lambda m:m[1]+v,text)
        if not count:
            replaced.add(key+'='+v)
    if replaced:
        text = text.rstrip('\r\n') + '\n' + '\n'.join(sorted(replaced)) + '\n'
    return text.encode()


def run(config,key):
    subprocess.run(config['commands'][key],check=True,timeout=120,stdin=subprocess.DEVNULL)


def health(config,key,wait=0):
    deadline=time.monotonic()+wait
    opener=urllib.request.build_opener(urllib.request.ProxyHandler({}))
    while True:
        try:
            with opener.open(config[key],timeout=4) as r:
                body=json.loads(r.read(512*1024))
                valid=(isinstance(body,dict) and body.get('code')==200) if key=='backend_health_url' else body is True
                require(valid, 'Unexpected response')
                return
        except (ValueError,OSError,urllib.error.URLError):
            if time.monotonic()>=deadline:
                raise RuntimeError('Health check failed: ' + key)
            time.sleep(1)


def frontend_target(c,name):
    target=safe_path(c['dist']/name)
    require(c['dist'] in target.parents and (not target.exists() or target.is_file()),
            'Unexpected frontend target: '+str(target))
    return target


def preflight(c,files):
    require(c['nginx_candidate'].is_file(), 'Supply a reviewed complete Nginx config')
    for key in ('jar','env_file','nginx_conf'):
        if c.get('previous_sha256',{}).get(key):
            require(digest(c[key])==c['previous_sha256'][key], 'Previous release fingerprint mismatch: '+key)
    with zipfile.ZipFile(c['jar']) as z:
        require(any(n.startswith('BOOT-INF/lib/wjdatafusion-manage-') for n in z.namelist()),
                'Current JAR does not contain the RYNEW business module')
    for name in set(files)|set(ENTRY):
        frontend_target(c,name)
    next_env(c)
    run(c,'nginx_test')
    health(c,'backend_health_url')
    health(c,'editor_health_url')
    # Each temp/backup copy can land on a different mount.
    dist_bytes=sum(p.stat().st_size for p in (PAYLOAD/'dist').rglob('*') if p.is_file())
    touched=sum(frontend_target(c,n).stat().st_size for n in set(files)|set(ENTRY) if frontend_target(c,n).is_file())
    jar_bytes=(PAYLOAD/'wjdatafusion-admin.jar').stat().st_size
    budgets={}
    for path,needed in ((c['backup_root'],sum(c[k].stat().st_size for k in LIVE_FILES)+touched),
                        (c['jar'].parent,jar_bytes),(c['dist'],dist_bytes)):
        dev=path.stat().st_dev
        budgets[dev]=budgets.get(dev,0)+needed
        require(shutil.disk_usage(path).free>budgets[dev]+1024*1024,'Insufficient free space on '+str(path))


def site_id(c):
    return {k:str(c[k]) for k in (*LIVE_FILES,'dist')}


def state_write(backup,state,stage):
    state['stage']=stage
    sync_bytes(backup/'state.json',json.dumps(state,sort_keys=True).encode())


def backup_sources(c,files,backup):
    state={'site':site_id(c),'original':{},'target':{},'dist_original':{},'dist_target':{}}
    for k in LIVE_FILES:
        src=c[k]; dest=backup/BACKUP_NAMES[k]
        shutil.copy2(src,dest)
        state['original'][k]=digest(src)
        require(digest(dest)==state['original'][k], 'Unstable backup: '+k)
    state['target']={'jar':digest(PAYLOAD/'wjdatafusion-admin.jar'),
                     'env_file':hashlib.sha256(next_env(c)).hexdigest(),'nginx_conf':digest(c['nginx_candidate'])}
    for name in sorted(set(files)|set(ENTRY)):
        src=frontend_target(c,name)
        cand=PAYLOAD/'dist'/name
        old=digest(src) if src.exists() else None
        new=digest(cand) if cand.is_file() else None
        if old == new:
            continue
        state['dist_original'][name]=old
        state['dist_target'][name]=new
        if old:
            dest=backup/'dist'/name
            dest.parent.mkdir(parents=True,exist_ok=True)
            shutil.copy2(src,dest)
            require(digest(dest)==old, 'Unstable frontend backup: '+name)
    state_write(backup,state,'backed-up')
    return state


def publish_dist(c,state,source,restore=False):
    desired=state['dist_original' if restore else 'dist_target']
    names=[name for name in desired if name not in ENTRY] + [name for name in ENTRY if name in desired]
    for name in names:
        path=frontend_target(c,name)
        if desired[name] is None:
            # Remove a stale compressed index. On rollback, retain new immutable
            # hashed assets for open tabs, but remove other new unversioned URLs.
            immutable_asset = re.search(r'-[a-zA-Z0-9_-]{8,}\.[a-zA-Z0-9]+(?:\.gz)?$', name)
            if path.exists() and (name in ENTRY or (restore and not immutable_asset)):
                expected = state['dist_target'][name] if restore else state['dist_original'][name]
                require(digest(path) == expected,'Static file contents changed: '+name)
                path.unlink()
            continue
        publish_file(source/name,path)
        require(digest(path)==desired[name], 'Frontend verification failed: '+name)


def restore(c,backup,state):
    for k in LIVE_FILES:
        require(digest(backup/BACKUP_NAMES[k])==state['original'][k], 'Damaged backup: '+k)
    for name,old in state['dist_original'].items():
        if old:
            require(digest(backup/'dist'/name)==old,'Damaged frontend backup: '+name)
    # Only stop scripts that safely accept an already stopped application are suitable here.
    run(c,'stop')
    for k in LIVE_FILES:
        publish_file(backup/BACKUP_NAMES[k],c[k],inplace=(k=='nginx_conf'))
        require(digest(c[k])==state['original'][k],'Restore failed: '+k)
    publish_dist(c,state,backup/'dist',restore=True)
    run(c,'nginx_test')
    run(c,'nginx_reload')
    run(c,'start')
    health(c,'backend_health_url',wait=75)
    health(c,'editor_health_url')
    state_write(backup,state,'restored')


def install(c,files):
    preflight(c,files)
    backup=c['backup_root']/('doc-editor-4175-'+time.strftime('%Y%m%d-%H%M%S')+'-'+uuid.uuid4().hex[:8])
    backup.mkdir(mode=0o700)
    state=backup_sources(c,files,backup)
    print('Verified rollback backup:',backup,flush=True)
    # Verify the candidate nginx on the live mount; reload only after syntax check.
    state_write(backup,state,'installing')
    try:
        publish_file(c['nginx_candidate'],c['nginx_conf'],inplace=True)
        run(c,'nginx_test')
    except BaseException:
        publish_file(backup/BACKUP_NAMES['nginx_conf'],c['nginx_conf'],inplace=True)
        run(c,'nginx_test')
        state_write(backup,state,'restored')
        raise
    try:
        run(c,'stop')
        require(digest(c['env_file'])==state['original']['env_file'],'Environment changed during preflight')
        sync_bytes(c['env_file'],next_env(c))
        publish_file(PAYLOAD/'wjdatafusion-admin.jar',c['jar'])
        run(c,'start')
        health(c,'backend_health_url',wait=75)
        run(c,'nginx_reload')
        health(c,'portal_editor_health_url',wait=10)
        publish_dist(c,state,PAYLOAD/'dist')
        for k in LIVE_FILES:
            require(digest(c[k])==state['target'][k], 'Unexpected final fingerprint: '+k)
        state_write(backup,state,'installed')
    except BaseException:
        print('Restoring previous release from',backup,file=sys.stderr,flush=True)
        restore(c,backup,state)
        raise
    print('Installed. Rollback backup:',backup)


def rollback(c,provided):
    b=safe_path(provided)
    require(b.parent==c['backup_root'] and re.fullmatch(r'doc-editor-4175-\d{8}-\d{6}-[0-9a-f]{8}',b.name),
            'Backup must be a specific directory created by this installer')
    state=json.loads((b/'state.json').read_text())
    require(state['site']==site_id(c),'Backup belongs to another deployment')
    if state['stage']=='restored':
        print('This release was already rolled back')
        return
    require(state['stage'] in ('installed','installing'), 'No upgrade to roll back')
    interrupted = state['stage'] == 'installing'
    for k,new in state['target'].items():
        allowed = (new,state['original'][k]) if interrupted else (new,)
        require(digest(c[k]) in allowed,'Unexpected live fingerprint; review before rollback: '+k)
    for name,new in state['dist_target'].items():
        target=frontend_target(c,name)
        allowed = (new,state['dist_original'][name]) if interrupted else (new,)
        require((digest(target) if target.exists() else None) in allowed,
                'Unexpected frontend fingerprint; review before rollback: '+name)
    restore(c,b,state)
    print('Restored previous release from',b)


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    sub=parser.add_subparsers(dest='action',required=True)
    for name in ('preflight','install','rollback'):
        p=sub.add_parser(name)
        p.add_argument('--site',required=True,type=Path,help='Reviewed site JSON (outside the kit)')
        if name!='preflight':
            p.add_argument('--maintenance-window',required=True,action='store_true',
                           help='Confirm the saved editing sessions and agreed restart window')
        if name=='rollback':
            p.add_argument('--backup',required=True)
    args=parser.parse_args()
    c=site_config(args.site)
    fd=os.open(c['backup_root'],os.O_RDONLY)
    try:
        fcntl.flock(fd,fcntl.LOCK_EX|fcntl.LOCK_NB)
        if args.action=='rollback':
            rollback(c,args.backup)
        else:
            files=inventory()
            if args.action=='install':
                install(c,files)
            else:
                preflight(c,files)
                print('Preflight passed. Confirm a maintenance window before installation.')
    finally:
        os.close(fd)


if __name__=='__main__':
    try:
        main()
    except (ValueError,KeyError,OSError,RuntimeError,subprocess.SubprocessError,zipfile.BadZipFile) as exc:
        print('Upgrade tool stopped:',exc,file=sys.stderr)
        sys.exit(1)
