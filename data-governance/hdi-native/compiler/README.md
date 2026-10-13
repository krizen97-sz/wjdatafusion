# 原任务配置编译适配

本组件把平台已持久化的原 `FlowVisualVo` 修订投影为本次运行的原元数据，再调用原 `ZookeeperServiceImpl.forcePushFlowDataToZk` 和原三种插件配置生成器。它不实现数据处理算法，不启动原任务管理服务、Agent、身份服务或许可服务，也不连接数据库、Redis、ZooKeeper。

`OriginalFlowCompiler.java` 仍是合成验收 fixture。这里的 `OriginalTaskCompiler.java` 是独立的元数据适配入口：没有固定任务、实例或元数据 ID，也没有 fixture 的固定数据内容。任务数据、参数、字段和映射均取自平台冻结的修订。真实运行及操作权限由平台和原 Core 执行器负责。

## 当前执行范围

原 `dataGeneratorInput` → 零个或多个原 `fieldMapping` → 原本地 `fileOutput`，一个连接完整的线性链，最多 32 个节点、96 条边、512 个字段。生成器首批开放 `FIXED_VALUE`；映射首批开放无外部函数的 `move` / `keep`；输出首批开放同步 CSV / line。数据处理由原插件完成。

保存允许空草稿、不完整链、未配置完的节点和原无限行数 `-1`。运行明确拒绝无界生成、分叉、环、未知插件、FTP/SFTP、SQL、脚本、外部 FEL / Redis、全局参数引用、异步输出及当前范围外的表达式。保存不会把这些配置静默修改成可运行的配置。生成器缺省行数从已校验的原 `etl_plugin.json` 读取（本版原值为 100000），不会被偷偷改为测试行数；运行上限为 1000000 行。

原字段映射表单自带的未启用 Redis 占位值可以保留；只有与原目录默认值完全一致且当前映射没有外部函数的配置可进入本批执行。参数值改变时返回 `UNSUPPORTED_GRAPH`。

文件名边界在执行前校验：原 `FileOutput.getAllFields` 会从流 schema 的主键取 `primaryFieldName`，`genFileName` 会直接把该字段值加入文件名。因此本 profile 拒绝输入/输出 `fieldKeyFlag!=0`，避免普通数据中的路径字符变成文件名。`fileNameFromFlow`、远程子目录、按字段文件名分组、动态路径等只能保持原空/false 默认；临时扩展名只能保持原默认，`customExtension` 仅允许 1–16 位字母数字、下划线或连字符。受信输出目录也不能含 `${...}` 动态占位符。合法草稿仍可保存，超范围能力在 compile 阶段明确拒绝。原 `flowName` 在已核 FileOutput 中仅参与日志上下文，没有加入此默认文件名生成链。

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

用户在原 File 输出表单中必须明确选择精确逻辑路径 **`/平台输出`**。它表示每次写入执行器的独立 `run/outputs`，完整产物校验归档后通过平台下载，绝不表示写到主机上同名目录。测试和正式运行都拒绝空路径、任意主机路径、`/平台输出xxx` 或附加子目录；草稿可以先保存。只有这个明确选项才允许编译时绑定实际 worker 路径，不能将用户指定的其他路径静默重定向。`localDir`、`absoluteDir` 与旧 `etl.file.localDir` 参数中所有非空值都必须符合该边界。

编译机与执行器不在同一主机时，平台在严格校验 `worker.reserve` 的身份响应后设置 `runContext.outputRootSource="worker-reserve"`。此时编译器仅做绝对路径、规范化、严格子目录和无动态占位符检查，不用编译机的本地符号链接解释 Linux 目标路径（例如 macOS 的 `/home` 与 Linux `/home` 不同）。实际目标文件系统的符号链接、所有者和权限检查仍由 worker 完成。此字段不能来自浏览器；省略时保留原本机符号链接拒绝。

### 原定时规则编译

输入 `{"operation":"schedule","scheduleConfig":<原ScheduleConfigVo>,"zoneId":"Asia/Shanghai"}`，成功返回原 config、zoneId、cronExpressions、nativeSchedule 和 diagnostics。这里直接调用原 `ScheduleConfigVo.toScheduleVo()` 与 `CronUtil.generateEnableCronExpr()`，平台随后用已有 Quartz 解析、预览和计算下一次执行时间。

首批仅支持 `scheduleType=2`、`unit=2`（每月）、`3`（每周）、`4`（每日），`executeTime` 为 `HH:mm:ss`，`timeInterval` 为原选项字符串（月1–32，其中32为原月末；周1–7；每日1）。不开放实时、依赖、按完成时间间隔、排除窗口、自定义 Cron、立即首跑或增量连续状态；不能把未实现的频率显示为可用。原生成器若给出 Quartz 不接受的组合则明确拒绝。调度实际持久化、并发占用、触发去重及重启语义属于平台调度服务，不在本编译进程中执行。

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

元数据适配对原 DAO/service 方法进行精确绑定和 ID 检查，未知方法立即失败。当前 profile 的外部数据源、全局参数、第三方 FEL、共享扩展版本和运行配置表为空；本地原插件目录是唯一可用插件来源。没有通用空值/成功兜底，后续插件需先补真实元数据契约和执行验证。

## 验证

```sh
HDI_ORIGINAL_BUNDLE=/absolute/original-bundle \
HDI_COMPILER_JAVA_HOME=/absolute/jdk-17 \
python3 -m unittest discover -s tools/data-governance -p test_hdi_original_compiler.py -v
```

未配置原 SDK 时只跑启动器单元测试，其余明确 skipped。已配置时实际编译原 DTO/生成器，检查三节点配置、修改配置、运行 ID 隔离、路径绑定、摘要篡改、身份错配、草稿、原参数默认值、非法拓扑、重复 JSON 字段和外部引用拒绝。这些属于原配置编译验证；不等于真实浏览器保存运行或 Linux 原 Core 最终验收。
