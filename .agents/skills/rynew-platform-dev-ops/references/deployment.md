# 初始化部署与外部配置

本参考的默认交付是 Java JAR + 两个外部 YAML + 前端 dist + 现有 Nginx。执行现场写入前核对授权目标及版本；只准备手册/配置不代表实际连接现场。

## 1. 先选对数据库入口

以 `WDF100.0/sql/README.md` 为准，核验基线为 MySQL 8，快照测试版本 8.4.6。

| 目标 | 入口 | 关键区别 |
| --- | --- | --- |
| 全新空库、需要能登录 | `WDF100.0/sql/rynew_init_latest.sql` | 105张运行表及技术种子；导入前必须设置本次管理员 BCrypt 哈希 |
| 只要表结构 | `WDF100.0/sql/rynew_schema_latest.sql` | 没有用户/菜单/字典，导入后不能直接登录 |
| 已有业务库升级 | SQL README中与实际起点匹配的模块/累计增量 | 不使用新库初始化，不盲跑SQL全目录 |

完整初始化要求库中无永久表/视图、存储过程/函数及事件；不要先导入结构，再导入完整初始化。仓库不保存通用可登录初始密码或哈希。

新库操作：

1. 安装期间停止应用写入和其他迁移。部署者在 Git 外准备权限0600、本人拥有的密码文件，密码须满足现行账号规则（核验基线为12–20位可打印非空格ASCII、至少三类字符）。
2. 使用仓库准备器生成私有安装副本；它仅写文件，不连接数据库，需要 Node 和支持BCrypt的 `htpasswd`：

   ```bash
   node tools/database/init-latest.mjs --prepare \
     --password-file /受控私有目录/admin-password.txt \
     --output /受控私有目录/rynew-install.sql
   ```

3. 运维创建并确认全新空库，通过专用安装账户和新批处理连接导入；替换库名和本机已配置的login-path：

   ```bash
   mysql --login-path=rynew-install --database=EMPTY_DATABASE \
     --default-character-set=utf8mb4 --batch < /受控私有目录/rynew-install.sql
   ```

4. 退出码0且输出 `INIT_OK`、`initial_users=1`、`built_in_tools=14`、`installation_lock_released=1`。应用就绪后用 `admin` 和自选密码登录并按提示修改，重新分配业务角色。

不使用 `--force`。中断先查错误和已存在对象；DDL无法跨表整体回滚。只有确认是本次专属且未使用的新库，才由运维决定重建；已有业务库没有初始化式回退。私有SQL含哈希，不入Git或普通交付包。

已有完整v4.17.0的累计入口是 `data_governance_upgrade_20261026_v4_17_0_to_v4_17_2_all.sql`，前后配套只读check；旧版本看README的前置链，不能只看前端显示版本。

## 2. 放置目录

```text
/opt/rynew/
  backend/wjdatafusion-admin.jar
  config/application.yml
  config/application-druid.yml
  frontend/index.html 及静态资源
  upload/
  logs/
  start-backend.sh
```

由运行账户创建可写目录。配置文件权限0600，脚本0750。文档、任务归档和私有状态目录按启用模块独立保留。

## 3. 外部 Spring 配置

复制与JAR同提交的配置结构，替换现场地址和密钥；不把仓库中的开发值直接投产。下面展示**键名和占位环境引用**，实际秘密由运维私有机制提供，不粘贴进聊天/命令行。

`application.yml`：

```yaml
server:
  port: 8080
ruoyi:
  profile: /opt/rynew/upload
log:
  path: /opt/rynew/logs
spring:
  profiles:
    active: druid
  data:
    redis:
      host: ${RYNEW_REDIS_HOST}
      port: 6379
      database: 0
      password: ${RYNEW_REDIS_PASSWORD}
      timeout: 10s
token:
  secret: ${RYNEW_TOKEN_SECRET}
support:
  credential:
    key: ${SUPPORT_AES_KEY}
```

`application-druid.yml`：

```yaml
spring:
  datasource:
    type: com.alibaba.druid.pool.DruidDataSource
    driverClassName: com.mysql.cj.jdbc.Driver
    druid:
      master:
        url: ${RYNEW_DB_URL}
        username: ${RYNEW_DB_USER}
        password: ${RYNEW_DB_PASSWORD}
      slave:
        enabled: false
      initialSize: 5
      minIdle: 5
      maxActive: 20
      statViewServlet:
        enabled: false
```

`RYNEW_*` 是此示例明确声明的环境变量；它们通过 `${...}` 被读取，不表示原代码自动识别这些自定义名字。核对端口、库名、连接池、时区和字符集。数据库URL示例结构为 `jdbc:mysql://数据库地址:3306/库名?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia%2FShanghai`；TLS参数按现场数据库要求填写。

`support.credential.key` 会在启动时校验非空，服务用SHA-256派生AES/GCM密钥。新装生成独立高熵秘密；已有环境保留原有效密钥，改掉它会使历史凭据无法解密。Token密钥在多节点必须一致且保持高熵；Redis节点、库和会话配置也要一致。

## 4. 直接启动JAR

脚本示例：

```bash
#!/usr/bin/env bash
set -euo pipefail
APP_DIR=/opt/rynew
CONFIG_DIR="$APP_DIR/config"
test -r "$CONFIG_DIR/application.yml"
test -r "$CONFIG_DIR/application-druid.yml"
cd "$APP_DIR"
exec java -jar "$APP_DIR/backend/wjdatafusion-admin.jar" \
  --spring.config.additional-location="file:$CONFIG_DIR/"
```

Spring参数在JAR路径之后；目录末尾保留 `/`。`additional-location` 叠加包内默认配置，外部同名配置覆盖相应项；druid profile负责加载第二份文件。示例不使用optional，目录或配置缺失应先修正。

```bash
chmod 750 /opt/rynew/start-backend.sh
/opt/rynew/start-backend.sh
```

前台运行直接观察启动日志，停止按Ctrl+C。需要后台时由现场既有进程管理方式托管；简单手动方式可使用 `nohup /opt/rynew/start-backend.sh >> /opt/rynew/logs/backend-console.log 2>&1 &` 并记录真实PID，普通 `kill PID` 优雅停止。

构建来源：干净、确切的main提交/标签，在独立发布worktree执行 `mvn -f WDF100.0/pom.xml -pl wjdatafusion-admin -am package -DskipTests`。默认产物在 `WDF100.0/wjdatafusion-admin/target/wjdatafusion-admin.jar`；跳过测试须单独报告，不能说全量测试通过。

## 5. 前端dist与现有Nginx

在主前端运行 `npm ci`、对应验证和 `npm run build:prod`。核对生产 `VITE_APP_BASE_API` 与代理前缀一致；把 `dist` **目录中的内容**放入 `/opt/rynew/frontend`，确保该目录直接含index.html。

站点配置最小示例（不包含Nginx安装）：

```nginx
server {
    listen 80;
    server_name _;
    root /opt/rynew/frontend;
    index index.html;
    location / { try_files $uri $uri/ /index.html; }
    location /prod-api/ {
        proxy_pass http://127.0.0.1:8080/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    }
}
```

后端在其他主机/端口时改 `proxy_pass`；末尾 `/` 用于去掉 `/prod-api/` 前缀。先 `nginx -t`，成功后 `nginx -s reload`。生产域名、TLS、上传体积和模块长请求超时沿用现场策略；使用文档/HDI时再对齐其需求，不把本示例视为完整公网配置。

## 6. 按启用功能补依赖

| 功能 | 另行核对 |
| --- | --- |
| 普通页面/现场/非消息类巡检 | 无需填写全局Kafka配置 |
| Kafka类巡检 | 模板步骤和目标字段；见自动巡检参考中的实际客户端边界 |
| 文档 | 存储目录、额度、代理/Servlet上传上限；在线Office另配OnlyOffice和回调/JWT |
| IPAM | 当前凭据实现、查看权限与授权扫描范围；见其他业务参考，不凭旧文档新增密钥配置 |
| 原生HDI | 受校验bundle、Core11/Compiler17、私有状态和端点策略；见数据治理参考 |
| 白名单 | 仅本次交付明确启用时核对外部PG及专用Kafka；被排除时不带入包 |

验收顺序：MySQL/Redis → 后端启动 → 验证码和登录 → 前端资源和刷新 → 角色权限 → 每个启用模块的最小业务。回退保留旧制品、有效配置、数据库与文件/任务归档；先暂停新任务，再切回成套前后端。数据库恢复须核对上线后新增数据，不能为回退程序直接覆盖整库。
