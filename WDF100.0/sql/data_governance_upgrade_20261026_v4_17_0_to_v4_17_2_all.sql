-- RYNEW v4.17.0 -> v4.17.2 cumulative database delta, prepared 2026-10-26.
-- Requires a complete v4.17.0 platform schema. Choose the target database explicitly.
-- Back up first, pause application writers, and run on a dedicated connection without --force.
-- Sources: data_governance_hdi_core_upgrade_20261013.sql
--          data_governance_hdi_connections_upgrade_20261025.sql
-- Adds 10 dg_hdi_* tables and missing default workspace/counter rows only.
-- Does not update servers, credentials, inspection history, users, menus or role grants.
SET NAMES utf8mb4;
DELIMITER $$
DROP PROCEDURE IF EXISTS rynew_dg_20261026_v4172_guard$$
CREATE PROCEDURE rynew_dg_20261026_v4172_guard()
BEGIN
  IF DATABASE() IS NULL OR DATABASE() IN ('mysql','sys','information_schema','performance_schema') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='STOP: select the intended RYNEW business database';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns
      WHERE table_schema=DATABASE() AND table_name='sup_server_scan_credential'
        AND column_name='password_cipher' AND data_type='varchar'
        AND character_maximum_length=1024 AND is_nullable='NO')
    OR NOT EXISTS (SELECT 1 FROM information_schema.tables
      WHERE table_schema=DATABASE() AND table_name='sup_server_scan_profile' AND engine='InnoDB') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='STOP: complete v4.17.0 schema is required';
  END IF;
  IF EXISTS (SELECT 1 FROM information_schema.triggers WHERE trigger_schema=DATABASE()
      AND event_object_table IN ('dg_hdi_workspace','dg_hdi_counter','dg_hdi_task','dg_hdi_revision','dg_hdi_run','dg_hdi_preference','dg_hdi_schedule','dg_hdi_connection','dg_hdi_connection_revision','dg_hdi_connection_table')) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='STOP: review custom dg_hdi triggers before migration';
  END IF;
  IF EXISTS (
    SELECT 1 FROM (
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
    INNER JOIN information_schema.tables present ON present.table_schema=DATABASE()
      AND present.table_name=expected.table_name
    WHERE present.engine <> 'InnoDB' OR
      (SELECT COUNT(*) FROM information_schema.columns col
       WHERE col.table_schema=DATABASE() AND col.table_name=expected.table_name
         AND FIND_IN_SET(col.column_name,expected.column_names)>0) <> expected.required_count
  ) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='STOP: incompatible existing dg_hdi table; review schema';
  END IF;
END$$
CALL rynew_dg_20261026_v4172_guard()$$
DROP PROCEDURE rynew_dg_20261026_v4172_guard$$
DELIMITER ;

-- Begin verbatim released task-core schema.
-- HDI task-core integration. Platform metadata only; no HDI product database or activation state.
-- Additive and repeatable on the platform's existing MySQL database.
CREATE TABLE IF NOT EXISTS dg_hdi_workspace (
  workspace_id INT NOT NULL AUTO_INCREMENT,
  workspace_key VARCHAR(64) NOT NULL,
  workspace_name VARCHAR(128) NOT NULL,
  created_at DATETIME(3) NOT NULL,
  PRIMARY KEY (workspace_id), UNIQUE KEY uk_dg_hdi_workspace_key (workspace_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
INSERT INTO dg_hdi_workspace(workspace_key, workspace_name, created_at)
SELECT 'platform', '数据流程', CURRENT_TIMESTAMP(3)
WHERE NOT EXISTS (SELECT 1 FROM dg_hdi_workspace WHERE workspace_key='platform');

CREATE TABLE IF NOT EXISTS dg_hdi_counter (
  counter_name VARCHAR(32) NOT NULL,
  next_value BIGINT NOT NULL,
  PRIMARY KEY (counter_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
INSERT INTO dg_hdi_counter(counter_name, next_value)
SELECT 'native_runtime', 100000
WHERE NOT EXISTS (SELECT 1 FROM dg_hdi_counter WHERE counter_name='native_runtime');

CREATE TABLE IF NOT EXISTS dg_hdi_task (
  task_id BIGINT NOT NULL AUTO_INCREMENT,
  workspace_id INT NOT NULL,
  owner_id BIGINT NOT NULL,
  project_code VARCHAR(64) NOT NULL,
  task_name VARCHAR(128) NOT NULL,
  folder_id BIGINT NOT NULL DEFAULT -1,
  revision BIGINT NOT NULL DEFAULT 0,
  published_revision BIGINT NULL,
  state VARCHAR(24) NOT NULL DEFAULT 'DRAFT',
  last_run_id VARCHAR(36) NULL,
  schedule_json MEDIUMTEXT NULL,
  schedule_enabled TINYINT NOT NULL DEFAULT 0,
  next_fire_at DATETIME(3) NULL,
  deleted TINYINT NOT NULL DEFAULT 0,
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  PRIMARY KEY (task_id),
  KEY ix_dg_hdi_task_owner (workspace_id, owner_id, deleted, updated_at),
  KEY ix_dg_hdi_task_schedule (schedule_enabled, next_fire_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS dg_hdi_revision (
  task_id BIGINT NOT NULL,
  revision BIGINT NOT NULL,
  owner_id BIGINT NOT NULL,
  visual_json MEDIUMTEXT NOT NULL,
  definition_sha256 CHAR(64) NOT NULL,
  snapshot_json MEDIUMTEXT NOT NULL,
  snapshot_sha256 CHAR(64) NOT NULL,
  diagnostics_json MEDIUMTEXT NOT NULL,
  created_at DATETIME(3) NOT NULL,
  PRIMARY KEY (task_id, revision)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS dg_hdi_run (
  run_id VARCHAR(36) NOT NULL,
  task_id BIGINT NOT NULL,
  revision BIGINT NOT NULL,
  owner_id BIGINT NOT NULL,
  workspace_id INT NOT NULL,
  native_flow_id BIGINT NOT NULL,
  native_instance_id BIGINT NOT NULL,
  metadata_id_base BIGINT NOT NULL,
  definition_sha256 CHAR(64) NOT NULL,
  mode VARCHAR(16) NOT NULL,
  state VARCHAR(24) NOT NULL,
  trigger_key VARCHAR(128) NULL,
  worker_snapshot_json MEDIUMTEXT NULL,
  error_message VARCHAR(1000) NULL,
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  finished_at DATETIME(3) NULL,
  PRIMARY KEY (run_id),
  UNIQUE KEY uk_dg_hdi_native_flow (native_flow_id),
  UNIQUE KEY uk_dg_hdi_trigger (trigger_key),
  KEY ix_dg_hdi_run_task (task_id, created_at),
  KEY ix_dg_hdi_run_owner (owner_id, state)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS dg_hdi_preference (
  owner_id BIGINT NOT NULL,
  preference_key VARCHAR(64) NOT NULL,
  preference_json MEDIUMTEXT NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  PRIMARY KEY (owner_id, preference_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS dg_hdi_schedule (
  task_id BIGINT NOT NULL,
  owner_id BIGINT NOT NULL,
  revision BIGINT NOT NULL,
  generation BIGINT NOT NULL,
  plan_json MEDIUMTEXT NOT NULL,
  enabled TINYINT NOT NULL DEFAULT 0,
  next_fire_at DATETIME(3) NULL,
  last_outcome VARCHAR(128) NULL,
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  PRIMARY KEY (task_id),
  KEY ix_dg_hdi_schedule_due (enabled, next_fire_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Begin verbatim released connection schema.
-- Platform-owned HDI datasource profiles; never store plaintext credentials in task visuals.
CREATE TABLE IF NOT EXISTS dg_hdi_connection (
  connection_id BIGINT NOT NULL AUTO_INCREMENT,
  owner_id BIGINT NOT NULL,
  revision BIGINT NOT NULL,
  name VARCHAR(128) NOT NULL,
  db_type INT NOT NULL,
  enabled TINYINT NOT NULL DEFAULT 1,
  deleted TINYINT NOT NULL DEFAULT 0,
  test_status INT NOT NULL DEFAULT -1,
  tested_revision BIGINT NULL,
  test_message VARCHAR(512) NULL,
  tested_at DATETIME(3) NULL,
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  PRIMARY KEY (connection_id),
  KEY ix_dg_hdi_connection_owner (owner_id, deleted, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS dg_hdi_connection_revision (
  connection_id BIGINT NOT NULL,
  revision BIGINT NOT NULL,
  owner_id BIGINT NOT NULL,
  datasource_json MEDIUMTEXT NOT NULL,
  encrypted_secret TEXT NOT NULL,
  created_at DATETIME(3) NOT NULL,
  PRIMARY KEY (connection_id, revision),
  KEY ix_dg_hdi_connection_revision_owner (owner_id, connection_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Stable platform identities assigned only to tables/topics actually discovered from an owned connection.
-- Identity is the lowercase SHA-256 of the exact UTF-8 schema/name/kind, separated by NUL bytes.
-- Never replace object_key lookup/uniqueness with schema_name/table_name collation comparisons:
-- PostgreSQL quoted identifiers and Kafka topics remain distinct for Foo/foo on a MySQL ci database.
CREATE TABLE IF NOT EXISTS dg_hdi_connection_table (
  table_id BIGINT NOT NULL AUTO_INCREMENT,
  connection_id BIGINT NOT NULL,
  owner_id BIGINT NOT NULL,
  object_key CHAR(64) NOT NULL,
  schema_name VARCHAR(128) NOT NULL,
  table_name VARCHAR(256) NOT NULL,
  table_kind VARCHAR(16) NOT NULL,
  created_at DATETIME(3) NOT NULL,
  PRIMARY KEY (table_id),
  UNIQUE KEY uk_dg_hdi_connection_object (connection_id, owner_id, object_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Structural presence only. Run the companion read-only schema check before and after this file.
SELECT 'v4.17.0_to_v4.17.2' AS upgrade_range, DATABASE() AS database_name,
       COUNT(*) AS dg_hdi_tables_present
FROM information_schema.tables
WHERE table_schema=DATABASE() AND table_name IN ('dg_hdi_workspace','dg_hdi_counter','dg_hdi_task','dg_hdi_revision','dg_hdi_run','dg_hdi_preference','dg_hdi_schedule','dg_hdi_connection','dg_hdi_connection_revision','dg_hdi_connection_table');
