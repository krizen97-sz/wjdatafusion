-- v4.16.0 server discovery and verified inventory synchronization.
-- Prerequisite: complete v4.15.1 schema, including sup_server.root_direct_login.
-- Back up first. No existing credentials, ports, roles or server rows are rewritten.
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS sup_server_scan_credential (
  credential_id BIGINT NOT NULL AUTO_INCREMENT,
  site_id BIGINT NOT NULL,
  credential_name VARCHAR(80) NOT NULL,
  login_mode VARCHAR(20) NOT NULL COMMENT 'ROOT_DIRECT/HIK_SU_ROOT',
  hik_password_cipher VARCHAR(1024) NOT NULL DEFAULT '',
  root_password_cipher VARCHAR(1024) NOT NULL DEFAULT '',
  create_by VARCHAR(64) DEFAULT '',
  create_time DATETIME DEFAULT NULL,
  update_by VARCHAR(64) DEFAULT '',
  update_time DATETIME DEFAULT NULL,
  PRIMARY KEY (credential_id),
  KEY idx_server_scan_credential_site (site_id),
  CONSTRAINT fk_server_scan_credential_site FOREIGN KEY (site_id) REFERENCES sup_site(site_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='现场授权扫描凭据池（AES加密）';

CREATE TABLE IF NOT EXISTS sup_server_scan_profile (
  server_id BIGINT NOT NULL,
  site_id BIGINT NOT NULL,
  host_name VARCHAR(255) NOT NULL DEFAULT '',
  os_name VARCHAR(120) NOT NULL DEFAULT '',
  os_version VARCHAR(120) NOT NULL DEFAULT '',
  kernel_version VARCHAR(120) NOT NULL DEFAULT '',
  detected_ports VARCHAR(64) NOT NULL DEFAULT '',
  scan_time DATETIME DEFAULT NULL,
  PRIMARY KEY (server_id),
  KEY idx_server_scan_profile_site (site_id),
  CONSTRAINT fk_server_scan_profile_server FOREIGN KEY (server_id) REFERENCES sup_server(server_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='服务器最后成功扫描的系统资料';

INSERT INTO sys_menu
(menu_name,parent_id,order_num,path,component,query,route_name,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time,remark)
SELECT '扫描服务器并同步', parent.menu_id, 7, '#', '', '', '', 1, 0, 'F', '0', '0', 'support:server:scan', '#', 'admin', NOW(), '授权网段发现、凭据池及验证成功后同步'
FROM sys_menu parent
WHERE parent.perms='support:server:list' AND parent.menu_type='C'
  AND NOT EXISTS (SELECT 1 FROM sys_menu existing WHERE existing.perms='support:server:scan');
-- No automatic role grants. datafusion/admin use the existing permission mechanism.
-- Keep tables on application rollback; credential pool values are encrypted with the existing SUPPORT_AES_KEY.
