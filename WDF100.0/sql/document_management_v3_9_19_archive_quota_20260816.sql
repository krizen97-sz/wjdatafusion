-- 文档管理 v3.9.19：ZIP/RAR 文件传输、专属入口权限、用户容量策略
-- 风险边界：不修改 doc_document/doc_acl/doc_version 中的现有业务数据。
-- 上线顺序：数据库备份 -> 本脚本 -> 后端 -> 前端 -> 权限与容量验收。

SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS doc_user_quota (
  user_id           BIGINT(20)   NOT NULL COMMENT '用户ID',
  quota_bytes       BIGINT(20)   NOT NULL DEFAULT 104857600 COMMENT '文档总可用空间，默认100MB',
  max_upload_bytes  BIGINT(20)   NOT NULL DEFAULT 104857600 COMMENT '单文件上传上限，最大100MB',
  create_by         VARCHAR(64)  DEFAULT '' COMMENT '创建者',
  create_time       DATETIME     DEFAULT NULL COMMENT '创建时间',
  update_by         VARCHAR(64)  DEFAULT '' COMMENT '更新者',
  update_time       DATETIME     DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文档用户容量策略';

DROP PROCEDURE IF EXISTS document_access_preflight_20260816;
DELIMITER $$
CREATE PROCEDURE document_access_preflight_20260816()
SQL SECURITY INVOKER
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM sys_menu
    WHERE menu_id = 2500 AND component = 'document/workspace/index'
      AND perms IN ('document:workspace:list', 'document:workspace:access')
  ) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '未找到预期的文档管理菜单2500，停止升级';
  END IF;
END$$
DELIMITER ;

CALL document_access_preflight_20260816();
DROP PROCEDURE IF EXISTS document_access_preflight_20260816;

UPDATE sys_menu
SET perms = 'document:workspace:access',
    remark = '纯内网文档、压缩包传输、目录归档与协同权限',
    update_by = 'admin', update_time = NOW()
WHERE menu_id = 2500 AND component = 'document/workspace/index';

-- 只为已获得文档管理菜单的用户建立显式默认值；后续新增用户由服务端按需补齐。
INSERT IGNORE INTO doc_user_quota(user_id, quota_bytes, max_upload_bytes, create_by, create_time, update_by, update_time)
SELECT DISTINCT ur.user_id, 104857600, 104857600, 'admin', NOW(), 'admin', NOW()
FROM sys_user_role ur
JOIN sys_role_menu rm ON rm.role_id = ur.role_id
WHERE rm.menu_id = 2500;

INSERT IGNORE INTO doc_user_quota(user_id, quota_bytes, max_upload_bytes, create_by, create_time, update_by, update_time)
SELECT DISTINCT ur.user_id, 104857600, 104857600, 'admin', NOW(), 'admin', NOW()
FROM sys_user_role ur
JOIN sys_role r ON r.role_id = ur.role_id AND r.role_key = 'admin';

SELECT menu_id, menu_name, perms, component
FROM sys_menu WHERE menu_id = 2500;

SELECT user_id, quota_bytes, max_upload_bytes
FROM doc_user_quota ORDER BY user_id;
