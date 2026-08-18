-- 文档管理 P1 回滚脚本
-- 安全前置：如果仍存在带有效期的ACL，脚本会拒绝回滚，防止旧版把它们误当作永久授权。
-- 请先备份并显式撤销这些授权；已经自动移除的ACL不会自动恢复。
-- 执行前必须备份 doc_acl 与 sys_job。历史版本文件和操作记录不会删除。
-- 本次前向迁移若补齐了 sys_job_log.start_time/end_time，回滚时会保留：
-- 这两个字段属于当前平台 Quartz 的通用兼容修复，删除会重新造成任务日志写入失败。

SET NAMES utf8mb4;

DROP PROCEDURE IF EXISTS document_management_p1_rollback_preflight_20260816;
DELIMITER $$
CREATE PROCEDURE document_management_p1_rollback_preflight_20260816()
SQL SECURITY INVOKER
BEGIN
  IF EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'doc_acl' AND column_name = 'expires_at'
  ) AND EXISTS (
    SELECT 1 FROM doc_acl WHERE expires_at IS NOT NULL
  ) THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = '仍有带有效期的ACL；请先导出并撤销这些授权，避免旧版将其误当作永久权限';
  END IF;
END$$
DELIMITER ;

CALL document_management_p1_rollback_preflight_20260816();
DROP PROCEDURE IF EXISTS document_management_p1_rollback_preflight_20260816;

DELETE FROM sys_job WHERE invoke_target = 'documentPermissionExpiryTask.expire';

DROP PROCEDURE IF EXISTS document_management_p1_rollback_20260816;
DELIMITER $$
CREATE PROCEDURE document_management_p1_rollback_20260816()
SQL SECURITY INVOKER
BEGIN
  IF EXISTS (
    SELECT 1 FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'doc_acl'
      AND index_name = 'idx_doc_acl_expiry'
  ) THEN
    ALTER TABLE doc_acl DROP INDEX idx_doc_acl_expiry;
  END IF;

  IF EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'doc_acl' AND column_name = 'expires_at'
  ) THEN
    ALTER TABLE doc_acl DROP COLUMN expires_at;
  END IF;
END$$
DELIMITER ;

CALL document_management_p1_rollback_20260816();
DROP PROCEDURE IF EXISTS document_management_p1_rollback_20260816;
