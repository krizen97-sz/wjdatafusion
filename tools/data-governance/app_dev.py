#!/usr/bin/env python3
"""Run the real RYNEW application against the isolated governance acceptance services."""
import argparse
import json
import os
from pathlib import Path
import secrets
import signal
import socket
import subprocess
import time
import urllib.request

OWNER = "rynew-data-governance-app"

def read(path):
    if path.stat().st_mode & 0o077:
        raise RuntimeError("Private configuration permissions must be 600")
    return json.loads(path.read_text())

def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
    fd=os.open(path,os.O_CREAT|os.O_TRUNC|os.O_WRONLY,0o600)
    with os.fdopen(fd,"w") as f:json.dump(value,f,indent=2)
    path.chmod(0o600)

def identity(pid):
    if not isinstance(pid,int) or pid<=1:return None
    r=subprocess.run(["/bin/ps","-p",str(pid),"-o","lstart=","-o","command="],capture_output=True,text=True)
    return r.stdout.strip() if r.returncode==0 else None

def main():
    p=argparse.ArgumentParser();p.add_argument("action",choices=["prepare","start","stop","status"]);p.add_argument("--runtime",type=Path,required=True);p.add_argument("--app-runtime",type=Path,required=True);a=p.parse_args()
    runtime=a.runtime.resolve();app=a.app_runtime.resolve();root=Path(__file__).resolve().parents[2]
    marker=json.loads((runtime/"runtime.json").read_text())
    if marker.get("owner")!="rynew-data-governance-runtime" or marker.get("runtimeRoot")!=str(runtime):raise RuntimeError("Runtime ownership mismatch")
    app.mkdir(parents=True,exist_ok=True);app.chmod(0o700);private=app/"private";private.mkdir(exist_ok=True);private.chmod(0o700)
    owner_file=private/"app-owner.json"
    if not owner_file.exists():
        if a.action!="prepare":raise RuntimeError("Run prepare first")
        db=read(private/"database-bootstrap.json")
        if db.get("database")!="rynew_governance_dev" or db.get("stage")!="initialized":raise RuntimeError("Initialize the dedicated database first")
        write(owner_file,{"owner":OWNER,"root":str(app)})
    owner=read(owner_file)
    if owner!={"owner":OWNER,"root":str(app)}:raise RuntimeError("App ownership mismatch")
    config=private/"application-governance.json"
    state=private/"app-process.json"
    if a.action=="prepare":
        # JSON is YAML-compatible and accepted as Spring external .yml configuration below.
        mysql=read(runtime/"private/mysql-credentials.json");redis=read(runtime/"private/redis-credentials.json")
        binding=json.loads((app/"nifi-binding.json").read_text())
        if mysql["host"]!="127.0.0.1" or mysql["port"]!=13306 or redis["host"]!="127.0.0.1" or redis["port"]!=16379:raise RuntimeError("Dedicated service endpoint mismatch")
        keys=read(private/"app-secrets.json") if (private/"app-secrets.json").exists() else {"jwt":secrets.token_urlsafe(48),"support":secrets.token_urlsafe(48)}
        write(private/"app-secrets.json",keys)
        doc={
          "server":{"port":8083,"address":"127.0.0.1"},
          "ruoyi":{"profile":str(app/"uploads")},"log":{"path":str(app/"logs")},
          "logging":{"level":{"com.hm":"info","org.springframework":"warn"}},
          "spring":{"datasource":{"druid":{"master":{"url":"jdbc:mysql://127.0.0.1:13306/rynew_governance_dev?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false","username":mysql["username"],"password":mysql["password"]},"slave":{"enabled":False},"initialSize":1,"minIdle":1,"maxActive":10,"statViewServlet":{"enabled":False}}},"data":{"redis":{"host":"127.0.0.1","port":16379,"password":redis["password"],"database":0}},"devtools":{"restart":{"enabled":False}}},
          "token":{"secret":keys["jwt"]},"support":{"credential":{"key":keys["support"]}},
          "data-governance":{"enabled":True,"storage-dir":str(app/"state"),"nifi":{"base-url":binding["baseUrl"],"root-group-id":binding["rootGroupId"],"credentials-file":binding["credentialsFile"]}}
        }
        config=private/"application-governance.yml";write(config,doc)
        print(json.dumps({"prepared":True,"configFile":str(config),"database":"rynew_governance_dev","port":8083}));return
    record=read(state) if state.exists() else None
    current=identity(record.get("pid")) if record else None
    if current and (current!=record.get("identity") or str(app) not in current or OWNER not in current):raise RuntimeError("PID ownership mismatch; refusing to signal process")
    if a.action=="status":print(json.dumps({"running":bool(current),"pid":record.get("pid") if current else None}));return
    if a.action=="stop":
        if current:
            os.kill(record["pid"],signal.SIGTERM)
            for _ in range(100):
                if not identity(record["pid"]):break
                time.sleep(0.1)
            if identity(record["pid"]):raise RuntimeError("Application still stopping; no forced termination performed")
        print(json.dumps({"stopped":True}));return
    if current:print(json.dumps({"alreadyRunning":True,"pid":record["pid"]}));return
    config=private/"application-governance.yml"
    if not config.exists():raise RuntimeError("Run prepare first")
    with socket.socket() as sock:
        sock.setsockopt(socket.SOL_SOCKET,socket.SO_REUSEADDR,1);sock.bind(("127.0.0.1",8083))
    jar=root/"WDF100.0/wjdatafusion-admin/target/wjdatafusion-admin.jar"
    if not jar.exists():raise RuntimeError("Build the application in this worktree first")
    argv=["/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home/bin/java","-Xms256m","-Xmx1g","-Dgovernance.owner="+OWNER,"-jar",str(jar),"--spring.config.additional-location=file:"+str(config)]
    log=private/"application-console.log"
    with log.open("ab") as f:proc=subprocess.Popen(argv,cwd=str(root/"WDF100.0"),stdout=f,stderr=f,start_new_session=True)
    log.chmod(0o600);time.sleep(0.25)
    ident=identity(proc.pid)
    if not ident or str(app) not in ident or OWNER not in ident:raise RuntimeError("App launch did not establish expected ownership")
    write(state,{"pid":proc.pid,"identity":ident,"port":8083,"sourceRoot":str(root)})
    print(json.dumps({"started":True,"pid":proc.pid,"port":8083,"logFile":str(log)}))

if __name__=="__main__":main()
