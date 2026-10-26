# 原任务核心接入平台

当前实施范围是用户确认的“迁任务核心，管理接本平台”：保留安装包中的 DFP 画布、节点表单、配置生成器、执行核心和选定插件。任务、版本、权限、计划、运行记录与完整文件归档由 RYNEW 管理。

旧的管理端移植、Agent、本地身份桥仅保留为研究代码，不是本模式的部署依赖。运行不连接 10 服务器，不启动原 HDI 管理后台，不复制源站身份、许可证或实验授权配置。旧 Kettle、NiFi 运行入口也不参与本链路。

## 已实现的基础范围

| 部分 | 当前边界 |
| --- | --- |
| 原页面 | 原任务列表、画布、插件表单、连线、任务设置和运行配置；仅宿主与 HTTP 契约适配 |
| 原插件 | 固定数据生成、字段映射、PostgreSQL 输入/输出、File 输入/输出（FTP、SFTP及本地输出）、Kafka 输入/输出、RabbitMQ 输入/输出；最多32节点单链 |
| MySQL | 经用户确认的两个最小驱动/方言子类，继承原JDBC读写；独立平台包及固定摘要，页面明确标为“原JDBC适配” |
| 连接管理 | 原数据源页面/表单、测试连接、实际表/主题/字段读取；平台加密保存口令，按用户隔离并冻结连接版本 |
| 任务管理 | 平台用户隔离、不可变版本、陈旧窗口冲突检查、显式发布 |
| 执行 | 真实原配置字节、独立执行 ID、真实状态、停止、日志、输出归档及下载；原持续输入经主动停止确认后，可下载明确标记的部分结果 |
| 计划 | 原手动、月、周、日、小时和分钟语义；现有 Quartz 计算时间，平台数据库持久化 |
| 部署 | 单管理进程；Linux x86_64 原执行核心，复用平台 MySQL / Redis，原核心所需协调组件随模块配置 |

三节点基础验收见 [原记录](docs/LOCAL-ACCEPTANCE-20261025.md)，六路连接器的真实浏览器配置、试运行、正式运行及目标回读见 [连接器验收](docs/CONNECTOR-ACCEPTANCE-20261025.md)。当前范围是 PostgreSQL 选表读取/INSERT写入、FTP被动传输、UTF-8 CSV/行文件与Kafka基本批次；Kafka JSON已完成真实输入输出验收，字符串格式按原字面值 `string` 生成，尚未计入本轮目标回读结论。

新增MySQL、RabbitMQ、SFTP的来源、浏览器验收与真实结果见 [新增连接器验收](docs/MORE-CONNECTOR-ACCEPTANCE-20261025.md)。MySQL的表读取/INSERT继续使用原JDBC基类，仅驱动名、URL前缀、标识符引号三个方法由平台补足；原包未提供可直接使用的专用MySQL子插件，不能将平台适配声称为原厂二进制。

不能据此宣称原包全部插件或用户全部业务已迁移。当前RabbitMQ为AMQP/JSON、SFTP为密码方式及UTF-8 CSV验收；Kafka认证/Avro/BSON、RabbitMQ TLS/MQTT、SFTP私钥、表达式、分支、增量/CDC和依赖任务未开放。原表单中的未支持选项执行前会明确拒绝；未知插件、分叉和环不接收，不静默改写业务含义。

## 数据库与配置

先备份平台数据库，依次执行以下独立脚本，均可重复执行、不修改原有业务表：

1. `WDF100.0/sql/data_governance_hdi_core_upgrade_20261013.sql`：七张任务/版本/运行/计划表及初始任务域。
2. `WDF100.0/sql/data_governance_hdi_connections_upgrade_20261025.sql`：连接、加密连接版本、真实表/主题标识三张表。

本轮三个连接器复用上述十张表，没有新增数据库DDL；已执行过两份脚本的环境不需要另补一套表。

任务与连接存储明确使用平台 `masterDataSource`。连接端点、FTP被动端口和Kafka公布的broker由部署配置限定，具体配置与原表单字段见 [CONNECTIONS.md](core-runtime/CONNECTIONS.md)。

模块参数为 `data-governance-hdi-core`：

```yaml
data-governance-hdi:
  enabled: false
data-governance-hdi-core:
  enabled: true
  public-origin: https://your-platform.example
  bundle-dir: /opt/rynew/hdi/bundle
  platform-adapters-dir: /opt/rynew/hdi/platform-adapters
  platform-adapters-manifest-sha256: "<已审核平台适配manifest的64位SHA256>"
  host-dir: /opt/rynew/hdi/host
  state-dir: /var/lib/rynew/hdi/platform
  worker-startup-timeout-seconds: 180
  compiler-command:
    - /usr/bin/python3
    - /opt/rynew/tools/data-governance/hdi_original_compiler.py
    - --bundle
    - /opt/rynew/hdi/bundle
    - --java-home
    - /usr/lib/jvm/java-17-openjdk-amd64
    - --runtime
    - /var/lib/rynew/hdi/compiler
    - --platform-adapters
    - /opt/rynew/hdi/platform-adapters
    - --platform-adapters-sha256
    - "<与上面相同的SHA256>"
  worker-command:
    - /usr/bin/python3
    - /opt/rynew/tools/data-governance/hdi_core_worker.py
    - --config
    - /var/lib/rynew/hdi/private/worker.json
```

`worker-command` 使用部署机本地worker的明确参数数组，不经shell；与编译器保持同一个受校验bundle。采用已实测组合：平台、编译器和元数据子JVM复用现有Java17，**原执行核心单独使用Linux x86_64 Java11**。上例JDK路径须改成部署机的真实路径。使用 [本机安装准备入口](core-runtime/SETUP.md) 明确区分两个JDK、构建控制桥并生成私有配置；它不会启动服务或覆盖既有目录。协议见 `core-runtime/PROTOCOL.md`，元数据访问边界见 [METADATA-NETWORK.md](core-runtime/METADATA-NETWORK.md)。准备完成后仍需真实连接及任务验收。

开启MySQL时，Catalog、编译器CLI和worker配置中的平台包摘要必须完全相同；目录和摘要必须成对配置。MySQL任务快照也冻结该摘要，换包后旧任务明确拒绝运行，不会静默用同版本名下的其他适配代码或参数替代。仅部署原插件时可以同时省略这两项及CLI对应参数。

首次访问执行资源会校验完整 bundle、探测隔离能力并启动内置协调器，只有全部成功才返回可用状态。`worker-startup-timeout-seconds` 只控制首次握手，默认 180 秒，允许 60–300 秒；普通请求及后续资源状态查询的协议交换仍限 30 秒，受控关闭的协议交换限 80 秒。客户端串行处理请求，等待前一请求释放锁的时间不计入这些交换预算；部署反向代理的读取超时需覆盖启动及排队时间。冷启动超时仍关闭通道并要求核查，不会自动重启 worker 或重复提交任务。

缺失 worker 或隔离能力时，执行资源显示不可用，不自动连接原平台。开发机跨架构实验的 SSH 通道不是生产依赖。

平台状态、编译目录和 worker 私有配置放在 Git 外，目录属运行账号且权限 0700，私有配置/密钥 0600。原包由 `tools/data-governance/hdi_native_bundle.py` 按固定来源摘要和文件清单提取；不得把现场凭据、授权工具或现场运行配置放进 bundle。原 `instance/conf/task_config.json` 等参数定义属于必需程序资源，保留原字节并由清单校验。

## 页面操作

1. 进入治理工作台，使用原“添加”表单建立任务。
2. 通过“连接管理”在独立窗口使用原表单新增连接并测试；返回画布拖入支持的节点，选择真实表/主题、配置字段并连线。
3. 使用本地文件输出时选择目录 `/平台输出`。这是本次运行私有输出目录的逻辑名称，文件在平台运行记录中下载；FTP/SFTP输出填写所选连接对应的远端目录。
4. 使用原“试运行”检查结果；平台测试会实际执行输出，Kafka可能提交消费位置，RabbitMQ原输入会确认消息。请使用测试目标、队列和专用消费组，不能把关闭自动提交当作不提交。正式运行使用原一次性或支持的周期配置。
5. 在平台外层“运行记录”查看具体版本、状态、原日志和归档文件。

原同步File输出可能带三个 `_RESULT_*` 响应字段；需要业务列专用文件时，在原字段页将它们设为“是否忽略”，使用原 `passThrough` 能力，不改插件。FTP上传后删除本地暂存、数据库或消息输出没有本地文件均属正常情况。

RabbitMQ输入会持续监听；空队列等待时间不是总采样时长。验证数据后使用原“暂停任务→停止”，确认原exec为`stop`且资源清理完成后，记录保持`CANCELLED`，界面显示已停止及“部分结果”，不会伪装成功。只有明确用户停止、无错误/恢复标记、文件完整性校验通过时才发布部分文件；超时、失败、强制结束和后台关闭不提供这一授权。worker执行时限仍生效，默认300秒，可按部署配置设置1–86400秒，不宣称无限常驻服务。

节点计数未采集时显示未知，不能把空指标解释为零行。界面将 `SUCCEEDED` 标为“引擎完成”；原JDBC全行失败也可能返回finish，平台从本次原日志读取已观测输出告警，显示“完成 · 待核对”。无告警不代替目标端回读。原任务重试可能重复实际输出，测试失败场景应明确配置重试策略。失败记录保留；原“暂停任务→停止”可确认已结束的失败并恢复编辑，不能清除未知运行。异常断连/重启后的未知运行不自动重放。

## 验证与回退

编译器契约见 `compiler/README.md`，调度、运行及失败语义见 `core-runtime/PLATFORM-RUNS.md`，宿主复用与原组件适配见 `docs/DFP-HOST-REUSE-PLAN.md`。

上线前必须分别确认 SQL、平台启动、原页面保存/回读、Linux 真实执行、停止、产物下载摘要和周期触发。单元测试或原核心命令行成功不能代替浏览器闭环。生产包应在验收完成后的干净提交构建，不能从正在运行且会继续修改的工作目录交付。

回退先禁用新计划并核查当前运行终态，停止本模块 worker，再将 `data-governance-hdi-core.enabled` 设为 false 并回退平台制品。保留十张任务与连接表、加密连接版本和归档用于审计，不执行 DROP 或清空。不要仅切换配置就自动启用旧引擎来执行这些原生任务。
