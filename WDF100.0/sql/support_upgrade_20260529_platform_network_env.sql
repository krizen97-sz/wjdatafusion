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
