-- 现场留言板问题修复升级脚本 v2.3.1
-- 执行日期：2026-06-09
-- 说明：补充留言轮询查询索引；不修改已有业务数据，可重复执行。

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
  KEY idx_sup_site_message_site_status_id (site_id, status, message_id),
  KEY idx_sup_site_message_publisher (publisher_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='现场留言板';

SET @support_db_name := DATABASE();
SET @idx_exists := (
  SELECT COUNT(1)
  FROM information_schema.statistics
  WHERE table_schema = @support_db_name
    AND table_name = 'sup_site_message'
    AND index_name = 'idx_sup_site_message_site_status_id'
);

SET @ddl := IF(
  @idx_exists = 0,
  'ALTER TABLE sup_site_message ADD INDEX idx_sup_site_message_site_status_id (site_id, status, message_id)',
  'SELECT ''idx_sup_site_message_site_status_id exists'' AS message'
);

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
