# 平台连接适配

`HdiCoreConnections` 管理原 HDI 节点引用的数据源，使用平台 `masterDataSource`。原插件、表单和配置生成器仍由原包提供；本类只承担连接持久化、权限与网络范围、连接检查、关系表元数据查询。

先执行独立脚本 `WDF100.0/sql/data_governance_hdi_connections_upgrade_20261025.sql`，新增当前连接、不可变连接版本、实际表/主题标识登记三张表。每个用户最多100条未删除连接。连接密码接收原表单 RSA 密文，复用平台 `CredentialCryptoService` AES-GCM 加密保存；查询仅返回 `******` 和 `passwordConfigured`。原表单编辑重新加密 `******` 时保持旧密码。密码不进入任务 visual 或公开快照。

本轮原数据源：PostgreSQL/dbType16/9.x，FTP/dbType18/1.0，Kafka/dbType8/2.x。FTP使用被动模式，Kafka使用无认证模式；SFTP、Kerberos、Ranger、SASL和自定义JDBC参数需要独立接入与验收，不能把未支持配置静默降级。

## 与原表单的连接

原通用表单 module79553 提交 multipart 顶层 `datasourceName`、`datasourceType`、`datasourceVersion`、`folderId` 和 JSON `parameters`。HTTP适配层须校验真实平台权限与CSRF，拒绝本轮未支持文件上传，再将 `parameters` 解析为对象传 `saveForm` / `testForm`。

- PostgreSQL：`connectType`、`address`、`databaseSid`、`databaseUsername`、`databasePassword`，或原URL模式。
- FTP：`Host`、`Port`、`FolderPath`、`ConnectMode=1`、`ext2=ftp`及账号密码。原 `jointUrl` 对 `/incoming` 拼出 `ftp://IP:port//incoming`，双斜线为原实现行为，保留不重写。
- Kafka：`address`、`connectType`、`certification=none`、`authentication=none`。原测试按钮会携带隐藏的JAAS模板；无认证模式不使用该隐藏内容。
- 编辑读取 `get` 的 `_platformRevision`，调用 `formParameters` 填充原 formItems.value，保留原字段名及密码占位，不创建第二套表单。

平台兼容接口可直接调用 `list/page/nameExists/get/saveForm/delete/testForm/testById/schemas/tables/columns/getTable`。元数据采用实际 JDBC DatabaseMetaData，按连接属主校验，无伪造表字段。连接测试不写数据库表、不上传FTP文件、不生产或消费Kafka消息；真正节点运行仍由原 Core 负责。

## 冻结和执行

保存任务时 `freezeReferences(owner, visual)` 产生 `{connectionId:revision}`；由编译器放入快照封套的 `connectionRefs`，不改变原 visual。运行时 `resolveForRun(owner, visual, connectionRefs)` 读取精确属主和历史版本：

1. `connections`：按ID映射原 DatasourceDto、datasourceParamVoList、connectionRevision，仅供可信编译器；含解密密码，不可回传浏览器。
2. `connectionBindings`：owner/connectionId/revision/kind及部署允许的精确endpoint，交worker reserve。
3. `redactionValues`：真实秘密值，仅交worker私有脱敏文件，不进状态响应。

修改连接不会改变已发布任务的冻结版本。删除/停用当前连接后，未来执行明确拒绝；不会偷偷切换到最新版本。端点策略每次解析重新校验。

## 部署网络策略

```yaml
data-governance-hdi-core:
  connections:
    allowed-endpoints:
      - 127.0.0.1:15432
      - 127.0.0.1:2121
      - 127.0.0.1:19092
    ftp-passive-ranges:
      "127.0.0.1:2121": 22100-22109
    kafka-brokers:
      "127.0.0.1:19092": ["127.0.0.1:19092"]
```

地址是示例，部署应填写实际批准端点。连接使用明确IPv4，支持标准FTP控制端口21；禁止Core保留2180–2183，FTP被动范围最多32个端口。Kafka公布的broker须同时属于允许端点，worker仍使用隔离网络和固定通道。

测试：`HdiCoreConnectionsTest` 验证owner隔离、密文保存、原表单字段、编辑冲突、冻结版本、删除阻断和网络策略。设置 `HDI_CONNECTION_FIXTURE_DIR` 可读取本任务私有 PostgreSQL/FTP fixture JSON 并执行真实连接检查、PG模式/表/列读取与Kafka集群元数据检查；凭据不打印、不提交。
