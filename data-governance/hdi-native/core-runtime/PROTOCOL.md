# 原 Core 本机执行协议 v1

本模块只管理原 Core 进程及原 ZooKeeper 控制协议，不实现 ETL 算法。原 Agent、原管理服务、BIC 身份初始化和授权程序均不是本模块的启动步骤。Linux Java 11 已完成原三节点执行及 stdio 产物验收；浏览器与平台认证的完整闭环由平台集成验证，此处不将组件证明作为该闭环的替代。

传输为受平台持有的 stdin/stdout JSON-lines；日志不进入 stdout。每个请求含 `protocolVersion:1`、`requestId`、`op`。成功响应含 `ok:true` 与 `data`；错误只返回稳定错误码，不回显配置、原生异常中的凭据或原请求全文。浏览器不能直接打开该传输。

## 预留与提交

`reserve` 由平台先持久化运行后调用，字段为：

```json
{
  "protocolVersion": 1,
  "requestId": "reserve-1",
  "op": "reserve",
  "runId": "11111111-1111-4111-8111-111111111111",
  "ownerId": "1",
  "businessTaskId": "platform-task-id",
  "revision": "1",
  "nativeFlowId": "21001",
  "nativeInstanceId": "21002",
  "execId": "21003",
  "workspaceId": "100",
  "metadataIdBase": "21101",
  "mode": "test"
}
```

`runId` 是平台管理 UUID；`execId` 单独分配，必须与原 schedule 的 `exec_id` 一致。原生 ID 都是正十进制字符串；原 `InstanceInfo.groupId` 是 int，`workspaceId` 不能超过 2147483647，其余原 long ID 不能溢出。平台每次运行分配独立 ID 块，避免复用 Redis 时不同运行混用原生键。

预留返回 worker 真实的 `outputsDir`、`authorizedOutputRoot` 及运行状态。两条路径仅由平台用于编译，不接收浏览器指定值。编译器与 worker 可以在不同系统运行。

`submit` 携带 `ownerId`、`runId`、`compiledBase64` 和 `sha256`。Base64 内容是原编译器返回的完整 envelope 的 UTF-8 JSON 字节：`businessTaskId/revision/ownerId/flowId/workspaceId/instanceId/execId/snapshotSha256`、`flowjson/schedule/stop:{base64,sha256}`、`metadataSnapshot`、`nodeMappings`。外层及三份内部原字节分别校验哈希。worker 校验预留标识和授权输出目录后保存不可变原字节。写 ZooKeeper 时不重序列化 flowjson/schedule/stop，不要求平台与 worker 共享文件路径。

同 owner/run 的重试返回同一个运行。已提交运行不能换编译字节。写入提交意图之后若响应未知，不重新发送 schedule，不自动重跑。

## 状态、取消和结果

- `status(ownerId,runId)`：返回平台阶段、原生状态、最终确认标识和错误码。
- `cancel(ownerId,runId)`：提出取消请求。先发布编译器生成的原 stop 字节，再按停止超时关闭 held Core 子进程；确认退出之前不称已取消完成。
- `logs(ownerId,runId,cursor,maxBytes)`：返回 `text/cursor/nextCursor/eof`，cursor 为经过脱敏后的 UTF-8 日志字节偏移。
- `artifact(ownerId,runId,artifactId,offset,maxBytes)`：返回 `artifactId/offset/nextOffset/eof/bytes/sha256/base64`。只允许已终态运行 manifest 中的自身结果文件。不能传任意路径。平台复核 SHA/bytes 后交已有 ArtifactStore。
- `shutdown` 或 stdin EOF：停止本 worker 持有的运行和内置协调进程。不能停止配置为复用的外部 Redis/ZooKeeper。

阶段依次为 `RESERVED/PREPARING/STARTING/READY/SUBMITTING/RUNNING/STOPPING`，终态区别 `SUCCEEDED/FAILED/CANCELLED/INTERRUPTED/TIMED_OUT`；未知恢复为 `UNKNOWN` 且 `finalized:false/recoveryRequired:true`。状态响应的 `artifacts` 为 `[{artifactId,name,bytes,sha256}]`，只在成功终态且进程已关闭后提供。原生成功仅来自本次 `/metadata/schedule/<nativeFlowId>/exec`，且 `exec_id` 完全匹配。进程退出、端口监听、fixture 元数据写入成功均不能替代它。

就绪同时要求：原 `/status/instance/<nativeInstanceId>` 的 id 匹配、`status=3`、更新时间不早于本次启动；原 pid 节点与 namespace 内 held Java PID 匹配；namespace supervisor 仍存活。依据为原 `EtlState.RunnableState→RunningState`，而不是固定 sleep。

## 隔离与生命周期

Linux 必须支持 unprivileged user/network/PID namespaces。Core namespace 只有 lo；原 wildcard RPC/data 监听不发布到主机或 LAN。依赖经私有 0600 Unix socket 透明代理到固定的回环 Redis/ZooKeeper。当前只支持三种已验证原插件，不开放任意网络目的地；不具备隔离能力直接拒绝启动，不采用 macOS 测试 bind shim。

每个运行使用独立 namespace、私有目录、原 ZK digest ACL 和原生 ID。worker 记录启动/执行/停止超时及进程身份，持有子进程对象进行终止，不运行 pkill、stopall 或厂商全局安装脚本。原进程没确认退出时不释放运行屏障；重启后未知记录需要核查，不自动派发。

私有配置、证书、临时数据库及生成结果放在仓库和程序 bundle 外。程序 JAR 保持原字节。新证书和运行配置属于本平台正常部署材料，不复制原环境的密钥、身份或许可文件。

## 本机配置与验收边界

使用 `python3 tools/data-governance/hdi_core_worker.py --config <私有配置文件>` 启动。配置包含 `stateDir/bundle/javaHome/controlJar/controlJarSha256` 和 `redis:{host,port,database,passwordFile}`。Redis 只接受回环地址，复用平台已有 Redis；口令读取私有文件。未配置 `zookeeper` 时从 bundle 的 `zookeeper/lib` 启动原协调器，数据放本 worker stateDir，随机高端口仅绑定 127.0.0.1，关闭仅作用于持有的子进程。显式配置回环 `zookeeper:{host,port}` 表示复用已有协调器，此模式不将其停止。

`tls` 可指向管理员为该模块新建的私有 `serverKeystore/clientKeystore/passwordFile`；省略时为每次运行生成新 JKS。测试证书的短有效期不能当作正式部署配置。`javaRuntimeMode` 默认 `normal`；`interpreted-validation` 仅用于 QEMU TCG 上 Java 11 C1 不稳定的本机验证，会显式出现在 hello 中。正式原生 Linux 不应照抄该测试模式。

本次验证包括真实原 DataGenerator → FieldMapping → FileOutput 的原生 finish、3 行 CSV 的内容/完整 SHA、分块产物、跨 owner 拒绝、重复 submit 幂等以及 held 子进程退出。运行取消已在 45 秒等待配置下验证本次 exec_id 的原生 stop 终态，随后确认进程退出且不返回中间产物；若其他运行在停止窗口未观察到原生终态则明确返回 `ORIGINAL_STOP_TIMEOUT_PROCESS_CLOSED`，不会把中间文件作为成功产物。当前 namespace 隔离的是网络与 PID，不是完整文件系统沙箱；只接受受平台编译器约束的三个已选插件。


内置协调器追加验收：无 `zookeeper` 配置时，原 bundle 的 ZooKeeper 独立启动，完整三节点 stdio 验收通过；关闭后 held supervisor 与 JVM 均结束，预先存在的实验协调器保持运行。协调 JVM 通过 stdin 租约和 Linux parent-death signal 双重约束；worker 断连正常清理，supervisor 被强制结束时只终止其 held JVM。启动保存 PID、启动 tick、argv 哈希，发现旧记录对应进程仍存活时拒绝接管。Linux 实测 28 项组件测试通过，包含 stdin EOF 与 supervisor SIGKILL 后原子进程不遗留两项；macOS 上这两项 Linux 专有测试明确跳过。


`stopTimeoutSeconds` 默认 45 秒，可设置为 45–60 的整数，覆盖原普通任务 30 秒的停止等待。普通取消等待这个窗口，再关闭本次 held 进程；未确认关闭保持 UNKNOWN。`shutdown` 为所有运行共享 70 秒总预算：先同时提出取消，运行线程并行在前 60 秒完成或留下恢复屏障，最后最多 10 秒关闭内置协调器。进入 shutdown 后原 stop 的等待会缩短到共享截止前 15 秒，为进程确认与代理关闭留时间；不会按四个运行逐个等待 45 秒。该预算由 hello 的 `shutdownTimeoutSeconds` 返回，平台 stdio 超时应至少 80 秒。文件系统故障或无法确认退出会持久化 `.shutdown-unconfirmed.json` 并拒绝自动重启。
