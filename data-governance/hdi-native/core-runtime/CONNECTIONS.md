# 平台连接适配

`HdiCoreConnections` 管理原 HDI 节点引用的数据源，使用平台 `masterDataSource`。原插件、表单和配置生成器仍由原包提供；本类只承担连接持久化、权限与网络范围、连接检查、关系表元数据查询。

先执行独立脚本 `WDF100.0/sql/data_governance_hdi_connections_upgrade_20261025.sql`，新增当前连接、不可变连接版本、实际表/主题标识登记三张表。每个用户最多100条未删除连接。连接密码接收原表单 RSA 密文，复用平台 `CredentialCryptoService` AES-GCM 加密保存；查询仅返回 `******` 和 `passwordConfigured`。原表单编辑重新加密 `******` 时保持旧密码。密码不进入任务 visual 或公开快照。

原数据源包括 PostgreSQL/dbType16/9.x、MySQL/dbType2/8.x、FTP/dbType18/1.0、Kafka/dbType8/2.x、RabbitMQ/dbType120/3.x。SFTP沿原FTP数据源表单的 `ext2=sftp`，不另造数据源类型；FTP使用被动模式，SFTP使用原密码认证。Kafka使用无认证模式，RabbitMQ使用显式账号密码的原AMQP模式；Kerberos、Ranger、SASL、RabbitMQ TLS和任意JDBC参数仍未开放。

## 与原表单的连接

原通用表单 module79553 提交 multipart 顶层 `datasourceName`、`datasourceType`、`datasourceVersion`、`folderId` 和 JSON `parameters`。HTTP适配层须校验真实平台权限与CSRF，拒绝本轮未支持文件上传，再将 `parameters` 解析为对象传 `saveForm` / `testForm`。

- PostgreSQL：`connectType`、`address`、`databaseSid`、`databaseUsername`、`databasePassword`，或原URL模式。
- FTP：`Host`、`Port`、`FolderPath`、`ConnectMode=1`、`ext2=ftp`及账号密码。原 `jointUrl` 对 `/incoming` 拼出 `ftp://IP:port//incoming`，双斜线为原实现行为，保留不重写。
- Kafka：`address`、`connectType`、`certification=none`、`authentication=none`。原测试按钮会携带隐藏的JAAS模板；无认证模式不使用该隐藏内容。
- MySQL：原 `MysqlDatasource` 表单，`connectType=host/url`、`address`、`databaseSid`、账号密码。原驱动是包内 MariaDB 2.7.5，host模式仍生成 `jdbc:mysql://IP:port/database?useSSL=false`。使用原URL模式时，可显式指定这一模板加 `&allowPublicKeyRetrieval=true` 供冷 `caching_sha2_password` 认证；端点仍必须在部署允许清单，参数不会被默认加进host模式，也不接受任意JDBC参数。该附加选项已用本机全新账号首次登录验证，不以预热缓存掩盖认证失败。
- RabbitMQ：原 `address`、`databaseUsername`、`databasePassword`、`Ssl`、`dbUrl`；`Ssl=1` 是原“否”，存入原DTO的 `ext1=0`。队列、vhost、exchange和routing key属于原节点参数。原连接SDK测试仅连接默认 `/` vhost，账号需有该vhost连接权限；本地fixture只授予默认vhost的空资源权限，业务仍用专用vhost。该原连接SDK没有队列枚举服务，平台不会返回虚构的队列列表。
- SFTP：仍使用 `Host/Port/FolderPath/ConnectMode/ext2`；`ext2=sftp` 时只批准SSH端口，不分配FTP被动端口。原 `FtpDatasource.jointUrl` 即使SFTP也使用 `ftp://` 前缀并保留原目录双斜线，协议取自 `ext2`。
- 编辑读取 `get` 的 `_platformRevision`，调用 `formParameters` 填充原 formItems.value，保留原字段名及密码占位，不创建第二套表单。

平台兼容接口可直接调用 `list/page/nameExists/get/saveForm/delete/testForm/testById/schemas/tables/columns/getTable`。元数据采用实际 JDBC DatabaseMetaData，按连接属主校验，无伪造表字段。连接测试不写数据库表、不上传FTP文件、不生产或消费Kafka消息；真正节点运行仍由原 Core 负责。

MySQL、RabbitMQ、SFTP的新连接测试进入已有的一次性SDK子JVM，在任何连接前安装精确端点守卫；不为主平台JVM增加协议客户端直连回退。原注解注入器会调用旧管理加密服务，因此先以空密码注入原非敏感表单字段，再调用原 `setDatabasePassword` 传入平台已经解密的凭据。RabbitMQ额外调用原 `setAddress` 初始化原客户端的host/port，而不是自行实现AMQP测试。MySQL实际连接使用原 `MysqlDatasource.getDriver`，表/字段读取调用原 `DatabaseUtils`；MySQL逻辑schema来自真实 `Connection.getCatalog` / `TABLE_CAT`，不伪造public。原管理服务的字段类型小写展示投影保留。

## 冻结和执行

保存任务时 `freezeReferences(owner, visual)` 产生 `{connectionId:revision}`；由编译器放入快照封套的 `connectionRefs`，不改变原 visual。运行时 `resolveForRun(owner, visual, connectionRefs)` 读取精确属主和历史版本：

1. `connections`：按ID映射原 DatasourceDto、datasourceParamVoList、connectionRevision，仅供可信编译器；含解密密码，不可回传浏览器。
2. `connectionBindings`：owner/connectionId/revision/kind及部署允许的精确endpoint，交worker reserve。
3. `redactionValues`：真实秘密值，仅交worker私有脱敏文件，不进状态响应。

修改连接不会改变已发布任务的冻结版本。删除/停用当前连接后，未来执行明确拒绝；不会偷偷切换到最新版本。端点策略每次解析重新校验。

表和主题标识不依赖管理库的名称列排序规则：`dg_hdi_connection_table.object_key` 取原始 UTF-8 `schema + NUL + name + NUL + kind` 的 SHA-256 小写十六进制，唯一键和再次查找均使用 `connection_id + owner_id + object_key`，按ID读取时也不比较名称。名称列仅保留原文。因此即使 MySQL 的 `schema_name/table_name` 为 `utf8mb4_0900_ai_ci` 等不区分大小写的排序规则，PostgreSQL 的 `Foo/foo`、`Schema/schema` 和 Kafka 的 `Orders.V1/orders.v1` 仍分配不同的稳定平台注册ID。不要改成名称列的普通等值查找。

此边界由 `HdiCoreMetadataIdentityTest` 分别验证：H2用例只证明原字节摘要与重复查找；设置 `HDI_METADATA_MYSQL_FIXTURE` 后，MySQL用例仅接受单独标记的本机测试数据库，并实证名称列的ci等值查询会命中多行，而实际注册方法仍产生不同ID。当前实现已使用该摘要键，无需对现有安装追加名称列DDL或迁移ID；相关基础SQL保持幂等，审计不会修改已有任务库或外部业务表。

该可选MySQL测试的配置为0600 JSON：`owner=rynew-hdi-metadata-case-test`、`host=127.0.0.1`、`port=13306`、`database=rynew_hdi_case_<12位十六进制>`，另含账号、密码、`expectedDatadir` 及现有应用 MySQL JDBC 驱动的 `driverJar/driverSha256`。先单独创建该空测试库，测试只在其中重复应用模块建表SQL和登记测试标识。它复用已安装驱动，不新增生产依赖；不允许指定平台应用数据库，不以H2通过代替MySQL排序规则实测。

## 部署网络策略

```yaml
data-governance-hdi-core:
  connections:
    allowed-endpoints:
      - 127.0.0.1:15432
      - 127.0.0.1:2121
      - 127.0.0.1:19092
    ftp-passive-ranges:
      "[127.0.0.1:2121]": 22100-22109
    kafka-brokers:
      "[127.0.0.1:19092]": ["127.0.0.1:19092"]
```

地址是示例，部署应填写实际批准端点。Map键须使用方括号保留IPv4与冒号，例如 `"[127.0.0.1:2121]"`；否则Spring配置绑定会改变键，导致被动范围无法匹配。连接使用明确IPv4，支持标准FTP控制端口21；禁止Core保留2180–2183，FTP被动范围最多32个端口。Kafka公布的broker须同时属于允许端点，worker仍使用隔离网络和固定通道。

测试：`HdiCoreConnectionsTest` 验证owner隔离、密文保存、原表单字段、编辑冲突、冻结版本、删除阻断和网络策略。设置 `HDI_CONNECTION_FIXTURE_DIR` 可读取本任务私有 PostgreSQL/FTP fixture JSON 并执行真实连接检查、PG模式/表/列读取与Kafka集群元数据检查；凭据不打印、不提交。
