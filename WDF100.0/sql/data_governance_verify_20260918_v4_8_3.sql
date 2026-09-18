-- v4.4.1 -> v4.8.3 升级后只读核验；不会修复或写入任何业务表。
SET NAMES utf8mb4;
SET @gov483_check_parent = NULL, @gov483_check_workspace = NULL;
SET @gov483_check_parent = (SELECT MAX(menu_id) FROM sys_menu WHERE parent_id=0 AND path='governance');
SET @gov483_check_workspace = (SELECT MAX(menu_id) FROM sys_menu WHERE parent_id=@gov483_check_parent AND path='workspace');
SELECT DATABASE() AS checked_database, VERSION() AS mysql_version;
SELECT 'root' AS check_name, 1 AS expected_count, COUNT(*) AS actual_count,
 IF(COUNT(*)=1,'PASS','CHECK_REQUIRED') AS result
FROM sys_menu WHERE parent_id=0 AND path='governance' AND menu_type='M'
UNION ALL
SELECT 'workspace',1,COUNT(*),IF(COUNT(*)=1,'PASS','CHECK_REQUIRED')
FROM sys_menu WHERE parent_id=@gov483_check_parent AND path='workspace' AND menu_type='C'
 AND component='governance/workspace/index' AND perms='governance:flow:list' AND is_frame=1
UNION ALL
SELECT 'four_button_permissions',4,COUNT(*),IF(COUNT(*)=4 AND COUNT(DISTINCT perms)=4,'PASS','CHECK_REQUIRED')
FROM sys_menu WHERE parent_id=@gov483_check_workspace AND menu_type='F' AND path='#'
 AND perms IN ('governance:project:add','governance:flow:add','governance:flow:edit','governance:flow:test')
UNION ALL
SELECT 'active_admin_role_1',1,COUNT(*),IF(COUNT(*)=1,'PASS','CHECK_REQUIRED')
FROM sys_role WHERE role_id=1 AND role_key='admin' AND status='0' AND del_flag='0'
UNION ALL
SELECT 'admin_expected_grants',6,COUNT(*),IF(COUNT(*)=6,'PASS','CHECK_REQUIRED')
FROM sys_role_menu rm JOIN sys_menu m ON m.menu_id=rm.menu_id
WHERE rm.role_id=1 AND (m.menu_id=@gov483_check_parent OR m.menu_id=@gov483_check_workspace OR
 (m.parent_id=@gov483_check_workspace AND m.menu_type='F' AND m.perms IN
 ('governance:project:add','governance:flow:add','governance:flow:edit','governance:flow:test')));

-- 已有隐藏/停用配置不会自动开启：非 AVAILABLE 须由管理员核对是否为有意配置。
SELECT menu_id,menu_name,parent_id,path,component,menu_type,perms,visible,status,
 IF(status='0' AND visible='0','AVAILABLE','PRESERVED_HIDDEN_OR_DISABLED') AS availability
FROM sys_menu WHERE menu_id IN (@gov483_check_parent,@gov483_check_workspace)
 OR parent_id=@gov483_check_workspace ORDER BY parent_id,order_num,menu_id;
-- 普通角色授权由管理员按需要分配；这里仅展示已有状态。
SELECT rm.role_id,m.menu_id,m.menu_name,m.perms FROM sys_role_menu rm
JOIN sys_menu m ON m.menu_id=rm.menu_id
WHERE m.menu_id IN (@gov483_check_parent,@gov483_check_workspace) OR m.parent_id=@gov483_check_workspace
ORDER BY rm.role_id,m.menu_id;

-- v4.8.3 仅改变 max_upload_bytes=0 的应用语义；以下都是只读查询。
SELECT 'document_existing_quota_columns_and_innodb' AS check_name, 1 AS expected_count,
 ((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE()
   AND TABLE_NAME='doc_user_quota' AND COLUMN_NAME IN
   ('user_id','quota_bytes','max_upload_bytes','create_by','create_time','update_by','update_time'))=7
  AND (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE()
       AND TABLE_NAME='doc_user_quota' AND ENGINE='InnoDB')=1) AS actual_count,
 IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE()
     AND TABLE_NAME='doc_user_quota' AND COLUMN_NAME IN
     ('user_id','quota_bytes','max_upload_bytes','create_by','create_time','update_by','update_time'))=7
    AND (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE()
         AND TABLE_NAME='doc_user_quota' AND ENGINE='InnoDB')=1,'PASS','CHECK_REQUIRED') AS result
UNION ALL
SELECT 'document_existing_quota_bigint_non_generated',3,COUNT(*),
 IF(COUNT(*)=3,'PASS','CHECK_REQUIRED')
FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='doc_user_quota'
 AND COLUMN_NAME IN ('user_id','quota_bytes','max_upload_bytes')
 AND DATA_TYPE='bigint' AND IS_NULLABLE='NO' AND EXTRA NOT LIKE '%GENERATED%'
UNION ALL
SELECT 'document_no_custom_check_requires_review',0,COUNT(*),IF(COUNT(*)=0,'PASS','CHECK_REQUIRED')
FROM information_schema.TABLE_CONSTRAINTS WHERE TABLE_SCHEMA=DATABASE()
 AND TABLE_NAME='doc_user_quota' AND CONSTRAINT_TYPE='CHECK'
UNION ALL
SELECT 'document_no_custom_write_trigger_requires_review',0,COUNT(*),IF(COUNT(*)=0,'PASS','CHECK_REQUIRED')
FROM information_schema.TRIGGERS WHERE EVENT_OBJECT_SCHEMA=DATABASE()
 AND EVENT_OBJECT_TABLE='doc_user_quota' AND EVENT_MANIPULATION IN ('INSERT','UPDATE');

-- 缺列/缺表也能输出可读检查结果；不自动建表，不初始化或重置任何额度。
SET @gov483_doc_columns = (
 SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE()
 AND TABLE_NAME='doc_user_quota' AND COLUMN_NAME IN ('quota_bytes','max_upload_bytes'));
SET @gov483_doc_values_query = IF(@gov483_doc_columns=2,
 'SELECT ''document_existing_quota_values_valid'' AS check_name, 0 AS expected_count, COUNT(*) AS actual_count, IF(COUNT(*)=0,''PASS'',''CHECK_REQUIRED'') AS result FROM doc_user_quota WHERE quota_bytes<=0 OR max_upload_bytes<0',
 'SELECT ''document_existing_quota_values_valid'' AS check_name, 0 AS expected_count, NULL AS actual_count, ''CHECK_REQUIRED'' AS result');
PREPARE gov483_doc_values_statement FROM @gov483_doc_values_query;
EXECUTE gov483_doc_values_statement;
DEALLOCATE PREPARE gov483_doc_values_statement;
SET @gov483_doc_summary_query = IF(@gov483_doc_columns=2,
 'SELECT COUNT(*) AS configured_users, COALESCE(SUM(max_upload_bytes=104857600),0) AS upload_100mb_users, COALESCE(SUM(max_upload_bytes=0),0) AS upload_unlimited_users, COALESCE(SUM(max_upload_bytes>0 AND max_upload_bytes<>104857600),0) AS preserved_other_positive_limits FROM doc_user_quota',
 'SELECT ''完整 v4.4.1 文档基线缺失；先审计历史升级，禁止自动补默认额度'' AS document_baseline_issue');
PREPARE gov483_doc_summary_statement FROM @gov483_doc_summary_query;
EXECUTE gov483_doc_summary_statement;
DEALLOCATE PREPARE gov483_doc_summary_statement;

-- 旧列注释可能写“最大100MB”，只是历史说明，不能用注释推断当前应用限制。
SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_DEFAULT, EXTRA, COLUMN_COMMENT
FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='doc_user_quota'
 AND COLUMN_NAME IN ('quota_bytes','max_upload_bytes') ORDER BY ORDINAL_POSITION;
