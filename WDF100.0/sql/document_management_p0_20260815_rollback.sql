-- 文档管理 P0 回滚脚本
-- 警告：回滚会删除 P0 新增的结构化权限审计字段及其历史值。
-- 执行前必须备份 doc_operation_log；V1 文档、ACL、版本和原始摘要日志不会删除。

SET NAMES utf8mb4;

DROP PROCEDURE IF EXISTS document_management_p0_rollback_20260815;
DELIMITER $$
CREATE PROCEDURE document_management_p0_rollback_20260815()
SQL SECURITY INVOKER
BEGIN
  IF EXISTS (
    SELECT 1 FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'doc_operation_log'
      AND index_name = 'idx_doc_log_target_time'
  ) THEN
    ALTER TABLE doc_operation_log DROP INDEX idx_doc_log_target_time;
  END IF;

  IF EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'doc_operation_log' AND column_name = 'current_value'
  ) THEN
    ALTER TABLE doc_operation_log DROP COLUMN current_value;
  END IF;

  IF EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'doc_operation_log' AND column_name = 'previous_value'
  ) THEN
    ALTER TABLE doc_operation_log DROP COLUMN previous_value;
  END IF;

  IF EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'doc_operation_log' AND column_name = 'target_user_name'
  ) THEN
    ALTER TABLE doc_operation_log DROP COLUMN target_user_name;
  END IF;

  IF EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'doc_operation_log' AND column_name = 'target_user_id'
  ) THEN
    ALTER TABLE doc_operation_log DROP COLUMN target_user_id;
  END IF;
END$$
DELIMITER ;

CALL document_management_p0_rollback_20260815();
DROP PROCEDURE IF EXISTS document_management_p0_rollback_20260815;
