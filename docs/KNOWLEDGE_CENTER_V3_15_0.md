# 知识中心 v3.15.0 开发与部署说明

## 1. 冻结目标

知识中心采用阅读优先的三栏工作区，面向平台内部知识沉淀。拥有专属维护权限的用户可以直接创建和修改知识，不设置审核、待复核、已验证、有效期或发布审批流程。

本版本必须满足：

- 完全离线部署，复用现有 Vue 3、Element Plus、Quill、Spring Boot 和 MySQL。
- 不新增独立服务、端口、对象存储、全文检索服务、AI 问答或公网资源。
- 每次显式保存都生成不可覆盖的新版本，记录操作人、修改说明、变化字段、完整内容关系快照和校验摘要。
- 知识只有 `ACTIVE`、`ARCHIVED`、`TRASH` 三种生命周期状态。
- 附件只关联现有文档管理的 `document_id`，不复制文件、存储键、历史版本或 ACL。

## 2. 权限模型

| 权限字符 | 能力 |
| --- | --- |
| `knowledge:page:list` | 查看空间、目录、知识正文、搜索和修改记录 |
| `knowledge:page:write` | 创建、编辑、归档知识及将历史内容恢复为新版本 |
| `knowledge:space:manage` | 创建和编辑空间，创建、编辑和删除空目录 |
| `knowledge:page:remove` | 查看回收站、移入回收站和恢复知识 |
| `document:file:manage` | 进入附件选择器，并按文档管理规则打开、预览或下载文档 |

知识权限不扩大文档权限。用户能看到知识正文，不代表能看到其附件元数据；附件每次读取均通过现有文档服务重新判断所有者、共享 ACL、有效期和生命周期状态。

## 3. 数据边界

本版本新增六张独立表：

- `kb_space`：知识空间。
- `kb_page`：目录和文章当前版本。
- `kb_tag`、`kb_page_tag`：标签及文章标签关系。
- `kb_page_document`：只保存文章、现有文档 ID 和展示顺序。
- `kb_page_version`：不可覆盖的完整版本快照。

不修改 `doc_*` 表，不复制 `doc_acl`，不读取或写入文档存储路径。文档被归档、移入回收站、删除共享或权限到期后，知识附件区在下一次读取时立即反映当前状态。

## 4. 关键接口

- `GET /knowledge/spaces`
- `GET /knowledge/pages/tree`
- `GET /knowledge/pages/search`
- `GET /knowledge/pages/{pageId}`
- `POST|PUT /knowledge/pages`
- `PUT /knowledge/pages/{pageId}/archive|trash|restore`
- `GET /knowledge/pages/{pageId}/versions`
- `POST /knowledge/pages/{pageId}/versions/{versionNo}/restore`
- `GET /knowledge/document-candidates`

写接口使用 `expectedVersion` 进行乐观并发校验。历史版本恢复只恢复知识内容和关系并生成新版本，不隐式改变当前生命周期；回收站知识必须先恢复后才能编辑。

## 5. 部署步骤

1. 备份当前数据库，并记录前后端运行版本和 Git 提交。
2. 执行 `WDF100.0/sql/knowledge_center_v3_15_0_20260826.sql`。脚本会先确认 `doc_document` 存在并检查菜单 ID 冲突。
3. 发布同一提交构建出的后端包和前端包。
4. 在角色管理中按职责授予知识查看、维护、空间管理和移除权限；附件维护人还需拥有现有 `document:file:manage`。
5. 依次验收空间目录、知识创建、第二次保存、版本恢复、搜索、归档、回收站以及本人/共享/无权/归档/回收站文档附件。

## 6. 回滚

执行 `WDF100.0/sql/knowledge_center_v3_15_0_20260826_rollback.sql` 可移除知识中心菜单与角色菜单关系，再切回旧前后端。回滚脚本不会删除 `kb_*` 表和任何知识版本，避免业务数据不可恢复；确认不再需要数据后才可另行人工清理。

## 7. 验收标准

- 无维护权限的用户只能阅读，界面不出现创建和编辑入口。
- 有 `knowledge:page:write` 的用户无需审核即可创建和修改，连续保存版本号连续递增。
- 两个用户基于同一版本编辑时，后保存者收到版本冲突提示且内容不被覆盖。
- 回收站内容无法通过普通编辑或历史版本恢复绕过恢复权限。
- 无文档权限时不返回附件标题等元数据；文档权限变化后无需修改知识即可实时生效。
- 前端生产构建和知识专项测试通过，后端专项测试和模块构建通过，升级脚本可重复执行。
