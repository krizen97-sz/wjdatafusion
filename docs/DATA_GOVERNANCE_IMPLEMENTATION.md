# 数据治理开发版 v4.5.0

本版是通用浏览器 ETL 的开发基础，不是现有两条海康业务的生产迁移完成版。

v4.5.1已将日常设计入口升级为平台内画布，见[平台内ETL设计器](DATA_GOVERNANCE_DESIGNER.md)。本文保留v4.5.0底座、执行边界和运行方式；原生NiFi入口仅用于高级管理。

## 已实现

- RYNEW 项目/流程目录、组件可用性、原生 NiFi 设计入口及隔离样本测试台。
- NiFi Process Group 持久化项目与流程定义；样本提交时冻结安全拓扑、参数与准确组件版本，记录 `definitionHash`、`definitionCapturedAt`。
- 后台有界执行，读取真实输入/输出队列和 sample.* 属性；支持取消、超时、失败与资源清理确认。
- JSONPath、受限属性/条件路由、基础内联 Jolt Chain；不支持的组件或配置返回 `UNSUPPORTED`，不静默跳过。
- `DelimitedTextWriter` 独立 NAR：封闭 JSON 对象数组转 UTF-8 文本，字段顺序、十六进制分隔符、表头、数据记录/旧表头计数、显式到达时间驱动的滚动可配置。
- 仅本机监听的 NiFi、MySQL、Redis、PostgreSQL、FTP 和同源 HTTPS 网关，以及针对独立数据目录的安全启停工具。

## 执行范围与限制

安全样本测试当前最多12个处理器、24条连接，一个样本源、一个观察节点，输入不超过256 KiB及100个数组元素。它不是任意 NiFi 图的通用无副作用执行器：子流程、外部连接器、连接服务、脚本、自定义Jolt类和环境表达式明确拒绝。

观察计数为 FlowFile，不等于业务记录数。样本内容为有界预览，不能据此断言超出预览范围的数据全部正确。`EMPTY` 为已观察到空批次关系的终态，清理未确认时不会标记为完整通过。

协议文本组件仅处理已经关闭的批次，不负责实时Kafka消费、数据库查表、持续流聚合、目录扫描、临时文件发布或FTP确认。它没有复制海康私有JAR；兼容规则以静态审查得到的计数与格式契约独立实现，原运行环境的完整等价性仍须另外验收。

当前报告仓库是原子文件存储：文件/目录权限、单实例锁、原子替换、持久化摘要已实现；多实例协调、数据库化存储及大规模历史查询不在本版范围。未配置NiFi时不会默认连接任何外部引擎。

## 前端组件复用

| 页面需要 | 参考实现 | 选用能力 | 新增范围 |
| --- | --- | --- | --- |
| 项目与流程列表 | `system/post`、`system/user` | el-form、el-table、RightToolbar、Pagination、el-dialog | 治理数据绑定与模块内规则 |
| 状态与视图 | `system/user/profile`、平台动效约定 | el-tabs、SvgIcon、el-tag、Precision Rail | 固定状态集中映射、未知状态明确显示 |
| 长结果详情 | `knowledge/components/KnowledgeHistoryDrawer.vue` | el-drawer、el-descriptions、表格与默认Tabs | 真实步骤样本、属性与错误展示 |
| 样本输入 | 已有表单与文本输入 | el-input textarea、表单校验与loading | 有界JSON校验，参数覆盖默认为空对象 |
| 流程设计 | NiFi原生浏览器画布 | 同源 `/nifi/` 安全链接 | 不新增通用画布，不继承旧iFrame计时假完成逻辑 |

没有增加第二套UI框架、图标库或全局主题覆盖，没有UI Guard例外。每个流程的未提交样本与参数仅在当前页面内存保留，避免切换时丢失或写入浏览器长期存储。

## 本地配置与启动

运行底座详见 [DATA_GOVERNANCE_RUNTIME.md](DATA_GOVERNANCE_RUNTIME.md)。开发机器上运行目录为仓库之外的独立路径；代码不包含凭据、私钥、数据库数据或下载的二进制。

1. 校验并准备运行底座、私有NiFi登录及CA，启动各独立服务。
2. 在NiFi全局root下创建本开发任务独有的治理Process Group，记录其ID到应用运行目录的 `nifi-binding.json`；不要复用生产root。
3. `bootstrap-dev-db.py` 仅连接127.0.0.1:13306，并核对实际datadir，创建 `rynew_governance_dev`；只导入系统表结构，不导入现有用户、数据源、业务记录或计划任务。已有数据库无所属标记时拒绝覆盖。
4. `app_dev.py prepare` 生成600权限的外部配置，绑定新MySQL/Redis及独立NiFi。应用登录信息只保存在私有文件；密码遵循现有平台5—20字符约定。
5. 在本工作树的WDF100.0构建真实管理后端，再执行 `app_dev.py start`。停止时核对PID、启动时间、命令和所属目录，不按端口杀未知进程。
6. 前端执行 `npm run verify:frontend` 后，由同源网关提供dist及API，开发入口为 `https://localhost:10443/governance/workspace`。

示例命令（运行目录按本机实际路径设置）：

```sh
python3 tools/data-governance/bootstrap-dev-db.py --runtime "$DG_RUNTIME" --app-runtime "$DG_APP_RUNTIME"
python3 tools/data-governance/app_dev.py prepare --runtime "$DG_RUNTIME" --app-runtime "$DG_APP_RUNTIME"
mvn -f WDF100.0/pom.xml -pl wjdatafusion-admin -am -DskipTests package
python3 tools/data-governance/app_dev.py start --runtime "$DG_RUNTIME" --app-runtime "$DG_APP_RUNTIME"
python3 tools/data-governance/smoke_app.py --runtime "$DG_RUNTIME" --app-runtime "$DG_APP_RUNTIME"
python3 tools/data-governance/smoke_permissions.py --runtime "$DG_RUNTIME" --app-runtime "$DG_APP_RUNTIME"
```

自签名开发证书需要浏览器建立信任。程序HTTP验收使用明确的本地CA进行严格TLS校验；没有通过全局关闭证书验证替代浏览器信任，也没有自动改变系统信任设置。

## 协议组件构建

`data-governance/compatibility/core` 是无网络和文件副作用的纯编码核心；NiFi包装与NAR使用独立Java21构建，业务后端继续Java17。

```sh
# 在子进程中选择已校验的独立Java21，不更改系统默认Java。
env JAVA_HOME="$DG_JAVA21" mvn -f data-governance/compatibility/pom.xml package
```

NAR制品为 `data-governance/compatibility/nifi-nar/target/governance-nifi-nar-1.0.0.nar`。安装仅针对本任务的独立NiFi，记录制品摘要及配置备份；新包需验证真实注册和实际执行，不能只看构建通过。

封闭批次中的 `KETTLE_HEADER_INCLUSIVE` 使用原写入计数（含表头），纯行数滚动下75条数据分成74＋1；`DATA_RECORDS` 则明确按数据记录计数。最大文件年龄是下一条记录到达时严格超过阈值才滚动，非定时flush。到达时间必须是非负long整数；非法UTF-8和越界时间戳会拒绝，不静默修正。

## 权限与部署边界

独立SQL `data_governance_upgrade_20260909_v4_5_0.sql` 仅新增菜单、按钮权限及管理员映射。它不修改现有业务表；部署前应检查路径冲突并备份菜单相关表。

API沿用真实RYNEW登录与 `@PreAuthorize`，测试记录还按创建用户隔离。NiFi是独立登录，当前同源入口不代表SSO或生产多租户授权已完成；浏览器不持有后端NiFi管理员令牌。

## 已执行的验证类型

- 前端完整verify：UI Guard、原有模块测试、新治理规则测试、生产构建。
- 编码核心和NiFi Mock：计数、分隔字节、空输入、混合滚动、字节预算、严格UTF-8/时间戳、读取失败回滚。
- 真实NiFi：当前画布修改、Jolt Chain、取消、拒绝外部组件、文本/EMPTY/failure、多片继续执行、排队后画布修改不改变冻结结果、清理确认。
- 真实平台HTTPS：登录、菜单、NiFi连通、项目/流程创建、样本结果与74＋1分片；匿名/只读角色/跨用户记录访问的拒绝。
- 运行底座：制品SHA/PGP、认证健康、PID与目录保护、FTP写入/改名/读回/删除、网关路径和TLS。

详细结果在运行目录的evidence及模块测试报告中。2026-09-09已使用用户Chrome完成下列开发版业务交互验收；亮暗主题、窄屏和完整键盘无障碍矩阵尚未完成，不把本次默认桌面视口检查扩大为全面视觉验收。

### Chrome交互验收与修复（2026-09-09）

- 使用真实平台账号登录同源HTTPS入口，在页面中新建项目、JSON与协议文本流程；NiFi仍是独立登录。
- 修复NiFi 2.11深链接：使用 `/nifi/#/process-groups/{id}`，避免旧查询参数使页面落到全局根画布。
- 新模板节点间距调整为520px；平行/跨节点分支增加转折点。仍保留每条连接一个relationship，不改变安全执行契约，不重新布局已有用户流程。Chrome新建模板确认没有连接重叠警告。
- 在原生画布拖动节点并刷新，位置持久化；把JSONPath由 `$.message` 改为 `$.payload.message`，工作台参数保持 `{}`，真实执行得到新字段值，确认画布配置未被模板默认值覆盖。
- 从组件栏拖入UpdateAttribute，添加 `sample.browser` 属性，再用鼠标连接条件分支与观察节点。工作台真实执行5个步骤、2条分支输出，新增节点返回配置的属性值。接线过程中检查起终点，识别为自身连接的尝试已取消，未保存环路。
- 非法JSON显示表单错误并保留原运行记录；协议模板提供可用数组样本；空数组返回EMPTY且明确显示无输出；75条合成记录实际产生74＋1两片，分隔符为7C1F，各次测试清理均已确认。
- 前端合入主线Chrome兼容基线后完整校验143项通过，构建成功，120个JavaScript产物通过兼容扫描；治理后端15项单元＋4项真实NiFi集成测试通过，额外HTTPS应用烟测4种结果通过。

运行目录的 `chrome-jsonpath-result.png`、`chrome-nifi-layout.png`、`chrome-added-node-canvas.png`、`chrome-added-node-result.png`、`chrome-empty-batch.png` 和 `chrome-browser-acceptance.json` 记录页面证据。浏览器只执行合成样本，没有开启持续消费、定时生产任务或访问原业务源目标。


## 后续未完成的业务范围

- 原两条海康任务的完整查表、JSON业务改写、Kafka批次/位点与FTP确认，以及原引擎对照样本。
- 通用数据源配置、隔离集成测试、调度发布、持久交付台账和按场景扩展的连接器。
- HBP/Face/BSP、海康云存储、其他厂商SDK与schema/字典制品。
- 主线/共享IDE工作目录同步、发布包与生产切换，需在相应验证完成后独立进行。

这些能力在界面中显示待适配，不以模板名称或插件目录项冒充实现完成。
