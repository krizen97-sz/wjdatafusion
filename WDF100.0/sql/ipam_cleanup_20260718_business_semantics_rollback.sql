-- 2026-07-18 IPAM 业务语义整理定向回滚
--
-- 只适用于 ipam_clean_bak_20260718_151401_ 备份创建后、尚无新的 IPAM 业务编辑时。
-- 回滚会恢复本批次修改的字段、4 条被归并的历史关系和 75 条冲突状态，
-- 并删除本批新增的 6 条端口映射及操作日志。不会删除备份表。
-- IPAM 密钥机制移除后，设备密码仅从 login_password 原样恢复。

START TRANSACTION;

UPDATE ipam_address current_address
JOIN ipam_clean_bak_20260718_151401_address backup
  ON backup.address_id = current_address.address_id
SET current_address.status = backup.status,
    current_address.address_role = backup.address_role,
    current_address.target_type = backup.target_type,
    current_address.target_name = backup.target_name,
    current_address.device_code = backup.device_code,
    current_address.manufacturer = backup.manufacturer,
    current_address.internal_ip_address = backup.internal_ip_address,
    current_address.access_unit = backup.access_unit,
    current_address.purpose = backup.purpose,
    current_address.login_username = backup.login_username,
    current_address.login_password = backup.login_password,
    current_address.mapping_address = backup.mapping_address,
    current_address.mapping_port = backup.mapping_port,
    current_address.mapping_description = backup.mapping_description,
    current_address.owner_name = backup.owner_name,
    current_address.owner_phone = backup.owner_phone,
    current_address.update_by = backup.update_by,
    current_address.update_time = backup.update_time,
    current_address.remark = backup.remark
WHERE current_address.update_by = 'data_cleanup_20260718'
   OR current_address.address_id IN (
     113, 114, 115, 116,
     662, 663, 664, 665, 696,
     1248, 1249, 1250, 1251, 1252, 1253, 1254, 1255, 1256, 1257,
     1258, 1259, 1260, 1261, 1262, 1263, 1264, 1265, 1266, 1267, 1268,
     1269, 1270, 1271,
     1390, 1541, 2037, 2038, 2059, 2893, 2894
   );

UPDATE ipam_site current_site
JOIN ipam_clean_bak_20260718_151401_site backup
  ON backup.site_id = current_site.site_id
SET current_site.access_unit = backup.access_unit,
    current_site.contact_name = backup.contact_name,
    current_site.contact_phone = backup.contact_phone,
    current_site.update_by = backup.update_by,
    current_site.update_time = backup.update_time,
    current_site.remark = backup.remark
WHERE current_site.site_id IN (20, 26, 102, 105, 107, 109, 110, 125, 131, 270, 273, 274, 387);

DELETE current_mapping
FROM ipam_mapping_relation current_mapping
LEFT JOIN ipam_clean_bak_20260718_151401_mapping_relation backup
  ON backup.mapping_id = current_mapping.mapping_id
WHERE backup.mapping_id IS NULL
  AND current_mapping.create_by = 'data_cleanup_20260718';

INSERT INTO ipam_mapping_relation
SELECT backup.*
FROM ipam_clean_bak_20260718_151401_mapping_relation backup
LEFT JOIN ipam_mapping_relation current_mapping
  ON current_mapping.mapping_id = backup.mapping_id
WHERE current_mapping.mapping_id IS NULL;

UPDATE ipam_mapping_relation current_mapping
JOIN ipam_clean_bak_20260718_151401_mapping_relation backup
  ON backup.mapping_id = current_mapping.mapping_id
SET current_mapping.migration_batch_id = backup.migration_batch_id,
    current_mapping.relation_key = backup.relation_key,
    current_mapping.site_id = backup.site_id,
    current_mapping.area_name = backup.area_name,
    current_mapping.community_name = backup.community_name,
    current_mapping.source_ip = backup.source_ip,
    current_mapping.source_port = backup.source_port,
    current_mapping.target_ip = backup.target_ip,
    current_mapping.target_port = backup.target_port,
    current_mapping.direction = backup.direction,
    current_mapping.relation_type = backup.relation_type,
    current_mapping.description = backup.description,
    current_mapping.source_sheet = backup.source_sheet,
    current_mapping.source_row = backup.source_row,
    current_mapping.create_by = backup.create_by,
    current_mapping.create_time = backup.create_time,
    current_mapping.update_by = backup.update_by,
    current_mapping.update_time = backup.update_time
WHERE current_mapping.update_by = 'data_cleanup_20260718'
   OR current_mapping.mapping_id IN (14, 15, 50, 51, 52, 53, 54, 55, 56, 58, 60, 62, 69,
     78, 79, 80, 103, 104, 118, 124, 125, 127, 128, 131, 137, 138, 139, 140,
     142, 143, 144, 145, 146, 169, 170, 171, 172, 173, 174, 175, 176, 177,
     178, 179, 180, 181, 182, 183, 184, 185, 186, 187, 188, 189, 190, 191,
     192, 193, 194, 196, 197, 198, 209, 210, 226, 227, 240, 285, 286, 292,
     293, 295, 296, 297, 299, 302, 307, 343, 346, 355, 356, 363, 401, 416,
     425, 426, 437, 438, 452, 460, 461, 462, 463);

UPDATE ipam_migration_conflict current_conflict
JOIN ipam_clean_bak_20260718_151401_conflict backup
  ON backup.conflict_id = current_conflict.conflict_id
SET current_conflict.resolution_status = backup.resolution_status,
    current_conflict.resolution_note = backup.resolution_note;

DELETE FROM ipam_operation_log
WHERE action_type = 'DATA_SEMANTIC_CLEANUP'
  AND operator_name = 'data_cleanup_20260718'
  AND summary = '2026-07-18历史IP重复、反向映射与字段错位整理';

COMMIT;
