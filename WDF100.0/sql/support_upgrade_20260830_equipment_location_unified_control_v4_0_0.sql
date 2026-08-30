-- 现场融合管理 v4.0.0：设备与机房一张图统一管控权限
-- 说明：本脚本仅新增按钮权限并给 datafusion 角色授权，不修改任何业务数据或业务表结构。

INSERT INTO sys_menu(menu_id, menu_name, parent_id, order_num, path, component, `query`, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
VALUES
(2321, '设备统一新增', 2201, 16, '#', '', '', '', 1, 0, 'F', '0', '0', 'support:equipment:add', '#', 'admin', NOW(), '', NULL, ''),
(2322, '设备统一修改', 2201, 17, '#', '', '', '', 1, 0, 'F', '0', '0', 'support:equipment:edit', '#', 'admin', NOW(), '', NULL, ''),
(2323, '设备统一删除', 2201, 18, '#', '', '', '', 1, 0, 'F', '0', '0', 'support:equipment:remove', '#', 'admin', NOW(), '', NULL, '')
ON DUPLICATE KEY UPDATE
menu_name = VALUES(menu_name),
parent_id = VALUES(parent_id),
order_num = VALUES(order_num),
perms = VALUES(perms),
status = VALUES(status);

INSERT INTO sys_role_menu(role_id, menu_id)
SELECT r.role_id, m.menu_id
FROM sys_role r
         INNER JOIN sys_menu m ON m.menu_id IN (2321, 2322, 2323)
WHERE r.role_key = 'datafusion'
  AND NOT EXISTS (
    SELECT 1
    FROM sys_role_menu rm
    WHERE rm.role_id = r.role_id
      AND rm.menu_id = m.menu_id
  );
