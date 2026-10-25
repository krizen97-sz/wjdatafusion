# 原任务核心本地验收（2026-10-25）

本轮在外网环境恢复已有本地测试实例，未连接 10 / 250，也未变更生产数据库。实施方向仍为“迁任务核心，管理接本平台”：使用原画布、表单、配置生成器、执行 Core 和选定插件，平台管理任务、权限、版本、计划和运行记录。

## 范围与环境

- 源码工作树：`data-governance-hdi-native-transplant`；状态同步修复提交 `895159f`，冷启动握手修复 `a3be669`。
- RYNEW 本地后端 Java 17，前端 Chrome；MySQL 数据目录与原任务所有权标记核对一致，七张 `dg_hdi_*` 表保留。
- 本地 Linux x86_64 虚拟机使用原 Java 11 Core 和内置 ZooKeeper；QEMU 的 `interpreted-validation` 模式仅作兼容性验收，不代表生产性能。
- 本次实验中的 Core 使用虚拟机 Redis database 1，平台使用主机隔离 Redis；生产安装入口可引用平台现有回环 Redis。本次实验不能作为两者已共用同一 Redis 实例的证据。
- 使用的原程序、TLS 和状态目录均在 Git 外；未复制源站许可证、实验授权、身份配置，也未启动原管理后台或 Agent。

## 现有真实业务证据

此前从原页面建立的任务 1“原生流程验收”保留三节点和两条连线：固定生成三条 `message`，字段映射重命名为 `mapped_message`，原 File 输出生成带表头的同步本地 CSV。

| 场景 | 已核对的结果 | 证据 |
| --- | --- | --- |
| 浏览器编排、原表单配置及试运行 | revision 16，run `ad6241e7-ae10-4146-8d90-6c85468c46f3`，原生 finish、SUCCEEDED、finalized | `20261013-hdi-results-review/output/playwright/ui-authored-task1/evidence.json` |
| 连续快速编辑保存 | revision 15；maxRow 300、flushTime 1000，目录与原表单内容保留 | `platform-local/private/rapid-edit-proof.json` |
| 陈旧窗口拒绝 | 当前 revision 17，提交 revision 16 返回 HTTP 409，版本不变 | `platform-local/private/stale-revision-proof.json` |
| 原周期配置触发 | revision 17，run `243a601a-15cc-4ae8-afbd-8ffa87de233d`，SCHEDULE、SUCCEEDED、finalized | `platform-local/private/browser-scheduled-status.json` 和 `browser-schedule-proof.json` |
| 计划清理 | 本轮数据库复核 enabled=0、next_fire_at=NULL、last_outcome=DISABLED | 本轮只读数据库查询 |

旧浏览器成功记录使用旧 worker，其日志中的磁盘映射与日志目录问题不能省略。本轮已另外完成最新 worker 的平台与页面联合验收，结果见下节。

## 本轮原页面到原执行器联合验收

Chrome 原“试运行”提交任务 1 的 revision 18，run 为 `03ae6be5-21ce-46bc-94cc-2866fe0938bf`。平台记录的提交时间为 2026-10-25 12:08:32，完成观测时间为 12:10:21（Asia/Shanghai；跨架构解释执行环境，不作为生产耗时标准）。

- 原画布自动从“已启动”变为“运行中”再变为“已完成”，没有点击刷新。运行中与完成后 iframe `performance.timeOrigin` 均为 `1792901280503.9`，三节点保留。
- 平台与 worker 同时确认 `SUCCEEDED`、原生 `finish`、`finalized=true`、`error=null`；不是根据文件出现提前判定成功。
- 通过页面“运行记录 → 查看详情 → 下载”取得完整 CSV：81 字节，表头 `mapped_message`，三条 `browser original core`。SHA-256 为 `a7189e1fb83166fbb5c3d7a8ca2b7b7471b2ca011d663b4c8ab0eae0439d99f0`，与原预期一致。
- 页面“运行日志 → 读取日志”实际展示首批 32768 字符。该次新浏览器会话控制台 0 errors / 0 warnings；此前启动失败会话独立保留，不混作本次结果。
- 原生 256 MiB scratch 正常，`File too large` 为 0，task-schedule 日志缺失为 0。原海康 SIFT 的 class/discriminator/nestedAppender/filter 结构保留，10 份非空任务日志落盘。
- 仍保留首次读取不存在历史文件的原引擎提示 1 次；随后原实现写出 90 字节、3 行历史记录及 2828 字节调度日志。不宣称原引擎日志完全无提示。

页面与下载证据在本轮持久日志目录：`browser-running-state.json`、`browser-completed-state.json`、`browser-run-status.json`、`artifact-proof.json`、`browser-downloaded-output.csv`、`run-completed.png`、`run-results.png`、`run-log.png`。执行器独立只读复核见 `rynew-runtime/hdi-native/linux-x86-lab/resume-20261025/final-platform-run-proof.json`。

## 本轮检查

持久日志根目录为 `rynew-runtime/research/20261025-hdi-local-resume/`，不依赖 `/tmp` 文件。

| 检查 | 结果 |
| --- | --- |
| 根树完整 `npm run verify:frontend`，启用原包 Store 测试 | 321 项通过、0 失败、0 跳过；UI Guard、生产构建通过 |
| 平台前端产物兼容检查 | 168 个 JavaScript 文件通过 Chrome 64+ 检查；不据此推断原厂 bundle 的浏览器兼容性 |
| 根树 HDI 后端定向 Maven 测试，启用原包/原编译器/真实浏览器 DTO | 108 项通过、0 失败、0 跳过 |
| Python Core 与安装入口测试 | 49 项中 47 项通过，2 项 Linux 专属测试在 macOS 跳过 |
| 握手修复后定向回归与打包 | worker 12 项、运行 13 项、调度 14 项，共 39 项通过、0 跳过；Maven package 通过 |

恢复时发现首次 worker 握手预算不足：平台 30 秒超时，而 worker 必须先校验原包、验证隔离能力并启动最长允许 60 秒的内置协调器。独立 exact-argv hello 实测约 24.5 秒成功，随后 shutdown 正常。最初页面资源请求返回 503，未创建新的业务运行。修复后首次握手独立使用默认 180 秒预算（可配 60–300 秒），普通请求仍为 30 秒；超时后保持断开，不重启、不重发。同步锁排队时间不含在单次请求预算内，生产代理读超时也需与冷启动相适应。

## 交付边界

当前开放固定数据生成、字段映射和同步本地 CSV / 行输出，最多 32 节点单链。数据库、FTP/SFTP、消息队列、表达式、分支、多实例、实时和依赖任务尚未逐项迁移验收。节点行数和吞吐尚未采集，页面不能将其解释为零。

此次没有合并主分支或部署 250。数据库新增内容见 `WDF100.0/sql/data_governance_hdi_core_upgrade_20261013.sql`，安装与回退见 [SETUP.md](../core-runtime/SETUP.md) 和 [README.md](../README.md)。
