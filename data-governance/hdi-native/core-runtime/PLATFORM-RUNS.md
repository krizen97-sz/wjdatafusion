# 平台运行与持久调度

本轮支持单个 RYNEW 管理进程、一个平台内置原 Core worker。当前不是多管理副本方案：平台启动时会把数据库中所有未终结运行标为 `UNKNOWN`，避免重放旧任务；多个管理 JVM 共用此数据库会相互影响，不能据本轮实现部署多副本。

## 运行与产物

平台先通过 `HdiTaskStore.reserveRun` 持久化业务 run UUID、冻结 revision、独立 native flow / instance / metadata ID 块和 execId，再申请 worker 运行目录。只有该真实预留响应可以作为编译器 `outputRootSource=worker-reserve` 的来源。输出位置不依据编译机 macOS 的同名目录推断。

原编译器生成 flowjson / schedule / stop 原字节，平台按 Base64 和完整 envelope SHA 交 worker；每次 JSON-line 请求都有随机 requestId，严格核对响应及归属。断连、错序、超时不会自动启动第二 worker，不会重发 schedule。重启后的未知任务仍保留执行屏障，需要核查。

用户必须在原 File 输出表单明确选中逻辑路径 `/平台输出`。它映射到本次 worker 私有 `run/outputs`，结果完整校验后在运行记录下载；它不是宿主机上同名物理目录。任意主机路径、空路径及别名前缀变体都不能直接执行。

只有原 worker `SUCCEEDED` 且 `finalized=true`，平台才按该 owner/run 的 manifest 分块读取。每块检查 artifact ID、偏移、完整长度和 SHA 引用，完整文件再经现有 `DataGovernanceArtifactStore` 核验摘要并原子发布。平台下载只接受本地产物 UUID，不接受路径。worker 之后断开不会使已归档文件不可下载。

原引擎成功但收编失败时，保留真实引擎成功，并明确标记产物未发布；不能把样本、部分文件或失败后的残留输出当完整交付。当前收编在终态观察线程同步执行，适合本轮小型基础文件任务；大文件或大量并行运行需要独立持久收编阶段，不能因此丢失 EngineSuccess。

原模块吞吐量/行数尚未采集时返回 `available=false / NOT_COLLECTED`，行数为 null。注册执行目标的真实平台任务计数、worker 容量/占用与原 Core 在线指标是不同信息，不能用固定零代替未知观测。

## 周期计划

`HdiCoreSchedules` 首批开放原 `scheduleType=2` 的每月（unit2）、每周（unit3）、每日（unit4）。Cron 由原 `ScheduleConfigVo.toScheduleVo` 和 `CronUtil.generateEnableCronExpr` 生成，平台现有 Quartz 负责未来时间计算。时区明确为 `Asia/Shanghai`，返回给原表单的预览为该时区的 `yyyy-MM-dd HH:mm:ss`。

计划只绑定已发布且与预期版本相同的 immutable revision，后续编辑或发布不会暗中替换旧计划的数据定义。保存计划不执行数据任务；启用后由持久 due 时间触发。禁用只停止后续触发，当前运行另调用 stop。

Store 在同一事务中按 Task → Plan 顺序加锁，校验 generation / expectedFire，推进下一次时间并预留唯一触发 run。服务拿到已预留 run 后直接协调，不能再 reserve 一次。重叠运行和 `UNKNOWN` 屏障都会跳过本次触发并记录 `SKIPPED_OVERLAP`。

后台触发重新核对用户数据库状态及当前 `governance:flow:list/test` 权限，无需保持浏览器登录。用户禁用、删除或权限撤销时记 `SKIPPED_PERMISSION`。权限数据库临时不可用时不冒充权限撤销，也不派发任务。

服务启动前已经到期的触发记 `SKIPPED_RESTART`，运行时迟到超过 30 秒记 `SKIPPED_MISFIRE`，均直接计算未来时间，不补跑历史任务。已成功 claim 但平台在派发前崩溃的 QUEUED 记录重启后成为 UNKNOWN，不自动重新执行。

小时/分钟/秒的原间隔语义、实时、依赖、排除窗口和立即首跑仍需按原实际调度链另行核实实现；不可将它们展示为已支持的整点 Cron。每项能力应在真实保存、触发、终态验证后开放。

## 验证边界

运行协调测试使用本机真实 stdio 子进程验证协议中断、请求绑定和不重放，并用可控 worker 观测验证生命周期与完整产物校验。计划测试使用受控时钟、原生成规则输出和实际 H2 Store 事务测试。它们不能替代真实浏览器→平台→隔离 Linux 原 Core 的最终链路验收。

```sh
mvn -f WDF100.0/pom.xml -pl wjdatafusion-manage -am \
  -Dtest=HdiCoreSchedulesTest,HdiCoreRunsTest,HdiWorkerClientTest,HdiTaskStoreTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```
