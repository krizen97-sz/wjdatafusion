# v4.8.4 至 v4.15.1 数据库升级与部署说明

整理日期：2026-09-29。适用范围：**已经完整部署 v4.8.4 的环境**升级到当前已集成的 v4.15.1。
业务源码基线为 `c0d9efa`，目标为 `b3758652c5b9dd6c25cdeca221a45f637eff610e`。
本交付只包含 SQL、检查脚本及说明，不包含前后端程序包，不表示已升级生产环境。

## 1. 本区间只有一项数据库增量

| 对象 | 变更 | 原有数据处理 |
| --- | --- | --- |
| `sup_server.root_direct_login` | 新增 `TINYINT(1) NOT NULL DEFAULT 0`，是否 root 直接登录 | 新增字段时旧记录为关闭；若字段已存在，保留所有现有 0/1 值 |
| `sys_menu` | 新增按钮权限 `support:credential:verify`，名称“服务器登录校验” | 按权限字符去重，菜单 ID 自增，挂到现有服务器管理菜单，不覆盖旧菜单 |
| `sys_role_menu` | **不修改** | 不自动为普通角色授权，不撤销原授权 |

不新增业务表，不重写 IP、SSH 端口、用户名、密码密文、平台归属、机房/U 位、操作记录及其他模块数据。
v4.14.2 的 hik 小写兼容由代码完成，不需要 SQL 批量改名或重新加密。

版本核对同时比对了 Git 中全部 SQL 变更与版本记录，不仅依赖版本描述：

| 版本区间 | 数据库变化 | 程序变化 |
| --- | --- | --- |
| v4.8.4 | 无数据库增量 | 文档 ZIP 兼容及现场代理排查；作为本次已部署基线 |
| v4.8.5、v4.8.6 | 无 | 自动化巡检执行记录和结果展示 |
| v4.9.0 至 v4.10.0 | 无 | 设备录入、接口整合、工作簿与明文读取；前后端配套 |
| v4.10.1、v4.11.0、v4.12.0 | 无 | 关系树、布局、搜索和全局交互 |
| v4.13.0 | 无 | 工作簿快照、事务批量保存、统一配置；前后端配套 |
| v4.14.0 | **上表的字段与权限** | SSH 登录校验和策略字段；SQL、前后端配套 |
| v4.14.1、v4.14.2 | 无新结构 | 账号统一、历史凭据兼容、实际登录使用小写 hik；前后端配套 |
| v4.14.3、v4.15.0、v4.15.1 | 无 | 端口列、Excel 式编辑与复制、编辑默认明文 |

未合并开发分支不属于此次“当前版本”。若实际数据库漏做过 v4.8.4 之前的迁移，必须先补齐并演练，不能凭界面版本号认定数据库完整。

## 2. 使用哪些文件

1. `support_check_20260929_server_credential_v4_8_4_to_v4_15_1.sql`：升级前后各运行一次，只读检查，不输出密码或密文。
2. `support_upgrade_20260929_server_credential_v4_8_4_to_v4_15_1_all.sql`：本区间完整增量 SQL，业务增量等价于 v4.14.0 独立脚本，额外增加失败前置检查。
3. 本说明：执行顺序、程序注意点和回滚边界。

源文件位于 `WDF100.0/sql/`。已有环境**不要执行** `ry_20260320.sql`、`support_v1.sql`、`support_deploy_all.sql` 或其他全量初始化脚本，避免重放历史迁移、种子或角色配置。
已经执行原 v4.14.0 独立升级 SQL 的环境，运行本脚本应显示 `PRESERVED`，不会重复新增。

脚本需要 MySQL 客户端支持 `DELIMITER`，请整文件运行，不要按分号拆分执行。
使用专用连接、默认 `autocommit=1`；同一数据库只允许一个升级执行人，禁止并发运行迁移或同时编辑菜单。
账号需要目标库的 `SELECT`、`ALTER`、`INSERT`、`CREATE ROUTINE`、`ALTER ROUTINE`、`EXECUTE` 权限。
脚本临时创建并删除 `rynew_upgrade_v484_v4151_20260929` 过程；报错中断可能暂留该辅助过程，解决问题后重新执行会清理重建。

脚本遇到缺表、缺字段、非 InnoDB 基线表、自定义触发器、父菜单缺失/重复、权限重复/挂错父菜单、已有登录字段定义或值不兼容时停止。**不要删除检查或使用 `--force` 跳过错误。**
这种停止不会覆盖原有业务数据；若在 DDL 完成后发生断线或后续权限不足，新列可能已经存在，修复原因后重跑即可。

## 3. 推荐部署顺序

### 3.1 维护窗口与备份

- 通知用户保存草稿并退出，暂停设备维护、导入和登录校验；确认没有长事务占用待改表。
- 若是多实例部署，所有实例停止接收写入，保持同一前后端版本，避免新旧接口混用。
- 备份整个业务数据库；至少单独核对 `sup_server`、`sys_menu`、`sys_role_menu` 的备份与行数。
- 备份原 JAR、完整前端目录、实际生效的配置/环境变量、代理配置、上传文件目录；敏感备份仅 DBA 可读，不进入 Git 或公开下载目录。
- 特别保留原来的 `support.credential.key` / `SUPPORT_AES_KEY`。**不能随程序替换、随机生成或改为空值**，否则原密码密文无法解密。

以下命令在升级包解压目录中执行。先将占位值替换为真实环境；密码用 `-p` 交互输入，不写进命令、脚本或 shell 历史。

```bash
export DB_HOST='实际数据库地址'
export DB_PORT='3306'
export DB_NAME='rynew'
export DB_USER='部署DBA账号'
umask 077
mysqldump --host="$DB_HOST" --port="$DB_PORT" --user="$DB_USER" -p \
  --default-character-set=utf8mb4 --single-transaction --routines --triggers --events \
  --hex-blob --set-gtid-purged=OFF --no-tablespaces "$DB_NAME" > rynew_before_v4_15_1.sql
test -s rynew_before_v4_15_1.sql
```

必须检查备份命令退出码，并在独立测试库做恢复验证；文件非空不等于备份可恢复。`--single-transaction` 针对事务表，若其他模块有非 InnoDB 表，交由 DBA 补充一致性备份方案。备份期间不要执行 DDL。

### 3.2 检查、升级、复核

```bash
mysql --host="$DB_HOST" --port="$DB_PORT" --user="$DB_USER" -p \
  --default-character-set=utf8mb4 --database="$DB_NAME" \
  < support_check_20260929_server_credential_v4_8_4_to_v4_15_1.sql > precheck.log 2>&1

mysql --host="$DB_HOST" --port="$DB_PORT" --user="$DB_USER" -p \
  --default-character-set=utf8mb4 --database="$DB_NAME" \
  < support_upgrade_20260929_server_credential_v4_8_4_to_v4_15_1_all.sql > upgrade.log 2>&1

mysql --host="$DB_HOST" --port="$DB_PORT" --user="$DB_USER" -p \
  --default-character-set=utf8mb4 --database="$DB_NAME" \
  < support_check_20260929_server_credential_v4_8_4_to_v4_15_1.sql > postcheck.log 2>&1
```

**逐条执行并检查日志及退出码，前一步失败就停止，不要无条件批量粘贴全部命令。**
升级前允许新字段和新权限数量为 0；其他基线检查必须正常。升级后 `matches_expected_definition=1`、`permission_count=1`，权限父菜单为现场服务器管理，`upgrade.log` 中有 `SUCCESS`。
对比升级前后服务器总行数和角色菜单关系总行数，均应不变；菜单最多新增 1 条。原有字段、密文及历史端口也应抽样比对，不只看数量。

### 3.3 替换前后端并重启

1. SQL 完成并复核后，部署同一已验证源码版本构建的完整后端 `wjdatafusion-admin.jar` 和完整前端 `dist`。
2. 保留生产数据库、Redis、凭据密钥、上传路径、ONLYOFFICE、Kafka/ETL 等现有配置。不要把开发配置覆盖到生产。
3. 按现场原来的服务管理方式重启后端，确认日志无缺列、SQL、解密或 Mapper 绑定异常。
4. 前端按完整目录原子切换，避免只替换 `index.html` 或部分 JS；保留旧目录供回退。
5. 提醒所有用户刷新并重新登录，使浏览器资源、菜单及权限重新加载；不要用 `FLUSHALL`/`FLUSHDB` 清空整个 Redis。

本区间没有新增 Maven/前端运行时依赖或必填配置项；沿用项目 JDK 17 基线。依然必须部署最新后端，不能因为 SQL 只有一列就只换前端。

## 4. 其他部署注意点

### 接口与代理

- v4.9.2 已撤除一批旧设备录入/删除接口。例如 `POST /support/server`、`POST /support/hardwareAsset`、`GET /support/equipment/list`，外部脚本若直接调用，需先核对迁移。
- 新工作簿使用 `GET /support/equipment/workbook/{siteId}`、`PUT /support/equipment/workbook`；统一录入和校验使用 `/support/equipment/servers/*`。代理或网关需要允许原有 GET/POST/PUT/DELETE 及上传能力。
- 现场实际 API 前缀、上游地址和 HTTPS 配置沿用原配置，不把本机 5185/8080 或其他测试地址写到生产。
- 凭据响应必须保持 `Cache-Control: no-store`，不要被代理缓存，也不要在代理/网关调试日志里记录明文请求体与响应。
- 保留 v4.8.4 已调整的文件上传代理配置及用户额度。本次 SQL 不改变文档上传额度，不要重新跑旧的额度初始化脚本。
- 前端使用生产构建；低版本 Chrome 场景继续执行 `npm run check:browser-compat`。语法扫描通过不等于已在目标旧内核实测；机房 3D 仍需浏览器支持 WebGL。

### 账号、权限与 SSH

- 普通角色按需单独授予 `support:credential:verify`；显示明文仍要求 `support:credential:viewPlain`。脚本不修改角色授权，管理员和 `datafusion` 沿用项目原权限逻辑。
- v4.15.1 中有明文查看权限的用户进入设备清单编辑时会自动显示密码，可手动隐藏，退出编辑后隐藏。上线前告知使用人员，避免共享屏幕或截图泄露。
- `root_direct_login=0` 只表示平台默认策略关闭，不修改远端 `PermitRootLogin`。登记了 hik 密码时先 hik 再 su；仅有 root 凭据时仍可直接用 root 校验。
- 显示和实际 SSH 用户名统一为小写 **hik**，密码不改大小写。若远端真实账号只有大写 `HIK`，应先核实设备账号，平台不会自动尝试多个大小写用户名。
- 新增服务器程序默认 SSH 端口是 **55555**；已有 22 或自定义端口原样保留，本 SQL 不统一改端口，也不修改旧表 `ssh_port` 默认值。
- 必须由后端部署主机能访问设备登记的 SSH 地址和端口。首次校验先与设备管理员核对 SSH 主机指纹，明确确认后才发送密码。
- 错误密码可能触发设备自己的锁定策略；不要拿全现场设备做批量试登录。校验不自动猜测或切换路径，同设备有 60 秒冷却，单实例最多 4 项并发。
- 旧 SSH 算法、su/PAM 或设备策略不兼容时单独处理，不以关闭主机校验、降低认证要求来“解决”。

### 数据与操作语义

- 表格编辑的是平台资产档案，不会直接修改远端 IP 或系统密码；单次统一保存最多 500 台，范围/版本冲突时重新核对。
- 服务器模板继续使用 `.xlsx`，建议从新程序下载模板；批量导入先校验再确认保存，已有服务器的复用不会覆盖其密码和端口。
- 旧凭据兼容读取不做一次性数据库搬迁。用户之后编辑、清空、删除账号可能同步更新旧字段与独立凭据记录，这是正常业务写入，不是本 SQL 的数据迁移。

## 5. 上线验收与回退

上线验收至少包括：登录及版本号 v4.15.1、现场画布、设备关系树、编辑/取消/保存、列内复制、权限受限账号、明文显示与退出隐藏、XLSX 预览确认、机房位置/上联，以及自动化巡检执行记录和原有文档上传。
写入验收使用明确的测试现场或测试设备，避免修改真实业务台账；SSH 实测另选经授权设备并先确认指纹。

**应用回退优先恢复成套旧前后端及原配置，保留新增列和权限。** 不执行 `DROP COLUMN`、批量置 0、清密码或删除角色菜单关系。
新增列不会要求旧程序读取；但新程序上线后的账号清空、删除等业务写入不会因回退程序自动恢复，应单独核验旧程序兼容性。
如确需恢复业务数据，先停写并比对备份与上线后新增数据，执行经确认的定向恢复方案；不要直接覆盖整库。

MySQL `ALTER TABLE`、存储过程 DDL 会隐式提交，不能靠包一层 `BEGIN/ROLLBACK` 实现整个升级的原子回退。参考 [MySQL 隐式提交说明](https://dev.mysql.com/doc/refman/8.0/en/implicit-commit.html)。大表 DDL 可能等待元数据锁或重建表，应留足维护窗口，参见 [ALTER TABLE 文档](https://dev.mysql.com/doc/refman/8.0/en/alter-table.html)。

## 6. 本次验证边界

- SQL 在隔离 MySQL 8.4.6 实例中验证，关闭 TCP 监听，使用独立临时 socket，不连接本机业务库或 250。
- 13 项真实 SQL 测试通过：首次升级、重复执行、原 v4.14.0 已升级状态、已有策略值 1 保留、旧字段和角色关系不变、定制父菜单 ID，以及缺表/字段、权限冲突、异常列类型/值、自定义触发器/引擎的停止保护。
- 测试基于 v4.8.4 的建表定义和合成记录，不是生产数据库全量镜像，不代替在生产备份恢复库中的演练。其他 MySQL 版本尚未实测。
- 复现脚本：`tools/site-fusion/test_server_credential_upgrade_v4_8_4_to_v4_15_1.mjs`；只接受本任务隔离 socket 命名并校验 datadir 和关闭网络状态，防止误跑到业务库。
- 本次未执行生产 SQL、未重启生产程序、未触发真实设备 SSH 登录；没有改变业务版本号。
