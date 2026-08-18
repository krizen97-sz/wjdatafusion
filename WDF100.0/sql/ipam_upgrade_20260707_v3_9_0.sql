-- v3.9.0 独立 IP 分配管控应用
-- 说明：
-- 1. 本脚本只创建 ipam_ 前缀业务表与 IP分配管控 顶级菜单，不修改 sup_* 现场融合业务表。
-- 2. IPAM 应用独立使用 ipam:* 权限字符，可在系统管理中按角色单独授权。
-- 3. 第一版按大网段 -> /24 子网段 -> 地址台账管理，不做自动探测和厂商接口调用。

CREATE TABLE IF NOT EXISTS ipam_network (
  network_id      BIGINT(20)    NOT NULL AUTO_INCREMENT COMMENT '大网段ID',
  network_name    VARCHAR(100)  NOT NULL COMMENT '网段名称',
  cidr_block      VARCHAR(64)   NOT NULL COMMENT 'CIDR网段',
  start_ip        VARCHAR(64)   NOT NULL COMMENT '起始IP',
  end_ip          VARCHAR(64)   NOT NULL COMMENT '结束IP',
  prefix_length   INT           NOT NULL COMMENT '掩码长度',
  scenario_type   VARCHAR(20)   NOT NULL DEFAULT 'SOCIAL' COMMENT '使用场景（SOCIAL社会面场景 INTERNAL公安内网场景）',
  status          CHAR(1)       DEFAULT '0' COMMENT '状态（0启用 1停用）',
  create_by       VARCHAR(64)   DEFAULT '' COMMENT '创建者',
  create_time     DATETIME      DEFAULT NULL COMMENT '创建时间',
  update_by       VARCHAR(64)   DEFAULT '' COMMENT '更新者',
  update_time     DATETIME      DEFAULT NULL COMMENT '更新时间',
  remark          VARCHAR(500)  DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (network_id),
  UNIQUE KEY uk_ipam_network_cidr (cidr_block)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='IPAM大网段';

CREATE TABLE IF NOT EXISTS ipam_segment (
  segment_id      BIGINT(20)    NOT NULL AUTO_INCREMENT COMMENT '子网段ID',
  network_id      BIGINT(20)    NOT NULL COMMENT '大网段ID',
  segment_name    VARCHAR(120)  NOT NULL COMMENT '子网段名称',
  cidr_block      VARCHAR(64)   NOT NULL COMMENT 'CIDR子网段',
  start_ip        VARCHAR(64)   NOT NULL COMMENT '起始IP',
  end_ip          VARCHAR(64)   NOT NULL COMMENT '结束IP',
  gateway_ip      VARCHAR(64)   DEFAULT NULL COMMENT '网关IP',
  prefix_length   INT           NOT NULL COMMENT '掩码长度',
  total_count     BIGINT(20)    NOT NULL DEFAULT 0 COMMENT '地址总数',
  status          CHAR(1)       DEFAULT '0' COMMENT '状态（0启用 1停用）',
  create_by       VARCHAR(64)   DEFAULT '' COMMENT '创建者',
  create_time     DATETIME      DEFAULT NULL COMMENT '创建时间',
  update_by       VARCHAR(64)   DEFAULT '' COMMENT '更新者',
  update_time     DATETIME      DEFAULT NULL COMMENT '更新时间',
  remark          VARCHAR(500)  DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (segment_id),
  UNIQUE KEY uk_ipam_segment_cidr (cidr_block),
  KEY idx_ipam_segment_network (network_id),
  KEY idx_ipam_segment_start_ip (start_ip)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='IPAM可分配子网段';

CREATE TABLE IF NOT EXISTS ipam_address (
  address_id      BIGINT(20)    NOT NULL AUTO_INCREMENT COMMENT '地址ID',
  network_id      BIGINT(20)    NOT NULL COMMENT '大网段ID',
  segment_id      BIGINT(20)    NOT NULL COMMENT '子网段ID',
  ip_address      VARCHAR(64)   NOT NULL COMMENT 'IP地址',
  ip_value        BIGINT(20)    NOT NULL COMMENT 'IP数值',
  status          VARCHAR(20)   NOT NULL DEFAULT 'FREE' COMMENT '状态（FREE空闲 RESERVED保留 ALLOCATED已占用 DISABLED禁用）',
  community_name  VARCHAR(120)  DEFAULT NULL COMMENT '小区名称',
  target_type     VARCHAR(40)   DEFAULT NULL COMMENT '设备类别',
  target_name     VARCHAR(160)  DEFAULT NULL COMMENT '对象名称',
  manufacturer    VARCHAR(60)   DEFAULT NULL COMMENT '厂商',
  internal_ip_address VARCHAR(128) DEFAULT NULL COMMENT '小区内网IP',
  access_unit     VARCHAR(80)   DEFAULT NULL COMMENT '接入单位',
  purpose         VARCHAR(255)  DEFAULT NULL COMMENT '用途说明',
  login_username  VARCHAR(120)  DEFAULT NULL COMMENT '登录账号',
  login_password  VARCHAR(200)  DEFAULT NULL COMMENT '登录密码',
  mapping_address VARCHAR(128)  DEFAULT NULL COMMENT '映射地址',
  mapping_port    VARCHAR(80)   DEFAULT NULL COMMENT '映射端口',
  mapping_description VARCHAR(500) DEFAULT NULL COMMENT '映射说明',
  owner_name      VARCHAR(80)   DEFAULT NULL COMMENT '责任人',
  owner_phone     VARCHAR(40)   DEFAULT NULL COMMENT '联系电话',
  issue_batch     VARCHAR(80)   DEFAULT NULL COMMENT '历史下发批次（兼容字段）',
  allocated_time  DATETIME      DEFAULT NULL COMMENT '分配时间',
  issued_time     DATETIME      DEFAULT NULL COMMENT '历史下发时间（兼容字段）',
  released_time   DATETIME      DEFAULT NULL COMMENT '释放时间',
  create_by       VARCHAR(64)   DEFAULT '' COMMENT '创建者',
  create_time     DATETIME      DEFAULT NULL COMMENT '创建时间',
  update_by       VARCHAR(64)   DEFAULT '' COMMENT '更新者',
  update_time     DATETIME      DEFAULT NULL COMMENT '更新时间',
  remark          VARCHAR(500)  DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (address_id),
  UNIQUE KEY uk_ipam_address_ip (ip_address),
  KEY idx_ipam_address_network (network_id),
  KEY idx_ipam_address_segment (segment_id),
  KEY idx_ipam_address_status (status),
  KEY idx_ipam_address_value (ip_value),
  KEY idx_ipam_address_ledger (network_id, segment_id, status, community_name, target_type, manufacturer),
  KEY idx_ipam_address_internal_ip (internal_ip_address)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='IPAM地址台账';

CREATE TABLE IF NOT EXISTS ipam_operation_log (
  log_id          BIGINT(20)    NOT NULL AUTO_INCREMENT COMMENT '日志ID',
  action_type     VARCHAR(50)   NOT NULL COMMENT '操作类型',
  target_type     VARCHAR(50)   DEFAULT NULL COMMENT '目标类型',
  target_id       BIGINT(20)    DEFAULT NULL COMMENT '目标ID',
  ip_address      VARCHAR(64)   DEFAULT NULL COMMENT 'IP地址',
  summary         VARCHAR(255)  DEFAULT NULL COMMENT '操作摘要',
  detail_content  MEDIUMTEXT    DEFAULT NULL COMMENT '操作详情',
  operator_name   VARCHAR(64)   DEFAULT NULL COMMENT '操作人',
  operator_ip     VARCHAR(128)  DEFAULT NULL COMMENT '操作IP',
  create_time     DATETIME      DEFAULT NULL COMMENT '创建时间',
  PRIMARY KEY (log_id),
  KEY idx_ipam_log_target (target_type, target_id),
  KEY idx_ipam_log_ip (ip_address),
  KEY idx_ipam_log_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='IPAM应用操作记录';

INSERT INTO sys_menu(menu_id, menu_name, parent_id, order_num, path, component, `query`, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
VALUES
(2400, 'IP分配管控', 0, 8, 'ipam', 'ipam/index', '', 'Ipam', 1, 0, 'C', '0', '0', 'ipam:network:list', 'tree-table', 'admin', NOW(), '', NULL, '独立IP分配管控应用'),
(2401, '网段查询', 2400, 1, '#', '', '', '', 1, 0, 'F', '0', '0', 'ipam:network:list', '#', 'admin', NOW(), '', NULL, ''),
(2402, '网段新增', 2400, 2, '#', '', '', '', 1, 0, 'F', '0', '0', 'ipam:network:add', '#', 'admin', NOW(), '', NULL, ''),
(2403, '网段修改', 2400, 3, '#', '', '', '', 1, 0, 'F', '0', '0', 'ipam:network:edit', '#', 'admin', NOW(), '', NULL, ''),
(2404, '网段删除', 2400, 4, '#', '', '', '', 1, 0, 'F', '0', '0', 'ipam:network:remove', '#', 'admin', NOW(), '', NULL, ''),
(2405, '地址查询', 2400, 5, '#', '', '', '', 1, 0, 'F', '0', '0', 'ipam:address:list', '#', 'admin', NOW(), '', NULL, ''),
(2406, '地址分配', 2400, 6, '#', '', '', '', 1, 0, 'F', '0', '0', 'ipam:address:allocate', '#', 'admin', NOW(), '', NULL, ''),
(2407, '地址修改', 2400, 7, '#', '', '', '', 1, 0, 'F', '0', '0', 'ipam:address:edit', '#', 'admin', NOW(), '', NULL, ''),
(2408, '地址释放', 2400, 8, '#', '', '', '', 1, 0, 'F', '0', '0', 'ipam:address:release', '#', 'admin', NOW(), '', NULL, ''),
(2410, '地址导出', 2400, 9, '#', '', '', '', 1, 0, 'F', '0', '0', 'ipam:address:export', '#', 'admin', NOW(), '', NULL, '')
ON DUPLICATE KEY UPDATE
  menu_name=VALUES(menu_name),
  parent_id=VALUES(parent_id),
  order_num=VALUES(order_num),
  path=VALUES(path),
  component=VALUES(component),
  `query`=VALUES(`query`),
  route_name=VALUES(route_name),
  menu_type=VALUES(menu_type),
  visible=VALUES(visible),
  status=VALUES(status),
  perms=VALUES(perms),
  icon=VALUES(icon),
  remark=VALUES(remark);
