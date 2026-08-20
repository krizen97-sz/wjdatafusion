-- v3.10.1 自动化巡检数据库查询工具中文字符修复
-- 说明：不修改表结构，只修复 DATABASE_QUERY 内置工具的中文名称、单位和备注。
-- 中文值使用 UTF-8 十六进制写入，避免 MySQL 客户端默认 latin1 时再次产生乱码。

SET NAMES utf8mb4;

START TRANSACTION;

INSERT INTO sup_auto_inspection_tool(
  tool_code, tool_name, tool_type, value_unit, default_compare_rule,
  default_threshold_value, default_timeout_seconds, default_time_window_minutes,
  param_schema, built_in_flag, status, create_by, create_time, remark
)
SELECT
  'DATABASE_QUERY',
  CONVERT(0xe695b0e68daee5ba93e69fa5e8afa2e6a380e69fa5 USING utf8mb4),
  'DATABASE_QUERY',
  CONVERT(0xe69da1 USING utf8mb4),
  'MIN', 1, 15, 0,
  '{"fields":["databaseType","host","port","databaseName","query","resultMode","resultColumn"]}',
  'Y', '0', 'admin', NOW(),
  CONVERT(0xe887aae58aa8e58c96e5b7a1e6a380e58685e7bdaee58faae8afbbe695b0e68daee5ba93e58f96e695b0e5b7a5e585b7 USING utf8mb4)
WHERE NOT EXISTS (
  SELECT 1 FROM sup_auto_inspection_tool WHERE tool_code = 'DATABASE_QUERY'
);

UPDATE sup_auto_inspection_tool
SET tool_name = CONVERT(0xe695b0e68daee5ba93e69fa5e8afa2e6a380e69fa5 USING utf8mb4),
    value_unit = CONVERT(0xe69da1 USING utf8mb4),
    remark = CONVERT(0xe887aae58aa8e58c96e5b7a1e6a380e58685e7bdaee58faae8afbbe695b0e68daee5ba93e58f96e695b0e5b7a5e585b7 USING utf8mb4),
    update_by = 'admin',
    update_time = NOW()
WHERE tool_code = 'DATABASE_QUERY';

COMMIT;
