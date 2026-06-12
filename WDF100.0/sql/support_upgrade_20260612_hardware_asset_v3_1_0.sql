-- 现场融合管理硬件资产扩展 v3.1.0
-- 说明：
-- 1. 新增非服务器类硬件资产表，资产类型包含解码器、终端、交换机。
-- 2. 保留 sup_server 作为服务器资产来源，不迁移服务器数据。
-- 3. 新增平台-硬件资产关系，支持绑定主平台、子平台或作为现场公共资产。
-- 4. 复用 support_network_env 网络环境字典，并新增 support_hardware_type 硬件资产类型字典。

CREATE TABLE IF NOT EXISTS sup_hardware_asset (
  asset_id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '硬件资产ID',
  site_id             BIGINT       NOT NULL COMMENT '现场ID',
  asset_name          VARCHAR(120) NOT NULL COMMENT '资产名称',
  asset_type          VARCHAR(32)  NOT NULL COMMENT '资产类型（DECODER/TERMINAL/SWITCH）',
  network_env         VARCHAR(100) NOT NULL COMMENT '网络环境',
  ip_address          VARCHAR(255) NOT NULL COMMENT 'IP地址',
  manage_ip           VARCHAR(255) DEFAULT NULL COMMENT '管理地址',
  mac_address         VARCHAR(64)  DEFAULT NULL COMMENT 'MAC地址',
  manufacturer        VARCHAR(100) DEFAULT NULL COMMENT '厂商',
  asset_model         VARCHAR(120) DEFAULT NULL COMMENT '型号',
  serial_no           VARCHAR(120) DEFAULT NULL COMMENT '序列号',
  install_location    VARCHAR(200) DEFAULT NULL COMMENT '安装位置',
  owner_org           VARCHAR(160) DEFAULT NULL COMMENT '归属组织',
  owner_contact       VARCHAR(80)  DEFAULT NULL COMMENT '责任人',
  channel_count       INT          DEFAULT NULL COMMENT '解码器通道数',
  output_type         VARCHAR(80)  DEFAULT NULL COMMENT '解码器输出类型',
  terminal_type       VARCHAR(80)  DEFAULT NULL COMMENT '终端类型',
  department          VARCHAR(120) DEFAULT NULL COMMENT '使用部门',
  use_location        VARCHAR(200) DEFAULT NULL COMMENT '使用位置',
  switch_level        VARCHAR(80)  DEFAULT NULL COMMENT '交换机层级',
  port_count          INT          DEFAULT NULL COMMENT '端口数',
  uplink_device       VARCHAR(160) DEFAULT NULL COMMENT '上联设备',
  vlan_info           VARCHAR(500) DEFAULT NULL COMMENT 'VLAN说明',
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

INSERT INTO sys_menu(menu_id, menu_name, parent_id, order_num, path, component, `query`, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
VALUES
(2294, '硬件资产查询', 2201, 9, '#', '', '', '', 1, 0, 'F', '0', '0', 'support:hardwareAsset:query', '#', 'admin', NOW(), '', NULL, ''),
(2295, '硬件资产新增', 2201, 10, '#', '', '', '', 1, 0, 'F', '0', '0', 'support:hardwareAsset:add', '#', 'admin', NOW(), '', NULL, ''),
(2296, '硬件资产修改', 2201, 11, '#', '', '', '', 1, 0, 'F', '0', '0', 'support:hardwareAsset:edit', '#', 'admin', NOW(), '', NULL, ''),
(2297, '硬件资产删除', 2201, 12, '#', '', '', '', 1, 0, 'F', '0', '0', 'support:hardwareAsset:remove', '#', 'admin', NOW(), '', NULL, ''),
(2298, '硬件资产导出', 2201, 13, '#', '', '', '', 1, 0, 'F', '0', '0', 'support:hardwareAsset:export', '#', 'admin', NOW(), '', NULL, '')
ON DUPLICATE KEY UPDATE perms=VALUES(perms), menu_name=VALUES(menu_name), parent_id=VALUES(parent_id), order_num=VALUES(order_num);

INSERT INTO sys_role_menu(role_id, menu_id)
SELECT r.role_id, m.menu_id
FROM sys_role r
INNER JOIN sys_menu m ON m.menu_id IN (2294, 2295, 2296, 2297, 2298)
WHERE r.role_key = 'datafusion'
  AND NOT EXISTS (
    SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = r.role_id AND rm.menu_id = m.menu_id
  );
