-- v3.3.6 现场融合管理设备资产登录信息与统一维护入口
-- 说明：新增非服务器硬件资产登录账号、密码密文字段；原有数据保持为空，不影响历史设备资产。

SET @has_login_username := (
  SELECT COUNT(1)
  FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'sup_hardware_asset' AND column_name = 'login_username'
);
SET @sql_login_username := IF(
  @has_login_username = 0,
  'ALTER TABLE sup_hardware_asset ADD COLUMN login_username VARCHAR(128) DEFAULT NULL COMMENT ''设备登录账号'' AFTER owner_contact',
  'SELECT 1'
);
PREPARE stmt FROM @sql_login_username;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_login_password_cipher := (
  SELECT COUNT(1)
  FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'sup_hardware_asset' AND column_name = 'login_password_cipher'
);
SET @sql_login_password_cipher := IF(
  @has_login_password_cipher = 0,
  'ALTER TABLE sup_hardware_asset ADD COLUMN login_password_cipher VARCHAR(1024) DEFAULT NULL COMMENT ''设备登录密码密文'' AFTER login_username',
  'SELECT 1'
);
PREPARE stmt FROM @sql_login_password_cipher;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
