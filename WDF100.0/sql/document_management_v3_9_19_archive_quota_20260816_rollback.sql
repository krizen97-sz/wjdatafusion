-- 文档管理 v3.9.19 回滚：恢复旧入口权限并移除用户容量策略。
-- 执行前应备份 doc_user_quota；本脚本不会删除现有文档、ACL、版本或文件内容。

SET NAMES utf8mb4;

UPDATE sys_menu
SET perms = 'document:workspace:list',
    remark = '纯内网在线文档、目录归档与协同权限',
    update_by = 'admin', update_time = NOW()
WHERE menu_id = 2500 AND component = 'document/workspace/index'
  AND perms = 'document:workspace:access';

DROP TABLE IF EXISTS doc_user_quota;

SELECT menu_id, menu_name, perms, component
FROM sys_menu WHERE menu_id = 2500;
