-- Read-only v4.8.4 -> v4.15.1 pre/post deployment checks. No passwords or ciphertext are returned.
SELECT DATABASE() AS target_database, VERSION() AS mysql_version, @@SESSION.autocommit AS autocommit;

SELECT required.table_name,
  CASE WHEN actual.table_name IS NULL THEN 'MISSING: stop and repair baseline' ELSE 'OK' END AS baseline_status
FROM (
  SELECT 'sup_site' AS table_name UNION ALL SELECT 'sup_platform' UNION ALL SELECT 'sup_server'
  UNION ALL SELECT 'sup_server_credential' UNION ALL SELECT 'sup_hardware_asset'
  UNION ALL SELECT 'sup_platform_server_rel' UNION ALL SELECT 'sup_platform_asset_rel'
  UNION ALL SELECT 'sup_equipment_room' UNION ALL SELECT 'sup_equipment_cabinet'
  UNION ALL SELECT 'sup_equipment_link' UNION ALL SELECT 'sys_menu' UNION ALL SELECT 'sys_role_menu'
) required
LEFT JOIN information_schema.tables actual ON actual.table_schema = DATABASE()
  AND actual.table_name = required.table_name AND actual.table_type = 'BASE TABLE';

SELECT table_name, column_name, column_type, is_nullable, column_default
FROM information_schema.columns
WHERE table_schema = DATABASE() AND (
  (table_name = 'sup_server' AND column_name IN ('ssh_port', 'root_direct_login'))
  OR (table_name = 'sys_menu' AND column_name IN ('menu_id', 'route_name'))
);

SELECT menu_id, menu_name, parent_id, menu_type, perms, status, visible
FROM sys_menu
WHERE perms IN ('support:server:list', 'support:credential:verify', 'support:credential:viewPlain')
ORDER BY perms, menu_id;

SELECT 'sup_server' AS counted_table, COUNT(*) AS row_count FROM sup_server
UNION ALL SELECT 'sys_menu', COUNT(*) FROM sys_menu
UNION ALL SELECT 'sys_role_menu', COUNT(*) FROM sys_role_menu;

SELECT 'root_direct_login' AS upgrade_check, COUNT(*) AS matches_expected_definition
FROM information_schema.columns
WHERE table_schema = DATABASE() AND table_name = 'sup_server' AND column_name = 'root_direct_login'
  AND data_type = 'tinyint' AND is_nullable = 'NO' AND column_default = '0'
  AND COALESCE(generation_expression, '') = '';
SELECT 'support:credential:verify' AS upgrade_check, COUNT(*) AS permission_count
FROM sys_menu WHERE perms = 'support:credential:verify';
