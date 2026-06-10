-- 现场融合管理 v2.2.0 现场导入权限
-- 说明：仅新增现场管理“导入”按钮权限，不修改业务表结构，不影响原有数据。

INSERT INTO sys_menu(menu_id, menu_name, parent_id, order_num, path, component, `query`, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
VALUES
(2216, '现场导入', 2201, 6, '#', '', '', '', 1, 0, 'F', '0', '0', 'support:site:import', '#', 'admin', NOW(), '', NULL, '')
ON DUPLICATE KEY UPDATE
  menu_name = VALUES(menu_name),
  parent_id = VALUES(parent_id),
  order_num = VALUES(order_num),
  perms = VALUES(perms),
  status = VALUES(status);
