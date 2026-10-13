# 原任务核心接入平台

当前实施范围是用户确认的“迁任务核心，管理接本平台”：保留安装包中的 DFP 画布、节点表单、配置生成器、执行核心和选定插件。任务、版本、权限、计划、运行记录与完整文件归档由 RYNEW 管理。

旧的管理端移植、Agent、本地身份桥仅保留为研究代码，不是本模式的部署依赖。运行不连接 10 服务器，不启动原 HDI 管理后台，不复制源站身份、许可证或实验授权配置。旧 Kettle、NiFi 运行入口也不参与本链路。

## 已实现的基础范围

| 部分 | 当前边界 |
| --- | --- |
| 原页面 | 原任务列表、画布、插件表单、连线、任务设置和运行配置；仅宿主与 HTTP 契约适配 |
| 原插件 | 有界固定值生成 → 字段映射 → 同步本地 CSV / 行文件输出；最多 32 节点的单链 |
| 任务管理 | 平台用户隔离、不可变版本、陈旧窗口冲突检查、显式发布 |
| 执行 | 真实原配置字节、独立执行 ID、真实状态、停止、日志、完整输出归档及下载 |
| 计划 | 原手动、月、周、日、小时和分钟语义；现有 Quartz 计算时间，平台数据库持久化 |
| 部署 | 单管理进程；Linux x86_64 原执行核心，复用平台 MySQL / Redis，原核心所需协调组件随模块配置 |

上表说明代码接入范围，不单独构成浏览器验收结论；真实链路结果需结合本轮验收记录。这是基础任务核心的接入范围，不能据此宣称原安装包全部插件或已有业务已完成迁移。数据库、FTP/SFTP、消息队列、表达式、分支、多实例、实时与依赖任务需要逐项迁移并完成真实测试后开放。支持节点中尚未配置完整或部分未开放的参数可保留为草稿，执行前会明确拒绝；未知插件、分叉和环当前连草稿也不接收，不能静默改写业务含义。

## 数据库与配置

先备份平台数据库，再执行独立升级脚本 `WDF100.0/sql/data_governance_hdi_core_upgrade_20261013.sql`。脚本只新增本模块七张表和初始任务域/计数器，可重复执行；不修改现有业务表。任务存储明确使用平台 `masterDataSource`，不会使用其他模块的数据源模板。

模块参数为 `data-governance-hdi-core`：

```yaml
data-governance-hdi:
  enabled: false
data-governance-hdi-core:
  enabled: true
  public-origin: https://your-platform.example
  bundle-dir: /opt/rynew/hdi/bundle
  host-dir: /opt/rynew/hdi/host
  state-dir: /var/lib/rynew/hdi/platform
  compiler-command:
    - /usr/bin/python3
    - /opt/rynew/tools/data-governance/hdi_original_compiler.py
    - --bundle
    - /opt/rynew/hdi/bundle
    - --java-home
    - /opt/rynew/hdi/jdk
    - --runtime
    - /var/lib/rynew/hdi/compiler
  worker-command:
    - /usr/bin/python3
    - /opt/rynew/tools/data-governance/hdi_core_worker.py
    - --config
    - /var/lib/rynew/hdi/private/worker.json
```

`worker-command` 使用部署机本地 worker 的明确参数数组，不经 shell；与编译器保持同一个已校验 bundle。平台自身继续使用 Java 17，编译器可用 Java 11 / 17，**原执行核心必须单独配置 Linux x86_64 Java 11**，不能直接照搬平台的 JAVA_HOME。私有 worker JSON、控制桥构建、Redis 口令文件和内置协调器规则见 `core-runtime/PROTOCOL.md`。先构建控制桥并填写其实际 SHA-256，再启动 worker 的真实预检；仅填路径不算部署成功。

缺失 worker 或隔离能力时，执行资源显示不可用，不自动连接原平台。开发机跨架构实验的 SSH 通道不是生产依赖。

平台状态、编译目录和 worker 私有配置放在 Git 外，目录属运行账号且权限 0700，私有配置/密钥 0600。原包由 `tools/data-governance/hdi_native_bundle.py` 按固定来源摘要和文件清单提取；不得把现场凭据、授权工具或现场运行配置放进 bundle。原 `instance/conf/task_config.json` 等参数定义属于必需程序资源，保留原字节并由清单校验。

## 页面操作

1. 进入治理工作台，使用原“添加”表单建立任务。
2. 拖入支持的输入、处理、输出节点，完成原表单配置并连线。
3. 文件输出选择本地目录 `/平台输出`。这是本次运行私有输出目录的逻辑名称，完整文件在平台运行记录中下载。
4. 使用原“试运行”检查结果；需要正式运行时打开原运行配置并选择手动或支持的周期。
5. 在平台外层“运行记录”查看具体版本、状态、原日志和归档文件。

节点计数未采集时显示未知，不能把空指标解释为零行。运行成功和完整文件归档是两个独立结果；归档失败会保留原引擎成功状态，并显示具体限制。异常断连/重启后的未知运行不自动重放，防止重复写入。

## 验证与回退

编译器契约见 `compiler/README.md`，调度、运行及失败语义见 `core-runtime/PLATFORM-RUNS.md`，宿主复用与原组件适配见 `docs/DFP-HOST-REUSE-PLAN.md`。

上线前必须分别确认 SQL、平台启动、原页面保存/回读、Linux 真实执行、停止、产物下载摘要和周期触发。单元测试或原核心命令行成功不能代替浏览器闭环。生产包应在验收完成后的干净提交构建，不能从正在运行且会继续修改的工作目录交付。

回退先禁用新计划并核查当前运行终态，停止本模块 worker，再将 `data-governance-hdi-core.enabled` 设为 false 并回退平台制品。保留七张任务表、版本与归档用于审计，不执行 DROP 或清空。不要仅切换配置就自动启用旧引擎来执行这些原生任务。
