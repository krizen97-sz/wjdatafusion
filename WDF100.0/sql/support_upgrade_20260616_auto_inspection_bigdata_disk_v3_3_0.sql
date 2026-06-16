-- v3.3.0 自动化巡检新增大数据服务器爆盘检测
-- 说明：不修改表结构，仅新增/修正自动化巡检内置工具。

INSERT INTO sup_auto_inspection_tool(
  tool_code, tool_name, tool_type, value_unit, default_compare_rule,
  default_threshold_value, default_timeout_seconds, default_time_window_minutes,
  param_schema, built_in_flag, status, create_by, create_time, remark
)
SELECT
  'BIG_DATA_SERVER_DISK',
  '大数据服务器爆盘检测',
  'BIG_DATA_SERVER_DISK',
  '%',
  'MAX',
  85,
  15,
  0,
  '{"fields":["serverTargets","includePseudo"]}',
  'Y',
  '0',
  'admin',
  NOW(),
  '自动化巡检内置工具'
WHERE NOT EXISTS (
  SELECT 1 FROM sup_auto_inspection_tool WHERE tool_code = 'BIG_DATA_SERVER_DISK'
);

UPDATE sup_auto_inspection_tool
SET tool_name = '大数据服务器爆盘检测',
    tool_type = 'BIG_DATA_SERVER_DISK',
    value_unit = '%',
    default_compare_rule = 'MAX',
    default_threshold_value = 85,
    default_timeout_seconds = 15,
    default_time_window_minutes = 0,
    param_schema = '{"fields":["serverTargets","includePseudo"]}',
    built_in_flag = 'Y',
    status = '0',
    remark = '自动化巡检内置工具',
    update_by = 'admin',
    update_time = NOW()
WHERE tool_code = 'BIG_DATA_SERVER_DISK';
