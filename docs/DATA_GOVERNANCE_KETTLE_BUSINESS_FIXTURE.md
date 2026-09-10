# 原 18/24 步业务图的隔离夹具

本目录包含离线准备器、产物校验器及经过单独授权的本地执行 runner。离线准备命令本身不运行原图、不联网；后续真实验收已完成两条原业务转换，违法图完整交付通过，普通图发现原 FTP 每次最多 80 份导致部分交付。结果与边界见下方“真实验收结果”。

脚本只读三个原 ZIP：`851987.zip` 的 18 步普通图、`851988.zip` 的 2 项普通作业，以及 `917552.zip` 的 24 步违法图和 3 项作业。不会修改附件，也不拿可解析 ZIP 替代 `917552 (1).zip` 的未支持二进制成员。生成的 XML、消息和 SQL 必须留在 Git 外的私有新目录，目录权限 700、文件 600；已有证据不覆盖。

## 原生配置的保留与环境替换

| 范围 | 执行副本允许改变的字段 | 保留的原逻辑 |
| --- | --- | --- |
| PostgreSQL connection | 名称、server、port/PORT_NUMBER、database、username、password、tablespace | 原 POSTGRESQL 类型与连接选项 |
| DBLookup | connection 引用 | 表、键、条件、返回列、默认值、缓存、比较类型全部保留 |
| KafkaConsumer | TOPIC、group.id、zookeeper.connect | LIMIT、TIMEOUT、offset reset、auto commit、STOPONEMPTY 元素存在性全部保留 |
| KafkaProducer | TOPIC、metadata.broker.list | FIELD、DefaultEncoder 和其他属性全部保留 |
| TextFileOutput | file/name 的目录改为 WORK_DIR | 原 basename、分隔符、编码、字段顺序、分片、定时落盘、rename 和表头全部保留 |
| TRANS | filename 改 INPUT_DIR；可绑定导入后 definition UUID | 原参数、执行和成功条件配置 |
| FTP_PUT | 主机、端口、账户、目录；清空旧代理 | 原 wildcard、binary、remove、rename、主动/被动模式和控制编码 |

42 个 step 逐一计算排除上述环境字段后的摘要，执行副本与原附件必须一致；原 XML 中未知配置、Script、JsonInput、FilterRows、SwitchCase、SelectValues、Constant、复制数和连线均保留。当前所有 copies 都为 1。原始密码标签清除，换为本地夹具密码或显式占位符；默认占位状态不能直接称可运行。原 Script 的 URL 匹配/替换字面量保存在私有执行副本中，合成消息只用 `urn:rynew:fixture:*`，不会请求图片地址，也未覆盖那些私有地址的匹配分支。

固定连接为 PG `127.0.0.1:15432`、Kafka `127.0.0.1:29092`、ZooKeeper `127.0.0.1:22181`、FTP `127.0.0.1:2121`。库名、topic、group 和 FTP 子目录都带新夹具 slug。SQL 要求新建专属数据库，检查 `current_database()`，使用 CREATE TABLE，无 DROP/TRUNCATE/覆盖旧表；脚本只生成 SQL，不创建数据库或执行它。

## 原消息字段与五张表

普通消息根为 `vehicleRcogResult`，违法消息根为 `vehicleAlarmResult`，均为数组。当前夹具每条消息只有一个 result 和一个 target，并额外带 `_fixture.id/case` 用于检验遗漏、重复、错分支和未知字段保留。

| 原 JsonInput 字段 | JSON 路径（R 表示相应消息根） |
| --- | --- |
| crossingIndexCode、recognitionSign、platePicUrl、vehiclePicUrl1…6 | `$.R[0].targetAttrs.<字段名>` |
| targetPicUrl | `$.R[0].targetPicUrl` |
| 普通 plateNo / 违法 value | `$.R[0].target[0].vehicle.plateNo.value` |
| 普通 facePicUrl | `$.R[0].targetAttrs.facePicUrl`，原配置 type=None，未简化为 String |
| 违法 cameraName、alarmType、passTime | `$.R[0].targetAttrs.<字段名>` |
| 违法 targetSubUrl | `$.R[0].target[0].targetSubUrl` |

全部原路径在每个合成消息中存在，包括显式 null 图片场景，未开启 ignoreMissingPath 或改动原 JsonInput 容错开关。车牌、摄像机及告警类型全部为 TEST/夹具值，无真实业务记录。

| 原表 | 输入→数据库键 | 返回字段/规则 |
| --- | --- | --- |
| redlist（普通） | plateNo→plateno | id；仅 `1` 丢弃，其他 id 和未命中继续 |
| xc_cross_csd_status（两图） | platformIndexCode→platform_index_code；crossingIndexCode→cross_index_code | push_status、cross_external_code |
| xc_local_sync_cross（两图） | crossingIndexCode→index_code | crossing_id 未命中默认字符串 `0`；region_external_code 未命中 null |
| qiuji（违法） | cameraName→name；alarmType→alarmtype | id；仅 `1` 丢弃 |
| whitelist（违法） | value→vehicleplate；数据库 alarmtype `IS NOT NULL`，第二个输入字段名原本为空 | id 重命名 record；仅 `2` 原样写回 Kafka |

违法图有四次 DBLookup，普通图有三次，两图合计涉及五张表。夹具采用 TEXT 列匹配原返回类型，组合键保持唯一，避免原配置未排序且允许多匹配造成不确定结果。`platformIndexCode` 从原 Constant 读取，生成对应 SQL 值，不改 Constant 或将其猜成新值。

状态分支须按原图区分：普通图 `push_status=1` 与默认分支均继续查询本地映射；违法图仅 `1` 继续，默认分支丢弃。两图 recognitionSign 仅 `0/1` 继续。脚本将外部卡口码、crossingId 和区域码写回消息 JSON；缺失映射时的默认值也列入夹具。

违法日期脚本去掉 `+08:00`，将 `T` 换为空格，截取前 19 字符，再以斜线日期解析，与固定 `2019/12/24 10:22:25` 比较，必须 **严格晚于**。不是相对当前时间的窗口。夹具包括前一秒、同秒、后一秒和无效日期；保留原 Script Boolean→FilterRows 常量 `Y` 的实际组合；本次违法原图已证实日期边界和无效日期三条均被原规则丢弃。

## 消费初始状态与期望计数

| 图 | 原消费者配置 | 合成输入 | 期望结果 |
| --- | --- | --- | --- |
| 普通 18 步 | largest、auto.commit=true、LIMIT=10000、TIMEOUT=60000、STOPONEMPTY 存在 | 9 类场景，首个接受场景补足 10000 条 | 文件 9998，丢弃 2 |
| 违法 24 步 | smallest、auto.commit=false、LIMIT=200、TIMEOUT=60000、STOPONEMPTY 不存在 | 15 类场景，首个接受场景补足 200 条 | 文件 192，丢弃 7，Kafka 原样回写 1 |

普通新 group 无 offset 时，预灌消息后再以 largest 启动会跳过这些消息。验收约定在**唯一专属夹具 group/topic** 上预建 partition 0 的 offset 0，然后预灌 10000 条；manifest 给出精确 ZooKeeper 路径，但生成器不写该路径。这是夹具初始状态，不能声称覆盖无 offset 的冷启动。三个 topic 均为新建单分区 topic，违法也使用唯一 group 和 200 条预灌消息。不修改原 XML 来规避这条规则，不触碰生产 group。

`*-messages.ndjson` 每行是 `{key,caseId,message}`，其中 message 为应发送给原 KafkaConsumer 的完整 JSON 字符串。`*-expected.json` 列出每一场景的路线、数量和映射结果，状态固定为 EXPECTED_NOT_EXECUTED。普通涵盖两种合法 recognitionSign、二次识别、redlist 1/其他、状态 0/缺失、本地映射缺失及 null 图片；违法增加日期边界、qiuji 1/其他、whitelist 2/其他/NULL alarmtype、状态 0/缺失分支。

## 作业与 FTP 产物断言

原普通 `851988.zip` 只有 SPECIAL→FTP，没有 TRANS。生成器保留该两项作业，同时另产出 `ordinary-composed-fixture.kjb`，**明确为夹具新增的三项组合**，使用原 TRANS 配置作为模板，TRANS→FTP 使用 `evaluation=Y,unconditional=N`。不得将新增组合称为原两项作业的原样执行。违法 `917552.zip` 的原三项作业已经包含 TRANS。

绑定平台 API 时，先导入对应 KTR，再用其 definition UUID 设置 TRANS 的 `data-rynew-definition-id` 与 `${INPUT_DIR}/<UUID>.ktr`；违法可在重新生成时传 `--child-definition-id`。未绑定的离线原生副本使用 `${INPUT_DIR}/illegal.ktr` 或 `ordinary.ktr`，可供 worker 元数据审阅，但不能冒充平台已经建立绑定。所有输入/子 KTR 在 INPUT_DIR，TextFileOutput 与 FTP localDirectory 只使用 WORK_DIR，绝不把输入拷回输出。

原分隔符 `|$[1F]` 经过 Kettle 环境替换是十六进制 `7C1F`，输出字段顺序为 k_message、targetPicUrl_base64、platePicUrl_base64、targetPicUrl、platePicUrl；两个 base64 Constant 原本为空，夹具也不编造图片抓取。普通 splitevery=75、表头计入分片容量，每份最多 74 条数据，9998 条预期 136 份；违法 splitevery=200，每份最多 199 条，原 max_wait_time_ms=5000 可提前轮换，不能仅以一份文件为成功条件。普通 max_wait_time_ms=0。该字节分隔符和表头计数来自原工具包及既有离线原生 TextFileOutput oracle，本次两条原整图输出已再次证实。

原 FTP 两图均 `remove=Y,rename=Y`，上传成功后本地 CSV 可能已删除。因此必须读取隔离 FTP 目标，不以 worker 下载列表为空推断未输出。普通 binary=Y；违法 binary=N，验证器允许 FTP ASCII 模式的 CRLF/LF 差异，同时记录真实文件 SHA-256。

`--verify-ftp-dir` **只读本地挂载或已下载的专属 FTP 目录**，不联网。检查每个文件符合原 wildcard，拒绝额外输入、子 KTR、临时文件；检查表头、五列、原分片容量、每个 `_fixture.id` 唯一且与文件分支集合完全相等、完整 JSON 映射及图片列值。违法还要求独立捕获的专属 Kafka egress NDJSON，验证唯一回写记录和消息字符串逐字节不变。返回 ARTIFACTS_VERIFIED 仅证明这些文件/消息匹配；真实引擎 provenance、失败时不触发 FTP、任务完成与 stop 门控仍需 worker/API 运行记录证明。

## 复现与审阅

```bash
KETTLE_ORIGINAL_ATTACHMENTS=1 python3 -m unittest discover \
  -s tools/data-governance -p test_kettle_business_fixture.py -v

python3 tools/data-governance/kettle_business_fixture.py \
  --slug acceptance_v2 \
  --output /Users/krizen/Documents/Code/projects/2026projects/rynew-runtime/data-governance-kettle-v2/business-fixture/prepare-v2
```

本地夹具凭据可通过 `--pg-password-env <环境变量名>` 和 `--ftp-password-env <环境变量名>` 绑定，值不进入命令行输出。默认使用显式占位符，manifest.credentialsBound 为 false。不要在聊天、日志或 Git 中打印生成 XML。脚本输出只含图节点数、文件数和 PREPARED_NOT_EXECUTED 状态。

审阅通过并由主任务执行后，使用本地 FTP 目录与独立 Kafka 抓取文件验证：

```bash
python3 tools/data-governance/kettle_business_fixture.py \
  --output /private/prepared-fixture-directory --kind illegal \
  --verify-ftp-dir /private/isolated-ftp-root/acceptance_v2/illegal \
  --egress-records /private/captured-egress.ndjson
```

当前 10 项离线测试通过，包含真实三个 ZIP 的 42 步处理摘要、所有原 JsonPath 存在性、复制数/连线、五张表列匹配、原 consumer/producer 属性、INPUT_DIR/WORK_DIR 分离，以及验证器拒绝缺行、回写字节变化和混入子 KTR。人工构造的验证器测试产物会删除，不能计入整图通过证据。

本次真实原图已验证普通 type=None 字段回读、违法 Boolean/`Y` 筛选组合、完整输出分支计数和实际分片。多 result 消息中违法脚本使用 `target[i]`，当前仅一项夹具未覆盖；私有 URL 替换匹配也未覆盖。脚本会直接拒绝新增非 public lookup schema、集群步骤或远程 TRANS，而不会猜测它们的隔离映射。

## 有限本地执行 runner

`run_kettle_business_fixture.py` 把准备、单次提交和只读恢复分开。`prepare` 仅连接固定 localhost 夹具端口，读取现有私有 PostgreSQL/FTP 凭据，创建唯一随机 slug 的数据库、三个单分区 topic、两个专属 offset 0 group，以及 FTP slug 子目录；遇到已有名称直接失败，不覆盖或重置。它复用既有 FTP 服务，不启动、停止或替换任何进程。原 Kafka 图使用 0.8 原插件；`KettleBusinessFixtureProbe.java` 独立使用隔离 Kafka 的官方客户端 JAR 做建 topic、发布、offset 审计和读取，不混入原 Kettle classpath。

已实际完成的准备目录为 `rynew-runtime/data-governance-kettle-v2/business-live/acceptance-01`，slug 为 `business_18864de700ce`。专属数据库五表行数为 redlist 2、qiuji 2、whitelist 3、xc_cross_csd_status 3、xc_local_sync_cross 3。Kafka 两个输入末尾 offset 为 10000/200，回写 topic 为 0，两 group ZooKeeper offset 均为 0。FTP 仅创建该 slug 的 ordinary/illegal 两个目录。该准备快照随后用于下方两条唯一原生运行，未重新发送消息或改动处理节点。

```bash
python3 tools/data-governance/run_kettle_business_fixture.py prepare --root /private/new-acceptance-directory

# 仅在主任务确认 INPUT_DIR 与执行时区 broker 已升级后使用：
python3 tools/data-governance/run_kettle_business_fixture.py run \
  --root /private/prepared-acceptance-directory --kind ordinary --broker-ready
python3 tools/data-governance/run_kettle_business_fixture.py run \
  --root /private/prepared-acceptance-directory --kind illegal --broker-ready

# 网络响应不明只能核查已经记录的 runId，不再次调用 POST /runs：
python3 tools/data-governance/run_kettle_business_fixture.py resume \
  --root /private/prepared-acceptance-directory --kind ordinary
```

提交前核对所有夹具文件 SHA-256，先持久化 runId、jobId、冻结 job/child XML 摘要与输入指纹，再保存不可变 worker Job 定义，最后单次提交 `/runs`。未知提交状态保留 journal，后续 `run` 拒绝重投，`resume` 只 GET 已有 run。运行前后记录 Kafka offsets；终态和进程结束后通过实际 FTP RETR 读取目标所有文件，保存字节数/摘要/捕获文件，再调用产物验证器。运行证据还包括原 Meta classSource、worker 库清单摘要、实际桥接 class 文件摘要、原生日志事件、节点 metrics 与原生校验响应。源码仅提交 runner/探针/测试，不提交私有 XML、账号、消息或输出文件。

runner 的四个定向测试验证未确认 broker/输入被修改时禁止提交、执行意图先于网络调用、未知提交不重放，group 已被消费/占用时阻止提交，以及非本地凭据和其他数据库名称被拒绝。这些测试不计作业务图执行通过。


## 真实验收结果

私有证据根：`/Users/krizen/Documents/Code/projects/2026projects/rynew-runtime/data-governance-kettle-v2/business-live/acceptance-01`，总报告 `acceptance.json` 状态为 **PARTIAL_DELIVERY**。所有原图、凭据、消息、完整事件和产物只保存在该私有运行目录及原 worker 自有目录，未入 Git。两个运行都已结束，没有停止现有服务或删除数据库/topic/group。

| 项目 | 普通过车原 18 步＋明确新增复合作业 | 违法原 24 步＋原三项作业 |
| --- | --- | --- |
| runId | `5c88336d-22da-4220-ba4d-7448cc99ba6b` | `a4a60abd-a9ae-41bb-a0e4-15d0d9c7a05a` |
| 原引擎终态 | SUCCEEDED / exitCode=0 | SUCCEEDED / exitCode=0 |
| 原输入/分支 | 10000 → 9998 文件、2 丢弃 | 200 → 192 文件、7 丢弃、1 Kafka |
| 原文件实际生成 | 136 份，9998 条，全量 JSON/列/场景匹配 | 1 份，192 条，全量 JSON/列/场景匹配 |
| FTP 实际 RETR | 80 份，5854 条 | 1 份，192 条，完整通过 |
| 留在原 run output | 56 份，4144 条，尚未上传 | 0 份 |
| Kafka 末尾 group offset | 10000，无 consumer owners | 200，无 consumer owners |
| 回写 topic | 尚未写入（普通图无此节点） | 末尾 offset=1，唯一 payload 字符串逐字节等于原输入 |

普通图的残留不是 SQL/Script 分支漏行或文件名覆盖。原 BASIC 完成日志显示 File step `R=9998,W=9998,O=10134`，即 9998 数据行加 136 表头；136 次 rename 的目标全部唯一。FTP 仅上传 80 份后记录 `has put 80 files, break put files`，仍返回零错误和 true。原工具包 `org.pentaho.di.job.entries.ftpput.JobEntryFTPPUT.executeEx(...)` 字节码将局部最大文件数设为常量 80，到达后跳出循环并返回错误计数；原 Meta 没有对应可调 XML 字段。

为核对转换结果，仅将 80 份 FTP **实际 RETR** 与 56 份 worker **未交付残留**只读复制到 `ordinary-combined-native-output`，逐文件标明来源后核对完整 136 份/9998 条。`ordinary-partial-delivery-analysis.json` 明确 `allFtpDelivered=false`。残留没有上传到 FTP 来伪装原作业完整通过，没有重跑 TRANS 或重发同批 Kafka 消息，也没有调大 splitevery、降低输入量、替换 FTP 插件或增加自动重试来凑结果。

这暴露了需要平台单独处理的边界：原 FTP 的成功返回不等于整批所有文件均已交付；对该固定版本和 `remove=Y/only_new=N` 配置，应识别输出残留并提供可核查的后续交付机制，不能将重放整个源图作为恢复。该后续机制不在本次原语义验收中实现。

同一原 `executeEx` 还有必须保留记录的已知行为：远端存在性检查之后，字节码先调用 FTP delete（rename 模式处理临时名，非 rename 模式处理目标名），把局部 exists 标志置为 false，之后才判断 `onlyPuttingNewFiles`。因此 `only_new=Y` 不能被解释为可靠的“远端已存在则不覆盖”保障。原字节码证据已保存在第一套私有证据中，修复 helper 的独立测试也已观察到覆盖；本整图夹具原值为 N，未为了测试改变它。批次 manifest 中的本地文件 SHA 和 nativeProcessedFiles 只表示本地冻结与原调用处理记录，不能等同于远端内容已验证。远端 SHA 结论必须来自独立 FTP RETR，再与冻结内容摘要核对。

违法原图则通过独立 FTP RETR 和 Kafka capture 的完整校验，原 Date/Boolean 筛选、qiuji/whitelist/状态开关/本地映射和两种输出分支均按原配置运行。原消费 TIMEOUT 保持 60000，约一分钟后自然结束，未人为停止或额外发送第 201 条消息。

证据包括 `*-events.ndjson`、`*-latest-run.json`、`*-offsets-before/after.json`、`*-ftp-evidence.json`、`*-sourceclasses.json`、`*-native-step-metrics.json`、`original-ftp-put-limit.json` 与原 FTP class/JAR/字节码 SHA-256。当前 Job 扁平 `nodes` 为空，子转换 metrics 由原 BASIC 完成日志提取并标明来源；普通记录 17 个有实际处理日志的步骤（原图一个未走到的 Dummy 无完成计数），违法记录 24 个。不得把静态 42 节点数量改称 42 个均有非零执行。

## 修复版第二套命名空间

`business-live/acceptance-02` 已独立准备，slug 为 `business_9677ebe75102`，数据库名为 `rynew_kettle_fixture_business_9677ebe75102`。新建三个唯一单分区 topic 与两个专属 group，输入末尾 offset 为 10000/200，egress 为 0，两个 group 初始 offset 均为 0；FTP 仅创建新 slug 的 ordinary/illegal 子目录。42 步的 processingSha256 与第一套逐项相同，原 LIMIT/TIMEOUT/STOPONEMPTY/offset reset/auto commit、SQL/Script/条件/Writer 配置未改。

该第二套目前为 PREPARED_NOT_SUBMITTED，等待主任务确认 `NativeFtpBatchDelivery` 与新 broker 编译、接入完成。未来修复版完整执行必须独立证明：普通一次 TRANS、单次消费 10000、FTP 两个原生 pass 处理 80+56 并全部实际 RETR；违法一次 TRANS、单次消费 200、FTP 一个 pass 及唯一 Kafka 回写。第一套 PARTIAL_DELIVERY 报告、已消费 group、80 份 FTP 读回及原 run 的 56 份残留全部保留，不重用、不补写为通过。
