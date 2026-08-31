-- 现场融合管理 v3.17.0：机房三维摆放与设备上联拓扑
-- 适用范围：已部署现场融合管理且已存在 sup_equipment_room / sup_equipment_cabinet 的数据库
-- 数据安全：仅新增兼容字段和新关系表，不删除、不覆盖原有现场、设备、机房、机柜数据

SET NAMES utf8mb4;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sup_equipment_room' AND COLUMN_NAME = 'room_width') = 0,
  'ALTER TABLE sup_equipment_room ADD COLUMN room_width DECIMAL(8,2) DEFAULT 12.00 COMMENT ''机房宽度（米）'' AFTER room_code', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sup_equipment_room' AND COLUMN_NAME = 'room_depth') = 0,
  'ALTER TABLE sup_equipment_room ADD COLUMN room_depth DECIMAL(8,2) DEFAULT 8.00 COMMENT ''机房深度（米）'' AFTER room_width', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sup_equipment_cabinet' AND COLUMN_NAME = 'position_x') = 0,
  'ALTER TABLE sup_equipment_cabinet ADD COLUMN position_x DECIMAL(8,2) DEFAULT NULL COMMENT ''机柜平面X坐标（米）'' AFTER u_capacity', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sup_equipment_cabinet' AND COLUMN_NAME = 'position_z') = 0,
  'ALTER TABLE sup_equipment_cabinet ADD COLUMN position_z DECIMAL(8,2) DEFAULT NULL COMMENT ''机柜平面Z坐标（米）'' AFTER position_x', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sup_equipment_cabinet' AND COLUMN_NAME = 'rotation_y') = 0,
  'ALTER TABLE sup_equipment_cabinet ADD COLUMN rotation_y DECIMAL(6,1) DEFAULT 0.0 COMMENT ''机柜Y轴朝向角度'' AFTER position_z', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS sup_equipment_link (
  link_id             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '设备链路ID',
  site_id             BIGINT       NOT NULL COMMENT '现场ID',
  source_type         VARCHAR(16)  NOT NULL COMMENT '源设备类型（SERVER/HARDWARE）',
  source_id           BIGINT       NOT NULL COMMENT '源设备ID',
  target_type         VARCHAR(16)  NOT NULL DEFAULT 'HARDWARE' COMMENT '目标设备类型',
  target_id           BIGINT       NOT NULL COMMENT '目标交换机资产ID',
  medium_type         VARCHAR(16)  NOT NULL COMMENT '链路介质（OPTICAL/ELECTRICAL）',
  port_count          INT          NOT NULL DEFAULT 1 COMMENT '占用端口数量',
  source_port         VARCHAR(80)  DEFAULT NULL COMMENT '源端口说明',
  target_port         VARCHAR(80)  DEFAULT NULL COMMENT '目标端口说明',
  status              CHAR(1)      DEFAULT '0' COMMENT '状态（0正常 1停用）',
  create_by           VARCHAR(64)  DEFAULT '' COMMENT '创建者',
  create_time         DATETIME     DEFAULT NULL COMMENT '创建时间',
  update_by           VARCHAR(64)  DEFAULT '' COMMENT '更新者',
  update_time         DATETIME     DEFAULT NULL COMMENT '更新时间',
  remark              VARCHAR(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (link_id),
  KEY idx_sup_equipment_link_site (site_id),
  KEY idx_sup_equipment_link_source (source_type, source_id),
  KEY idx_sup_equipment_link_target (target_type, target_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='现场设备物理上联关系';

SELECT
  (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sup_equipment_room' AND COLUMN_NAME IN ('room_width', 'room_depth')) AS room_3d_columns,
  (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sup_equipment_cabinet' AND COLUMN_NAME IN ('position_x', 'position_z', 'rotation_y')) AS cabinet_3d_columns,
  (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sup_equipment_link') AS equipment_link_table;
