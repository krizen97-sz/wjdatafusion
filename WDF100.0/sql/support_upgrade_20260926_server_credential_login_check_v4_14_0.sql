-- v4.14.0 服务器凭据与SSH密码登录校验
-- 仅新增默认关闭的登录策略字段和按钮权限；不更改地址、端口或任何凭据。
-- 执行前备份sup_server、sys_menu、sys_role_menu。可重复执行。
SET @has_root_direct_login := (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'sup_server' AND column_name = 'root_direct_login'
);
SET @login_policy_sql := IF(@has_root_direct_login = 0,
  'ALTER TABLE sup_server ADD COLUMN root_direct_login TINYINT(1) NOT NULL DEFAULT 0 COMMENT ''是否root直接登录'' AFTER ssh_port',
  'SELECT ''root_direct_login already exists'' AS migration_status');
PREPARE login_policy_statement FROM @login_policy_sql;
EXECUTE login_policy_statement;
DEALLOCATE PREPARE login_policy_statement;

INSERT INTO sys_menu
(menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type,
 visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
SELECT '服务器登录校验', parent.menu_id, 6, '#', '', '', '', 1, 0, 'F',
 '0', '0', 'support:credential:verify', '#', 'admin', NOW(), '', NULL, '使用登记凭据验证SSH登录，不返回明文密码'
FROM sys_menu parent
WHERE parent.menu_id = 2203
  AND NOT EXISTS (SELECT 1 FROM sys_menu existing WHERE existing.perms = 'support:credential:verify');

-- 不自动扩大普通角色权限。管理员及datafusion沿用原有权限机制；
-- 其他角色按需授予support:credential:verify，显示密码权限保持独立。
-- 回退应用无需删除字段；避免删除升级后用户保存的配置。
