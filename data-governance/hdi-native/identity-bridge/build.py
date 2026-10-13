#!/usr/bin/env python3
"""Compile Java 11 extension against an explicitly supplied original task-service SDK; no downloads."""
import argparse, hashlib, json, os, shutil, subprocess, tempfile
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('--task-service',required=True);p.add_argument('--output',required=True);p.add_argument('--java-home');p.add_argument('--test',action='store_true');args=p.parse_args()
root=Path(__file__).resolve().parent;sdk=Path(args.task_service).resolve();out=Path(args.output).resolve()
if not (sdk/'dEtlv2.jar').is_file() or not (sdk/'lib/dEtlv2-core-3.3.0.jar').is_file():raise SystemExit('Original task-service SDK is incomplete')
java=lambda name:str(Path(args.java_home)/'bin'/name) if args.java_home else name
out.mkdir(parents=True,exist_ok=True);stage=Path(tempfile.mkdtemp(prefix='compile-',dir=out))
try:
 classes=stage/'classes';tests=stage/'test-classes';classes.mkdir();tests.mkdir()
 cp=os.pathsep.join([str(sdk/'dEtlv2.jar'),str(sdk/'lib/*')]);sources=sorted((root/'src/main/java').rglob('*.java'))
 subprocess.run([java('javac'),'--release','11','-encoding','UTF-8','-cp',cp,'-d',str(classes),*[str(x) for x in sources]],check=True)
 shutil.copytree(root/'src/main/resources',classes,dirs_exist_ok=True)
 artifact=stage/'rynew-detl-identity-bridge.jar';subprocess.run([java('jar'),'--create','--file',str(artifact),'-C',str(classes),'.'],check=True)
 runtime=subprocess.run([java('java'),'-version'],capture_output=True,text=True,check=True).stderr.splitlines()[0]
 if args.test:
  test_sources=sorted((root/'src/test/java').rglob('*.java'));testcp=os.pathsep.join([str(classes),cp])
  subprocess.run([java('javac'),'--release','11','-encoding','UTF-8','-cp',testcp,'-d',str(tests),*[str(x) for x in test_sources]],check=True)
  subprocess.run([java('java'),'-cp',os.pathsep.join([str(tests),testcp]),'com.rynew.detl.bridge.IdentityBridgeProof'],check=True)
  proof=stage/'rynew-detl-identity-bridge-tests.jar';subprocess.run([java('jar'),'--create','--file',str(proof),'-C',str(tests),'.'],check=True)
 metadata={'artifact':artifact.name,'sha256':hashlib.sha256(artifact.read_bytes()).hexdigest(),'javaTarget':11,'runtimeUsed':runtime,'testsRun':args.test,'originalTaskJarSha256':hashlib.sha256((sdk/'dEtlv2.jar').read_bytes()).hexdigest(),'originalCoreJarSha256':hashlib.sha256((sdk/'lib/dEtlv2-core-3.3.0.jar').read_bytes()).hexdigest(),'sourceFiles':{str(x.relative_to(root)):hashlib.sha256(x.read_bytes()).hexdigest() for x in sources}}
 if args.test:metadata['testArtifact']=proof.name;metadata['testArtifactSha256']=hashlib.sha256(proof.read_bytes()).hexdigest();os.replace(proof,out/proof.name)
 os.replace(artifact,out/artifact.name);(out/'build-evidence.json').write_text(json.dumps(metadata,indent=2));print(json.dumps({key:value for key,value in metadata.items() if key!='sourceFiles'}))
finally:shutil.rmtree(stage)
