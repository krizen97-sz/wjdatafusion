-- Read-only pre/post checks for complete v4.16.0 -> v4.17.0.
-- Reports metadata and aggregate counts only; no password/ciphertext/key values.
-- This is a report, not an automatic execution gate. Stop on STOP or SQL errors.
-- NOT_APPLIED is allowed before the first upgrade only.
SET NAMES utf8mb4;
SELECT DATABASE() AS target_database, VERSION() AS mysql_version,
  CASE WHEN DATABASE() IS NULL THEN 'STOP: select database' ELSE 'PASS' END AS database_check;
SELECT required.table_name, actual.engine,
  CASE WHEN actual.table_name IS NULL THEN 'STOP: incomplete v4.16.0 baseline'
    WHEN actual.engine <> 'InnoDB' THEN 'STOP: review storage engine' ELSE 'PASS' END AS result
FROM (SELECT 'sup_site' table_name UNION ALL SELECT 'sup_server' UNION ALL SELECT 'sup_server_credential'
 UNION ALL SELECT 'sup_server_scan_credential' UNION ALL SELECT 'sup_server_scan_profile'
 UNION ALL SELECT 'sys_menu' UNION ALL SELECT 'sys_role_menu') required
LEFT JOIN information_schema.tables actual ON actual.table_schema=DATABASE()
 AND actual.table_name=required.table_name AND actual.table_type='BASE TABLE';

SELECT required.table_name,required.column_name,actual.column_type,actual.is_nullable,
 CASE WHEN required.column_name IN ('hik_password_cipher','root_password_cipher')
 THEN CASE WHEN actual.column_default IS NULL THEN 'NULL' WHEN actual.column_default='' THEN 'EMPTY' ELSE 'NONEMPTY_REDACTED' END
 ELSE actual.column_default END AS reported_default,
 CASE WHEN actual.column_name IS NULL THEN 'STOP: missing baseline column'
 WHEN actual.data_type<>required.data_type OR actual.is_nullable<>required.nullable
   OR (required.max_length>0 AND COALESCE(actual.character_maximum_length,0)<>required.max_length)
   OR actual.column_type LIKE '%unsigned%' OR COALESCE(actual.generation_expression,'')<>''
 THEN 'STOP: incompatible baseline column' ELSE 'PASS' END AS result
FROM (
 SELECT 'sup_server_scan_credential' table_name,'credential_id' column_name,'bigint' data_type,0 max_length,'NO' nullable
 UNION ALL SELECT 'sup_server_scan_credential','site_id','bigint',0,'NO'
 UNION ALL SELECT 'sup_server_scan_credential','credential_name','varchar',80,'NO'
 UNION ALL SELECT 'sup_server_scan_credential','login_mode','varchar',20,'NO'
 UNION ALL SELECT 'sup_server_scan_credential','hik_password_cipher','varchar',1024,'NO'
 UNION ALL SELECT 'sup_server_scan_credential','root_password_cipher','varchar',1024,'NO'
 UNION ALL SELECT 'sup_server_scan_credential','create_by','varchar',64,'YES'
 UNION ALL SELECT 'sup_server_scan_credential','create_time','datetime',0,'YES'
 UNION ALL SELECT 'sup_server_scan_credential','update_by','varchar',64,'YES'
 UNION ALL SELECT 'sup_server_scan_credential','update_time','datetime',0,'YES'
 UNION ALL SELECT 'sup_server_scan_profile','server_id','bigint',0,'NO'
 UNION ALL SELECT 'sup_server_scan_profile','site_id','bigint',0,'NO'
 UNION ALL SELECT 'sup_server_scan_profile','host_name','varchar',255,'NO'
 UNION ALL SELECT 'sup_server_scan_profile','os_name','varchar',120,'NO'
 UNION ALL SELECT 'sup_server_scan_profile','os_version','varchar',120,'NO'
 UNION ALL SELECT 'sup_server_scan_profile','kernel_version','varchar',120,'NO'
 UNION ALL SELECT 'sup_server_scan_profile','detected_ports','varchar',64,'NO'
 UNION ALL SELECT 'sup_server_scan_profile','scan_time','datetime',0,'YES'
 UNION ALL SELECT 'sup_server','root_direct_login','tinyint',0,'NO'
) required LEFT JOIN information_schema.columns actual ON actual.table_schema=DATABASE()
 AND actual.table_name=required.table_name AND actual.column_name=required.column_name;

SELECT column_name, column_type,
 CASE WHEN column_name IN ('hik_password_cipher','root_password_cipher')
 THEN CASE WHEN column_default IS NULL THEN 'NULL' WHEN column_default='' THEN 'EMPTY' ELSE 'NONEMPTY_REDACTED' END
 ELSE column_default END AS reported_default,extra,
 CASE WHEN column_name='credential_id' AND column_key='PRI' AND extra LIKE '%auto_increment%' THEN 'PASS'
 WHEN column_name IN ('hik_password_cipher','root_password_cipher') AND column_default='' THEN 'PASS'
 ELSE 'STOP: incompatible pool key or legacy cipher default' END AS result
FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='sup_server_scan_credential'
 AND column_name IN ('credential_id','hik_password_cipher','root_password_cipher');
SELECT required.table_name,required.index_name,
 CASE WHEN COUNT(actual.column_name)=1 AND MAX(actual.column_name)=required.column_name
 AND MAX(actual.non_unique)=required.non_unique THEN 'PASS' ELSE 'STOP: missing or incompatible index' END AS result
FROM (
 SELECT 'sup_server_scan_credential' table_name,'PRIMARY' index_name,'credential_id' column_name,0 non_unique
 UNION ALL SELECT 'sup_server_scan_credential','idx_server_scan_credential_site','site_id',1
 UNION ALL SELECT 'sup_server_scan_profile','PRIMARY','server_id',0
 UNION ALL SELECT 'sup_server_scan_profile','idx_server_scan_profile_site','site_id',1
) required LEFT JOIN information_schema.statistics actual ON actual.table_schema=DATABASE()
 AND actual.table_name=required.table_name AND actual.index_name=required.index_name
GROUP BY required.table_name,required.index_name,required.column_name,required.non_unique;
SELECT required.table_name,
 CASE WHEN COUNT(k.column_name)=1 AND MAX(k.column_name)=required.column_name
 AND MAX(k.referenced_table_name)=required.parent_table AND MAX(k.referenced_column_name)=required.parent_column
 AND MAX(r.delete_rule)='CASCADE' THEN 'PASS' ELSE 'STOP: incompatible foreign key' END AS result
FROM (
 SELECT 'sup_server_scan_credential' table_name,'fk_server_scan_credential_site' constraint_name,
 'site_id' column_name,'sup_site' parent_table,'site_id' parent_column
 UNION ALL SELECT 'sup_server_scan_profile','fk_server_scan_profile_server','server_id','sup_server','server_id'
) required LEFT JOIN information_schema.key_column_usage k ON k.table_schema=DATABASE()
 AND k.table_name=required.table_name AND k.constraint_name=required.constraint_name
LEFT JOIN information_schema.referential_constraints r ON r.constraint_schema=k.constraint_schema
 AND r.table_name=k.table_name AND r.constraint_name=k.constraint_name
GROUP BY required.table_name,required.column_name,required.parent_table,required.parent_column;
SELECT 'pool_custom_triggers' AS check_item,COUNT(*) AS found,
 IF(COUNT(*)=0,'PASS','STOP: review custom triggers') AS result FROM information_schema.triggers
WHERE trigger_schema=DATABASE() AND event_object_table='sup_server_scan_credential';
SELECT 'scan_permission' AS check_item,COUNT(*) AS found,
 CASE WHEN COUNT(*)=1 AND SUM(CASE WHEN m.menu_type='F' AND m.status='0' AND m.visible='0'
 AND m.parent_id IN (SELECT menu_id FROM sys_menu WHERE perms='support:server:list' AND menu_type='C')
 THEN 1 ELSE 0 END)=1 THEN 'PASS' ELSE 'STOP: missing or incompatible v4.16 scan permission' END AS result
FROM sys_menu m WHERE m.perms='support:server:scan';

SET @pool_v4170_column_count=(SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE()
 AND table_name='sup_server_scan_credential' AND column_name='password_cipher');
SELECT 'password_cipher' AS check_item,
 CASE WHEN @pool_v4170_column_count=0 THEN 'NOT_APPLIED'
 WHEN EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
 AND table_name='sup_server_scan_credential' AND column_name='password_cipher'
 AND data_type='varchar' AND character_maximum_length=1024 AND is_nullable='NO'
 AND column_default='' AND COALESCE(generation_expression,'')='') THEN 'PASS'
 ELSE 'STOP: incompatible password_cipher' END AS result;
SET @pool_v4170_base_columns=(SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE()
 AND table_name='sup_server_scan_credential' AND column_name IN ('login_mode','root_password_cipher','hik_password_cipher'));
-- Dynamic read-only SELECT avoids referring to password_cipher before it is added.
SET @pool_v4170_query=IF(@pool_v4170_base_columns<>3,
 'SELECT ''STOP: missing legacy columns, data counts unavailable'' AS result',
 IF(@pool_v4170_column_count=0,
 'SELECT COUNT(*) AS pool_rows_before, COALESCE(SUM(login_mode=''HIK_SU_ROOT''),0) AS pairs_before, COUNT(*)+COALESCE(SUM(login_mode=''HIK_SU_ROOT''),0) AS first_upgrade_expected_rows, COALESCE(SUM(login_mode NOT IN (''ROOT_DIRECT'',''HIK_SU_ROOT'') OR root_password_cipher='''' OR (login_mode=''HIK_SU_ROOT'' AND hik_password_cipher='''')),0) AS invalid_legacy_rows FROM sup_server_scan_credential',
 'SELECT COUNT(*) AS pool_rows_now, COALESCE(SUM(password_cipher=''''),0) AS pending_rows, COALESCE(SUM(password_cipher='''' AND login_mode=''HIK_SU_ROOT''),0) AS pending_pairs, CASE WHEN COALESCE(SUM(password_cipher=''''),0)=0 THEN ''PASS'' ELSE ''STOP: unconverted rows remain'' END AS result FROM sup_server_scan_credential'));
PREPARE pool_v4170_check FROM @pool_v4170_query;
EXECUTE pool_v4170_check;
DEALLOCATE PREPARE pool_v4170_check;
-- On first upgrade, compare pool_rows_now to first_upgrade_expected_rows saved before migration.
-- Never apply that historical formula to an already edited v4.17 pool. Do not print cipher values.
SELECT 'sup_server' AS counted_table,COUNT(*) AS row_count FROM sup_server
UNION ALL SELECT 'sup_server_credential',COUNT(*) FROM sup_server_credential
UNION ALL SELECT 'sup_server_scan_profile',COUNT(*) FROM sup_server_scan_profile
UNION ALL SELECT 'sys_menu',COUNT(*) FROM sys_menu
UNION ALL SELECT 'sys_role_menu',COUNT(*) FROM sys_role_menu;
