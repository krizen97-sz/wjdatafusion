-- 2026-07-17 IPAM 历史 Excel 数据质量清理
--
-- 适用源文件：社会面新整理(3.xlsx
-- SHA-256：66520ed4d2fe4a2cf3cd15290e84ab3c1cae703a3bcf5f4d44dce2addd5a564d
--
-- 边界：
-- 1. 只处理证据明确的 ipam_ 历史迁移数据，不修改 sup_* 现场融合业务表。
-- 2. 不修改 ipam_migration_source_row，Excel 3,535 条原始非空行继续完整保留。
-- 3. 不处理 75 条来源冲突、不确定联系人、疑似重复小区和未落位内网 IP。
-- 4. 不修改设备密码内容，避免多人维护表中的有效密码字符被误清理。
-- 5. 执行前由审计程序校验批次哈希、表行数和目标旧值，任何一项不匹配即停止。

-- 本次备份。表名固定，重复执行会因表已存在而停止，避免覆盖首次备份。
CREATE TABLE ipam_clean_bak_20260717_203146_address LIKE ipam_address;
INSERT INTO ipam_clean_bak_20260717_203146_address SELECT * FROM ipam_address;

CREATE TABLE ipam_clean_bak_20260717_203146_site LIKE ipam_site;
INSERT INTO ipam_clean_bak_20260717_203146_site SELECT * FROM ipam_site;

CREATE TABLE ipam_clean_bak_20260717_203146_mapping_relation LIKE ipam_mapping_relation;
INSERT INTO ipam_clean_bak_20260717_203146_mapping_relation SELECT * FROM ipam_mapping_relation;

CREATE TABLE ipam_clean_bak_20260717_203146_source_row LIKE ipam_migration_source_row;
INSERT INTO ipam_clean_bak_20260717_203146_source_row SELECT * FROM ipam_migration_source_row;

CREATE TABLE ipam_clean_bak_20260717_203146_conflict LIKE ipam_migration_conflict;
INSERT INTO ipam_clean_bak_20260717_203146_conflict SELECT * FROM ipam_migration_conflict;

CREATE TABLE ipam_clean_bak_20260717_203146_operation_log LIKE ipam_operation_log;
INSERT INTO ipam_clean_bak_20260717_203146_operation_log SELECT * FROM ipam_operation_log;

CREATE TABLE ipam_clean_bak_20260717_203146_migration_batch LIKE ipam_migration_batch;
INSERT INTO ipam_clean_bak_20260717_203146_migration_batch SELECT * FROM ipam_migration_batch;

START TRANSACTION;

-- “丁龙崎15996312932”位于“恒大御景”连续设备块中，是联系人式文本，
-- 迁移时被误识别为新小区并向下传播。合并归属，但不猜测联系人角色。
UPDATE ipam_site target
JOIN ipam_site misplaced ON misplaced.site_id = 34
SET target.source_refs_json = JSON_MERGE_PRESERVE(
      COALESCE(target.source_refs_json, JSON_ARRAY()),
      COALESCE(misplaced.source_refs_json, JSON_ARRAY())
    ),
    target.remark = CONCAT_WS(
      '；',
      NULLIF(target.remark, ''),
      '数据清理：湖塘!67“丁龙崎15996312932”疑似联系人，角色待确认；原始行已归档'
    ),
    target.update_by = 'data_cleanup_20260717',
    target.update_time = NOW()
WHERE target.site_id = 33
  AND target.area_name = '湖塘'
  AND target.site_name = '恒大御景'
  AND misplaced.area_name = '湖塘'
  AND misplaced.site_name = '丁龙崎15996312932';

UPDATE ipam_address
SET site_id = 33,
    community_name = '恒大御景',
    update_by = 'data_cleanup_20260717',
    update_time = NOW()
WHERE site_id = 34
  AND area_name = '湖塘'
  AND community_name = '丁龙崎15996312932';

UPDATE ipam_mapping_relation
SET site_id = 33,
    community_name = '恒大御景',
    update_by = 'data_cleanup_20260717',
    update_time = NOW()
WHERE site_id = 34
  AND area_name = '湖塘'
  AND community_name = '丁龙崎15996312932';

-- 历史迁移把 IPv4 的数字片段误写成端口，466 条关系均无对应源端口语义。
-- 真正的 6 条 IP:端口源记录没有进入这些关系，另列待确认清单。
UPDATE ipam_mapping_relation
SET source_port = NULL,
    target_port = NULL,
    update_by = 'data_cleanup_20260717',
    update_time = NOW()
WHERE NULLIF(source_port, '') IS NOT NULL
   OR NULLIF(target_port, '') IS NOT NULL;

DELETE FROM ipam_site
WHERE site_id = 34
  AND area_name = '湖塘'
  AND site_name = '丁龙崎15996312932';

-- 从同一小区源行中的合法经纬度对补齐坐标。
UPDATE ipam_site
SET longitude = 119.9449080,
    latitude = 31.7323730,
    update_by = 'data_cleanup_20260717',
    update_time = NOW()
WHERE site_id = 53
  AND area_name = '湖塘'
  AND site_name = '美的国宾府'
  AND longitude IS NULL
  AND latitude IS NULL;

UPDATE ipam_site
SET longitude = 119.8660890,
    latitude = 31.6748830,
    update_by = 'data_cleanup_20260717',
    update_time = NOW()
WHERE site_id = 280
  AND area_name = '滆湖'
  AND site_name = '蓝天湖畔'
  AND longitude IS NULL
  AND latitude IS NULL;

-- 美的国宾府源行的经度被误写入品牌；同一行明确记录品牌为“大华”。
UPDATE ipam_address
SET manufacturer = '大华',
    update_by = 'data_cleanup_20260717',
    update_time = NOW()
WHERE ip_address IN ('2.57.3.90', '2.57.3.91')
  AND site_id = 53
  AND manufacturer = '119.944908';

-- 蓝天湖畔源行的纬度被误写入品牌；源行没有填写品牌。
UPDATE ipam_address
SET manufacturer = NULL,
    update_by = 'data_cleanup_20260717',
    update_time = NOW()
WHERE ip_address = '2.57.32.48'
  AND site_id = 280
  AND manufacturer = '31.674883';

-- 设备编码已正确落在 device_code，清理重复写入的 manufacturer。
UPDATE ipam_address
SET manufacturer = NULL,
    update_by = 'data_cleanup_20260717',
    update_time = NOW()
WHERE ip_address IN (
    '2.57.6.143', '2.57.6.144',
    '2.57.9.110', '2.57.9.111', '2.57.9.112', '2.57.9.113'
  )
  AND NULLIF(device_code, '') IS NOT NULL
  AND manufacturer = device_code;

-- 观棠家园源表表头依次为“设备品牌、接入单位、联系人”。
-- 连续行中的“电信、刘峰、空”整体左移一列，修正为“空、电信、刘峰”。
UPDATE ipam_address
SET manufacturer = NULL,
    access_unit = '电信',
    owner_name = '刘峰',
    update_by = 'data_cleanup_20260717',
    update_time = NOW()
WHERE site_id = 104
  AND area_name = '马杭'
  AND community_name = '观棠家园'
  AND manufacturer = '电信'
  AND access_unit = '刘峰'
  AND NULLIF(owner_name, '') IS NULL;

UPDATE ipam_site
SET access_unit = '电信',
    contact_name = '刘峰',
    update_by = 'data_cleanup_20260717',
    update_time = NOW()
WHERE site_id = 104
  AND area_name = '马杭'
  AND site_name = '观棠家园'
  AND access_unit = '刘峰'
  AND NULLIF(contact_name, '') IS NULL;

INSERT INTO ipam_operation_log(
  action_type, target_type, target_id, summary, detail_content,
  operator_name, operator_ip, create_time
)
VALUES(
  'DATA_QUALITY_CLEANUP', 'MIGRATION_BATCH', 1,
  '2026-07-17历史Excel确定性数据清理',
  JSON_OBJECT(
    'sourceSha256', '66520ed4d2fe4a2cf3cd15290e84ab3c1cae703a3bcf5f4d44dce2addd5a564d',
    'backupPrefix', 'ipam_clean_bak_20260717_203146_',
    'sourceRowsPreserved', 3535,
    'siteMerged', '湖塘/丁龙崎15996312932 -> 湖塘/恒大御景',
    'addressRowsReassigned', 9,
    'mappingRowsReassigned', 8,
    'invalidMappingPortRowsCleared', 466,
    'coordinatesFilled', 2,
    'manufacturerRowsCorrected', 9,
    'shiftedAddressRowsCorrected', 11,
    'uncertainItemsChanged', 0,
    'credentialRowsChanged', 0
  ),
  'data_cleanup_20260717', '127.0.0.1', NOW()
);

COMMIT;

-- 执行后关键验收：address=3763、site=425、mapping=466、source_row=3535、conflict=75。
