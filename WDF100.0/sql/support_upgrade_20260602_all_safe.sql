-- 现场融合管理 2026-06-02 数据库合并升级脚本
-- 设计原则：
-- 1. 只补充缺失的字段、表、字典和权限，不删除、不清空、不覆盖已有业务数据。
-- 2. 可重复执行；已存在的对象会自动跳过。
-- 3. 基础业务表不存在时，对应字段升级会跳过，避免影响非现场融合库或未初始化库。

-- =========================================================
-- 一、主平台网络环境字段
-- =========================================================
SET @support_has_sup_platform := (
  SELECT COUNT(1)
  FROM information_schema.TABLES
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'sup_platform'
);

SET @support_has_platform_network_env := (
  SELECT COUNT(1)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'sup_platform'
    AND COLUMN_NAME = 'network_env'
);

SET @support_platform_network_env_sql := IF(
  @support_has_sup_platform > 0 AND @support_has_platform_network_env = 0,
  'ALTER TABLE sup_platform ADD COLUMN network_env VARCHAR(100) DEFAULT NULL COMMENT ''网络环境'' AFTER platform_level',
  'SELECT 1'
);

PREPARE support_platform_network_env_stmt FROM @support_platform_network_env_sql;
EXECUTE support_platform_network_env_stmt;
DEALLOCATE PREPARE support_platform_network_env_stmt;

-- =========================================================
-- 二、联系人角色字段
-- =========================================================
SET @support_has_sup_contact := (
  SELECT COUNT(1)
  FROM information_schema.TABLES
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'sup_contact'
);

SET @support_has_contact_role_type := (
  SELECT COUNT(1)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'sup_contact'
    AND COLUMN_NAME = 'role_type'
);

SET @support_contact_role_type_sql := IF(
  @support_has_sup_contact > 0 AND @support_has_contact_role_type = 0,
  'ALTER TABLE sup_contact ADD COLUMN role_type VARCHAR(32) DEFAULT NULL COMMENT ''角色（support_contact_role字典）'' AFTER contact_name',
  'SELECT 1'
);

PREPARE support_contact_role_type_stmt FROM @support_contact_role_type_sql;
EXECUTE support_contact_role_type_stmt;
DEALLOCATE PREPARE support_contact_role_type_stmt;

-- =========================================================
-- 三、现场融合用户修改记录表
-- =========================================================
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
  KEY idx_sup_change_log_target (target_type, target_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='现场融合用户修改记录';

SET @support_has_change_log_detail := (
  SELECT COUNT(1)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'sup_change_log'
    AND COLUMN_NAME = 'detail_content'
);

SET @support_change_log_detail_sql := IF(
  @support_has_change_log_detail = 0,
  'ALTER TABLE sup_change_log ADD COLUMN detail_content TEXT DEFAULT NULL COMMENT ''操作详情'' AFTER summary',
  'SELECT 1'
);

PREPARE support_change_log_detail_stmt FROM @support_change_log_detail_sql;
EXECUTE support_change_log_detail_stmt;
DEALLOCATE PREPARE support_change_log_detail_stmt;

-- =========================================================
-- 四、网络环境内置字典
-- 说明：仅在不存在时新增，不修改已有字典数据。
-- =========================================================
INSERT INTO sys_dict_type(dict_name, dict_type, status, create_by, create_time, remark)
SELECT '网络环境', 'support_network_env', '0', 'admin', NOW(), '现场融合主平台网络环境'
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_type WHERE dict_type = 'support_network_env');

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

-- =========================================================
-- 五、联系人角色内置字典
-- 说明：仅在不存在时新增，不修改已有角色。
-- =========================================================
INSERT INTO sys_dict_type(dict_name, dict_type, status, create_by, create_time, remark)
SELECT '联系人角色', 'support_contact_role', '0', 'admin', NOW(), '现场融合联系人角色'
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_type WHERE dict_type = 'support_contact_role');

INSERT INTO sys_dict_data(dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status, create_by, create_time, remark)
SELECT 1, '技术', 'TECH', 'support_contact_role', '', 'primary', 'Y', '0', 'admin', NOW(), '内置联系人角色'
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data WHERE dict_type = 'support_contact_role' AND dict_value = 'TECH');

INSERT INTO sys_dict_data(dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status, create_by, create_time, remark)
SELECT 2, '管理', 'MANAGER', 'support_contact_role', '', 'success', 'N', '0', 'admin', NOW(), '内置联系人角色'
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data WHERE dict_type = 'support_contact_role' AND dict_value = 'MANAGER');

INSERT INTO sys_dict_data(dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status, create_by, create_time, remark)
SELECT 3, '商务', 'BIZ', 'support_contact_role', '', 'warning', 'N', '0', 'admin', NOW(), '内置联系人角色'
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data WHERE dict_type = 'support_contact_role' AND dict_value = 'BIZ');

-- =========================================================
-- 六、现场融合总权限 datafusion
-- 说明：仅当权限字符不存在时新增。若 menu_id=2291 已被占用，则使用当前最大 menu_id + 1。
-- =========================================================
SET @support_datafusion_menu_id := (
  SELECT IF(
    EXISTS(SELECT 1 FROM sys_menu WHERE menu_id = 2291),
    (SELECT COALESCE(MAX(menu_id), 2290) + 1 FROM sys_menu m),
    2291
  )
);

INSERT INTO sys_menu(menu_id, menu_name, parent_id, order_num, path, component, `query`, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
SELECT @support_datafusion_menu_id, '现场融合全部权限', 2200, 99, '#', '', '', '', 1, 0, 'F', '0', '0', 'datafusion', '#', 'admin', NOW(), '', NULL, ''
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'datafusion');

-- =========================================================
-- 七、执行后核验
-- =========================================================
SELECT 'sup_platform.network_env' AS check_item, COUNT(1) AS exists_count
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sup_platform' AND COLUMN_NAME = 'network_env'
UNION ALL
SELECT 'sup_contact.role_type', COUNT(1)
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sup_contact' AND COLUMN_NAME = 'role_type'
UNION ALL
SELECT 'sup_change_log.detail_content', COUNT(1)
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sup_change_log' AND COLUMN_NAME = 'detail_content'
UNION ALL
SELECT 'dict.support_network_env', COUNT(1)
FROM sys_dict_type
WHERE dict_type = 'support_network_env'
UNION ALL
SELECT 'dict.support_contact_role', COUNT(1)
FROM sys_dict_type
WHERE dict_type = 'support_contact_role'
UNION ALL
SELECT 'menu.datafusion', COUNT(1)
FROM sys_menu
WHERE perms = 'datafusion';
