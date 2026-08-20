#!/usr/bin/env python3
"""Export a fresh-install SQL package without copying real business rows.

The database connection is read-only. Table structures come from the current
configured MySQL database, while data is restricted to technical dictionaries,
menus and built-in tool definitions. IPAM, field-fusion and auto-inspection
business rows are replaced with an explicit fictional demo dataset. Delivery
variants can omit the IP allocation and whitelist-management modules completely.
"""

from __future__ import annotations

import argparse
import datetime as dt
import decimal
import ipaddress
import re
from pathlib import Path
from typing import Iterable, Sequence

import pymysql


BUSINESS_PREFIXES = ("ipam_", "sup_")
EXCLUDED_TABLE_PATTERNS = (
    re.compile(r"^ipam_bak_", re.I),
    re.compile(r"^ipam_clean_bak_", re.I),
)

LIVE_DATA_FILTERS = {
    "sys_config": None,
    "sys_dict_type": None,
    "sys_dict_data": None,
    "sys_menu": None,
    "sys_post": None,
    "sys_role": "role_id IN (1, 2)",
    "sys_role_menu": "role_id IN (1, 2)",
    "sup_auto_inspection_tool": None,
    "sup_tim_inspection_item_config": None,
    "ipam_setting": None,
    "sys_job": "invoke_target LIKE 'ipamScanTask%'",
}

BASE_SOURCE_TABLES = {
    "sys_dept",
    "sys_user",
    "sys_user_post",
    "sys_user_role",
    "sys_role_dept",
}


def parse_datasource(config_path: Path) -> dict[str, object]:
    text = config_path.read_text(encoding="utf-8")
    url = re.search(
        r"url:\s*jdbc:mysql://([^/:?]+)(?::(\d+))?/([^?\s]+)", text
    )
    username = re.search(r"^[ \t]*username:\s*([^\s#]+)", text, re.M)
    password = re.search(r"^[ \t]*password:\s*([^\s#]+)", text, re.M)
    if not (url and username and password):
        raise RuntimeError(f"Cannot parse datasource settings from {config_path}")
    return {
        "host": url.group(1),
        "port": int(url.group(2) or 3306),
        "database": url.group(3),
        "user": username.group(1),
        "password": password.group(1),
    }


def is_excluded_table(table: str) -> bool:
    return any(pattern.search(table) for pattern in EXCLUDED_TABLE_PATTERNS)


def sql_literal(connection, value: object) -> str:
    if value is None:
        return "NULL"
    if isinstance(value, bool):
        return "1" if value else "0"
    if isinstance(value, (int, float, decimal.Decimal)):
        return str(value)
    if isinstance(value, (dt.date, dt.datetime, dt.time)):
        return connection.escape(str(value))
    if isinstance(value, (bytes, bytearray, memoryview)):
        return "0x" + bytes(value).hex()
    return connection.escape(str(value))


def emit_rows(
    connection,
    cursor,
    table: str,
    where: str | None = None,
    order_by: str | None = None,
) -> list[str]:
    cursor.execute(
        "SELECT COLUMN_NAME FROM information_schema.COLUMNS "
        "WHERE TABLE_SCHEMA=%s AND TABLE_NAME=%s ORDER BY ORDINAL_POSITION",
        (connection.db.decode() if isinstance(connection.db, bytes) else connection.db, table),
    )
    columns = [row[0] for row in cursor.fetchall()]
    if not columns:
        return []
    query = f"SELECT * FROM `{table}`"
    if where:
        query += f" WHERE {where}"
    if order_by:
        query += f" ORDER BY {order_by}"
    cursor.execute(query)
    rows = cursor.fetchall()
    if not rows:
        return []
    column_sql = ", ".join(f"`{column}`" for column in columns)
    statements = []
    for row in rows:
        values = ", ".join(sql_literal(connection, value) for value in row)
        statements.append(f"INSERT INTO `{table}` ({column_sql}) VALUES ({values});")
    return statements


def split_sql_statements(text: str) -> Iterable[str]:
    current: list[str] = []
    quote: str | None = None
    escaped = False
    for char in text:
        current.append(char)
        if escaped:
            escaped = False
            continue
        if char == "\\":
            escaped = True
            continue
        if quote:
            if char == quote:
                quote = None
            continue
        if char in ("'", '"', "`"):
            quote = char
            continue
        if char == ";":
            statement = "".join(current).strip()
            if statement:
                yield statement
            current = []
    tail = "".join(current).strip()
    if tail:
        yield tail


def extract_base_statements(source_path: Path) -> list[str]:
    statements: list[str] = []
    source = source_path.read_text(encoding="utf-8-sig")
    for statement in split_sql_statements(source):
        match = re.search(
            r"\b(?:INSERT|REPLACE)\s+INTO\s+`?([a-zA-Z0-9_]+)`?",
            statement,
            re.I,
        )
        if match and match.group(1).lower() in BASE_SOURCE_TABLES:
            statements.append(statement.rstrip(";") + ";")
    return statements


def demo_statements(include_ipam: bool = True) -> list[str]:
    demo_time = "2026-08-10 00:00:00"
    network = ipaddress.ip_network("10.255.254.0/29")
    start_value = int(network.network_address)
    end_value = int(network.broadcast_address)
    ip_value = int(ipaddress.ip_address("10.255.254.3"))
    statements = [
        "-- 现场融合管理演示数据：名称、地址、人员和联系方式均为虚构。",
        "INSERT INTO `sup_site` (`site_id`,`site_name`,`site_code`,`province_code`,`province_name`,`city_code`,`city_name`,`district_code`,`district_name`,`location`,`description`,`status`,`create_by`,`create_time`,`remark`) VALUES "
        f"(900001,'演示现场A','DEMO-SITE-001','320000','演示省','320100','演示市','320102','演示区','演示路100号','仅用于功能演示，不对应真实现场','0','demo','{demo_time}','DEMO DATA');",
        "INSERT INTO `sup_platform` (`platform_id`,`site_id`,`platform_name`,`platform_level`,`network_env`,`status`,`create_by`,`create_time`,`remark`) VALUES "
        f"(900001,900001,'演示主平台','MAIN','DEMO_NET','0','demo','{demo_time}','DEMO DATA');",
        "INSERT INTO `sup_org` (`org_id`,`org_type`,`org_name`,`short_name`,`status`,`create_by`,`create_time`,`remark`) VALUES "
        f"(900001,'OWNER','演示运维单位','演示运维','0','demo','{demo_time}','DEMO DATA');",
        "INSERT INTO `sup_contact` (`contact_id`,`org_id`,`contact_name`,`role_type`,`phone`,`email`,`wechat`,`is_primary`,`create_by`,`create_time`,`remark`) VALUES "
        f"(900001,900001,'演示管理员','MAINTAINER','13800000000','demo@example.invalid',NULL,'1','demo','{demo_time}','DEMO DATA');",
        "INSERT INTO `sup_server` (`server_id`,`site_id`,`server_name`,`server_address`,`ssh_port`,`os_type`,`equipment_room`,`cabinet_no`,`rack_u_start`,`rack_u_end`,`os_username`,`os_password_cipher`,`status`,`create_by`,`create_time`,`remark`) VALUES "
        f"(900001,900001,'演示应用服务器','10.255.253.10',22,'Linux','演示机房','DEMO-A01',10,12,NULL,NULL,'0','demo','{demo_time}','DEMO DATA - NO CREDENTIAL');",
        "INSERT INTO `sup_hardware_asset` (`asset_id`,`site_id`,`asset_name`,`asset_type`,`network_env`,`ip_address`,`manufacturer`,`asset_model`,`serial_no`,`install_location`,`equipment_room`,`cabinet_no`,`rack_u_start`,`rack_u_end`,`status`,`create_by`,`create_time`,`remark`) VALUES "
        f"(900001,900001,'演示交换机','SWITCH','DEMO_NET','10.255.253.20','DEMO','DEMO-SW24',NULL,'演示机房A01机柜','演示机房','DEMO-A01',20,20,'0','demo','{demo_time}','DEMO DATA');",
        "INSERT INTO `sup_platform_server_rel` (`rel_id`,`platform_id`,`server_id`,`create_by`,`create_time`) VALUES "
        f"(900001,900001,900001,'demo','{demo_time}');",
        "INSERT INTO `sup_platform_contact_rel` (`rel_id`,`platform_id`,`contact_id`,`create_by`,`create_time`) VALUES "
        f"(900001,900001,900001,'demo','{demo_time}');",
    ]
    if include_ipam:
        statements.extend([
        "-- IP 分配演示数据：使用专用演示网段，不包含任何现网 IP、映射、账号或密码。",
        "INSERT INTO `ipam_network` (`network_id`,`network_name`,`police_station_name`,`cidr_block`,`start_ip`,`end_ip`,`start_value`,`end_value`,`prefix_length`,`scenario_type`,`status`,`create_by`,`create_time`,`remark`) VALUES "
        f"(900001,'演示专网','演示单位','{network.with_prefixlen}','{network.network_address}','{network.broadcast_address}',{start_value},{end_value},29,'SOCIAL','0','demo','{demo_time}','DEMO DATA');",
        "INSERT INTO `ipam_segment` (`segment_id`,`network_id`,`segment_name`,`cidr_block`,`start_ip`,`end_ip`,`gateway_ip`,`prefix_length`,`total_count`,`status`,`create_by`,`create_time`,`remark`) VALUES "
        f"(900001,900001,'演示专网-01','{network.with_prefixlen}','{network.network_address}','{network.broadcast_address}','10.255.254.1',29,8,'0','demo','{demo_time}','DEMO DATA');",
        "INSERT INTO `ipam_site` (`site_id`,`area_name`,`site_name`,`scenario_type`,`access_unit`,`contact_name`,`contact_phone`,`access_control_brand`,`barrier_gate_brand`,`access_status`,`create_by`,`create_time`,`remark`) VALUES "
        f"(900001,'演示区','演示小区A','SOCIAL','演示接入单位','演示联系人','13800000000','DEMO','DEMO','演示接入','demo','{demo_time}','DEMO DATA');",
        "INSERT INTO `ipam_address` (`address_id`,`network_id`,`segment_id`,`site_id`,`ip_address`,`ip_value`,`status`,`address_role`,`area_name`,`community_name`,`target_type`,`target_name`,`manufacturer`,`access_unit`,`purpose`,`login_username`,`login_password`,`owner_name`,`owner_phone`,`issue_batch`,`allocated_time`,`create_by`,`create_time`,`remark`) VALUES "
        f"(900001,900001,900001,900001,'10.255.254.3',{ip_value},'ALLOCATED','DEVICE','演示区','演示小区A','ACCESS_CONTROL','演示门禁终端','DEMO','演示接入单位','平台功能演示',NULL,NULL,'演示联系人','13800000000','DEMO-20260810','{demo_time}','demo','{demo_time}','DEMO DATA - NO CREDENTIAL');",
        ])
    statements.extend([
        "-- 自动巡检演示数据：目标指向 .invalid 保留域，计划默认停用，不会访问真实系统。",
        "INSERT INTO `sup_auto_inspection_target` (`target_id`,`target_name`,`target_type`,`server_id`,`host`,`port`,`path`,`url`,`http_method`,`username`,`password_cipher`,`secret_cipher`,`status`,`create_by`,`create_time`,`remark`) VALUES "
        f"(900001,'演示健康检查目标','HTTP_HEALTH',NULL,NULL,NULL,NULL,'https://example.invalid/health','GET',NULL,NULL,NULL,'0','demo','{demo_time}','DEMO DATA - NO CREDENTIAL');",
        "INSERT INTO `sup_auto_inspection_template` (`template_id`,`template_name`,`template_desc`,`status`,`create_by`,`create_time`,`remark`) VALUES "
        f"(900001,'演示基础巡检模板','仅用于展示模板、步骤和计划配置流程','0','demo','{demo_time}','DEMO DATA');",
        "INSERT INTO `sup_auto_inspection_template_step` (`step_id`,`template_id`,`tool_code`,`step_name`,`enabled_flag`,`sort_order`,`threshold_value`,`threshold_unit`,`compare_rule`,`time_window_minutes`,`timeout_seconds`,`step_params`,`create_by`,`create_time`,`remark`) VALUES "
        f"(900001,900001,'HTTP_HEALTH','检查演示健康地址','Y',1,NULL,NULL,'MAX',0,5,'{{\"expectedStatus\":200}}','demo','{demo_time}','DEMO DATA');",
        "INSERT INTO `sup_auto_inspection_template_step_target` (`step_target_id`,`step_id`,`target_id`,`create_by`,`create_time`,`remark`) VALUES "
        f"(900001,900001,900001,'demo','{demo_time}','DEMO DATA');",
        "INSERT INTO `sup_auto_inspection_plan` (`plan_id`,`template_id`,`plan_name`,`cron_expression`,`cron_config`,`job_id`,`report_style`,`status`,`create_by`,`create_time`,`remark`) VALUES "
        f"(900001,900001,'演示每日巡检计划','0 0 3 * * ?','{{\"mode\":\"DEMO\"}}',NULL,'STANDARD','1','demo','{demo_time}','DEMO DATA - DISABLED');",
    ])
    return statements


def write_export(
    config: Path,
    base_sql: Path,
    output: Path,
    include_ipam: bool = True,
    include_whitelist: bool = True,
) -> tuple[int, int]:
    settings = parse_datasource(config)
    connection = pymysql.connect(
        host=settings["host"],
        port=settings["port"],
        user=settings["user"],
        password=settings["password"],
        database=settings["database"],
        charset="utf8mb4",
        autocommit=False,
        read_timeout=20,
        write_timeout=20,
    )
    module_scope = (
        "IP 分配、现场融合、自动巡检"
        if include_ipam
        else "现场融合、自动巡检"
    )
    lines: list[str] = [
        "-- 华东信息融合平台 v3.9.1 初始化脚本（脱敏演示版）",
        "-- 生成日期：2026-08-10",
        f"-- 数据边界：{module_scope}仅含表结构、内置技术配置和明确标记的虚构演示数据。",
        "-- 不含：真实现场、联系人、账号口令、巡检记录、运行日志、迁移原始数据和历史备份表。",
        "-- 用途：仅用于全新数据库初始化。严禁在未备份的已有数据库上直接执行。",
        "SET NAMES utf8mb4;",
        "SET SQL_MODE = 'NO_AUTO_VALUE_ON_ZERO';",
        "SET FOREIGN_KEY_CHECKS = 0;",
        "CREATE DATABASE IF NOT EXISTS `rynew` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;",
        "USE `rynew`;",
        "",
    ]
    try:
        with connection.cursor() as cursor:
            cursor.execute("SET SESSION TRANSACTION READ ONLY")
            cursor.execute("START TRANSACTION READ ONLY")
            cursor.execute("SHOW FULL TABLES WHERE Table_type = 'BASE TABLE'")
            all_tables = [row[0] for row in cursor.fetchall()]
            tables = [
                table
                for table in all_tables
                if not is_excluded_table(table)
                and (include_ipam or not table.lower().startswith("ipam_"))
                and (
                    include_whitelist
                    or (
                        "whitelist" not in table.lower()
                        and not table.lower().startswith("wl_")
                    )
                )
            ]

            lines.append("-- 一、最新有效表结构")
            for table in tables:
                cursor.execute(f"SHOW CREATE TABLE `{table}`")
                create_sql = cursor.fetchone()[1]
                create_sql = re.sub(r" AUTO_INCREMENT=\d+", "", create_sql)
                lines.extend(
                    [
                        "",
                        f"-- Table: {table}",
                        f"DROP TABLE IF EXISTS `{table}`;",
                        create_sql.rstrip(";") + ";",
                    ]
                )

            lines.extend(["", "-- 二、平台基础技术数据（不含运行日志和真实业务记录）"])
            live_filters = dict(LIVE_DATA_FILTERS)
            if not include_ipam:
                live_filters.update(
                    {
                        "sys_config": "config_key NOT LIKE 'ipam.%'",
                        "sys_dict_type": "dict_type NOT LIKE 'ipam_%'",
                        "sys_dict_data": "dict_type NOT LIKE 'ipam_%'",
                        "sys_menu": "COALESCE(perms, '') NOT LIKE 'ipam:%' AND LOWER(COALESCE(path, '')) NOT LIKE '%ipam%' AND LOWER(COALESCE(component, '')) NOT LIKE '%ipam%'",
                        "sys_role_menu": "menu_id NOT IN (SELECT menu_id FROM sys_menu WHERE COALESCE(perms, '') LIKE 'ipam:%' OR LOWER(COALESCE(path, '')) LIKE '%ipam%' OR LOWER(COALESCE(component, '')) LIKE '%ipam%')",
                    }
                )
                live_filters.pop("ipam_setting", None)
                live_filters.pop("sys_job", None)
            if not include_whitelist:
                whitelist_menu = (
                    "COALESCE(perms, '') LIKE 'whitelist:%' "
                    "OR LOWER(COALESCE(path, '')) LIKE '%whitelist%' "
                    "OR LOWER(COALESCE(component, '')) LIKE '%whitelist%' "
                    "OR COALESCE(menu_name, '') LIKE '%白名单%'"
                )
                def add_filter(table: str, clause: str) -> None:
                    current = live_filters.get(table)
                    live_filters[table] = f"({current}) AND ({clause})" if current else clause

                add_filter("sys_config", "config_key NOT LIKE 'whitelist.%'")
                add_filter("sys_dict_type", "dict_type NOT LIKE 'whitelist_%'")
                add_filter("sys_dict_data", "dict_type NOT LIKE 'whitelist_%'")
                add_filter("sys_menu", f"NOT ({whitelist_menu})")
                add_filter(
                    "sys_role_menu",
                    f"menu_id NOT IN (SELECT menu_id FROM sys_menu WHERE {whitelist_menu})",
                )
                # Scheduled jobs are runtime state. Do not seed any live job row
                # in a package where a business scheduler module is removed.
                live_filters.pop("sys_job", None)
            for table, where in live_filters.items():
                if table not in tables:
                    continue
                statements = emit_rows(connection, cursor, table, where=where)
                if statements:
                    lines.extend(["", f"-- Technical seed: {table}", *statements])

            lines.extend(
                [
                    "",
                    "-- 三、标准初始组织与登录用户（来自仓库基线 SQL，不读取现网用户数据）",
                    *extract_base_statements(base_sql),
                    "",
                    "-- 四、业务模块虚构演示数据",
                    *demo_statements(include_ipam=include_ipam),
                    "",
                    "SET FOREIGN_KEY_CHECKS = 1;",
                    "",
                    "-- 五、导入后核验（期望每类演示记录至少 1 条，且所有敏感凭据列为空）",
                    "SELECT COUNT(*) AS demo_site_count FROM sup_site WHERE create_by = 'demo';",
                    "SELECT COUNT(*) AS demo_inspection_plan_count FROM sup_auto_inspection_plan WHERE create_by = 'demo' AND status = '1';",
                    "SELECT COUNT(*) AS nonempty_demo_credentials FROM sup_server WHERE create_by = 'demo' AND COALESCE(os_password_cipher, '') <> '';",
                ]
            )
            if include_ipam:
                lines.extend(
                    [
                        "SELECT COUNT(*) AS demo_ip_count FROM ipam_address WHERE create_by = 'demo';",
                        "SELECT COUNT(*) AS nonempty_demo_ip_credentials FROM ipam_address WHERE create_by = 'demo' AND COALESCE(login_password, '') <> '';",
                    ]
                )
            connection.rollback()
    finally:
        connection.close()

    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text("\n".join(lines) + "\n", encoding="utf-8")
    business_inserts = sum(
        1
        for line in lines
        if line.startswith("INSERT INTO `")
        and any(f"INSERT INTO `{prefix}" in line for prefix in BUSINESS_PREFIXES)
    )
    return len(tables), business_inserts


def main(argv: Sequence[str] | None = None) -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--config", type=Path, required=True)
    parser.add_argument("--base-sql", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--exclude-ipam", action="store_true")
    parser.add_argument("--exclude-whitelist", action="store_true")
    args = parser.parse_args(argv)
    table_count, business_insert_count = write_export(
        args.config,
        args.base_sql,
        args.output,
        include_ipam=not args.exclude_ipam,
        include_whitelist=not args.exclude_whitelist,
    )
    print(
        f"exported_tables={table_count} business_seed_statements={business_insert_count}"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
