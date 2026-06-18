-- 自动化巡检黑盒检测工具增补 v3.6.0
-- 说明：不修改表结构，仅补充 HTTP 健康检测和 TCP 端口连通性检测两个内置工具。

INSERT INTO sup_auto_inspection_tool(tool_code, tool_name, tool_type, value_unit, default_compare_rule, default_threshold_value, default_timeout_seconds, default_time_window_minutes, param_schema, built_in_flag, status, create_by, create_time, remark)
SELECT 'HTTP_HEALTH', 'HTTP接口健康检测', 'HTTP_HEALTH', 'ms', 'MAX', 3000, 10, 0, '{"fields":["url","httpMethod","expectedStatus","timeoutSeconds"]}', 'Y', '0', 'admin', NOW(), '自动化巡检内置工具'
WHERE NOT EXISTS (SELECT 1 FROM sup_auto_inspection_tool WHERE tool_code = 'HTTP_HEALTH');

UPDATE sup_auto_inspection_tool
SET tool_name = 'HTTP接口健康检测',
    tool_type = 'HTTP_HEALTH',
    value_unit = 'ms',
    default_compare_rule = 'MAX',
    default_threshold_value = 3000,
    default_timeout_seconds = 10,
    default_time_window_minutes = 0,
    param_schema = '{"fields":["url","httpMethod","expectedStatus","timeoutSeconds"]}',
    built_in_flag = 'Y',
    status = '0',
    update_by = 'admin',
    update_time = NOW(),
    remark = '自动化巡检内置工具'
WHERE tool_code = 'HTTP_HEALTH';

INSERT INTO sup_auto_inspection_tool(tool_code, tool_name, tool_type, value_unit, default_compare_rule, default_threshold_value, default_timeout_seconds, default_time_window_minutes, param_schema, built_in_flag, status, create_by, create_time, remark)
SELECT 'TCP_PORT_CHECK', 'TCP端口连通性检测', 'TCP_PORT_CHECK', 'ms', 'MAX', 1000, 5, 0, '{"fields":["host","port","timeoutSeconds"]}', 'Y', '0', 'admin', NOW(), '自动化巡检内置工具'
WHERE NOT EXISTS (SELECT 1 FROM sup_auto_inspection_tool WHERE tool_code = 'TCP_PORT_CHECK');

UPDATE sup_auto_inspection_tool
SET tool_name = 'TCP端口连通性检测',
    tool_type = 'TCP_PORT_CHECK',
    value_unit = 'ms',
    default_compare_rule = 'MAX',
    default_threshold_value = 1000,
    default_timeout_seconds = 5,
    default_time_window_minutes = 0,
    param_schema = '{"fields":["host","port","timeoutSeconds"]}',
    built_in_flag = 'Y',
    status = '0',
    update_by = 'admin',
    update_time = NOW(),
    remark = '自动化巡检内置工具'
WHERE tool_code = 'TCP_PORT_CHECK';
