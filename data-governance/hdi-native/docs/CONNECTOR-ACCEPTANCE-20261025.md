# PostgreSQL、FTP、Kafka 原插件验收

日期：2026-10-25，Asia/Shanghai。环境：本机 RYNEW、Chrome、隔离测试数据库/FTP/Kafka，以及本机 Linux x86_64 虚拟机中的原 HDI Core。未访问 10 / 250，未发布生产；跨架构解释执行的耗时不作为生产性能结论。

保留原 DFP 画布、节点表单、参数定义、保存投影、配置生成器、插件与执行 Core。原 JAR 未修改，来源为 `detl_3.3.0.20260310110148.zip`，SHA-256 `3bd2ef065255e9e19119b1e2ade294b32b59d47c051a0a10981496766f052859`。管理、权限、加密连接、版本、计划、运行记录和文件归档由平台承担，不启动原 HDI 管理/Agent/授权服务。

完整连接器/UI bundle为2147个受校验文件、548235632字节；manifest摘要为 `e853e095647485679cb1239ed78fc1f93b87cf0fe05c35efc503015dface1329`。原连接表单中文词典与数据源图标也来自同一原包。

## 原浏览器配置与运行

三条连接均通过原数据源表单新增、连接测试和保存：PostgreSQL连接1、FTP连接2、Kafka连接3。下表任务均从原任务列表建立，鼠标拖入节点、连线、原表单配置并保存；没有用API生成图或替代点击运行。API与目标端查询只作结果核对。

| 链路 | 任务 | 试运行 | 正式一次性运行 | 独立业务结果 |
| --- | --- | --- | --- | --- |
| PostgreSQL输入→本地CSV | 3，原生数据库输入验收 | r12 `29a67d6f-d29d-497d-adbe-d4003fe2a8ba` | r14 `0dc4c95f-7177-4f2e-a0c5-392e6341b15f` | 每次页面下载76字节；message及3条真实源表值 |
| FTP输入→本地CSV | 4，原生FTP输入验收 | r20 `d635701b-ddba-4902-9bbb-703660761eb0` | r21 `b3d9b249-34e9-49a6-ac56-09101aa507b4` | 每次下载120字节；id/message 3条，保留原默认3个响应列 |
| 数据生成→PostgreSQL输出 | 5，原生数据库输出验收 | r11 `0eb73edd-a9c9-4003-adc8-6b951b08b5b1` | r12 `49c04aa8-9002-4b01-bd71-35befb34048b` | 独立SQL按新值计数0→3→6，未清空已有数据 |
| FTP输入→FTP输出 | 4，原节点切换输出配置 | r30 `6d09756a-2e40-42fe-8951-46a8c471e1c1` | r31 `a00bed2d-ac14-4d25-a168-825117fcf144` | 原字段页忽略3个响应字段；两次独立RETR均70字节、精确id/message两列3条，旧文件保留 |
| Kafka输入→本地CSV | 6，原生Kafka输入验收 | r17 `d1804db4-e023-4ea1-9abe-7f90dffe19b9` | r18 `907198e1-b1bf-4b76-8edb-f9c34cfa3f24` | 各下载59字节，message及3条原消息；两个原UI消费组，无offset重置 |
| 数据生成→Kafka输出 | 7，原生Kafka输出验收 | r11 `fddd67cf-9c0a-4f0e-8a88-99f6de16b45a` | r12 `f4303036-b8c4-4115-b87c-d3060d283017` | 独立原客户端读取新增offset `[3,6)`、`[6,9)`，各3条JSON；历史消息保留 |

以上12次正向运行均为原 `finish`、`SUCCEEDED`、`finalized=true`。页面自动更新状态，源字段/输出字段与本次冻结版本对应；没有把文件出现或任务已受理当作执行成功。

文件摘要：

- PostgreSQL输入CSV：`05a3ce46a6ead9fa93735431e2f062d96129b6f50b35779b5729ff418df4cd5e`。
- FTP输入默认同步CSV：`b1c6cc69e70e7bf4db5e25aa0ba5c7f3149331bf338a0fa02a7dba5ace1bb0bc`。
- FTP输出两列CSV：`2c4bcc47d04a4d7ac7e52b6c852ab4b26c14bf1dd071e603586d2e22f5c5556e`。
- Kafka输入CSV：`933914358b2adac67203e68ed97441d208b8c7eec3611315b8788f70295525cd`。

## 失败与原实现的边界

1. **原JDBC全行失败仍可能finish。** task5对独立CHECK拒绝表写入时，开启原默认任务重试的r16/run `9ea395ff-f238-4f3f-9f70-c93804079d06` 持续重试，最后平台限时停止，状态FAILED且资源清理确认。通过原属性/运行配置关闭重试后的r18/run `da2db703-be10-4950-bee0-a0f4c48de2fe` 自然finish，但目标仍0行，平台显示“完成 · 待核对”及数据库输出错误告警。正常目标的6条记录保留。平台没有修改原算法或伪造原失败终态。
2. **告警不是完整行数监控。** 只报告本次原日志中匹配到的固定JDBC输出错误/跳过事件，忽略冷启动历史文件提示、其他流程和其他logger。未观测输入/输出计数保持null；无告警不能代替目标回读。
3. **试运行有实际副作用。** 它按节点执行真实输出。Kafka `autoCommit=false` 时原插件仍可能 `commitSync`；两次输入验收使用不同测试消费组，没有清主题或重置offset。本轮是独立批次，不宣称已迁移常驻流消费或端到端恰好一次。
4. **同步文件响应列保留原语义。** 原UI可能加入 `_RESULT_CODE_/_RESULT_MSG_/_RESULT_DATA_`。不自动删除它们；使用原“是否忽略”/`passThrough=1` 后，物理文件可仅保留业务列，原同步响应仍由原插件生成。
5. **失败不自动重发。** 已结束失败可通过原“暂停任务→停止”确认后恢复编辑，失败run、错误、版本保持不变；UNKNOWN和并发新run不能被该操作清除。修复前的编译失败记录也保留。

负例后，task5已通过原UI恢复正常演示并仅保存到当前/发布revision22：正常目标表、3条原测试值、重试关闭。没有执行新任务，历史run仍4条，正常目标6条、拒绝表0条未变。所有本轮手动计划均enabled=0、next_fire_at=NULL，未留下定时自动写入。

## 源码与验证

- 平台后端：连接owner/CAS/冻结版本、原RSA传输与平台AES-GCM存储、可信端点绑定、任务/运行/结果接口；独立SQL新增3张连接表，已在任务所有权核对、备份后执行两遍。
- 原SDK桥：调用原输入/输出/字段/参数保存服务，保留 `srcTableId`、原字段表达式、清理码`0`、原`INSERT`、扩展列投影、可空adapterData和同步响应语义。
- 前端：外层复用Element Plus，原Vue2/HUI仅在同源iframe；原数据源组件与共享依赖装配，NodeVo.dbType字符串适配、原公钥初始化、原字典与图标资源、并发URL预览和任务状态同步。
- 已核验的检查：最新根树后端145项（含真实SDK/连接/表元数据/元数据网络网关）0失败0跳过；前端完整verify 324项0失败0跳过、UI Guard、生产构建及168个JS产物兼容检查；原编译器40项、Linux worker63项。安装准备27项通过，包含Core11/Compiler17分离及receipt版本3，不将这些单测当作新版本在Linux部署机的安装验收。

原UI/原SDK代码不是全量浏览器兼容性承诺；168个产物检查针对平台构建，原资源实际用本次Chrome验收。所有抓图、测试输出、日志、原包与凭据均在Git外。

表/主题标识另经H2与真实MySQL `utf8mb4_0900_ai_ci`审计：原始名称字节生成的现有SHA-256 object_key正确区分表、schema、Kafka主题大小写，8种身份对应8个稳定平台ID。无需更改应用库DDL或假造原管理数据库ID。

Kafka元数据读另补了实际网络边界：主进程不再直接创建AdminClient，测试连接/读取主题进入一次性原SDK子JVM，在connect之前核对精确端点。双broker对照允许目标收到1个连接/102字节；未批准的advertised目标收到0个新连接/0字节。4项原SDK专项和网关回归通过，无法安装守卫时失败，不降级为主进程直连。实际验收使用JDK17；Java11只完成最小守卫合同，不宣称QEMU完整元数据测试通过。详见 [METADATA-NETWORK.md](../core-runtime/METADATA-NETWORK.md)。

最终后端重建后，通过原Kafka连接卡片再次测试成功，task6原“修改表”读取3条实际主题，HTTP200/code0；取消选择，没有改消费组或执行新任务。原PostgreSQL、FTP连接卡片也重新测试成功。最终浏览器控制台无error/warning。

截图复核修正了原连接页嵌入平台后的窄窗挤压：原页面固定四列卡片和绝对定位筛选栏在较窄iframe内会重叠；只在宿主HTML、原组件样式作用域内按窗口换行，没有修改原bundle、表单或节点逻辑。Chrome窗口1024/1200/1440/1920宽度分别检查，卡片及工具栏不重叠、无文档横向溢出；明暗平台外壳均检查，原iframe仍保留原浅色主题。此次显示修正后重新执行完整前端verify，324项、UI Guard、构建和168个JS兼容检查均通过。没有新增组件、图标库或UI Guard例外。

## 证据位置

持久证据根目录：`rynew-runtime/research/`。

- `20261025-hdi-pg-browser/browser-authoring-proof.json`
- `20261025-hdi-pg-output-browser/pg-output-acceptance.json`
- `20261025-hdi-ftp-browser/browser-acceptance-summary.json`
- `20261025-hdi-kafka-output-browser/kafka-input-browser-acceptance.json`
- `20261025-hdi-kafka-output-browser/kafka-output-browser-acceptance.json`
- `20261025-hdi-connectors/`：根树测试、配置/SQL验证和连接表单证据。
- `20261025-hdi-connectors/final-browser-regression.json`：最终连接测试、主题读取、不同宽度检查与截图索引。

独立worker六路原执行与目标回读另存于 `rynew-runtime/hdi-native/linux-x86-lab/connectors-20261025/acceptance-summary.json`，不能替代上表的浏览器证明。

## 部署与后续范围

按照 [README](../README.md)、[安装准备](../core-runtime/SETUP.md)、[连接配置](../core-runtime/CONNECTIONS.md)部署。平台、编译器及元数据JVM使用已有Java17；原Core独立使用Linux x86_64 Java11，单管理进程；复用平台MySQL/Redis、随模块内置协调器。开发用SSH与QEMU不是生产依赖。本次实验Core使用虚拟机Redis、平台使用主机隔离Redis，不能把实验拓扑说成已经共用同一Redis实例。

当前验收针对PostgreSQL表读取/INSERT、FTP被动传输与同步UTF-8 CSV/行文件、Kafka无认证JSON批次。其它数据库、RabbitMQ、SFTP、认证Kafka、Avro/BSON、FEL/脚本、分支、增量/CDC、常驻与依赖任务未开放。未验证的参数/节点不能宣称覆盖用户全部业务。

回退保留任务和连接历史：先禁用计划、确认运行终态并停本模块worker，再禁用新核心配置/回退平台制品。保留十张模块表、加密历史与产物，不DROP、不自动交给旧引擎重放。
