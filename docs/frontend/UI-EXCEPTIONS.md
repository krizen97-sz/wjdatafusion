# UI 例外登记与边界

## 1. 原则

例外用于表达 Element Plus 不直接覆盖、且业务确实需要的可视化或专业交互，
不是绕过统一规范的快捷方式。历史代码存在偏离，不代表自动获得批准。

每项例外必须同时满足：

1. 有明确业务语义，不是“更现代”或“更像原型”；
2. 已搜索当前模块、公共组件、RuoYi、Element Plus 和图标目录；
3. 偏离范围可精确到文件、规则和媒介；
4. 普通按钮、表单、状态、反馈仍使用现有组件；
5. 有验证、回退与后续复核边界；
6. 同步到 `RuoYi-Vue3-master/scripts/ui-guard-allowlist.json`。

## 2. 当前已知例外

| 编号 | 范围 | 允许的特例 | 仍然禁止 | 状态 |
| --- | --- | --- | --- | --- |
| UIX-001 | `src/components/AuthScene/index.vue` | 登录 / 注册认证侧的本地 Vue/SCSS 动画；第三方视觉来源由 `CAREERCOMPASS_NOTICE.txt` 留存 | 不得扩散到普通业务页；不得恢复 Firebase、React、Tailwind、远程图片依赖 | 保留现状 |
| UIX-002 | `src/views/support/autoInspection/cockpit.vue` | ECharts 健康度与趋势可视化，颜色读取现有主题变量 | 按钮、Tag、空态、错误、表格和图标仍用 Element Plus；不得手写操作 SVG | 保留现状 |
| UIX-003 | `src/views/ipam/overview/index.vue` | IP 地址占用与统计可视化；图表媒介可按登记使用 SVG / Canvas | 不得用 Canvas 模拟表格、表单或分页 | 保留现状 |
| UIX-004 | `src/views/support/site/SiteConfigDialog.vue` | 现场关系拓扑、连接线、节点布局和设备位置等专业可视结构 | 普通编辑、状态、按钮、Dialog、Drawer 和图标不得自绘；不把拓扑视觉推广为 CRUD 标准 | 保留现状，优先渐进拆分 |
| UIX-005 | `src/views/ipam/workbook` | 已安装 `@revolist/vue3-datagrid` 用于 Excel 式大规模 IP 编辑 | 普通管理列表继续用 `el-table`；不得为其他页面默认引入 RevoGrid | 保留现状 |
| UIX-006 | `src/views/document/workspace/index.vue` | 文件夹树、拖拽移动、列表/网格切换、撤销反馈等文件工作区交互 | 普通 CRUD 不复制文件卡片、颜色选择或自定义原生按钮体系 | 保留现状 |

这些登记只解释现有业务特例，不自动放行新代码。UI Guard 对新增内联 SVG
或 Canvas 仍要求同时命中精确路径和 `data-ui-guard="chart|map|diagram|flow"`
标记；该标记必须用于可视化根节点，不能放在普通操作图标上。

## 3. 申请模板

新增例外时在本文件追加：

| 字段 | 内容 |
| --- | --- |
| 编号 | `UIX-xxx` |
| 业务目标 | 为什么现有组件无法正确表达 |
| 复用检索 | 当前模块、公共组件、两个相似页面、Element Plus、图标目录 |
| 精确范围 | 文件路径、UI Guard rule、可选 linePattern |
| 允许内容 | 只描述必要偏离 |
| 继续禁止 | 明确普通控件和视觉语言边界 |
| 验证 | 测试、截图/浏览器验收、可访问性、主题、响应式 |
| 回退 | 如何移除或回到现有组件 |
| 责任人 / 复核时间 | 可追踪信息；临时项必须有到期时间 |

然后在 `ui-guard-allowlist.json` 增加同编号 entry。没有理由、路径或验证的
条目不得合并。

## 4. Allowlist 维护

- `rule` 必须与 Guard 输出的规则 ID 完全一致。
- `paths` 使用相对 `RuoYi-Vue3-master` 的路径；范围尽量精确。
- SVG / Canvas 例外同时使用 `linePattern` 限制显式标记。
- `reason` 引用本文件编号和业务原因。
- 临时例外设置 ISO 日期 `expires`；过期条目不再放行，并产生警告。
- Guard 输出 `EXCEPTION` 不是静默通过；最终报告必须列出使用的例外。
- 依赖批准列表只记录仓库已采用且有明确业务用途的能力。新增依赖仍需用户
  明确批准，不能只改 JSON。

## 5. 不构成例外的情况

- 原型中按钮、Tabs、Tag、Switch 或 Dialog 看起来不同；
- 页面希望“更现代”“更有科技感”；
- 现有 Element Plus 样式需要少量间距适配；
- 不愿搜索当前组件；
- 为单页方便复制第三方组件；
- 用 Emoji / Unicode / CSS / data URI 绕过图标目录；
- 用截图或 Canvas 加快普通界面复刻；
- 旧页面中已经存在同类偏离。
