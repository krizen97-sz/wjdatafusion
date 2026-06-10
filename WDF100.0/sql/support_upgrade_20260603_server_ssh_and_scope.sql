-- ============================================================================
-- 服务器SSH端口与子平台绑定口径升级
-- 说明：
-- 1. 为服务器增加 SSH 端口字段，默认 22。
-- 2. 增加 site_id + server_address 普通索引，配合服务层防止新增重复服务器。
-- 3. 不删除、不迁移已有主平台服务器关系；新版本服务层会阻止继续绑定到主平台。

SET @has_server_ssh_port := (
  SELECT COUNT(1)
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'sup_server'
    AND column_name = 'ssh_port'
);

SET @sql := IF(
  @has_server_ssh_port = 0,
  'ALTER TABLE sup_server ADD COLUMN ssh_port INT DEFAULT 22 COMMENT ''SSH端口'' AFTER server_address',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE sup_server
SET ssh_port = 22
WHERE ssh_port IS NULL;

SET @has_server_site_address_index := (
  SELECT COUNT(1)
  FROM information_schema.statistics
  WHERE table_schema = DATABASE()
    AND table_name = 'sup_server'
    AND index_name = 'idx_sup_server_site_address'
);

SET @sql := IF(
  @has_server_site_address_index = 0,
  'ALTER TABLE sup_server ADD INDEX idx_sup_server_site_address (site_id, server_address)',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
