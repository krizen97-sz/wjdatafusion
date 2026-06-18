-- 服务器服务状态检测多子项配置 v3.6.7
-- 说明：
-- 1. 不修改表结构。
-- 2. 将 SERVER_SERVICE_STATUS 工具参数说明从单目标字段调整为 serverTargets 多子项模型。

UPDATE sup_auto_inspection_tool
SET param_schema = '{"fields":["serverTargets","serviceName","privilegeMode","autoRestart","restartWaitSeconds"]}',
    update_by = 'admin',
    update_time = NOW(),
    remark = '服务器服务状态检测支持多服务器服务子项配置'
WHERE tool_code = 'SERVER_SERVICE_STATUS';
