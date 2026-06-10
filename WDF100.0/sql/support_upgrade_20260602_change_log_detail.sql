-- 现场融合操作记录详情字段

SET @support_change_log_detail_sql := (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE sup_change_log ADD COLUMN detail_content TEXT DEFAULT NULL COMMENT ''操作详情'' AFTER summary',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'sup_change_log'
    AND COLUMN_NAME = 'detail_content'
);

PREPARE support_change_log_detail_stmt FROM @support_change_log_detail_sql;
EXECUTE support_change_log_detail_stmt;
DEALLOCATE PREPARE support_change_log_detail_stmt;
