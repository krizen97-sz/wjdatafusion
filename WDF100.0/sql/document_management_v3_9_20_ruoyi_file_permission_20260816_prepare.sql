-- 文档管理 v3.9.20（阶段一）：接入若依原生角色菜单权限
-- 发布顺序：本脚本 -> 新后端/前端 -> finalize 脚本。
-- 阶段一暂时在父目录保留旧权限字符，保证旧后端在切换窗口内仍可工作。

SET NAMES utf8mb4;

DROP PROCEDURE IF EXISTS document_file_permission_prepare_20260816;
DELIMITER $$
CREATE PROCEDURE document_file_permission_prepare_20260816()
SQL SECURITY INVOKER
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM sys_menu
    WHERE menu_id = 2500
      AND parent_id = 0
      AND path = 'documents'
      AND component = 'document/workspace/index'
      AND route_name = 'DocumentWorkspace'
      AND menu_type = 'C'
      AND perms = 'document:workspace:access'
  ) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '文档管理菜单2500不是预期的v3.9.19状态，停止权限迁移';
  END IF;

  IF EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 2507) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '菜单ID 2507已被占用或迁移已执行，请先核对现场状态';
  END IF;

  IF EXISTS (
    SELECT 1 FROM sys_menu
    WHERE menu_id BETWEEN 2501 AND 2506 AND parent_id <> 2500
  ) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '文档功能权限2501-2506父节点异常，停止权限迁移';
  END IF;
END$$
DELIMITER ;

CALL document_file_permission_prepare_20260816();
DROP PROCEDURE IF EXISTS document_file_permission_prepare_20260816;

START TRANSACTION;

-- 2500 变为若依目录节点；旧权限字符仅在发布窗口内保留。
UPDATE sys_menu
SET menu_name = '文档管理', parent_id = 0, order_num = 9,
    path = 'documents', component = '', `query` = '', route_name = 'Documents',
    is_frame = 1, is_cache = 0, menu_type = 'M', visible = '0', status = '0',
    perms = 'document:workspace:access', icon = 'documentation',
    update_by = 'admin', update_time = NOW(),
    remark = '若依原生角色菜单目录；应用访问由子菜单“文件管理”授权'
WHERE menu_id = 2500;

-- 新增可在“系统管理 / 角色管理 / 菜单权限”中勾选的应用菜单。
-- 空子路径是 Vue Router 的默认子路由，最终地址仍为 /documents。
INSERT INTO sys_menu(
  menu_id, menu_name, parent_id, order_num, path, component, `query`, route_name,
  is_frame, is_cache, menu_type, visible, status, perms, icon,
  create_by, create_time, update_by, update_time, remark
) VALUES (
  2507, '文件管理', 2500, 1, '', 'document/workspace/index', '', 'DocumentWorkspace',
  1, 0, 'C', '0', '0', 'document:file:manage', 'list',
  'admin', NOW(), 'admin', NOW(), '文件管理应用入口；使用若依sys_menu/sys_role_menu原生授权'
);

-- 既有按钮权限归入“文件管理”应用节点。
UPDATE sys_menu
SET parent_id = 2507, update_by = 'admin', update_time = NOW()
WHERE menu_id BETWEEN 2501 AND 2506 AND parent_id = 2500;

-- 兼容迁移：此前能看到文档管理的角色继续拥有文件管理应用。
INSERT IGNORE INTO sys_role_menu(role_id, menu_id)
SELECT role_id, 2507 FROM sys_role_menu WHERE menu_id = 2500;

-- 为通过若依角色权限获得文件管理能力的用户补齐默认容量策略。
INSERT IGNORE INTO doc_user_quota(
  user_id, quota_bytes, max_upload_bytes, create_by, create_time, update_by, update_time
)
SELECT DISTINCT ur.user_id, 104857600, 104857600, 'admin', NOW(), 'admin', NOW()
FROM sys_user_role ur
JOIN sys_role_menu rm ON rm.role_id = ur.role_id AND rm.menu_id = 2507;

COMMIT;

SELECT menu_id, menu_name, parent_id, path, component, route_name, menu_type, perms
FROM sys_menu WHERE menu_id BETWEEN 2500 AND 2507 ORDER BY menu_id;

SELECT rm.role_id, r.role_name, r.role_key, rm.menu_id
FROM sys_role_menu rm
JOIN sys_role r ON r.role_id = rm.role_id
WHERE rm.menu_id IN (2500, 2507)
ORDER BY rm.role_id, rm.menu_id;
