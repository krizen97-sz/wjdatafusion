# RYNEW 前端设计治理规范

## 1. 适用范围与事实来源

本文是当前仓库的前端组件与交互治理规范。产品气质继续由仓库根目录
`PRODUCT.md` 和 `DESIGN.md` 描述；本文把真实 Vue 代码中已经存在的能力
转成可执行规则。

当前有两个前端代码基线：

| 目录 | 技术基线 | 治理边界 |
| --- | --- | --- |
| `RuoYi-Vue3-master` | Vue 3.5.26、Element Plus 2.13.1、Vite 6.4.1 | 当前主前端；本文默认所称“页面”均指此目录 |
| `WDF100.0/wjdatafusion-ui` | Vue 2.6.12、Element UI 2.15.14、Vue CLI 4.4.6 | 遗留前端；普通任务不得顺手迁移、混入 Element Plus 或复制 Vue3 组件 |

发生冲突时按以下顺序判断：

1. 当前功能模块中已经验证的业务实现；
2. 当前仓库公共组件与全局注册方式；
3. 当前仓库的 RuoYi 页面模式；
4. 当前安装版本支持的 Element Plus 能力；
5. 当前图标、主题变量和工具类；
6. 本文与其他治理文档；
7. 外部原型、截图或上游 RuoYi 示例。

当前仓库代码是唯一事实来源。本规范不授权升级依赖、替换主题或批量
重构历史页面。

## 2. 技术与样式基线

- 应用入口：`RuoYi-Vue3-master/src/main.js`。
- 构建：Vite 6.4.1，配置入口 `RuoYi-Vue3-master/vite.config.js`。
- 状态：Pinia 3.0.4；路由：Vue Router 4.6.4。
- 样式：SCSS，入口 `src/assets/styles/index.scss`。
- 全局字体：`PingFang SC`、`Microsoft YaHei`、`Helvetica Neue`、Arial、sans-serif。
- Element Plus 全局尺寸：用户设置或 `default`；不得在同一操作组混用尺寸。
- 页面容器：`.app-container`，当前全局内边距为 16px。
- 主响应式断点：布局在 992px 以下进入 mobile 模式；业务页面还应按
  自身信息密度检查 768px 左右的换行和滚动。

### 2.1 现有主题变量

优先使用 `src/assets/styles/variables.module.scss` 中的变量：

| 语义 | 浅色变量 | 暗色变量由同名变量覆盖 |
| --- | --- | --- |
| 正文 / 标题 / 辅助文字 | `--app-text` / `--app-heading` / `--app-muted` | 是 |
| 页面背景 | `--page-bg` | 是 |
| 普通 / 强 / 弱表面 | `--surface-bg` / `--surface-strong` / `--surface-muted` / `--surface-subtle` | 是 |
| 悬停与边框 | `--surface-hover` / `--surface-border` / `--surface-border-strong` | 是 |
| 浮层阴影 | `--surface-shadow` | 是 |
| 加载遮罩 | `--loading-mask-bg` | 是 |
| 图表网格 / 坐标 | `--chart-grid` / `--chart-axis` | 是 |
| 健康状态 | `--health-normal` / `--health-warning` / `--health-danger` / `--health-idle` | 是 |

Element Plus 的 `--el-*` 变量继续由框架和现有暗色覆盖维护。不要再创建
同义色值。`support.scss` 中的 `--support-*` 只用于现场融合等 support
模块，不能扩散为第二套全局主题。

参考 Apple Human Interface Guidelines 的语义色与排版层级：静态标题、
版本号、摘要值和正文以 `--app-heading` / `--app-text` 为主；平台主色只用于
链接、当前选中、主要操作和确有含义的状态，不用同一种蓝色替代全部信息层级。
标准材质只用于区分导航、分组和内容层；不在普通内容卡片中堆叠玻璃、渐变、
彩色描边或阴影。对齐、字号、字重和间距先于装饰色。

### 2.2 形状、边框与阴影

- 普通控件沿用 Element Plus 默认圆角和高度。
- 普通页面内容不额外套卡片；需要独立表面时优先使用 `el-card` 或现有
  模块容器。
- 现有普通内容面常见 12px 圆角；不要把它解释为所有控件的新全局值。
- 阴影只用于浮层或确有层级的表面，优先 `--surface-shadow`。
- 业务页面不得逐项硬编码全套颜色、圆角和阴影来形成独立视觉语言。

## 3. 页面结构

普通管理列表采用以下顺序：

1. `.app-container`；
2. 可折叠的行内查询表单；
3. `el-row.mb8` 操作工具栏与 `RightToolbar`；
4. `el-table`，使用 `v-loading`；
5. `Pagination`，仅在 `total > 0` 时显示；
6. `el-dialog` 新增 / 编辑表单。

不要为普通列表增加独立的标题英雄区、统计卡阵列或自定义表格壳。复杂
业务页可以改变信息结构，但其按钮、状态、表单、反馈和图标仍遵循本规范。

## 4. 按钮

### 4.1 语义映射

| 操作 | Element Plus 语义 |
| --- | --- |
| 页面唯一主要操作、提交、确认 | `primary` |
| 普通次要操作、取消、关闭 | 默认按钮 |
| 明确完成或通过语义 | `success`，不要把它当普通强调色 |
| 需要注意、导出等仓库既有警示语义 | `warning` |
| 删除、解除、终止、清空等危险操作 | `danger` |
| 普通辅助状态或导入等既有语义 | `info`，先对照相似页面 |

规则：

- 同一操作区原则上只有一个实心 `primary`。
- 查询区固定“搜索”在前、“重置”在后。
- 弹窗 footer 固定“取消”在前、“确认/保存”在后；历史页面中相反的顺序
  视为遗留，不作为新增页面依据。
- 提交按钮必须绑定 `loading`，处理函数还要防止重复提交。
- 表格行内操作使用 `link` 轻量按钮；危险操作使用 `danger`，不得全部
  涂成同一种 primary。
- 仅图标按钮必须有 `aria-label`、`title` 或 `el-tooltip`。系统用户、角色、
  任务页面中无文本且无可访问名称的历史按钮不作为标准。
- 同组按钮尺寸、图标位置和间距一致；不使用渐变、发光、超大圆角或胶囊
  作为普通操作按钮。

### 4.2 按钮反馈与动作状态

- 普通后台按钮继续由 `el-button` 负责尺寸、语义、禁用、loading 和键盘行为。
  hover 只调整语义表面、边框和文字，可选图标位移不超过 1–2px；press 使用
  轻微表面变化或 1px 下压，不做大幅缩放或弹跳。
- hover 动效只在支持 hover 的指针上启用，触屏不能留下粘滞 hover 状态；
  `focus-visible` 必须独立清晰。
- 打开驾驶舱、编辑器或独立工作区的“应用入口”按钮可以使用显式 opt-in
  入口样式和方向图标微移；普通查询、重置、取消和表格行操作不套入口动效。
- 提交与执行按钮遵循 `idle -> loading -> success/error` 的真实业务状态。
  loading 保持按钮宽度稳定并复用平台加载标识；success morph 仅在接口或任务
  明确成功后出现，不能由定时器或动画结束伪造。
- Ripple 只允许作为明确提交/执行按钮的有界、可清理状态层。不得给全部
  `el-button`、link 行操作、Tabs 或 Segmented 全局加 Ripple，也不得引入
  Material Web 或其他运行时依赖只为获得该效果。

## 5. Tabs 与页面切换

先判断语义，再选控件：

| 语义 | 实现 |
| --- | --- |
| 页面内部内容切换 | `el-tabs` |
| 少量互斥筛选 | `el-segmented` 或 `el-radio-group`，先看相似页面 |
| 路由导航 | Router、菜单和路由元数据 |
| 动态可关闭页面标签 | RuoYi `TagsView` |
| 普通查询条件 | 查询表单，不改造成 Tab |

当前业务页面的 `el-tabs` 均以默认样式为主。新增页面不要在同一区域混用
default、card、border-card，也不要用 `div` 和绝对定位下划线模拟 Tabs。
路由型切换必须把状态放进 route 参数或 query，使刷新后可恢复。

- 应用级内容 Tabs 可通过 label slot 使用现有 Element Plus / Keyline 图标，
  继续复用原生 active bar 表达共享位置，不另画第二条活动指示器。
- “业务视图 / 技术视图”、全部/异常/正常、例行/高频等少量互斥模式使用
  `el-segmented`；通过 default slot 放置图标和文字，并保留其 radio-group
  键盘语义及原生滑动选中层。
- Motion 的 LayoutGroup 只作为“共享布局连续性”的设计启发。本项目优先用
  Element Plus active bar / selected thumb、Vue `Transition` 和 CSS transform，
  不为了同类效果新增 Motion 或 React 风格封装。
- 内容切换确有价值时使用 4–8px、160–220ms 的轻量位移与透明度过渡；快速
  连续切换必须可中断并落在真实选中视图，不使用造成空白间隙的重型编排。

## 6. 状态、Tag 与 Switch

### 6.1 组件映射

| 语义 | 组件 |
| --- | --- |
| 后端字典字段 | 全局 `DictTag` |
| 页面内固定枚举或辅助分类 | `el-tag` |
| 可修改二元状态 | `el-switch` |
| 只读布尔状态 | `DictTag` 或 `el-tag`，不使用可交互 Switch |
| 数量提醒 | `el-badge` |
| 页面级结果 | `el-result` |
| 空数据 | `el-empty` |
| 局部加载 | `v-loading`；骨架确有首屏结构价值时用 `el-skeleton` |

语义颜色：

- `success`：正常、成功、完成、启用；
- `warning`：等待、处理中、部分异常、需要注意；
- `danger`：失败、异常、禁用、高风险；
- `info`：未知、未配置、普通辅助状态；
- `primary`：当前主要流程状态，不用作通用装饰色。

同一业务状态必须集中映射文案、类型和图标。复杂模块可使用一个纯函数或
模块规则文件输出 `{ label, type, icon }`，不能在每个模板里重复三元表达式。

### 6.2 可修改状态

- `active-value` / `inactive-value` 必须与接口字段类型完全一致。
- 高风险切换先确认；接口失败或用户取消时恢复旧值。
- 参考 `src/views/system/role/index.vue` 的确认与回滚结构。
- 多状态业务使用 Select、Radio 或其他枚举控件，不强行压成 Switch。
- `active-text` / `inactive-text` 在同一业务域保持一致，不在一页写“停用”、
  另一页写“关闭”却表达同一状态。

## 7. 表单

### 7.1 查询表单

- 使用 `el-form`、`:inline="true"`、`v-show="showSearch"` 和 `queryRef`。
- 普通查询控件在当前标准页常用 200px；日期范围常见 308px。先复制相似
  页面值，不建立新的页面级宽度体系。
- 输入支持 `clearable`，文本查询支持 Enter 触发。
- `handleQuery` 先把 `pageNum` 重置为 1；`resetQuery` 使用 `resetForm` 后查询。

### 7.2 新增 / 编辑表单

- 简单任务使用 `el-dialog + el-form`；需要保留页面上下文且内容可浏览时
  使用 `el-drawer`。
- 简单单列表单可参考 500px 对话框；两列用户表单参考 600px；信息更完整
  的引导表单可参考 720px。宽度由内容选择，不逐页发明近似值。
- 普通表单使用现有 80px / 100px 标签基线；字段名称较长时采用同类页面
  的 110px，或在复杂编辑器中统一 `label-position="top"`。
- 必填、校验和错误提示使用 Element Plus rules，不用自绘红字代替。
- placeholder 不能替代必要标签；帮助说明放在字段附近并保持辅助层级。
- 长表单分组；弹窗内容限制在视口内并内部滚动。
- 提交期间使用 loading、禁用重复触发，并在 finally 中恢复状态。

详情只读展示优先 `el-descriptions`，不要用禁用输入框拼出详情页。

## 8. 表格、列表与分页

- 普通管理数据使用 `el-table`，不要再封装一套通用表格。
- 工具栏使用 `RightToolbar`；列显隐需要传入 `columns`。
- 分页统一使用全局 `Pagination`，默认布局和页大小以组件实现为准。
- 字典状态使用 `DictTag`，权限按钮使用 `v-hasPermi` / `v-hasRole`。
- 长文本使用 `show-overflow-tooltip` 或进入详情；不要把所有列都设固定宽度。
- 操作列置于右侧，宽度只覆盖真实操作；低频操作可以进入下拉菜单。
- 空数据优先使用表格 `empty-text` 或 `el-empty`；加载使用 `v-loading`。
- 批量操作依赖明确的 selection 状态，按钮使用 `disabled` 表达不可执行。

## 9. Dialog、Drawer 与反馈

| 场景 | 实现 |
| --- | --- |
| 简单新增 / 编辑 | `el-dialog` |
| 长详情、历史记录、保留主页面上下文 | `el-drawer` |
| 短确认、危险确认 | `proxy.$modal.confirm` / `ElMessageBox` |
| 普通成功、警告、错误 | `proxy.$modal.msg*` 或 `ElMessage` |
| 跨上下文重要通知 | `ElNotification`，当前项目少用，需有明确理由 |
| 页面级完成 / 失败结果 | `el-result` |

- 不使用 `div + position: fixed` 重画 Modal。
- 不叠加多层对话框；必要的子任务应关闭、替换或使用 Drawer 保留上下文。
- 标题说明任务；footer 顺序与 loading 一致。
- 有未保存内容时才增加关闭确认，不能一律阻断关闭。

## 10. 图标

当前有两条正式图标链：

1. Element Plus 控件图标：`@element-plus/icons-vue` 2.3.2，全局注册；
2. 平台 SVG：`SvgIcon + vite-plugin-svg-icons`，由 `iconRegistry.js` 提供名称
   和未知图标回退。

`src/assets/icons/svg` 当前共 662 个 SVG，其中 115 个平台图标、547 个
`keyline-*` 图标。菜单选择必须使用 `IconSelect` 与 `iconCatalog.js`，不要
自行扫描目录或维护第二份名单。

禁止在业务页面写内联 SVG、CSS 图标、Emoji、Unicode 操作符号或
`data:image/svg+xml`。确需图表、地图、拓扑或流程图自身的 SVG / Canvas
时，按 `UI-EXCEPTIONS.md` 登记；这些媒介仍不能承担普通按钮图标。

## 11. 加载、空、错误与结果状态

- 列表和局部容器：`v-loading`，并确保遮罩只覆盖所属区域。
- `v-loading`、按钮 `loading`、全屏阻塞和首屏启动统一使用平台 Logo 与
  线性脉冲反馈；不得恢复若依三层圆环、Element Plus 圆形图标或页面级自制
  转圈动画。全屏服务统一通过 `openPlatformLoading`，禁止业务代码直接创建
  `ElLoading.service` 或写死黑色遮罩。
- 首次加载且结构占位能减少跳动时：`el-skeleton`；当前仓库尚无标准用例，
  新增前先确认确有价值。
- 空数据：`el-empty`，业务允许创建时提供一个明确下一步按钮。
- 局部错误：`el-alert` 或与页面结构一致的错误区，包含原因和重试。
- 页面级完成或失败：`el-result`；当前仓库无标准用例，直接沿用 Element
  Plus，不创建 `SuccessState` / `ErrorState`。
- 不得让加载、空、错误和权限不足共享同一含糊文案。

## 12. 响应式与可访问性

- 桌面后台是主场景，但 992px 以下不得出现工具栏覆盖、弹窗越界或不可达
  操作；窄屏允许换行、水平滚动或收敛低频信息。
- 关键按钮、Tabs、Switch、状态和错误不能只靠颜色传达。
- 仅图标按钮必须提供可访问名称；动态反馈用适当的 `role="status"`、
  `aria-live` 或 Element Plus 反馈组件。
- 表格长内容处理溢出；弹窗 / Drawer 内容在视口内可滚动。
- 动画尊重 `prefers-reduced-motion`，不增加与任务无关的动效。

### 12.1 统一动效语言

后台页面处于 Operate 场景，动效只承担反馈、状态、连续性和真实进度：

当前统一方向为 **精准滑轨（Precision Rail）**：Tabs 的选中关系由一条短而
稳定的活动轨道承载；已有选中底色的 Segmented 只使用滑动选中面，不再叠加
下划线。按钮反馈使用轻微位移和表面变化，高价值执行动作才使用有界 Ripple；
内容切换只做短距离、可中断的方向过渡。禁止恢复历史上的图标放大、弹跳、
彩色光晕和多层阴影叠加。

| 节奏 | 时长 | 使用场景 |
| --- | --- | --- |
| 即时 | 100–140ms | press、focus、图标颜色 |
| 快速 | 140–180ms | hover、小范围内容进入、错误恢复 |
| 标准 | 180–240ms | Tabs 活动条、Segmented 选中层、选择表面 |
| 审慎 | 240–320ms | Drawer 内布局连续性、Stepper 阶段内容 |

- 进入优先使用 `cubic-bezier(0.16, 1, 0.3, 1)`，状态交接可使用
  `cubic-bezier(0.2, 0.8, 0.2, 1)`；退出不慢于进入。
- 常规控件不使用 bounce、elastic、旋转、持续漂浮和超过 8px 的位移。
- 简单反馈优先 CSS；列表增删重排使用 Vue `TransitionGroup`；真实线性流程
  使用 `el-steps` 加 keyed `Transition` 内容。音频、模型、导入或分析阶段
  必须来自真实后端状态，不用前端计时器伪造进度或成功。
- 全局样式只定义共享动效 token 和克制的按钮反馈。入口按钮、Ripple、
  success morph、增强 Tabs 和 Stepper 均为显式 opt-in，不能靠宽泛选择器扩散。
- 减少动效模式移除位移、Ripple 扩张、morph 编排和非必要循环，但保留最终
  选中、焦点、loading、success 和 error 状态。

### 12.2 精准滑轨实现契约

| 业务语义 | 项目实现 | 使用边界 |
| --- | --- | --- |
| 普通后台按钮 | 全局 `el-button` hover / press 基础反馈 | 不改业务颜色，不作用于 link / text 行操作 |
| 应用或工作区入口 | `.motion-entry-action` + `data-motion-direction` | 只移动方向图标，普通查询/重置不使用 |
| 提交或执行 | `.motion-execute-action` + `v-motion-ripple` | 只绑定明确执行动作，loading/disabled/reduced-motion 自动停用 |
| 真实成功反馈 | `.is-motion-success` + `.motion-action-state` | 必须来自真实业务结果，错误后立即恢复可执行态 |
| 应用级 Tabs | `el-tabs.motion-tabs` + `.motion-control-label` | 复用原生 active bar，不自绘第二套下划线；图文使用共享块级 Flex 与 `1em` 图标，选中态不改变纵向几何 |
| 紧凑互斥筛选 | `el-segmented.motion-segmented` + option 图标 | 复用原生滑动选中面，只用单层轻阴影，不加选中边框或下划线；文字统一使用 `--el-font-size-base`，选中文字使用标题色，普通图标使用主色；图文使用共享块级 Flex 与 `1em` 图标自然居中，不叠加 `translateY`、top 或 margin 纵向补偿；局部决定等宽、换行或横向滚动 |
| 视图内容交接 | keyed `.motion-view-stage.is-forward/.is-backward` | 只在关系明确的相邻视图使用，不制造 `out-in` 空白 |
| 真实阶段流程 | `el-steps` + keyed Vue `Transition` | 当前没有真实阶段数据的页面不得为了视觉效果新增 |

允许的高辨识度微动效只有：活动轨道交接、方向图标轻推、有界 Ripple、真实
成功 Morph、真实 loading 进度轨道，以及业务确实处于活动态时的低频状态脉冲。
单个交互最多选择一项主效应和一项辅助效应；页面密度越高，位移和阴影越弱。

## 13. 实施前后检查

编码前先完成组件复用计划；有原型时再完成原型适配表。实施后检查：

- 是否仍然使用当前业务流程、权限和接口；
- 是否复用了组件目录、Element Plus、图标和变量；
- 是否创建了重复组件或新的视觉语言；
- 是否覆盖了加载、空、错误、权限和提交中状态；
- 是否出现不必要的 absolute、硬编码或页面级全局覆盖；
- 是否通过 `npm run ui:guard` 与相应测试；
- 是否在最终报告中列出复用项、偏离项和假设。

## 14. UI Guard

`RuoYi-Vue3-master/scripts/ui-guard.mjs` 默认比较 `origin/main` 与当前工作树，
只分析新增/修改行及依赖差异；未跟踪的新文件按全部新增处理。若当前 HEAD
未包含基线，Guard 直接要求同步，避免把主线新提交反向判为本任务问题。

常用命令：

```bash
npm run ui:guard
node scripts/ui-guard.mjs --staged
node scripts/ui-guard.mjs --all
node scripts/ui-guard.mjs --json
node scripts/ui-guard.mjs --fail-on-warn
```

硬错误包括：业务页真实内联 SVG、`data:image/svg+xml`、Emoji 操作图标、
第二套 UI / 图标 / 设计系统、未批准的运行时 UI 依赖、普通页面 Canvas 或
截图模拟界面。`<svg-icon>` 是正式组件，不得误报为内联 SVG。

警告包括：大量新增硬编码颜色与视觉尺寸、疑似重复组件、页面主体大量
absolute、CSS 绘制图标、页面级重写按钮/Tabs/Switch/Badge/Dialog、同页
新增多种按钮尺寸或 Tab 风格、其他新增运行时依赖及依赖版本变化。

`--all` 用于看见历史债务；它不改变默认的差异基线。allowlist 只把命中的
发现显示为 `EXCEPTION`，不会静默消失。过期条目不再放行。
