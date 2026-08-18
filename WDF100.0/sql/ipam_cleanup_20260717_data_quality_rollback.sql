-- 2026-07-17 IPAM 数据质量清理定向回滚
-- 警告：只适合在本次清理后、尚无新的 IPAM 编辑时执行。
-- 本脚本不会删除备份表，也不会触碰 ipam_migration_source_row。

START TRANSACTION;

INSERT INTO ipam_site
SELECT backup.*
FROM ipam_clean_bak_20260717_203146_site backup
WHERE backup.site_id = 34
  AND NOT EXISTS (SELECT 1 FROM ipam_site current_site WHERE current_site.site_id = backup.site_id);

UPDATE ipam_site current_site
JOIN ipam_clean_bak_20260717_203146_site backup
  ON backup.site_id = current_site.site_id
SET current_site.migration_batch_id = backup.migration_batch_id,
    current_site.area_name = backup.area_name,
    current_site.site_name = backup.site_name,
    current_site.scenario_type = backup.scenario_type,
    current_site.longitude = backup.longitude,
    current_site.latitude = backup.latitude,
    current_site.access_unit = backup.access_unit,
    current_site.contact_name = backup.contact_name,
    current_site.contact_phone = backup.contact_phone,
    current_site.access_control_brand = backup.access_control_brand,
    current_site.barrier_gate_brand = backup.barrier_gate_brand,
    current_site.access_control_mapping_ip = backup.access_control_mapping_ip,
    current_site.barrier_gate_mapping_ip = backup.barrier_gate_mapping_ip,
    current_site.access_status = backup.access_status,
    current_site.source_sheet = backup.source_sheet,
    current_site.source_row = backup.source_row,
    current_site.source_refs_json = backup.source_refs_json,
    current_site.create_by = backup.create_by,
    current_site.create_time = backup.create_time,
    current_site.update_by = backup.update_by,
    current_site.update_time = backup.update_time,
    current_site.remark = backup.remark
WHERE current_site.site_id IN (33, 53, 104, 280);

UPDATE ipam_address current_address
JOIN ipam_clean_bak_20260717_203146_address backup
  ON backup.address_id = current_address.address_id
SET current_address.site_id = backup.site_id,
    current_address.community_name = backup.community_name,
    current_address.manufacturer = backup.manufacturer,
    current_address.access_unit = backup.access_unit,
    current_address.owner_name = backup.owner_name,
    current_address.update_by = backup.update_by,
    current_address.update_time = backup.update_time
WHERE current_address.ip_address IN (
  '2.57.2.114', '2.57.2.115', '2.57.2.116', '2.57.2.117',
  '2.57.2.133', '2.57.2.134', '2.57.2.135', '2.57.2.136', '2.57.2.137',
  '2.57.3.90', '2.57.3.91',
  '2.57.6.143', '2.57.6.144',
  '2.57.9.53', '2.57.9.54', '2.57.9.55', '2.57.9.56', '2.57.9.57',
  '2.57.9.58', '2.57.9.59', '2.57.9.60', '2.57.9.61', '2.57.9.62', '2.57.9.151',
  '2.57.9.110', '2.57.9.111', '2.57.9.112', '2.57.9.113',
  '2.57.32.48'
);

UPDATE ipam_mapping_relation current_mapping
JOIN ipam_clean_bak_20260717_203146_mapping_relation backup
  ON backup.mapping_id = current_mapping.mapping_id
SET current_mapping.site_id = backup.site_id,
    current_mapping.community_name = backup.community_name,
    current_mapping.update_by = backup.update_by,
    current_mapping.update_time = backup.update_time
WHERE backup.site_id = 34;

UPDATE ipam_mapping_relation current_mapping
JOIN ipam_clean_bak_20260717_203146_mapping_relation backup
  ON backup.mapping_id = current_mapping.mapping_id
SET current_mapping.source_port = backup.source_port,
    current_mapping.target_port = backup.target_port,
    current_mapping.update_by = backup.update_by,
    current_mapping.update_time = backup.update_time
WHERE (current_mapping.source_port IS NULL AND NULLIF(backup.source_port, '') IS NOT NULL)
   OR (current_mapping.target_port IS NULL AND NULLIF(backup.target_port, '') IS NOT NULL);

DELETE FROM ipam_operation_log
WHERE action_type = 'DATA_QUALITY_CLEANUP'
  AND operator_name = 'data_cleanup_20260717'
  AND summary = '2026-07-17历史Excel确定性数据清理';

COMMIT;
