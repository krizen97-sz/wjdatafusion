-- 文档管理 v3.9.20（阶段二）：新代码上线后移除旧入口权限
-- 执行后只认若依“文件管理”菜单的 document:file:manage 权限字符。

SET NAMES utf8mb4;

DROP PROCEDURE IF EXISTS document_file_permission_finalize_20260816;
DELIMITER $$
CREATE PROCEDURE document_file_permission_finalize_20260816()
SQL SECURITY INVOKER
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM sys_menu
    WHERE menu_id = 2500 AND menu_type = 'M'
      AND path = 'documents' AND perms = 'document:workspace:access'
  ) OR NOT EXISTS (
    SELECT 1 FROM sys_menu
    WHERE menu_id = 2507 AND parent_id = 2500
      AND component = 'document/workspace/index'
      AND route_name = 'DocumentWorkspace'
      AND menu_type = 'C' AND perms = 'document:file:manage'
  ) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '若依文件管理权限准备阶段未完成，禁止执行收口脚本';
  END IF;

  IF EXISTS (
    SELECT 1
    FROM sys_role_menu parent_rm
    WHERE parent_rm.menu_id = 2500
      AND NOT EXISTS (
        SELECT 1 FROM sys_role_menu child_rm
        WHERE child_rm.role_id = parent_rm.role_id AND child_rm.menu_id = 2507
      )
  ) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '存在仅有文档目录但未获文件管理菜单的角色，停止收口以防权限中断';
  END IF;
END$$
DELIMITER ;

CALL document_file_permission_finalize_20260816();
DROP PROCEDURE IF EXISTS document_file_permission_finalize_20260816;

UPDATE sys_menu
SET perms = '', update_by = 'admin', update_time = NOW(),
    remark = '若依原生角色菜单目录；应用访问由子菜单“文件管理”授权'
WHERE menu_id = 2500
  AND menu_type = 'M'
  AND perms = 'document:workspace:access';

SELECT menu_id, menu_name, parent_id, path, component, route_name, menu_type, perms
FROM sys_menu WHERE menu_id IN (2500, 2507) ORDER BY menu_id;
