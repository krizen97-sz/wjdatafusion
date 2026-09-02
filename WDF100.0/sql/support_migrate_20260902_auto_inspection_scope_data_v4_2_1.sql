-- 自动化巡检旧数据归属迁移 v4.2.1
-- 前置条件：已执行 v4.2.0 结构升级，计划、执行记录和每日健康表已存在归属快照字段。
-- 默认行为：高置信度自动迁移；多现场冲突或无资产证据的计划保留待归属并输出清单。
-- 预览模式：执行脚本前先设置 SET @auto_apply_scope_migration = 0;
-- 正式模式：默认值为 1，也可显式设置 SET @auto_apply_scope_migration = 1;

SET NAMES utf8mb4;
SET @auto_apply_scope_migration = IFNULL(@auto_apply_scope_migration, 1);

DROP TEMPORARY TABLE IF EXISTS tmp_auto_scope_override;
CREATE TEMPORARY TABLE tmp_auto_scope_override (
  plan_id BIGINT NOT NULL COMMENT '计划ID',
  scope_type VARCHAR(20) NOT NULL COMMENT 'SITE或MAIN_PLATFORM',
  site_id BIGINT DEFAULT NULL COMMENT '现场ID；MAIN_PLATFORM可省略并由主平台反查',
  main_platform_id BIGINT DEFAULT NULL COMMENT '主平台ID',
  remark VARCHAR(500) DEFAULT NULL COMMENT '覆盖原因',
  PRIMARY KEY (plan_id)
) ENGINE=InnoDB;

-- 手工覆盖区：仅用于无法通过服务器资产推导的HTTP、Kafka、数据库等计划。
-- 示例一：计划归属现场。
-- INSERT INTO tmp_auto_scope_override(plan_id, scope_type, site_id, main_platform_id, remark)
-- VALUES (1001, 'SITE', 2, NULL, '该计划检查整个现场');
-- 示例二：计划归属主平台。
-- INSERT INTO tmp_auto_scope_override(plan_id, scope_type, site_id, main_platform_id, remark)
-- VALUES (1002, 'MAIN_PLATFORM', NULL, 19, '该计划只检查TIM主平台');

DROP TEMPORARY TABLE IF EXISTS tmp_auto_server_scope;
CREATE TEMPORARY TABLE tmp_auto_server_scope ENGINE=InnoDB AS
SELECT s.server_id,
       s.site_id,
       CASE
         WHEN COUNT(DISTINCT CASE
           WHEN p.platform_level = 'MAIN' THEN p.platform_id
           WHEN p.platform_level = 'SUB' THEN p.parent_platform_id
         END) = 1
         THEN MIN(CASE
           WHEN p.platform_level = 'MAIN' THEN p.platform_id
           WHEN p.platform_level = 'SUB' THEN p.parent_platform_id
         END)
         ELSE NULL
       END AS main_platform_id,
       COUNT(DISTINCT CASE
         WHEN p.platform_level = 'MAIN' THEN p.platform_id
         WHEN p.platform_level = 'SUB' THEN p.parent_platform_id
       END) AS main_platform_count
FROM sup_server s
LEFT JOIN sup_platform_server_rel psr ON psr.server_id = s.server_id
LEFT JOIN sup_platform p ON p.platform_id = psr.platform_id
GROUP BY s.server_id, s.site_id;
ALTER TABLE tmp_auto_server_scope ADD PRIMARY KEY (server_id);

DROP TEMPORARY TABLE IF EXISTS tmp_auto_plan_scope_evidence;
CREATE TEMPORARY TABLE tmp_auto_plan_scope_evidence (
  plan_id BIGINT NOT NULL,
  evidence_source VARCHAR(32) NOT NULL,
  site_id BIGINT NOT NULL,
  main_platform_id BIGINT DEFAULT NULL,
  KEY idx_tmp_auto_evidence_plan (plan_id)
) ENGINE=InnoDB;

-- 已有执行快照优先作为历史事实证据。
INSERT INTO tmp_auto_plan_scope_evidence(plan_id, evidence_source, site_id, main_platform_id)
SELECT DISTINCT r.plan_id, 'RECORD_SNAPSHOT', r.site_id,
       CASE WHEN r.scope_type = 'MAIN_PLATFORM' THEN r.main_platform_id ELSE NULL END
FROM sup_auto_inspection_record r
WHERE r.plan_id IS NOT NULL AND r.site_id IS NOT NULL;

INSERT INTO tmp_auto_plan_scope_evidence(plan_id, evidence_source, site_id, main_platform_id)
SELECT DISTINCT h.plan_id, 'HEALTH_SNAPSHOT', h.site_id,
       CASE WHEN h.scope_type = 'MAIN_PLATFORM' THEN h.main_platform_id ELSE NULL END
FROM sup_auto_inspection_health_daily h
WHERE h.plan_id IS NOT NULL AND h.site_id IS NOT NULL;

-- 当前模板步骤关联的现场服务器证据。
INSERT INTO tmp_auto_plan_scope_evidence(plan_id, evidence_source, site_id, main_platform_id)
SELECT DISTINCT p.plan_id, 'TEMPLATE_SERVER', ss.site_id, ss.main_platform_id
FROM sup_auto_inspection_plan p
INNER JOIN sup_auto_inspection_template_step step ON step.template_id = p.template_id
INNER JOIN sup_auto_inspection_template_step_target rel ON rel.step_id = step.step_id
INNER JOIN sup_auto_inspection_target target ON target.target_id = rel.target_id
INNER JOIN tmp_auto_server_scope ss ON ss.server_id = target.server_id
WHERE target.server_id IS NOT NULL;

-- 历史执行曾经调用过、但模板关系已调整或缺失的服务器证据。
INSERT INTO tmp_auto_plan_scope_evidence(plan_id, evidence_source, site_id, main_platform_id)
SELECT DISTINCT record.plan_id, 'RESULT_SERVER', ss.site_id, ss.main_platform_id
FROM sup_auto_inspection_record record
INNER JOIN sup_auto_inspection_target_result result_row ON result_row.record_id = record.record_id
INNER JOIN sup_auto_inspection_target target ON target.target_id = result_row.target_id
INNER JOIN tmp_auto_server_scope ss ON ss.server_id = target.server_id
WHERE record.plan_id IS NOT NULL AND target.server_id IS NOT NULL;

DROP TEMPORARY TABLE IF EXISTS tmp_auto_plan_evidence_summary;
CREATE TEMPORARY TABLE tmp_auto_plan_evidence_summary ENGINE=InnoDB AS
SELECT evidence.plan_id,
       COUNT(*) AS evidence_count,
       COUNT(DISTINCT evidence.site_id) AS site_count,
       MIN(evidence.site_id) AS inferred_site_id,
       COUNT(DISTINCT evidence.main_platform_id) AS main_platform_count,
       MIN(evidence.main_platform_id) AS inferred_main_platform_id,
       SUM(CASE WHEN evidence.main_platform_id IS NULL THEN 1 ELSE 0 END) AS site_level_evidence_count,
       GROUP_CONCAT(DISTINCT evidence.evidence_source ORDER BY evidence.evidence_source SEPARATOR ',') AS evidence_sources
FROM tmp_auto_plan_scope_evidence evidence
GROUP BY evidence.plan_id;
ALTER TABLE tmp_auto_plan_evidence_summary ADD PRIMARY KEY (plan_id);

DROP TEMPORARY TABLE IF EXISTS tmp_auto_scope_override_resolved;
CREATE TEMPORARY TABLE tmp_auto_scope_override_resolved ENGINE=InnoDB AS
SELECT override_row.plan_id,
       UPPER(TRIM(override_row.scope_type)) AS scope_type,
       CASE
         WHEN UPPER(TRIM(override_row.scope_type)) = 'MAIN_PLATFORM' THEN main_platform.site_id
         ELSE override_row.site_id
       END AS site_id,
       CASE
         WHEN UPPER(TRIM(override_row.scope_type)) = 'MAIN_PLATFORM' THEN override_row.main_platform_id
         ELSE NULL
       END AS main_platform_id,
       override_row.remark,
       CASE
         WHEN plan_row.plan_id IS NULL THEN 'N'
         WHEN UPPER(TRIM(override_row.scope_type)) = 'SITE'
           AND site_row.site_id IS NOT NULL AND override_row.main_platform_id IS NULL THEN 'Y'
         WHEN UPPER(TRIM(override_row.scope_type)) = 'MAIN_PLATFORM'
           AND main_platform.platform_id IS NOT NULL
           AND main_platform.platform_level = 'MAIN'
           AND (override_row.site_id IS NULL OR override_row.site_id = main_platform.site_id) THEN 'Y'
         ELSE 'N'
       END AS valid_flag,
       CASE
         WHEN plan_row.plan_id IS NULL THEN '覆盖计划不存在'
         WHEN UPPER(TRIM(override_row.scope_type)) NOT IN ('SITE', 'MAIN_PLATFORM') THEN '覆盖类型必须为SITE或MAIN_PLATFORM'
         WHEN UPPER(TRIM(override_row.scope_type)) = 'SITE' AND site_row.site_id IS NULL THEN '覆盖现场不存在'
         WHEN UPPER(TRIM(override_row.scope_type)) = 'SITE' AND override_row.main_platform_id IS NOT NULL THEN '现场级覆盖不能填写主平台'
         WHEN UPPER(TRIM(override_row.scope_type)) = 'MAIN_PLATFORM' AND main_platform.platform_id IS NULL THEN '覆盖主平台不存在'
         WHEN UPPER(TRIM(override_row.scope_type)) = 'MAIN_PLATFORM' AND main_platform.platform_level <> 'MAIN' THEN '只能覆盖到主平台'
         WHEN UPPER(TRIM(override_row.scope_type)) = 'MAIN_PLATFORM'
           AND override_row.site_id IS NOT NULL AND override_row.site_id <> main_platform.site_id THEN '覆盖现场与主平台不属于同一现场'
         ELSE '覆盖配置有效'
       END AS validation_message
FROM tmp_auto_scope_override override_row
LEFT JOIN sup_auto_inspection_plan plan_row ON plan_row.plan_id = override_row.plan_id
LEFT JOIN sup_site site_row ON site_row.site_id = override_row.site_id
LEFT JOIN sup_platform main_platform ON main_platform.platform_id = override_row.main_platform_id;
ALTER TABLE tmp_auto_scope_override_resolved ADD PRIMARY KEY (plan_id);

DROP TEMPORARY TABLE IF EXISTS tmp_auto_plan_scope_decision;
CREATE TEMPORARY TABLE tmp_auto_plan_scope_decision ENGINE=InnoDB AS
SELECT plan_row.plan_id,
       plan_row.plan_name,
       plan_row.scope_type AS current_scope_type,
       plan_row.site_id AS current_site_id,
       plan_row.main_platform_id AS current_main_platform_id,
       CASE
         WHEN (plan_row.scope_type = 'SITE' AND current_site.site_id IS NOT NULL)
           OR (plan_row.scope_type = 'MAIN_PLATFORM' AND current_main.platform_id IS NOT NULL AND current_main.platform_level = 'MAIN')
           THEN 'ALREADY_ASSIGNED'
         WHEN override_resolved.plan_id IS NOT NULL AND override_resolved.valid_flag = 'N' THEN 'INVALID_OVERRIDE'
         WHEN override_resolved.valid_flag = 'Y' THEN 'READY_OVERRIDE'
         WHEN evidence.site_count = 1 AND evidence.main_platform_count = 1 AND evidence.site_level_evidence_count = 0 THEN 'READY_MAIN_PLATFORM'
         WHEN evidence.site_count = 1 THEN 'READY_SITE'
         WHEN evidence.site_count > 1 THEN 'CONFLICT_MULTI_SITE'
         ELSE 'NO_EVIDENCE'
       END AS migration_status,
       CASE
         WHEN override_resolved.valid_flag = 'Y' THEN override_resolved.scope_type
         WHEN evidence.site_count = 1 AND evidence.main_platform_count = 1 AND evidence.site_level_evidence_count = 0 THEN 'MAIN_PLATFORM'
         WHEN evidence.site_count = 1 THEN 'SITE'
         ELSE NULL
       END AS proposed_scope_type,
       CASE
         WHEN override_resolved.valid_flag = 'Y' THEN override_resolved.site_id
         WHEN evidence.site_count = 1 THEN evidence.inferred_site_id
         ELSE NULL
       END AS proposed_site_id,
       CASE
         WHEN override_resolved.valid_flag = 'Y' THEN override_resolved.main_platform_id
         WHEN evidence.site_count = 1 AND evidence.main_platform_count = 1 AND evidence.site_level_evidence_count = 0
           THEN evidence.inferred_main_platform_id
         ELSE NULL
       END AS proposed_main_platform_id,
       evidence.evidence_count,
       evidence.site_count,
       evidence.main_platform_count,
       evidence.site_level_evidence_count,
       evidence.evidence_sources,
       CASE
         WHEN (plan_row.scope_type = 'SITE' AND current_site.site_id IS NOT NULL)
           OR (plan_row.scope_type = 'MAIN_PLATFORM' AND current_main.platform_id IS NOT NULL AND current_main.platform_level = 'MAIN')
           THEN '保留现有有效归属'
         WHEN override_resolved.plan_id IS NOT NULL AND override_resolved.valid_flag = 'N' THEN override_resolved.validation_message
         WHEN override_resolved.valid_flag = 'Y' THEN CONCAT('使用手工覆盖：', COALESCE(override_resolved.remark, '未填写原因'))
         WHEN evidence.site_count = 1 AND evidence.main_platform_count = 1 AND evidence.site_level_evidence_count = 0
           THEN CONCAT('服务器及历史快照均指向同一主平台；证据：', evidence.evidence_sources)
         WHEN evidence.site_count = 1 THEN CONCAT('证据指向同一现场但无法唯一归属主平台；证据：', evidence.evidence_sources)
         WHEN evidence.site_count > 1 THEN CONCAT('证据跨越', evidence.site_count, '个现场，禁止自动迁移')
         ELSE '没有可关联的现场服务器或历史归属快照'
       END AS migration_reason
FROM sup_auto_inspection_plan plan_row
LEFT JOIN sup_site current_site ON current_site.site_id = plan_row.site_id
LEFT JOIN sup_platform current_main ON current_main.platform_id = plan_row.main_platform_id
LEFT JOIN tmp_auto_scope_override_resolved override_resolved ON override_resolved.plan_id = plan_row.plan_id
LEFT JOIN tmp_auto_plan_evidence_summary evidence ON evidence.plan_id = plan_row.plan_id;
ALTER TABLE tmp_auto_plan_scope_decision ADD PRIMARY KEY (plan_id);

DROP TEMPORARY TABLE IF EXISTS tmp_auto_plan_scope_preview;
CREATE TEMPORARY TABLE tmp_auto_plan_scope_preview ENGINE=InnoDB AS
SELECT decision.*,
       site_row.site_name AS proposed_site_name,
       main_platform.platform_name AS proposed_main_platform_name
FROM tmp_auto_plan_scope_decision decision
LEFT JOIN sup_site site_row ON site_row.site_id = decision.proposed_site_id
LEFT JOIN sup_platform main_platform ON main_platform.platform_id = decision.proposed_main_platform_id;
ALTER TABLE tmp_auto_plan_scope_preview ADD PRIMARY KEY (plan_id);

-- 执行前预览。READY_*会自动应用，CONFLICT/NO_EVIDENCE/INVALID_OVERRIDE只输出不修改。
SELECT plan_id, plan_name, current_scope_type, current_site_id, current_main_platform_id,
       migration_status, proposed_scope_type, proposed_site_id, proposed_site_name,
       proposed_main_platform_id, proposed_main_platform_name, migration_reason
FROM tmp_auto_plan_scope_preview
ORDER BY FIELD(migration_status, 'INVALID_OVERRIDE', 'CONFLICT_MULTI_SITE', 'NO_EVIDENCE',
               'READY_OVERRIDE', 'READY_MAIN_PLATFORM', 'READY_SITE', 'ALREADY_ASSIGNED'), plan_id;

START TRANSACTION;

UPDATE sup_auto_inspection_plan plan_row
INNER JOIN tmp_auto_plan_scope_preview preview ON preview.plan_id = plan_row.plan_id
SET plan_row.scope_type = preview.proposed_scope_type,
    plan_row.site_id = preview.proposed_site_id,
    plan_row.site_name = preview.proposed_site_name,
    plan_row.main_platform_id = preview.proposed_main_platform_id,
    plan_row.main_platform_name = preview.proposed_main_platform_name,
    plan_row.update_by = 'migration_v4.2.1',
    plan_row.update_time = NOW()
WHERE @auto_apply_scope_migration = 1
  AND preview.migration_status IN ('READY_OVERRIDE', 'READY_MAIN_PLATFORM', 'READY_SITE');

UPDATE sup_auto_inspection_record record_row
INNER JOIN sup_auto_inspection_plan plan_row ON plan_row.plan_id = record_row.plan_id
SET record_row.scope_type = plan_row.scope_type,
    record_row.site_id = plan_row.site_id,
    record_row.site_name = plan_row.site_name,
    record_row.main_platform_id = plan_row.main_platform_id,
    record_row.main_platform_name = plan_row.main_platform_name,
    record_row.update_by = 'migration_v4.2.1',
    record_row.update_time = NOW()
WHERE @auto_apply_scope_migration = 1
  AND plan_row.scope_type IN ('SITE', 'MAIN_PLATFORM')
  AND plan_row.site_id IS NOT NULL
  AND (record_row.scope_type IS NULL OR record_row.site_id IS NULL);

UPDATE sup_auto_inspection_health_daily health_row
INNER JOIN sup_auto_inspection_plan plan_row ON plan_row.plan_id = health_row.plan_id
SET health_row.scope_type = plan_row.scope_type,
    health_row.site_id = plan_row.site_id,
    health_row.site_name = plan_row.site_name,
    health_row.main_platform_id = plan_row.main_platform_id,
    health_row.main_platform_name = plan_row.main_platform_name,
    health_row.update_by = 'migration_v4.2.1',
    health_row.update_time = NOW()
WHERE @auto_apply_scope_migration = 1
  AND plan_row.scope_type IN ('SITE', 'MAIN_PLATFORM')
  AND plan_row.site_id IS NOT NULL
  AND (health_row.scope_type IS NULL OR health_row.site_id IS NULL);

-- 为旧版逐次计划补建缺失的每日汇总；已有每日健康记录保持原统计口径。
INSERT IGNORE INTO sup_auto_inspection_health_daily(
  health_date, plan_id, plan_name, template_id, template_name,
  scope_type, site_id, site_name, main_platform_id, main_platform_name,
  expected_count, completed_count, normal_count, warning_count, abnormal_count, skipped_count,
  missing_count, health_score, health_target, day_status,
  first_abnormal_time, last_abnormal_time, last_run_time, last_result_status, abnormal_summary,
  create_by, create_time, update_by, update_time
)
SELECT DATE(record_row.inspection_time),
       record_row.plan_id,
       COALESCE(MAX(record_row.plan_name), MAX(plan_row.plan_name), '未命名计划'),
       MAX(record_row.template_id),
       MAX(record_row.template_name),
       MAX(plan_row.scope_type),
       MAX(plan_row.site_id),
       MAX(plan_row.site_name),
       MAX(plan_row.main_platform_id),
       MAX(plan_row.main_platform_name),
       COUNT(1),
       COUNT(1),
       SUM(CASE WHEN record_row.result_status = '1' THEN 1 ELSE 0 END),
       SUM(CASE WHEN record_row.result_status = '4' THEN 1 ELSE 0 END),
       SUM(CASE WHEN record_row.result_status = '2' THEN 1 ELSE 0 END),
       SUM(CASE WHEN record_row.result_status = '3' THEN 1 ELSE 0 END),
       0,
       ROUND(SUM(CASE WHEN record_row.result_status = '1' THEN 1 ELSE 0 END) * 100 / COUNT(1), 2),
       99,
       CASE
         WHEN SUM(CASE WHEN record_row.result_status = '2' THEN 1 ELSE 0 END) > 0 THEN '2'
         WHEN SUM(CASE WHEN record_row.result_status = '4' THEN 1 ELSE 0 END) > 0 THEN '4'
         WHEN SUM(CASE WHEN record_row.result_status = '1' THEN 1 ELSE 0 END) > 0 THEN '1'
         ELSE '3'
       END,
       MIN(CASE WHEN record_row.result_status = '2' THEN record_row.inspection_time END),
       MAX(CASE WHEN record_row.result_status = '2' THEN record_row.inspection_time END),
       MAX(record_row.inspection_time),
       SUBSTRING_INDEX(GROUP_CONCAT(record_row.result_status ORDER BY record_row.inspection_time DESC, record_row.record_id DESC), ',', 1),
       SUBSTRING_INDEX(GROUP_CONCAT(CASE WHEN record_row.result_status IN ('2', '4') THEN record_row.abnormal_summary END
         ORDER BY record_row.inspection_time DESC, record_row.record_id DESC SEPARATOR '||'), '||', 1),
       'migration_v4.2.1', NOW(), 'migration_v4.2.1', NOW()
FROM sup_auto_inspection_record record_row
INNER JOIN sup_auto_inspection_plan plan_row ON plan_row.plan_id = record_row.plan_id
WHERE @auto_apply_scope_migration = 1
  AND record_row.source_type = 'AUTO'
  AND record_row.plan_id IS NOT NULL
GROUP BY DATE(record_row.inspection_time), record_row.plan_id;

COMMIT;

-- 执行结果与仍需人工归属的计划。
SELECT migration_status, COUNT(*) AS plan_count
FROM tmp_auto_plan_scope_preview
GROUP BY migration_status
ORDER BY migration_status;

SELECT preview.plan_id, preview.plan_name, preview.migration_status, preview.migration_reason,
       plan_row.scope_type AS final_scope_type,
       plan_row.site_id AS final_site_id,
       plan_row.site_name AS final_site_name,
       plan_row.main_platform_id AS final_main_platform_id,
       plan_row.main_platform_name AS final_main_platform_name
FROM tmp_auto_plan_scope_preview preview
INNER JOIN sup_auto_inspection_plan plan_row ON plan_row.plan_id = preview.plan_id
WHERE preview.migration_status IN ('INVALID_OVERRIDE', 'CONFLICT_MULTI_SITE', 'NO_EVIDENCE')
ORDER BY preview.migration_status, preview.plan_id;

SELECT COUNT(*) AS total_plan_count,
       SUM(CASE WHEN scope_type IN ('SITE', 'MAIN_PLATFORM') AND site_id IS NOT NULL THEN 1 ELSE 0 END) AS assigned_plan_count,
       SUM(CASE WHEN scope_type IS NULL OR site_id IS NULL THEN 1 ELSE 0 END) AS pending_plan_count
FROM sup_auto_inspection_plan;

DROP TEMPORARY TABLE IF EXISTS tmp_auto_plan_scope_preview;
DROP TEMPORARY TABLE IF EXISTS tmp_auto_plan_scope_decision;
DROP TEMPORARY TABLE IF EXISTS tmp_auto_scope_override_resolved;
DROP TEMPORARY TABLE IF EXISTS tmp_auto_plan_evidence_summary;
DROP TEMPORARY TABLE IF EXISTS tmp_auto_plan_scope_evidence;
DROP TEMPORARY TABLE IF EXISTS tmp_auto_server_scope;
DROP TEMPORARY TABLE IF EXISTS tmp_auto_scope_override;
