CREATE TABLE IF NOT EXISTS sup_platform_contact_rel (
  rel_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '关系ID',
  platform_id BIGINT NOT NULL COMMENT '主平台ID',
  contact_id BIGINT NOT NULL COMMENT '联系人ID',
  create_by VARCHAR(64) DEFAULT '' COMMENT '创建者',
  create_time DATETIME DEFAULT NULL COMMENT '创建时间',
  PRIMARY KEY (rel_id),
  UNIQUE KEY uk_sup_platform_contact (platform_id, contact_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='主平台-联系人关系';
