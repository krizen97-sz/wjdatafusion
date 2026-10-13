# HDI 原任务模块移植

2026-10-13，开发基线 `4d96abf`。本模块处于原件拆分和独立运行验证阶段，尚未替换线上入口，也未完成平台保存、测试和运行闭环。

## 已确认的范围

采用 HDI DETL 3.3.0 的任务列表、设计器、原节点配置、执行核心和按需插件。平台提供账号、权限、菜单和部署管理；不复制 HDI 门户和运管套件，不继续此前 Kettle/NiFi 或新建纯 Java 设计器方案。

原件来自用户指定 HDI 的安装包 `detl_3.3.0.20260310110148.zip`，大小 1,583,500,438 字节，SHA-256 为 `3bd2ef065255e9e19119b1e2ade294b32b59d47c051a0a10981496766f052859`。部署只选取核验后的原文件；不在 Git 存放安装包、现场配置、私钥、凭据或许可证，不修改原 JAR 和前端 bundle。

## UI 复用和适配边界

已检查当前治理模块、公共 iFrame、`tool/build`、`document/workspace`、前端设计规范和已安装依赖。用户明确要求保留 HDI 原 UI，因此使用独立 iframe 容纳原 Vue 2/HUI，不向平台 Vue 3 注入另一套组件库。

| 页面需求 | 已有实现 | 采用组件 | 参考文件 | 自定义范围 |
| --- | --- | --- | --- | --- |
| 任务列表、创建和筛选 | 原 TaskList | `AsyncTaskList` | 原 module-16638 | 仅原事件导航 |
| 画布、参数、测试和运行 | 原 ProcessMain | `AsyncDagMain` | 原 module-41843 | 仅宿主 props 和生命周期 |
| 节点表单 | 原 remotes/components | `./Plugins`、`./TheComponents` | 原 Federation 导出 | 无替代表单 |
| 平台页面容器 | 平台 iFrame 和治理入口 | 独立同源 iframe，外层 `el-result`、`el-button`、`v-loading` | `src/components/iFrame/index.vue`、`views/governance/workspace/index.vue` | 已接入开发分支；现有公共 iFrame 不提供安全消息/真实就绪事件，故仅这里维护宿主生命周期 |

| 原版区域 | 产品意图 | 移植方式 | 保持的内容 |
| --- | --- | --- | --- |
| 列表和设计器 | 原操作逻辑 | 直接加载原组件 | 工具栏、表格、节点、参数和状态 |
| HUI 主题 | 原元素风格 | 原 CSS 原字节 | Vue 2.7.16、HUI 2.54.3、图标和字体 |
| 平台宿主 | 融合登录与菜单 | 单独适配 | 平台权限与导航，不重画业务页面 |

原 bundle 固定 `/detl/`；同源路径必须保留。`/detl/` 本身是原 CSRF bootstrap，不能被前端 index fallback 吞掉。原 HUI 样式还需要 `static/css/188.c7f3bc5a.css` 与 `index.13312c3e.css`，不能依赖父页面样式穿过 iframe。

`data-governance/hdi-native/host/native-task-host.mjs` 只初始化原共享实例、获取原组件、传递宿主上下文、转发导航事件并卸载原 Vue 实例。它不创建替代任务模型、不生成任务 JSON、不伪造工作区/许可/执行结果。宿主上下文不是授权凭据。

## 独立执行所需的原链路

| 层 | 原件/协议 | 当前结论 |
| --- | --- | --- |
| 任务管理 | 原 `/flow`、`/workspace`、`/globalConfig` 等接口 | 原列表和画布确实依赖这些接口，不能只发布静态文件 |
| 编译和保存 | 原 `FlowVisualVo`、`FlowTemplateVo`、原插件配置 builder | 必须保留原协议，不接此前自建 graph 模型 |
| 执行 | 原 `etl-core-3.3.0.jar`、`etl-common`、`etl-adapter` 和插件 | 同一原进程完成两次不同配置的真实文件输出及一次无效配置拒绝；业务全集尚未验收 |
| 控制 | 原 ZooKeeper `/metadata/group/{groupId}/flowjson/{flowId}`、`/metadata/control/{flowId}` | 是真实运行依赖；按需随平台部署独立实例 |
| 状态 | 原 Redis 状态通道 | 可用独立实例或验证过的隔离配置，不直接共享平台 Redis DB0 |
| 持久化 | 原任务管理服务数据库 | 与平台 MySQL 直接替换的兼容性未验证；不能承诺无需数据库适配 |

原核心不是“传入 JSON 执行一次即退出”的 CLI。`EtlInstance` 是驻留进程，配置来自 `ETL_USER_DIR/conf/etl_instance.properties`。不能仅凭进程退出码判断就绪：实测 RPC 初始化失败后仍可退出 0。

原核心已在 macOS Java 17、本机独立 Redis/ZooKeeper 下进入 `runningState`，没有启动 BIC、PostgreSQL、RabbitMQ 或门户，也没有连接源 10。原 RPC 硬编码 wildcard 监听且为单向 TLS；本机测试只对该 JVM 使用 bind 地址适配，并保留 Seatbelt 回环限制。合成连接验证证明回环可达、本机 LAN 地址不可达、非许可端口不可绑定；原核心实测两个监听都在 127.0.0.1。原 JAR 摘要保持不变。这个 macOS 测试适配不纳入生产执行包；Linux 发布仍需正常进程/网络隔离验收。

这证明可以抽离执行核心，并不证明原任务管理服务已经去除所有外围依赖。原 `EtlApplication` 使用 Spring Boot 2.5.15，与平台当前 Jakarta 栈应分进程。它启动时会迁移/初始化原任务元数据库，不能指向平台现有业务库试运行。

合成验收源码与运行方法见 `data-governance/hdi-native/tests/README.md`。它使用明确的 DTO/内存元数据输入调用原配置生成器和原一次性调度器，不是通用业务编排后端。两次 CSV 均为 65 字节，摘要分别为 `736dc8280575a1e4a8603da6bbd376907e9cd29b975e9a226ab7923fd3cf6b3d` 与 `29f8689b63d94d985edfb5a9aee6e25262fe8c1beb0a77fcaa87cc6d778b74b5`；第三次原插件拒绝缺少扩展名的 line 输出，原状态为 error，未新增产物。

## 平台接入的前置条件

1. 每个原 API、静态资源和下载都必须经过平台权限校验。平台 JWT 默认只从 Authorization 读取，原 iframe 请求不会自动携带它；不能直接放通 `/detl/**`。
2. 会话桥需要短时、HttpOnly、同源 cookie，并逐次验证原平台登录和权限。上游原会话按用户隔离，不共用管理员会话。
3. 原成功响应是 `{code:"0",msg,data}`，不能送进以 `code:200` 为约定的平台 request 拦截器。
4. 权限按原接口语义映射，不能把 GET 当成只读：原任务导入保存等 GET 也可能产生写入。
5. 代理目标固定为本机任务服务；不允许请求参数指定地址，不转发浏览器 Authorization/Cookie 到外部。资源只能按原件清单服务，不能暴露安装目录、配置、日志和私钥。
6. 保留原许可、TLS 和能力校验。尚未接通的接口返回明确错误，不用空数组或固定成功响应伪装完成。

这些前置条件尚未全部实现，当前不添加一个指向源 10 的线上 iframe 作为交付。

平台侧已实现 `DataGovernanceHdiClient` 的本机身份交换客户端。它只签署平台认证用户对应的 subject，不向原端传递浏览器自报权限；目标限定 127.0.0.1，不跟随登录重定向，原会话 Cookie 仅交给服务器端后续代理使用。客户端的 8 项专项测试通过。接入开关默认关闭，测试通过不代表整个原任务服务已经就绪。

## 可重复的原前端验证

原公开资源放到 Git 外的专用目录后运行：

```sh
python3 tools/data-governance/probe_hdi_frontend.py --vendor-public /absolute/path/to/detl-public
```

浏览器访问 `http://127.0.0.1:19091/probe`。探测服务只绑定回环、只提供原公开资源，不转发任何业务接口。`/detl/` 明确返回 503；`/probe?bootstrap` 验证原初始化遇到未接通的真实接口时拒绝继续。

实测原模块导出为 `AsyncTaskList`、`AsyncDagMain`、`AsyncDagMainForView`、`AsyncAddBasicInfo`，原 Vue 2.7.16、vue-i18n 7.8.1、HUI 2.54.3 和主题 CSS 成功加载。这只证明独立宿主能加载原件，**不代表任务画布挂载、任务执行或完整业务迁移验收通过**。

上述验证已使用按需打包后的 1,456 文件产物再次执行，而非只对原完整 public 目录验证。原 bootstrap 对未配置的任务接口返回 503 并拒绝继续；未创建任何虚假任务、工作区、成功响应或测试结果。

## 原件构建和验证

```sh
python3 tools/data-governance/hdi_native_bundle.py \
  --archive /absolute/path/to/detl_3.3.0.20260310110148.zip \
  --output /absolute/path/to/new-task-module \
  --plugin etl-dataGeneratorInput-2.0.0 \
  --plugin etl-fileOutput-2.0.0
python3 tools/data-governance/hdi_native_bundle.py --verify /absolute/path/to/new-task-module
python3 -m unittest discover -s tools/data-governance/tests -p test_hdi_native_bundle.py -v
```

`--plugin` 可重复以选择后续业务所需插件，或显式使用 `--all-plugins`；不能与前者混用。`--include-task-service` 加入原任务接口 JAR、配套库和所需静态配置资源，`--include-coordinator` 加入原 ZooKeeper 库；都不复制现场运行配置、数据、密钥或激活许可。来源 ZIP 必须匹配固定版本摘要。输出必须是新目录，不能覆盖现有部署。`--verify` 校验产物相对于生成清单的完整性，不是独立的数字签名验证。

原任务服务必须沿用原启动脚本的 classpath（包含 `config/`、`dEtlv2.jar`、`lib/*`、`ext/normal/*`），不能简化成 `java -jar`。原 Manifest 中 Kafka 路径指向 lib，但实际发行和现场文件都在 ext/normal，原脚本本来就补入该目录。本模块按原路径保留，不改名伪装另一份库。

首批 7 个基础插件打包产物大小为 132,907,629 字节，包含 1,226 个原任务前端文件和配套核心/插件原件。后续含原任务服务、协调库和 3 个验收插件的候选为 1,944 文件、436,576,424 字节。打包测试 13 项、执行验收包检查 6 项通过，覆盖路径穿越、ZIP 链接、重复项、来源/前端版本不符、产物篡改、越界及覆盖现有目录拒绝。其他插件是否具有额外的本地库或外部业务依赖，要逐项执行验收；不能把“可选择复制”当成“已运行兼容”。

## 本地平台页面开发入口

从 `RuoYi-Vue3-master` 运行：

```sh
npx vite --config ../tools/data-governance/hdi_vite.config.mjs
```

此模块专用配置复用平台 Vite 配置，只增加 `/detl` 到平台后端的同源代理；不直连原任务服务，也不修改公共 Vite 配置。生产 Web 网关同样必须把 `/detl/` 路由到平台后端鉴权入口。

开发分支中的治理入口只加载原任务宿主。原生错误保留失败状态，页面通过实际 `rynew-hdi:ready` 消息结束加载；超时只会显示错误，不会伪装成功。父子消息检查同源和指定 iframe，外层复用平台反馈组件。浏览器子页面中的导入总览、告警等独立路由尚未完成接入，不能据此承诺所有原按钮都已覆盖。

平台前端 `verify:frontend` 已通过：291 项测试、构建、平台产物 Chrome 64 检查。该检查不包含独立原厂 bundle，不能推导原 HDI 模块也支持 Chrome 64；原主题和框架版本按原件保留。尚待完整页面的视觉、窄屏与操作验收。

完整验收需要：平台登录 → 原列表创建任务 → 拖入原节点并配置 → 保存重开 → 原调试 → 原运行 → 输出逐字节核对 → 停止/失败恢复 → 调度和发布版本绑定 → 断开源 10 后复验。
