-- 现场融合管理 v2.4.2 datafusion 权限字符绑定修复
-- 执行日期：2026-06-10
-- 说明：datafusion 是 sys_menu.perms 权限字符；本脚本将其绑定给 role_key='datafusion' 的角色。
--      不新增业务表、不修改业务数据，可重复执行。

INSERT INTO sys_role_menu(role_id, menu_id)
SELECT r.role_id, m.menu_id
FROM sys_role r
         INNER JOIN sys_menu m ON m.perms = 'datafusion'
WHERE r.role_key = 'datafusion'
  AND NOT EXISTS (
    SELECT 1
    FROM sys_role_menu rm
    WHERE rm.role_id = r.role_id
      AND rm.menu_id = m.menu_id
  );
