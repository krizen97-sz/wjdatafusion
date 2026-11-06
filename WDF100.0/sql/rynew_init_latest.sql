-- RYNEW v4.17.2 complete fresh-database initialization (2026-11-06).
-- EMPTY DATABASE ONLY. Not an upgrade. Use a new batch connection, never --force.
-- Set @rynew_admin_password_hash to your installation-specific BCrypt hash first.
-- No universal bootstrap password, source users, business rows or live credentials.
-- Prepare a private runnable copy: node tools/database/init-latest.mjs --prepare --password-file FILE --output FILE
-- Source SHA-256 (schema snapshot): e4e98bc986f01de8ead28ae038c074499999433f38577a244d0895bb46f24957
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
-- cc18a112e21d2b8691b3987fc48ba895a3e9e65bcac814ce20bc2ddedc48cf0c  document_management_v3_9_20_ruoyi_file_permission_20260816_prepare.sql
-- 7df5feb84a5e3583cfb9b9e3d01750f896148a419c495b261f8230224b6dcc76  document_management_v3_9_20_ruoyi_file_permission_20260816_finalize.sql
-- 3da01f25f172bf79b8cbcf98eadf6edd7d4453bb8a048f082242ce10d78ff6a8  document_management_v3_9_21_document_role_20260816.sql
-- d65c0b76859ae7f03ef4f3e4bd8247f9ed81fcaaff6d6c162c8af2548f053e2a  data_governance_upgrade_20260909_v4_5_0.sql
-- a32d768049d4c46a56a7ae238cd3d996b6b7ddcdd408224b6ab80fe7fb2994fc  support_upgrade_20260826_menu_icons_v3_14_3.sql
-- Seed policy SHA-256: 6d9fa201a84a3c3599c85c3a48d87dbeb7702e976d93e65269525b71f2c0c940

SET NAMES utf8mb4;
CREATE TEMPORARY TABLE rynew_init_guard(check_name VARCHAR(80) PRIMARY KEY);
INSERT INTO rynew_init_guard VALUES('DATABASE_MUST_BE_SELECTED');
INSERT INTO rynew_init_guard SELECT 'DATABASE_MUST_BE_SELECTED' WHERE DATABASE() IS NULL;
INSERT INTO rynew_init_guard VALUES('INSTALL_PASSWORD_HASH_REQUIRED');
INSERT INTO rynew_init_guard SELECT 'INSTALL_PASSWORD_HASH_REQUIRED' WHERE NOT REGEXP_LIKE(CAST(COALESCE(@rynew_admin_password_hash,'') AS CHAR CHARACTER SET utf8mb4),'^[$]2[aby][$]1[0-4][$][./A-Za-z0-9]{53}$','c');
SET @rynew_init_lock = CONCAT('rynew_init_',LEFT(SHA2(DATABASE(),256),40));
SET @rynew_init_lock_acquired = GET_LOCK(@rynew_init_lock,0);
INSERT INTO rynew_init_guard VALUES('ANOTHER_INSTALLATION_IS_RUNNING');
INSERT INTO rynew_init_guard SELECT 'ANOTHER_INSTALLATION_IS_RUNNING' WHERE @rynew_init_lock_acquired IS NULL OR @rynew_init_lock_acquired<>1;
INSERT INTO rynew_init_guard VALUES('EMPTY_DATABASE_REQUIRED');
INSERT INTO rynew_init_guard SELECT 'EMPTY_DATABASE_REQUIRED' WHERE (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE())<>0 OR (SELECT COUNT(*) FROM information_schema.ROUTINES WHERE ROUTINE_SCHEMA=DATABASE())<>0 OR (SELECT COUNT(*) FROM information_schema.EVENTS WHERE EVENT_SCHEMA=DATABASE())<>0;

-- BEGIN SCHEMA SNAPSHOT
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

-- END SCHEMA SNAPSHOT

START TRANSACTION;
-- Technical seed: sys_dept (1 rows)
INSERT INTO `sys_dept` (`dept_id`,`parent_id`,`ancestors`,`dept_name`,`order_num`,`leader`,`phone`,`email`,`status`,`del_flag`,`create_by`,`create_time`,`update_by`,`update_time`) VALUES (100,0,CONVERT(0x30 USING utf8mb4),CONVERT(0xe5b9b3e58fb0e7aea1e79086 USING utf8mb4),0,NULL,NULL,NULL,CONVERT(0x30 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4));

-- Technical seed: sys_post (1 rows)
INSERT INTO `sys_post` (`post_id`,`post_code`,`post_name`,`post_sort`,`status`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (1,CONVERT(0x61646d696e USING utf8mb4),CONVERT(0xe5b9b3e58fb0e7aea1e79086e59198 USING utf8mb4),1,CONVERT(0x30 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),NULL);

-- Technical seed: sys_role (4 rows)
INSERT INTO `sys_role` (`role_id`,`role_name`,`role_key`,`role_sort`,`data_scope`,`menu_check_strictly`,`dept_check_strictly`,`status`,`del_flag`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (1,CONVERT(0xe8b685e7baa7e7aea1e79086e59198 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),1,CONVERT(0x31 USING utf8mb4),1,1,CONVERT(0x30 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),CONVERT(0xe8b685e7baa7e7aea1e79086e59198 USING utf8mb4));
INSERT INTO `sys_role` (`role_id`,`role_name`,`role_key`,`role_sort`,`data_scope`,`menu_check_strictly`,`dept_check_strictly`,`status`,`del_flag`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (2,CONVERT(0xe699aee9809ae8a792e889b2 USING utf8mb4),CONVERT(0x636f6d6d6f6e USING utf8mb4),2,CONVERT(0x35 USING utf8mb4),1,1,CONVERT(0x30 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),CONVERT(0xe699aee9809ae8a792e889b2 USING utf8mb4));
INSERT INTO `sys_role` (`role_id`,`role_name`,`role_key`,`role_sort`,`data_scope`,`menu_check_strictly`,`dept_check_strictly`,`status`,`del_flag`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (100,CONVERT(0xe69687e6a1a3e7aea1e79086 USING utf8mb4),CONVERT(0x646f63756d656e74 USING utf8mb4),3,CONVERT(0x35 USING utf8mb4),1,1,CONVERT(0x30 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),CONVERT(0xe69687e6a1a3e7aea1e79086e4b893e794a8e8a792e889b2efbc9be58886e9858de5908ee58fafe8a781e5b9b6e4bdbfe794a8e69687e4bbb6e7aea1e79086e5ba94e794a8 USING utf8mb4));
INSERT INTO `sys_role` (`role_id`,`role_name`,`role_key`,`role_sort`,`data_scope`,`menu_check_strictly`,`dept_check_strictly`,`status`,`del_flag`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (101,CONVERT(0xe78eb0e59cbae89e8de59088e4b88ee5b7a1e6a380 USING utf8mb4),CONVERT(0x64617461667573696f6e USING utf8mb4),3,CONVERT(0x31 USING utf8mb4),1,1,CONVERT(0x30 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),NULL);

-- Technical seed: sys_menu (195 rows)
INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (1,CONVERT(0xe7b3bbe7bb9fe7aea1e79086 USING utf8mb4),0,1,CONVERT(0x73797374656d USING utf8mb4),NULL,'','',1,0,CONVERT(0x4d USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x30 USING utf8mb4),'',CONVERT(0x73797374656d USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),CONVERT(0xe7b3bbe7bb9fe7aea1e79086e79baee5bd95 USING utf8mb4));
INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (2,CONVERT(0xe7b3bbe7bb9fe79b91e68ea7 USING utf8mb4),0,2,CONVERT(0x6d6f6e69746f72 USING utf8mb4),NULL,'','',1,0,CONVERT(0x4d USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x30 USING utf8mb4),'',CONVERT(0x6d6f6e69746f72 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),CONVERT(0xe7b3bbe7bb9fe79b91e68ea7e79baee5bd95 USING utf8mb4));
INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (3,CONVERT(0xe7b3bbe7bb9fe5b7a5e585b7 USING utf8mb4),0,3,CONVERT(0x746f6f6c USING utf8mb4),NULL,'','',1,0,CONVERT(0x4d USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x30 USING utf8mb4),'',CONVERT(0x746f6f6c USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),CONVERT(0xe7b3bbe7bb9fe5b7a5e585b7e79baee5bd95 USING utf8mb4));
INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (100,CONVERT(0xe794a8e688b7e7aea1e79086 USING utf8mb4),1,1,CONVERT(0x75736572 USING utf8mb4),CONVERT(0x73797374656d2f757365722f696e646578 USING utf8mb4),'','',1,0,CONVERT(0x43 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x73797374656d3a757365723a6c697374 USING utf8mb4),CONVERT(0x75736572 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),CONVERT(0xe794a8e688b7e7aea1e79086e88f9ce58d95 USING utf8mb4));
INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (101,CONVERT(0xe8a792e889b2e7aea1e79086 USING utf8mb4),1,2,CONVERT(0x726f6c65 USING utf8mb4),CONVERT(0x73797374656d2f726f6c652f696e646578 USING utf8mb4),'','',1,0,CONVERT(0x43 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x73797374656d3a726f6c653a6c697374 USING utf8mb4),CONVERT(0x70656f706c6573 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),CONVERT(0xe8a792e889b2e7aea1e79086e88f9ce58d95 USING utf8mb4));
INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (102,CONVERT(0xe88f9ce58d95e7aea1e79086 USING utf8mb4),1,3,CONVERT(0x6d656e75 USING utf8mb4),CONVERT(0x73797374656d2f6d656e752f696e646578 USING utf8mb4),'','',1,0,CONVERT(0x43 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x73797374656d3a6d656e753a6c697374 USING utf8mb4),CONVERT(0x747265652d7461626c65 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),CONVERT(0xe88f9ce58d95e7aea1e79086e88f9ce58d95 USING utf8mb4));
INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (103,CONVERT(0xe983a8e997a8e7aea1e79086 USING utf8mb4),1,4,CONVERT(0x64657074 USING utf8mb4),CONVERT(0x73797374656d2f646570742f696e646578 USING utf8mb4),'','',1,0,CONVERT(0x43 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x73797374656d3a646570743a6c697374 USING utf8mb4),CONVERT(0x74726565 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),CONVERT(0xe983a8e997a8e7aea1e79086e88f9ce58d95 USING utf8mb4));
INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (104,CONVERT(0xe5b297e4bd8de7aea1e79086 USING utf8mb4),1,5,CONVERT(0x706f7374 USING utf8mb4),CONVERT(0x73797374656d2f706f73742f696e646578 USING utf8mb4),'','',1,0,CONVERT(0x43 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x73797374656d3a706f73743a6c697374 USING utf8mb4),CONVERT(0x706f7374 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),CONVERT(0xe5b297e4bd8de7aea1e79086e88f9ce58d95 USING utf8mb4));
INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (105,CONVERT(0xe5ad97e585b8e7aea1e79086 USING utf8mb4),1,6,CONVERT(0x64696374 USING utf8mb4),CONVERT(0x73797374656d2f646963742f696e646578 USING utf8mb4),'','',1,0,CONVERT(0x43 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x73797374656d3a646963743a6c697374 USING utf8mb4),CONVERT(0x64696374 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),CONVERT(0xe5ad97e585b8e7aea1e79086e88f9ce58d95 USING utf8mb4));
INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (106,CONVERT(0xe58f82e695b0e8aebee7bdae USING utf8mb4),1,7,CONVERT(0x636f6e666967 USING utf8mb4),CONVERT(0x73797374656d2f636f6e6669672f696e646578 USING utf8mb4),'','',1,0,CONVERT(0x43 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x73797374656d3a636f6e6669673a6c697374 USING utf8mb4),CONVERT(0x65646974 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),CONVERT(0xe58f82e695b0e8aebee7bdaee88f9ce58d95 USING utf8mb4));
INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (107,CONVERT(0xe9809ae79fa5e585ace5918a USING utf8mb4),1,8,CONVERT(0x6e6f74696365 USING utf8mb4),CONVERT(0x73797374656d2f6e6f746963652f696e646578 USING utf8mb4),'','',1,0,CONVERT(0x43 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x73797374656d3a6e6f746963653a6c697374 USING utf8mb4),CONVERT(0x6d657373616765 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),CONVERT(0xe9809ae79fa5e585ace5918ae88f9ce58d95 USING utf8mb4));
INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (108,CONVERT(0xe697a5e5bf97e7aea1e79086 USING utf8mb4),1,9,CONVERT(0x6c6f67 USING utf8mb4),'','','',1,0,CONVERT(0x4d USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x30 USING utf8mb4),'',CONVERT(0x6c6f67 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),CONVERT(0xe697a5e5bf97e7aea1e79086e88f9ce58d95 USING utf8mb4));
INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (109,CONVERT(0xe59ca8e7babfe794a8e688b7 USING utf8mb4),2,1,CONVERT(0x6f6e6c696e65 USING utf8mb4),CONVERT(0x6d6f6e69746f722f6f6e6c696e652f696e646578 USING utf8mb4),'','',1,0,CONVERT(0x43 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x6d6f6e69746f723a6f6e6c696e653a6c697374 USING utf8mb4),CONVERT(0x6f6e6c696e65 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),CONVERT(0xe59ca8e7babfe794a8e688b7e88f9ce58d95 USING utf8mb4));
INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (110,CONVERT(0xe5ae9ae697b6e4bbbbe58aa1 USING utf8mb4),2,2,CONVERT(0x6a6f62 USING utf8mb4),CONVERT(0x6d6f6e69746f722f6a6f622f696e646578 USING utf8mb4),'','',1,0,CONVERT(0x43 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x6d6f6e69746f723a6a6f623a6c697374 USING utf8mb4),CONVERT(0x6a6f62 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),CONVERT(0xe5ae9ae697b6e4bbbbe58aa1e88f9ce58d95 USING utf8mb4));
INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (111,CONVERT(0xe695b0e68daee79b91e68ea7 USING utf8mb4),2,3,CONVERT(0x6472756964 USING utf8mb4),CONVERT(0x6d6f6e69746f722f64727569642f696e646578 USING utf8mb4),'','',1,0,CONVERT(0x43 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x6d6f6e69746f723a64727569643a6c697374 USING utf8mb4),CONVERT(0x6472756964 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),CONVERT(0xe695b0e68daee79b91e68ea7e88f9ce58d95 USING utf8mb4));
INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (112,CONVERT(0xe69c8de58aa1e79b91e68ea7 USING utf8mb4),2,4,CONVERT(0x736572766572 USING utf8mb4),CONVERT(0x6d6f6e69746f722f7365727665722f696e646578 USING utf8mb4),'','',1,0,CONVERT(0x43 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x6d6f6e69746f723a7365727665723a6c697374 USING utf8mb4),CONVERT(0x736572766572 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),CONVERT(0xe69c8de58aa1e79b91e68ea7e88f9ce58d95 USING utf8mb4));
INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (113,CONVERT(0xe7bc93e5ad98e79b91e68ea7 USING utf8mb4),2,5,CONVERT(0x6361636865 USING utf8mb4),CONVERT(0x6d6f6e69746f722f63616368652f696e646578 USING utf8mb4),'','',1,0,CONVERT(0x43 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x6d6f6e69746f723a63616368653a6c697374 USING utf8mb4),CONVERT(0x7265646973 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),CONVERT(0xe7bc93e5ad98e79b91e68ea7e88f9ce58d95 USING utf8mb4));
INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (114,CONVERT(0xe7bc93e5ad98e58897e8a1a8 USING utf8mb4),2,6,CONVERT(0x63616368654c697374 USING utf8mb4),CONVERT(0x6d6f6e69746f722f63616368652f6c697374 USING utf8mb4),'','',1,0,CONVERT(0x43 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x6d6f6e69746f723a63616368653a6c697374 USING utf8mb4),CONVERT(0x72656469732d6c697374 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),CONVERT(0xe7bc93e5ad98e58897e8a1a8e88f9ce58d95 USING utf8mb4));
INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (115,CONVERT(0xe8a1a8e58d95e69e84e5bbba USING utf8mb4),3,1,CONVERT(0x6275696c64 USING utf8mb4),CONVERT(0x746f6f6c2f6275696c642f696e646578 USING utf8mb4),'','',1,0,CONVERT(0x43 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x746f6f6c3a6275696c643a6c697374 USING utf8mb4),CONVERT(0x6275696c64 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),CONVERT(0xe8a1a8e58d95e69e84e5bbbae88f9ce58d95 USING utf8mb4));
INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (116,CONVERT(0xe4bba3e7a081e7949fe68890 USING utf8mb4),3,2,CONVERT(0x67656e USING utf8mb4),CONVERT(0x746f6f6c2f67656e2f696e646578 USING utf8mb4),'','',1,0,CONVERT(0x43 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x746f6f6c3a67656e3a6c697374 USING utf8mb4),CONVERT(0x636f6465 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),CONVERT(0xe4bba3e7a081e7949fe68890e88f9ce58d95 USING utf8mb4));
INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (117,CONVERT(0xe7b3bbe7bb9fe68ea5e58fa3 USING utf8mb4),3,3,CONVERT(0x73776167676572 USING utf8mb4),CONVERT(0x746f6f6c2f737761676765722f696e646578 USING utf8mb4),'','',1,0,CONVERT(0x43 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x746f6f6c3a737761676765723a6c697374 USING utf8mb4),CONVERT(0x73776167676572 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),CONVERT(0xe7b3bbe7bb9fe68ea5e58fa3e88f9ce58d95 USING utf8mb4));
INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (500,CONVERT(0xe6938de4bd9ce697a5e5bf97 USING utf8mb4),108,1,CONVERT(0x6f7065726c6f67 USING utf8mb4),CONVERT(0x6d6f6e69746f722f6f7065726c6f672f696e646578 USING utf8mb4),'','',1,0,CONVERT(0x43 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x6d6f6e69746f723a6f7065726c6f673a6c697374 USING utf8mb4),CONVERT(0x666f726d USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),CONVERT(0xe6938de4bd9ce697a5e5bf97e88f9ce58d95 USING utf8mb4));
INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (501,CONVERT(0xe799bbe5bd95e697a5e5bf97 USING utf8mb4),108,2,CONVERT(0x6c6f67696e696e666f72 USING utf8mb4),CONVERT(0x6d6f6e69746f722f6c6f67696e696e666f722f696e646578 USING utf8mb4),'','',1,0,CONVERT(0x43 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x6d6f6e69746f723a6c6f67696e696e666f723a6c697374 USING utf8mb4),CONVERT(0x6c6f67696e696e666f72 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),CONVERT(0xe799bbe5bd95e697a5e5bf97e88f9ce58d95 USING utf8mb4));
INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (1000,CONVERT(0xe794a8e688b7e69fa5e8afa2 USING utf8mb4),100,1,'','','','',1,0,CONVERT(0x46 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x73797374656d3a757365723a7175657279 USING utf8mb4),CONVERT(0x23 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'');
INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (1001,CONVERT(0xe794a8e688b7e696b0e5a29e USING utf8mb4),100,2,'','','','',1,0,CONVERT(0x46 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x73797374656d3a757365723a616464 USING utf8mb4),CONVERT(0x23 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'');
INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (1002,CONVERT(0xe794a8e688b7e4bfaee694b9 USING utf8mb4),100,3,'','','','',1,0,CONVERT(0x46 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x73797374656d3a757365723a65646974 USING utf8mb4),CONVERT(0x23 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'');
INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (1003,CONVERT(0xe794a8e688b7e588a0e999a4 USING utf8mb4),100,4,'','','','',1,0,CONVERT(0x46 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x73797374656d3a757365723a72656d6f7665 USING utf8mb4),CONVERT(0x23 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'');
INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (1004,CONVERT(0xe794a8e688b7e5afbce587ba USING utf8mb4),100,5,'','','','',1,0,CONVERT(0x46 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x73797374656d3a757365723a6578706f7274 USING utf8mb4),CONVERT(0x23 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'');
INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (1005,CONVERT(0xe794a8e688b7e5afbce585a5 USING utf8mb4),100,6,'','','','',1,0,CONVERT(0x46 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x73797374656d3a757365723a696d706f7274 USING utf8mb4),CONVERT(0x23 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'');
INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (1006,CONVERT(0xe9878de7bdaee5af86e7a081 USING utf8mb4),100,7,'','','','',1,0,CONVERT(0x46 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x73797374656d3a757365723a7265736574507764 USING utf8mb4),CONVERT(0x23 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'');
INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (1007,CONVERT(0xe8a792e889b2e69fa5e8afa2 USING utf8mb4),101,1,'','','','',1,0,CONVERT(0x46 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x73797374656d3a726f6c653a7175657279 USING utf8mb4),CONVERT(0x23 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'');
INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (1008,CONVERT(0xe8a792e889b2e696b0e5a29e USING utf8mb4),101,2,'','','','',1,0,CONVERT(0x46 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x73797374656d3a726f6c653a616464 USING utf8mb4),CONVERT(0x23 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'');
INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (1009,CONVERT(0xe8a792e889b2e4bfaee694b9 USING utf8mb4),101,3,'','','','',1,0,CONVERT(0x46 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x73797374656d3a726f6c653a65646974 USING utf8mb4),CONVERT(0x23 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'');
INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (1010,CONVERT(0xe8a792e889b2e588a0e999a4 USING utf8mb4),101,4,'','','','',1,0,CONVERT(0x46 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x73797374656d3a726f6c653a72656d6f7665 USING utf8mb4),CONVERT(0x23 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'');
INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (1011,CONVERT(0xe8a792e889b2e5afbce587ba USING utf8mb4),101,5,'','','','',1,0,CONVERT(0x46 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x73797374656d3a726f6c653a6578706f7274 USING utf8mb4),CONVERT(0x23 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'');
INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (1012,CONVERT(0xe88f9ce58d95e69fa5e8afa2 USING utf8mb4),102,1,'','','','',1,0,CONVERT(0x46 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x73797374656d3a6d656e753a7175657279 USING utf8mb4),CONVERT(0x23 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'');
INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (1013,CONVERT(0xe88f9ce58d95e696b0e5a29e USING utf8mb4),102,2,'','','','',1,0,CONVERT(0x46 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x73797374656d3a6d656e753a616464 USING utf8mb4),CONVERT(0x23 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'');
INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (1014,CONVERT(0xe88f9ce58d95e4bfaee694b9 USING utf8mb4),102,3,'','','','',1,0,CONVERT(0x46 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x73797374656d3a6d656e753a65646974 USING utf8mb4),CONVERT(0x23 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'');
INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (1015,CONVERT(0xe88f9ce58d95e588a0e999a4 USING utf8mb4),102,4,'','','','',1,0,CONVERT(0x46 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x73797374656d3a6d656e753a72656d6f7665 USING utf8mb4),CONVERT(0x23 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'');
INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (1016,CONVERT(0xe983a8e997a8e69fa5e8afa2 USING utf8mb4),103,1,'','','','',1,0,CONVERT(0x46 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x73797374656d3a646570743a7175657279 USING utf8mb4),CONVERT(0x23 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'');
INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (1017,CONVERT(0xe983a8e997a8e696b0e5a29e USING utf8mb4),103,2,'','','','',1,0,CONVERT(0x46 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x73797374656d3a646570743a616464 USING utf8mb4),CONVERT(0x23 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'');
INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (1018,CONVERT(0xe983a8e997a8e4bfaee694b9 USING utf8mb4),103,3,'','','','',1,0,CONVERT(0x46 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x73797374656d3a646570743a65646974 USING utf8mb4),CONVERT(0x23 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'');
INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (1019,CONVERT(0xe983a8e997a8e588a0e999a4 USING utf8mb4),103,4,'','','','',1,0,CONVERT(0x46 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x73797374656d3a646570743a72656d6f7665 USING utf8mb4),CONVERT(0x23 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'');
INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (1020,CONVERT(0xe5b297e4bd8de69fa5e8afa2 USING utf8mb4),104,1,'','','','',1,0,CONVERT(0x46 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x73797374656d3a706f73743a7175657279 USING utf8mb4),CONVERT(0x23 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'');
INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (1021,CONVERT(0xe5b297e4bd8de696b0e5a29e USING utf8mb4),104,2,'','','','',1,0,CONVERT(0x46 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x73797374656d3a706f73743a616464 USING utf8mb4),CONVERT(0x23 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'');
INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (1022,CONVERT(0xe5b297e4bd8de4bfaee694b9 USING utf8mb4),104,3,'','','','',1,0,CONVERT(0x46 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x73797374656d3a706f73743a65646974 USING utf8mb4),CONVERT(0x23 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'');
INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (1023,CONVERT(0xe5b297e4bd8de588a0e999a4 USING utf8mb4),104,4,'','','','',1,0,CONVERT(0x46 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x73797374656d3a706f73743a72656d6f7665 USING utf8mb4),CONVERT(0x23 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'');
INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (1024,CONVERT(0xe5b297e4bd8de5afbce587ba USING utf8mb4),104,5,'','','','',1,0,CONVERT(0x46 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x73797374656d3a706f73743a6578706f7274 USING utf8mb4),CONVERT(0x23 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'');
INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (1025,CONVERT(0xe5ad97e585b8e69fa5e8afa2 USING utf8mb4),105,1,CONVERT(0x23 USING utf8mb4),'','','',1,0,CONVERT(0x46 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x73797374656d3a646963743a7175657279 USING utf8mb4),CONVERT(0x23 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'');
INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (1026,CONVERT(0xe5ad97e585b8e696b0e5a29e USING utf8mb4),105,2,CONVERT(0x23 USING utf8mb4),'','','',1,0,CONVERT(0x46 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x73797374656d3a646963743a616464 USING utf8mb4),CONVERT(0x23 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'');
INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (1027,CONVERT(0xe5ad97e585b8e4bfaee694b9 USING utf8mb4),105,3,CONVERT(0x23 USING utf8mb4),'','','',1,0,CONVERT(0x46 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x73797374656d3a646963743a65646974 USING utf8mb4),CONVERT(0x23 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'');
INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (1028,CONVERT(0xe5ad97e585b8e588a0e999a4 USING utf8mb4),105,4,CONVERT(0x23 USING utf8mb4),'','','',1,0,CONVERT(0x46 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x73797374656d3a646963743a72656d6f7665 USING utf8mb4),CONVERT(0x23 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'');
INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (1029,CONVERT(0xe5ad97e585b8e5afbce587ba USING utf8mb4),105,5,CONVERT(0x23 USING utf8mb4),'','','',1,0,CONVERT(0x46 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x73797374656d3a646963743a6578706f7274 USING utf8mb4),CONVERT(0x23 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'');
INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (1030,CONVERT(0xe58f82e695b0e69fa5e8afa2 USING utf8mb4),106,1,CONVERT(0x23 USING utf8mb4),'','','',1,0,CONVERT(0x46 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x73797374656d3a636f6e6669673a7175657279 USING utf8mb4),CONVERT(0x23 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'');
INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (1031,CONVERT(0xe58f82e695b0e696b0e5a29e USING utf8mb4),106,2,CONVERT(0x23 USING utf8mb4),'','','',1,0,CONVERT(0x46 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x73797374656d3a636f6e6669673a616464 USING utf8mb4),CONVERT(0x23 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'');
INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (1032,CONVERT(0xe58f82e695b0e4bfaee694b9 USING utf8mb4),106,3,CONVERT(0x23 USING utf8mb4),'','','',1,0,CONVERT(0x46 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x73797374656d3a636f6e6669673a65646974 USING utf8mb4),CONVERT(0x23 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'');
INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (1033,CONVERT(0xe58f82e695b0e588a0e999a4 USING utf8mb4),106,4,CONVERT(0x23 USING utf8mb4),'','','',1,0,CONVERT(0x46 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x73797374656d3a636f6e6669673a72656d6f7665 USING utf8mb4),CONVERT(0x23 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'');
INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (1034,CONVERT(0xe58f82e695b0e5afbce587ba USING utf8mb4),106,5,CONVERT(0x23 USING utf8mb4),'','','',1,0,CONVERT(0x46 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x73797374656d3a636f6e6669673a6578706f7274 USING utf8mb4),CONVERT(0x23 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'');
INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (1035,CONVERT(0xe585ace5918ae69fa5e8afa2 USING utf8mb4),107,1,CONVERT(0x23 USING utf8mb4),'','','',1,0,CONVERT(0x46 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x73797374656d3a6e6f746963653a7175657279 USING utf8mb4),CONVERT(0x23 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'');
INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (1036,CONVERT(0xe585ace5918ae696b0e5a29e USING utf8mb4),107,2,CONVERT(0x23 USING utf8mb4),'','','',1,0,CONVERT(0x46 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x73797374656d3a6e6f746963653a616464 USING utf8mb4),CONVERT(0x23 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'');
INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (1037,CONVERT(0xe585ace5918ae4bfaee694b9 USING utf8mb4),107,3,CONVERT(0x23 USING utf8mb4),'','','',1,0,CONVERT(0x46 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x73797374656d3a6e6f746963653a65646974 USING utf8mb4),CONVERT(0x23 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'');
INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (1038,CONVERT(0xe585ace5918ae588a0e999a4 USING utf8mb4),107,4,CONVERT(0x23 USING utf8mb4),'','','',1,0,CONVERT(0x46 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x73797374656d3a6e6f746963653a72656d6f7665 USING utf8mb4),CONVERT(0x23 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'');
INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (1039,CONVERT(0xe6938de4bd9ce69fa5e8afa2 USING utf8mb4),500,1,CONVERT(0x23 USING utf8mb4),'','','',1,0,CONVERT(0x46 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x6d6f6e69746f723a6f7065726c6f673a7175657279 USING utf8mb4),CONVERT(0x23 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'');
INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (1040,CONVERT(0xe6938de4bd9ce588a0e999a4 USING utf8mb4),500,2,CONVERT(0x23 USING utf8mb4),'','','',1,0,CONVERT(0x46 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x6d6f6e69746f723a6f7065726c6f673a72656d6f7665 USING utf8mb4),CONVERT(0x23 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'');
INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (1041,CONVERT(0xe697a5e5bf97e5afbce587ba USING utf8mb4),500,3,CONVERT(0x23 USING utf8mb4),'','','',1,0,CONVERT(0x46 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x6d6f6e69746f723a6f7065726c6f673a6578706f7274 USING utf8mb4),CONVERT(0x23 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'');
INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (1042,CONVERT(0xe799bbe5bd95e69fa5e8afa2 USING utf8mb4),501,1,CONVERT(0x23 USING utf8mb4),'','','',1,0,CONVERT(0x46 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x6d6f6e69746f723a6c6f67696e696e666f723a7175657279 USING utf8mb4),CONVERT(0x23 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'');
INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (1043,CONVERT(0xe799bbe5bd95e588a0e999a4 USING utf8mb4),501,2,CONVERT(0x23 USING utf8mb4),'','','',1,0,CONVERT(0x46 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x6d6f6e69746f723a6c6f67696e696e666f723a72656d6f7665 USING utf8mb4),CONVERT(0x23 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'');
INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (1044,CONVERT(0xe697a5e5bf97e5afbce587ba USING utf8mb4),501,3,CONVERT(0x23 USING utf8mb4),'','','',1,0,CONVERT(0x46 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x6d6f6e69746f723a6c6f67696e696e666f723a6578706f7274 USING utf8mb4),CONVERT(0x23 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'');
INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (1045,CONVERT(0xe8b4a6e688b7e8a7a3e99481 USING utf8mb4),501,4,CONVERT(0x23 USING utf8mb4),'','','',1,0,CONVERT(0x46 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x6d6f6e69746f723a6c6f67696e696e666f723a756e6c6f636b USING utf8mb4),CONVERT(0x23 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'');
INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (1046,CONVERT(0xe59ca8e7babfe69fa5e8afa2 USING utf8mb4),109,1,CONVERT(0x23 USING utf8mb4),'','','',1,0,CONVERT(0x46 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x6d6f6e69746f723a6f6e6c696e653a7175657279 USING utf8mb4),CONVERT(0x23 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'');
INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (1047,CONVERT(0xe689b9e9878fe5bcbae98080 USING utf8mb4),109,2,CONVERT(0x23 USING utf8mb4),'','','',1,0,CONVERT(0x46 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x6d6f6e69746f723a6f6e6c696e653a62617463684c6f676f7574 USING utf8mb4),CONVERT(0x23 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'');
INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (1048,CONVERT(0xe58d95e69da1e5bcbae98080 USING utf8mb4),109,3,CONVERT(0x23 USING utf8mb4),'','','',1,0,CONVERT(0x46 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x6d6f6e69746f723a6f6e6c696e653a666f7263654c6f676f7574 USING utf8mb4),CONVERT(0x23 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'');
INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (1049,CONVERT(0xe4bbbbe58aa1e69fa5e8afa2 USING utf8mb4),110,1,CONVERT(0x23 USING utf8mb4),'','','',1,0,CONVERT(0x46 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x6d6f6e69746f723a6a6f623a7175657279 USING utf8mb4),CONVERT(0x23 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'');
INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (1050,CONVERT(0xe4bbbbe58aa1e696b0e5a29e USING utf8mb4),110,2,CONVERT(0x23 USING utf8mb4),'','','',1,0,CONVERT(0x46 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x6d6f6e69746f723a6a6f623a616464 USING utf8mb4),CONVERT(0x23 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'');
INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (1051,CONVERT(0xe4bbbbe58aa1e4bfaee694b9 USING utf8mb4),110,3,CONVERT(0x23 USING utf8mb4),'','','',1,0,CONVERT(0x46 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x6d6f6e69746f723a6a6f623a65646974 USING utf8mb4),CONVERT(0x23 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'');
INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (1052,CONVERT(0xe4bbbbe58aa1e588a0e999a4 USING utf8mb4),110,4,CONVERT(0x23 USING utf8mb4),'','','',1,0,CONVERT(0x46 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x6d6f6e69746f723a6a6f623a72656d6f7665 USING utf8mb4),CONVERT(0x23 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'');
INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (1053,CONVERT(0xe78ab6e68081e4bfaee694b9 USING utf8mb4),110,5,CONVERT(0x23 USING utf8mb4),'','','',1,0,CONVERT(0x46 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x6d6f6e69746f723a6a6f623a6368616e6765537461747573 USING utf8mb4),CONVERT(0x23 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'');
INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (1054,CONVERT(0xe4bbbbe58aa1e5afbce587ba USING utf8mb4),110,6,CONVERT(0x23 USING utf8mb4),'','','',1,0,CONVERT(0x46 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x6d6f6e69746f723a6a6f623a6578706f7274 USING utf8mb4),CONVERT(0x23 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'');
INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (1055,CONVERT(0xe7949fe68890e69fa5e8afa2 USING utf8mb4),116,1,CONVERT(0x23 USING utf8mb4),'','','',1,0,CONVERT(0x46 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x746f6f6c3a67656e3a7175657279 USING utf8mb4),CONVERT(0x23 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'');
INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (1056,CONVERT(0xe7949fe68890e4bfaee694b9 USING utf8mb4),116,2,CONVERT(0x23 USING utf8mb4),'','','',1,0,CONVERT(0x46 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x746f6f6c3a67656e3a65646974 USING utf8mb4),CONVERT(0x23 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'');
INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (1057,CONVERT(0xe7949fe68890e588a0e999a4 USING utf8mb4),116,3,CONVERT(0x23 USING utf8mb4),'','','',1,0,CONVERT(0x46 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x746f6f6c3a67656e3a72656d6f7665 USING utf8mb4),CONVERT(0x23 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'');
INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (1058,CONVERT(0xe5afbce585a5e4bba3e7a081 USING utf8mb4),116,4,CONVERT(0x23 USING utf8mb4),'','','',1,0,CONVERT(0x46 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x746f6f6c3a67656e3a696d706f7274 USING utf8mb4),CONVERT(0x23 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'');
INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (1059,CONVERT(0xe9a284e8a788e4bba3e7a081 USING utf8mb4),116,5,CONVERT(0x23 USING utf8mb4),'','','',1,0,CONVERT(0x46 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x746f6f6c3a67656e3a70726576696577 USING utf8mb4),CONVERT(0x23 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'');
INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (1060,CONVERT(0xe7949fe68890e4bba3e7a081 USING utf8mb4),116,6,CONVERT(0x23 USING utf8mb4),'','','',1,0,CONVERT(0x46 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x746f6f6c3a67656e3a636f6465 USING utf8mb4),CONVERT(0x23 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'');
INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (2200,CONVERT(0xe78eb0e59cbae89e8de59088e7aea1e79086 USING utf8mb4),0,5,CONVERT(0x737570706f7274 USING utf8mb4),NULL,'',CONVERT(0x537570706f7274 USING utf8mb4),1,0,CONVERT(0x4d USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x30 USING utf8mb4),'',CONVERT(0x6e6574776f726b USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),CONVERT(0xe78eb0e59cbae4bfa1e681afe89e8de59088e5b9b3e58fb0 USING utf8mb4));
INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (2201,CONVERT(0xe78eb0e59cbae7aea1e79086 USING utf8mb4),2200,1,CONVERT(0x73697465 USING utf8mb4),CONVERT(0x737570706f72742f736974652f696e646578 USING utf8mb4),'',CONVERT(0x537570706f727453697465 USING utf8mb4),1,0,CONVERT(0x43 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x737570706f72743a736974653a6c697374 USING utf8mb4),CONVERT(0x6d61702d70696e6e6564 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'');
INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (2202,CONVERT(0xe5b9b3e58fb0e7aea1e79086 USING utf8mb4),2200,2,CONVERT(0x706c6174666f726d USING utf8mb4),CONVERT(0x737570706f72742f706c6174666f726d2f696e646578 USING utf8mb4),'',CONVERT(0x537570706f7274506c6174666f726d USING utf8mb4),1,0,CONVERT(0x43 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x737570706f72743a706c6174666f726d3a6c697374 USING utf8mb4),CONVERT(0x70616e656c732d746f702d6c656674 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'');
INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (2203,CONVERT(0xe69c8de58aa1e599a8e7aea1e79086 USING utf8mb4),2200,3,CONVERT(0x736572766572 USING utf8mb4),CONVERT(0x737570706f72742f7365727665722f696e646578 USING utf8mb4),'',CONVERT(0x537570706f7274536572766572 USING utf8mb4),1,0,CONVERT(0x43 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x737570706f72743a7365727665723a6c697374 USING utf8mb4),CONVERT(0x7365727665722d636f67 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'');
INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (2204,CONVERT(0xe7bb84e7bb87e4b88ee88194e7b3bbe4baba USING utf8mb4),2200,4,CONVERT(0x6f7267 USING utf8mb4),CONVERT(0x737570706f72742f6f72672f696e646578 USING utf8mb4),'',CONVERT(0x537570706f72744f7267 USING utf8mb4),1,0,CONVERT(0x43 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x737570706f72743a6f72673a6c697374 USING utf8mb4),CONVERT(0x636f6e746163742d726f756e64 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'');
INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (2205,CONVERT(0xe78988e69cace8aeb0e5bd95 USING utf8mb4),0,7,CONVERT(0x76657273696f6e USING utf8mb4),CONVERT(0x737570706f72742f76657273696f6e2f696e646578 USING utf8mb4),'',CONVERT(0x537570706f727456657273696f6e USING utf8mb4),1,0,CONVERT(0x43 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x737570706f72743a76657273696f6e3a6c697374 USING utf8mb4),CONVERT(0x66696c652d636c6f636b USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),CONVERT(0xe5b9b3e58fb0e58a9fe883bde78988e69cace8aeb0e5bd95e4b8ade5bf83 USING utf8mb4));
INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (2206,CONVERT(0x54494de7b3bbe7bb9fe5b7a1e6a380 USING utf8mb4),2200,6,CONVERT(0x74696d496e7370656374696f6e USING utf8mb4),CONVERT(0x737570706f72742f74696d496e7370656374696f6e2f696e646578 USING utf8mb4),'',CONVERT(0x537570706f727454696d496e7370656374696f6e USING utf8mb4),1,0,CONVERT(0x43 USING utf8mb4),CONVERT(0x31 USING utf8mb4),CONVERT(0x31 USING utf8mb4),CONVERT(0x737570706f72743a74696d496e7370656374696f6e3a6c697374 USING utf8mb4),CONVERT(0x7363616e2d736561726368 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),CONVERT(0xe5b7b2e794b1e887aae58aa8e58c96e5b7a1e6a380e6a8a1e59d97e69bbfe4bba3efbc8ce4bf9de79599e697a7e695b0e68daee585a5e58fa3e99a90e8978f USING utf8mb4));
INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (2207,CONVERT(0xe78988e69cace8aeb0e5bd95 USING utf8mb4),2200,5,CONVERT(0x76657273696f6e USING utf8mb4),CONVERT(0x737570706f72742f76657273696f6e2f696e646578 USING utf8mb4),CONVERT(0x7b226d6f64756c65223a2273697465227d USING utf8mb4),CONVERT(0x537570706f72745369746556657273696f6e USING utf8mb4),1,0,CONVERT(0x43 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x737570706f72743a76657273696f6e3a6c697374 USING utf8mb4),CONVERT(0x66696c652d636c6f636b USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),CONVERT(0xe78eb0e59cbae89e8de59088e7aea1e79086e6a8a1e59d97e78988e69cace8aeb0e5bd95 USING utf8mb4));
INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (2211,CONVERT(0xe78eb0e59cbae69fa5e8afa2 USING utf8mb4),2201,1,CONVERT(0x23 USING utf8mb4),'','','',1,0,CONVERT(0x46 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x737570706f72743a736974653a7175657279 USING utf8mb4),CONVERT(0x23 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'');
INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (2212,CONVERT(0xe78eb0e59cbae696b0e5a29e USING utf8mb4),2201,2,CONVERT(0x23 USING utf8mb4),'','','',1,0,CONVERT(0x46 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x737570706f72743a736974653a616464 USING utf8mb4),CONVERT(0x23 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'');
INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (2213,CONVERT(0xe78eb0e59cbae4bfaee694b9 USING utf8mb4),2201,3,CONVERT(0x23 USING utf8mb4),'','','',1,0,CONVERT(0x46 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x737570706f72743a736974653a65646974 USING utf8mb4),CONVERT(0x23 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'');
INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (2214,CONVERT(0xe78eb0e59cbae588a0e999a4 USING utf8mb4),2201,4,CONVERT(0x23 USING utf8mb4),'','','',1,0,CONVERT(0x46 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x737570706f72743a736974653a72656d6f7665 USING utf8mb4),CONVERT(0x23 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'');
INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (2215,CONVERT(0xe78eb0e59cbae5afbce587ba USING utf8mb4),2201,5,CONVERT(0x23 USING utf8mb4),'','','',1,0,CONVERT(0x46 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x737570706f72743a736974653a6578706f7274 USING utf8mb4),CONVERT(0x23 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'');
INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (2216,CONVERT(0xe78eb0e59cbae5afbce585a5 USING utf8mb4),2201,6,CONVERT(0x23 USING utf8mb4),'','','',1,0,CONVERT(0x46 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x737570706f72743a736974653a696d706f7274 USING utf8mb4),CONVERT(0x23 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'');
INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (2221,CONVERT(0xe5b9b3e58fb0e69fa5e8afa2 USING utf8mb4),2202,1,CONVERT(0x23 USING utf8mb4),'','','',1,0,CONVERT(0x46 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x737570706f72743a706c6174666f726d3a7175657279 USING utf8mb4),CONVERT(0x23 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'');
INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (2222,CONVERT(0xe5b9b3e58fb0e696b0e5a29e USING utf8mb4),2202,2,CONVERT(0x23 USING utf8mb4),'','','',1,0,CONVERT(0x46 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x737570706f72743a706c6174666f726d3a616464 USING utf8mb4),CONVERT(0x23 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'');
INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (2223,CONVERT(0xe5b9b3e58fb0e4bfaee694b9 USING utf8mb4),2202,3,CONVERT(0x23 USING utf8mb4),'','','',1,0,CONVERT(0x46 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x737570706f72743a706c6174666f726d3a65646974 USING utf8mb4),CONVERT(0x23 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'');
INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (2224,CONVERT(0xe5b9b3e58fb0e588a0e999a4 USING utf8mb4),2202,4,CONVERT(0x23 USING utf8mb4),'','','',1,0,CONVERT(0x46 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x737570706f72743a706c6174666f726d3a72656d6f7665 USING utf8mb4),CONVERT(0x23 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'');
INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (2225,CONVERT(0xe5b9b3e58fb0e5afbce587ba USING utf8mb4),2202,5,CONVERT(0x23 USING utf8mb4),'','','',1,0,CONVERT(0x46 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x737570706f72743a706c6174666f726d3a6578706f7274 USING utf8mb4),CONVERT(0x23 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'');
INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (2231,CONVERT(0xe69c8de58aa1e599a8e69fa5e8afa2 USING utf8mb4),2203,1,CONVERT(0x23 USING utf8mb4),'','','',1,0,CONVERT(0x46 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x737570706f72743a7365727665723a7175657279 USING utf8mb4),CONVERT(0x23 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'');
INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (2232,CONVERT(0xe69c8de58aa1e599a8e696b0e5a29e USING utf8mb4),2203,2,CONVERT(0x23 USING utf8mb4),'','','',1,0,CONVERT(0x46 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x737570706f72743a7365727665723a616464 USING utf8mb4),CONVERT(0x23 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'');
INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (2233,CONVERT(0xe69c8de58aa1e599a8e4bfaee694b9 USING utf8mb4),2203,3,CONVERT(0x23 USING utf8mb4),'','','',1,0,CONVERT(0x46 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x737570706f72743a7365727665723a65646974 USING utf8mb4),CONVERT(0x23 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'');
INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (2234,CONVERT(0xe69c8de58aa1e599a8e588a0e999a4 USING utf8mb4),2203,4,CONVERT(0x23 USING utf8mb4),'','','',1,0,CONVERT(0x46 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x737570706f72743a7365727665723a72656d6f7665 USING utf8mb4),CONVERT(0x23 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'');
INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (2235,CONVERT(0xe69c8de58aa1e599a8e5afbce587ba USING utf8mb4),2203,5,CONVERT(0x23 USING utf8mb4),'','','',1,0,CONVERT(0x46 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x737570706f72743a7365727665723a6578706f7274 USING utf8mb4),CONVERT(0x23 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'');
INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (2241,CONVERT(0xe7bb84e7bb87e69fa5e8afa2 USING utf8mb4),2204,1,CONVERT(0x23 USING utf8mb4),'','','',1,0,CONVERT(0x46 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x737570706f72743a6f72673a7175657279 USING utf8mb4),CONVERT(0x23 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'');
INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (2242,CONVERT(0xe7bb84e7bb87e696b0e5a29e USING utf8mb4),2204,2,CONVERT(0x23 USING utf8mb4),'','','',1,0,CONVERT(0x46 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x737570706f72743a6f72673a616464 USING utf8mb4),CONVERT(0x23 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'');
INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (2243,CONVERT(0xe7bb84e7bb87e4bfaee694b9 USING utf8mb4),2204,3,CONVERT(0x23 USING utf8mb4),'','','',1,0,CONVERT(0x46 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x737570706f72743a6f72673a65646974 USING utf8mb4),CONVERT(0x23 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'');
INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (2244,CONVERT(0xe7bb84e7bb87e588a0e999a4 USING utf8mb4),2204,4,CONVERT(0x23 USING utf8mb4),'','','',1,0,CONVERT(0x46 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x737570706f72743a6f72673a72656d6f7665 USING utf8mb4),CONVERT(0x23 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'');
INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (2245,CONVERT(0xe7bb84e7bb87e5afbce587ba USING utf8mb4),2204,5,CONVERT(0x23 USING utf8mb4),'','','',1,0,CONVERT(0x46 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x737570706f72743a6f72673a6578706f7274 USING utf8mb4),CONVERT(0x23 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'');
INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (2261,CONVERT(0x54494de5b7a1e6a380e69fa5e8afa2 USING utf8mb4),2206,1,CONVERT(0x23 USING utf8mb4),'','','',1,0,CONVERT(0x46 USING utf8mb4),CONVERT(0x31 USING utf8mb4),CONVERT(0x31 USING utf8mb4),CONVERT(0x737570706f72743a74696d496e7370656374696f6e3a7175657279 USING utf8mb4),CONVERT(0x23 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'');
INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (2262,CONVERT(0x54494de5b7a1e6a380e689a7e8a18c USING utf8mb4),2206,2,CONVERT(0x23 USING utf8mb4),'','','',1,0,CONVERT(0x46 USING utf8mb4),CONVERT(0x31 USING utf8mb4),CONVERT(0x31 USING utf8mb4),CONVERT(0x737570706f72743a74696d496e7370656374696f6e3a72756e USING utf8mb4),CONVERT(0x23 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'');
INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (2263,CONVERT(0x54494de5b7a1e6a380e5afbce587ba USING utf8mb4),2206,3,CONVERT(0x23 USING utf8mb4),'','','',1,0,CONVERT(0x46 USING utf8mb4),CONVERT(0x31 USING utf8mb4),CONVERT(0x31 USING utf8mb4),CONVERT(0x737570706f72743a74696d496e7370656374696f6e3a6578706f7274 USING utf8mb4),CONVERT(0x23 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'');
INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (2264,CONVERT(0x54494de5b7a1e6a380e9858de7bdae USING utf8mb4),2206,4,CONVERT(0x23 USING utf8mb4),'','','',1,0,CONVERT(0x46 USING utf8mb4),CONVERT(0x31 USING utf8mb4),CONVERT(0x31 USING utf8mb4),CONVERT(0x737570706f72743a74696d496e7370656374696f6e3a636f6e666967 USING utf8mb4),CONVERT(0x23 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'');
INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (2265,CONVERT(0x54494de5b7a1e6a380e8aea1e58892 USING utf8mb4),2206,5,CONVERT(0x23 USING utf8mb4),'','','',1,0,CONVERT(0x46 USING utf8mb4),CONVERT(0x31 USING utf8mb4),CONVERT(0x31 USING utf8mb4),CONVERT(0x737570706f72743a74696d496e7370656374696f6e3a706c616e USING utf8mb4),CONVERT(0x23 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'');
INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (2288,CONVERT(0xe8aebee5a487e6b885e58d95e69fa5e8afa2 USING utf8mb4),2201,14,CONVERT(0x23 USING utf8mb4),'','','',1,0,CONVERT(0x46 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x737570706f72743a65717569706d656e743a7175657279 USING utf8mb4),CONVERT(0x23 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'');
INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (2289,CONVERT(0xe8aebee5a487e6b885e58d95e5afbce587ba USING utf8mb4),2201,15,CONVERT(0x23 USING utf8mb4),'','','',1,0,CONVERT(0x46 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x737570706f72743a65717569706d656e743a6578706f7274 USING utf8mb4),CONVERT(0x23 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'');
INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (2290,CONVERT(0xe69fa5e79c8be6958fe6849fe587ade68dae USING utf8mb4),2203,1,CONVERT(0x23 USING utf8mb4),'','','',1,0,CONVERT(0x46 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x737570706f72743a63726564656e7469616c3a76696577506c61696e USING utf8mb4),CONVERT(0x23 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'');
INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (2291,CONVERT(0xe78eb0e59cbae89e8de59088e585a8e983a8e69d83e99990 USING utf8mb4),2200,99,CONVERT(0x23 USING utf8mb4),'','','',1,0,CONVERT(0x46 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x64617461667573696f6e USING utf8mb4),CONVERT(0x23 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'');
INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (2292,CONVERT(0xe79599e8a880e69fa5e79c8b USING utf8mb4),2201,7,CONVERT(0x23 USING utf8mb4),'','','',1,0,CONVERT(0x46 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x737570706f72743a6d6573736167653a6c697374 USING utf8mb4),CONVERT(0x23 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'');
INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (2293,CONVERT(0xe79599e8a880e58f91e5b883 USING utf8mb4),2201,8,CONVERT(0x23 USING utf8mb4),'','','',1,0,CONVERT(0x46 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x737570706f72743a6d6573736167653a616464 USING utf8mb4),CONVERT(0x23 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'');
INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (2294,CONVERT(0xe7a1ace4bbb6e8b584e4baa7e69fa5e8afa2 USING utf8mb4),2201,9,CONVERT(0x23 USING utf8mb4),'','','',1,0,CONVERT(0x46 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x737570706f72743a686172647761726541737365743a7175657279 USING utf8mb4),CONVERT(0x23 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'');
INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (2295,CONVERT(0xe7a1ace4bbb6e8b584e4baa7e696b0e5a29e USING utf8mb4),2201,10,CONVERT(0x23 USING utf8mb4),'','','',1,0,CONVERT(0x46 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x737570706f72743a686172647761726541737365743a616464 USING utf8mb4),CONVERT(0x23 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'');
INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (2296,CONVERT(0xe7a1ace4bbb6e8b584e4baa7e4bfaee694b9 USING utf8mb4),2201,11,CONVERT(0x23 USING utf8mb4),'','','',1,0,CONVERT(0x46 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x737570706f72743a686172647761726541737365743a65646974 USING utf8mb4),CONVERT(0x23 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'');
INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (2297,CONVERT(0xe7a1ace4bbb6e8b584e4baa7e588a0e999a4 USING utf8mb4),2201,12,CONVERT(0x23 USING utf8mb4),'','','',1,0,CONVERT(0x46 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x737570706f72743a686172647761726541737365743a72656d6f7665 USING utf8mb4),CONVERT(0x23 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'');
INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (2298,CONVERT(0xe7a1ace4bbb6e8b584e4baa7e5afbce587ba USING utf8mb4),2201,13,CONVERT(0x23 USING utf8mb4),'','','',1,0,CONVERT(0x46 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x737570706f72743a686172647761726541737365743a6578706f7274 USING utf8mb4),CONVERT(0x23 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'');
INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (2300,CONVERT(0xe887aae58aa8e58c96e5b7a1e6a380 USING utf8mb4),0,6,CONVERT(0x6175746f496e7370656374696f6e USING utf8mb4),CONVERT(0x4c61796f7574 USING utf8mb4),'',CONVERT(0x4175746f496e7370656374696f6e USING utf8mb4),1,0,CONVERT(0x4d USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x30 USING utf8mb4),'',CONVERT(0x7363616e2d736561726368 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),CONVERT(0xe58fafe9858de7bdaee887aae58aa8e58c96e5b7a1e6a380e4b8ade5bf83 USING utf8mb4));
INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (2301,CONVERT(0xe5b7a1e6a380e9858de7bdae USING utf8mb4),2300,3,CONVERT(0x636f6e666967 USING utf8mb4),CONVERT(0x737570706f72742f6175746f496e7370656374696f6e2f696e646578 USING utf8mb4),CONVERT(0x7b22746162223a2274656d706c617465227d USING utf8mb4),CONVERT(0x4175746f496e7370656374696f6e436f6e666967 USING utf8mb4),1,0,CONVERT(0x43 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x30 USING utf8mb4),'',CONVERT(0x776f726b666c6f77 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),CONVERT(0xe59ca8e6a8a1e69dbfe6ada5e9aaa4e4b8ade98089e68ba9e5b7a1e6a380e5b7a5e585b7e5b9b6e9858de7bdaee79baee6a087e38081e99888e580bce5928ce58f82e695b0 USING utf8mb4));
INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (2302,CONVERT(0xe5b7a1e6a380e79baee6a087 USING utf8mb4),2300,2,CONVERT(0x746172676574 USING utf8mb4),CONVERT(0x737570706f72742f6175746f496e7370656374696f6e2f696e646578 USING utf8mb4),CONVERT(0x7b22746162223a22746172676574227d USING utf8mb4),CONVERT(0x4175746f496e7370656374696f6e546172676574 USING utf8mb4),1,0,CONVERT(0x43 USING utf8mb4),CONVERT(0x31 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x737570706f72743a6175746f496e7370656374696f6e3a746172676574 USING utf8mb4),CONVERT(0x7365727665722d636f67 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),CONVERT(0xe697a7e79baee6a087e78bace7ab8be585a5e58fa3e5b7b2e99a90e8978fefbc8ce79baee6a087e59ca8e5b7a1e6a380e6a8a1e69dbfe6ada5e9aaa4e58685e9858de7bdae USING utf8mb4));
INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (2303,CONVERT(0xe5b7a1e6a380e8aea1e58892 USING utf8mb4),2300,3,CONVERT(0x706c616e USING utf8mb4),CONVERT(0x737570706f72742f6175746f496e7370656374696f6e2f696e646578 USING utf8mb4),CONVERT(0x7b22746162223a22706c616e227d USING utf8mb4),CONVERT(0x4175746f496e7370656374696f6e506c616e USING utf8mb4),1,0,CONVERT(0x43 USING utf8mb4),CONVERT(0x31 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x737570706f72743a6175746f496e7370656374696f6e3a706c616e USING utf8mb4),CONVERT(0x74696d65 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),CONVERT(0xe5b7b2e59088e5b9b6e588b0e5b7a1e6a380e9858de7bdaee585a5e58fa3 USING utf8mb4));
INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (2304,CONVERT(0xe5b7a1e6a380e8aeb0e5bd95 USING utf8mb4),2300,3,CONVERT(0x7265636f7264 USING utf8mb4),CONVERT(0x737570706f72742f6175746f496e7370656374696f6e2f696e646578 USING utf8mb4),CONVERT(0x7b22746162223a227265636f7264227d USING utf8mb4),CONVERT(0x4175746f496e7370656374696f6e5265636f7264 USING utf8mb4),1,0,CONVERT(0x43 USING utf8mb4),CONVERT(0x31 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x737570706f72743a6175746f496e7370656374696f6e3a7175657279 USING utf8mb4),CONVERT(0x66696c652d636c6f636b USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),CONVERT(0xe5b7a1e6a380e8aeb0e5bd95e5b7b2e59088e5b9b6e588b0e5b7a1e6a380e680bbe8a788efbc8ce4bf9de79599e99a90e8978fe8b7afe794b1e585bce5aeb9e58e86e58fb2e585a5e58fa3 USING utf8mb4));
INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (2305,CONVERT(0xe5b7a1e6a380e680bbe8a788 USING utf8mb4),2300,2,CONVERT(0x64617368626f617264 USING utf8mb4),CONVERT(0x737570706f72742f6175746f496e7370656374696f6e2f696e646578 USING utf8mb4),CONVERT(0x7b22746162223a2264617368626f617264227d USING utf8mb4),CONVERT(0x4175746f496e7370656374696f6e44617368626f617264 USING utf8mb4),1,0,CONVERT(0x43 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x737570706f72743a6175746f496e7370656374696f6e3a7175657279 USING utf8mb4),CONVERT(0x63686172742d6e6f2d617865732d636f6d62696e6564 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),CONVERT(0xe68c89e5a4a9e69fa5e79c8be5b7a1e6a380e8aeb0e5bd95e5928ce9ab98e9a291e6af8fe697a5e581a5e5bab7e6988ee7bb86 USING utf8mb4));
INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (2306,CONVERT(0xe5b7a1e6a380e78988e69cace8aeb0e5bd95 USING utf8mb4),2300,4,CONVERT(0x76657273696f6e USING utf8mb4),CONVERT(0x737570706f72742f76657273696f6e2f696e646578 USING utf8mb4),CONVERT(0x7b226d6f64756c65223a226175746f496e7370656374696f6e227d USING utf8mb4),CONVERT(0x4175746f496e7370656374696f6e56657273696f6e USING utf8mb4),1,0,CONVERT(0x43 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x737570706f72743a76657273696f6e3a6c697374 USING utf8mb4),CONVERT(0x66696c652d636c6f636b USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),CONVERT(0xe887aae58aa8e58c96e5b7a1e6a380e6a8a1e59d97e78988e69cace8aeb0e5bd95 USING utf8mb4));
INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (2307,CONVERT(0xe5b7a1e6a380e9a9bee9a9b6e888b1 USING utf8mb4),2300,1,CONVERT(0x636f636b706974 USING utf8mb4),CONVERT(0x737570706f72742f6175746f496e7370656374696f6e2f636f636b706974 USING utf8mb4),'',CONVERT(0x4175746f496e7370656374696f6e436f636b706974 USING utf8mb4),1,0,CONVERT(0x43 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x737570706f72743a6175746f496e7370656374696f6e3a7175657279 USING utf8mb4),CONVERT(0x6761756765 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),CONVERT(0xe7bb9fe4b880e5b195e7a4bae4be8be8a18ce5b7a1e6a380e38081e9ab98e9a291e581a5e5bab7e38081e5bd93e5898de8aea1e58892e78ab6e68081e5928ce5bd93e697a5e997aee9a298 USING utf8mb4));
INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (2311,CONVERT(0xe6a8a1e69dbfe7aea1e79086 USING utf8mb4),2301,1,CONVERT(0x23 USING utf8mb4),'','','',1,0,CONVERT(0x46 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x737570706f72743a6175746f496e7370656374696f6e3a74656d706c617465 USING utf8mb4),CONVERT(0x23 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'');
INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (2312,CONVERT(0xe79baee6a087e7aea1e79086 USING utf8mb4),2301,2,CONVERT(0x23 USING utf8mb4),'','','',1,0,CONVERT(0x46 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x737570706f72743a6175746f496e7370656374696f6e3a746172676574 USING utf8mb4),CONVERT(0x23 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'');
INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (2313,CONVERT(0xe8aea1e58892e7aea1e79086 USING utf8mb4),2301,3,CONVERT(0x23 USING utf8mb4),'','','',1,0,CONVERT(0x46 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x737570706f72743a6175746f496e7370656374696f6e3a706c616e USING utf8mb4),CONVERT(0x23 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'');
INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (2314,CONVERT(0xe8aeb0e5bd95e69fa5e8afa2 USING utf8mb4),2304,1,CONVERT(0x23 USING utf8mb4),'','','',1,0,CONVERT(0x46 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x737570706f72743a6175746f496e7370656374696f6e3a7175657279 USING utf8mb4),CONVERT(0x23 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'');
INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (2315,CONVERT(0xe5b7a1e6a380e689a7e8a18c USING utf8mb4),2301,4,CONVERT(0x23 USING utf8mb4),'','','',1,0,CONVERT(0x46 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x737570706f72743a6175746f496e7370656374696f6e3a72756e USING utf8mb4),CONVERT(0x23 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'');
INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (2316,CONVERT(0xe5b7a1e6a380e5afbce587ba USING utf8mb4),2304,2,CONVERT(0x23 USING utf8mb4),'','','',1,0,CONVERT(0x46 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x737570706f72743a6175746f496e7370656374696f6e3a6578706f7274 USING utf8mb4),CONVERT(0x23 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'');
INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (2321,CONVERT(0xe8aebee5a487e7bb9fe4b880e696b0e5a29e USING utf8mb4),2201,16,CONVERT(0x23 USING utf8mb4),'','','',1,0,CONVERT(0x46 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x737570706f72743a65717569706d656e743a616464 USING utf8mb4),CONVERT(0x23 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'');
INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (2322,CONVERT(0xe8aebee5a487e7bb9fe4b880e4bfaee694b9 USING utf8mb4),2201,17,CONVERT(0x23 USING utf8mb4),'','','',1,0,CONVERT(0x46 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x737570706f72743a65717569706d656e743a65646974 USING utf8mb4),CONVERT(0x23 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'');
INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (2323,CONVERT(0xe8aebee5a487e7bb9fe4b880e588a0e999a4 USING utf8mb4),2201,18,CONVERT(0x23 USING utf8mb4),'','','',1,0,CONVERT(0x46 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x737570706f72743a65717569706d656e743a72656d6f7665 USING utf8mb4),CONVERT(0x23 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'');
INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (2324,CONVERT(0xe69c8de58aa1e599a8e799bbe5bd95e6a0a1e9aa8c USING utf8mb4),2203,6,CONVERT(0x23 USING utf8mb4),'','','',1,0,CONVERT(0x46 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x737570706f72743a63726564656e7469616c3a766572696679 USING utf8mb4),CONVERT(0x23 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),CONVERT(0xe4bdbfe794a8e799bbe8aeb0e587ade68daee9aa8ce8af81535348e799bbe5bd95efbc8ce4b88de8bf94e59b9ee6988ee69687e5af86e7a081 USING utf8mb4));
INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (2325,CONVERT(0xe689abe68f8fe69c8de58aa1e599a8e5b9b6e5908ce6ada5 USING utf8mb4),2203,7,CONVERT(0x23 USING utf8mb4),'','','',1,0,CONVERT(0x46 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x737570706f72743a7365727665723a7363616e USING utf8mb4),CONVERT(0x23 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),CONVERT(0xe68e88e69d83e7bd91e6aeb5e58f91e78eb0e38081e587ade68daee6b1a0e58f8ae9aa8ce8af81e68890e58a9fe5908ee5908ce6ada5 USING utf8mb4));
INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (2400,CONVERT(0x4950e58886e9858de7aea1e68ea7 USING utf8mb4),0,8,CONVERT(0x6970616d USING utf8mb4),'','','',1,0,CONVERT(0x4d USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x30 USING utf8mb4),'',CONVERT(0x6e6574776f726b USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),CONVERT(0xe78bace7ab8b4950e58886e9858de7aea1e68ea7e5ba94e794a8e79baee5bd95 USING utf8mb4));
INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (2401,CONVERT(0xe7bd91e6aeb5e69fa5e8afa2 USING utf8mb4),2415,1,CONVERT(0x23 USING utf8mb4),'','','',1,0,CONVERT(0x46 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x6970616d3a6e6574776f726b3a6c697374 USING utf8mb4),CONVERT(0x23 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'');
INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (2402,CONVERT(0xe7bd91e6aeb5e696b0e5a29e USING utf8mb4),2415,2,CONVERT(0x23 USING utf8mb4),'','','',1,0,CONVERT(0x46 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x6970616d3a6e6574776f726b3a616464 USING utf8mb4),CONVERT(0x23 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'');
INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (2403,CONVERT(0xe7bd91e6aeb5e4bfaee694b9 USING utf8mb4),2415,3,CONVERT(0x23 USING utf8mb4),'','','',1,0,CONVERT(0x46 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x6970616d3a6e6574776f726b3a65646974 USING utf8mb4),CONVERT(0x23 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'');
INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (2404,CONVERT(0xe7bd91e6aeb5e588a0e999a4 USING utf8mb4),2415,4,CONVERT(0x23 USING utf8mb4),'','','',1,0,CONVERT(0x46 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x6970616d3a6e6574776f726b3a72656d6f7665 USING utf8mb4),CONVERT(0x23 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'');
INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (2405,CONVERT(0xe59cb0e59d80e69fa5e8afa2 USING utf8mb4),2415,5,CONVERT(0x23 USING utf8mb4),'','','',1,0,CONVERT(0x46 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x6970616d3a616464726573733a6c697374 USING utf8mb4),CONVERT(0x23 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'');
INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (2406,CONVERT(0xe59cb0e59d80e58da0e794a8 USING utf8mb4),2415,6,CONVERT(0x23 USING utf8mb4),'','','',1,0,CONVERT(0x46 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x6970616d3a616464726573733a616c6c6f63617465 USING utf8mb4),CONVERT(0x23 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'');
INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (2407,CONVERT(0xe59cb0e59d80e4bfaee694b9 USING utf8mb4),2415,7,CONVERT(0x23 USING utf8mb4),'','','',1,0,CONVERT(0x46 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x6970616d3a616464726573733a65646974 USING utf8mb4),CONVERT(0x23 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'');
INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (2408,CONVERT(0xe59cb0e59d80e9878ae694be USING utf8mb4),2415,8,CONVERT(0x23 USING utf8mb4),'','','',1,0,CONVERT(0x46 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x6970616d3a616464726573733a72656c65617365 USING utf8mb4),CONVERT(0x23 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'');
INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (2409,CONVERT(0xe59cb0e59d80e4b88be58f91 USING utf8mb4),2415,9,CONVERT(0x23 USING utf8mb4),'','','',1,0,CONVERT(0x46 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x6970616d3a616464726573733a6973737565 USING utf8mb4),CONVERT(0x23 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),CONVERT(0xe6a087e8aeb04950e4b8bae5b7b2e4b88be58f91e78ab6e68081 USING utf8mb4));
INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (2410,CONVERT(0xe59cb0e59d80e5afbce587ba USING utf8mb4),2415,10,CONVERT(0x23 USING utf8mb4),'','','',1,0,CONVERT(0x46 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x6970616d3a616464726573733a6578706f7274 USING utf8mb4),CONVERT(0x23 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'');
INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (2411,CONVERT(0xe7bd91e6aeb5e689abe68f8f USING utf8mb4),2415,11,CONVERT(0x23 USING utf8mb4),'','','',1,0,CONVERT(0x46 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x6970616d3a6e6574776f726b3a7363616e USING utf8mb4),CONVERT(0x23 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),CONVERT(0xe689abe68f8fe7bd91e6aeb54950e8bf9ee9809ae680a7 USING utf8mb4));
INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (2412,CONVERT(0xe69fa5e79c8be8aebee5a487e5af86e7a081 USING utf8mb4),2415,12,CONVERT(0x23 USING utf8mb4),'','','',1,0,CONVERT(0x46 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x6970616d3a63726564656e7469616c3a76696577 USING utf8mb4),CONVERT(0x23 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),CONVERT(0xe68c89e99c80e69fa5e79c8b4950414de8aebee5a487e5af86e7a081e5b9b6e8aeb0e5bd95e5aea1e8aea1 USING utf8mb4));
INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (2414,CONVERT(0xe680bbe8a788 USING utf8mb4),2400,1,CONVERT(0x6f76657276696577 USING utf8mb4),CONVERT(0x6970616d2f6f766572766965772f696e646578 USING utf8mb4),'',CONVERT(0x4970616d4f76657276696577 USING utf8mb4),1,0,CONVERT(0x43 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x6970616d3a6e6574776f726b3a6c697374 USING utf8mb4),CONVERT(0x63686172742d6e6f2d617865732d636f6d62696e6564 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),CONVERT(0x4950e58886e9858de585a8e59f9fe7bb9fe8aea1e4b88ee5b08fe58cbae6988ee7bb86 USING utf8mb4));
INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (2415,CONVERT(0x4950e58886e9858de9858de7bdae USING utf8mb4),2400,2,CONVERT(0x636f6e666967 USING utf8mb4),CONVERT(0x6970616d2f696e646578 USING utf8mb4),'',CONVERT(0x4970616d436f6e666967 USING utf8mb4),1,0,CONVERT(0x43 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x6970616d3a6e6574776f726b3a6c697374 USING utf8mb4),CONVERT(0x726f757465 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),CONVERT(0xe7bd91e6aeb5e380814950e9858de7bdaee4b88ee59cb0e59d80e58fb0e8b4a6 USING utf8mb4));
INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (2416,CONVERT(0x4950e58886e9858de8a1a8e6a0bc USING utf8mb4),2400,3,CONVERT(0x776f726b626f6f6b USING utf8mb4),CONVERT(0x6970616d2f776f726b626f6f6b2f696e646578 USING utf8mb4),'',CONVERT(0x4970616d576f726b626f6f6b USING utf8mb4),1,0,CONVERT(0x43 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x6970616d3a616464726573733a6c697374 USING utf8mb4),CONVERT(0x657863656c USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),CONVERT(0xe68c89e7bd91e6aeb5e68896e5b08fe58cbae69fa5e79c8be5b9b6e7bbb4e68aa44950e58886e9858de59ca8e7babfe5b7a5e4bd9ce8a1a8 USING utf8mb4));
INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (2500,CONVERT(0xe69687e6a1a3e7aea1e79086 USING utf8mb4),0,9,CONVERT(0x646f63756d656e7473 USING utf8mb4),'','',CONVERT(0x446f63756d656e7473 USING utf8mb4),1,0,CONVERT(0x4d USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x30 USING utf8mb4),'',CONVERT(0x666f6c6465722d74726565 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),CONVERT(0xe88ba5e4be9de58e9fe7949fe8a792e889b2e88f9ce58d95e79baee5bd95efbc9be5ba94e794a8e8aebfe997aee794b1e5ad90e88f9ce58d95e2809ce69687e4bbb6e7aea1e79086e2809de68e88e69d83 USING utf8mb4));
INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (2501,CONVERT(0xe79baee5bd95e7aea1e79086 USING utf8mb4),2507,1,CONVERT(0x23 USING utf8mb4),'','','',1,0,CONVERT(0x46 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x646f63756d656e743a666f6c6465723a6d616e616765 USING utf8mb4),CONVERT(0x23 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'');
INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (2502,CONVERT(0xe696b0e5bbbae69687e6a1a3 USING utf8mb4),2507,2,CONVERT(0x23 USING utf8mb4),'','','',1,0,CONVERT(0x46 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x646f63756d656e743a646f63756d656e743a616464 USING utf8mb4),CONVERT(0x23 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'');
INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (2503,CONVERT(0xe4bfaee694b9e69687e6a1a3 USING utf8mb4),2507,3,CONVERT(0x23 USING utf8mb4),'','','',1,0,CONVERT(0x46 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x646f63756d656e743a646f63756d656e743a65646974 USING utf8mb4),CONVERT(0x23 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'');
INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (2504,CONVERT(0xe588a0e999a4e69687e6a1a3 USING utf8mb4),2507,4,CONVERT(0x23 USING utf8mb4),'','','',1,0,CONVERT(0x46 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x646f63756d656e743a646f63756d656e743a72656d6f7665 USING utf8mb4),CONVERT(0x23 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'');
INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (2505,CONVERT(0xe585b1e4baabe69687e6a1a3 USING utf8mb4),2507,5,CONVERT(0x23 USING utf8mb4),'','','',1,0,CONVERT(0x46 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x646f63756d656e743a646f63756d656e743a7368617265 USING utf8mb4),CONVERT(0x23 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'');
INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (2506,CONVERT(0xe4b88be8bdbde69687e6a1a3 USING utf8mb4),2507,6,CONVERT(0x23 USING utf8mb4),'','','',1,0,CONVERT(0x46 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x646f63756d656e743a646f63756d656e743a646f776e6c6f6164 USING utf8mb4),CONVERT(0x23 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'');
INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (2507,CONVERT(0xe69687e4bbb6e7aea1e79086 USING utf8mb4),2500,1,'',CONVERT(0x646f63756d656e742f776f726b73706163652f696e646578 USING utf8mb4),'',CONVERT(0x446f63756d656e74576f726b7370616365 USING utf8mb4),1,0,CONVERT(0x43 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x646f63756d656e743a66696c653a6d616e616765 USING utf8mb4),CONVERT(0x66696c6573 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),CONVERT(0xe69687e4bbb6e7aea1e79086e5ba94e794a8e585a5e58fa3efbc9be4bdbfe794a8e88ba5e4be9d7379735f6d656e752f7379735f726f6c655f6d656e75e58e9fe7949fe68e88e69d83 USING utf8mb4));
INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (2510,CONVERT(0xe79fa5e8af86e4b8ade5bf83 USING utf8mb4),0,10,CONVERT(0x6b6e6f776c65646765 USING utf8mb4),CONVERT(0x6b6e6f776c656467652f776f726b73706163652f696e646578 USING utf8mb4),'',CONVERT(0x4b6e6f776c65646765576f726b7370616365 USING utf8mb4),1,0,CONVERT(0x43 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x6b6e6f776c656467653a706167653a6c697374 USING utf8mb4),CONVERT(0x646f63756d656e746174696f6e USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),CONVERT(0xe5ae8ce585a8e7a6bbe7babfe79a84e79fa5e8af86e99885e8afbbe38081e7bc96e8be91e38081e78988e69cace5928ce78eb0e69c89e69687e6a1a3e88194e58aa8 USING utf8mb4));
INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (2511,CONVERT(0xe7bbb4e68aa4e79fa5e8af86 USING utf8mb4),2510,1,CONVERT(0x23 USING utf8mb4),'','','',1,0,CONVERT(0x46 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x6b6e6f776c656467653a706167653a7772697465 USING utf8mb4),CONVERT(0x23 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),CONVERT(0xe58581e8aeb8e5889be5bbbae5928ce4bfaee694b9e79fa5e8af86efbc8ce6af8fe6aca1e4bf9de5ad98e4baa7e7949fe78988e69cace8aeb0e5bd95 USING utf8mb4));
INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (2512,CONVERT(0xe7aea1e79086e79fa5e8af86e7a9bae997b4 USING utf8mb4),2510,2,CONVERT(0x23 USING utf8mb4),'','','',1,0,CONVERT(0x46 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x6b6e6f776c656467653a73706163653a6d616e616765 USING utf8mb4),CONVERT(0x23 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),CONVERT(0xe7aea1e79086e79fa5e8af86e7a9bae997b4e5928ce79baee5bd95 USING utf8mb4));
INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (2513,CONVERT(0xe7a7bbe999a4e4b88ee681a2e5a48de79fa5e8af86 USING utf8mb4),2510,3,CONVERT(0x23 USING utf8mb4),'','','',1,0,CONVERT(0x46 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x6b6e6f776c656467653a706167653a72656d6f7665 USING utf8mb4),CONVERT(0x23 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),CONVERT(0xe7a7bbe585a5e59b9ee694b6e7ab99e5b9b6e681a2e5a48de79fa5e8af86 USING utf8mb4));
INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (3000,CONVERT(0xe799bde5908de58d95e7aea1e79086 USING utf8mb4),0,6,CONVERT(0x77686974656c697374 USING utf8mb4),NULL,'','',1,0,CONVERT(0x4d USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x30 USING utf8mb4),'',CONVERT(0x736869656c642d636865636b USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),CONVERT(0xe799bde5908de58d95e7aea1e79086e79baee5bd95 USING utf8mb4));
INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (3001,CONVERT(0xe8bda6e7898ce7aea1e68ea7 USING utf8mb4),3000,1,CONVERT(0x706c617465 USING utf8mb4),CONVERT(0x77686974656c6973742f706c6174652f696e646578 USING utf8mb4),'','',1,0,CONVERT(0x43 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x77686974656c6973743a706c6174653a6c697374 USING utf8mb4),CONVERT(0x6361722d66726f6e74 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),CONVERT(0xe8bda6e7898ce7aea1e68ea7e88f9ce58d95 USING utf8mb4));
INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (3002,CONVERT(0xe8bf87e6bba4e695b0e68dae USING utf8mb4),3000,2,CONVERT(0x66696c74657244617461 USING utf8mb4),CONVERT(0x77686974656c6973742f66696c746572446174612f696e646578 USING utf8mb4),'','',1,0,CONVERT(0x43 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x77686974656c6973743a66696c746572446174613a6c697374 USING utf8mb4),CONVERT(0x6c6973742d66696c746572 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),CONVERT(0xe8bf87e6bba4e695b0e68daee88f9ce58d95 USING utf8mb4));
INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (3003,CONVERT(0xe8bda6e7898ce7aea1e68ea7e69fa5e8afa2 USING utf8mb4),3001,1,CONVERT(0x23 USING utf8mb4),'','','',1,0,CONVERT(0x46 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x77686974656c6973743a706c6174653a7175657279 USING utf8mb4),CONVERT(0x23 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'');
INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (3004,CONVERT(0xe8bda6e7898ce7aea1e68ea7e696b0e5a29e USING utf8mb4),3001,2,CONVERT(0x23 USING utf8mb4),'','','',1,0,CONVERT(0x46 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x77686974656c6973743a706c6174653a616464 USING utf8mb4),CONVERT(0x23 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'');
INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (3005,CONVERT(0xe8bda6e7898ce7aea1e68ea7e4bfaee694b9 USING utf8mb4),3001,3,CONVERT(0x23 USING utf8mb4),'','','',1,0,CONVERT(0x46 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x77686974656c6973743a706c6174653a65646974 USING utf8mb4),CONVERT(0x23 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'');
INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (3006,CONVERT(0xe8bda6e7898ce7aea1e68ea7e588a0e999a4 USING utf8mb4),3001,4,CONVERT(0x23 USING utf8mb4),'','','',1,0,CONVERT(0x46 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x77686974656c6973743a706c6174653a72656d6f7665 USING utf8mb4),CONVERT(0x23 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'');
INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (3007,CONVERT(0xe8bda6e7898ce7aea1e68ea7e5afbce587ba USING utf8mb4),3001,5,CONVERT(0x23 USING utf8mb4),'','','',1,0,CONVERT(0x46 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x77686974656c6973743a706c6174653a6578706f7274 USING utf8mb4),CONVERT(0x23 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'');
INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (3008,CONVERT(0xe8bf87e6bba4e695b0e68daee69fa5e8afa2 USING utf8mb4),3002,1,CONVERT(0x23 USING utf8mb4),'','','',1,0,CONVERT(0x46 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x77686974656c6973743a66696c746572446174613a7175657279 USING utf8mb4),CONVERT(0x23 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'');
INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (3009,CONVERT(0xe8bf87e6bba4e695b0e68daee588a0e999a4 USING utf8mb4),3002,2,CONVERT(0x23 USING utf8mb4),'','','',1,0,CONVERT(0x46 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x77686974656c6973743a66696c746572446174613a72656d6f7665 USING utf8mb4),CONVERT(0x23 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'');
INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (3010,CONVERT(0xe8bf87e6bba4e695b0e68daee5afbce587ba USING utf8mb4),3002,3,CONVERT(0x23 USING utf8mb4),'','','',1,0,CONVERT(0x46 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x77686974656c6973743a66696c746572446174613a6578706f7274 USING utf8mb4),CONVERT(0x23 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'');
INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (3011,CONVERT(0xe8bf87e6bba4e695b0e68daee68b89e58f96 USING utf8mb4),3002,4,CONVERT(0x23 USING utf8mb4),'','','',1,0,CONVERT(0x46 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x77686974656c6973743a66696c746572446174613a70756c6c USING utf8mb4),CONVERT(0x23 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'');
INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (3012,CONVERT(0xe8bda6e7898ce7aea1e68ea7e5afbce585a5 USING utf8mb4),3001,6,CONVERT(0x23 USING utf8mb4),'','','',1,0,CONVERT(0x46 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x77686974656c6973743a706c6174653a696d706f7274 USING utf8mb4),CONVERT(0x23 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'');
INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (3013,CONVERT(0xe695b0e68daee6b2bbe79086 USING utf8mb4),0,6,CONVERT(0x676f7665726e616e6365 USING utf8mb4),NULL,NULL,CONVERT(0x44617461476f7665726e616e6365 USING utf8mb4),1,1,CONVERT(0x4d USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x30 USING utf8mb4),'',CONVERT(0x74726565 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),CONVERT(0xe78bace7ab8be695b0e68daee6b2bbe79086e6a8a1e59d97 USING utf8mb4));
INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (3014,CONVERT(0xe6b2bbe79086e5b7a5e4bd9ce58fb0 USING utf8mb4),3013,1,CONVERT(0x776f726b7370616365 USING utf8mb4),CONVERT(0x676f7665726e616e63652f776f726b73706163652f696e646578 USING utf8mb4),NULL,CONVERT(0x44617461476f7665726e616e6365576f726b7370616365 USING utf8mb4),1,1,CONVERT(0x43 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x676f7665726e616e63653a666c6f773a6c697374 USING utf8mb4),CONVERT(0x6a6f62 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),CONVERT(0xe6b58fe8a788e599a8e8aebee8aea1e585a5e58fa3e38081e7bb84e4bbb6e79baee5bd95e4b88ee99a94e7a6bbe6a0b7e69cace6b58be8af95 USING utf8mb4));
INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (3015,CONVERT(0xe9a1b9e79baee696b0e5a29e USING utf8mb4),3014,1,CONVERT(0x23 USING utf8mb4),NULL,NULL,'',1,0,CONVERT(0x46 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x676f7665726e616e63653a70726f6a6563743a616464 USING utf8mb4),CONVERT(0x23 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'');
INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (3016,CONVERT(0xe6b581e7a88be696b0e5a29e USING utf8mb4),3014,2,CONVERT(0x23 USING utf8mb4),NULL,NULL,'',1,0,CONVERT(0x46 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x676f7665726e616e63653a666c6f773a616464 USING utf8mb4),CONVERT(0x23 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'');
INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (3017,CONVERT(0xe6b581e7a88be4bfaee694b9 USING utf8mb4),3014,3,CONVERT(0x23 USING utf8mb4),NULL,NULL,'',1,0,CONVERT(0x46 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x676f7665726e616e63653a666c6f773a65646974 USING utf8mb4),CONVERT(0x23 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'');
INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (3018,CONVERT(0xe99a94e7a6bbe6a0b7e69cace6b58be8af95 USING utf8mb4),3014,4,CONVERT(0x23 USING utf8mb4),NULL,NULL,'',1,0,CONVERT(0x46 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x676f7665726e616e63653a666c6f773a74657374 USING utf8mb4),CONVERT(0x23 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'');

-- Technical seed: sys_dict_type (13 rows)
INSERT INTO `sys_dict_type` (`dict_id`,`dict_name`,`dict_type`,`status`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (1,CONVERT(0xe794a8e688b7e680a7e588ab USING utf8mb4),CONVERT(0x7379735f757365725f736578 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),CONVERT(0xe794a8e688b7e680a7e588abe58897e8a1a8 USING utf8mb4));
INSERT INTO `sys_dict_type` (`dict_id`,`dict_name`,`dict_type`,`status`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (2,CONVERT(0xe88f9ce58d95e78ab6e68081 USING utf8mb4),CONVERT(0x7379735f73686f775f68696465 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),CONVERT(0xe88f9ce58d95e78ab6e68081e58897e8a1a8 USING utf8mb4));
INSERT INTO `sys_dict_type` (`dict_id`,`dict_name`,`dict_type`,`status`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (3,CONVERT(0xe7b3bbe7bb9fe5bc80e585b3 USING utf8mb4),CONVERT(0x7379735f6e6f726d616c5f64697361626c65 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),CONVERT(0xe7b3bbe7bb9fe5bc80e585b3e58897e8a1a8 USING utf8mb4));
INSERT INTO `sys_dict_type` (`dict_id`,`dict_name`,`dict_type`,`status`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (4,CONVERT(0xe4bbbbe58aa1e78ab6e68081 USING utf8mb4),CONVERT(0x7379735f6a6f625f737461747573 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),CONVERT(0xe4bbbbe58aa1e78ab6e68081e58897e8a1a8 USING utf8mb4));
INSERT INTO `sys_dict_type` (`dict_id`,`dict_name`,`dict_type`,`status`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (5,CONVERT(0xe4bbbbe58aa1e58886e7bb84 USING utf8mb4),CONVERT(0x7379735f6a6f625f67726f7570 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),CONVERT(0xe4bbbbe58aa1e58886e7bb84e58897e8a1a8 USING utf8mb4));
INSERT INTO `sys_dict_type` (`dict_id`,`dict_name`,`dict_type`,`status`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (6,CONVERT(0xe7b3bbe7bb9fe698afe590a6 USING utf8mb4),CONVERT(0x7379735f7965735f6e6f USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),CONVERT(0xe7b3bbe7bb9fe698afe590a6e58897e8a1a8 USING utf8mb4));
INSERT INTO `sys_dict_type` (`dict_id`,`dict_name`,`dict_type`,`status`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (7,CONVERT(0xe9809ae79fa5e7b1bbe59e8b USING utf8mb4),CONVERT(0x7379735f6e6f746963655f74797065 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),CONVERT(0xe9809ae79fa5e7b1bbe59e8be58897e8a1a8 USING utf8mb4));
INSERT INTO `sys_dict_type` (`dict_id`,`dict_name`,`dict_type`,`status`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (8,CONVERT(0xe9809ae79fa5e78ab6e68081 USING utf8mb4),CONVERT(0x7379735f6e6f746963655f737461747573 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),CONVERT(0xe9809ae79fa5e78ab6e68081e58897e8a1a8 USING utf8mb4));
INSERT INTO `sys_dict_type` (`dict_id`,`dict_name`,`dict_type`,`status`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (9,CONVERT(0xe6938de4bd9ce7b1bbe59e8b USING utf8mb4),CONVERT(0x7379735f6f7065725f74797065 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),CONVERT(0xe6938de4bd9ce7b1bbe59e8be58897e8a1a8 USING utf8mb4));
INSERT INTO `sys_dict_type` (`dict_id`,`dict_name`,`dict_type`,`status`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (10,CONVERT(0xe7b3bbe7bb9fe78ab6e68081 USING utf8mb4),CONVERT(0x7379735f636f6d6d6f6e5f737461747573 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),CONVERT(0xe799bbe5bd95e78ab6e68081e58897e8a1a8 USING utf8mb4));
INSERT INTO `sys_dict_type` (`dict_id`,`dict_name`,`dict_type`,`status`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (100,CONVERT(0xe7bd91e7bb9ce78eafe5a283 USING utf8mb4),CONVERT(0x737570706f72745f6e6574776f726b5f656e76 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),CONVERT(0xe78eb0e59cbae89e8de59088e4b8bbe5b9b3e58fb0e7bd91e7bb9ce78eafe5a283 USING utf8mb4));
INSERT INTO `sys_dict_type` (`dict_id`,`dict_name`,`dict_type`,`status`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (101,CONVERT(0xe88194e7b3bbe4babae8a792e889b2 USING utf8mb4),CONVERT(0x737570706f72745f636f6e746163745f726f6c65 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),CONVERT(0xe78eb0e59cbae89e8de59088e88194e7b3bbe4babae8a792e889b2 USING utf8mb4));
INSERT INTO `sys_dict_type` (`dict_id`,`dict_name`,`dict_type`,`status`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (102,CONVERT(0xe7a1ace4bbb6e8b584e4baa7e7b1bbe59e8b USING utf8mb4),CONVERT(0x737570706f72745f68617264776172655f74797065 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),CONVERT(0xe78eb0e59cbae89e8de59088e7a1ace4bbb6e8b584e4baa7e7b1bbe59e8b USING utf8mb4));

-- Technical seed: sys_dict_data (42 rows)
INSERT INTO `sys_dict_data` (`dict_code`,`dict_sort`,`dict_label`,`dict_value`,`dict_type`,`css_class`,`list_class`,`is_default`,`status`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (1,1,CONVERT(0xe794b7 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x7379735f757365725f736578 USING utf8mb4),'','',CONVERT(0x59 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),CONVERT(0xe680a7e588abe794b7 USING utf8mb4));
INSERT INTO `sys_dict_data` (`dict_code`,`dict_sort`,`dict_label`,`dict_value`,`dict_type`,`css_class`,`list_class`,`is_default`,`status`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (2,2,CONVERT(0xe5a5b3 USING utf8mb4),CONVERT(0x31 USING utf8mb4),CONVERT(0x7379735f757365725f736578 USING utf8mb4),'','',CONVERT(0x4e USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),CONVERT(0xe680a7e588abe5a5b3 USING utf8mb4));
INSERT INTO `sys_dict_data` (`dict_code`,`dict_sort`,`dict_label`,`dict_value`,`dict_type`,`css_class`,`list_class`,`is_default`,`status`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (3,3,CONVERT(0xe69caae79fa5 USING utf8mb4),CONVERT(0x32 USING utf8mb4),CONVERT(0x7379735f757365725f736578 USING utf8mb4),'','',CONVERT(0x4e USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),CONVERT(0xe680a7e588abe69caae79fa5 USING utf8mb4));
INSERT INTO `sys_dict_data` (`dict_code`,`dict_sort`,`dict_label`,`dict_value`,`dict_type`,`css_class`,`list_class`,`is_default`,`status`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (4,1,CONVERT(0xe698bee7a4ba USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x7379735f73686f775f68696465 USING utf8mb4),'',CONVERT(0x7072696d617279 USING utf8mb4),CONVERT(0x59 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),CONVERT(0xe698bee7a4bae88f9ce58d95 USING utf8mb4));
INSERT INTO `sys_dict_data` (`dict_code`,`dict_sort`,`dict_label`,`dict_value`,`dict_type`,`css_class`,`list_class`,`is_default`,`status`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (5,2,CONVERT(0xe99a90e8978f USING utf8mb4),CONVERT(0x31 USING utf8mb4),CONVERT(0x7379735f73686f775f68696465 USING utf8mb4),'',CONVERT(0x64616e676572 USING utf8mb4),CONVERT(0x4e USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),CONVERT(0xe99a90e8978fe88f9ce58d95 USING utf8mb4));
INSERT INTO `sys_dict_data` (`dict_code`,`dict_sort`,`dict_label`,`dict_value`,`dict_type`,`css_class`,`list_class`,`is_default`,`status`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (6,1,CONVERT(0xe6ada3e5b8b8 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x7379735f6e6f726d616c5f64697361626c65 USING utf8mb4),'',CONVERT(0x7072696d617279 USING utf8mb4),CONVERT(0x59 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),CONVERT(0xe6ada3e5b8b8e78ab6e68081 USING utf8mb4));
INSERT INTO `sys_dict_data` (`dict_code`,`dict_sort`,`dict_label`,`dict_value`,`dict_type`,`css_class`,`list_class`,`is_default`,`status`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (7,2,CONVERT(0xe5819ce794a8 USING utf8mb4),CONVERT(0x31 USING utf8mb4),CONVERT(0x7379735f6e6f726d616c5f64697361626c65 USING utf8mb4),'',CONVERT(0x64616e676572 USING utf8mb4),CONVERT(0x4e USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),CONVERT(0xe5819ce794a8e78ab6e68081 USING utf8mb4));
INSERT INTO `sys_dict_data` (`dict_code`,`dict_sort`,`dict_label`,`dict_value`,`dict_type`,`css_class`,`list_class`,`is_default`,`status`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (8,1,CONVERT(0xe6ada3e5b8b8 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x7379735f6a6f625f737461747573 USING utf8mb4),'',CONVERT(0x7072696d617279 USING utf8mb4),CONVERT(0x59 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),CONVERT(0xe6ada3e5b8b8e78ab6e68081 USING utf8mb4));
INSERT INTO `sys_dict_data` (`dict_code`,`dict_sort`,`dict_label`,`dict_value`,`dict_type`,`css_class`,`list_class`,`is_default`,`status`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (9,2,CONVERT(0xe69a82e5819c USING utf8mb4),CONVERT(0x31 USING utf8mb4),CONVERT(0x7379735f6a6f625f737461747573 USING utf8mb4),'',CONVERT(0x64616e676572 USING utf8mb4),CONVERT(0x4e USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),CONVERT(0xe5819ce794a8e78ab6e68081 USING utf8mb4));
INSERT INTO `sys_dict_data` (`dict_code`,`dict_sort`,`dict_label`,`dict_value`,`dict_type`,`css_class`,`list_class`,`is_default`,`status`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (10,1,CONVERT(0xe9bb98e8aea4 USING utf8mb4),CONVERT(0x44454641554c54 USING utf8mb4),CONVERT(0x7379735f6a6f625f67726f7570 USING utf8mb4),'','',CONVERT(0x59 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),CONVERT(0xe9bb98e8aea4e58886e7bb84 USING utf8mb4));
INSERT INTO `sys_dict_data` (`dict_code`,`dict_sort`,`dict_label`,`dict_value`,`dict_type`,`css_class`,`list_class`,`is_default`,`status`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (11,2,CONVERT(0xe7b3bbe7bb9f USING utf8mb4),CONVERT(0x53595354454d USING utf8mb4),CONVERT(0x7379735f6a6f625f67726f7570 USING utf8mb4),'','',CONVERT(0x4e USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),CONVERT(0xe7b3bbe7bb9fe58886e7bb84 USING utf8mb4));
INSERT INTO `sys_dict_data` (`dict_code`,`dict_sort`,`dict_label`,`dict_value`,`dict_type`,`css_class`,`list_class`,`is_default`,`status`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (12,1,CONVERT(0xe698af USING utf8mb4),CONVERT(0x59 USING utf8mb4),CONVERT(0x7379735f7965735f6e6f USING utf8mb4),'',CONVERT(0x7072696d617279 USING utf8mb4),CONVERT(0x59 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),CONVERT(0xe7b3bbe7bb9fe9bb98e8aea4e698af USING utf8mb4));
INSERT INTO `sys_dict_data` (`dict_code`,`dict_sort`,`dict_label`,`dict_value`,`dict_type`,`css_class`,`list_class`,`is_default`,`status`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (13,2,CONVERT(0xe590a6 USING utf8mb4),CONVERT(0x4e USING utf8mb4),CONVERT(0x7379735f7965735f6e6f USING utf8mb4),'',CONVERT(0x64616e676572 USING utf8mb4),CONVERT(0x4e USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),CONVERT(0xe7b3bbe7bb9fe9bb98e8aea4e590a6 USING utf8mb4));
INSERT INTO `sys_dict_data` (`dict_code`,`dict_sort`,`dict_label`,`dict_value`,`dict_type`,`css_class`,`list_class`,`is_default`,`status`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (14,1,CONVERT(0xe9809ae79fa5 USING utf8mb4),CONVERT(0x31 USING utf8mb4),CONVERT(0x7379735f6e6f746963655f74797065 USING utf8mb4),'',CONVERT(0x7761726e696e67 USING utf8mb4),CONVERT(0x59 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),CONVERT(0xe9809ae79fa5 USING utf8mb4));
INSERT INTO `sys_dict_data` (`dict_code`,`dict_sort`,`dict_label`,`dict_value`,`dict_type`,`css_class`,`list_class`,`is_default`,`status`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (15,2,CONVERT(0xe585ace5918a USING utf8mb4),CONVERT(0x32 USING utf8mb4),CONVERT(0x7379735f6e6f746963655f74797065 USING utf8mb4),'',CONVERT(0x73756363657373 USING utf8mb4),CONVERT(0x4e USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),CONVERT(0xe585ace5918a USING utf8mb4));
INSERT INTO `sys_dict_data` (`dict_code`,`dict_sort`,`dict_label`,`dict_value`,`dict_type`,`css_class`,`list_class`,`is_default`,`status`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (16,1,CONVERT(0xe6ada3e5b8b8 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x7379735f6e6f746963655f737461747573 USING utf8mb4),'',CONVERT(0x7072696d617279 USING utf8mb4),CONVERT(0x59 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),CONVERT(0xe6ada3e5b8b8e78ab6e68081 USING utf8mb4));
INSERT INTO `sys_dict_data` (`dict_code`,`dict_sort`,`dict_label`,`dict_value`,`dict_type`,`css_class`,`list_class`,`is_default`,`status`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (17,2,CONVERT(0xe585b3e997ad USING utf8mb4),CONVERT(0x31 USING utf8mb4),CONVERT(0x7379735f6e6f746963655f737461747573 USING utf8mb4),'',CONVERT(0x64616e676572 USING utf8mb4),CONVERT(0x4e USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),CONVERT(0xe585b3e997ade78ab6e68081 USING utf8mb4));
INSERT INTO `sys_dict_data` (`dict_code`,`dict_sort`,`dict_label`,`dict_value`,`dict_type`,`css_class`,`list_class`,`is_default`,`status`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (18,99,CONVERT(0xe585b6e4bb96 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x7379735f6f7065725f74797065 USING utf8mb4),'',CONVERT(0x696e666f USING utf8mb4),CONVERT(0x4e USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),CONVERT(0xe585b6e4bb96e6938de4bd9c USING utf8mb4));
INSERT INTO `sys_dict_data` (`dict_code`,`dict_sort`,`dict_label`,`dict_value`,`dict_type`,`css_class`,`list_class`,`is_default`,`status`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (19,1,CONVERT(0xe696b0e5a29e USING utf8mb4),CONVERT(0x31 USING utf8mb4),CONVERT(0x7379735f6f7065725f74797065 USING utf8mb4),'',CONVERT(0x696e666f USING utf8mb4),CONVERT(0x4e USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),CONVERT(0xe696b0e5a29ee6938de4bd9c USING utf8mb4));
INSERT INTO `sys_dict_data` (`dict_code`,`dict_sort`,`dict_label`,`dict_value`,`dict_type`,`css_class`,`list_class`,`is_default`,`status`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (20,2,CONVERT(0xe4bfaee694b9 USING utf8mb4),CONVERT(0x32 USING utf8mb4),CONVERT(0x7379735f6f7065725f74797065 USING utf8mb4),'',CONVERT(0x696e666f USING utf8mb4),CONVERT(0x4e USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),CONVERT(0xe4bfaee694b9e6938de4bd9c USING utf8mb4));
INSERT INTO `sys_dict_data` (`dict_code`,`dict_sort`,`dict_label`,`dict_value`,`dict_type`,`css_class`,`list_class`,`is_default`,`status`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (21,3,CONVERT(0xe588a0e999a4 USING utf8mb4),CONVERT(0x33 USING utf8mb4),CONVERT(0x7379735f6f7065725f74797065 USING utf8mb4),'',CONVERT(0x64616e676572 USING utf8mb4),CONVERT(0x4e USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),CONVERT(0xe588a0e999a4e6938de4bd9c USING utf8mb4));
INSERT INTO `sys_dict_data` (`dict_code`,`dict_sort`,`dict_label`,`dict_value`,`dict_type`,`css_class`,`list_class`,`is_default`,`status`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (22,4,CONVERT(0xe68e88e69d83 USING utf8mb4),CONVERT(0x34 USING utf8mb4),CONVERT(0x7379735f6f7065725f74797065 USING utf8mb4),'',CONVERT(0x7072696d617279 USING utf8mb4),CONVERT(0x4e USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),CONVERT(0xe68e88e69d83e6938de4bd9c USING utf8mb4));
INSERT INTO `sys_dict_data` (`dict_code`,`dict_sort`,`dict_label`,`dict_value`,`dict_type`,`css_class`,`list_class`,`is_default`,`status`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (23,5,CONVERT(0xe5afbce587ba USING utf8mb4),CONVERT(0x35 USING utf8mb4),CONVERT(0x7379735f6f7065725f74797065 USING utf8mb4),'',CONVERT(0x7761726e696e67 USING utf8mb4),CONVERT(0x4e USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),CONVERT(0xe5afbce587bae6938de4bd9c USING utf8mb4));
INSERT INTO `sys_dict_data` (`dict_code`,`dict_sort`,`dict_label`,`dict_value`,`dict_type`,`css_class`,`list_class`,`is_default`,`status`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (24,6,CONVERT(0xe5afbce585a5 USING utf8mb4),CONVERT(0x36 USING utf8mb4),CONVERT(0x7379735f6f7065725f74797065 USING utf8mb4),'',CONVERT(0x7761726e696e67 USING utf8mb4),CONVERT(0x4e USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),CONVERT(0xe5afbce585a5e6938de4bd9c USING utf8mb4));
INSERT INTO `sys_dict_data` (`dict_code`,`dict_sort`,`dict_label`,`dict_value`,`dict_type`,`css_class`,`list_class`,`is_default`,`status`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (25,7,CONVERT(0xe5bcbae98080 USING utf8mb4),CONVERT(0x37 USING utf8mb4),CONVERT(0x7379735f6f7065725f74797065 USING utf8mb4),'',CONVERT(0x64616e676572 USING utf8mb4),CONVERT(0x4e USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),CONVERT(0xe5bcbae98080e6938de4bd9c USING utf8mb4));
INSERT INTO `sys_dict_data` (`dict_code`,`dict_sort`,`dict_label`,`dict_value`,`dict_type`,`css_class`,`list_class`,`is_default`,`status`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (26,8,CONVERT(0xe7949fe68890e4bba3e7a081 USING utf8mb4),CONVERT(0x38 USING utf8mb4),CONVERT(0x7379735f6f7065725f74797065 USING utf8mb4),'',CONVERT(0x7761726e696e67 USING utf8mb4),CONVERT(0x4e USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),CONVERT(0xe7949fe68890e6938de4bd9c USING utf8mb4));
INSERT INTO `sys_dict_data` (`dict_code`,`dict_sort`,`dict_label`,`dict_value`,`dict_type`,`css_class`,`list_class`,`is_default`,`status`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (27,9,CONVERT(0xe6b885e7a9bae695b0e68dae USING utf8mb4),CONVERT(0x39 USING utf8mb4),CONVERT(0x7379735f6f7065725f74797065 USING utf8mb4),'',CONVERT(0x64616e676572 USING utf8mb4),CONVERT(0x4e USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),CONVERT(0xe6b885e7a9bae6938de4bd9c USING utf8mb4));
INSERT INTO `sys_dict_data` (`dict_code`,`dict_sort`,`dict_label`,`dict_value`,`dict_type`,`css_class`,`list_class`,`is_default`,`status`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (28,1,CONVERT(0xe68890e58a9f USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x7379735f636f6d6d6f6e5f737461747573 USING utf8mb4),'',CONVERT(0x7072696d617279 USING utf8mb4),CONVERT(0x4e USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),CONVERT(0xe6ada3e5b8b8e78ab6e68081 USING utf8mb4));
INSERT INTO `sys_dict_data` (`dict_code`,`dict_sort`,`dict_label`,`dict_value`,`dict_type`,`css_class`,`list_class`,`is_default`,`status`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (29,2,CONVERT(0xe5a4b1e8b4a5 USING utf8mb4),CONVERT(0x31 USING utf8mb4),CONVERT(0x7379735f636f6d6d6f6e5f737461747573 USING utf8mb4),'',CONVERT(0x64616e676572 USING utf8mb4),CONVERT(0x4e USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),CONVERT(0xe5819ce794a8e78ab6e68081 USING utf8mb4));
INSERT INTO `sys_dict_data` (`dict_code`,`dict_sort`,`dict_label`,`dict_value`,`dict_type`,`css_class`,`list_class`,`is_default`,`status`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (100,1,CONVERT(0xe585ace5ae89e7bd91 USING utf8mb4),CONVERT(0xe585ace5ae89e7bd91 USING utf8mb4),CONVERT(0x737570706f72745f6e6574776f726b5f656e76 USING utf8mb4),CONVERT(0x6e6574776f726b2d656e762d7461672d2d706f6c696365 USING utf8mb4),CONVERT(0x7072696d617279 USING utf8mb4),CONVERT(0x59 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),CONVERT(0xe58685e7bdaee7bd91e7bb9ce78eafe5a283 USING utf8mb4));
INSERT INTO `sys_dict_data` (`dict_code`,`dict_sort`,`dict_label`,`dict_value`,`dict_type`,`css_class`,`list_class`,`is_default`,`status`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (101,2,CONVERT(0xe59bbee5838fe7bd91 USING utf8mb4),CONVERT(0xe59bbee5838fe7bd91 USING utf8mb4),CONVERT(0x737570706f72745f6e6574776f726b5f656e76 USING utf8mb4),CONVERT(0x6e6574776f726b2d656e762d7461672d2d696d616765 USING utf8mb4),CONVERT(0x73756363657373 USING utf8mb4),CONVERT(0x4e USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),CONVERT(0xe58685e7bdaee7bd91e7bb9ce78eafe5a283 USING utf8mb4));
INSERT INTO `sys_dict_data` (`dict_code`,`dict_sort`,`dict_label`,`dict_value`,`dict_type`,`css_class`,`list_class`,`is_default`,`status`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (102,3,CONVERT(0xe694bfe58aa1e7bd91 USING utf8mb4),CONVERT(0xe694bfe58aa1e7bd91 USING utf8mb4),CONVERT(0x737570706f72745f6e6574776f726b5f656e76 USING utf8mb4),CONVERT(0x6e6574776f726b2d656e762d7461672d2d676f7665726e6d656e74 USING utf8mb4),CONVERT(0x7761726e696e67 USING utf8mb4),CONVERT(0x4e USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),CONVERT(0xe58685e7bdaee7bd91e7bb9ce78eafe5a283 USING utf8mb4));
INSERT INTO `sys_dict_data` (`dict_code`,`dict_sort`,`dict_label`,`dict_value`,`dict_type`,`css_class`,`list_class`,`is_default`,`status`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (103,4,CONVERT(0xe4ba8ce7b1bbe58cba USING utf8mb4),CONVERT(0xe4ba8ce7b1bbe58cba USING utf8mb4),CONVERT(0x737570706f72745f6e6574776f726b5f656e76 USING utf8mb4),CONVERT(0x6e6574776f726b2d656e762d7461672d2d7365636f6e64617279 USING utf8mb4),CONVERT(0x696e666f USING utf8mb4),CONVERT(0x4e USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),CONVERT(0xe58685e7bdaee7bd91e7bb9ce78eafe5a283 USING utf8mb4));
INSERT INTO `sys_dict_data` (`dict_code`,`dict_sort`,`dict_label`,`dict_value`,`dict_type`,`css_class`,`list_class`,`is_default`,`status`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (104,5,CONVERT(0xe5859ae694bfe5869b USING utf8mb4),CONVERT(0xe5859ae694bfe5869b USING utf8mb4),CONVERT(0x737570706f72745f6e6574776f726b5f656e76 USING utf8mb4),CONVERT(0x6e6574776f726b2d656e762d7461672d2d7061727479 USING utf8mb4),CONVERT(0x64616e676572 USING utf8mb4),CONVERT(0x4e USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),CONVERT(0xe58685e7bdaee7bd91e7bb9ce78eafe5a283 USING utf8mb4));
INSERT INTO `sys_dict_data` (`dict_code`,`dict_sort`,`dict_label`,`dict_value`,`dict_type`,`css_class`,`list_class`,`is_default`,`status`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (105,6,CONVERT(0xe7a781e7bd91 USING utf8mb4),CONVERT(0xe7a781e7bd91 USING utf8mb4),CONVERT(0x737570706f72745f6e6574776f726b5f656e76 USING utf8mb4),CONVERT(0x6e6574776f726b2d656e762d7461672d2d70726976617465 USING utf8mb4),CONVERT(0x64656661756c74 USING utf8mb4),CONVERT(0x4e USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),CONVERT(0xe58685e7bdaee7bd91e7bb9ce78eafe5a283 USING utf8mb4));
INSERT INTO `sys_dict_data` (`dict_code`,`dict_sort`,`dict_label`,`dict_value`,`dict_type`,`css_class`,`list_class`,`is_default`,`status`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (106,1,CONVERT(0xe68a80e69caf USING utf8mb4),CONVERT(0x54454348 USING utf8mb4),CONVERT(0x737570706f72745f636f6e746163745f726f6c65 USING utf8mb4),'',CONVERT(0x7072696d617279 USING utf8mb4),CONVERT(0x59 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),CONVERT(0xe58685e7bdaee88194e7b3bbe4babae8a792e889b2 USING utf8mb4));
INSERT INTO `sys_dict_data` (`dict_code`,`dict_sort`,`dict_label`,`dict_value`,`dict_type`,`css_class`,`list_class`,`is_default`,`status`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (107,2,CONVERT(0xe7aea1e79086 USING utf8mb4),CONVERT(0x4d414e41474552 USING utf8mb4),CONVERT(0x737570706f72745f636f6e746163745f726f6c65 USING utf8mb4),'',CONVERT(0x73756363657373 USING utf8mb4),CONVERT(0x4e USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),CONVERT(0xe58685e7bdaee88194e7b3bbe4babae8a792e889b2 USING utf8mb4));
INSERT INTO `sys_dict_data` (`dict_code`,`dict_sort`,`dict_label`,`dict_value`,`dict_type`,`css_class`,`list_class`,`is_default`,`status`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (108,3,CONVERT(0xe59586e58aa1 USING utf8mb4),CONVERT(0x42495a USING utf8mb4),CONVERT(0x737570706f72745f636f6e746163745f726f6c65 USING utf8mb4),'',CONVERT(0x7761726e696e67 USING utf8mb4),CONVERT(0x4e USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),CONVERT(0xe58685e7bdaee88194e7b3bbe4babae8a792e889b2 USING utf8mb4));
INSERT INTO `sys_dict_data` (`dict_code`,`dict_sort`,`dict_label`,`dict_value`,`dict_type`,`css_class`,`list_class`,`is_default`,`status`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (109,1,CONVERT(0xe8a7a3e7a081e599a8 USING utf8mb4),CONVERT(0x4445434f444552 USING utf8mb4),CONVERT(0x737570706f72745f68617264776172655f74797065 USING utf8mb4),CONVERT(0x68617264776172652d747970652d2d6465636f646572 USING utf8mb4),CONVERT(0x7072696d617279 USING utf8mb4),CONVERT(0x59 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),CONVERT(0xe58685e7bdaee7a1ace4bbb6e7b1bbe59e8b USING utf8mb4));
INSERT INTO `sys_dict_data` (`dict_code`,`dict_sort`,`dict_label`,`dict_value`,`dict_type`,`css_class`,`list_class`,`is_default`,`status`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (110,2,CONVERT(0xe7bb88e7abaf USING utf8mb4),CONVERT(0x5445524d494e414c USING utf8mb4),CONVERT(0x737570706f72745f68617264776172655f74797065 USING utf8mb4),CONVERT(0x68617264776172652d747970652d2d7465726d696e616c USING utf8mb4),CONVERT(0x73756363657373 USING utf8mb4),CONVERT(0x4e USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),CONVERT(0xe58685e7bdaee7a1ace4bbb6e7b1bbe59e8b USING utf8mb4));
INSERT INTO `sys_dict_data` (`dict_code`,`dict_sort`,`dict_label`,`dict_value`,`dict_type`,`css_class`,`list_class`,`is_default`,`status`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (111,3,CONVERT(0xe4baa4e68da2e69cba USING utf8mb4),CONVERT(0x535749544348 USING utf8mb4),CONVERT(0x737570706f72745f68617264776172655f74797065 USING utf8mb4),CONVERT(0x68617264776172652d747970652d2d737769746368 USING utf8mb4),CONVERT(0x7761726e696e67 USING utf8mb4),CONVERT(0x4e USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),CONVERT(0xe58685e7bdaee7a1ace4bbb6e7b1bbe59e8b USING utf8mb4));
INSERT INTO `sys_dict_data` (`dict_code`,`dict_sort`,`dict_label`,`dict_value`,`dict_type`,`css_class`,`list_class`,`is_default`,`status`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (112,4,CONVERT(0xe7bd91e997b8 USING utf8mb4),CONVERT(0x47415445574159 USING utf8mb4),CONVERT(0x737570706f72745f68617264776172655f74797065 USING utf8mb4),CONVERT(0x68617264776172652d747970652d2d67617465776179 USING utf8mb4),CONVERT(0x64616e676572 USING utf8mb4),CONVERT(0x4e USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),CONVERT(0xe58685e7bdaee7a1ace4bbb6e7b1bbe59e8b USING utf8mb4));

-- Technical seed: sys_config (8 rows)
INSERT INTO `sys_config` (`config_id`,`config_name`,`config_key`,`config_value`,`config_type`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (1,CONVERT(0xe4b8bbe6a186e69eb6e9a1b52de9bb98e8aea4e79aaee882a4e6a0b7e5bc8fe5908de7a7b0 USING utf8mb4),CONVERT(0x7379732e696e6465782e736b696e4e616d65 USING utf8mb4),CONVERT(0x736b696e2d626c7565 USING utf8mb4),CONVERT(0x59 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),CONVERT(0xe8939de889b220736b696e2d626c7565e38081e7bbbfe889b220736b696e2d677265656ee38081e7b4abe889b220736b696e2d707572706c65e38081e7baa2e889b220736b696e2d726564e38081e9bb84e889b220736b696e2d79656c6c6f77 USING utf8mb4));
INSERT INTO `sys_config` (`config_id`,`config_name`,`config_key`,`config_value`,`config_type`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (2,CONVERT(0xe794a8e688b7e7aea1e790862de8b4a6e58fb7e5889de5a78be5af86e7a081 USING utf8mb4),CONVERT(0x7379732e757365722e696e697450617373776f7264 USING utf8mb4),'',CONVERT(0x59 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),CONVERT(0xe794b1e7aea1e79086e59198e98090e4babae8aebee7bdaee78bace7ab8be5af86e7a081efbc8ce4b88de68f90e4be9be9809ae794a8e9bb98e8aea4e58fa3e4bba4 USING utf8mb4));
INSERT INTO `sys_config` (`config_id`,`config_name`,`config_key`,`config_value`,`config_type`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (3,CONVERT(0xe4b8bbe6a186e69eb6e9a1b52de4bea7e8beb9e6a08fe4b8bbe9a298 USING utf8mb4),CONVERT(0x7379732e696e6465782e736964655468656d65 USING utf8mb4),CONVERT(0x7468656d652d6461726b USING utf8mb4),CONVERT(0x59 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),CONVERT(0xe6b7b1e889b2e4b8bbe9a2987468656d652d6461726befbc8ce6b585e889b2e4b8bbe9a2987468656d652d6c69676874 USING utf8mb4));
INSERT INTO `sys_config` (`config_id`,`config_name`,`config_key`,`config_value`,`config_type`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (4,CONVERT(0xe8b4a6e58fb7e887aae58aa92de9aa8ce8af81e7a081e5bc80e585b3 USING utf8mb4),CONVERT(0x7379732e6163636f756e742e63617074636861456e61626c6564 USING utf8mb4),CONVERT(0x74727565 USING utf8mb4),CONVERT(0x59 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),CONVERT(0xe698afe590a6e5bc80e590afe9aa8ce8af81e7a081e58a9fe883bdefbc8874727565e5bc80e590afefbc8c66616c7365e585b3e997adefbc89 USING utf8mb4));
INSERT INTO `sys_config` (`config_id`,`config_name`,`config_key`,`config_value`,`config_type`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (5,CONVERT(0xe8b4a6e58fb7e887aae58aa92de698afe590a6e5bc80e590afe794a8e688b7e6b3a8e5868ce58a9fe883bd USING utf8mb4),CONVERT(0x7379732e6163636f756e742e726567697374657255736572 USING utf8mb4),CONVERT(0x66616c7365 USING utf8mb4),CONVERT(0x59 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),CONVERT(0xe698afe590a6e5bc80e590afe6b3a8e5868ce794a8e688b7e58a9fe883bdefbc8874727565e5bc80e590afefbc8c66616c7365e585b3e997adefbc89 USING utf8mb4));
INSERT INTO `sys_config` (`config_id`,`config_name`,`config_key`,`config_value`,`config_type`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (6,CONVERT(0xe794a8e688b7e799bbe5bd952de9bb91e5908de58d95e58897e8a1a8 USING utf8mb4),CONVERT(0x7379732e6c6f67696e2e626c61636b49504c697374 USING utf8mb4),'',CONVERT(0x59 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),CONVERT(0xe8aebee7bdaee799bbe5bd954950e9bb91e5908de58d95e99990e588b6efbc8ce5a49ae4b8aae58cb9e9858de9a1b9e4bba53be58886e99a94efbc8ce694afe68c81e58cb9e9858defbc882ae9809ae9858de38081e7bd91e6aeb5efbc89 USING utf8mb4));
INSERT INTO `sys_config` (`config_id`,`config_name`,`config_key`,`config_value`,`config_type`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (7,CONVERT(0xe794a8e688b7e7aea1e790862de5889de5a78be5af86e7a081e4bfaee694b9e7ad96e795a5 USING utf8mb4),CONVERT(0x7379732e6163636f756e742e696e697450617373776f72644d6f64696679 USING utf8mb4),CONVERT(0x31 USING utf8mb4),CONVERT(0x59 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),CONVERT(0x30efbc9ae5889de5a78be5af86e7a081e4bfaee694b9e7ad96e795a5e585b3e997adefbc8ce6b2a1e69c89e4bbbbe4bd95e68f90e7a4baefbc8c31efbc9ae68f90e98692e794a8e688b7efbc8ce5a682e69e9ce69caae4bfaee694b9e5889de5a78be5af86e7a081efbc8ce58899e59ca8e799bbe5bd95e697b6e5b0b1e4bc9ae68f90e98692e4bfaee694b9e5af86e7a081e5afb9e8af9de6a186 USING utf8mb4));
INSERT INTO `sys_config` (`config_id`,`config_name`,`config_key`,`config_value`,`config_type`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (8,CONVERT(0xe794a8e688b7e7aea1e790862de8b4a6e58fb7e5af86e7a081e69bb4e696b0e591a8e69c9f USING utf8mb4),CONVERT(0x7379732e6163636f756e742e70617373776f726456616c696461746544617973 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x59 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),CONVERT(0xe5af86e7a081e69bb4e696b0e591a8e69c9fefbc88e5a1abe58699e695b0e5ad97efbc8ce695b0e68daee5889de5a78be58c96e580bce4b8ba30e4b88de99990e588b6efbc8ce88ba5e4bfaee694b9e5bf85e9a1bbe4b8bae5a4a7e4ba8e30e5b08fe4ba8e333635e79a84e6ada3e695b4e695b0efbc89efbc8ce5a682e69e9ce8b685e8bf87e8bf99e4b8aae591a8e69c9fe799bbe5bd95e7b3bbe7bb9fe697b6efbc8ce58899e59ca8e799bbe5bd95e697b6e5b0b1e4bc9ae68f90e98692e4bfaee694b9e5af86e7a081e5afb9e8af9de6a186 USING utf8mb4));

-- Technical seed: sys_user (1 rows)
INSERT INTO `sys_user` (`user_id`,`dept_id`,`user_name`,`nick_name`,`user_type`,`email`,`phonenumber`,`sex`,`avatar`,`password`,`status`,`del_flag`,`login_ip`,`login_date`,`pwd_update_date`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (1,100,CONVERT(0x61646d696e USING utf8mb4),CONVERT(0xe5b9b3e58fb0e7aea1e79086e59198 USING utf8mb4),CONVERT(0x3030 USING utf8mb4),'','',CONVERT(0x32 USING utf8mb4),'',@rynew_admin_password_hash,CONVERT(0x30 USING utf8mb4),CONVERT(0x30 USING utf8mb4),'',NULL,NULL,CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),NULL);

-- Technical seed: sys_user_role (1 rows)
INSERT INTO `sys_user_role` (`user_id`,`role_id`) VALUES (1,1);

-- Technical seed: sys_user_post (1 rows)
INSERT INTO `sys_user_post` (`user_id`,`post_id`) VALUES (1,1);

-- Technical seed: sys_role_menu (260 rows)
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (1,1);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (1,2);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (1,3);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (1,100);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (1,101);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (1,102);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (1,103);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (1,104);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (1,105);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (1,106);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (1,107);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (1,108);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (1,109);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (1,110);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (1,111);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (1,112);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (1,113);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (1,114);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (1,115);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (1,116);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (1,117);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (1,500);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (1,501);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (1,1000);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (1,1001);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (1,1002);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (1,1003);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (1,1004);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (1,1005);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (1,1006);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (1,1007);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (1,1008);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (1,1009);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (1,1010);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (1,1011);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (1,1012);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (1,1013);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (1,1014);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (1,1015);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (1,1016);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (1,1017);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (1,1018);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (1,1019);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (1,1020);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (1,1021);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (1,1022);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (1,1023);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (1,1024);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (1,1025);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (1,1026);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (1,1027);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (1,1028);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (1,1029);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (1,1030);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (1,1031);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (1,1032);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (1,1033);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (1,1034);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (1,1035);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (1,1036);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (1,1037);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (1,1038);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (1,1039);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (1,1040);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (1,1041);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (1,1042);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (1,1043);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (1,1044);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (1,1045);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (1,1046);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (1,1047);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (1,1048);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (1,1049);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (1,1050);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (1,1051);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (1,1052);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (1,1053);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (1,1054);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (1,1055);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (1,1056);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (1,1057);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (1,1058);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (1,1059);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (1,1060);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (1,2200);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (1,2201);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (1,2202);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (1,2203);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (1,2204);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (1,2205);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (1,2206);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (1,2207);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (1,2211);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (1,2212);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (1,2213);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (1,2214);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (1,2215);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (1,2216);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (1,2221);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (1,2222);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (1,2223);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (1,2224);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (1,2225);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (1,2231);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (1,2232);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (1,2233);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (1,2234);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (1,2235);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (1,2241);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (1,2242);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (1,2243);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (1,2244);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (1,2245);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (1,2261);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (1,2262);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (1,2263);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (1,2264);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (1,2265);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (1,2288);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (1,2289);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (1,2290);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (1,2291);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (1,2292);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (1,2293);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (1,2294);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (1,2295);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (1,2296);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (1,2297);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (1,2298);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (1,2300);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (1,2301);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (1,2302);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (1,2303);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (1,2304);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (1,2305);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (1,2306);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (1,2307);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (1,2311);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (1,2312);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (1,2313);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (1,2314);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (1,2315);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (1,2316);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (1,2321);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (1,2322);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (1,2323);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (1,2324);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (1,2325);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (1,2400);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (1,2401);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (1,2402);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (1,2403);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (1,2404);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (1,2405);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (1,2406);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (1,2407);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (1,2408);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (1,2409);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (1,2410);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (1,2411);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (1,2412);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (1,2414);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (1,2415);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (1,2416);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (1,2500);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (1,2501);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (1,2502);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (1,2503);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (1,2504);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (1,2505);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (1,2506);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (1,2507);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (1,2510);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (1,2511);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (1,2512);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (1,2513);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (1,3000);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (1,3001);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (1,3002);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (1,3003);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (1,3004);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (1,3005);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (1,3006);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (1,3007);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (1,3008);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (1,3009);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (1,3010);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (1,3011);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (1,3012);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (1,3013);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (1,3014);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (1,3015);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (1,3016);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (1,3017);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (1,3018);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (100,2500);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (100,2501);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (100,2502);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (100,2503);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (100,2504);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (100,2505);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (100,2506);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (100,2507);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (101,2200);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (101,2201);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (101,2202);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (101,2203);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (101,2204);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (101,2207);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (101,2211);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (101,2212);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (101,2213);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (101,2214);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (101,2215);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (101,2216);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (101,2221);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (101,2222);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (101,2223);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (101,2224);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (101,2225);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (101,2231);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (101,2232);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (101,2233);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (101,2234);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (101,2235);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (101,2241);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (101,2242);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (101,2243);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (101,2244);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (101,2245);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (101,2288);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (101,2289);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (101,2290);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (101,2291);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (101,2292);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (101,2293);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (101,2294);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (101,2295);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (101,2296);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (101,2297);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (101,2298);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (101,2300);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (101,2301);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (101,2302);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (101,2303);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (101,2304);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (101,2305);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (101,2306);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (101,2307);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (101,2311);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (101,2312);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (101,2313);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (101,2314);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (101,2315);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (101,2316);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (101,2321);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (101,2322);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (101,2323);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (101,2324);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (101,2325);

-- Technical seed: sys_job (1 rows)
INSERT INTO `sys_job` (`job_id`,`job_name`,`job_group`,`invoke_target`,`cron_expression`,`misfire_policy`,`concurrent`,`status`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (1,CONVERT(0xe69687e6a1a3e58d8fe4bd9ce69d83e99990e588b0e69c9fe5a484e79086 USING utf8mb4),CONVERT(0x53595354454d USING utf8mb4),CONVERT(0x646f63756d656e745065726d697373696f6e4578706972795461736b2e657870697265 USING utf8mb4),CONVERT(0x302f3330202a202a202a202a203f USING utf8mb4),CONVERT(0x33 USING utf8mb4),CONVERT(0x31 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),CONVERT(0xe6af8f3330e7a792e7a7bbe999a4e5b7b2e588b0e69c9f41434cefbc9be7bc96e8be91e69d83e99990e588b0e69c9fe697b6e5908ce6ada5e696ade5bc804f4e4c594f4646494345e7bc96e8be91e4bc9ae8af9d USING utf8mb4));

-- Technical seed: sup_auto_inspection_tool (14 rows)
INSERT INTO `sup_auto_inspection_tool` (`tool_code`,`tool_name`,`tool_type`,`value_unit`,`default_compare_rule`,`default_threshold_value`,`default_timeout_seconds`,`default_time_window_minutes`,`param_schema`,`built_in_flag`,`status`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (CONVERT(0x4249475f444154415f5345525645525f4449534b USING utf8mb4),CONVERT(0xe5a4a7e695b0e68daee69c8de58aa1e599a8e78886e79b98e6a380e6b58b USING utf8mb4),CONVERT(0x4249475f444154415f5345525645525f4449534b USING utf8mb4),CONVERT(0x25 USING utf8mb4),CONVERT(0x4d4158 USING utf8mb4),85,15,0,CONVERT(0x7b226669656c6473223a5b2273657276657254617267657473222c22696e636c75646550736575646f225d7d USING utf8mb4),CONVERT(0x59 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),CONVERT(0xe887aae58aa8e58c96e5b7a1e6a380e58685e7bdaee5b7a5e585b7 USING utf8mb4));
INSERT INTO `sup_auto_inspection_tool` (`tool_code`,`tool_name`,`tool_type`,`value_unit`,`default_compare_rule`,`default_threshold_value`,`default_timeout_seconds`,`default_time_window_minutes`,`param_schema`,`built_in_flag`,`status`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (CONVERT(0x44415441424153455f5155455259 USING utf8mb4),CONVERT(0xe695b0e68daee5ba93e69fa5e8afa2e6a380e69fa5 USING utf8mb4),CONVERT(0x44415441424153455f5155455259 USING utf8mb4),CONVERT(0xe69da1 USING utf8mb4),CONVERT(0x4d494e USING utf8mb4),1,15,0,CONVERT(0x7b226669656c6473223a5b22646174616261736554797065222c22686f7374222c22706f7274222c2264617461626173654e616d65222c227175657279222c22726573756c744d6f6465222c22726573756c74436f6c756d6e225d7d USING utf8mb4),CONVERT(0x59 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),CONVERT(0xe887aae58aa8e58c96e5b7a1e6a380e58685e7bdaee58faae8afbbe695b0e68daee5ba93e58f96e695b0e5b7a5e585b7 USING utf8mb4));
INSERT INTO `sup_auto_inspection_tool` (`tool_code`,`tool_name`,`tool_type`,`value_unit`,`default_compare_rule`,`default_threshold_value`,`default_timeout_seconds`,`default_time_window_minutes`,`param_schema`,`built_in_flag`,`status`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (CONVERT(0x4654505f46494c455f434f554e54 USING utf8mb4),CONVERT(0x465450e79baee5bd95e69687e4bbb6e695b0e9878fe6a380e6b58b USING utf8mb4),CONVERT(0x4654505f46494c455f434f554e54 USING utf8mb4),CONVERT(0xe4b8aa USING utf8mb4),CONVERT(0x4d4158 USING utf8mb4),50,10,0,CONVERT(0x7b226669656c6473223a5b2270617468225d7d USING utf8mb4),CONVERT(0x59 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),CONVERT(0xe887aae58aa8e58c96e5b7a1e6a380e58685e7bdaee5b7a5e585b7 USING utf8mb4));
INSERT INTO `sup_auto_inspection_tool` (`tool_code`,`tool_name`,`tool_type`,`value_unit`,`default_compare_rule`,`default_threshold_value`,`default_timeout_seconds`,`default_time_window_minutes`,`param_schema`,`built_in_flag`,`status`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (CONVERT(0x485454505f4150495f54455354 USING utf8mb4),CONVERT(0xe68ea5e58fa3e8b083e794a8e6b58be8af95 USING utf8mb4),CONVERT(0x485454505f4150495f54455354 USING utf8mb4),CONVERT(0x6d73 USING utf8mb4),CONVERT(0x4d4158 USING utf8mb4),3000,10,0,CONVERT(0x7b226669656c6473223a5b2275726c222c22687474704d6574686f64222c227175657279506172616d73222c2268656164657273222c22636f6f6b696573222c2261757468222c22626f647954797065222c22626f6479222c22666f726d506172616d73222c22617373657274696f6e73222c227472757374496e7465726e616c4365727469666963617465225d7d USING utf8mb4),CONVERT(0x59 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),CONVERT(0xe887aae58aa8e58c96e5b7a1e6a380e58685e7bdaee5b7a5e585b7 USING utf8mb4));
INSERT INTO `sup_auto_inspection_tool` (`tool_code`,`tool_name`,`tool_type`,`value_unit`,`default_compare_rule`,`default_threshold_value`,`default_timeout_seconds`,`default_time_window_minutes`,`param_schema`,`built_in_flag`,`status`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (CONVERT(0x485454505f434f554e54 USING utf8mb4),CONVERT(0xe6b5b7e5bab7e68ea5e58fa3e695b0e9878fe6a380e6b58b USING utf8mb4),CONVERT(0x485454505f434f554e54 USING utf8mb4),CONVERT(0xe69da1 USING utf8mb4),CONVERT(0x4d494e USING utf8mb4),0,10,480,CONVERT(0x7b226669656c6473223a5b22726573756c7450617468222c226578747261506172616d73222c2274696d6557696e646f774d696e75746573225d7d USING utf8mb4),CONVERT(0x59 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),CONVERT(0xe887aae58aa8e58c96e5b7a1e6a380e58685e7bdaee5b7a5e585b7 USING utf8mb4));
INSERT INTO `sup_auto_inspection_tool` (`tool_code`,`tool_name`,`tool_type`,`value_unit`,`default_compare_rule`,`default_threshold_value`,`default_timeout_seconds`,`default_time_window_minutes`,`param_schema`,`built_in_flag`,`status`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (CONVERT(0x485454505f4845414c5448 USING utf8mb4),CONVERT(0x48545450e68ea5e58fa3e581a5e5bab7e6a380e6b58b USING utf8mb4),CONVERT(0x485454505f4845414c5448 USING utf8mb4),CONVERT(0x6d73 USING utf8mb4),CONVERT(0x4d4158 USING utf8mb4),3000,10,0,CONVERT(0x7b226669656c6473223a5b2275726c222c22687474704d6574686f64222c226578706563746564537461747573222c2274696d656f75745365636f6e6473225d7d USING utf8mb4),CONVERT(0x59 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),CONVERT(0xe887aae58aa8e58c96e5b7a1e6a380e58685e7bdaee5b7a5e585b7 USING utf8mb4));
INSERT INTO `sup_auto_inspection_tool` (`tool_code`,`tool_name`,`tool_type`,`value_unit`,`default_compare_rule`,`default_threshold_value`,`default_timeout_seconds`,`default_time_window_minutes`,`param_schema`,`built_in_flag`,`status`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (CONVERT(0x4b41464b415f434f4e53554d45525f50524f4752455353 USING utf8mb4),CONVERT(0x4b61666b61e6b688e8b4b9e5819ce6bb9ee6a380e6b58b USING utf8mb4),CONVERT(0x4b41464b415f434f4e53554d45525f50524f4752455353 USING utf8mb4),CONVERT(0xe69da1 USING utf8mb4),CONVERT(0x4d494e USING utf8mb4),1,10,0,CONVERT(0x7b226669656c6473223a5b22746f706963222c22636f6e73756d657247726f7570222c226576616c756174696f6e436f6e666967225d7d USING utf8mb4),CONVERT(0x59 USING utf8mb4),CONVERT(0x31 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),CONVERT(0xe58e86e58fb2e6a8a1e69dbfe585bce5aeb9e5b7a5e585b7efbc9be696b0e5bbbae6ada5e9aaa4e8afb7e4bdbfe794a84b41464b415f4c4147e5b9b6e98089e68ba9e7949fe4baa7e680bb4f6666736574e68896e6b688e8b4b9e680bb4f6666736574 USING utf8mb4));
INSERT INTO `sup_auto_inspection_tool` (`tool_code`,`tool_name`,`tool_type`,`value_unit`,`default_compare_rule`,`default_threshold_value`,`default_timeout_seconds`,`default_time_window_minutes`,`param_schema`,`built_in_flag`,`status`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (CONVERT(0x4b41464b415f4c4147 USING utf8mb4),CONVERT(0x4b61666b61e6b688e8b4b9e7bb84e68c87e6a087e6a380e6b58b USING utf8mb4),CONVERT(0x4b41464b415f4c4147 USING utf8mb4),CONVERT(0xe69da1 USING utf8mb4),CONVERT(0x4d4158 USING utf8mb4),2000,10,0,CONVERT(0x7b226669656c6473223a5b22746f706963222c22636f6e73756d657247726f7570222c226b61666b614d6574726963222c226576616c756174696f6e436f6e666967225d7d USING utf8mb4),CONVERT(0x59 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),CONVERT(0xe4b880e6aca1e98787e99b86e69c80e5a4a7e7a7afe58e8be38081e680bbe7a7afe58e8be38081e7949fe4baa74f6666736574e5928ce6b688e8b4b94f6666736574efbc9be694afe68c81e59bbae5ae9ae99888e580bce68896e4b88ee4b88ae6aca1e7bb93e69e9ce6af94e8be83 USING utf8mb4));
INSERT INTO `sup_auto_inspection_tool` (`tool_code`,`tool_name`,`tool_type`,`value_unit`,`default_compare_rule`,`default_threshold_value`,`default_timeout_seconds`,`default_time_window_minutes`,`param_schema`,`built_in_flag`,`status`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (CONVERT(0x4b41464b415f544f5049435f4143544956495459 USING utf8mb4),CONVERT(0x4b61666b61e4b8bbe9a298e58699e585a5e4b8ade696ade6a380e6b58b USING utf8mb4),CONVERT(0x4b41464b415f544f5049435f4143544956495459 USING utf8mb4),CONVERT(0xe69da1 USING utf8mb4),CONVERT(0x4d494e USING utf8mb4),1,10,0,CONVERT(0x7b226669656c6473223a5b22746f706963222c226576616c756174696f6e436f6e666967225d7d USING utf8mb4),CONVERT(0x59 USING utf8mb4),CONVERT(0x31 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),CONVERT(0xe58e86e58fb2e6a8a1e69dbfe585bce5aeb9e5b7a5e585b7efbc9be696b0e5bbbae6ada5e9aaa4e8afb7e4bdbfe794a84b41464b415f4c4147e5b9b6e98089e68ba9e7949fe4baa7e680bb4f6666736574e68896e6b688e8b4b9e680bb4f6666736574 USING utf8mb4));
INSERT INTO `sup_auto_inspection_tool` (`tool_code`,`tool_name`,`tool_type`,`value_unit`,`default_compare_rule`,`default_threshold_value`,`default_timeout_seconds`,`default_time_window_minutes`,`param_schema`,`built_in_flag`,`status`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (CONVERT(0x4d5154545f544f5049435f4143544956495459 USING utf8mb4),CONVERT(0x4d515454e4b8bbe9a298e6b4bbe8b783e5baa6e6a380e6b58b USING utf8mb4),CONVERT(0x4d5154545f544f5049435f4143544956495459 USING utf8mb4),CONVERT(0xe69da1 USING utf8mb4),CONVERT(0x4d494e USING utf8mb4),1,10,0,CONVERT(0x7b226669656c6473223a5b2262726f6b6572222c22746f70696346696c746572222c22716f73222c2269676e6f726552657461696e6564222c226576616c756174696f6e436f6e666967225d7d USING utf8mb4),CONVERT(0x59 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),CONVERT(0xe5908ee58fb0e68c81e7bbade8aea2e998854d515454e4b8bbe9a298e5b9b6e694afe68c81e59bbae5ae9ae99888e580bce68896e4b88ae6aca1e7bb93e69e9ce6af94e8be83 USING utf8mb4));
INSERT INTO `sup_auto_inspection_tool` (`tool_code`,`tool_name`,`tool_type`,`value_unit`,`default_compare_rule`,`default_threshold_value`,`default_timeout_seconds`,`default_time_window_minutes`,`param_schema`,`built_in_flag`,`status`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (CONVERT(0x5345525645525f4449534b USING utf8mb4),CONVERT(0xe69c8de58aa1e599a8e7a381e79b98e4bdbfe794a8e78e87e6a380e6b58b USING utf8mb4),CONVERT(0x5345525645525f4449534b USING utf8mb4),CONVERT(0x25 USING utf8mb4),CONVERT(0x4d4158 USING utf8mb4),80,10,0,CONVERT(0x7b226669656c6473223a5b2270617468225d7d USING utf8mb4),CONVERT(0x59 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),CONVERT(0xe887aae58aa8e58c96e5b7a1e6a380e58685e7bdaee5b7a5e585b7 USING utf8mb4));
INSERT INTO `sup_auto_inspection_tool` (`tool_code`,`tool_name`,`tool_type`,`value_unit`,`default_compare_rule`,`default_threshold_value`,`default_timeout_seconds`,`default_time_window_minutes`,`param_schema`,`built_in_flag`,`status`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (CONVERT(0x5345525645525f46494c455f434f554e54 USING utf8mb4),CONVERT(0xe69c8de58aa1e599a8e79baee5bd95e69687e4bbb6e695b0e9878fe6a380e6b58b USING utf8mb4),CONVERT(0x5345525645525f46494c455f434f554e54 USING utf8mb4),CONVERT(0xe4b8aa USING utf8mb4),CONVERT(0x4d4158 USING utf8mb4),20,10,0,CONVERT(0x7b226669656c6473223a5b2273657276657254617267657473222c22726563757273697665222c2266696c655061747465726e225d7d USING utf8mb4),CONVERT(0x59 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),CONVERT(0xe887aae58aa8e58c96e5b7a1e6a380e58685e7bdaee5b7a5e585b7 USING utf8mb4));
INSERT INTO `sup_auto_inspection_tool` (`tool_code`,`tool_name`,`tool_type`,`value_unit`,`default_compare_rule`,`default_threshold_value`,`default_timeout_seconds`,`default_time_window_minutes`,`param_schema`,`built_in_flag`,`status`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (CONVERT(0x5345525645525f534552564943455f535441545553 USING utf8mb4),CONVERT(0xe69c8de58aa1e599a8e69c8de58aa1e78ab6e68081e6a380e6b58b USING utf8mb4),CONVERT(0x5345525645525f534552564943455f535441545553 USING utf8mb4),CONVERT(0xe78ab6e68081 USING utf8mb4),CONVERT(0x4d494e USING utf8mb4),1,15,0,CONVERT(0x7b226669656c6473223a5b2273657276657254617267657473222c22736572766963654e616d65222c2270726976696c6567654d6f6465222c226175746f52657374617274222c2272657374617274576169745365636f6e6473225d7d USING utf8mb4),CONVERT(0x59 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),CONVERT(0xe887aae58aa8e58c96e5b7a1e6a380e58685e7bdaee5b7a5e585b7 USING utf8mb4));
INSERT INTO `sup_auto_inspection_tool` (`tool_code`,`tool_name`,`tool_type`,`value_unit`,`default_compare_rule`,`default_threshold_value`,`default_timeout_seconds`,`default_time_window_minutes`,`param_schema`,`built_in_flag`,`status`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (CONVERT(0x5443505f504f52545f434845434b USING utf8mb4),CONVERT(0x544350e7abafe58fa3e8bf9ee9809ae680a7e6a380e6b58b USING utf8mb4),CONVERT(0x5443505f504f52545f434845434b USING utf8mb4),CONVERT(0x6d73 USING utf8mb4),CONVERT(0x4d4158 USING utf8mb4),1000,5,0,CONVERT(0x7b226669656c6473223a5b22686f7374222c22706f7274222c2274696d656f75745365636f6e6473225d7d USING utf8mb4),CONVERT(0x59 USING utf8mb4),CONVERT(0x30 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),'',CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),CONVERT(0xe887aae58aa8e58c96e5b7a1e6a380e58685e7bdaee5b7a5e585b7 USING utf8mb4));

-- Technical seed: ipam_setting (2 rows)
INSERT INTO `ipam_setting` (`setting_key`,`setting_value`,`setting_name`,`create_by`,`create_time`,`update_by`,`update_time`) VALUES (CONVERT(0x4e4554574f524b5f52414e47455f4c4f434b USING utf8mb4),CONVERT(0x31 USING utf8mb4),CONVERT(0xe7bd91e6aeb5e88c83e59bb4e5b9b6e58f91e99481 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4));
INSERT INTO `ipam_setting` (`setting_key`,`setting_value`,`setting_name`,`create_by`,`create_time`,`update_by`,`update_time`) VALUES (CONVERT(0x5343454e4152494f5f54595045 USING utf8mb4),CONVERT(0x534f4349414c USING utf8mb4),CONVERT(0x4950414de4bdbfe794a8e59cbae699af USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4));

-- Technical seed: doc_user_quota (1 rows)
INSERT INTO `doc_user_quota` (`user_id`,`quota_bytes`,`max_upload_bytes`,`create_by`,`create_time`,`update_by`,`update_time`) VALUES (1,104857600,104857600,CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4));

-- Technical seed: kb_space (1 rows)
INSERT INTO `kb_space` (`space_id`,`space_name`,`description`,`sort_order`,`status`,`create_by`,`create_time`,`update_by`,`update_time`) VALUES (1,CONVERT(0xe9bb98e8aea4e79fa5e8af86e7a9bae997b4 USING utf8mb4),CONVERT(0xe6b289e6b780e5b9b3e58fb0e8bf90e7bbb4e38081e5b7a1e6a380e5928ce69585e99a9ce5a484e79086e79fa5e8af86 USING utf8mb4),10,CONVERT(0x30 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4),CONVERT(0x61646d696e USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4));

-- Technical seed: dg_hdi_workspace (1 rows)
INSERT INTO `dg_hdi_workspace` (`workspace_id`,`workspace_key`,`workspace_name`,`created_at`) VALUES (1,CONVERT(0x706c6174666f726d USING utf8mb4),CONVERT(0xe695b0e68daee6b581e7a88b USING utf8mb4),CONVERT(0x323032362d31312d30362030303a30303a3030 USING utf8mb4));

-- Technical seed: dg_hdi_counter (1 rows)
INSERT INTO `dg_hdi_counter` (`counter_name`,`next_value`) VALUES (CONVERT(0x6e61746976655f72756e74696d65 USING utf8mb4),100000);

COMMIT;
SET @rynew_admin_password_hash = NULL;
DROP TEMPORARY TABLE rynew_init_guard;
SELECT 'INIT_OK' AS installation_status, (SELECT COUNT(*) FROM sys_user) AS initial_users, (SELECT COUNT(*) FROM sys_menu) AS menu_count, (SELECT COUNT(*) FROM sup_auto_inspection_tool) AS built_in_tools;
SELECT RELEASE_LOCK(@rynew_init_lock) AS installation_lock_released;
