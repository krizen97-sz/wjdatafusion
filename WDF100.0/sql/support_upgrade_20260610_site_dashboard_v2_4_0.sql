-- 现场融合管理 v2.4.0 datafusion 用户首页
-- 执行日期：2026-06-10
-- 说明：补充首页聚合查询索引；不新增业务表、不修改已有业务数据，可重复执行。

SET @support_db_name := DATABASE();

SET @idx_exists := (
  SELECT COUNT(1)
  FROM information_schema.statistics
  WHERE table_schema = @support_db_name
    AND table_name = 'sup_site'
    AND index_name = 'idx_sup_site_create_by'
);

SET @ddl := IF(
  @idx_exists = 0,
  'ALTER TABLE sup_site ADD INDEX idx_sup_site_create_by (create_by, site_id)',
  'SELECT ''idx_sup_site_create_by exists'' AS message'
);

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx_exists := (
  SELECT COUNT(1)
  FROM information_schema.statistics
  WHERE table_schema = @support_db_name
    AND table_name = 'sup_site'
    AND index_name = 'idx_sup_site_update_by'
);

SET @ddl := IF(
  @idx_exists = 0,
  'ALTER TABLE sup_site ADD INDEX idx_sup_site_update_by (update_by, site_id)',
  'SELECT ''idx_sup_site_update_by exists'' AS message'
);

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx_exists := (
  SELECT COUNT(1)
  FROM information_schema.statistics
  WHERE table_schema = @support_db_name
    AND table_name = 'sup_change_log'
    AND index_name = 'idx_sup_change_log_operator_action_site'
);

SET @ddl := IF(
  @idx_exists = 0,
  'ALTER TABLE sup_change_log ADD INDEX idx_sup_change_log_operator_action_site (operator_name, action_type, site_id, log_id)',
  'SELECT ''idx_sup_change_log_operator_action_site exists'' AS message'
);

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx_exists := (
  SELECT COUNT(1)
  FROM information_schema.statistics
  WHERE table_schema = @support_db_name
    AND table_name = 'sup_change_log'
    AND index_name = 'idx_sup_change_log_action_time'
);

SET @ddl := IF(
  @idx_exists = 0,
  'ALTER TABLE sup_change_log ADD INDEX idx_sup_change_log_action_time (action_type, create_time, log_id)',
  'SELECT ''idx_sup_change_log_action_time exists'' AS message'
);

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
