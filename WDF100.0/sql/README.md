# 数据库脚本入口

当前应用版本：`v4.17.2`；建表快照更新日期：`2026-11-06`。

## 新库完整初始化

[`rynew_init_latest.sql`](rynew_init_latest.sql) 是与最新结构匹配的完整新库入口：105 张运行表及必要的技术初始化数据，**不是业务库导出，也不是升级脚本**。

目标为 MySQL 8，实测版本为 `8.4.6`；不适用于 MySQL 5.7、MariaDB 或 PostgreSQL。安装期间不要启动应用写入或并行执行其他迁移。

### 初始数据范围

- 仅创建 `admin` 一个用户，ID 为 1，绑定内置管理员角色、一个“平台管理”部门和管理员岗位。邮箱、手机号、头像、登录记录为空，没有测试用户、示例公司或人员。首次登录沿用若依原生初始密码修改提醒。
- 包含若依基础菜单及现场融合、自动巡检、文档、知识中心、IPAM、数据治理、白名单入口和按钮权限；移除源脚本中的外链菜单。旧巡检菜单保留但隐藏、停用，没有旧巡检作业。
- 管理员拥有完整菜单授权；普通角色没有预授菜单。“文档管理”和“现场融合与巡检”是可由管理员按需分配的功能角色，默认不绑定其他用户。
- 包含系统/业务字典、安全及界面默认配置、14 个内置巡检工具的参数元数据、IPAM 默认场景、管理员 100 MiB 文档额度、空的默认知识空间、HDI 平台工作区和初始计数器。
- 唯一启用的 Quartz 作业是 `documentPermissionExpiryTask.expire`，用于已经创建的文档协作权限到期处理。没有扫描、自动巡检、Kafka 拉取或演示任务；空库没有文档和 ACL，因此不会因该作业访问外部编辑器。
- 不带现场、服务器、设备、联系人、IP/网段、文档文件、知识文章、巡检目标/模板/计划/记录、数据源连接、执行记录、密码池或密钥。外部 Office/HDI/白名单数据库等依赖仍须另行配置，不通过初始化 SQL 猜造或启用。

### 安装时设置管理员密码

仓库文件不保存可登录的默认密码或其哈希。导入前必须提供部署者自行设置的 BCrypt 哈希；缺少或格式不符会在创建运行表之前停止。

推荐使用准备工具，它只写本地文件，**不连接任何数据库**：

1. 用密码管理器等安全方式，将本次专用管理员密码存入仓库外、当前用户拥有且权限为 `0600` 的小文件。密码为 12–20 个无空格的可打印 ASCII 字符，至少包含大小写字母、数字、符号中的三类；上限遵循当前若依账号规则。不要把口令放进命令参数、环境变量、聊天或 Git。
2. 运行以下命令，路径示例需要换成实际私有路径。准备工具需要 Node.js 和支持 BCrypt 的 Apache `htpasswd`（macOS 通常已安装，Linux 可用发行版的 apache2-utils/httpd-tools 软件包）。密码仅从文件经标准输入交给 `htpasswd`，不进入进程参数。每次产生带随机盐、cost=12 的哈希，写入不可覆盖的 `0600` SQL 副本；工具拒绝把副本写入任何 Git 工作树。

```bash
node tools/database/init-latest.mjs --prepare --password-file /私有目录/admin-password.txt --output /私有目录/rynew-install.sql
```

3. 由运维创建并核对一个全新空库，使用能查看该库元数据、具备 SELECT、INSERT、CREATE、REFERENCES 和 CREATE TEMPORARY TABLES 等必要权限的安装账户，在独立批处理连接中导入该副本。下例的登录配置和库名需由运维自行设置，不能指向已有业务库：

```bash
mysql --login-path=rynew-install --database=EMPTY_DATABASE --default-character-set=utf8mb4 --batch < /私有目录/rynew-install.sql
```

4. 确认客户端退出码为 0，输出 `INIT_OK`、`initial_users=1`、`built_in_tools=14` 和 `installation_lock_released=1`。发布匹配应用、配置独立数据库账号及必要运行依赖后，使用 `admin` 和第 1 步自行选择的密码登录，再按系统提醒修改。安全处置密码输入文件和安装副本；准备工具不会擅自删除你的文件。

高级用法：可以在同一个新批处理连接中先设置 `@rynew_admin_password_hash`，再执行仓库中的 SQL 全文。只接受 `$2a$`、`$2b$`、`$2y$` 且 cost 为 10–14 的合法 BCrypt 格式。不能把明文、SHA/MD5 或通用默认密码填入该变量。[htpasswd 标准输入与 BCrypt 选项](https://httpd.apache.org/docs/2.4/programs/htpasswd.html)、[Spring Security BCrypt 版本支持](https://docs.spring.io/spring-security/reference/api/java/org/springframework/security/crypto/bcrypt/BCryptPasswordEncoder.html)。

### 安全边界与失败处理

完整入口首先检查密码参数、同库安装锁，以及目标中不存在永久表、视图、存储过程/函数或事件。拒绝重复初始化或向已有结构中混入默认授权；错误会以相应检查名称出现在日志里。不要使用 `--force`、忽略错误继续、只执行选中片段或复用有业务事务的会话。

表结构 DDL 不是跨表事务；初始化数据单独处于事务中。中途失败时应停止、断开连接并查看错误，不得盲目重跑。只有在确认目标是本次专属、尚未使用且无其他写入的新库后，才可由运维删除并重建该库再安装。没有适用于已有业务库的一键回退；禁止整库覆盖、重置已有账号或重新授予线上角色。

哈希也属于部署敏感材料，包含它的安装副本不得提交 Git、公开发送或纳入普通交付包。原始仓库入口没有该哈希，也不读取开发/生产数据源配置。本轮只在自建、禁用网络的临时 MySQL 中验证，不执行真实业务数据库。

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

- 无用户、口令、菜单、角色授权、字典、工具元数据、Quartz 作业或其他业务行。因此**仅导入本文件不能直接登录或启用业务**；需要技术种子数据时应使用上面的完整初始化入口，不要先导入结构再运行完整初始化。
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
node tools/database/init-latest.mjs --write
node tools/database/init-latest.mjs --check
node --test tools/database/*.test.mjs
```

测试工具只接受 MySQL 服务端/客户端二进制路径覆盖（`MYSQL_TEST_SERVER`、`MYSQL_TEST_CLIENT`），不接受现成连接地址、数据库或 datadir。默认使用 `/usr/local/mysql/bin`；临时目录归测试拥有，结束时关闭独立 mysqld 并清理。

客户端显式使用 `--no-defaults --no-login-paths --skip-password`，子进程环境剔除 `MYSQL_*`，避免读取或继承本机登录配置。工具捕获 SIGINT/SIGTERM 后先终止并等待自有 mysqld 退出，再删除临时目录；不处理无法捕获的 SIGKILL。[MySQL 选项说明](https://dev.mysql.com/doc/refman/8.4/en/option-file-options.html)、[Node 信号说明](https://nodejs.org/api/process.html#signal-events)。

回归核对来源哈希、全新导入后的列/索引/外键等价、所有表无数据、关键新增字段/索引、重复执行保留已有行、外键检查状态恢复、本机凭据隔离和两种信号中断清理。变更业务结构时应维护独立升级脚本，再更新来源和快照；不能仅改快照以掩盖源码缺少升级路径。

完整初始化额外验证：安装密码与项目实际 Spring Security BCrypt 编码器匹配；管理员、角色、菜单层级、字典和 14 个工具的完整性；业务/凭据表全空；已有库及重复导入拒绝；私有副本权限和 Git 边界。密码兼容测试需要当前工程的 Java/Maven 依赖，通过 Maven 解析实际类路径，不更换应用密码实现。这些验证不等于完成真实环境发布、Redis/外部编辑器/HDI 配置或浏览器登录验收。
