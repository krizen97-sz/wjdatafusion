# 数据治理业务与执行边界

## 先确认当前运行模式

仓库同时保留原生HDI、旧HDI管理端桥接、NiFi、Kettle、Kafka及交付实验链。默认前端当前工作台读 `views/governance/workspace/index.vue` 与 `views/governance/hdi`；**是否真正启用**由部署配置与资源状态决定，不能根据代码存在就宣称引擎已运行。

当前已集成主链为“原任务核心、管理接本平台”：保留原DFP画布/表单/配置生成器/执行核心，RYNEW管理用户、任务、版本、连接、权限、调度、记录和归档。权威入口 `data-governance/hdi-native/README.md`，不要拿早期 `DATA_GOVERNANCE_*` 说明替代当前主链。

## 后端对象与职责

Java根为 `WDF100.0/wjdatafusion-manage/src/main/java/com/hm/manage/service/governance/hdi`。

| 入口/对象 | 职责和须保持的约束 |
| --- | --- |
| `DataGovernanceHdiCoreController`、`HdiCoreApi` | `/detl/**`原页面HTTP契约适配，继续检查平台用户与业务权限 |
| `DataGovernanceHdiCoreSessionController`、`HdiCoreSessions` | 宿主与原页面的会话、用户边界；不复制原站登录身份 |
| `HdiTaskStore` | 明确masterDataSource；用户所属任务、不可变revision、期望版本冲突、run及计划持久化 |
| `HdiCoreConnections`、`HdiCoreConnectionApi` | 用户所属连接、加密连接revision、真实表/主题发现与选择 |
| `HdiCoreTableMetadata` | 精确对象身份；Foo/foo不同对象，不能统一转小写或仅比较显示名 |
| `HdiCoreCatalog`、`HdiCoreAssets` | 已审核bundle/宿主及支持插件目录，不能暴露未支持能力 |
| `HdiCompilerClient` | 原编译器生成配置、原表单与元数据契约；不自行翻译成另一种引擎任务 |
| `HdiCoreRuns`、`HdiWorkerClient` | 预留独立运行ID、冻结版本、真实worker提交/状态/停止/日志/文件 |
| `HdiCoreSchedules` | Quartz时间语义计算+数据库冻结计划和领取；不是普通sys_job CRUD计划 |
| `HdiCoreConnectionProperties` | 部署端点、FTP被动范围、Kafka广播broker网络策略，不从画布输入放宽 |

十表：`dg_hdi_workspace/counter/task/revision/run/preference/schedule/connection/connection_revision/connection_table`。文件归档、编译目录和worker状态在Git外，不能仅备份十张表就认为完整恢复。

## 任务与运行逻辑

```text
用户所属任务 → 保存/不可变revision → 显式发布或冻结计划
→ 校验任务/连接/适配包版本 → reserveRun → 编译原配置 → worker提交
→ 独立原生执行ID → 状态/日志/停止 → 终态及文件摘要 → 下载/目标回读
```

- 编辑/发布需核对expectedRevision，旧窗口冲突应提示，不做最后写入覆盖。原生任务不能静默交给旧引擎。
- 运行保存任务、revision、mode、native ID等身份。调度领取和已预留状态变化不能通过“再提交一次”消除不确定状态。
- `startDebug` 试运行也实际执行输出；Kafka可能提交消费位置、RabbitMQ会确认消息。使用专门测试目标/队列/消费组，不能宣称只读或仅预览。
- 输入持续监听时的主动停止不是成功。满足当前明确用户停止、无错误/恢复标记及产物完整性时可发布“部分结果”；超时、崩溃、强杀或未知运行不能套用这一语义。
- 原引擎finish不保证所有行成功。当前页面区分“引擎完成”和“完成·待核对”，应结合原日志、产物及输出目标回读，空指标不能解释成0行。
- 异常断连/重启后的未知运行不自动重放；重试可能重复真实输出。查run和worker实际状态后再决定恢复策略。

## 支持能力与部署

核验基线支持最多32节点单链，选定原插件包括数据生成、字段映射、PostgreSQL、文件/FTP/SFTP、Kafka和RabbitMQ；MySQL为明确的原JDBC最小适配，不声称厂商专用插件。分支/环、CDC、依赖任务及未验收格式/认证不能因为原表单有选项就开放；以compiler拒绝规则及当前文档为准。

`data-governance-hdi-core`参数：enabled、bundle-dir、host-dir、state-dir、public-origin、compiler-command、worker-command；启用MySQL时适配目录与manifest摘要成对，目录、compiler argv、worker和冻结任务中摘要必须一致。命令使用明确argv数组，不拼shell。

当前验收分工为：平台/编译器/元数据工具Java17，Linux x86_64原Core使用Java11；需要Python3、namespace隔离及已有Redis。安装准备器 `tools/data-governance/hdi_core_setup.py` 不启动业务，`PREPARED/VERIFIED`不等于运行验收。读取：

- `data-governance/hdi-native/core-runtime/SETUP.md`
- `data-governance/hdi-native/core-runtime/CONNECTIONS.md`
- `data-governance/hdi-native/core-runtime/METADATA-NETWORK.md`
- `data-governance/hdi-native/core-runtime/PLATFORM-RUNS.md`

不启用原生模块时不把整套Core依赖塞进基础Java部署。启用后配置受控外部端点和私有目录；资源hello、实际运行、停止、下载摘要及目标回读分别验收。

## 旧链路如何定位

| 链路 | 代码入口 | 注意 |
| --- | --- | --- |
| NiFi/早期本地流程 | `DataGovernanceService/Engine/NifiClient/SafeFlow` | 独立模型、运行记录和安全校验；不重启未发布实验入口 |
| Kettle | `DataGovernanceKettleService/Client/Scheduler/Xml` | worker、任务定义、日志/结果和文件存储各自契约 |
| Kafka批次任务 | `DataGovernanceKafkaService/Execution/Transport` | 与巡检位点检查、白名单拉取是不同客户端，不能通用推断认证或提交语义 |
| 定时交付 | `DataGovernanceScheduler/ScheduleDelivery/FtpDelivery` | 运行后输出与交付状态分开，不能用调度触发成功代替接收端成功 |
| 旧HDI管理桥 | `DataGovernanceHdiClient/Gateway` | 当前原生任务不依赖原管理后台、Agent或外站身份 |

单元/契约测试在 `src/test/java/com/hm/manage/service/governance` 和 `.../hdi`，前端 `npm run test:governance`。真实Core、FTP、Kafka等环境测试按当前开关和明确目标执行，不以跳过充当支持证据。

回退先禁用计划并确认运行终态，停止本模块owned worker，再回退成套程序和配置。保留任务/连接表、密钥、私有状态及归档，不DROP或清空，也不自动把这些任务切给旧引擎执行。
