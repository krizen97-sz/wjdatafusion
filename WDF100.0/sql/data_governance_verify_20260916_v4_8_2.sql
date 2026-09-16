-- v4.4.1 -> v4.8.2 升级后只读核验；不会修复或写入任何业务表。
SET NAMES utf8mb4;
SET @gov482_check_parent = NULL, @gov482_check_workspace = NULL;
SET @gov482_check_parent = (SELECT MAX(menu_id) FROM sys_menu WHERE parent_id=0 AND path='governance');
SET @gov482_check_workspace = (SELECT MAX(menu_id) FROM sys_menu WHERE parent_id=@gov482_check_parent AND path='workspace');
SELECT DATABASE() AS checked_database, VERSION() AS mysql_version;
SELECT 'root' AS check_name, 1 AS expected_count, COUNT(*) AS actual_count,
 IF(COUNT(*)=1,'PASS','CHECK_REQUIRED') AS result
FROM sys_menu WHERE parent_id=0 AND path='governance' AND menu_type='M'
UNION ALL
SELECT 'workspace',1,COUNT(*),IF(COUNT(*)=1,'PASS','CHECK_REQUIRED')
FROM sys_menu WHERE parent_id=@gov482_check_parent AND path='workspace' AND menu_type='C'
 AND component='governance/workspace/index' AND perms='governance:flow:list' AND is_frame=1
UNION ALL
SELECT 'four_button_permissions',4,COUNT(*),IF(COUNT(*)=4 AND COUNT(DISTINCT perms)=4,'PASS','CHECK_REQUIRED')
FROM sys_menu WHERE parent_id=@gov482_check_workspace AND menu_type='F' AND path='#'
 AND perms IN ('governance:project:add','governance:flow:add','governance:flow:edit','governance:flow:test')
UNION ALL
SELECT 'active_admin_role_1',1,COUNT(*),IF(COUNT(*)=1,'PASS','CHECK_REQUIRED')
FROM sys_role WHERE role_id=1 AND role_key='admin' AND status='0' AND del_flag='0'
UNION ALL
SELECT 'admin_expected_grants',6,COUNT(*),IF(COUNT(*)=6,'PASS','CHECK_REQUIRED')
FROM sys_role_menu rm JOIN sys_menu m ON m.menu_id=rm.menu_id
WHERE rm.role_id=1 AND (m.menu_id=@gov482_check_parent OR m.menu_id=@gov482_check_workspace OR
 (m.parent_id=@gov482_check_workspace AND m.menu_type='F' AND m.perms IN
 ('governance:project:add','governance:flow:add','governance:flow:edit','governance:flow:test')));

-- 已有隐藏/停用配置不会自动开启：非 AVAILABLE 须由管理员核对是否为有意配置。
SELECT menu_id,menu_name,parent_id,path,component,menu_type,perms,visible,status,
 IF(status='0' AND visible='0','AVAILABLE','PRESERVED_HIDDEN_OR_DISABLED') AS availability
FROM sys_menu WHERE menu_id IN (@gov482_check_parent,@gov482_check_workspace)
 OR parent_id=@gov482_check_workspace ORDER BY parent_id,order_num,menu_id;
-- 普通角色授权由管理员按需要分配；这里仅展示已有状态。
SELECT rm.role_id,m.menu_id,m.menu_name,m.perms FROM sys_role_menu rm
JOIN sys_menu m ON m.menu_id=rm.menu_id
WHERE m.menu_id IN (@gov482_check_parent,@gov482_check_workspace) OR m.parent_id=@gov482_check_workspace
ORDER BY rm.role_id,m.menu_id;
