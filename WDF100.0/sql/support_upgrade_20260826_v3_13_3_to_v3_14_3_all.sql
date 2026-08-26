-- RYNEW v3.13.3 -> v3.14.3 数据库合并升级脚本
--
-- 适用基线：数据库已完成 v3.13.3 及以前的升级。
-- 目标版本：v3.14.3。
--
-- 版本变更：
--   v3.14.0 新增“巡检驾驶舱”菜单，为 datafusion 角色补充菜单权限。
--   v3.14.1 无数据库修改。
--   v3.14.2 无数据库修改。
--   v3.14.3 更新标准业务菜单的语义图标。
--
-- 安全说明：
--   1. 只修改 sys_menu 和 sys_role_menu。
--   2. 不修改现场、巡检、IP、文档或白名单业务数据。
--   3. 不包含 ALTER TABLE、DELETE、TRUNCATE 或 DROP。
--   4. 可重复执行。

SET NAMES utf8mb4;

START TRANSACTION;

-- v3.14.0：新增或修正巡检驾驶舱菜单，直接使用 v3.14.3 最终图标。
INSERT INTO sys_menu(
  menu_id, menu_name, parent_id, order_num, path, component, `query`, route_name,
  is_frame, is_cache, menu_type, visible, status, perms, icon,
  create_by, create_time, update_by, update_time, remark
)
VALUES (
  2307, '巡检驾驶舱', 2300, 1, 'cockpit', 'support/autoInspection/cockpit', '',
  'AutoInspectionCockpit', 1, 0, 'C', '0', '0', 'support:autoInspection:query',
  'gauge', 'admin', NOW(), '', NULL,
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

-- v3.14.3：统一平台标准业务菜单图标。
UPDATE sys_menu
SET icon = CASE menu_id
    WHEN 2200 THEN 'network'
    WHEN 2201 THEN 'map-pinned'
    WHEN 2202 THEN 'panels-top-left'
    WHEN 2203 THEN 'server-cog'
    WHEN 2204 THEN 'contact-round'
    WHEN 2205 THEN 'file-clock'
    WHEN 2206 THEN 'scan-search'
    WHEN 2207 THEN 'file-clock'
    WHEN 2300 THEN 'scan-search'
    WHEN 2301 THEN 'workflow'
    WHEN 2302 THEN 'server-cog'
    WHEN 2304 THEN 'file-clock'
    WHEN 2305 THEN 'chart-no-axes-combined'
    WHEN 2306 THEN 'file-clock'
    WHEN 2307 THEN 'gauge'
    WHEN 2400 THEN 'network'
    WHEN 2414 THEN 'chart-no-axes-combined'
    WHEN 2415 THEN 'route'
    WHEN 2500 THEN 'folder-tree'
    WHEN 2507 THEN 'files'
    WHEN 3000 THEN 'shield-check'
    WHEN 3001 THEN 'car-front'
    WHEN 3002 THEN 'list-filter'
    ELSE icon
  END,
  update_by = 'admin',
  update_time = NOW()
WHERE menu_id IN (
  2200, 2201, 2202, 2203, 2204, 2205, 2206, 2207,
  2300, 2301, 2302, 2304, 2305, 2306, 2307,
  2400, 2414, 2415, 2500, 2507, 3000, 3001, 3002
);

COMMIT;

-- 执行后校验 1：驾驶舱菜单应存在且图标为 gauge。
SELECT menu_id, menu_name, parent_id, order_num, path, component, perms, icon
FROM sys_menu
WHERE menu_id = 2307;

-- 执行后校验 2：启用的 datafusion 角色均应拥有驾驶舱菜单。
SELECT r.role_id, r.role_name, r.role_key, rm.menu_id
FROM sys_role r
LEFT JOIN sys_role_menu rm
  ON rm.role_id = r.role_id
 AND rm.menu_id = 2307
WHERE r.role_key = 'datafusion'
  AND r.status = '0';

-- 执行后校验 3：标准业务菜单图标。
SELECT menu_id, menu_name, icon
FROM sys_menu
WHERE menu_id IN (
  2200, 2201, 2202, 2203, 2204, 2205, 2206, 2207,
  2300, 2301, 2302, 2304, 2305, 2306, 2307,
  2400, 2414, 2415, 2500, 2507, 3000, 3001, 3002
)
ORDER BY menu_id;
