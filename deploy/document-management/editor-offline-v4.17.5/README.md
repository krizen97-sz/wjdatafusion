# RYNEW v4.17.5 文档编辑离线升级

适用对象是已部署 RYNEW 的 Linux 现场。主包包含运行用的 Spring Boot JAR 和 Vue 3 完整静态目录。两者来自主线标签 `v4.17.5`（`078744ad`），包含 v4.17.3、v4.17.4 的巡检报告改动。250 已上线的文档优化使用 v4.17.2 的回移基线；本包用于安装完整主线。

## 先确定现场起点

| 现场情况 | 操作 |
| --- | --- |
| 已有 v4.17.2、v4.17.3 或 v4.17.4 的完整平台库，已有正常编辑服务 | 仅使用主包，复用现有 MySQL、Redis、文件目录和编辑器。v4.17.2 至 v4.17.5 无平台数据库增量 |
| 仍运行 v4.8.4 至 v4.17.1 | 先查看 `db/README.md`，按当前已完成的结构版本补齐至 v4.17.2，再切程序 |
| 编辑器未安装，目标为 Linux x86_64 | 先按 `ENGINE.md` 使用独立的编辑器镜像附件安装，再执行以下步骤 |

平台升级需要现场已有 Java 17、Python 3、Nginx、MySQL 和 Redis。部署机直接使用已构建制品，无需 Maven、npm 或公网下载。引擎附包需要 Docker 和 Compose。已有引擎有用户在线时，先保存并安排切换窗口。

## 1. 解压并校验

将主包和随附的外部 SHA-256 清单拷入离线环境。先核对主包的 SHA-256，再解压到本次专用的新目录。以下命令从解压后的 `RYNEW-v4.17.5-offline-upgrade` 根目录执行。

```bash
sha256sum -c SHA256SUMS
python3 bin/ops.py --help
```

`payload/wjdatafusion-admin.jar` 是完整后端，`payload/dist/` 是完整前端。应用与制包提交、JAR 和前端入口摘要记录在 `RELEASE.json`。现场的数据库连接、Redis 认证、平台令牌和凭据加密密钥必须由已有外部配置提供。保留原应用启动参数、活动 profile 和附加配置路径，避免历史加密业务数据失去解密能力。

## 2. 准备站点参数

下面以 32.73.150.46 的宿主机 Nginx 为例，将站点参数和候选配置复制到包外。已有 250 式容器网关的站点应按下表使用其实际配置。站点文件、环境文件和备份目录仅供部署账号访问。

```bash
umask 077
install -d -m 700 /etc/rynew-editor-upgrade /var/backups/rynew-editor
cp -n config/site.example.json /etc/rynew-editor-upgrade/site.json
cp -n config/nginx-32.73.150.46.conf /etc/rynew-editor-upgrade/nginx-candidate.conf
chmod 600 /etc/rynew-editor-upgrade/site.json
```

| 字段 | 填入内容 |
| --- | --- |
| `jar`、`dist` | 当前服务真正加载的 JAR 绝对路径、Nginx 当前静态根目录。32.73.150.46 的已知静态目录为 `/usr/local/nginx/dist` |
| `env_file` | 当前后端启动时实际加载的受限环境文件。若采用 systemd，在服务单元中核对 `EnvironmentFile=`；若使用启动脚本，核对其读取并导出该文件的变量 |
| `nginx_conf` | 当前生效的宿主配置文件。用 `nginx -T` 或现场容器的 `nginx -T` 确认 |
| `nginx_candidate` | 位于包外且已经审核的完整候选配置绝对路径。32.73.150.46 可先复制 `config/nginx-32.73.150.46.conf` 核对。若站点自定义了其他 location、TLS 或 upstream，将本次编辑器片段合入当前配置 |
| `backup_root` | 提前创建的私有备份根，权限 0700。放在包、应用、前端目录之外；预留完整旧 JAR/dist 备份与新制品切换的空间 |
| `commands` | 现场已有的停止、启动、Nginx 语法检查和 reload 命令。每条使用参数数组，首项为绝对可执行路径。示例中 `rynew.service` 仅代表现场服务名 |
| `backend_health_url` | RYNEW 后端的 captchaImage 接口，期望 HTTP 200 且业务 code 200 |
| `editor_server_url`、`editor_health_url` | 后端实际可达的编辑器网关以及其 `/healthcheck`。已知宿主部署为 `http://127.0.0.1:8082`；容器部署按容器网络填入 |
| `browser_path`、`portal_editor_health_url` | 浏览器跟随平台使用 `/onlyoffice`；另填经真实站点入口访问的 `/onlyoffice/healthcheck` |

启动和停止命令需要等待操作完成。停止命令在进程已经退出时也应成功返回，便于在启动故障后回退。对于用脚本管理程序的现场，将参数数组换为现有绝对路径。例如启动命令可填 `["/bin/bash","/opt/java/start.sh"]`。Docker 中的前端需要用容器内 Nginx 的 `-t` 和 `-s reload`。若需校对升级前制品，可在站点 JSON 中增加 `previous_sha256`，按 `jar`、`env_file`、`nginx_conf` 填入现场原文件的 SHA-256。

JAR 交付时已将原开发配置中的连接口令和本机路径改为外部配置。沿用现场原 `application.yml`、`application-druid.yml` 的值即可。如果旧服务完全依赖 JAR 自带配置，应先提取并妥善保存该现场原配置，再配置启动器读取它。至少核对 MySQL、Redis、平台 `token.secret`、`support.credential.key`、文档服务 JWT，以及启用的 Druid 监控和白名单连接。环境变量名和精确属性对照见 `evidence/payload-audit.json`。新建服务应检查运行用户对日志目录和原文件目录的权限。

**先核对编辑器现有私有配置。** 文件和保存回调使用 `document-management.only-office.platform-base-url`。250 上该值为 `http://onlyoffice-gateway:8083`，其他现场以实际容器别名和端口为准。签名密钥须与编辑器一致，下载主机白名单包含用户实际访问的内网域名或 IP。现场原有的文档存储目录、令牌、回调配置和加密密钥继续使用原值。网关应从平台后端可达；编辑器容器须能从内部回调入口读取平台文件。

## 3. 预检和应用升级

```bash
SITE=/etc/rynew-editor-upgrade/site.json
python3 bin/ops.py preflight --site "$SITE"
```

记录运行版本和已审核的备份目录。确认现场数据库基线、已有外部配置加载顺序、JVM路径以及停止/启动命令。预检会检查正在运行的后端及可直连的编辑网关。首次添加同源代理的现场，在 reload 后检查站点入口下的 `/onlyoffice/healthcheck`。若要补装引擎，先完成引擎验收。对接入的站点可在浏览器 Network 检查入口、端口及 WebSocket 目标。

让正在编辑的用户保存文档。确认平台已有对应服务器版本，或已确认没有待保存变化。暂停其他模块正在执行的任务后，在维护窗口操作。升级脚本会生成本轮备份，保留备份路径供回退。

```bash
python3 bin/ops.py install --site "$SITE" --maintenance-window
```

脚本配置浏览器同源路径、后台网关及编辑器扩展开关，使用站点命令切换服务。代理使用同源的 `/onlyoffice/`，须保留虚拟路径转发头、WebSocket Upgrade、现有接口鉴权及文档精确上传 location。单文件 Docker bind mount 的配置应保持原 inode。250 式容器和 32.73.150.46 式宿主机的上游地址见 `ENGINE.md`。

## 4. 业务验收

1. 正常登录，从文档管理新建一个 Word 和一个 Excel，并从真实内网平台地址打开。在 Network 中核对 `editor-config` 返回的 `apiJsUrl` 以 `/onlyoffice/` 开头。编辑器静态资源、iframe 和 WebSocket 均应指向现访问的站点。
2. 分别记录首次打开到“编辑器已连接”的用时，以及关闭后复开的用时。新建 Word 使用编辑器已有的文泉驿正黑，新建 Excel 首张工作表为“工作表1”。界面显示简体中文，日期/地区使用中国设置。
3. 在两份测试文档输入不同的验收文字，等待服务器版本增加。关闭、重新打开，并下载核对内容。普通只读账号验证查看/复制权限，协作者验证同步编辑。
4. 若打开正常但保存失败，优先核对平台服务端至网关的命令路径、编辑服务至 `platform-base-url` 的回调路径、JWT 及签名缓存下载白名单。出现相对路径、跨站或 WebSocket 问题，核对 `X-Forwarded-Host` 是否含正确的站点及 `/onlyoffice` 前缀。

社区版 9.4.0 保留原厂品牌和版权标识，平台编辑页面、加载和异常提示使用中文。第三方扩展在此现场配置中关闭。引擎镜像、网关和本地中文字体的来源与许可见引擎附包。

## 5. 回滚

出现实际文档打不开、保存无法落盘或其他业务回归时，暂停新操作，记录本次备份路径与故障时间。使用同一站点参数和本次备份执行回滚。

```bash
python3 bin/ops.py rollback --site "$SITE" --backup /本次安装输出的备份绝对路径 --maintenance-window
```

脚本会校验原 JAR、环境文件、Nginx 配置，以及会被覆盖的前端文件备份。回退时恢复旧入口和发生修改的原资源，同时保留旧目录里其他文件及方便已打开标签页继续使用的哈希资源。随后核对原入口、登录及原文档编辑/保存。如果从较旧版本单独执行过数据库迁移，应根据当时的数据库备份和迁移记录评估该阶段的恢复。若另行安装了引擎附包，按附包说明处理该实例，保留原有卷和 JWT。
