-- v3.16.2 自动化巡检高频历史基线修复
-- 说明：只修正能够从目标详情明确识别为首次/重建基线的旧高频记录。
-- 不修改普通跳过、停用步骤、配置缺失、连接失败和业务异常记录，可重复执行。

SET NAMES utf8mb4;

-- 兼容尚未单独执行 v3.16.0 的环境，补齐新代码读取的结构化判定字段。
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sup_auto_inspection_target_result' AND COLUMN_NAME = 'evaluation_mode') = 0,
  'ALTER TABLE sup_auto_inspection_target_result ADD COLUMN evaluation_mode VARCHAR(16) DEFAULT ''FIXED'' COMMENT ''判定方式（FIXED固定阈值 PREVIOUS上次结果）'' AFTER actual_unit', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sup_auto_inspection_target_result' AND COLUMN_NAME = 'previous_value') = 0,
  'ALTER TABLE sup_auto_inspection_target_result ADD COLUMN previous_value DECIMAL(30,2) DEFAULT NULL COMMENT ''上次采样值'' AFTER evaluation_mode', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sup_auto_inspection_target_result' AND COLUMN_NAME = 'change_value') = 0,
  'ALTER TABLE sup_auto_inspection_target_result ADD COLUMN change_value DECIMAL(30,2) DEFAULT NULL COMMENT ''本次与上次变化量'' AFTER previous_value', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sup_auto_inspection_target_result' AND COLUMN_NAME = 'evaluation_rule') = 0,
  'ALTER TABLE sup_auto_inspection_target_result ADD COLUMN evaluation_rule VARCHAR(500) DEFAULT NULL COMMENT ''本次判定公式'' AFTER change_value', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sup_auto_inspection_target_result' AND COLUMN_NAME = 'baseline_flag') = 0,
  'ALTER TABLE sup_auto_inspection_target_result ADD COLUMN baseline_flag CHAR(1) DEFAULT ''N'' COMMENT ''是否本次建立基线（Y是 N否）'' AFTER evaluation_rule', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

DROP TEMPORARY TABLE IF EXISTS tmp_v3162_baseline_targets;
CREATE TEMPORARY TABLE tmp_v3162_baseline_targets (
  result_id BIGINT NOT NULL PRIMARY KEY,
  step_result_id BIGINT NOT NULL,
  record_id BIGINT NOT NULL,
  target_id BIGINT DEFAULT NULL,
  plan_id BIGINT DEFAULT NULL,
  health_date DATE NOT NULL
) ENGINE=MEMORY;

INSERT INTO tmp_v3162_baseline_targets(result_id, step_result_id, record_id, target_id, plan_id, health_date)
SELECT tr.result_id, tr.step_result_id, tr.record_id, tr.target_id, r.plan_id, DATE(r.inspection_time)
FROM sup_auto_inspection_target_result tr
INNER JOIN sup_auto_inspection_record r ON r.record_id = tr.record_id
WHERE r.run_mode = 'FREQUENT'
  AND r.source_type = 'AUTO'
  AND tr.result_status = '3'
  AND (
    tr.result_detail LIKE '%已建立首次采样基线%'
    OR tr.result_detail LIKE '%已重新建立基线%'
  );

UPDATE sup_auto_inspection_target_result tr
INNER JOIN tmp_v3162_baseline_targets t ON t.result_id = tr.result_id
SET tr.result_status = '1',
    tr.evaluation_mode = 'PREVIOUS',
    tr.baseline_flag = 'Y',
    tr.evaluation_rule = CASE
      WHEN tr.evaluation_rule IS NULL OR tr.evaluation_rule = ''
        THEN '历史首次采样建立基线，本次按正常计入健康度'
      ELSE tr.evaluation_rule
    END,
    tr.update_by = 'system',
    tr.update_time = NOW();

DROP TEMPORARY TABLE IF EXISTS tmp_v3162_baseline_steps;
CREATE TEMPORARY TABLE tmp_v3162_baseline_steps (
  step_result_id BIGINT NOT NULL PRIMARY KEY
) ENGINE=MEMORY;
INSERT INTO tmp_v3162_baseline_steps(step_result_id)
SELECT DISTINCT step_result_id FROM tmp_v3162_baseline_targets;

UPDATE sup_auto_inspection_step_result sr
INNER JOIN tmp_v3162_baseline_steps t ON t.step_result_id = sr.step_result_id
SET sr.result_status = '1',
    sr.result_summary = CASE
      WHEN sr.result_summary LIKE '%正在建立活性基线%'
        THEN REPLACE(sr.result_summary, '正在建立活性基线', '已建立对照基线并按正常计入')
      ELSE sr.result_summary
    END,
    sr.update_by = 'system',
    sr.update_time = NOW()
WHERE sr.result_status = '3'
  AND EXISTS (
    SELECT 1 FROM sup_auto_inspection_target_result tr
    WHERE tr.step_result_id = sr.step_result_id
  )
  AND NOT EXISTS (
    SELECT 1 FROM sup_auto_inspection_target_result tr
    WHERE tr.step_result_id = sr.step_result_id AND tr.result_status <> '1'
  );

DROP TEMPORARY TABLE IF EXISTS tmp_v3162_baseline_records;
CREATE TEMPORARY TABLE tmp_v3162_baseline_records (
  record_id BIGINT NOT NULL PRIMARY KEY,
  plan_id BIGINT DEFAULT NULL,
  health_date DATE NOT NULL
) ENGINE=MEMORY;
INSERT INTO tmp_v3162_baseline_records(record_id, plan_id, health_date)
SELECT DISTINCT record_id, plan_id, health_date FROM tmp_v3162_baseline_targets;

UPDATE sup_auto_inspection_record r
INNER JOIN tmp_v3162_baseline_records t ON t.record_id = r.record_id
SET r.result_status = '1',
    r.warning_count = 0,
    r.abnormal_count = 0,
    r.abnormal_summary = '无异常',
    r.update_by = 'system',
    r.update_time = NOW()
WHERE r.result_status = '3'
  AND EXISTS (
    SELECT 1 FROM sup_auto_inspection_step_result sr
    WHERE sr.record_id = r.record_id AND sr.enabled_flag = 'Y' AND sr.result_status = '1'
  )
  AND NOT EXISTS (
    SELECT 1 FROM sup_auto_inspection_step_result sr
    WHERE sr.record_id = r.record_id AND sr.enabled_flag = 'Y' AND sr.result_status <> '1'
  );

UPDATE sup_auto_inspection_probe_state ps
INNER JOIN tmp_v3162_baseline_targets t
  ON t.plan_id = ps.plan_id AND t.target_id = ps.target_id
INNER JOIN sup_auto_inspection_step_result sr
  ON sr.step_result_id = t.step_result_id AND sr.step_id = ps.step_id AND sr.tool_code = ps.tool_code
SET ps.state_status = '1',
    ps.state_detail = CASE
      WHEN ps.state_detail LIKE '%已建立首次采样基线%' THEN REPLACE(ps.state_detail, '已建立首次采样基线', '已建立首次采样基线，本次按正常计入健康度')
      WHEN ps.state_detail LIKE '%已重新建立基线%' THEN REPLACE(ps.state_detail, '已重新建立基线', '已重新建立基线，本次按正常计入健康度')
      ELSE ps.state_detail
    END,
    ps.update_by = 'system',
    ps.update_time = NOW()
WHERE ps.state_status = '3'
  AND (ps.state_detail LIKE '%已建立首次采样基线%' OR ps.state_detail LIKE '%已重新建立基线%');

DROP TEMPORARY TABLE IF EXISTS tmp_v3162_daily_stats;
CREATE TEMPORARY TABLE tmp_v3162_daily_stats ENGINE=InnoDB
SELECT d.plan_id,
       d.health_date,
       COUNT(r.record_id) AS completed_count,
       SUM(CASE WHEN r.result_status = '1' THEN 1 ELSE 0 END) AS normal_count,
       SUM(CASE WHEN r.result_status = '4' THEN 1 ELSE 0 END) AS warning_count,
       SUM(CASE WHEN r.result_status = '2' THEN 1 ELSE 0 END) AS abnormal_count,
       SUM(CASE WHEN r.result_status = '3' THEN 1 ELSE 0 END) AS skipped_count,
       MIN(CASE WHEN r.result_status = '2' THEN r.inspection_time END) AS first_abnormal_time,
       MAX(CASE WHEN r.result_status = '2' THEN r.inspection_time END) AS last_abnormal_time,
       MAX(r.inspection_time) AS last_run_time,
       SUBSTRING_INDEX(GROUP_CONCAT(r.result_status ORDER BY r.inspection_time DESC, r.record_id DESC), ',', 1) AS last_result_status,
       SUBSTRING_INDEX(GROUP_CONCAT(CASE WHEN r.result_status IN ('2', '4') THEN r.abnormal_summary END ORDER BY r.inspection_time DESC, r.record_id DESC SEPARATOR '||'), '||', 1) AS abnormal_summary
FROM (
  SELECT DISTINCT plan_id, health_date
  FROM tmp_v3162_baseline_records
  WHERE plan_id IS NOT NULL
) d
INNER JOIN sup_auto_inspection_record r
  ON r.plan_id = d.plan_id
  AND r.run_mode = 'FREQUENT'
  AND r.source_type = 'AUTO'
  AND r.inspection_time >= d.health_date
  AND r.inspection_time < DATE_ADD(d.health_date, INTERVAL 1 DAY)
GROUP BY d.plan_id, d.health_date;

UPDATE sup_auto_inspection_health_daily h
INNER JOIN tmp_v3162_daily_stats s
  ON s.plan_id = h.plan_id AND s.health_date = h.health_date
SET h.completed_count = s.completed_count,
    h.normal_count = s.normal_count,
    h.warning_count = s.warning_count,
    h.abnormal_count = s.abnormal_count,
    h.skipped_count = s.skipped_count,
    h.missing_count = GREATEST(COALESCE(h.expected_count, 0) - s.completed_count, 0),
    h.health_score = CASE
      WHEN GREATEST(COALESCE(h.expected_count, 0), s.completed_count) > 0
        THEN ROUND(s.normal_count * 100 / GREATEST(COALESCE(h.expected_count, 0), s.completed_count), 2)
      ELSE 0
    END,
    h.day_status = CASE
      WHEN s.abnormal_count > 0 THEN '2'
      WHEN s.warning_count > 0 OR GREATEST(COALESCE(h.expected_count, 0) - s.completed_count, 0) > 0 THEN '4'
      WHEN s.completed_count > 0 THEN '1'
      ELSE '3'
    END,
    h.first_abnormal_time = s.first_abnormal_time,
    h.last_abnormal_time = s.last_abnormal_time,
    h.last_run_time = s.last_run_time,
    h.last_result_status = s.last_result_status,
    h.abnormal_summary = COALESCE(NULLIF(s.abnormal_summary, ''), '无异常'),
    h.update_by = 'system',
    h.update_time = NOW();

SELECT COUNT(*) AS matched_baseline_targets FROM tmp_v3162_baseline_targets;
SELECT COUNT(*) AS affected_records FROM tmp_v3162_baseline_records;
SELECT COUNT(*) AS affected_daily_summaries FROM tmp_v3162_daily_stats;

DROP TEMPORARY TABLE IF EXISTS tmp_v3162_daily_stats;
DROP TEMPORARY TABLE IF EXISTS tmp_v3162_baseline_records;
DROP TEMPORARY TABLE IF EXISTS tmp_v3162_baseline_steps;
DROP TEMPORARY TABLE IF EXISTS tmp_v3162_baseline_targets;
