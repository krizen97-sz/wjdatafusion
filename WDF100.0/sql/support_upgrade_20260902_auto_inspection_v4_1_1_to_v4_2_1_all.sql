-- 自动化巡检 v4.1.1 至 v4.2.1 累计结构升级脚本
-- 适用基线：应用版本 v4.1.1；脚本同时防御性补齐 v4.1.0 统计窗口字段。
-- 执行顺序：先执行本脚本，再执行 support_migrate_20260902_auto_inspection_scope_data_v4_2_1.sql。
-- 安全边界：只新增字段、索引和更新字段注释；不删除表、列、计划、记录、步骤或目标明细。

SET NAMES utf8mb4;

-- 一、v4.1.x 统计窗口证据字段（防御性补齐）
SET @parts = CONCAT_WS(', ',
  IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sup_auto_inspection_target_result' AND COLUMN_NAME = 'comparison_scope') = 0,
    'ADD COLUMN comparison_scope VARCHAR(16) DEFAULT ''CONTINUOUS'' COMMENT ''比较范围（CONTINUOUS连续 DAY按天 HOUR按小时）'' AFTER baseline_flag', NULL),
  IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sup_auto_inspection_target_result' AND COLUMN_NAME = 'window_key') = 0,
    'ADD COLUMN window_key VARCHAR(64) DEFAULT NULL COMMENT ''本次统计窗口标识'' AFTER comparison_scope', NULL),
  IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sup_auto_inspection_target_result' AND COLUMN_NAME = 'window_start') = 0,
    'ADD COLUMN window_start DATETIME DEFAULT NULL COMMENT ''本次统计窗口开始时间'' AFTER window_key', NULL),
  IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sup_auto_inspection_target_result' AND COLUMN_NAME = 'window_end') = 0,
    'ADD COLUMN window_end DATETIME DEFAULT NULL COMMENT ''本次统计窗口有效结束时间'' AFTER window_start', NULL));
SET @ddl = IF(@parts = '', 'SELECT 1', CONCAT('ALTER TABLE sup_auto_inspection_target_result ', @parts));
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @parts = CONCAT_WS(', ',
  IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sup_auto_inspection_probe_state' AND COLUMN_NAME = 'comparison_scope') = 0,
    'ADD COLUMN comparison_scope VARCHAR(16) DEFAULT ''CONTINUOUS'' COMMENT ''比较范围（CONTINUOUS连续 DAY按天 HOUR按小时）'' AFTER last_activity_at', NULL),
  IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sup_auto_inspection_probe_state' AND COLUMN_NAME = 'window_key') = 0,
    'ADD COLUMN window_key VARCHAR(64) DEFAULT NULL COMMENT ''当前统计窗口标识'' AFTER comparison_scope', NULL),
  IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sup_auto_inspection_probe_state' AND COLUMN_NAME = 'window_start') = 0,
    'ADD COLUMN window_start DATETIME DEFAULT NULL COMMENT ''当前统计窗口开始时间'' AFTER window_key', NULL),
  IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sup_auto_inspection_probe_state' AND COLUMN_NAME = 'window_end') = 0,
    'ADD COLUMN window_end DATETIME DEFAULT NULL COMMENT ''当前统计窗口有效结束时间'' AFTER window_start', NULL));
SET @ddl = IF(@parts = '', 'SELECT 1', CONCAT('ALTER TABLE sup_auto_inspection_probe_state ', @parts));
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

UPDATE sup_auto_inspection_target_result
SET comparison_scope = 'CONTINUOUS'
WHERE comparison_scope IS NULL OR comparison_scope = '';

UPDATE sup_auto_inspection_probe_state
SET comparison_scope = 'CONTINUOUS',
    window_key = CASE WHEN window_key IS NULL OR window_key = '' THEN 'CONTINUOUS' ELSE window_key END,
    window_end = COALESCE(window_end, observed_at)
WHERE comparison_scope IS NULL OR comparison_scope = ''
   OR window_key IS NULL OR window_key = ''
   OR window_end IS NULL;

-- 二、v4.2.x 现场与主平台健康归属字段
SET @parts = CONCAT_WS(', ',
  IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sup_auto_inspection_plan' AND COLUMN_NAME = 'scope_type') = 0,
    'ADD COLUMN scope_type VARCHAR(20) DEFAULT NULL COMMENT ''健康归属类型（SITE现场 MAIN_PLATFORM主平台）'' AFTER health_config', NULL),
  IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sup_auto_inspection_plan' AND COLUMN_NAME = 'site_id') = 0,
    'ADD COLUMN site_id BIGINT DEFAULT NULL COMMENT ''所属现场ID'' AFTER scope_type', NULL),
  IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sup_auto_inspection_plan' AND COLUMN_NAME = 'site_name') = 0,
    'ADD COLUMN site_name VARCHAR(100) DEFAULT NULL COMMENT ''所属现场名称快照'' AFTER site_id', NULL),
  IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sup_auto_inspection_plan' AND COLUMN_NAME = 'main_platform_id') = 0,
    'ADD COLUMN main_platform_id BIGINT DEFAULT NULL COMMENT ''所属主平台ID'' AFTER site_name', NULL),
  IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sup_auto_inspection_plan' AND COLUMN_NAME = 'main_platform_name') = 0,
    'ADD COLUMN main_platform_name VARCHAR(120) DEFAULT NULL COMMENT ''所属主平台名称快照'' AFTER main_platform_id', NULL));
SET @ddl = IF(@parts = '', 'SELECT 1', CONCAT('ALTER TABLE sup_auto_inspection_plan ', @parts));
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @parts = CONCAT_WS(', ',
  IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sup_auto_inspection_record' AND COLUMN_NAME = 'scope_type') = 0,
    'ADD COLUMN scope_type VARCHAR(20) DEFAULT NULL COMMENT ''执行时健康归属类型快照'' AFTER plan_name', NULL),
  IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sup_auto_inspection_record' AND COLUMN_NAME = 'site_id') = 0,
    'ADD COLUMN site_id BIGINT DEFAULT NULL COMMENT ''执行时所属现场ID快照'' AFTER scope_type', NULL),
  IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sup_auto_inspection_record' AND COLUMN_NAME = 'site_name') = 0,
    'ADD COLUMN site_name VARCHAR(100) DEFAULT NULL COMMENT ''执行时所属现场名称快照'' AFTER site_id', NULL),
  IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sup_auto_inspection_record' AND COLUMN_NAME = 'main_platform_id') = 0,
    'ADD COLUMN main_platform_id BIGINT DEFAULT NULL COMMENT ''执行时所属主平台ID快照'' AFTER site_name', NULL),
  IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sup_auto_inspection_record' AND COLUMN_NAME = 'main_platform_name') = 0,
    'ADD COLUMN main_platform_name VARCHAR(120) DEFAULT NULL COMMENT ''执行时所属主平台名称快照'' AFTER main_platform_id', NULL));
SET @ddl = IF(@parts = '', 'SELECT 1', CONCAT('ALTER TABLE sup_auto_inspection_record ', @parts));
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @parts = CONCAT_WS(', ',
  IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sup_auto_inspection_health_daily' AND COLUMN_NAME = 'scope_type') = 0,
    'ADD COLUMN scope_type VARCHAR(20) DEFAULT NULL COMMENT ''健康归属类型快照'' AFTER template_name', NULL),
  IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sup_auto_inspection_health_daily' AND COLUMN_NAME = 'site_id') = 0,
    'ADD COLUMN site_id BIGINT DEFAULT NULL COMMENT ''所属现场ID快照'' AFTER scope_type', NULL),
  IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sup_auto_inspection_health_daily' AND COLUMN_NAME = 'site_name') = 0,
    'ADD COLUMN site_name VARCHAR(100) DEFAULT NULL COMMENT ''所属现场名称快照'' AFTER site_id', NULL),
  IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sup_auto_inspection_health_daily' AND COLUMN_NAME = 'main_platform_id') = 0,
    'ADD COLUMN main_platform_id BIGINT DEFAULT NULL COMMENT ''所属主平台ID快照'' AFTER site_name', NULL),
  IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sup_auto_inspection_health_daily' AND COLUMN_NAME = 'main_platform_name') = 0,
    'ADD COLUMN main_platform_name VARCHAR(120) DEFAULT NULL COMMENT ''所属主平台名称快照'' AFTER main_platform_id', NULL));
SET @ddl = IF(@parts = '', 'SELECT 1', CONCAT('ALTER TABLE sup_auto_inspection_health_daily ', @parts));
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 三、v4.2.x 归属查询索引
SET @parts = CONCAT_WS(', ',
  IF((SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sup_auto_inspection_plan' AND INDEX_NAME = 'idx_sup_auto_plan_site_status') = 0,
    'ADD INDEX idx_sup_auto_plan_site_status(site_id, status)', NULL),
  IF((SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sup_auto_inspection_plan' AND INDEX_NAME = 'idx_sup_auto_plan_main_status') = 0,
    'ADD INDEX idx_sup_auto_plan_main_status(main_platform_id, status)', NULL));
SET @ddl = IF(@parts = '', 'SELECT 1', CONCAT('ALTER TABLE sup_auto_inspection_plan ', @parts));
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @parts = CONCAT_WS(', ',
  IF((SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sup_auto_inspection_record' AND INDEX_NAME = 'idx_sup_auto_record_site_time') = 0,
    'ADD INDEX idx_sup_auto_record_site_time(site_id, inspection_time)', NULL),
  IF((SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sup_auto_inspection_record' AND INDEX_NAME = 'idx_sup_auto_record_main_time') = 0,
    'ADD INDEX idx_sup_auto_record_main_time(main_platform_id, inspection_time)', NULL));
SET @ddl = IF(@parts = '', 'SELECT 1', CONCAT('ALTER TABLE sup_auto_inspection_record ', @parts));
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @parts = CONCAT_WS(', ',
  IF((SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sup_auto_inspection_health_daily' AND INDEX_NAME = 'idx_sup_auto_health_site_date') = 0,
    'ADD INDEX idx_sup_auto_health_site_date(site_id, health_date)', NULL),
  IF((SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sup_auto_inspection_health_daily' AND INDEX_NAME = 'idx_sup_auto_health_main_date') = 0,
    'ADD INDEX idx_sup_auto_health_main_date(main_platform_id, health_date)', NULL));
SET @ddl = IF(@parts = '', 'SELECT 1', CONCAT('ALTER TABLE sup_auto_inspection_health_daily ', @parts));
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 四、字段语义统一
ALTER TABLE sup_auto_inspection_plan
  MODIFY COLUMN plan_mode VARCHAR(16) DEFAULT 'ROUTINE' COMMENT '历史执行模式兼容字段（ROUTINE/FREQUENT）';
ALTER TABLE sup_auto_inspection_plan
  MODIFY COLUMN health_config TEXT COMMENT '每日健康汇总配置JSON';
ALTER TABLE sup_auto_inspection_record
  MODIFY COLUMN run_mode VARCHAR(16) DEFAULT 'ROUTINE' COMMENT '历史执行模式兼容字段（ROUTINE/FREQUENT）';
ALTER TABLE sup_auto_inspection_health_daily COMMENT='自动化巡检计划每日健康汇总';

-- 五、结构核验。每张归属表应返回5个字段，每组索引应返回2个索引。
SELECT table_name, COUNT(*) AS scope_column_count
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME IN ('sup_auto_inspection_plan', 'sup_auto_inspection_record', 'sup_auto_inspection_health_daily')
  AND COLUMN_NAME IN ('scope_type', 'site_id', 'site_name', 'main_platform_id', 'main_platform_name')
GROUP BY table_name
ORDER BY table_name;

SELECT table_name, COUNT(DISTINCT index_name) AS scope_index_count
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = DATABASE()
  AND index_name IN ('idx_sup_auto_plan_site_status', 'idx_sup_auto_plan_main_status',
                     'idx_sup_auto_record_site_time', 'idx_sup_auto_record_main_time',
                     'idx_sup_auto_health_site_date', 'idx_sup_auto_health_main_date')
GROUP BY table_name
ORDER BY table_name;

SELECT '结构升级完成；请继续执行 support_migrate_20260902_auto_inspection_scope_data_v4_2_1.sql' AS next_step;
