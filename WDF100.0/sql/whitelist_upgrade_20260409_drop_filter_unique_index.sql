SET @idx_exists := (
  SELECT COUNT(1)
  FROM information_schema.statistics
  WHERE table_schema = DATABASE()
    AND table_name = 'wl_filter_data'
    AND index_name = 'uk_wl_filter_unique'
);

SET @drop_sql := IF(
  @idx_exists > 0,
  'ALTER TABLE wl_filter_data DROP INDEX uk_wl_filter_unique',
  'SELECT ''uk_wl_filter_unique already removed'' AS message'
);

PREPARE stmt FROM @drop_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
