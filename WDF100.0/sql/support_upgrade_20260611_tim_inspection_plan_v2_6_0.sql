-- v2.6.0 TIM巡检计划配置
-- 说明：新增巡检计划、计划巡检项、计划目标关系表；巡检记录补充计划来源和报告样式字段；补充计划配置权限。

SET @schema_name := DATABASE();

SET @sql := (
  SELECT IF(COUNT(1) = 0,
    'ALTER TABLE sup_tim_inspection ADD COLUMN plan_id BIGINT DEFAULT NULL COMMENT ''巡检计划ID'' AFTER executor_name',
    'SELECT 1')
  FROM information_schema.columns
  WHERE table_schema = @schema_name AND table_name = 'sup_tim_inspection' AND column_name = 'plan_id'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := (
  SELECT IF(COUNT(1) = 0,
    'ALTER TABLE sup_tim_inspection ADD COLUMN plan_name VARCHAR(120) DEFAULT NULL COMMENT ''巡检计划名称'' AFTER plan_id',
    'SELECT 1')
  FROM information_schema.columns
  WHERE table_schema = @schema_name AND table_name = 'sup_tim_inspection' AND column_name = 'plan_name'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := (
  SELECT IF(COUNT(1) = 0,
    'ALTER TABLE sup_tim_inspection ADD COLUMN report_style VARCHAR(32) DEFAULT ''STANDARD'' COMMENT ''巡检报告样式'' AFTER plan_name',
    'SELECT 1')
  FROM information_schema.columns
  WHERE table_schema = @schema_name AND table_name = 'sup_tim_inspection' AND column_name = 'report_style'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := (
  SELECT IF(COUNT(1) = 0,
    'ALTER TABLE sup_tim_inspection ADD KEY idx_sup_tim_inspection_plan (plan_id, inspection_time)',
    'SELECT 1')
  FROM information_schema.statistics
  WHERE table_schema = @schema_name AND table_name = 'sup_tim_inspection' AND index_name = 'idx_sup_tim_inspection_plan'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS sup_tim_inspection_plan (
  plan_id          BIGINT        NOT NULL AUTO_INCREMENT COMMENT '计划ID',
  plan_name        VARCHAR(120)  NOT NULL COMMENT '计划名称',
  cron_expression  VARCHAR(255)  NOT NULL COMMENT 'Cron表达式',
  job_id           BIGINT        DEFAULT NULL COMMENT '若依定时任务ID',
  report_style     VARCHAR(32)   DEFAULT 'STANDARD' COMMENT '巡检报告样式（STANDARD标准 SIMPLE简要 DETAIL明细 EXCEPTION_ONLY异常）',
  status           CHAR(1)       DEFAULT '0' COMMENT '状态（0正常 1暂停）',
  create_by        VARCHAR(64)   DEFAULT '' COMMENT '创建者',
  create_time      DATETIME      DEFAULT NULL COMMENT '创建时间',
  update_by        VARCHAR(64)   DEFAULT '' COMMENT '更新者',
  update_time      DATETIME      DEFAULT NULL COMMENT '更新时间',
  remark           VARCHAR(500)  DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (plan_id),
  KEY idx_sup_tim_plan_status (status),
  KEY idx_sup_tim_plan_job (job_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='TIM巡检计划';

CREATE TABLE IF NOT EXISTS sup_tim_inspection_plan_item (
  plan_item_id        BIGINT        NOT NULL AUTO_INCREMENT COMMENT '计划巡检项ID',
  plan_id             BIGINT        NOT NULL COMMENT '计划ID',
  item_code           VARCHAR(64)   NOT NULL COMMENT '巡检项编码',
  item_name           VARCHAR(100)  NOT NULL COMMENT '巡检项名称',
  item_type           VARCHAR(32)   NOT NULL COMMENT '巡检项类型',
  enabled_flag        CHAR(1)       DEFAULT 'Y' COMMENT '是否启用（Y是 N否）',
  sort_order          INT           DEFAULT 0 COMMENT '排序',
  threshold_value     DECIMAL(18,2) DEFAULT NULL COMMENT '告警阈值',
  threshold_unit      VARCHAR(32)   DEFAULT NULL COMMENT '阈值单位',
  compare_rule        VARCHAR(16)   DEFAULT 'MAX' COMMENT '比较规则（MIN最低阈值 MAX最高阈值）',
  time_window_minutes INT           DEFAULT 0 COMMENT '统计时间窗口分钟数',
  timeout_seconds     INT           DEFAULT 10 COMMENT '超时时间秒',
  create_by           VARCHAR(64)   DEFAULT '' COMMENT '创建者',
  create_time         DATETIME      DEFAULT NULL COMMENT '创建时间',
  update_by           VARCHAR(64)   DEFAULT '' COMMENT '更新者',
  update_time         DATETIME      DEFAULT NULL COMMENT '更新时间',
  remark              VARCHAR(500)  DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (plan_item_id),
  UNIQUE KEY uk_sup_tim_plan_item (plan_id, item_code),
  KEY idx_sup_tim_plan_item_sort (plan_id, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='TIM巡检计划巡检项';

CREATE TABLE IF NOT EXISTS sup_tim_inspection_plan_target (
  plan_target_id  BIGINT       NOT NULL AUTO_INCREMENT COMMENT '计划目标关系ID',
  plan_id         BIGINT       NOT NULL COMMENT '计划ID',
  item_code       VARCHAR(64)  NOT NULL COMMENT '巡检项编码',
  target_id       BIGINT       NOT NULL COMMENT '巡检目标ID',
  create_by       VARCHAR(64)  DEFAULT '' COMMENT '创建者',
  create_time     DATETIME     DEFAULT NULL COMMENT '创建时间',
  update_by       VARCHAR(64)  DEFAULT '' COMMENT '更新者',
  update_time     DATETIME     DEFAULT NULL COMMENT '更新时间',
  remark          VARCHAR(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (plan_target_id),
  UNIQUE KEY uk_sup_tim_plan_target (plan_id, item_code, target_id),
  KEY idx_sup_tim_plan_target_item (plan_id, item_code),
  KEY idx_sup_tim_plan_target_target (target_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='TIM巡检计划目标关系';

INSERT INTO sys_menu(menu_id, menu_name, parent_id, order_num, path, component, `query`, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
VALUES
(2265, 'TIM巡检计划', 2206, 5, '#', '', '', '', 1, 0, 'F', '0', '0', 'support:timInspection:plan', '#', 'admin', NOW(), '', NULL, '')
ON DUPLICATE KEY UPDATE perms=VALUES(perms), menu_name=VALUES(menu_name), order_num=VALUES(order_num);

INSERT INTO sys_role_menu(role_id, menu_id)
SELECT r.role_id, m.menu_id
FROM sys_role r
INNER JOIN sys_menu m ON m.menu_id = 2265
WHERE r.role_key = 'datafusion'
  AND NOT EXISTS (
    SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = r.role_id AND rm.menu_id = m.menu_id
  );
