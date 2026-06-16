-- v3.4.0 服务器多凭据与巡检凭据隔离
-- 说明：
-- 1. 新增服务器多凭据档案表，用于记录同一服务器的运维账号、巡检账号、root账号等多套账号。
-- 2. 自动化巡检 SSH 执行凭据继续保存于 sup_auto_inspection_target，不从本表或 sup_server 自动取密。
-- 3. 本脚本不修改、不迁移 sup_server 历史账号密码，执行前建议先备份数据库。

CREATE TABLE IF NOT EXISTS sup_server_credential (
  credential_id     BIGINT        NOT NULL AUTO_INCREMENT COMMENT '凭据ID',
  server_id         BIGINT        NOT NULL COMMENT '服务器ID',
  credential_name   VARCHAR(120)  NOT NULL COMMENT '凭据名称',
  username          VARCHAR(128)  NOT NULL COMMENT '登录账号',
  password_cipher   VARCHAR(1024) DEFAULT NULL COMMENT '登录密码密文',
  purpose           VARCHAR(120)  DEFAULT NULL COMMENT '用途',
  is_default        CHAR(1)       DEFAULT '0' COMMENT '是否默认（0否 1是）',
  status            CHAR(1)       DEFAULT '0' COMMENT '状态（0正常 1停用）',
  create_by         VARCHAR(64)   DEFAULT '' COMMENT '创建者',
  create_time       DATETIME      DEFAULT NULL COMMENT '创建时间',
  update_by         VARCHAR(64)   DEFAULT '' COMMENT '更新者',
  update_time       DATETIME      DEFAULT NULL COMMENT '更新时间',
  remark            VARCHAR(500)  DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (credential_id),
  KEY idx_sup_server_credential_server (server_id),
  KEY idx_sup_server_credential_status (server_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='服务器多凭据档案';
