-- 知识中心 v3.15.0 安全回滚：仅撤销菜单与角色入口，保留全部知识和版本数据。
-- 不删除 kb_* 表，不修改 doc_* 表；切回旧前后端后数据保持可恢复。

SET NAMES utf8mb4;

DELETE role_menu
FROM sys_role_menu role_menu
JOIN sys_menu menu ON menu.menu_id = role_menu.menu_id
WHERE menu.menu_id BETWEEN 2510 AND 2513
  AND (menu.component = 'knowledge/workspace/index' OR menu.perms LIKE 'knowledge:%');
DELETE FROM sys_menu
WHERE menu_id BETWEEN 2510 AND 2513
  AND (component = 'knowledge/workspace/index' OR perms LIKE 'knowledge:%');

SELECT table_name, table_rows
FROM information_schema.tables
WHERE table_schema = DATABASE() AND table_name LIKE 'kb_%'
ORDER BY table_name;
