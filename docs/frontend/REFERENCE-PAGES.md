# RYNEW 前端参考页面

## 1. 选择原则

参考页面不是“最好看页面”排行榜。选择同时考虑使用频率、RuoYi 一致性、
公共组件复用、交互完整性与代码可维护性。一个页面可以提供结构参考，同时
仍有明确遗留限制；复制前必须阅读“采用”与“不要复制”两列。

## 2. 标准参考集合

| 场景 | 参考文件 | 采用的模式 | 不要复制的遗留或特例 |
| --- | --- | --- | --- |
| 普通列表 CRUD | `RuoYi-Vue3-master/src/views/system/post/index.vue` | `.app-container`、行内查询、搜索/重置、`el-row.mb8` 工具栏、`RightToolbar`、`el-table`、`DictTag`、`Pagination`、新增/编辑 Dialog | footer 中确认在取消之前；提交按钮没有 loading。新增页面应按治理规范改为取消在前、确认在后并补 loading |
| 查询表单与树形筛选 | `RuoYi-Vue3-master/src/views/system/user/index.vue` | 左侧部门树、行内条件、日期范围、列显隐、分页重置、`el-tree-select` | 行内操作是无文本图标按钮且缺少明确可访问名称；不得作为新标准 |
| 新增 / 编辑引导表单 | `RuoYi-Vue3-master/src/views/ipam/index.vue`（251–304 行） | 720px Dialog、rules、110px label、字段帮助 Alert、实时预览、取消在前、确认在后、提交 loading | IPAM 的流程布局、全屏工作区和页面级样式只属于该业务，不推广到普通 CRUD |
| 可修改状态 | `RuoYi-Vue3-master/src/views/system/role/index.vue`（101–109、348–357 行） | `el-switch`、与后端字符串类型一致的 active/inactive value、确认、失败/取消回滚 | 角色页的无文本行内操作图标是遗留；状态确认文案中的旧式引号拼接不必复制 |
| 简单内容 Tabs | `RuoYi-Vue3-master/src/views/system/user/profile/index.vue` | 默认 `el-tabs`、明确 name、路由参数恢复 active tab、响应式列 | 个人资料卡片的旧列表样式不是通用详情规范 |
| 业务级 Tabs | `RuoYi-Vue3-master/src/views/ipam/index.vue`（86–247 行） | 同一页面内固定一种默认 Tab、lazy 内容、Tab 内各自维护查询/加载/分页 | 页面顶部工作流和专用 Tab CSS 是 IPAM 特例，普通页面先采用默认样式 |
| 状态与长详情 Drawer | `RuoYi-Vue3-master/src/views/knowledge/components/KnowledgeHistoryDrawer.vue` | Drawer 保留上下文；列表/详情分区；`v-loading`、`el-empty`、`el-descriptions`、默认 Tabs、恢复确认、提交 loading | 820px 和双栏导航针对版本历史，不是所有 Drawer 的固定宽度 |
| 复杂业务工作区 | `RuoYi-Vue3-master/src/views/document/workspace/index.vue` | 模块级 ActionMenu、上传 Dialog 与多个 Drawer 分离；加载状态集中；`el-empty` 提供下一步；图标有可访问名称；复杂规则拆到 `documentWorkspaceRules.js` | 文件卡片、拖拽、撤销提示、颜色选择属于文件工作区，不应移植为普通后台页面视觉 |
| 页面级错误与图表空态 | `RuoYi-Vue3-master/src/views/support/autoInspection/cockpit.vue` | 明确错误区与重试；图表无数据使用 `el-empty`；按钮、表格、Tag 仍用 Element Plus；ECharts 读取主题语义 | 驾驶舱是桌面值守特例；专用健康带、图表面板和定制密度不能成为 CRUD 模板 |

## 3. 推荐组合

不要寻找一个“万能页面”整页复制。按需求组合参考：

- 普通 CRUD：`system/post` 的骨架 + `system/user` 的列显隐 + `ipam/index`
  的提交 loading。
- 状态修改：`system/role` 的确认/回滚 + `DictTag` 的只读展示。
- 复杂详情：`KnowledgeHistoryDrawer` 的加载/空态/Descriptions/Tabs。
- 复杂工作区：`document/workspace` 的模块拆分方式；视觉仍服从所属模块。
- 图表页：`autoInspection/cockpit` 的错误、空态与主题色读取；不要复制面板外观。

任何新页面至少再找一个同业务域页面，确保字段、权限和接口流程没有因通用
示例而被覆盖。

## 4. 当前推荐标准

### 4.1 查询与工具栏

- 标准：RuoYi 行内查询 + 搜索/重置 + `RightToolbar`。
- 标准查询控件先沿用同类页面 200px，日期范围按现有 308px。
- 有列显隐需求时参考 `system/user` 的 `columns` 契约。

### 4.2 表格操作

- 标准：`link` 按钮，保留短文本；删除等危险操作使用 `danger`。
- 仅图标按钮只用于空间极其受限且有 Tooltip / `aria-label` 的情况。
- `system/user`、`system/role`、`monitor/job` 的无文本操作按钮是历史遗留，
  后续触达时渐进修复，不在本次治理任务批量修改。

### 4.3 Dialog footer

- 新标准：取消/关闭在左，确认/保存在右；确认绑定 loading。
- `system/post` 等旧 RuoYi 页的相反顺序保留到业务页面被正常触达时再治理。

### 4.4 Tabs

- 标准：默认 `el-tabs`；当前视图未发现业务页混用 card / border-card。
- 路由页签必须使用 TagsView / Router，不把它伪装成内容 Tabs。

## 5. 历史不一致清单

本节用于阻止新增问题，不授权立即重构：

1. `SiteConfigDialog.vue` 约 14,448 行，`autoInspection/index.vue` 约 10,985
   行，是高风险单文件；新能力优先拆出有业务边界的模块级组件。
2. 页面中存在大量硬编码颜色，尤其现场融合、自动巡检、平台、IPAM 和版本
   中心；新增样式必须使用现有主题变量，旧值渐进治理。
3. Dialog 宽度从 400px 到 1280px 及百分比均有使用；新页面只从已验证的
   内容级别选 500 / 600 / 720 等基线，不再产生近似宽度。
4. label-width 存在 68、80、90、100、110、120、150px 等多种值；新增
   普通页面优先 80 / 100，复杂长标签再引用同类页面。
5. 部分复杂页对 `.el-dialog`、`.el-tabs`、`.el-button` 做大量 `:deep` 或
   `:global` 覆盖；这些是模块例外，不是可复制的全局设计语言。
6. 现场融合中存在自定义 `.empty-state`；新页面使用 `el-empty`，除非该
   空态承载拓扑等特殊业务结构并已登记例外。
7. 静态状态既有 `DictTag`，也有散落的 `el-tag` 三元表达式；字典优先
   `DictTag`，复杂固定状态集中到模块单一映射函数。
8. 当前没有 `el-result`、`el-skeleton`、`el-badge` 的稳定业务示例；需要时
   直接用 Element Plus，不新增同义组件。

## 6. 渐进治理顺序

后续在正常业务需求触达时，建议按风险和收益处理：

1. 为无文本行内操作按钮补文本或可访问名称；
2. 给旧 CRUD Dialog 提交补 loading，并统一 footer 顺序；
3. 把重复状态三元表达式集中成模块映射；
4. 将复杂页新增能力拆到模块级组件，停止扩大超大单文件；
5. 用现有语义变量替换触达区域的硬编码色值；
6. 将自定义普通空态迁移到 `el-empty`；
7. 仅在重复语义跨越两个业务域后再评估新公共组件。
