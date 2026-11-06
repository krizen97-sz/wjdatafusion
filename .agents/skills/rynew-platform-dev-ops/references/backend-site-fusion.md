# 现场融合业务链路

业务根：`WDF100.0/wjdatafusion-manage/src/main`。以下文件未写完整前缀时，Java位于 `java/com/hm/manage`，XML位于 `resources/mapper/support`。

## 工作台与主子平台

`SupportSiteController.workbench` 的 `GET /support/site/workbench/{siteId}` 调用 `SupportSiteServiceImpl.getSiteWorkbench`。工作台把现场、平台树、服务器、硬件、组织联系人和关系数据组装成VO；前端 `views/support/site/SiteConfigDialog.vue` 是重要维护入口，列表页不是全部业务逻辑。

`SupportPlatformServiceImpl` 负责：

- 主平台 `MAIN`、子平台 `SUB`；子平台父节点必须是同一现场的主平台。
- 主平台维护网络环境；子平台按现有规范清理不适用值。修改前核对 `validatePlatform` 相关分支，不能仅让前端允许填值。
- 平台关联服务器限定同一现场；绑定前检查对象存在和重复关联。
- 联系人只能关联主平台。联系人角色从 `support_contact_role` 字典取值，网络环境从 `support_network_env` 取值；内置字典保护同时位于system服务。

追踪关系到 `SupportPlatformMapper`、`SupportPlatformServerRelMapper`、`SupportPlatformAssetRelMapper`、`SupportPlatformOrgRelMapper`、`SupportPlatformContactRelMapper`。增加字段时同时核对查询VO、导入导出以及删除引用关系。

## 设备、位置、拓扑与工作簿

| 关注点 | 主要Service/Mapper | 保持的语义 |
| --- | --- | --- |
| 统一设备列表/入库 | `SupportEquipmentServiceImpl`、`SupportEquipmentServerIntakeService`、`SupportEquipmentBindingMapper` | 分清SERVER和HARDWARE来源、可修改范围和平台归属 |
| 机房/机柜/U位 | `SupportEquipmentLocationMapper`、`SupportEquipmentLayoutUtils` | 位置边界、机柜占用和同现场关系须后端验证 |
| 设备链路 | `SupportEquipmentTopologyServiceImpl`、`SupportEquipmentTopologyMapper` | 端点、链路对象和现场归属保持一致 |
| 表格式编辑 | `SupportEquipmentWorkbookService`、`SupportEquipmentWorkbookMapper` | 读取快照后按现有提交校验，不用整表覆盖绕过校验 |
| 拓扑工作簿 | `SupportEquipmentTopologyWorkbookServiceImpl` | 导入导出与图形编辑沿同一关系模型 |
| 现场整包导入导出 | `SupportSiteServiceImpl.exportSitePackage/importSitePackage` | 多表ID映射和依赖顺序，不能把源ID当目标库ID |

前端3D是同一设备数据的编辑入口。改动时同时看表格、画布、旧设备引用和导出格式；不要另建一套与服务器/硬件脱节的数据源。

## 凭据和登录验证

`CredentialCryptoService` 通过 `SupportCredentialProperties` 读取 `support.credential.key`，启动校验非空，使用AES/GCM随机nonce，密钥由配置字符串SHA-256派生。`sup_server_credential`与扫描候选密码池是不同对象。

普通响应按现有逻辑脱敏；明文查看使用 `support:credential:viewPlain` 专用接口并保留审计。不要把密文再次作为SSH密码、把`******`保存成新密码或在失败日志输出解密内容。

密码池v4.17迁移新增 `sup_server_scan_credential.password_cipher`，新程序使用独立候选密码。旧 `hik_password_cipher/root_password_cipher/login_mode` 列保留作核对，但不代表旧程序回退后天然兼容新编辑。原密钥必须保留。

`SupportServerPasswordPoolMatcher`：完整字符串去重，不trim、不改大小写；支持 `AUTO`、`ROOT_DIRECT`、`HIK_SU_ROOT`。当前每账号尝试限额由请求设置在1–3之间；AUTO优先hik，只有SSH认证候选均被拒绝才回退root。hik成功但su失败时保持已验证hik，有限尝试root候选，不做任意N×N穷举。具体额度/保护继续以 `SupportServerLoginGuard` 和当前请求为准。

## 扫描状态与同步

源码入口是 `SupportServerScanController`、`SupportServerScanService`；关键阶段：

```text
start: 核对现场/子平台/目标/密码池 → DISCOVERING（不试密码）
→ REVIEW（用户核对SSH指纹和目标）
→ confirm → SCANNING（有限登录核验）
→ SupportServerScanSyncService（核验后同步）→ DONE
```

- start只允许当前现场SUB平台；同现场已有运行或REVIEW任务时拒绝再开，任务总数和协调/工作线程有上限。
- confirm重新检查候选池是否变化、目标是否属于本任务、端口指纹是否已发现、设备可编辑状态。不能自动越过REVIEW。
- 已登记硬件、重复IP、停用或不可编辑的设备有明确跳过/拒绝分支；不能“扫描到IP就强制新建服务器”。
- 取消是 `STOPPING/CANCELLED` 等实际状态，须等待工作退出；任务在内存中按userId隔离，不承诺重启后继续。
- 异步工作保留必要SecurityContext，但没有HTTP请求。`SupportChangeLogServiceImpl` 使用 `RequestContextHolder` 安全获取请求；诊断“验证或同步未完成”要区分登录、root核验、同步和审计，不能直接归咎密码错误。

扫描会访问真实设备并可能入库。讲解、构建、SQL验证不授权真实扫描；根据用户本次明确范围选择测试目标，不能因连接已保存就随意扩大范围。

## 排障与验证

工作台缺数据：先核对siteId/权限和Mapper返回，再查平台/设备关系；树显示正常不证明绑定正确。登录后同步失败：查阶段状态→核验VO→Sync→审计上下文，避免无差别重试触发设备锁定。

相关回归：`SupportEquipmentServiceImplTest`、`SupportEquipmentCredentialTest`、`SupportEquipmentWorkbookServiceTest`、`SupportEquipmentTopologyServiceImplTest`、`SupportServerPasswordPoolMatcherTest`、`SupportServerScanServiceTest`、`SupportServerScanPersistenceTest`、`SupportChangeLogServiceTest`；前端 `npm run test:site-fusion`。

需要迁移时读取 `docs/site-fusion/DEPLOY_v4.16.0_to_v4.17.0.md`、`server-password-pool-v4.17.0.md` 和相应独立SQL，不复跑旧整包初始化。
