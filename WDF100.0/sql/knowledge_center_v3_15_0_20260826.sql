-- 知识中心 v3.15.0：离线轻量知识文章、不可覆盖版本与现有文档关联
-- 边界：不修改 doc_* 表，不复制文档文件或 ACL；仅保存 document_id 并在运行时复用文档服务权限。
-- 上线顺序：数据库备份 -> 本脚本 -> 新后端 -> 新前端 -> 权限与文档联动验收。

SET NAMES utf8mb4;

DROP PROCEDURE IF EXISTS knowledge_center_preflight_20260826;
DELIMITER $$
CREATE PROCEDURE knowledge_center_preflight_20260826()
SQL SECURITY INVOKER
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.tables
    WHERE table_schema = DATABASE() AND table_name = 'doc_document'
  ) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '未找到现有文档管理表 doc_document，停止知识中心升级';
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM sys_role WHERE role_id = 1 AND del_flag = '0'
  ) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '未找到平台admin角色（role_id=1），停止知识中心菜单授权';
  END IF;

  IF EXISTS (
    SELECT 1 FROM sys_menu
    WHERE menu_id BETWEEN 2510 AND 2513
      AND NOT (menu_id = 2510 AND component = 'knowledge/workspace/index')
      AND NOT (parent_id = 2510 AND perms LIKE 'knowledge:%')
  ) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '菜单ID 2510-2513 已被其他模块占用，请先处理冲突';
  END IF;
END$$
DELIMITER ;

CALL knowledge_center_preflight_20260826();
DROP PROCEDURE IF EXISTS knowledge_center_preflight_20260826;

CREATE TABLE IF NOT EXISTS kb_space (
  space_id      BIGINT(20)    NOT NULL AUTO_INCREMENT COMMENT '知识空间ID',
  space_name    VARCHAR(100)  NOT NULL COMMENT '知识空间名称',
  description   VARCHAR(500)  NOT NULL DEFAULT '' COMMENT '知识空间说明',
  sort_order    INT           NOT NULL DEFAULT 0 COMMENT '排序',
  status        CHAR(1)       NOT NULL DEFAULT '0' COMMENT '状态（0正常 1停用）',
  create_by     VARCHAR(64)   DEFAULT '' COMMENT '创建者',
  create_time   DATETIME      DEFAULT NULL COMMENT '创建时间',
  update_by     VARCHAR(64)   DEFAULT '' COMMENT '更新者',
  update_time   DATETIME      DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (space_id),
  UNIQUE KEY uk_kb_space_name (space_name),
  KEY idx_kb_space_status_sort (status, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识空间';

CREATE TABLE IF NOT EXISTS kb_page (
  page_id           BIGINT(20)    NOT NULL AUTO_INCREMENT COMMENT '知识节点ID',
  space_id          BIGINT(20)    NOT NULL COMMENT '知识空间ID',
  parent_id         BIGINT(20)    NOT NULL DEFAULT 0 COMMENT '上级知识目录ID，0为根目录',
  page_type         VARCHAR(16)   NOT NULL COMMENT '节点类型（FOLDER ARTICLE）',
  title             VARCHAR(160)  NOT NULL COMMENT '目录或知识标题',
  summary           VARCHAR(500)  NOT NULL DEFAULT '' COMMENT '知识摘要',
  content           MEDIUMTEXT    DEFAULT NULL COMMENT '已清洗的知识正文HTML',
  sort_order        INT           NOT NULL DEFAULT 0 COMMENT '同级排序',
  content_version   INT           NOT NULL DEFAULT 0 COMMENT '当前内容版本号，目录为0',
  lifecycle_status  VARCHAR(16)   NOT NULL DEFAULT 'ACTIVE' COMMENT '状态（ACTIVE ARCHIVED TRASH）',
  create_user_id    BIGINT(20)    DEFAULT NULL COMMENT '创建用户ID',
  update_user_id    BIGINT(20)    DEFAULT NULL COMMENT '最后修改用户ID',
  create_by         VARCHAR(64)   DEFAULT '' COMMENT '创建者',
  create_time       DATETIME      DEFAULT NULL COMMENT '创建时间',
  update_by         VARCHAR(64)   DEFAULT '' COMMENT '更新者',
  update_time       DATETIME      DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (page_id),
  KEY idx_kb_page_tree (space_id, parent_id, lifecycle_status, sort_order),
  KEY idx_kb_page_type_status (page_type, lifecycle_status, update_time),
  KEY idx_kb_page_update_user (update_user_id, update_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识目录与文章当前版本';

CREATE TABLE IF NOT EXISTS kb_tag (
  tag_id       BIGINT(20)   NOT NULL AUTO_INCREMENT COMMENT '标签ID',
  tag_name     VARCHAR(40)  NOT NULL COMMENT '标签名称',
  create_by    VARCHAR(64)  DEFAULT '' COMMENT '创建者',
  create_time  DATETIME     DEFAULT NULL COMMENT '创建时间',
  PRIMARY KEY (tag_id),
  UNIQUE KEY uk_kb_tag_name (tag_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识标签';

CREATE TABLE IF NOT EXISTS kb_page_tag (
  page_id  BIGINT(20) NOT NULL COMMENT '知识文章ID',
  tag_id   BIGINT(20) NOT NULL COMMENT '标签ID',
  PRIMARY KEY (page_id, tag_id),
  KEY idx_kb_page_tag_tag (tag_id, page_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识文章标签关系';

CREATE TABLE IF NOT EXISTS kb_page_document (
  link_id       BIGINT(20)   NOT NULL AUTO_INCREMENT COMMENT '关联ID',
  page_id       BIGINT(20)   NOT NULL COMMENT '知识文章ID',
  document_id   BIGINT(20)   NOT NULL COMMENT '现有文档管理文档ID',
  sort_order    INT          NOT NULL DEFAULT 0 COMMENT '展示顺序',
  create_by     VARCHAR(64)  DEFAULT '' COMMENT '关联人',
  create_time   DATETIME     DEFAULT NULL COMMENT '关联时间',
  PRIMARY KEY (link_id),
  UNIQUE KEY uk_kb_page_document (page_id, document_id),
  KEY idx_kb_document_page (document_id, page_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识文章与现有文档关联';

CREATE TABLE IF NOT EXISTS kb_page_version (
  version_id                 BIGINT(20)    NOT NULL AUTO_INCREMENT COMMENT '版本记录ID',
  page_id                    BIGINT(20)    NOT NULL COMMENT '知识文章ID',
  version_no                 INT           NOT NULL COMMENT '版本号',
  snapshot_space_id          BIGINT(20)    NOT NULL COMMENT '版本知识空间ID',
  snapshot_parent_id         BIGINT(20)    NOT NULL DEFAULT 0 COMMENT '版本上级目录ID',
  snapshot_title             VARCHAR(160)  NOT NULL COMMENT '版本标题',
  snapshot_summary           VARCHAR(500)  NOT NULL DEFAULT '' COMMENT '版本摘要',
  snapshot_content           MEDIUMTEXT    NOT NULL COMMENT '版本正文HTML',
  snapshot_lifecycle_status  VARCHAR(16)   NOT NULL COMMENT '版本状态',
  snapshot_tags              TEXT          NOT NULL COMMENT '版本标签JSON',
  snapshot_document_ids      TEXT          NOT NULL COMMENT '版本关联文档ID JSON',
  operation_type             VARCHAR(32)   NOT NULL COMMENT '操作类型',
  change_fields              VARCHAR(500)  NOT NULL DEFAULT '' COMMENT '变化字段',
  change_note                VARCHAR(500)  NOT NULL DEFAULT '' COMMENT '修改说明',
  content_checksum           VARCHAR(64)   NOT NULL COMMENT '版本SHA-256摘要',
  operator_id                BIGINT(20)    DEFAULT NULL COMMENT '修改用户ID',
  operator_name              VARCHAR(64)   NOT NULL DEFAULT '' COMMENT '修改用户名称',
  restored_from_version      INT           DEFAULT NULL COMMENT '恢复来源版本号',
  create_time                DATETIME      DEFAULT NULL COMMENT '版本时间',
  PRIMARY KEY (version_id),
  UNIQUE KEY uk_kb_page_version (page_id, version_no),
  KEY idx_kb_page_version_time (page_id, create_time),
  KEY idx_kb_version_operator (operator_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识文章不可覆盖版本记录';

INSERT INTO kb_space(space_name, description, sort_order, status, create_by, create_time, update_by, update_time)
SELECT '运维操作手册', '沉淀平台运维、巡检和故障处理知识', 10, '0', 'admin', NOW(), 'admin', NOW()
WHERE NOT EXISTS (SELECT 1 FROM kb_space WHERE space_name = '运维操作手册');

SET @kb_default_space_id = (
  SELECT space_id FROM kb_space WHERE space_name = '运维操作手册' ORDER BY space_id LIMIT 1
);

INSERT INTO kb_page(space_id, parent_id, page_type, title, summary, content, sort_order,
                    content_version, lifecycle_status, create_user_id, update_user_id,
                    create_by, create_time, update_by, update_time)
SELECT @kb_default_space_id, 0, 'FOLDER', seed.title, '', NULL, seed.sort_order,
       0, 'ACTIVE', 1, 1, 'admin', NOW(), 'admin', NOW()
FROM (
  SELECT '日常运维' AS title, 10 AS sort_order
  UNION ALL SELECT '故障处理', 20
  UNION ALL SELECT '数据库', 30
  UNION ALL SELECT '文档服务', 40
  UNION ALL SELECT '网络与安全', 50
) seed
WHERE @kb_default_space_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM kb_page existing
    WHERE existing.space_id = @kb_default_space_id AND existing.parent_id = 0
      AND existing.page_type = 'FOLDER' AND existing.title = seed.title
      AND existing.lifecycle_status = 'ACTIVE'
  );

INSERT INTO sys_menu(
  menu_id, menu_name, parent_id, order_num, path, component, `query`, route_name,
  is_frame, is_cache, menu_type, visible, status, perms, icon,
  create_by, create_time, update_by, update_time, remark
)
VALUES
  (2510, '知识中心', 0, 10, 'knowledge', 'knowledge/workspace/index', '', 'KnowledgeWorkspace',
   1, 0, 'C', '0', '0', 'knowledge:page:list', 'documentation',
   'admin', NOW(), 'admin', NOW(), '完全离线的知识阅读、编辑、版本和现有文档联动'),
  (2511, '维护知识', 2510, 1, '#', '', '', '', 1, 0, 'F', '0', '0', 'knowledge:page:write', '#',
   'admin', NOW(), 'admin', NOW(), '允许创建和修改知识，每次保存产生版本记录'),
  (2512, '管理知识空间', 2510, 2, '#', '', '', '', 1, 0, 'F', '0', '0', 'knowledge:space:manage', '#',
   'admin', NOW(), 'admin', NOW(), '管理知识空间和目录'),
  (2513, '移除与恢复知识', 2510, 3, '#', '', '', '', 1, 0, 'F', '0', '0', 'knowledge:page:remove', '#',
   'admin', NOW(), 'admin', NOW(), '移入回收站并恢复知识')
ON DUPLICATE KEY UPDATE
  menu_name = VALUES(menu_name), parent_id = VALUES(parent_id), order_num = VALUES(order_num),
  path = VALUES(path), component = VALUES(component), `query` = VALUES(`query`),
  route_name = VALUES(route_name), is_frame = VALUES(is_frame), is_cache = VALUES(is_cache),
  menu_type = VALUES(menu_type), visible = VALUES(visible), status = VALUES(status),
  perms = VALUES(perms), icon = VALUES(icon), update_by = 'admin', update_time = NOW(),
  remark = VALUES(remark);

INSERT IGNORE INTO sys_role_menu(role_id, menu_id)
SELECT 1, menu_id FROM sys_menu WHERE menu_id BETWEEN 2510 AND 2513;

SELECT table_name
FROM information_schema.tables
WHERE table_schema = DATABASE() AND table_name LIKE 'kb_%'
ORDER BY table_name;

SELECT menu_id, menu_name, parent_id, menu_type, perms, component
FROM sys_menu WHERE menu_id BETWEEN 2510 AND 2513 ORDER BY menu_id;
