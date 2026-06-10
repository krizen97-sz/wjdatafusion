-- 现场融合管理版本记录页面菜单
-- 说明：仅新增菜单入口，不新增业务表，不影响已有业务数据。

INSERT INTO sys_menu(menu_id, menu_name, parent_id, order_num, path, component, `query`, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
VALUES
(2205, '版本记录', 2200, 5, 'version', 'support/version/index', '', 'SupportVersion', 1, 0, 'C', '0', '0', 'support:version:list', 'documentation', 'admin', NOW(), '', NULL, '现场融合功能版本记录')
ON DUPLICATE KEY UPDATE
  menu_name = VALUES(menu_name),
  parent_id = VALUES(parent_id),
  order_num = VALUES(order_num),
  path = VALUES(path),
  component = VALUES(component),
  route_name = VALUES(route_name),
  visible = VALUES(visible),
  status = VALUES(status),
  perms = VALUES(perms),
  icon = VALUES(icon),
  remark = VALUES(remark);
