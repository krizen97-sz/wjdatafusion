-- 文档管理 v3.9.22 回滚：移除目录颜色字段。
-- 回滚顺序：恢复旧前端 -> 恢复旧后端 -> 执行本脚本。
-- 注意：目录颜色配置会被移除；目录顺序、文档、ACL、版本与文件不受影响。

SET NAMES utf8mb4;

DROP PROCEDURE IF EXISTS document_folder_color_rollback_preflight_20260817;
DELIMITER $$
CREATE PROCEDURE document_folder_color_rollback_preflight_20260817()
SQL SECURITY INVOKER
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'doc_folder' AND column_name = 'folder_color'
  ) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'folder_color不存在，无需执行v3.9.22回滚';
  END IF;
END$$
DELIMITER ;

CALL document_folder_color_rollback_preflight_20260817();
DROP PROCEDURE IF EXISTS document_folder_color_rollback_preflight_20260817;

ALTER TABLE doc_folder DROP COLUMN folder_color;

SELECT column_name
FROM information_schema.columns
WHERE table_schema = DATABASE() AND table_name = 'doc_folder' AND column_name = 'folder_color';
