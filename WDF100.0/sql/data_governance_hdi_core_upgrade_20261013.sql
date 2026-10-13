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
