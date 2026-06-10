-- 现场融合用户修改记录与 datafusion 总权限

CREATE TABLE IF NOT EXISTS sup_change_log (
  log_id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '记录ID',
  site_id             BIGINT       DEFAULT NULL COMMENT '现场ID',
  action_type         VARCHAR(16)  NOT NULL COMMENT '操作类型（INSERT/UPDATE/DELETE）',
  target_type         VARCHAR(32)  NOT NULL COMMENT '对象类型',
  target_id           BIGINT       DEFAULT NULL COMMENT '对象ID',
  target_name         VARCHAR(200) DEFAULT NULL COMMENT '对象名称',
  summary             VARCHAR(500) DEFAULT NULL COMMENT '操作摘要',
  detail_content      TEXT         DEFAULT NULL COMMENT '操作详情',
  operator_name       VARCHAR(64)  DEFAULT NULL COMMENT '操作用户',
  operator_ip         VARCHAR(64)  DEFAULT NULL COMMENT '操作IP',
  create_time         DATETIME     DEFAULT NULL COMMENT '操作时间',
  PRIMARY KEY (log_id),
  KEY idx_sup_change_log_site_time (site_id, create_time),
  KEY idx_sup_change_log_target (target_type, target_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='现场融合用户修改记录';

INSERT INTO sys_menu(menu_id, menu_name, parent_id, order_num, path, component, `query`, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
VALUES
(2291, '现场融合全部权限', 2200, 99, '#', '', '', '', 1, 0, 'F', '0', '0', 'datafusion', '#', 'admin', NOW(), '', NULL, '')
ON DUPLICATE KEY UPDATE perms=VALUES(perms), menu_name=VALUES(menu_name);
