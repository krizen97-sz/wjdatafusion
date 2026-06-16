-- v3.3.5 自动化巡检服务器目录多服务器目标配置
-- 说明：不修改业务表结构，仅修正内置工具参数说明，支持一个服务器目录检测步骤配置多台服务器。

UPDATE sup_auto_inspection_tool
SET param_schema = '{"fields":["serverTargets","recursive","filePattern"]}',
    remark = '自动化巡检内置工具',
    update_by = 'admin',
    update_time = NOW()
WHERE tool_code = 'SERVER_FILE_COUNT';
