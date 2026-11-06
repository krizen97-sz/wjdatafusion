# 自动巡检业务链路

主要Service：`WDF100.0/wjdatafusion-manage/src/main/java/com/hm/manage/service/impl/SupportAutoInspectionServiceImpl.java`。不要把名称相近的 `SupportTimInspection*` 历史兼容代码当作本模块。

## 对象与存储

| 对象 | 表 | 作用 |
| --- | --- | --- |
| 工具 | `sup_auto_inspection_tool` | 工具code与参数元数据，当前完整新库有14种内置工具 |
| 目标 | `sup_auto_inspection_target` | 连接地址/服务器引用/凭据/参数 |
| 模板、步骤、步骤目标 | `sup_auto_inspection_template`、`_template_step`、`_template_step_target` | 有序步骤、工具、目标绑定、阈值/比较/执行策略 |
| 计划 | `sup_auto_inspection_plan` | 模板、现场/主平台范围、cron、模式及健康规则、jobId |
| 本次记录/步骤/目标结果 | `sup_auto_inspection_record`、`_step_result`、`_target_result` | 执行时快照和逐层证据 |
| 比较探针状态 | `sup_auto_inspection_probe_state` | plan/step/target/tool维度历史取值与统计窗口 |
| 每日健康 | `sup_auto_inspection_health_daily` | 日统计、应采样/实际采样、异常等聚合 |

表名前缀 `_...` 在上表续接 `sup_auto_inspection`。Mapper为 `SupportAutoInspectionMapper.xml`；驾驶舱指标另看 `SupportAutoInspectionMetricsServiceImpl` 与Metrics Mapper。

## 权限和入口

manage控制器 `SupportAutoInspectionController` 暴露 `/support/autoInspection` 下的tools、targets、templates、dashboard、health、records、reports/export。目标/模板/运行/导出权限分别检查，不把查询权限视为运行权限。服务器明文凭据另需 `support:credential:viewPlain`。

**计划控制器位于quartz模块**：`SupportAutoInspectionPlanController` 的 `/support/autoInspection/plans`，通过 `SupportAutoInspectionPlanScheduleService` 保存/启停/删除计划与Quartz job。手改计划表状态会绕过调度同步。

`syncQuartzJob` 设置 `supportAutoInspectionTask.runPlan(<id>L)`、不补偿错过触发、禁止并发的任务语义。调度入口再到 `SupportAutoInspectionTask` → `runScheduledPlan`；手动模板/计划分别到 `runManualTemplate/runManualPlan`。

## 单次执行

`runInspection`：

1. 读取模板，拒绝停用或无步骤；解析计划模式/时间窗。
2. 高频自动执行只在有效时间窗采样，以plan和对齐slot检查已有记录，并通过专用插入防止重复。手动运行与自动高频统计语义不同，不能统一改成同一个模式。
3. 写主记录的模板、计划、现场/主平台名、范围、时间等执行快照。
4. 依序复制步骤为结果；关闭步骤跳过；前序异常且STOP时后续步骤明确跳过；启用但无目标记异常。
5. 对启用目标在本次范围内按工具分派，实际执行前临时取明文凭据。按步骤策略重试，再判阈值/比较规则。
6. 持久化目标结果、步骤结果和总摘要；需要时刷新探针状态/每日健康，按配置清理过期高频记录。

`AutoInspectionExecutionPolicy` 把retryCount限制0–3、间隔1–60秒，异常后CONTINUE或STOP。重试不是无限补跑；失败、未配目标、跳过和正常必须保留区别。

工具契约集中在 `AutoInspectionToolContract` 及Service分派；新增工具应同步参数元数据、BO/VO/前端表单、执行处理器、结果详情与测试，不能只新增下拉选项。

## 取值、比较与健康度

关键纯逻辑：`AutoInspectionValueComparison`、`AutoInspectionComparisonWindow`、`AutoInspectionPlanHealthConfig`、`AutoInspectionPlanScheduleMatcher`。

- 固定阈值与上次取值比较分别处理；当前值、前值、增量、规则/公式、scope和window应能从结果解释。
- `applyPreviousComparison` 用plan/step/target/tool读取探针状态，并核对统计窗口；首样本和周期切换建立基线，按现有评估器处理，不能一律归为“未检测”拉低健康度。
- 无计划的模板手动运行只展示取值，不写计划历史基线；不要把测试模板的取值污染正式计划。
- 日健康应结合有效窗口、应采样槽位、实际结果和当前规则，不是简单平均所有记录。数据缺失、计划停用、还未到执行时间和真实故障不可互相伪装。
- 驾驶舱→记录→步骤→目标，日健康→样本，保留可追溯ID和执行时范围；配置后来修改不应抹去历史快照。

涉及聚合先读 `SupportAutoInspectionMetricsServiceImpl` 和 `AutoInspectionPeriodReport`。当前v4.17.2周月报重在所选周期汇总和图表，月报返回docx；前后端文件类型/命名须成套，不恢复成逐记录堆叠或旧zip契约。

## 数据库工具与消息工具的边界

数据库工具在 `checkDatabaseQuery` 使用 `AutoInspectionReadOnlyQueryGuard`，只收单条SELECT/WITH，拒绝写入、锁表、高风险函数/多语句；数据库账号仍应只读。数据治理可写任务与巡检只读检查是两条独立链路。

Kafka类检查在目标/步骤里读取host、topic、consumerGroup等实际参数。`readKafkaOffsets` 临时创建consumer、关闭auto-commit，查询分区末端及组提交位点，不依赖Spring全局消费者；基础部署不写全局Kafka段，也不因无需配置删掉kafka-clients。

核验版本中的 `readKafkaOffsets` 没有把security.protocol/SASL/证书参数传入；不能声称在任意YAML加入认证即可生效。`testKafkaTarget`、其他工具和数据治理/白名单客户端各自独立，按本次确切方法核查，不能从某条链路推断其他链路支持度。

## 常见定位

| 现象 | 首先核对 |
| --- | --- |
| 保存计划后不执行 | 计划状态/jobId、SysJob、invokeTarget、cron、有效时间窗、模板和目标启用状态 |
| 有记录没健康度 | sourceType/runMode、plan绑定、slot、healthConfig及health_daily更新链 |
| 首样本异常/未检测 | 比较模式、probe_state、windowKey、基线证据；不要只改前端状态文案 |
| 找不到字段/详情报Unknown column | 当前Mapper需要列与实际独立升级SQL是否匹配 |
| 周月报统计不符 | 日期范围、范围快照、去重/窗口口径、后端聚合和导出契约 |

测试入口：`AutoInspectionExecutionPolicyTest`、`AutoInspectionComparisonWindowTest`、`AutoInspectionPlanScheduleMatcherTest`、`AutoInspectionReadOnlyQueryGuardTest`、`AutoInspectionPeriodReportTest`、`AutoInspectionDashboardAggregationTest`；前端 `npm run test:auto-inspection`。名称含LocalReadOnly或真实服务的测试先核对环境，不将跳过计作真实验收。
