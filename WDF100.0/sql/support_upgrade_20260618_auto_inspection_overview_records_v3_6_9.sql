-- 自动化巡检总览与记录合并 v3.6.9
-- 说明：
-- 1. 不修改业务表结构。
-- 2. 将“巡检看板”和“巡检记录”合并为“巡检总览”页面。
-- 3. 旧“巡检记录”菜单隐藏但保留路由和权限，兼容历史入口。

INSERT INTO sys_menu(menu_id, menu_name, parent_id, order_num, path, component, `query`, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
VALUES
(2305, '巡检总览', 2300, 1, 'dashboard', 'support/autoInspection/index', '{"tab":"dashboard"}', 'AutoInspectionDashboard', 1, 0, 'C', '0', '0', 'support:autoInspection:query', 'dashboard', 'admin', NOW(), '', NULL, '查看自动化巡检运行概览、趋势、异常子项和巡检记录')
ON DUPLICATE KEY UPDATE
  menu_name = VALUES(menu_name),
  parent_id = VALUES(parent_id),
  order_num = VALUES(order_num),
  path = VALUES(path),
  component = VALUES(component),
  `query` = VALUES(`query`),
  route_name = VALUES(route_name),
  visible = VALUES(visible),
  status = VALUES(status),
  perms = VALUES(perms),
  icon = VALUES(icon),
  update_time = NOW(),
  remark = VALUES(remark);

UPDATE sys_menu
SET order_num = 2,
    visible = '0',
    status = '0',
    update_time = NOW()
WHERE menu_id = 2301;

UPDATE sys_menu
SET visible = '1',
    status = '0',
    update_time = NOW(),
    remark = '巡检记录已合并到巡检总览，保留隐藏路由兼容历史入口'
WHERE menu_id = 2304;

INSERT INTO sys_role_menu(role_id, menu_id)
SELECT r.role_id, 2305
FROM sys_role r
WHERE r.role_key = 'datafusion'
  AND NOT EXISTS (
    SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = r.role_id AND rm.menu_id = 2305
  );
