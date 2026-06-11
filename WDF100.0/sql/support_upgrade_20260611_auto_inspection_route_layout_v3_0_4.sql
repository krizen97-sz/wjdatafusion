-- v3.0.4 自动化巡检动态路由 Layout 修复
-- 说明：
-- 1. 修复自动化巡检一级目录 component 为空导致若依动态路由生成空组件的问题。
-- 2. 不修改业务表结构，不影响已有巡检配置和巡检记录数据。

START TRANSACTION;

UPDATE sys_menu
SET component = 'Layout',
    update_by = 'admin',
    update_time = NOW()
WHERE menu_id = 2300
  AND (component IS NULL OR component = '');

COMMIT;
