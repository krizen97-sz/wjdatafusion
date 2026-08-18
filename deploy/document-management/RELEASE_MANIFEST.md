# RYNEW ONLYOFFICE 9.4.0 远程测试包清单

生成基线：2026-08-15

## 固定制品

- 镜像：`onlyoffice/documentserver:9.4.0`
- 多架构 manifest digest：`sha256:e3da62a847b9a5d51a11f73cfea1d9c13c3be3809614490d4edddcf01dcf919b`
- linux/amd64 digest：`sha256:8c114db7648a1654828d33e1f9f3b18e97c4caae6707d34bf04d793d260f7e67`
- linux/arm64 digest：`sha256:c6e8858f573e56272d618d70505d08cc121189b8510cacad248078ad0b3dbcc6`

## 包内容

- `docker-compose.onlyoffice.yml`：JWT、健康检查、日志轮转、持久卷和无外网运行网络。
- `.env.example`：无真实密钥的环境模板。
- `nginx-onlyoffice-container.conf`：独立网关的 8082/8083 转发配置。
- `nginx-datafusion-external.conf`：复用 5554/5555 业务入口的 `/onlyoffice/` 虚拟路径配置，含 WebSocket 与 `X-Forwarded-Host` 路径传递。
- `backend-document.env.example`：RuoYi 文档模块环境变量模板。
- `scripts/preflight.sh`：只读系统、Docker、端口、资源和现有服务核验。
- `scripts/prepare-env.sh`：生成权限 600 的随机 JWT 环境文件。
- `scripts/deploy.sh`：支持在线固定 digest 或预导入离线镜像，启动、等待健康并执行基础验收。
- `scripts/verify.sh`：健康、API 脚本、回调链路、镜像和重启计数报告。
- `scripts/package-image.sh`：在远程导出真实镜像 tar.gz 和 SHA-256。
- `scripts/rollback.sh`：只停止实例，保留卷、镜像、密钥和业务数据。
- `TEST_MATRIX.md`：基础设施、文档、权限、协同、安全与恢复测试矩阵。

## 2026-08-15 现场固定值

- 内网主机：`2.57.0.250`。
- 外网业务入口：`http://139.224.24.77:5555`，映射到内网 `5554`。
- 外网 ONLYOFFICE 虚拟路径：`http://139.224.24.77:5555/onlyoffice`。
- DocumentServer 不直接发布宿主机端口；网关 `8082` 只作为服务器/容器侧入口。
- 浏览器 URL、代理 `X-Forwarded-Host` 与后端精确下载白名单必须同时包含 `/onlyoffice`/`139.224.24.77`，三者缺一会导致文档加载或回调保存失败。

## 明确排除

- 不包含真实 JWT、数据库密码、SSH 凭据、用户文档或线上数据库数据。
- 不自动覆盖现有 RuoYi JAR、前端 dist、Nginx/OpenResty 配置或数据库。
- 不包含未采购的 Enterprise 许可证或镜像。
- 本清单不等同于远程部署成功证明；远程结果以 `QA_REPORT.txt`、Docker inspect、日志和业务测试记录为准。
