-- v3.10.0 自动化巡检 ETL 化编排与数据库查询检查工具
-- 兼容说明：不修改现有业务表，步骤复检和失败策略继续存入 step_params JSON。

SET NAMES utf8mb4;

START TRANSACTION;

INSERT INTO sup_auto_inspection_tool(
  tool_code, tool_name, tool_type, value_unit, default_compare_rule,
  default_threshold_value, default_timeout_seconds, default_time_window_minutes,
  param_schema, built_in_flag, status, create_by, create_time, remark
)
SELECT
  'DATABASE_QUERY', '数据库查询检查', 'DATABASE_QUERY', '条', 'MIN',
  1, 15, 0,
  '{"fields":["databaseType","host","port","databaseName","query","resultMode","resultColumn"]}',
  'Y', '0', 'admin', NOW(), '自动化巡检内置只读数据库取数工具'
WHERE NOT EXISTS (
  SELECT 1 FROM sup_auto_inspection_tool WHERE tool_code = 'DATABASE_QUERY'
);

UPDATE sup_auto_inspection_tool
SET tool_name = '数据库查询检查',
    tool_type = 'DATABASE_QUERY',
    value_unit = '条',
    default_compare_rule = 'MIN',
    default_threshold_value = 1,
    default_timeout_seconds = 15,
    default_time_window_minutes = 0,
    param_schema = '{"fields":["databaseType","host","port","databaseName","query","resultMode","resultColumn"]}',
    built_in_flag = 'Y',
    status = '0',
    remark = '自动化巡检内置只读数据库取数工具'
WHERE tool_code = 'DATABASE_QUERY';

COMMIT;
