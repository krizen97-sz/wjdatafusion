-- v3.7.8 现场融合管理设备安装位置可视化配置
-- 说明：
-- 1. 设备资产安装位置从单一文本补充为所属机房、机柜编号、起始U位、结束U位。
-- 2. 新增现场机房、机柜配置表，支持每个现场维护多个机房、每个机房维护多个机柜和U数。
-- 3. 保留原 install_location 字段作为历史数据兼容和展示兜底，不迁移、不清空原有数据。
-- 4. 不影响已有设备资产、平台绑定关系和服务器数据。

SET @support_db_name := DATABASE();

SET @has_equipment_room := (
  SELECT COUNT(1)
  FROM information_schema.columns
  WHERE table_schema = @support_db_name
    AND table_name = 'sup_hardware_asset'
    AND column_name = 'equipment_room'
);
SET @sql := IF(
  @has_equipment_room = 0,
  'ALTER TABLE sup_hardware_asset ADD COLUMN equipment_room VARCHAR(100) DEFAULT NULL COMMENT ''所属机房'' AFTER install_location',
  'SELECT ''equipment_room exists'' AS message'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS sup_equipment_room (
  room_id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '机房ID',
  site_id            BIGINT       NOT NULL COMMENT '现场ID',
  room_name          VARCHAR(120) NOT NULL COMMENT '机房名称',
  room_code          VARCHAR(80)  DEFAULT NULL COMMENT '机房编码',
  status             CHAR(1)      DEFAULT '0' COMMENT '状态（0正常 1停用）',
  create_by          VARCHAR(64)  DEFAULT '' COMMENT '创建者',
  create_time        DATETIME     DEFAULT NULL COMMENT '创建时间',
  update_by          VARCHAR(64)  DEFAULT '' COMMENT '更新者',
  update_time        DATETIME     DEFAULT NULL COMMENT '更新时间',
  remark             VARCHAR(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (room_id),
  KEY idx_sup_equipment_room_site (site_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='现场设备机房';

CREATE TABLE IF NOT EXISTS sup_equipment_cabinet (
  cabinet_id         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '机柜ID',
  room_id            BIGINT       NOT NULL COMMENT '机房ID',
  site_id            BIGINT       NOT NULL COMMENT '现场ID',
  cabinet_no         VARCHAR(80)  NOT NULL COMMENT '机柜编号',
  u_capacity         INT          DEFAULT 45 COMMENT '机柜U数',
  status             CHAR(1)      DEFAULT '0' COMMENT '状态（0正常 1停用）',
  create_by          VARCHAR(64)  DEFAULT '' COMMENT '创建者',
  create_time        DATETIME     DEFAULT NULL COMMENT '创建时间',
  update_by          VARCHAR(64)  DEFAULT '' COMMENT '更新者',
  update_time        DATETIME     DEFAULT NULL COMMENT '更新时间',
  remark             VARCHAR(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (cabinet_id),
  UNIQUE KEY uk_sup_equipment_cabinet_room_no (room_id, cabinet_no),
  KEY idx_sup_equipment_cabinet_site (site_id),
  KEY idx_sup_equipment_cabinet_room (room_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='现场设备机柜';

SET @has_cabinet_no := (
  SELECT COUNT(1)
  FROM information_schema.columns
  WHERE table_schema = @support_db_name
    AND table_name = 'sup_hardware_asset'
    AND column_name = 'cabinet_no'
);
SET @sql := IF(
  @has_cabinet_no = 0,
  'ALTER TABLE sup_hardware_asset ADD COLUMN cabinet_no VARCHAR(80) DEFAULT NULL COMMENT ''机柜编号'' AFTER equipment_room',
  'SELECT ''cabinet_no exists'' AS message'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_rack_u_start := (
  SELECT COUNT(1)
  FROM information_schema.columns
  WHERE table_schema = @support_db_name
    AND table_name = 'sup_hardware_asset'
    AND column_name = 'rack_u_start'
);
SET @sql := IF(
  @has_rack_u_start = 0,
  'ALTER TABLE sup_hardware_asset ADD COLUMN rack_u_start INT DEFAULT NULL COMMENT ''起始U位'' AFTER cabinet_no',
  'SELECT ''rack_u_start exists'' AS message'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_rack_u_end := (
  SELECT COUNT(1)
  FROM information_schema.columns
  WHERE table_schema = @support_db_name
    AND table_name = 'sup_hardware_asset'
    AND column_name = 'rack_u_end'
);
SET @sql := IF(
  @has_rack_u_end = 0,
  'ALTER TABLE sup_hardware_asset ADD COLUMN rack_u_end INT DEFAULT NULL COMMENT ''结束U位'' AFTER rack_u_start',
  'SELECT ''rack_u_end exists'' AS message'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
