# RYNEW 前端组件目录

## 1. 使用方法

本目录记录 `RuoYi-Vue3-master` 当前可复用能力。编码前先在本表定位语义，
再搜索“参考文件”中的真实用法。组件存在不代表任何场景都应使用；先判断
展示、编辑、筛选、导航、反馈等语义。

全局组件在 `src/main.js` 注册，可以直接在模板中使用。其他组件应从其真实
路径导入，不要建立同名代理组件。

## 2. 全局业务组件

| 组件 | 路径 | 主要能力 | 关键约定 | 参考 |
| --- | --- | --- | --- | --- |
| `DictTag` | `src/components/DictTag/index.vue` | 按 RuoYi 字典显示文本或 `el-tag` | 传 `options`、`value`；未匹配值可由 `showValue` 控制 | `src/views/system/post/index.vue` |
| `Pagination` | `src/components/Pagination/index.vue` | 统一总数、页大小、翻页和自动滚顶 | `v-model:page`、`v-model:limit`、`@pagination`；默认页大小 20，选项 10/20/30/50 | `src/views/system/post/index.vue` |
| `RightToolbar` | `src/components/RightToolbar/index.vue` | 查询区显隐、刷新、列显隐 | `v-model:showSearch`、`@queryTable`；纯图标按钮已带 Tooltip | `src/views/system/user/index.vue` |
| `FileUpload` | `src/components/FileUpload/index.vue` | 多文件上传、类型/大小/数量校验、拖拽排序 | `v-model`、`action`、`limit`、`fileSize`、`fileType`、`disabled` | 搜索 `<file-upload` 的现有表单 |
| `ImageUpload` | `src/components/ImageUpload/index.vue` | 图片上传、预览、数量与大小校验、拖拽排序 | `v-model`；不要另建业务图片上传器 | 搜索 `<image-upload` |
| `ImagePreview` | `src/components/ImagePreview/index.vue` | 本地或外链图片预览，支持多图列表 | `src` 可为逗号分隔值；`width` / `height` 支持数字或字符串 | `src/views/whitelist/filterData/index.vue` |
| `Editor` | `src/components/Editor/index.vue` | Quill 富文本、图片上传与粘贴 | `v-model`、`height`、`minHeight`、`readOnly`、`fileSize` | `src/views/system/notice/index.vue` |

`SvgIcon` 也全局注册为 `svg-icon`，单列在图标章节。

## 3. 图标能力

| 能力 | 路径 | 用法 | 不要做 |
| --- | --- | --- | --- |
| Element Plus 图标 | `@element-plus/icons-vue`，由 `src/components/SvgIcon/svgicon.js` 注册 | `icon="Plus"`、`:icon="View"` 或 `<el-icon>` | 不引入其他 npm 图标库 |
| 平台 SVG | `src/components/SvgIcon/index.vue` | `<svg-icon icon-class="user" />` | 不在业务页写 `<svg>` / `<path>` |
| 图标注册与回退 | `src/utils/iconRegistry.js` | 名称不存在时回退到 `component`，避免菜单空白 | 不在页面复制图标名单 |
| 菜单图标选择 | `src/components/IconSelect/index.vue` | 平台 / Keyline 来源、分类、中文/英文/拼音搜索 | 不新建菜单图标选择器 |
| 图标目录 | `src/assets/icons/svg` | 115 个平台图标、547 个 `keyline-*` 图标 | 不改现有 `sys_menu.icon` 语义，不改 Keyline 路径造变体 |

## 4. 其他仓库公共组件

| 组件 | 路径 | 适用场景 | 备注 |
| --- | --- | --- | --- |
| `Crontab` | `src/components/Crontab/index.vue` | Cron 表达式编辑与回显 | 定时任务不得再拼一套 Cron 表单 |
| `Breadcrumb` | `src/components/Breadcrumb/index.vue` | 根据路由生成面包屑 | 由布局使用，不在普通页面重复挂载 |
| `TopNav` | `src/components/TopNav/index.vue` | 顶部路由导航 | 与 Router / 权限路由绑定 |
| `HeaderSearch` | `src/components/HeaderSearch/index.vue` | 全局路由搜索 | 不用于普通列表字段查询 |
| `Screenfull` | `src/components/Screenfull/index.vue` | 应用级全屏切换 | 业务画布全屏先查模块已有实现 |
| `SizeSelect` | `src/components/SizeSelect/index.vue` | Element Plus 全局尺寸切换 | 页面不得另设独立全局控件尺寸 |
| `ParentView` | `src/components/ParentView/index.vue` | 嵌套路由占位 | 路由结构能力，不是内容容器 |
| `iFrame` | `src/components/iFrame/index.vue` | 内嵌外部路由页面 | 需要真实路由元数据，不用 iframe 模拟普通页面 |
| `AuthScene` | `src/components/AuthScene/index.vue` | 登录 / 注册认证侧动画场景 | 仅认证页例外；不构成业务页视觉标准 |

## 5. Element Plus 标准能力

这些能力无需本项目再创建等价公共组件：

| 语义 | 首选能力 | 已有参考 |
| --- | --- | --- |
| 查询 / 编辑表单 | `el-form`、`el-form-item` 与 rules | `src/views/system/post/index.vue` |
| 普通管理表格 | `el-table`、`el-table-column` | `src/views/system/post/index.vue` |
| 页面内切换 | `el-tabs`、`el-tab-pane` | `src/views/system/user/profile/index.vue` |
| 少量互斥筛选 | `el-segmented` / `el-radio-group` | `src/views/support/autoInspection/cockpit.vue` |
| 二元修改 | `el-switch` | `src/views/system/role/index.vue` |
| 静态状态 | `el-tag` | `src/views/ipam/index.vue` |
| 数量提醒 | `el-badge` | 当前无稳定页面示例；直接按 Element Plus 使用 |
| 树形选择 | `el-tree-select` | `src/views/system/user/index.vue` |
| 简单编辑 | `el-dialog` | `src/views/system/post/index.vue` |
| 保留上下文的长详情 | `el-drawer` | `src/views/knowledge/components/KnowledgeHistoryDrawer.vue` |
| 详情字段 | `el-descriptions` | 同上 |
| 短确认 | `proxy.$modal.confirm` / `ElMessageBox` | `src/views/system/role/index.vue` |
| 普通反馈 | `proxy.$modal.msgSuccess/msgWarning/msgError` / `ElMessage` | 多个 RuoYi 页面 |
| 重要通知 | `ElNotification` | 当前无统一页面示例，谨慎使用 |
| 局部加载 | `v-loading` | 43 个页面/组件已有使用 |
| 空数据 | `el-empty` | `src/views/document/workspace/index.vue` |
| 页面级结果 | `el-result` | 当前无稳定页面示例；不要另建 Result 组件 |
| 首屏结构占位 | `el-skeleton` | 当前无稳定页面示例；有必要再使用官方组件 |
| 上传 | `el-upload` 或项目 `FileUpload` / `ImageUpload` | 优先项目封装 |
| 富文本 | 项目 `Editor` | 不直接在业务页重复配置 Quill |

## 6. 布局、路由与权限能力

| 能力 | 位置 | 约定 |
| --- | --- | --- |
| 应用布局 | `src/layout/index.vue` | Sidebar + Navbar + TagsView + AppMain；992px 以下进入 mobile |
| 动态路由页签 | `src/layout/components/TagsView/index.vue` | 可关闭路由标签，刷新和关闭行为由 `$tab` 管理 |
| 页面主区 | `src/layout/components/AppMain.vue` | RouterView、KeepAlive、固定头部和滚动边界 |
| 按钮权限 | `src/directive/permission/hasPermi.js` | `v-hasPermi="['module:resource:action']"` |
| 角色权限 | `src/directive/permission/hasRole.js` | `v-hasRole="['admin']"` |
| 字典 | `src/utils/dict` + `proxy.useDict` | 字典字段先加载 options，再交给 `DictTag` 或表单选项 |
| 统一反馈 / 下载 / 页签 | `src/plugins` | `$modal`、`$download`、`$tab`；不要在每页建立新服务 |

## 7. 模块级组件

模块级组件可以在同一业务域内复用，但不能未经评估提升为全局组件：

- 文档管理：`src/views/document/components/DocumentActionMenu.vue`、
  `DocumentUploadDialog.vue`、`DocumentRecordsDrawer.vue`、
  `DocumentShareDrawer.vue`、`DocumentStorageDrawer.vue`。
- 知识中心：`src/views/knowledge/components/KnowledgeHistoryDrawer.vue`、
  `KnowledgeDocumentSelector.vue`、`KnowledgeNavigation.vue`。
- IPAM：`src/views/ipam/components/IpamConfigWorkspace.vue`、`Ipv4Input.vue`、
  `IpamNetworkTree.vue`；工作簿继续使用已安装的 RevoGrid 能力。
- 自动巡检：`src/views/support/autoInspection/components` 中的健康度和表单
  子组件；新代码不要继续扩大 10,985 行的 `index.vue`。

提升为全局组件前至少证明两个独立业务域存在相同语义和 API，而不是只有
相似外观。

## 8. 当前不存在的通用封装

当前没有稳定的全局“页面标题、查询区域、表格壳、状态 Badge、详情壳、
Dialog 壳、EmptyState、SuccessState”组件。默认使用 `.app-container` 与
Element Plus 组合，不得因为本表记录“缺失”就自动创建。

仅在以下条件同时满足时考虑新增公共组件：

1. 至少两个独立业务域重复同一语义和行为；
2. Element Plus 与现有组件无法直接表达；
3. Props、事件、可访问性、加载/错误状态有稳定契约；
4. 有迁移与回归边界，不要求本次批量重构历史页；
5. 已在 `UI-EXCEPTIONS.md` 或治理评审中记录理由。
