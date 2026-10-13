# 原 DETL 任务服务身份桥

本扩展只把已验证的 RYNEW 身份交换为原 DETL session。调用原 `HttpUserSessionInfo.storeSessionLoginUserRole`，不重写任务控制器、画布、节点、执行逻辑、许可或工作区权限。默认不启用，也不自动生成映射或管理员。

## 协议（与平台服务端一致）

仅服务器对服务器请求：`POST /detl/_platform/session`，`Content-Type: application/json;charset=UTF-8`。原任务服务应只监听本机；本桥仅接受127.0.0.1/IPv6 loopback，拒绝浏览器Origin/Sec-Fetch-Site、Forwarded/X-Forwarded-For、查询字符串及压缩请求体。

原始UTF-8请求体只允许：

```json
{"subject":"rynew:user:7"}
```

不能由票据传入role、userId、workspace或project等权限字段。最长4096字节，严格UTF-8、重复键/尾随JSON/未知字段均拒绝。

头：`X-RYNEW-Timestamp`（毫秒十进制）、`X-RYNEW-Nonce`（32随机字节的base64url，无padding，43字符）、`X-RYNEW-Signature`（HmacSHA256的小写64位HEX）。签名串为以下各行连接，**最后无换行**：

```text
POST
/detl/_platform/session
<timestamp原字符串>
<nonce>
<raw request body的SHA256小写HEX>
```

HMAC密钥是私有文件中的**原始二进制32–64字节**，两端不trim、不base64解码。每对平台/原任务服务使用独立密钥。票据默认有效60秒，允许最多5秒未来时钟偏差；时间、方法、完整requestURI、body、nonce均被签名覆盖。

nonce在模块私有目录原子持久化，防并发、同进程与重启后短窗口重放；目录带自身owner标记，不接管未知非空目录。保留未完成写入作为已消费记录，最多8192条未清理记录；过期记录可由该模块清除。目录应在同一个交换服务实例的持久卷，不能每次重启换空目录。

成功HTTP200：`{"code":"0","data":{"user":{...原显示字段...},"projectCode":"...","projectName":"...","projectList":[...],"workspaceId":"901","locale":"zh_CN"}}`。workspaceId、user.id和user.userId以十进制字符串返回，避免大Long经过JavaScript Number丢失精度；原UserRole内部仍是Long。顶层locale直接采用明确映射的language，供原页面宿主选择语言。原容器生成的Set-Cookie只由RYNEW后台保存，禁止转发给浏览器。失败HTTP401/503且响应不包含密钥、票据、映射内容或文件路径。失败不能创建新session；已消费票据遇到临时服务问题，调用方应重新签发新nonce。

## 私有配置与明确映射

Spring配置前缀：

```properties
rynew.detl.identity-bridge.enabled=false
rynew.detl.identity-bridge.secret-file=/private-runtime/bridge/signing.key
rynew.detl.identity-bridge.mapping-file=/private-runtime/bridge/users.json
rynew.detl.identity-bridge.replay-directory=/private-runtime/bridge/nonces
rynew.detl.identity-bridge.session-seconds=900
```

可通过对应Spring环境变量配置路径。secret和mapping必须绝对路径、无软链、属于执行用户且无group/other权限（例如0600）；replay目录0700。模块拒绝错误权限，不自行chmod已有配置。密钥、body和用户映射不写日志。启用但配置无效会失败关闭。

`mapping.example.json`刻意为空。真实users中的每项结构如下，值必须来自真实原用户/工程/工作区映射，示意值不可直接作为部署授权：

```json
{
  "subject":"rynew:user:7",
  "userId":73,
  "accountName":"existing-native-user",
  "userName":"existing-native-user",
  "realUserName":"真实映射用户",
  "roleType":5,
  "tenantCode":"existing-tenant",
  "workspaceId":901,
  "projectCode":"existing-project",
  "projectName":"真实工程",
  "language":"zh_CN",
  "projectList":[{"projectCode":"existing-project","projectName":"真实工程"}]
}
```

外层为`{"schemaVersion":1,"users":[...]}`。roleType必须明确填入原1–5编码，示例5为原开发者；不存在默认角色/用户/租户/项目。1–4为原管理角色，只有经授权的明确映射才能配置。未知subject、重复subject、缺失角色、非法字段均拒绝。同一tenantCode内的userId和accountName也必须各自唯一，不能把多个平台subject配置成同一个原任务拥有者或审计身份；不同租户按各自命名空间校验。配置只读载入，变更需受控重启；不会自动创建数据库用户或把任意RYNEW用户变成原管理员。完整服务验收必须验证原工作区权限仍有效。

成功时先作废已有原session再建立新session，防止session固定；原UserRole和UserDetails由原方法写入。桥在调用时绑定真实ServletRequestAttributes，调用后恢复原上下文，不使用默认mock管理员。

## 只读任务归属校验

`POST /detl/_platform/authorize` 使用同一签名头和算法，canonical中的完整路径必须是`/detl/_platform/authorize`。它与session交换共用同一持久nonce池，不能跨接口重用票据。请求只允许：

```json
{"subject":"rynew:user:7","flowIds":["123","9007199254740993"]}
```

flowIds必须是1–100个不重复的正Long十进制字符串，不能使用JSON Number、前导零、负数或超出Long范围。workspace、role等字段不接受。subject只能来自可信RYNEW当前登录用户，原用户与工作区仍取本模块私有映射。

扩展直接注入原`com.hikvision.idatafusion.detl.mapper.EtlFlowMapper`，逐项调用`selectByPrimaryKey(Long)`，要求每个原`EtlFlow.groupId`等于映射workspaceId；不修改原SQL、表、任务、成员或许可。任何一项不存在或越界统一HTTP403 `{"code":"BRIDGE_SCOPE_DENIED","msg":"任务范围未获授权"}`，不返回对象内容或指出哪一项不存在。鉴签、结构或未知映射失败仍为401 BRIDGE_DENIED，原DAO异常为503 BRIDGE_UNAVAILABLE。

全部符合时返回HTTP200 `{"code":"0","data":{"authorized":true}}`，不会建立/更新session。这个结果只证明当前查询时的宿主工作区范围，后续原任务请求仍必须经过原业务许可、工作区及数据权限。网关必须在每次操作前校验原请求真正使用的任务ID（包括解码visualBase64后的flowVo.id），不能采信另附的客户端subject/workspace，也不能把此结果长期缓存为许可。

启用本桥时原EtlFlowMapper必须可注入；没有原DAO则配置失败关闭，不用虚拟映射或成功回退。默认disabled时不创建这两个端点。专项测试的Mapper是原接口动态代理，只允许selectByPrimaryKey并拒绝任何写方法，用于验证控制流；它不代表实际数据库集成已通过。

## 原过滤器装配边界

桥自身只接管精确`/_platform/session`和`/_platform/authorize`，使用HMAC提供这两个服务器端操作的认证；普通`/flow`等请求继续原过滤器链。原EtlFilter、UserDetermineFilter、CSRF、许可/能力与workspace检查必须保留。

原SSO的`CasClientAutoConfiguration`对`/*`注册认证/票据过滤器，且`AuthenticationFilter`还单列在spring.factories。切换由RYNEW承担入口身份验证时，应通过标准`spring.autoconfigure.exclude`对这些原CAS装配作**明确的部署适配**，不使用全路径ignore规则：

- `com.hikvision.sso.client.config.CasClientAutoConfiguration`
- `org.jasig.cas.client.authentication.AuthenticationFilter`
- `org.jasig.cas.client.session.SingleSignOutHandler`
- `org.jasig.cas.client.session.SingleSignOutHttpSessionListener`

部署时须合并原有exclude条目，不能覆盖原有排除项。这是根据原包真实自动配置清单确定的宿主装配候选，不是已验证完整服务启动的承诺；其他原组件若依赖CAS bean须进一步精确适配。保留SSO/CSRF类所在原JAR供已有类型与CSRF使用，不整包删库。尤其`starfish-security-cas-plugin`中有原CSRF实现，不应按名字删掉。没有改变任何许可类或返回假许可成功。

RYNEW后台必须先验证当前用户，再签发subject；交换接口不能暴露到外部网络。后续原API代理仍须逐次做RYNEW权限校验，绑定该用户对应的原session，不能共享管理员cookie；浏览器不能自选上游URL、cookie、工作区或签名头。

## 编译和验证

不下载SDK，不改原JAR。对独立采集的原task-service目录运行：

```sh
python3 build.py --task-service /path/to/original/task-service --output /path/to/bridge-build --test
```

`--java-home`可指向实际JDK11。编译强制`--release 11`；生成扩展jar、独立测试jar和build-evidence.json（包含运行JDK与原SDK摘要）。每次用新的临时classes目录，避免旧class混入。原服务启动时在原classpath基础上加入该扩展jar，Spring读取本扩展META-INF/spring.factories，默认关闭。

JDK测试入口`com.rynew.detl.bridge.IdentityBridgeProof`调用**实际原HttpUserSessionInfo/UserRole/UserDetails**，仅Servlet请求/响应使用接口代理；无数据库、服务器、BIC、许可或native初始化。覆盖跨语言HMAC固定向量、请求绑定、过期/未来票据、防重放/并发/重启、未知/越权字段映射、私有文件与软链拒绝、真实原session写入/旋转、上下文还原、普通任务路径保留过滤链、默认关闭。

本模块完成不等于原管理服务已启动、许可已加载或真实浏览器完成SSO。原cookie生成、完整过滤器顺序、真实原用户/工程/权限、CSRF与前后端集成要在隔离完整服务中另行验收。
