# 原生 Kettle 平台 API

本接口把浏览器编辑的原生转换/作业 XML 保存到独立、按用户隔离的加密目录，并通过本机 worker 调用附件中的原引擎。现有 NiFi 接口、流程和数据表不变；未配置 NiFi 也不影响本接口。保存草稿只进行 XML 安全及结构检查，完整配置校验由用户显式触发。

## 配置与部署边界

新配置前缀为 `data-governance-kettle`，默认不启用执行：

```yaml
data-governance-kettle:
  enabled: false
  worker-url: http://127.0.0.1:19162
  worker-token-file: /private/runtime/kettle-worker/.broker-token
  storage-dir: /private/runtime/kettle-api
  catalog-file: /private/runtime/catalog/catalog.json
```

`storage-dir` 默认是 `${user.home}/.rynew/data-governance/kettle`，另外两个文件路径默认空。整个原生 XML 和输入文件内容使用已有 `CredentialCryptoService` 加密，复用 `support.credential.key`；启动运行仍须按既有方式注入该密钥，不把实际密钥放入配置示例或仓库。

worker URL 只接受字面量 `http://127.0.0.1:<port>`，运行默认端口为 19162。禁止 DNS 地址、userinfo、额外路径、query、fragment、代理及重定向。Bearer token 只从后台配置的普通文件读取，不透传浏览器请求头，不返回 token、token 路径或原始上游错误。测试允许另一个明确配置的本机端口。

目录首次使用时创建，私有目录权限为 0700，记录权限为 0600；记录写入先落临时文件、flush/fsync，再原子替换。独占文件锁阻止两个平台进程同时写同一目录，既有草稿不要求 `enabled=true` 才可读取和编辑。无需数据库变更。

本地联调证据中的 worker token 文件为 `rynew-runtime/data-governance-kettle-worker-v2/.broker-token`，静态目录为 `rynew-runtime/data-governance-kettle-v2/catalog/catalog.json`。平台存储应使用单独的运行目录；不要与 worker 原文件目录、NiFi 存储或其他平台实例共用。

## 通用响应及权限

JSON 响应沿用 `AjaxResult`，下文描述的内容均位于 `data`。真实文件下载直接返回二进制，不包装 JSON。

- 查询与下载：`governance:flow:list`。
- 创建、修改、导入、上传及删除输入文件：`governance:flow:edit`。
- 校验、运行及停止：`governance:flow:test`。
- 所有修改/执行端点的 `@Log` 均关闭请求体和响应体记录。
- 所有定义、输入文件、运行、事件、停止及输出下载，先核对当前用户 owner，再访问 worker。跨用户与不存在资源返回同一错误；管理权限不隐式绕过 owner。

## 接口契约

基础路径：`/governance/kettle`。

| 方法及路径 | 请求 | `data` / 返回 |
| --- | --- | --- |
| `GET /status` | 无 | `enabled,draftStorageAvailable,executionAvailable`，可用时附 `engine,sandbox,protocolVersion`，以及大小/预览上限 |
| `GET /catalog` | 无 | `{steps,jobs,workerAvailable,staticInventoryAvailable,staticDiscoveredCount}` |
| `GET /definitions` | 无 | 当前用户 `Summary[]` |
| `POST /definitions` | `{name,xmlBase64}` | 脱敏后的完整定义 |
| `GET /definitions/{id}` | 无 | `{id,name,kind,revision,nodeCount,runtimeFilename,createdAt,updatedAt,xmlBase64,references}` |
| `PUT /definitions/{id}` | `{name,revision,xmlBase64}` | 新修订、脱敏后的完整定义；修订不匹配错误码 409 |
| `POST /imports` | multipart 字段 `file` | 成功导入的 `Summary[]`；不执行导入内容 |
| `GET /definitions/{id}/files` | 无 | `{id,name,bytes,sha256,uploadedAt}[]` |
| `POST /definitions/{id}/files` | multipart 字段 `file` | 上传文件元信息 |
| `DELETE /definitions/{id}/files/{fileId}` | 无 | 普通成功响应；只删除此定义的输入文件 |
| `POST /definitions/{id}/validate` | 无 | 原生校验结果；不会自动修改已保存的 XML |
| `POST /definitions/{id}/runs` | `{revision,mode,previewStep?,rowLimit?,requestId?}` | 持久化的运行记录，成功提交或失败/未知状态均有 `id` |
| `GET /definitions/{id}/runs` | 无 | 当前用户此定义的运行历史摘要，按创建时间倒序；不逐条轮询 worker |
| `GET /runs/{id}` | 无 | 合并原生快照的扁平运行记录 |
| `GET /runs/{id}/events?after=0` | 非负游标 | `{events,nextCursor,state,workerAvailable}` |
| `POST /runs/{id}/stop` | 无 | 原生停止结果合并后的运行记录 |
| `GET /runs/{id}/files/{filename}` | 原文件 basename | 流式完整文件、Content-Length、Content-Disposition、`X-Kettle-Partial` |

`Summary` 字段为 `id,name,kind,revision,nodeCount,runtimeFilename,createdAt,updatedAt`。`kind` 是 `transformation` 或 `job`。`runtimeFilename` 固定为 `<definition UUID>.ktr` 或 `.kjb`，与中文显示名称分离，重命名不会改变运行引用文件名。

`mode` 为 `run` 或 `preview`；预览必须传真实步骤名称 `previewStep`。`rowLimit` 默认为 20，允许 1–200，只控制预览/观察数据的采样。平台不按此参数截断完整运行的输入行或产物。作业只允许 `mode=run`，要预览子转换时直接对对应转换发起预览。

## 目录是发现、加载及执行三种证据

目录合并原生 `/capabilities` 和独立静态清单。静态清单中同一个 ID、不同 Meta 类仍分别保留；仅当 `kind + id + className` 对上 worker 当前登记时才标记 `loadable=true`，不根据同名 ID 猜测加载版本。

目录条目包括 `id,kind,name,category,className,catalogKey?,nameSource?,classVariantCount?,discovered,loadable,executionSupported,executable,executionValidated,status,defaultXmlBase64?`。其中：

- `discovered`：静态或原生登记中找到。
- `loadable`：当前原生 worker 能构造对应登记类。
- `executionSupported`：遵从 native 对此工具的执行支持声明；Job 没有明确声明时默认为不支持。它与类能否构造分开。
- `executable`：worker 可用、登记类可加载且允许执行，表示可以尝试提交；不是行为验收结论。不能把所有可构造的 Job Meta 都标为可执行，例如只支持 SPECIAL/TRANS/FTP_PUT 的 worker 必须将其他 Job 留为不可执行。
- `executionValidated`：本目录不自动宣称逐工具执行已验收，保持 `false`。
- `status`：`DISCOVERED`、`LOAD_FAILED` 或 `LOADABLE`。
- `defaultXmlBase64`：原 Meta 的配置片段，不包含完整 step/entry 外壳；敏感默认值清空，临时稳定 ID 移除。浏览器创建节点时补 `name/type/GUI`，不能把片段作为完整转换运行。

未启用 worker 时仍可返回静态中文目录，但这些条目不会被标为可执行。接口不会返回约 10 MB 的全量来源图、类字节信息或库路径。

## XML 与秘密字段往返

XML 必须是 UTF-8 文本的 Base64，最大 2 MiB。Base64 让原生 XML、JavaScript、CDATA 和条件表达式通过既有全局 XSS 过滤器，无需修改全局安全策略。

使用禁用 DTD、外部 general/parameter entity、外部 DTD/schema 和 XInclude 的 DOM 解析器。不重建成一个简化步骤模型：原属性、未知节点、CDATA、注释与顺序均保留；XML 声明、空元素写法及属性顺序可由 DOM 序列化规范化，因此不承诺导入前后的字节完全相同。

根节点以及 `step/entry/connection` 添加 `data-rynew-id` 稳定 UUID。密码、secret、token、access/private key 等敏感叶子节点及属性有独立秘密 UUID；`<attribute><code>password</code><value>…</value></attribute>` 这类键值配置也会识别。查询时非空秘密显示为：

```text
__RYNEW_SECRET_<秘密 UUID>__
```

该标记只在旧修订、同一个稳定 owner 元素、相同秘密元素类型/属性及秘密 UUID 下恢复原值。步骤仅重命名不改变稳定 UUID，因此原凭据保留；复制标记到其他节点、其他定义或非敏感字段会拒绝。前端须保留这些属性，不自行修改标记。用户传空值就是清空秘密，绝不把空值解释为静默保留。明确输入新值则更新加密 XML。

任意脚本可能把文字常量当作外部凭据，因此应通过原生密码/秘密配置字段管理真实凭据；本接口不尝试将任意程序文本自动改写为凭据仓库。平台不会把原流程脚本改写成另一种语言或裁剪成安全样本。

## 文件导入与 Job 的子转换

导入接受 `.ktr/.kjb/.zip`。ZIP 最多 20 个 XML 流程、解压内容总额 20 MiB、最多 200 个成员；成员目录穿越和非法 XML 均拒绝。所有候选先完成结构解析，再开始保存，避免混合有效/无效成员时部分导入。每个用户最多 200 个定义。整个导入不调用 worker，不测试原地址，也不执行原流程。

例如 `917552 (1).zip` 内成员不是有效 XML 时，接口明确返回该成员无法解析，不能悄悄换用旁边的 `917552.zip`。实际 XML 的业务缺值可以保存草稿，原引擎校验仍是独立操作。

Job 的 TRANS 条目使用明确的定义绑定：

```xml
<entry data-rynew-id="条目稳定UUID"
       data-rynew-definition-id="当前用户的转换定义UUID">
  <name>运行子转换</name>
  <type>TRANS</type>
  <filename>${WORK_DIR}/转换定义UUID.ktr</filename>
</entry>
```

客户端从当前用户的定义列表选择转换，`filename` 使用 `${WORK_DIR}/` 加其 `runtimeFilename`。保存/导入不会猜测旧电脑路径对应哪个定义；detail 的 `references` 给出 `elementId,name,filename,definitionId,bound` 供界面显式绑定。

Job 校验和运行时，平台核对绑定属于当前用户且类型为转换，将该转换的当前原生 XML 冻结为 `inputFiles`。引用文件名冲突、跨用户引用及非 `${WORK_DIR}/basename.ktr` 路径均拒绝。后续修改子转换不会改变已经保存的运行快照。原生 worker 的 Job 接口为 `/jobs/validate`、`PUT /jobs/{id}`，以及 `POST /runs {jobId,...}`。

上传输入文件仅保留安全 UTF-8 basename，中文名可保留，不能包含目录、控制字符、`.` 或 `..`，最长 240 个 UTF-8 字节。单文件 8 MiB、每个定义及每次运行最多 20 个文件、总额 16 MiB；Job 关联转换也计入此运行总额。文件只从当前定义的自有加密目录读取，传给 worker 的结构为 `{name,contentBase64}`，不接受任意主机源路径。

若应用实例的既有 multipart 请求上限小于导入上限，应在独立本机运行配置中提高 `spring.servlet.multipart.max-file-size/max-request-size` 到能容纳 20 MiB 导入的值。此次源码不改全局上传策略或生产配置。

## 不可变提交与结果未知

提交顺序为：

1. 检查 owner、修订、运行参数及输入总额，生成平台 run UUID。
2. 先持久化 owner、定义/修订、XML SHA-256、加密 XML、加密输入快照和 `PREPARING` 状态。
3. 用 run UUID 作为新的不变 worker transformation/job ID，保存原生 XML 及 Job 附件；不会覆盖以后编辑的定义。
4. 原生校验失败返回 `VALIDATION_FAILED`，不发运行请求。
5. 保存 `SUBMITTING` 后才 `POST /runs`，使用相同 run UUID 作为 worker `runId`。
6. 收到结果标记 `ACCEPTED`，否则区分 `PREPARATION_FAILED`、`SUBMISSION_REJECTED` 和 `SUBMISSION_UNKNOWN`，保留快照与查询 ID。

`requestId` 建议由浏览器为一次逻辑运行生成并在网络重试时保留。同一用户、同一定义、同一 requestId 和相同请求参数返回已有记录；即使之后定义被编辑也不会再次提交。相同 requestId 用不同参数/修订会冲突。系统不自动重发未知提交，只允许 GET 原 runId 核查。上传文件后来变化不会替换已受理请求的快照。

worker 也可能在受理后报告 `PREPARING`，因此平台用 `submissionState` 区分本地准备与原生执行准备。读取运行时可按原 runId 更新状态；上游不可用时返回最后保存的记录及 `workerStateAvailable=false/reconciliationMessage`，绝不将旧快照伪装成当前实时状态。

`GET /definitions/{id}/runs` 从平台持久化记录恢复历史，worker 不可用时仍能查询。摘要包含 `id,definitionId,kind,revision,xmlSha256,state,submissionState,createdAt,updatedAt,mode,previewStep,rowLimit,requestId,message?`，保留 `VALIDATION_FAILED/PREPARATION_FAILED/SUBMISSION_UNKNOWN` 等真实状态；中断时残留的 `SUBMITTING` 显示为 `SUBMISSION_UNKNOWN`。它不包含 XML、加密输入、采样行或原生大快照，也不自动对历史记录发远程请求。用户选择某次历史后再读取单次运行和事件。

事件和快照会屏蔽 XML 及关联子转换中已知的秘密值、明显秘密键和赋值日志，不返回 worker 私有目录、classpath 或原 XML。下载仍是完整二进制流，经过 owner 检查，保留原始文件内容、长度和 partial 标识。

## 验证

专项测试：

```bash
mvn -f WDF100.0/pom.xml -pl wjdatafusion-manage -am \
  -Dtest=DataGovernanceKettleApiTest,DataGovernanceKettleClientTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

显式本机 broker 烟测（只使用新建合成数据，不读取或运行旧附件）：

```bash
mvn -f WDF100.0/pom.xml -pl wjdatafusion-manage -am \
  -Dtest=DataGovernanceKettleApiTest,DataGovernanceKettleClientTest,DataGovernanceKettleLiveTest \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -Dkettle.live=true \
  -Dkettle.tokenFile=/private/runtime/kettle-worker/.broker-token test
```

本轮已运行验证包括：未知 XML/CDATA/注释往返、XXE 拒绝、凭据遮蔽与重命名/清空/跨元素拒绝、修订与跨 owner、未知提交/重复提交不重执行、冻结 XML 与输入、ZIP 失败不替换、Job 同 owner 关联、目录发现/加载区别、loopback 及重定向拒绝，以及真实原引擎 `CSV → Script → File` 经 Java 网关校验、运行、事件和完整产物下载，输出包含合成行 `ALICE!`、`BOB!`。

这证明 Java 服务与本机 broker 主链连通。生产服务、远程地址、旧原流程业务、全部插件行为、Job 的真实外部交付和浏览器交互验收仍由各自专项证据覆盖；本提交不把 mock Job 编排当成真实 FTP 交付证明。
