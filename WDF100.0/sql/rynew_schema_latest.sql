-- RYNEW v4.17.2 latest platform schema (2026-11-06).
-- Schema only: no accounts, passwords, menus, dictionaries, jobs or business rows.
-- Source: repository SQL rebuilt in a new socket-only MySQL; never a live database export.
-- Select an EMPTY target database before importing; database creation is deliberately excluded.
-- NOT an upgrade script. IF NOT EXISTS does not repair old columns/indexes.
-- Original legacy inspection tables are retained for historical compatibility only.
-- Rebuild: node tools/database/schema-latest.mjs --write
-- Validate: node --test tools/database/*.test.mjs
-- Source fingerprints (SHA-256):
-- 5600bf38bd8883e52a9f6b897b6ce9f4f4ef9532d09f6c10fb6f0082a3c00cf7  ry_20260320.sql
-- 5a0e64a518eb16a1ffbb65b1569002e41ec3b1639b0d5770468beecc3c05fc52  quartz.sql
-- b20cbc56fb54ba136fca28ff3f434559b036a35cbc2c9d69621c163d94ae049f  support_deploy_all.sql
-- 65856d102a079236f68f9a36e66ed35aa6efc06b9cc7b7059fa1aa8882caf18c  support_upgrade_20260929_server_credential_password_pool_v4_17_0.sql
-- 84b74db6e8f3533d852ac842801386cd4b25ef5f36bceaa413021a19b8469c62  ipam_upgrade_20260707_v3_9_0.sql
-- 34a6311f565027280870a80a0b40b1cd61b26b3fa37371d44b4f7d72b96176ff  ipam_upgrade_20260708_v3_9_2.sql
-- 6ae6510a37f1715fcc86280b7876e27d2340a6150083cfbdbd6628a8aba20cb2  ipam_upgrade_20260709_v3_9_3.sql
-- 283b071c680b3ca2f6e02bae3b52c506dace3c4ced1415e8f6ce0afd4ae836db  ipam_upgrade_20260711_v3_9_4.sql
-- 64957e4cb2ffe2f4d68afe6de74cfb23362916288abb70d03cf1c0dff8c9b2f9  ipam_upgrade_20260712_v3_9_5.sql
-- acf4122ee41b9016cad4fcd09697b46ceeb3056260ab802944b13759e09c7ecd  ipam_upgrade_20260712_v3_9_6.sql
-- 256f8ed899810f5efbedd49469d99e4e6048f78961a72a5939bda9e2e6479256  ipam_upgrade_20260712_v3_9_7.sql
-- f1169f828d954fc2f6a97b5d4dbb0acf50eacb7798130a1dd17d8ad1b460d74a  ipam_upgrade_20260717_v3_9_8.sql
-- 330f34120eec15a89caa62510f399c98e321aa42931718340606f9d2525579cf  ipam_upgrade_20260717_v3_9_9.sql
-- 2f444958535bad4b578bfc20ac2ede804df463404f5baafcf4af07473483deca  ipam_upgrade_20260811_v3_9_11.sql
-- cdf8b36d4dbea31237f1043537380d0d4d798e0deb8ec76b1532a9ff533316d7  ipam_remove_credential_key_20260811.sql
-- eb9eb15db863d8518fb9f325501d15ba6e42d1555a467f790bd3b3463f7d88eb  whitelist_v1.sql
-- 31e909e15c70752f39d14ac449d9f647defd1d157c37700061d8e50f6afd5c2c  whitelist_upgrade_20260409_drop_filter_unique_index.sql
-- 0076009ff61cb66f44cb5cf89857ee0f5e33bcda58428e506cd82771d44fd5f5  document_management_v1_20260815.sql
-- ac586f9f73f5c77d27d4e933e072909f41e360e4ad45c92a39c5e0a72cc04ac0  document_management_p0_20260815.sql
-- fdf96216d33dda116a84f9f1af2e222e5b63f83dbd5028e51f9dc4283ca4063a  document_management_p1_20260816.sql
-- 262cafdb6256e6dc0670b1fbb9c89e5550d27e39e54424325e6907320a109c85  document_management_p1_lightweight_versions_20260816.sql
-- 56091befa1a5a8e3c87c374d4df552774e0659d78291cd03d127cd572c2c5947  document_management_v3_9_19_archive_quota_20260816.sql
-- b01fc807db43d6e4959384e7b34844f38f4f63cc0ee1cfe7a0615190ce690b2d  knowledge_center_upgrade_20260829_v3_14_3_to_v3_15_2_all.sql
-- cfe9890219645ef047ead5c19e53109197562f4012e20a28a8376e12661a1ac3  data_governance_hdi_core_upgrade_20261013.sql
-- fc697ec815ec7ee9d62dd43b6810ef7d12364295f50358bcfcad673f6169464f  data_governance_hdi_connections_upgrade_20261025.sql

SET NAMES utf8mb4;
SET @rynew_previous_foreign_key_checks = @@FOREIGN_KEY_CHECKS;
SET FOREIGN_KEY_CHECKS = 0;

-- Table: QRTZ_BLOB_TRIGGERS
CREATE TABLE IF NOT EXISTS `QRTZ_BLOB_TRIGGERS` (
  `sched_name` varchar(120) COLLATE utf8mb4_general_ci NOT NULL COMMENT '调度名称',
  `trigger_name` varchar(200) COLLATE utf8mb4_general_ci NOT NULL COMMENT 'qrtz_triggers表trigger_name的外键',
  `trigger_group` varchar(200) COLLATE utf8mb4_general_ci NOT NULL COMMENT 'qrtz_triggers表trigger_group的外键',
  `blob_data` blob COMMENT '存放持久化Trigger对象',
  PRIMARY KEY (`sched_name`,`trigger_name`,`trigger_group`),
  CONSTRAINT `qrtz_blob_triggers_ibfk_1` FOREIGN KEY (`sched_name`, `trigger_name`, `trigger_group`) REFERENCES `QRTZ_TRIGGERS` (`sched_name`, `trigger_name`, `trigger_group`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Blob类型的触发器表';

-- Table: QRTZ_CALENDARS
CREATE TABLE IF NOT EXISTS `QRTZ_CALENDARS` (
  `sched_name` varchar(120) COLLATE utf8mb4_general_ci NOT NULL COMMENT '调度名称',
  `calendar_name` varchar(200) COLLATE utf8mb4_general_ci NOT NULL COMMENT '日历名称',
  `calendar` blob NOT NULL COMMENT '存放持久化calendar对象',
  PRIMARY KEY (`sched_name`,`calendar_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='日历信息表';

-- Table: QRTZ_CRON_TRIGGERS
CREATE TABLE IF NOT EXISTS `QRTZ_CRON_TRIGGERS` (
  `sched_name` varchar(120) COLLATE utf8mb4_general_ci NOT NULL COMMENT '调度名称',
  `trigger_name` varchar(200) COLLATE utf8mb4_general_ci NOT NULL COMMENT 'qrtz_triggers表trigger_name的外键',
  `trigger_group` varchar(200) COLLATE utf8mb4_general_ci NOT NULL COMMENT 'qrtz_triggers表trigger_group的外键',
  `cron_expression` varchar(200) COLLATE utf8mb4_general_ci NOT NULL COMMENT 'cron表达式',
  `time_zone_id` varchar(80) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '时区',
  PRIMARY KEY (`sched_name`,`trigger_name`,`trigger_group`),
  CONSTRAINT `qrtz_cron_triggers_ibfk_1` FOREIGN KEY (`sched_name`, `trigger_name`, `trigger_group`) REFERENCES `QRTZ_TRIGGERS` (`sched_name`, `trigger_name`, `trigger_group`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Cron类型的触发器表';

-- Table: QRTZ_FIRED_TRIGGERS
CREATE TABLE IF NOT EXISTS `QRTZ_FIRED_TRIGGERS` (
  `sched_name` varchar(120) COLLATE utf8mb4_general_ci NOT NULL COMMENT '调度名称',
  `entry_id` varchar(95) COLLATE utf8mb4_general_ci NOT NULL COMMENT '调度器实例id',
  `trigger_name` varchar(200) COLLATE utf8mb4_general_ci NOT NULL COMMENT 'qrtz_triggers表trigger_name的外键',
  `trigger_group` varchar(200) COLLATE utf8mb4_general_ci NOT NULL COMMENT 'qrtz_triggers表trigger_group的外键',
  `instance_name` varchar(200) COLLATE utf8mb4_general_ci NOT NULL COMMENT '调度器实例名',
  `fired_time` bigint NOT NULL COMMENT '触发的时间',
  `sched_time` bigint NOT NULL COMMENT '定时器制定的时间',
  `priority` int NOT NULL COMMENT '优先级',
  `state` varchar(16) COLLATE utf8mb4_general_ci NOT NULL COMMENT '状态',
  `job_name` varchar(200) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '任务名称',
  `job_group` varchar(200) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '任务组名',
  `is_nonconcurrent` varchar(1) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '是否并发',
  `requests_recovery` varchar(1) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '是否接受恢复执行',
  PRIMARY KEY (`sched_name`,`entry_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='已触发的触发器表';

-- Table: QRTZ_JOB_DETAILS
CREATE TABLE IF NOT EXISTS `QRTZ_JOB_DETAILS` (
  `sched_name` varchar(120) COLLATE utf8mb4_general_ci NOT NULL COMMENT '调度名称',
  `job_name` varchar(200) COLLATE utf8mb4_general_ci NOT NULL COMMENT '任务名称',
  `job_group` varchar(200) COLLATE utf8mb4_general_ci NOT NULL COMMENT '任务组名',
  `description` varchar(250) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '相关介绍',
  `job_class_name` varchar(250) COLLATE utf8mb4_general_ci NOT NULL COMMENT '执行任务类名称',
  `is_durable` varchar(1) COLLATE utf8mb4_general_ci NOT NULL COMMENT '是否持久化',
  `is_nonconcurrent` varchar(1) COLLATE utf8mb4_general_ci NOT NULL COMMENT '是否并发',
  `is_update_data` varchar(1) COLLATE utf8mb4_general_ci NOT NULL COMMENT '是否更新数据',
  `requests_recovery` varchar(1) COLLATE utf8mb4_general_ci NOT NULL COMMENT '是否接受恢复执行',
  `job_data` blob COMMENT '存放持久化job对象',
  PRIMARY KEY (`sched_name`,`job_name`,`job_group`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='任务详细信息表';

-- Table: QRTZ_LOCKS
CREATE TABLE IF NOT EXISTS `QRTZ_LOCKS` (
  `sched_name` varchar(120) COLLATE utf8mb4_general_ci NOT NULL COMMENT '调度名称',
  `lock_name` varchar(40) COLLATE utf8mb4_general_ci NOT NULL COMMENT '悲观锁名称',
  PRIMARY KEY (`sched_name`,`lock_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='存储的悲观锁信息表';

-- Table: QRTZ_PAUSED_TRIGGER_GRPS
CREATE TABLE IF NOT EXISTS `QRTZ_PAUSED_TRIGGER_GRPS` (
  `sched_name` varchar(120) COLLATE utf8mb4_general_ci NOT NULL COMMENT '调度名称',
  `trigger_group` varchar(200) COLLATE utf8mb4_general_ci NOT NULL COMMENT 'qrtz_triggers表trigger_group的外键',
  PRIMARY KEY (`sched_name`,`trigger_group`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='暂停的触发器表';

-- Table: QRTZ_SCHEDULER_STATE
CREATE TABLE IF NOT EXISTS `QRTZ_SCHEDULER_STATE` (
  `sched_name` varchar(120) COLLATE utf8mb4_general_ci NOT NULL COMMENT '调度名称',
  `instance_name` varchar(200) COLLATE utf8mb4_general_ci NOT NULL COMMENT '实例名称',
  `last_checkin_time` bigint NOT NULL COMMENT '上次检查时间',
  `checkin_interval` bigint NOT NULL COMMENT '检查间隔时间',
  PRIMARY KEY (`sched_name`,`instance_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='调度器状态表';

-- Table: QRTZ_SIMPLE_TRIGGERS
CREATE TABLE IF NOT EXISTS `QRTZ_SIMPLE_TRIGGERS` (
  `sched_name` varchar(120) COLLATE utf8mb4_general_ci NOT NULL COMMENT '调度名称',
  `trigger_name` varchar(200) COLLATE utf8mb4_general_ci NOT NULL COMMENT 'qrtz_triggers表trigger_name的外键',
  `trigger_group` varchar(200) COLLATE utf8mb4_general_ci NOT NULL COMMENT 'qrtz_triggers表trigger_group的外键',
  `repeat_count` bigint NOT NULL COMMENT '重复的次数统计',
  `repeat_interval` bigint NOT NULL COMMENT '重复的间隔时间',
  `times_triggered` bigint NOT NULL COMMENT '已经触发的次数',
  PRIMARY KEY (`sched_name`,`trigger_name`,`trigger_group`),
  CONSTRAINT `qrtz_simple_triggers_ibfk_1` FOREIGN KEY (`sched_name`, `trigger_name`, `trigger_group`) REFERENCES `QRTZ_TRIGGERS` (`sched_name`, `trigger_name`, `trigger_group`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='简单触发器的信息表';

-- Table: QRTZ_SIMPROP_TRIGGERS
CREATE TABLE IF NOT EXISTS `QRTZ_SIMPROP_TRIGGERS` (
  `sched_name` varchar(120) COLLATE utf8mb4_general_ci NOT NULL COMMENT '调度名称',
  `trigger_name` varchar(200) COLLATE utf8mb4_general_ci NOT NULL COMMENT 'qrtz_triggers表trigger_name的外键',
  `trigger_group` varchar(200) COLLATE utf8mb4_general_ci NOT NULL COMMENT 'qrtz_triggers表trigger_group的外键',
  `str_prop_1` varchar(512) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'String类型的trigger的第一个参数',
  `str_prop_2` varchar(512) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'String类型的trigger的第二个参数',
  `str_prop_3` varchar(512) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'String类型的trigger的第三个参数',
  `int_prop_1` int DEFAULT NULL COMMENT 'int类型的trigger的第一个参数',
  `int_prop_2` int DEFAULT NULL COMMENT 'int类型的trigger的第二个参数',
  `long_prop_1` bigint DEFAULT NULL COMMENT 'long类型的trigger的第一个参数',
  `long_prop_2` bigint DEFAULT NULL COMMENT 'long类型的trigger的第二个参数',
  `dec_prop_1` decimal(13,4) DEFAULT NULL COMMENT 'decimal类型的trigger的第一个参数',
  `dec_prop_2` decimal(13,4) DEFAULT NULL COMMENT 'decimal类型的trigger的第二个参数',
  `bool_prop_1` varchar(1) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'Boolean类型的trigger的第一个参数',
  `bool_prop_2` varchar(1) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'Boolean类型的trigger的第二个参数',
  PRIMARY KEY (`sched_name`,`trigger_name`,`trigger_group`),
  CONSTRAINT `qrtz_simprop_triggers_ibfk_1` FOREIGN KEY (`sched_name`, `trigger_name`, `trigger_group`) REFERENCES `QRTZ_TRIGGERS` (`sched_name`, `trigger_name`, `trigger_group`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='同步机制的行锁表';

-- Table: QRTZ_TRIGGERS
CREATE TABLE IF NOT EXISTS `QRTZ_TRIGGERS` (
  `sched_name` varchar(120) COLLATE utf8mb4_general_ci NOT NULL COMMENT '调度名称',
  `trigger_name` varchar(200) COLLATE utf8mb4_general_ci NOT NULL COMMENT '触发器的名字',
  `trigger_group` varchar(200) COLLATE utf8mb4_general_ci NOT NULL COMMENT '触发器所属组的名字',
  `job_name` varchar(200) COLLATE utf8mb4_general_ci NOT NULL COMMENT 'qrtz_job_details表job_name的外键',
  `job_group` varchar(200) COLLATE utf8mb4_general_ci NOT NULL COMMENT 'qrtz_job_details表job_group的外键',
  `description` varchar(250) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '相关介绍',
  `next_fire_time` bigint DEFAULT NULL COMMENT '上一次触发时间（毫秒）',
  `prev_fire_time` bigint DEFAULT NULL COMMENT '下一次触发时间（默认为-1表示不触发）',
  `priority` int DEFAULT NULL COMMENT '优先级',
  `trigger_state` varchar(16) COLLATE utf8mb4_general_ci NOT NULL COMMENT '触发器状态',
  `trigger_type` varchar(8) COLLATE utf8mb4_general_ci NOT NULL COMMENT '触发器的类型',
  `start_time` bigint NOT NULL COMMENT '开始时间',
  `end_time` bigint DEFAULT NULL COMMENT '结束时间',
  `calendar_name` varchar(200) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '日程表名称',
  `misfire_instr` smallint DEFAULT NULL COMMENT '补偿执行的策略',
  `job_data` blob COMMENT '存放持久化job对象',
  PRIMARY KEY (`sched_name`,`trigger_name`,`trigger_group`),
  KEY `sched_name` (`sched_name`,`job_name`,`job_group`),
  CONSTRAINT `qrtz_triggers_ibfk_1` FOREIGN KEY (`sched_name`, `job_name`, `job_group`) REFERENCES `QRTZ_JOB_DETAILS` (`sched_name`, `job_name`, `job_group`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='触发器详细信息表';

-- Table: dg_hdi_connection
CREATE TABLE IF NOT EXISTS `dg_hdi_connection` (
  `connection_id` bigint NOT NULL AUTO_INCREMENT,
  `owner_id` bigint NOT NULL,
  `revision` bigint NOT NULL,
  `name` varchar(128) NOT NULL,
  `db_type` int NOT NULL,
  `enabled` tinyint NOT NULL DEFAULT '1',
  `deleted` tinyint NOT NULL DEFAULT '0',
  `test_status` int NOT NULL DEFAULT '-1',
  `tested_revision` bigint DEFAULT NULL,
  `test_message` varchar(512) DEFAULT NULL,
  `tested_at` datetime(3) DEFAULT NULL,
  `created_at` datetime(3) NOT NULL,
  `updated_at` datetime(3) NOT NULL,
  PRIMARY KEY (`connection_id`),
  KEY `ix_dg_hdi_connection_owner` (`owner_id`,`deleted`,`updated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Table: dg_hdi_connection_revision
CREATE TABLE IF NOT EXISTS `dg_hdi_connection_revision` (
  `connection_id` bigint NOT NULL,
  `revision` bigint NOT NULL,
  `owner_id` bigint NOT NULL,
  `datasource_json` mediumtext NOT NULL,
  `encrypted_secret` text NOT NULL,
  `created_at` datetime(3) NOT NULL,
  PRIMARY KEY (`connection_id`,`revision`),
  KEY `ix_dg_hdi_connection_revision_owner` (`owner_id`,`connection_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Table: dg_hdi_connection_table
CREATE TABLE IF NOT EXISTS `dg_hdi_connection_table` (
  `table_id` bigint NOT NULL AUTO_INCREMENT,
  `connection_id` bigint NOT NULL,
  `owner_id` bigint NOT NULL,
  `object_key` char(64) NOT NULL,
  `schema_name` varchar(128) NOT NULL,
  `table_name` varchar(256) NOT NULL,
  `table_kind` varchar(16) NOT NULL,
  `created_at` datetime(3) NOT NULL,
  PRIMARY KEY (`table_id`),
  UNIQUE KEY `uk_dg_hdi_connection_object` (`connection_id`,`owner_id`,`object_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Table: dg_hdi_counter
CREATE TABLE IF NOT EXISTS `dg_hdi_counter` (
  `counter_name` varchar(32) NOT NULL,
  `next_value` bigint NOT NULL,
  PRIMARY KEY (`counter_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Table: dg_hdi_preference
CREATE TABLE IF NOT EXISTS `dg_hdi_preference` (
  `owner_id` bigint NOT NULL,
  `preference_key` varchar(64) NOT NULL,
  `preference_json` mediumtext NOT NULL,
  `updated_at` datetime(3) NOT NULL,
  PRIMARY KEY (`owner_id`,`preference_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Table: dg_hdi_revision
CREATE TABLE IF NOT EXISTS `dg_hdi_revision` (
  `task_id` bigint NOT NULL,
  `revision` bigint NOT NULL,
  `owner_id` bigint NOT NULL,
  `visual_json` mediumtext NOT NULL,
  `definition_sha256` char(64) NOT NULL,
  `snapshot_json` mediumtext NOT NULL,
  `snapshot_sha256` char(64) NOT NULL,
  `diagnostics_json` mediumtext NOT NULL,
  `created_at` datetime(3) NOT NULL,
  PRIMARY KEY (`task_id`,`revision`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Table: dg_hdi_run
CREATE TABLE IF NOT EXISTS `dg_hdi_run` (
  `run_id` varchar(36) NOT NULL,
  `task_id` bigint NOT NULL,
  `revision` bigint NOT NULL,
  `owner_id` bigint NOT NULL,
  `workspace_id` int NOT NULL,
  `native_flow_id` bigint NOT NULL,
  `native_instance_id` bigint NOT NULL,
  `metadata_id_base` bigint NOT NULL,
  `definition_sha256` char(64) NOT NULL,
  `mode` varchar(16) NOT NULL,
  `state` varchar(24) NOT NULL,
  `trigger_key` varchar(128) DEFAULT NULL,
  `worker_snapshot_json` mediumtext,
  `error_message` varchar(1000) DEFAULT NULL,
  `created_at` datetime(3) NOT NULL,
  `updated_at` datetime(3) NOT NULL,
  `finished_at` datetime(3) DEFAULT NULL,
  PRIMARY KEY (`run_id`),
  UNIQUE KEY `uk_dg_hdi_native_flow` (`native_flow_id`),
  UNIQUE KEY `uk_dg_hdi_trigger` (`trigger_key`),
  KEY `ix_dg_hdi_run_task` (`task_id`,`created_at`),
  KEY `ix_dg_hdi_run_owner` (`owner_id`,`state`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Table: dg_hdi_schedule
CREATE TABLE IF NOT EXISTS `dg_hdi_schedule` (
  `task_id` bigint NOT NULL,
  `owner_id` bigint NOT NULL,
  `revision` bigint NOT NULL,
  `generation` bigint NOT NULL,
  `plan_json` mediumtext NOT NULL,
  `enabled` tinyint NOT NULL DEFAULT '0',
  `next_fire_at` datetime(3) DEFAULT NULL,
  `last_outcome` varchar(128) DEFAULT NULL,
  `created_at` datetime(3) NOT NULL,
  `updated_at` datetime(3) NOT NULL,
  PRIMARY KEY (`task_id`),
  KEY `ix_dg_hdi_schedule_due` (`enabled`,`next_fire_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Table: dg_hdi_task
CREATE TABLE IF NOT EXISTS `dg_hdi_task` (
  `task_id` bigint NOT NULL AUTO_INCREMENT,
  `workspace_id` int NOT NULL,
  `owner_id` bigint NOT NULL,
  `project_code` varchar(64) NOT NULL,
  `task_name` varchar(128) NOT NULL,
  `folder_id` bigint NOT NULL DEFAULT '-1',
  `revision` bigint NOT NULL DEFAULT '0',
  `published_revision` bigint DEFAULT NULL,
  `state` varchar(24) NOT NULL DEFAULT 'DRAFT',
  `last_run_id` varchar(36) DEFAULT NULL,
  `schedule_json` mediumtext,
  `schedule_enabled` tinyint NOT NULL DEFAULT '0',
  `next_fire_at` datetime(3) DEFAULT NULL,
  `deleted` tinyint NOT NULL DEFAULT '0',
  `created_at` datetime(3) NOT NULL,
  `updated_at` datetime(3) NOT NULL,
  PRIMARY KEY (`task_id`),
  KEY `ix_dg_hdi_task_owner` (`workspace_id`,`owner_id`,`deleted`,`updated_at`),
  KEY `ix_dg_hdi_task_schedule` (`schedule_enabled`,`next_fire_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Table: dg_hdi_workspace
CREATE TABLE IF NOT EXISTS `dg_hdi_workspace` (
  `workspace_id` int NOT NULL AUTO_INCREMENT,
  `workspace_key` varchar(64) NOT NULL,
  `workspace_name` varchar(128) NOT NULL,
  `created_at` datetime(3) NOT NULL,
  PRIMARY KEY (`workspace_id`),
  UNIQUE KEY `uk_dg_hdi_workspace_key` (`workspace_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Table: doc_acl
CREATE TABLE IF NOT EXISTS `doc_acl` (
  `acl_id` bigint NOT NULL AUTO_INCREMENT COMMENT '权限ID',
  `document_id` bigint NOT NULL COMMENT '文档ID',
  `user_id` bigint NOT NULL COMMENT '授权用户ID',
  `permission` varchar(16) NOT NULL COMMENT '权限（VIEW EDIT）',
  `granted_by` bigint NOT NULL COMMENT '授权人用户ID',
  `expires_at` datetime DEFAULT NULL COMMENT '权限到期时间；空值表示永久有效',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`acl_id`),
  UNIQUE KEY `uk_doc_acl_document_user` (`document_id`,`user_id`),
  KEY `idx_doc_acl_user` (`user_id`,`permission`,`document_id`),
  KEY `idx_doc_acl_expiry` (`expires_at`,`permission`,`acl_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='文档用户权限';

-- Table: doc_document
CREATE TABLE IF NOT EXISTS `doc_document` (
  `document_id` bigint NOT NULL AUTO_INCREMENT COMMENT '文档ID',
  `folder_id` bigint NOT NULL DEFAULT '0' COMMENT '所属目录ID，0为根目录',
  `owner_id` bigint NOT NULL COMMENT '所有者用户ID',
  `title` varchar(160) NOT NULL COMMENT '文件名（含扩展名）',
  `file_type` varchar(16) NOT NULL COMMENT '文件类型（doc docx xls xlsx）',
  `document_type` varchar(16) NOT NULL COMMENT '编辑器类型（word cell）',
  `storage_key` varchar(500) NOT NULL COMMENT '受控存储相对路径',
  `file_size` bigint NOT NULL DEFAULT '0' COMMENT '文件字节数',
  `content_version` int NOT NULL DEFAULT '1' COMMENT '内容版本号',
  `editor_key` varchar(128) NOT NULL COMMENT 'ONLYOFFICE协同会话键',
  `checksum` varchar(64) NOT NULL DEFAULT '' COMMENT 'SHA-256摘要',
  `lifecycle_status` varchar(16) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态（ACTIVE ARCHIVED TRASH）',
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`document_id`),
  UNIQUE KEY `uk_doc_document_editor_key` (`editor_key`),
  KEY `idx_doc_document_owner_status` (`owner_id`,`lifecycle_status`,`update_time`),
  KEY `idx_doc_document_folder_status` (`folder_id`,`lifecycle_status`,`update_time`),
  KEY `idx_doc_document_type` (`file_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='在线文档';

-- Table: doc_folder
CREATE TABLE IF NOT EXISTS `doc_folder` (
  `folder_id` bigint NOT NULL AUTO_INCREMENT COMMENT '目录ID',
  `parent_id` bigint NOT NULL DEFAULT '0' COMMENT '上级目录ID，0为根目录',
  `owner_id` bigint NOT NULL COMMENT '所有者用户ID',
  `folder_name` varchar(100) NOT NULL COMMENT '目录名称',
  `folder_color` varchar(16) NOT NULL DEFAULT '#4F7CCF' COMMENT '目录显示颜色',
  `sort_order` int NOT NULL DEFAULT '0' COMMENT '排序',
  `deleted` char(1) NOT NULL DEFAULT '0' COMMENT '删除标记（0正常 1删除）',
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`folder_id`),
  KEY `idx_doc_folder_owner_parent` (`owner_id`,`parent_id`,`deleted`),
  KEY `idx_doc_folder_parent` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='文档目录';

-- Table: doc_operation_log
CREATE TABLE IF NOT EXISTS `doc_operation_log` (
  `log_id` bigint NOT NULL AUTO_INCREMENT COMMENT '日志ID',
  `document_id` bigint DEFAULT NULL COMMENT '文档ID；目录操作可为空',
  `action_type` varchar(32) NOT NULL COMMENT '动作类型',
  `operator_id` bigint DEFAULT NULL COMMENT '操作用户ID',
  `operator_name` varchar(64) DEFAULT NULL COMMENT '操作用户名称',
  `target_user_id` bigint DEFAULT NULL COMMENT '权限操作目标用户ID',
  `target_user_name` varchar(64) DEFAULT NULL COMMENT '权限操作目标用户名称',
  `previous_value` varchar(128) DEFAULT NULL COMMENT '变更前值',
  `current_value` varchar(128) DEFAULT NULL COMMENT '变更后值',
  `detail_content` varchar(500) DEFAULT NULL COMMENT '操作摘要',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  PRIMARY KEY (`log_id`),
  KEY `idx_doc_log_document_time` (`document_id`,`create_time`),
  KEY `idx_doc_log_operator_recent` (`operator_id`,`action_type`,`create_time`),
  KEY `idx_doc_log_target_time` (`target_user_id`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='文档操作日志';

-- Table: doc_user_quota
CREATE TABLE IF NOT EXISTS `doc_user_quota` (
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `quota_bytes` bigint NOT NULL DEFAULT '104857600' COMMENT '文档总可用空间，默认100MB',
  `max_upload_bytes` bigint NOT NULL DEFAULT '104857600' COMMENT '单文件上传上限，最大100MB',
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='文档用户容量策略';

-- Table: doc_version
CREATE TABLE IF NOT EXISTS `doc_version` (
  `version_id` bigint NOT NULL AUTO_INCREMENT COMMENT '版本ID',
  `document_id` bigint NOT NULL COMMENT '文档ID',
  `version_no` int NOT NULL COMMENT '版本号',
  `storage_key` varchar(500) DEFAULT NULL COMMENT '兼容旧历史快照；轻量版本新记录为空',
  `file_size` bigint DEFAULT NULL COMMENT '兼容旧历史快照；轻量版本新记录为空',
  `checksum` varchar(64) DEFAULT NULL COMMENT '兼容旧历史快照；轻量版本新记录为空',
  `source_type` varchar(32) NOT NULL COMMENT '来源（CREATE FORCE_SAVE FINAL_SAVE）',
  `creator_id` bigint DEFAULT NULL COMMENT '保存用户ID',
  `creator_name` varchar(64) DEFAULT NULL COMMENT '保存用户名称',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  PRIMARY KEY (`version_id`),
  UNIQUE KEY `uk_doc_version_document_no` (`document_id`,`version_no`),
  KEY `idx_doc_version_time` (`document_id`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='文档轻量修改版本';

-- Table: gen_table
CREATE TABLE IF NOT EXISTS `gen_table` (
  `table_id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `table_name` varchar(200) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '表名称',
  `table_comment` varchar(500) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '表描述',
  `sub_table_name` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '关联子表的表名',
  `sub_table_fk_name` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '子表关联的外键名',
  `class_name` varchar(100) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '实体类名称',
  `tpl_category` varchar(200) COLLATE utf8mb4_general_ci DEFAULT 'crud' COMMENT '使用的模板（crud单表操作 tree树表操作）',
  `tpl_web_type` varchar(30) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '前端模板类型（element-ui模版 element-plus模版）',
  `package_name` varchar(100) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '生成包路径',
  `module_name` varchar(30) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '生成模块名',
  `business_name` varchar(30) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '生成业务名',
  `function_name` varchar(50) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '生成功能名',
  `function_author` varchar(50) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '生成功能作者',
  `gen_type` char(1) COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT '生成代码方式（0zip压缩包 1自定义路径）',
  `gen_path` varchar(200) COLLATE utf8mb4_general_ci DEFAULT '/' COMMENT '生成路径（不填默认项目路径）',
  `options` varchar(1000) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '其它生成选项',
  `create_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`table_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='代码生成业务表';

-- Table: gen_table_column
CREATE TABLE IF NOT EXISTS `gen_table_column` (
  `column_id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `table_id` bigint DEFAULT NULL COMMENT '归属表编号',
  `column_name` varchar(200) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '列名称',
  `column_comment` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '列描述',
  `column_type` varchar(100) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '列类型',
  `java_type` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'JAVA类型',
  `java_field` varchar(200) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'JAVA字段名',
  `is_pk` char(1) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '是否主键（1是）',
  `is_increment` char(1) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '是否自增（1是）',
  `is_required` char(1) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '是否必填（1是）',
  `is_insert` char(1) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '是否为插入字段（1是）',
  `is_edit` char(1) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '是否编辑字段（1是）',
  `is_list` char(1) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '是否列表字段（1是）',
  `is_query` char(1) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '是否查询字段（1是）',
  `query_type` varchar(200) COLLATE utf8mb4_general_ci DEFAULT 'EQ' COMMENT '查询方式（等于、不等于、大于、小于、范围）',
  `html_type` varchar(200) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '显示类型（文本框、文本域、下拉框、复选框、单选框、日期控件）',
  `dict_type` varchar(200) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '字典类型',
  `sort` int DEFAULT NULL COMMENT '排序',
  `create_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`column_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='代码生成业务表字段';

-- Table: ipam_address
CREATE TABLE IF NOT EXISTS `ipam_address` (
  `address_id` bigint NOT NULL AUTO_INCREMENT COMMENT '地址ID',
  `network_id` bigint NOT NULL COMMENT '大网段ID',
  `segment_id` bigint NOT NULL COMMENT '子网段ID',
  `site_id` bigint DEFAULT NULL COMMENT '小区资料ID',
  `ip_address` varchar(64) NOT NULL COMMENT 'IP地址',
  `ip_value` bigint NOT NULL COMMENT 'IP数值',
  `status` varchar(20) NOT NULL DEFAULT 'FREE' COMMENT '状态（FREE空闲 RESERVED保留 ALLOCATED已占用 DISABLED禁用）',
  `address_role` varchar(30) DEFAULT NULL COMMENT '来源业务角色',
  `area_name` varchar(80) DEFAULT NULL COMMENT '区域名称',
  `community_name` varchar(120) DEFAULT NULL COMMENT '小区名称',
  `target_type` varchar(40) DEFAULT NULL COMMENT '设备类别',
  `target_name` varchar(160) DEFAULT NULL COMMENT '对象名称',
  `device_code` varchar(100) DEFAULT NULL COMMENT '设备编码',
  `manufacturer` varchar(60) DEFAULT NULL COMMENT '厂商',
  `internal_ip_address` varchar(128) DEFAULT NULL COMMENT '小区内网IP',
  `access_unit` varchar(80) DEFAULT NULL COMMENT '接入单位',
  `purpose` varchar(255) DEFAULT NULL COMMENT '用途说明',
  `login_username` varchar(120) DEFAULT NULL COMMENT '登录账号',
  `login_password` varchar(200) DEFAULT NULL COMMENT '登录密码',
  `mapping_address` varchar(128) DEFAULT NULL COMMENT '映射地址',
  `mapping_port` varchar(80) DEFAULT NULL COMMENT '映射端口',
  `mapping_description` varchar(500) DEFAULT NULL COMMENT '映射说明',
  `owner_name` varchar(80) DEFAULT NULL COMMENT '责任人',
  `owner_phone` varchar(40) DEFAULT NULL COMMENT '联系电话',
  `issue_batch` varchar(80) DEFAULT NULL COMMENT '历史下发批次（兼容字段）',
  `allocated_time` datetime DEFAULT NULL COMMENT '分配时间',
  `issued_time` datetime DEFAULT NULL COMMENT '历史下发时间（兼容字段）',
  `released_time` datetime DEFAULT NULL COMMENT '释放时间',
  `source_sheet` varchar(120) DEFAULT NULL COMMENT '来源工作表',
  `source_row` int DEFAULT NULL COMMENT '来源行号',
  `source_column` varchar(16) DEFAULT NULL COMMENT '来源列',
  `source_value` varchar(255) DEFAULT NULL COMMENT '来源原值',
  `source_refs_json` json DEFAULT NULL COMMENT '全部来源位置',
  `migration_batch_id` bigint DEFAULT NULL COMMENT '迁移批次ID',
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`address_id`),
  UNIQUE KEY `uk_ipam_address_ip` (`ip_address`),
  KEY `idx_ipam_address_network` (`network_id`),
  KEY `idx_ipam_address_segment` (`segment_id`),
  KEY `idx_ipam_address_status` (`status`),
  KEY `idx_ipam_address_value` (`ip_value`),
  KEY `idx_ipam_address_ledger` (`network_id`,`segment_id`,`status`,`community_name`,`target_type`,`manufacturer`),
  KEY `idx_ipam_address_internal_ip` (`internal_ip_address`),
  KEY `idx_ipam_address_site` (`site_id`),
  KEY `idx_ipam_address_migration_batch` (`migration_batch_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='IPAM地址台账';

-- Table: ipam_mapping_relation
CREATE TABLE IF NOT EXISTS `ipam_mapping_relation` (
  `mapping_id` bigint NOT NULL AUTO_INCREMENT COMMENT '映射关系ID',
  `migration_batch_id` bigint NOT NULL COMMENT '迁移批次ID',
  `relation_key` varchar(180) NOT NULL COMMENT '批次内关系唯一键',
  `site_id` bigint DEFAULT NULL COMMENT '小区资料ID',
  `area_name` varchar(80) DEFAULT NULL COMMENT '区域名称',
  `community_name` varchar(160) DEFAULT NULL COMMENT '小区名称',
  `source_ip` varchar(128) DEFAULT NULL COMMENT '源地址',
  `source_port` varchar(80) DEFAULT NULL COMMENT '源端口',
  `target_ip` varchar(128) DEFAULT NULL COMMENT '目标地址',
  `target_port` varchar(80) DEFAULT NULL COMMENT '目标端口',
  `direction` varchar(30) DEFAULT NULL COMMENT '映射方向',
  `relation_type` varchar(40) DEFAULT NULL COMMENT '关系类型',
  `description` varchar(1000) DEFAULT NULL COMMENT '映射说明',
  `source_sheet` varchar(120) DEFAULT NULL COMMENT '来源工作表',
  `source_row` int DEFAULT NULL COMMENT '来源行号',
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`mapping_id`),
  UNIQUE KEY `uk_ipam_mapping_batch_key` (`migration_batch_id`,`relation_key`),
  KEY `idx_ipam_mapping_site` (`site_id`),
  KEY `idx_ipam_mapping_source` (`source_ip`),
  KEY `idx_ipam_mapping_target` (`target_ip`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='IPAM历史映射关系';

-- Table: ipam_migration_batch
CREATE TABLE IF NOT EXISTS `ipam_migration_batch` (
  `batch_id` bigint NOT NULL AUTO_INCREMENT COMMENT '迁移批次ID',
  `batch_code` varchar(80) NOT NULL COMMENT '迁移批次编码',
  `source_file_name` varchar(255) NOT NULL COMMENT '源文件名',
  `source_file_sha256` char(64) NOT NULL COMMENT '源文件SHA-256',
  `backup_prefix` varchar(100) DEFAULT NULL COMMENT '迁移前备份表前缀',
  `migration_status` varchar(20) NOT NULL COMMENT '状态（PREPARED RUNNING COMPLETED FAILED ROLLED_BACK）',
  `source_row_count` int NOT NULL DEFAULT '0' COMMENT '源文件非空行数',
  `network_count` int NOT NULL DEFAULT '0' COMMENT '规范化网段数',
  `site_count` int NOT NULL DEFAULT '0' COMMENT '规范化小区数',
  `address_count` int NOT NULL DEFAULT '0' COMMENT '正式地址数',
  `mapping_count` int NOT NULL DEFAULT '0' COMMENT '映射关系数',
  `conflict_count` int NOT NULL DEFAULT '0' COMMENT '冲突数',
  `summary_json` json DEFAULT NULL COMMENT '迁移汇总',
  `error_message` varchar(1000) DEFAULT NULL COMMENT '失败原因',
  `started_time` datetime DEFAULT NULL COMMENT '开始时间',
  `completed_time` datetime DEFAULT NULL COMMENT '完成时间',
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`batch_id`),
  UNIQUE KEY `uk_ipam_migration_batch_code` (`batch_code`),
  KEY `idx_ipam_migration_batch_hash` (`source_file_sha256`),
  KEY `idx_ipam_migration_batch_status` (`migration_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='IPAM一次性迁移批次';

-- Table: ipam_migration_conflict
CREATE TABLE IF NOT EXISTS `ipam_migration_conflict` (
  `conflict_id` bigint NOT NULL AUTO_INCREMENT COMMENT '冲突ID',
  `migration_batch_id` bigint NOT NULL COMMENT '迁移批次ID',
  `ip_address` varchar(64) NOT NULL COMMENT '冲突IP',
  `conflict_type` varchar(60) NOT NULL COMMENT '冲突类型',
  `selected_source` varchar(120) DEFAULT NULL COMMENT '采用的来源位置',
  `conflicting_fields_json` json DEFAULT NULL COMMENT '冲突字段',
  `source_values_json` json NOT NULL COMMENT '全部冲突来源与原值',
  `resolution_status` varchar(30) NOT NULL COMMENT '处理状态',
  `resolution_note` varchar(500) DEFAULT NULL COMMENT '处理说明',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  PRIMARY KEY (`conflict_id`),
  UNIQUE KEY `uk_ipam_conflict_batch_ip` (`migration_batch_id`,`ip_address`),
  KEY `idx_ipam_conflict_status` (`resolution_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='IPAM迁移冲突审计';

-- Table: ipam_migration_source_row
CREATE TABLE IF NOT EXISTS `ipam_migration_source_row` (
  `source_id` bigint NOT NULL AUTO_INCREMENT COMMENT '原始行ID',
  `batch_id` bigint NOT NULL COMMENT '迁移批次ID',
  `sheet_name` varchar(120) NOT NULL COMMENT '工作表名称',
  `source_row_number` int NOT NULL COMMENT 'Excel行号',
  `raw_json` json NOT NULL COMMENT '原始单元格值',
  `detected_ips_json` json DEFAULT NULL COMMENT '识别到的IP地址',
  `archive_status` varchar(30) NOT NULL DEFAULT 'ARCHIVED' COMMENT '归档状态',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  PRIMARY KEY (`source_id`),
  UNIQUE KEY `uk_ipam_source_batch_row` (`batch_id`,`sheet_name`,`source_row_number`),
  KEY `idx_ipam_source_batch` (`batch_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='IPAM迁移原始行归档';

-- Table: ipam_network
CREATE TABLE IF NOT EXISTS `ipam_network` (
  `network_id` bigint NOT NULL AUTO_INCREMENT COMMENT '大网段ID',
  `network_name` varchar(100) NOT NULL COMMENT '网段名称',
  `police_station_name` varchar(80) NOT NULL DEFAULT '' COMMENT '所属派出所',
  `cidr_block` varchar(64) NOT NULL COMMENT 'CIDR网段',
  `start_ip` varchar(64) NOT NULL COMMENT '起始IP',
  `end_ip` varchar(64) NOT NULL COMMENT '结束IP',
  `start_value` bigint DEFAULT NULL COMMENT '网络起始数值',
  `end_value` bigint DEFAULT NULL COMMENT '网络结束数值',
  `prefix_length` int NOT NULL COMMENT '掩码长度',
  `scenario_type` varchar(20) NOT NULL DEFAULT 'SOCIAL' COMMENT '使用场景（SOCIAL社会面场景 INTERNAL公安内网场景）',
  `status` char(1) DEFAULT '0' COMMENT '状态（0启用 1停用）',
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`network_id`),
  UNIQUE KEY `uk_ipam_network_cidr` (`cidr_block`),
  KEY `idx_ipam_network_range` (`start_value`,`end_value`),
  KEY `idx_ipam_network_police_station` (`police_station_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='IPAM大网段';

-- Table: ipam_operation_log
CREATE TABLE IF NOT EXISTS `ipam_operation_log` (
  `log_id` bigint NOT NULL AUTO_INCREMENT COMMENT '日志ID',
  `action_type` varchar(50) NOT NULL COMMENT '操作类型',
  `target_type` varchar(50) DEFAULT NULL COMMENT '目标类型',
  `target_id` bigint DEFAULT NULL COMMENT '目标ID',
  `ip_address` varchar(64) DEFAULT NULL COMMENT 'IP地址',
  `summary` varchar(255) DEFAULT NULL COMMENT '操作摘要',
  `detail_content` mediumtext COMMENT '操作详情',
  `operator_name` varchar(64) DEFAULT NULL COMMENT '操作人',
  `operator_ip` varchar(128) DEFAULT NULL COMMENT '操作IP',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  PRIMARY KEY (`log_id`),
  KEY `idx_ipam_log_target` (`target_type`,`target_id`),
  KEY `idx_ipam_log_ip` (`ip_address`),
  KEY `idx_ipam_log_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='IPAM应用操作记录';

-- Table: ipam_scan_job
CREATE TABLE IF NOT EXISTS `ipam_scan_job` (
  `scan_id` bigint NOT NULL AUTO_INCREMENT COMMENT '扫描任务ID',
  `scan_scope` varchar(20) NOT NULL COMMENT '扫描范围（NETWORK单网段 ALL全域）',
  `trigger_type` varchar(20) NOT NULL COMMENT '触发方式（MANUAL手工 SCHEDULED定时）',
  `network_id` bigint DEFAULT NULL COMMENT '单网段扫描时的网段ID',
  `network_name` varchar(120) DEFAULT NULL COMMENT '单网段扫描时的网段名称',
  `scan_status` varchar(20) NOT NULL COMMENT '状态（QUEUED RUNNING COMPLETED PARTIAL FAILED）',
  `total_count` bigint NOT NULL DEFAULT '0' COMMENT '待检测地址数',
  `completed_count` bigint NOT NULL DEFAULT '0' COMMENT '已完成地址数',
  `online_count` bigint NOT NULL DEFAULT '0' COMMENT '在线地址数',
  `offline_count` bigint NOT NULL DEFAULT '0' COMMENT '离线地址数',
  `error_count` bigint NOT NULL DEFAULT '0' COMMENT '检测异常地址数',
  `timeout_ms` int NOT NULL COMMENT '单地址超时时间（毫秒）',
  `interval_ms` int NOT NULL COMMENT '探针提交间隔（毫秒）',
  `concurrency_count` int NOT NULL COMMENT '最大并发探针数',
  `started_time` datetime DEFAULT NULL COMMENT '开始时间',
  `finished_time` datetime DEFAULT NULL COMMENT '结束时间',
  `error_message` varchar(1000) DEFAULT NULL COMMENT '任务异常说明',
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`scan_id`),
  KEY `idx_ipam_scan_job_network` (`network_id`,`create_time`),
  KEY `idx_ipam_scan_job_status` (`scan_status`,`create_time`),
  KEY `idx_ipam_scan_job_scope` (`scan_scope`,`trigger_type`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='IPAM连通性扫描任务';

-- Table: ipam_scan_lock
CREATE TABLE IF NOT EXISTS `ipam_scan_lock` (
  `lock_name` varchar(32) NOT NULL COMMENT '锁名称',
  `owner_token` varchar(64) DEFAULT NULL COMMENT '持有实例随机令牌',
  `scan_id` bigint DEFAULT NULL COMMENT '当前扫描任务ID',
  `lease_until` datetime DEFAULT NULL COMMENT '租约失效时间',
  `update_time` datetime DEFAULT NULL COMMENT '最近续租时间',
  PRIMARY KEY (`lock_name`),
  KEY `idx_ipam_scan_lock_lease` (`lease_until`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='IPAM扫描全局租约';

-- Table: ipam_scan_result
CREATE TABLE IF NOT EXISTS `ipam_scan_result` (
  `result_id` bigint NOT NULL AUTO_INCREMENT COMMENT '扫描结果ID',
  `scan_id` bigint NOT NULL COMMENT '最近扫描任务ID',
  `network_id` bigint NOT NULL COMMENT '网段ID',
  `segment_id` bigint NOT NULL COMMENT '地址池ID',
  `ip_address` varchar(64) NOT NULL COMMENT 'IP地址',
  `ip_value` bigint NOT NULL COMMENT 'IP数值',
  `connectivity_status` varchar(20) NOT NULL COMMENT '连通状态（ONLINE OFFLINE UNKNOWN）',
  `response_time_ms` bigint DEFAULT NULL COMMENT '本次响应时间（毫秒）',
  `last_scan_time` datetime NOT NULL COMMENT '最近检测时间',
  `last_online_time` datetime DEFAULT NULL COMMENT '最近在线时间',
  `last_offline_time` datetime DEFAULT NULL COMMENT '最近离线时间',
  `error_message` varchar(500) DEFAULT NULL COMMENT '检测异常说明',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`result_id`),
  UNIQUE KEY `uk_ipam_scan_result_ip` (`ip_address`),
  KEY `idx_ipam_scan_result_segment` (`segment_id`,`ip_value`),
  KEY `idx_ipam_scan_result_network_status` (`network_id`,`connectivity_status`),
  KEY `idx_ipam_scan_result_time` (`last_scan_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='IPAM地址最新连通状态';

-- Table: ipam_segment
CREATE TABLE IF NOT EXISTS `ipam_segment` (
  `segment_id` bigint NOT NULL AUTO_INCREMENT COMMENT '子网段ID',
  `network_id` bigint NOT NULL COMMENT '大网段ID',
  `segment_name` varchar(120) NOT NULL COMMENT '子网段名称',
  `cidr_block` varchar(64) NOT NULL COMMENT 'CIDR子网段',
  `start_ip` varchar(64) NOT NULL COMMENT '起始IP',
  `end_ip` varchar(64) NOT NULL COMMENT '结束IP',
  `gateway_ip` varchar(64) DEFAULT NULL COMMENT '网关IP',
  `prefix_length` int NOT NULL COMMENT '掩码长度',
  `total_count` bigint NOT NULL DEFAULT '0' COMMENT '地址总数',
  `status` char(1) DEFAULT '0' COMMENT '状态（0启用 1停用）',
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`segment_id`),
  UNIQUE KEY `uk_ipam_segment_cidr` (`cidr_block`),
  UNIQUE KEY `uk_ipam_segment_network` (`network_id`),
  KEY `idx_ipam_segment_network` (`network_id`),
  KEY `idx_ipam_segment_start_ip` (`start_ip`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='IPAM可分配子网段';

-- Table: ipam_setting
CREATE TABLE IF NOT EXISTS `ipam_setting` (
  `setting_key` varchar(64) NOT NULL COMMENT '设置键',
  `setting_value` varchar(128) NOT NULL COMMENT '设置值',
  `setting_name` varchar(100) NOT NULL COMMENT '设置名称',
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`setting_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='IPAM应用设置';

-- Table: ipam_site
CREATE TABLE IF NOT EXISTS `ipam_site` (
  `site_id` bigint NOT NULL AUTO_INCREMENT COMMENT '小区资料ID',
  `migration_batch_id` bigint DEFAULT NULL COMMENT '迁移批次ID',
  `area_name` varchar(80) NOT NULL DEFAULT '' COMMENT '区域名称',
  `site_name` varchar(160) NOT NULL COMMENT '小区名称',
  `scenario_type` varchar(20) NOT NULL DEFAULT 'SOCIAL' COMMENT '使用场景',
  `longitude` decimal(11,7) DEFAULT NULL COMMENT '经度',
  `latitude` decimal(10,7) DEFAULT NULL COMMENT '纬度',
  `access_unit` varchar(120) DEFAULT NULL COMMENT '接入单位',
  `contact_name` varchar(80) DEFAULT NULL COMMENT '联系人',
  `contact_phone` varchar(40) DEFAULT NULL COMMENT '联系电话',
  `access_control_brand` varchar(80) DEFAULT NULL COMMENT '门禁品牌',
  `barrier_gate_brand` varchar(80) DEFAULT NULL COMMENT '道闸品牌',
  `access_control_mapping_ip` varchar(128) DEFAULT NULL COMMENT '门禁映射地址',
  `barrier_gate_mapping_ip` varchar(128) DEFAULT NULL COMMENT '道闸映射地址',
  `access_status` varchar(100) DEFAULT NULL COMMENT '接入状态',
  `source_sheet` varchar(120) DEFAULT NULL COMMENT '首个来源工作表',
  `source_row` int DEFAULT NULL COMMENT '首个来源行号',
  `source_refs_json` json DEFAULT NULL COMMENT '全部来源位置',
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`site_id`),
  UNIQUE KEY `uk_ipam_site_area_name` (`area_name`,`site_name`),
  KEY `idx_ipam_site_migration_batch` (`migration_batch_id`),
  KEY `idx_ipam_site_name` (`site_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='IPAM小区资料';

-- Table: kb_page
CREATE TABLE IF NOT EXISTS `kb_page` (
  `page_id` bigint NOT NULL AUTO_INCREMENT COMMENT '知识节点ID',
  `space_id` bigint NOT NULL COMMENT '知识空间ID',
  `parent_id` bigint NOT NULL DEFAULT '0' COMMENT '上级知识目录ID，0为根目录',
  `page_type` varchar(16) NOT NULL COMMENT '节点类型（FOLDER ARTICLE）',
  `title` varchar(160) NOT NULL COMMENT '目录或知识标题',
  `summary` varchar(500) NOT NULL DEFAULT '' COMMENT '知识摘要',
  `content` mediumtext COMMENT '已清洗的知识正文HTML',
  `sort_order` int NOT NULL DEFAULT '0' COMMENT '同级排序',
  `content_version` int NOT NULL DEFAULT '0' COMMENT '当前内容版本号，目录为0',
  `lifecycle_status` varchar(16) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态（ACTIVE ARCHIVED TRASH）',
  `create_user_id` bigint DEFAULT NULL COMMENT '创建用户ID',
  `update_user_id` bigint DEFAULT NULL COMMENT '最后修改用户ID',
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`page_id`),
  KEY `idx_kb_page_tree` (`space_id`,`parent_id`,`lifecycle_status`,`sort_order`),
  KEY `idx_kb_page_type_status` (`page_type`,`lifecycle_status`,`update_time`),
  KEY `idx_kb_page_update_user` (`update_user_id`,`update_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='知识目录与文章当前版本';

-- Table: kb_page_document
CREATE TABLE IF NOT EXISTS `kb_page_document` (
  `link_id` bigint NOT NULL AUTO_INCREMENT COMMENT '关联ID',
  `page_id` bigint NOT NULL COMMENT '知识文章ID',
  `document_id` bigint NOT NULL COMMENT '现有文档管理文档ID',
  `sort_order` int NOT NULL DEFAULT '0' COMMENT '展示顺序',
  `create_by` varchar(64) DEFAULT '' COMMENT '关联人',
  `create_time` datetime DEFAULT NULL COMMENT '关联时间',
  PRIMARY KEY (`link_id`),
  UNIQUE KEY `uk_kb_page_document` (`page_id`,`document_id`),
  KEY `idx_kb_document_page` (`document_id`,`page_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='知识文章与现有文档关联';

-- Table: kb_page_tag
CREATE TABLE IF NOT EXISTS `kb_page_tag` (
  `page_id` bigint NOT NULL COMMENT '知识文章ID',
  `tag_id` bigint NOT NULL COMMENT '标签ID',
  PRIMARY KEY (`page_id`,`tag_id`),
  KEY `idx_kb_page_tag_tag` (`tag_id`,`page_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='知识文章标签关系';

-- Table: kb_page_version
CREATE TABLE IF NOT EXISTS `kb_page_version` (
  `version_id` bigint NOT NULL AUTO_INCREMENT COMMENT '版本记录ID',
  `page_id` bigint NOT NULL COMMENT '知识文章ID',
  `version_no` int NOT NULL COMMENT '版本号',
  `snapshot_space_id` bigint NOT NULL COMMENT '版本知识空间ID',
  `snapshot_parent_id` bigint NOT NULL DEFAULT '0' COMMENT '版本上级目录ID',
  `snapshot_title` varchar(160) NOT NULL COMMENT '版本标题',
  `snapshot_summary` varchar(500) NOT NULL DEFAULT '' COMMENT '版本摘要',
  `snapshot_content` mediumtext NOT NULL COMMENT '版本正文HTML',
  `snapshot_lifecycle_status` varchar(16) NOT NULL COMMENT '版本状态',
  `snapshot_tags` text NOT NULL COMMENT '版本标签JSON',
  `snapshot_document_ids` text NOT NULL COMMENT '版本关联文档ID JSON',
  `operation_type` varchar(32) NOT NULL COMMENT '操作类型',
  `change_fields` varchar(500) NOT NULL DEFAULT '' COMMENT '变化字段',
  `change_note` varchar(500) NOT NULL DEFAULT '' COMMENT '修改说明',
  `content_checksum` varchar(64) NOT NULL COMMENT '版本SHA-256摘要',
  `operator_id` bigint DEFAULT NULL COMMENT '修改用户ID',
  `operator_name` varchar(64) NOT NULL DEFAULT '' COMMENT '修改用户名称',
  `restored_from_version` int DEFAULT NULL COMMENT '恢复来源版本号',
  `create_time` datetime DEFAULT NULL COMMENT '版本时间',
  PRIMARY KEY (`version_id`),
  UNIQUE KEY `uk_kb_page_version` (`page_id`,`version_no`),
  KEY `idx_kb_page_version_time` (`page_id`,`create_time`),
  KEY `idx_kb_version_operator` (`operator_id`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='知识文章不可覆盖版本记录';

-- Table: kb_space
CREATE TABLE IF NOT EXISTS `kb_space` (
  `space_id` bigint NOT NULL AUTO_INCREMENT COMMENT '知识空间ID',
  `space_name` varchar(100) NOT NULL COMMENT '知识空间名称',
  `description` varchar(500) NOT NULL DEFAULT '' COMMENT '知识空间说明',
  `sort_order` int NOT NULL DEFAULT '0' COMMENT '排序',
  `status` char(1) NOT NULL DEFAULT '0' COMMENT '状态（0正常 1停用）',
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`space_id`),
  UNIQUE KEY `uk_kb_space_name` (`space_name`),
  KEY `idx_kb_space_status_sort` (`status`,`sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='知识空间';

-- Table: kb_tag
CREATE TABLE IF NOT EXISTS `kb_tag` (
  `tag_id` bigint NOT NULL AUTO_INCREMENT COMMENT '标签ID',
  `tag_name` varchar(40) NOT NULL COMMENT '标签名称',
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  PRIMARY KEY (`tag_id`),
  UNIQUE KEY `uk_kb_tag_name` (`tag_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='知识标签';

-- Table: sup_auto_inspection_health_daily
CREATE TABLE IF NOT EXISTS `sup_auto_inspection_health_daily` (
  `summary_id` bigint NOT NULL AUTO_INCREMENT COMMENT '汇总ID',
  `health_date` date NOT NULL COMMENT '健康日期',
  `plan_id` bigint NOT NULL COMMENT '巡检计划ID',
  `plan_name` varchar(120) NOT NULL COMMENT '计划名称快照',
  `template_id` bigint DEFAULT NULL COMMENT '模板ID',
  `template_name` varchar(120) DEFAULT NULL COMMENT '模板名称快照',
  `scope_type` varchar(20) DEFAULT NULL COMMENT '健康归属类型快照',
  `site_id` bigint DEFAULT NULL COMMENT '所属现场ID快照',
  `site_name` varchar(100) DEFAULT NULL COMMENT '所属现场名称快照',
  `main_platform_id` bigint DEFAULT NULL COMMENT '所属主平台ID快照',
  `main_platform_name` varchar(120) DEFAULT NULL COMMENT '所属主平台名称快照',
  `expected_count` int DEFAULT '0' COMMENT '截至当前应执行次数',
  `completed_count` int DEFAULT '0' COMMENT '实际完成次数',
  `normal_count` int DEFAULT '0' COMMENT '正常采样次数',
  `warning_count` int DEFAULT '0' COMMENT '关注采样次数',
  `abnormal_count` int DEFAULT '0' COMMENT '异常采样次数',
  `skipped_count` int DEFAULT '0' COMMENT '未执行或跳过次数',
  `missing_count` int DEFAULT '0' COMMENT '缺失采样次数',
  `health_score` decimal(5,2) DEFAULT '0.00' COMMENT '当日健康度',
  `health_target` decimal(5,2) DEFAULT '99.00' COMMENT '健康目标',
  `day_status` char(1) DEFAULT '3' COMMENT '日状态（1正常 2异常 3无数据 4关注）',
  `first_abnormal_time` datetime DEFAULT NULL COMMENT '首次异常时间',
  `last_abnormal_time` datetime DEFAULT NULL COMMENT '最后异常时间',
  `last_run_time` datetime DEFAULT NULL COMMENT '最后运行时间',
  `last_result_status` char(1) DEFAULT NULL COMMENT '最后采样状态',
  `abnormal_summary` text COMMENT '最近异常摘要',
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`summary_id`),
  UNIQUE KEY `uk_sup_auto_health_day_plan` (`health_date`,`plan_id`),
  KEY `idx_sup_auto_health_plan_date` (`plan_id`,`health_date`),
  KEY `idx_sup_auto_health_status_date` (`day_status`,`health_date`),
  KEY `idx_sup_auto_health_site_date` (`site_id`,`health_date`),
  KEY `idx_sup_auto_health_main_date` (`main_platform_id`,`health_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='自动化巡检计划每日健康汇总';

-- Table: sup_auto_inspection_plan
CREATE TABLE IF NOT EXISTS `sup_auto_inspection_plan` (
  `plan_id` bigint NOT NULL AUTO_INCREMENT COMMENT '计划ID',
  `template_id` bigint NOT NULL COMMENT '模板ID',
  `plan_name` varchar(120) NOT NULL COMMENT '计划名称',
  `label_name` varchar(64) DEFAULT NULL COMMENT '标签名称',
  `plan_mode` varchar(16) DEFAULT 'ROUTINE' COMMENT '历史执行模式兼容字段（ROUTINE/FREQUENT）',
  `cron_expression` varchar(255) NOT NULL COMMENT '系统生成Cron表达式',
  `cron_config` text COMMENT '可视化周期配置JSON',
  `health_config` text COMMENT '每日健康汇总配置JSON',
  `scope_type` varchar(20) DEFAULT NULL COMMENT '健康归属类型（SITE现场 MAIN_PLATFORM主平台）',
  `site_id` bigint DEFAULT NULL COMMENT '所属现场ID',
  `site_name` varchar(100) DEFAULT NULL COMMENT '所属现场名称快照',
  `main_platform_id` bigint DEFAULT NULL COMMENT '所属主平台ID',
  `main_platform_name` varchar(120) DEFAULT NULL COMMENT '所属主平台名称快照',
  `job_id` bigint DEFAULT NULL COMMENT '若依定时任务ID',
  `report_style` varchar(32) DEFAULT 'STANDARD' COMMENT '报告样式',
  `status` char(1) DEFAULT '0' COMMENT '状态（0正常 1暂停）',
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`plan_id`),
  KEY `idx_sup_auto_plan_label_status` (`label_name`,`status`),
  KEY `idx_sup_auto_plan_template` (`template_id`),
  KEY `idx_sup_auto_plan_status` (`status`),
  KEY `idx_sup_auto_plan_site_status` (`site_id`,`status`),
  KEY `idx_sup_auto_plan_main_status` (`main_platform_id`,`status`),
  KEY `idx_sup_auto_plan_job` (`job_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='自动化巡检计划';

-- Table: sup_auto_inspection_probe_state
CREATE TABLE IF NOT EXISTS `sup_auto_inspection_probe_state` (
  `state_id` bigint NOT NULL AUTO_INCREMENT COMMENT '状态ID',
  `plan_id` bigint NOT NULL COMMENT '计划ID',
  `step_id` bigint NOT NULL COMMENT '模板步骤ID',
  `target_id` bigint NOT NULL COMMENT '目标ID',
  `tool_code` varchar(64) NOT NULL COMMENT '工具编码',
  `primary_value` decimal(30,2) DEFAULT NULL COMMENT '当前主观测值',
  `secondary_value` decimal(30,2) DEFAULT NULL COMMENT '当前辅助观测值',
  `observed_at` datetime DEFAULT NULL COMMENT '最近采样时间',
  `last_activity_at` datetime DEFAULT NULL COMMENT '最近活动时间',
  `comparison_scope` varchar(16) DEFAULT 'CONTINUOUS' COMMENT '比较范围（CONTINUOUS连续 DAY按天 HOUR按小时）',
  `window_key` varchar(64) DEFAULT NULL COMMENT '当前统计窗口标识',
  `window_start` datetime DEFAULT NULL COMMENT '当前统计窗口开始时间',
  `window_end` datetime DEFAULT NULL COMMENT '当前统计窗口有效结束时间',
  `abnormal_streak` int DEFAULT '0' COMMENT '连续异常次数',
  `normal_streak` int DEFAULT '0' COMMENT '连续恢复次数',
  `state_status` char(1) DEFAULT '3' COMMENT '状态（1正常 2异常 3基线 4关注）',
  `state_detail` varchar(1000) DEFAULT NULL COMMENT '状态说明',
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`state_id`),
  UNIQUE KEY `uk_sup_auto_probe_scope` (`plan_id`,`step_id`,`target_id`,`tool_code`),
  KEY `idx_sup_auto_probe_activity` (`last_activity_at`,`state_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='自动化巡检高频目标状态';

-- Table: sup_auto_inspection_record
CREATE TABLE IF NOT EXISTS `sup_auto_inspection_record` (
  `record_id` bigint NOT NULL AUTO_INCREMENT COMMENT '记录ID',
  `inspection_time` datetime NOT NULL COMMENT '巡检时间',
  `source_type` varchar(16) DEFAULT 'AUTO' COMMENT '执行来源（AUTO自动 MANUAL手动）',
  `run_mode` varchar(16) DEFAULT 'ROUTINE' COMMENT '历史执行模式兼容字段（ROUTINE/FREQUENT）',
  `schedule_slot_time` datetime DEFAULT NULL COMMENT '高频计划归一化采样时隙',
  `duration_ms` bigint DEFAULT NULL COMMENT '执行耗时毫秒',
  `result_status` char(1) DEFAULT '3' COMMENT '巡检结果（1正常 2异常 3未检测 4关注）',
  `executor_name` varchar(64) DEFAULT NULL COMMENT '执行人名称',
  `template_id` bigint DEFAULT NULL COMMENT '模板ID',
  `template_name` varchar(120) DEFAULT NULL COMMENT '模板名称',
  `plan_id` bigint DEFAULT NULL COMMENT '计划ID',
  `plan_name` varchar(120) DEFAULT NULL COMMENT '计划名称',
  `scope_type` varchar(20) DEFAULT NULL COMMENT '执行时健康归属类型快照',
  `site_id` bigint DEFAULT NULL COMMENT '执行时所属现场ID快照',
  `site_name` varchar(100) DEFAULT NULL COMMENT '执行时所属现场名称快照',
  `main_platform_id` bigint DEFAULT NULL COMMENT '执行时所属主平台ID快照',
  `main_platform_name` varchar(120) DEFAULT NULL COMMENT '执行时所属主平台名称快照',
  `report_style` varchar(32) DEFAULT 'STANDARD' COMMENT '报告样式',
  `enabled_step_count` int DEFAULT '0' COMMENT '启用步骤数',
  `skipped_step_count` int DEFAULT '0' COMMENT '跳过步骤数',
  `target_count` int DEFAULT '0' COMMENT '检测目标数',
  `abnormal_count` int DEFAULT '0' COMMENT '异常步骤数',
  `warning_count` int DEFAULT '0' COMMENT '关注步骤数',
  `summary` varchar(500) DEFAULT NULL COMMENT '巡检摘要',
  `abnormal_summary` text COMMENT '异常摘要',
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`record_id`),
  UNIQUE KEY `uk_sup_auto_record_plan_slot` (`plan_id`,`schedule_slot_time`),
  KEY `idx_sup_auto_record_time` (`inspection_time`),
  KEY `idx_sup_auto_record_result` (`result_status`,`inspection_time`),
  KEY `idx_sup_auto_record_plan` (`plan_id`,`inspection_time`),
  KEY `idx_sup_auto_record_site_time` (`site_id`,`inspection_time`),
  KEY `idx_sup_auto_record_main_time` (`main_platform_id`,`inspection_time`),
  KEY `idx_sup_auto_record_mode_time` (`run_mode`,`inspection_time`),
  KEY `idx_sup_auto_record_template` (`template_id`,`inspection_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='自动化巡检记录';

-- Table: sup_auto_inspection_step_result
CREATE TABLE IF NOT EXISTS `sup_auto_inspection_step_result` (
  `step_result_id` bigint NOT NULL AUTO_INCREMENT COMMENT '步骤结果ID',
  `record_id` bigint NOT NULL COMMENT '记录ID',
  `step_id` bigint DEFAULT NULL COMMENT '模板步骤ID',
  `tool_code` varchar(64) NOT NULL COMMENT '工具编码',
  `tool_name` varchar(120) DEFAULT NULL COMMENT '工具名称',
  `tool_type` varchar(64) NOT NULL COMMENT '工具类型',
  `step_name` varchar(120) NOT NULL COMMENT '步骤名称',
  `enabled_flag` char(1) DEFAULT 'Y' COMMENT '是否启用',
  `sort_order` int DEFAULT '0' COMMENT '排序',
  `threshold_value` decimal(18,2) DEFAULT NULL COMMENT '告警阈值',
  `threshold_unit` varchar(32) DEFAULT NULL COMMENT '阈值单位',
  `compare_rule` varchar(16) DEFAULT 'MAX' COMMENT '比较规则',
  `time_window_minutes` int DEFAULT '0' COMMENT '统计时间窗口分钟数',
  `timeout_seconds` int DEFAULT '10' COMMENT '超时时间秒',
  `step_params` text COMMENT '步骤参数JSON',
  `result_status` char(1) DEFAULT '3' COMMENT '步骤结果（1正常 2异常 3未检测 4关注）',
  `actual_value` decimal(18,2) DEFAULT NULL COMMENT '代表实际值',
  `actual_unit` varchar(32) DEFAULT NULL COMMENT '实际单位',
  `result_summary` varchar(1000) DEFAULT NULL COMMENT '结果摘要',
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`step_result_id`),
  KEY `idx_sup_auto_step_result_record` (`record_id`,`sort_order`),
  KEY `idx_sup_auto_step_result_tool` (`tool_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='自动化巡检步骤结果';

-- Table: sup_auto_inspection_target
CREATE TABLE IF NOT EXISTS `sup_auto_inspection_target` (
  `target_id` bigint NOT NULL AUTO_INCREMENT COMMENT '目标ID',
  `target_name` varchar(120) NOT NULL COMMENT '目标名称',
  `target_type` varchar(32) NOT NULL COMMENT '目标类型（KAFKA HTTP FTP SERVER）',
  `server_id` bigint DEFAULT NULL COMMENT '关联服务器ID',
  `host` varchar(255) DEFAULT NULL COMMENT '主机或Kafka bootstrap',
  `port` int DEFAULT NULL COMMENT '端口',
  `path` varchar(500) DEFAULT NULL COMMENT '默认目录路径或挂载点',
  `url` varchar(1000) DEFAULT NULL COMMENT 'HTTP接口地址',
  `http_method` varchar(10) DEFAULT NULL COMMENT 'HTTP方法',
  `topic` varchar(200) DEFAULT NULL COMMENT 'Kafka Topic',
  `consumer_group` varchar(200) DEFAULT NULL COMMENT 'Kafka消费组',
  `username` varchar(128) DEFAULT NULL COMMENT '登录账号',
  `password_cipher` varchar(1024) DEFAULT NULL COMMENT '登录密码密文',
  `app_key` varchar(128) DEFAULT NULL COMMENT '接口AppKey',
  `secret_cipher` varchar(1024) DEFAULT NULL COMMENT '接口密钥密文',
  `result_path` varchar(200) DEFAULT NULL COMMENT 'HTTP结果路径',
  `extra_params` text COMMENT '扩展参数或请求体模板',
  `status` char(1) DEFAULT '0' COMMENT '状态（0正常 1停用）',
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`target_id`),
  KEY `idx_sup_auto_target_type` (`target_type`,`status`),
  KEY `idx_sup_auto_target_server` (`server_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='自动化巡检目标';

-- Table: sup_auto_inspection_target_result
CREATE TABLE IF NOT EXISTS `sup_auto_inspection_target_result` (
  `result_id` bigint NOT NULL AUTO_INCREMENT COMMENT '目标结果ID',
  `record_id` bigint NOT NULL COMMENT '记录ID',
  `step_result_id` bigint NOT NULL COMMENT '步骤结果ID',
  `target_id` bigint DEFAULT NULL COMMENT '目标ID',
  `target_name` varchar(120) DEFAULT NULL COMMENT '目标名称',
  `target_type` varchar(32) DEFAULT NULL COMMENT '目标类型',
  `result_status` char(1) DEFAULT '3' COMMENT '目标结果（1正常 2异常 3未执行 4关注）',
  `actual_value` decimal(18,2) DEFAULT NULL COMMENT '实际值',
  `actual_unit` varchar(32) DEFAULT NULL COMMENT '实际单位',
  `evaluation_mode` varchar(16) DEFAULT 'FIXED' COMMENT '判定方式（FIXED固定阈值 PREVIOUS上次结果）',
  `previous_value` decimal(30,2) DEFAULT NULL COMMENT '上次采样值',
  `change_value` decimal(30,2) DEFAULT NULL COMMENT '本次与上次变化量',
  `evaluation_rule` varchar(500) DEFAULT NULL COMMENT '本次判定公式',
  `baseline_flag` char(1) DEFAULT 'N' COMMENT '是否本次建立基线（Y是 N否）',
  `comparison_scope` varchar(16) DEFAULT 'CONTINUOUS' COMMENT '比较范围（CONTINUOUS连续 DAY按天 HOUR按小时）',
  `window_key` varchar(64) DEFAULT NULL COMMENT '本次统计窗口标识',
  `window_start` datetime DEFAULT NULL COMMENT '本次统计窗口开始时间',
  `window_end` datetime DEFAULT NULL COMMENT '本次统计窗口有效结束时间',
  `result_detail` mediumtext COMMENT '结果详情',
  `error_message` mediumtext COMMENT '异常原因',
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`result_id`),
  KEY `idx_sup_auto_target_result_record` (`record_id`,`step_result_id`),
  KEY `idx_sup_auto_target_result_target` (`target_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='自动化巡检目标结果';

-- Table: sup_auto_inspection_template
CREATE TABLE IF NOT EXISTS `sup_auto_inspection_template` (
  `template_id` bigint NOT NULL AUTO_INCREMENT COMMENT '模板ID',
  `template_name` varchar(120) NOT NULL COMMENT '模板名称',
  `label_name` varchar(64) DEFAULT NULL COMMENT '标签名称',
  `template_desc` varchar(500) DEFAULT NULL COMMENT '模板描述',
  `status` char(1) DEFAULT '0' COMMENT '状态（0正常 1停用）',
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`template_id`),
  KEY `idx_sup_auto_template_label_status` (`label_name`,`status`),
  KEY `idx_sup_auto_template_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='自动化巡检模板';

-- Table: sup_auto_inspection_template_step
CREATE TABLE IF NOT EXISTS `sup_auto_inspection_template_step` (
  `step_id` bigint NOT NULL AUTO_INCREMENT COMMENT '模板步骤ID',
  `template_id` bigint NOT NULL COMMENT '模板ID',
  `tool_code` varchar(64) NOT NULL COMMENT '工具编码',
  `step_name` varchar(120) NOT NULL COMMENT '步骤名称',
  `enabled_flag` char(1) DEFAULT 'Y' COMMENT '是否启用（Y是 N否）',
  `sort_order` int DEFAULT '0' COMMENT '排序',
  `threshold_value` decimal(18,2) DEFAULT NULL COMMENT '告警阈值',
  `threshold_unit` varchar(32) DEFAULT NULL COMMENT '阈值单位',
  `compare_rule` varchar(16) DEFAULT 'MAX' COMMENT '比较规则（MIN最低阈值 MAX最高阈值）',
  `time_window_minutes` int DEFAULT '0' COMMENT '统计时间窗口分钟数',
  `timeout_seconds` int DEFAULT '10' COMMENT '超时时间秒',
  `step_params` text COMMENT '步骤参数JSON',
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`step_id`),
  KEY `idx_sup_auto_step_template` (`template_id`,`sort_order`),
  KEY `idx_sup_auto_step_tool` (`tool_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='自动化巡检模板步骤';

-- Table: sup_auto_inspection_template_step_target
CREATE TABLE IF NOT EXISTS `sup_auto_inspection_template_step_target` (
  `step_target_id` bigint NOT NULL AUTO_INCREMENT COMMENT '步骤目标关系ID',
  `step_id` bigint NOT NULL COMMENT '模板步骤ID',
  `target_id` bigint NOT NULL COMMENT '目标ID',
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`step_target_id`),
  UNIQUE KEY `uk_sup_auto_step_target` (`step_id`,`target_id`),
  KEY `idx_sup_auto_step_target_step` (`step_id`),
  KEY `idx_sup_auto_step_target_target` (`target_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='自动化巡检模板步骤目标关系';

-- Table: sup_auto_inspection_tool
CREATE TABLE IF NOT EXISTS `sup_auto_inspection_tool` (
  `tool_code` varchar(64) NOT NULL COMMENT '工具编码',
  `tool_name` varchar(120) NOT NULL COMMENT '工具名称',
  `tool_type` varchar(64) NOT NULL COMMENT '工具类型',
  `value_unit` varchar(32) DEFAULT NULL COMMENT '结果单位',
  `default_compare_rule` varchar(16) DEFAULT 'MAX' COMMENT '默认比较规则（MIN最低阈值 MAX最高阈值）',
  `default_threshold_value` decimal(18,2) DEFAULT NULL COMMENT '默认阈值',
  `default_timeout_seconds` int DEFAULT '10' COMMENT '默认超时秒数',
  `default_time_window_minutes` int DEFAULT '0' COMMENT '默认统计窗口分钟数',
  `param_schema` text COMMENT '参数结构描述',
  `built_in_flag` char(1) DEFAULT 'Y' COMMENT '是否内置（Y是 N否）',
  `status` char(1) DEFAULT '0' COMMENT '状态（0正常 1停用）',
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`tool_code`),
  KEY `idx_sup_auto_tool_type` (`tool_type`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='自动化巡检工具定义';

-- Table: sup_change_log
CREATE TABLE IF NOT EXISTS `sup_change_log` (
  `log_id` bigint NOT NULL AUTO_INCREMENT COMMENT '记录ID',
  `site_id` bigint DEFAULT NULL COMMENT '现场ID',
  `action_type` varchar(16) NOT NULL COMMENT '操作类型（INSERT/UPDATE/DELETE）',
  `target_type` varchar(32) NOT NULL COMMENT '对象类型',
  `target_id` bigint DEFAULT NULL COMMENT '对象ID',
  `target_name` varchar(200) DEFAULT NULL COMMENT '对象名称',
  `summary` varchar(500) DEFAULT NULL COMMENT '操作摘要',
  `detail_content` text COMMENT '操作详情',
  `operator_name` varchar(64) DEFAULT NULL COMMENT '操作用户',
  `operator_ip` varchar(64) DEFAULT NULL COMMENT '操作IP',
  `create_time` datetime DEFAULT NULL COMMENT '操作时间',
  PRIMARY KEY (`log_id`),
  KEY `idx_sup_change_log_site_time` (`site_id`,`create_time`),
  KEY `idx_sup_change_log_target` (`target_type`,`target_id`),
  KEY `idx_sup_change_log_operator_action_site` (`operator_name`,`action_type`,`site_id`,`log_id`),
  KEY `idx_sup_change_log_action_time` (`action_type`,`create_time`,`log_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='现场融合用户修改记录';

-- Table: sup_contact
CREATE TABLE IF NOT EXISTS `sup_contact` (
  `contact_id` bigint NOT NULL AUTO_INCREMENT COMMENT '联系人ID',
  `org_id` bigint NOT NULL COMMENT '组织ID',
  `contact_name` varchar(80) NOT NULL COMMENT '联系人姓名',
  `role_type` varchar(32) DEFAULT NULL COMMENT '角色（support_contact_role字典）',
  `phone` varchar(30) DEFAULT NULL COMMENT '手机号',
  `email` varchar(120) DEFAULT NULL COMMENT '邮箱',
  `wechat` varchar(80) DEFAULT NULL COMMENT '微信',
  `is_primary` char(1) DEFAULT '0' COMMENT '是否主联系人（0否 1是）',
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`contact_id`),
  KEY `idx_sup_contact_org` (`org_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='联系人';

-- Table: sup_equipment_cabinet
CREATE TABLE IF NOT EXISTS `sup_equipment_cabinet` (
  `cabinet_id` bigint NOT NULL AUTO_INCREMENT COMMENT '机柜ID',
  `room_id` bigint NOT NULL COMMENT '机房ID',
  `site_id` bigint NOT NULL COMMENT '现场ID',
  `cabinet_no` varchar(80) NOT NULL COMMENT '机柜编号',
  `u_capacity` int DEFAULT '45' COMMENT '机柜U数',
  `position_x` decimal(8,2) DEFAULT NULL COMMENT '机柜平面X坐标（米）',
  `position_z` decimal(8,2) DEFAULT NULL COMMENT '机柜平面Z坐标（米）',
  `rotation_y` decimal(6,1) DEFAULT '0.0' COMMENT '机柜Y轴朝向角度',
  `status` char(1) DEFAULT '0' COMMENT '状态（0正常 1停用）',
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`cabinet_id`),
  UNIQUE KEY `uk_sup_equipment_cabinet_room_no` (`room_id`,`cabinet_no`),
  KEY `idx_sup_equipment_cabinet_site` (`site_id`),
  KEY `idx_sup_equipment_cabinet_room` (`room_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='现场设备机柜';

-- Table: sup_equipment_link
CREATE TABLE IF NOT EXISTS `sup_equipment_link` (
  `link_id` bigint NOT NULL AUTO_INCREMENT COMMENT '设备链路ID',
  `site_id` bigint NOT NULL COMMENT '现场ID',
  `source_type` varchar(16) NOT NULL COMMENT '源设备类型（SERVER/HARDWARE）',
  `source_id` bigint NOT NULL COMMENT '源设备ID',
  `target_type` varchar(16) NOT NULL DEFAULT 'HARDWARE' COMMENT '目标设备类型',
  `target_id` bigint NOT NULL COMMENT '目标交换机资产ID',
  `medium_type` varchar(16) NOT NULL COMMENT '链路介质（OPTICAL/ELECTRICAL）',
  `port_count` int NOT NULL DEFAULT '1' COMMENT '占用端口数量',
  `source_port` varchar(80) DEFAULT NULL COMMENT '源端口说明',
  `target_port` varchar(80) DEFAULT NULL COMMENT '目标端口说明',
  `status` char(1) DEFAULT '0' COMMENT '状态（0正常 1停用）',
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`link_id`),
  KEY `idx_sup_equipment_link_site` (`site_id`),
  KEY `idx_sup_equipment_link_source` (`source_type`,`source_id`),
  KEY `idx_sup_equipment_link_target` (`target_type`,`target_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='现场设备物理上联关系';

-- Table: sup_equipment_room
CREATE TABLE IF NOT EXISTS `sup_equipment_room` (
  `room_id` bigint NOT NULL AUTO_INCREMENT COMMENT '机房ID',
  `site_id` bigint NOT NULL COMMENT '现场ID',
  `room_name` varchar(120) NOT NULL COMMENT '机房名称',
  `room_code` varchar(80) DEFAULT NULL COMMENT '机房编码',
  `room_width` decimal(8,2) DEFAULT '12.00' COMMENT '机房宽度（米）',
  `room_depth` decimal(8,2) DEFAULT '8.00' COMMENT '机房深度（米）',
  `status` char(1) DEFAULT '0' COMMENT '状态（0正常 1停用）',
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`room_id`),
  KEY `idx_sup_equipment_room_site` (`site_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='现场设备机房';

-- Table: sup_hardware_asset
CREATE TABLE IF NOT EXISTS `sup_hardware_asset` (
  `asset_id` bigint NOT NULL AUTO_INCREMENT COMMENT '硬件资产ID',
  `site_id` bigint NOT NULL COMMENT '现场ID',
  `asset_name` varchar(120) NOT NULL COMMENT '资产名称',
  `asset_type` varchar(32) NOT NULL COMMENT '资产类型（DECODER/TERMINAL/SWITCH/GATEWAY）',
  `network_env` varchar(100) NOT NULL COMMENT '网络环境',
  `ip_address` varchar(255) NOT NULL COMMENT 'IP地址',
  `manage_ip` varchar(255) DEFAULT NULL COMMENT '管理地址',
  `mac_address` varchar(64) DEFAULT NULL COMMENT 'MAC地址',
  `manufacturer` varchar(100) DEFAULT NULL COMMENT '厂商',
  `asset_model` varchar(120) DEFAULT NULL COMMENT '型号',
  `serial_no` varchar(120) DEFAULT NULL COMMENT '序列号',
  `install_location` varchar(200) DEFAULT NULL COMMENT '安装位置',
  `equipment_room` varchar(100) DEFAULT NULL COMMENT '所属机房',
  `cabinet_no` varchar(80) DEFAULT NULL COMMENT '机柜编号',
  `rack_u_start` int DEFAULT NULL COMMENT '起始U位',
  `rack_u_end` int DEFAULT NULL COMMENT '结束U位',
  `owner_org` varchar(160) DEFAULT NULL COMMENT '归属组织',
  `owner_contact` varchar(80) DEFAULT NULL COMMENT '责任人',
  `login_username` varchar(128) DEFAULT NULL COMMENT '设备登录账号',
  `login_password_cipher` varchar(1024) DEFAULT NULL COMMENT '设备登录密码密文',
  `channel_count` int DEFAULT NULL COMMENT '解码器通道数',
  `output_type` varchar(80) DEFAULT NULL COMMENT '解码器输出类型',
  `terminal_type` varchar(80) DEFAULT NULL COMMENT '终端类型',
  `department` varchar(120) DEFAULT NULL COMMENT '使用部门',
  `use_location` varchar(200) DEFAULT NULL COMMENT '使用位置',
  `switch_level` varchar(80) DEFAULT NULL COMMENT '交换机层级',
  `port_count` int DEFAULT NULL COMMENT '端口数',
  `uplink_device` varchar(160) DEFAULT NULL COMMENT '上联设备',
  `vlan_info` varchar(500) DEFAULT NULL COMMENT 'VLAN说明',
  `gateway_mode` varchar(80) DEFAULT NULL COMMENT '网闸模式',
  `gateway_direction` varchar(120) DEFAULT NULL COMMENT '网闸数据流向',
  `gateway_bandwidth` varchar(80) DEFAULT NULL COMMENT '网闸带宽',
  `security_zone` varchar(200) DEFAULT NULL COMMENT '安全域说明',
  `status` char(1) DEFAULT '0' COMMENT '状态（0正常 1停用）',
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`asset_id`),
  KEY `idx_sup_hardware_site` (`site_id`),
  KEY `idx_sup_hardware_site_type` (`site_id`,`asset_type`),
  KEY `idx_sup_hardware_site_network` (`site_id`,`network_env`),
  KEY `idx_sup_hardware_site_ip` (`site_id`,`ip_address`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='现场硬件资产';

-- Table: sup_org
CREATE TABLE IF NOT EXISTS `sup_org` (
  `org_id` bigint NOT NULL AUTO_INCREMENT COMMENT '组织ID',
  `org_type` varchar(16) NOT NULL COMMENT '组织类型（CUSTOMER/USER/THIRD_VENDOR）',
  `org_name` varchar(160) NOT NULL COMMENT '组织名称',
  `short_name` varchar(80) DEFAULT NULL COMMENT '简称',
  `status` char(1) DEFAULT '0' COMMENT '状态（0正常 1停用）',
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`org_id`),
  KEY `idx_sup_org_type` (`org_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='客户组织';

-- Table: sup_platform
CREATE TABLE IF NOT EXISTS `sup_platform` (
  `platform_id` bigint NOT NULL AUTO_INCREMENT COMMENT '平台ID',
  `site_id` bigint NOT NULL COMMENT '现场ID',
  `platform_name` varchar(120) NOT NULL COMMENT '平台名称',
  `platform_level` varchar(16) NOT NULL COMMENT '平台级别（MAIN/SUB）',
  `network_env` varchar(100) DEFAULT NULL COMMENT '网络环境',
  `parent_platform_id` bigint DEFAULT NULL COMMENT '父平台ID',
  `status` char(1) DEFAULT '0' COMMENT '状态（0正常 1停用）',
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`platform_id`),
  KEY `idx_sup_platform_site` (`site_id`),
  KEY `idx_sup_platform_parent` (`parent_platform_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='平台信息（主/子）';

-- Table: sup_platform_asset_rel
CREATE TABLE IF NOT EXISTS `sup_platform_asset_rel` (
  `rel_id` bigint NOT NULL AUTO_INCREMENT COMMENT '关系ID',
  `platform_id` bigint NOT NULL COMMENT '平台ID',
  `asset_id` bigint NOT NULL COMMENT '硬件资产ID',
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  PRIMARY KEY (`rel_id`),
  UNIQUE KEY `uk_sup_platform_asset` (`platform_id`,`asset_id`),
  KEY `idx_sup_platform_asset_asset` (`asset_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='平台-硬件资产关系';

-- Table: sup_platform_contact_rel
CREATE TABLE IF NOT EXISTS `sup_platform_contact_rel` (
  `rel_id` bigint NOT NULL AUTO_INCREMENT COMMENT '关系ID',
  `platform_id` bigint NOT NULL COMMENT '主平台ID',
  `contact_id` bigint NOT NULL COMMENT '联系人ID',
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  PRIMARY KEY (`rel_id`),
  UNIQUE KEY `uk_sup_platform_contact` (`platform_id`,`contact_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='主平台-联系人关系';

-- Table: sup_platform_org_rel
CREATE TABLE IF NOT EXISTS `sup_platform_org_rel` (
  `rel_id` bigint NOT NULL AUTO_INCREMENT COMMENT '关系ID',
  `platform_id` bigint NOT NULL COMMENT '平台ID',
  `org_id` bigint NOT NULL COMMENT '组织ID',
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  PRIMARY KEY (`rel_id`),
  UNIQUE KEY `uk_sup_platform_org` (`platform_id`,`org_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='平台-组织关系（兼容保留）';

-- Table: sup_platform_server_rel
CREATE TABLE IF NOT EXISTS `sup_platform_server_rel` (
  `rel_id` bigint NOT NULL AUTO_INCREMENT COMMENT '关系ID',
  `platform_id` bigint NOT NULL COMMENT '平台ID',
  `server_id` bigint NOT NULL COMMENT '服务器ID',
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  PRIMARY KEY (`rel_id`),
  UNIQUE KEY `uk_sup_platform_server` (`platform_id`,`server_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='平台-服务器关系';

-- Table: sup_server
CREATE TABLE IF NOT EXISTS `sup_server` (
  `server_id` bigint NOT NULL AUTO_INCREMENT COMMENT '服务器ID',
  `site_id` bigint NOT NULL COMMENT '现场ID',
  `server_name` varchar(120) NOT NULL COMMENT '服务器名称',
  `server_address` varchar(255) NOT NULL COMMENT '服务器地址',
  `ssh_port` int DEFAULT '22' COMMENT 'SSH端口',
  `root_direct_login` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否root直接登录',
  `os_type` varchar(64) DEFAULT NULL COMMENT '操作系统类型',
  `equipment_room` varchar(100) DEFAULT NULL COMMENT '所属机房',
  `cabinet_no` varchar(80) DEFAULT NULL COMMENT '机柜编号',
  `rack_u_start` int DEFAULT NULL COMMENT '起始U位',
  `rack_u_end` int DEFAULT NULL COMMENT '结束U位',
  `os_username` varchar(128) DEFAULT NULL COMMENT '系统登录账号',
  `os_password_cipher` varchar(1024) DEFAULT NULL COMMENT '系统登录密码密文',
  `status` char(1) DEFAULT '0' COMMENT '状态（0正常 1停用）',
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`server_id`),
  KEY `idx_sup_server_site` (`site_id`),
  KEY `idx_sup_server_site_address` (`site_id`,`server_address`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='服务器信息';

-- Table: sup_server_credential
CREATE TABLE IF NOT EXISTS `sup_server_credential` (
  `credential_id` bigint NOT NULL AUTO_INCREMENT COMMENT '凭据ID',
  `server_id` bigint NOT NULL COMMENT '服务器ID',
  `credential_name` varchar(120) NOT NULL COMMENT '凭据名称',
  `username` varchar(128) NOT NULL COMMENT '登录账号',
  `password_cipher` varchar(1024) DEFAULT NULL COMMENT '登录密码密文',
  `purpose` varchar(120) DEFAULT NULL COMMENT '用途',
  `is_default` char(1) DEFAULT '0' COMMENT '是否默认（0否 1是）',
  `status` char(1) DEFAULT '0' COMMENT '状态（0正常 1停用）',
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`credential_id`),
  KEY `idx_sup_server_credential_server` (`server_id`),
  KEY `idx_sup_server_credential_status` (`server_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='服务器多凭据档案';

-- Table: sup_server_scan_credential
CREATE TABLE IF NOT EXISTS `sup_server_scan_credential` (
  `credential_id` bigint NOT NULL AUTO_INCREMENT,
  `site_id` bigint NOT NULL,
  `credential_name` varchar(80) NOT NULL,
  `login_mode` varchar(20) NOT NULL COMMENT 'ROOT_DIRECT/HIK_SU_ROOT',
  `hik_password_cipher` varchar(1024) NOT NULL DEFAULT '',
  `root_password_cipher` varchar(1024) NOT NULL DEFAULT '',
  `password_cipher` varchar(1024) NOT NULL DEFAULT '' COMMENT 'Independent authorized candidate password (AES encrypted)',
  `create_by` varchar(64) DEFAULT '',
  `create_time` datetime DEFAULT NULL,
  `update_by` varchar(64) DEFAULT '',
  `update_time` datetime DEFAULT NULL,
  PRIMARY KEY (`credential_id`),
  KEY `idx_server_scan_credential_site` (`site_id`),
  CONSTRAINT `fk_server_scan_credential_site` FOREIGN KEY (`site_id`) REFERENCES `sup_site` (`site_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='现场授权扫描凭据池（AES加密）';

-- Table: sup_server_scan_profile
CREATE TABLE IF NOT EXISTS `sup_server_scan_profile` (
  `server_id` bigint NOT NULL,
  `site_id` bigint NOT NULL,
  `host_name` varchar(255) NOT NULL DEFAULT '',
  `os_name` varchar(120) NOT NULL DEFAULT '',
  `os_version` varchar(120) NOT NULL DEFAULT '',
  `kernel_version` varchar(120) NOT NULL DEFAULT '',
  `detected_ports` varchar(64) NOT NULL DEFAULT '',
  `scan_time` datetime DEFAULT NULL,
  PRIMARY KEY (`server_id`),
  KEY `idx_server_scan_profile_site` (`site_id`),
  CONSTRAINT `fk_server_scan_profile_server` FOREIGN KEY (`server_id`) REFERENCES `sup_server` (`server_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='服务器最后成功扫描的系统资料';

-- Table: sup_site
CREATE TABLE IF NOT EXISTS `sup_site` (
  `site_id` bigint NOT NULL AUTO_INCREMENT COMMENT '现场ID',
  `site_name` varchar(100) NOT NULL COMMENT '现场名称',
  `site_code` varchar(64) DEFAULT NULL COMMENT '现场编码',
  `province_code` varchar(32) DEFAULT NULL COMMENT '省级行政区编码',
  `province_name` varchar(64) DEFAULT NULL COMMENT '省级行政区名称',
  `city_code` varchar(32) DEFAULT NULL COMMENT '市级行政区编码',
  `city_name` varchar(64) DEFAULT NULL COMMENT '市级行政区名称',
  `district_code` varchar(32) DEFAULT NULL COMMENT '区县行政区编码',
  `district_name` varchar(64) DEFAULT NULL COMMENT '区县行政区名称',
  `location` varchar(255) DEFAULT NULL COMMENT '现场地址',
  `description` varchar(500) DEFAULT NULL COMMENT '现场描述',
  `status` char(1) DEFAULT '0' COMMENT '状态（0正常 1停用）',
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`site_id`),
  UNIQUE KEY `uk_sup_site_code` (`site_code`),
  KEY `idx_sup_site_create_by` (`create_by`,`site_id`),
  KEY `idx_sup_site_update_by` (`update_by`,`site_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='现场信息';

-- Table: sup_site_message
CREATE TABLE IF NOT EXISTS `sup_site_message` (
  `message_id` bigint NOT NULL AUTO_INCREMENT COMMENT '留言ID',
  `site_id` bigint NOT NULL COMMENT '现场ID',
  `message_content` varchar(300) NOT NULL COMMENT '留言内容',
  `publisher_id` bigint DEFAULT NULL COMMENT '发布用户ID',
  `publisher_name` varchar(64) DEFAULT NULL COMMENT '发布用户昵称',
  `status` char(1) DEFAULT '0' COMMENT '状态（0正常 1停用）',
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`message_id`),
  KEY `idx_sup_site_message_site_time` (`site_id`,`create_time`),
  KEY `idx_sup_site_message_site_status_id` (`site_id`,`status`,`message_id`),
  KEY `idx_sup_site_message_publisher` (`publisher_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='现场留言板';

-- Table: sup_subplatform_endpoint
CREATE TABLE IF NOT EXISTS `sup_subplatform_endpoint` (
  `endpoint_id` bigint NOT NULL AUTO_INCREMENT COMMENT '页面ID',
  `sub_platform_id` bigint NOT NULL COMMENT '子平台ID',
  `endpoint_name` varchar(120) DEFAULT NULL COMMENT '页面名称',
  `access_url` varchar(500) NOT NULL COMMENT '访问URL',
  `login_username` varchar(128) DEFAULT NULL COMMENT '登录账号',
  `login_password_cipher` varchar(1024) DEFAULT NULL COMMENT '登录密码密文',
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`endpoint_id`),
  KEY `idx_sup_endpoint_sub_platform` (`sub_platform_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='子平台页面信息';

-- Table: sup_tim_inspection
CREATE TABLE IF NOT EXISTS `sup_tim_inspection` (
  `inspection_id` bigint NOT NULL AUTO_INCREMENT COMMENT '巡检ID',
  `inspection_time` datetime NOT NULL COMMENT '巡检时间',
  `inspection_type` varchar(64) DEFAULT 'TIM_GA_VEHICLE' COMMENT '巡检类型',
  `source_type` varchar(16) DEFAULT 'AUTO' COMMENT '执行来源（AUTO自动 MANUAL手动）',
  `result_status` char(1) DEFAULT '3' COMMENT '巡检结果（1正常 2异常 3未检测）',
  `executor_name` varchar(64) DEFAULT NULL COMMENT '执行人名称',
  `plan_id` bigint DEFAULT NULL COMMENT '巡检计划ID',
  `plan_name` varchar(120) DEFAULT NULL COMMENT '巡检计划名称',
  `report_style` varchar(32) DEFAULT 'STANDARD' COMMENT '巡检报告样式',
  `enabled_item_count` int DEFAULT '0' COMMENT '启用项数',
  `skipped_item_count` int DEFAULT '0' COMMENT '跳过项数',
  `summary` varchar(500) DEFAULT NULL COMMENT '巡检摘要',
  `abnormal_summary` text COMMENT '异常摘要',
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`inspection_id`),
  KEY `idx_sup_tim_inspection_time` (`inspection_time`),
  KEY `idx_sup_tim_inspection_result` (`result_status`,`inspection_time`),
  KEY `idx_sup_tim_inspection_source` (`source_type`,`inspection_time`),
  KEY `idx_sup_tim_inspection_plan` (`plan_id`,`inspection_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='TIM系统巡检记录';

-- Table: sup_tim_inspection_item
CREATE TABLE IF NOT EXISTS `sup_tim_inspection_item` (
  `item_id` bigint NOT NULL AUTO_INCREMENT COMMENT '巡检项结果ID',
  `inspection_id` bigint NOT NULL COMMENT '巡检ID',
  `item_code` varchar(64) NOT NULL COMMENT '巡检项编码',
  `item_name` varchar(100) NOT NULL COMMENT '巡检项名称',
  `item_type` varchar(32) NOT NULL COMMENT '巡检项类型',
  `enabled_flag` char(1) DEFAULT 'Y' COMMENT '执行时是否启用（Y是 N否）',
  `sort_order` int DEFAULT '0' COMMENT '排序',
  `threshold_value` decimal(18,2) DEFAULT NULL COMMENT '阈值',
  `threshold_unit` varchar(32) DEFAULT NULL COMMENT '阈值单位',
  `compare_rule` varchar(16) DEFAULT 'MAX' COMMENT '比较规则（MIN最低阈值 MAX最高阈值）',
  `time_window_minutes` int DEFAULT '0' COMMENT '统计时间窗口分钟数',
  `timeout_seconds` int DEFAULT '10' COMMENT '超时时间秒',
  `result_status` char(1) DEFAULT '3' COMMENT '巡检项结果（1正常 2异常 3未检测）',
  `actual_value` decimal(18,2) DEFAULT NULL COMMENT '实际代表值',
  `actual_unit` varchar(32) DEFAULT NULL COMMENT '实际值单位',
  `result_summary` varchar(1000) DEFAULT NULL COMMENT '结果摘要',
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`item_id`),
  KEY `idx_sup_tim_item_inspection` (`inspection_id`,`sort_order`),
  KEY `idx_sup_tim_item_code` (`item_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='TIM系统巡检项结果';

-- Table: sup_tim_inspection_item_config
CREATE TABLE IF NOT EXISTS `sup_tim_inspection_item_config` (
  `config_id` bigint NOT NULL AUTO_INCREMENT COMMENT '配置ID',
  `item_code` varchar(64) NOT NULL COMMENT '巡检项编码',
  `item_name` varchar(100) NOT NULL COMMENT '巡检项名称',
  `item_type` varchar(32) NOT NULL COMMENT '巡检项类型',
  `enabled_flag` char(1) DEFAULT 'Y' COMMENT '是否启用（Y是 N否）',
  `sort_order` int DEFAULT '0' COMMENT '排序',
  `threshold_value` decimal(18,2) DEFAULT NULL COMMENT '阈值',
  `threshold_unit` varchar(32) DEFAULT NULL COMMENT '阈值单位',
  `compare_rule` varchar(16) DEFAULT 'MAX' COMMENT '比较规则（MIN最低阈值 MAX最高阈值）',
  `time_window_minutes` int DEFAULT '0' COMMENT '统计时间窗口分钟数',
  `timeout_seconds` int DEFAULT '10' COMMENT '超时时间秒',
  `status` char(1) DEFAULT '0' COMMENT '状态（0正常 1停用）',
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`config_id`),
  UNIQUE KEY `uk_sup_tim_config_code` (`item_code`),
  KEY `idx_sup_tim_config_sort` (`sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='TIM系统巡检项配置';

-- Table: sup_tim_inspection_plan
CREATE TABLE IF NOT EXISTS `sup_tim_inspection_plan` (
  `plan_id` bigint NOT NULL AUTO_INCREMENT COMMENT '计划ID',
  `plan_name` varchar(120) NOT NULL COMMENT '计划名称',
  `cron_expression` varchar(255) NOT NULL COMMENT 'Cron表达式',
  `job_id` bigint DEFAULT NULL COMMENT '若依定时任务ID',
  `report_style` varchar(32) DEFAULT 'STANDARD' COMMENT '巡检报告样式（STANDARD标准 SIMPLE简要 DETAIL明细 EXCEPTION_ONLY异常）',
  `status` char(1) DEFAULT '0' COMMENT '状态（0正常 1暂停）',
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`plan_id`),
  KEY `idx_sup_tim_plan_status` (`status`),
  KEY `idx_sup_tim_plan_job` (`job_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='TIM巡检计划';

-- Table: sup_tim_inspection_plan_item
CREATE TABLE IF NOT EXISTS `sup_tim_inspection_plan_item` (
  `plan_item_id` bigint NOT NULL AUTO_INCREMENT COMMENT '计划巡检项ID',
  `plan_id` bigint NOT NULL COMMENT '计划ID',
  `item_code` varchar(64) NOT NULL COMMENT '巡检项编码',
  `item_name` varchar(100) NOT NULL COMMENT '巡检项名称',
  `item_type` varchar(32) NOT NULL COMMENT '巡检项类型',
  `enabled_flag` char(1) DEFAULT 'Y' COMMENT '是否启用（Y是 N否）',
  `sort_order` int DEFAULT '0' COMMENT '排序',
  `threshold_value` decimal(18,2) DEFAULT NULL COMMENT '告警阈值',
  `threshold_unit` varchar(32) DEFAULT NULL COMMENT '阈值单位',
  `compare_rule` varchar(16) DEFAULT 'MAX' COMMENT '比较规则（MIN最低阈值 MAX最高阈值）',
  `time_window_minutes` int DEFAULT '0' COMMENT '统计时间窗口分钟数',
  `timeout_seconds` int DEFAULT '10' COMMENT '超时时间秒',
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`plan_item_id`),
  UNIQUE KEY `uk_sup_tim_plan_item` (`plan_id`,`item_code`),
  KEY `idx_sup_tim_plan_item_sort` (`plan_id`,`sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='TIM巡检计划巡检项';

-- Table: sup_tim_inspection_plan_target
CREATE TABLE IF NOT EXISTS `sup_tim_inspection_plan_target` (
  `plan_target_id` bigint NOT NULL AUTO_INCREMENT COMMENT '计划目标关系ID',
  `plan_id` bigint NOT NULL COMMENT '计划ID',
  `item_code` varchar(64) NOT NULL COMMENT '巡检项编码',
  `target_id` bigint NOT NULL COMMENT '巡检目标ID',
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`plan_target_id`),
  UNIQUE KEY `uk_sup_tim_plan_target` (`plan_id`,`item_code`,`target_id`),
  KEY `idx_sup_tim_plan_target_item` (`plan_id`,`item_code`),
  KEY `idx_sup_tim_plan_target_target` (`target_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='TIM巡检计划目标关系';

-- Table: sup_tim_inspection_target
CREATE TABLE IF NOT EXISTS `sup_tim_inspection_target` (
  `target_id` bigint NOT NULL AUTO_INCREMENT COMMENT '目标ID',
  `item_code` varchar(64) NOT NULL COMMENT '巡检项编码',
  `target_name` varchar(120) NOT NULL COMMENT '目标名称',
  `target_type` varchar(32) NOT NULL COMMENT '目标类型',
  `server_id` bigint DEFAULT NULL COMMENT '关联服务器ID',
  `host` varchar(255) DEFAULT NULL COMMENT '主机或Kafka bootstrap',
  `port` int DEFAULT NULL COMMENT '端口',
  `path` varchar(500) DEFAULT NULL COMMENT '目录路径或磁盘挂载点',
  `url` varchar(1000) DEFAULT NULL COMMENT 'HTTP接口地址',
  `http_method` varchar(10) DEFAULT NULL COMMENT 'HTTP方法',
  `topic` varchar(200) DEFAULT NULL COMMENT 'Kafka Topic',
  `consumer_group` varchar(200) DEFAULT NULL COMMENT 'Kafka消费组',
  `username` varchar(128) DEFAULT NULL COMMENT '登录账号',
  `password_cipher` varchar(1024) DEFAULT NULL COMMENT '登录密码密文',
  `app_key` varchar(128) DEFAULT NULL COMMENT '接口AppKey',
  `secret_cipher` varchar(1024) DEFAULT NULL COMMENT '接口密钥密文',
  `result_path` varchar(200) DEFAULT NULL COMMENT 'HTTP结果路径',
  `extra_params` text COMMENT '扩展参数/请求体模板',
  `status` char(1) DEFAULT '0' COMMENT '状态（0正常 1停用）',
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`target_id`),
  KEY `idx_sup_tim_target_item` (`item_code`,`status`),
  KEY `idx_sup_tim_target_server` (`server_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='TIM系统巡检目标配置';

-- Table: sup_tim_inspection_target_result
CREATE TABLE IF NOT EXISTS `sup_tim_inspection_target_result` (
  `result_id` bigint NOT NULL AUTO_INCREMENT COMMENT '目标结果ID',
  `inspection_id` bigint NOT NULL COMMENT '巡检ID',
  `item_id` bigint NOT NULL COMMENT '巡检项结果ID',
  `target_id` bigint DEFAULT NULL COMMENT '目标ID',
  `target_name` varchar(120) DEFAULT NULL COMMENT '目标名称',
  `target_type` varchar(32) DEFAULT NULL COMMENT '目标类型',
  `result_status` char(1) DEFAULT '3' COMMENT '目标结果（1正常 2异常 3未检测）',
  `actual_value` decimal(18,2) DEFAULT NULL COMMENT '实际值',
  `actual_unit` varchar(32) DEFAULT NULL COMMENT '实际单位',
  `result_detail` varchar(1000) DEFAULT NULL COMMENT '结果详情',
  `error_message` varchar(1000) DEFAULT NULL COMMENT '异常原因',
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`result_id`),
  KEY `idx_sup_tim_target_result_inspection` (`inspection_id`,`item_id`),
  KEY `idx_sup_tim_target_result_target` (`target_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='TIM系统巡检目标结果';

-- Table: sys_config
CREATE TABLE IF NOT EXISTS `sys_config` (
  `config_id` int NOT NULL AUTO_INCREMENT COMMENT '参数主键',
  `config_name` varchar(100) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '参数名称',
  `config_key` varchar(100) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '参数键名',
  `config_value` varchar(500) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '参数键值',
  `config_type` char(1) COLLATE utf8mb4_general_ci DEFAULT 'N' COMMENT '系统内置（Y是 N否）',
  `create_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`config_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='参数配置表';

-- Table: sys_dept
CREATE TABLE IF NOT EXISTS `sys_dept` (
  `dept_id` bigint NOT NULL AUTO_INCREMENT COMMENT '部门id',
  `parent_id` bigint DEFAULT '0' COMMENT '父部门id',
  `ancestors` varchar(50) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '祖级列表',
  `dept_name` varchar(30) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '部门名称',
  `order_num` int DEFAULT '0' COMMENT '显示顺序',
  `leader` varchar(20) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '负责人',
  `phone` varchar(11) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '联系电话',
  `email` varchar(50) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '邮箱',
  `status` char(1) COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT '部门状态（0正常 1停用）',
  `del_flag` char(1) COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT '删除标志（0代表存在 2代表删除）',
  `create_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`dept_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='部门表';

-- Table: sys_dict_data
CREATE TABLE IF NOT EXISTS `sys_dict_data` (
  `dict_code` bigint NOT NULL AUTO_INCREMENT COMMENT '字典编码',
  `dict_sort` int DEFAULT '0' COMMENT '字典排序',
  `dict_label` varchar(100) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '字典标签',
  `dict_value` varchar(100) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '字典键值',
  `dict_type` varchar(100) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '字典类型',
  `css_class` varchar(100) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '样式属性（其他样式扩展）',
  `list_class` varchar(100) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '表格回显样式',
  `is_default` char(1) COLLATE utf8mb4_general_ci DEFAULT 'N' COMMENT '是否默认（Y是 N否）',
  `status` char(1) COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT '状态（0正常 1停用）',
  `create_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`dict_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='字典数据表';

-- Table: sys_dict_type
CREATE TABLE IF NOT EXISTS `sys_dict_type` (
  `dict_id` bigint NOT NULL AUTO_INCREMENT COMMENT '字典主键',
  `dict_name` varchar(100) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '字典名称',
  `dict_type` varchar(100) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '字典类型',
  `status` char(1) COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT '状态（0正常 1停用）',
  `create_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`dict_id`),
  UNIQUE KEY `dict_type` (`dict_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='字典类型表';

-- Table: sys_job
CREATE TABLE IF NOT EXISTS `sys_job` (
  `job_id` bigint NOT NULL AUTO_INCREMENT COMMENT '任务ID',
  `job_name` varchar(64) COLLATE utf8mb4_general_ci NOT NULL DEFAULT '' COMMENT '任务名称',
  `job_group` varchar(64) COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'DEFAULT' COMMENT '任务组名',
  `invoke_target` varchar(500) COLLATE utf8mb4_general_ci NOT NULL COMMENT '调用目标字符串',
  `cron_expression` varchar(255) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT 'cron执行表达式',
  `misfire_policy` varchar(20) COLLATE utf8mb4_general_ci DEFAULT '3' COMMENT '计划执行错误策略（1立即执行 2执行一次 3放弃执行）',
  `concurrent` char(1) COLLATE utf8mb4_general_ci DEFAULT '1' COMMENT '是否并发执行（0允许 1禁止）',
  `status` char(1) COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT '状态（0正常 1暂停）',
  `create_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '备注信息',
  PRIMARY KEY (`job_id`,`job_name`,`job_group`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='定时任务调度表';

-- Table: sys_job_log
CREATE TABLE IF NOT EXISTS `sys_job_log` (
  `job_log_id` bigint NOT NULL AUTO_INCREMENT COMMENT '任务日志ID',
  `job_name` varchar(64) COLLATE utf8mb4_general_ci NOT NULL COMMENT '任务名称',
  `job_group` varchar(64) COLLATE utf8mb4_general_ci NOT NULL COMMENT '任务组名',
  `invoke_target` varchar(500) COLLATE utf8mb4_general_ci NOT NULL COMMENT '调用目标字符串',
  `job_message` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '日志信息',
  `status` char(1) COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT '执行状态（0正常 1失败）',
  `exception_info` varchar(2000) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '异常信息',
  `start_time` datetime DEFAULT NULL COMMENT '执行开始时间',
  `end_time` datetime DEFAULT NULL COMMENT '执行结束时间',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  PRIMARY KEY (`job_log_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='定时任务调度日志表';

-- Table: sys_logininfor
CREATE TABLE IF NOT EXISTS `sys_logininfor` (
  `info_id` bigint NOT NULL AUTO_INCREMENT COMMENT '访问ID',
  `user_name` varchar(50) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '用户账号',
  `ipaddr` varchar(128) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '登录IP地址',
  `login_location` varchar(255) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '登录地点',
  `browser` varchar(50) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '浏览器类型',
  `os` varchar(50) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '操作系统',
  `status` char(1) COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT '登录状态（0成功 1失败）',
  `msg` varchar(255) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '提示消息',
  `login_time` datetime DEFAULT NULL COMMENT '访问时间',
  PRIMARY KEY (`info_id`),
  KEY `idx_sys_logininfor_s` (`status`),
  KEY `idx_sys_logininfor_lt` (`login_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='系统访问记录';

-- Table: sys_menu
CREATE TABLE IF NOT EXISTS `sys_menu` (
  `menu_id` bigint NOT NULL AUTO_INCREMENT COMMENT '菜单ID',
  `menu_name` varchar(50) COLLATE utf8mb4_general_ci NOT NULL COMMENT '菜单名称',
  `parent_id` bigint DEFAULT '0' COMMENT '父菜单ID',
  `order_num` int DEFAULT '0' COMMENT '显示顺序',
  `path` varchar(200) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '路由地址',
  `component` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '组件路径',
  `query` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '路由参数',
  `route_name` varchar(50) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '路由名称',
  `is_frame` int DEFAULT '1' COMMENT '是否为外链（0是 1否）',
  `is_cache` int DEFAULT '0' COMMENT '是否缓存（0缓存 1不缓存）',
  `menu_type` char(1) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '菜单类型（M目录 C菜单 F按钮）',
  `visible` char(1) COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT '菜单状态（0显示 1隐藏）',
  `status` char(1) COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT '菜单状态（0正常 1停用）',
  `perms` varchar(100) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '权限标识',
  `icon` varchar(100) COLLATE utf8mb4_general_ci DEFAULT '#' COMMENT '菜单图标',
  `create_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '备注',
  PRIMARY KEY (`menu_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='菜单权限表';

-- Table: sys_notice
CREATE TABLE IF NOT EXISTS `sys_notice` (
  `notice_id` int NOT NULL AUTO_INCREMENT COMMENT '公告ID',
  `notice_title` varchar(50) COLLATE utf8mb4_general_ci NOT NULL COMMENT '公告标题',
  `notice_type` char(1) COLLATE utf8mb4_general_ci NOT NULL COMMENT '公告类型（1通知 2公告）',
  `notice_content` longblob COMMENT '公告内容',
  `status` char(1) COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT '公告状态（0正常 1关闭）',
  `create_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`notice_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='通知公告表';

-- Table: sys_notice_read
CREATE TABLE IF NOT EXISTS `sys_notice_read` (
  `read_id` bigint NOT NULL AUTO_INCREMENT COMMENT '已读主键',
  `notice_id` int NOT NULL COMMENT '公告id',
  `user_id` bigint NOT NULL COMMENT '用户id',
  `read_time` datetime NOT NULL COMMENT '阅读时间',
  PRIMARY KEY (`read_id`),
  UNIQUE KEY `uk_user_notice` (`user_id`,`notice_id`) COMMENT '同一用户同一公告只记录一次'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='公告已读记录表';

-- Table: sys_oper_log
CREATE TABLE IF NOT EXISTS `sys_oper_log` (
  `oper_id` bigint NOT NULL AUTO_INCREMENT COMMENT '日志主键',
  `title` varchar(50) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '模块标题',
  `business_type` int DEFAULT '0' COMMENT '业务类型（0其它 1新增 2修改 3删除）',
  `method` varchar(200) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '方法名称',
  `request_method` varchar(10) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '请求方式',
  `operator_type` int DEFAULT '0' COMMENT '操作类别（0其它 1后台用户 2手机端用户）',
  `oper_name` varchar(50) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '操作人员',
  `dept_name` varchar(50) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '部门名称',
  `oper_url` varchar(255) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '请求URL',
  `oper_ip` varchar(128) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '主机地址',
  `oper_location` varchar(255) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '操作地点',
  `oper_param` varchar(2000) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '请求参数',
  `json_result` varchar(2000) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '返回参数',
  `status` int DEFAULT '0' COMMENT '操作状态（0正常 1异常）',
  `error_msg` varchar(2000) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '错误消息',
  `oper_time` datetime DEFAULT NULL COMMENT '操作时间',
  `cost_time` bigint DEFAULT '0' COMMENT '消耗时间',
  PRIMARY KEY (`oper_id`),
  KEY `idx_sys_oper_log_bt` (`business_type`),
  KEY `idx_sys_oper_log_s` (`status`),
  KEY `idx_sys_oper_log_ot` (`oper_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='操作日志记录';

-- Table: sys_post
CREATE TABLE IF NOT EXISTS `sys_post` (
  `post_id` bigint NOT NULL AUTO_INCREMENT COMMENT '岗位ID',
  `post_code` varchar(64) COLLATE utf8mb4_general_ci NOT NULL COMMENT '岗位编码',
  `post_name` varchar(50) COLLATE utf8mb4_general_ci NOT NULL COMMENT '岗位名称',
  `post_sort` int NOT NULL COMMENT '显示顺序',
  `status` char(1) COLLATE utf8mb4_general_ci NOT NULL COMMENT '状态（0正常 1停用）',
  `create_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`post_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='岗位信息表';

-- Table: sys_role
CREATE TABLE IF NOT EXISTS `sys_role` (
  `role_id` bigint NOT NULL AUTO_INCREMENT COMMENT '角色ID',
  `role_name` varchar(30) COLLATE utf8mb4_general_ci NOT NULL COMMENT '角色名称',
  `role_key` varchar(100) COLLATE utf8mb4_general_ci NOT NULL COMMENT '角色权限字符串',
  `role_sort` int NOT NULL COMMENT '显示顺序',
  `data_scope` char(1) COLLATE utf8mb4_general_ci DEFAULT '1' COMMENT '数据范围（1：全部数据权限 2：自定数据权限 3：本部门数据权限 4：本部门及以下数据权限）',
  `menu_check_strictly` tinyint(1) DEFAULT '1' COMMENT '菜单树选择项是否关联显示',
  `dept_check_strictly` tinyint(1) DEFAULT '1' COMMENT '部门树选择项是否关联显示',
  `status` char(1) COLLATE utf8mb4_general_ci NOT NULL COMMENT '角色状态（0正常 1停用）',
  `del_flag` char(1) COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT '删除标志（0代表存在 2代表删除）',
  `create_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='角色信息表';

-- Table: sys_role_dept
CREATE TABLE IF NOT EXISTS `sys_role_dept` (
  `role_id` bigint NOT NULL COMMENT '角色ID',
  `dept_id` bigint NOT NULL COMMENT '部门ID',
  PRIMARY KEY (`role_id`,`dept_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='角色和部门关联表';

-- Table: sys_role_menu
CREATE TABLE IF NOT EXISTS `sys_role_menu` (
  `role_id` bigint NOT NULL COMMENT '角色ID',
  `menu_id` bigint NOT NULL COMMENT '菜单ID',
  PRIMARY KEY (`role_id`,`menu_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='角色和菜单关联表';

-- Table: sys_user
CREATE TABLE IF NOT EXISTS `sys_user` (
  `user_id` bigint NOT NULL AUTO_INCREMENT COMMENT '用户ID',
  `dept_id` bigint DEFAULT NULL COMMENT '部门ID',
  `user_name` varchar(30) COLLATE utf8mb4_general_ci NOT NULL COMMENT '用户账号',
  `nick_name` varchar(30) COLLATE utf8mb4_general_ci NOT NULL COMMENT '用户昵称',
  `user_type` varchar(2) COLLATE utf8mb4_general_ci DEFAULT '00' COMMENT '用户类型（00系统用户）',
  `email` varchar(50) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '用户邮箱',
  `phonenumber` varchar(11) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '手机号码',
  `sex` char(1) COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT '用户性别（0男 1女 2未知）',
  `avatar` varchar(100) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '头像地址',
  `password` varchar(100) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '密码',
  `status` char(1) COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT '账号状态（0正常 1停用）',
  `del_flag` char(1) COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT '删除标志（0代表存在 2代表删除）',
  `login_ip` varchar(128) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '最后登录IP',
  `login_date` datetime DEFAULT NULL COMMENT '最后登录时间',
  `pwd_update_date` datetime DEFAULT NULL COMMENT '密码最后更新时间',
  `create_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='用户信息表';

-- Table: sys_user_post
CREATE TABLE IF NOT EXISTS `sys_user_post` (
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `post_id` bigint NOT NULL COMMENT '岗位ID',
  PRIMARY KEY (`user_id`,`post_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='用户与岗位关联表';

-- Table: sys_user_role
CREATE TABLE IF NOT EXISTS `sys_user_role` (
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `role_id` bigint NOT NULL COMMENT '角色ID',
  PRIMARY KEY (`user_id`,`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='用户和角色关联表';

-- Table: wl_filter_data
CREATE TABLE IF NOT EXISTS `wl_filter_data` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `plate_no` varchar(32) DEFAULT NULL COMMENT '车牌号',
  `alarm_type` varchar(64) DEFAULT NULL COMMENT '告警类型',
  `channel_id` int DEFAULT NULL COMMENT '通道ID',
  `channel_name` varchar(255) DEFAULT NULL COMMENT '通道名称',
  `data_type` varchar(64) DEFAULT NULL COMMENT '数据类型',
  `event_type` varchar(64) DEFAULT NULL COMMENT '事件类型',
  `event_description` varchar(128) DEFAULT NULL COMMENT '事件描述',
  `ip_address` varchar(64) DEFAULT NULL COMMENT '设备IP',
  `port_no` int DEFAULT NULL COMMENT '端口',
  `send_time` datetime DEFAULT NULL COMMENT '发送时间',
  `recv_time` datetime DEFAULT NULL COMMENT '接收时间',
  `pass_time` datetime DEFAULT NULL COMMENT '过车时间',
  `camera_name` varchar(255) DEFAULT NULL COMMENT '相机名称',
  `camera_address` varchar(255) DEFAULT NULL COMMENT '相机地址',
  `device_name` varchar(255) DEFAULT NULL COMMENT '设备名称',
  `direction_index` varchar(64) DEFAULT NULL COMMENT '方向索引',
  `crossing_id` bigint DEFAULT NULL COMMENT '路口ID',
  `lane_no` int DEFAULT NULL COMMENT '车道号',
  `plate_color` varchar(32) DEFAULT NULL COMMENT '车牌颜色',
  `vehicle_type` varchar(64) DEFAULT NULL COMMENT '车辆类型',
  `pass_id` varchar(128) DEFAULT NULL COMMENT '过车ID',
  `event_uuid` varchar(128) DEFAULT NULL COMMENT '事件UUID',
  `task_id` varchar(128) DEFAULT NULL COMMENT '任务ID',
  `target_pic_url` varchar(1000) DEFAULT NULL COMMENT '抓拍图地址',
  `raw_json` longtext COMMENT '原始报文',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='白名单过滤数据';

SET FOREIGN_KEY_CHECKS = @rynew_previous_foreign_key_checks;
