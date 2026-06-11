-- v3.0.2 自动化巡检配置入口与目标表单优化
-- 说明：
-- 1. 将巡检目标、巡检模板、巡检计划收敛到“巡检配置”一个入口。
-- 2. 原“巡检目标”“巡检计划”菜单保留但隐藏，避免影响已有权限和历史路由。
-- 3. 不修改业务表结构，不影响已有巡检目标、模板、计划和记录数据。

START TRANSACTION;

UPDATE sys_menu
SET menu_name = '巡检配置',
    order_num = 1,
    path = 'config',
    component = 'support/autoInspection/index',
    `query` = '{"tab":"target"}',
    route_name = 'AutoInspectionConfig',
    visible = '0',
    status = '0',
    perms = '',
    icon = 'setting',
    remark = '统一维护巡检目标、模板和计划',
    update_by = 'admin',
    update_time = NOW()
WHERE menu_id = 2301;

UPDATE sys_menu
SET visible = '1',
    status = '0',
    `query` = '{"tab":"target"}',
    remark = '已合并到巡检配置入口',
    update_by = 'admin',
    update_time = NOW()
WHERE menu_id = 2302;

UPDATE sys_menu
SET visible = '1',
    status = '0',
    `query` = '{"tab":"plan"}',
    remark = '已合并到巡检配置入口',
    update_by = 'admin',
    update_time = NOW()
WHERE menu_id = 2303;

UPDATE sys_menu
SET menu_name = '巡检记录',
    order_num = 2,
    path = 'record',
    component = 'support/autoInspection/index',
    `query` = '{"tab":"record"}',
    route_name = 'AutoInspectionRecord',
    visible = '0',
    status = '0',
    perms = 'support:autoInspection:query',
    icon = 'documentation',
    update_by = 'admin',
    update_time = NOW()
WHERE menu_id = 2304;

UPDATE sys_menu
SET parent_id = 2301,
    order_num = 1,
    update_by = 'admin',
    update_time = NOW()
WHERE menu_id = 2311;

UPDATE sys_menu
SET parent_id = 2301,
    order_num = 2,
    update_by = 'admin',
    update_time = NOW()
WHERE menu_id = 2312;

UPDATE sys_menu
SET parent_id = 2301,
    order_num = 3,
    update_by = 'admin',
    update_time = NOW()
WHERE menu_id = 2313;

UPDATE sys_menu
SET parent_id = 2301,
    order_num = 4,
    update_by = 'admin',
    update_time = NOW()
WHERE menu_id = 2315;

UPDATE sys_menu
SET parent_id = 2304,
    order_num = 1,
    update_by = 'admin',
    update_time = NOW()
WHERE menu_id = 2314;

UPDATE sys_menu
SET parent_id = 2304,
    order_num = 2,
    update_by = 'admin',
    update_time = NOW()
WHERE menu_id = 2316;

INSERT INTO sys_role_menu(role_id, menu_id)
SELECT r.role_id, m.menu_id
FROM sys_role r
INNER JOIN sys_menu m ON m.menu_id IN (2300, 2301, 2302, 2303, 2304, 2311, 2312, 2313, 2314, 2315, 2316)
WHERE r.role_key = 'datafusion'
  AND NOT EXISTS (
    SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = r.role_id AND rm.menu_id = m.menu_id
  );

COMMIT;
