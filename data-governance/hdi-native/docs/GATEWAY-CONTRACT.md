# 原 HDI 任务同源网关契约

此模块只适配 RYNEW 身份、同源传输和原资源，不修改原任务算法、插件、工作区校验或许可校验。默认 `data-governance-hdi.enabled=false`，此时不注册 `/detl` 专用安全链和控制器。

## 会话与原上下文

- `POST /governance/hdi/session` 使用现有平台 JWT 和 `governance:flow:list`，以当前 `LoginUser.userId` 生成原身份交换 subject。成功为平台 AjaxResult，`data` 是原交换上下文加 `frameUrl: /detl/#/task/taskList?appCode=<workspaceId>&flag=1&menuflag=1`。入口只取可信原交换的正数 Long 工作区 ID，拒绝缺失、非法或溢出 ID，不读取客户端 appCode。原前端的新增/编辑/查看新窗口继续使用相同同源安全链。
- 浏览器仅收到随机 `RYNEW_HDI_SESSION` Cookie，`HttpOnly; SameSite=Strict; Path=/detl`；对外地址为 HTTPS 时加 `Secure`。原 SESSION/JSESSIONID、CSRF Cookie 始终留在后端。
- `GET /detl/_platform/context` 返回原协议形状 `{"code":"0","data":{...}}`，上下文来自后端身份交换，不能用前端消息或 URL 参数覆盖。
- 每次 `/detl` 请求都重新查询 Redis 中 `login_tokens:<平台登录UUID>`，校验用户、过期时间和数据流程查看权限。注销、权限撤销、Redis 无法核验时拒绝。会话存于当前 RYNEW 进程，重启后需从平台重新进入，多实例应使用会话粘滞路由。
- 同一有效平台 loginUUID 默认复用原会话、opaque Cookie 和原 CSRF，原列表/编辑多窗口不会互相破坏。并发新窗口只进行一次原身份交换。只有明确提交 JSON `{"renew":true}` 才重新连接并轮换 Cookie；旧窗口在明确重连后需要重新进入。空 body、空对象或 `{"renew":false}` 复用，subject/userId/workspaceId 等额外字段拒绝。不同 loginUUID 或用户不共享；空闲默认 1800 秒，总会话最多 2048 个。

## 来源、范围与原 CSRF

每个资源/API 请求均检查来源。存在 Origin 必须匹配配置的完整 origin；缺失时只接受 `Sec-Fetch-Site: same-origin` 或有效同源 Referer。`same-site`、`cross-site` 拒绝，`none` 且无 Referer 拒绝。

浏览器的 Workspace 必须等于原交换上下文的 workspaceId；projectCode 仅允许上下文 projectCode 或 projectList[].projectCode。插件/实例 URL 的 groupId 和实例查询的非空 groupId 必须匹配工作区；目录的 projectCode/userId 查询参数也校验映射和重复项，该原 params-only POST 不接受 request body。缺失的服务端映射不从浏览器补齐。原 `/detl/` HTML 及其中真实 CSRF meta 原样传递，原声明的 CSRF header 原值透传；不会生成 token、关闭原 CSRF 或替换原权限检查。

## API 边界和当前前置缺口

已核验查询名单为 `DataGovernanceHdiGateway.READ_ONLY`，以及绑定当前工作区 ID 的插件/实例查询。原 standalone 的 getLanguage/getEtlUserInfo、listWorkspace、许可状态、菜单扩展、字段类型字典和健康状态查询是逐项核验的精确 GET 例外，不放通其他 user/system 管理接口。菜单扩展使用原官方配置关闭时返回的真实 404 原样透传，由原页面自己的失败分支处理，不伪造权限成功。原 POST 查询按实际方法处理，不能按 HTTP 动词猜测权限。

任务接口采用明确的操作规则，**没有 flow/flowvisual/flowtemplate/flownode/flowlog 命名空间兜底放行**。既有任务的读取、状态、执行记录及启动/停止/锁定/调试/推送 ZK 均先通过本机原身份桥的只读对象授权：RYNEW 以服务端 LoginUser 生成 subject，使用与 session 交换相同的密钥/随机 nonce，对完整 `/detl/_platform/authorize` 路径和请求体签名，仅发送正十进制字符串 flowIds。原桥用原 EtlFlowMapper 按主键查询并将 groupId 与可信映射 workspaceId 比较，不向 RYNEW 返回对象内容。只有原 `code:"0", data.authorized:true` 才继续调用原任务 API；归属拒绝、验签失败、数据库不可用、协议错误均不会发出后续任务请求。

操作范围详见 [TASK-OPERATION-CONTRACT.md](TASK-OPERATION-CONTRACT.md)。写操作还需同时具备 `governance:flow:list`、`governance:flow:edit`、`governance:flow:test`；GET 形式的原写操作也不例外。原任务权限、工作区权限、CSRF、状态、许可和 ZK 检查仍在原后端继续执行。当前 DTO 保存/发布、新建、批量、删除、日志打包等仍关闭，尚不能声称页面已完成创建到运行的闭环。

**`/workspace/*` 只允许精确 GET listWorkspace，其余均拒绝。** 原 GET getByProduct 实际执行 getByProductAndInitPermission，按 product/default workspace 查找后可自动给当前用户增加原编辑权限，不能视为只读引导。原 addWorkspace 也不向浏览器开放。必须先完成受控的原工作区/权限初始化并验证授权边界，再针对已核验入口扩展策略。主入口采用原 non-DFP standalone TaskList，避免 DFP 专用 getByProduct 引导；网关测试成功不代表原任务页已完成真实业务闭环，也没有伪造 workspace 成功数据。

## 配置与文件

配置前缀均为 `data-governance-hdi`：

| 名称 | 默认值/约束 |
| --- | --- |
| enabled | false |
| task-service-url | http://127.0.0.1:19312/detl；只允许 127.0.0.1、HTTP、端口 1024–65535 和 /detl |
| public-origin | 必填；浏览器访问平台的 http/https origin，不含路径 |
| identity-key-file | 与本机原身份桥配对的独立私有 32–64 字节文件；禁止符号链接及组/其他用户权限 |
| bundle-dir | 原包根目录，包含 bundle-manifest.json |
| host-dir | 原微前端加载实验仅保留 task-host.html、host.mjs、native-task-host.mjs 三个明确文件；standalone 主入口不使用 |
| session-idle-seconds | 1800；允许 60–86400 |
| max-request-bytes | 8388608；允许 1024–33554432，超限 HTTP 413 |
| max-response-bytes | 33554432；允许 1024–67108864 |
| connect-timeout-ms | 5000；允许 100–15000 |
| read-timeout-ms | 30000；允许 100–120000 |

bundle-manifest.json 必须为 engine=original-detl，资源仅来自 files 中 public/ 下明确列出且 SHA-256 匹配的文件。host/bundle 路径及子路径禁止符号链接与路径穿越。不会暴露 task-service/config、JAR、证书或未列出文件。

上游请求强制 `Proxy.NO_PROXY`、不跟随重定向；不转发浏览器 Authorization/Cookie/Forwarded/X-Forwarded 或身份交换签名头。响应保持原 HTTP 状态和原 `code:"0"` 字节；Set-Cookie 仅更新后端 CookieJar。响应禁止缓存，错误不包含任意原响应正文或密钥。

## 验证

Java 17 下执行：

    mvn -f WDF100.0/pom.xml -pl wjdatafusion-manage -am \
      -Dtest=DataGovernanceHdiClientTest,DataGovernanceHdiGatewayTest,DataGovernanceHdiSecurityConfigTest \
      -Dsurefire.failIfNoSpecifiedTests=false test

40 项通过，覆盖身份签名、并发多窗口会话/CSRF 复用、显式 renew、注销不复活、对象签名授权失败不触发原操作、未知写接口拒绝、原 Cookie 轮换与用户隔离、来源、注销与权限撤销、工作区自动授权拒绝、可信工作区原入口、精确 GET bootstrap、真实原 404、项目范围、原 CSRF、查询/GET 变更权限、原响应协议、上传/响应限额、禁止跳转、静态 manifest/hash/路径边界，以及真实 Spring Security chain 下匿名原 JS 被拒和关闭功能后平台链不受影响。HTTP 上游是本机隔离合成服务，不把该测试当作原生产后端或浏览器业务验收。
