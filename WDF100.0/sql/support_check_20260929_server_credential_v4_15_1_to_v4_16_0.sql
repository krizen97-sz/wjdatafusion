-- Read-only pre/post checks for complete v4.15.1 -> v4.16.0.
-- No passwords, ciphertext values or business device addresses are returned.
-- This reports findings, not automatic execution guards: manually stop on STOP.
-- NOT_APPLIED is allowed before upgrade only. Review existing new-table definitions.
SET NAMES utf8mb4;
SELECT DATABASE() AS target_database, VERSION() AS mysql_version,
  @@SESSION.autocommit AS autocommit,
  CASE WHEN DATABASE() IS NULL THEN 'STOP: select target database' ELSE 'PASS' END AS database_check;

SELECT required.table_name, actual.engine,
  CASE WHEN actual.table_name IS NULL THEN 'STOP: incomplete v4.15.1 baseline'
    WHEN actual.engine <> 'InnoDB' THEN 'STOP: review custom storage engine'
    ELSE 'PASS' END AS baseline_check
FROM (
  SELECT 'sup_site' AS table_name UNION ALL SELECT 'sup_platform' UNION ALL SELECT 'sup_server'
  UNION ALL SELECT 'sup_server_credential' UNION ALL SELECT 'sup_hardware_asset'
  UNION ALL SELECT 'sup_platform_server_rel' UNION ALL SELECT 'sup_platform_asset_rel'
  UNION ALL SELECT 'sup_equipment_room' UNION ALL SELECT 'sup_equipment_cabinet'
  UNION ALL SELECT 'sup_equipment_link' UNION ALL SELECT 'sys_menu' UNION ALL SELECT 'sys_role_menu'
) required
LEFT JOIN information_schema.tables actual ON actual.table_schema = DATABASE()
  AND actual.table_name = required.table_name AND actual.table_type = 'BASE TABLE';

SELECT required.table_name, required.column_name, actual.column_type, actual.column_default,
  CASE WHEN actual.column_name IS NULL THEN 'STOP: missing prerequisite column'
    WHEN required.column_name IN ('site_id', 'server_id') AND
      (actual.data_type <> 'bigint' OR actual.column_type LIKE '%unsigned%' OR actual.column_key <> 'PRI')
      THEN 'STOP: FK parent must have signed BIGINT primary key'
    WHEN required.column_name = 'root_direct_login' AND
      (actual.data_type <> 'tinyint' OR actual.is_nullable <> 'NO'
       OR COALESCE(actual.column_default, '') <> '0' OR COALESCE(actual.generation_expression, '') <> '')
      THEN 'STOP: incompatible root_direct_login definition'
    ELSE 'PASS' END AS column_check
FROM (
  SELECT 'sup_site' AS table_name, 'site_id' AS column_name
  UNION ALL SELECT 'sup_server', 'server_id'
  UNION ALL SELECT 'sup_server', 'root_direct_login'
  UNION ALL SELECT 'sup_server', 'ssh_port'
  UNION ALL SELECT 'sys_menu', 'route_name'
) required
LEFT JOIN information_schema.columns actual ON actual.table_schema = DATABASE()
  AND actual.table_name = required.table_name AND actual.column_name = required.column_name;

SELECT 'server_parent_menu' AS check_item, COUNT(*) AS match_count,
  CASE WHEN COUNT(*) = 1 THEN 'PASS' ELSE 'STOP: exactly one server C menu required' END AS result
FROM sys_menu WHERE perms = 'support:server:list' AND menu_type = 'C';

SELECT 'scan_permission' AS check_item, COUNT(*) AS match_count,
  CASE WHEN COUNT(*) = 0 THEN 'NOT_APPLIED'
    WHEN COUNT(*) = 1 AND SUM(CASE WHEN menu_type = 'F' AND status = '0' AND visible = '0'
      AND parent_id IN (SELECT menu_id FROM sys_menu WHERE perms = 'support:server:list' AND menu_type = 'C')
      THEN 1 ELSE 0 END) = 1 THEN 'PASS'
    ELSE 'STOP: duplicate or incompatible scan permission' END AS result
FROM sys_menu WHERE perms = 'support:server:scan';

SELECT menu_id, menu_name, parent_id, menu_type, perms, status, visible
FROM sys_menu WHERE perms IN ('support:server:list', 'support:server:scan') ORDER BY perms, menu_id;

SELECT 'custom_triggers' AS check_item, COUNT(*) AS match_count,
  CASE WHEN COUNT(*) = 0 THEN 'PASS' ELSE 'STOP: DBA review of custom triggers required' END AS result
FROM information_schema.triggers WHERE trigger_schema = DATABASE()
  AND event_object_table IN ('sys_menu', 'sup_server_scan_credential', 'sup_server_scan_profile');

SELECT required.table_name, actual.engine,
  CASE WHEN actual.table_name IS NULL THEN 'NOT_APPLIED'
    WHEN actual.engine <> 'InnoDB' THEN 'STOP: incompatible new-table engine'
    ELSE 'REVIEW: compare columns, indexes and FKs with upgrade SQL' END AS schema_check
FROM (SELECT 'sup_server_scan_credential' AS table_name UNION ALL SELECT 'sup_server_scan_profile') required
LEFT JOIN information_schema.tables actual ON actual.table_schema = DATABASE()
  AND actual.table_name = required.table_name AND actual.table_type = 'BASE TABLE';

SELECT table_name, column_name, column_type, is_nullable, column_default, column_key, extra
FROM information_schema.columns WHERE table_schema = DATABASE()
  AND table_name IN ('sup_server_scan_credential', 'sup_server_scan_profile')
ORDER BY table_name, ordinal_position;

SELECT table_name, index_name, non_unique, seq_in_index, column_name
FROM information_schema.statistics WHERE table_schema = DATABASE()
  AND table_name IN ('sup_server_scan_credential', 'sup_server_scan_profile')
ORDER BY table_name, index_name, seq_in_index;

SELECT k.table_name, k.column_name, k.referenced_table_name, k.referenced_column_name, r.delete_rule
FROM information_schema.key_column_usage k
JOIN information_schema.referential_constraints r
  ON r.constraint_schema = k.constraint_schema AND r.table_name = k.table_name AND r.constraint_name = k.constraint_name
WHERE k.table_schema = DATABASE() AND k.table_name IN ('sup_server_scan_credential', 'sup_server_scan_profile');

SELECT 'sup_site' AS counted_table, COUNT(*) AS row_count FROM sup_site
UNION ALL SELECT 'sup_server', COUNT(*) FROM sup_server
UNION ALL SELECT 'sup_server_credential', COUNT(*) FROM sup_server_credential
UNION ALL SELECT 'sys_menu', COUNT(*) FROM sys_menu
UNION ALL SELECT 'sys_role_menu', COUNT(*) FROM sys_role_menu;
