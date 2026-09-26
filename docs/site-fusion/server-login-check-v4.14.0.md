# v4.14.0 服务器密码登录校验

## 入口与规则

现场画布进入设备管理，点击服务器行的“配置”，打开“凭据”页。
其中“是否root直接登录”默认关闭，修改确认后复用设备工作簿接口保存。
设备清单的“账号密码”字段组和单台新增服务器也提供此开关。

| 开关 | 已登记凭据 | 校验路径 |
| --- | --- | --- |
| 关闭 | HIK与root | 按实际登记的HIK用户名登录SSH，再通过su验证root密码及UID 0 |
| 关闭 | 只有root，没有HIK密码 | root直接登录SSH，并确认UID 0 |
| 开启 | 有root，不论是否有HIK | root直接登录SSH，并确认UID 0 |
| 任意 | 缺少root密码 | 返回缺少凭据，不连接设备 |

新服务器SSH端口沿用55555，历史端口不修改。该开关只控制平台的校验方式，
不会修改设备的PermitRootLogin、系统账号、密码或其他SSH配置。
当前功能针对服务器的Linux/Unix SSH登录，不将其他硬件的Web登录密码当作SSH凭据。

## 校验交互

1. 点击“校验登录”，后端只读取该现场、该服务器已保存的地址、端口与加密凭据。
2. 首次连接只取得SSH主机指纹，未确认前不发送密码。与设备管理员核对指纹后，
   点击“确认指纹并校验”，明确确认后执行真实登录。
3. 分步显示SSH登录、用户身份及root切换结果。错误登录可能触发设备自己的锁定策略。
4. 每次只尝试一次密码，不猜密码、不自动改试另一种登录路径；同一服务器完成
   一次尝试后间隔60秒，单实例最多4项并发。进程重启不保留此临时冷却。
5. 结果包含时间、实际登录账号、主机指纹及每步结果，不包含密码或远端原始输出。
   结果仅用于本次校验，不代表永久有效的设备健康状态。

未发生SSH密码认证、HIK本身已经是UID 0、或su未要求密码而取得root权限时，
明确返回“未验证密码”，不误报密码正确。su/PAM可能拒绝切换，不能将所有拒绝
都归因于密码错误。机制依据：[su手册](https://www.man7.org/linux/man-pages/man1/su.1.html)、
[JSch主机密钥示例](https://www.jcraft.com/jsch/examples/KnownHosts.java.html)。

## 数据与权限

- 新增sup_server.root_direct_login，TINYINT(1)、NOT NULL、DEFAULT 0。
- 新增support:credential:verify按钮权限；datafusion和管理员沿用原有全权限逻辑。
- 登录校验和显示明文密码为不同权限，普通角色不自动扩权。
- POST /support/equipment/servers/loginCheck只接收siteId、serverId、hostFingerprint，
  不接受任意命令、目标地址或浏览器传来的密码。
- 开关由已有PUT /support/equipment/workbook稀疏更新，保留版本核对和修改记录。
- 同名多份凭据、停用凭据均拒绝猜测；明确清空的新凭据不恢复使用历史密码。
- 请求/响应不进入通用操作日志正文，密码不写前端防重复提交缓存。
- 新旧现场Excel兼容：新增root直接登录列，旧文件没有此列时按关闭导入。
- 保留既有JSch库和算法，不为通过校验而降低算法或主机校验要求。算法不兼容单独提示。

## 组件复用

| 需求 | 已有实现 | 选用组件 | 参考 | 定制范围 |
| --- | --- | --- | --- | --- |
| 登录策略 | 角色页开关确认 | el-switch、现有modal确认 | system/role/index.vue | 保存单一工作簿字段 |
| 凭据配置 | 设备配置Drawer/Tabs | EquipmentRoom3DWorkspace | 原模块 | 新增凭据页内模块组件 |
| 校验过程与结果 | 批量录入校验反馈 | el-button、el-alert、el-table、el-tag、el-descriptions | EquipmentIntakeDialog.vue、ServerIntakeReviewDialog.vue | 业务状态映射和指纹换行 |
| 表格布尔编辑 | 现有RevoGrid单元格 | EquipmentWorkbookCell、el-switch | EquipmentInventory.vue | 新增boolean字段类型 |

没有新增前端运行时依赖、主题或全局样式。沿用既有UI例外边界，无新增例外。

## 部署与验证

独立脚本：
WDF100.0/sql/support_upgrade_20260926_server_credential_login_check_v4_14_0.sql。
support_v1.sql和support_deploy_all.sql也包含同样的字段及幂等升级块。
已有环境只执行独立脚本，不为这一字段重跑整套历史升级SQL。

本地localhost:3306/rynew已获用户授权升级，执行前备份相关表；连续执行两次，
251台服务器的原有字段逐值保持一致，新字段均为关闭，权限菜单不重复。
不升级250生产环境，不使用业务设备的密码做自动验收。

- 相关后端82项测试通过，包含本机隔离SSH服务的真实握手、主机指纹、
  正确/错误HIK和root密码、su交互与免密切换。隔离服务模拟命令应答，
  不等同于已验收具体设备的Linux/PAM配置。
- 前端270项测试及UI Guard、生产构建、163个构建JS兼容语法检查通过。
  Chrome64语法检查不等于Chrome64实体浏览器验收。
- 已登录真实页面检查凭据配置和默认关闭状态；写入及结果交互用隔离内存
  适配器验收，不提交现有设备数据。浅深色及390px窄屏检查。
- 本地完整程序需同步前后端并重启8080；数据库先升级再切程序。

回退只需恢复配套前后端程序。新增字段可保留，不执行DROP、不恢复整库，
避免覆盖其他模块或升级后用户登记的数据。SSH登录仍受真实设备网络、
认证策略、su/PAM规则和算法兼容性约束；必要时另选经授权的设备实测。
