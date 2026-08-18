-- v3.9.7 IPAM 网段一致性、敏感日志与扫描稳定性加固
-- 说明：
-- 1. 本脚本只修改 ipam_ 独立业务表和 ipam:* 权限，不修改 sup_* 现场融合业务表。
-- 2. 设备密码统一保存在 login_password；密码接口仍受权限和审计保护。

SET @ipam_db_name := DATABASE();

SET @has_network_start_value := (
  SELECT COUNT(1) FROM information_schema.columns
  WHERE table_schema = @ipam_db_name AND table_name = 'ipam_network' AND column_name = 'start_value'
);
SET @sql := IF(
  @has_network_start_value = 0,
  'ALTER TABLE ipam_network ADD COLUMN start_value BIGINT(20) DEFAULT NULL COMMENT ''网络起始数值'' AFTER end_ip',
  'SELECT ''ipam_network.start_value exists'' AS message'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_network_end_value := (
  SELECT COUNT(1) FROM information_schema.columns
  WHERE table_schema = @ipam_db_name AND table_name = 'ipam_network' AND column_name = 'end_value'
);
SET @sql := IF(
  @has_network_end_value = 0,
  'ALTER TABLE ipam_network ADD COLUMN end_value BIGINT(20) DEFAULT NULL COMMENT ''网络结束数值'' AFTER start_value',
  'SELECT ''ipam_network.end_value exists'' AS message'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE ipam_network
SET start_value = INET_ATON(start_ip),
    end_value = INET_ATON(end_ip)
WHERE start_value IS NULL OR end_value IS NULL;

SET @has_network_range_index := (
  SELECT COUNT(1) FROM information_schema.statistics
  WHERE table_schema = @ipam_db_name AND table_name = 'ipam_network' AND index_name = 'idx_ipam_network_range'
);
SET @sql := IF(
  @has_network_range_index = 0,
  'ALTER TABLE ipam_network ADD INDEX idx_ipam_network_range (start_value, end_value)',
  'SELECT ''idx_ipam_network_range exists'' AS message'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS ipam_scan_lock (
  lock_name     VARCHAR(32)  NOT NULL COMMENT '锁名称',
  owner_token   VARCHAR(64)  DEFAULT NULL COMMENT '持有实例随机令牌',
  scan_id       BIGINT(20)   DEFAULT NULL COMMENT '当前扫描任务ID',
  lease_until   DATETIME     DEFAULT NULL COMMENT '租约失效时间',
  update_time   DATETIME     DEFAULT NULL COMMENT '最近续租时间',
  PRIMARY KEY (lock_name),
  KEY idx_ipam_scan_lock_lease (lease_until)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='IPAM扫描全局租约';

INSERT INTO ipam_scan_lock(lock_name, owner_token, scan_id, lease_until, update_time)
VALUES ('GLOBAL', NULL, NULL, NULL, NOW())
ON DUPLICATE KEY UPDATE lock_name = VALUES(lock_name);

INSERT INTO ipam_setting
  (setting_key, setting_value, setting_name, create_by, create_time, update_by, update_time)
VALUES
  ('NETWORK_RANGE_LOCK', '1', '网段范围并发锁', 'admin', NOW(), 'admin', NOW())
ON DUPLICATE KEY UPDATE setting_name = VALUES(setting_name);

SET @duplicate_segment_networks := (
  SELECT COUNT(1)
  FROM (
    SELECT network_id
    FROM ipam_segment
    GROUP BY network_id
    HAVING COUNT(1) > 1
  ) duplicate_networks
);
SET @has_segment_network_unique := (
  SELECT COUNT(1) FROM information_schema.statistics
  WHERE table_schema = @ipam_db_name AND table_name = 'ipam_segment' AND index_name = 'uk_ipam_segment_network'
);
SET @sql := IF(
  @has_segment_network_unique > 0,
  'SELECT ''uk_ipam_segment_network exists'' AS message',
  IF(
    @duplicate_segment_networks = 0,
    'ALTER TABLE ipam_segment ADD UNIQUE INDEX uk_ipam_segment_network (network_id)',
    'SELECT ''存在一网段多地址池历史数据，未自动删改数据，请先拆分后再执行本脚本'' AS warning'
  )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

INSERT INTO sys_menu
  (menu_id, menu_name, parent_id, order_num, path, component, `query`, route_name,
   is_frame, is_cache, menu_type, visible, status, perms, icon,
   create_by, create_time, update_by, update_time, remark)
SELECT 2409, '地址下发', 2400, 9, '#', '', '', '', 1, 0, 'F', '0', '0',
       'ipam:address:issue', '#', 'admin', NOW(), 'admin', NOW(), '标记IP为已下发状态'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'ipam:address:issue');

INSERT INTO sys_menu
  (menu_id, menu_name, parent_id, order_num, path, component, `query`, route_name,
   is_frame, is_cache, menu_type, visible, status, perms, icon,
   create_by, create_time, update_by, update_time, remark)
SELECT 2412, '查看设备密码', 2400, 12, '#', '', '', '', 1, 0, 'F', '0', '0',
       'ipam:credential:view', '#', 'admin', NOW(), 'admin', NOW(), '按需查看IPAM设备密码并记录审计'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'ipam:credential:view');

DELETE role_menu
FROM sys_role_menu role_menu
JOIN sys_menu menu ON menu.menu_id = role_menu.menu_id
WHERE menu.perms = 'ipam:credential:migrate';

DELETE FROM sys_menu WHERE perms = 'ipam:credential:migrate';

UPDATE sys_menu
SET menu_name = '地址下发', visible = '0', status = '0', order_num = 9,
    update_by = 'admin', update_time = NOW(), remark = '标记IP为已下发状态'
WHERE perms = 'ipam:address:issue';

UPDATE sys_menu SET order_num = 10, update_time = NOW() WHERE perms = 'ipam:address:export';
UPDATE sys_menu SET order_num = 11, update_time = NOW() WHERE perms = 'ipam:network:scan';

UPDATE ipam_address
SET community_name = TRIM(community_name)
WHERE community_name IS NOT NULL AND community_name != TRIM(community_name);

UPDATE ipam_operation_log
SET detail_content = '{"redacted":true,"reason":"v3.9.7 credential hardening"}'
WHERE detail_content LIKE '%loginPassword%'
   OR detail_content LIKE '%login_password%';

UPDATE sys_oper_log
SET oper_param = '[IPAM敏感请求参数已脱敏]'
WHERE title LIKE 'IP分配管控%'
  AND (oper_param LIKE '%loginPassword%' OR oper_param LIKE '%login_password%');

UPDATE sys_oper_log
SET json_result = '[IPAM敏感响应数据已脱敏]'
WHERE title LIKE 'IP分配管控%'
  AND (json_result LIKE '%loginPassword%' OR json_result LIKE '%login_password%');
