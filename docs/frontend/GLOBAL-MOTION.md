# 全平台动效适配 v4.12.0

## 设计依据

在v4.11.0按钮反馈基础上，将动效扩展到跨模块原生交互。
参考 [LottieFiles按钮目录](https://lottiefiles.com/free-animations/button) 与
[官方UI交互说明](https://lottiefiles.com/blog/design-inspiration/struggling-with-engagement-heres-how-ui-animation-can-fix-it)
中的微交互、导航连续性及操作结果反馈，使用本项目CSS和Vue Transition实现。
没有复制或下载动画素材，没有新增Lottie运行库、外网请求或依赖包。

## 组件复用

| 页面需要 | 已有实现与选型 | 参考文件 | 定制范围 |
| --- | --- | --- | --- |
| 编辑与确认 | Element Plus Dialog / MessageBox | system/post/index.vue、plugins/modal.js | 原生进入退出类，8px进入、4px退出 |
| 上下文详情 | Element Plus Drawer | knowledge/components/KnowledgeHistoryDrawer.vue | 保留四个方向和原有尺寸，只统一过渡 |
| 开关与选择 | Switch / Radio / Checkbox | system/role/index.vue、system/post/index.vue | 原生轨道、圆点和勾选形状不变 |
| 上传与步骤 | Upload / Steps / Progress | document/components/DocumentUploadDialog.vue、site/components/ServerIntakeReviewDialog.vue | 拖放反馈、文件状态、真实进度 |
| 导航与入口 | AppMain、Hamburger、Screenfull、RightToolbar | layout/components/AppMain.vue、components下对应目录 | 路由过渡及固定尺寸图标交接 |
| 操作结果 | 原生Message / Notification | plugins/modal.js | 统一进入退出节奏，复用真实成功状态 |

静态模板盘点覆盖86个组件文件，包含104处Dialog、27处Drawer、57处Switch、
29处Checkbox、96处Radio、12处Upload、1处Steps、9处Progress和5处Menu。
这些是代码使用处数，不代表实际同时显示数量，也不等于逐一完成业务流程验收。

## 行为与性能边界

- 160/220ms控制反馈，280ms浮层进入，140ms退出。选择状态立即更新，无额外业务等待。
- 图标在同一个1em网格内交接；沿用Element Plus图标和SvgIcon，不新绘制图标。
- 只对真实状态变化反馈，无自动成功、假进度、常驻装饰循环或逐表格行编排。
- 普通弹窗移动面板，不移动其固定遮罩；可拖拽、全屏窗口不附加transform。
- 抽屉保留原生方向与拖动行为，Popper定位不重写。
- 路由、缓存、滚动和业务事件不变；离场页面关闭pointer-events。
- Reduced Motion关闭新增动画，缩短原生过渡；不清除Radio/Checkbox的选中transform。
- 表格、拓扑、地图和3D对象不套通用内容进入动画；后端、数据库与Vue 2不改。

## 验证记录

- 全量 `npm run verify:frontend` 通过：260项测试，生产构建成功。
- UI Guard：0错误、0警告，7项既有UIX-004例外，无新增例外。
- Chrome 64+语法扫描：161个构建JS通过；未宣称Chrome 64实机验收。
- 设计检测器：本次修改目标无提示。
- 实际已登录页面：系统菜单、岗位新增弹窗打开/取消、现场与岗位页签快速切换。
  岗位弹窗观测到 `motion-panel-in`，离场页pointer-events为none，没有提交业务数据。
- 原生组件夹具：弹窗/确认框、抽屉、开关/勾选、上传状态、步骤、成功/失败提示、
  通知、下拉、公共按钮键盘操作、快速图标切换、浅深色与390px窄屏。
- 全屏弹窗覆盖1280x720视口且无transform；可拖拽弹窗无附加动画transform。
- 390px窄屏弹窗位于x=12、宽366px，页面无水平溢出；下拉保持bottom-start定位。
- 减少动态效果下控件过渡为1ms，选中勾和Radio圆点的原生变换保持完整。
- 业务模块的增删改请求没有逐个重放；本次验证集中于共享组件状态与实际页面冒烟。

## 发布与回退

仅前端。版本记录由总版本数据读取，未新增SQL。
验证提交同步本地main和日常工作区；250生产环境未修改。
回退本次前端提交即可恢复前一版动效，不需数据恢复或后端替换。
