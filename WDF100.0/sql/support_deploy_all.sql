-- 现场融合管理整合部署脚本
-- 生成来源：support_v1.sql + support_upgrade_*.sql
-- 说明：可用于新库初始化，也可用于已有库补齐升级项。建议部署前先备份数据库。

SET NAMES utf8mb4;

-- ============================================================================
-- support_v1.sql
-- ============================================================================
-- 现场信息融合平台 v1

CREATE TABLE IF NOT EXISTS sup_site (
  site_id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '现场ID',
  site_name          VARCHAR(100) NOT NULL COMMENT '现场名称',
  site_code          VARCHAR(64)  DEFAULT NULL COMMENT '现场编码',
  province_code      VARCHAR(32)  DEFAULT NULL COMMENT '省级行政区编码',
  province_name      VARCHAR(64)  DEFAULT NULL COMMENT '省级行政区名称',
  city_code          VARCHAR(32)  DEFAULT NULL COMMENT '市级行政区编码',
  city_name          VARCHAR(64)  DEFAULT NULL COMMENT '市级行政区名称',
  district_code      VARCHAR(32)  DEFAULT NULL COMMENT '区县行政区编码',
  district_name      VARCHAR(64)  DEFAULT NULL COMMENT '区县行政区名称',
  location           VARCHAR(255) DEFAULT NULL COMMENT '现场地址',
  description        VARCHAR(500) DEFAULT NULL COMMENT '现场描述',
  status             CHAR(1)      DEFAULT '0' COMMENT '状态（0正常 1停用）',
  create_by          VARCHAR(64)  DEFAULT '' COMMENT '创建者',
  create_time        DATETIME     DEFAULT NULL COMMENT '创建时间',
  update_by          VARCHAR(64)  DEFAULT '' COMMENT '更新者',
  update_time        DATETIME     DEFAULT NULL COMMENT '更新时间',
  remark             VARCHAR(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (site_id),
  UNIQUE KEY uk_sup_site_code (site_code),
  KEY idx_sup_site_create_by (create_by, site_id),
  KEY idx_sup_site_update_by (update_by, site_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='现场信息';

CREATE TABLE IF NOT EXISTS sup_platform (
  platform_id        BIGINT       NOT NULL AUTO_INCREMENT COMMENT '平台ID',
  site_id            BIGINT       NOT NULL COMMENT '现场ID',
  platform_name      VARCHAR(120) NOT NULL COMMENT '平台名称',
  platform_level     VARCHAR(16)  NOT NULL COMMENT '平台级别（MAIN/SUB）',
  network_env        VARCHAR(100) DEFAULT NULL COMMENT '网络环境',
  parent_platform_id BIGINT       DEFAULT NULL COMMENT '父平台ID',
  status             CHAR(1)      DEFAULT '0' COMMENT '状态（0正常 1停用）',
  create_by          VARCHAR(64)  DEFAULT '' COMMENT '创建者',
  create_time        DATETIME     DEFAULT NULL COMMENT '创建时间',
  update_by          VARCHAR(64)  DEFAULT '' COMMENT '更新者',
  update_time        DATETIME     DEFAULT NULL COMMENT '更新时间',
  remark             VARCHAR(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (platform_id),
  KEY idx_sup_platform_site (site_id),
  KEY idx_sup_platform_parent (parent_platform_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='平台信息（主/子）';

CREATE TABLE IF NOT EXISTS sup_server (
  server_id           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '服务器ID',
  site_id             BIGINT       NOT NULL COMMENT '现场ID',
  server_name         VARCHAR(120) NOT NULL COMMENT '服务器名称',
  server_address      VARCHAR(255) NOT NULL COMMENT '服务器地址',
  ssh_port            INT          DEFAULT 22 COMMENT 'SSH端口',
  os_type             VARCHAR(64)  DEFAULT NULL COMMENT '操作系统类型',
  equipment_room      VARCHAR(100) DEFAULT NULL COMMENT '所属机房',
  cabinet_no          VARCHAR(80)  DEFAULT NULL COMMENT '机柜编号',
  rack_u_start        INT          DEFAULT NULL COMMENT '起始U位',
  rack_u_end          INT          DEFAULT NULL COMMENT '结束U位',
  os_username         VARCHAR(128) DEFAULT NULL COMMENT '系统登录账号',
  os_password_cipher  VARCHAR(1024) DEFAULT NULL COMMENT '系统登录密码密文',
  status              CHAR(1)      DEFAULT '0' COMMENT '状态（0正常 1停用）',
  create_by           VARCHAR(64)  DEFAULT '' COMMENT '创建者',
  create_time         DATETIME     DEFAULT NULL COMMENT '创建时间',
  update_by           VARCHAR(64)  DEFAULT '' COMMENT '更新者',
  update_time         DATETIME     DEFAULT NULL COMMENT '更新时间',
  remark              VARCHAR(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (server_id),
  KEY idx_sup_server_site (site_id),
  KEY idx_sup_server_site_address (site_id, server_address)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='服务器信息';

CREATE TABLE IF NOT EXISTS sup_server_credential (
  credential_id     BIGINT        NOT NULL AUTO_INCREMENT COMMENT '凭据ID',
  server_id         BIGINT        NOT NULL COMMENT '服务器ID',
  credential_name   VARCHAR(120)  NOT NULL COMMENT '凭据名称',
  username          VARCHAR(128)  NOT NULL COMMENT '登录账号',
  password_cipher   VARCHAR(1024) DEFAULT NULL COMMENT '登录密码密文',
  purpose           VARCHAR(120)  DEFAULT NULL COMMENT '用途',
  is_default        CHAR(1)       DEFAULT '0' COMMENT '是否默认（0否 1是）',
  status            CHAR(1)       DEFAULT '0' COMMENT '状态（0正常 1停用）',
  create_by         VARCHAR(64)   DEFAULT '' COMMENT '创建者',
  create_time       DATETIME      DEFAULT NULL COMMENT '创建时间',
  update_by         VARCHAR(64)   DEFAULT '' COMMENT '更新者',
  update_time       DATETIME      DEFAULT NULL COMMENT '更新时间',
  remark            VARCHAR(500)  DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (credential_id),
  KEY idx_sup_server_credential_server (server_id),
  KEY idx_sup_server_credential_status (server_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='服务器多凭据档案';

CREATE TABLE IF NOT EXISTS sup_subplatform_endpoint (
  endpoint_id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '页面ID',
  sub_platform_id        BIGINT       NOT NULL COMMENT '子平台ID',
  endpoint_name          VARCHAR(120) DEFAULT NULL COMMENT '页面名称',
  access_url             VARCHAR(500) NOT NULL COMMENT '访问URL',
  login_username         VARCHAR(128) DEFAULT NULL COMMENT '登录账号',
  login_password_cipher  VARCHAR(1024) DEFAULT NULL COMMENT '登录密码密文',
  create_by              VARCHAR(64)  DEFAULT '' COMMENT '创建者',
  create_time            DATETIME     DEFAULT NULL COMMENT '创建时间',
  update_by              VARCHAR(64)  DEFAULT '' COMMENT '更新者',
  update_time            DATETIME     DEFAULT NULL COMMENT '更新时间',
  remark                 VARCHAR(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (endpoint_id),
  KEY idx_sup_endpoint_sub_platform (sub_platform_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='子平台页面信息';

CREATE TABLE IF NOT EXISTS sup_org (
  org_id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '组织ID',
  org_type            VARCHAR(16)  NOT NULL COMMENT '组织类型（CUSTOMER/USER/THIRD_VENDOR）',
  org_name            VARCHAR(160) NOT NULL COMMENT '组织名称',
  short_name          VARCHAR(80)  DEFAULT NULL COMMENT '简称',
  status              CHAR(1)      DEFAULT '0' COMMENT '状态（0正常 1停用）',
  create_by           VARCHAR(64)  DEFAULT '' COMMENT '创建者',
  create_time         DATETIME     DEFAULT NULL COMMENT '创建时间',
  update_by           VARCHAR(64)  DEFAULT '' COMMENT '更新者',
  update_time         DATETIME     DEFAULT NULL COMMENT '更新时间',
  remark              VARCHAR(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (org_id),
  KEY idx_sup_org_type (org_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='客户组织';

CREATE TABLE IF NOT EXISTS sup_contact (
  contact_id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '联系人ID',
  org_id              BIGINT       NOT NULL COMMENT '组织ID',
  contact_name        VARCHAR(80)  NOT NULL COMMENT '联系人姓名',
  role_type           VARCHAR(32)  DEFAULT NULL COMMENT '角色（support_contact_role字典）',
  phone               VARCHAR(30)  DEFAULT NULL COMMENT '手机号',
  email               VARCHAR(120) DEFAULT NULL COMMENT '邮箱',
  wechat              VARCHAR(80)  DEFAULT NULL COMMENT '微信',
  is_primary          CHAR(1)      DEFAULT '0' COMMENT '是否主联系人（0否 1是）',
  create_by           VARCHAR(64)  DEFAULT '' COMMENT '创建者',
  create_time         DATETIME     DEFAULT NULL COMMENT '创建时间',
  update_by           VARCHAR(64)  DEFAULT '' COMMENT '更新者',
  update_time         DATETIME     DEFAULT NULL COMMENT '更新时间',
  remark              VARCHAR(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (contact_id),
  KEY idx_sup_contact_org (org_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='联系人';

CREATE TABLE IF NOT EXISTS sup_platform_server_rel (
  rel_id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '关系ID',
  platform_id         BIGINT       NOT NULL COMMENT '平台ID',
  server_id           BIGINT       NOT NULL COMMENT '服务器ID',
  create_by           VARCHAR(64)  DEFAULT '' COMMENT '创建者',
  create_time         DATETIME     DEFAULT NULL COMMENT '创建时间',
  PRIMARY KEY (rel_id),
  UNIQUE KEY uk_sup_platform_server (platform_id, server_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='平台-服务器关系';

CREATE TABLE IF NOT EXISTS sup_hardware_asset (
  asset_id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '硬件资产ID',
  site_id             BIGINT       NOT NULL COMMENT '现场ID',
  asset_name          VARCHAR(120) NOT NULL COMMENT '资产名称',
  asset_type          VARCHAR(32)  NOT NULL COMMENT '资产类型（DECODER/TERMINAL/SWITCH/GATEWAY）',
  network_env         VARCHAR(100) NOT NULL COMMENT '网络环境',
  ip_address          VARCHAR(255) NOT NULL COMMENT 'IP地址',
  manage_ip           VARCHAR(255) DEFAULT NULL COMMENT '管理地址',
  mac_address         VARCHAR(64)  DEFAULT NULL COMMENT 'MAC地址',
  manufacturer        VARCHAR(100) DEFAULT NULL COMMENT '厂商',
  asset_model         VARCHAR(120) DEFAULT NULL COMMENT '型号',
  serial_no           VARCHAR(120) DEFAULT NULL COMMENT '序列号',
  install_location    VARCHAR(200) DEFAULT NULL COMMENT '安装位置',
  equipment_room      VARCHAR(100) DEFAULT NULL COMMENT '所属机房',
  cabinet_no          VARCHAR(80)  DEFAULT NULL COMMENT '机柜编号',
  rack_u_start        INT          DEFAULT NULL COMMENT '起始U位',
  rack_u_end          INT          DEFAULT NULL COMMENT '结束U位',
  owner_org           VARCHAR(160) DEFAULT NULL COMMENT '归属组织',
  owner_contact       VARCHAR(80)  DEFAULT NULL COMMENT '责任人',
  login_username      VARCHAR(128) DEFAULT NULL COMMENT '设备登录账号',
  login_password_cipher VARCHAR(1024) DEFAULT NULL COMMENT '设备登录密码密文',
  channel_count       INT          DEFAULT NULL COMMENT '解码器通道数',
  output_type         VARCHAR(80)  DEFAULT NULL COMMENT '解码器输出类型',
  terminal_type       VARCHAR(80)  DEFAULT NULL COMMENT '终端类型',
  department          VARCHAR(120) DEFAULT NULL COMMENT '使用部门',
  use_location        VARCHAR(200) DEFAULT NULL COMMENT '使用位置',
  switch_level        VARCHAR(80)  DEFAULT NULL COMMENT '交换机层级',
  port_count          INT          DEFAULT NULL COMMENT '端口数',
  uplink_device       VARCHAR(160) DEFAULT NULL COMMENT '上联设备',
  vlan_info           VARCHAR(500) DEFAULT NULL COMMENT 'VLAN说明',
  gateway_mode        VARCHAR(80)  DEFAULT NULL COMMENT '网闸模式',
  gateway_direction   VARCHAR(120) DEFAULT NULL COMMENT '网闸数据流向',
  gateway_bandwidth   VARCHAR(80)  DEFAULT NULL COMMENT '网闸带宽',
  security_zone       VARCHAR(200) DEFAULT NULL COMMENT '安全域说明',
  status              CHAR(1)      DEFAULT '0' COMMENT '状态（0正常 1停用）',
  create_by           VARCHAR(64)  DEFAULT '' COMMENT '创建者',
  create_time         DATETIME     DEFAULT NULL COMMENT '创建时间',
  update_by           VARCHAR(64)  DEFAULT '' COMMENT '更新者',
  update_time         DATETIME     DEFAULT NULL COMMENT '更新时间',
  remark              VARCHAR(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (asset_id),
  KEY idx_sup_hardware_site (site_id),
  KEY idx_sup_hardware_site_type (site_id, asset_type),
  KEY idx_sup_hardware_site_network (site_id, network_env),
  KEY idx_sup_hardware_site_ip (site_id, ip_address)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='现场硬件资产';

CREATE TABLE IF NOT EXISTS sup_platform_asset_rel (
  rel_id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '关系ID',
  platform_id         BIGINT       NOT NULL COMMENT '平台ID',
  asset_id            BIGINT       NOT NULL COMMENT '硬件资产ID',
  create_by           VARCHAR(64)  DEFAULT '' COMMENT '创建者',
  create_time         DATETIME     DEFAULT NULL COMMENT '创建时间',
  PRIMARY KEY (rel_id),
  UNIQUE KEY uk_sup_platform_asset (platform_id, asset_id),
  KEY idx_sup_platform_asset_asset (asset_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='平台-硬件资产关系';

CREATE TABLE IF NOT EXISTS sup_equipment_room (
  room_id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '机房ID',
  site_id            BIGINT       NOT NULL COMMENT '现场ID',
  room_name          VARCHAR(120) NOT NULL COMMENT '机房名称',
  room_code          VARCHAR(80)  DEFAULT NULL COMMENT '机房编码',
  status             CHAR(1)      DEFAULT '0' COMMENT '状态（0正常 1停用）',
  create_by          VARCHAR(64)  DEFAULT '' COMMENT '创建者',
  create_time        DATETIME     DEFAULT NULL COMMENT '创建时间',
  update_by          VARCHAR(64)  DEFAULT '' COMMENT '更新者',
  update_time        DATETIME     DEFAULT NULL COMMENT '更新时间',
  remark             VARCHAR(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (room_id),
  KEY idx_sup_equipment_room_site (site_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='现场设备机房';

CREATE TABLE IF NOT EXISTS sup_equipment_cabinet (
  cabinet_id         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '机柜ID',
  room_id            BIGINT       NOT NULL COMMENT '机房ID',
  site_id            BIGINT       NOT NULL COMMENT '现场ID',
  cabinet_no         VARCHAR(80)  NOT NULL COMMENT '机柜编号',
  u_capacity         INT          DEFAULT 45 COMMENT '机柜U数',
  status             CHAR(1)      DEFAULT '0' COMMENT '状态（0正常 1停用）',
  create_by          VARCHAR(64)  DEFAULT '' COMMENT '创建者',
  create_time        DATETIME     DEFAULT NULL COMMENT '创建时间',
  update_by          VARCHAR(64)  DEFAULT '' COMMENT '更新者',
  update_time        DATETIME     DEFAULT NULL COMMENT '更新时间',
  remark             VARCHAR(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (cabinet_id),
  UNIQUE KEY uk_sup_equipment_cabinet_room_no (room_id, cabinet_no),
  KEY idx_sup_equipment_cabinet_site (site_id),
  KEY idx_sup_equipment_cabinet_room (room_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='现场设备机柜';

CREATE TABLE IF NOT EXISTS sup_platform_contact_rel (
  rel_id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '关系ID',
  platform_id         BIGINT       NOT NULL COMMENT '主平台ID',
  contact_id          BIGINT       NOT NULL COMMENT '联系人ID',
  create_by           VARCHAR(64)  DEFAULT '' COMMENT '创建者',
  create_time         DATETIME     DEFAULT NULL COMMENT '创建时间',
  PRIMARY KEY (rel_id),
  UNIQUE KEY uk_sup_platform_contact (platform_id, contact_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='主平台-联系人关系';

CREATE TABLE IF NOT EXISTS sup_platform_org_rel (
  rel_id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '关系ID',
  platform_id         BIGINT       NOT NULL COMMENT '平台ID',
  org_id              BIGINT       NOT NULL COMMENT '组织ID',
  create_by           VARCHAR(64)  DEFAULT '' COMMENT '创建者',
  create_time         DATETIME     DEFAULT NULL COMMENT '创建时间',
  PRIMARY KEY (rel_id),
  UNIQUE KEY uk_sup_platform_org (platform_id, org_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='平台-组织关系（兼容保留）';

CREATE TABLE IF NOT EXISTS sup_change_log (
  log_id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '记录ID',
  site_id             BIGINT       DEFAULT NULL COMMENT '现场ID',
  action_type         VARCHAR(16)  NOT NULL COMMENT '操作类型（INSERT/UPDATE/DELETE）',
  target_type         VARCHAR(32)  NOT NULL COMMENT '对象类型',
  target_id           BIGINT       DEFAULT NULL COMMENT '对象ID',
  target_name         VARCHAR(200) DEFAULT NULL COMMENT '对象名称',
  summary             VARCHAR(500) DEFAULT NULL COMMENT '操作摘要',
  detail_content      TEXT         DEFAULT NULL COMMENT '操作详情',
  operator_name       VARCHAR(64)  DEFAULT NULL COMMENT '操作用户',
  operator_ip         VARCHAR(64)  DEFAULT NULL COMMENT '操作IP',
  create_time         DATETIME     DEFAULT NULL COMMENT '操作时间',
  PRIMARY KEY (log_id),
  KEY idx_sup_change_log_site_time (site_id, create_time),
  KEY idx_sup_change_log_target (target_type, target_id),
  KEY idx_sup_change_log_operator_action_site (operator_name, action_type, site_id, log_id),
  KEY idx_sup_change_log_action_time (action_type, create_time, log_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='现场融合用户修改记录';

CREATE TABLE IF NOT EXISTS sup_site_message (
  message_id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '留言ID',
  site_id             BIGINT       NOT NULL COMMENT '现场ID',
  message_content     VARCHAR(300) NOT NULL COMMENT '留言内容',
  publisher_id        BIGINT       DEFAULT NULL COMMENT '发布用户ID',
  publisher_name      VARCHAR(64)  DEFAULT NULL COMMENT '发布用户昵称',
  status              CHAR(1)      DEFAULT '0' COMMENT '状态（0正常 1停用）',
  create_by           VARCHAR(64)  DEFAULT '' COMMENT '创建者',
  create_time         DATETIME     DEFAULT NULL COMMENT '创建时间',
  update_by           VARCHAR(64)  DEFAULT '' COMMENT '更新者',
  update_time         DATETIME     DEFAULT NULL COMMENT '更新时间',
  remark              VARCHAR(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (message_id),
  KEY idx_sup_site_message_site_time (site_id, create_time),
  KEY idx_sup_site_message_site_status_id (site_id, status, message_id),
  KEY idx_sup_site_message_publisher (publisher_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='现场留言板';

-- 网络环境字典（内置项由后端保护，不允许修改和删除；可继续新增自定义项）
INSERT INTO sys_dict_type(dict_name, dict_type, status, create_by, create_time, remark)
VALUES ('网络环境', 'support_network_env', '0', 'admin', NOW(), '现场融合主平台网络环境')
ON DUPLICATE KEY UPDATE dict_name=VALUES(dict_name), status=VALUES(status), remark=VALUES(remark);

INSERT INTO sys_dict_data(dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status, create_by, create_time, remark)
SELECT 1, '公安网', '公安网', 'support_network_env', 'network-env-tag--police', 'primary', 'Y', '0', 'admin', NOW(), '内置网络环境'
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data WHERE dict_type = 'support_network_env' AND dict_value = '公安网');
INSERT INTO sys_dict_data(dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status, create_by, create_time, remark)
SELECT 2, '图像网', '图像网', 'support_network_env', 'network-env-tag--image', 'success', 'N', '0', 'admin', NOW(), '内置网络环境'
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data WHERE dict_type = 'support_network_env' AND dict_value = '图像网');
INSERT INTO sys_dict_data(dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status, create_by, create_time, remark)
SELECT 3, '政务网', '政务网', 'support_network_env', 'network-env-tag--government', 'warning', 'N', '0', 'admin', NOW(), '内置网络环境'
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data WHERE dict_type = 'support_network_env' AND dict_value = '政务网');
INSERT INTO sys_dict_data(dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status, create_by, create_time, remark)
SELECT 4, '二类区', '二类区', 'support_network_env', 'network-env-tag--secondary', 'info', 'N', '0', 'admin', NOW(), '内置网络环境'
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data WHERE dict_type = 'support_network_env' AND dict_value = '二类区');
INSERT INTO sys_dict_data(dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status, create_by, create_time, remark)
SELECT 5, '党政军', '党政军', 'support_network_env', 'network-env-tag--party', 'danger', 'N', '0', 'admin', NOW(), '内置网络环境'
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data WHERE dict_type = 'support_network_env' AND dict_value = '党政军');
INSERT INTO sys_dict_data(dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status, create_by, create_time, remark)
SELECT 6, '私网', '私网', 'support_network_env', 'network-env-tag--private', 'default', 'N', '0', 'admin', NOW(), '内置网络环境'
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data WHERE dict_type = 'support_network_env' AND dict_value = '私网');

-- 联系人角色字典（可在新增联系人时继续新增和配置）
INSERT INTO sys_dict_type(dict_name, dict_type, status, create_by, create_time, remark)
VALUES ('联系人角色', 'support_contact_role', '0', 'admin', NOW(), '现场融合联系人角色')
ON DUPLICATE KEY UPDATE dict_name=VALUES(dict_name), status=VALUES(status), remark=VALUES(remark);

INSERT INTO sys_dict_data(dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status, create_by, create_time, remark)
SELECT 1, '技术', 'TECH', 'support_contact_role', '', 'primary', 'Y', '0', 'admin', NOW(), '内置联系人角色'
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data WHERE dict_type = 'support_contact_role' AND dict_value = 'TECH');
INSERT INTO sys_dict_data(dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status, create_by, create_time, remark)
SELECT 2, '管理', 'MANAGER', 'support_contact_role', '', 'success', 'N', '0', 'admin', NOW(), '内置联系人角色'
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data WHERE dict_type = 'support_contact_role' AND dict_value = 'MANAGER');
INSERT INTO sys_dict_data(dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status, create_by, create_time, remark)
SELECT 3, '商务', 'BIZ', 'support_contact_role', '', 'warning', 'N', '0', 'admin', NOW(), '内置联系人角色'
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data WHERE dict_type = 'support_contact_role' AND dict_value = 'BIZ');

-- 硬件资产类型字典
INSERT INTO sys_dict_type(dict_name, dict_type, status, create_by, create_time, remark)
VALUES ('硬件资产类型', 'support_hardware_type', '0', 'admin', NOW(), '现场融合硬件资产类型')
ON DUPLICATE KEY UPDATE dict_name=VALUES(dict_name), status=VALUES(status), remark=VALUES(remark);

INSERT INTO sys_dict_data(dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status, create_by, create_time, remark)
SELECT 1, '解码器', 'DECODER', 'support_hardware_type', 'hardware-type--decoder', 'primary', 'Y', '0', 'admin', NOW(), '内置硬件类型'
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data WHERE dict_type = 'support_hardware_type' AND dict_value = 'DECODER');
INSERT INTO sys_dict_data(dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status, create_by, create_time, remark)
SELECT 2, '终端', 'TERMINAL', 'support_hardware_type', 'hardware-type--terminal', 'success', 'N', '0', 'admin', NOW(), '内置硬件类型'
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data WHERE dict_type = 'support_hardware_type' AND dict_value = 'TERMINAL');
INSERT INTO sys_dict_data(dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status, create_by, create_time, remark)
SELECT 3, '交换机', 'SWITCH', 'support_hardware_type', 'hardware-type--switch', 'warning', 'N', '0', 'admin', NOW(), '内置硬件类型'
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data WHERE dict_type = 'support_hardware_type' AND dict_value = 'SWITCH');
INSERT INTO sys_dict_data(dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status, create_by, create_time, remark)
SELECT 4, '网闸', 'GATEWAY', 'support_hardware_type', 'hardware-type--gateway', 'danger', 'N', '0', 'admin', NOW(), '内置硬件类型'
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data WHERE dict_type = 'support_hardware_type' AND dict_value = 'GATEWAY');


-- ============================================================================
-- support_upgrade_20260603_server_ssh_and_scope.sql
-- ============================================================================
-- 服务器SSH端口与子平台绑定口径升级
-- 说明：
-- 1. 为服务器增加 SSH 端口字段，默认 22。
-- 2. 增加 site_id + server_address 普通索引，配合服务层防止新增重复服务器。
-- 3. 不删除、不迁移已有主平台服务器关系；新版本服务层会阻止继续绑定到主平台。

SET @has_server_ssh_port := (
  SELECT COUNT(1)
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'sup_server'
    AND column_name = 'ssh_port'
);

SET @sql := IF(
  @has_server_ssh_port = 0,
  'ALTER TABLE sup_server ADD COLUMN ssh_port INT DEFAULT 22 COMMENT ''SSH端口'' AFTER server_address',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE sup_server
SET ssh_port = 22
WHERE ssh_port IS NULL;

SET @has_server_site_address_index := (
  SELECT COUNT(1)
  FROM information_schema.statistics
  WHERE table_schema = DATABASE()
    AND table_name = 'sup_server'
    AND index_name = 'idx_sup_server_site_address'
);

SET @sql := IF(
  @has_server_site_address_index = 0,
  'ALTER TABLE sup_server ADD INDEX idx_sup_server_site_address (site_id, server_address)',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ============================================================================
-- support_upgrade_20260629_equipment_location_unified_v3_8_1.sql
-- ============================================================================
-- 服务器位置字段与设备位置图统一

SET @has_server_equipment_room := (
  SELECT COUNT(1)
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'sup_server'
    AND column_name = 'equipment_room'
);

SET @sql := IF(
  @has_server_equipment_room = 0,
  'ALTER TABLE sup_server ADD COLUMN equipment_room VARCHAR(100) DEFAULT NULL COMMENT ''所属机房'' AFTER os_type',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_server_cabinet_no := (
  SELECT COUNT(1)
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'sup_server'
    AND column_name = 'cabinet_no'
);

SET @sql := IF(
  @has_server_cabinet_no = 0,
  'ALTER TABLE sup_server ADD COLUMN cabinet_no VARCHAR(80) DEFAULT NULL COMMENT ''机柜编号'' AFTER equipment_room',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_server_rack_u_start := (
  SELECT COUNT(1)
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'sup_server'
    AND column_name = 'rack_u_start'
);

SET @sql := IF(
  @has_server_rack_u_start = 0,
  'ALTER TABLE sup_server ADD COLUMN rack_u_start INT DEFAULT NULL COMMENT ''起始U位'' AFTER cabinet_no',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_server_rack_u_end := (
  SELECT COUNT(1)
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'sup_server'
    AND column_name = 'rack_u_end'
);

SET @sql := IF(
  @has_server_rack_u_end = 0,
  'ALTER TABLE sup_server ADD COLUMN rack_u_end INT DEFAULT NULL COMMENT ''结束U位'' AFTER rack_u_start',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 菜单目录
INSERT INTO sys_menu(menu_id, menu_name, parent_id, order_num, path, component, `query`, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
VALUES
(2200, '现场融合管理', 0, 5, 'support', NULL, '', 'Support', 1, 0, 'M', '0', '0', '', 'network', 'admin', NOW(), '', NULL, '现场信息融合平台')
ON DUPLICATE KEY UPDATE menu_name=VALUES(menu_name), path=VALUES(path), route_name=VALUES(route_name);

INSERT INTO sys_menu(menu_id, menu_name, parent_id, order_num, path, component, `query`, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
VALUES
(2201, '现场管理', 2200, 1, 'site', 'support/site/index', '', 'SupportSite', 1, 0, 'C', '0', '0', 'support:site:list', 'map-pinned', 'admin', NOW(), '', NULL, ''),
(2202, '平台管理', 2200, 2, 'platform', 'support/platform/index', '', 'SupportPlatform', 1, 0, 'C', '0', '0', 'support:platform:list', 'panels-top-left', 'admin', NOW(), '', NULL, ''),
(2203, '服务器管理', 2200, 3, 'server', 'support/server/index', '', 'SupportServer', 1, 0, 'C', '0', '0', 'support:server:list', 'server-cog', 'admin', NOW(), '', NULL, ''),
(2204, '组织与联系人', 2200, 4, 'org', 'support/org/index', '', 'SupportOrg', 1, 0, 'C', '0', '0', 'support:org:list', 'contact-round', 'admin', NOW(), '', NULL, ''),
(2207, '版本记录', 2200, 5, 'version', 'support/version/index', '{"module":"site"}', 'SupportSiteVersion', 1, 0, 'C', '0', '0', 'support:version:list', 'file-clock', 'admin', NOW(), '', NULL, '现场融合管理模块版本记录')
ON DUPLICATE KEY UPDATE menu_name=VALUES(menu_name), perms=VALUES(perms), component=VALUES(component);

INSERT INTO sys_menu(menu_id, menu_name, parent_id, order_num, path, component, `query`, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
VALUES
(2205, '版本记录', 0, 7, 'version', 'support/version/index', '', 'SupportVersion', 1, 0, 'C', '0', '0', 'support:version:list', 'file-clock', 'admin', NOW(), '', NULL, '平台功能版本记录中心')
ON DUPLICATE KEY UPDATE menu_name=VALUES(menu_name), parent_id=VALUES(parent_id), order_num=VALUES(order_num), path=VALUES(path), component=VALUES(component), route_name=VALUES(route_name), visible=VALUES(visible), status=VALUES(status), perms=VALUES(perms), icon=VALUES(icon), remark=VALUES(remark);

INSERT INTO sys_role_menu(role_id, menu_id)
SELECT r.role_id, 2205
FROM sys_role r
WHERE r.role_key = 'datafusion'
  AND NOT EXISTS (
    SELECT 1
    FROM sys_role_menu rm
    WHERE rm.role_id = r.role_id
      AND rm.menu_id = 2205
  );

INSERT INTO sys_role_menu(role_id, menu_id)
SELECT r.role_id, 2207
FROM sys_role r
WHERE r.role_key = 'datafusion'
  AND NOT EXISTS (
    SELECT 1
    FROM sys_role_menu rm
    WHERE rm.role_id = r.role_id
      AND rm.menu_id = 2207
  );

-- 凭据查看权限按钮
INSERT INTO sys_menu(menu_id, menu_name, parent_id, order_num, path, component, `query`, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
VALUES
(2290, '查看敏感凭据', 2203, 1, '#', '', '', '', 1, 0, 'F', '0', '0', 'support:credential:viewPlain', '#', 'admin', NOW(), '', NULL, '')
ON DUPLICATE KEY UPDATE perms=VALUES(perms), menu_name=VALUES(menu_name);

-- 现场融合总权限字符
INSERT INTO sys_menu(menu_id, menu_name, parent_id, order_num, path, component, `query`, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
VALUES
(2291, '现场融合全部权限', 2200, 99, '#', '', '', '', 1, 0, 'F', '0', '0', 'datafusion', '#', 'admin', NOW(), '', NULL, '')
ON DUPLICATE KEY UPDATE perms=VALUES(perms), menu_name=VALUES(menu_name);

-- 如果环境中存在 datafusion 角色，则自动绑定 datafusion 权限字符
INSERT INTO sys_role_menu(role_id, menu_id)
SELECT r.role_id, m.menu_id
FROM sys_role r
         INNER JOIN sys_menu m ON m.perms = 'datafusion'
WHERE r.role_key = 'datafusion'
  AND NOT EXISTS (
    SELECT 1
    FROM sys_role_menu rm
    WHERE rm.role_id = r.role_id
      AND rm.menu_id = m.menu_id
  );

-- 现场管理按钮权限
INSERT INTO sys_menu(menu_id, menu_name, parent_id, order_num, path, component, `query`, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
VALUES
(2211, '现场查询', 2201, 1, '#', '', '', '', 1, 0, 'F', '0', '0', 'support:site:query', '#', 'admin', NOW(), '', NULL, ''),
(2212, '现场新增', 2201, 2, '#', '', '', '', 1, 0, 'F', '0', '0', 'support:site:add', '#', 'admin', NOW(), '', NULL, ''),
(2213, '现场修改', 2201, 3, '#', '', '', '', 1, 0, 'F', '0', '0', 'support:site:edit', '#', 'admin', NOW(), '', NULL, ''),
(2214, '现场删除', 2201, 4, '#', '', '', '', 1, 0, 'F', '0', '0', 'support:site:remove', '#', 'admin', NOW(), '', NULL, ''),
(2215, '现场导出', 2201, 5, '#', '', '', '', 1, 0, 'F', '0', '0', 'support:site:export', '#', 'admin', NOW(), '', NULL, ''),
(2216, '现场导入', 2201, 6, '#', '', '', '', 1, 0, 'F', '0', '0', 'support:site:import', '#', 'admin', NOW(), '', NULL, ''),
(2292, '留言查看', 2201, 7, '#', '', '', '', 1, 0, 'F', '0', '0', 'support:message:list', '#', 'admin', NOW(), '', NULL, ''),
(2293, '留言发布', 2201, 8, '#', '', '', '', 1, 0, 'F', '0', '0', 'support:message:add', '#', 'admin', NOW(), '', NULL, ''),
(2294, '硬件资产查询', 2201, 9, '#', '', '', '', 1, 0, 'F', '0', '0', 'support:hardwareAsset:query', '#', 'admin', NOW(), '', NULL, ''),
(2295, '硬件资产新增', 2201, 10, '#', '', '', '', 1, 0, 'F', '0', '0', 'support:hardwareAsset:add', '#', 'admin', NOW(), '', NULL, ''),
(2296, '硬件资产修改', 2201, 11, '#', '', '', '', 1, 0, 'F', '0', '0', 'support:hardwareAsset:edit', '#', 'admin', NOW(), '', NULL, ''),
(2297, '硬件资产删除', 2201, 12, '#', '', '', '', 1, 0, 'F', '0', '0', 'support:hardwareAsset:remove', '#', 'admin', NOW(), '', NULL, ''),
(2298, '硬件资产导出', 2201, 13, '#', '', '', '', 1, 0, 'F', '0', '0', 'support:hardwareAsset:export', '#', 'admin', NOW(), '', NULL, ''),
(2288, '设备清单查询', 2201, 14, '#', '', '', '', 1, 0, 'F', '0', '0', 'support:equipment:query', '#', 'admin', NOW(), '', NULL, ''),
(2289, '设备清单导出', 2201, 15, '#', '', '', '', 1, 0, 'F', '0', '0', 'support:equipment:export', '#', 'admin', NOW(), '', NULL, '')
ON DUPLICATE KEY UPDATE perms=VALUES(perms), menu_name=VALUES(menu_name);

INSERT INTO sys_role_menu(role_id, menu_id)
SELECT r.role_id, m.menu_id
FROM sys_role r
INNER JOIN sys_menu m ON m.menu_id IN (2294, 2295, 2296, 2297, 2298, 2288, 2289)
WHERE r.role_key = 'datafusion'
  AND NOT EXISTS (
    SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = r.role_id AND rm.menu_id = m.menu_id
  );

-- 平台管理按钮权限（含页面管理）
INSERT INTO sys_menu(menu_id, menu_name, parent_id, order_num, path, component, `query`, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
VALUES
(2221, '平台查询', 2202, 1, '#', '', '', '', 1, 0, 'F', '0', '0', 'support:platform:query', '#', 'admin', NOW(), '', NULL, ''),
(2222, '平台新增', 2202, 2, '#', '', '', '', 1, 0, 'F', '0', '0', 'support:platform:add', '#', 'admin', NOW(), '', NULL, ''),
(2223, '平台修改', 2202, 3, '#', '', '', '', 1, 0, 'F', '0', '0', 'support:platform:edit', '#', 'admin', NOW(), '', NULL, ''),
(2224, '平台删除', 2202, 4, '#', '', '', '', 1, 0, 'F', '0', '0', 'support:platform:remove', '#', 'admin', NOW(), '', NULL, ''),
(2225, '平台导出', 2202, 5, '#', '', '', '', 1, 0, 'F', '0', '0', 'support:platform:export', '#', 'admin', NOW(), '', NULL, '')
ON DUPLICATE KEY UPDATE perms=VALUES(perms), menu_name=VALUES(menu_name);

-- 服务器管理按钮权限
INSERT INTO sys_menu(menu_id, menu_name, parent_id, order_num, path, component, `query`, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
VALUES
(2231, '服务器查询', 2203, 1, '#', '', '', '', 1, 0, 'F', '0', '0', 'support:server:query', '#', 'admin', NOW(), '', NULL, ''),
(2232, '服务器新增', 2203, 2, '#', '', '', '', 1, 0, 'F', '0', '0', 'support:server:add', '#', 'admin', NOW(), '', NULL, ''),
(2233, '服务器修改', 2203, 3, '#', '', '', '', 1, 0, 'F', '0', '0', 'support:server:edit', '#', 'admin', NOW(), '', NULL, ''),
(2234, '服务器删除', 2203, 4, '#', '', '', '', 1, 0, 'F', '0', '0', 'support:server:remove', '#', 'admin', NOW(), '', NULL, ''),
(2235, '服务器导出', 2203, 5, '#', '', '', '', 1, 0, 'F', '0', '0', 'support:server:export', '#', 'admin', NOW(), '', NULL, '')
ON DUPLICATE KEY UPDATE perms=VALUES(perms), menu_name=VALUES(menu_name);

-- 组织与联系人按钮权限
INSERT INTO sys_menu(menu_id, menu_name, parent_id, order_num, path, component, `query`, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
VALUES
(2241, '组织查询', 2204, 1, '#', '', '', '', 1, 0, 'F', '0', '0', 'support:org:query', '#', 'admin', NOW(), '', NULL, ''),
(2242, '组织新增', 2204, 2, '#', '', '', '', 1, 0, 'F', '0', '0', 'support:org:add', '#', 'admin', NOW(), '', NULL, ''),
(2243, '组织修改', 2204, 3, '#', '', '', '', 1, 0, 'F', '0', '0', 'support:org:edit', '#', 'admin', NOW(), '', NULL, ''),
(2244, '组织删除', 2204, 4, '#', '', '', '', 1, 0, 'F', '0', '0', 'support:org:remove', '#', 'admin', NOW(), '', NULL, ''),
(2245, '组织导出', 2204, 5, '#', '', '', '', 1, 0, 'F', '0', '0', 'support:org:export', '#', 'admin', NOW(), '', NULL, '')
ON DUPLICATE KEY UPDATE perms=VALUES(perms), menu_name=VALUES(menu_name);


-- ============================================================================
-- support_upgrade_20260326_site_region_and_code.sql
-- ============================================================================
SET @ddl = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE sup_site ADD COLUMN province_code VARCHAR(32) DEFAULT NULL COMMENT ''省级行政区编码'' AFTER site_code',
    'SELECT 1'
  )
  FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'sup_site' AND column_name = 'province_code'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE sup_site ADD COLUMN province_name VARCHAR(64) DEFAULT NULL COMMENT ''省级行政区名称'' AFTER province_code',
    'SELECT 1'
  )
  FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'sup_site' AND column_name = 'province_name'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE sup_site ADD COLUMN city_code VARCHAR(32) DEFAULT NULL COMMENT ''市级行政区编码'' AFTER province_name',
    'SELECT 1'
  )
  FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'sup_site' AND column_name = 'city_code'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE sup_site ADD COLUMN city_name VARCHAR(64) DEFAULT NULL COMMENT ''市级行政区名称'' AFTER city_code',
    'SELECT 1'
  )
  FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'sup_site' AND column_name = 'city_name'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE sup_site ADD COLUMN district_code VARCHAR(32) DEFAULT NULL COMMENT ''区县行政区编码'' AFTER city_name',
    'SELECT 1'
  )
  FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'sup_site' AND column_name = 'district_code'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE sup_site ADD COLUMN district_name VARCHAR(64) DEFAULT NULL COMMENT ''区县行政区名称'' AFTER district_code',
    'SELECT 1'
  )
  FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'sup_site' AND column_name = 'district_name'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;


-- ============================================================================
-- support_upgrade_20260326_platform_contact_rel.sql
-- ============================================================================
CREATE TABLE IF NOT EXISTS sup_platform_contact_rel (
  rel_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '关系ID',
  platform_id BIGINT NOT NULL COMMENT '主平台ID',
  contact_id BIGINT NOT NULL COMMENT '联系人ID',
  create_by VARCHAR(64) DEFAULT '' COMMENT '创建者',
  create_time DATETIME DEFAULT NULL COMMENT '创建时间',
  PRIMARY KEY (rel_id),
  UNIQUE KEY uk_sup_platform_contact (platform_id, contact_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='主平台-联系人关系';


-- ============================================================================
-- support_upgrade_20260326_migrate_org_type_values.sql
-- ============================================================================
-- support 组织类型升级：
-- VENDOR -> THIRD_VENDOR
-- USER_UNIT -> USER
-- 可重复执行，未命中的语句不会报错。

START TRANSACTION;

UPDATE sup_org
SET org_type = 'THIRD_VENDOR'
WHERE org_type = 'VENDOR';

UPDATE sup_org
SET org_type = 'USER'
WHERE org_type = 'USER_UNIT';

COMMIT;


-- ============================================================================
-- support_upgrade_20260326_migrate_platform_org_rel_to_contact_rel.sql
-- ============================================================================
-- 将历史“主平台-组织”关系迁移为“主平台-联系人”关系
-- 迁移规则：
-- 1. 仅处理主平台的旧组织关系
-- 2. 将组织下的联系人全部映射到主平台
-- 3. 迁移完成后删除已迁移的旧组织关系

START TRANSACTION;

INSERT IGNORE INTO sup_platform_contact_rel (platform_id, contact_id, create_by, create_time)
SELECT por.platform_id, c.contact_id, 'migration', NOW()
FROM sup_platform_org_rel por
INNER JOIN sup_platform p ON p.platform_id = por.platform_id
INNER JOIN sup_contact c ON c.org_id = por.org_id
WHERE p.platform_level = 'MAIN';

DELETE por
FROM sup_platform_org_rel por
INNER JOIN sup_platform p ON p.platform_id = por.platform_id
INNER JOIN sup_contact c ON c.org_id = por.org_id
WHERE p.platform_level = 'MAIN';

COMMIT;


-- ============================================================================
-- support_upgrade_20260529_platform_network_env.sql
-- ============================================================================
-- 主平台网络环境字段与字典

SET @has_platform_network_env := (
  SELECT COUNT(1)
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'sup_platform'
    AND column_name = 'network_env'
);

SET @sql := IF(
  @has_platform_network_env = 0,
  'ALTER TABLE sup_platform ADD COLUMN network_env VARCHAR(100) DEFAULT NULL COMMENT ''网络环境'' AFTER platform_level',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

INSERT INTO sys_dict_type(dict_name, dict_type, status, create_by, create_time, remark)
VALUES ('网络环境', 'support_network_env', '0', 'admin', NOW(), '现场融合主平台网络环境')
ON DUPLICATE KEY UPDATE dict_name=VALUES(dict_name), status=VALUES(status), remark=VALUES(remark);

INSERT INTO sys_dict_data(dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status, create_by, create_time, remark)
SELECT 1, '公安网', '公安网', 'support_network_env', 'network-env-tag--police', 'primary', 'Y', '0', 'admin', NOW(), '内置网络环境'
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data WHERE dict_type = 'support_network_env' AND dict_value = '公安网');

INSERT INTO sys_dict_data(dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status, create_by, create_time, remark)
SELECT 2, '图像网', '图像网', 'support_network_env', 'network-env-tag--image', 'success', 'N', '0', 'admin', NOW(), '内置网络环境'
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data WHERE dict_type = 'support_network_env' AND dict_value = '图像网');

INSERT INTO sys_dict_data(dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status, create_by, create_time, remark)
SELECT 3, '政务网', '政务网', 'support_network_env', 'network-env-tag--government', 'warning', 'N', '0', 'admin', NOW(), '内置网络环境'
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data WHERE dict_type = 'support_network_env' AND dict_value = '政务网');

INSERT INTO sys_dict_data(dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status, create_by, create_time, remark)
SELECT 4, '二类区', '二类区', 'support_network_env', 'network-env-tag--secondary', 'info', 'N', '0', 'admin', NOW(), '内置网络环境'
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data WHERE dict_type = 'support_network_env' AND dict_value = '二类区');

INSERT INTO sys_dict_data(dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status, create_by, create_time, remark)
SELECT 5, '党政军', '党政军', 'support_network_env', 'network-env-tag--party', 'danger', 'N', '0', 'admin', NOW(), '内置网络环境'
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data WHERE dict_type = 'support_network_env' AND dict_value = '党政军');

INSERT INTO sys_dict_data(dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status, create_by, create_time, remark)
SELECT 6, '私网', '私网', 'support_network_env', 'network-env-tag--private', 'default', 'N', '0', 'admin', NOW(), '内置网络环境'
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data WHERE dict_type = 'support_network_env' AND dict_value = '私网');

UPDATE sys_dict_data
SET css_class = CASE dict_value
    WHEN '公安网' THEN 'network-env-tag--police'
    WHEN '图像网' THEN 'network-env-tag--image'
    WHEN '政务网' THEN 'network-env-tag--government'
    WHEN '二类区' THEN 'network-env-tag--secondary'
    WHEN '党政军' THEN 'network-env-tag--party'
    WHEN '私网' THEN 'network-env-tag--private'
    ELSE css_class
  END,
  list_class = CASE dict_value
    WHEN '公安网' THEN 'primary'
    WHEN '图像网' THEN 'success'
    WHEN '政务网' THEN 'warning'
    WHEN '二类区' THEN 'info'
    WHEN '党政军' THEN 'danger'
    WHEN '私网' THEN 'default'
    ELSE list_class
  END
WHERE dict_type = 'support_network_env'
  AND dict_value IN ('公安网', '图像网', '政务网', '二类区', '党政军', '私网');


-- ============================================================================
-- support_upgrade_20260529_contact_role_dict.sql
-- ============================================================================
-- 联系人角色改为可配置字典，新增联系人时可继续添加角色
INSERT INTO sys_dict_type(dict_name, dict_type, status, create_by, create_time, remark)
VALUES ('联系人角色', 'support_contact_role', '0', 'admin', NOW(), '现场融合联系人角色')
ON DUPLICATE KEY UPDATE dict_name=VALUES(dict_name), status=VALUES(status), remark=VALUES(remark);

INSERT INTO sys_dict_data(dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status, create_by, create_time, remark)
SELECT 1, '技术', 'TECH', 'support_contact_role', '', 'primary', 'Y', '0', 'admin', NOW(), '内置联系人角色'
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data WHERE dict_type = 'support_contact_role' AND dict_value = 'TECH');

INSERT INTO sys_dict_data(dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status, create_by, create_time, remark)
SELECT 2, '管理', 'MANAGER', 'support_contact_role', '', 'success', 'N', '0', 'admin', NOW(), '内置联系人角色'
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data WHERE dict_type = 'support_contact_role' AND dict_value = 'MANAGER');

INSERT INTO sys_dict_data(dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status, create_by, create_time, remark)
SELECT 3, '商务', 'BIZ', 'support_contact_role', '', 'warning', 'N', '0', 'admin', NOW(), '内置联系人角色'
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data WHERE dict_type = 'support_contact_role' AND dict_value = 'BIZ');

-- ============================================================================
-- support_upgrade_20260610_tim_inspection_v2_5_0.sql
-- ============================================================================

CREATE TABLE IF NOT EXISTS sup_tim_inspection (
  inspection_id       BIGINT       NOT NULL AUTO_INCREMENT COMMENT '巡检ID',
  inspection_time     DATETIME     NOT NULL COMMENT '巡检时间',
  inspection_type     VARCHAR(64)  DEFAULT 'TIM_GA_VEHICLE' COMMENT '巡检类型',
  source_type         VARCHAR(16)  DEFAULT 'AUTO' COMMENT '执行来源（AUTO自动 MANUAL手动）',
  result_status       CHAR(1)      DEFAULT '3' COMMENT '巡检结果（1正常 2异常 3未检测）',
  executor_name       VARCHAR(64)  DEFAULT NULL COMMENT '执行人名称',
  plan_id             BIGINT       DEFAULT NULL COMMENT '巡检计划ID',
  plan_name           VARCHAR(120) DEFAULT NULL COMMENT '巡检计划名称',
  report_style        VARCHAR(32)  DEFAULT 'STANDARD' COMMENT '巡检报告样式',
  enabled_item_count  INT          DEFAULT 0 COMMENT '启用项数',
  skipped_item_count  INT          DEFAULT 0 COMMENT '跳过项数',
  summary             VARCHAR(500) DEFAULT NULL COMMENT '巡检摘要',
  abnormal_summary    TEXT         COMMENT '异常摘要',
  create_by           VARCHAR(64)  DEFAULT '' COMMENT '创建者',
  create_time         DATETIME     DEFAULT NULL COMMENT '创建时间',
  update_by           VARCHAR(64)  DEFAULT '' COMMENT '更新者',
  update_time         DATETIME     DEFAULT NULL COMMENT '更新时间',
  remark              VARCHAR(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (inspection_id),
  KEY idx_sup_tim_inspection_time (inspection_time),
  KEY idx_sup_tim_inspection_result (result_status, inspection_time),
  KEY idx_sup_tim_inspection_source (source_type, inspection_time),
  KEY idx_sup_tim_inspection_plan (plan_id, inspection_time)
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
  extra_params     TEXT          COMMENT '扩展参数/请求体模板',
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
(2206, 'TIM系统巡检', 2200, 6, 'timInspection', 'support/timInspection/index', '', 'SupportTimInspection', 1, 0, 'C', '0', '0', 'support:timInspection:list', 'scan-search', 'admin', NOW(), '', NULL, 'TIM系统可配置巡检')
ON DUPLICATE KEY UPDATE menu_name=VALUES(menu_name), path=VALUES(path), component=VALUES(component), perms=VALUES(perms), route_name=VALUES(route_name);

INSERT INTO sys_menu(menu_id, menu_name, parent_id, order_num, path, component, `query`, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
VALUES
(2261, 'TIM巡检查询', 2206, 1, '#', '', '', '', 1, 0, 'F', '0', '0', 'support:timInspection:query', '#', 'admin', NOW(), '', NULL, ''),
(2262, 'TIM巡检执行', 2206, 2, '#', '', '', '', 1, 0, 'F', '0', '0', 'support:timInspection:run', '#', 'admin', NOW(), '', NULL, ''),
(2263, 'TIM巡检导出', 2206, 3, '#', '', '', '', 1, 0, 'F', '0', '0', 'support:timInspection:export', '#', 'admin', NOW(), '', NULL, ''),
(2264, 'TIM巡检配置', 2206, 4, '#', '', '', '', 1, 0, 'F', '0', '0', 'support:timInspection:config', '#', 'admin', NOW(), '', NULL, ''),
(2265, 'TIM巡检计划', 2206, 5, '#', '', '', '', 1, 0, 'F', '0', '0', 'support:timInspection:plan', '#', 'admin', NOW(), '', NULL, '')
ON DUPLICATE KEY UPDATE perms=VALUES(perms), menu_name=VALUES(menu_name);

INSERT INTO sys_role_menu(role_id, menu_id)
SELECT r.role_id, m.menu_id
FROM sys_role r
INNER JOIN sys_menu m ON m.menu_id IN (2206, 2261, 2262, 2263, 2264, 2265)
WHERE r.role_key = 'datafusion'
  AND NOT EXISTS (
    SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = r.role_id AND rm.menu_id = m.menu_id
  );

-- ============================================================================
-- support_upgrade_20260611_auto_inspection_v3_0_0.sql
-- ============================================================================

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
  label_name     VARCHAR(64)  DEFAULT NULL COMMENT '标签名称',
  template_desc  VARCHAR(500) DEFAULT NULL COMMENT '模板描述',
  status         CHAR(1)      DEFAULT '0' COMMENT '状态（0正常 1停用）',
  create_by      VARCHAR(64)  DEFAULT '' COMMENT '创建者',
  create_time    DATETIME     DEFAULT NULL COMMENT '创建时间',
  update_by      VARCHAR(64)  DEFAULT '' COMMENT '更新者',
  update_time    DATETIME     DEFAULT NULL COMMENT '更新时间',
  remark         VARCHAR(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (template_id),
  KEY idx_sup_auto_template_label_status (label_name, status),
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
  label_name      VARCHAR(64)   DEFAULT NULL COMMENT '标签名称',
  plan_mode       VARCHAR(16)   DEFAULT 'ROUTINE' COMMENT '计划模式（ROUTINE例行 FREQUENT高频）',
  cron_expression VARCHAR(255)  NOT NULL COMMENT '系统生成Cron表达式',
  cron_config     TEXT          COMMENT '可视化周期配置JSON',
  health_config   TEXT          COMMENT '高频健康配置JSON',
  job_id          BIGINT        DEFAULT NULL COMMENT '若依定时任务ID',
  report_style    VARCHAR(32)   DEFAULT 'STANDARD' COMMENT '报告样式',
  status          CHAR(1)       DEFAULT '0' COMMENT '状态（0正常 1暂停）',
  create_by       VARCHAR(64)   DEFAULT '' COMMENT '创建者',
  create_time     DATETIME      DEFAULT NULL COMMENT '创建时间',
  update_by       VARCHAR(64)   DEFAULT '' COMMENT '更新者',
  update_time     DATETIME      DEFAULT NULL COMMENT '更新时间',
  remark          VARCHAR(500)  DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (plan_id),
  KEY idx_sup_auto_plan_label_status (label_name, status),
  KEY idx_sup_auto_plan_template (template_id),
  KEY idx_sup_auto_plan_status (status),
  KEY idx_sup_auto_plan_job (job_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='自动化巡检计划';

CREATE TABLE IF NOT EXISTS sup_auto_inspection_record (
  record_id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '记录ID',
  inspection_time    DATETIME     NOT NULL COMMENT '巡检时间',
  source_type        VARCHAR(16)  DEFAULT 'AUTO' COMMENT '执行来源（AUTO自动 MANUAL手动）',
  run_mode           VARCHAR(16)  DEFAULT 'ROUTINE' COMMENT '运行模式（ROUTINE例行 FREQUENT高频）',
  schedule_slot_time DATETIME     DEFAULT NULL COMMENT '高频计划归一化采样时隙',
  duration_ms        BIGINT       DEFAULT NULL COMMENT '执行耗时毫秒',
  result_status      CHAR(1)      DEFAULT '3' COMMENT '巡检结果（1正常 2异常 3未检测 4关注）',
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
  warning_count      INT          DEFAULT 0 COMMENT '关注步骤数',
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
  UNIQUE KEY uk_sup_auto_record_plan_slot (plan_id, schedule_slot_time),
  KEY idx_sup_auto_record_mode_time (run_mode, inspection_time),
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
  result_status       CHAR(1)       DEFAULT '3' COMMENT '步骤结果（1正常 2异常 3未检测 4关注）',
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
  result_status   CHAR(1)       DEFAULT '3' COMMENT '目标结果（1正常 2异常 3未执行 4关注）',
  actual_value    DECIMAL(18,2) DEFAULT NULL COMMENT '实际值',
  actual_unit     VARCHAR(32)   DEFAULT NULL COMMENT '实际单位',
  evaluation_mode VARCHAR(16)   DEFAULT 'FIXED' COMMENT '判定方式（FIXED固定阈值 PREVIOUS上次结果）',
  previous_value  DECIMAL(30,2) DEFAULT NULL COMMENT '上次采样值',
  change_value    DECIMAL(30,2) DEFAULT NULL COMMENT '本次与上次变化量',
  evaluation_rule VARCHAR(500)  DEFAULT NULL COMMENT '本次判定公式',
  baseline_flag   CHAR(1)       DEFAULT 'N' COMMENT '是否本次建立基线（Y是 N否）',
  result_detail   MEDIUMTEXT    DEFAULT NULL COMMENT '结果详情',
  error_message   MEDIUMTEXT    DEFAULT NULL COMMENT '异常原因',
  create_by       VARCHAR(64)   DEFAULT '' COMMENT '创建者',
  create_time     DATETIME      DEFAULT NULL COMMENT '创建时间',
  update_by       VARCHAR(64)   DEFAULT '' COMMENT '更新者',
  update_time     DATETIME      DEFAULT NULL COMMENT '更新时间',
  remark          VARCHAR(500)  DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (result_id),
  KEY idx_sup_auto_target_result_record (record_id, step_result_id),
  KEY idx_sup_auto_target_result_target (target_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='自动化巡检目标结果';

CREATE TABLE IF NOT EXISTS sup_auto_inspection_probe_state (
  state_id          BIGINT         NOT NULL AUTO_INCREMENT COMMENT '状态ID',
  plan_id           BIGINT         NOT NULL COMMENT '计划ID',
  step_id           BIGINT         NOT NULL COMMENT '模板步骤ID',
  target_id         BIGINT         NOT NULL COMMENT '目标ID',
  tool_code         VARCHAR(64)    NOT NULL COMMENT '工具编码',
  primary_value     DECIMAL(30,2)  DEFAULT NULL COMMENT '当前主观测值',
  secondary_value   DECIMAL(30,2)  DEFAULT NULL COMMENT '当前辅助观测值',
  observed_at       DATETIME       DEFAULT NULL COMMENT '最近采样时间',
  last_activity_at  DATETIME       DEFAULT NULL COMMENT '最近活动时间',
  abnormal_streak   INT            DEFAULT 0 COMMENT '连续异常次数',
  normal_streak     INT            DEFAULT 0 COMMENT '连续恢复次数',
  state_status      CHAR(1)        DEFAULT '3' COMMENT '状态（1正常 2异常 3基线 4关注）',
  state_detail      VARCHAR(1000)  DEFAULT NULL COMMENT '状态说明',
  create_by         VARCHAR(64)    DEFAULT '' COMMENT '创建者',
  create_time       DATETIME       DEFAULT NULL COMMENT '创建时间',
  update_by         VARCHAR(64)    DEFAULT '' COMMENT '更新者',
  update_time       DATETIME       DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (state_id),
  UNIQUE KEY uk_sup_auto_probe_scope (plan_id, step_id, target_id, tool_code),
  KEY idx_sup_auto_probe_activity (last_activity_at, state_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='自动化巡检高频目标状态';

CREATE TABLE IF NOT EXISTS sup_auto_inspection_health_daily (
  summary_id          BIGINT        NOT NULL AUTO_INCREMENT COMMENT '汇总ID',
  health_date         DATE          NOT NULL COMMENT '健康日期',
  plan_id             BIGINT        NOT NULL COMMENT '高频计划ID',
  plan_name           VARCHAR(120)  NOT NULL COMMENT '计划名称快照',
  template_id         BIGINT        DEFAULT NULL COMMENT '模板ID',
  template_name       VARCHAR(120)  DEFAULT NULL COMMENT '模板名称快照',
  expected_count      INT           DEFAULT 0 COMMENT '截至当前应执行次数',
  completed_count     INT           DEFAULT 0 COMMENT '实际完成次数',
  normal_count        INT           DEFAULT 0 COMMENT '正常采样次数',
  warning_count       INT           DEFAULT 0 COMMENT '关注采样次数',
  abnormal_count      INT           DEFAULT 0 COMMENT '异常采样次数',
  skipped_count       INT           DEFAULT 0 COMMENT '未执行或跳过次数',
  missing_count       INT           DEFAULT 0 COMMENT '缺失采样次数',
  health_score        DECIMAL(5,2)  DEFAULT 0 COMMENT '当日健康度',
  health_target       DECIMAL(5,2)  DEFAULT 99 COMMENT '健康目标',
  day_status          CHAR(1)       DEFAULT '3' COMMENT '日状态（1正常 2异常 3无数据 4关注）',
  first_abnormal_time DATETIME      DEFAULT NULL COMMENT '首次异常时间',
  last_abnormal_time  DATETIME      DEFAULT NULL COMMENT '最后异常时间',
  last_run_time       DATETIME      DEFAULT NULL COMMENT '最后运行时间',
  last_result_status  CHAR(1)       DEFAULT NULL COMMENT '最后采样状态',
  abnormal_summary    TEXT          COMMENT '最近异常摘要',
  create_by           VARCHAR(64)   DEFAULT '' COMMENT '创建者',
  create_time         DATETIME      DEFAULT NULL COMMENT '创建时间',
  update_by           VARCHAR(64)   DEFAULT '' COMMENT '更新者',
  update_time         DATETIME      DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (summary_id),
  UNIQUE KEY uk_sup_auto_health_day_plan (health_date, plan_id),
  KEY idx_sup_auto_health_plan_date (plan_id, health_date),
  KEY idx_sup_auto_health_status_date (day_status, health_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='自动化巡检高频每日健康汇总';

INSERT INTO sup_auto_inspection_tool(tool_code, tool_name, tool_type, value_unit, default_compare_rule, default_threshold_value, default_timeout_seconds, default_time_window_minutes, param_schema, built_in_flag, status, create_by, create_time, remark)
SELECT 'KAFKA_LAG', 'Kafka消费组指标检测', 'KAFKA_LAG', '条', 'MAX', 2000, 10, 0, '{"fields":["topic","consumerGroup","kafkaMetric","evaluationConfig"]}', 'Y', '0', 'admin', NOW(), '一次采集Kafka积压和Offset指标，支持固定阈值或上次结果比较'
WHERE NOT EXISTS (SELECT 1 FROM sup_auto_inspection_tool WHERE tool_code = 'KAFKA_LAG');
INSERT INTO sup_auto_inspection_tool(tool_code, tool_name, tool_type, value_unit, default_compare_rule, default_threshold_value, default_timeout_seconds, default_time_window_minutes, param_schema, built_in_flag, status, create_by, create_time, remark)
SELECT 'KAFKA_TOPIC_ACTIVITY', 'Kafka主题写入中断检测', 'KAFKA_TOPIC_ACTIVITY', '条', 'MIN', 1, 10, 0, '{"fields":["topic","evaluationConfig"]}', 'Y', '1', 'admin', NOW(), '历史模板兼容工具'
WHERE NOT EXISTS (SELECT 1 FROM sup_auto_inspection_tool WHERE tool_code = 'KAFKA_TOPIC_ACTIVITY');
INSERT INTO sup_auto_inspection_tool(tool_code, tool_name, tool_type, value_unit, default_compare_rule, default_threshold_value, default_timeout_seconds, default_time_window_minutes, param_schema, built_in_flag, status, create_by, create_time, remark)
SELECT 'KAFKA_CONSUMER_PROGRESS', 'Kafka消费停滞检测', 'KAFKA_CONSUMER_PROGRESS', '条', 'MIN', 1, 10, 0, '{"fields":["topic","consumerGroup","evaluationConfig"]}', 'Y', '1', 'admin', NOW(), '历史模板兼容工具'
WHERE NOT EXISTS (SELECT 1 FROM sup_auto_inspection_tool WHERE tool_code = 'KAFKA_CONSUMER_PROGRESS');
INSERT INTO sup_auto_inspection_tool(tool_code, tool_name, tool_type, value_unit, default_compare_rule, default_threshold_value, default_timeout_seconds, default_time_window_minutes, param_schema, built_in_flag, status, create_by, create_time, remark)
SELECT 'MQTT_TOPIC_ACTIVITY', 'MQTT主题活跃度检测', 'MQTT_TOPIC_ACTIVITY', '条', 'MIN', 1, 10, 0, '{"fields":["broker","topicFilter","qos","ignoreRetained","evaluationConfig"]}', 'Y', '0', 'admin', NOW(), '后台持续订阅MQTT主题并支持固定阈值或上次结果比较'
WHERE NOT EXISTS (SELECT 1 FROM sup_auto_inspection_tool WHERE tool_code = 'MQTT_TOPIC_ACTIVITY');
INSERT INTO sup_auto_inspection_tool(tool_code, tool_name, tool_type, value_unit, default_compare_rule, default_threshold_value, default_timeout_seconds, default_time_window_minutes, param_schema, built_in_flag, status, create_by, create_time, remark)
SELECT 'HTTP_COUNT', '海康接口数量检测', 'HTTP_COUNT', '条', 'MIN', 0, 10, 480, '{"fields":["resultPath","extraParams","timeWindowMinutes"]}', 'Y', '0', 'admin', NOW(), '自动化巡检内置工具'
WHERE NOT EXISTS (SELECT 1 FROM sup_auto_inspection_tool WHERE tool_code = 'HTTP_COUNT');
INSERT INTO sup_auto_inspection_tool(tool_code, tool_name, tool_type, value_unit, default_compare_rule, default_threshold_value, default_timeout_seconds, default_time_window_minutes, param_schema, built_in_flag, status, create_by, create_time, remark)
SELECT 'HTTP_HEALTH', 'HTTP接口健康检测', 'HTTP_HEALTH', 'ms', 'MAX', 3000, 10, 0, '{"fields":["url","httpMethod","expectedStatus","timeoutSeconds"]}', 'Y', '0', 'admin', NOW(), '自动化巡检内置工具'
WHERE NOT EXISTS (SELECT 1 FROM sup_auto_inspection_tool WHERE tool_code = 'HTTP_HEALTH');
INSERT INTO sup_auto_inspection_tool(tool_code, tool_name, tool_type, value_unit, default_compare_rule, default_threshold_value, default_timeout_seconds, default_time_window_minutes, param_schema, built_in_flag, status, create_by, create_time, remark)
SELECT 'HTTP_API_TEST', '接口调用测试', 'HTTP_API_TEST', 'ms', 'MAX', 3000, 10, 0, '{"fields":["url","httpMethod","queryParams","headers","cookies","auth","bodyType","body","formParams","assertions","trustInternalCertificate"]}', 'Y', '0', 'admin', NOW(), '自动化巡检内置工具'
WHERE NOT EXISTS (SELECT 1 FROM sup_auto_inspection_tool WHERE tool_code = 'HTTP_API_TEST');
INSERT INTO sup_auto_inspection_tool(tool_code, tool_name, tool_type, value_unit, default_compare_rule, default_threshold_value, default_timeout_seconds, default_time_window_minutes, param_schema, built_in_flag, status, create_by, create_time, remark)
SELECT 'FTP_FILE_COUNT', 'FTP目录文件数量检测', 'FTP_FILE_COUNT', '个', 'MAX', 50, 10, 0, '{"fields":["path"]}', 'Y', '0', 'admin', NOW(), '自动化巡检内置工具'
WHERE NOT EXISTS (SELECT 1 FROM sup_auto_inspection_tool WHERE tool_code = 'FTP_FILE_COUNT');
INSERT INTO sup_auto_inspection_tool(tool_code, tool_name, tool_type, value_unit, default_compare_rule, default_threshold_value, default_timeout_seconds, default_time_window_minutes, param_schema, built_in_flag, status, create_by, create_time, remark)
SELECT 'SERVER_FILE_COUNT', '服务器目录文件数量检测', 'SERVER_FILE_COUNT', '个', 'MAX', 20, 10, 0, '{"fields":["serverTargets","recursive","filePattern"]}', 'Y', '0', 'admin', NOW(), '自动化巡检内置工具'
WHERE NOT EXISTS (SELECT 1 FROM sup_auto_inspection_tool WHERE tool_code = 'SERVER_FILE_COUNT');
INSERT INTO sup_auto_inspection_tool(tool_code, tool_name, tool_type, value_unit, default_compare_rule, default_threshold_value, default_timeout_seconds, default_time_window_minutes, param_schema, built_in_flag, status, create_by, create_time, remark)
SELECT 'SERVER_DISK', '服务器磁盘使用率检测', 'SERVER_DISK', '%', 'MAX', 80, 10, 0, '{"fields":["path"]}', 'Y', '0', 'admin', NOW(), '自动化巡检内置工具'
WHERE NOT EXISTS (SELECT 1 FROM sup_auto_inspection_tool WHERE tool_code = 'SERVER_DISK');
INSERT INTO sup_auto_inspection_tool(tool_code, tool_name, tool_type, value_unit, default_compare_rule, default_threshold_value, default_timeout_seconds, default_time_window_minutes, param_schema, built_in_flag, status, create_by, create_time, remark)
SELECT 'BIG_DATA_SERVER_DISK', '大数据服务器爆盘检测', 'BIG_DATA_SERVER_DISK', '%', 'MAX', 85, 15, 0, '{"fields":["serverTargets","includePseudo"]}', 'Y', '0', 'admin', NOW(), '自动化巡检内置工具'
WHERE NOT EXISTS (SELECT 1 FROM sup_auto_inspection_tool WHERE tool_code = 'BIG_DATA_SERVER_DISK');
INSERT INTO sup_auto_inspection_tool(tool_code, tool_name, tool_type, value_unit, default_compare_rule, default_threshold_value, default_timeout_seconds, default_time_window_minutes, param_schema, built_in_flag, status, create_by, create_time, remark)
SELECT 'TCP_PORT_CHECK', 'TCP端口连通性检测', 'TCP_PORT_CHECK', 'ms', 'MAX', 1000, 5, 0, '{"fields":["host","port","timeoutSeconds"]}', 'Y', '0', 'admin', NOW(), '自动化巡检内置工具'
WHERE NOT EXISTS (SELECT 1 FROM sup_auto_inspection_tool WHERE tool_code = 'TCP_PORT_CHECK');
INSERT INTO sup_auto_inspection_tool(tool_code, tool_name, tool_type, value_unit, default_compare_rule, default_threshold_value, default_timeout_seconds, default_time_window_minutes, param_schema, built_in_flag, status, create_by, create_time, remark)
SELECT 'SERVER_SERVICE_STATUS', '服务器服务状态检测', 'SERVER_SERVICE_STATUS', '状态', 'MIN', 1, 15, 0, '{"fields":["serverTargets","serviceName","privilegeMode","autoRestart","restartWaitSeconds"]}', 'Y', '0', 'admin', NOW(), '自动化巡检内置工具'
WHERE NOT EXISTS (SELECT 1 FROM sup_auto_inspection_tool WHERE tool_code = 'SERVER_SERVICE_STATUS');

INSERT INTO sup_auto_inspection_tool(tool_code, tool_name, tool_type, value_unit, default_compare_rule, default_threshold_value, default_timeout_seconds, default_time_window_minutes, param_schema, built_in_flag, status, create_by, create_time, remark)
SELECT 'DATABASE_QUERY', '数据库查询检查', 'DATABASE_QUERY', '条', 'MIN', 1, 15, 0, '{"fields":["databaseType","host","port","databaseName","query","resultMode","resultColumn"]}', 'Y', '0', 'admin', NOW(), '自动化巡检内置只读数据库取数工具'
WHERE NOT EXISTS (SELECT 1 FROM sup_auto_inspection_tool WHERE tool_code = 'DATABASE_QUERY');

UPDATE sup_auto_inspection_tool
SET tool_name = CONVERT(0xe695b0e68daee5ba93e69fa5e8afa2e6a380e69fa5 USING utf8mb4),
    value_unit = CONVERT(0xe69da1 USING utf8mb4),
    remark = CONVERT(0xe887aae58aa8e58c96e5b7a1e6a380e58685e7bdaee58faae8afbbe695b0e68daee5ba93e58f96e695b0e5b7a5e585b7 USING utf8mb4)
WHERE tool_code = 'DATABASE_QUERY';

UPDATE sys_menu SET visible = '1', update_time = NOW(), remark = '已由自动化巡检模块替代，保留旧数据入口隐藏'
WHERE menu_id = 2206;

UPDATE sys_job
SET status = '1',
    update_by = 'admin',
    update_time = NOW(),
    remark = CONCAT(IFNULL(remark, ''), IF(IFNULL(remark, '') = '', '', '；'), '旧巡检任务已冻结，请使用自动化巡检计划')
WHERE invoke_target LIKE 'supportTimInspectionTask%';

INSERT INTO sys_menu(menu_id, menu_name, parent_id, order_num, path, component, `query`, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
VALUES
(2300, '自动化巡检', 0, 6, 'autoInspection', 'Layout', '', 'AutoInspection', 1, 0, 'M', '0', '0', '', 'scan-search', 'admin', NOW(), '', NULL, '可配置自动化巡检中心')
ON DUPLICATE KEY UPDATE menu_name=VALUES(menu_name), path=VALUES(path), route_name=VALUES(route_name), visible=VALUES(visible), status=VALUES(status), remark=VALUES(remark);

INSERT INTO sys_menu(menu_id, menu_name, parent_id, order_num, path, component, `query`, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
VALUES
(2307, '巡检驾驶舱', 2300, 1, 'cockpit', 'support/autoInspection/cockpit', '', 'AutoInspectionCockpit', 1, 0, 'C', '0', '0', 'support:autoInspection:query', 'gauge', 'admin', NOW(), '', NULL, '统一展示例行巡检、高频健康、当前计划状态和当日问题'),
(2305, '巡检总览', 2300, 2, 'dashboard', 'support/autoInspection/index', '{"tab":"dashboard"}', 'AutoInspectionDashboard', 1, 0, 'C', '0', '0', 'support:autoInspection:query', 'chart-no-axes-combined', 'admin', NOW(), '', NULL, '按天查看巡检记录和高频每日健康明细'),
(2301, '巡检配置', 2300, 3, 'config', 'support/autoInspection/index', '{"tab":"template"}', 'AutoInspectionConfig', 1, 0, 'C', '0', '0', '', 'workflow', 'admin', NOW(), '', NULL, '在模板步骤中选择巡检工具并配置目标、阈值和参数'),
(2302, '巡检目标', 2300, 2, 'target', 'support/autoInspection/index', '{"tab":"target"}', 'AutoInspectionTarget', 1, 0, 'C', '1', '0', 'support:autoInspection:target', 'server-cog', 'admin', NOW(), '', NULL, '旧目标独立入口已隐藏，目标在巡检模板步骤内配置'),
(2303, '巡检计划', 2300, 3, 'plan', 'support/autoInspection/index', '{"tab":"plan"}', 'AutoInspectionPlan', 1, 0, 'C', '1', '0', 'support:autoInspection:plan', 'time', 'admin', NOW(), '', NULL, '已合并到巡检配置入口'),
(2304, '巡检记录', 2300, 3, 'record', 'support/autoInspection/index', '{"tab":"record"}', 'AutoInspectionRecord', 1, 0, 'C', '1', '0', 'support:autoInspection:query', 'file-clock', 'admin', NOW(), '', NULL, '巡检记录已合并到巡检总览，保留隐藏路由兼容历史入口'),
(2306, '巡检版本记录', 2300, 4, 'version', 'support/version/index', '{"module":"autoInspection"}', 'AutoInspectionVersion', 1, 0, 'C', '0', '0', 'support:version:list', 'file-clock', 'admin', NOW(), '', NULL, '自动化巡检模块版本记录')
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
INNER JOIN sys_menu m ON m.menu_id IN (2300, 2301, 2302, 2303, 2304, 2305, 2306, 2307, 2311, 2312, 2313, 2314, 2315, 2316)
WHERE r.role_key = 'datafusion'
  AND NOT EXISTS (
    SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = r.role_id AND rm.menu_id = m.menu_id
  );

-- v3.11.0 自动化巡检模板与计划标签
SET @template_label_column = (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sup_auto_inspection_template' AND COLUMN_NAME = 'label_name'
);
SET @ddl = IF(@template_label_column = 0,
  'ALTER TABLE sup_auto_inspection_template ADD COLUMN label_name VARCHAR(64) DEFAULT NULL COMMENT ''标签名称'' AFTER template_name',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @plan_label_column = (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sup_auto_inspection_plan' AND COLUMN_NAME = 'label_name'
);
SET @ddl = IF(@plan_label_column = 0,
  'ALTER TABLE sup_auto_inspection_plan ADD COLUMN label_name VARCHAR(64) DEFAULT NULL COMMENT ''标签名称'' AFTER plan_name',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @template_label_index = (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sup_auto_inspection_template' AND INDEX_NAME = 'idx_sup_auto_template_label_status'
);
SET @ddl = IF(@template_label_index = 0,
  'ALTER TABLE sup_auto_inspection_template ADD INDEX idx_sup_auto_template_label_status (label_name, status)',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @plan_label_index = (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sup_auto_inspection_plan' AND INDEX_NAME = 'idx_sup_auto_plan_label_status'
);
SET @ddl = IF(@plan_label_index = 0,
  'ALTER TABLE sup_auto_inspection_plan ADD INDEX idx_sup_auto_plan_label_status (label_name, status)',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- v3.13.0 自动化巡检高频模式兼容升级
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sup_auto_inspection_plan' AND COLUMN_NAME = 'plan_mode') = 0,
  'ALTER TABLE sup_auto_inspection_plan ADD COLUMN plan_mode VARCHAR(16) DEFAULT ''ROUTINE'' COMMENT ''计划模式（ROUTINE例行 FREQUENT高频）'' AFTER label_name', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sup_auto_inspection_plan' AND COLUMN_NAME = 'health_config') = 0,
  'ALTER TABLE sup_auto_inspection_plan ADD COLUMN health_config TEXT COMMENT ''高频健康配置JSON'' AFTER cron_config', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sup_auto_inspection_record' AND COLUMN_NAME = 'run_mode') = 0,
  'ALTER TABLE sup_auto_inspection_record ADD COLUMN run_mode VARCHAR(16) DEFAULT ''ROUTINE'' COMMENT ''运行模式（ROUTINE例行 FREQUENT高频）'' AFTER source_type', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sup_auto_inspection_record' AND COLUMN_NAME = 'schedule_slot_time') = 0,
  'ALTER TABLE sup_auto_inspection_record ADD COLUMN schedule_slot_time DATETIME DEFAULT NULL COMMENT ''高频计划归一化采样时隙'' AFTER run_mode', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sup_auto_inspection_record' AND COLUMN_NAME = 'duration_ms') = 0,
  'ALTER TABLE sup_auto_inspection_record ADD COLUMN duration_ms BIGINT DEFAULT NULL COMMENT ''执行耗时毫秒'' AFTER schedule_slot_time', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sup_auto_inspection_record' AND COLUMN_NAME = 'warning_count') = 0,
  'ALTER TABLE sup_auto_inspection_record ADD COLUMN warning_count INT DEFAULT 0 COMMENT ''关注步骤数'' AFTER abnormal_count', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sup_auto_inspection_record' AND INDEX_NAME = 'uk_sup_auto_record_plan_slot') = 0,
  'ALTER TABLE sup_auto_inspection_record ADD UNIQUE KEY uk_sup_auto_record_plan_slot(plan_id, schedule_slot_time)', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sup_auto_inspection_record' AND INDEX_NAME = 'idx_sup_auto_record_mode_time') = 0,
  'ALTER TABLE sup_auto_inspection_record ADD KEY idx_sup_auto_record_mode_time(run_mode, inspection_time)', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
UPDATE sup_auto_inspection_plan SET plan_mode = 'ROUTINE' WHERE plan_mode IS NULL OR plan_mode = '';
UPDATE sup_auto_inspection_record SET run_mode = 'ROUTINE' WHERE run_mode IS NULL OR run_mode = '';

-- v3.16.0 自动化巡检统一数值判定与结构化判定证据
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sup_auto_inspection_target_result' AND COLUMN_NAME = 'evaluation_mode') = 0,
  'ALTER TABLE sup_auto_inspection_target_result ADD COLUMN evaluation_mode VARCHAR(16) DEFAULT ''FIXED'' COMMENT ''判定方式（FIXED固定阈值 PREVIOUS上次结果）'' AFTER actual_unit', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sup_auto_inspection_target_result' AND COLUMN_NAME = 'previous_value') = 0,
  'ALTER TABLE sup_auto_inspection_target_result ADD COLUMN previous_value DECIMAL(30,2) DEFAULT NULL COMMENT ''上次采样值'' AFTER evaluation_mode', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sup_auto_inspection_target_result' AND COLUMN_NAME = 'change_value') = 0,
  'ALTER TABLE sup_auto_inspection_target_result ADD COLUMN change_value DECIMAL(30,2) DEFAULT NULL COMMENT ''本次与上次变化量'' AFTER previous_value', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sup_auto_inspection_target_result' AND COLUMN_NAME = 'evaluation_rule') = 0,
  'ALTER TABLE sup_auto_inspection_target_result ADD COLUMN evaluation_rule VARCHAR(500) DEFAULT NULL COMMENT ''本次判定公式'' AFTER change_value', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sup_auto_inspection_target_result' AND COLUMN_NAME = 'baseline_flag') = 0,
  'ALTER TABLE sup_auto_inspection_target_result ADD COLUMN baseline_flag CHAR(1) DEFAULT ''N'' COMMENT ''是否本次建立基线（Y是 N否）'' AFTER evaluation_rule', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
UPDATE sup_auto_inspection_target_result SET evaluation_mode = 'FIXED' WHERE evaluation_mode IS NULL OR evaluation_mode = '';
UPDATE sup_auto_inspection_target_result SET baseline_flag = 'N' WHERE baseline_flag IS NULL OR baseline_flag = '';
UPDATE sup_auto_inspection_tool SET tool_name = 'Kafka消费组指标检测', param_schema = '{"fields":["topic","consumerGroup","kafkaMetric","evaluationConfig"]}', status = '0', remark = '一次采集最大积压、总积压、生产Offset和消费Offset；支持固定阈值或与上次结果比较', update_time = NOW() WHERE tool_code = 'KAFKA_LAG';
UPDATE sup_auto_inspection_tool SET status = '1', default_threshold_value = 1, param_schema = CASE tool_code WHEN 'KAFKA_TOPIC_ACTIVITY' THEN '{"fields":["topic","evaluationConfig"]}' ELSE '{"fields":["topic","consumerGroup","evaluationConfig"]}' END, remark = '历史模板兼容工具；新建步骤请使用KAFKA_LAG并选择生产总Offset或消费总Offset', update_time = NOW() WHERE tool_code IN ('KAFKA_TOPIC_ACTIVITY', 'KAFKA_CONSUMER_PROGRESS');
UPDATE sup_auto_inspection_tool SET default_threshold_value = 1, param_schema = '{"fields":["broker","topicFilter","qos","ignoreRetained","evaluationConfig"]}', remark = '后台持续订阅MQTT主题并支持固定阈值或上次结果比较', update_time = NOW() WHERE tool_code = 'MQTT_TOPIC_ACTIVITY';
