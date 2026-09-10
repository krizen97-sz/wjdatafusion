# 原生 Kettle Job 执行与本机 FTP 验证

本模块直接调用原工具包的 `Job`、`JobMeta`、`JobEntryTrans` 和 `JobEntryFTPPUT`，不把作业改写成另一种引擎的处理图。当前入口覆盖原生 `SPECIAL`、`TRANS`、`FTP_PUT`；未实现的作业节点明确拒绝。转换内部仍由原 Kettle 步骤执行。

## 与隔离 worker 的接口

文件：`data-governance/kettle-worker/src/NativeJobExecutor.java`，无 package，与 `KettleWorker` 同目录编译。

- `public static void validate(Path root)`：读取 `root/transformation.kjb`，使用原 `JobMeta` 解析，输出 `validation` 事件，包含 `valid/name/kind/nodes/hops`。节点包含原 pluginId、类名与实际 JAR 来源；连线保留 enabled、evaluation、unconditional。
- `public static void run(Path root)`：执行同一 Job，使用 `KettleWorker.event` 输出 JSONL。启动前由 broker 配置 OS 沙箱；本类不是网络或文件访问沙箱。
- 子转换必须是当前操作目录里的文件，可引用 `${INPUT_DIR}/synthetic.ktr`。`INPUT_DIR=root/input`，`WORK_DIR=root/output`，`JOB_DIR=root`。输入文件与子转换只在 input 中，FTP 只能发送 output 及其子目录的普通文件。验证也需要先提供子转换文件。
- 本版只接受本地 `filename` 转换引用，不接受 repository、remote、cluster 或嵌套 Job。XML 在交给原加载器前先经禁止 DOCTYPE/外部实体的解析器检查。
- 原 FTP 密码编码器显式注册并初始化，避免只注册 Step 后原 FTP 的 `loadXML` 因 `Encr.encoder` 未初始化而失败。

`KettleWorker` 集成时可使用反射调用 `run/validate`；本类不修改 worker 主入口、HTTP broker 或其网络授权配置。

## 事件、日志和停止

`state` 事件提供 PREPARING、RUNNING、STOPPING；每次原作业节点执行由 `JobEntryListener` 产生配对的 `job-entry` BEFORE/AFTER，携带节点名、copy、执行序号、pluginId、错误数、原始成功布尔值和经检查后的结果。子转换通过原 `DelegationListener` 和 `TransListener` 产生 `job-transformation` 事件。

`log` 来自原 `KettleLogStore`，不是模拟日志；单次操作最多输出 500 行，每行至多 8192 字符，保留级别、节点和日志 channel。XML 中的 password 值及其原解码值会在事件中替换。运行目录、原引擎标准错误和业务数据仍需按私有数据管理，不能把任意原插件日志视为天然无敏感信息。

向进程标准输入发送一行 `STOP`，执行器设置原 Job 停止标志并传递给已委派的真实子 `Trans`。只有 Job 线程返回、子转换的原 `isFinished` 状态确认后，才发出 STOPPED；停止请求本身不是停止完成。内部默认 120 秒后请求停止，可用 `-Dgovernance.job.timeout.seconds` 在 1–900 秒内设置；若原连接器不响应停止，进程仍需由 broker 的绝对时限管理，不能伪造已完成事件。

原包有两处必须保留的兼容边界：

1. `JobEntryListener.afterExecution` 在原 Job 选择后续连线之前运行。若错误数大于零、原结果已停止或用户请求停止，即把该次结果布尔值设为 false，保留原值到事件，避免误走成功连线进入 FTP。
2. 该发行包的 `Trans.waitUntilFinished()` 消费一次性阻塞队列。原 `JobEntryTrans` 已经调用它，外层再次调用会永久等待。本实现等待拥有该子转换的 Job 线程返回，再检查原完成状态；不重复消费完成信号。

终态包含 `SUCCEEDED/FAILED/STOPPED`、errors、resultBoolean、jobFinished、childrenFinished、timedOut、entriesExecuted、logTruncated。父 Job 的成功不能仅依据 HTTP 200、节点回调或 Result 成功布尔值。

## 可重复的独立实测

工具 `tools/data-governance/test_native_job_ftp.py` 是显式运行的集成验证，不属于自动连接真实业务源的默认测试。它需要 worker 已准备且有原包哈希清单的运行目录，以及已有的 pyftpdlib venv。

```sh
python3 tools/data-governance/test_native_job_ftp.py \
  --worker-runtime "$KETTLE_WORKER_RUNTIME" \
  --ftp-python "$FTP_VENV/bin/python" \
  --output "$NATIVE_JOB_PROOF_ROOT"
```

分支尚未整合时可用 `--worker-source /absolute/path/to/KettleWorker.java` 指定协作分支源码。编译输出放在新 proof 子目录，不修改原 worker classes/lib。每次校验 manifest 中所有原 JAR 的 SHA-256，并保持其 custom-first classpath 顺序。

脚本每次新建专属随机目录和随机口令、只绑定 `127.0.0.1` 的 FTP 控制端口及 4 个被动端口。原 Java 进程使用 macOS `sandbox-exec`：只读所需原 JAR、Java 系统资源、本次编译 classes 和操作目录，只能写自己的操作目录；只逐端口授权该 FTP 的 `remote tcp4 "localhost:port"`。其他网络与进程 fork 拒绝。Java 使用 `preferIPv4Stack=true`，避免旧 FTP 客户端默认 IPv6 socket 不匹配精确 IPv4 授权。脚本不会开启现有 fixture 或业务 FTP；退出时只停止自己创建并持有 Popen 对象的 fixture 进程。

合成实测包含：

| 用例 | 必须同时成立 |
| --- | --- |
| OS 沙箱 | 自建无关回环端口被拒绝，目录外合成文件读写被拒绝，专属 FTP 可连接 |
| 成功 Job | 原 DataGrid → TextFileOutput；START → TRANS → 成功条件连线 → FTP_PUT，使用全匹配 `.*` 实际 STOR 后通过 FTP RETR 读回比对完整字节，远端只含输出文件，不含输入 CSV 或子 KTR |
| 转换失败 | 原 DataGrid → Abort；预放 canary 文件使误上传可被发现；Job FAILED，FTP BEFORE 事件为 0，远端目录为空 |
| 用户停止 | 原 DataGrid → Delay → TextFileOutput，运行中发送 STOP；Job 和子转换均确认完成，FTP BEFORE 事件为 0，远端目录为空 |
| 非输出目录 | INPUT_DIR、JOB_DIR 和空目录在原执行前拒绝，不进入 FTP |
| 原 Job 验证 | 通过同一 XML/子转换文件返回原 pluginId、类来源及条件连线，不执行 FTP |

2026-09-10 本机实测通过：448 个原 JAR 校验；三个作业节点实际来自 `kettle-6.1.0.7.36.jar`。成功文本含表头及 3 条合成中文记录，共 **98 字节**，本地完整文件与 FTP RETR 内容一致，SHA-256 为 `6a93c74071eee5705b94d49ce3b1b02b28a6088af39ac6e2ad732278ca623c82`。失败与停止都只执行 START/TRANS 两节点，均未进入 FTP；停止场景还实际观察到原 Trans 返回 boolean=true，经停止屏障改为 false。所有 fixture 已退出。

证据在仓库外的 `data-governance-kettle-v2/job-proof/proof-<id>/acceptance.json`、各操作 events.jsonl/stderr.log 和 FTP transfers.jsonl。原附件、实际连接、口令、运行数据和生成类均不进入 Git。本证明不包含原业务 Kafka、现场数据库、真实 FTP 接收协议或生产部署；这些必须在对应节点接入后以实际目标回读继续验收。

## 原项目依据

- [Pentaho 6.1 JobEntryListener](https://github.com/pentaho/pentaho-kettle/blob/6.1.0.1-R/engine/src/org/pentaho/di/job/JobEntryListener.java)
- [Pentaho 6.1 Job](https://github.com/pentaho/pentaho-kettle/blob/6.1.0.1-R/engine/src/org/pentaho/di/job/Job.java)
- [Pentaho 6.1 JobEntryTrans](https://github.com/pentaho/pentaho-kettle/blob/6.1.0.1-R/engine/src/org/pentaho/di/job/entries/trans/JobEntryTrans.java)

上述主源用于解释 API；实际运行证据以磁盘原包的类来源、哈希和真实事件为准。
