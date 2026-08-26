-- v3.9.9 IPAM总览与配置菜单拆分
-- 说明：
-- 1. 将原“IP分配管控”页面改为目录，下设“总览”和“IP分配配置”两个独立页面。
-- 2. 原IPAM功能权限统一归入配置页面，已有角色自动继承两个页面菜单。
-- 3. 本脚本只调整IPAM菜单，不修改sup_*现场融合业务表。

INSERT INTO sys_menu(menu_id, menu_name, parent_id, order_num, path, component, `query`, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
VALUES
(2414, '总览', 2400, 1, 'overview', 'ipam/overview/index', '', 'IpamOverview', 1, 0, 'C', '0', '0', 'ipam:network:list', 'chart-no-axes-combined', 'admin', NOW(), '', NULL, 'IP分配全域统计与小区明细'),
(2415, 'IP分配配置', 2400, 2, 'config', 'ipam/index', '', 'IpamConfig', 1, 0, 'C', '0', '0', 'ipam:network:list', 'route', 'admin', NOW(), '', NULL, '网段、IP配置与地址台账')
ON DUPLICATE KEY UPDATE
  menu_name = VALUES(menu_name),
  parent_id = VALUES(parent_id),
  order_num = VALUES(order_num),
  path = VALUES(path),
  component = VALUES(component),
  `query` = VALUES(`query`),
  route_name = VALUES(route_name),
  is_frame = VALUES(is_frame),
  is_cache = VALUES(is_cache),
  menu_type = VALUES(menu_type),
  visible = VALUES(visible),
  status = VALUES(status),
  perms = VALUES(perms),
  icon = VALUES(icon),
  update_by = 'admin',
  update_time = NOW(),
  remark = VALUES(remark);

UPDATE sys_menu
SET menu_name = 'IP分配管控',
    path = 'ipam',
    component = '',
    `query` = '',
    route_name = '',
    is_frame = 1,
    is_cache = 0,
    menu_type = 'M',
    visible = '0',
    status = '0',
    perms = '',
    icon = 'network',
    update_by = 'admin',
    update_time = NOW(),
    remark = '独立IP分配管控应用目录'
WHERE menu_id = 2400;

UPDATE sys_menu
SET parent_id = 2415,
    update_by = 'admin',
    update_time = NOW()
WHERE menu_id IN (2401, 2402, 2403, 2404, 2405, 2406, 2407, 2408, 2409, 2410, 2411, 2412, 2413)
  AND menu_type = 'F';

INSERT IGNORE INTO sys_role_menu(role_id, menu_id)
SELECT role_id, 2414
FROM (
  SELECT DISTINCT role_id
  FROM sys_role_menu
  WHERE menu_id IN (2400, 2401, 2402, 2403, 2404, 2405, 2406, 2407, 2408, 2409, 2410, 2411, 2412, 2413, 2414, 2415)
) existing_ipam_roles;

INSERT IGNORE INTO sys_role_menu(role_id, menu_id)
SELECT role_id, 2415
FROM (
  SELECT DISTINCT role_id
  FROM sys_role_menu
  WHERE menu_id IN (2400, 2401, 2402, 2403, 2404, 2405, 2406, 2407, 2408, 2409, 2410, 2411, 2412, 2413, 2414, 2415)
) existing_ipam_roles;
