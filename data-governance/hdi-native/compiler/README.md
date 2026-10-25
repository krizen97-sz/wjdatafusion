# 原任务配置编译适配

本组件把平台已持久化的原 `FlowVisualVo` 修订投影为本次运行的原元数据，再调用原 `ZookeeperServiceImpl.forcePushFlowDataToZk` 和原插件配置生成器。它不实现数据处理算法，不启动原任务管理服务、Agent、身份服务或许可服务，也不连接数据库、Redis、ZooKeeper。

`OriginalFlowCompiler.java` 仍是合成验收 fixture。这里的 `OriginalTaskCompiler.java` 是独立的元数据适配入口：没有固定任务、实例或元数据 ID，也没有 fixture 的固定数据内容。任务数据、参数、字段和映射均取自平台冻结的修订。真实运行及操作权限由平台和原 Core 执行器负责。

## 当前执行范围

原输入 → 零个或多个原 `fieldMapping` → 原输出，一个连接完整的线性链，最多 32 个节点、96 条边、512 个字段。生成器开放 `FIXED_VALUE`，映射开放无外部函数的 `move` / `keep`，文件开放同步 CSV / line。数据处理始终由原插件完成。

| 类别 | 原插件 | 原配置生成器 | 本次编译边界 |
| --- | --- | --- | --- |
| 本地生成/映射/输出 | dataGeneratorInput / fieldMapping / fileOutput | DataGeneratorInputServiceImpl / FieldMappingServiceImpl / FileOutputServiceImpl | 原有本地流程兼容 |
| PostgreSQL | postgresqlInput / postgresqlOutput | InputServiceImpl / OutputServiceImpl | 选择单表全量读取、向既有表插入；不自动建表/清空 |
| FTP | fileInput / fileOutput | FileInputServiceImpl / FileOutputServiceImpl | 原FTP绝对目录、CSV/行文件；本地下载暂存与输出归档目录由平台绑定 |
| Kafka | kafkaInput / kafkaV2Output | MqInputServiceImpl / MqOutputServiceImpl | 单一明确topic、原JSON/string消息格式；不开放任意认证/序列化器覆盖 |

原包没有本次可验证的独立 MySQL 输入输出插件，因此不把 PostgreSQL 插件冒充为 MySQL 实现。上述表仅证明编译接入能力；真实网络、原插件执行以及浏览器闭环必须另行验收。

PostgreSQL 选表输入必须保留原浏览器生成的 `fieldExpression=columnName`，`srcTableType=1` 和 `srcTableId`；本次执行只重绑 `srcTableId` 到独立元数据块，不补写 SQL 表达式。原 `StreamFieldServiceImpl.saveStreamFields` 会为缺省/空表达式填入字段名；编译器现复用该保存投影，并仅捕获本次 DAO 写入，不能跳过此步把空表达式直接交给原 SQL 引擎。非同名 SQL 表达式仍在执行前拒绝。原输出表单的 `dataCleansing` 禁用值是字符串 `"0"`，不是布尔值或 `"false"`；原 Core 按整数读取该值。当前 profile 拒绝清空目标数据，并拒绝非法值。显式写入方式沿用原枚举 `INSERT`，不把任意大小写或未支持模式静默归一化。

原 JDBC / 文件输出按 `fieldName` 读取上游行值。仅把目标字段名改为另一个名称而把 `fieldExpression` 留成源字段，并不会执行改名，可能写入 NULL。当前直接字段 profile 要求目标字段名与上游一致，且 `fieldSrcType` 为原直接来源 0/缺省；改名必须显式使用原 `fieldMapping` 节点。原浏览器可能从固定数据生成器继承字面 `fieldExpression`，该直接来源下不执行它，元数据原样保留；非 0 来源继续拒绝，编译器不补写字段转换或 SQL。

同步 File 输出原表单会追加 `_RESULT_CODE_`、`_RESULT_MSG_`、`_RESULT_DATA_` 三个隐藏响应字段；原 `FileOutput.synchronousProcessData` 确实产生这些响应，它们不是普通上游字段。编译器仅对原同步 File 输出接受这三个精确字段并保留原 `DstTableFieldServiceImpl.batchInsert` 的顺序与启用标志，不根据 `isNew` 猜测删除。输入字段通过原 `saveStreamFields` 保留列表顺序和缺省值；该服务不补 `fieldOrder`，本适配也不编造。无选表的原 FileInput 使用原 `mockTableUse/mockTable` 占位行为，DAO 仅对该原占位 ID 返回缺行，其余非冻结 ID 继续拒绝。 用户可使用原“是否忽略”开关（`passThrough=1`）将这三个响应字段排除在物理文件列之外；原 `batchInsert` 跳过它们，原同步方法仍回填响应，任务快照中的五字段与忽略标志继续保留。编译器不自动代用户勾选。FTP输出原表单无 `dstTableVo` 和可见本地缓存路径时，沿用原输出服务的占位目标表与按节点字段读取，缓存仍绑定本次私有 outputs，远端目录保持用户选择。

原输出参数必须经过原 `ParamManageServiceImpl.saveAdapterParam` 保存投影：`addEtlColumn=0` / 缺省会生成 `etl.plugin.extend.fields=false`，对应大小写选项生成 `etl.plugin.extend.fields.upper`。本适配调用原方法，仅截获其 `saveNodeParam` 的持久化出口，不手写这套投影规则。PostgreSQL/Kafka 可在原高级参数显式选择 `etl.plugin.partial.failed.stop.enable=true`；原默认是 false，关闭时原引擎可能跳过失败数据后正常 finish，因此真实验收必须另核目标行数/消息数，不能以 finish 等同写入成功。另已实证原 `JdbcOutput.stopCheck` 对 `returnCount==0` 不触发 fatal，即使开关为 true，全部行被约束拒绝时也可能记录 insert 0 / skip N 后 finish；平台必须保留原状态与实际写入异常的区别，不能伪造原 FAILED。

兼容原浏览器的缺省 DTO：`transformFieldVoList.processType` 可以缺失，原 `FieldMappingServiceImpl` 对同名同类型的字段不生成转换项，改名时沿用原非 `keep` 默认；本适配不填造处理规则。输出字段可以没有 `fieldExpression` / `enableFlag`，编译元数据通过原 `DstTableFieldServiceImpl.batchInsert` 的实际投影生成字段顺序、启用标志和本次 stream/node 归属，原字段表达式继续保持缺省。该 DAO 的批量写入仅捕获到本次不可变快照，不连接数据库。字段名仍须来自上游，显式 FEL 表达式不开放。

原 `TransformFieldServiceImpl.upsertTransformField` 只复制 DTO 并设置 itemId、enableFlag、fieldDstOrder，不补 `processType`。已核真实原浏览器改名形状（`fieldSyncStatus=sync`、`condExpression=""`、无 processType）：原生成器为 `message → mapped_message` 生成一条明确的源/目标字段映射，不会把改名当成空透传；同名同类型才省略转换项。此验证仅证明原 SDK 编译结果，实际运行另由原 Core 验收。

原 `el-input` / `el-switch` / `el-select` 参数 DTO 可以保留。原文件生成器优先采用 `adapterParamVoList` 中已有参数，再从 `adapterData` 补缺；因此若历史参数 `etl.file.fileType=txt` 与表单 `adapterData.fileType=csv` 冲突，草稿可保存，但编译明确拒绝，不能静默输出另一种格式。受支持的显式格式参数须与表单一致。目录适配应遵循原参数表单的过滤契约，不把已由专属节点表单接管的旧默认值重新注入。

编译完成后还检查原 `flowjson` 的实际合并结果，与 worker 的本地文件 profile 使用相同规则：`etl.file.serverType` 必须与原表单所选的 `local` / `ftp` 一致，`etl.file.isUpload` 必须对应 `false` / `true`。即使结构化表单已选本地，残留的高级 `serverType=Ftp` 仍会按原优先级覆盖，因此返回 `UNSUPPORTED_GRAPH` / `reason=LOCAL_OUTPUT_REQUIRED`，由用户在原表单取消冲突参数；不改写草稿或生成结果。文件格式冲突返回固定 `FILE_FORMAT_CONFLICT` reason。平台仅对白名单 reason 映射固定说明，不能展示任意原异常消息或参数值。

保存允许空草稿、不完整链、未配置完的节点和原无限行数 `-1`。运行明确拒绝无界生成、分叉、环、未知插件、SFTP、原始SQL、脚本、外部 FEL / Redis、全局参数引用、异步输出及当前范围外的表达式。保存不会把这些配置静默修改成可运行的配置。生成器缺省行数从已校验的原 `etl_plugin.json` 读取（本版原值为 100000），不会被偷偷改为测试行数；运行上限为 1000000 行。

原目录声明为 `el-switch` 的参数按其 `allowableValues` 校验；没有自定义选项时使用原布尔 true/false，接受浏览器传来的布尔或字符串值。Kafka 的 `etl.kafka.enable.auto.commit=false` 不再被错误归为“非默认不支持”。 这不代表不提交消费位置：原 `ConsumerWorker.getData` 在自动提交关闭且取到数据时仍调用 `commitSync`；独立重复验收须用不同测试消费组或新增输入消息，不重置既有主题。显式路径派生、未接入的删除/截断、SCRAM 等原有执行范围限制仍单独保留，不以通用开关校验绕过它们。消息格式的原 wire 值为 `json` / `string`；`text` 不是该原目录选项，Avro/BSON 本轮不开放。当前浏览器真实验收使用 JSON。

原字段映射表单自带的未启用 Redis 占位值可以保留；只有与原目录默认值完全一致且当前映射没有外部函数的配置可进入本批执行。参数值改变时返回 `UNSUPPORTED_GRAPH`。

文件名边界在执行前校验：原 `FileOutput.getAllFields` 会从流 schema 的主键取 `primaryFieldName`，`genFileName` 会直接把该字段值加入文件名。因此本 profile 仅拒绝最终 File 输出实际处理的字段 `fieldKeyFlag!=0`，避免普通数据中的路径字符变成文件名。被原 `passThrough=1` 忽略的字段不进入物理文件 schema；数据库输入、数据库输出和 Kafka 的主键元数据保留，不受文件名规则误限。`fileNameFromFlow`、远程子目录、按字段文件名分组、动态路径等只能保持原空/false 默认；临时扩展名只能保持原默认，`customExtension` 仅允许 1–16 位字母数字、下划线或连字符。受信输出目录也不能含 `${...}` 动态占位符。合法草稿仍可保存，超范围能力在 compile 阶段明确拒绝。原 `flowName` 在已核 FileOutput 中仅参与日志上下文，没有加入此默认文件名生成链。

## 平台调用

```sh
python3 tools/data-governance/hdi_original_compiler.py \
  --bundle /absolute/original-bundle \
  --java-home /absolute/jdk-11-or-17 \
  --runtime /absolute/platform-private-compiler-state
```

`runtime` 须由平台创建为当前用户拥有的 0700 目录。启动器在其下建独立临时目录编译 Java 11 字节码，并在一次性 JVM 中处理一份 stdin JSON。stdout 只返回一份 JSON，退出码 0 表示编译/准备成功，2 表示拒绝或失败。它不表示原 Core 已运行成功。原 SDK 日志不会原样转发给客户端；临时输出和日志在退出后清理。JVM 最大堆 256 MiB，默认超时 45 秒，最大 120 秒。

启动器验证原包来源 SHA、所有入 classpath 的 JAR、插件 properties 和 JSON 目录的 SHA 与字节数。classpath 只列出已验证文件，不使用通配符。所有原文件保持字节不变，禁止符号链接。打包来源固定为本次已验证 `detl_3.3.0.20260310110148.zip`，SHA-256 `3bd2ef065255e9e19119b1e2ade294b32b59d47c051a0a10981496766f052859`。

### 保存准备

```json
{
  "operation": "prepare",
  "context": {
    "businessTaskId": "17", "revision": "2", "ownerId": "4",
    "workspaceId": "1", "projectCode": "rynew-platform"
  },
  "visual": {"flowVo": {}, "nodes": {}, "links": [], "streamVoList": [], "paramVoList": []}
}
```

context 必须由认证后的平台持久化记录构造，不能接受浏览器指定 owner/workspace。平台在同一事务中分配真实 task/revision 后调用 prepare，失败回滚。prepare 用真实平台上下文绑定 flow identity，保留原业务节点及 UI 信息，并规范化 `panelUIInfo` 为原 DTO 的 `panelUiInfo`；两个别名冲突时拒绝。

成功返回 `ok:true, protocolVersion:1, operation:"prepare", canonicalVisual, snapshotJson, snapshotSha256, diagnostics`。平台原样保存 **snapshotJson 的 UTF-8 字节**及其 SHA。不要解析后重新序列化再计算/比较摘要；canonicalVisual 用于原 UI 回读。原子发布和权限校验仍由平台负责。

### 冻结连接版本

有连接的图保存时，平台先核对连接属主与启用状态，再传 `trustedConnectionRefs:{"71":"1"}`。编译器要求该键集合与原节点 `dbId` 精确匹配，并将非秘密的版本引用写入 snapshot envelope 的 `connectionRefs`。旧无连接 snapshot 三键格式继续兼容。原表单中的瞬态 password/databasePassword 字段会从 canonicalVisual 去除，任务快照不接收凭据。

执行时，平台按被冻结版本解密连接，传入可信 `runContext.connections`，结构为 `{ "71": { "connectionRevision":"1", "datasource":<原DatasourceDto>, "datasourceParamVoList":[] } }`。编译器核对版本、连接ID、原插件类型以及表DTO归属，再通过原生成器投影为 native 数据源配置。`connectionBindings` 只携带 worker reserve 确认的非秘密网络授权，原样写入 metadataSnapshot，供 worker 再核对；实际密码只出现在私有 compiled native 配置，不能回传浏览器或混入公开任务修订。

当前原 Core 以原支持的 `etl.security.decrypt=false` 模式消费本地私有配置；没有套用 BIC 身份加密或编造另一种协议。平台连接存储加密、运行授权、日志凭据脱敏与网络端点限制由对应平台/worker层执行。

### 运行编译

```json
{
  "operation": "compile",
  "snapshotJson": "<prepare 返回的原字符串>",
  "snapshotSha256": "<其 UTF-8 SHA-256>",
  "runContext": {
    "businessTaskId": "17", "revision": "2",
    "nativeFlowId": "21001", "nativeInstanceId": "21002",
    "metadataIdBase": "22000", "execId": "21003",
    "mode": "test",
    "outputsDir": "/private/platform-runs/21003/outputs",
    "authorizedOutputRoot": "/private/platform-runs/21003"
  }
}
```

所有平台/运行 ID 为正十进制字符串。workspace 是原 int 正数，最大 2147483647；其他 ID 限制在原浏览器安全整数范围。平台必须每次运行事务性分配全局唯一 native flow、instance、exec 和至少 **8192 个 ID** 的 metadata block，并持久化运行与业务 task/revision 的关联；不能跨运行复用。编译器核对 task/revision、摘要和区间重叠，但全局唯一性只能由平台数据库保证。

outputsDir 必须是 authorizedOutputRoot 下的独立子目录。二者只能来自平台授权输出策略/worker reserve，不能取自浏览器路径。编译器将原文件输出配置的路径及其旧参数形式绑定到此目录，不修改冻结 Visual，并拒绝符号链接。`test` 使用私有运行目录；`run` 使用平台已授权输出根下的独立运行目录。执行器在实际启动时仍须复核目录归属、隔离和路径，避免校验后目录被替换。

用户在原 File 输出表单中必须明确选择精确逻辑路径 **`/平台输出`**。它表示每次写入执行器的独立 `run/outputs`，完整产物校验归档后通过平台下载，绝不表示写到主机上同名目录。测试和正式运行都拒绝空路径、任意主机路径、`/平台输出xxx` 或附加子目录；草稿可以先保存。只有这个明确选项才允许编译时绑定实际 worker 路径，不能将用户指定的其他路径静默重定向。本地输出时 `localDir`、`absoluteDir` 与旧 `etl.file.localDir` 参数中所有非空值都必须符合该边界。FTP输出的 `absoluteDir` 保留原远程路径，只有 `localDir` 被绑定到独立输出暂存目录；FTP输入的 `localDir` 绑定本次 `tmp/downloads`。远程绝对目录拒绝 `..`、`.`、动态占位符与反斜杠，不把用户远程路径静默替换成本地路径。

编译机与执行器不在同一主机时，平台在严格校验 `worker.reserve` 的身份响应后设置 `runContext.outputRootSource="worker-reserve"`。此时编译器仅做绝对路径、规范化、严格子目录和无动态占位符检查，不用编译机的本地符号链接解释 Linux 目标路径（例如 macOS 的 `/home` 与 Linux `/home` 不同）。实际目标文件系统的符号链接、所有者和权限检查仍由 worker 完成。此字段不能来自浏览器；省略时保留原本机符号链接拒绝。

### 原定时规则编译

输入 `{"operation":"schedule","scheduleConfig":<原ScheduleConfigVo>,"zoneId":"Asia/Shanghai"}`，成功返回原 config、zoneId、cronExpressions、nativeSchedule 和 diagnostics。这里直接调用原 `ScheduleConfigVo.toScheduleVo()` 与 `CronUtil.generateEnableCronExpr()`，平台随后用已有 Quartz 解析、预览和计算下一次执行时间。

`scheduleType=1` 使用原 DTO 校验后返回 `mode=MANUAL`、空 cronExpressions，不生成自动触发；平台可以持久保存手动设置并原子禁用旧周期，立即运行仍是另一次明确操作。周期首批支持 `scheduleType=2`、`unit=2`（每月）、`3`（每周）、`4`（每日），返回 `mode=CRON`；`executeTime` 为 `HH:mm:ss`，`timeInterval` 为原选项字符串（月1–32，其中32为原月末；周1–7；每日1）。原表单无 exactDate 时仍携带的 executeTimeExactDate 默认值保留，它不参与这条 Cron 生成链。

`scheduleType=2` 的小时（unit5）和分钟（unit6）返回 `mode=CALENDAR`、空 cronExpressions、原 `nativeExpression=N#unit` 与 `calendar:{interval,unit}`。编译器先用原 `CronUtil.getExecExpr`，再调用原 Core `JobManager.getScheduleBuilder` 构造原 Quartz CalendarIntervalScheduleBuilder，读取其 interval/unit；不会调用 submitTask、getStartTime 或启动 Quartz/ZK。36小时和90分钟属于合法日历间隔，不伪装成整点 Cron。

不开放实时、依赖、秒级、按完成时间间隔、排除窗口、自定义 Cron、额外的立即全量首跑或增量连续状态；不能把未实现的频率显示为可用。原生成器若给出 Quartz 不接受的组合则明确拒绝。调度实际持久化、并发占用、触发去重及重启语义属于平台调度服务，不在本编译进程中执行。

成功返回：

* `protocolVersion:1, ok:true, operation:"compile"`
* `businessTaskId, revision, ownerId, workspaceId, flowId, instanceId, execId` 均为十进制字符串；flowId/instanceId 为本次 native ID。
* `snapshotSha256`
* `flowjson`, `schedule`, `stop` 各含 `base64` 和原字节的 `sha256`。
* `metadataSnapshot` 含本次 `runtimeVisual`、原 `ModuleDto`、`metadataIdBase`、`allocatedThrough`、`outputsDir`。
* `nodeMappings` 含原 UI `visualNodeId` 与本次 native `nodeId,moduleId,stepId,itemId,streamId,adapterName`。
* `diagnostics`。

平台原子写整个 compiled JSON 并计算文件 SHA，交给 worker。worker 校验文件 SHA、每份原字节 SHA 及身份字段，然后原样写 ZK。`CompilationSink` 只是截获原生成器要求写入的唯一 flowjson 路径，并记录原发布意图；没有连接 ZK，也没有把数据库状态标成已发布。

原通道如下（namespace 由 worker 独立分配）：

* 配置：`/metadata/group/<workspaceId>/flowjson/<flowId>`。
* 启动：`/metadata/schedule/<flowId>`；原序列化字段是 `id`、`exec_id`、`oper_type`、`oper_value`、`strategy_flow`，不是假设的 `flow_id`。
* 停止：同一 schedule 路径，使用本次编译返回的原 `stop` 字节。它由原 `FlowControlMessageDto.builder().flowId(nativeFlowId).type(0).build()` 生成，不自造 control 消息。
* 终态：原 `/metadata/schedule/<flowId>/exec`，必须匹配本次 exec_id。实际输出成功、日志及产物校验属于 worker 的执行验收。

元数据适配对原 DAO/service 方法进行精确绑定和 ID 检查，未知方法立即失败。原 UI 的 `paramVoList` 从包内 `instance/conf/task_config.json` 的真实目录校验，并投影为本次 flowId / type2 的原 `EtlParam`，原生成器写入 `flow.flow_param`，不会清空 UI 默认任务参数。全部原默认值可保留；日志级别枚举、布尔开关和有限数值设置按原目录及当前资源边界校验。周期延展/强制周期、Kafka offset、失败数据自定义 Cron 和心跳等未接能力只允许原未启用默认值；未知、全局或外部 FEL 参数明确拒绝。

当前 profile 的外部数据源、全局参数、第三方 FEL、共享扩展版本和运行配置表为空；本地原插件目录是唯一可用插件来源。没有通用空值/成功兜底，后续插件需先补真实元数据契约和执行验证。

## 验证

```sh
HDI_ORIGINAL_BUNDLE=/absolute/original-bundle \
HDI_COMPILER_JAVA_HOME=/absolute/jdk-17 \
python3 -m unittest discover -s tools/data-governance -p test_hdi_original_compiler.py -v
```

未配置原 SDK 时只跑启动器单元测试，其余明确 skipped。已配置时实际编译原 DTO/生成器，检查三节点配置、修改配置、运行 ID 隔离、路径绑定、摘要篡改、身份错配、草稿、原参数默认值、非法拓扑、重复 JSON 字段和外部引用拒绝。这些属于原配置编译验证；不等于真实浏览器保存运行或 Linux 原 Core 最终验收。
