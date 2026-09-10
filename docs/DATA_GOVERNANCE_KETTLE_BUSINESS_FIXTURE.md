# 原 18/24 步业务图的隔离夹具

当前交付是 **离线准备与校验器**，没有运行原图，没有向 PostgreSQL、Kafka、ZooKeeper 或 FTP 写入数据。全部分支计数均为待实测的期望值；不能用生成成功或单元测试替代原引擎执行证据。

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

违法日期脚本去掉 `+08:00`，将 `T` 换为空格，截取前 19 字符，再以斜线日期解析，与固定 `2019/12/24 10:22:25` 比较，必须 **严格晚于**。不是相对当前时间的窗口。夹具包括前一秒、同秒、后一秒和无效日期；保留原 Script Boolean→FilterRows 常量 `Y` 的实际组合，等待原引擎验证。

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

原分隔符 `|$[1F]` 经过 Kettle 环境替换是十六进制 `7C1F`，输出字段顺序为 k_message、targetPicUrl_base64、platePicUrl_base64、targetPicUrl、platePicUrl；两个 base64 Constant 原本为空，夹具也不编造图片抓取。普通 splitevery=75、表头计入分片容量，每份最多 74 条数据，9998 条预期 136 份；违法 splitevery=200，每份最多 199 条，原 max_wait_time_ms=5000 可提前轮换，不能仅以一份文件为成功条件。普通 max_wait_time_ms=0。该字节分隔符和表头计数来自原工具包及既有离线原生 TextFileOutput oracle，当前整图仍待实测。

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

尚待原引擎确认：普通 type=None 字段回读、违法 Boolean/`Y` 筛选组合、原完整图所有输出与上述计数、实际分片/定时轮换、失败时 FTP 不触发。多 result 消息中违法脚本使用 `target[i]`，当前仅一项夹具未覆盖；私有 URL 替换匹配也未覆盖。脚本会直接拒绝新增非 public lookup schema、集群步骤或远程 TRANS，而不会猜测它们的隔离映射。

## 有限本地执行 runner

`run_kettle_business_fixture.py` 把准备、单次提交和只读恢复分开。`prepare` 仅连接固定 localhost 夹具端口，读取现有私有 PostgreSQL/FTP 凭据，创建唯一随机 slug 的数据库、三个单分区 topic、两个专属 offset 0 group，以及 FTP slug 子目录；遇到已有名称直接失败，不覆盖或重置。它复用既有 FTP 服务，不启动、停止或替换任何进程。原 Kafka 图使用 0.8 原插件；`KettleBusinessFixtureProbe.java` 独立使用隔离 Kafka 的官方客户端 JAR 做建 topic、发布、offset 审计和读取，不混入原 Kettle classpath。

已实际完成的准备目录为 `rynew-runtime/data-governance-kettle-v2/business-live/acceptance-01`，slug 为 `business_18864de700ce`。专属数据库五表行数为 redlist 2、qiuji 2、whitelist 3、xc_cross_csd_status 3、xc_local_sync_cross 3。Kafka 两个输入末尾 offset 为 10000/200，回写 topic 为 0，两 group ZooKeeper offset 均为 0。FTP 仅创建该 slug 的 ordinary/illegal 两个目录。**此状态只证明准备成功，尚未提交原图。**

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

runner 的三个定向测试验证未确认 broker/输入被修改时禁止提交、执行意图先于网络调用、未知提交不重放，以及非本地凭据和其他数据库名称被拒绝。这些测试不计作业务图执行通过。
