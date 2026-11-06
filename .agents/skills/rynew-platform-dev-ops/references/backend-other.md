# IPAM、白名单与历史兼容边界

只在任务涉及这些模块时读取。业务说明不自动授权扫描、消息拉取/发布或外部数据写入；交付被明确排除的模块按排除清单核验。

## IPAM

前端 `RuoYi-Vue3-master/src/views/ipam`；后端 `IpamController/WorkbookController/ScanController/DashboardController`，Service为 `IpamServiceImpl/IpamWorkbookServiceImpl/IpamScanServiceImpl/IpamDashboardServiceImpl`，XML位于 `resources/mapper/ipam`。

| 业务 | 实现与约束 |
| --- | --- |
| 网络/网段 | network→segment，CIDR、边界/网关和网段重叠按 `IpamAddressUtils`、`IpamConfigValidator`及Service校验 |
| 地址网格 | 未持久化地址可由网段生成呈现；不能将“列表没有行”视为地址不存在或已释放 |
| 地址状态 | FREE/RESERVED/ALLOCATED/DISABLED/ISSUED，使用既有分配/修改/释放方法，不直接改状态列 |
| 权限 | 分配、下发、编辑、释放分别校验；保存包含ISSUED状态的批次也要 `ipam:address:issue` |
| 配置/多表格提交 | `commitConfigSheet`、`commitWorkbook`事务，单批行数及归属校验；工作簿不能绕过普通分配规则 |
| 扫描 | 授权网段、`IpamScanProperties`限额、锁/租约/续期、job/result及失败清理；不是服务器SSH密码池扫描 |
| 展示统计 | `IpamDashboardServiceImpl`按实际地址/映射/扫描记录聚合，不把探测到在线直接变成已分配 |

十三表包括site/network/segment/address、setting、operation_log、mapping_relation、三张scan表和migration追踪表；以当前105表快照核对完整名称。

### 凭据实现必须核对当前代码

核验基线中 `prepareCredentialForSave` 保留空输入对应的原值，`IpamMapper.xml` 直接存取 `login_password`，`getAddressCredential`在专用权限下记录查看审计并返回该字段；没有读取 `IPAM_CREDENTIAL_KEY`。因此不能从旧记忆宣称本模块已做AES加密，也不在部署清单新增一个无消费者的密钥。

这是当前实现边界，不是鼓励扩大明文暴露。普通列表/导出应核对既有隐藏和脱敏契约；如用户要求改造加密，应单独设计存量迁移、密钥生命周期及回退，不能静默混入其他模块任务。

相关测试：`IpamServiceImplTest`、`IpamWorkbookServiceImplTest`、`IpamScanServiceImplTest`、`IpamAddressSerializationTest`、前端 `npm run test:ipam`。扫描验证使用本次授权的合成/隔离目标。

## 白名单

`WhitelistPlateController` → `WhitelistPlateServiceImpl` 使用专用 `whitelistPostgresJdbcTemplate` 访问**外部PostgreSQL的whitelist表**；不是平台master MySQL表。非管理员有owner token/车牌范围过滤，导入/删除也不能绕过授权范围。

`WhitelistFilterDataController` → `WhitelistFilterDataServiceImpl`：按授权车牌过滤，拉取Kafka并处理到本地MySQL `wl_filter_data`，另有发布操作。`whitelist.kafka.*`配置属于这条独立业务链，不是巡检或平台全局消费者配置。拉取/发布是有副作用的业务操作，不作随手健康检查。

配置类：`WhitelistExternalDataSourceConfig`、`WhitelistPostgresProperties`、`WhitelistKafkaProperties`。DriverManagerDataSource建立Bean不等于外部数据库已验证可连接；实际依赖按执行路径判断。

当前全量初始化只建本地filter数据表并含菜单权限；不带入外部PG的准确结构和业务行。`WDF100.0/sql/whitelist_postgres.sql`仅有说明，不能据此猜造外部白名单表。

用户明确不要白名单程序的交付：核对前后端编译内容、接口/页面/菜单、权限、外部驱动/Bean、专用配置、定时/消息任务、SQL与手册。只隐藏菜单或删一个YAML段不代表程序已裁掉。不得把整个当前源码仓库当作裁剪程序包发送。

## 历史巡检和演示代码

`SupportTimInspectionServiceImpl/PlanServiceImpl`、相应Controller/Mapper和八张 `sup_tim_inspection*` 表为历史兼容路径。新库保留表但菜单隐藏停用；新功能优先自动巡检链，数据迁移应显式处理历史含义。

`NovelServiceImpl`为遗留演示代码，当前最新SQL不猜造缺少定义的 `tb_novel`。遇到此入口异常先确认是否启用和是否在任务范围，不为满足健康检查补造业务表。
