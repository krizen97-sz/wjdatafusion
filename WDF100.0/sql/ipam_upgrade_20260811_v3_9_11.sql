-- v3.9.11 IPAM在线工作表菜单
-- 说明：
-- 1. 在“IP分配管控”目录下新增“IP分配表格”独立页面。
-- 2. 复用现有IPAM地址查询和分配权限，不新增权限字符。
-- 3. 不修改任何ipam_*业务数据，不涉及sup_*现场融合业务表。

SET NAMES utf8mb4;

DROP PROCEDURE IF EXISTS ipam_workbook_menu_preflight_20260811;
DELIMITER $$
CREATE PROCEDURE ipam_workbook_menu_preflight_20260811()
SQL SECURITY INVOKER
BEGIN
  IF EXISTS (
    SELECT 1 FROM sys_menu
    WHERE menu_id = 2416
      AND NOT (component = 'ipam/workbook/index' OR path = 'workbook')
  ) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '菜单ID 2416 已被非IPAM页面占用，请先处理ID冲突';
  END IF;
END$$
DELIMITER ;

CALL ipam_workbook_menu_preflight_20260811();
DROP PROCEDURE IF EXISTS ipam_workbook_menu_preflight_20260811;

DROP TEMPORARY TABLE IF EXISTS tmp_ipam_workbook_roles_20260811;
CREATE TEMPORARY TABLE tmp_ipam_workbook_roles_20260811 (
  role_id BIGINT(20) NOT NULL,
  PRIMARY KEY (role_id)
) ENGINE=MEMORY;

INSERT IGNORE INTO tmp_ipam_workbook_roles_20260811(role_id)
SELECT DISTINCT role_id
FROM sys_role_menu
WHERE menu_id BETWEEN 2400 AND 2415;

INSERT INTO sys_menu(
  menu_id, menu_name, parent_id, order_num, path, component, `query`, route_name,
  is_frame, is_cache, menu_type, visible, status, perms, icon,
  create_by, create_time, update_by, update_time, remark
)
VALUES
  (2416, 'IP分配表格', 2400, 3, 'workbook', 'ipam/workbook/index', '', 'IpamWorkbook',
   1, 0, 'C', '0', '0', 'ipam:address:list', 'excel',
   'admin', NOW(), 'admin', NOW(), '按网段或小区查看并维护IP分配在线工作表')
ON DUPLICATE KEY UPDATE
  menu_name = VALUES(menu_name),
  parent_id = VALUES(parent_id),
  order_num = VALUES(order_num),
  path = VALUES(path),
  component = VALUES(component),
  `query` = VALUES(`query`),
  route_name = VALUES(route_name),
  is_frame = VALUES(is_frame),
  is_cache = VALUES(is_cache),
  menu_type = VALUES(menu_type),
  visible = VALUES(visible),
  status = VALUES(status),
  perms = VALUES(perms),
  icon = VALUES(icon),
  update_by = 'admin',
  update_time = NOW(),
  remark = VALUES(remark);

INSERT IGNORE INTO sys_role_menu(role_id, menu_id)
SELECT role_id, 2416 FROM tmp_ipam_workbook_roles_20260811;

DROP TEMPORARY TABLE IF EXISTS tmp_ipam_workbook_roles_20260811;

DROP PROCEDURE IF EXISTS ipam_workbook_menu_verify_20260811;
DELIMITER $$
CREATE PROCEDURE ipam_workbook_menu_verify_20260811()
SQL SECURITY INVOKER
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM sys_menu
    WHERE menu_id = 2416
      AND parent_id = 2400
      AND menu_type = 'C'
      AND path = 'workbook'
      AND component = 'ipam/workbook/index'
      AND perms = 'ipam:address:list'
  ) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'IPAM在线工作表菜单校验失败';
  END IF;
END$$
DELIMITER ;

CALL ipam_workbook_menu_verify_20260811();
DROP PROCEDURE IF EXISTS ipam_workbook_menu_verify_20260811;

SELECT menu_id, menu_name, parent_id, order_num, path, component, route_name, perms, icon
FROM sys_menu
WHERE menu_id = 2416;
