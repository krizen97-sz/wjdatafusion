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

## 活动任务状态同步

原GLOBAL_TASKRUNSTATUS_CONTROL仅轮询节点统计，原checkTaskStatusLoop达到20次后本地回退为运行中，无法覆盖平台长任务真实终态。宿主复用原Vuex watch、原Axios库及setFlowVo，在可见的活动任务（1/2/5/10）每2秒查询平台getFlowById。用户、工作区、项目、任务ID和修订必须一致；只合并权威状态字段，保留完整live flowVo和nodes/links/params。保存期间跳过读取或丢弃读回。

隐藏页面/iframe、离开任务、销毁时暂停或取消；ResizeObserver及visibility/focus恢复可见查询。失败不刷新页面、不替换画布、不推断完成，同一任务修订只提示一次。使用原裸Axios库而非旧管理实例，因为后者遇Network Error会重新加载顶层页面。没有增加节点速度、行数或其它未采集指标。

状态同步验证：16项定向测试（含3项原Store实例测试）通过；完整前端verify使用原bundle跑321项测试、0跳过，UI Guard、生产构建和167个JS兼容检查通过。原运行终态浏览器证据仍由集成任务采集。

## 原连接管理组件接入（2026-10-25）

| 页面需要 | 现有实现 | 选择 | 参考文件 | 必需定制 |
| --- | --- | --- | --- | --- |
| 从任务打开连接管理 | 原HdiTaskWorkspace、现有任务新窗口协议 | 外层el-button，独立原组件iframe视图 | HdiTaskWorkspace.vue、hdiHostBridge.js | 新窗口保留当前画布，URL严格限定connections视图 |
| 连接列表与新增/编辑 | 原73.55a627ab.js module15442、58713、79553 | 原DatasourceTable、类型选择及通用连接表单 | 原字节按bundle manifest校验，模块通过原webpack runtime装配 | 接平台HTTP契约；原授权数量展示改读真实平台连接数量，不调用原授权接口 |
| 连接字段、条件显隐、URL组合 | 原dEtlv2-datasource.jar | 原getParameters、DatasourceFormProcessor.inject、jointUrl、showVersion | OriginalConnectionForms.java | 受控一请求JVM，不启动原管理服务；原中文字典随bundle提取 |
| 连接密码与版本 | 平台CredentialCryptoService、HdiCoreKeys | 现有AES-GCM存储、原RSA传输 | governance/hdi、CredentialCryptoService | 连接按owner隔离，发布时冻结版本；公开表单仅回读密码占位 |

沿用已读的前端规范、组件目录和参考页面，外层仅增加模块入口，不新增UI库、图标库或全局样式。原Vue2/HUI仍仅在专用iframe内运行，未改原render或原资源字节。原连接资源列表/表单与平台结果抽屉分别承担配置和结果，不新增另一套节点表单。

## 原连接页的嵌入宽度适配

| 页面需要 | 现有实现 | 选择 | 参考文件 | 必需定制 |
| --- | --- | --- | --- | --- |
| 平台侧栏内仍能阅读连接卡片、操作和筛选 | 原DatasourceTable的卡片、HUI工具栏及窗口resize处理 | 保留原DOM、事件与控件，仅在iframe宿主按可用宽度换行 | HdiTaskWorkspace.vue、components/iFrame/index.vue、document/workspace/index.vue | 原卡片固定25%、右侧筛选绝对定位会压缩较窄iframe；将该组件限定为两列/单列，工具栏上下排列 |

范围只限 `task-host.html` 中原组件的 `data-v-51501056` 样式作用域；不修改bundle原字节、不新增组件或颜色、不影响画布和平台全局样式。使用原窗口resize方法重新计算列表高度。验证真实Chrome不同窗口宽度、原连接测试、表单打开及控制台，并运行UI Guard与完整前端验证。

## MySQL、RabbitMQ、SFTP 的原表单装配

| 页面需要 | 现有实现 | 选择 | 参考文件 | 必需适配 |
| --- | --- | --- | --- | --- |
| MySQL与RabbitMQ连接参数及测试 | 原数据源通用表单、原Datasource参数定义 | 原字段、URL模式、RSA提交 | 原module79553、OriginalConnectionForms、HdiCoreConnectionApi | 新类型与平台连接存储/端点约束接线 |
| SFTP文件参数 | 原FTP数据源ext2协议选择、原File节点表单 | 原SFTP选项、FileInput/FileOutput | 原File表单、HdiCoreConnectionApi | 保留原FTP形式的dbUrl，协议由原ext2/sourceType确定 |
| 插件显示与表配置开关 | 原etl_adapter_ext.ui_info、原插件adapterUIParams | 保留原字符串开关及节点表单 | HdiCoreCatalog、原GroupAdapterManageServiceImpl/PackageTaskConfigServiceImpl | 非空插件声明优先；MySQL/Rabbit缺失或空声明时读取原V1.0.63迁移中的ui_info |
| MySQL处理实现来源 | 原JDBC读写基类、用户批准的最小方言子类 | 独立平台适配包，显示“原JDBC适配” | platform-adapters/mysql、HdiCorePlatformAdapters | 仅驱动、URL前缀、标识符引号三方法；单独的manifest固定版本，不伪装原专用MySQL插件 |

复用现有平台宿主、原数据源/画布/字段组件与运行记录抽屉，不新增UI框架或另一套节点表单。原RabbitMQ数据源字典在V1.0.22新增，sort本来为NULL；原表单开关从V1.0.63取出并保持字符串类型，不凭空设置默认值。上述代码装配验证与真实浏览器输入输出验收分别记录，不把元数据读取成功当作运行成功。
