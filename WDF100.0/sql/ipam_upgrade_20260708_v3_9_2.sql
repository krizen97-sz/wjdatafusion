-- v3.9.2 IP分配管控业务模型调整
-- 说明：
-- 1. 本脚本只修改 ipam_ 独立业务表与 IP分配管控 菜单权限，不修改 sup_* 现场融合业务表。
-- 2. 增加网关IP、小区内网IP、接入单位、账号与映射信息字段。
-- 3. 取消下发业务语义，将历史 ISSUED 状态统一迁移为 ALLOCATED，并停用 ipam:address:issue 权限。

SET @ipam_db_name := DATABASE();

SET @has_segment_gateway_ip := (
  SELECT COUNT(1)
  FROM information_schema.columns
  WHERE table_schema = @ipam_db_name
    AND table_name = 'ipam_segment'
    AND column_name = 'gateway_ip'
);
SET @sql := IF(
  @has_segment_gateway_ip = 0,
  'ALTER TABLE ipam_segment ADD COLUMN gateway_ip VARCHAR(64) DEFAULT NULL COMMENT ''网关IP'' AFTER end_ip',
  'SELECT ''gateway_ip exists'' AS message'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_address_internal_ip := (
  SELECT COUNT(1)
  FROM information_schema.columns
  WHERE table_schema = @ipam_db_name
    AND table_name = 'ipam_address'
    AND column_name = 'internal_ip_address'
);
SET @sql := IF(
  @has_address_internal_ip = 0,
  'ALTER TABLE ipam_address ADD COLUMN internal_ip_address VARCHAR(128) DEFAULT NULL COMMENT ''小区内网IP'' AFTER manufacturer',
  'SELECT ''internal_ip_address exists'' AS message'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_address_access_unit := (
  SELECT COUNT(1)
  FROM information_schema.columns
  WHERE table_schema = @ipam_db_name
    AND table_name = 'ipam_address'
    AND column_name = 'access_unit'
);
SET @sql := IF(
  @has_address_access_unit = 0,
  'ALTER TABLE ipam_address ADD COLUMN access_unit VARCHAR(80) DEFAULT NULL COMMENT ''接入单位'' AFTER internal_ip_address',
  'SELECT ''access_unit exists'' AS message'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_address_login_username := (
  SELECT COUNT(1)
  FROM information_schema.columns
  WHERE table_schema = @ipam_db_name
    AND table_name = 'ipam_address'
    AND column_name = 'login_username'
);
SET @sql := IF(
  @has_address_login_username = 0,
  'ALTER TABLE ipam_address ADD COLUMN login_username VARCHAR(120) DEFAULT NULL COMMENT ''登录账号'' AFTER purpose',
  'SELECT ''login_username exists'' AS message'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_address_login_password := (
  SELECT COUNT(1)
  FROM information_schema.columns
  WHERE table_schema = @ipam_db_name
    AND table_name = 'ipam_address'
    AND column_name = 'login_password'
);
SET @sql := IF(
  @has_address_login_password = 0,
  'ALTER TABLE ipam_address ADD COLUMN login_password VARCHAR(200) DEFAULT NULL COMMENT ''登录密码'' AFTER login_username',
  'SELECT ''login_password exists'' AS message'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_address_mapping_address := (
  SELECT COUNT(1)
  FROM information_schema.columns
  WHERE table_schema = @ipam_db_name
    AND table_name = 'ipam_address'
    AND column_name = 'mapping_address'
);
SET @sql := IF(
  @has_address_mapping_address = 0,
  'ALTER TABLE ipam_address ADD COLUMN mapping_address VARCHAR(128) DEFAULT NULL COMMENT ''映射地址'' AFTER login_password',
  'SELECT ''mapping_address exists'' AS message'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_address_mapping_port := (
  SELECT COUNT(1)
  FROM information_schema.columns
  WHERE table_schema = @ipam_db_name
    AND table_name = 'ipam_address'
    AND column_name = 'mapping_port'
);
SET @sql := IF(
  @has_address_mapping_port = 0,
  'ALTER TABLE ipam_address ADD COLUMN mapping_port VARCHAR(80) DEFAULT NULL COMMENT ''映射端口'' AFTER mapping_address',
  'SELECT ''mapping_port exists'' AS message'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_address_mapping_description := (
  SELECT COUNT(1)
  FROM information_schema.columns
  WHERE table_schema = @ipam_db_name
    AND table_name = 'ipam_address'
    AND column_name = 'mapping_description'
);
SET @sql := IF(
  @has_address_mapping_description = 0,
  'ALTER TABLE ipam_address ADD COLUMN mapping_description VARCHAR(500) DEFAULT NULL COMMENT ''映射说明'' AFTER mapping_port',
  'SELECT ''mapping_description exists'' AS message'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_internal_ip_index := (
  SELECT COUNT(1)
  FROM information_schema.statistics
  WHERE table_schema = @ipam_db_name
    AND table_name = 'ipam_address'
    AND index_name = 'idx_ipam_address_internal_ip'
);
SET @sql := IF(
  @has_internal_ip_index = 0,
  'ALTER TABLE ipam_address ADD INDEX idx_ipam_address_internal_ip (internal_ip_address)',
  'SELECT ''idx_ipam_address_internal_ip exists'' AS message'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE ipam_address
SET status = 'ALLOCATED',
    issue_batch = NULL,
    issued_time = NULL
WHERE status = 'ISSUED';

UPDATE sys_menu
SET status = '1',
    visible = '1',
    update_time = NOW(),
    remark = 'v3.9.2 已取消IP下发逻辑'
WHERE perms = 'ipam:address:issue';

UPDATE sys_menu
SET menu_name = '地址占用',
    update_time = NOW()
WHERE perms = 'ipam:address:allocate';

UPDATE sys_menu
SET menu_name = '地址导出',
    update_time = NOW()
WHERE perms = 'ipam:address:export';
