# Kettle 附件工具目录与配置来源

本次目录来自实际附件 `kettle6.1.3.0.zip` 的静态解析，目标是把完整工具包中的输入、处理、输出及作业工具变成可追溯的编排能力清单。它不把现有安全样本节点当成整个工具箱，也不把“目录里有这个工具”当成已经完成了浏览器配置、运行或生产兼容。

## 附件与复现

附件大小为 **716,365,328 bytes**，SHA-256 为：

```text
86b8b5e8ced63e43288f70911b2b3b077f2d2593917a7d6a45e84d42fe2ee902
```

采集脚本是 `tools/data-governance/kettle_inventory.py`，只依赖 Python 3.9+ 标准库。它直接读取 ZIP/JAR 的条目，不解压启动环境，不导入插件类，不启动 JVM，不执行原 KTR/KJB，不读取运行配置或连接原地址。输出不包含业务配置值；资源文案中的地址、凭据赋值有额外脱敏处理。

在仓库根目录执行：

```bash
python3 tools/data-governance/kettle_inventory.py --self-test
python3 tools/data-governance/kettle_inventory.py \
  --zip /Volumes/KINGSTON/kettle6.1.3.0.zip \
  --output /Users/krizen/Documents/Code/projects/2026projects/rynew-runtime/data-governance-kettle-v2/catalog/catalog.json
```

完整目录保存在独立运行证据目录，不提交仓库。`catalog.json` 无采集时间等随机字段，同一附件、同一脚本可生成相同内容。脚本解析失败会记录 `errors` 并返回非零退出码；输出中可按 `source` 精确定位原附件中的条目。`!/` 表示 JAR 或内嵌归档内的成员，例如：

```text
kettle工具/data-integration-hikvision/lib/kettle-6.1.0.7.36.jar!/kettle-steps.xml
kettle工具/data-integration-hikvision/plugins/steps/hbp-kafka-consumer/plugin.xml
```

## 目录范围与当前结果

本轮扫描覆盖外层全部 1,434 个 JAR、运行目录下的辅助分发 ZIP，以及它们内嵌的运行归档。外层 256 个 KTR/KJB 文件和 27 个非运行目录中的示例压缩包不读取内容。归档级字节重复会复用解析结果，仍保留每个原始来源。

| 统计口径 | 结果 |
| --- | ---: |
| 扫描归档（含内嵌与辅助分发包） | 1,473：1,470 JAR、3 ZIP |
| 不同归档字节哈希 | 895 |
| 去重归档中检查的 Class 条目 | 248,965 |
| 登记证据记录（保留重复及其他类型） | 919 |
| 步骤实现目录项 | 249 |
| 作业实现目录项 | 86 |
| 步骤与作业合计 | 335 |
| 按类型及 ID 去重的逻辑插件 | 332 |
| 有中文显示名称的目录项 | 335 |
| 找到登记类字节码的目录项 | 335 |
| 找到 Dialog 构造、类常量或命名约定对应类的目录项 | 329 |
| 找到执行类构造或作业实现登记证据的目录项 | 331 |
| 有原生 `step-attributes.xml` 的目录项 | 4 |
| 登记类存在多个不同字节码哈希的目录项 | 239 |
| 有类、但未找到步骤/作业登记的候选 | 2 |
| 单独保留的其他类型插件描述 | 1 |

335 是**静态登记目录项**，不等于可加载或可运行数量。所有项的 `runtimeLoaded`、`configurationValidated`、`executionValidated`、`browserAdapterAvailable` 均为 `false`，由后续独立验证证据提升状态。源包资源库插件 `PentahoEnterpriseRepository` 留在 `otherPluginDescriptors`，不混入步骤/作业数量。

原生 worker 在开发期间的一次运行快照报告了 **173 个可加载步骤**，其选定核心描述文件有 **189 个步骤声明**。证据位置为独立运行目录 `rynew-runtime/data-governance-kettle-worker-v2/capabilities.json`，该文件由 worker 生成，本采集脚本不读取也不依赖它。这是特定 classpath 下的运行发现口径，不能与 335 项（包含作业及所有插件归档）直接相减解释成缺失数。可加载也不意味着配置可往返保存、真实执行已通过或浏览器已完成验收；最终以 worker 的注册结果、类来源和逐工具执行记录为准。

## 中文名称与分类

每个名称、分类同时保留中文、英文及来源：`message_resource` 是指定包中的语言资源，`message_key_fallback` 是跨包同键匹配，`literal` 是原描述字面值，`editorial_translation` 是脚本里可审核的人工翻译。中文缺失时补译，原英文与登记文字保留。没有把英文消息键伪装成中文业务名称。

当前保留原包分类语义，涵盖 35 种分类显示值：

| 原包分类显示值 | 项数 | 原包分类显示值 | 项数 |
| --- | ---: | --- | ---: |
| 输入 | 47 | 输出 | 35 |
| 转换 | 26 | 查询 | 16 |
| 流程 | 17 | 连接 | 6 |
| 脚本 | 12 | 统计 | 7 |
| 映射 | 4 | 内联 | 3 |
| 应用 | 28 | 批量加载 | 13 |
| 大数据 | 20 | 地理空间 | 7 |
| 文件管理 | 18 | 文件传输 | 7 |
| 文件加密 | 3 | 加密 | 4 |
| 条件 | 12 | 通用 | 5 |
| 作业 | 6 | 邮件 | 3 |
| XML | 4 | 资源库 | 2 |
| 商业分析服务器 | 3 | 数据仓库 | 2 |
| 数据建模 | 2 | 敏捷数据集市 | 2 |
| 检验 | 3 | 校验 | 1 |
| 已弃用 | 5 | 不推荐的 | 1 |
| 实验 | 2 | Palo | 6 |
| OpenERP | 3 | — | — |

“应用”“检验/校验”“已弃用/不推荐的”等来自不同资源，不在静态取证阶段强行合并。`CloudStore` 在原包声明为“查询”，`JudgeTableCount` 声明为“输出”；这不代表其副作用已被验证。浏览器可以增加输入/处理/输出导航分组，但必须保留真实插件 ID、来源分类和实际执行语义。

## 海康及捆绑扩展的差异

`kettle-6.1.0.7.36.jar` 的 `kettle-steps.xml` 有 189 条，而同包 `kettle-engine-6.1.0.1-196.jar` 的对应描述有 168 条。前者多出的 21 个 ID 是：

```text
ActivemqConsumer, ActivemqProducer, CloudStore, ElasticSearch4HumanInfo,
ElasticSearchBulk, HBaseOutput, HbaseInput, ImpalaBatchOut, JsonInput,
JsonOutput, JudgeTableCount, KafkaConsumer, KafkaConsumer4Huawei,
KafkaConsumerByBroker, KafkaProducer, MongoDbInput, MongoDbOutput,
MyPlugin, NewKafkaProducer, RabbitmqConsumer, RabbitmqProducer
```

这仅比较包内两个描述文件，不把后者认定为未经修改的官方发行版；多出的 JSON/Elasticsearch 等工具也不能仅因出现在差集中就认定为海康独创。

以下 22 个登记实现项能通过海康命名、HBP 标识、云存储描述或 `com.ruckuswireless` 扩展命名识别。该分组是来源线索，不是版权归属结论：

| 插件 ID | 中文名称 | 登记类/执行来源线索 |
| --- | --- | --- |
| HbaseInput | HBase 输入 | `com.hikvision.bigdata.kettle.plugins.input.hbase.KettleHBaseInputMeta` |
| HBaseOutput | HBase 输出 | `com.hikvision.bigdata.kettle.plugins.output.KettleHBaseOutputMeta` |
| ImpalaBatchOut | Impala 批量输出 | `com.hikvision.impalabatchout.ImpalaBatchOutMeta` |
| ElasticSearch4HumanInfo | 人员信息 Elasticsearch 批量输出 | `com.hikvision.elasticsearch2humaninfo.ElasticSearchBulkMeta` |
| JudgeTableCount | 表记录数判断 | `com.hikvision.judgetablecount.JudgeTableCountMeta` |
| MyPlugin | 海康自定义 MyPlugin | `com.hikvision.myplugin.MypluginMeta`；名称不用于推断处理语义 |
| CloudStore | 上传图片到海康云存储 | `org.pentaho.di.trans.steps.cloudstore.CloudStoreMeta`，执行类 `CloudStore` |
| HBPKafkaConsumer | HBP Kafka 输入 | 独立 descriptor，`HBPKafkaConsumerMeta` / `HBPKafkaConsumerStep` |
| HBPFaceKafkaConsumer | HBP 人脸 Kafka 输入 | 独立 descriptor，`HBPFaceKafkaConsumerMeta` / `HBPFaceKafkaConsumerStep` |
| HBPKafkaProducer | HBP Kafka 输出 | 独立 descriptor，`HBPKafkaProducerMeta` / `HBPKafkaProducerStep` |
| HBPFaceKafkaProducer | HBP 人脸 Kafka 输出 | 独立 descriptor，`HBPFaceKafkaProducerMeta` / `HBPFaceKafkaProducerStep` |
| KafkaConsumer | Kafka 输入 | `com.ruckuswireless.pentaho.kafka.consumer.KafkaConsumerMeta` |
| KafkaConsumerByBroker | 按 Broker 连接的 Kafka 输入 | `consumerbybroker.KafkaConsumerMetaByBroker` |
| KafkaConsumer4Huawei | 华为 Kafka 输入 | `consumer4huawei.KafkaConsumerMeta` |
| KafkaProducer | Kafka 输出 | `com.ruckuswireless.pentaho.kafka.producer.KafkaProducerMeta` |
| NewKafkaProducer | 新版 Kafka 输出 | `com.ruckuswireless.pentaho.kafka.producer.NewKafkaProducerMeta` |
| ActivemqConsumer | ActiveMQ 输入 | `com.ruckuswireless.pentaho.activemq.consumer.ActivemqConsumerMeta` |
| ActivemqProducer | ActiveMQ 输出 | `com.ruckuswireless.pentaho.activemq.producer.ActivemqProducerMeta` |
| RabbitmqConsumer | RabbitMQ 输入 | `com.ruckuswireless.pentaho.rabbitmq.consumer.RabbitmqConsumerMeta` |
| RabbitmqProducer | RabbitMQ 输出 | `com.ruckuswireless.pentaho.rabbitmq.producer.RabbitmqProducerMeta` |
| MongoDbInput | MongoDB 输入 | `com.ruckuswireless.pentaho.mongodb.di.trans.steps.mongodbinput.MongoDbInputMeta` |
| MongoDbOutput | MongoDB 输出 | `com.ruckuswireless.pentaho.mongodb.di.trans.steps.mongodboutput.MongoDbOutputMeta` |

另有 `HBPCustomKafkaProducerMeta` 的实际类字节码，但未找到登记描述或步骤注解。它和 `JobEntryEmpty` 都保留在 `unregisteredClassCandidates`，包含来源、不同类哈希及声明字段，不擅自添加成一个可拖拽的运行节点。

三个同 ID、不同登记类的冲突必须保留：

| ID | 实现 A | 实现 B |
| --- | --- | --- |
| HBaseOutput | `com.hikvision...KettleHBaseOutputMeta` | `org.pentaho.big.data...HBaseOutputMeta` |
| MongoDbInput | `com.ruckuswireless...MongoDbInputMeta` | `org.pentaho.di...MongoDbInputMeta` |
| MongoDbOutput | `com.ruckuswireless...MongoDbOutputMeta` | `org.pentaho.di...MongoDbOutputMeta` |

目录键使用 `kind:id:registeredClass`。同一登记类又按完整 class 字节 SHA-256 分组，组内保留全部 JAR 路径。不同字节哈希可能仅来自编译或调试信息，也可能有行为修改；这里不推断行为相同或不同。`lib/kettle-6.1.0.7.36.jar`、旧 engine JAR、插件目录及 Karaf 缓存的发现均不代表最终加载次序。

## 配置字段如何追溯

| JSON 字段 | 真实来源 | 能证明什么 | 不能证明什么 |
| --- | --- | --- | --- |
| `registrations` | `kettle-steps.xml`、`kettle-job-entries.xml`、`plugin.xml`，或 Class 中的 `@Step` / `@JobEntry` | 原 ID、登记 Meta/作业类、名称和分类、库声明 | 类加载成功、有效运行 classpath |
| `registeredClassVariants` | 登记类的 Class 文件 | 类来源、哈希、父类、接口 | 运行时实际选中哪个副本 |
| `declaredInstanceFields` | Meta/作业类的字段表 | 字段名、JVM 类型、同类 getter/setter | 必填、默认值、校验、表格子项、完整可编辑配置 |
| `serializationIdentifierCandidates` | `getXML/loadXML/saveRep/readRep` 等方法的字节码字符串和结构标签 | 字段序列化线索及对应方法名 | 完整 XML 树、精确条件分支和 schema；非字段常量仍可能混入 |
| `relatedClasses` | `getStep/getDialog/getDialogClassName` 中构造、类常量或有实际类的命名约定 | Meta 与执行类/Dialog 的关系证据 | 构造一定成功、桌面对话框可直接在浏览器运行 |
| `dialogLabelCandidates` | Dialog 代码使用的消息键及语言包 | 配置项标题、列名和帮助文案的来源 | 控件绑定关系、字段验证、业务语义 |
| `stepAttributeDescriptors` | 原生 `step-attributes.xml` | 原字段 ID、XML/资源库键、类型和父子 ID | 全部插件通用配置协议 |

字段抽取不输出 Meta 默认值、任意常量池、原流程中的变量值或凭据。Java 类型中出现密码、地址等字段名是配置结构证据，不是对应的实际取值。配置键候选排除语言资源键和明显默认常量，优先保留在多个读写方法中重复的标识符、结构标签或声明字段同名项；仍须按候选对待。

原生属性描述只在 **CsvInput、MonetDBBulkLoader、PGBulkLoader、SelectValues** 四类步骤中找到。其他工具需要从各自 Meta 的 XML 读写、嵌套模型、校验和 Dialog 控件绑定建立配置契约，不能给数百节点复制一个只有名称的占位表单。

以下代表性配置线索来自实际类，完整候选和每个变体来源保存在 JSON：

| 工具 | 发现的真实结构 | 后续必须验证 |
| --- | --- | --- |
| HBP Kafka 输入 | `kafkaProperties`、`topic`、`field`、`keyField`、`limit`、`timeout`、`stopOnEmptyTopic` | Kafka 属性映射、字段输出、空主题停止、超时、消费位置/提交策略 |
| HBP 人脸 Kafka 输出 | `kafkaProperties`、`topic`、`messageField`、`keyField`；存在两个 Meta 字节变体 | HBP/人脸协议、键值、批量与失败提交、选定实际类 |
| CloudStore | `secretKey/accessKey` 等凭据字段名、`tokenIp/tokenPort`、`poolId`、`originalUrl`、返回字段 | 凭据托管、URL 变换、图片/云存储协议；本采集未访问服务 |
| 文本输入 | Meta 的 `content/filter` 嵌套对象，编码、分隔、表头、文件名及字段读写键 | 嵌套字段类型、旧流程默认值、坏行处理、中文与日期语义 |
| JSON 输入 | `jsonInputFiles`、字段源/文件源切换、路径缺失、空文件、源字段移除配置 | 多字段/多行展开、缺失路径、空输入、字段与文件源差异 |
| FTP_PUT 作业 | 服务器/端口/认证字段名、本地远端目录、通配、二进制、超时、只传新文件、代理及重命名 | 成功门控、文件提交/读回、失败重试、停止行为 |
| SPECIAL 作业 | `start/dummy/repeat`、周期类型、间隔、星期/日期与时分 | 开始/空节点/调度语义，不能把所有 SPECIAL 当作同一种节点 |

## 静态完整性与运行验收分开推进

本脚本的五个内置测试覆盖 Java 注解/字段读取、字节码操作数与指令边界、资源编码与续行、描述文件解析及脱敏。针对实际附件还需检查 `errors=[]`、目录项有来源、冲突分组保留、所有运行状态为 false，以及复现输出一致。没有运行 UI Guard 或前端构建：本次提交只有采集脚本和说明。

后续工具箱接入应分别记录以下证据，不通过一个 `supported=true` 隐藏差异：

1. **可加载**：固定 JAR 哈希、加载优先级、插件搜索目录；逐 ID 输出被选中的 Meta/执行类来源、缺依赖原因及重名冲突处理。
2. **可配置**：从原 Meta/XML 模型与校验约束得到真实配置；浏览器保存后可读回、导出/导入保持一致，嵌套表格和敏感字段有对应处理。
3. **可实时测试**：节点能显示真实输入/输出行、字段类型、行数、错误和耗时；用户修改配置后执行结果发生对应变化。
4. **可运行编排**：步骤 Hop 与作业的成功/失败/无条件路由均使用原引擎语义，支持异步状态、日志、停止、错误保留和重试。
5. **有代表性的验收**：输入→处理→输出链、复杂字段/脚本链、成功后 FTP 交付、失败分支和停止；Kafka/图片/云存储等副作用只对明确的测试目标实施。

上述运行验收属于原生 worker、接口与浏览器实现阶段。此清单提供完整发现和配置来源证据，不宣布海康全部插件或原业务已等价完成。
