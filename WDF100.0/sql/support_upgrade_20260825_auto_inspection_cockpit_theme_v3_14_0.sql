-- v3.14.0 自动化巡检统一健康驾驶舱与前端本地化
-- 说明：只新增菜单数据，不修改巡检业务表，不影响已有模板、计划和巡检记录。

SET NAMES utf8mb4;

INSERT INTO sys_menu(
  menu_id, menu_name, parent_id, order_num, path, component, `query`, route_name,
  is_frame, is_cache, menu_type, visible, status, perms, icon,
  create_by, create_time, update_by, update_time, remark
)
VALUES (
  2307, '巡检驾驶舱', 2300, 1, 'cockpit', 'support/autoInspection/cockpit', '',
  'AutoInspectionCockpit', 1, 0, 'C', '0', '0', 'support:autoInspection:query',
  'data-analysis', 'admin', NOW(), '', NULL,
  '统一展示例行巡检、高频健康、当前计划状态和当日问题'
)
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
  update_by = 'admin',
  update_time = NOW(),
  remark = VALUES(remark);

UPDATE sys_menu
SET order_num = 2,
    menu_name = '巡检总览',
    remark = '按天查看巡检记录和高频每日健康明细',
    update_by = 'admin',
    update_time = NOW()
WHERE menu_id = 2305;

UPDATE sys_menu
SET order_num = 3,
    update_by = 'admin',
    update_time = NOW()
WHERE menu_id = 2301;

INSERT INTO sys_role_menu(role_id, menu_id)
SELECT r.role_id, 2307
FROM sys_role r
WHERE r.role_key = 'datafusion'
  AND r.status = '0'
  AND NOT EXISTS (
    SELECT 1
    FROM sys_role_menu rm
    WHERE rm.role_id = r.role_id
      AND rm.menu_id = 2307
  );
