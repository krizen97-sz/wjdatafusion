# v4.8.4 压缩包上传错误修复

这两个报错位于不同环境：离线环境返回413，139入口对应ZIP兼容性误判。
本目录的脚本不连接服务器、不修改数据库、不会自动覆盖或重载Nginx。

## 一、离线环境413

已按用户提供的32.73.150.46完整配置生成 `nginx-32.73.150.46.conf`。
原配置没有设置client_max_body_size，Nginx默认1MB；新文件只放开prod-api/dev-api的精确文档上传入口，
保留127.0.0.1:8080上游、主机名、静态目录、普通API及其Host头。
目前没有离线服务器访问通道，配置需在该服务器应用后才能确认实机修复。
先在浏览器Network中记录真实上传URL、时间、HTTP状态、Content-Type和响应正文。
前端通用413提示只表示没有读到业务错误msg，不能单凭这段文字确定是哪一层。

### 1. 找到实际生效入口

在实际接收请求的主机或容器执行 `nginx -T`；Docker示例为
`docker exec 实际前端容器名 nginx -T`。将完整配置输出保存在受限目录内，不公开其中可能的认证头。
检查listen/server_name、include、`/prod-api/`映射、所有client_max_body_size、rewrite、error_page、proxy_intercept_errors和日志。
存在外层代理/网关时，各层都需要检查文档上传路径；修改未命中的server不会生效。

对于此次32.73.150.46环境，可直接审核配套完整配置。先将其放到现有nginx.conf同目录的一个新文件，
用现场Nginx二进制的 `-t -c /绝对路径/新配置文件` 检查（这样mime.types相对引用保持有效）；
确认当前主配置路径后备份并原位替换，最后正常 `-t`、`-s reload`。
如果此后现场配置已变化，先合并两个精确location，不用旧快照整文件覆盖新业务设置。

### 2. 用现场上游生成精确上传配置

找到当前 `/prod-api/` location 的**真实proxy_pass值**，不能照搬139或2.57.0.250地址。
普通、不含rewrite/变量的前缀location可用本工具生成片段：

```bash
python3 render_upload_location.py \
  --proxy-pass 'http://现场现有后端地址:8080/' \
  --output document-upload.location.conf
```

命令中的地址必须替换为现场原配置，保留原值有无尾部斜杠及路径。
已有输出文件时脚本直接拒绝覆盖。非默认公开前缀可传 `--public-prefix /dev-api/`，值以实际Network请求为准。

| 原proxy_pass | 生成的精确location上游 |
| --- | --- |
| `http://backend/` | `http://backend/document/workspace/documents/upload` |
| `http://backend/api/` | `http://backend/api/document/workspace/documents/upload` |
| `http://backend`（没有URI） | 保持该值，Nginx继续转发完整原始URI |
| 含变量、rewrite、复杂URI、鉴权/TLS定制 | 人工保留实际转发规则，不机械套用片段 |

把片段加到**原业务server内部**，保留原普通API和OnlyOffice配置。已有同名精确location时应核对并修改它，不重复添加。
鉴权、CORS、TLS校验/客户端证书、转发头等原location的额外设置须同步保留；精确location不会继承兄弟location的设置。
核心改动只作用于文档入口：`client_max_body_size 0`、`proxy_request_buffering off`、`proxy_intercept_errors off`。
最后一项使后端的额度错误JSON正常返回，避免被替换成笼统HTML错误页。

### 3. 备份、验证、生效

先设置 `umask 077`，在0700备份目录保存本次原配置，禁止覆盖已有备份。
单文件Docker bind mount必须原位更新宿主文件以保持inode，不能rename/unlink原文件后误以为容器已读到新内容。
对比宿主文件和容器内对应文件SHA256，执行**实际实例**的 `nginx -t`，通过后 `nginx -s reload`。
失败则原位恢复本次备份并重新检查；不要为上传功能改变其他网站或全局请求限制。

### 4. 核对后端确实是新版本

后端必须包含并实际加载 v4.8.3及以上的 `DocumentUploadServletConfiguration` 和 `DocumentUploadAdmissionFilter`。
仅换前端或Nginx不充分。检查正在运行的JAR路径/哈希和启动时间，不能仅看磁盘上的新JAR文件。
新版本summary接口含 `maxUploadSize`、`remainingSize`、`maxOfficeUploadSize`、`maxEditorSaveSize`；
应在正常登录后核对，不把匿名401视作完整上传验收。
无需全局取消Spring multipart限制；专用文档Servlet保留权限、Content-Length、个人额度和总空间检查。

### 5. 用正常登录账号验收

- 100MB档：100MiB边界允许，超限拒绝。
- 不限档且剩余容量足够：103MiB有效ZIP/RAR上传成功，下载SHA256相同。
- 剩余总空间不足仍拒绝；JSON413明确说明单文件上限或空间不足时应调整该用户策略，不再盲目放大代理限制。
- 自定义上传客户端必须发送Content-Length；浏览器FormData自动提供。
- 如果仍是HTML413，按同一次请求的时间关联每层代理日志；不要只重复刷新页面。

## 二、139环境ZIP误报

原验证器使用Java ZIP默认UTF-8。合法GBK中文文件名ZIP和传统密码ZIP在真实139入口复现了相同“结构损坏”提示，
UTF-8中文ZIP对照成功；这证明兼容性问题存在，但不能据此认定每个失败文件都完整无损。
v4.8.4仅修改用于管理/传输的ZIP校验；Office的OOXML安全检查独立保留。
真正截断、错误签名、越界路径及过多条目仍须拒绝。

生产更新前备份当前JAR，使用准确提交的干净构建包，优雅重启后对同一组合成ZIP再次上传并核对下载SHA。
此次不需要数据库升级，也不自动调整任何用户的100MB/不限策略或总空间。

参考：[Nginx proxy_pass路径规则](https://nginx.org/en/docs/http/ngx_http_proxy_module.html#proxy_pass)、
[Nginx默认请求体限制](https://nginx.org/en/docs/http/ngx_http_core_module.html#client_max_body_size)。
