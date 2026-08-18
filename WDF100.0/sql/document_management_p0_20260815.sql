-- 文档管理 P0：差量权限审计字段
-- 前置条件：已执行 document_management_v1_20260815.sql。
-- 上线前请先备份 doc_operation_log，并在测试库完成升级与回滚演练。

SET NAMES utf8mb4;

DROP PROCEDURE IF EXISTS document_management_p0_upgrade_20260815;
DELIMITER $$
CREATE PROCEDURE document_management_p0_upgrade_20260815()
SQL SECURITY INVOKER
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'doc_operation_log' AND column_name = 'target_user_id'
  ) THEN
    ALTER TABLE doc_operation_log
      ADD COLUMN target_user_id BIGINT(20) DEFAULT NULL COMMENT '权限操作目标用户ID' AFTER operator_name;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'doc_operation_log' AND column_name = 'target_user_name'
  ) THEN
    ALTER TABLE doc_operation_log
      ADD COLUMN target_user_name VARCHAR(64) DEFAULT NULL COMMENT '权限操作目标用户名称' AFTER target_user_id;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'doc_operation_log' AND column_name = 'previous_value'
  ) THEN
    ALTER TABLE doc_operation_log
      ADD COLUMN previous_value VARCHAR(128) DEFAULT NULL COMMENT '变更前值' AFTER target_user_name;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'doc_operation_log' AND column_name = 'current_value'
  ) THEN
    ALTER TABLE doc_operation_log
      ADD COLUMN current_value VARCHAR(128) DEFAULT NULL COMMENT '变更后值' AFTER previous_value;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'doc_operation_log'
      AND index_name = 'idx_doc_log_target_time'
  ) THEN
    ALTER TABLE doc_operation_log
      ADD KEY idx_doc_log_target_time (target_user_id, create_time);
  END IF;
END$$
DELIMITER ;

CALL document_management_p0_upgrade_20260815();
DROP PROCEDURE IF EXISTS document_management_p0_upgrade_20260815;

SELECT column_name, column_type, is_nullable, column_comment
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name = 'doc_operation_log'
  AND column_name IN ('target_user_id', 'target_user_name', 'previous_value', 'current_value')
ORDER BY ordinal_position;
