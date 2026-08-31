-- RYNEW v3.16.2 至 v4.0.1 累计数据库升级脚本
-- 生成日期：2026-08-31
-- 适用基线：已完成 v3.16.1 及更早版本数据库部署的环境
--
-- 实际数据库变更：
--   1. v3.16.2 自动化巡检结构化判定字段兼容与有证据的历史基线健康度修复
--   2. v3.17.0 机房/机柜三维布局字段与设备物理上联关系表
--   3. v4.0.0 设备统一管控按钮权限及 datafusion 角色授权
--
-- 无数据库变更版本：v3.16.3-v3.16.8、v3.18.0、v4.0.1
-- 数据安全：不删除业务表和既有现场、设备、机房、机柜数据；所有DDL和权限写入均可重复执行。
-- 历史修复仅命中自动高频巡检中明确包含首次/重建基线证据且仍为未执行状态的记录。
-- MySQL DDL 会自动提交，生产执行前仍应完成数据库备份并在维护窗口执行。

SET NAMES utf8mb4;

-- ============================================================================
-- v3.16.2 自动化巡检高频历史基线修复
-- 来源：support_upgrade_20260829_auto_inspection_daily_history_fix_v3_16_2.sql
-- ============================================================================

-- 兼容尚未单独执行 v3.16.0 的环境，补齐新代码读取的结构化判定字段。
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sup_auto_inspection_target_result' AND COLUMN_NAME = 'evaluation_mode') = 0,
  'ALTER TABLE sup_auto_inspection_target_result ADD COLUMN evaluation_mode VARCHAR(16) DEFAULT ''FIXED'' COMMENT ''判定方式（FIXED固定阈值 PREVIOUS上次结果）'' AFTER actual_unit', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sup_auto_inspection_target_result' AND COLUMN_NAME = 'previous_value') = 0,
  'ALTER TABLE sup_auto_inspection_target_result ADD COLUMN previous_value DECIMAL(30,2) DEFAULT NULL COMMENT ''上次采样值'' AFTER evaluation_mode', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sup_auto_inspection_target_result' AND COLUMN_NAME = 'change_value') = 0,
  'ALTER TABLE sup_auto_inspection_target_result ADD COLUMN change_value DECIMAL(30,2) DEFAULT NULL COMMENT ''本次与上次变化量'' AFTER previous_value', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sup_auto_inspection_target_result' AND COLUMN_NAME = 'evaluation_rule') = 0,
  'ALTER TABLE sup_auto_inspection_target_result ADD COLUMN evaluation_rule VARCHAR(500) DEFAULT NULL COMMENT ''本次判定公式'' AFTER change_value', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sup_auto_inspection_target_result' AND COLUMN_NAME = 'baseline_flag') = 0,
  'ALTER TABLE sup_auto_inspection_target_result ADD COLUMN baseline_flag CHAR(1) DEFAULT ''N'' COMMENT ''是否本次建立基线（Y是 N否）'' AFTER evaluation_rule', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

DROP TEMPORARY TABLE IF EXISTS tmp_v3162_baseline_targets;
CREATE TEMPORARY TABLE tmp_v3162_baseline_targets (
  result_id BIGINT NOT NULL PRIMARY KEY,
  step_result_id BIGINT NOT NULL,
  record_id BIGINT NOT NULL,
  target_id BIGINT DEFAULT NULL,
  plan_id BIGINT DEFAULT NULL,
  health_date DATE NOT NULL
) ENGINE=MEMORY;

INSERT INTO tmp_v3162_baseline_targets(result_id, step_result_id, record_id, target_id, plan_id, health_date)
SELECT tr.result_id, tr.step_result_id, tr.record_id, tr.target_id, r.plan_id, DATE(r.inspection_time)
FROM sup_auto_inspection_target_result tr
INNER JOIN sup_auto_inspection_record r ON r.record_id = tr.record_id
WHERE r.run_mode = 'FREQUENT'
  AND r.source_type = 'AUTO'
  AND tr.result_status = '3'
  AND (
    tr.result_detail LIKE '%已建立首次采样基线%'
    OR tr.result_detail LIKE '%已重新建立基线%'
  );

UPDATE sup_auto_inspection_target_result tr
INNER JOIN tmp_v3162_baseline_targets t ON t.result_id = tr.result_id
SET tr.result_status = '1',
    tr.evaluation_mode = 'PREVIOUS',
    tr.baseline_flag = 'Y',
    tr.evaluation_rule = CASE
      WHEN tr.evaluation_rule IS NULL OR tr.evaluation_rule = ''
        THEN '历史首次采样建立基线，本次按正常计入健康度'
      ELSE tr.evaluation_rule
    END,
    tr.update_by = 'system',
    tr.update_time = NOW();

DROP TEMPORARY TABLE IF EXISTS tmp_v3162_baseline_steps;
CREATE TEMPORARY TABLE tmp_v3162_baseline_steps (
  step_result_id BIGINT NOT NULL PRIMARY KEY
) ENGINE=MEMORY;
INSERT INTO tmp_v3162_baseline_steps(step_result_id)
SELECT DISTINCT step_result_id FROM tmp_v3162_baseline_targets;

UPDATE sup_auto_inspection_step_result sr
INNER JOIN tmp_v3162_baseline_steps t ON t.step_result_id = sr.step_result_id
SET sr.result_status = '1',
    sr.result_summary = CASE
      WHEN sr.result_summary LIKE '%正在建立活性基线%'
        THEN REPLACE(sr.result_summary, '正在建立活性基线', '已建立对照基线并按正常计入')
      ELSE sr.result_summary
    END,
    sr.update_by = 'system',
    sr.update_time = NOW()
WHERE sr.result_status = '3'
  AND EXISTS (
    SELECT 1 FROM sup_auto_inspection_target_result tr
    WHERE tr.step_result_id = sr.step_result_id
  )
  AND NOT EXISTS (
    SELECT 1 FROM sup_auto_inspection_target_result tr
    WHERE tr.step_result_id = sr.step_result_id AND tr.result_status <> '1'
  );

DROP TEMPORARY TABLE IF EXISTS tmp_v3162_baseline_records;
CREATE TEMPORARY TABLE tmp_v3162_baseline_records (
  record_id BIGINT NOT NULL PRIMARY KEY,
  plan_id BIGINT DEFAULT NULL,
  health_date DATE NOT NULL
) ENGINE=MEMORY;
INSERT INTO tmp_v3162_baseline_records(record_id, plan_id, health_date)
SELECT DISTINCT record_id, plan_id, health_date FROM tmp_v3162_baseline_targets;

UPDATE sup_auto_inspection_record r
INNER JOIN tmp_v3162_baseline_records t ON t.record_id = r.record_id
SET r.result_status = '1',
    r.warning_count = 0,
    r.abnormal_count = 0,
    r.abnormal_summary = '无异常',
    r.update_by = 'system',
    r.update_time = NOW()
WHERE r.result_status = '3'
  AND EXISTS (
    SELECT 1 FROM sup_auto_inspection_step_result sr
    WHERE sr.record_id = r.record_id AND sr.enabled_flag = 'Y' AND sr.result_status = '1'
  )
  AND NOT EXISTS (
    SELECT 1 FROM sup_auto_inspection_step_result sr
    WHERE sr.record_id = r.record_id AND sr.enabled_flag = 'Y' AND sr.result_status <> '1'
  );

UPDATE sup_auto_inspection_probe_state ps
INNER JOIN tmp_v3162_baseline_targets t
  ON t.plan_id = ps.plan_id AND t.target_id = ps.target_id
INNER JOIN sup_auto_inspection_step_result sr
  ON sr.step_result_id = t.step_result_id AND sr.step_id = ps.step_id AND sr.tool_code = ps.tool_code
SET ps.state_status = '1',
    ps.state_detail = CASE
      WHEN ps.state_detail LIKE '%已建立首次采样基线%' THEN REPLACE(ps.state_detail, '已建立首次采样基线', '已建立首次采样基线，本次按正常计入健康度')
      WHEN ps.state_detail LIKE '%已重新建立基线%' THEN REPLACE(ps.state_detail, '已重新建立基线', '已重新建立基线，本次按正常计入健康度')
      ELSE ps.state_detail
    END,
    ps.update_by = 'system',
    ps.update_time = NOW()
WHERE ps.state_status = '3'
  AND (ps.state_detail LIKE '%已建立首次采样基线%' OR ps.state_detail LIKE '%已重新建立基线%');

DROP TEMPORARY TABLE IF EXISTS tmp_v3162_daily_stats;
CREATE TEMPORARY TABLE tmp_v3162_daily_stats ENGINE=InnoDB
SELECT d.plan_id,
       d.health_date,
       COUNT(r.record_id) AS completed_count,
       SUM(CASE WHEN r.result_status = '1' THEN 1 ELSE 0 END) AS normal_count,
       SUM(CASE WHEN r.result_status = '4' THEN 1 ELSE 0 END) AS warning_count,
       SUM(CASE WHEN r.result_status = '2' THEN 1 ELSE 0 END) AS abnormal_count,
       SUM(CASE WHEN r.result_status = '3' THEN 1 ELSE 0 END) AS skipped_count,
       MIN(CASE WHEN r.result_status = '2' THEN r.inspection_time END) AS first_abnormal_time,
       MAX(CASE WHEN r.result_status = '2' THEN r.inspection_time END) AS last_abnormal_time,
       MAX(r.inspection_time) AS last_run_time,
       SUBSTRING_INDEX(GROUP_CONCAT(r.result_status ORDER BY r.inspection_time DESC, r.record_id DESC), ',', 1) AS last_result_status,
       SUBSTRING_INDEX(GROUP_CONCAT(CASE WHEN r.result_status IN ('2', '4') THEN r.abnormal_summary END ORDER BY r.inspection_time DESC, r.record_id DESC SEPARATOR '||'), '||', 1) AS abnormal_summary
FROM (
  SELECT DISTINCT plan_id, health_date
  FROM tmp_v3162_baseline_records
  WHERE plan_id IS NOT NULL
) d
INNER JOIN sup_auto_inspection_record r
  ON r.plan_id = d.plan_id
  AND r.run_mode = 'FREQUENT'
  AND r.source_type = 'AUTO'
  AND r.inspection_time >= d.health_date
  AND r.inspection_time < DATE_ADD(d.health_date, INTERVAL 1 DAY)
GROUP BY d.plan_id, d.health_date;

UPDATE sup_auto_inspection_health_daily h
INNER JOIN tmp_v3162_daily_stats s
  ON s.plan_id = h.plan_id AND s.health_date = h.health_date
SET h.completed_count = s.completed_count,
    h.normal_count = s.normal_count,
    h.warning_count = s.warning_count,
    h.abnormal_count = s.abnormal_count,
    h.skipped_count = s.skipped_count,
    h.missing_count = GREATEST(COALESCE(h.expected_count, 0) - s.completed_count, 0),
    h.health_score = CASE
      WHEN GREATEST(COALESCE(h.expected_count, 0), s.completed_count) > 0
        THEN ROUND(s.normal_count * 100 / GREATEST(COALESCE(h.expected_count, 0), s.completed_count), 2)
      ELSE 0
    END,
    h.day_status = CASE
      WHEN s.abnormal_count > 0 THEN '2'
      WHEN s.warning_count > 0 OR GREATEST(COALESCE(h.expected_count, 0) - s.completed_count, 0) > 0 THEN '4'
      WHEN s.completed_count > 0 THEN '1'
      ELSE '3'
    END,
    h.first_abnormal_time = s.first_abnormal_time,
    h.last_abnormal_time = s.last_abnormal_time,
    h.last_run_time = s.last_run_time,
    h.last_result_status = s.last_result_status,
    h.abnormal_summary = COALESCE(NULLIF(s.abnormal_summary, ''), '无异常'),
    h.update_by = 'system',
    h.update_time = NOW();

SELECT COUNT(*) AS matched_baseline_targets FROM tmp_v3162_baseline_targets;
SELECT COUNT(*) AS affected_records FROM tmp_v3162_baseline_records;
SELECT COUNT(*) AS affected_daily_summaries FROM tmp_v3162_daily_stats;

DROP TEMPORARY TABLE IF EXISTS tmp_v3162_daily_stats;
DROP TEMPORARY TABLE IF EXISTS tmp_v3162_baseline_records;
DROP TEMPORARY TABLE IF EXISTS tmp_v3162_baseline_steps;
DROP TEMPORARY TABLE IF EXISTS tmp_v3162_baseline_targets;

-- ============================================================================
-- v3.17.0 现场设备机房三维摆放与上联拓扑
-- 来源：support_upgrade_20260830_equipment_location_3d_topology_v3_17_0.sql
-- ============================================================================

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sup_equipment_room' AND COLUMN_NAME = 'room_width') = 0,
  'ALTER TABLE sup_equipment_room ADD COLUMN room_width DECIMAL(8,2) DEFAULT 12.00 COMMENT ''机房宽度（米）'' AFTER room_code', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sup_equipment_room' AND COLUMN_NAME = 'room_depth') = 0,
  'ALTER TABLE sup_equipment_room ADD COLUMN room_depth DECIMAL(8,2) DEFAULT 8.00 COMMENT ''机房深度（米）'' AFTER room_width', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sup_equipment_cabinet' AND COLUMN_NAME = 'position_x') = 0,
  'ALTER TABLE sup_equipment_cabinet ADD COLUMN position_x DECIMAL(8,2) DEFAULT NULL COMMENT ''机柜平面X坐标（米）'' AFTER u_capacity', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sup_equipment_cabinet' AND COLUMN_NAME = 'position_z') = 0,
  'ALTER TABLE sup_equipment_cabinet ADD COLUMN position_z DECIMAL(8,2) DEFAULT NULL COMMENT ''机柜平面Z坐标（米）'' AFTER position_x', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sup_equipment_cabinet' AND COLUMN_NAME = 'rotation_y') = 0,
  'ALTER TABLE sup_equipment_cabinet ADD COLUMN rotation_y DECIMAL(6,1) DEFAULT 0.0 COMMENT ''机柜Y轴朝向角度'' AFTER position_z', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS sup_equipment_link (
  link_id             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '设备链路ID',
  site_id             BIGINT       NOT NULL COMMENT '现场ID',
  source_type         VARCHAR(16)  NOT NULL COMMENT '源设备类型（SERVER/HARDWARE）',
  source_id           BIGINT       NOT NULL COMMENT '源设备ID',
  target_type         VARCHAR(16)  NOT NULL DEFAULT 'HARDWARE' COMMENT '目标设备类型',
  target_id           BIGINT       NOT NULL COMMENT '目标交换机资产ID',
  medium_type         VARCHAR(16)  NOT NULL COMMENT '链路介质（OPTICAL/ELECTRICAL）',
  port_count          INT          NOT NULL DEFAULT 1 COMMENT '占用端口数量',
  source_port         VARCHAR(80)  DEFAULT NULL COMMENT '源端口说明',
  target_port         VARCHAR(80)  DEFAULT NULL COMMENT '目标端口说明',
  status              CHAR(1)      DEFAULT '0' COMMENT '状态（0正常 1停用）',
  create_by           VARCHAR(64)  DEFAULT '' COMMENT '创建者',
  create_time         DATETIME     DEFAULT NULL COMMENT '创建时间',
  update_by           VARCHAR(64)  DEFAULT '' COMMENT '更新者',
  update_time         DATETIME     DEFAULT NULL COMMENT '更新时间',
  remark              VARCHAR(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (link_id),
  KEY idx_sup_equipment_link_site (site_id),
  KEY idx_sup_equipment_link_source (source_type, source_id),
  KEY idx_sup_equipment_link_target (target_type, target_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='现场设备物理上联关系';

-- ============================================================================
-- v4.0.0 设备与机房一张图统一管控权限
-- 来源：support_upgrade_20260830_equipment_location_unified_control_v4_0_0.sql
-- ============================================================================

INSERT INTO sys_menu(menu_id, menu_name, parent_id, order_num, path, component, `query`, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
VALUES
(2321, '设备统一新增', 2201, 16, '#', '', '', '', 1, 0, 'F', '0', '0', 'support:equipment:add', '#', 'admin', NOW(), '', NULL, ''),
(2322, '设备统一修改', 2201, 17, '#', '', '', '', 1, 0, 'F', '0', '0', 'support:equipment:edit', '#', 'admin', NOW(), '', NULL, ''),
(2323, '设备统一删除', 2201, 18, '#', '', '', '', 1, 0, 'F', '0', '0', 'support:equipment:remove', '#', 'admin', NOW(), '', NULL, '')
ON DUPLICATE KEY UPDATE
menu_name = VALUES(menu_name),
parent_id = VALUES(parent_id),
order_num = VALUES(order_num),
perms = VALUES(perms),
status = VALUES(status);

INSERT INTO sys_role_menu(role_id, menu_id)
SELECT r.role_id, m.menu_id
FROM sys_role r
         INNER JOIN sys_menu m ON m.menu_id IN (2321, 2322, 2323)
WHERE r.role_key = 'datafusion'
  AND NOT EXISTS (
    SELECT 1
    FROM sys_role_menu rm
    WHERE rm.role_id = r.role_id
      AND rm.menu_id = m.menu_id
  );

-- ============================================================================
-- 最终核验：预期 inspection_columns=5、room_3d_columns=2、cabinet_3d_columns=3、
-- equipment_link_table=1、equipment_permissions=3。
-- ============================================================================

SELECT
  (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sup_auto_inspection_target_result' AND COLUMN_NAME IN ('evaluation_mode', 'previous_value', 'change_value', 'evaluation_rule', 'baseline_flag')) AS inspection_columns,
  (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sup_equipment_room' AND COLUMN_NAME IN ('room_width', 'room_depth')) AS room_3d_columns,
  (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sup_equipment_cabinet' AND COLUMN_NAME IN ('position_x', 'position_z', 'rotation_y')) AS cabinet_3d_columns,
  (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sup_equipment_link') AS equipment_link_table,
  (SELECT COUNT(*) FROM sys_menu WHERE menu_id IN (2321, 2322, 2323) AND perms IN ('support:equipment:add', 'support:equipment:edit', 'support:equipment:remove')) AS equipment_permissions;
