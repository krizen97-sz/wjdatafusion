# DFP 任务宿主复用计划

基线：5ca363f。范围：平台 iframe 入口、宿主协议及测试；不修改原 DETL 组件、节点表单、原共享资源、生成器或执行器。

| 页面需要 | 现有实现 | 选择 | 参考文件 | 必需定制 |
| --- | --- | --- | --- | --- |
| 平台进入、失败恢复 | 现有 HdiTaskWorkspace | el-result / el-button / v-loading | views/governance/hdi/HdiTaskWorkspace.vue；autoInspection/cockpit.vue错误/重试 | 真正的原组件mounted消息与失败状态 |
| iframe生命周期 | 当前治理宿主；公共iFrame | 治理专用iframe，沿用同源隔离 | components/iFrame/index.vue；governance/hdi | 公共iFrame用定时器撤加载，不能满足原组件初始化判定，因此不替换现有专用宿主 |
| 列表、画布、节点表单 | 原DFPModules / Plugins / TheComponents | 原Vue2/HUI组件 | host/native-task-host.mjs，原module16638/41843 | 只传真实平台context、原props和导航事件 |
| 返回/刷新恢复/新窗口 | 原任务事件 + 现有hdiHostBridge | RuoYi路由query与标准浏览器窗口 | document/workspace模块拆分；hdiHostBridge.js | 严格host路径、精确ID、模式保留；弹窗被拦截时在当前原组件打开 |
| 会话续期 | getInfo + 原有交互监听 | 保留用户操作驱动模式 | HdiTaskWorkspace已有实现 | GET平台/detl/_platform/context，不依赖原管理端用户接口 |
| 平台运行记录与完整文件 | DeliveryWorkbench、ScheduleWorkbench详情抽屉 | el-drawer、el-table、el-tag、el-tabs、el-alert、共享download.saveAs | views/governance/components/DeliveryWorkbench.vue、ScheduleWorkbench.vue；components/RightToolbar | 原UI无平台文件归档入口，新增模块内HdiRunHistory；展示真实平台记录，不改变原画布/节点表单 |

已读 DESIGN、COMPONENT-CATALOG、REFERENCE-PAGES，并核对 Vue 3.5 / Element Plus 2.13.1 / VueUse 已安装依赖。外层不引入新UI库，原Vue2/HUI留在独立iframe。没有复制普通CRUD新页面，没有新增图标/颜色/全局样式，没有新增UI Guard例外。

接口约定：POST /governance/hdi/session 返回真实平台context与 frameUrl=/detl/task-host.html；iframe取 /detl/_platform/context，并由原Async导出消费平台真正CSRF及持久任务域。原store得到的workspaceId必须与可信context匹配，否则拒绝挂载。没有设置checkLicenseAuthorization固定成功回调。

ready只代表原TaskList/ProcessMain实例完成Vue挂载，不代表后端任务运行成功。原节点、列表的业务加载和接口错误仍由原组件显示。任务/运行权限始终在后台检查，前端readOnly只是显示属性。

验证使用宿主契约测试（可控Vue接口证明边界和事件，不冒充原组件浏览器验收）及项目完整verify:frontend。真实平台CRUD和原执行器浏览器闭环由集成任务另行验证。

## 本轮验证记录

- npm run verify:frontend：通过；包含UI Guard、296项测试、生产构建及167个JS产物Chrome64+检查。
- 随后仅在独立host增加元素节点检查，防止Vue渲染异常留下的注释节点被当作ready；7项宿主定向测试重新通过（含该场景）。
- 只使用既有安装依赖，未改package.json、lockfile、全局样式或版本记录。
- 未声称浏览器实际CRUD/执行通过；需要新平台兼容API后联合验收。

## 原发布与运行配置的版本边界

复用原Vuex `subscribeAction` 和原Axios `interceptors`，不新增页面、组件或视觉例外。原 `ProcessMain.startTask` 先打开配置再 `pushFlowTask`，因此宿主只在同一个iframe闭包内等待同任务的原发布动作（含原Visual回读）完成，再发送 `getStartConfig`。校验Visual的用户、工作区、任务ID以及发布前修订加一；响应修订再次校验，防止期间另一窗口改版。后台的版本比较保持严格，不从“当前最新版本”自动提升陈旧窗口权限。

原HTTP模块通过同版本webpack运行时扩展接口取得；原JS字节、原store action、原参数/节点算法均未替换。没有发布动作的查询直接读取；发布失败、读取期间版本变化、iframe销毁都拒绝等待中的配置请求。

本轮发布边界验证：10项宿主定向测试通过；`npm run verify:frontend`完整通过（UI Guard、303项Node测试、生产构建、167个JS产物兼容检查）。首次完整检查因独立树缺安装依赖停止；核对既有UI工作树依赖声明一致后复用其已安装依赖，再完成全检。验证不等同于真实浏览器执行验收。

## 原组件装配补充

| 页面需要 | 原实现 | 复用方式 | 证据 | 必需适配 |
| --- | --- | --- | --- | --- |
| 首次筛选数组 | TaskList.handleCachePreSearch | 只增加beforeMount调用原方法 | 原module16638 data在返回前展开this.searchForm，HUI multiple选项直接读value.length | 原render/methods不改，补正常初始生命周期 |
| 自适应操作按钮 | 原AdaptiveButtonContainer | 注册原Plugins公开anchor暴露的组件对象 | TheDataStandardTransformerProcessPluginInfo.anchors[0].component=StandardConversion；其setup返回原共享组件 | 原Vue未挂载实例获取setup上下文后立即销毁，无HTTP、无业务运行、无新render |

列表默认列取原EtlConstants.DFP_FLOW_COLUMN_DISPLAY_DEFAULT；目录iconStr引用同包、manifest校验的原默认SVG。字段映射使用原PROCESS_ADAPTER_TYPE未覆盖时的other分类。没有绘制或替换插件图标，也没有向页面注入伪全局枚举或空update。

共享工具栏装配验证：13项宿主定向测试、完整前端verify（306项测试、UI Guard、构建、167个产物兼容检查）通过；原资源字节保持不变，实际原画布操作仍由集成浏览器另行验收。

节点抽屉使用原188.15d552eb.js / module25418的982字节注册片段（SHA256 3ed9bc64f696cb331efe748828880af980a483af0d61594744b99bffc465fd9a），直接clone原HUI.Drawer并保留其render/CSS和原拖动计算。该宿主未求值整份25418，因为它会启动旧管理应用。14项定向测试、UI Guard以及完整前端verify（307项测试、生产构建、167个JS兼容检查）通过；真实抽屉显示需集成浏览器复测。

## 同窗口自动保存顺序

复用原saveFlowTask/pushFlowTask动作，并在同版本原Vuex的store.dispatch与flowData模块context.dispatch绑定处串行排队。入队时复制原Visual，前一条自己的timeStamp回读确认用户/工作区/任务及修订恰好+1后，才递交后续同窗口快照。保存回读只同步权威ID/版本/运行状态；当前nodes/links/params和业务flowVo草稿不被旧回读覆盖，显式打开/刷新仍走完整原initFlowDataByTaskId。

原动作没有捕获内部回读promise的异常，宿主记录该失败并使公开排队动作拒绝，避免悬挂；失败队列不自动重试，使用原HUI错误提示保留草稿并请用户核对版本。后端CAS、发布和权限检查不变，其他窗口的版本从不自动提升。

保存队列验证：20项宿主定向测试通过，完整前端verify通过（313项测试、UI Guard、构建、167个JS兼容检查）。原试运行成功提示按扁平locale key改为“试运行已提交”，不把排队受理称为执行成功。

原store集成修正：flowVo/loadTemplate位于root.flowData，动作context属于非命名空间flowData模块。测试现以原包SHA校验后的Vue 2.7.16、Vuex 3.6.2、module54098构建真实Store执行save/save/push及回读失败路径，替代此前错误的平面状态假设。真实原Store测试通过；网络与无关展示集成仍为受控依赖，不宣称引擎运行验证。

本修正完整verify使用HDI_CORE_BUNDLE_DIR启用真实原Store用例，共315项测试通过、0跳过，UI Guard/构建/167个JS兼容检查通过。
