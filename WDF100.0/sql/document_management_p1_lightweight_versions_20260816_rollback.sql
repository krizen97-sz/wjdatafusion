-- 文档管理 P1 版本轻量化回滚保护
-- 一旦产生轻量版本行，对应历史快照文件就不存在，不能只回滚列约束和应用。
-- 此脚本仅在尚未产生轻量版本时允许恢复旧约束；否则必须成对恢复发布前数据库与文档存储备份。

DROP PROCEDURE IF EXISTS rollback_document_lightweight_versions_guard;

DELIMITER $$
CREATE PROCEDURE rollback_document_lightweight_versions_guard()
BEGIN
  IF EXISTS (
    SELECT 1
    FROM doc_version
    WHERE storage_key IS NULL OR file_size IS NULL OR checksum IS NULL
    LIMIT 1
  ) THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = '存在轻量版本记录：请成对恢复发布前数据库和文档存储备份，禁止仅回滚结构';
  END IF;
END$$
DELIMITER ;

CALL rollback_document_lightweight_versions_guard();
DROP PROCEDURE rollback_document_lightweight_versions_guard;

ALTER TABLE doc_version
  MODIFY COLUMN storage_key VARCHAR(500) NOT NULL COMMENT '不可变版本存储相对路径',
  MODIFY COLUMN file_size BIGINT(20) NOT NULL DEFAULT 0 COMMENT '文件字节数',
  MODIFY COLUMN checksum VARCHAR(64) NOT NULL COMMENT 'SHA-256摘要',
  COMMENT = '文档内容版本';
