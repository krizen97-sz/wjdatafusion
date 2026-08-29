-- v3.16.0 自动化巡检数值判定与高频结果可解释性升级
-- 基线：已完成 v3.13.0 高频健康监测及后续自动化巡检升级。
-- 说明：新增结构化判定证据；不修改旧模板、旧巡检记录及旧目标数据。
-- 可重复执行。

SET NAMES utf8mb4;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sup_auto_inspection_target_result' AND COLUMN_NAME = 'evaluation_mode') = 0,
  'ALTER TABLE sup_auto_inspection_target_result ADD COLUMN evaluation_mode VARCHAR(16) DEFAULT ''FIXED'' COMMENT ''判定方式（FIXED固定阈值 PREVIOUS上次结果）'' AFTER actual_unit', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sup_auto_inspection_target_result' AND COLUMN_NAME = 'previous_value') = 0,
  'ALTER TABLE sup_auto_inspection_target_result ADD COLUMN previous_value DECIMAL(30,2) DEFAULT NULL COMMENT ''上次采样值'' AFTER evaluation_mode', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sup_auto_inspection_target_result' AND COLUMN_NAME = 'change_value') = 0,
  'ALTER TABLE sup_auto_inspection_target_result ADD COLUMN change_value DECIMAL(30,2) DEFAULT NULL COMMENT ''本次与上次变化量'' AFTER previous_value', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sup_auto_inspection_target_result' AND COLUMN_NAME = 'evaluation_rule') = 0,
  'ALTER TABLE sup_auto_inspection_target_result ADD COLUMN evaluation_rule VARCHAR(500) DEFAULT NULL COMMENT ''本次判定公式'' AFTER change_value', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sup_auto_inspection_target_result' AND COLUMN_NAME = 'baseline_flag') = 0,
  'ALTER TABLE sup_auto_inspection_target_result ADD COLUMN baseline_flag CHAR(1) DEFAULT ''N'' COMMENT ''是否本次建立基线（Y是 N否）'' AFTER evaluation_rule', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

UPDATE sup_auto_inspection_target_result
SET evaluation_mode = 'FIXED'
WHERE evaluation_mode IS NULL OR evaluation_mode = '';

UPDATE sup_auto_inspection_target_result
SET baseline_flag = 'N'
WHERE baseline_flag IS NULL OR baseline_flag = '';

UPDATE sup_auto_inspection_tool
SET tool_name = 'Kafka消费组指标检测',
    param_schema = '{"fields":["topic","consumerGroup","kafkaMetric","evaluationConfig"]}',
    status = '0',
    remark = '一次采集最大积压、总积压、生产Offset和消费Offset；支持固定阈值或与上次结果比较',
    update_time = NOW()
WHERE tool_code = 'KAFKA_LAG';

UPDATE sup_auto_inspection_tool
SET status = '1', default_threshold_value = 1,
    param_schema = CASE tool_code
      WHEN 'KAFKA_TOPIC_ACTIVITY' THEN '{"fields":["topic","evaluationConfig"]}'
      ELSE '{"fields":["topic","consumerGroup","evaluationConfig"]}' END,
    remark = '历史模板兼容工具；新建步骤请使用KAFKA_LAG并选择生产总Offset或消费总Offset',
    update_time = NOW()
WHERE tool_code IN ('KAFKA_TOPIC_ACTIVITY', 'KAFKA_CONSUMER_PROGRESS');

UPDATE sup_auto_inspection_tool
SET default_threshold_value = 1,
    param_schema = '{"fields":["broker","topicFilter","qos","ignoreRetained","evaluationConfig"]}',
    remark = '后台持续订阅MQTT主题并支持固定阈值或上次结果比较',
    update_time = NOW()
WHERE tool_code = 'MQTT_TOPIC_ACTIVITY';
