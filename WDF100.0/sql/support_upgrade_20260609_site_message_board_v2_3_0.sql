-- 现场融合管理 v2.3.0 现场留言板
-- 说明：新增现场留言板业务表和留言查看/发布按钮权限，不影响原有现场配置数据。

CREATE TABLE IF NOT EXISTS sup_site_message (
  message_id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '留言ID',
  site_id             BIGINT       NOT NULL COMMENT '现场ID',
  message_content     VARCHAR(300) NOT NULL COMMENT '留言内容',
  publisher_id        BIGINT       DEFAULT NULL COMMENT '发布用户ID',
  publisher_name      VARCHAR(64)  DEFAULT NULL COMMENT '发布用户昵称',
  status              CHAR(1)      DEFAULT '0' COMMENT '状态（0正常 1停用）',
  create_by           VARCHAR(64)  DEFAULT '' COMMENT '创建者',
  create_time         DATETIME     DEFAULT NULL COMMENT '创建时间',
  update_by           VARCHAR(64)  DEFAULT '' COMMENT '更新者',
  update_time         DATETIME     DEFAULT NULL COMMENT '更新时间',
  remark              VARCHAR(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (message_id),
  KEY idx_sup_site_message_site_time (site_id, create_time),
  KEY idx_sup_site_message_publisher (publisher_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='现场留言板';

INSERT INTO sys_menu(menu_id, menu_name, parent_id, order_num, path, component, `query`, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
VALUES
(2292, '留言查看', 2201, 7, '#', '', '', '', 1, 0, 'F', '0', '0', 'support:message:list', '#', 'admin', NOW(), '', NULL, ''),
(2293, '留言发布', 2201, 8, '#', '', '', '', 1, 0, 'F', '0', '0', 'support:message:add', '#', 'admin', NOW(), '', NULL, '')
ON DUPLICATE KEY UPDATE perms=VALUES(perms), menu_name=VALUES(menu_name);
