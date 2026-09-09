# 数据治理发布版本与固定批次定时任务

此模块把当前安全图和显式 JSON 输入发布为不可变版本，再将 Cron 计划绑定到该版本。输入模式为 `FIXED_JSON_BATCH`：每次运行读取发布时保存的同一份 JSON 对象或数组。它适用于有界批次验证和固定批次任务；持续数据源、Kafka 位点、FTP 交付台账和海康完整业务适配尚未接入。

## 执行与版本边界

- 发布时沿用安全执行白名单、2 至 12 节点、24 条连接、无环、一个样本输入和一个结果观察节点的校验；禁止脚本、外部系统、子流程及连接服务。
- 输入仍限制为 256 KiB 内的 JSON 对象或最多 100 条记录数组；参数仅接受已有有限 `jsonPath` 与 `requiredValue`。冻结定义保存真实组件 bundle、有效属性、覆盖参数后的配置、输入及 SHA-256。
- 版本发布后不能修改；同一用户同一流程的版本号递增。后续画布修改不会改变已发布任务；编辑计划时可显式绑定另一个已发布版本，编辑完成自动暂停。
- 执行调用现有 NiFi 隔离批次执行器，并且只重查流程所属治理根目录，不重新读取当前画布。每次执行的实际 TestRun 保存版本哈希，可查看步骤、真实输出与清理确认。
- 计划先落盘本次 `activeRunId` 再提交执行。只有实际 TestRun 已终止且 `cleanupConfirmed=true` 才释放计划的执行占用。队列满、失败、超时、EMPTY 和清理失败均保留实际状态，不把提交成功当作执行成功。

## 调度规则

已有 manage 模块提供 Quartz 依赖。协调器每秒读取一次私有计划文件，并使用 `org.quartz.CronExpression` 的 6 或 7 字段语法及显式 IANA 时区计算下一次时间，例如 `0 0 9 * * ?`、`Asia/Shanghai`。不注册 RuoYi 任意反射类任务，也不新增数据库或依赖。

新计划默认暂停。启用后 `nextRunAt` 为 UTC ISO 时间，界面可按本地时区显示。暂停只停止未来触发，正在执行的任务继续跟踪；已有测试记录提供取消入口。同一计划执行中再次触发会跳过，并累计 `skippedCount`、记录 `lastSkippedAt` 和原因。不同计划共用当前有界单线程测试队列。

延迟轮询最多处理一次当前到期触发，不追赶中间漏过的周期。服务重启将正常启用任务的下一次时间重新计算为启动之后；不会补跑重启期间遗漏的周期。带活动执行记录的计划在启动时暂停并进入 `RECOVERY_REQUIRED`，不能自动或手动重入。

`recover` 是显式恢复操作：读取上次真实 TestRun，必要时调用已有残留测试组清理，并确认终态与清理后恢复为暂停。若执行记录不存在，提交器的“先保存再创建引擎资源”约束证明该次提交未进入引擎，此时记录 `INTERRUPTED` 并恢复为暂停。读取失败、仍在执行或清理未确认时继续阻止运行。正常关闭也会为活动计划写入恢复屏障。

## API

所有返回值包在现有 `AjaxResult.data`。列表仅有元数据，版本和计划按当前登录用户 owner 隔离；管理员角色也不会直接绕过 owner。写操作日志不保存请求或响应 body。

| API | 请求/返回 |
| --- | --- |
| `GET /governance/releases?flowId=` | `ReleaseSummary[]`，不包含输入与冻结原始定义 |
| `POST /governance/releases` | `{flowId,name,inputJson,parameters}` → 版本摘要 |
| `GET /governance/releases/{id}` | `{release,inputJson,parameters}`，仅 owner 可读；仍不暴露原始引擎定义 |
| `GET /governance/schedules?flowId=` | `ScheduleSummary[]` |
| `POST /governance/schedules` | `{name,releaseId,cron,timeZone}` → 默认暂停计划 |
| `PUT /governance/schedules/{id}` | `{name,releaseId,cron,timeZone,revision}` → 更新并暂停；执行中/待恢复时不可修改 |
| `POST /governance/schedules/{id}/state` | `{enabled,revision}`；修订号冲突返回应用码 409 |
| `POST /governance/schedules/{id}/run` | 无 body → 完整 `TestRun`（`id` 是真实执行记录） |
| `POST /governance/schedules/{id}/recover` | 无 body → 恢复后的计划摘要 |

查询沿用 `governance:flow:list`；发布和编辑沿用 `governance:flow:edit`；启停、执行、恢复沿用 `governance:flow:test`。不需要数据库升级。

计划摘要包含 `id/revision/name/releaseId/flowId/cron/timeZone/enabled/nextRunAt/status/activeRunId/lastRunId/lastRunStatus/lastRunAt/lastFinishedAt/lastError/skippedCount/lastSkippedAt/lastSkippedReason/recoveryRequired/createdAt/updatedAt/inputMode`。`status` 是计划状态，最近执行结果以 `lastRunStatus` 为准。

## 存储与运行边界

版本和计划保存于 `${data-governance.storage-dir}/schedules/`；目录 700、文件 600、随机临时文件 + fsync + 原子替换，独立文件锁保证单实例。版本创建后不可覆盖。拒绝无效 UUID、符号链接、超过 4 MiB 的文件；单用户最多 500 个版本和 100 个计划，读取每类最多 10000 条记录。版本列表与发布时的版本号统计逐文件消费，仅保留摘要或计数，避免将所有冻结输入和定义同时载入内存。运行历史同样逐文件读取轻量元数据；仅详情查询或重启恢复活动记录时读取完整输入、定义与样本，恢复时保留原始快照。

未配置引擎时不启动调度线程、不自动访问 NiFi，也不自动扫描存储。无需启用该功能的既有部署保持默认配置。当前文件存储不支持多副本部署；迁移到数据库/持久 Quartz 时需要保留同样的版本绑定、执行占用、恢复和清理约束。

## 验证

```bash
mvn -f WDF100.0/pom.xml -pl wjdatafusion-manage -am \
  -Dtest=DataGovernanceSchedulerTest,DataGovernanceSnapshotTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

单元测试覆盖 owner 隔离、不可变版本、不泄露列表输入、文件权限/锁/路径、Cron/时区/修订号、默认暂停、实际终态与清理、非并发跳过、恢复屏障、重启不补跑、提交前持久 intent、冻结快照复用与篡改校验。

`DataGovernanceScheduleLiveEngineTest` 使用显式 `governance.nifi.smoke=true` 和现有 smoke 连接参数，仅在新建独立 NiFi 根组运行合成样本：发布 v1 后改变画布并发布 v2，推进可注入时钟触发 Cron 并验证实际输出仍为 v1；暂停不触发；显式改绑 v2 后手动运行得到 v2；两次均确认真实终态与清理。结束前校验根组所有权标记、删除临时根，并以 404 确认清理。
