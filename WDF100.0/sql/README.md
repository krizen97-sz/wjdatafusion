# 数据库脚本入口

当前应用版本：`v4.17.2`；建表快照更新日期：`2026-11-06`。

## 最新建表 SQL

[`rynew_schema_latest.sql`](rynew_schema_latest.sql) 是平台 MySQL 运行表的最新**仅结构**快照，共 105 张表：

| 范围 | 表数 |
| --- | ---: |
| 若依系统、代码生成 | 20 |
| Quartz | 11 |
| 现场融合、设备、服务器与凭据池 | 19 |
| 自动巡检 | 11 |
| 旧巡检历史兼容表 | 8 |
| 文档管理 | 6 |
| 知识中心 | 6 |
| IPAM | 13 |
| 数据治理原生任务、连接及对象标识 | 10 |
| 本地白名单过滤数据 | 1 |

保留字段类型、默认值、字符集、索引和外键，不保留增长中的 AUTO_INCREMENT 计数。自动巡检的窗口/健康度/归属字段、服务器独立密码池、文档额度/ACL 有效期、机房三维位置、HDI 连接对象的精确身份索引均在本文件中。

该文件使用 `CREATE TABLE IF NOT EXISTS`，没有 DROP、ALTER、INSERT、UPDATE、DELETE 或 TRUNCATE。不会清空、替换已有表，但**不会升级或校正已有表的字段和索引**。重复执行安全不等于旧库已经升级完成。

由运维事先创建并确认一个空库，再使用独立批处理连接导入。例如将 `EMPTY_DATABASE` 替换为已确认的空库名；不要使用生产库名，不要启用 `--force`：

```bash
mysql --login-path=rynew-install --database=EMPTY_DATABASE --default-character-set=utf8mb4 --batch < WDF100.0/sql/rynew_schema_latest.sql
```

文件不会创建或切换数据库，也不读取任何应用数据库配置。实测 MySQL `8.4.6`；快照包含 MySQL 8 的排序规则，不声明兼容 MySQL 5.7、MariaDB 或 PostgreSQL。

### 不包含什么

- 无用户、口令、菜单、角色授权、字典、工具元数据、Quartz 作业或其他业务行。因此**仅导入本文件不能直接登录或启用业务**；完整新环境还需经审核的技术初始化数据、应用配置及配套存储/运行组件。
- 不包含历史清洗备份表、回滚台账、文档历史根目录迁移台账，也不会重放历史归属/凭据/数据迁移。
- 不包含外部系统的 PostgreSQL `whitelist` 表、HDI 产品内部表、激活状态、Kettle/NiFi 运行目录或文档文件。`whitelist_postgres.sql` 仅有说明，仓库缺少该外部表的准确结构，不据此猜造建表语句。
- 不为旧小说演示功能猜造仓库没有定义的 `tb_novel` 表。

新空库导入失败时，断开连接并排查；仅可删除本次明确创建且尚未使用的测试/安装库后重新导入。已有业务库不得套用此回退方式。

## 已有数据库升级

| 实际基线 | 入口 | 说明 |
| --- | --- | --- |
| 完整 v4.17.0 | `data_governance_upgrade_20261026_v4_17_0_to_v4_17_2_all.sql` | 新增 10 张 HDI 表；先后运行 `data_governance_check_20261026_v4_17_0_to_v4_17_2.sql` |
| 完整 v4.16.0，尚未完成凭据池迁移 | `support_upgrade_20260929_server_credential_password_pool_v4_17_0.sql` | 先备份、停写及停止扫描，保留加密密钥；升级不接触服务器本身 |
| 完整 v4.4.1 升至 v4.8.3 | `data_governance_upgrade_20260918_v4_4_1_to_v4_8_3_all.sql` | 先核对同名 README 的菜单和文档额度基线 |
| 更早或未知版本 | 按对应模块发布记录审计 | 不盲跑全目录，不把快照或旧整合脚本当成升级入口 |

`support_v1.sql` 和 `support_deploy_all.sql` 仅维护现场融合/巡检范围，不是完整平台初始化 SQL。`ry_20260320.sql`、`quartz.sql` 等历史脚本含 DROP；部分旧迁移还包含数据重写和权限变更，不得直接用于线上重装或回退。

任何真实数据库执行均须另行确认目标、停写窗口、备份、核验及回退方案。本次 SQL 整理没有执行真实业务数据库。

## 来源与维护

来源顺序固定在 [`tools/database/schema-latest.mjs`](../../tools/database/schema-latest.mjs) 的 `sources` 中。每次在全新、禁用 TCP 的独立 MySQL 中执行仓库脚本，随后只读取 `SHOW CREATE TABLE` 生成快照；不会读取本机开发库、线上库、配置凭据或业务数据。

快照头部保留每个来源文件的 SHA-256。知识中心单独与累计脚本二选一，文档目录颜色已并入当前 V1，不重复执行拒绝重入的旧颜色迁移。IPAM 清洗/备份及各模块回滚脚本不进入生成链。原始来源中的种子行只存在于临时隔离库，不输出到快照，也不启动应用或 Quartz。

```bash
node tools/database/schema-latest.mjs --write
node tools/database/schema-latest.mjs --check
node --test tools/database/*.test.mjs
```

测试工具只接受 MySQL 服务端/客户端二进制路径覆盖（`MYSQL_TEST_SERVER`、`MYSQL_TEST_CLIENT`），不接受现成连接地址、数据库或 datadir。默认使用 `/usr/local/mysql/bin`；临时目录归测试拥有，结束时关闭独立 mysqld 并清理。

客户端显式使用 `--no-defaults --no-login-paths --skip-password`，子进程环境剔除 `MYSQL_*`，避免读取或继承本机登录配置。工具捕获 SIGINT/SIGTERM 后先终止并等待自有 mysqld 退出，再删除临时目录；不处理无法捕获的 SIGKILL。[MySQL 选项说明](https://dev.mysql.com/doc/refman/8.4/en/option-file-options.html)、[Node 信号说明](https://nodejs.org/api/process.html#signal-events)。

回归核对来源哈希、全新导入后的列/索引/外键等价、所有表无数据、关键新增字段/索引、重复执行保留已有行、外键检查状态恢复、本机凭据隔离和两种信号中断清理。变更业务结构时应维护独立升级脚本，再更新来源和快照；不能仅改快照以掩盖源码缺少升级路径。
