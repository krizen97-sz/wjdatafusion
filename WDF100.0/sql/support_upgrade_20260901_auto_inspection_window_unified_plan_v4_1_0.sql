-- 自动化巡检统计窗口与统一计划语义升级 v4.1.0
-- 基线：已完成 v3.16.2 及之后自动化巡检升级
-- 说明：只补充窗口证据字段并更新字段注释，不删除、不重算历史巡检数据。

SET NAMES utf8mb4;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sup_auto_inspection_target_result'
    AND COLUMN_NAME = 'comparison_scope') = 0,
  'ALTER TABLE sup_auto_inspection_target_result ADD COLUMN comparison_scope VARCHAR(16) DEFAULT ''CONTINUOUS'' COMMENT ''比较范围（CONTINUOUS连续 DAY按天 HOUR按小时）'' AFTER baseline_flag',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sup_auto_inspection_target_result'
    AND COLUMN_NAME = 'window_key') = 0,
  'ALTER TABLE sup_auto_inspection_target_result ADD COLUMN window_key VARCHAR(64) DEFAULT NULL COMMENT ''本次统计窗口标识'' AFTER comparison_scope',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sup_auto_inspection_target_result'
    AND COLUMN_NAME = 'window_start') = 0,
  'ALTER TABLE sup_auto_inspection_target_result ADD COLUMN window_start DATETIME DEFAULT NULL COMMENT ''本次统计窗口开始时间'' AFTER window_key',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sup_auto_inspection_target_result'
    AND COLUMN_NAME = 'window_end') = 0,
  'ALTER TABLE sup_auto_inspection_target_result ADD COLUMN window_end DATETIME DEFAULT NULL COMMENT ''本次统计窗口有效结束时间'' AFTER window_start',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sup_auto_inspection_probe_state'
    AND COLUMN_NAME = 'comparison_scope') = 0,
  'ALTER TABLE sup_auto_inspection_probe_state ADD COLUMN comparison_scope VARCHAR(16) DEFAULT ''CONTINUOUS'' COMMENT ''比较范围（CONTINUOUS连续 DAY按天 HOUR按小时）'' AFTER last_activity_at',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sup_auto_inspection_probe_state'
    AND COLUMN_NAME = 'window_key') = 0,
  'ALTER TABLE sup_auto_inspection_probe_state ADD COLUMN window_key VARCHAR(64) DEFAULT NULL COMMENT ''当前统计窗口标识'' AFTER comparison_scope',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sup_auto_inspection_probe_state'
    AND COLUMN_NAME = 'window_start') = 0,
  'ALTER TABLE sup_auto_inspection_probe_state ADD COLUMN window_start DATETIME DEFAULT NULL COMMENT ''当前统计窗口开始时间'' AFTER window_key',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sup_auto_inspection_probe_state'
    AND COLUMN_NAME = 'window_end') = 0,
  'ALTER TABLE sup_auto_inspection_probe_state ADD COLUMN window_end DATETIME DEFAULT NULL COMMENT ''当前统计窗口有效结束时间'' AFTER window_start',
  'SELECT 1');
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

ALTER TABLE sup_auto_inspection_plan
  MODIFY COLUMN plan_mode VARCHAR(16) DEFAULT 'ROUTINE'
  COMMENT '结果汇总方式（ROUTINE逐次记录 FREQUENT每日汇总）';

ALTER TABLE sup_auto_inspection_plan
  MODIFY COLUMN health_config TEXT
  COMMENT '每日健康汇总配置JSON';
