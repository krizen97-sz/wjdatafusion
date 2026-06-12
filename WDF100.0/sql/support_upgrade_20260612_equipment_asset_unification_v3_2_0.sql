-- v3.2.0 服务器与硬件资产统一管理
-- 说明：保留 sup_server 既有数据和维护方式，新增统一设备清单权限、网闸类型和网闸登记字段。

SET @col_exists := (
  SELECT COUNT(1) FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'sup_hardware_asset' AND column_name = 'gateway_mode'
);
SET @sql := IF(@col_exists = 0,
  'ALTER TABLE sup_hardware_asset ADD COLUMN gateway_mode VARCHAR(80) DEFAULT NULL COMMENT ''网闸模式'' AFTER vlan_info',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists := (
  SELECT COUNT(1) FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'sup_hardware_asset' AND column_name = 'gateway_direction'
);
SET @sql := IF(@col_exists = 0,
  'ALTER TABLE sup_hardware_asset ADD COLUMN gateway_direction VARCHAR(120) DEFAULT NULL COMMENT ''网闸数据流向'' AFTER gateway_mode',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists := (
  SELECT COUNT(1) FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'sup_hardware_asset' AND column_name = 'gateway_bandwidth'
);
SET @sql := IF(@col_exists = 0,
  'ALTER TABLE sup_hardware_asset ADD COLUMN gateway_bandwidth VARCHAR(80) DEFAULT NULL COMMENT ''网闸带宽'' AFTER gateway_direction',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists := (
  SELECT COUNT(1) FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'sup_hardware_asset' AND column_name = 'security_zone'
);
SET @sql := IF(@col_exists = 0,
  'ALTER TABLE sup_hardware_asset ADD COLUMN security_zone VARCHAR(200) DEFAULT NULL COMMENT ''安全域说明'' AFTER gateway_bandwidth',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

INSERT INTO sys_dict_type(dict_name, dict_type, status, create_by, create_time, remark)
VALUES ('硬件资产类型', 'support_hardware_type', '0', 'admin', NOW(), '现场融合硬件资产类型')
ON DUPLICATE KEY UPDATE dict_name=VALUES(dict_name), status=VALUES(status), remark=VALUES(remark);

INSERT INTO sys_dict_data(dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status, create_by, create_time, remark)
SELECT 4, '网闸', 'GATEWAY', 'support_hardware_type', 'hardware-type--gateway', 'danger', 'N', '0', 'admin', NOW(), '内置硬件类型'
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data WHERE dict_type = 'support_hardware_type' AND dict_value = 'GATEWAY');

INSERT INTO sys_menu(menu_id, menu_name, parent_id, order_num, path, component, `query`, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
VALUES
(2288, '设备清单查询', 2201, 14, '#', '', '', '', 1, 0, 'F', '0', '0', 'support:equipment:query', '#', 'admin', NOW(), '', NULL, ''),
(2289, '设备清单导出', 2201, 15, '#', '', '', '', 1, 0, 'F', '0', '0', 'support:equipment:export', '#', 'admin', NOW(), '', NULL, '')
ON DUPLICATE KEY UPDATE menu_name=VALUES(menu_name), parent_id=VALUES(parent_id), order_num=VALUES(order_num), perms=VALUES(perms), visible=VALUES(visible), status=VALUES(status);

INSERT INTO sys_role_menu(role_id, menu_id)
SELECT r.role_id, m.menu_id
FROM sys_role r
         INNER JOIN sys_menu m ON m.menu_id IN (2288, 2289)
WHERE r.role_key = 'datafusion'
  AND NOT EXISTS (
    SELECT 1
    FROM sys_role_menu rm
    WHERE rm.role_id = r.role_id
      AND rm.menu_id = m.menu_id
  );
