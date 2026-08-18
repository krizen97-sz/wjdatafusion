-- 2026-08-11 IPAM 凭据密钥机制清理回滚结构
-- 说明：本脚本只恢复旧版应用启动所需的空密文字段和迁移权限。
-- 密码及其他业务数据应优先使用清理前数据库备份恢复。

SET NAMES utf8mb4;

SET @ipam_db_name := DATABASE();
SET @has_cipher_column := (
  SELECT COUNT(1)
  FROM information_schema.columns
  WHERE table_schema = @ipam_db_name
    AND table_name = 'ipam_address'
    AND column_name = 'login_password_cipher'
);
SET @sql := IF(
  @has_cipher_column = 0,
  'ALTER TABLE ipam_address ADD COLUMN login_password_cipher VARCHAR(512) DEFAULT NULL COMMENT ''AES-GCM设备密码密文'' AFTER login_password',
  'SELECT ''login_password_cipher exists'' AS message'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

INSERT INTO sys_menu(
  menu_id, menu_name, parent_id, order_num, path, component, `query`, route_name,
  is_frame, is_cache, menu_type, visible, status, perms, icon,
  create_by, create_time, update_by, update_time, remark
)
SELECT 2413, '迁移历史密码', 2415, 13, '#', '', '', '',
       1, 0, 'F', '0', '0', 'ipam:credential:migrate', '#',
       'admin', NOW(), 'admin', NOW(), '回滚恢复的历史密码迁移权限'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 2413 OR perms = 'ipam:credential:migrate');

SELECT 'IPAM_CREDENTIAL_KEY_SCHEMA_ROLLBACK_COMPLETED' AS result;
