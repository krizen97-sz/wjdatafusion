-- v3.13.0 自动化巡检高频健康监测
-- 说明：计划增加例行/高频模式，新增Kafka与MQTT活性检测、采样状态和每日健康汇总。
-- 可重复执行，不修改已有模板、计划和巡检记录的业务值。

SET NAMES utf8mb4;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sup_auto_inspection_plan' AND COLUMN_NAME = 'plan_mode') = 0,
  'ALTER TABLE sup_auto_inspection_plan ADD COLUMN plan_mode VARCHAR(16) DEFAULT ''ROUTINE'' COMMENT ''计划模式（ROUTINE例行 FREQUENT高频）'' AFTER label_name', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sup_auto_inspection_plan' AND COLUMN_NAME = 'health_config') = 0,
  'ALTER TABLE sup_auto_inspection_plan ADD COLUMN health_config TEXT COMMENT ''高频健康配置JSON'' AFTER cron_config', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sup_auto_inspection_record' AND COLUMN_NAME = 'run_mode') = 0,
  'ALTER TABLE sup_auto_inspection_record ADD COLUMN run_mode VARCHAR(16) DEFAULT ''ROUTINE'' COMMENT ''运行模式（ROUTINE例行 FREQUENT高频）'' AFTER source_type', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sup_auto_inspection_record' AND COLUMN_NAME = 'schedule_slot_time') = 0,
  'ALTER TABLE sup_auto_inspection_record ADD COLUMN schedule_slot_time DATETIME DEFAULT NULL COMMENT ''高频计划归一化采样时隙'' AFTER run_mode', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sup_auto_inspection_record' AND COLUMN_NAME = 'duration_ms') = 0,
  'ALTER TABLE sup_auto_inspection_record ADD COLUMN duration_ms BIGINT DEFAULT NULL COMMENT ''执行耗时毫秒'' AFTER schedule_slot_time', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sup_auto_inspection_record' AND COLUMN_NAME = 'warning_count') = 0,
  'ALTER TABLE sup_auto_inspection_record ADD COLUMN warning_count INT DEFAULT 0 COMMENT ''关注步骤数'' AFTER abnormal_count', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sup_auto_inspection_record' AND INDEX_NAME = 'uk_sup_auto_record_plan_slot') = 0,
  'ALTER TABLE sup_auto_inspection_record ADD UNIQUE KEY uk_sup_auto_record_plan_slot(plan_id, schedule_slot_time)', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sup_auto_inspection_record' AND INDEX_NAME = 'idx_sup_auto_record_mode_time') = 0,
  'ALTER TABLE sup_auto_inspection_record ADD KEY idx_sup_auto_record_mode_time(run_mode, inspection_time)', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS sup_auto_inspection_probe_state (
  state_id BIGINT NOT NULL AUTO_INCREMENT, plan_id BIGINT NOT NULL, step_id BIGINT NOT NULL, target_id BIGINT NOT NULL,
  tool_code VARCHAR(64) NOT NULL, primary_value DECIMAL(30,2) DEFAULT NULL, secondary_value DECIMAL(30,2) DEFAULT NULL,
  observed_at DATETIME DEFAULT NULL, last_activity_at DATETIME DEFAULT NULL, abnormal_streak INT DEFAULT 0,
  normal_streak INT DEFAULT 0, state_status CHAR(1) DEFAULT '3', state_detail VARCHAR(1000) DEFAULT NULL,
  create_by VARCHAR(64) DEFAULT '', create_time DATETIME DEFAULT NULL, update_by VARCHAR(64) DEFAULT '', update_time DATETIME DEFAULT NULL,
  PRIMARY KEY (state_id), UNIQUE KEY uk_sup_auto_probe_scope(plan_id, step_id, target_id, tool_code),
  KEY idx_sup_auto_probe_activity(last_activity_at, state_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='自动化巡检高频目标状态';

CREATE TABLE IF NOT EXISTS sup_auto_inspection_health_daily (
  summary_id BIGINT NOT NULL AUTO_INCREMENT, health_date DATE NOT NULL, plan_id BIGINT NOT NULL,
  plan_name VARCHAR(120) NOT NULL, template_id BIGINT DEFAULT NULL, template_name VARCHAR(120) DEFAULT NULL,
  expected_count INT DEFAULT 0, completed_count INT DEFAULT 0, normal_count INT DEFAULT 0,
  warning_count INT DEFAULT 0, abnormal_count INT DEFAULT 0, skipped_count INT DEFAULT 0, missing_count INT DEFAULT 0,
  health_score DECIMAL(5,2) DEFAULT 0, health_target DECIMAL(5,2) DEFAULT 99, day_status CHAR(1) DEFAULT '3',
  first_abnormal_time DATETIME DEFAULT NULL, last_abnormal_time DATETIME DEFAULT NULL, last_run_time DATETIME DEFAULT NULL,
  last_result_status CHAR(1) DEFAULT NULL, abnormal_summary TEXT, create_by VARCHAR(64) DEFAULT '', create_time DATETIME DEFAULT NULL,
  update_by VARCHAR(64) DEFAULT '', update_time DATETIME DEFAULT NULL, PRIMARY KEY (summary_id),
  UNIQUE KEY uk_sup_auto_health_day_plan(health_date, plan_id), KEY idx_sup_auto_health_plan_date(plan_id, health_date),
  KEY idx_sup_auto_health_status_date(day_status, health_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='自动化巡检高频每日健康汇总';

INSERT INTO sup_auto_inspection_tool(tool_code, tool_name, tool_type, value_unit, default_compare_rule, default_threshold_value, default_timeout_seconds, default_time_window_minutes, param_schema, built_in_flag, status, create_by, create_time, remark) VALUES
('KAFKA_TOPIC_ACTIVITY', 'Kafka主题写入中断检测', 'KAFKA_TOPIC_ACTIVITY', '条', 'MIN', 0, 10, 0, '{"fields":["topic","activityRule"]}', 'Y', '0', 'admin', NOW(), '按Topic总Offset变化判断持续无写入时长'),
('KAFKA_CONSUMER_PROGRESS', 'Kafka消费停滞检测', 'KAFKA_CONSUMER_PROGRESS', '条', 'MIN', 0, 10, 0, '{"fields":["topic","consumerGroup","activityRule"]}', 'Y', '0', 'admin', NOW(), '上游有新增但消费组提交位点不推进时告警'),
('MQTT_TOPIC_ACTIVITY', 'MQTT主题活跃度检测', 'MQTT_TOPIC_ACTIVITY', '条', 'MIN', 0, 10, 0, '{"fields":["broker","topicFilter","qos","ignoreRetained","activityRule"]}', 'Y', '0', 'admin', NOW(), '后台持续订阅MQTT主题并判断持续无消息时长')
ON DUPLICATE KEY UPDATE tool_name = VALUES(tool_name), tool_type = VALUES(tool_type), value_unit = VALUES(value_unit),
  param_schema = VALUES(param_schema), built_in_flag = 'Y', status = '0', remark = VALUES(remark), update_time = NOW();

UPDATE sup_auto_inspection_plan SET plan_mode = 'ROUTINE' WHERE plan_mode IS NULL OR plan_mode = '';
UPDATE sup_auto_inspection_record SET run_mode = 'ROUTINE' WHERE run_mode IS NULL OR run_mode = '';
