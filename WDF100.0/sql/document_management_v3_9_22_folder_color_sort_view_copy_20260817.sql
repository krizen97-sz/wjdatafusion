-- 文档管理 v3.9.22：目录颜色、同级拖拽排序、仅查看正文复制
-- 风险边界：只为 doc_folder 新增显示颜色字段；不改写现有目录顺序、文档、ACL、版本或文件。
-- 上线顺序：数据库备份 -> 本脚本 -> 新后端 -> 新前端 -> 目录与VIEW权限验收。

SET NAMES utf8mb4;

DROP PROCEDURE IF EXISTS document_folder_color_preflight_20260817;
DELIMITER $$
CREATE PROCEDURE document_folder_color_preflight_20260817()
SQL SECURITY INVOKER
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.tables
    WHERE table_schema = DATABASE() AND table_name = 'doc_folder'
  ) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '未找到doc_folder，停止升级';
  END IF;

  IF EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'doc_folder' AND column_name = 'folder_color'
  ) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'folder_color已存在，请勿重复执行v3.9.22迁移';
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM sys_role WHERE role_key = 'document' AND status = '0' AND del_flag = '0'
  ) OR NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE menu_id = 2507 AND perms = 'document:file:manage' AND status = '0'
  ) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '现场未完成v3.9.21文档角色基线，停止升级';
  END IF;
END$$
DELIMITER ;

CALL document_folder_color_preflight_20260817();
DROP PROCEDURE IF EXISTS document_folder_color_preflight_20260817;

ALTER TABLE doc_folder
  ADD COLUMN folder_color VARCHAR(16) NOT NULL DEFAULT '#4F7CCF' COMMENT '目录显示颜色' AFTER folder_name;

SELECT COUNT(*) AS active_folder_count,
       SUM(folder_color = '#4F7CCF') AS default_color_count,
       MIN(sort_order) AS min_sort_order,
       MAX(sort_order) AS max_sort_order
FROM doc_folder
WHERE deleted = '0';

SELECT column_name, column_type, is_nullable, column_default
FROM information_schema.columns
WHERE table_schema = DATABASE() AND table_name = 'doc_folder' AND column_name = 'folder_color';
