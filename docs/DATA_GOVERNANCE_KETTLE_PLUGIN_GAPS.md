# 原生 Kettle 插件差距审计

本报告对照附件 `kettle6.1.3.0.zip` 的 335 个静态实现目录项，与本轮只读 `/capabilities` 快照；不修改 worker、不提取安装插件、不执行原业务任务。比较键为插件类型、ID/别名和登记类，避免同 ID 不同实现混计。归档路径只按 `!/` 前的容器定位，不能把 Java 包名中的 `/plugins/` 误认为附件插件目录。

## 当前边界

| 口径 | 步骤 | 作业 | 合计 |
| --- | ---: | ---: | ---: |
| 静态实现目录 | 249 | 86 | 335 |
| 当前 worker 已登记 | 189 | 67 | 256 |
| 已登记且可构造 | 187 | 67 | 254 |
| 未登记、实现位于普通 `plugins/` 容器 | 49 | 10 | 59 |
| 未登记、实现位于 Karaf/OSGi 容器 | 11 | 9 | 20 |

未登记共 79 项，不能统一解释为“缺依赖”，也不能统一通过解压普通插件目录解决。67 个作业 Meta 可加载，但当前执行器只支持 SPECIAL、TRANS、FTP_PUT 三类；其余 64 项是执行适配边界，不是缺少插件类。

当前已登记但不能构造的两项，是 `com.ruckuswireless.pentaho.mongodb...MongoDbInputMeta/MongoDbOutputMeta`，探测错误均为 `InvocationTargetException`。它们已经存在于顶层 fat JAR，和“未装外置插件”不同。

原业务实际使用的 **12 类步骤全部已 loadable**，且当前类来源均为 `lib/kettle-6.1.0.7.36.jar`：

```text
Constant, DBLookup, Dummy, FilterRows, JsonInput, KafkaConsumer,
KafkaProducer, ScriptValueMod, SelectValues, SwitchCase,
TextFileOutput, WriteToLog
```

其中 `ScriptValueMod,ScriptValue` 是同一原生别名组。可加载只证明当前类构造边界，不替代原 18/24 步整图、数据语义和外部交付验收。

## 四个 HBP/人脸 Kafka 插件

下面路径均相对于附件中的 `kettle工具/data-integration-hikvision/`；应保留完整目录和相对库路径，不能只复制入口 JAR。

| 原插件 ID | 精确提取目录 | bundle 入口 | 顶层 lib 是否已有登记 Meta 类 |
| --- | --- | --- | --- |
| HBPKafkaConsumer | `plugins/steps/hbp-kafka-consumer/` | `plugin.xml` → `pentaho-kafka-consumer.jar` → `com.ruckuswireless.pentaho.kafka.consumer.HBPKafkaConsumerMeta` | 没有；类只在独立 consumer bundle 中 |
| HBPFaceKafkaConsumer | `plugins/steps/hbp-face-kafka-consumer/` | `plugin.xml` → `pentaho-kafka-consumer.jar` → `com.ruckuswireless.pentaho.kafka.consumer.HBPFaceKafkaConsumerMeta` | 没有；类只在独立 consumer bundle 中 |
| HBPKafkaProducer | `plugins/steps/hbp-kafka-producer/` | `plugin.xml` → `pentaho-kafka-producer.jar` → `com.ruckuswireless.pentaho.kafka.producer.HBPKafkaProducerMeta` | 有，但没有核心步骤描述登记；外置版本字节不同 |
| HBPFaceKafkaProducer | `plugins/steps/hbp-face-kafka-producer/` | `plugin.xml` → `pentaho-kafka-producer.jar` → `com.ruckuswireless.pentaho.kafka.producer.HBPFaceKafkaProducerMeta` | 有，但没有核心步骤描述登记；外置版本字节不同 |

四个 ID 当前都未出现在 worker 步骤目录。两个 consumer 目录中的入口 JAR 完全相同，SHA-256 前缀 `4918ea8515271e15`；两个 HBP producer 目录的入口 JAR 同为 `9a63b114d4df8a47`。每个入口包还包含同族其他类，但 `plugin.xml` 只登记其指定的一个 ID，不能按“JAR 中有两个 Meta”就自动扩大登记数。

另有 `plugins/steps/pentaho-kafka-producer/pentaho-kafka-producer.jar`，哈希前缀 `085dbd97fefad21a`，描述登记的是现有 `KafkaProducer`。它不是补齐四个 HBP 插件的必要入口，不应为了增加 HBP 顺手覆盖已验证的普通 KafkaProducer。

### bundle 自身存在声明缺项

四个 descriptor 均声明 21 个 library，实际文件数与声明不完全一致：

| 目录后缀 | 实际 JAR 数 | descriptor 指向但本目录不存在的文件 |
| --- | ---: | --- |
| `hbp-kafka-consumer` | 19 | `lib/log4j-1.2.16.jar`、`lib/xercesImpl-2.9.1.jar`、`lib/xml-apis-1.3.04.jar` |
| `hbp-face-kafka-consumer` | 18 | 上述三项，另缺 `lib/hbp-jobscheduler-api-1.2.5.jar` |
| `hbp-kafka-producer` | 19 | `lib/log4j-1.2.16.jar`、`lib/xercesImpl-2.9.1.jar`、`lib/xml-apis-1.3.04.jar` |
| `hbp-face-kafka-producer` | 15 | 上述三项，另缺 `lib/slf4j-api-1.7.2.jar`、`lib/slf4j-log4j12-1.6.1.jar`、`lib/hbp-jobscheduler-api-1.2.5.jar` |

声明缺项不等于原 Spoon 必定失败：原父加载器还提供顶层库，部分依赖可能回退到父加载器。例如 `hbp-common-1.2.5.jar` 与顶层版本字节相同；非 face 目录中的 `hbp-jobscheduler-api-1.2.5.jar` 也和顶层相同。当前顶层还存在 Xerces 和 Log4j 的对应/其他版本。应记录实际类来源，不能把缺项直接删掉或任意下载新版本“补齐”。

### 不宜平铺的具体冲突

| HBP bundle 内 | 顶层 lib 内 | 风险边界 |
| --- | --- | --- |
| Kafka `0.8.2.1`（Scala 与 clients 两个 JAR） | Kafka `0.10.0.1` | 同包不同版本，须保留各自调用及消息语义；不能未经验证互换 |
| Scala `2.10.4` | Scala `2.10.5` | 即使补丁版本可能兼容，也不应靠目录排序决定实际实现 |
| Fastjson `1.2.7.hik.1` | Fastjson `1.2.9` | 私有后缀意味着需检查具体字节/行为，不能按版本号猜等价 |
| Netty `3.7.0.Final` | Netty `3.6.2.Final` | 同名类来自不同网络栈版本 |
| zkclient `0.3` | zkclient `0.7` | Kafka 高级消费者相关依赖不同 |
| SLF4J `1.7.2`、binding `1.6.1` | 多份 API `1.7.5/1.7.7` 与 binding `1.7.5/1.7.7/1.7.9` | 可能出现绑定/加载来源混用，需明确共享日志边界 |

这些是已确认的版本/字节冲突线索，不等于本轮已经执行并复现 ABI 错误。直接按 basename 平铺还会让多个不同 `pentaho-kafka-producer.jar` 互相覆盖；即便重命名保留，平面 classpath 也只能按先后顺序选择重复 FQCN。

## 其他海康/捆绑扩展

当前 **已登记且 loadable**：HbaseInput、海康 HBaseOutput、ImpalaBatchOut、ElasticSearch4HumanInfo、JudgeTableCount、MyPlugin、CloudStore、KafkaConsumer/Producer、KafkaConsumerByBroker、KafkaConsumer4Huawei、NewKafkaProducer、RabbitmqConsumer/Producer、ActivemqConsumer/Producer。它们的 Meta 已在 fat JAR 内，不能把这些工具再算进“等插件目录加载”的缺口。

`HBPCustomKafkaProducerMeta` 虽有真实类，但静态扫描未找到原生 descriptor/annotation 登记；它是未登记类候选，不应擅自当成一个遗漏的正式工具。

### MongoDB 的精确区别

当前两个失败的私有 Mongo Meta 构造函数均调用公共父类 `com.ruckuswireless.pentaho.mongodb.di.trans.steps.mongodb.MongoDbMeta`。该父类构造函数必须访问 `org.pentaho.mongo.NamedReadPreference.PRIMARY.getName()`。本轮静态确认顶层 worker lib 中没有 `org/pentaho/mongo/NamedReadPreference.class`，也没有 `com/mongodb/MongoClient.class`。

附件中精确的补充来源是：

```text
system/karaf/system/pentaho/pentaho-mongo-utils/6.1.0.1-196/
  pentaho-mongo-utils-6.1.0.1-196.jar
system/karaf/system/org/mongodb/mongo-java-driver/2.13.0/
  mongo-java-driver-2.13.0.jar
```

这是具体缺少的必需构造/驱动依赖证据；当前 runtime 只返回外层 `InvocationTargetException`，仍应在隔离 metadata probe 中解包确认完整 cause 链。另一个 `plugins/pentaho-mongodb-delete-plugin-master/lib/mongo-java-driver-2.13.2.jar` 是不同版本，不能无说明替代 2.13.0。

私有 Meta 当前由父加载器的 fat JAR 加载，仅把 mongo-utils 放到一个新子加载器，不能倒向满足父类解析依赖。可评估把上述匹配依赖作为明确、单独校验的父层兼容扩展，或让私有 Mongo 类和其依赖一并进入受控插件加载器并保留共享 PDI API 的父加载边界。不要将同 ID 的官方 OSGi MongoDbInput/Output 类静默替换进来。

## 普通插件目录和 OSGi 分开

59 个普通插件实现项分布于以下目录（步骤/作业数量按实现去重；HBP 四目录的类重复不重复计入总数）：

| `plugins/` 下目录 | 步骤/作业 |
| --- | --- |
| `kettle-xml-plugin/` | 7 / 4 |
| `kettle-palo-plugin/` | 4 / 2 |
| `kettle-hl7-plugin/` | 1 / 2 |
| `kettle-dummy-plugin/` | 1 / 1 |
| `ms-access-bulk-loader-plugin/` | 0 / 1 |
| `pentaho-gis-plugins/` | 7 / 0 |
| `pdi-salesforce-plugin/` | 5 / 0 |
| `kettle-openerp-plugin/`、`pentaho-cassandra-plugin/`、`platform-utils-plugin/` | 各 3 / 0 |
| `kettle-drools5-plugin/` | 2 / 0 |
| 四个 `steps/hbp-*/` 目录 | 共 4 / 0 |
| `VerticaBulkLoader/`、`gp-bulk-loader-plugin/`、`kettle-gpload-plugin/`、`kettle-s3csvinput-plugin/`、`kettle-shapefilereader-plugin/`、`lucid-db-streaming-loader-plugin/`、`pdi-google-analytics-plugin-ce/`、`pentaho-mongodb-delete-plugin-master/`、`teradata-tpt-bulk-loader/` | 各 1 / 0 |

除带 plugin.xml 的目录外，入口通常是目录内同名版本 JAR 的 `@Step/@JobEntry` 注解；GPLoad 入口是 `kettle-gpload-plugin-V003R002C001B0028.jar`，GIS 是 `pentaho-gis-plugins-1.2.1.jar`，Mongo delete 是 `pentaho-mongodb-delete-plugin-1.0.1-RELEASE.jar`。完整精确入口路径已保存在审计 JSON。

另外 20 项来自 `system/karaf/system/` 的 bundle，而不是普通插件目录，涵盖 DataRefinery/发布模型、Hadoop/MapReduce/Pig、Oozie、Sqoop、官方 HBase 与官方 MongoDB。`system/karaf/caches/spoon/data-1/cache/` 和 `data-2/cache/` 是缓存副本，不能当作独立插件或可靠发布入口。它们还涉及 OSGi imports、服务和 Hadoop shims，不能通过复制缓存 bundle.jar 到顶层 lib 宣布已支持。

## 原加载机制与建议

原附件类字节码提供以下直接证据：

1. `StepPluginType` 构造调用 `populateFolders("steps")`；`PluginFolder.populateFolders` 读取 `KETTLE_PLUGIN_BASE_FOLDERS`，按逗号拆分。每个 base 建立 annotation 扫描目录，并建立 `base/steps` 的 XML 插件扫描目录。因此应配置私有 `.../plugins` 为 base，保留 `plugins/steps/<bundle>/plugin.xml` 的层级。
2. `BasePluginType.searchPlugins` 顺序调用 `registerNatives → registerPluginJars → registerXmlPlugins`。XML 登记读取 `<libraries><library name=...>`，将相对路径解析到插件目录。
3. `PluginRegistry.createClassLoader` 根据 `PluginInterface.getLibraries()` 创建 URL 列表；`getClassLoader/loadClass` 有单独 loader、插件目录缓存和 classLoaderGroup 缓存。native 插件分支直接使用 `Class.forName`。
4. `KettleURLClassLoader.loadClass` 明确先 `loadClassFromThisLoader`，失败后 `loadClassFromParent`，资源也优先自身。`KettleSelectiveParentFirstClassLoader` 对匹配配置模式的类优先父加载器，其余仍走前者。不能用普通父优先 URLClassLoader 自称完全复刻原机制。
5. 当前 worker `prepare` 只提取顶层 `data-integration-hikvision/lib/*.jar`，优先 fat JAR；启动把 `KETTLE_PLUGIN_BASE_FOLDERS` 指向私有空目录。Java init 手工登记核心描述，并以 `native=true`、空 libraries、空 pluginDirectory 注册；能力构造也使用 `Class.forName`。因此单独改环境变量或解压文件仍不足以启用独立插件。

建议下一步先只允许上述四个 HBP XML bundle，保留原目录、descriptor、原库文件及哈希，并用原 PluginRegistry 的非 native 插件登记/加载方式建立隔离 loader。能力探测、原 getXML/loadXML 和执行类获取都须通过该 registry，记录实际 loader、入口/依赖来源与哈希。共享 PDI API/日志边界和缺失声明项的父层回退须明确列出，不把全部 JAR 平铺。

确认 metadata 构造、配置往返和原生行类型之后，再针对 HBP/人脸协议、Kafka 键值、位点、空主题停止、失败分支及资源关闭做合成数据执行验收。不要为补外置工具改变已经验证的原业务 12 类实现来源，也不要同时拉起整个 Spoon/Karaf 启动链。

## 证据与复现范围

生成证据位于独立运行目录 `rynew-runtime/data-governance-kettle-v2/plugin-gap-audit/`：

- `capabilities-slim.json`：当前 runtime ID、登记类、来源、loadable/执行支持，不含配置值。
- `catalog-comparison.json`、`gap-locations.json`：按 ID/别名/实现及真实归档容器定位的差异。
- `PluginFolder/BasePluginType/StepPluginType/PluginRegistry/KettleURLClassLoader/KettleSelectiveParentFirstClassLoader.javap.txt`：原附件类的只读反汇编。

本轮读取原 ZIP、静态 JSON、所选原类字节码和已运行 broker 的只读能力接口；没有安装新依赖、修改 worker、加载四个 HBP bundle、运行原 18/24 步业务或访问生产地址。建议部分是后续受控实现方案，不能当成本轮加载/执行成功证据。
