# 本机 Kafka 验收运行时

该运行时专供数据治理模块的现代 Kafka 接入和有界批次验收，使用官方 Apache Kafka **4.1.2**、既有独立 **JDK 21**、单节点 KRaft。2026-09-10 核对时，官方仍将 4.1.2 列为受支持发行版；当时最新版本为 4.3.1。本次按明确的验收版本要求固定 4.1.2，不自动追随版本升级。[官方下载与版本列表](https://kafka.apache.org/community/downloads/)

## 目录及监听范围

运行时根目录必须已有 `runtime.json`，且 owner 为 `rynew-data-governance-runtime`。新增内容只在该根的 `kafka/` 子目录中；不修改 `runtime_ctl.py`、原 `artifact-lock.json` 或其他服务配置。

| 位置 | 作用 |
| --- | --- |
| `kafka/owner.json` | 独立 owner、规范化目录、随机实例 ID、cluster ID、格式化状态 |
| `kafka/downloads/` | 官方压缩包、SHA-512、签名和 KEYS |
| `kafka/apps/kafka_2.13-4.1.2/` | 已验证的官方安装包内容 |
| `kafka/installation.json` | 安装包 SHA-512 和解压后逐文件 SHA-256 清单 |
| `kafka/private/server.properties` | 固定的本机 KRaft 配置 |
| `kafka/data/` | Kafka 数据、消费位点和集群元数据 |
| `kafka/process.json` | 启动 PID 与启动时间、完整命令身份 |
| `kafka/tools/` | 从仓库复制并用独立 JDK 编译的合成验收程序 |
| `kafka/evidence/` | 下载验证、初始化、停止及重启收发证据 |

Kafka 根目录为 700；配置、状态和证据文件为 600。Broker 固定 `127.0.0.1:19092`，Controller 固定 `127.0.0.1:19093`，`advertised.listeners` 也固定为 `127.0.0.1:19092`。健康检查同时核对实际 PID 的 TCP LISTEN 地址和 Admin API 返回的 broker 地址、cluster ID、单一 KRaft voter。

采用 `process.roles=broker,controller`、`controller.quorum.bootstrap.servers=127.0.0.1:19093`；首次调用 StorageTool 使用 `format --standalone`。此配置用于本机开发；Kafka 官方将 combined 模式用于这类小型环境，并说明动态 quorum 的独立初始化方式。[KRaft 配置](https://kafka.apache.org/41/operations/kraft/)

## 命令

在对应源码工作树中运行：

```bash
KAFKA_DEV_RUNTIME=/Users/krizen/Documents/Code/projects/2026projects/rynew-runtime/data-governance
python3 tools/data-governance/kafka_dev.py prepare --runtime-root "$KAFKA_DEV_RUNTIME"
python3 tools/data-governance/kafka_dev.py start --runtime-root "$KAFKA_DEV_RUNTIME"
python3 tools/data-governance/kafka_dev.py status --runtime-root "$KAFKA_DEV_RUNTIME"
python3 tools/data-governance/kafka_dev.py health --runtime-root "$KAFKA_DEV_RUNTIME"
```

`prepare` 验证包、解压、编译探针并检查数据目录。仅在正确归属、尚未格式化且为空的新数据目录中执行格式化；非空无元数据、cluster ID 不符、已格式化数据丢失等情况都拒绝。没有 `--ignore-formatted` 或自动清空路径。

`start` 先核验配置与完整安装清单，再检查两个端口。已有其他 listener 时直接拒绝；不会根据端口或进程名称查杀。启动使用 JDK 的绝对路径及 `kafka.Kafka` Java main，忽略继承的 JVM 注入选项和远程 JMX 变量；不修改系统 Java、全局 PATH 或其他服务。

```bash
python3 tools/data-governance/kafka_dev.py stop --runtime-root "$KAFKA_DEV_RUNTIME"
```

`stop` 只对同时符合记录的 PID、启动时间、完整命令、实例标记和 `kafka.Kafka` main 的进程发送一次 SIGTERM。身份不符即拒绝；不降级到 SIGKILL，也不删除消息或位点。该命令也是退出本次运行时的回滚动作。

## 下载验证

专用清单位于 `tools/data-governance/kafka-artifact-lock.json`。固定包为 `kafka_2.13-4.1.2.tgz`，133,586,323 字节，SHA-512：

```text
78ac6e488b1071122f9608dfdb363f6fe50e1dbbc492347002c0398dfbf77e0d8caa5bf794c5937379721004dfe92025937d238b0faf4eb715529417fd43b491
```

只接受清单中的 Apache HTTPS 地址，拒绝重定向到其他位置。比较官方 SHA-512 文件、仓库固定摘要及下载实算摘要三者，之后通过既有 `tools/pgp-venv/bin/python` 验证 detached signature。固定签名公钥指纹为 `29DFD0B67638FE76706B399DDCFDB318BA5D9AA8`；公钥来自官方 HTTPS KEYS。此证据不包含独立 Web of Trust 或撤销状态认证。[官方 4.1.2 文件目录](https://downloads.apache.org/kafka/4.1.2/)

解压前拒绝路径越界、链接、特殊文件、重复成员及超量内容。每次启动还会比对安装清单，新增或改动 JAR 不会静默加入运行类路径。Broker 与 StorageTool 的类路径只包含官方安装库；探针编译使用 `-proc:none`，其源码与编译结果另存摘要，每次执行前连同依赖检查。

## 验收

```bash
python3 -m unittest discover -s tools/data-governance -p test_kafka_dev.py -v
python3 tools/data-governance/kafka_dev.py smoke --runtime-root "$KAFKA_DEV_RUNTIME"
```

`smoke` 会**重启这个独立 Kafka 进程一次**。先协调使用它的其他本机联测，再运行；NiFi、RYNEW、Chrome 等其他服务不在该工具控制范围。

每次 smoke 使用新的 `rynew-governance-smoke-<随机ID>` topic 和独立 group，显式创建 2 个分区；`auto.create.topics.enable=false`。消息只有合成标记、随机验收 ID、分区、序号与合成中文文本。探针不接受 broker 地址参数，也不读取原附件连接。

实际验收顺序：

1. 每分区写 3 条消息，检查 key、消息体及 offset 0/1/2。
2. 关闭自动提交，消费 6 条并证明显式提交前没有 group offset。
3. 手动提交每分区的 next offset 为 2，留每分区 1 条未确认记录。
4. SIGTERM 停止并重新启动该 owned broker；核对 cluster ID 和监听地址未变。
5. 同 group 从 2/2 恢复，正好读到 2 条；再手动提交到 3/3。
6. 显式从头重放，证明原 6 条消息及 key/body 在重启后仍存在且一致。

本次开发验收通过 14 项离线安全测试和上述真实 broker smoke。完整结果在 `kafka/evidence/latest-smoke.json`，包验证在 `artifact-verification.json`。这证明本机现代 Kafka 的基本持久化与手动提交路径；原 ZooKeeper 消费者位点迁移、旧插件生命周期、源批次到 FTP 的交付一致性由独立适配器及业务闭环测试验证。
