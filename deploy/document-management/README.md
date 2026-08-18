# RYNEW 文档管理 / ONLYOFFICE 远程部署包

目标主机：`2.57.0.250`。本包不在开发者本机启动 Docker，也不修改现有 80、8080、8081、9000 业务端口。

## 架构与隔离边界

```text
外网用户浏览器
  -> http://139.224.24.77:5555/onlyoffice/
  -> 2.57.0.250:5554（datafusion-vue Nginx）
  -> rynew-onlyoffice-gateway:8082（独立 Nginx 容器）
  -> rynew-onlyoffice-documentserver
  -> http://onlyoffice-gateway:8083
  -> http://host.docker.internal:8080（取文件、保存回调）
```

- ONLYOFFICE Docs 使用官方 `9.4.0` 多架构清单，并固定 manifest digest。
- DocumentServer 不发布任何宿主机端口；独立网关绑定 `8082`。公网只复用现有业务入口的 `/onlyoffice/` 虚拟路径，不修改 1Panel/OpenResty 的 80/443 入口。
- `datafusion-vue` 容器加入 `rynew-document-management_onlyoffice_ingress` 网络，并应用 `nginx-datafusion-external.conf`。虚拟路径代理必须把 `$http_host/onlyoffice` 作为 `X-Forwarded-Host` 继续传递，否则编辑器可能报保存错误 `-85` 或把文档下载地址生成为错误路径。
- DocumentServer 只加入 `internal: true` 网络，不具备互联网出口；文件和回调只能经网关内部 `8083` 转发到 RuoYi 后端。
- JWT 强制开启，且启用 `JWT_IN_BODY=true`；密钥只保存在远程 `.env`，权限为 600。
- 文档元数据、目录、ACL、版本与审计仍由 RuoYi/MySQL 管理；DocumentServer 负责 DOCX/XLSX 渲染、协同编辑和保存回调。
- 生产正式使用 Community 版前必须完成 AGPL 许可评审；需要商业许可、集群、高并发或厂商支持时替换为已采购的 Enterprise 镜像。

## 现场已知端口

2026-08-15 的外部只读核验结果：

| 端口 | 当前服务 | 本方案动作 |
|---|---|---|
| 80 | 1Panel 管理入口 | 不修改 |
| 443 | 1Panel/OpenResty 入口 | 不修改 |
| 5554 | 华东信息融合平台前端 | 仅在发布新前端时重启原容器 |
| 8080 | RuoYi v3.9.1 后端 | 仅作为内部回调目标 |
| 8081 | Kodbox | 不修改 |
| 9000 | MinIO | 不修改 |
| 8082 | ONLYOFFICE 独立网关 | 仅供服务器/容器侧联调；外网统一走 5555 `/onlyoffice/` |

外网映射：`139.224.24.77:5555 -> 2.57.0.250:5554`。SSH 映射 `139.224.24.77:5556 -> 2.57.0.250:22` 只用于运维，不属于业务访问链路。

外部探测不能替代服务器内部 `ss`、Docker、磁盘和内存检查，部署前必须运行预检脚本。

## 远程执行顺序

将本目录放到远程 `/opt/rynew/onlyoffice`，然后仅在 `2.57.0.250` 上执行：

```bash
cd /opt/rynew/onlyoffice
scripts/preflight.sh
scripts/prepare-env.sh
scripts/deploy.sh
```

`prepare-env.sh` 会生成随机 64 位十六进制 JWT 密钥，不会把密钥打印到终端。`deploy.sh` 可从官方仓库拉取固定 digest；完全离线时先 `docker load`，把 `.env` 的 `ONLYOFFICE_IMAGE` 指向已导入标签并设置 `ONLYOFFICE_SKIP_PULL=true`。随后脚本启动独立容器、等待健康检查，并验证 API 脚本和到现有 8080 后端的回调路径。

## 独立网关与公网虚拟路径

Compose 会同时启动 `rynew-onlyoffice-gateway`，将内部测试入口固定为 `8082` 并转发到独立 DocumentServer。外网访问时，需把 `datafusion-vue` 容器加入 Compose 的 `onlyoffice_ingress` 网络，并用本包 `nginx-datafusion-external.conf` 增加 `/onlyoffice/` 代理。变更前备份现有 Nginx 配置，执行 `nginx -t` 成功后再 reload。

配置生效后验证：

```bash
curl -fsS http://127.0.0.1:8082/healthcheck
curl -fsS -o /dev/null -w '%{http_code}\n' \
  http://127.0.0.1:8082/web-apps/apps/api/documents/api.js
curl -fsS http://139.224.24.77:5555/onlyoffice/healthcheck
curl -fsS -o /dev/null -w '%{http_code}\n' \
  http://139.224.24.77:5555/onlyoffice/web-apps/apps/api/documents/api.js
```

正式内网环境应把 `2.57.0.250:8082` 限制为业务网段可访问，或改为单位内网 DNS + 内网 CA HTTPS；不要把 DocumentServer 无限制暴露到公网。

## RuoYi 后端配置

参考 `backend-document.env.example`，将以下值放进远程后端的受控启动环境：

```bash
DOCUMENT_MANAGEMENT_ONLY_OFFICE_ENABLED=true
DOCUMENT_MANAGEMENT_ONLY_OFFICE_SERVER_URL=http://139.224.24.77:5555/onlyoffice
DOCUMENT_MANAGEMENT_ONLY_OFFICE_PLATFORM_BASE_URL=http://onlyoffice-gateway:8083
DOCUMENT_MANAGEMENT_ONLY_OFFICE_JWT_SECRET=<与 /opt/rynew/onlyoffice/.env 完全一致>
DOCUMENT_MANAGEMENT_ONLY_OFFICE_TRUSTED_DOWNLOAD_HOSTS=139.224.24.77,2.57.0.250,onlyoffice-documentserver,rynew-onlyoffice-documentserver
DOCUMENT_MANAGEMENT_STORAGE_ROOT=/opt/rynew/data/documents
DOCUMENT_MANAGEMENT_MAX_FILE_SIZE=52428800
```

两个 URL 不能混用：

- `DOCUMENT_MANAGEMENT_ONLY_OFFICE_SERVER_URL` 是浏览器能访问的编辑器地址。
- `DOCUMENT_MANAGEMENT_ONLY_OFFICE_PLATFORM_BASE_URL` 是 DocumentServer 容器能访问的平台文件与回调地址。
- `DOCUMENT_MANAGEMENT_ONLY_OFFICE_TRUSTED_DOWNLOAD_HOSTS` 必须只列出实际回调下载地址可能使用的精确主机；当前公网虚拟路径会生成 `139.224.24.77` 下载地址，因此需要加入该 IP，不能使用通配或关闭 SSRF 校验。

普通角色要看到“文档管理”入口时，仅按业务需要授予父菜单权限 `2500`，具体文档仍由 `doc_acl` 的 `VIEW`/`EDIT` 控制。目标现场已给 `common` 角色（角色 ID 2）增加 `sys_role_menu(2,2500)`，没有授予新建、删除、共享或下载按钮权限。

安装新后端前，先备份数据库和 `/opt/rynew/data/documents`，再在测试库执行 `WDF100.0/sql/document_management_v1_20260815.sql`。当前生产库和现有 JAR 不应在未经备份、差异核对和回滚演练的情况下直接替换。

## 远程验收

基础设施验收：

```bash
cd /opt/rynew/onlyoffice
scripts/verify.sh
docker logs --tail 200 rynew-onlyoffice-documentserver
```

业务验收按 `TEST_MATRIX.md` 执行，至少覆盖：DOCX/XLSX 新建与打开、两名编辑用户实时协同、只读用户无法编辑、自动保存与最终保存、版本记录、错误 JWT、断网运行和重启恢复。2026-08-15 的公网真实验收已覆盖 DOCX/XLSX、目录/归档、VIEW/EDIT、双用户同步、强制/最终保存及重启恢复；未执行的破坏性恢复演练仍应在正式投产窗口单独完成。

## 离线镜像包

基础验收成功后在远程服务器执行：

```bash
cd /opt/rynew/onlyoffice
scripts/package-image.sh
```

脚本在 `artifacts/` 生成真实镜像 `tar.gz` 和 SHA-256 文件。该文件通常超过 1 GB，执行前须确认磁盘空间；压缩包不包含 JWT、业务文档或数据库数据。

## 回滚

停止测试实例且保留数据、镜像与密钥：

```bash
cd /opt/rynew/onlyoffice
scripts/rollback.sh
```

数据库回滚脚本 `document_management_v1_20260815_rollback.sql` 会删除五张文档业务表，只能在确认永久移除且已备份后人工执行；容器回滚脚本不会触碰数据库或文档文件。

## 官方参考

- [ONLYOFFICE Docker-DocumentServer](https://github.com/ONLYOFFICE/Docker-DocumentServer)
- [ONLYOFFICE 反向代理与虚拟路径](https://helpcenter.onlyoffice.com/docs/installation/docs-community-proxy.aspx)
- [ONLYOFFICE 官方虚拟路径 Nginx 示例](https://github.com/ONLYOFFICE/document-server-proxy/blob/master/nginx/proxy-to-virtual-path.conf)
- [ONLYOFFICE Docs 回调处理](https://api.onlyoffice.com/docs/docs-api/usage-api/callback-handler/)
- [ONLYOFFICE Docs 协同编辑](https://api.onlyoffice.com/docs/docs-api/get-started/how-it-works/co-editing/)
- [ONLYOFFICE Community 与商业版本说明](https://helpcenter.onlyoffice.com/zh/docs/faq/docs-community.aspx)
