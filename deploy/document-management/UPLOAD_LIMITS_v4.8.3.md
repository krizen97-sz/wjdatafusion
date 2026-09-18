# v4.8.3 文档上传与单文件额度

## 配额契约

- 管理员在用户空间管理中，按用户设置 `maxUploadMb=100` 或 `0`（无限制）。
- 数据库存储 `doc_user_quota.max_upload_bytes`；0 表示不设个人单文件上限。
- 新用户默认 100 MB；历史 1–99 MB 配置仍按原值生效，重新保存时需明确选择 100 MB 或无限制。
- 总空间配额不变，上传前和正式写入前均检查，回收站继续占用空间。
- ZIP/RAR 仅作管理与传输，流式复制、签名及容器校验不取消。
- Word/Excel 保留独立安全处理大小和解压/条目校验；PDF 保留页数、安全内容检查和磁盘缓存。
- `summary.maxOfficeUploadSize`、`maxEditorSaveSize` 为实际文档处理边界；不要将它们解释为个人上传额度。
- 无需数据迁移或批量调整用户配额。admin 账号沿用现有配置规则。

## 入口配置

后端 `DocumentUploadServletConfiguration` 用公开 Servlet API 注册精确路径
`/document/workspace/documents/upload`，复用现有控制器、安全链和业务服务。
此路径的 multipart 传输不设置固定大小上限；其他路径继续使用原 Spring
multipart 配置，包括现场外部配置中的 20 MB 或 100 MB 限制。

专用前置过滤器在身份认证之后、multipart 解析之前检查文档权限与请求体大小，
避免不合权限/配额的超大请求先落临时盘。文件大小加 multipart 必需头部允许
固定小额开销，文件实际字节仍由服务层按用户额度二次校验。上传客户端必须
提供 `Content-Length`（浏览器 FormData 自动提供），未知长度请求返回 411。

外层代理必须增加 `nginx-datafusion-external.conf` 中的精确上传 location，
使 `client_max_body_size 0` 仅作用于 `/prod-api/document/workspace/documents/upload`。
`proxy_request_buffering off` 避免代理再次缓存整个大文件。若还有前置代理，
也需核对同一路径。不要全局解除其他应用和 ONLYOFFICE 的请求限制。

前端去掉固定两分钟上传超时，并展示真实已传输进度；传输完成后等待服务端
校验/保存响应，不能把传输 100% 当作保存成功。413 提示明确指向请求入口限制。

## 发布与回滚

1. 核对当前 JAR、前端目录和 Nginx 配置，记录 SHA-256 与当前源码基线。
2. 在 `/opt/rynew/releases/` 新建本次独立备份目录，备份 JAR、dist 和 Nginx 配置。
3. 从干净、已提交的 release worktree 构建。若线上前端落后，使用线上版本加
   文档修复的独立回移提交构建，不把其他模块未验收界面一起发布。
4. 替换后端 JAR 后优雅重启，检查启动日志、健康接口和文档专用 Servlet。
5. 为现有 Nginx 配置增补精确 location，保留其他配置。单文件 bind mount
   需原位更新保持 inode，容器内 `nginx -t` 通过后 reload。
6. 发布匹配的前端资源，保留旧 hash 文件以兼容已打开页面，最后更新入口 HTML。
7. 验收通过前保留全部备份。失败时恢复原 JAR、入口 HTML/资源及代理配置，
   重新执行语法、健康和登录核验。配额字段向后兼容，但旧程序会把 0 解释为默认100 MB。

## 最小验收

- 100 MB 用户：100 MB 边界通过，超出 1 字节拒绝。
- 无限用户：超过 100 MB 的 ZIP/RAR 上传及下载字节一致。
- 无限用户仍不能超出自己的剩余总容量；不同用户的策略互不影响。
- 无权限用户不能上传或修改他人额度；原其他模块上传限制不变。
- ZIP/RAR 错误签名、空文件、Office 越界路径等安全校验仍拒绝。
- 页面下拉选项、额度详情、真实传输进度、失败提示与深色模式可正常使用。

参考：[Nginx 请求体大小配置](https://nginx.org/en/docs/http/ngx_http_core_module.html#client_max_body_size)、
[Spring MVC Servlet multipart 配置](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-servlet/multipart.html)。
