-- v3.8.1 现场融合管理设备位置统一图上配置
-- 说明：
-- 1. 服务器与其他设备统一使用所属机房、机柜编号、起始U位、结束U位描述安装位置。
-- 2. 前端位置配置统一收敛到机房分布图，不再在多个表单位置重复维护。
-- 3. 本脚本只新增可空字段，不修改、不迁移、不清空原有服务器数据。

SET @support_db_name := DATABASE();

SET @has_server_equipment_room := (
  SELECT COUNT(1)
  FROM information_schema.columns
  WHERE table_schema = @support_db_name
    AND table_name = 'sup_server'
    AND column_name = 'equipment_room'
);
SET @sql := IF(
  @has_server_equipment_room = 0,
  'ALTER TABLE sup_server ADD COLUMN equipment_room VARCHAR(100) DEFAULT NULL COMMENT ''所属机房'' AFTER os_type',
  'SELECT ''equipment_room exists'' AS message'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_server_cabinet_no := (
  SELECT COUNT(1)
  FROM information_schema.columns
  WHERE table_schema = @support_db_name
    AND table_name = 'sup_server'
    AND column_name = 'cabinet_no'
);
SET @sql := IF(
  @has_server_cabinet_no = 0,
  'ALTER TABLE sup_server ADD COLUMN cabinet_no VARCHAR(80) DEFAULT NULL COMMENT ''机柜编号'' AFTER equipment_room',
  'SELECT ''cabinet_no exists'' AS message'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_server_rack_u_start := (
  SELECT COUNT(1)
  FROM information_schema.columns
  WHERE table_schema = @support_db_name
    AND table_name = 'sup_server'
    AND column_name = 'rack_u_start'
);
SET @sql := IF(
  @has_server_rack_u_start = 0,
  'ALTER TABLE sup_server ADD COLUMN rack_u_start INT DEFAULT NULL COMMENT ''起始U位'' AFTER cabinet_no',
  'SELECT ''rack_u_start exists'' AS message'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_server_rack_u_end := (
  SELECT COUNT(1)
  FROM information_schema.columns
  WHERE table_schema = @support_db_name
    AND table_name = 'sup_server'
    AND column_name = 'rack_u_end'
);
SET @sql := IF(
  @has_server_rack_u_end = 0,
  'ALTER TABLE sup_server ADD COLUMN rack_u_end INT DEFAULT NULL COMMENT ''结束U位'' AFTER rack_u_start',
  'SELECT ''rack_u_end exists'' AS message'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
