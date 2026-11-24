# 离线编辑服务与 Nginx

平台在后台调用本机网关 `127.0.0.1:8082`。用户在浏览器访问平台原有地址，编辑脚本、字体、电子表格和文字编辑器通过同源 `/onlyoffice/` 路径加载。文档服务从 Docker 私网 `http://onlyoffice-gateway:8083` 获取文件并回调保存。引擎使用社区版中文界面和中国地区设置，模板字体选用镜像内已注册的文泉驿正黑。社区版保留引擎的原厂标识及版权信息。

## 已有正常运行的文档服务

升级前在后端宿主机执行 `curl -fsS http://127.0.0.1:8082/healthcheck`，预期回复 `true`。继续使用已有服务的卷、JWT 及内部回调地址。若网关使用其他端口，把后端服务地址和反向代理上游同步改成该端口。受控环境文件权限为 0600；后端启动器必须加载该文件。保留原有可信下载主机，并确认包含每个实际访问平台的主机名或 IP（无端口）。

宿主机运行平台 Nginx 的 `32.73.150.46` 可用 `config/nginx-32.73.150.46.conf` 作为完整候选配置。静态资源根为 `/usr/local/nginx/dist`，API 上游为 `127.0.0.1:8080`，dev/prod 的文档上传均使用精确 location。相对引用的 `mime.types` 应与该配置位于同一目录。

其他站点按现有配置合并两个片段。把 `config/nginx-editor-map.conf` 中的 map 放入 `http`，再把 `config/nginx-office-location.conf` 放入实际处理该站点的 `server`。已有相同 map 或 location 时直接核对现有定义。容器版前端（250）使用 `config/nginx-250-container-location.conf`，其上游通过现有 Docker 业务网络解析为网关，宿主机回环地址在容器内表示容器自身。250 线上已有此代理和文档上传专用 location 时，可原样使用现网配置。

业务入口向下传递带端口的主机名、协议和 `X-Forwarded-Host: 当前主机/onlyoffice`，两层代理都转发 WebSocket 升级头。如果外层负载均衡器终止 TLS，内部代理应只信任该设备，并保留浏览器实际协议与主机。勿缓存命令、JWT、回调、文档数据或 WebSocket 路径。

## 首次安装编辑引擎

配套文件名为 `RYNEW-Docs-9.4.0-linux-amd64-offline.tar`。安装前确认应用服务器架构为 x86_64，已安装 Docker Engine 20.10+、bash、curl、sha256sum，8082 端口可供本次使用。Java 后端需从 Docker 网关可达；若它只监听宿主机回环地址，请先用现场受限内部代理向网关网络提供文件和回调接口，再在 prepare 时填写该内部地址。只在新安装的可写本地目录中解包。

```bash
tar -xf RYNEW-Docs-9.4.0-linux-amd64-offline.tar
cd RYNEW-Docs-9.4.0-linux-amd64-offline
sha256sum --check --quiet SHA256SUMS
bash scripts/load-images.sh
bash scripts/prepare.sh 32.73.150.46 host.docker.internal 8080
bash scripts/deploy.sh
```

第一参数应填写浏览器使用的现场地址。平台通过多个 DNS 或 IP 访问时，将所有准确主机加入生成的 `config/backend-document.env` 白名单。prepare 只在首次运行生成共享 JWT。将这个文件的编辑器配置合并到**后端实际读取的私有环境文件**，保留数据库、Redis、文档目录等其他现有配置。执行平台升级脚本之前，先确认它的后端环境文件路径指向同一文件。

附件使用固定的 linux/amd64 镜像归档和离线 Compose 二进制。Compose 只向宿主机回环地址发布网关 8082，文档容器仅接入内部网络。宿主机前端通过完整候选配置访问网关。新装场景若**前端也在 Docker 容器里**，在核对网络命名后将前端容器接入文档网关的 ingress 网络，并把容器版 location 的上游设为该网络中实际可解析的服务名（本附包默认为 `onlyoffice-gateway:8082`，已有 250 为 `rynew-onlyoffice-gateway:8082`）。引擎私网 8083 只用于后端文档文件与保存回调。

## 验收与恢复

1. 在后端宿主机检查 `http://127.0.0.1:8082/healthcheck`，在用户访问的站点检查 `/onlyoffice/healthcheck` 和 `/onlyoffice/web-apps/apps/api/documents/api.js`，分别预期 `true` 和 HTTP 200 的 JavaScript。
2. 使用正常账号新建临时 Word 和 Excel。浏览器的编辑配置应返回同源 API 路径、中文界面和中国地区，界面出现“编辑器已连接”。检查新 Excel 的“工作表1”。
3. 输入短文字保存，观察服务器版本增加；关闭后重新打开或下载确认文字存在。只有出现修改记录以及内容验证才算保存成功。
4. 检查只读授权文档、其他业务API和上传。若有旧协作会话，切换前用业务正常保存流程确认状态。
5. 平台和 Nginx 配置回退使用主包的回滚步骤。首次安装的引擎可在附件目录执行 `bash scripts/stop.sh`，保留 Docker 卷、文件及私有配置。再次使用同一目录与卷恢复时运行 `bash scripts/compose.sh start`、`bash scripts/verify.sh`。
