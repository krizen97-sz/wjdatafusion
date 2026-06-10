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
  UNIQUE KEY uk_sup_site_code (site_code)
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
  KEY idx_sup_change_log_target (target_type, target_id)
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

-- 菜单目录
INSERT INTO sys_menu(menu_id, menu_name, parent_id, order_num, path, component, `query`, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
VALUES
(2200, '现场融合管理', 0, 5, 'support', NULL, '', 'Support', 1, 0, 'M', '0', '0', '', 'monitor', 'admin', NOW(), '', NULL, '现场信息融合平台')
ON DUPLICATE KEY UPDATE menu_name=VALUES(menu_name), path=VALUES(path), route_name=VALUES(route_name);

INSERT INTO sys_menu(menu_id, menu_name, parent_id, order_num, path, component, `query`, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
VALUES
(2201, '现场管理', 2200, 1, 'site', 'support/site/index', '', 'SupportSite', 1, 0, 'C', '0', '0', 'support:site:list', 'tree-table', 'admin', NOW(), '', NULL, ''),
(2202, '平台管理', 2200, 2, 'platform', 'support/platform/index', '', 'SupportPlatform', 1, 0, 'C', '0', '0', 'support:platform:list', 'build', 'admin', NOW(), '', NULL, ''),
(2203, '服务器管理', 2200, 3, 'server', 'support/server/index', '', 'SupportServer', 1, 0, 'C', '0', '0', 'support:server:list', 'server', 'admin', NOW(), '', NULL, ''),
(2204, '组织与联系人', 2200, 4, 'org', 'support/org/index', '', 'SupportOrg', 1, 0, 'C', '0', '0', 'support:org:list', 'peoples', 'admin', NOW(), '', NULL, ''),
(2205, '版本记录', 2200, 5, 'version', 'support/version/index', '', 'SupportVersion', 1, 0, 'C', '0', '0', 'support:version:list', 'documentation', 'admin', NOW(), '', NULL, '现场融合功能版本记录')
ON DUPLICATE KEY UPDATE menu_name=VALUES(menu_name), perms=VALUES(perms), component=VALUES(component);

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
(2293, '留言发布', 2201, 8, '#', '', '', '', 1, 0, 'F', '0', '0', 'support:message:add', '#', 'admin', NOW(), '', NULL, '')
ON DUPLICATE KEY UPDATE perms=VALUES(perms), menu_name=VALUES(menu_name);

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
