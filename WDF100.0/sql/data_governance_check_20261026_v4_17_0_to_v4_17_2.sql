-- Read-only pre/post verification for a complete v4.17.0 -> v4.17.2 platform upgrade.
-- Existing target tables must be compared with the CREATE statements for types/defaults/indexes.
-- PASS here does not replace a backup or validate external HDI runtime deployment.
SET NAMES utf8mb4;
SELECT DATABASE() AS database_name, VERSION() AS mysql_version,
 CASE WHEN DATABASE() IS NULL OR DATABASE() IN ('mysql','sys','information_schema','performance_schema')
 THEN 'STOP: select business database' ELSE 'REVIEW: confirm intended database' END AS result;

SELECT 'v4.17.0_password_pool' AS check_name,
 CASE WHEN EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
   AND table_name='sup_server_scan_credential' AND column_name='password_cipher'
   AND data_type='varchar' AND character_maximum_length=1024 AND is_nullable='NO')
   AND EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema=DATABASE()
     AND table_name='sup_server_scan_profile' AND engine='InnoDB')
 THEN 'PASS' ELSE 'STOP: v4.17.0 baseline missing or incompatible' END AS result;

SELECT expected.table_name,
 CASE WHEN present.table_name IS NULL THEN 'NOT_APPLIED'
      WHEN present.engine <> 'InnoDB' THEN 'STOP: incompatible engine'
      WHEN (SELECT COUNT(*) FROM information_schema.columns col WHERE col.table_schema=DATABASE()
        AND col.table_name=expected.table_name AND FIND_IN_SET(col.column_name,expected.column_names)>0) <> expected.required_count
      THEN 'STOP: required columns missing'
      ELSE 'PRESENT: review definitions below' END AS result
FROM (
      SELECT 'dg_hdi_workspace' AS table_name, 'workspace_id,workspace_key,workspace_name,created_at' AS column_names, 4 AS required_count
      UNION ALL SELECT 'dg_hdi_counter' AS table_name, 'counter_name,next_value' AS column_names, 2 AS required_count
      UNION ALL SELECT 'dg_hdi_task' AS table_name, 'task_id,workspace_id,owner_id,project_code,task_name,folder_id,revision,published_revision,state,last_run_id,schedule_json,schedule_enabled,next_fire_at,deleted,created_at,updated_at' AS column_names, 16 AS required_count
      UNION ALL SELECT 'dg_hdi_revision' AS table_name, 'task_id,revision,owner_id,visual_json,definition_sha256,snapshot_json,snapshot_sha256,diagnostics_json,created_at' AS column_names, 9 AS required_count
      UNION ALL SELECT 'dg_hdi_run' AS table_name, 'run_id,task_id,revision,owner_id,workspace_id,native_flow_id,native_instance_id,metadata_id_base,definition_sha256,mode,state,trigger_key,worker_snapshot_json,error_message,created_at,updated_at,finished_at' AS column_names, 17 AS required_count
      UNION ALL SELECT 'dg_hdi_preference' AS table_name, 'owner_id,preference_key,preference_json,updated_at' AS column_names, 4 AS required_count
      UNION ALL SELECT 'dg_hdi_schedule' AS table_name, 'task_id,owner_id,revision,generation,plan_json,enabled,next_fire_at,last_outcome,created_at,updated_at' AS column_names, 10 AS required_count
      UNION ALL SELECT 'dg_hdi_connection' AS table_name, 'connection_id,owner_id,revision,name,db_type,enabled,deleted,test_status,tested_revision,test_message,tested_at,created_at,updated_at' AS column_names, 13 AS required_count
      UNION ALL SELECT 'dg_hdi_connection_revision' AS table_name, 'connection_id,revision,owner_id,datasource_json,encrypted_secret,created_at' AS column_names, 6 AS required_count
      UNION ALL SELECT 'dg_hdi_connection_table' AS table_name, 'table_id,connection_id,owner_id,object_key,schema_name,table_name,table_kind,created_at' AS column_names, 8 AS required_count
) expected
LEFT JOIN information_schema.tables present ON present.table_schema=DATABASE()
  AND present.table_name=expected.table_name
ORDER BY expected.table_name;

SELECT table_name,column_name,column_type,is_nullable,
 CASE WHEN column_name LIKE '%secret%' OR column_name LIKE '%cipher%' OR column_name LIKE '%password%'
      THEN CASE WHEN column_default IS NULL THEN 'NULL' ELSE '[REDACTED]' END
      ELSE COALESCE(column_default,'NULL') END AS column_default,extra
FROM information_schema.columns
WHERE table_schema=DATABASE() AND table_name IN ('dg_hdi_workspace','dg_hdi_counter','dg_hdi_task','dg_hdi_revision','dg_hdi_run','dg_hdi_preference','dg_hdi_schedule','dg_hdi_connection','dg_hdi_connection_revision','dg_hdi_connection_table')
ORDER BY table_name,ordinal_position;

SELECT table_name,index_name,non_unique,seq_in_index,column_name,sub_part
FROM information_schema.statistics
WHERE table_schema=DATABASE() AND table_name IN ('dg_hdi_workspace','dg_hdi_counter','dg_hdi_task','dg_hdi_revision','dg_hdi_run','dg_hdi_preference','dg_hdi_schedule','dg_hdi_connection','dg_hdi_connection_revision','dg_hdi_connection_table')
ORDER BY table_name,index_name,seq_in_index;

SELECT event_object_table,trigger_name,event_manipulation,action_timing,
 'STOP: review custom trigger' AS result
FROM information_schema.triggers
WHERE trigger_schema=DATABASE() AND event_object_table IN ('dg_hdi_workspace','dg_hdi_counter','dg_hdi_task','dg_hdi_revision','dg_hdi_run','dg_hdi_preference','dg_hdi_schedule','dg_hdi_connection','dg_hdi_connection_revision','dg_hdi_connection_table');
