# RYNEW v4.4.1 → v4.8.3 累计升级与部署

日期：2026-09-18。起始应用基线 `e19796b`，目标功能基线 `7873ec8`（标签 `v4.8.3`）。
本次资料整理提交另见包内 `MANIFEST.json`。适用于已经完成 v4.4.1 及以前结构升级的现有平台，不能用于空库初始化。

本包交付累计 SQL、只读核验、部署/回滚方案、无凭据配置示例和配套执行组件源码。
不包含应用 JAR/dist、原海康引擎 ZIP、JDK、Docker 镜像、现有私有配置或业务数据。
这些制品按下列清单另行准备；不要把整个开发工作区或 `private/` 目录复制到现场。

## 1. 先区分此次完整升级与已发布文档修复

| 内容 | 完整 v4.8.3 升级要求 |
| --- | --- |
| 平台后端 | 使用 v4.8.3 主线干净构建的 `wjdatafusion-admin.jar`，Java 17 |
| Vue 3 前端 | 使用同一主线提交构建的完整 `dist`，含治理 v4.8.1/v4.8.2 界面改动 |
| 平台数据库 | 执行本包累计 SQL 与只读核验，主要新增治理菜单和权限 |
| 文件上传代理 | 增补本包精确 Nginx location，不全局取消其他上传上限 |
| 原生 Kettle | 启用数据开发时同步 Broker、Linux adapter、3份 Java worker 源码编译结果 |
| NiFi | 仅使用已有 NiFi 治理流程时需要；原生 Kettle 不依赖 NiFi |
| 文档/OnlyOffice | 保留原存储、JWT、回调地址及卷；此次不要求升级或重建 OnlyOffice |

**250 服务器在2026-09-18已发布文档修复，但前端使用基于 v4.8.0 的回移提交 `178d995`。**
它避免了顺带切换治理界面，不等于完整主线 `7873ec8` 的前端。
本次完整升级应重新从主线构建，不能混用这个文档兼容 dist 和“完整 v4.8.3”的交付标识。

## 2. 版本与数据库范围

| 版本 | 平台数据库增量 |
| --- | --- |
| v4.4.2 | Chrome 64 前端兼容，无SQL |
| v4.5.0 | 数据治理目录、工作台及4个操作权限，已并入累计入口 |
| v4.5.1～v4.8.2 | 治理功能、执行组件和前端；配置/运行数据保存在独立私有目录，无新增平台表 |
| v4.8.3 | 无新增表/字段；文档 `max_upload_bytes=0` 表示单文件无限制，不改变个人总空间 |

执行文件位于包内 `WDF100.0/sql/`：

1. `data_governance_upgrade_20260918_v4_4_1_to_v4_8_3_all.sql`：累计入口，含前置核验和事务。
2. `data_governance_verify_20260918_v4_8_3.sql`：升级后只读核验。
3. `data_governance_upgrade_20260918_v4_4_1_to_v4_8_3_README.md`：SQL详细范围、预期结果与失败处理。

不需要再执行旧 v4.5.0 或 v4.8.2 累计入口。已有有效菜单、隐藏/停用状态、普通角色授权和文档额度均保留。
出现 `BLOCKED` / SQL错误时停止，核对前置结构或身份冲突，不使用 `--force` 或跳过报错。
禁止使用 `ry_20260320.sql`、`support_deploy_all.sql`、旧历史归属迁移替代本增量包。

### 数据库操作示例

以下在解压后的包根目录执行；先把连接名和数据库名换成现场实际值。
凭据由 MySQL login-path 或0600客户端配置提供，禁止把密码写在命令行。

```bash
set -euo pipefail
umask 077
set -o noclobber
UPGRADE_DB='实际平台数据库名'
UPGRADE_BACKUP='/实际备份目录/升级前平台库.sql'
mysqldump --login-path=rynew-upgrade --single-transaction --no-tablespaces \
  --set-gtid-purged=OFF "$UPGRADE_DB" > "$UPGRADE_BACKUP"
mysql --login-path=rynew-upgrade --database="$UPGRADE_DB" --default-character-set=utf8mb4 --batch \
  < WDF100.0/sql/data_governance_upgrade_20260918_v4_4_1_to_v4_8_3_all.sql > upgrade-v4.8.3.log 2>&1
mysql --login-path=rynew-upgrade --database="$UPGRADE_DB" --default-character-set=utf8mb4 --batch \
  < WDF100.0/sql/data_governance_verify_20260918_v4_8_3.sql > verify-v4.8.3.log 2>&1
```

备份父目录使用0700权限，备份与日志输出文件必须是本轮新文件；不要覆盖已有备份。
先确认备份可读，再运行升级。SQL前置检查需要读系统表/元数据、创建临时表和插入指定菜单/授权的权限。
Navicat可在选定数据库后**完整运行文件**，不要只执行选中片段；任一步报错停止并断开该连接。
数据库核验通过不代表 Kettle、NiFi 或业务连接已就绪。

## 3. 部署顺序与备份

1. 核实服务器、架构、应用/代理目录、JDK版本、运行用户和磁盘，保存原制品SHA-256。
2. 暂停治理调度和新任务提交，等待正在执行的任务结束。保存所有待核查运行，不自动重投。
3. 备份数据库、应用JAR、完整前端、Nginx和外部配置；另外备份下表中的持久数据。
4. 在目标数据库副本演练SQL，确认重复执行、既有授权及文档额度不变，再执行生产升级和核验。
5. 若启用治理引擎，先按第6节准备配套执行组件，完成隔离烟测；保持业务端点关闭。
6. 优雅停止旧后端，替换匹配JAR，沿用原外部配置启动；验证单进程、登录接口、数据库连接和日志。
7. 增补Nginx精确上传location，语法通过后reload。复制前端hash资源，最后切换index.html。
8. 重新登录，按第8节验收，然后按审批恢复计划；保留备份直到业务验收完成。

| 必须单独保留 | 注意事项 |
| --- | --- |
| 文档storage-root、回收站、OnlyOffice卷 | 不删除、不初始化；核对所有者与可写权限 |
| `support.credential.key`、JWT和Broker token | 沿用现场原值；不能换密钥后期待旧密文仍可读取 |
| `data-governance.storage-dir` | 连接、冻结版本、计划、完整产物、交付记录 |
| `data-governance-kettle.storage-dir` | definitions/files/runs/schedules/schedule-snapshots |
| `data-governance-kafka.storage-dir` | 消费配置、回执、位点/租约核查台账 |
| Broker、operations、adapter journals | 区分已完成、待核查及仍运行的身份，不仅备份数据库 |
| NiFi的配置、证书、流程和各repository | 可选引擎自己的持久数据，不在平台SQL中 |

现有250主机已知路径仅作定位参考，操作前仍需核对：
JAR `/opt/java/wjdatafusion-admin.jar`；前端 `/home/hik/datafusion-vue/dist`；
代理 `/opt/apps/datafusion-nginx/nginx.conf`；前端容器 `datafusion-vue`。
原启动脚本 `/opt/java/start.sh` 会加载现有环境及治理附加配置，不要直接用一条裸 `java -jar` 丢失这些参数。

## 4. 从主工作区构建匹配应用

这些命令在**完整源码仓库**执行，不在仅有升级资料的解压包内执行。

```bash
git status --short
git rev-parse main
git worktree add --detach /实际新建构建目录 main
```

新构建目录必须不存在，切换进入后确认 `git status --porcelain` 无输出。
在该目录的 `WDF100.0` 中使用JDK17执行 `mvn -pl wjdatafusion-admin -am clean package`；
在 `RuoYi-Vue3-master` 中按锁文件准备依赖后执行 `npm run verify:frontend` 和
`node --test src/views/document/__tests__/*.test.js`。内网无依赖缓存时，在受控构建机完成后传入制品，不要求生产机器联网安装。
交付JAR/dist时记录实际提交、干净状态和SHA-256；不要复制含其他任务修改的工作区。

## 5. Nginx与后端上传配置

将本包 `config/nginx-document-upload.location.conf` 增补到现有业务server中，按现场后端地址调整proxy_pass。
保留原server级限制、普通API代理、OnlyOffice虚拟路径和WebSocket配置。
额外存在前置代理时，同样检查这个精确路径；只改最终一层不能保证消除413。

```bash
docker exec datafusion-vue nginx -t
docker exec datafusion-vue nginx -s reload
```

单文件Docker bind mount需要原位更新配置以保持inode；不要移动替换宿主配置后误以为容器已经读到新文件。
必须先验证再reload，失败即恢复本次备份。

新后端已为文档上传注册独立Servlet及前置权限/额度检查；**不需要把全局Spring multipart改为无限制**。
浏览器FormData提供Content-Length；自定义上传客户端也必须提供，未知长度返回411。
个人单文件选项为100MB/无限制，默认100MB；无限制仍受剩余总空间和格式安全约束。
Word/Excel继续使用独立安全解析/编辑保存限制，PDF保留页数/活动内容检查。

`config/application-upgrade.example.yml`是新增配置片段，不是完整application.yml。
文档当前显式环境变量名见该文件注释，旧的backend-document.env.example不能直接当成整份现场配置覆盖。
加密key的环境变量要显式映射到 `support.credential.key`，并继续使用已有密钥。

## 6. 启用原生Kettle时的额外部署

Broker、Linux adapter与3份Java worker源码必须同版本一起更新，不能只换Python或复用旧classes。
具体规则见 `docs/DATA_GOVERNANCE_KETTLE_WORKER.md`、`docs/DATA_GOVERNANCE_KETTLE_LINUX.md` 和 `docs/DATA_GOVERNANCE_KETTLE_API.md`。

准备 Python 3.10+及IANA时区数据、JDK17、rootful Docker。Linux执行镜像锁定amd64官方JRE17；
已有镜像可核对后复用，离线缺失时单独准备 `eclipse-temurin-17-jre-jammy-linux-amd64.docker.tar.gz` 并 `docker image load`。
镜像SHA及配置ID见 `data-governance/kettle-worker/linux/image-lock.json` 和本包离线清单。

在包根目录执行准备命令（路径为示例，先准备受限父目录并核对所属用户）。
prepare目标必须是不存在的新制品目录；不要在正在使用的worker_root上原位prepare：

```bash
python3 tools/data-governance/kettle_worker.py prepare \
  --archive /opt/rynew/offline/kettle6.1.3.0.zip \
  --runtime /var/lib/rynew/kettle/artifacts/v4.8.3 --java-home /opt/rynew/jdk-17
python3 tools/data-governance/kettle_inventory.py \
  --zip /opt/rynew/offline/kettle6.1.3.0.zip --output /var/lib/rynew/kettle/catalog/catalog.json
```

检查旧manifest的`runtimeExtensions`。从原ZIP重新prepare不会自动继承现场额外JDBC，
例如已有批准的PostgreSQL42.7.7驱动，应在新制品目录重新安装核验过的原JAR及旁边同名`.sha1`：

```bash
python3 tools/data-governance/kettle_worker.py install-postgres-jdbc \
  --runtime /var/lib/rynew/kettle/artifacts/v4.8.3 \
  --jdbc-jar /opt/rynew/offline/postgresql-42.7.7.jar
```

不要把运行目录里的`approved-postgresql-42.7.7.jar`直接作为此命令输入；要求原文件名和SHA1侧车。
其他额外扩展按现场原审批及manifest逐项迁移，不把未知JAR混入原引擎classpath。

按 `config/kettle-linux.example.json` 配置独立制品、operations、journals目录，
Broker状态单独使用 `/var/lib/rynew/kettle/broker`，API加密存储也单独配置。
不要改动既有instance_id，不复制Mac进程/PID状态文件去接管Linux服务。
首次部署生成自己的32位hex instance_id；配置文件由controller持有且0600。
本次模板worker_root与prepare目录一致，image使用离线load可识别的完整配置ID；
若调整目录须同步修改模板，不能沿用通用模板的`artifacts/verified-version`示例路径。
容器非root uid/gid需能遍历/读取classes/lib；prepare产生的700/600权限需按实际用户安排检查，不能用递归777解决。

首次执行前先跑无执行计划：

```bash
python3 data-governance/kettle-worker/linux/smoke.py --config /etc/rynew/kettle-linux.json
```

审核镜像、UID/目录和网络计划后，将 `execution_enabled` 设为true，使用同一smoke命令加`--execute`做合成数据烟测。
默认端点为空、network none。通过后再按现场批准的IPv4/TCP白名单放开业务端点，不给任意出网权限。
联网模式要求iptables backend、已有DOCKER-USER及nsenter能力；Docker Desktop/rootless/未经验证的nftables不属于等价部署环境。

```bash
python3 tools/data-governance/kettle_worker.py serve \
  --runtime /var/lib/rynew/kettle/broker --linux-config /etc/rynew/kettle-linux.json \
  --port 19162 --timeout 120
```

上述为前台核验命令。确认后纳入现场现有服务管理器；不另编造会并发启动第二份Broker的服务名。
Broker固定loopback且要求私有Bearer token；后端保持同主机 `http://127.0.0.1:19162` 和实际token-file。
后端执行开关在验证后才启用。`--timeout` 1–3600秒，默认120，属于受控运行预算。

新契约要求输入只放 `${INPUT_DIR}`、输出放 `${WORK_DIR}`；保存/load/capabilities保持断网。
旧定义若引用输出目录下的输入文件，应保存为新版本迁移，不改写历史冻结版本。
运行时区和超时进入journal身份；旧待核查任务只能按原身份恢复，不能重放。
历史250的旧UTC烟测不能替代此版本INPUT_DIR/时区/超时契约验收。

## 7. 已有NiFi流程的可选升级

NiFi路径为独立可选能力：NiFi2.11.0、独立Java21、兼容组件NAR1.2.3。
原生Kettle无须部署NiFi。只在现场使用该能力时处理。

```bash
python3 tools/data-governance/build_compatibility.py --java-home /opt/rynew/jdk-21
python3 tools/data-governance/verify_nar.py \
  data-governance/compatibility/nifi-nar/target/governance-nifi-nar-1.2.3.nar --version 1.2.3
```

构建机需要对应Maven依赖，离线生产只接收已验证NAR。没有活跃线程/队列后，备份引擎状态，按既有NiFi服务正常停止、添加准确版本NAR并启动。
保留旧流程依赖的旧NAR版本；不自动迁移所有流程、不删除旧组件。
本包 `runtime_ctl.py` / `app_dev.py` 等历史文档中的本地fixture工具不是生产一键安装器；不要在服务器执行 `start all` 初始化现有服务。
下载验证依赖PGPy和测试FTP依赖pyftpdlib不是Kettle生产必装依赖。

## 8. 发布后的验收与回退

- 首页与登录接口正常、单个后端进程、代理语法通过、日志无新增启动异常。
- 数据库核验PASS；既有普通角色没有意外扩权，重新登录后按需人工授予治理菜单。
- 文档100MB档拒绝超限，不限档103MiB ZIP/RAR可上传，下载SHA一致；空间不足仍拒绝。
- 普通用户不能修改配额；Word/Excel正常打开/保存、PDF只读预览、OnlyOffice healthcheck正常。
- 治理草稿、旧运行、计划、连接可读取；合成流程完整输出正确；字段检查/保存不隐式运行写入步骤。
- 有Kafka/FTP业务时先用隔离命名空间核对位点、产物和成功门槛，再恢复真实调度。

回退方案见 `ROLLBACK.md`。本次整理和打包不会自动执行生产SQL、部署治理运行组件或重启线上服务。
本轮已有文档上传的验证和发布事实不等于所有治理业务的生产全链验收。
