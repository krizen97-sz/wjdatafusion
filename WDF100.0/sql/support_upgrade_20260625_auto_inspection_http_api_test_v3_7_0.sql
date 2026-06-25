-- 自动化巡检接口调用测试工具 v3.7.0
-- 说明：
-- 1. 不新增业务表，复用 sup_auto_inspection_target.extra_params 保存请求、Body 和断言配置。
-- 2. 复用 sup_auto_inspection_target.secret_cipher 加密保存 Token、Basic 密码、API Key、敏感 Header/Cookie/Form/Query 值。
-- 3. 扩容目标结果详情字段，避免接口响应预览和断言失败摘要过长导致入库失败。

INSERT INTO sup_auto_inspection_tool(
  tool_code, tool_name, tool_type, value_unit,
  default_compare_rule, default_threshold_value, default_timeout_seconds, default_time_window_minutes,
  param_schema, built_in_flag, status, create_by, create_time, remark
)
SELECT
  'HTTP_API_TEST',
  '接口调用测试',
  'HTTP_API_TEST',
  'ms',
  'MAX',
  3000,
  10,
  0,
  '{"fields":["url","httpMethod","queryParams","headers","cookies","auth","bodyType","body","formParams","assertions","trustInternalCertificate"]}',
  'Y',
  '0',
  'admin',
  NOW(),
  '自动化巡检内置工具'
WHERE NOT EXISTS (
  SELECT 1 FROM sup_auto_inspection_tool WHERE tool_code = 'HTTP_API_TEST'
);

UPDATE sup_auto_inspection_tool
SET tool_name = '接口调用测试',
    tool_type = 'HTTP_API_TEST',
    value_unit = 'ms',
    default_compare_rule = 'MAX',
    default_threshold_value = 3000,
    default_timeout_seconds = 10,
    default_time_window_minutes = 0,
    param_schema = '{"fields":["url","httpMethod","queryParams","headers","cookies","auth","bodyType","body","formParams","assertions","trustInternalCertificate"]}',
    built_in_flag = 'Y',
    status = '0',
    update_by = 'admin',
    update_time = NOW(),
    remark = '自动化巡检内置工具'
WHERE tool_code = 'HTTP_API_TEST';

ALTER TABLE sup_auto_inspection_target_result
  MODIFY COLUMN result_detail MEDIUMTEXT DEFAULT NULL COMMENT '结果详情',
  MODIFY COLUMN error_message MEDIUMTEXT DEFAULT NULL COMMENT '异常信息';
