# 数据编排可用性调研与改进路线

研究日期：2026-09-16。审查基线：`312d406` / v4.8.0。范围是本地 RYNEW 前端操作、后端实现，以及四家国内产品和八个官方 GitHub 仓库。随后按用户“基于本地代码进行分析和开发”的指示，实施首批本地修复；不发布到 250。

## 1. 核心判断

当前项目已经具备真实的原 Kettle 执行、XML 往返、节点预览、运行记录、冻结调度和海康 FTP 适配基础。但使用这些能力仍要求用户理解原插件参数、执行环境和异常状态；它离“业务人员能独立搭建并长期维护任务”的数据开发产品还有明显距离。

首要问题有三层：

1. **入口和配置门槛高。**插件目录大于实际可用范围，通用 XML 表单没有把业务字段与引擎参数分层，连接与字段发现没有连贯的操作路径。
2. **测试反馈不够可信、可见和可解释。**本轮实际复现了“预览成功但结果被布局挤到视口外”；代码还存在上传文件未进入转换字段检查、字段未完整解析却提示成功等问题。
3. **生产管理模型不完整。**缺独立连接资产、可浏览版本、发布对象、运行队列、漏跑记录、全局运行中心和交付核查；不能靠增加节点数量弥补。

建议保留原海康兼容执行通道，优先建设它上面的产品和控制层。图编辑组件、调度器、第二执行引擎应按具体瓶颈引入，避免再次更换整套平台后重新验证所有业务语义。

## 2. 取证方法与边界

- 本地及远程 Git main 查询均为 `312d406`，基线工作区干净。代码审查覆盖 Kettle 工作台、配置/结果/调度组件、平台 Service/Client/Scheduler、Python broker、Linux 适配和 Java 原生执行器。
- Chrome 使用同版本本地前端及已有隔离 API/账号。操作包括进入工作台、打开转换、查看排序参数、工具目录、连接表单、实际节点预览和定时入口。未修改既有任务定义、连接或计划，新增一次合成数据预览记录。
- 当前预览 `1aafb85c-387d-4e4f-ab53-65f14d0c5406` 实际得到 `web:ALICE`、`web:BOB`、`web:张三`，下游文件输出未执行。未读取浏览器存储或隐藏应用变量。
- 250 只核对公网入口；其 index SHA 与 v4.8.0 发布一致。登录后流程没有复验，用户随后指定本地分析开发，因此本报告不以旧上线记录代替当前线上验收。
- 厂商与开源研究使用官方文档、GitHub 发布/许可证/固定 SHA 源码；没有实际试用这些厂商控制台，也没有上游产品性能基准。本文不作市场份额排名。
- Graphify 对 12 个前端文件做了结构辅助提取：316 个节点、658 条边，提取器 LLM 输入/输出 token 为 0；源码及浏览器证据仍是判断依据。

## 3. 前端：沿实际任务路径发现的问题

| 步骤 | 当前体验与证据 | 影响 | 优先级 |
| --- | --- | --- | --- |
| 进入工作台 | 新建、导入和任务列表可用；空画布、空配置栏占主要空间，没有带样本的入门任务 | 用户不清楚先准备连接、上传文件还是拖节点 | P1 |
| 选工具 | 输入组47项，较少使用的工具在前，HBP等不可用项混列；缺可用范围筛选 | 用户要先研究插件兼容性，再完成业务 | P0 |
| 配参数 | SortRows 默认先显示目录、prefix、sort_size、free_memory、compress，真正排序字段靠后 | 常用操作也需要熟悉引擎内部字段 | P0 |
| 建连接 | 任务内可保存服务器/端口/库/账号，没有当前 Worker 的连通测试或库表浏览 | 填对参数后仍不知能否执行，字段映射靠手写 | P0/P1 |
| 获取字段 | `validate()` 只按 valid 判断成功，未充分消费 fieldsResolved/fieldDiagnostics | 不完整字段也可能获得成功提示，错误被推迟到运行 | P0 |
| 节点预览 | 原生执行确实成功，但长表单撑高 grid，结果面板位于屏幕之外 | 用户认为测试没有返回结果 | P0 |
| 编辑复杂图 | 已有拖动、缩放、端口、分支与键盘移动；缺多选、复制粘贴、撤销重做、命令事务 | 大图反复调整和误操作恢复成本高 | P1 |
| 查看结果 | 已有输入/输出/错误行、字段/指标/日志/文件，但观察样本固定20行，缺采样策略与并排对照 | 用户难判断空结果、截断、无匹配和真正无数据 | P1 |
| 调度运维 | 冻结计划、Cron生成、显式启停正确；入口藏在单任务抽屉，版本号缺差异解释 | 任务多后难找到失败、漏跑、待发布和需核查的任务 | P1 |

本轮布局缺陷有实际尺寸证据：视口 `1728×996`，工作区高度 `756px`，内部 main 却高 `1301px`，结果面板在 `top=1192px / bottom=1512px`。固定高度 Grid 的子项最小内容尺寸被长配置撑大，画布和结果随之下移。这是可复现的功能缺陷，不只是视觉偏好。

关键源码位置（均指基线）：

- `KettleWorkbench.vue:31,131–140`：目录分组与工具禁用；`:271–274`：字段检查反馈；`:345–364`：桌面 grid 布局。
- `KettleNodeConfig.vue:9–35`、`KettleGenericConfig.vue:3–25,37`：专用/通用/原生参数及上下文；`KettleValueInput.vue:8–12`：代码文本框和自由输入。
- `KettleWorkbench.vue:81–85,149,331–335`：任务内连接；`:312,324`：固定采样与历史结果展示。
- `FlowDiagram.vue:151–193`：已有键盘/缩放/布局；`KettleSchedules.vue:3,19–27`：已有冻结提示与 Cron 配置。

现有的展开配置、未保存提示、自动应用节点修改、过期结果提示、权限控制和未知提交屏障都应保留。前端优化的重点应是让这些真实能力可理解、可发现，而非更换配色和图标。

## 4. 后端：为什么前端很难做得顺

| 当前约束 | 代码事实 | 产品后果 | 改进方向 |
| --- | --- | --- | --- |
| 工具可用程度缺契约 | 可构造/可加载推导可尝试执行，executionValidated统一false；Job仅SPECIAL/TRANS/FTP_PUT | 名称出现在目录却不能完整配置、预览或运行 | 分别声明配置/元数据/预览/运行/验收能力，建立少量完整工具闭环 |
| 转换校验没有上传文件 | Service发inputFiles，但broker validate路由及方法未传入；Job路径已有传递 | 依赖输入文件的字段发现与正式运行不在相同上下文 | 共用不可变ExecutionContext，保持load离线，校验明确有界 |
| Job隐藏120秒上限 | Java默认120秒，broker可调至更长，但启动命令不传内层属性 | 大任务被意外截停，运维调整看似无效 | 唯一可信超时预算，冻结到运行身份；区分排队/启动/执行/停止 |
| 四槽共用且无持久排队 | capabilities、load、validate、preview、run共用4个进程槽，满载返回400 | 正式任务占满时，字段和调试也报错 | 交互/批任务配额、持久队列、明确QUEUED与取消契约 |
| 连接与运行许可脱节 | 原生XML内嵌连接；可信出网仍由后台文件登记；旧治理已有PG连接服务未接入 | 用户反复填写，管理员另行开通，无法从界面定位原因 | 连接注册表、secretRef、执行环境、元数据和受控端点策略 |
| 错误跨边界丢原因 | Client非2xx只保留状态，Service给泛化说明 | 满载、缺文件、缺依赖、认证错误难以区分 | code/stage/node/field/retryability/traceId/修复建议统一返回并脱敏 |
| revision不等于版本库 | 保存覆盖当前定义；运行/计划快照存在但无完整revisions/diff/restore接口 | 未运行过的旧草稿未必能恢复，发布状态不清 | Draft、Revision、Release与Plan分离，保留冻结资产和原XML |
| 调度缺时隙实例 | 错过窗口≥1秒跳过，失败暂停，缺漏跑记录和补数/告警模型 | 看不到为什么没跑，也不能明确补哪一天 | 持久ScheduleSlot、misfire策略、告警和审计式补数 |
| 单机文件存储及全量扫描 | 独占文件锁、扫描定义/运行JSON、附件随快照复制 | 任务增长后的性能/协作/多实例能力受限 | 先索引分页、内容寻址资产，再独立评审事务化迁移 |
| 日志和指标留存未闭环 | 指标100ms发送，事件常驻内存；前500条日志上限；产物状态轮询可能hash文件 | 长任务诊断、内存和磁盘增长难管理 | 分段事件、错误优先保留、指标聚合、产物索引、留存策略 |
| 外部交付仍需核查 | 已有UNKNOWN防重放与FTP批次manifest，但无交付账本页面 | 停止后不知道哪些已写，盲目重跑会重复副作用 | 独立交付核查/补偿，确定未交付才补送，不为补FTP重消费Kafka |

详细事实和17项发现见分项后端报告。未做并发和大规模历史压测，因此上述扩展性风险不是已测出的性能数字。

应继续保留：原 KTR/KJB 未知字段往返、原插件执行、秘密槽和 owner 隔离、非 root 沙箱、原包/镜像哈希、输入输出目录隔离、冻结快照、幂等 requestId、nonce/CID 停止身份、未知结果不自动重放，以及原 FTP 80 文件分批适配。它们是后续产品化的基础。

## 5. 国内产品：重点学习怎样完成任务

比较对象按功能相关性选择，不是市场份额排名。行级 ETL、任务级 DAG、持续 CDC 的目标不同，不能只用“支持拖拽”打勾比较。

| 产品 | 值得借鉴的具体机制 | 在本项目的落点 | 不应照搬 |
| --- | --- | --- | --- |
| FineDataLink | 外层作业/内部转换分层，逐算子样本预览、字段类型与分支结果、明确开发/生产版本 | 常用节点专用表单、字段驱动参数、输入输出对照；显示草稿和计划版本差异 | 不必一次上齐实时任务、管道、服务等所有入口 |
| DataWorks | 实际执行资源的连通准备、单表同步向导、字段映射、重跑策略与发布检查 | 连接测试从Worker发起；简单搬运用向导、复杂转换用图；写出副作用预检 | 云资源组/计费概念及全量云平台组织结构 |
| WeData | 表单/画布/脚本分工，任务和工作流层级，版本包及独立运行实例 | 配置、图、原生高级模式有边界；发布冻结资源；历史运行绑定当次图 | 不能承诺任意高级脚本都能无损转回简单表单 |
| TapData | 连接/模型/时区/类型预检查，全量与增量阶段、位点/延迟/错误诊断 | 结构化问题面板与启动准备；未来持续任务显示真实推进状态 | 不把Kettle有Kafka输入直接称为完整CDC平台 |

一手依据：[FineDataLink 入门](https://help.fanruan.com/finedatalink/doc-view-6.html)、[样本设置](https://help.fanruan.com/finedatalink/doc-view-222.html)、[字段表达式](https://help.fanruan.com/finedatalink/doc-view-286.html)、[开发生产模式](https://help.fanruan.com/finedatalink/doc-view-678.html)；[DataWorks 单表向导](https://help.aliyun.com/zh/dataworks/user-guide/configure-a-batch-synchronization-node-by-using-the-codeless-ui)、[重跑策略](https://help.aliyun.com/zh/dataworks/user-guide/scheduling-policy-configurations)；[WeData 单任务](https://cloud.tencent.com/document/product/1267/110729)、[运行实例](https://cloud.tencent.com/document/product/1267/131253)；[TapData 预检查](https://docs.tapdata.net/user-guide/pre-check/)、[任务监控](https://docs.tapdata.net/user-guide/data-development/monitor-task/)。

有三点需要谨慎理解厂商文档：DataWorks旧概述和新版单表向导的转换能力范围不同；WeData发布中心仍有白名单说明；TapData同一文档导航涉及Cloud/Enterprise/Community，不能假设所有功能在社区版相同。FineDataLink官方也指出重试可能产生重复写入，本项目应保留对副作用的严格核查。[FineDataLink 容错](https://help.fanruan.com/finedatalink/doc-view-620.html)、[WeData 发布中心](https://cloud.tencent.com/document/product/1267/109575)。

## 6. GitHub：按层选组件，避免整体替换的错觉

以下是2026-09-16检索快照；版本号和维护状态可能随后变化。仓库许可证不自动覆盖海康包、JDBC和附带依赖。

| 项目 | 当时可验证版本 | 用途及建议 | 主要限制 |
| --- | --- | --- | --- |
| Apache Hop / Hop Web | Hop 2.19.0，2026-08-17，Java21，Apache-2.0 | 学习原生ETL预览、测试数据、项目环境；远期迁移PoC | Hop Web官方明确仍为开发中；Kettle导入不保证海康二进制和行为兼容 |
| Apache SeaTunnel | 2.3.13，2026-03-14，Apache-2.0 | 学习Source/Transform/Sink能力和OptionRule；新增CDC/标准同步候选 | 不能等价替换原Kettle行处理/位点/交付语义 |
| SeaTunnel Web | 1.0.2，2024-10-24，Apache-2.0 | 参考动态表单、数据源模型 | 默认分支最近提交观察为2025-07-24；其设计器与新引擎内置监控UI不同 |
| DolphinScheduler | 3.4.3，2026-09-06，Apache-2.0 | 有跨任务依赖、补数、SLA、多Worker需求后接调度层 | 不是行级ETL引擎；不能以Shell重试绕开幂等及UNKNOWN |
| Apache NiFi | 2.12.0，2026-09-13，Apache-2.0 | 学习队列/背压/数据追踪和交付证据；保留既有用途 | Kettle→FlowFile迁移成本高；不作为新默认设计入口 |
| AntV X6 | GitHub Release v3.1.7，MIT | 图编辑PoC优先候选：历史、剪贴板、多选、对齐等 | 双向同步和原XML编辑事务仍需自己实现；需验证Chrome64基线 |
| Vue Flow | core v1.48.2，MIT | Vue3契合，作为X6对照候选 | 完整业务撤销、布局和字段语义不由组件自动解决 |
| Addax | 6.0.13，2026-08-31，Apache-2.0 | 较轻的标准批量搬运备选 | 引擎本身不是完整Web设计平台；若选择SeaTunnel通常不必再引入 |

核心一手链接：[Hop 下载及Web声明](https://hop.apache.org/download/)、[Hop Web](https://hop.apache.org/manual/latest/hop-gui/hop-web.html)、[SeaTunnel 引擎UI](https://seatunnel.apache.org/docs/2.3.13/engines/zeta/web-ui/)、[SeaTunnel Web Release](https://github.com/apache/seatunnel-web/releases/tag/1.0.2)、[OptionRule](https://github.com/apache/seatunnel/blob/b37af3a9bf634735cabe4908014c2d390b288558/seatunnel-api/src/main/java/org/apache/seatunnel/api/configuration/util/OptionRule.java)、[DolphinScheduler 3.4.3](https://github.com/apache/dolphinscheduler/releases/tag/3.4.3)、[NiFi 下载](https://nifi.apache.org/download/)、[X6](https://github.com/antvis/X6)、[Vue Flow](https://github.com/bcakmakoglu/vue-flow)、[Addax](https://github.com/wgzhao/Addax)。

NiFi 官方已在2026年2月弃用独立 NiFi Registry；后续版本治理应核实 Git-based Flow Registry Clients，不按旧教程新建 Registry 服务。SeaTunnel 引擎内置UI的 Jobs/Workers/Master 监控不能作为独立Web设计器已更新的证据。

**当前推荐：保持一个用户工作台、一个海康默认执行通道；选择性复用组件和机制。** X6/Vue Flow二选一，先PoC后决策；DolphinScheduler按调度需求引入；SeaTunnel/Addax只评估新增通用同步。没有证据证明现在整体搬到其中某个产品比补齐现有控制层更省成本。

## 7. 目标体验与支撑架构

用户应能顺序完成：选择任务目标 → 选择已授权数据源 → 获取表与字段/输入样本 → 配置业务规则 → 对照节点输入输出 → 有界试运行 → 发布固定版本 → 调度 → 追踪运行与处理失败。

| 层次 | 要形成的稳定契约 | 现有能力怎样承接 |
| --- | --- | --- |
| 编辑工作台 | 常用工具、语义表单、字段映射、可撤销编辑、验证问题定位 | Element Plus与现有图组件继续复用；分离工作台职责 |
| 元数据与工具 | Connection、SecretRef、NodeCapability、ConfigSchema、SchemaResolver、PreviewPolicy | 原Meta负责XML编解码和引擎字段；语义规则位于其上 |
| 发布编译 | Draft/Revision/Release、ExecutionContext、插件/连接/资产摘要 | 复用稳定ID、冻结XML/子任务/输入/时区；未知字段保真 |
| 队列调度 | RunIntent、Queue/Quota、ScheduleSlot、Lease、Backfill、Alert | 保留幂等键和未知状态屏障；调度引用不可变release |
| 引擎适配 | validate/preview/run/stop/reconcile/capabilities | 原海康Kettle默认；新通用引擎独立profile和明确支持范围 |
| 运行交付 | NodeExecution、事件游标、指标窗口、Artifact、DeliveryLedger | 复用原行/日志/状态和FTP manifest；补可查询可恢复模型 |

不要让图组件自己的JSON替代原XML成为未经验证的新执行标准。当前阶段采用保真原XML加语义视图，编辑操作通过同一编解码层写回；以后需要统一IR时，也须保留扩展字段并通过原图多轮往返和执行差分验收。

## 8. 分阶段优化与验收标准

时间是规划估算，不是完成承诺；假设1名前端、1名后端及共享测试支持，实际取决于连接器、样本和环境。

| 阶段 | 优先交付 | 参考周期 | 验收门槛 |
| --- | --- | --- | --- |
| 0：本轮止痛 | 布局、工具分层、排序表单、参数分层、字段诊断、校验输入与超时一致 | 本轮实施，结果另记 | 同样4节点预览可见；未知参数保留；不完整字段不报全成功；Job超120秒有真实证明 |
| 1：把高频任务做顺 | 连接中心与Worker连通测试、库表字段、CSV/JSON样本推断、字段映射、输入输出对照、10类结构化错误 | 2–4周 | 5名目标用户中至少4人，10分钟内无XML/研发帮助完成3–5节点流程；错误可定位，改条件能改变真实结果 |
| 2：日常生产闭环 | 草稿/发布版本/差异、运行中心、队列配额、计划时隙/漏跑/告警、日志与资产留存、交付核查 | 3–5周 | 20并发请求有确定接收/排队/取消；响应丢失/重启不重复外写；草稿变化不改变计划；失败可解释 |
| 3：编辑效率及复用 | 图框架PoC、撤销复制、多选、子转换模板、项目权限、环境映射 | 与阶段2按需求并行 | 原图20次编辑往返保持未知XML与输出一致；200节点/500边有测量；一次业务修改一次撤销 |
| 4：有依据地扩展 | HBP bundle受控加载、第二引擎、持续CDC、质量/血缘 | 由真实业务触发 | 每插件配置/字段/预览/运行/取消/恢复逐层通过；位点、DDL、交付等价性单独证明 |

这里的10分钟、80%成功率、20并发等是建议验收目标，尚未开展用户研究或容量基准，不是本轮成绩。

三个值得做的可证伪PoC：

- **画布对照**：同一XML适配与表单接X6、Vue Flow，验证复杂分支、复制/撤销、200节点、Chrome64。任一出现XML丢失、语义变化或用户完成率无明显收益，则不替换。
- **调度恢复**：DolphinScheduler通过幂等API调原执行器，注入提交响应丢失、双方重启、FTP回执丢失。若只能用重跑整个Shell恢复，则不作为生产入口。
- **Hop迁移差分**：固定版本导入脱敏原图，逐节点报告缺失和默认值变化，以相同样本、位点、回写字节、文件哈希比较。标准子图通过只代表该类任务，不代表海康全图兼容。

## 9. 本轮实施记录

本地候选版本为 v4.8.1。前端与Worker在独立worktree开发，整合后记录实际提交、自动化与浏览器证据；没有数据库迁移或线上部署。最终结果以同名候选验收记录为准，研究建议不能当成已实现功能。

## 10. 分项与原始证据

完整分项在本地运行目录 `rynew-runtime/research/20260916-etl-usability/`：

- `frontend-audit.md`：本轮8步界面观察、代码对应、截图和局限。
- `backend-audit.md`：17条后端发现及精确文件/行号。
- `domestic-products.md`：4家厂商、25个官方页面、版本及商业范围差异。
- `open-source.md`：8个仓库、固定SHA模块、维护/许可证、集成成本和PoC。
- `github-project-evidence.json`：官方仓库/发布快照。
- `baseline-layout-and-preview.json`、`screenshots/`：本轮DOM尺寸、实际结果和截图。

本报告的判断以本轮源码、页面和官方资料为依据；历史记忆仅用于定位已有工作。所有生产覆盖、性能与最终用户任务完成率，都必须通过对应环境的独立验收。
