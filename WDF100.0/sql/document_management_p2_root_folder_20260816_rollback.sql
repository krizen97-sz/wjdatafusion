-- 文档管理 P2 数据迁移回滚。
-- 必须与 P2 应用回滚同时执行；当前 P2 应用禁止在根目录创建或移动文档。
-- 只回退仍位于迁移目标目录的历史文档，不覆盖上线后用户主动移动的文档。

SET NAMES utf8mb4;

UPDATE doc_document d
JOIN doc_root_file_migration_20260816 m ON m.document_id = d.document_id
SET d.folder_id = 0,
    d.update_by = 'document-p2-rollback',
    d.update_time = NOW()
WHERE d.folder_id = m.target_folder_id;

UPDATE doc_folder f
SET f.deleted = '1',
    f.update_by = 'document-p2-rollback',
    f.update_time = NOW()
WHERE f.create_by = 'document-p2-migration'
  AND NOT EXISTS (SELECT 1 FROM doc_document d WHERE d.folder_id = f.folder_id)
  AND NOT EXISTS (SELECT 1 FROM doc_folder c WHERE c.parent_id = f.folder_id AND c.deleted = '0');

DROP TABLE IF EXISTS doc_root_file_migration_20260816;

SELECT COUNT(1) AS restored_root_documents
FROM doc_document
WHERE folder_id = 0;
