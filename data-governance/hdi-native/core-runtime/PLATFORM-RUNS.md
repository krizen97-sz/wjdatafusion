# 平台运行与持久调度

本轮支持单个 RYNEW 管理进程、一个平台内置原 Core worker。当前不是多管理副本方案：平台启动时会把数据库中所有未终结运行标为 `UNKNOWN`，避免重放旧任务；多个管理 JVM 共用此数据库会相互影响，不能据本轮实现部署多副本。

## 运行与产物

平台先通过 `HdiTaskStore.reserveRun` 持久化业务 run UUID、冻结 revision、独立 native flow / instance / metadata ID 块和 execId，再申请 worker 运行目录。只有该真实预留响应可以作为编译器 `outputRootSource=worker-reserve` 的来源。输出位置不依据编译机 macOS 的同名目录推断。

原编译器生成 flowjson / schedule / stop 原字节，平台按 Base64 和完整 envelope SHA 交 worker；每次 JSON-line 请求都有随机 requestId，严格核对响应及归属。断连、错序、超时不会自动启动第二 worker，不会重发 schedule。重启后的未知任务仍保留执行屏障，需要核查。

用户必须在原 File 输出表单明确选中逻辑路径 `/平台输出`。它映射到本次 worker 私有 `run/outputs`，结果完整校验后在运行记录下载；它不是宿主机上同名物理目录。任意主机路径、空路径及别名前缀变体都不能直接执行。

完整结果要求原 worker `SUCCEEDED` 且 `finalized=true`，平台按该 owner/run 的 manifest 分块读取。每块检查 artifact ID、偏移、完整长度和 SHA 引用，文件再经现有 `DataGovernanceArtifactStore` 核验摘要并原子发布。平台下载只接受本地产物 UUID，不接受路径。worker 之后断开不会使已归档文件不可下载。

原引擎成功但收编失败时，保留真实引擎成功，并明确标记产物未发布；不能把样本、部分文件或失败后的残留输出当完整交付。当前收编在终态观察线程同步执行，适合本轮小型基础文件任务；大文件或大量并行运行需要独立持久收编阶段，不能因此丢失 EngineSuccess。

原模块吞吐量/行数尚未采集时返回 `available=false / NOT_COLLECTED`，行数为 null。注册执行目标的真实平台任务计数、worker 容量/占用与原 Core 在线指标是不同信息，不能用固定零代替未知观测。

## 原持续输入与部分结果

原RabbitMQ输入在当前消息读完后继续监听。原`no.data.max.time`只控制单次等待，没有已确认的总行数/总时长采样入口；原厂debug调度还会跳过输出。平台的“试运行”沿既有真实读写测试语义，不把这个跳过输出开关当作落地验证，也不修改原插件令它伪finish。

用户通过原界面停止后，worker必须同时证明`stopRequestedByUser=true`、本次exec的`nativeState=stop`、`state=CANCELLED`、`finalized=true`、`recoveryRequired=false`且无`errorCode`，才可将已生成文件标为`artifactCompleteness=PARTIAL`。平台再次核对这些字段，按同样分块/摘要/owner规则归档，使用单独的`publishStoppedPartial`入口；真实运行状态保持CANCELLED，归档manifest的`partial=true`，不伪造SUCCEEDED。旧完整manifest未含partial字段时按false读取。

界面显示“已停止”，输出页明确这些文件只代表停止前已处理的数据。后台关闭、EOF、超时、失败、原Stop未确认或资源清理未确认均不能发布部分结果；故障残留不变成可下载交付。执行时间仍受worker配置限制，默认300秒、范围1–86400秒。平台不自动重发已确认的消息，不承诺恰好一次。

## 周期计划

`HdiCoreSchedules` 开放原 `scheduleType=2` 的每月（unit2）、每周（unit3）、每日（unit4）。Cron 由原 `ScheduleConfigVo.toScheduleVo` 和 `CronUtil.generateEnableCronExpr` 生成，平台现有 Quartz 负责未来时间计算。时区明确为 `Asia/Shanghai`，返回给原表单的预览为该时区的 `yyyy-MM-dd HH:mm:ss`。

小时（unit5）与分钟（unit6）按原 `CronUtil.getExecExpr` 的 `N#unit` 协议以及 Core `JobManager.getScheduleBuilder` 的 CalendarIntervalScheduleBuilder 实现，持久计划使用 `kind=CALENDAR`、原表达式、interval/unit 和 anchorTime。原 `JobManager.initStartTime` 默认 startNow；平台在启用后的下一个 1 秒轮询触发首次运行，随后按持久锚点推进。它既不是整点 Cron，也不是等上一轮完成后再加间隔；例如 10:07:03 起每90分钟，后续为 11:37:03、13:07:03。运行重叠仍明确跳过该期，恢复后根据相同锚点找未来时间，不补派过去任务。

计划只绑定已发布且与预期版本相同的 immutable revision，后续编辑或发布不会暗中替换旧计划的数据定义。保存计划不执行数据任务；启用后由持久 due 时间触发。禁用只停止后续触发，当前运行另调用 stop。

原手动 `scheduleType=1` 的设置也真实持久化：`kind=MANUAL`、空 cron、enabled=false、nextFireAt=null，并在同一 Store 事务中禁用旧周期/generation。回读返回保存的原手动配置；“立即启动”由 API 另行显式调用 startRun，不能把保存成功当执行成功，也不会由 timer 自动启动手动计划。

Store 在同一事务中按 Task → Plan 顺序加锁，校验 generation / expectedFire，推进下一次时间并预留唯一触发 run。服务拿到已预留 run 后直接协调，不能再 reserve 一次。重叠运行和 `UNKNOWN` 屏障都会跳过本次触发并记录 `SKIPPED_OVERLAP`。

后台触发重新核对用户数据库状态及当前 `governance:flow:list/test` 权限，无需保持浏览器登录。用户禁用、删除或权限撤销时记 `SKIPPED_PERMISSION`。权限数据库临时不可用时不冒充权限撤销，也不派发任务。

服务启动前已经到期的触发记 `SKIPPED_RESTART`，运行时迟到超过 30 秒记 `SKIPPED_MISFIRE`，均直接计算未来时间，不补跑历史任务。已成功 claim 但平台在派发前崩溃的 QUEUED 记录重启后成为 UNKNOWN，不自动重新执行。

秒级、实时、依赖、排除窗口和额外立即全量首跑仍未开放。每项能力应在真实保存、触发、终态验证后开放，不以一个频率名称替代执行语义。

## 验证边界

运行协调测试使用本机真实 stdio 子进程验证协议中断、请求绑定和不重放，并用可控 worker 观测验证生命周期与完整产物校验。计划测试使用受控时钟、原生成规则输出和实际 H2 Store 事务测试。它们不能替代真实浏览器→平台→隔离 Linux 原 Core 的最终链路验收。

```sh
mvn -f WDF100.0/pom.xml -pl wjdatafusion-manage -am \
  -Dtest=HdiCoreSchedulesTest,HdiCoreRunsTest,HdiWorkerClientTest,HdiTaskStoreTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```
