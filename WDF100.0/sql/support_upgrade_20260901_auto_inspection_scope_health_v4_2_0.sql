-- 自动化巡检现场/主平台健康归属升级 v4.2.0
-- 基线：已完成 v4.1.0 及之后自动化巡检升级
-- 说明：新增归属快照并补建已有自动执行记录的每日汇总；不删除计划、记录、步骤或目标明细。

SET NAMES utf8mb4;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sup_auto_inspection_plan' AND COLUMN_NAME = 'scope_type') = 0,
  'ALTER TABLE sup_auto_inspection_plan ADD COLUMN scope_type VARCHAR(20) DEFAULT NULL COMMENT ''健康归属类型（SITE现场 MAIN_PLATFORM主平台）'' AFTER health_config', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sup_auto_inspection_plan' AND COLUMN_NAME = 'site_id') = 0,
  'ALTER TABLE sup_auto_inspection_plan ADD COLUMN site_id BIGINT DEFAULT NULL COMMENT ''所属现场ID'' AFTER scope_type', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sup_auto_inspection_plan' AND COLUMN_NAME = 'site_name') = 0,
  'ALTER TABLE sup_auto_inspection_plan ADD COLUMN site_name VARCHAR(100) DEFAULT NULL COMMENT ''所属现场名称快照'' AFTER site_id', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sup_auto_inspection_plan' AND COLUMN_NAME = 'main_platform_id') = 0,
  'ALTER TABLE sup_auto_inspection_plan ADD COLUMN main_platform_id BIGINT DEFAULT NULL COMMENT ''所属主平台ID'' AFTER site_name', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sup_auto_inspection_plan' AND COLUMN_NAME = 'main_platform_name') = 0,
  'ALTER TABLE sup_auto_inspection_plan ADD COLUMN main_platform_name VARCHAR(120) DEFAULT NULL COMMENT ''所属主平台名称快照'' AFTER main_platform_id', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sup_auto_inspection_record' AND COLUMN_NAME = 'scope_type') = 0,
  'ALTER TABLE sup_auto_inspection_record ADD COLUMN scope_type VARCHAR(20) DEFAULT NULL COMMENT ''执行时健康归属类型快照'' AFTER plan_name', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sup_auto_inspection_record' AND COLUMN_NAME = 'site_id') = 0,
  'ALTER TABLE sup_auto_inspection_record ADD COLUMN site_id BIGINT DEFAULT NULL COMMENT ''执行时所属现场ID快照'' AFTER scope_type', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sup_auto_inspection_record' AND COLUMN_NAME = 'site_name') = 0,
  'ALTER TABLE sup_auto_inspection_record ADD COLUMN site_name VARCHAR(100) DEFAULT NULL COMMENT ''执行时所属现场名称快照'' AFTER site_id', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sup_auto_inspection_record' AND COLUMN_NAME = 'main_platform_id') = 0,
  'ALTER TABLE sup_auto_inspection_record ADD COLUMN main_platform_id BIGINT DEFAULT NULL COMMENT ''执行时所属主平台ID快照'' AFTER site_name', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sup_auto_inspection_record' AND COLUMN_NAME = 'main_platform_name') = 0,
  'ALTER TABLE sup_auto_inspection_record ADD COLUMN main_platform_name VARCHAR(120) DEFAULT NULL COMMENT ''执行时所属主平台名称快照'' AFTER main_platform_id', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sup_auto_inspection_health_daily' AND COLUMN_NAME = 'scope_type') = 0,
  'ALTER TABLE sup_auto_inspection_health_daily ADD COLUMN scope_type VARCHAR(20) DEFAULT NULL COMMENT ''健康归属类型快照'' AFTER template_name', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sup_auto_inspection_health_daily' AND COLUMN_NAME = 'site_id') = 0,
  'ALTER TABLE sup_auto_inspection_health_daily ADD COLUMN site_id BIGINT DEFAULT NULL COMMENT ''所属现场ID快照'' AFTER scope_type', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sup_auto_inspection_health_daily' AND COLUMN_NAME = 'site_name') = 0,
  'ALTER TABLE sup_auto_inspection_health_daily ADD COLUMN site_name VARCHAR(100) DEFAULT NULL COMMENT ''所属现场名称快照'' AFTER site_id', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sup_auto_inspection_health_daily' AND COLUMN_NAME = 'main_platform_id') = 0,
  'ALTER TABLE sup_auto_inspection_health_daily ADD COLUMN main_platform_id BIGINT DEFAULT NULL COMMENT ''所属主平台ID快照'' AFTER site_name', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sup_auto_inspection_health_daily' AND COLUMN_NAME = 'main_platform_name') = 0,
  'ALTER TABLE sup_auto_inspection_health_daily ADD COLUMN main_platform_name VARCHAR(120) DEFAULT NULL COMMENT ''所属主平台名称快照'' AFTER main_platform_id', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sup_auto_inspection_plan' AND INDEX_NAME = 'idx_sup_auto_plan_site_status') = 0,
  'ALTER TABLE sup_auto_inspection_plan ADD INDEX idx_sup_auto_plan_site_status(site_id, status)', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sup_auto_inspection_plan' AND INDEX_NAME = 'idx_sup_auto_plan_main_status') = 0,
  'ALTER TABLE sup_auto_inspection_plan ADD INDEX idx_sup_auto_plan_main_status(main_platform_id, status)', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sup_auto_inspection_record' AND INDEX_NAME = 'idx_sup_auto_record_site_time') = 0,
  'ALTER TABLE sup_auto_inspection_record ADD INDEX idx_sup_auto_record_site_time(site_id, inspection_time)', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sup_auto_inspection_record' AND INDEX_NAME = 'idx_sup_auto_record_main_time') = 0,
  'ALTER TABLE sup_auto_inspection_record ADD INDEX idx_sup_auto_record_main_time(main_platform_id, inspection_time)', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sup_auto_inspection_health_daily' AND INDEX_NAME = 'idx_sup_auto_health_site_date') = 0,
  'ALTER TABLE sup_auto_inspection_health_daily ADD INDEX idx_sup_auto_health_site_date(site_id, health_date)', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sup_auto_inspection_health_daily' AND INDEX_NAME = 'idx_sup_auto_health_main_date') = 0,
  'ALTER TABLE sup_auto_inspection_health_daily ADD INDEX idx_sup_auto_health_main_date(main_platform_id, health_date)', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

UPDATE sup_auto_inspection_record r
JOIN sup_auto_inspection_plan p ON p.plan_id = r.plan_id
SET r.scope_type = p.scope_type,
    r.site_id = p.site_id,
    r.site_name = p.site_name,
    r.main_platform_id = p.main_platform_id,
    r.main_platform_name = p.main_platform_name
WHERE r.scope_type IS NULL AND p.scope_type IS NOT NULL;

INSERT IGNORE INTO sup_auto_inspection_health_daily(
  health_date, plan_id, plan_name, template_id, template_name,
  scope_type, site_id, site_name, main_platform_id, main_platform_name,
  expected_count, completed_count, normal_count, warning_count, abnormal_count, skipped_count,
  missing_count, health_score, health_target, day_status,
  first_abnormal_time, last_abnormal_time, last_run_time, last_result_status, abnormal_summary,
  create_by, create_time, update_by, update_time
)
SELECT DATE(r.inspection_time), r.plan_id, COALESCE(MAX(r.plan_name), MAX(p.plan_name), '未命名计划'),
       MAX(r.template_id), MAX(r.template_name),
       MAX(COALESCE(r.scope_type, p.scope_type)), MAX(COALESCE(r.site_id, p.site_id)),
       MAX(COALESCE(r.site_name, p.site_name)), MAX(COALESCE(r.main_platform_id, p.main_platform_id)),
       MAX(COALESCE(r.main_platform_name, p.main_platform_name)),
       COUNT(1), COUNT(1),
       SUM(CASE WHEN r.result_status = '1' THEN 1 ELSE 0 END),
       SUM(CASE WHEN r.result_status = '4' THEN 1 ELSE 0 END),
       SUM(CASE WHEN r.result_status = '2' THEN 1 ELSE 0 END),
       SUM(CASE WHEN r.result_status = '3' THEN 1 ELSE 0 END),
       0,
       ROUND(SUM(CASE WHEN r.result_status = '1' THEN 1 ELSE 0 END) * 100 / COUNT(1), 2),
       99,
       CASE
         WHEN SUM(CASE WHEN r.result_status = '2' THEN 1 ELSE 0 END) > 0 THEN '2'
         WHEN SUM(CASE WHEN r.result_status = '4' THEN 1 ELSE 0 END) > 0 THEN '4'
         WHEN SUM(CASE WHEN r.result_status = '1' THEN 1 ELSE 0 END) > 0 THEN '1'
         ELSE '3'
       END,
       MIN(CASE WHEN r.result_status = '2' THEN r.inspection_time END),
       MAX(CASE WHEN r.result_status = '2' THEN r.inspection_time END),
       MAX(r.inspection_time),
       SUBSTRING_INDEX(GROUP_CONCAT(r.result_status ORDER BY r.inspection_time DESC, r.record_id DESC), ',', 1),
       SUBSTRING_INDEX(GROUP_CONCAT(CASE WHEN r.result_status IN ('2', '4') THEN r.abnormal_summary END ORDER BY r.inspection_time DESC, r.record_id DESC SEPARATOR '||'), '||', 1),
       'system', NOW(), 'system', NOW()
FROM sup_auto_inspection_record r
LEFT JOIN sup_auto_inspection_plan p ON p.plan_id = r.plan_id
WHERE r.source_type = 'AUTO' AND r.plan_id IS NOT NULL
GROUP BY DATE(r.inspection_time), r.plan_id;

UPDATE sup_auto_inspection_health_daily h
JOIN sup_auto_inspection_plan p ON p.plan_id = h.plan_id
SET h.scope_type = p.scope_type,
    h.site_id = p.site_id,
    h.site_name = p.site_name,
    h.main_platform_id = p.main_platform_id,
    h.main_platform_name = p.main_platform_name
WHERE h.scope_type IS NULL AND p.scope_type IS NOT NULL;

ALTER TABLE sup_auto_inspection_plan
  MODIFY COLUMN plan_mode VARCHAR(16) DEFAULT 'ROUTINE' COMMENT '历史执行模式兼容字段（ROUTINE/FREQUENT）';
ALTER TABLE sup_auto_inspection_record
  MODIFY COLUMN run_mode VARCHAR(16) DEFAULT 'ROUTINE' COMMENT '历史执行模式兼容字段（ROUTINE/FREQUENT）';
ALTER TABLE sup_auto_inspection_health_daily COMMENT='自动化巡检计划每日健康汇总';
