-- v3.9.8 IPAM派出所树状网段分类
-- 说明：
-- 1. 本脚本只修改 ipam_ 独立业务表，不修改或关联 sup_* 现场融合业务表。
-- 2. 网段使用明确的所属派出所字段；历史数据仅在来源区域唯一时自动归类，避免把混合区域误分到某个派出所。

SET @ipam_db_name := DATABASE();

SET @has_police_station_name := (
  SELECT COUNT(1)
  FROM information_schema.columns
  WHERE table_schema = @ipam_db_name
    AND table_name = 'ipam_network'
    AND column_name = 'police_station_name'
);
SET @sql := IF(
  @has_police_station_name = 0,
  'ALTER TABLE ipam_network ADD COLUMN police_station_name VARCHAR(80) NOT NULL DEFAULT '''' COMMENT ''所属派出所'' AFTER network_name',
  'SELECT ''ipam_network.police_station_name exists'' AS message'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 网段名称已经明确包含“派出所”时，可安全提取完整名称。
UPDATE ipam_network
SET police_station_name = TRIM(LEFT(network_name, LOCATE('派出所', network_name) + CHAR_LENGTH('派出所') - 1))
WHERE NULLIF(TRIM(police_station_name), '') IS NULL
  AND LOCATE('派出所', network_name) > 0;

-- 历史迁移数据只有一个非空来源区域时，才将该区域作为派出所归属。
SET @has_address_area_name := (
  SELECT COUNT(1)
  FROM information_schema.columns
  WHERE table_schema = @ipam_db_name
    AND table_name = 'ipam_address'
    AND column_name = 'area_name'
);
SET @sql := IF(
  @has_address_area_name = 1,
  'UPDATE ipam_network n
     JOIN (
       SELECT network_id, MAX(TRIM(area_name)) AS station_name
       FROM ipam_address
       WHERE NULLIF(TRIM(area_name), '''') IS NOT NULL
       GROUP BY network_id
       HAVING COUNT(DISTINCT TRIM(area_name)) = 1
     ) source_area ON source_area.network_id = n.network_id
     SET n.police_station_name = source_area.station_name
   WHERE NULLIF(TRIM(n.police_station_name), '''') IS NULL',
  'SELECT ''ipam_address.area_name not found; skip station backfill'' AS message'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_police_station_index := (
  SELECT COUNT(1)
  FROM information_schema.statistics
  WHERE table_schema = @ipam_db_name
    AND table_name = 'ipam_network'
    AND index_name = 'idx_ipam_network_police_station'
);
SET @sql := IF(
  @has_police_station_index = 0,
  'ALTER TABLE ipam_network ADD INDEX idx_ipam_network_police_station (police_station_name)',
  'SELECT ''idx_ipam_network_police_station exists'' AS message'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
