-- 文档管理 v3.9.21 回滚：移除专用“文档管理”角色，并精确恢复升级前普通角色菜单。
-- 不删除文档、目录、ACL、修改记录、存储文件或用户容量数据。
-- 如需恢复到发布前完全一致的现场状态，优先使用发布时生成的整库备份。

SET NAMES utf8mb4;

DROP PROCEDURE IF EXISTS document_role_rollback_preflight_20260816;
DELIMITER $$
CREATE PROCEDURE document_role_rollback_preflight_20260816()
SQL SECURITY INVOKER
BEGIN
  IF (
    SELECT COUNT(*) FROM sys_role
    WHERE role_key = 'document' AND role_name = '文档管理' AND del_flag = '0'
  ) <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '未找到唯一的v3.9.21文档管理角色，停止自动回滚';
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.tables
    WHERE table_schema = DATABASE() AND table_name = 'doc_role_menu_backup_v3921'
  ) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '缺少v3.9.21角色菜单回滚账本，停止自动回滚';
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM sys_menu
    WHERE menu_id = 2507 AND parent_id = 2500
      AND menu_type = 'C' AND perms = 'document:file:manage'
  ) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '文档菜单结构已变化，停止自动回滚';
  END IF;
END$$
DELIMITER ;

CALL document_role_rollback_preflight_20260816();
DROP PROCEDURE IF EXISTS document_role_rollback_preflight_20260816;

SET @document_role_id = (
  SELECT role_id FROM sys_role
  WHERE role_key = 'document' AND role_name = '文档管理' AND del_flag = '0'
  LIMIT 1
);

START TRANSACTION;

-- 先恢复升级前普通角色的菜单授权快照。
INSERT IGNORE INTO sys_role_menu(role_id, menu_id)
SELECT backup.role_id, backup.menu_id
FROM doc_role_menu_backup_v3921 backup
JOIN sys_role r ON r.role_id = backup.role_id
JOIN sys_menu m ON m.menu_id = backup.menu_id;

-- 清理专用角色可能在上线后产生的用户、菜单及自定义部门关联。
DELETE FROM sys_user_role WHERE role_id = @document_role_id;
DELETE FROM sys_role_menu WHERE role_id = @document_role_id;
DELETE FROM sys_role_dept WHERE role_id = @document_role_id;
DELETE FROM sys_role
WHERE role_id = @document_role_id
  AND role_key = 'document' AND role_name = '文档管理';

COMMIT;

DROP TABLE doc_role_menu_backup_v3921;

SELECT role_id, role_name, role_key, status, del_flag
FROM sys_role
WHERE role_key IN ('admin', 'document')
ORDER BY role_id;

SELECT rm.role_id, r.role_name, r.role_key, rm.menu_id
FROM sys_role_menu rm
JOIN sys_role r ON r.role_id = rm.role_id
WHERE rm.menu_id BETWEEN 2500 AND 2507
ORDER BY rm.role_id, rm.menu_id;
