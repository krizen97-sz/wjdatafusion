#!/usr/bin/env python3
"""Create production-safe Spring YAML files from the current project config."""

from __future__ import annotations

import argparse
import re
from pathlib import Path


def sanitize_application(
    text: str,
    exclude_ipam: bool = False,
    exclude_whitelist: bool = False,
) -> str:
    replacements = [
        (r"(?m)^(\s*profile:)\s*.*$", r"\1 ${RUOYI_PROFILE:/opt/rynew/upload}"),
        (r"(?m)^(\s*path:)\s*/Users/[^\r\n]+$", r"\1 ${LOG_PATH:/var/log/rynew}"),
        (r"(?m)^(\s*host:)\s*localhost\s*$", r"\1 ${REDIS_HOST:127.0.0.1}"),
        (r"(?m)^(\s*port:)\s*6379\s*$", r"\1 ${REDIS_PORT:6379}"),
        (r"(?m)^(\s*secret:)\s*abcdefghijklmnopqrstuvwxyz\s*$", r"\1 ${TOKEN_SECRET:CHANGE_ME_TO_A_RANDOM_64_CHAR_SECRET}"),
        (r"(?m)^(\s*key:)\s*[\"']?Sz9227328[\"']?\s*$", r"\1 ${SUPPORT_CREDENTIAL_KEY:CHANGE_ME_TO_A_RANDOM_AES_KEY}"),
        (r"(?m)^(\s*url:)\s*jdbc:postgresql://[^\r\n]+$", r"\1 ${WHITELIST_POSTGRES_URL:jdbc:postgresql://127.0.0.1:5432/xresmgr}"),
        (r"(?m)^(\s*bootstrap-servers:)\s*[^\r\n]+$", r"\1 ${WHITELIST_KAFKA_SERVERS:127.0.0.1:9092}"),
    ]
    for pattern, replacement in replacements:
        text = re.sub(pattern, replacement, text)

    # Remove example credentials from comments as well; deliverables must not
    # carry a value that can be mistaken for a deployable password.
    text = re.sub(r"(?m)^(\s*#\s*password:)\s*.*$", r"\1 <set-by-environment>", text)
    text = re.sub(r"(?m)^(\s*com\.hm:)\s*debug\s*$", r"\1 info", text)

    lines = text.splitlines()
    in_postgres = False
    result: list[str] = []
    for line in lines:
        stripped = line.strip()
        if stripped == "postgres:":
            in_postgres = True
        elif in_postgres and stripped and not line.startswith("    "):
            in_postgres = False
        if in_postgres and re.match(r"^\s*username:", line):
            indent = line[: len(line) - len(line.lstrip())]
            line = indent + "username: ${WHITELIST_POSTGRES_USERNAME:rynew_readonly}"
        elif in_postgres and re.match(r"^\s*password:", line):
            indent = line[: len(line) - len(line.lstrip())]
            line = indent + "password: ${WHITELIST_POSTGRES_PASSWORD:CHANGE_ME}"
        result.append(line)
    text = "\n".join(result) + "\n"
    text = text.replace("      enabled: true\n", "      enabled: false\n", 1)
    text = text.replace("    enabled: true\n    path: /swagger-ui.html", "    enabled: false\n    path: /swagger-ui.html", 1)
    if exclude_ipam:
        text = re.sub(
            r"(?ms)^# IP分配管控连通性扫描\n.*?(?=^whitelist:)",
            "",
            text,
        )
    if exclude_whitelist:
        text = re.sub(r"(?ms)^whitelist:\n.*\Z", "", text)
    return text


def sanitize_druid(text: str) -> str:
    text = re.sub(
        r"(?m)^(\s*url:)\s*jdbc:mysql://[^\r\n]+$",
        r"\1 ${DB_URL:jdbc:mysql://127.0.0.1:3306/rynew?useUnicode=true&characterEncoding=utf8&zeroDateTimeBehavior=convertToNull&useSSL=false&serverTimezone=GMT%2B8}",
        text,
        count=1,
    )
    text = re.sub(
        r"(?m)^(\s*username:)\s*[^\r\n]*$",
        r"\1 ${DB_USERNAME:rynew}",
        text,
        count=1,
    )
    text = re.sub(
        r"(?m)^(\s*password:)\s*[^\r\n]*$",
        r"\1 ${DB_PASSWORD:CHANGE_ME}",
        text,
        count=1,
    )
    text = re.sub(
        r"(?m)^(\s*login-username:)\s*[^\r\n]*$",
        r"\1 ${DRUID_ADMIN_USERNAME:druid_admin}",
        text,
    )
    text = re.sub(
        r"(?m)^(\s*login-password:)\s*[^\r\n]*$",
        r"\1 ${DRUID_ADMIN_PASSWORD:CHANGE_ME}",
        text,
    )
    text = text.replace("            statViewServlet:\n                enabled: true", "            statViewServlet:\n                enabled: false", 1)
    return text


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--application", type=Path, required=True)
    parser.add_argument("--druid", type=Path, required=True)
    parser.add_argument("--output-dir", type=Path, required=True)
    parser.add_argument("--exclude-ipam", action="store_true")
    parser.add_argument("--exclude-whitelist", action="store_true")
    args = parser.parse_args()
    args.output_dir.mkdir(parents=True, exist_ok=True)
    app = sanitize_application(
        args.application.read_text(encoding="utf-8"),
        exclude_ipam=args.exclude_ipam,
        exclude_whitelist=args.exclude_whitelist,
    )
    druid = sanitize_druid(args.druid.read_text(encoding="utf-8"))
    (args.output_dir / "application.yml").write_text(app, encoding="utf-8")
    (args.output_dir / "application-druid.yml").write_text(druid, encoding="utf-8")
    print("sanitized_runtime_configs=2")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
