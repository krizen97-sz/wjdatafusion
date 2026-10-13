# DFP 任务宿主复用计划

基线：5ca363f。范围：平台 iframe 入口、宿主协议及测试；不修改原 DETL 组件、节点表单、原共享资源、生成器或执行器。

| 页面需要 | 现有实现 | 选择 | 参考文件 | 必需定制 |
| --- | --- | --- | --- | --- |
| 平台进入、失败恢复 | 现有 HdiTaskWorkspace | el-result / el-button / v-loading | views/governance/hdi/HdiTaskWorkspace.vue；autoInspection/cockpit.vue错误/重试 | 真正的原组件mounted消息与失败状态 |
| iframe生命周期 | 当前治理宿主；公共iFrame | 治理专用iframe，沿用同源隔离 | components/iFrame/index.vue；governance/hdi | 公共iFrame用定时器撤加载，不能满足原组件初始化判定，因此不替换现有专用宿主 |
| 列表、画布、节点表单 | 原DFPModules / Plugins / TheComponents | 原Vue2/HUI组件 | host/native-task-host.mjs，原module16638/41843 | 只传真实平台context、原props和导航事件 |
| 返回/刷新恢复/新窗口 | 原任务事件 + 现有hdiHostBridge | RuoYi路由query与标准浏览器窗口 | document/workspace模块拆分；hdiHostBridge.js | 严格host路径、精确ID、模式保留；弹窗被拦截时在当前原组件打开 |
| 会话续期 | getInfo + 原有交互监听 | 保留用户操作驱动模式 | HdiTaskWorkspace已有实现 | GET平台/detl/_platform/context，不依赖原管理端用户接口 |

已读 DESIGN、COMPONENT-CATALOG、REFERENCE-PAGES，并核对 Vue 3.5 / Element Plus 2.13.1 / VueUse 已安装依赖。外层不引入新UI库，原Vue2/HUI留在独立iframe。没有复制普通CRUD新页面，没有新增图标/颜色/全局样式，没有新增UI Guard例外。

接口约定：POST /governance/hdi/session 返回真实平台context与 frameUrl=/detl/task-host.html；iframe取 /detl/_platform/context，并由原Async导出消费平台真正CSRF及持久任务域。原store得到的workspaceId必须与可信context匹配，否则拒绝挂载。没有设置checkLicenseAuthorization固定成功回调。

ready只代表原TaskList/ProcessMain实例完成Vue挂载，不代表后端任务运行成功。原节点、列表的业务加载和接口错误仍由原组件显示。任务/运行权限始终在后台检查，前端readOnly只是显示属性。

验证使用宿主契约测试（可控Vue接口证明边界和事件，不冒充原组件浏览器验收）及项目完整verify:frontend。真实平台CRUD和原执行器浏览器闭环由集成任务另行验证。

## 本轮验证记录

- npm run verify:frontend：通过；包含UI Guard、296项测试、生产构建及167个JS产物Chrome64+检查。
- 随后仅在独立host增加元素节点检查，防止Vue渲染异常留下的注释节点被当作ready；7项宿主定向测试重新通过（含该场景）。
- 只使用既有安装依赖，未改package.json、lockfile、全局样式或版本记录。
- 未声称浏览器实际CRUD/执行通过；需要新平台兼容API后联合验收。
