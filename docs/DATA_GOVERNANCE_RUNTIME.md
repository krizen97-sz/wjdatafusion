# 数据治理本地隔离运行环境

## 范围

本运行环境供数据治理模块本地开发与集成测试使用。它不连接附件中的 Kafka、FTP、数据库，不执行海康 Kettle 包，不覆盖现有 MySQL 数据目录、Redis 6379 或系统 Java 17。运行程序、数据、凭据和日志全部保存在仓库外的私有目录。

2026-09-09 实际准备的根目录：

```text
/Users/krizen/Documents/Code/projects/2026projects/rynew-runtime/data-governance
```

根目录与私有子目录权限为 700；认证文件权限为 600。`runtime.json` 标明所有权及独立 Java/NiFi 路径；`services.json` 保存本任务进程的 PID、启动时间与完整命令身份。不要复制这两个状态文件去“接管”其他运行环境。

## 已准备的组件

| 服务 | 版本与接口 | 隔离边界 |
| --- | --- | --- |
| Apache NiFi | 2.11.0；`https://localhost:9443/nifi/` | HTTPS 只绑定 `127.0.0.1:9443`；管理接口 `127.0.0.1:52020`；独立配置、日志与存储 |
| Eclipse Temurin | 21.0.12.1+1，macOS aarch64 | 解压到运行根目录的 `jdks/`，不安装系统 JDK、不更改全局 JAVA_HOME |
| MySQL | 已有本机二进制 8.4.6；`127.0.0.1:13306` | 新建 `mysql/data`、私有 `mysql/mysql.sock`；X Plugin 关闭；初始化与原实例分开 |
| Redis | 使用本机已安装二进制；`127.0.0.1:16379` | 独立配置、数据目录、密码；现有 6379 不动 |
| PostgreSQL | 已有 Homebrew 17.8；`127.0.0.1:15432` | 新建 `postgres/data`；SCRAM 认证，仅本地 TCP |
| FTP fixture | pyftpdlib 2.2.0；`127.0.0.1:2121` | 独立 Python venv 与 `ftp/files`；被动端口 22100–22109，仅本机测试 |
| 本地 HTTPS 网关 | Node 内置 HTTPS；`127.0.0.1:10443` | 静态 dist、RYNEW API 和 NiFi 同源入口；独立私有 CA，不改变系统信任 |

PostgreSQL 私有目录路径较长，macOS Unix socket 路径最大 103 字节；本环境显式关闭 PostgreSQL Unix socket，仅通过 SCRAM 认证的 loopback TCP 访问。MySQL 的私有 socket 已实际连接成功。

NiFi 使用随机单用户身份仅供本地集成验证，**不是最终多用户权限模型**。后续原生 NiFi UI 的正式多用户授权、同源代理与发布身份仍需独立实现，不能把当前测试凭据分发到前端。

## 制品校验与初次准备证据

`tools/data-governance/artifact-lock.json` 固定 NiFi 2.11.0 与独立 JDK 21 的下载 URL、完整散列和签名指纹。NiFi 来自 [Apache 官方下载页](https://nifi.apache.org/download/)，JDK 来自 [Adoptium 官方发布](https://github.com/adoptium/temurin21-binaries/releases/tag/jdk-21.0.12.1%2B1)。

实际已完成：NiFi SHA-512、JDK SHA-256，与官方清单一致；两份 detached OpenPGP 签名均用官方 HTTPS 发布的公钥验证通过。验证工具为局部 venv 内 PGPy 0.6.0；没有修改全局 GPG keyring。此处证明文件散列与签名匹配，不声称独立 Web-of-Trust/撤销状态审计。

复核现有下载文件：

```bash
DG_RUNTIME_ROOT=/Users/krizen/Documents/Code/projects/2026projects/rynew-runtime/data-governance
"$DG_RUNTIME_ROOT/tools/pgp-venv/bin/python" tools/data-governance/verify_artifacts.py --runtime-root "$DG_RUNTIME_ROOT"
```

仅散列检查可使用系统 Python 加 `--hash-only`；该选项不声称验证签名。源代码中有验证工具与 FTP 依赖版本文件，均安装在运行根目录自己的 venv。

本次初次下载、解压、随机凭据/证书准备过程及证据保留在运行根目录 `bin/`、`evidence/` 与 `private/`；源码脚本管理已经准备好的 NiFi 环境，并可初始化本任务缺失的 MySQL/PostgreSQL/Redis/FTP fixture。**当前不是一个从空机器自动安装全部系统依赖的一键安装器**；缺少 `/usr/local/mysql`、Homebrew PostgreSQL/Redis 或局部 FTP venv 时会失败，不会自动 sudo 或全局安装。

## 私有配置交接

程序应在服务器侧读取文件，不把凭据复制到聊天、Git、前端配置或普通日志。

| 文件（相对于运行根目录） | 用途 |
| --- | --- |
| `private/nifi-credentials.json` | `baseUrl`、`username`、`password`、`caCert` |
| `private/nifi-ca.pem` | 本地 NiFi 自签名服务器证书；客户端通过它验证 TLS 与 localhost SAN |
| `private/mysql-client.cnf` | MySQL CLI 使用 `--defaults-file`，通过私有 socket 认证 |
| `private/mysql-credentials.json` | 后端测试数据库连接参数 |
| `private/redis-credentials.json` | 独立 Redis 连接参数 |
| `private/postgres-credentials.json`、`private/pgpass` | 独立 PostgreSQL 参数与 CLI 密码文件 |
| `private/ftp-credentials.json` | FTP fixture 用户及限定文件根目录 |

MySQL 初次启动使用一次性私有密码设置 SQL；认证成功后健康脚本移除该文件并清除初始化日志中的临时密码行。禁止把 `private/`、数据库目录、NiFi 二进制、Python venv、下载包或运行日志加入源码提交。

## 启动、状态、健康和停止

在源码 checkout 根目录执行；以下均只操作指定、带正确 owner 标记的运行根目录：

```bash
DG_RUNTIME_ROOT=/Users/krizen/Documents/Code/projects/2026projects/rynew-runtime/data-governance
python3 tools/data-governance/runtime_ctl.py --runtime-root "$DG_RUNTIME_ROOT" status all
python3 tools/data-governance/runtime_ctl.py --runtime-root "$DG_RUNTIME_ROOT" health all
python3 tools/data-governance/runtime_ctl.py --runtime-root "$DG_RUNTIME_ROOT" start ftp
python3 tools/data-governance/runtime_ctl.py --runtime-root "$DG_RUNTIME_ROOT" stop ftp
```

服务可选 `nifi`、`mysql`、`redis`、`postgres`、`ftp` 、`gateway` 或 `all`。`start all` 跳过仍匹配的本任务进程；`stop all` 会停止全部上述本地测试服务，执行前要确认没有本任务正在进行的联调。

安全约束：

- 不按端口杀进程，不调用 `killall`，不会清理未知数据库目录。
- 开始监听前检查端口；处理 TIME_WAIT，同时拒绝占用中的 listener。
- 停止前核对 PID、启动时间、完整命令身份和私有路径；PID 重用、未知进程、身份不符一律拒绝。
- NiFi/PG 使用自己的正常停止协议；其他 fixture 使用 SIGTERM；停止不能确认时报告失败，不自动强杀。
- 新进程使用独立 session，避免启动工具退出后把 NiFi 子进程一起终止。
- 状态表示已识别进程；健康另外执行真实认证。NiFi UI HTTP 200 不等于浏览器完整业务体验验收。

## 本地验证

安全回归：

```bash
python3 -m unittest discover -s tools/data-governance -p 'test_*.py' -v
```

FTP 真实二进制读写与改名回归：

```bash
python3 tools/data-governance/smoke_ftp.py --runtime-root "$DG_RUNTIME_ROOT"
```

该测试只在本任务 fixture 内创建随机 `runtime-smoke-*` 文件，执行 STOR、改名、RETR 字节对比并删除自己的文件，不使用任何业务附件内容。

当前验证结果：11 项安全测试通过；NiFi 带本地 CA 的认证 API 返回 2.11.0、UI 返回 200；MySQL/PostgreSQL 认证版本查询通过；Redis 认证 PING 通过；FTP 认证 NOOP 和 31 字节二进制写读/改名/清理通过；FTP 的停止、重启与重复启动通过。其余正在联调的服务没有为验证脚本而重启。

这些结果是运行底座验收，不代表 RYNEW 菜单/登录已完成、不代表 Kafka 已安装、也不代表原 ETL 两条业务或海康扩展已等价运行。


## 同源 HTTPS 验收网关

源码：`tools/data-governance/local_gateway.mjs`，仅使用 Node 内置模块。当前指定 dist 是：

```text
/Users/krizen/Documents/Code/projects/2026projects/rynew-worktrees/data-governance-browser-etl-v1/RuoYi-Vue3-master/dist
```

网关只监听 `127.0.0.1:10443`，接受 Host `localhost:10443` 或 `127.0.0.1:10443`，不新增 HTTP 监听和全局 DNS/信任配置。

| 路径 | 转发/服务行为 |
| --- | --- |
| `/prod-api`、`/dev-api` 及其子路径 | 代理到 `http://127.0.0.1:8083`，仅去掉匹配的前缀，保留方法、查询和请求体 |
| `/nifi`、`/nifi/`、`/nifi-*` | 代理到 loopback NiFi 9443，验证 `private/nifi-ca.pem` 和 localhost SAN |
| 其他静态路径 | 只读取指定 dist；禁止 JSON、私有目录、点文件、证书/配置与路径穿越；不跟随逃出 dist 的符号链接 |
| `/__gateway_health` | 返回合成健康 JSON，只说明网关与 dist 是否准备好；不是读取静态 JSON 文件 |

合法后端/NiFi API 的 JSON 响应正常透传；“禁止 JSON”指网关静态文件读取。静态文件支持 GET/HEAD；SPA 回退仅适用于 GET、Accept 包含 text/html、没有文件扩展名的路径。dist 尚未构建时静态请求返回 503，构建出现后自动可用，不需重启网关。网关不是 Vite 开发服务器，不提供 HMR/任意 WebSocket 转发。

NiFi 请求保留用户自己的 Authorization、Cookie 与 Request-Token，响应保留原始 CSP、X-Frame-Options、Set-Cookie 和 CSRF 行为。网关清除外来代理身份头及转发头，再设置真实 `X-ProxyScheme=https`、`X-ProxyHost`、`X-ProxyPort=10443`；不注入管理员令牌，不发送伪造的 `X-ProxiedEntitiesChain`。外部路径没有增加额外上下文前缀，所以不发送 X-ProxyContextPath。

NiFi 已在一次授权的维护窗口中追加以下 proxy host（保留原 9443 入口）：

```properties
nifi.web.proxy.host=localhost:9443,127.0.0.1:9443,localhost:10443,127.0.0.1:10443
```

本轮同一窗口安装 `governance-nifi-nar-1.0.0.nar`，SHA-256 为 `82c602e6dd3dfb021ea4663b76879f94f5c649526ac713954c8363c4a343b88c`。目标原先无同名 NAR；配置备份和安装记录位于 runtime 的私有备份/evidence 中。后续修改 NiFi 白名单或替换 NAR 要协调该测试实例中的进行中工作，不能因为网关代码变更擅自重启 NiFi。

TLS 文件位于 `private/gateway-ca.pem`、`gateway-ca-key.pem`、`gateway-cert.pem`、`gateway-key.pem`，均为 600。CA 与叶子证书采用不同 DN，叶子 SAN 为 localhost 与 127.0.0.1。证书准备命令不会修改系统钥匙串：

```bash
python3 tools/data-governance/prepare_gateway_tls.py --runtime-root "$DG_RUNTIME_ROOT"
```

初次运行需要将源码网关复制到 `$DG_RUNTIME_ROOT/bin/local_gateway.mjs`，并在运行根目录 `gateway.json` 中配置 script、distRoot、node。当前环境已经准备好；`runtime_ctl.py start/stop/status/health gateway` 可管理它。升级网关时，先用 owner/PID 校验安全停止 gateway，再更新这个副本并启动；不要以改配置为由重启其他五个服务。

也可前台运行源码以排查问题（启动前确认 10443 空闲）：

```bash
node tools/data-governance/local_gateway.mjs --runtime-root "$DG_RUNTIME_ROOT" --dist-root /Users/krizen/Documents/Code/projects/2026projects/rynew-worktrees/data-governance-browser-etl-v1/RuoYi-Vue3-master/dist
```

回归命令：

```bash
node --test tools/data-governance/local_gateway.test.mjs
```

12 项回归覆盖 API 前缀/请求体、用户认证与 CSRF 保留、拒绝伪造身份头、TLS 验证失败、dist 缺失、私有文件/JSON/路径穿越/符号链接、SPA 回退边界、静态 HEAD 与 Host 限制。真实网关已验证：`/` 与 `/nifi/` 为 200、NiFi 登录 201、认证 about API 为 200 且版本 2.11.0，NiFi 原 CSP/SAMEORIGIN 保留；私有 JSON 请求为 403；`/prod-api/captchaImage` 与 `/dev-api/captchaImage` 均为 HTTP 200、业务 code 200。这些 HTTP/TLS 验证不代替浏览器登录及嵌入画布完整交互验收。
