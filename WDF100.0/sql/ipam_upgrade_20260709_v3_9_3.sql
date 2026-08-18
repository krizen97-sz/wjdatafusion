-- v3.9.3 IP分配管控网段场景配置
-- 说明：
-- 1. 本脚本只修改 ipam_ 独立业务表，不修改 sup_* 现场融合业务表。
-- 2. 增加网段使用场景：SOCIAL 社会面场景、INTERNAL 公安内网场景。

SET @ipam_db_name := DATABASE();

SET @has_network_scenario_type := (
  SELECT COUNT(1)
  FROM information_schema.columns
  WHERE table_schema = @ipam_db_name
    AND table_name = 'ipam_network'
    AND column_name = 'scenario_type'
);
SET @sql := IF(
  @has_network_scenario_type = 0,
  'ALTER TABLE ipam_network ADD COLUMN scenario_type VARCHAR(20) NOT NULL DEFAULT ''SOCIAL'' COMMENT ''使用场景（SOCIAL社会面场景 INTERNAL公安内网场景）'' AFTER prefix_length',
  'SELECT ''scenario_type exists'' AS message'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE ipam_network
SET scenario_type = 'SOCIAL'
WHERE scenario_type IS NULL OR scenario_type = '';
