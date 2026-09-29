# v4.15.1 至 v4.16.0 数据库增量部署说明

整理日期：2026-09-29。基线为已完整部署的 **v4.15.1**（`b375865`），当前目标为 **v4.16.0**（业务源码 `9360a2e`）。只整理已集成变更，不包含未合并开发分支。

## 一、完整变更清单

| 对象 | 变更 | 原有数据影响 |
| --- | --- | --- |
| `sup_server_scan_credential` | 新增现场授权候选凭据池：名称、登录方式、hik/root 密码密文、审计字段 | 不从旧密码或附件导入任何默认凭据；现场被业务删除时级联删除对应池记录 |
| `sup_server_scan_profile` | 新增最近成功扫描资料：主机名、系统名称、系统版本、内核版本、探测端口、采集时间 | 不改原服务器记录；服务器被业务删除时级联删除对应扫描资料 |
| `sys_menu` | 新增“扫描服务器并同步”按钮权限 `support:server:scan` | 按权限去重，父菜单按 `support:server:list` 查找，ID 自增，不覆盖旧菜单 |
| `sys_role_menu` 和其他业务表 | **不修改** | 不自动授权、不修改原服务器、账号密码、SSH 端口、归属及位置 |

本区间不新增旧表字段。`sup_server.root_direct_login` 属于早先 v4.14.0 的变更，完整 v4.15.1 环境应已有该字段，本包只检查、不重复迁移。

已对照 Git 全部 SQL 差异与版本记录：v4.16.0 独立 SQL 和两个全量脚本中的新增段一致。区间中出现的 `f20de34` 只是整理 **v4.8.4 至 v4.15.1** 的旧增量，不是此次新增数据库需求，因此没有重复打包。

## 二、包内文件

- `support_upgrade_20260929_server_credential_v4_15_1_to_v4_16_0_all.sql`：唯一需要执行的增量升级 SQL。
- `support_check_20260929_server_credential_v4_15_1_to_v4_16_0.sql`：升级前后各运行一次的只读检查，不输出密码或密文值。
- `README.md`：本说明；`MANIFEST.json`、`SHA256SUMS`：提交来源和文件校验；`upgrade-tests.log`：隔离测试结果。

源 SQL 位于 `WDF100.0/sql/`。本包升级语句与原 `support_upgrade_20260929_server_credential_scan_v4_16_0.sql` 完全一致，只补充交付头注释；**二选一执行，不需要先后执行两份**。已经执行过原脚本且结构正确的环境无需重复升级。

已有数据库不要运行 `support_v1.sql`、`support_deploy_all.sql` 或全量初始化脚本。若仍在 v4.8.4，先完成前一份 v4.8.4 至 v4.15.1 升级及复核，再使用本包。

## 三、执行步骤

### 1. 维护与备份

停止设备维护写入和正在运行的扫描任务，确认目标数据库及版本。备份业务库、旧前后端、有效配置，保留原 `support.credential.key` / `SUPPORT_AES_KEY`，不能生成新密钥替换，否则旧凭据无法解密。

备份含敏感信息，只放在 DBA 可读的受控目录，不进入 Git、公开目录或本交付包。恢复演练应在隔离库完成。以下命令使用交互密码，不把密码写进脚本；先替换环境占位值。

```bash
export DB_HOST='实际数据库地址'
export DB_PORT='3306'
export DB_NAME='rynew'
export DB_USER='部署DBA账号'
umask 077
mysqldump --host="$DB_HOST" --port="$DB_PORT" --user="$DB_USER" -p \
  --default-character-set=utf8mb4 --single-transaction --routines --triggers --events \
  --hex-blob --set-gtid-purged=OFF --no-tablespaces "$DB_NAME" > rynew_before_v4_16_0.sql
```

检查备份退出码和恢复结果，不能只以文件非空判断成功。备份期间禁止 DDL；若存在非事务表，由 DBA 补充一致性备份方案。迁移账号需要读元数据及基线表、建表、创建外键所需 REFERENCES、插入菜单的权限；本脚本不需要存储过程。

### 2. 前置检查

在交付包解压目录执行：

```bash
mysql --host="$DB_HOST" --port="$DB_PORT" --user="$DB_USER" -p \
  --default-character-set=utf8mb4 --database="$DB_NAME" \
  < support_check_20260929_server_credential_v4_15_1_to_v4_16_0.sql > precheck.log 2>&1
```

**检查脚本只报告，不会自动阻止之后的升级。** 必须人工确认：目标库正确、基础检查均为 `PASS`，没有 `STOP` 或 SQL 错误；新表及新权限升级前允许 `NOT_APPLIED`。

必须只有一个 `support:server:list` 的 C 类型父菜单；同名扫描权限最多一条且归属、类型、状态正确。发现自定义触发器、重复权限、缺少前置字段等，先由 DBA 处理，不能删掉检查继续运行。

若新表已经存在，需将输出的列、类型、默认值、主键、索引和外键与升级 SQL 逐项比较。`REVIEW` 表示需要核对，不等于自动验收通过；`IF NOT EXISTS` **不会修复已有错误表结构**。若账号无权查看触发器元数据，应由有权限的 DBA 完成检查，不能把无可见记录当作没有触发器。

### 3. 执行升级及复核

```bash
mysql --host="$DB_HOST" --port="$DB_PORT" --user="$DB_USER" -p \
  --default-character-set=utf8mb4 --database="$DB_NAME" \
  < support_upgrade_20260929_server_credential_v4_15_1_to_v4_16_0_all.sql > upgrade.log 2>&1
```

确认退出码为 0、日志无错误后，重新执行只读检查，输出到 `postcheck.log`。不要使用 `--force`，不要多人并发执行，不要把全部步骤无条件连续执行。

验收标准：两张新表结构与 SQL 一致；现场池外键指向 `sup_site.site_id`、扫描资料外键指向 `sup_server.server_id`，删除规则均为 `CASCADE`；扫描权限只有一条、父级正确。现场、原服务器、原凭据、角色授权行数及内容应保持不变；菜单最多新增一条。仅比较行数不足以证明内容未变，需与备份抽样核对。

首次执行不插入扫描业务数据；重复执行不清空已有池及扫描资料。若执行中断，两张表可能已部分建好，先处理原因、检查已有定义再重跑，禁止直接删表重建。

## 四、程序部署注意点

1. SQL 检查通过后，配套部署同一源码版本的前端和后端，不能只换界面。保留生产数据库、Redis、上传目录、代理及 AES 密钥等原配置。
2. 普通用户按需授予 `support:server:scan`，扫描同步仍需原有服务器/设备新增、编辑权限；本 SQL 不自动扩大角色权限，管理员和 `datafusion` 沿用现有机制。重新登录加载新菜单权限，勿清空整个 Redis。
3. 扫描由后端发起，需具备到授权设备 22、2343、55555 端口的网络可达性；本 SQL 不修改现有 SSH 端口，不修改远端设备配置，也不会自行开始扫描。
4. 无需部署附件 EXE、Python 或 Flask。候选凭据由管理员配置，按 root 直登或小写 hik 后切换 root 使用；不会从设备读取原密码，也不内置附件中的候选密码。
5. 任务暂存在后端单实例内，升级前停止任务并等待结束；重启不会自动恢复/重试密码。多实例需保持同实例会话粘性，当前没有分布式扫描队列。
6. 上线后先检查入口、凭据池及扫描资料字段；真实网段扫描另行选择授权设备，核对 SSH 指纹后才发送密码，避免因错误候选密码触发设备锁定。

## 五、回退与验证边界

回退时优先恢复匹配的旧前后端和原配置，**保留新增表、菜单以及已产生的数据**。不要 DROP 表、清空凭据、删除扫描新建的服务器或覆盖整个数据库来回退程序。上线后产生的正常业务写入不会随程序回退自动撤销，数据恢复须另行评估。

MySQL DDL 会隐式提交，不能用外围事务保证整个建表升级原子回退。即使应用回退，也应保留本次数据库备份供定向恢复核验。参考 [MySQL 隐式提交说明](https://dev.mysql.com/doc/refman/8.0/en/implicit-commit.html)。

本次仅整理、测试、交付脚本，不执行本机业务库或 250 生产库升级，不重启程序。测试使用独立临时 MySQL、关闭 TCP 监听、使用合成数据；覆盖首次执行、重复执行、原单版脚本已执行、定制菜单 ID 及检查脚本异常提示。具体结果见 `upgrade-tests.log`。测试不是生产全量镜像，部署前仍须在生产备份恢复库演练。

复现测试：`tools/site-fusion/test_server_credential_upgrade_v4_15_1_to_v4_16_0.mjs`，只接受本任务专用临时 socket，并核验 datadir 及禁用网络状态。本次无应用代码修改，不增加业务版本号，不重复运行无关前后端构建。
