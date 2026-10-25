# Kafka 元数据连接前校验

平台的“测试连接”和主题选择不再直接使用主 JVM 的 Kafka AdminClient。`HdiKafkaMetadataGateway` 将经过属主与部署策略核验的 bootstrap、全部允许的 advertised endpoint 和只读操作类型交给既有 `HdiCompilerClient`，由一次性原 SDK JVM 处理 `connectionMetadata`。使用原包自带的 Kafka client 3.9.1；没有替换任务原 Kafka 输入/输出插件，也没有修改其消费者、生产者或 offset 算法。

原先先 `describeCluster()`、再检查返回节点的方式太晚：客户端已可能自动连接集群公布的节点。现在在创建 AdminClient **之前**，仅在该子 JVM 安装前置 `SecurityManager.checkConnect` / `SocketPermission` 守卫。每次连接的 IPv4 与端口必须精确匹配可信清单；不允许主机名、任意 DNS、通配地址、监听或接入连接。禁止移除/替换守卫。守卫不回收为无防护状态，整个子进程随请求退出；主平台 JVM 不安装 SecurityManager，不改变全局网络设置。

只提供 `test`（DescribeCluster）和 `topics`（ListTopics）两种原客户端只读调用，不写消息、不消费、不修改 group offset、不创建/删除主题。网络拒绝及元数据失败返回固定原因，平台显示固定中文，不回显原异常或请求配置。异常退出、超时或无法安装守卫一律失败，无主进程直连回退。

推荐已经实测的组合：**平台、编译器及元数据子 JVM 使用 JDK 17；原 Core 独立使用 Linux x86_64 JDK 11**。守卫代码接受 Java 11 / 17，其他版本直接拒绝。Java 11 在隔离实验机上仅完成不可移除、DNS/端口拒绝的最小守卫合同验证；QEMU 全 SDK 元数据探测超时，不能宣称完整 Java 11 元数据验收通过，也不能据其耗时推断原生 Linux 性能。应按已验证的 JDK 17 元数据组合部署，不使用全局 `-Djava.security.manager` 参数。

验证包括：当前本机 Kafka 实际连接和主题列表；双 broker fixture 的允许对照会收到真实流量，而同一 advertised endpoint 不在清单时新增连接数和收到字节均为 0；守卫不能被移除，未知 DNS 及端点被拒绝；Java 网关拒绝没有有效守卫或返回范围外节点的响应。该隔离是连接目的地边界，不声称完整文件系统沙箱或已开放 SASL/TLS。

```sh
HDI_ORIGINAL_BUNDLE=/绝对路径/原bundle \
HDI_COMPILER_JAVA_HOME=/绝对路径/jdk17 \
python3 -m unittest discover -s tools/data-governance -p test_hdi_kafka_metadata.py
```

本机 Kafka 正例需额外设置 `HDI_KAFKA_FIXTURE=1`，只指向本任务的 `127.0.0.1:19092` fixture；不要把测试入口当作生产连接配置。
