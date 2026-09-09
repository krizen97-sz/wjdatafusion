# 数据治理 v4.6.0：批次快照查表

本版提供可复用的PostgreSQL字典读取、多条件查表和固定批次调度。它不是原有海康任务的完整等价迁移。原附件所在KINGSTON盘在本轮未挂载，具体业务字段、URL改写、时间阈值及旧引擎输出仍待重新核对。

## 连接与快照

连接入口位于治理工作台“数据连接”。密码复用平台CredentialCryptoService的AES-GCM能力加密，列表不回显密码；编辑留空保留原密码。记录按用户隔离，使用私有目录、原子文件和版本校验。

服务端配置`data-governance.connection-allowed-endpoints`限定目标IP和端口，默认仅独立PostgreSQL的`127.0.0.1:15432`及`::1:15432`。不接受任意JDBC URL、驱动属性或浏览器SQL。远程目标必须校验证书；本机测试模式只允许明确的回环连接。

按schema、表/业务视图、字段和可选排序读取完整小字典。查询处于真实只读事务，连接测试检查PostgreSQL返回的`transaction_read_only=on`。最多1000行、64KiB，超限拒绝而不截断。标识符限制UTF-8 63字节；查询在服务端对超大行返回拒绝标记，避免先把大字段载入JVM再检查。

读取得到行数、原始JSON、读取时间、连接版本与SHA-256。加载到节点后保存；数据库后续变化不会自动更新该快照。发布版本冻结节点的字典和输入，因此计划重复使用已发布的数据。

## 通用查表节点

`com.hm.governance.nifi.JsonLookupSnapshot`随自有NAR 1.1.0提供，无网络或文件副作用，1.0.0组件仍保留供旧冻结版本使用。

| 属性 | 契约 |
| --- | --- |
| Lookup Rows | JSON对象数组；平台64KiB/1000行，独立NAR上限256KiB |
| Match Fields | 多条件，全部命中同一行；EQ读取输入JSON Pointer，IS_NOT_NULL只检查字典列 |
| Return Fields | 字典列映射到输出根字段；未命中KEEP使用default或null，字符串"0"保留类型 |
| Missing Match | KEEP / DROP / FAIL，默认KEEP |
| Multiple Matches | FAIL / FIRST / LAST，默认FAIL；首末按当前快照顺序，不替数据库自然顺序作保证 |

STRING为精确字符串比较；NUMBER使用BigDecimal比较，支持精确数字。输入对象仍输出对象，数组仍输出数组；全部删除或空批次走empty。规则或任一记录错误整批failure，不先输出部分success。输入和快照在处理前验证层级、总值数量、UTF-8和重复JSON键，输出受2MiB上限约束。

匹配/未命中/输入/输出记录数和快照摘要以固定`governance.lookup.*`属性返回，组件错误代码以`governance.error`返回。页面区分记录数与FlowFile数；样本与快照预览保留长整数和小数文本，不通过浏览器数值重序列化改变内容。

## 页面复用与验证

连接列表及弹窗复用ElementPlus表格/表单；字典读取用SnapshotPicker，查表配置继承NodeConfigPanel的表单与滚动容器。发布与计划复用Crontab、StepDetailDrawer和已有任务结果API，没有增加UI依赖或新的画布例外。

Cron生成器新增可选`preserveFieldValues`模式，仅数据治理启用。不同字段的自动归零被忽略，日/周互斥规则保留；其他调用者默认行为不变。

本地验收工具：

```sh
python3 tools/data-governance/prepare_lookup_fixture.py --runtime "$DG_RUNTIME"
python3 tools/data-governance/smoke_lookup_scheduler.py --runtime "$DG_RUNTIME" --app-runtime "$DG_APP_RUNTIME"
```

工具严格校验独立运行目录和PostgreSQL实际数据目录，只建立带所属标记的合成字典。真实Cron任务验证后暂停。发布/定时任务契约见[调度说明](DATA_GOVERNANCE_SCHEDULER.md)，完整设计器见[设计器说明](DATA_GOVERNANCE_DESIGNER.md)。

已完成真实数据库读取、快照加载、NiFi查表、发布版本、真实定时触发及Chrome完整操作。3条输入中1条命中、2条未命中（含非空条件过滤），返回值分别为EXT-001、字符串0、字符串0，快照摘要一致且资源清理确认。

本轮验证：前端完整校验174项测试通过，UI Guard零错误/零警告，沿用2项UIX-010流程图例外；治理后端53项测试通过（含真实NiFi和PostgreSQL），兼容组件40项测试通过。新增接口11项只读账号拒绝验证、匿名拒绝及owner隔离通过。最终PostgreSQL回归8项通过，包含内部标志同名业务列的排序、Unicode标识符、大字段和1001行拒绝。旧Writer 1.0.0流程在安装1.1.0后仍能运行，75条记录保持74+1分片，空批次及非法输入均确认清理。

Chrome已验证字典读取/加载/保存、发布V2、Cron生成器字段保持、启用/定时触发/暂停及结果详情；深浅主题在1728px桌面视口无页面横向溢出。验收任务最终全部暂停，运行详情明确显示资源清理确认。证据仅针对独立合成字典和本机运行环境，不替代原海康任务验收。

当前未完成：Kafka持续输入/位点迁移、逐批LIVE数据库查询、实际文件/FTP交付台账、两条海康任务原引擎等价验证。计划输入模式明确为FIXED_JSON_BATCH，不将重复测试批次标成生产持续采集。
