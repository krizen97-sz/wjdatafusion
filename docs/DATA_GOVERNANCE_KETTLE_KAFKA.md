# 原 Kettle Kafka 节点的 ZooKeeper 实测环境

本环境用于实际运行磁盘原工具包的 KafkaConsumer、ScriptValueMod、KafkaProducer，不连接附件中的原主题、原消费组或任何业务目标。新运行目录独立于既有 Kafka 4.1.2；不使用 Docker，不替换系统 Java，不操作 250。

## 固定底座及归属

- Apache 官方 Kafka **3.9.2 / Scala 2.13**，使用发行包内 ZooKeeper。配置选择 ZooKeeper 模式，不运行 KRaft 格式化工具。
- ZooKeeper 仅 `127.0.0.1:22181`，Kafka 仅 `127.0.0.1:29092`，advertised.listeners 同为后者；ZooKeeper 管理 HTTP 服务关闭。
- 使用现有 Java 17 或 21。原 Kettle worker 和官方 Kafka 探针运行在不同 JVM/classpath，不能把现代客户端换入原节点后声称原插件已运行。
- 根目录必须以 `kafka-legacy` 命名，owner/root/instanceId 写入 600 权限标记；未知现有目录拒绝接管。启动前检查端口，进程同时按 PID、启动时间/完整命令、实例标记核对；不按端口杀进程。
- 归档、安装逐文件清单、配置、探针 classes 均校验；已初始化的数据目录丢失时拒绝自动重建。停止只发 SIGTERM，不能确认时保留错误，不自动强杀或删数据。

锁定信息位于 `tools/data-governance/kettle_kafka_fixture_lock.json`。下载比对仓库固定 SHA-512、官方 SHA-512 和实际文件；按固定官方公钥指纹验证 detached signature。官方归档支持 Range，本工具验证每段 Content-Range、长度及最终合并摘要，不信任分段成功本身。

本次官方签名创建于 2026-02-07，发布密钥于 2026-08-25 到期。工具明确检查密钥在签名时有效，并验证签名数学；只接受“现已过期”这一历史密钥状态，不忽略其他已检测异常。证据保留 `validAtSigning/signatureCryptographicallyVerified/signerExpiredNow`，不声称当前密钥仍有效，也不声称独立信任链和撤销审计。

## 命令

```sh
python3 tools/data-governance/kettle_kafka_fixture.py prepare \
  --runtime "$KETTLE_KAFKA_RUNTIME" --java-home "$EXISTING_JAVA_HOME" \
  --pgp-python "$PGP_VENV/bin/python"
python3 tools/data-governance/kettle_kafka_fixture.py start --runtime "$KETTLE_KAFKA_RUNTIME"
python3 tools/data-governance/kettle_kafka_fixture.py status --runtime "$KETTLE_KAFKA_RUNTIME"
python3 tools/data-governance/kettle_kafka_fixture.py stop --runtime "$KETTLE_KAFKA_RUNTIME"
```

首次 prepare 需要下载约 117 MiB 归档，不安装全局包；PGPy 使用既有隔离 venv。prepare 要求端口空闲，已经运行时使用 status/probe，而不是再次 prepare。

原节点全链证明：

```sh
python3 tools/data-governance/kettle_kafka_fixture_proof.py \
  --runtime "$KETTLE_KAFKA_RUNTIME" --worker-runtime "$ORIGINAL_WORKER_RUNTIME" \
  --worker-module /absolute/path/to/tools/data-governance/kettle_worker.py \
  --worker-sources /absolute/path/to/data-governance/kettle-worker/src
```

工具校验原 worker 清单中的全部 JAR，将其复制到本环境自己的 worker/lib，保持 custom-first 类顺序，在自己的 classes 编译 worker。不会改动其他 worker 的类库或入口。OS 沙箱仅授权专属 ZooKeeper/Kafka 的两个 IPv4 回环端口和本次操作文件目录。所有主题和组都必须带 `kettle-v2-随机ID` 前缀；预览组以 `-preview` 结束，绝不能使用业务组。

## 从原类实测确认的配置语义

| 项目 | 原类行为及本平台要求 |
| --- | --- |
| 输入协议 | 原 KafkaConsumer 使用 ZooKeeper/旧 ConsumerConnector；现代 Kafka 4.1.2 的独立客户端验收不能替代此项 |
| 输出编码器 | 原 KafkaProducerStep 已把字符串转换为 byte[]，必须使用 `kafka.serializer.DefaultEncoder`；StringEncoder 会产生 `[B cannot be cast to String` |
| 有限输入 | LIMIT 约束输入消息数，测试用 8 条消息验证 5＋3 两批；同时核对真实 ZK 位点与目标主题尾部，不以页面计数替代 |
| 空主题开关 | `STOPONEMPTYTOPIC` 在该原 Meta 中“元素存在即 true”，写 false 字符串仍启用；关闭时必须省略元素 |
| 自动提交 | `auto.commit.enable=true` 可能在下游发送失败时仍提交已取的消息；不能提供整链成功保障 |
| 原手动提交 | 该海康包 `Trans$3` 在 `isFinished && errors<=0` 时显式 commitOffsets，即使 auto.commit=false；不检查 preview/stopped |
| 预览屏障 | 必须使用独立预览组且禁自动提交，并在 worker 安装禁止显式 commitOffsets 的预览代理；只设置 auto.commit=false 不足以保护位点 |
| 运行中停止 | 原 hasNext 可阻塞；测试使用有界 consumer.timeout.ms，在运行中明确请求 STOP，核对原进程正常退出，不能把强杀当作正常停止 |

标准运行采用 `auto.commit=false`，由原引擎在完整转换成功后手动提交。该范围只涵盖这条同步 KafkaProducer 的转换；若父 Job 之后还有 FTP、外部接收方确认或部分输出重试，则必须另外设计父作业级确认与幂等协议，不能延伸声称端到端原子性。

## 实测验收项目

1. 官方探针创建 1 分区的独立输入/输出主题，写入 8 条合成消息。
2. 原 KafkaConsumer → 原 ScriptValueMod 增加可辨识后缀 → 原 KafkaProducer，同步发送第一批 5 条；目标读回仅 5 条、ZK offset=5。
3. 同一专属组运行第二批 3 条；目标共 8 条，不重复、不丢尾部。
4. 故意配置错误输出编码器，原 producer 报错；在 auto.commit=false 的运行中，原手动提交逻辑不前移该独立失败组位点。
5. 预览只运行上游和选中处理节点，不执行输出节点；新预览组关闭自动提交，结束后同时核对 broker/ZK 无位点，目标主题数量不变。
6. 独立预览组取到数据后请求 STOP；正常停止、无强杀、broker/ZK 无位点。
7. fixture 停止/重启保持 clusterId、已写消息和消费位点，端口与 PID 归属再次核验。

每次证据保存在运行目录 `evidence/kettle-v2-*/`；`acceptance.json` 仅在所有断言通过后生成。原始失败样本和日志保留，不能用后续成功覆盖第一次发现的风险。基础目录/PID/下载来源/固定端口检查运行：

2026-09-10 已通过 7 项边界测试及上述原 Kafka 全链。最终独立源主题 8 条按 5＋3 写回；错误编码器用例 errors=1 且未提交源位点。预览与停止均实测到 `preview-offset-commit-blocked`，ZK/broker 位点为空、consumer owners 清空、输出主题仍为 8 条，停止没有强杀。停/启 fixture 后 clusterId 与原先 8 条目标数据保持；`evidence/restart-persistence.json` 保存回读结果。证明使用已提交的 broker `23f2966` 与协作 worker 的预览屏障源码快照，不能据此声称随后 broker 恢复机制已经验收。

```sh
python3 tools/data-governance/kettle_kafka_fixture_test.py
```

## 主源

- [Apache Kafka 3.9.2 官方归档](https://archive.apache.org/dist/kafka/3.9.2/)
- [Kafka 3.9 ZooKeeper 启动方式](https://kafka.apache.org/39/getting-started/quickstart/#kafka-with-zookeeper)
- [官方 SHA-512](https://archive.apache.org/dist/kafka/3.9.2/kafka_2.13-3.9.2.tgz.sha512)
- [官方签名](https://archive.apache.org/dist/kafka/3.9.2/kafka_2.13-3.9.2.tgz.asc)

私有包行为以实际 classSource、字节码和真实输入/目标/位点证据为准，不按社区同名插件文档猜测海康改动。
