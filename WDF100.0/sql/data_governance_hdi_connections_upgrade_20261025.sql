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
