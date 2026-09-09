-- 数据治理 v4.5.0：独立菜单与权限。执行前备份 sys_menu/sys_role_menu。
-- 不改变现有巡检表、任务或数据源；NiFi及开发测试参数由部署环境单独提供。
SET NAMES utf8mb4;
START TRANSACTION;

INSERT INTO sys_menu (menu_name,parent_id,order_num,path,component,route_name,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time,remark)
SELECT '数据治理',0,6,'governance',NULL,'DataGovernance',1,1,'M','0','0','','tree','admin',NOW(),'独立数据治理模块'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE parent_id=0 AND path='governance');
SET @gov_parent = (SELECT menu_id FROM sys_menu WHERE parent_id=0 AND path='governance' ORDER BY menu_id LIMIT 1);

INSERT INTO sys_menu (menu_name,parent_id,order_num,path,component,route_name,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time,remark)
SELECT '治理工作台',@gov_parent,1,'workspace','governance/workspace/index','DataGovernanceWorkspace',1,1,'C','0','0','governance:flow:list','job','admin',NOW(),'浏览器设计入口、组件目录与隔离样本测试'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE parent_id=@gov_parent AND path='workspace');
SET @gov_workspace = (SELECT menu_id FROM sys_menu WHERE parent_id=@gov_parent AND path='workspace' ORDER BY menu_id LIMIT 1);

INSERT INTO sys_menu (menu_name,parent_id,order_num,path,menu_type,visible,status,perms,icon,create_by,create_time)
SELECT permissions.title,@gov_workspace,permissions.sort_no,'#','F','0','0',permissions.permission,'#','admin',NOW()
FROM (
 SELECT '项目新增' title,1 sort_no,'governance:project:add' permission
 UNION ALL SELECT '流程新增',2,'governance:flow:add'
 UNION ALL SELECT '流程修改',3,'governance:flow:edit'
 UNION ALL SELECT '隔离样本测试',4,'governance:flow:test'
) permissions
WHERE NOT EXISTS (SELECT 1 FROM sys_menu existing WHERE existing.parent_id=@gov_workspace AND existing.perms=permissions.permission);

INSERT INTO sys_role_menu(role_id,menu_id)
SELECT 1,menu_id FROM sys_menu m WHERE (m.menu_id=@gov_parent OR m.menu_id=@gov_workspace OR m.parent_id=@gov_workspace)
AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id=1 AND rm.menu_id=m.menu_id);
COMMIT;
