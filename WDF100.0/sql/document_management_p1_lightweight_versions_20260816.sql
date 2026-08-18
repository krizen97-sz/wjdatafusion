-- 文档管理 P1：版本轻量化
-- 目标：后续版本仅记录版本号、修改人、来源与时间；完整文件只保留当前文档。
-- 安全边界：本脚本不删除既有版本行和历史文件，存量清理由独立清单确认后执行。

ALTER TABLE doc_version
  MODIFY COLUMN storage_key VARCHAR(500) NULL DEFAULT NULL
    COMMENT '兼容旧历史快照；轻量版本新记录为空',
  MODIFY COLUMN file_size BIGINT(20) NULL DEFAULT NULL
    COMMENT '兼容旧历史快照；轻量版本新记录为空',
  MODIFY COLUMN checksum VARCHAR(64) NULL DEFAULT NULL
    COMMENT '兼容旧历史快照；轻量版本新记录为空',
  COMMENT = '文档轻量修改版本';

-- 验证：三列均应显示 YES；已有行仍保留原值。
SELECT column_name, is_nullable
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name = 'doc_version'
  AND column_name IN ('storage_key', 'file_size', 'checksum')
ORDER BY ordinal_position;

SELECT COUNT(*) AS existing_version_rows,
       SUM(storage_key IS NULL AND file_size IS NULL AND checksum IS NULL) AS lightweight_version_rows
FROM doc_version;
