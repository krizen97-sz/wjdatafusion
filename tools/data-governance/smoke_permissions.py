#!/usr/bin/env python3
"""Seed only an owned local viewer and verify actual API authorization boundaries."""
import argparse
import json
from pathlib import Path
import re
import secrets
import ssl
import subprocess
import urllib.error
import urllib.request

def main():
    p=argparse.ArgumentParser();p.add_argument("--runtime",type=Path,required=True);p.add_argument("--app-runtime",type=Path,required=True);a=p.parse_args()
    rt=a.runtime.resolve();app=a.app_runtime.resolve()
    marker=json.loads((app/"private/database-bootstrap.json").read_text())
    if marker.get("database")!="rynew_governance_dev" or Path(marker["datadir"]).resolve()!=(rt/"mysql/data").resolve():raise RuntimeError("Dedicated database ownership required")
    mysql=["/usr/local/mysql/bin/mysql","--defaults-extra-file="+str(rt/"private/mysql-client.cnf"),"--protocol=TCP","--host=127.0.0.1","--port=13306","--batch","--skip-column-names","rynew_governance_dev"]
    def sql(s):return subprocess.run(mysql,input=s,text=True,capture_output=True,check=True).stdout.strip()
    existing=sql("SELECT user_name FROM sys_user WHERE user_id=101;")
    if existing not in ["","governance_viewer"]:raise RuntimeError("Refusing to replace unrelated local user")
    vfile=app/"private/viewer-login.json"
    viewer=json.loads(vfile.read_text()) if vfile.exists() else {"username":"governance_viewer","password":secrets.token_urlsafe(15)}
    vfile.write_text(json.dumps(viewer,indent=2));vfile.chmod(0o600)
    hashed=subprocess.run(["/usr/sbin/htpasswd","-niBC","10",viewer["username"]],input=viewer["password"]+"\n",text=True,capture_output=True,check=True).stdout.strip().split(":",1)[1]
    if not re.fullmatch(r"\$2[aby]\$\d\d\$[./A-Za-z0-9]{53}",hashed):raise RuntimeError("Invalid hash")
    sql("INSERT IGNORE INTO sys_role(role_id,role_name,role_key,role_sort,status,del_flag) VALUES(101,'治理只读验收','governance_viewer',2,'0','0');")
    sql(f"INSERT INTO sys_user(user_id,dept_id,user_name,nick_name,password,status,del_flag,create_time,pwd_update_date) VALUES(101,100,'governance_viewer','治理只读验收','{hashed}','0','0',NOW(),NOW()) ON DUPLICATE KEY UPDATE password=VALUES(password);")
    sql("INSERT IGNORE INTO sys_user_role(user_id,role_id) VALUES(101,101);")
    sql("INSERT IGNORE INTO sys_role_menu(role_id,menu_id) SELECT 101,menu_id FROM sys_menu WHERE (path='governance' AND parent_id=0) OR perms='governance:flow:list';")
    ctx=ssl.create_default_context(cafile=str(rt/"private/gateway-ca.pem"))
    def call(path,method="GET",body=None,token=None):
        headers={"Content-Type":"application/json"}
        if token:headers["Authorization"]="Bearer "+token
        req=urllib.request.Request("https://localhost:10443/prod-api"+path,data=json.dumps(body).encode() if body is not None else None,headers=headers,method=method)
        try:
            with urllib.request.urlopen(req,context=ctx,timeout=15) as r:return json.load(r)
        except urllib.error.HTTPError as e:return {"code":e.code}
    admin=json.loads((app/"private/app-login.json").read_text())
    admin_token=call("/login","POST",{**admin,"code":"","uuid":""})["token"]
    viewer_token=call("/login","POST",{**viewer,"code":"","uuid":""})["token"]
    before=call("/governance/projects",token=admin_token)["data"]
    anonymous=call("/governance/overview")
    readable=call("/governance/overview",token=viewer_token)
    denied=call("/governance/projects","POST",{"name":"forbidden-test-project"},viewer_token)
    admin_runs=json.loads((app/"evidence/app-smoke.json").read_text())["cases"]
    private_run=call("/governance/test-runs/"+admin_runs[0]["id"],token=viewer_token)
    after=call("/governance/projects",token=admin_token)["data"]
    assert anonymous["code"]==401
    assert readable["code"]==200
    assert denied["code"]==403
    assert private_run["code"]!=200 and not private_run.get("data")
    assert len(before)==len(after)
    result={"anonymousDenied":True,"viewerCanReadOverview":True,"viewerCannotCreateProject":True,"viewerCannotReadAnotherUsersRun":True,"projectCountUnchanged":True}
    (app/"evidence/permissions-smoke.json").write_text(json.dumps(result,indent=2));print(json.dumps(result))

if __name__=="__main__":main()
