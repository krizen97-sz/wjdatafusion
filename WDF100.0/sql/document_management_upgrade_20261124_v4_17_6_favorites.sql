-- RYNEW v4.17.5 -> v4.17.6 文档个人收藏。选择已运行文档模块的平台库执行。
-- 升级保留全部既有文件、ACL、版本、配额和菜单。回退应用时保留本表即可。
CREATE TABLE IF NOT EXISTS doc_favorite (
  user_id       BIGINT NOT NULL COMMENT '收藏用户ID',
  document_id   BIGINT NOT NULL COMMENT '文档ID',
  create_time   DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '收藏时间',
  PRIMARY KEY (user_id, document_id),
  KEY idx_doc_favorite_user_time (user_id, create_time, document_id),
  KEY idx_doc_favorite_document (document_id),
  CONSTRAINT fk_doc_favorite_document FOREIGN KEY (document_id)
    REFERENCES doc_document (document_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文档个人收藏';
