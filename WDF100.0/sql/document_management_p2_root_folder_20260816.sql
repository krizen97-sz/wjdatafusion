-- 文档管理 P2：根目录仅承载目录，历史根文档迁移到“未归档文档”目录。
-- 执行前必须成套备份数据库与文档存储；脚本可重复执行。

SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS doc_root_file_migration_20260816 (
  document_id      BIGINT(20) NOT NULL COMMENT '迁移文档ID',
  owner_id         BIGINT(20) NOT NULL COMMENT '文档所有者',
  target_folder_id BIGINT(20) NOT NULL COMMENT '迁移目标目录',
  migrated_at      DATETIME   NOT NULL COMMENT '迁移时间',
  PRIMARY KEY (document_id),
  KEY idx_doc_root_migration_owner (owner_id, target_folder_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='P2历史根文档迁移回滚账本';

INSERT INTO doc_folder(parent_id, owner_id, folder_name, sort_order, deleted, create_by, create_time)
SELECT 0, root_docs.owner_id, '未归档文档', -100, '0', 'document-p2-migration', NOW()
FROM (
  SELECT DISTINCT owner_id
  FROM doc_document
  WHERE folder_id = 0
) root_docs
WHERE NOT EXISTS (
  SELECT 1
  FROM doc_folder f
  WHERE f.owner_id = root_docs.owner_id
    AND f.parent_id = 0
    AND f.folder_name = '未归档文档'
    AND f.deleted = '0'
);

INSERT IGNORE INTO doc_root_file_migration_20260816(document_id, owner_id, target_folder_id, migrated_at)
SELECT d.document_id, d.owner_id, min(f.folder_id), NOW()
FROM doc_document d
JOIN doc_folder f
  ON f.owner_id = d.owner_id
 AND f.parent_id = 0
 AND f.folder_name = '未归档文档'
 AND f.deleted = '0'
WHERE d.folder_id = 0
GROUP BY d.document_id, d.owner_id;

UPDATE doc_document d
JOIN doc_root_file_migration_20260816 m ON m.document_id = d.document_id
SET d.folder_id = m.target_folder_id,
    d.update_by = 'document-p2-migration',
    d.update_time = NOW()
WHERE d.folder_id = 0;

SELECT COUNT(1) AS remaining_root_documents
FROM doc_document
WHERE folder_id = 0;

SELECT owner_id, COUNT(1) AS file_count, ROUND(SUM(file_size) / 1024 / 1024, 2) AS size_mb
FROM doc_document
WHERE lifecycle_status != 'TRASH'
GROUP BY owner_id
ORDER BY owner_id;
