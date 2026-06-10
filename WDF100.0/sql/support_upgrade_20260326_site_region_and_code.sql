SET @ddl = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE sup_site ADD COLUMN province_code VARCHAR(32) DEFAULT NULL COMMENT ''省级行政区编码'' AFTER site_code',
    'SELECT 1'
  )
  FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'sup_site' AND column_name = 'province_code'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE sup_site ADD COLUMN province_name VARCHAR(64) DEFAULT NULL COMMENT ''省级行政区名称'' AFTER province_code',
    'SELECT 1'
  )
  FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'sup_site' AND column_name = 'province_name'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE sup_site ADD COLUMN city_code VARCHAR(32) DEFAULT NULL COMMENT ''市级行政区编码'' AFTER province_name',
    'SELECT 1'
  )
  FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'sup_site' AND column_name = 'city_code'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE sup_site ADD COLUMN city_name VARCHAR(64) DEFAULT NULL COMMENT ''市级行政区名称'' AFTER city_code',
    'SELECT 1'
  )
  FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'sup_site' AND column_name = 'city_name'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE sup_site ADD COLUMN district_code VARCHAR(32) DEFAULT NULL COMMENT ''区县行政区编码'' AFTER city_name',
    'SELECT 1'
  )
  FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'sup_site' AND column_name = 'district_code'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE sup_site ADD COLUMN district_name VARCHAR(64) DEFAULT NULL COMMENT ''区县行政区名称'' AFTER district_code',
    'SELECT 1'
  )
  FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'sup_site' AND column_name = 'district_name'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
