-- 文档管理 P1：权限有效期、自动失效任务与 Quartz 日志兼容
-- 前置条件：已执行 V1 与 P0 文档管理脚本。
-- 上线前请备份 doc_acl、sys_job，并在测试库完成升级与回滚演练。

SET NAMES utf8mb4;

DROP PROCEDURE IF EXISTS document_management_p1_upgrade_20260816;
DELIMITER $$
CREATE PROCEDURE document_management_p1_upgrade_20260816()
SQL SECURITY INVOKER
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'doc_acl' AND column_name = 'expires_at'
  ) THEN
    ALTER TABLE doc_acl
      ADD COLUMN expires_at DATETIME DEFAULT NULL COMMENT '权限到期时间；空值表示永久有效' AFTER granted_by;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'doc_acl'
      AND index_name = 'idx_doc_acl_expiry'
  ) THEN
    ALTER TABLE doc_acl
      ADD KEY idx_doc_acl_expiry (expires_at, permission, acl_id);
  END IF;

  -- 老环境可能缺少当前 Quartz 映射已使用的执行时间字段。
  -- 到期任务本身即使执行成功，也会因日志 INSERT 失败而持续输出错误；这里幂等补齐。
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'sys_job_log' AND column_name = 'start_time'
  ) THEN
    ALTER TABLE sys_job_log
      ADD COLUMN start_time DATETIME DEFAULT NULL COMMENT '执行开始时间' AFTER exception_info;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'sys_job_log' AND column_name = 'end_time'
  ) THEN
    ALTER TABLE sys_job_log
      ADD COLUMN end_time DATETIME DEFAULT NULL COMMENT '执行结束时间' AFTER start_time;
  END IF;
END$$
DELIMITER ;

CALL document_management_p1_upgrade_20260816();
DROP PROCEDURE IF EXISTS document_management_p1_upgrade_20260816;

INSERT INTO sys_job
  (job_name, job_group, invoke_target, cron_expression, misfire_policy, concurrent,
   status, create_by, create_time, update_by, update_time, remark)
SELECT
  '文档协作权限到期处理', 'SYSTEM', 'documentPermissionExpiryTask.expire',
  '0/30 * * * * ?', '3', '1', '0', 'admin', NOW(), 'admin', NOW(),
  '每30秒移除已到期ACL；编辑权限到期时同步断开ONLYOFFICE编辑会话'
FROM DUAL
WHERE NOT EXISTS (
  SELECT 1 FROM sys_job WHERE invoke_target = 'documentPermissionExpiryTask.expire'
);

UPDATE sys_job
SET job_name = '文档协作权限到期处理',
    job_group = 'SYSTEM',
    cron_expression = '0/30 * * * * ?',
    misfire_policy = '3',
    concurrent = '1',
    status = '0',
    update_by = 'admin',
    update_time = NOW(),
    remark = '每30秒移除已到期ACL；编辑权限到期时同步断开ONLYOFFICE编辑会话'
WHERE invoke_target = 'documentPermissionExpiryTask.expire';

SELECT column_name, column_type, is_nullable, column_comment
FROM information_schema.columns
WHERE table_schema = DATABASE() AND table_name = 'doc_acl' AND column_name = 'expires_at';

SELECT column_name, column_type, is_nullable, column_comment
FROM information_schema.columns
WHERE table_schema = DATABASE() AND table_name = 'sys_job_log'
  AND column_name IN ('start_time', 'end_time')
ORDER BY ordinal_position;

SELECT job_id, job_name, job_group, invoke_target, cron_expression, status
FROM sys_job WHERE invoke_target = 'documentPermissionExpiryTask.expire';
