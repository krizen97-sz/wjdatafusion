-- 文档管理 v3.9.20 回滚：恢复 v3.9.19 单层菜单和旧入口权限字符
-- 不删除文档、ACL、版本、操作记录或用户容量数据。

SET NAMES utf8mb4;

DROP PROCEDURE IF EXISTS document_file_permission_rollback_20260816;
DELIMITER $$
CREATE PROCEDURE document_file_permission_rollback_20260816()
SQL SECURITY INVOKER
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM sys_menu
    WHERE menu_id = 2500 AND menu_type = 'M' AND path = 'documents'
      AND component = '' AND perms IN ('', 'document:workspace:access')
  ) OR NOT EXISTS (
    SELECT 1 FROM sys_menu
    WHERE menu_id = 2507 AND parent_id = 2500
      AND component = 'document/workspace/index'
      AND menu_type = 'C' AND perms = 'document:file:manage'
  ) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '现场不是预期的v3.9.20菜单结构，停止自动回滚';
  END IF;
END$$
DELIMITER ;

CALL document_file_permission_rollback_20260816();
DROP PROCEDURE IF EXISTS document_file_permission_rollback_20260816;

START TRANSACTION;

-- 将拥有“文件管理”的角色映射回旧应用菜单，保留当前授权意图。
INSERT IGNORE INTO sys_role_menu(role_id, menu_id)
SELECT role_id, 2500 FROM sys_role_menu WHERE menu_id = 2507;

DELETE FROM sys_role_menu WHERE menu_id = 2507;

UPDATE sys_menu
SET parent_id = 2500, update_by = 'admin', update_time = NOW()
WHERE menu_id BETWEEN 2501 AND 2506 AND parent_id = 2507;

UPDATE sys_menu
SET menu_name = '文档管理', parent_id = 0, order_num = 9,
    path = 'documents', component = 'document/workspace/index', `query` = '',
    route_name = 'DocumentWorkspace', is_frame = 1, is_cache = 0,
    menu_type = 'C', visible = '0', status = '0',
    perms = 'document:workspace:access', icon = 'documentation',
    update_by = 'admin', update_time = NOW(),
    remark = '纯内网文档、压缩包传输、目录归档与协同权限'
WHERE menu_id = 2500;

DELETE FROM sys_menu
WHERE menu_id = 2507
  AND parent_id = 2500
  AND perms = 'document:file:manage';

COMMIT;

SELECT menu_id, menu_name, parent_id, path, component, route_name, menu_type, perms
FROM sys_menu WHERE menu_id BETWEEN 2500 AND 2507 ORDER BY menu_id;
