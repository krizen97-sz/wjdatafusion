-- 文档管理 V1：目录、在线 Office 文档、文档级 ACL、版本与审计
-- 适用范围：完全内网部署；编辑器由 ONLYOFFICE Docs 私有实例提供。
-- 上线前请先备份数据库，并在测试库执行本脚本及回滚演练。

SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS doc_folder (
  folder_id     BIGINT(20)   NOT NULL AUTO_INCREMENT COMMENT '目录ID',
  parent_id     BIGINT(20)   NOT NULL DEFAULT 0 COMMENT '上级目录ID，0为根目录',
  owner_id      BIGINT(20)   NOT NULL COMMENT '所有者用户ID',
  folder_name   VARCHAR(100) NOT NULL COMMENT '目录名称',
  folder_color  VARCHAR(16)  NOT NULL DEFAULT '#4F7CCF' COMMENT '目录显示颜色',
  sort_order    INT          NOT NULL DEFAULT 0 COMMENT '排序',
  deleted       CHAR(1)      NOT NULL DEFAULT '0' COMMENT '删除标记（0正常 1删除）',
  create_by     VARCHAR(64)  DEFAULT '' COMMENT '创建者',
  create_time   DATETIME     DEFAULT NULL COMMENT '创建时间',
  update_by     VARCHAR(64)  DEFAULT '' COMMENT '更新者',
  update_time   DATETIME     DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (folder_id),
  KEY idx_doc_folder_owner_parent (owner_id, parent_id, deleted),
  KEY idx_doc_folder_parent (parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文档目录';

CREATE TABLE IF NOT EXISTS doc_document (
  document_id       BIGINT(20)    NOT NULL AUTO_INCREMENT COMMENT '文档ID',
  folder_id         BIGINT(20)    NOT NULL DEFAULT 0 COMMENT '所属目录ID，0为根目录',
  owner_id          BIGINT(20)    NOT NULL COMMENT '所有者用户ID',
  title             VARCHAR(160)  NOT NULL COMMENT '文件名（含扩展名）',
  file_type         VARCHAR(16)   NOT NULL COMMENT '文件类型（doc docx xls xlsx）',
  document_type     VARCHAR(16)   NOT NULL COMMENT '编辑器类型（word cell）',
  storage_key       VARCHAR(500)  NOT NULL COMMENT '受控存储相对路径',
  file_size         BIGINT(20)    NOT NULL DEFAULT 0 COMMENT '文件字节数',
  content_version   INT           NOT NULL DEFAULT 1 COMMENT '内容版本号',
  editor_key        VARCHAR(128)  NOT NULL COMMENT 'ONLYOFFICE协同会话键',
  checksum          VARCHAR(64)   NOT NULL DEFAULT '' COMMENT 'SHA-256摘要',
  lifecycle_status  VARCHAR(16)   NOT NULL DEFAULT 'ACTIVE' COMMENT '状态（ACTIVE ARCHIVED TRASH）',
  create_by         VARCHAR(64)   DEFAULT '' COMMENT '创建者',
  create_time       DATETIME      DEFAULT NULL COMMENT '创建时间',
  update_by         VARCHAR(64)   DEFAULT '' COMMENT '更新者',
  update_time       DATETIME      DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (document_id),
  UNIQUE KEY uk_doc_document_editor_key (editor_key),
  KEY idx_doc_document_owner_status (owner_id, lifecycle_status, update_time),
  KEY idx_doc_document_folder_status (folder_id, lifecycle_status, update_time),
  KEY idx_doc_document_type (file_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='在线文档';

CREATE TABLE IF NOT EXISTS doc_acl (
  acl_id        BIGINT(20)   NOT NULL AUTO_INCREMENT COMMENT '权限ID',
  document_id   BIGINT(20)   NOT NULL COMMENT '文档ID',
  user_id       BIGINT(20)   NOT NULL COMMENT '授权用户ID',
  permission    VARCHAR(16)  NOT NULL COMMENT '权限（VIEW EDIT）',
  granted_by    BIGINT(20)   NOT NULL COMMENT '授权人用户ID',
  create_time   DATETIME     DEFAULT NULL COMMENT '创建时间',
  update_time   DATETIME     DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (acl_id),
  UNIQUE KEY uk_doc_acl_document_user (document_id, user_id),
  KEY idx_doc_acl_user (user_id, permission, document_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文档用户权限';

CREATE TABLE IF NOT EXISTS doc_version (
  version_id    BIGINT(20)   NOT NULL AUTO_INCREMENT COMMENT '版本ID',
  document_id   BIGINT(20)   NOT NULL COMMENT '文档ID',
  version_no    INT          NOT NULL COMMENT '版本号',
  storage_key   VARCHAR(500) DEFAULT NULL COMMENT '兼容旧历史快照；轻量版本新记录为空',
  file_size     BIGINT(20)   DEFAULT NULL COMMENT '兼容旧历史快照；轻量版本新记录为空',
  checksum      VARCHAR(64)  DEFAULT NULL COMMENT '兼容旧历史快照；轻量版本新记录为空',
  source_type   VARCHAR(32)  NOT NULL COMMENT '来源（CREATE FORCE_SAVE FINAL_SAVE）',
  creator_id    BIGINT(20)   DEFAULT NULL COMMENT '保存用户ID',
  creator_name  VARCHAR(64)  DEFAULT NULL COMMENT '保存用户名称',
  create_time   DATETIME     DEFAULT NULL COMMENT '创建时间',
  PRIMARY KEY (version_id),
  UNIQUE KEY uk_doc_version_document_no (document_id, version_no),
  KEY idx_doc_version_time (document_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文档轻量修改版本';

CREATE TABLE IF NOT EXISTS doc_operation_log (
  log_id          BIGINT(20)   NOT NULL AUTO_INCREMENT COMMENT '日志ID',
  document_id     BIGINT(20)   DEFAULT NULL COMMENT '文档ID；目录操作可为空',
  action_type     VARCHAR(32)  NOT NULL COMMENT '动作类型',
  operator_id     BIGINT(20)   DEFAULT NULL COMMENT '操作用户ID',
  operator_name   VARCHAR(64)  DEFAULT NULL COMMENT '操作用户名称',
  detail_content  VARCHAR(500) DEFAULT NULL COMMENT '操作摘要',
  create_time     DATETIME     DEFAULT NULL COMMENT '创建时间',
  PRIMARY KEY (log_id),
  KEY idx_doc_log_document_time (document_id, create_time),
  KEY idx_doc_log_operator_recent (operator_id, action_type, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文档操作日志';

DROP PROCEDURE IF EXISTS document_menu_preflight_20260815;
DELIMITER $$
CREATE PROCEDURE document_menu_preflight_20260815()
SQL SECURITY INVOKER
BEGIN
  IF EXISTS (
    SELECT 1 FROM sys_menu
    WHERE menu_id BETWEEN 2500 AND 2506
      AND NOT (menu_id = 2500 AND component = 'document/workspace/index')
      AND NOT (parent_id = 2500 AND perms LIKE 'document:%')
  ) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '菜单ID 2500-2506 已被其他模块占用，请先处理冲突';
  END IF;
END$$
DELIMITER ;

CALL document_menu_preflight_20260815();
DROP PROCEDURE IF EXISTS document_menu_preflight_20260815;

INSERT INTO sys_menu(
  menu_id, menu_name, parent_id, order_num, path, component, `query`, route_name,
  is_frame, is_cache, menu_type, visible, status, perms, icon,
  create_by, create_time, update_by, update_time, remark
)
VALUES
  (2500, '文档管理', 0, 9, 'documents', 'document/workspace/index', '', 'DocumentWorkspace',
   1, 0, 'C', '0', '0', 'document:workspace:list', 'folder-tree',
   'admin', NOW(), 'admin', NOW(), '纯内网在线文档、目录归档与协同权限'),
  (2501, '目录管理', 2500, 1, '#', '', '', '', 1, 0, 'F', '0', '0', 'document:folder:manage', '#', 'admin', NOW(), '', NULL, ''),
  (2502, '新建文档', 2500, 2, '#', '', '', '', 1, 0, 'F', '0', '0', 'document:document:add', '#', 'admin', NOW(), '', NULL, ''),
  (2503, '修改文档', 2500, 3, '#', '', '', '', 1, 0, 'F', '0', '0', 'document:document:edit', '#', 'admin', NOW(), '', NULL, ''),
  (2504, '删除文档', 2500, 4, '#', '', '', '', 1, 0, 'F', '0', '0', 'document:document:remove', '#', 'admin', NOW(), '', NULL, ''),
  (2505, '共享文档', 2500, 5, '#', '', '', '', 1, 0, 'F', '0', '0', 'document:document:share', '#', 'admin', NOW(), '', NULL, ''),
  (2506, '下载文档', 2500, 6, '#', '', '', '', 1, 0, 'F', '0', '0', 'document:document:download', '#', 'admin', NOW(), '', NULL, '')
ON DUPLICATE KEY UPDATE
  menu_name = VALUES(menu_name), parent_id = VALUES(parent_id), order_num = VALUES(order_num),
  path = VALUES(path), component = VALUES(component), `query` = VALUES(`query`),
  route_name = VALUES(route_name), is_frame = VALUES(is_frame), is_cache = VALUES(is_cache),
  menu_type = VALUES(menu_type), visible = VALUES(visible), status = VALUES(status),
  perms = VALUES(perms), icon = VALUES(icon), update_by = 'admin', update_time = NOW(),
  remark = VALUES(remark);

-- 只为内置管理员登记菜单；其他角色必须由管理员按需授权。
INSERT IGNORE INTO sys_role_menu(role_id, menu_id)
SELECT 1, menu_id FROM sys_menu WHERE menu_id BETWEEN 2500 AND 2506;

SELECT menu_id, menu_name, parent_id, menu_type, perms, component
FROM sys_menu WHERE menu_id BETWEEN 2500 AND 2506 ORDER BY menu_id;
