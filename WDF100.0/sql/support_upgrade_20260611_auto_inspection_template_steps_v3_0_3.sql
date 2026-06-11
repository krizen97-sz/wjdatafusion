-- v3.0.3 自动化巡检模板步骤化配置修复
-- 说明：
-- 1. 巡检目标不再作为独立配置入口暴露，统一在巡检模板步骤中配置。
-- 2. 巡检配置菜单默认进入“巡检模板”，避免继续打开旧目标配置页。
-- 3. 不修改业务表结构，不影响已有巡检模板、目标、计划和记录数据。

START TRANSACTION;

UPDATE sys_menu
SET menu_name = '巡检配置',
    order_num = 1,
    path = 'config',
    component = 'support/autoInspection/index',
    `query` = '{"tab":"template"}',
    route_name = 'AutoInspectionConfig',
    visible = '0',
    status = '0',
    perms = '',
    icon = 'setting',
    remark = '在模板步骤中选择巡检工具并配置目标、阈值和参数',
    update_by = 'admin',
    update_time = NOW()
WHERE menu_id = 2301;

UPDATE sys_menu
SET visible = '1',
    status = '0',
    `query` = '{"tab":"target"}',
    remark = '旧目标独立入口已隐藏，目标在巡检模板步骤内配置',
    update_by = 'admin',
    update_time = NOW()
WHERE menu_id = 2302;

UPDATE sys_menu
SET visible = '1',
    status = '0',
    `query` = '{"tab":"plan"}',
    remark = '计划入口已合并到巡检配置页面',
    update_by = 'admin',
    update_time = NOW()
WHERE menu_id = 2303;

COMMIT;
