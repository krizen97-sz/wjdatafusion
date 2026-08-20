-- v3.11.0 自动化巡检模板与计划标签树
-- 说明：新增可空标签字段和组合索引；历史模板、计划保留原数据并显示在“未分类”目录。

SET NAMES utf8mb4;

SET @template_label_column = (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sup_auto_inspection_template' AND COLUMN_NAME = 'label_name'
);
SET @ddl = IF(@template_label_column = 0,
  'ALTER TABLE sup_auto_inspection_template ADD COLUMN label_name VARCHAR(64) DEFAULT NULL COMMENT ''标签名称'' AFTER template_name',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @plan_label_column = (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sup_auto_inspection_plan' AND COLUMN_NAME = 'label_name'
);
SET @ddl = IF(@plan_label_column = 0,
  'ALTER TABLE sup_auto_inspection_plan ADD COLUMN label_name VARCHAR(64) DEFAULT NULL COMMENT ''标签名称'' AFTER plan_name',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @template_label_index = (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sup_auto_inspection_template' AND INDEX_NAME = 'idx_sup_auto_template_label_status'
);
SET @ddl = IF(@template_label_index = 0,
  'ALTER TABLE sup_auto_inspection_template ADD INDEX idx_sup_auto_template_label_status (label_name, status)',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @plan_label_index = (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sup_auto_inspection_plan' AND INDEX_NAME = 'idx_sup_auto_plan_label_status'
);
SET @ddl = IF(@plan_label_index = 0,
  'ALTER TABLE sup_auto_inspection_plan ADD INDEX idx_sup_auto_plan_label_status (label_name, status)',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
