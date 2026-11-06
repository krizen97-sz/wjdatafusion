#!/usr/bin/env python3
"""Read-only RYNEW source inventory. Never execute SQL, builds or service calls."""

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys
from urllib.parse import urlsplit, urlunsplit
import xml.etree.ElementTree as ET


BUSINESS = "WDF100.0/wjdatafusion-manage/src/main/"
JAVA = BUSINESS + "java/com/hm/manage/"
QUARTZ = "WDF100.0/wjdatafusion-quartz/src/main/java/com/hm/quartz/"
FRAMEWORK = "WDF100.0/wjdatafusion-framework/src/main/java/com/hm/framework/"

SOURCES = {
    "common": [
        "AGENTS.md", "docs/CODEX_WORKTREES.md", "tools/codex-worktree.sh",
        "RuoYi-Vue3-master/package.json", "RuoYi-Vue3-master/src/utils/request.js",
        "RuoYi-Vue3-master/src/views/support/version/releaseNotes.js",
        "WDF100.0/pom.xml", "WDF100.0/wjdatafusion-admin/pom.xml",
        "WDF100.0/wjdatafusion-common/src/main/java/com/hm/common/core/controller/BaseController.java",
        FRAMEWORK + "config/SecurityConfig.java", FRAMEWORK + "web/service/TokenService.java",
        "WDF100.0/sql/README.md", "WDF100.0/sql/rynew_schema_latest.sql",
        "WDF100.0/sql/rynew_init_latest.sql", "tools/database/init-latest.mjs",
        "docs/frontend/DESIGN.md", "docs/frontend/COMPONENT-CATALOG.md",
        "docs/frontend/REFERENCE-PAGES.md",
    ],
    "platform-ui": [
        ".agents/skills/ruoyi-element-plus-ui/SKILL.md",
        "RuoYi-Vue3-master/src/main.js",
        "RuoYi-Vue3-master/src/assets/styles/variables.module.scss",
        "RuoYi-Vue3-master/src/components/RightToolbar/index.vue",
        "RuoYi-Vue3-master/scripts/ui-guard.mjs",
        "docs/frontend/PROTOTYPE-ADAPTATION.md",
        "docs/frontend/UI-EXCEPTIONS.md",
    ],
    "site-fusion": [
        "RuoYi-Vue3-master/src/views/support/site/SiteConfigDialog.vue",
        JAVA + "controller/SupportSiteController.java",
        JAVA + "service/impl/SupportSiteServiceImpl.java",
        JAVA + "service/impl/SupportPlatformServiceImpl.java",
        JAVA + "service/impl/SupportEquipmentServiceImpl.java",
        JAVA + "service/impl/SupportEquipmentWorkbookService.java",
        JAVA + "service/impl/SupportServerScanService.java",
        JAVA + "service/impl/SupportServerScanSyncService.java",
        JAVA + "service/impl/SupportChangeLogServiceImpl.java",
        JAVA + "service/support/SupportServerPasswordPoolMatcher.java",
        JAVA + "service/support/CredentialCryptoService.java",
        BUSINESS + "resources/mapper/support/SupportSiteMapper.xml",
    ],
    "auto-inspection": [
        JAVA + "controller/SupportAutoInspectionController.java",
        JAVA + "service/impl/SupportAutoInspectionServiceImpl.java",
        JAVA + "service/impl/SupportAutoInspectionMetricsServiceImpl.java",
        JAVA + "service/impl/AutoInspectionValueComparison.java",
        JAVA + "service/impl/AutoInspectionComparisonWindow.java",
        JAVA + "service/impl/AutoInspectionReadOnlyQueryGuard.java",
        JAVA + "service/impl/AutoInspectionPeriodReport.java",
        QUARTZ + "controller/SupportAutoInspectionPlanController.java",
        QUARTZ + "service/SupportAutoInspectionPlanScheduleService.java",
        QUARTZ + "task/SupportAutoInspectionTask.java",
        BUSINESS + "resources/mapper/support/SupportAutoInspectionMapper.xml",
    ],
    "document-management": [
        JAVA + "controller/DocumentWorkspaceController.java",
        JAVA + "service/impl/DocumentWorkspaceServiceImpl.java",
        JAVA + "service/document/DocumentStorageService.java",
        JAVA + "service/document/OnlyOfficeDocumentEditorProvider.java",
        JAVA + "service/document/DocumentJwtService.java",
        JAVA + "config/DocumentManagementProperties.java",
        BUSINESS + "resources/mapper/document/DocumentWorkspaceMapper.xml",
        QUARTZ + "task/DocumentPermissionExpiryTask.java",
    ],
    "knowledge-center": [
        JAVA + "controller/KnowledgeCenterController.java",
        JAVA + "service/impl/KnowledgeCenterServiceImpl.java",
        JAVA + "service/knowledge/KnowledgeHtmlSanitizer.java",
        BUSINESS + "resources/mapper/knowledge/KnowledgeCenterMapper.xml",
    ],
    "data-governance": [
        JAVA + "controller/DataGovernanceHdiCoreController.java",
        JAVA + "service/governance/hdi/HdiTaskStore.java",
        JAVA + "service/governance/hdi/HdiCoreRuns.java",
        JAVA + "service/governance/hdi/HdiCoreSchedules.java",
        JAVA + "service/governance/hdi/HdiCoreConnections.java",
        JAVA + "service/governance/hdi/HdiCoreProperties.java",
        JAVA + "service/governance/hdi/HdiCoreConnectionProperties.java",
        JAVA + "service/governance/hdi/HdiCompilerClient.java",
        JAVA + "service/governance/hdi/HdiWorkerClient.java",
        "data-governance/hdi-native/README.md",
        "data-governance/hdi-native/core-runtime/SETUP.md",
        "tools/data-governance/hdi_core_setup.py",
    ],
    "ipam": [
        JAVA + "controller/IpamController.java",
        JAVA + "service/impl/IpamServiceImpl.java",
        JAVA + "service/impl/IpamWorkbookServiceImpl.java",
        JAVA + "service/impl/IpamScanServiceImpl.java",
        JAVA + "util/IpamAddressUtils.java",
        BUSINESS + "resources/mapper/ipam/IpamMapper.xml",
        BUSINESS + "resources/mapper/ipam/IpamScanMapper.xml",
    ],
    "whitelist": [
        JAVA + "controller/WhitelistPlateController.java",
        JAVA + "controller/WhitelistFilterDataController.java",
        JAVA + "service/impl/WhitelistPlateServiceImpl.java",
        JAVA + "service/impl/WhitelistFilterDataServiceImpl.java",
        JAVA + "config/WhitelistExternalDataSourceConfig.java",
        JAVA + "config/WhitelistKafkaProperties.java",
    ],
}


def git(root, *args, required=True):
    # Optional index locks are unnecessary for inventory and should not touch the index.
    env = dict(os.environ, GIT_OPTIONAL_LOCKS="0", GIT_TERMINAL_PROMPT="0")
    result = subprocess.run(
        ["git", "-C", str(root), *args], capture_output=True, text=True,
        timeout=30, env=env, check=False,
    )
    if result.returncode and required:
        raise ValueError("Git preflight failed; run this against a local RYNEW checkout")
    return result.stdout.strip() if result.returncode == 0 else None


def public_remote(value):
    """Strip userinfo, query and fragments; never echo an unrecognized remote."""
    if not value:
        return None
    parsed = urlsplit(value)
    if parsed.scheme in {"http", "https", "ssh"} and parsed.hostname:
        return urlunsplit((parsed.scheme, parsed.hostname, parsed.path, "", ""))
    match = re.fullmatch(r"(?:[^@/\s]+@)?([A-Za-z0-9.-]+):([A-Za-z0-9_./-]+)", value)
    if match:
        return "ssh://" + match[1] + "/" + match[2]
    return "[remote omitted]"


def configuration_keys(path):
    """Inventory indentation-based mapping keys, not YAML values or comments."""
    if not path.is_file():
        return []
    stack, keys = [], []
    for line in path.read_text(encoding="utf-8").splitlines():
        match = re.match(r"^( *)([A-Za-z][\w.-]*):(?:\s.*)?$", line)
        if not match:
            continue
        depth, key = len(match[1]), match[2]
        while stack and stack[-1][0] >= depth:
            stack.pop()
        stack.append((depth, key))
        keys.append(".".join(item[1] for item in stack))
    return sorted(set(keys))


def inventory(requested_root, module):
    requested_root = requested_root.expanduser().resolve(strict=True)
    root = Path(git(requested_root, "rev-parse", "--show-toplevel")).resolve()
    if not (root / "RuoYi-Vue3-master/package.json").is_file() or not (root / "WDF100.0/pom.xml").is_file():
        raise ValueError("Not a RYNEW source checkout: frontend/package.json and WDF100.0/pom.xml are required")
    origin_main = git(root, "rev-parse", "--verify", "origin/main", required=False)
    selected = list(SOURCES) if module == "all" else list(dict.fromkeys(["common", module]))
    paths = sorted({p for name in selected for p in SOURCES[name]})
    present, missing = [], []
    for relative in paths:
        source = root / relative
        if source.is_file():
            present.append({"path": relative, "sha256": hashlib.sha256(source.read_bytes()).hexdigest()})
        else:
            missing.append(relative)

    package = json.loads((root / "RuoYi-Vue3-master/package.json").read_text(encoding="utf-8"))
    pom = ET.parse(root / "WDF100.0/pom.xml").getroot()
    ns = {"m": "http://maven.apache.org/POM/4.0.0"}
    property_names = ["java.version", "spring-boot.version", "mybatis-spring-boot.version", "druid.version"]
    versions = {name: pom.findtext("m:properties/m:" + name, namespaces=ns) for name in property_names}
    schema = root / "WDF100.0/sql/rynew_schema_latest.sql"
    tables = re.findall(r"^CREATE TABLE IF NOT EXISTS `([^`]+)`", schema.read_text(encoding="utf-8"), re.M) if schema.is_file() else []
    config_root = root / "WDF100.0/wjdatafusion-admin/src/main/resources"
    dirty = git(root, "status", "--porcelain=v1", "-uall")
    return {
        "scope": "local source inventory only; no service, network or database checks",
        "repository": {
            "root": str(root), "branch": git(root, "branch", "--show-current") or "DETACHED",
            "head": git(root, "rev-parse", "HEAD"), "origin_main": origin_main,
            "contains_origin_main": (git(root, "merge-base", "--is-ancestor", "origin/main", "HEAD", required=False) is not None) if origin_main else None,
            "dirty_entry_count": len(dirty.splitlines()) if dirty else 0,
            "worktree_count": sum(line.startswith("worktree ") for line in git(root, "worktree", "list", "--porcelain").splitlines()),
            "remote": public_remote(git(root, "remote", "get-url", "origin", required=False)),
        },
        "versions": {"maven_project": pom.findtext("m:version", namespaces=ns), "maven_properties": versions,
                     "frontend_package": package.get("version"),
                     "frontend_dependencies": {name: package.get("dependencies", {}).get(name) for name in ["vue", "element-plus", "pinia", "vue-router"]}},
        "frontend_script_names": sorted(package.get("scripts", {})),
        "schema": {"table_count": len(tables), "table_names": sorted(tables)},
        "configuration_key_names": {name: configuration_keys(config_root / name) for name in ["application.yml", "application-druid.yml"]},
        "selected_modules": selected, "source_fingerprints": present, "missing_sources": missing,
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path.cwd(), help="Local RYNEW checkout or its subdirectory")
    parser.add_argument("--module", choices=["all", *SOURCES], default="all")
    parser.add_argument("--check", action="store_true", help="Return nonzero if documented source entrypoints are missing")
    args = parser.parse_args()
    try:
        result = inventory(args.root, args.module)
    except (OSError, ValueError, ET.ParseError, subprocess.TimeoutExpired):
        print(json.dumps({"error": "Cannot inspect this checkout; verify --root, Git and source files"}))
        return 2
    print(json.dumps(result, ensure_ascii=False, indent=2))
    return 1 if args.check and result["missing_sources"] else 0


if __name__ == "__main__":
    sys.exit(main())
