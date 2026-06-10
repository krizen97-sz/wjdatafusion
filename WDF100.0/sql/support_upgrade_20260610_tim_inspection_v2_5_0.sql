-- v2.5.0 TIM系统巡检可配置迁入
-- 说明：新增TIM巡检记录、巡检项配置、巡检目标和目标明细表；补充菜单权限和默认7项配置。

CREATE TABLE IF NOT EXISTS sup_tim_inspection (
  inspection_id       BIGINT       NOT NULL AUTO_INCREMENT COMMENT '巡检ID',
  inspection_time     DATETIME     NOT NULL COMMENT '巡检时间',
  inspection_type     VARCHAR(64)  DEFAULT 'TIM_GA_VEHICLE' COMMENT '巡检类型',
  source_type         VARCHAR(16)  DEFAULT 'AUTO' COMMENT '执行来源（AUTO自动 MANUAL手动）',
  result_status       CHAR(1)      DEFAULT '3' COMMENT '巡检结果（1正常 2异常 3未检测）',
  executor_name       VARCHAR(64)  DEFAULT NULL COMMENT '执行人名称',
  enabled_item_count  INT          DEFAULT 0 COMMENT '启用项数',
  skipped_item_count  INT          DEFAULT 0 COMMENT '跳过项数',
  summary             VARCHAR(500) DEFAULT NULL COMMENT '巡检摘要',
  abnormal_summary    TEXT         DEFAULT NULL COMMENT '异常摘要',
  create_by           VARCHAR(64)  DEFAULT '' COMMENT '创建者',
  create_time         DATETIME     DEFAULT NULL COMMENT '创建时间',
  update_by           VARCHAR(64)  DEFAULT '' COMMENT '更新者',
  update_time         DATETIME     DEFAULT NULL COMMENT '更新时间',
  remark              VARCHAR(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (inspection_id),
  KEY idx_sup_tim_inspection_time (inspection_time),
  KEY idx_sup_tim_inspection_result (result_status, inspection_time),
  KEY idx_sup_tim_inspection_source (source_type, inspection_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='TIM系统巡检记录';

CREATE TABLE IF NOT EXISTS sup_tim_inspection_item (
  item_id              BIGINT        NOT NULL AUTO_INCREMENT COMMENT '巡检项结果ID',
  inspection_id        BIGINT        NOT NULL COMMENT '巡检ID',
  item_code            VARCHAR(64)   NOT NULL COMMENT '巡检项编码',
  item_name            VARCHAR(100)  NOT NULL COMMENT '巡检项名称',
  item_type            VARCHAR(32)   NOT NULL COMMENT '巡检项类型',
  enabled_flag         CHAR(1)       DEFAULT 'Y' COMMENT '执行时是否启用（Y是 N否）',
  sort_order           INT           DEFAULT 0 COMMENT '排序',
  threshold_value      DECIMAL(18,2) DEFAULT NULL COMMENT '阈值',
  threshold_unit       VARCHAR(32)   DEFAULT NULL COMMENT '阈值单位',
  compare_rule         VARCHAR(16)   DEFAULT 'MAX' COMMENT '比较规则（MIN最低阈值 MAX最高阈值）',
  time_window_minutes  INT           DEFAULT 0 COMMENT '统计时间窗口分钟数',
  timeout_seconds      INT           DEFAULT 10 COMMENT '超时时间秒',
  result_status        CHAR(1)       DEFAULT '3' COMMENT '巡检项结果（1正常 2异常 3未检测）',
  actual_value         DECIMAL(18,2) DEFAULT NULL COMMENT '实际代表值',
  actual_unit          VARCHAR(32)   DEFAULT NULL COMMENT '实际值单位',
  result_summary       VARCHAR(1000) DEFAULT NULL COMMENT '结果摘要',
  create_by            VARCHAR(64)   DEFAULT '' COMMENT '创建者',
  create_time          DATETIME      DEFAULT NULL COMMENT '创建时间',
  update_by            VARCHAR(64)   DEFAULT '' COMMENT '更新者',
  update_time          DATETIME      DEFAULT NULL COMMENT '更新时间',
  remark               VARCHAR(500)  DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (item_id),
  KEY idx_sup_tim_item_inspection (inspection_id, sort_order),
  KEY idx_sup_tim_item_code (item_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='TIM系统巡检项结果';

CREATE TABLE IF NOT EXISTS sup_tim_inspection_item_config (
  config_id            BIGINT        NOT NULL AUTO_INCREMENT COMMENT '配置ID',
  item_code            VARCHAR(64)   NOT NULL COMMENT '巡检项编码',
  item_name            VARCHAR(100)  NOT NULL COMMENT '巡检项名称',
  item_type            VARCHAR(32)   NOT NULL COMMENT '巡检项类型',
  enabled_flag         CHAR(1)       DEFAULT 'Y' COMMENT '是否启用（Y是 N否）',
  sort_order           INT           DEFAULT 0 COMMENT '排序',
  threshold_value      DECIMAL(18,2) DEFAULT NULL COMMENT '阈值',
  threshold_unit       VARCHAR(32)   DEFAULT NULL COMMENT '阈值单位',
  compare_rule         VARCHAR(16)   DEFAULT 'MAX' COMMENT '比较规则（MIN最低阈值 MAX最高阈值）',
  time_window_minutes  INT           DEFAULT 0 COMMENT '统计时间窗口分钟数',
  timeout_seconds      INT           DEFAULT 10 COMMENT '超时时间秒',
  status               CHAR(1)       DEFAULT '0' COMMENT '状态（0正常 1停用）',
  create_by            VARCHAR(64)   DEFAULT '' COMMENT '创建者',
  create_time          DATETIME      DEFAULT NULL COMMENT '创建时间',
  update_by            VARCHAR(64)   DEFAULT '' COMMENT '更新者',
  update_time          DATETIME      DEFAULT NULL COMMENT '更新时间',
  remark               VARCHAR(500)  DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (config_id),
  UNIQUE KEY uk_sup_tim_config_code (item_code),
  KEY idx_sup_tim_config_sort (sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='TIM系统巡检项配置';

CREATE TABLE IF NOT EXISTS sup_tim_inspection_target (
  target_id        BIGINT        NOT NULL AUTO_INCREMENT COMMENT '目标ID',
  item_code        VARCHAR(64)   NOT NULL COMMENT '巡检项编码',
  target_name      VARCHAR(120)  NOT NULL COMMENT '目标名称',
  target_type      VARCHAR(32)   NOT NULL COMMENT '目标类型',
  server_id        BIGINT        DEFAULT NULL COMMENT '关联服务器ID',
  host             VARCHAR(255)  DEFAULT NULL COMMENT '主机或Kafka bootstrap',
  port             INT           DEFAULT NULL COMMENT '端口',
  path             VARCHAR(500)  DEFAULT NULL COMMENT '目录路径或磁盘挂载点',
  url              VARCHAR(1000) DEFAULT NULL COMMENT 'HTTP接口地址',
  http_method      VARCHAR(10)   DEFAULT NULL COMMENT 'HTTP方法',
  topic            VARCHAR(200)  DEFAULT NULL COMMENT 'Kafka Topic',
  consumer_group   VARCHAR(200)  DEFAULT NULL COMMENT 'Kafka消费组',
  username         VARCHAR(128)  DEFAULT NULL COMMENT '登录账号',
  password_cipher  VARCHAR(1024) DEFAULT NULL COMMENT '登录密码密文',
  app_key          VARCHAR(128)  DEFAULT NULL COMMENT '接口AppKey',
  secret_cipher    VARCHAR(1024) DEFAULT NULL COMMENT '接口密钥密文',
  result_path      VARCHAR(200)  DEFAULT NULL COMMENT 'HTTP结果路径',
  extra_params     TEXT          DEFAULT NULL COMMENT '扩展参数/请求体模板',
  status           CHAR(1)       DEFAULT '0' COMMENT '状态（0正常 1停用）',
  create_by        VARCHAR(64)   DEFAULT '' COMMENT '创建者',
  create_time      DATETIME      DEFAULT NULL COMMENT '创建时间',
  update_by        VARCHAR(64)   DEFAULT '' COMMENT '更新者',
  update_time      DATETIME      DEFAULT NULL COMMENT '更新时间',
  remark           VARCHAR(500)  DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (target_id),
  KEY idx_sup_tim_target_item (item_code, status),
  KEY idx_sup_tim_target_server (server_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='TIM系统巡检目标配置';

CREATE TABLE IF NOT EXISTS sup_tim_inspection_target_result (
  result_id       BIGINT        NOT NULL AUTO_INCREMENT COMMENT '目标结果ID',
  inspection_id   BIGINT        NOT NULL COMMENT '巡检ID',
  item_id         BIGINT        NOT NULL COMMENT '巡检项结果ID',
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
  KEY idx_sup_tim_target_result_inspection (inspection_id, item_id),
  KEY idx_sup_tim_target_result_target (target_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='TIM系统巡检目标结果';

INSERT INTO sup_tim_inspection_item_config(item_code, item_name, item_type, enabled_flag, sort_order, threshold_value, threshold_unit, compare_rule, time_window_minutes, timeout_seconds, status, create_by, create_time, remark)
SELECT 'VEHICLE_PASS', '过车数量', 'HTTP_COUNT', 'Y', 1, 4000000, '辆', 'MIN', 480, 10, '0', 'admin', NOW(), 'TIM巡检内置项'
WHERE NOT EXISTS (SELECT 1 FROM sup_tim_inspection_item_config WHERE item_code = 'VEHICLE_PASS');
INSERT INTO sup_tim_inspection_item_config(item_code, item_name, item_type, enabled_flag, sort_order, threshold_value, threshold_unit, compare_rule, time_window_minutes, timeout_seconds, status, create_by, create_time, remark)
SELECT 'FTP_FILE', 'FTP文件数量', 'FTP', 'Y', 2, 50, '个', 'MAX', 0, 10, '0', 'admin', NOW(), 'TIM巡检内置项'
WHERE NOT EXISTS (SELECT 1 FROM sup_tim_inspection_item_config WHERE item_code = 'FTP_FILE');
INSERT INTO sup_tim_inspection_item_config(item_code, item_name, item_type, enabled_flag, sort_order, threshold_value, threshold_unit, compare_rule, time_window_minutes, timeout_seconds, status, create_by, create_time, remark)
SELECT 'DATAI_FILE', 'DataI文件数量', 'SFTP', 'Y', 3, 20, '个', 'MAX', 0, 10, '0', 'admin', NOW(), 'TIM巡检内置项'
WHERE NOT EXISTS (SELECT 1 FROM sup_tim_inspection_item_config WHERE item_code = 'DATAI_FILE');
INSERT INTO sup_tim_inspection_item_config(item_code, item_name, item_type, enabled_flag, sort_order, threshold_value, threshold_unit, compare_rule, time_window_minutes, timeout_seconds, status, create_by, create_time, remark)
SELECT 'KAFKA_ORIGIN', '原始Kafka积压', 'KAFKA', 'Y', 4, 2000, '条', 'MAX', 0, 10, '0', 'admin', NOW(), 'TIM巡检内置项'
WHERE NOT EXISTS (SELECT 1 FROM sup_tim_inspection_item_config WHERE item_code = 'KAFKA_ORIGIN');
INSERT INTO sup_tim_inspection_item_config(item_code, item_name, item_type, enabled_flag, sort_order, threshold_value, threshold_unit, compare_rule, time_window_minutes, timeout_seconds, status, create_by, create_time, remark)
SELECT 'KAFKA_SECOND', '二次分析Kafka积压', 'KAFKA', 'Y', 5, 2000, '条', 'MAX', 0, 10, '0', 'admin', NOW(), 'TIM巡检内置项'
WHERE NOT EXISTS (SELECT 1 FROM sup_tim_inspection_item_config WHERE item_code = 'KAFKA_SECOND');
INSERT INTO sup_tim_inspection_item_config(item_code, item_name, item_type, enabled_flag, sort_order, threshold_value, threshold_unit, compare_rule, time_window_minutes, timeout_seconds, status, create_by, create_time, remark)
SELECT 'DISK_USAGE', '大数据服务器磁盘', 'SERVER_DISK', 'Y', 6, 80, '%', 'MAX', 0, 10, '0', 'admin', NOW(), 'TIM巡检内置项'
WHERE NOT EXISTS (SELECT 1 FROM sup_tim_inspection_item_config WHERE item_code = 'DISK_USAGE');
INSERT INTO sup_tim_inspection_item_config(item_code, item_name, item_type, enabled_flag, sort_order, threshold_value, threshold_unit, compare_rule, time_window_minutes, timeout_seconds, status, create_by, create_time, remark)
SELECT 'VEHICLE_ALARM', '违法数量', 'HTTP_COUNT', 'Y', 7, 60000, '条', 'MIN', 480, 10, '0', 'admin', NOW(), 'TIM巡检内置项'
WHERE NOT EXISTS (SELECT 1 FROM sup_tim_inspection_item_config WHERE item_code = 'VEHICLE_ALARM');

INSERT INTO sys_menu(menu_id, menu_name, parent_id, order_num, path, component, `query`, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
VALUES
(2206, 'TIM系统巡检', 2200, 6, 'timInspection', 'support/timInspection/index', '', 'SupportTimInspection', 1, 0, 'C', '0', '0', 'support:timInspection:list', 'monitor', 'admin', NOW(), '', NULL, 'TIM系统可配置巡检')
ON DUPLICATE KEY UPDATE menu_name=VALUES(menu_name), path=VALUES(path), component=VALUES(component), perms=VALUES(perms), route_name=VALUES(route_name);

INSERT INTO sys_menu(menu_id, menu_name, parent_id, order_num, path, component, `query`, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
VALUES
(2261, 'TIM巡检查询', 2206, 1, '#', '', '', '', 1, 0, 'F', '0', '0', 'support:timInspection:query', '#', 'admin', NOW(), '', NULL, ''),
(2262, 'TIM巡检执行', 2206, 2, '#', '', '', '', 1, 0, 'F', '0', '0', 'support:timInspection:run', '#', 'admin', NOW(), '', NULL, ''),
(2263, 'TIM巡检导出', 2206, 3, '#', '', '', '', 1, 0, 'F', '0', '0', 'support:timInspection:export', '#', 'admin', NOW(), '', NULL, ''),
(2264, 'TIM巡检配置', 2206, 4, '#', '', '', '', 1, 0, 'F', '0', '0', 'support:timInspection:config', '#', 'admin', NOW(), '', NULL, '')
ON DUPLICATE KEY UPDATE perms=VALUES(perms), menu_name=VALUES(menu_name);

INSERT INTO sys_role_menu(role_id, menu_id)
SELECT r.role_id, m.menu_id
FROM sys_role r
INNER JOIN sys_menu m ON m.menu_id IN (2206, 2261, 2262, 2263, 2264)
WHERE r.role_key = 'datafusion'
  AND NOT EXISTS (
    SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = r.role_id AND rm.menu_id = m.menu_id
  );
