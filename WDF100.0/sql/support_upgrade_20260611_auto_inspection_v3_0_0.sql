-- v3.0.0 自动化巡检抽象化重构
-- 说明：新增独立“自动化巡检”一级模块，旧 TIM 巡检表不删除，旧入口隐藏。

CREATE TABLE IF NOT EXISTS sup_auto_inspection_tool (
  tool_code                   VARCHAR(64)   NOT NULL COMMENT '工具编码',
  tool_name                   VARCHAR(120)  NOT NULL COMMENT '工具名称',
  tool_type                   VARCHAR(64)   NOT NULL COMMENT '工具类型',
  value_unit                  VARCHAR(32)   DEFAULT NULL COMMENT '结果单位',
  default_compare_rule        VARCHAR(16)   DEFAULT 'MAX' COMMENT '默认比较规则（MIN最低阈值 MAX最高阈值）',
  default_threshold_value     DECIMAL(18,2) DEFAULT NULL COMMENT '默认阈值',
  default_timeout_seconds     INT           DEFAULT 10 COMMENT '默认超时秒数',
  default_time_window_minutes INT           DEFAULT 0 COMMENT '默认统计窗口分钟数',
  param_schema                TEXT          COMMENT '参数结构描述',
  built_in_flag               CHAR(1)       DEFAULT 'Y' COMMENT '是否内置（Y是 N否）',
  status                      CHAR(1)       DEFAULT '0' COMMENT '状态（0正常 1停用）',
  create_by                   VARCHAR(64)   DEFAULT '' COMMENT '创建者',
  create_time                 DATETIME      DEFAULT NULL COMMENT '创建时间',
  update_by                   VARCHAR(64)   DEFAULT '' COMMENT '更新者',
  update_time                 DATETIME      DEFAULT NULL COMMENT '更新时间',
  remark                      VARCHAR(500)  DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (tool_code),
  KEY idx_sup_auto_tool_type (tool_type, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='自动化巡检工具定义';

CREATE TABLE IF NOT EXISTS sup_auto_inspection_target (
  target_id        BIGINT        NOT NULL AUTO_INCREMENT COMMENT '目标ID',
  target_name      VARCHAR(120)  NOT NULL COMMENT '目标名称',
  target_type      VARCHAR(32)   NOT NULL COMMENT '目标类型（KAFKA HTTP FTP SERVER）',
  server_id        BIGINT        DEFAULT NULL COMMENT '关联服务器ID',
  host             VARCHAR(255)  DEFAULT NULL COMMENT '主机或Kafka bootstrap',
  port             INT           DEFAULT NULL COMMENT '端口',
  path             VARCHAR(500)  DEFAULT NULL COMMENT '默认目录路径或挂载点',
  url              VARCHAR(1000) DEFAULT NULL COMMENT 'HTTP接口地址',
  http_method      VARCHAR(10)   DEFAULT NULL COMMENT 'HTTP方法',
  topic            VARCHAR(200)  DEFAULT NULL COMMENT 'Kafka Topic',
  consumer_group   VARCHAR(200)  DEFAULT NULL COMMENT 'Kafka消费组',
  username         VARCHAR(128)  DEFAULT NULL COMMENT '登录账号',
  password_cipher  VARCHAR(1024) DEFAULT NULL COMMENT '登录密码密文',
  app_key          VARCHAR(128)  DEFAULT NULL COMMENT '接口AppKey',
  secret_cipher    VARCHAR(1024) DEFAULT NULL COMMENT '接口密钥密文',
  result_path      VARCHAR(200)  DEFAULT NULL COMMENT 'HTTP结果路径',
  extra_params     TEXT          COMMENT '扩展参数或请求体模板',
  status           CHAR(1)       DEFAULT '0' COMMENT '状态（0正常 1停用）',
  create_by        VARCHAR(64)   DEFAULT '' COMMENT '创建者',
  create_time      DATETIME      DEFAULT NULL COMMENT '创建时间',
  update_by        VARCHAR(64)   DEFAULT '' COMMENT '更新者',
  update_time      DATETIME      DEFAULT NULL COMMENT '更新时间',
  remark           VARCHAR(500)  DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (target_id),
  KEY idx_sup_auto_target_type (target_type, status),
  KEY idx_sup_auto_target_server (server_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='自动化巡检目标';

CREATE TABLE IF NOT EXISTS sup_auto_inspection_template (
  template_id    BIGINT       NOT NULL AUTO_INCREMENT COMMENT '模板ID',
  template_name  VARCHAR(120) NOT NULL COMMENT '模板名称',
  template_desc  VARCHAR(500) DEFAULT NULL COMMENT '模板描述',
  status         CHAR(1)      DEFAULT '0' COMMENT '状态（0正常 1停用）',
  create_by      VARCHAR(64)  DEFAULT '' COMMENT '创建者',
  create_time    DATETIME     DEFAULT NULL COMMENT '创建时间',
  update_by      VARCHAR(64)  DEFAULT '' COMMENT '更新者',
  update_time    DATETIME     DEFAULT NULL COMMENT '更新时间',
  remark         VARCHAR(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (template_id),
  KEY idx_sup_auto_template_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='自动化巡检模板';

CREATE TABLE IF NOT EXISTS sup_auto_inspection_template_step (
  step_id              BIGINT        NOT NULL AUTO_INCREMENT COMMENT '模板步骤ID',
  template_id          BIGINT        NOT NULL COMMENT '模板ID',
  tool_code            VARCHAR(64)   NOT NULL COMMENT '工具编码',
  step_name            VARCHAR(120)  NOT NULL COMMENT '步骤名称',
  enabled_flag         CHAR(1)       DEFAULT 'Y' COMMENT '是否启用（Y是 N否）',
  sort_order           INT           DEFAULT 0 COMMENT '排序',
  threshold_value      DECIMAL(18,2) DEFAULT NULL COMMENT '告警阈值',
  threshold_unit       VARCHAR(32)   DEFAULT NULL COMMENT '阈值单位',
  compare_rule         VARCHAR(16)   DEFAULT 'MAX' COMMENT '比较规则（MIN最低阈值 MAX最高阈值）',
  time_window_minutes  INT           DEFAULT 0 COMMENT '统计时间窗口分钟数',
  timeout_seconds      INT           DEFAULT 10 COMMENT '超时时间秒',
  step_params          TEXT          COMMENT '步骤参数JSON',
  create_by            VARCHAR(64)   DEFAULT '' COMMENT '创建者',
  create_time          DATETIME      DEFAULT NULL COMMENT '创建时间',
  update_by            VARCHAR(64)   DEFAULT '' COMMENT '更新者',
  update_time          DATETIME      DEFAULT NULL COMMENT '更新时间',
  remark               VARCHAR(500)  DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (step_id),
  KEY idx_sup_auto_step_template (template_id, sort_order),
  KEY idx_sup_auto_step_tool (tool_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='自动化巡检模板步骤';

CREATE TABLE IF NOT EXISTS sup_auto_inspection_template_step_target (
  step_target_id BIGINT      NOT NULL AUTO_INCREMENT COMMENT '步骤目标关系ID',
  step_id        BIGINT      NOT NULL COMMENT '模板步骤ID',
  target_id      BIGINT      NOT NULL COMMENT '目标ID',
  create_by      VARCHAR(64) DEFAULT '' COMMENT '创建者',
  create_time    DATETIME    DEFAULT NULL COMMENT '创建时间',
  update_by      VARCHAR(64) DEFAULT '' COMMENT '更新者',
  update_time    DATETIME    DEFAULT NULL COMMENT '更新时间',
  remark         VARCHAR(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (step_target_id),
  UNIQUE KEY uk_sup_auto_step_target (step_id, target_id),
  KEY idx_sup_auto_step_target_step (step_id),
  KEY idx_sup_auto_step_target_target (target_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='自动化巡检模板步骤目标关系';

CREATE TABLE IF NOT EXISTS sup_auto_inspection_plan (
  plan_id         BIGINT        NOT NULL AUTO_INCREMENT COMMENT '计划ID',
  template_id     BIGINT        NOT NULL COMMENT '模板ID',
  plan_name       VARCHAR(120)  NOT NULL COMMENT '计划名称',
  cron_expression VARCHAR(255)  NOT NULL COMMENT '系统生成Cron表达式',
  cron_config     TEXT          COMMENT '可视化周期配置JSON',
  job_id          BIGINT        DEFAULT NULL COMMENT '若依定时任务ID',
  report_style    VARCHAR(32)   DEFAULT 'STANDARD' COMMENT '报告样式',
  status          CHAR(1)       DEFAULT '0' COMMENT '状态（0正常 1暂停）',
  create_by       VARCHAR(64)   DEFAULT '' COMMENT '创建者',
  create_time     DATETIME      DEFAULT NULL COMMENT '创建时间',
  update_by       VARCHAR(64)   DEFAULT '' COMMENT '更新者',
  update_time     DATETIME      DEFAULT NULL COMMENT '更新时间',
  remark          VARCHAR(500)  DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (plan_id),
  KEY idx_sup_auto_plan_template (template_id),
  KEY idx_sup_auto_plan_status (status),
  KEY idx_sup_auto_plan_job (job_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='自动化巡检计划';

CREATE TABLE IF NOT EXISTS sup_auto_inspection_record (
  record_id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '记录ID',
  inspection_time    DATETIME     NOT NULL COMMENT '巡检时间',
  source_type        VARCHAR(16)  DEFAULT 'AUTO' COMMENT '执行来源（AUTO自动 MANUAL手动）',
  result_status      CHAR(1)      DEFAULT '3' COMMENT '巡检结果（1正常 2异常 3未检测）',
  executor_name      VARCHAR(64)  DEFAULT NULL COMMENT '执行人名称',
  template_id        BIGINT       DEFAULT NULL COMMENT '模板ID',
  template_name      VARCHAR(120) DEFAULT NULL COMMENT '模板名称',
  plan_id            BIGINT       DEFAULT NULL COMMENT '计划ID',
  plan_name          VARCHAR(120) DEFAULT NULL COMMENT '计划名称',
  report_style       VARCHAR(32)  DEFAULT 'STANDARD' COMMENT '报告样式',
  enabled_step_count INT          DEFAULT 0 COMMENT '启用步骤数',
  skipped_step_count INT          DEFAULT 0 COMMENT '跳过步骤数',
  target_count       INT          DEFAULT 0 COMMENT '检测目标数',
  abnormal_count     INT          DEFAULT 0 COMMENT '异常步骤数',
  summary            VARCHAR(500) DEFAULT NULL COMMENT '巡检摘要',
  abnormal_summary   TEXT         COMMENT '异常摘要',
  create_by          VARCHAR(64)  DEFAULT '' COMMENT '创建者',
  create_time        DATETIME     DEFAULT NULL COMMENT '创建时间',
  update_by          VARCHAR(64)  DEFAULT '' COMMENT '更新者',
  update_time        DATETIME     DEFAULT NULL COMMENT '更新时间',
  remark             VARCHAR(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (record_id),
  KEY idx_sup_auto_record_time (inspection_time),
  KEY idx_sup_auto_record_result (result_status, inspection_time),
  KEY idx_sup_auto_record_plan (plan_id, inspection_time),
  KEY idx_sup_auto_record_template (template_id, inspection_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='自动化巡检记录';

CREATE TABLE IF NOT EXISTS sup_auto_inspection_step_result (
  step_result_id      BIGINT        NOT NULL AUTO_INCREMENT COMMENT '步骤结果ID',
  record_id           BIGINT        NOT NULL COMMENT '记录ID',
  step_id             BIGINT        DEFAULT NULL COMMENT '模板步骤ID',
  tool_code           VARCHAR(64)   NOT NULL COMMENT '工具编码',
  tool_name           VARCHAR(120)  DEFAULT NULL COMMENT '工具名称',
  tool_type           VARCHAR(64)   NOT NULL COMMENT '工具类型',
  step_name           VARCHAR(120)  NOT NULL COMMENT '步骤名称',
  enabled_flag        CHAR(1)       DEFAULT 'Y' COMMENT '是否启用',
  sort_order          INT           DEFAULT 0 COMMENT '排序',
  threshold_value     DECIMAL(18,2) DEFAULT NULL COMMENT '告警阈值',
  threshold_unit      VARCHAR(32)   DEFAULT NULL COMMENT '阈值单位',
  compare_rule        VARCHAR(16)   DEFAULT 'MAX' COMMENT '比较规则',
  time_window_minutes INT           DEFAULT 0 COMMENT '统计时间窗口分钟数',
  timeout_seconds     INT           DEFAULT 10 COMMENT '超时时间秒',
  step_params         TEXT          COMMENT '步骤参数JSON',
  result_status       CHAR(1)       DEFAULT '3' COMMENT '步骤结果（1正常 2异常 3未检测）',
  actual_value        DECIMAL(18,2) DEFAULT NULL COMMENT '代表实际值',
  actual_unit         VARCHAR(32)   DEFAULT NULL COMMENT '实际单位',
  result_summary      VARCHAR(1000) DEFAULT NULL COMMENT '结果摘要',
  create_by           VARCHAR(64)   DEFAULT '' COMMENT '创建者',
  create_time         DATETIME      DEFAULT NULL COMMENT '创建时间',
  update_by           VARCHAR(64)   DEFAULT '' COMMENT '更新者',
  update_time         DATETIME      DEFAULT NULL COMMENT '更新时间',
  remark              VARCHAR(500)  DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (step_result_id),
  KEY idx_sup_auto_step_result_record (record_id, sort_order),
  KEY idx_sup_auto_step_result_tool (tool_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='自动化巡检步骤结果';

CREATE TABLE IF NOT EXISTS sup_auto_inspection_target_result (
  result_id       BIGINT        NOT NULL AUTO_INCREMENT COMMENT '目标结果ID',
  record_id       BIGINT        NOT NULL COMMENT '记录ID',
  step_result_id  BIGINT        NOT NULL COMMENT '步骤结果ID',
  target_id       BIGINT        DEFAULT NULL COMMENT '目标ID',
  target_name     VARCHAR(120)  DEFAULT NULL COMMENT '目标名称',
  target_type     VARCHAR(32)   DEFAULT NULL COMMENT '目标类型',
  result_status   CHAR(1)       DEFAULT '3' COMMENT '目标结果（1正常 2异常 3未检测）',
  actual_value    DECIMAL(18,2) DEFAULT NULL COMMENT '实际值',
  actual_unit     VARCHAR(32)   DEFAULT NULL COMMENT '实际单位',
  result_detail   VARCHAR(1000) DEFAULT NULL COMMENT '结果详情',
  error_message   VARCHAR(1000) DEFAULT NULL COMMENT '异常原因',
  create_by       VARCHAR(64)   DEFAULT '' COMMENT '创建者',
  create_time     DATETIME      DEFAULT NULL COMMENT '创建时间',
  update_by       VARCHAR(64)   DEFAULT '' COMMENT '更新者',
  update_time     DATETIME      DEFAULT NULL COMMENT '更新时间',
  remark          VARCHAR(500)  DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (result_id),
  KEY idx_sup_auto_target_result_record (record_id, step_result_id),
  KEY idx_sup_auto_target_result_target (target_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='自动化巡检目标结果';

INSERT INTO sup_auto_inspection_tool(tool_code, tool_name, tool_type, value_unit, default_compare_rule, default_threshold_value, default_timeout_seconds, default_time_window_minutes, param_schema, built_in_flag, status, create_by, create_time, remark)
SELECT 'KAFKA_LAG', 'Kafka消费积压检测', 'KAFKA_LAG', '条', 'MAX', 2000, 10, 0, '{"fields":["topic","consumerGroup"]}', 'Y', '0', 'admin', NOW(), '自动化巡检内置工具'
WHERE NOT EXISTS (SELECT 1 FROM sup_auto_inspection_tool WHERE tool_code = 'KAFKA_LAG');
INSERT INTO sup_auto_inspection_tool(tool_code, tool_name, tool_type, value_unit, default_compare_rule, default_threshold_value, default_timeout_seconds, default_time_window_minutes, param_schema, built_in_flag, status, create_by, create_time, remark)
SELECT 'HTTP_COUNT', '海康接口数量检测', 'HTTP_COUNT', '条', 'MIN', 0, 10, 480, '{"fields":["resultPath","extraParams","timeWindowMinutes"]}', 'Y', '0', 'admin', NOW(), '自动化巡检内置工具'
WHERE NOT EXISTS (SELECT 1 FROM sup_auto_inspection_tool WHERE tool_code = 'HTTP_COUNT');
INSERT INTO sup_auto_inspection_tool(tool_code, tool_name, tool_type, value_unit, default_compare_rule, default_threshold_value, default_timeout_seconds, default_time_window_minutes, param_schema, built_in_flag, status, create_by, create_time, remark)
SELECT 'FTP_FILE_COUNT', 'FTP目录文件数量检测', 'FTP_FILE_COUNT', '个', 'MAX', 50, 10, 0, '{"fields":["path"]}', 'Y', '0', 'admin', NOW(), '自动化巡检内置工具'
WHERE NOT EXISTS (SELECT 1 FROM sup_auto_inspection_tool WHERE tool_code = 'FTP_FILE_COUNT');
INSERT INTO sup_auto_inspection_tool(tool_code, tool_name, tool_type, value_unit, default_compare_rule, default_threshold_value, default_timeout_seconds, default_time_window_minutes, param_schema, built_in_flag, status, create_by, create_time, remark)
SELECT 'SERVER_FILE_COUNT', '服务器目录文件数量检测', 'SERVER_FILE_COUNT', '个', 'MAX', 20, 10, 0, '{"fields":["path","recursive","filePattern"]}', 'Y', '0', 'admin', NOW(), '自动化巡检内置工具'
WHERE NOT EXISTS (SELECT 1 FROM sup_auto_inspection_tool WHERE tool_code = 'SERVER_FILE_COUNT');
INSERT INTO sup_auto_inspection_tool(tool_code, tool_name, tool_type, value_unit, default_compare_rule, default_threshold_value, default_timeout_seconds, default_time_window_minutes, param_schema, built_in_flag, status, create_by, create_time, remark)
SELECT 'SERVER_DISK', '服务器磁盘使用率检测', 'SERVER_DISK', '%', 'MAX', 80, 10, 0, '{"fields":["path"]}', 'Y', '0', 'admin', NOW(), '自动化巡检内置工具'
WHERE NOT EXISTS (SELECT 1 FROM sup_auto_inspection_tool WHERE tool_code = 'SERVER_DISK');

UPDATE sys_menu SET visible = '1', update_time = NOW(), remark = '已由自动化巡检模块替代，保留旧数据入口隐藏'
WHERE menu_id = 2206;

INSERT INTO sys_menu(menu_id, menu_name, parent_id, order_num, path, component, `query`, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
VALUES
(2300, '自动化巡检', 0, 6, 'autoInspection', NULL, '', 'AutoInspection', 1, 0, 'M', '0', '0', '', 'monitor', 'admin', NOW(), '', NULL, '可配置自动化巡检中心')
ON DUPLICATE KEY UPDATE menu_name=VALUES(menu_name), path=VALUES(path), route_name=VALUES(route_name), visible=VALUES(visible), status=VALUES(status), remark=VALUES(remark);

INSERT INTO sys_menu(menu_id, menu_name, parent_id, order_num, path, component, `query`, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
VALUES
(2301, '巡检配置', 2300, 1, 'config', 'support/autoInspection/index', '{"tab":"template"}', 'AutoInspectionConfig', 1, 0, 'C', '0', '0', '', 'setting', 'admin', NOW(), '', NULL, '在模板步骤中选择巡检工具并配置目标、阈值和参数'),
(2302, '巡检目标', 2300, 2, 'target', 'support/autoInspection/index', '{"tab":"target"}', 'AutoInspectionTarget', 1, 0, 'C', '1', '0', 'support:autoInspection:target', 'server', 'admin', NOW(), '', NULL, '旧目标独立入口已隐藏，目标在巡检模板步骤内配置'),
(2303, '巡检计划', 2300, 3, 'plan', 'support/autoInspection/index', '{"tab":"plan"}', 'AutoInspectionPlan', 1, 0, 'C', '1', '0', 'support:autoInspection:plan', 'time', 'admin', NOW(), '', NULL, '已合并到巡检配置入口'),
(2304, '巡检记录', 2300, 2, 'record', 'support/autoInspection/index', '{"tab":"record"}', 'AutoInspectionRecord', 1, 0, 'C', '0', '0', 'support:autoInspection:query', 'documentation', 'admin', NOW(), '', NULL, '')
ON DUPLICATE KEY UPDATE menu_name=VALUES(menu_name), parent_id=VALUES(parent_id), order_num=VALUES(order_num), path=VALUES(path), component=VALUES(component), `query`=VALUES(`query`), route_name=VALUES(route_name), perms=VALUES(perms), icon=VALUES(icon), visible=VALUES(visible), status=VALUES(status), remark=VALUES(remark);

INSERT INTO sys_menu(menu_id, menu_name, parent_id, order_num, path, component, `query`, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
VALUES
(2311, '模板管理', 2301, 1, '#', '', '', '', 1, 0, 'F', '0', '0', 'support:autoInspection:template', '#', 'admin', NOW(), '', NULL, ''),
(2312, '目标管理', 2301, 2, '#', '', '', '', 1, 0, 'F', '0', '0', 'support:autoInspection:target', '#', 'admin', NOW(), '', NULL, ''),
(2313, '计划管理', 2301, 3, '#', '', '', '', 1, 0, 'F', '0', '0', 'support:autoInspection:plan', '#', 'admin', NOW(), '', NULL, ''),
(2314, '记录查询', 2304, 1, '#', '', '', '', 1, 0, 'F', '0', '0', 'support:autoInspection:query', '#', 'admin', NOW(), '', NULL, ''),
(2315, '巡检执行', 2301, 4, '#', '', '', '', 1, 0, 'F', '0', '0', 'support:autoInspection:run', '#', 'admin', NOW(), '', NULL, ''),
(2316, '巡检导出', 2304, 2, '#', '', '', '', 1, 0, 'F', '0', '0', 'support:autoInspection:export', '#', 'admin', NOW(), '', NULL, '')
ON DUPLICATE KEY UPDATE perms=VALUES(perms), menu_name=VALUES(menu_name), parent_id=VALUES(parent_id), order_num=VALUES(order_num), visible=VALUES(visible), status=VALUES(status);

INSERT INTO sys_role_menu(role_id, menu_id)
SELECT r.role_id, m.menu_id
FROM sys_role r
INNER JOIN sys_menu m ON m.menu_id IN (2300, 2301, 2302, 2303, 2304, 2311, 2312, 2313, 2314, 2315, 2316)
WHERE r.role_key = 'datafusion'
  AND NOT EXISTS (
    SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = r.role_id AND rm.menu_id = m.menu_id
  );
