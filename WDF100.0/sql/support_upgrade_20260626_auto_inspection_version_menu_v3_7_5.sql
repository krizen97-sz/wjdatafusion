-- v3.7.5 自动化巡检独立版本记录菜单
-- 说明：
-- 1. 自动化巡检下新增“巡检版本记录”入口。
-- 2. 入口复用版本记录中心页面，通过 query.module=autoInspection 只展示自动化巡检相关版本。
-- 3. 不修改业务表结构，仅新增 sys_menu 菜单数据并绑定 datafusion 角色。

START TRANSACTION;

INSERT INTO sys_menu(menu_id, menu_name, parent_id, order_num, path, component, `query`, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
VALUES
(2306, '巡检版本记录', 2300, 4, 'version', 'support/version/index', '{"module":"autoInspection"}', 'AutoInspectionVersion', 1, 0, 'C', '0', '0', 'support:version:list', 'documentation', 'admin', NOW(), '', NULL, '自动化巡检模块版本记录')
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
  remark = VALUES(remark),
  update_by = 'admin',
  update_time = NOW();

INSERT INTO sys_role_menu(role_id, menu_id)
SELECT r.role_id, 2306
FROM sys_role r
WHERE r.role_key = 'datafusion'
  AND NOT EXISTS (
    SELECT 1
    FROM sys_role_menu rm
    WHERE rm.role_id = r.role_id
      AND rm.menu_id = 2306
  );

COMMIT;
