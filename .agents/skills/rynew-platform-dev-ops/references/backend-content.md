# 文档管理与知识中心

## 文档：模块权限不等于文件权限

入口 `DocumentWorkspaceController` → `DocumentWorkspaceServiceImpl` → `DocumentWorkspaceMapper.xml`，均在manage模块；API根为 `/document/workspace`。

Controller首先要求 `document:file:manage`，创建、编辑、删除、分享等再按各按钮权限检查。Service继续校验owner、当前ACL及生命周期；管理员通览也有专门的 `ADMIN_ALL` 和管理员检查。禁止把一个菜单权限改成全部文件可读。

| 数据 | 用途 |
| --- | --- |
| `doc_folder` | 用户目录层级；根目录0是汇总入口，不是实际挂载目录 |
| `doc_document` | owner、storageKey、checksum、contentVersion、editorKey、生命周期 |
| `doc_version` | 文件版本及事件；实际字节另存文件目录 |
| `doc_acl` | 协作者、权限和有效期 |
| `doc_user_quota` | 总额度、单文件上限，写入流程持有额度锁 |
| `doc_operation_log` | 文件业务审计，独立于若依通用操作日志 |

`listDocuments` 根据MY/SHARED/ARCHIVED/ADMIN_ALL等实际scope过滤；不同scope目录筛选语义不同。对象级读取走 `requireReadable`，修改/复制/管理走相应owner/编辑检查；服务接口不能只按ID直接取磁盘文件。

## 上传、编辑与存储一致性

- `createDocument/uploadDocument` 校验类型、文件体积、用户额度和路径；配额统计与写入保持现有锁和事务，不做“前端查一下就允许上传”。
- `DocumentStorageService` 负责受控存储路径、字节和摘要。新建会写初始文件、版本和审计；失败清理本次临时文件，但不能把文件系统当成与数据库同一个事务。
- 归档、回收站、还原、版本恢复沿现有生命周期方法；不直接删磁盘目录绕过关联数据。
- PDF走只读预览；压缩包走下载/受限处理，不进入在线Office。压缩条目数、展开体积、PDF页数等上限以 `DocumentManagementProperties` 和校验代码为准。
- OnlyOffice能力由 `OnlyOfficeDocumentEditorProvider`、`DocumentJwtService` 和回调处理组成。打开编辑器返回bootstrap，forcesave的queued仅表示已排队；内容版本最终由有效回调和实际文件更新确认，不能在按钮点击后伪造保存成功。
- 编辑器能访问平台的取文件/回调地址，平台能访问编辑器服务；JWT、可信下载主机和令牌有效期要配套。禁止为了“连通”删除回调校验或放开任意下载URL。
- 到期权限由 `DocumentPermissionExpiryTask` → `expireCollaboratorPermissions` 维护。当前完整新库唯一启用的Quartz技术任务为此任务，不是自动巡检演示任务。

运维同时备份六表、文件目录和编辑器/令牌有效配置。只恢复MySQL或只复制上传目录都会造成版本/摘要不一致。Nginx体积、Spring multipart和业务用户单文件上限需同时满足，不能仅调大其中一个。

验证：`DocumentWorkspaceServiceImplTest`、`DocumentUploadServletConfigurationTest`、现有文档Controller/Storage/Editor测试与前端 `src/views/document/__tests__`；真实Office打开、回调、强制保存和版本回读单独验收。

## 知识中心：目录、文章和版本

入口 `/knowledge` → `KnowledgeCenterController` → `KnowledgeCenterServiceImpl` → `KnowledgeCenterMapper.xml`；前端 `views/knowledge`。

六表为 `kb_space`、`kb_page`、`kb_page_version`、`kb_tag`、`kb_page_tag`、`kb_page_document`。目录和文章共享page模型，文件正文不因此复制进知识表。

操作顺序：空间→目录→文章标题/正文/标签→可读文档引用→保存→历史版本/归档/回收站/恢复。查询树、搜索与详情走各自已有过滤逻辑，角色按钮权限分别为 `knowledge:space:manage`、`knowledge:page:list/write/remove`。

关键约束：

- `normalizeParent` 校验父节点是当前空间的有效目录，不能挂自己或自己的子孙，限制异常循环/过深层级。
- `ensureUniqueSibling` 保持同空间同父目录名称唯一；标签去重且有数量/长度上限。
- 内容先通过 `KnowledgeHtmlSanitizer`，不得为了保留某段HTML直接旁路清理。
- 修改/恢复应走版本方法；`restoreVersion` 是业务恢复操作，不用SQL把某版本直接覆盖正文。
- `validateDocumentIds` 对新增/变更引用调用文档服务检查可读性，拒绝回收站文档；候选接口也需文档模块权限。读取引用再次解析当前可用性，文章引用不会授予被引用文件权限。

排障时区分知识按钮权限、空间/目录结构、内容清理和文件ACL。不要把“文章里有附件链接”当作用户有文件下载权限。

来源与测试：`docs/KNOWLEDGE_CENTER_V3_15_0.md`、`KnowledgeCenterServiceImplTest`、`KnowledgeHtmlSanitizerTest`、前端 `npm run test:knowledge`。增量升级另看当前知识中心升级说明，不和累计SQL重复执行。
