-- v3.9.4 IP分配管控全局场景设置
-- 说明：
-- 1. 本脚本只创建 ipam_ 独立业务表并同步 ipam_network，不修改 sup_* 现场融合业务表。
-- 2. 使用场景提升为应用级设置，统一作用于全部现有和后续新增网段。

CREATE TABLE IF NOT EXISTS ipam_setting (
  setting_key    VARCHAR(64)  NOT NULL COMMENT '设置键',
  setting_value  VARCHAR(128) NOT NULL COMMENT '设置值',
  setting_name   VARCHAR(100) NOT NULL COMMENT '设置名称',
  create_by      VARCHAR(64)  DEFAULT '' COMMENT '创建者',
  create_time    DATETIME     DEFAULT NULL COMMENT '创建时间',
  update_by      VARCHAR(64)  DEFAULT '' COMMENT '更新者',
  update_time    DATETIME     DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (setting_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='IPAM应用设置';

INSERT INTO ipam_setting
  (setting_key, setting_value, setting_name, create_by, create_time, update_by, update_time)
SELECT
  'SCENARIO_TYPE',
  COALESCE((
    SELECT scenario_type
    FROM ipam_network
    WHERE scenario_type IN ('SOCIAL', 'INTERNAL')
    ORDER BY network_id
    LIMIT 1
  ), 'SOCIAL'),
  'IPAM使用场景',
  'admin',
  NOW(),
  'admin',
  NOW()
FROM DUAL
ON DUPLICATE KEY UPDATE setting_key = VALUES(setting_key);

UPDATE ipam_network
SET scenario_type = (
  SELECT setting_value
  FROM ipam_setting
  WHERE setting_key = 'SCENARIO_TYPE'
)
WHERE scenario_type IS NULL
   OR scenario_type = ''
   OR scenario_type != (
     SELECT setting_value
     FROM ipam_setting
     WHERE setting_key = 'SCENARIO_TYPE'
   );
