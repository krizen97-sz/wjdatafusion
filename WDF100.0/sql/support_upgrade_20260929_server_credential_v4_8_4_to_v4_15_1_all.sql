-- RYNEW v4.8.4 -> v4.15.1 cumulative database upgrade (server credentials, 2026-09-29).
-- Source range: c0d9efa..b3758652c5b9dd6c25cdeca221a45f637eff610e.
-- The only schema/permission delta is v4.14.0. Do NOT rerun support_v1.sql
-- or support_deploy_all.sql on an existing database for this upgrade.
-- Select the deployment database in the mysql command line; no hardcoded USE.
-- Back up first, pause writes, use a dedicated connection with autocommit=1.
-- Run the WHOLE file using mysql (supports DELIMITER), without --force.
-- Requires SELECT, ALTER, INSERT, CREATE ROUTINE, ALTER ROUTINE, EXECUTE.
-- No business-row UPDATE/DELETE, no password/port rewrite, no role grants.
-- Existing root_direct_login values and the existing permission row are kept.
-- DROP PROCEDURE below removes ONLY this file's migration helper, never a table.

SET NAMES utf8mb4;

DELIMITER $$
DROP PROCEDURE IF EXISTS rynew_upgrade_v484_v4151_20260929$$
CREATE PROCEDURE rynew_upgrade_v484_v4151_20260929()
SQL SECURITY INVOKER
BEGIN
  DECLARE v_count INT DEFAULT 0;
  DECLARE v_has_column INT DEFAULT 0;
  DECLARE v_has_permission INT DEFAULT 0;
  DECLARE v_parent BIGINT;

  IF DATABASE() IS NULL OR DATABASE() IN ('mysql', 'sys', 'information_schema', 'performance_schema') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Select the RYNEW application database before running this upgrade';
  END IF;
  IF @@SESSION.autocommit <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Use a dedicated autocommit=1 connection; DDL is not transactionally reversible';
  END IF;

  SELECT COUNT(*) INTO v_count FROM information_schema.tables
  WHERE table_schema = DATABASE() AND table_type = 'BASE TABLE'
    AND table_name IN ('sup_server', 'sys_menu', 'sys_role_menu');
  IF v_count <> 3 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Missing baseline tables: sup_server, sys_menu or sys_role_menu';
  END IF;
  SELECT COUNT(*) INTO v_count FROM information_schema.tables
  WHERE table_schema = DATABASE() AND table_name IN ('sup_server', 'sys_menu', 'sys_role_menu')
    AND engine <> 'InnoDB';
  IF v_count <> 0 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Expected InnoDB baseline tables; review customized engines before upgrading';
  END IF;
  SELECT COUNT(*) INTO v_count FROM information_schema.triggers
  WHERE trigger_schema = DATABASE() AND event_object_table IN ('sup_server', 'sys_menu');
  IF v_count <> 0 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Custom server/menu triggers found; review side effects before upgrading';
  END IF;

  SELECT COUNT(*) INTO v_count FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'sup_server' AND column_name = 'ssh_port';
  IF v_count <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Missing sup_server.ssh_port; complete the pre-v4.8.4 database upgrades first';
  END IF;

  SELECT COUNT(*) INTO v_count FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'sys_menu'
    AND column_name IN ('menu_id', 'menu_name', 'parent_id', 'order_num', 'path', 'component',
      'query', 'route_name', 'is_frame', 'is_cache', 'menu_type', 'visible', 'status', 'perms',
      'icon', 'create_by', 'create_time', 'update_by', 'update_time', 'remark');
  IF v_count <> 20 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'sys_menu is missing required baseline columns; no business changes applied';
  END IF;
  SELECT COUNT(*) INTO v_count FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'sys_menu' AND column_name = 'menu_id'
    AND extra LIKE '%auto_increment%';
  IF v_count <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'sys_menu.menu_id must be AUTO_INCREMENT; do not allocate a guessed menu ID';
  END IF;

  -- Resolve the existing parent by permission, allowing site-specific menu IDs.
  SELECT COUNT(*), MIN(menu_id) INTO v_count, v_parent FROM sys_menu
  WHERE perms = 'support:server:list' AND menu_type = 'C';
  IF v_count <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Expected exactly one server-management menu (support:server:list)';
  END IF;
  SELECT COUNT(*) INTO v_has_permission FROM sys_menu WHERE perms = 'support:credential:verify';
  IF v_has_permission > 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Duplicate support:credential:verify menus; review manually before upgrading';
  END IF;
  IF v_has_permission = 1 THEN
    SELECT COUNT(*) INTO v_count FROM sys_menu
    WHERE perms = 'support:credential:verify' AND menu_type = 'F' AND parent_id = v_parent;
    IF v_count <> 1 THEN
      SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Existing verify permission has a different parent or type; review manually';
    END IF;
  END IF;

  SELECT COUNT(*) INTO v_has_column FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'sup_server' AND column_name = 'root_direct_login';
  IF v_has_column = 1 THEN
    SELECT COUNT(*) INTO v_count FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'sup_server' AND column_name = 'root_direct_login'
      AND data_type = 'tinyint' AND is_nullable = 'NO' AND column_default = '0'
      AND COALESCE(generation_expression, '') = '';
    IF v_count <> 1 THEN
      SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Existing root_direct_login must be TINYINT NOT NULL DEFAULT 0; not altered';
    END IF;
    SELECT COUNT(*) INTO v_count FROM sup_server WHERE root_direct_login NOT IN (0, 1);
    IF v_count <> 0 THEN
      SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Existing root_direct_login contains values other than 0/1; not rewritten';
    END IF;
  END IF;

  -- All structural and menu checks above finish before changing application data.
  IF v_has_column = 0 THEN
    ALTER TABLE sup_server ADD COLUMN root_direct_login TINYINT(1) NOT NULL DEFAULT 0
      COMMENT '是否root直接登录' AFTER ssh_port;
  END IF;

  IF v_has_permission = 0 THEN
    INSERT INTO sys_menu
      (menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache,
       menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
    VALUES
      ('服务器登录校验', v_parent, 6, '#', '', '', '', 1, 0, 'F', '0', '0',
       'support:credential:verify', '#', 'admin', NOW(), '', NULL,
       '使用登记凭据验证SSH登录，不返回明文密码');
  END IF;

  SELECT 'SUCCESS' AS migration_status, DATABASE() AS database_name,
    'v4.8.4 -> v4.15.1' AS version_range, IF(v_has_column = 0, 'ADDED', 'PRESERVED') AS root_direct_login,
    IF(v_has_permission = 0, 'ADDED', 'PRESERVED') AS verify_permission;
END$$
CALL rynew_upgrade_v484_v4151_20260929()$$
DROP PROCEDURE rynew_upgrade_v484_v4151_20260929$$
DELIMITER ;

-- Do not INSERT into sys_role_menu automatically. Grant verify separately in the
-- role UI when needed. support:credential:viewPlain remains a separate permission.
-- On application rollback retain this additive column and permission. Never drop
-- or zero the column, as users may already have saved root-direct-login settings.
