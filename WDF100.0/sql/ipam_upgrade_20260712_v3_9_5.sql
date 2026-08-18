-- v3.9.5 IPAM 历史 Excel 一次性迁移支撑结构
-- 说明：
-- 1. 本脚本只创建或修改 ipam_ 独立业务表，不修改 sup_* 现场融合业务表。
-- 2. 历史 Excel 只迁移一次；本脚本不提供上传、解析或重复导入功能。
-- 3. ipam_address 保存平台正式台账，原始行、冲突与映射关系分别归档，确保可追溯和可回滚。

CREATE TABLE IF NOT EXISTS ipam_setting (
  setting_key    VARCHAR(64)  NOT NULL COMMENT '设置键',
  setting_value  VARCHAR(128) NOT NULL COMMENT '设置值',
  setting_name   VARCHAR(100) NOT NULL COMMENT '设置名称',
  create_by      VARCHAR(64)  DEFAULT '' COMMENT '创建者',
  create_time    DATETIME     DEFAULT NULL COMMENT '创建时间',
  update_by      VARCHAR(64)  DEFAULT '' COMMENT '更新者',
  update_time    DATETIME     DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (setting_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='IPAM应用设置';

CREATE TABLE IF NOT EXISTS ipam_migration_batch (
  batch_id            BIGINT(20)   NOT NULL AUTO_INCREMENT COMMENT '迁移批次ID',
  batch_code          VARCHAR(80)  NOT NULL COMMENT '迁移批次编码',
  source_file_name    VARCHAR(255) NOT NULL COMMENT '源文件名',
  source_file_sha256  CHAR(64)     NOT NULL COMMENT '源文件SHA-256',
  backup_prefix       VARCHAR(100) DEFAULT NULL COMMENT '迁移前备份表前缀',
  migration_status    VARCHAR(20)  NOT NULL COMMENT '状态（PREPARED RUNNING COMPLETED FAILED ROLLED_BACK）',
  source_row_count    INT          NOT NULL DEFAULT 0 COMMENT '源文件非空行数',
  network_count       INT          NOT NULL DEFAULT 0 COMMENT '规范化网段数',
  site_count          INT          NOT NULL DEFAULT 0 COMMENT '规范化小区数',
  address_count       INT          NOT NULL DEFAULT 0 COMMENT '正式地址数',
  mapping_count       INT          NOT NULL DEFAULT 0 COMMENT '映射关系数',
  conflict_count      INT          NOT NULL DEFAULT 0 COMMENT '冲突数',
  summary_json        JSON         DEFAULT NULL COMMENT '迁移汇总',
  error_message       VARCHAR(1000) DEFAULT NULL COMMENT '失败原因',
  started_time        DATETIME     DEFAULT NULL COMMENT '开始时间',
  completed_time      DATETIME     DEFAULT NULL COMMENT '完成时间',
  create_by           VARCHAR(64)  DEFAULT '' COMMENT '创建者',
  create_time         DATETIME     DEFAULT NULL COMMENT '创建时间',
  update_by           VARCHAR(64)  DEFAULT '' COMMENT '更新者',
  update_time         DATETIME     DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (batch_id),
  UNIQUE KEY uk_ipam_migration_batch_code (batch_code),
  KEY idx_ipam_migration_batch_hash (source_file_sha256),
  KEY idx_ipam_migration_batch_status (migration_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='IPAM一次性迁移批次';

CREATE TABLE IF NOT EXISTS ipam_site (
  site_id                    BIGINT(20)    NOT NULL AUTO_INCREMENT COMMENT '小区资料ID',
  migration_batch_id         BIGINT(20)    DEFAULT NULL COMMENT '迁移批次ID',
  area_name                  VARCHAR(80)   NOT NULL DEFAULT '' COMMENT '区域名称',
  site_name                  VARCHAR(160)  NOT NULL COMMENT '小区名称',
  scenario_type              VARCHAR(20)   NOT NULL DEFAULT 'SOCIAL' COMMENT '使用场景',
  longitude                  DECIMAL(11,7) DEFAULT NULL COMMENT '经度',
  latitude                   DECIMAL(10,7) DEFAULT NULL COMMENT '纬度',
  access_unit                VARCHAR(120)  DEFAULT NULL COMMENT '接入单位',
  contact_name               VARCHAR(80)   DEFAULT NULL COMMENT '联系人',
  contact_phone              VARCHAR(40)   DEFAULT NULL COMMENT '联系电话',
  access_control_brand       VARCHAR(80)   DEFAULT NULL COMMENT '门禁品牌',
  barrier_gate_brand         VARCHAR(80)   DEFAULT NULL COMMENT '道闸品牌',
  access_control_mapping_ip  VARCHAR(128)  DEFAULT NULL COMMENT '门禁映射地址',
  barrier_gate_mapping_ip    VARCHAR(128)  DEFAULT NULL COMMENT '道闸映射地址',
  access_status              VARCHAR(100)  DEFAULT NULL COMMENT '接入状态',
  source_sheet               VARCHAR(120)  DEFAULT NULL COMMENT '首个来源工作表',
  source_row                 INT           DEFAULT NULL COMMENT '首个来源行号',
  source_refs_json           JSON          DEFAULT NULL COMMENT '全部来源位置',
  create_by                  VARCHAR(64)   DEFAULT '' COMMENT '创建者',
  create_time                DATETIME      DEFAULT NULL COMMENT '创建时间',
  update_by                  VARCHAR(64)   DEFAULT '' COMMENT '更新者',
  update_time                DATETIME      DEFAULT NULL COMMENT '更新时间',
  remark                     VARCHAR(500)  DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (site_id),
  UNIQUE KEY uk_ipam_site_area_name (area_name, site_name),
  KEY idx_ipam_site_migration_batch (migration_batch_id),
  KEY idx_ipam_site_name (site_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='IPAM小区资料';

CREATE TABLE IF NOT EXISTS ipam_migration_source_row (
  source_id          BIGINT(20)   NOT NULL AUTO_INCREMENT COMMENT '原始行ID',
  batch_id           BIGINT(20)   NOT NULL COMMENT '迁移批次ID',
  sheet_name         VARCHAR(120) NOT NULL COMMENT '工作表名称',
  source_row_number  INT          NOT NULL COMMENT 'Excel行号',
  raw_json           JSON         NOT NULL COMMENT '原始单元格值',
  detected_ips_json  JSON         DEFAULT NULL COMMENT '识别到的IP地址',
  archive_status     VARCHAR(30)  NOT NULL DEFAULT 'ARCHIVED' COMMENT '归档状态',
  create_time        DATETIME     DEFAULT NULL COMMENT '创建时间',
  PRIMARY KEY (source_id),
  UNIQUE KEY uk_ipam_source_batch_row (batch_id, sheet_name, source_row_number),
  KEY idx_ipam_source_batch (batch_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='IPAM迁移原始行归档';

CREATE TABLE IF NOT EXISTS ipam_mapping_relation (
  mapping_id          BIGINT(20)    NOT NULL AUTO_INCREMENT COMMENT '映射关系ID',
  migration_batch_id  BIGINT(20)    NOT NULL COMMENT '迁移批次ID',
  relation_key        VARCHAR(180)   NOT NULL COMMENT '批次内关系唯一键',
  site_id             BIGINT(20)    DEFAULT NULL COMMENT '小区资料ID',
  area_name           VARCHAR(80)   DEFAULT NULL COMMENT '区域名称',
  community_name      VARCHAR(160)  DEFAULT NULL COMMENT '小区名称',
  source_ip           VARCHAR(128)  DEFAULT NULL COMMENT '源地址',
  source_port         VARCHAR(80)   DEFAULT NULL COMMENT '源端口',
  target_ip           VARCHAR(128)  DEFAULT NULL COMMENT '目标地址',
  target_port         VARCHAR(80)   DEFAULT NULL COMMENT '目标端口',
  direction           VARCHAR(30)   DEFAULT NULL COMMENT '映射方向',
  relation_type       VARCHAR(40)   DEFAULT NULL COMMENT '关系类型',
  description         VARCHAR(1000) DEFAULT NULL COMMENT '映射说明',
  source_sheet        VARCHAR(120)  DEFAULT NULL COMMENT '来源工作表',
  source_row          INT           DEFAULT NULL COMMENT '来源行号',
  create_by           VARCHAR(64)   DEFAULT '' COMMENT '创建者',
  create_time         DATETIME      DEFAULT NULL COMMENT '创建时间',
  update_by           VARCHAR(64)   DEFAULT '' COMMENT '更新者',
  update_time         DATETIME      DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (mapping_id),
  UNIQUE KEY uk_ipam_mapping_batch_key (migration_batch_id, relation_key),
  KEY idx_ipam_mapping_site (site_id),
  KEY idx_ipam_mapping_source (source_ip),
  KEY idx_ipam_mapping_target (target_ip)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='IPAM历史映射关系';

CREATE TABLE IF NOT EXISTS ipam_migration_conflict (
  conflict_id          BIGINT(20)   NOT NULL AUTO_INCREMENT COMMENT '冲突ID',
  migration_batch_id   BIGINT(20)   NOT NULL COMMENT '迁移批次ID',
  ip_address           VARCHAR(64)  NOT NULL COMMENT '冲突IP',
  conflict_type        VARCHAR(60)  NOT NULL COMMENT '冲突类型',
  selected_source      VARCHAR(120) DEFAULT NULL COMMENT '采用的来源位置',
  conflicting_fields_json JSON      DEFAULT NULL COMMENT '冲突字段',
  source_values_json   JSON         NOT NULL COMMENT '全部冲突来源与原值',
  resolution_status    VARCHAR(30)  NOT NULL COMMENT '处理状态',
  resolution_note      VARCHAR(500) DEFAULT NULL COMMENT '处理说明',
  create_time          DATETIME     DEFAULT NULL COMMENT '创建时间',
  PRIMARY KEY (conflict_id),
  UNIQUE KEY uk_ipam_conflict_batch_ip (migration_batch_id, ip_address),
  KEY idx_ipam_conflict_status (resolution_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='IPAM迁移冲突审计';

SET @ipam_db_name := DATABASE();

SET @has_address_site_id := (
  SELECT COUNT(1) FROM information_schema.columns
  WHERE table_schema = @ipam_db_name AND table_name = 'ipam_address' AND column_name = 'site_id'
);
SET @sql := IF(
  @has_address_site_id = 0,
  'ALTER TABLE ipam_address ADD COLUMN site_id BIGINT(20) DEFAULT NULL COMMENT ''小区资料ID'' AFTER segment_id',
  'SELECT ''site_id exists'' AS message'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_address_area_name := (
  SELECT COUNT(1) FROM information_schema.columns
  WHERE table_schema = @ipam_db_name AND table_name = 'ipam_address' AND column_name = 'area_name'
);
SET @sql := IF(
  @has_address_area_name = 0,
  'ALTER TABLE ipam_address ADD COLUMN area_name VARCHAR(80) DEFAULT NULL COMMENT ''区域名称'' AFTER status',
  'SELECT ''area_name exists'' AS message'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_address_role := (
  SELECT COUNT(1) FROM information_schema.columns
  WHERE table_schema = @ipam_db_name AND table_name = 'ipam_address' AND column_name = 'address_role'
);
SET @sql := IF(
  @has_address_role = 0,
  'ALTER TABLE ipam_address ADD COLUMN address_role VARCHAR(30) DEFAULT NULL COMMENT ''来源业务角色'' AFTER status',
  'SELECT ''address_role exists'' AS message'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_address_device_code := (
  SELECT COUNT(1) FROM information_schema.columns
  WHERE table_schema = @ipam_db_name AND table_name = 'ipam_address' AND column_name = 'device_code'
);
SET @sql := IF(
  @has_address_device_code = 0,
  'ALTER TABLE ipam_address ADD COLUMN device_code VARCHAR(100) DEFAULT NULL COMMENT ''设备编码'' AFTER target_name',
  'SELECT ''device_code exists'' AS message'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_address_source_sheet := (
  SELECT COUNT(1) FROM information_schema.columns
  WHERE table_schema = @ipam_db_name AND table_name = 'ipam_address' AND column_name = 'source_sheet'
);
SET @sql := IF(
  @has_address_source_sheet = 0,
  'ALTER TABLE ipam_address ADD COLUMN source_sheet VARCHAR(120) DEFAULT NULL COMMENT ''来源工作表'' AFTER released_time',
  'SELECT ''source_sheet exists'' AS message'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_address_source_row := (
  SELECT COUNT(1) FROM information_schema.columns
  WHERE table_schema = @ipam_db_name AND table_name = 'ipam_address' AND column_name = 'source_row'
);
SET @sql := IF(
  @has_address_source_row = 0,
  'ALTER TABLE ipam_address ADD COLUMN source_row INT DEFAULT NULL COMMENT ''来源行号'' AFTER source_sheet',
  'SELECT ''source_row exists'' AS message'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_address_source_column := (
  SELECT COUNT(1) FROM information_schema.columns
  WHERE table_schema = @ipam_db_name AND table_name = 'ipam_address' AND column_name = 'source_column'
);
SET @sql := IF(
  @has_address_source_column = 0,
  'ALTER TABLE ipam_address ADD COLUMN source_column VARCHAR(16) DEFAULT NULL COMMENT ''来源列'' AFTER source_row',
  'SELECT ''source_column exists'' AS message'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_address_source_value := (
  SELECT COUNT(1) FROM information_schema.columns
  WHERE table_schema = @ipam_db_name AND table_name = 'ipam_address' AND column_name = 'source_value'
);
SET @sql := IF(
  @has_address_source_value = 0,
  'ALTER TABLE ipam_address ADD COLUMN source_value VARCHAR(255) DEFAULT NULL COMMENT ''来源原值'' AFTER source_column',
  'SELECT ''source_value exists'' AS message'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_address_source_refs := (
  SELECT COUNT(1) FROM information_schema.columns
  WHERE table_schema = @ipam_db_name AND table_name = 'ipam_address' AND column_name = 'source_refs_json'
);
SET @sql := IF(
  @has_address_source_refs = 0,
  'ALTER TABLE ipam_address ADD COLUMN source_refs_json JSON DEFAULT NULL COMMENT ''全部来源位置'' AFTER source_value',
  'SELECT ''source_refs_json exists'' AS message'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_address_migration_batch := (
  SELECT COUNT(1) FROM information_schema.columns
  WHERE table_schema = @ipam_db_name AND table_name = 'ipam_address' AND column_name = 'migration_batch_id'
);
SET @sql := IF(
  @has_address_migration_batch = 0,
  'ALTER TABLE ipam_address ADD COLUMN migration_batch_id BIGINT(20) DEFAULT NULL COMMENT ''迁移批次ID'' AFTER source_refs_json',
  'SELECT ''migration_batch_id exists'' AS message'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_address_site_index := (
  SELECT COUNT(1) FROM information_schema.statistics
  WHERE table_schema = @ipam_db_name AND table_name = 'ipam_address' AND index_name = 'idx_ipam_address_site'
);
SET @sql := IF(
  @has_address_site_index = 0,
  'ALTER TABLE ipam_address ADD INDEX idx_ipam_address_site (site_id)',
  'SELECT ''idx_ipam_address_site exists'' AS message'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_address_migration_index := (
  SELECT COUNT(1) FROM information_schema.statistics
  WHERE table_schema = @ipam_db_name AND table_name = 'ipam_address' AND index_name = 'idx_ipam_address_migration_batch'
);
SET @sql := IF(
  @has_address_migration_index = 0,
  'ALTER TABLE ipam_address ADD INDEX idx_ipam_address_migration_batch (migration_batch_id)',
  'SELECT ''idx_ipam_address_migration_batch exists'' AS message'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
