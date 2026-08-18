-- 文档管理 V1 回滚脚本
-- 警告：以下 DROP TABLE 会永久删除文档元数据、ACL、版本记录和审计日志。
-- 文件存储目录不会自动删除，便于人工恢复；执行前必须完成数据库与文件双份备份。

DELETE FROM sys_role_menu WHERE menu_id BETWEEN 2500 AND 2506;
DELETE FROM sys_menu WHERE menu_id BETWEEN 2500 AND 2506 AND perms LIKE 'document:%';

DROP TABLE IF EXISTS doc_operation_log;
DROP TABLE IF EXISTS doc_version;
DROP TABLE IF EXISTS doc_acl;
DROP TABLE IF EXISTS doc_document;
DROP TABLE IF EXISTS doc_folder;
