#!/usr/bin/env python3
"""Create a fresh, explicitly local RYNEW acceptance database, without business seed data."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import secrets
import subprocess

DATABASE = "rynew_governance_dev"

def private_write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(value, encoding="utf-8")
    path.chmod(0o600)

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--runtime", type=Path, required=True)
    parser.add_argument("--app-runtime", type=Path, required=True)
    args = parser.parse_args()
    runtime = args.runtime.resolve()
    app = args.app_runtime.resolve()
    app.mkdir(parents=True, exist_ok=True)
    app.chmod(0o700)
    root = Path(__file__).resolve().parents[2]
    private = app / "private"
    private.mkdir(exist_ok=True)
    private.chmod(0o700)
    mysql_conf = runtime / "private/mysql-client.cnf"
    credentials = json.loads((runtime / "private/mysql-credentials.json").read_text())
    if credentials["host"] != "127.0.0.1" or int(credentials["port"]) != 13306:
        raise SystemExit("Only the dedicated loopback MySQL on 13306 is accepted")
    cmd = ["/usr/local/mysql/bin/mysql", f"--defaults-extra-file={mysql_conf}", "--protocol=TCP", "--host=127.0.0.1", "--port=13306", "--default-character-set=utf8mb4", "--batch", "--skip-column-names"]
    def sql(text, database=False):
        result = subprocess.run(cmd + ([DATABASE] if database else []), input=text, text=True, capture_output=True, timeout=60)
        if result.returncode:
            private_write(private / "mysql-bootstrap-error.log", result.stderr)
            raise RuntimeError("Database operation failed; diagnostic saved privately")
        return result.stdout.strip()
    actual_dir = Path(sql("SELECT @@datadir;")).resolve()
    if actual_dir != (runtime / "mysql/data").resolve():
        raise SystemExit("Refusing to use a MySQL instance outside the dedicated runtime")
    marker = private / "database-bootstrap.json"
    exists = sql(f"SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name='{DATABASE}';") == "1"
    if exists and not marker.exists():
        raise SystemExit("Database exists without this tool's ownership marker; refusing to overwrite")
    if not exists:
        sql(f"CREATE DATABASE {DATABASE} CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;")
        # Save ownership before DDL so an interrupted bootstrap never targets an unrelated database.
        private_write(marker, json.dumps({"database": DATABASE, "datadir": str(actual_dir), "stage": "created"}, indent=2))
    marker_value = json.loads(marker.read_text())
    if marker_value.get("database") != DATABASE or Path(marker_value["datadir"]).resolve() != actual_dir:
        raise SystemExit("Ownership marker mismatch")
    if marker_value.get("stage") != "initialized":
        for source in [root / "WDF100.0/sql/ry_20260320.sql", root / "WDF100.0/sql/quartz.sql"]:
            text = source.read_text(encoding="utf-8-sig")
            blocks = re.findall(r"(?ims)^create\s+table\s+.*?^\)[^;]*;", text)
            if not blocks:
                raise RuntimeError("No DDL found in expected initialization source")
            for block in blocks:
                # Import table structure only. Never import users, credentials or scheduled jobs from source dumps.
                block = re.sub(r"(?i)^create\s+table\s+(?!if\s+not\s+exists)", "CREATE TABLE IF NOT EXISTS ", block)
                sql(block, True)
        account_file = private / "app-login.json"
        account = json.loads(account_file.read_text()) if account_file.exists() else {"username": "governance_dev", "password": secrets.token_urlsafe(15)}
        if not 5 <= len(account["password"]) <= 20:
            raise RuntimeError("Development login must follow the existing RYNEW 5..20 character policy")
        private_write(account_file, json.dumps(account, indent=2))
        hashed = subprocess.run(["/usr/sbin/htpasswd", "-niBC", "10", account["username"]], input=account["password"] + "\n", text=True, capture_output=True, check=True).stdout.strip().split(":", 1)[1]
        if not re.fullmatch(r"\$2[aby]\$\d\d\$[./A-Za-z0-9]{53}", hashed):
            raise RuntimeError("Unexpected password hash format")
        sql("INSERT IGNORE INTO sys_dept(dept_id,parent_id,ancestors,dept_name,status,del_flag) VALUES(100,0,'0','数据治理隔离开发','0','0');", True)
        sql("INSERT IGNORE INTO sys_role(role_id,role_name,role_key,role_sort,status,del_flag) VALUES(1,'隔离开发管理员','admin',1,'0','0');", True)
        sql(f"INSERT INTO sys_user(user_id,dept_id,user_name,nick_name,password,status,del_flag,create_time,pwd_update_date) VALUES(1,100,'governance_dev','隔离开发管理员','{hashed}','0','0',NOW(),NOW()) ON DUPLICATE KEY UPDATE password=VALUES(password);", True)
        sql("INSERT IGNORE INTO sys_user_role(user_id,role_id) VALUES(1,1);", True)
        sql("INSERT INTO sys_config(config_name,config_key,config_value,config_type) SELECT '开发环境验证码','sys.account.captchaEnabled','false','Y' WHERE NOT EXISTS(SELECT 1 FROM sys_config WHERE config_key='sys.account.captchaEnabled');", True)
        marker_value["stage"] = "initialized"
        private_write(marker, json.dumps(marker_value, indent=2))
    migration = root / "WDF100.0/sql/data_governance_upgrade_20260909_v4_5_0.sql"
    sql(migration.read_text(), True)
    print(json.dumps({"database": DATABASE, "tables": int(sql("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE();", True)), "scheduledJobs": int(sql("SELECT COUNT(*) FROM sys_job;", True)), "loginFile": str(private / "app-login.json"), "menuSqlSha256": hashlib.sha256(migration.read_bytes()).hexdigest()}, ensure_ascii=False))

if __name__ == "__main__":
    main()
