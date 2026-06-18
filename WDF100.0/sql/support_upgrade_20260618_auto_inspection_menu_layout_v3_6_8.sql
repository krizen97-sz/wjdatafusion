-- 自动化巡检左侧菜单化布局 v3.6.8
-- 说明：
-- 1. 不修改业务表结构。
-- 2. 将“巡检看板 / 巡检配置 / 巡检记录”统一放到自动化巡检左侧菜单下。
-- 3. 右侧页面不再使用这三个一级页签做切换。

INSERT INTO sys_menu(menu_id, menu_name, parent_id, order_num, path, component, `query`, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
VALUES
(2305, '巡检看板', 2300, 1, 'dashboard', 'support/autoInspection/index', '{"tab":"dashboard"}', 'AutoInspectionDashboard', 1, 0, 'C', '0', '0', 'support:autoInspection:query', 'dashboard', 'admin', NOW(), '', NULL, '查看自动化巡检运行概览、趋势和异常子项')
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
    path = 'config',
    component = 'support/autoInspection/index',
    `query` = '{"tab":"template"}',
    route_name = 'AutoInspectionConfig',
    visible = '0',
    status = '0',
    icon = 'setting',
    update_time = NOW(),
    remark = '在模板步骤中选择巡检工具并配置目标、阈值和参数'
WHERE menu_id = 2301;

UPDATE sys_menu
SET order_num = 3,
    path = 'record',
    component = 'support/autoInspection/index',
    `query` = '{"tab":"record"}',
    route_name = 'AutoInspectionRecord',
    visible = '0',
    status = '0',
    perms = 'support:autoInspection:query',
    icon = 'documentation',
    update_time = NOW()
WHERE menu_id = 2304;

UPDATE sys_menu
SET visible = '1',
    update_time = NOW(),
    remark = '目标配置已收敛到巡检模板步骤内'
WHERE menu_id = 2302;

UPDATE sys_menu
SET visible = '1',
    update_time = NOW(),
    remark = '计划配置已收敛到巡检配置页面内'
WHERE menu_id = 2303;

INSERT INTO sys_role_menu(role_id, menu_id)
SELECT r.role_id, 2305
FROM sys_role r
WHERE r.role_key = 'datafusion'
  AND NOT EXISTS (
    SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = r.role_id AND rm.menu_id = 2305
  );
