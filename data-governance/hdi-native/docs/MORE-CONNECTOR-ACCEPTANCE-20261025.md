# MySQL、RabbitMQ、SFTP 接入与验收

验证日期：2026-10-25；整合复核：2026-10-26。本轮在本机平台、Chrome、专属测试数据库/消息队列/SFTP以及本机Linux x86_64实验环境验证，未连接10/250、未部署生产。跨架构QEMU解释执行的耗时不能作为生产性能结论。

## 复用范围与来源

原DFP画布、节点表单、参数定义、保存投影、配置生成器和Core继续复用；没有新写数据库读写、消息协议或文件传输算法。原包`detl_3.3.0.20260310110148.zip`的SHA-256为`3bd2ef065255e9e19119b1e2ade294b32b59d47c051a0a10981496766f052859`。

- RabbitMQ使用原`rabbitmqInput/rabbitmqOutput`，平台目录ID9/10。
- SFTP使用原`fileInput/fileOutput`及原FTP数据源的`ext2=sftp`；不造一个新的数据源类型或传输实现。
- 原包缺少可直接使用的MySQL专用输入/输出插件。用户明确批准最小适配后，新增`MysqlInput/MysqlOutput`两个子类（平台ID11/12），各自只覆盖驱动名、JDBC URL前缀和标识符引号；其余读写、批次、SQL构造和错误处理继承原`JdbcInput/JdbcOutput`。原基类不能直接当通用插件使用，驱动/URL方法原本返回空。页面明确显示“原JDBC适配”。
- MySQL参数取原包公共JDBC定义，并保留逐项来源记录；平台适配manifest独立于原包，不能把它称为原厂二进制。

最终原程序包`more-connectors-ui-final-20261025`包含2341个文件、557371443字节、10个原插件；包括编译所需JAR和内置协调器，不包含原Agent或现场配置。manifest摘要为`9f5cd5b9a571b796af844e0fe583ffae0f9deb37f1a8ed9e7bf6ef993a0113fa`。相比前一原包，2175个文件逐字节不变，仅补166个原公开SVG图标。

独立MySQL平台包manifest摘要为`80474246261101c788786f0b550882c84eadc8bc0317b30d23f96780eea24ad2`，适配JAR摘要为`d0e767035ad76e5200d59d0a526d91d64d6143a7c640e1b066b3121f84a64e6e`。Catalog、编译CLI、worker配置和MySQL任务快照均固定同一manifest摘要；换包不能悄悄改变已发布任务。

## 原浏览器配置

三个连接均经原“新增数据源”表单完成配置、原测试按钮、保存、卡片状态更新及修改表单回读：MySQL连接4、RabbitMQ连接5、SFTP连接6，均revision1。公开密码均为`******`；编辑回读保留原URL、SSL或SFTP选项。没有通过API写连接或任务图；API仅用于读回核对。

MySQL使用原URL模式和显式`useSSL=false&allowPublicKeyRetrieval=true`。原host模式对全新`caching_sha2_password`账号首次认证失败已保留；另一个全新账号使用上述原驱动选项首次即成功，没有预热认证缓存、更换驱动或修改MySQL全局设置。

RabbitMQ原连接SDK测试默认`/` vhost；业务vhost、队列、exchange/routing key由原节点属性配置。连接测试成功不冒充已验证业务vhost。SFTP在原连接页仍属于FTP类型，URL仍显示原`ftp://`前缀，协议实际由ext2选择，真实传输经SSH/SFTP验证。

## 浏览器运行记录

所有任务均由原任务列表新增，实际拖入节点、鼠标连线、原字段及属性控件保存。各方向的试运行、一次性运行及真实数据回读记录如下；RabbitMQ输入正式运行仍待补验。以下记录与CLI独立原Core探针分开。

| 链路 | 任务 | 试运行 | 一次性运行 | 数据证据 |
| --- | --- | --- | --- | --- |
| MySQL输入→本地CSV | 8，原生MySQL输入验收 | r14 `71042c12-0783-45c0-8439-edab7dc06b05` | r15 `b55cc7f5-3ef7-4f4d-9049-6e492f2b47e3` | 各从浏览器下载59字节；message列及原表3条值 |
| 数据生成→MySQL输出 | 11，原生MySQL输出验收 | r11 `3c810865-d853-4030-a912-76a551b1c61e` | r12 `96f4fee8-691a-4d17-87f1-79980ed1cbac` | 原MariaDB驱动独立SELECT：新值0→3→6，既有3条保留，最终9条 |
| SFTP输入→本地CSV | 9，原生SFTP输入验收 | r23 `6991f418-1455-49d1-9533-4a6fe3b6b45e` | r24 `0e5f0e85-a2ef-454a-852b-61ac02a5aabc` | 各实际浏览器下载73字节、id/message两列3条 |
| SFTP输入→SFTP输出 | 9，原节点修改输出位置 | r30 `1b3dd47b-82e7-4152-b375-d06cb14cfb03` | r31 `f4b14c1f-8e59-4759-8b57-b573d157227e` | 原JSch远端GET各73字节、两列3条；两个新文件、原文件和源文件保留 |
| 数据生成→RabbitMQ输出 | 12，原生RabbitMQ输出验收 | r15 `d30b9b4f-c63a-4ccc-a266-10abf943bdd2` | r16 `e508ed8f-ad55-440d-b787-2685ead52970` | 两个独立队列各0→3；原AMQP客户端分别读取3条仅含message的业务JSON后统一nack/requeue，两批消息均保留 |
| RabbitMQ输入→本地CSV | 10，原生RabbitMQ输入验收 | r19 `f9d0de16-2b41-4d59-93c4-2de49fec141c` | 尚未完成验收 | 原UI正常暂停→停止，CANCELLED/PARTIAL；真实浏览器下载62字节、message列3条 |

MySQL、SFTP与RabbitMQ输出共10次均`SUCCEEDED/native finish/finalized=true`，文件、目标行数或队列消息均实际读回。RabbitMQ输入试运行另有1次显式停止的有效正例，终态为`CANCELLED/native stop/finalized=true/PARTIAL`，不计为SUCCEEDED。因此目前是11个浏览器正例，RabbitMQ输入正式一次性运行仍待补验。MySQL输入下载SHA-256为`9223369bc68aa7dca3306ba87ae8af8ac76cab80aee2c97b7f73e913ba676b2a`；SFTP四次文件均为`1783a4ba079e060bd275251d2d60b23a3d890ef7c86a6b168ae861f092248c6f`。RabbitMQ输入下载为`a0813bb649e7308a90f113e15ebbcf1f7aaa73e9d73ddaf852b8e183b5dfbce7`，实际值为original rabbit 1/2/3。

## 持续输入与原语义

RabbitMQ原输入读取后即确认消息，空队列后仍监听；没有已确认的总采样数量/时长参数。原空队列等待值仅控制一次poll；原厂debug调度还会跳过OUTPUT，不能把它当作实际落地证明。平台测试继续明确提示真实读写及消息消费副作用。

持续输入通过“启动→真实消费和落地→原停止→下载已生成部分结果”验收。只有显式用户停止、本exec原状态stop、清理确认、无错误/恢复标记时才提供PARTIAL产物；状态保留CANCELLED，页面显示已停止和部分结果提示，不伪SUCCEEDED。超时、强关、后台退出和失败残留不开放下载。每份归档仍验证完整字节数和SHA，partial指数据处理范围，不把预览伪装成完整结果。

原File同步响应字段使用原“是否忽略”排除物理CSV列；原RabbitMQ输出的ETL元数据通过原扩展字段开关控制，未手工删除原生成器输出。试运行可能真实写数据库、上传文件、发布或确认消息，使用专属目标与队列。

## 本轮修复与回归

RabbitMQ输入最初浏览器试运行r17 `e98b300a-72f9-4538-ba13-316e69261767`超时失败，记录与诊断完整保留，不计正例。原置顶提交提示遮挡暂停按钮，正常点击等待后超过实验worker执行预算。该实验配置启动150秒、执行120秒，不能与代码默认执行300秒混同。随后仅在原iframe中使用原HUI高度变量下移提示；新专属队列重试r19确认提示与暂停按钮不重叠，并经普通点击完成原停止、部分结果警示和真实下载，无force、无预先关闭提示。

- 原Rabbit输出无实体目标表时，原服务返回非null的空DstTableVo并继续读取字段。修复平台DAO适配漏项，沿原服务生成目标字段结构，不重写输出算法。
- 原UI控制开关来自`etl_adapter_ext`。MySQL/Rabbit缺失或空插件声明时读取原V1.0.63迁移；Rabbit数据源字典来自原V1.0.22且sort原为NULL，未凭空造类型值或表名。
- 多窗口配置曾出现一次编译器繁忙503。保留已存版本和草稿后，增加最多4个待处理请求/10秒等待，仍最多2个子进程、不自动重试。SFTP三次忽略开关及Rabbit属性重新通过原UI保存、HTTP200并权威回读，未用API补图。
- 快速添加原图标`fieldmapping.svg`曾漏包422。新包补原静态目录；Chrome原iframe内请求200、1447字节、52×52，SHA `b3b6adfcab9b104eb1f825ce9caf286d9c1450491c266fea7cba26a8004cb5b1`与原ZIP一致。顶层直接访问403属于原资源上下文保护，不是漏包。
- 部分原HUI复选框仍有Chrome的aria-hidden焦点警告；这是保留原控件的无障碍边界，不声称原资源全量兼容性验证通过。未使用隐藏控件force点击或API造图绕过验收。

验证源码固定`4d0b3f7`，独立验证树保持干净。Java共179项：初次178通过，1项误用了仍含遗留Ftp高参的历史browser-final快照；原样改用此前真实browser-ready正例单项复测通过，历史文件和初次失败报告保留。没有删参数或修改测试输入来伪造浏览器配置。

原SDK编译50项、MySQL适配5项、元数据6项、执行包保护6项全部通过且无跳过。macOS执行器/安装/打包共105项，最终102项通过、3项Linux专属跳过；其中初次3个Unix socket路径过长错误在专属短私有临时目录原样补测通过，未放宽100字节保护。另有原source-e `d880ab8`的Linux102项全过证据，单独记录其版本，不冒称这就是全部最终包的Linux回归。

前端完整`verify:frontend`为324项通过、0跳过，并通过UI Guard、生产构建、168个JS产物兼容检查。真实源码/网络/运行/浏览器证据分别保留，不互相替代。

## 部署与实际边界

复用平台MySQL、Redis及现有用户/权限/密钥；原Core使用Linux x86_64 Java11，平台、编译器和元数据工具使用Java17。任务核心不依赖10、原HDI管理/Agent/授权服务或Kettle/NiFi。实验SSH/QEMU、专属RabbitMQ和SFTP服务仅用于本轮数据源验收，不是新增平台控制服务依赖。

本轮复用既有十张任务/连接表，没有新增DDL。部署参数和两个历史基础SQL见 [README](../README.md)、[安装准备](../core-runtime/SETUP.md)及[连接说明](../core-runtime/CONNECTIONS.md)。应从干净提交准备配套程序与原包，显式配置批准端点及同一个MySQL适配包摘要。新版本在部署机上的实际安装、业务数据和性能仍需验收，本机测试不能冒充服务器发布。

当前验证为MySQL选表读取/INSERT、RabbitMQ无TLS AMQP/JSON、密码SFTP UTF-8 CSV。原SFTP插件不校验主机密钥；Java11环境使用专属SSH服务提供的ECDSA密钥完成验收，没有启用旧SHA1或修改系统SSH服务。严格ed25519-only服务、私钥认证、其他数据库写入模式、MQTT/TLS、CDC、分支等不在本轮证明范围。原JDBC的finish也不能替代实际写入回读，仍保留已观测输出异常提示。

## 证据索引

根目录为Git外的`rynew-runtime/`，凭据不进入本文件或Git：

- `research/20261025-hdi-more-connections-browser/connections-and-icons-final.json`：三个原连接及图标浏览器验证。
- `research/20261025-hdi-mysql-browser/mysql-browser-acceptance.json`：MySQL四次完整浏览器及SQL回读。
- `research/20261025-hdi-sftp-browser/browser-acceptance-summary.json`：SFTP四次下载/GET及文件保留。
- `research/20261025-hdi-rabbit-output-browser/rabbit-output-browser-acceptance.json`、`research/20261025-hdi-rabbit-browser/`：Rabbit输出完整回读及输入原UI配置。
- `research/20261025-hdi-rabbit-input-browser/debug-retry-download-proof.json`：Rabbit输入显式停止、部分结果和真实下载；同目录保留初次超时失败及原停止证据。
- `research/20261025-hdi-more-final-verify/final-verification.json`：精确验证命令、版本、条件、初次诊断与补测结果。
- `hdi-native/linux-x86-lab/more-connectors-20261025/acceptance-summary.json`：六路独立原Core探针与实际数据；不能代替浏览器验收。
- `research/20261025-hdi-more-connectors-compiler/`：MySQL缺原子插件/三方法适配、首次冷认证、原Rabbit生命周期与字段服务证据。

2026-10-26整合时机器已重启，原backend、Vite、QEMU和测试数据库均未运行，未重启实验服务；最后正式RUN尚无完成证据。调度禁用状态、无活动/未知运行需在恢复环境后重新在线核查，不能依据停机状态认定已经通过。源数据、旧任务和历史失败不删除。回退先禁用计划并确认停机，再停本模块worker、回退平台/程序配置；保留任务、连接、密文版本、完整与部分归档，不DROP表，不自动向旧引擎重放。
