-- 文档管理 v3.9.21：新增若依原生“文档管理”专用角色
-- 目标：角色列表出现 role_key=document 的“文档管理”角色；仅 admin 或获此角色的用户可进入并使用文件管理。
-- 前置条件：已完成 v3.9.20 菜单权限收口。

SET NAMES utf8mb4;

-- 保存升级前普通角色携带的文档菜单，供配套回滚脚本精确恢复。
CREATE TABLE IF NOT EXISTS doc_role_menu_backup_v3921 (
  role_id BIGINT NOT NULL COMMENT '升级前角色ID',
  menu_id BIGINT NOT NULL COMMENT '升级前文档菜单ID',
  backup_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '备份时间',
  PRIMARY KEY (role_id, menu_id)
) ENGINE=InnoDB COMMENT='v3.9.21文档专用角色迁移回滚账本';

DROP PROCEDURE IF EXISTS document_role_preflight_20260816;
DELIMITER $$
CREATE PROCEDURE document_role_preflight_20260816()
SQL SECURITY INVOKER
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM sys_menu
    WHERE menu_id = 2500 AND parent_id = 0 AND path = 'documents'
      AND component = '' AND menu_type = 'M' AND COALESCE(perms, '') = ''
  ) OR NOT EXISTS (
    SELECT 1 FROM sys_menu
    WHERE menu_id = 2507 AND parent_id = 2500
      AND component = 'document/workspace/index'
      AND route_name = 'DocumentWorkspace'
      AND menu_type = 'C' AND perms = 'document:file:manage'
  ) OR (
    SELECT COUNT(*) FROM sys_menu
    WHERE menu_id BETWEEN 2501 AND 2506 AND parent_id = 2507 AND menu_type = 'F'
  ) <> 6 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '现场不是预期的v3.9.20文档菜单结构，停止新增文档管理角色';
  END IF;

  IF EXISTS (
    SELECT 1 FROM sys_role
    WHERE role_key = 'document' OR role_name = '文档管理'
  ) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '文档管理角色已存在或名称/权限字符被占用，请先核对现场状态';
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM sys_role
    WHERE role_key = 'admin' AND status = '0' AND del_flag = '0'
  ) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '未找到有效的admin总权限角色，停止迁移';
  END IF;

  IF EXISTS (SELECT 1 FROM doc_role_menu_backup_v3921) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'v3.9.21回滚账本已有数据，禁止覆盖历史授权快照';
  END IF;
END$$
DELIMITER ;

CALL document_role_preflight_20260816();
DROP PROCEDURE IF EXISTS document_role_preflight_20260816;

START TRANSACTION;

-- 先记录所有非admin角色原来携带的文档菜单，再把授权集中到专用角色。
INSERT INTO doc_role_menu_backup_v3921(role_id, menu_id, backup_time)
SELECT rm.role_id, rm.menu_id, NOW()
FROM sys_role_menu rm
JOIN sys_role r ON r.role_id = rm.role_id
WHERE rm.menu_id BETWEEN 2500 AND 2507
  AND r.role_key <> 'admin';

-- “仅本人”数据范围避免该功能角色扩大其他业务模块的数据可见范围；
-- 文档本身继续使用所有者与ACL规则控制，不依赖若依部门数据范围。
INSERT INTO sys_role(
  role_name, role_key, role_sort, data_scope,
  menu_check_strictly, dept_check_strictly, status, del_flag,
  create_by, create_time, update_by, update_time, remark
)
SELECT
  '文档管理', 'document', COALESCE(MAX(role_sort), 0) + 1, '5',
  1, 1, '0', '0',
  'admin', NOW(), 'admin', NOW(),
  '文档管理专用角色；分配后可见并使用文件管理应用'
FROM sys_role;

SET @document_role_id = LAST_INSERT_ID();

-- 专用角色默认获得完整文档菜单和功能按钮权限。
INSERT INTO sys_role_menu(role_id, menu_id)
SELECT @document_role_id, menu_id
FROM sys_menu
WHERE menu_id BETWEEN 2500 AND 2507;

-- 平滑迁移：此前通过普通角色拥有文件管理入口的用户，自动补授专用角色。
INSERT IGNORE INTO sys_user_role(user_id, role_id)
SELECT DISTINCT ur.user_id, @document_role_id
FROM sys_user_role ur
JOIN doc_role_menu_backup_v3921 backup
  ON backup.role_id = ur.role_id AND backup.menu_id = 2507
JOIN sys_user u ON u.user_id = ur.user_id
WHERE u.status = '0' AND u.del_flag = '0';

-- 普通角色不再直接携带文档菜单，后续只需分配/撤销“文档管理”角色。
DELETE rm
FROM sys_role_menu rm
JOIN sys_role r ON r.role_id = rm.role_id
WHERE rm.menu_id BETWEEN 2500 AND 2507
  AND r.role_key NOT IN ('admin', 'document');

-- admin保持总权限及完整菜单树，避免不同若依版本的菜单构建差异。
INSERT IGNORE INTO sys_role_menu(role_id, menu_id)
SELECT r.role_id, m.menu_id
FROM sys_role r
CROSS JOIN sys_menu m
WHERE r.role_key = 'admin' AND r.del_flag = '0'
  AND m.menu_id BETWEEN 2500 AND 2507;

-- 为获专用角色的有效用户补齐默认100MB容量策略。
INSERT IGNORE INTO doc_user_quota(
  user_id, quota_bytes, max_upload_bytes, create_by, create_time, update_by, update_time
)
SELECT DISTINCT ur.user_id, 104857600, 104857600, 'admin', NOW(), 'admin', NOW()
FROM sys_user_role ur
JOIN sys_user u ON u.user_id = ur.user_id
WHERE ur.role_id = @document_role_id
  AND u.status = '0' AND u.del_flag = '0';

COMMIT;

SELECT role_id, role_name, role_key, role_sort, data_scope, status, del_flag, remark
FROM sys_role
WHERE role_key IN ('admin', 'document')
ORDER BY role_id;

SELECT r.role_id, r.role_name, GROUP_CONCAT(rm.menu_id ORDER BY rm.menu_id) AS document_menu_ids
FROM sys_role r
LEFT JOIN sys_role_menu rm
  ON rm.role_id = r.role_id AND rm.menu_id BETWEEN 2500 AND 2507
WHERE r.role_key IN ('admin', 'document')
GROUP BY r.role_id, r.role_name
ORDER BY r.role_id;

SELECT u.user_id, u.user_name, GROUP_CONCAT(r.role_key ORDER BY r.role_id) AS role_keys
FROM sys_user u
JOIN sys_user_role ur ON ur.user_id = u.user_id
JOIN sys_role r ON r.role_id = ur.role_id
WHERE EXISTS (
  SELECT 1 FROM sys_user_role document_ur
  JOIN sys_role document_role ON document_role.role_id = document_ur.role_id
  WHERE document_ur.user_id = u.user_id AND document_role.role_key = 'document'
)
GROUP BY u.user_id, u.user_name
ORDER BY u.user_id;
