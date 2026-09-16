-- RYNEW v4.4.1 -> v4.8.2 累计数据库升级（MySQL 5.7+/8.x语法；实测版本见说明）
-- 基线 e19796b；应用目标 3a12fd9。区间内唯一数据库增量为 v4.5.0 数据治理菜单/权限。
-- 来源 data_governance_upgrade_20260909_v4_5_0.sql；增加冲突预检并限定管理员授权六行。
-- 不运行历史数据迁移，不创建业务表，不更改已有菜单/角色，不写 sys_job。
-- 先备份 sys_menu/sys_role_menu/sys_role，使用 mysql 新连接 --database 指定目标库。
-- 禁止 --force/忽略错误/选段执行；报错须断开连接以回滚，并阅读同目录配套说明。
SET NAMES utf8mb4;
SET @gov482_ready = 0, @gov482_schema_ok = 0, @gov482_parent = NULL,
    @gov482_workspace = NULL, @gov482_lock = 0;
SET @gov482_lock_name = CONCAT('rynew:gov:v482:', MD5(DATABASE()));
SET @gov482_lock = IF(DATABASE() IS NULL, 0, GET_LOCK(@gov482_lock_name, 10));

CREATE TEMPORARY TABLE _rynew_gov482_checks (
  check_name VARCHAR(100) NOT NULL PRIMARY KEY, passed TINYINT NOT NULL
);
CREATE TEMPORARY TABLE _rynew_gov482_stop_on_conflict (stop_if_precheck_failed INT PRIMARY KEY);
INSERT INTO _rynew_gov482_stop_on_conflict VALUES (1);

-- 只支持已完成 v4.4.1 基础结构的库；不尝试初始化、覆盖或自动修复其他版本。
SET @gov482_schema_ok = (
  (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND (
    (TABLE_NAME='sys_menu' AND COLUMN_NAME IN ('menu_id','menu_name','parent_id','order_num','path','component','route_name','is_frame','is_cache','menu_type','visible','status','perms','icon','create_by','create_time','remark')) OR
    (TABLE_NAME='sys_role' AND COLUMN_NAME IN ('role_id','role_key','status','del_flag')) OR
    (TABLE_NAME='sys_role_menu' AND COLUMN_NAME IN ('role_id','menu_id')))) = 23
  AND (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE()
       AND TABLE_NAME IN ('sys_menu','sys_role_menu') AND ENGINE='InnoDB') = 2
  AND (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE()
       AND TABLE_NAME='sys_menu' AND COLUMN_NAME='menu_id' AND EXTRA LIKE '%auto_increment%') = 1
);
INSERT INTO _rynew_gov482_checks VALUES
 ('database_selected', IF(DATABASE() IS NOT NULL,1,0)),
 ('exclusive_upgrade_lock', IF(@gov482_lock=1,1,0)),
 ('base_columns_innodb_and_auto_increment', COALESCE(@gov482_schema_ok,0));
SELECT check_name, IF(passed=1,'PASS','BLOCKED') AS result FROM _rynew_gov482_checks;
-- 重复主键使默认 mysql 批处理立即失败；此时尚未写入任何永久表。
INSERT INTO _rynew_gov482_stop_on_conflict
SELECT 1 WHERE EXISTS (SELECT 1 FROM _rynew_gov482_checks WHERE passed<>1);

START TRANSACTION;
SET @gov482_parent = (SELECT MAX(menu_id) FROM sys_menu WHERE parent_id=0 AND path='governance');
SET @gov482_workspace = (SELECT MAX(menu_id) FROM sys_menu WHERE parent_id=@gov482_parent AND path='workspace');
INSERT INTO _rynew_gov482_checks
SELECT 'active_admin_role_1', IF(COUNT(*)=1,1,0) FROM sys_role
WHERE role_id=1 AND role_key='admin' AND status='0' AND del_flag='0';
INSERT INTO _rynew_gov482_checks
SELECT 'unique_governance_root', IF(COUNT(*)<=1,1,0) FROM sys_menu WHERE parent_id=0 AND path='governance';
INSERT INTO _rynew_gov482_checks
SELECT 'root_identity', IF(COUNT(*)=0,1,0) FROM sys_menu WHERE menu_id=@gov482_parent
AND NOT (menu_type <=> 'M' AND is_frame <=> 1 AND COALESCE(component,'')=''
         AND COALESCE(perms,'')='' AND COALESCE(route_name,'') IN ('','DataGovernance'));
INSERT INTO _rynew_gov482_checks
SELECT 'unique_workspace', IF(COUNT(*)<=1,1,0) FROM sys_menu WHERE parent_id=@gov482_parent AND path='workspace';
INSERT INTO _rynew_gov482_checks
SELECT 'workspace_identity', IF(COUNT(*)=0,1,0) FROM sys_menu WHERE menu_id=@gov482_workspace
AND NOT (menu_type <=> 'C' AND is_frame <=> 1 AND component <=> 'governance/workspace/index'
         AND perms <=> 'governance:flow:list' AND COALESCE(route_name,'') IN ('','DataGovernanceWorkspace'));
INSERT INTO _rynew_gov482_checks
SELECT 'route_names_not_occupied', IF(COUNT(*)=0,1,0) FROM sys_menu
WHERE (route_name='DataGovernance' AND NOT (menu_id <=> @gov482_parent))
   OR (route_name='DataGovernanceWorkspace' AND NOT (menu_id <=> @gov482_workspace));
INSERT INTO _rynew_gov482_checks
SELECT 'list_permission_location', IF(COUNT(*)=0,1,0) FROM sys_menu
WHERE perms='governance:flow:list' AND NOT (menu_id <=> @gov482_workspace);
INSERT INTO _rynew_gov482_checks
SELECT 'button_permission_locations', IF(COUNT(*)=0,1,0) FROM sys_menu
WHERE perms IN ('governance:project:add','governance:flow:add','governance:flow:edit','governance:flow:test')
AND NOT (parent_id <=> @gov482_workspace AND menu_type <=> 'F' AND path <=> '#');
INSERT INTO _rynew_gov482_checks
SELECT 'button_permissions_unique', IF(COUNT(*)=0,1,0) FROM (
 SELECT perms FROM sys_menu WHERE perms IN ('governance:project:add','governance:flow:add','governance:flow:edit','governance:flow:test')
 GROUP BY perms HAVING COUNT(*)>1
) duplicate_permissions;
SET @gov482_ready = IF(@gov482_schema_ok=1 AND @gov482_lock=1
  AND (SELECT COUNT(*)=12 AND MIN(passed)=1 FROM _rynew_gov482_checks),1,0);
SELECT check_name, IF(passed=1,'PASS','BLOCKED') AS result FROM _rynew_gov482_checks ORDER BY check_name;
INSERT INTO _rynew_gov482_stop_on_conflict SELECT 1 WHERE COALESCE(@gov482_ready,0)<>1;

INSERT INTO sys_menu (menu_name,parent_id,order_num,path,component,route_name,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time,remark)
SELECT '数据治理',0,6,'governance',NULL,'DataGovernance',1,1,'M','0','0','','tree','admin',NOW(),'独立数据治理模块'
WHERE @gov482_ready=1 AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE parent_id=0 AND path='governance');
SET @gov482_parent = (SELECT MAX(menu_id) FROM sys_menu WHERE parent_id=0 AND path='governance');

INSERT INTO sys_menu (menu_name,parent_id,order_num,path,component,route_name,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time,remark)
SELECT '治理工作台',@gov482_parent,1,'workspace','governance/workspace/index','DataGovernanceWorkspace',1,1,'C','0','0','governance:flow:list','job','admin',NOW(),'浏览器设计入口、组件目录与隔离样本测试'
WHERE @gov482_ready=1 AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE parent_id=@gov482_parent AND path='workspace');
SET @gov482_workspace = (SELECT MAX(menu_id) FROM sys_menu WHERE parent_id=@gov482_parent AND path='workspace');

INSERT INTO sys_menu (menu_name,parent_id,order_num,path,menu_type,visible,status,perms,icon,create_by,create_time)
SELECT permissions.title,@gov482_workspace,permissions.sort_no,'#','F','0','0',permissions.permission,'#','admin',NOW()
FROM (
 SELECT '项目新增' title,1 sort_no,'governance:project:add' permission
 UNION ALL SELECT '流程新增',2,'governance:flow:add'
 UNION ALL SELECT '流程修改',3,'governance:flow:edit'
 UNION ALL SELECT '隔离样本测试',4,'governance:flow:test'
) permissions
WHERE @gov482_ready=1 AND NOT EXISTS (
 SELECT 1 FROM sys_menu existing WHERE existing.parent_id=@gov482_workspace AND existing.perms=permissions.permission
);

-- 仅授予本次明确的2个入口和4个按钮，保留全部其他角色授权，不授予未知的自定义子菜单。
INSERT INTO sys_role_menu(role_id,menu_id)
SELECT 1,m.menu_id FROM sys_menu m WHERE @gov482_ready=1 AND
 (m.menu_id=@gov482_parent OR m.menu_id=@gov482_workspace OR
  (m.parent_id=@gov482_workspace AND m.menu_type='F' AND m.perms IN
   ('governance:project:add','governance:flow:add','governance:flow:edit','governance:flow:test')))
AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id=1 AND rm.menu_id=m.menu_id);
COMMIT;
SELECT IF(@gov482_ready=1,'UPGRADE_OK','UPGRADE_BLOCKED') AS upgrade_result,
       DATABASE() AS database_name, @gov482_parent AS governance_menu_id, @gov482_workspace AS workspace_menu_id;
SELECT RELEASE_LOCK(@gov482_lock_name) AS upgrade_lock_released;
DROP TEMPORARY TABLE _rynew_gov482_checks;
DROP TEMPORARY TABLE _rynew_gov482_stop_on_conflict;
