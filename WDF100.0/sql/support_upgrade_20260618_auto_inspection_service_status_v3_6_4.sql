-- 自动化巡检服务器服务状态检测工具增补 v3.6.4
-- 说明：不修改表结构，仅补充 SERVER_SERVICE_STATUS 内置工具。

INSERT INTO sup_auto_inspection_tool(
  tool_code,
  tool_name,
  tool_type,
  value_unit,
  default_compare_rule,
  default_threshold_value,
  default_timeout_seconds,
  default_time_window_minutes,
  param_schema,
  built_in_flag,
  status,
  create_by,
  create_time,
  remark
)
SELECT
  'SERVER_SERVICE_STATUS',
  '服务器服务状态检测',
  'SERVER_SERVICE_STATUS',
  '状态',
  'MIN',
  1,
  15,
  0,
  '{"fields":["serviceName","privilegeMode","autoRestart","restartWaitSeconds"]}',
  'Y',
  '0',
  'admin',
  NOW(),
  '自动化巡检内置工具'
WHERE NOT EXISTS (
  SELECT 1 FROM sup_auto_inspection_tool WHERE tool_code = 'SERVER_SERVICE_STATUS'
);

UPDATE sup_auto_inspection_tool
SET tool_name = '服务器服务状态检测',
    tool_type = 'SERVER_SERVICE_STATUS',
    value_unit = '状态',
    default_compare_rule = 'MIN',
    default_threshold_value = 1,
    default_timeout_seconds = 15,
    default_time_window_minutes = 0,
    param_schema = '{"fields":["serviceName","privilegeMode","autoRestart","restartWaitSeconds"]}',
    built_in_flag = 'Y',
    status = '0',
    update_by = 'admin',
    update_time = NOW(),
    remark = '自动化巡检内置工具'
WHERE tool_code = 'SERVER_SERVICE_STATUS';
