-- 2026-07-18 IPAM 历史数据业务语义整理
--
-- 适用源文件：社会面新整理(3.xlsx
-- SHA-256：66520ed4d2fe4a2cf3cd15290e84ab3c1cae703a3bcf5f4d44dce2addd5a564d
--
-- 用户确认口径：
-- 1. 同一 IP 的历史冲突属于重复记录，正式台账只保留一条，全部来源继续归档。
-- 2. 172.18.16.x 是政务网反向映射地址，关系方向统一为 172.18.16.x -> 小区内网 IP。
-- 3. 湖塘!125-130 的 1000-1005 是映射设备端口，554/8000/80 是被映射设备端口。
-- 4. 能由表头、连续行和字段形态唯一确定的错位字段进行重映射；不确定值不猜测。
--
-- 安全执行方式：IpamBusinessCleanupExecutor 先校验哈希、行数和旧值，再创建备份并事务执行。
-- 本 SQL 是同口径的运维审计脚本；直接执行前必须确认所有预检查仍与执行器输出一致。
-- 不修改 sup_* 表，不修改 ipam_migration_source_row，不覆盖 2026-07-17 的第一批备份。

CREATE TABLE ipam_clean_bak_20260718_151401_address LIKE ipam_address;
INSERT INTO ipam_clean_bak_20260718_151401_address SELECT * FROM ipam_address;

CREATE TABLE ipam_clean_bak_20260718_151401_site LIKE ipam_site;
INSERT INTO ipam_clean_bak_20260718_151401_site SELECT * FROM ipam_site;

CREATE TABLE ipam_clean_bak_20260718_151401_mapping_relation LIKE ipam_mapping_relation;
INSERT INTO ipam_clean_bak_20260718_151401_mapping_relation SELECT * FROM ipam_mapping_relation;

CREATE TABLE ipam_clean_bak_20260718_151401_source_row LIKE ipam_migration_source_row;
INSERT INTO ipam_clean_bak_20260718_151401_source_row SELECT * FROM ipam_migration_source_row;

CREATE TABLE ipam_clean_bak_20260718_151401_conflict LIKE ipam_migration_conflict;
INSERT INTO ipam_clean_bak_20260718_151401_conflict SELECT * FROM ipam_migration_conflict;

CREATE TABLE ipam_clean_bak_20260718_151401_operation_log LIKE ipam_operation_log;
INSERT INTO ipam_clean_bak_20260718_151401_operation_log SELECT * FROM ipam_operation_log;

CREATE TABLE ipam_clean_bak_20260718_151401_migration_batch LIKE ipam_migration_batch;
INSERT INTO ipam_clean_bak_20260718_151401_migration_batch SELECT * FROM ipam_migration_batch;

START TRANSACTION;

UPDATE ipam_migration_conflict
SET resolution_status = 'RESOLVED',
    resolution_note = '用户确认：相同IP属于历史重复记录，正式台账仅保留一条；全部来源继续保留在地址来源引用和原始行归档中'
WHERE resolution_status = 'AUTO_SELECTED'
  AND JSON_LENGTH(source_values_json) = 2;

-- 绿地峰云汇同一源行曾被拆成“2.57 -> 172”和“2.57 -> 内网”两条关系。
-- 保留一条并重建为“172 -> 内网”，删除冗余关系。
DELETE FROM ipam_mapping_relation
WHERE mapping_id IN (57, 59, 61, 63)
  AND source_ip IN ('2.57.1.150', '2.57.1.151', '2.57.1.152', '2.57.1.153')
  AND target_ip IN ('192.168.1.251', '192.168.1.252', '192.168.1.253', '192.168.1.254');

UPDATE ipam_mapping_relation mapping
JOIN (
  SELECT mapping_id,
         CASE WHEN source_ip LIKE '172.18.16.%' THEN source_ip ELSE target_ip END AS government_ip,
         CASE
           WHEN source_ip LIKE '172.18.16.%' AND target_ip LIKE '172.18.16.%' THEN NULL
           WHEN source_ip LIKE '172.18.16.%' THEN NULLIF(target_ip, '')
           ELSE NULLIF(source_ip, '')
         END AS internal_ip,
         source_sheet,
         source_row
  FROM ipam_mapping_relation
  WHERE source_ip LIKE '172.18.16.%' OR target_ip LIKE '172.18.16.%'
) normalized ON normalized.mapping_id = mapping.mapping_id
SET mapping.relation_key = CONCAT(
      'GOV_REVERSE|', mapping.mapping_id, '|', normalized.government_ip, '|',
      COALESCE(normalized.internal_ip, 'PENDING')
    ),
    mapping.source_ip = normalized.government_ip,
    mapping.source_port = NULL,
    mapping.target_ip = normalized.internal_ip,
    mapping.target_port = NULL,
    mapping.direction = 'REVERSE',
    mapping.relation_type = 'INTERNAL_MAPPING',
    mapping.description = CASE WHEN normalized.internal_ip IS NULL THEN CONCAT(
      '政务网反向映射地址：', normalized.government_ip,
      '；小区内网IP待补充；来源：', normalized.source_sheet, '!', normalized.source_row
    ) ELSE CONCAT(
      '政务网反向映射：', normalized.government_ip, ' -> ', normalized.internal_ip,
      '；来源：', normalized.source_sheet, '!', normalized.source_row
    ) END,
    mapping.update_by = 'data_cleanup_20260718',
    mapping.update_time = NOW();

INSERT INTO ipam_mapping_relation(
  migration_batch_id, relation_key, site_id, area_name, community_name,
  source_ip, source_port, target_ip, target_port, direction, relation_type,
  description, source_sheet, source_row, create_by, create_time, update_by, update_time
)
VALUES
  (1, 'PORT_MAPPING|湖塘|125|172.18.16.100|1000|554', 40, '湖塘', '常发大厦',
   '172.18.16.100', '1000', NULL, '554', 'REVERSE', 'PORT_MAPPING',
   '政务网映射设备端口：172.18.16.100:1000 -> 小区设备端口554（设备内网IP待补充）；来源：湖塘!125',
   '湖塘', 125, 'data_cleanup_20260718', NOW(), 'data_cleanup_20260718', NOW()),
  (1, 'PORT_MAPPING|湖塘|126|172.18.16.100|1001|8000', 40, '湖塘', '常发大厦',
   '172.18.16.100', '1001', NULL, '8000', 'REVERSE', 'PORT_MAPPING',
   '政务网映射设备端口：172.18.16.100:1001 -> 小区设备端口8000（设备内网IP待补充）；来源：湖塘!126',
   '湖塘', 126, 'data_cleanup_20260718', NOW(), 'data_cleanup_20260718', NOW()),
  (1, 'PORT_MAPPING|湖塘|127|172.18.16.100|1002|80', 40, '湖塘', '常发大厦',
   '172.18.16.100', '1002', NULL, '80', 'REVERSE', 'PORT_MAPPING',
   '政务网映射设备端口：172.18.16.100:1002 -> 小区设备端口80（设备内网IP待补充）；来源：湖塘!127',
   '湖塘', 127, 'data_cleanup_20260718', NOW(), 'data_cleanup_20260718', NOW()),
  (1, 'PORT_MAPPING|湖塘|128|172.18.16.100|1003|554', 41, '湖塘', '武房广场',
   '172.18.16.100', '1003', NULL, '554', 'REVERSE', 'PORT_MAPPING',
   '政务网映射设备端口：172.18.16.100:1003 -> 小区设备端口554（设备内网IP待补充）；来源：湖塘!128',
   '湖塘', 128, 'data_cleanup_20260718', NOW(), 'data_cleanup_20260718', NOW()),
  (1, 'PORT_MAPPING|湖塘|129|172.18.16.100|1004|8000', 41, '湖塘', '武房广场',
   '172.18.16.100', '1004', NULL, '8000', 'REVERSE', 'PORT_MAPPING',
   '政务网映射设备端口：172.18.16.100:1004 -> 小区设备端口8000（设备内网IP待补充）；来源：湖塘!129',
   '湖塘', 129, 'data_cleanup_20260718', NOW(), 'data_cleanup_20260718', NOW()),
  (1, 'PORT_MAPPING|湖塘|130|172.18.16.100|1005|80', 41, '湖塘', '武房广场',
   '172.18.16.100', '1005', NULL, '80', 'REVERSE', 'PORT_MAPPING',
   '政务网映射设备端口：172.18.16.100:1005 -> 小区设备端口80（设备内网IP待补充）；来源：湖塘!130',
   '湖塘', 130, 'data_cleanup_20260718', NOW(), 'data_cleanup_20260718', NOW());

-- 绿地峰云汇：172.18.16.x 是映射地址，192.168.1.x 是小区内网地址。
UPDATE ipam_address
SET address_role = 'DEVICE', target_type = 'MAPPING_DEVICE', target_name = '华龙映射设备-150',
    manufacturer = '华龙', access_unit = NULL, owner_name = NULL,
    mapping_description = '政务网反向映射：172.18.16.162 -> 192.168.1.251',
    update_by = 'data_cleanup_20260718', update_time = NOW()
WHERE address_id = 113;

UPDATE ipam_address address
JOIN ipam_migration_source_row source
  ON source.batch_id = 1 AND source.sheet_name = address.source_sheet
 AND source.source_row_number = address.source_row
SET address.address_role = 'DEVICE',
    address.target_type = CASE address.address_id WHEN 114 THEN 'NVR' ELSE 'CAMERA' END,
    address.target_name = CASE address.address_id
      WHEN 114 THEN 'NVR-151' WHEN 115 THEN '结构化摄像机-152' ELSE '结构化摄像机-153' END,
    address.login_username = NULLIF(JSON_UNQUOTE(JSON_EXTRACT(source.raw_json, '$[3]')), ''),
    address.login_password = NULLIF(JSON_UNQUOTE(JSON_EXTRACT(source.raw_json, '$[4]')), ''),
    address.access_unit = NULL,
    address.owner_name = NULL,
    address.mapping_description = CONCAT(
      '政务网反向映射：', address.mapping_address, ' -> ', address.internal_ip_address
    ),
    address.update_by = 'data_cleanup_20260718', address.update_time = NOW()
WHERE address.address_id IN (114, 115, 116);

UPDATE ipam_site
SET access_unit = NULL, contact_name = NULL,
    remark = CONCAT_WS('；', NULLIF(remark, ''), '172.18.16.x政务网反向映射关系已落入映射台账'),
    update_by = 'data_cleanup_20260718', update_time = NOW()
WHERE site_id = 20;

-- 马杭!10-32 实际列结构：内网IP、用户名、密码、映射尾号、运营商。
UPDATE ipam_address address
JOIN ipam_migration_source_row source
  ON source.batch_id = 1 AND source.sheet_name = address.source_sheet
 AND source.source_row_number = address.source_row
SET address.internal_ip_address = CASE WHEN address.source_row = 10 THEN '192.168.100.204'
      ELSE NULLIF(JSON_UNQUOTE(JSON_EXTRACT(source.raw_json, '$[3]')), '') END,
    address.login_username = NULLIF(JSON_UNQUOTE(JSON_EXTRACT(source.raw_json, '$[4]')), ''),
    address.login_password = NULLIF(JSON_UNQUOTE(JSON_EXTRACT(source.raw_json, '$[5]')), ''),
    address.manufacturer = NULL,
    address.access_unit = NULLIF(JSON_UNQUOTE(JSON_EXTRACT(source.raw_json, '$[7]')), ''),
    address.owner_name = NULL,
    address.remark = CASE WHEN address.source_row = 10 THEN CONCAT_WS(
      '；', NULLIF(address.remark, ''),
      '原表同时记录内网IP 192.168.1.201，实际内网IP为 192.168.100.204'
    ) ELSE address.remark END,
    address.update_by = 'data_cleanup_20260718', address.update_time = NOW()
WHERE address.site_id = 102 AND address.source_sheet = '马杭'
  AND address.source_row BETWEEN 10 AND 32 AND address.address_role = 'DEVICE';

UPDATE ipam_address
SET internal_ip_address = NULL, login_username = NULL, login_password = NULL,
    manufacturer = NULL, access_unit = '电信', owner_name = NULL,
    update_by = 'data_cleanup_20260718', update_time = NOW()
WHERE address_id = 1248;

UPDATE ipam_site
SET access_unit = '电信', contact_name = NULL,
    update_by = 'data_cleanup_20260718', update_time = NOW()
WHERE site_id = 102;

UPDATE ipam_mapping_relation
SET description = CASE mapping_id
      WHEN 170 THEN '历史地址关系：2.57.9.25 -> 192.168.1.201（原登记内网IP）；来源：马杭!10'
      WHEN 171 THEN '历史地址关系：2.57.9.25 -> 192.168.100.204（实际内网IP）；来源：马杭!10'
      ELSE CONCAT(
        '历史地址关系：', COALESCE(source_ip, '待补充'),
        CASE WHEN NULLIF(target_ip, '') IS NULL THEN '' ELSE CONCAT(' -> ', target_ip) END,
        '；来源：', source_sheet, '!', source_row
      ) END,
    update_by = 'data_cleanup_20260718', update_time = NOW()
WHERE site_id = 102 AND source_sheet = '马杭' AND source_row BETWEEN 10 AND 32;

-- 清理已经正确落入专用字段的重复值。
UPDATE ipam_address
SET login_username = NULL, update_by = 'data_cleanup_20260718', update_time = NOW()
WHERE NULLIF(login_username, '') IS NOT NULL AND login_username = internal_ip_address
  AND NOT (site_id = 102 AND source_sheet = '马杭' AND source_row BETWEEN 10 AND 32);

UPDATE ipam_address
SET login_username = NULL, update_by = 'data_cleanup_20260718', update_time = NOW()
WHERE NULLIF(device_code, '') IS NOT NULL AND login_username = device_code;

UPDATE ipam_address
SET access_unit = NULL, update_by = 'data_cleanup_20260718', update_time = NOW()
WHERE NULLIF(device_code, '') IS NOT NULL AND access_unit = device_code;

-- 设备类别/说明误入品牌：保留明确品牌“大华”，其余改入设备类别和名称。
UPDATE ipam_address
SET target_type = CASE
      WHEN LOWER(manufacturer) = 'nvr' THEN 'NVR'
      WHEN manufacturer = '人脸' THEN 'FACE_DEVICE'
      WHEN manufacturer IN ('阵列', '大华阵列', '存储') THEN 'STORAGE_SERVER'
      WHEN manufacturer IN ('录像机', '人脸录像机') THEN 'RECORDER'
      WHEN manufacturer IN ('大华平台', 'isc平台', '服务器、平台', '8700平台级联', '同步') THEN 'PLATFORM'
      WHEN manufacturer IN ('人脸相机', '道路监控') THEN 'CAMERA'
      ELSE 'OTHER' END,
    target_name = CONCAT(
      CASE
        WHEN LOWER(manufacturer) = 'nvr' THEN 'NVR'
        WHEN manufacturer = '人脸' THEN '人脸设备'
        WHEN manufacturer IN ('阵列', '大华阵列') THEN '存储阵列'
        WHEN manufacturer = '存储' THEN '存储设备'
        WHEN manufacturer = '录像机' THEN '录像机'
        WHEN manufacturer = '人脸录像机' THEN '人脸录像机'
        WHEN manufacturer = '大华平台' THEN '平台'
        WHEN manufacturer = 'isc平台' THEN 'ISC平台'
        WHEN manufacturer = '服务器、平台' THEN '平台服务器'
        WHEN manufacturer = '8700平台级联' THEN '8700平台级联'
        WHEN manufacturer = '同步' THEN '同步平台'
        WHEN manufacturer = '人脸相机' THEN '人脸摄像机'
        WHEN manufacturer = '道路监控' THEN '道路监控摄像机'
        ELSE '终端' END,
      '-', SUBSTRING_INDEX(ip_address, '.', -1)
    ),
    purpose = CASE WHEN manufacturer = '同步'
      THEN CONCAT_WS('；', NULLIF(purpose, ''), '设备用途：同步') ELSE purpose END,
    manufacturer = CASE WHEN manufacturer IN ('大华阵列', '大华平台') THEN '大华' ELSE NULL END,
    update_by = 'data_cleanup_20260718', update_time = NOW()
WHERE manufacturer IN (
  'nvr', 'NVR', '人脸', '阵列', '录像机', '大华阵列', '大华平台', '同步',
  '人脸相机', 'isc平台', '道路监控', '终端', '服务器、平台', '存储',
  '人脸录像机', '8700平台级联'
);

-- 设备类别、状态、位置说明误入用户名。
UPDATE ipam_address
SET status = CASE login_username WHEN '未用' THEN 'FREE' WHEN '停用' THEN 'DISABLED' ELSE status END,
    target_type = CASE
      WHEN login_username = '人像' THEN 'FACE_DEVICE'
      WHEN login_username = '服务器' THEN 'PLATFORM'
      WHEN login_username IN (
        '14#楼东侧人行道入口抓拍', '13#楼西侧人行道出口抓拍',
        '汽车坡道入口抓拍', '汽车坡道出口抓拍'
      ) THEN 'CAMERA' ELSE target_type END,
    target_name = CASE
      WHEN login_username = '人像' THEN CONCAT('人脸设备-', SUBSTRING_INDEX(ip_address, '.', -1))
      WHEN login_username = '服务器' THEN '小区平台服务器'
      WHEN login_username IN (
        '14#楼东侧人行道入口抓拍', '13#楼西侧人行道出口抓拍',
        '汽车坡道入口抓拍', '汽车坡道出口抓拍'
      ) THEN login_username ELSE target_name END,
    purpose = CASE login_username
      WHEN '未知谁用' THEN CONCAT_WS('；', NULLIF(purpose, ''), '使用人未知')
      WHEN '未用' THEN CONCAT_WS('；', NULLIF(purpose, ''), '来源标注未用')
      WHEN '停用' THEN CONCAT_WS('；', NULLIF(purpose, ''), '来源标注停用')
      ELSE purpose END,
    login_username = NULL,
    update_by = 'data_cleanup_20260718', update_time = NOW()
WHERE login_username IN (
  '人像', '未知谁用', '未用', '停用', '服务器',
  '14#楼东侧人行道入口抓拍', '13#楼西侧人行道出口抓拍',
  '汽车坡道入口抓拍', '汽车坡道出口抓拍'
);

UPDATE ipam_address
SET owner_name = access_unit, access_unit = NULL,
    update_by = 'data_cleanup_20260718', update_time = NOW()
WHERE access_unit IN ('束远超', '陈云亮', '束渊超') AND NULLIF(owner_name, '') IS NULL;

UPDATE ipam_address
SET purpose = CONCAT_WS('；', NULLIF(purpose, ''), '接入方式：同步'), access_unit = NULL,
    update_by = 'data_cleanup_20260718', update_time = NOW()
WHERE access_unit = '同步';

UPDATE ipam_address
SET owner_name = NULL, update_by = 'data_cleanup_20260718', update_time = NOW()
WHERE address_id IN (662, 663, 664) AND owner_name IN ('大华', '海康');

UPDATE ipam_address
SET manufacturer = owner_name, owner_name = NULL,
    update_by = 'data_cleanup_20260718', update_time = NOW()
WHERE address_id = 665 AND address_role = 'MANAGEMENT' AND owner_name = '海康';

UPDATE ipam_address
SET owner_name = NULL,
    remark = CONCAT_WS('；', NULLIF(remark, ''), '普通联系人字段中的历史凭据已清除，原始值保留在源行归档'),
    update_by = 'data_cleanup_20260718', update_time = NOW()
WHERE address_id = 696;

-- 从原始行归档恢复 5 条可唯一判断的账号/密码列，并清除 2 条管理地址复制的密码。
UPDATE ipam_address address
JOIN ipam_migration_source_row source
  ON source.batch_id = 1 AND source.sheet_name = '前黄' AND source.source_row_number = 29
SET address.login_username = NULLIF(JSON_UNQUOTE(JSON_EXTRACT(source.raw_json, '$[4]')), ''),
    address.login_password = NULLIF(JSON_UNQUOTE(JSON_EXTRACT(source.raw_json, '$[5]')), ''),
    address.manufacturer = '海康', address.target_type = 'BARRIER_GATE',
    address.target_name = '海康道闸平台', address.update_by = 'data_cleanup_20260718', address.update_time = NOW()
WHERE address.address_id = 1390;

UPDATE ipam_address SET manufacturer = NULL, update_by = 'data_cleanup_20260718', update_time = NOW()
WHERE address_id IN (2037, 2893) AND address_role = 'MANAGEMENT';

UPDATE ipam_address address
JOIN ipam_migration_source_row source
  ON source.batch_id = 1 AND source.sheet_name = '嘉泽' AND source.source_row_number = 60
SET address.login_username = NULLIF(JSON_UNQUOTE(JSON_EXTRACT(source.raw_json, '$[4]')), ''),
    address.login_password = NULLIF(JSON_UNQUOTE(JSON_EXTRACT(source.raw_json, '$[5]')), ''),
    address.manufacturer = NULL, address.update_by = 'data_cleanup_20260718', address.update_time = NOW()
WHERE address.address_id = 2038;

UPDATE ipam_address address
JOIN ipam_migration_source_row source
  ON source.batch_id = 1 AND source.sheet_name = '嘉泽' AND source.source_row_number = 81
SET address.login_username = NULLIF(JSON_UNQUOTE(JSON_EXTRACT(source.raw_json, '$[4]')), ''),
    address.login_password = NULLIF(JSON_UNQUOTE(JSON_EXTRACT(source.raw_json, '$[5]')), ''),
    address.manufacturer = NULL, address.target_type = 'PLATFORM', address.target_name = '平台-122',
    address.update_by = 'data_cleanup_20260718', address.update_time = NOW()
WHERE address.address_id = 2059;

UPDATE ipam_address address
JOIN ipam_migration_source_row source
  ON source.batch_id = 1 AND source.sheet_name = '滆湖32段' AND source.source_row_number = 54
SET address.login_username = NULLIF(JSON_UNQUOTE(JSON_EXTRACT(source.raw_json, '$[4]')), ''),
    address.login_password = NULLIF(JSON_UNQUOTE(JSON_EXTRACT(source.raw_json, '$[5]')), ''),
    address.manufacturer = NULL, address.target_type = 'NVR', address.target_name = 'NVR-46',
    address.update_by = 'data_cleanup_20260718', address.update_time = NOW()
WHERE address.address_id = 2894;

UPDATE ipam_address address
JOIN ipam_migration_source_row source
  ON source.batch_id = 1 AND source.sheet_name = '滆湖' AND source.source_row_number = 54
SET address.login_username = NULLIF(JSON_UNQUOTE(JSON_EXTRACT(source.raw_json, '$[13]')), ''),
    address.login_password = NULLIF(JSON_UNQUOTE(JSON_EXTRACT(source.raw_json, '$[14]')), ''),
    address.manufacturer = NULL, address.owner_name = NULL,
    address.target_type = 'PLATFORM', address.target_name = '小区平台服务器',
    address.remark = CONCAT_WS('；', NULLIF(address.remark, ''),
      '同一源行另有相机凭据，归属待确认，原始值仅保留在源行归档'),
    address.update_by = 'data_cleanup_20260718', address.update_time = NOW()
WHERE address.address_id = 1541;

UPDATE ipam_site
SET contact_name = access_unit, access_unit = NULL,
    update_by = 'data_cleanup_20260718', update_time = NOW()
WHERE site_id IN (105, 107, 109, 110)
  AND access_unit IN ('束远超', '陈云亮', '束渊超') AND NULLIF(contact_name, '') IS NULL;

UPDATE ipam_site
SET access_unit = NULL, remark = CONCAT_WS('；', NULLIF(remark, ''), '接入方式：同步'),
    update_by = 'data_cleanup_20260718', update_time = NOW()
WHERE site_id IN (26, 273, 274) AND access_unit = '同步';

UPDATE ipam_site
SET contact_name = NULL, remark = CONCAT_WS('；', NULLIF(remark, ''), '品牌信息已保留在对应地址记录'),
    update_by = 'data_cleanup_20260718', update_time = NOW()
WHERE site_id = 125 AND contact_name = '大华';

UPDATE ipam_site
SET contact_name = NULL,
    remark = CONCAT_WS('；', NULLIF(remark, ''), '普通联系人字段中的历史凭据已清除，原始值保留在源行归档'),
    update_by = 'data_cleanup_20260718', update_time = NOW()
WHERE site_id IN (131, 270) AND NULLIF(contact_name, '') IS NOT NULL;

UPDATE ipam_site
SET access_unit = NULL, remark = CONCAT_WS('；', NULLIF(remark, ''), '设备编码已保留在对应地址记录'),
    update_by = 'data_cleanup_20260718', update_time = NOW()
WHERE site_id = 387 AND access_unit = '32041253031316300001';

INSERT INTO ipam_operation_log(
  action_type, target_type, target_id, summary, detail_content,
  operator_name, operator_ip, create_time
)
VALUES(
  'DATA_SEMANTIC_CLEANUP', 'MIGRATION_BATCH', 1,
  '2026-07-18历史IP重复、反向映射与字段错位整理',
  JSON_OBJECT(
    'sourceSha256', '66520ed4d2fe4a2cf3cd15290e84ab3c1cae703a3bcf5f4d44dce2addd5a564d',
    'backupPrefix', 'ipam_clean_bak_20260718_151401_',
    'sourceRowsPreserved', 3535,
    'duplicateIpConflictsResolved', 75,
    'governmentMappingsNormalized', 67,
    'redundantRelationsRemoved', 4,
    'portMappingsCreated', 6,
    'greenlandRowsCorrected', 4,
    'mahangRowsCorrected', 24,
    'duplicateInternalUsernamesCleared', 33,
    'deviceCodeDuplicatesCleared', 5,
    'manufacturerClassificationsNormalized', 51,
    'usernameSemanticsNormalized', 35,
    'sourceArchiveChanged', 0
  ),
  'data_cleanup_20260718', '127.0.0.1', NOW()
);

COMMIT;

-- 执行后关键行数：site=425、address=3763、mapping=468、source_row=3535、conflict=75、log=17。
