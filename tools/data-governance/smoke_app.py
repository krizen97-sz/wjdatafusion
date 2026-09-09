#!/usr/bin/env python3
"""Authenticated HTTPS acceptance against the dedicated local app and real NiFi backend."""
import argparse
import json
from pathlib import Path
import ssl
import time
import urllib.error
import urllib.request

TERMINAL = {"SUCCEEDED", "EMPTY", "FAILED", "CANCELLED", "TIMED_OUT", "UNSUPPORTED", "CLEANUP_REQUIRED"}

def main():
    p=argparse.ArgumentParser();p.add_argument("--runtime",type=Path,required=True);p.add_argument("--app-runtime",type=Path,required=True);a=p.parse_args()
    runtime=a.runtime.resolve();app=a.app_runtime.resolve()
    owner=json.loads((app/"private/app-owner.json").read_text())
    if owner!={"owner":"rynew-data-governance-app","root":str(app)}:raise RuntimeError("App ownership mismatch")
    login_path=app/"private/app-login.json"
    if login_path.stat().st_mode&0o077:raise RuntimeError("Credential permissions must be 600")
    login=json.loads(login_path.read_text())
    context=ssl.create_default_context(cafile=str(runtime/"private/gateway-ca.pem"))
    base="https://localhost:10443/prod-api"
    token=None
    def request(path,method="GET",body=None):
        headers={"Content-Type":"application/json"}
        if token:headers["Authorization"]="Bearer "+token
        payload=json.dumps(body,ensure_ascii=False).encode() if body is not None else None
        req=urllib.request.Request(base+path,data=payload,headers=headers,method=method)
        try:
            with urllib.request.urlopen(req,context=context,timeout=20) as result:data=json.load(result)
        except urllib.error.HTTPError as error:
            raise RuntimeError(f"HTTP {error.code} at {path}") from None
        if data.get("code")!=200:raise RuntimeError(f"Application rejected {path}: code={data.get('code')}")
        return data
    auth=request("/login","POST",{**login,"code":"","uuid":""})
    token=auth["token"]
    info=request("/getInfo");routers=request("/getRouters")
    assert any(r.get("path")=="/governance" for r in routers["data"]),"Governance menu route missing"
    overview=request("/governance/overview")["data"]
    assert overview["engine"]["reachable"],"NiFi is not reachable through application"
    projects=request("/governance/projects")["data"]
    project=next((x for x in projects if x["name"]=="数据治理开发验收"),None)
    if project is None:project=request("/governance/projects","POST",{"name":"数据治理开发验收","description":"仅使用合成样本的本地验收项目"})["data"]
    def flow(name,template):
        candidates=request("/governance/flows?projectId="+project["id"])["data"]
        found=next((x for x in candidates if x["name"]==name),None)
        return found or request("/governance/flows","POST",{"projectId":project["id"],"name":name,"templateId":template})["data"]
    def run(flow,input_value,expected):
        response=request("/governance/flows/"+flow["id"]+"/tests","POST",{"inputJson":json.dumps(input_value,ensure_ascii=False),"parameters":{}})["data"]
        deadline=time.monotonic()+90
        while response["status"] not in TERMINAL and time.monotonic()<deadline:
            time.sleep(0.3);response=request("/governance/test-runs/"+response["id"])["data"]
        assert response["status"]==expected, f"Unexpected test outcome {response['status']}"
        assert response["cleanupConfirmed"],"Test cleanup not confirmed"
        return response
    normal=flow("JSON 路由样本","sample-safe-v1")
    r1=run(normal,{"message":"浏览器工作台联调样本","id":"synthetic"},"SUCCEEDED")
    writer=flow("协议文本与表头计数","delimited-safe-v1")
    r2=run(writer,[{"message":"合成记录"+str(i),"picture":""} for i in range(75)],"SUCCEEDED")
    assert len(r2["output"])==2,"Expected two header-inclusive segments"
    assert all(x.startswith("message|\u001fpicture\n") for x in r2["output"]),"Wire delimiter/header mismatch"
    assert [len(x.splitlines())-1 for x in r2["output"]]==[74,1],"Legacy record boundary mismatch"
    r3=run(writer,[],"EMPTY")
    r4=run(writer,[{"missing":"synthetic"}],"FAILED")
    evidence={"gateway":"https://localhost:10443","loginAuthenticated":True,"governanceRoute":True,"engineVersion":overview["engine"].get("version"),"projectId":project["id"],"flows":[normal,writer],"cases":[{"id":r["id"],"status":r["status"],"cleanupConfirmed":r["cleanupConfirmed"],"steps":len(r["steps"]),"outputCount":len(r["output"])} for r in [r1,r2,r3,r4]],"legacyDataRecordSegments":[74,1]}
    target=app/"evidence/app-smoke.json";target.parent.mkdir(exist_ok=True);target.write_text(json.dumps(evidence,ensure_ascii=False,indent=2))
    print(json.dumps(evidence,ensure_ascii=False,indent=2))

if __name__=="__main__":main()
