-- v3.14.3 平台菜单语义图标升级
-- 仅更新若依 sys_menu.icon，不修改菜单权限、路由或业务数据，可重复执行。

START TRANSACTION;

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

SELECT menu_id, menu_name, icon
FROM sys_menu
WHERE menu_id IN (
  2200, 2201, 2202, 2203, 2204, 2205, 2206, 2207,
  2300, 2301, 2302, 2304, 2305, 2306, 2307,
  2400, 2414, 2415, 2500, 2507, 3000, 3001, 3002
)
ORDER BY menu_id;
