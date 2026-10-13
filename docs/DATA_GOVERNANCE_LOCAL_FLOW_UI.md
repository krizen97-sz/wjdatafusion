# 平台内置基础数据流程工作台

## 本次范围

新增默认“数据流程”工作区，使用 `/governance/local` 的平台内置 Java 执行服务。复用现有 FlowDiagram 的拖拽、平移、连线、缩放、自动布局与节点状态；不新建画布内核。

旧 Kettle 任务与 NiFi 治理流程保留显式 Tab，并仅在选中后挂载；新的基础流程不调用它们的 API，不要求安装其服务。已有 `tab=kettle` 深链接仍打开旧 Kettle 工作区；没有指定 Tab 时进入内置流程。

## 组件复用计划

| 页面需要 | 既有实现 | 选用组件 | 参考文件 | 本次定制 |
| --- | --- | --- | --- | --- |
| 节点画布 | 当前治理模块画布 | FlowDiagram / graphRules.arrangeNodes | governance/components/FlowDiagram.vue | 将本地 graph 节点及连接映射为既有画布 props |
| 任务与组件库 | 现有治理左栏 | ElTabs / ElInput / ElScrollbar / ElButton | governance/kettle/KettleWorkbench.vue | 基础节点目录与任务切换保护 |
| 初次上手 | 文档工作区空态 | ElEmpty / ElSpace / ElButton | document/workspace/index.vue | 内置合成示例与空白流程入口 |
| 中文节点配置 | Element Plus 标准表单 | ElForm / ElInput / ElSelect / ElSwitch / ElUpload | ipam/index.vue / governance/kettle | 每种内置节点的有界配置 |
| 预览、历史与输出 | 治理模块结果模式 | ElTabs / ElTable / ElTag / ElAlert / ElDrawer | governance/kettle/KettleResults.vue | 展示实际输入输出行数、节点采样、文件与版本 |
| 定时计划 | 原模块 Cron 与计划交互 | Crontab / ElDialog / ElTable | governance/kettle/KettleSchedules.vue | 调用 local 调度契约，保存为暂停状态 |

没有新增依赖、图标库或主题；新增样式仅限定此工作区，使用已有语义变量。复用既有画布例外，不新增 SVG 或 UI Guard 例外。

## 首批节点与操作

- CSV 输入、JSON 输入：粘贴数据或读取本地 UTF-8 文本。上传仅在浏览器读取内容到节点配置，不向另一套文件服务发请求。
- 字段映射：输入字段、输出名、trim、可选默认值、是否保留其他字段。
- 条件过滤：文本/数字类型与显式比较条件。
- 排序：一个或多个字段及方向、文本/数字类型。
- 去重：按所选字段保留第一条记录。
- 字典关联：静态 JSON 配置（rows/matches/returns/missing/multiple），应用成功后进入流程草稿。
- 合并：显式接入多个上游，不把多个输入隐式混到普通处理节点。
- 输出：CSV 或 JSON 文件，正式执行后从运行结果下载；预览不发布文件。

示例为 CSV → 字段映射 → 条件过滤 → 排序 → 输出。3 行合成记录中选择“苏州”，金额按数字降序，预期为张三20、alice10。用户可修改筛选值为“上海”验证画布修改改变实际结果。示例只创建，不自动执行。

## 执行、版本与失败交互

- 保存空草稿允许继续编辑；执行前调用后台检查或由运行校验确定完整 DAG。
- 脏草稿先保存，执行提交使用保存返回的 revision。创建/保存API将整个 graph JSON 按UTF-8编码为 graphBase64，保留输入中的中文、尖括号和嵌套字符串，不放宽平台全局XSS规则。
- 请求标识在调用前写浏览器存储；未知提交保留原请求，先调用只读 `/runs/request/{requestId}` 核查，必要时重试同一请求体；不另造一次运行。
- 原请求版本已变化时再次核查运行记录，确认无记录后释放过期请求并提示重新读取。
- 真正的 QUEUED/RUNNING/STOPPING 轮询返回每节点进度；不生成假百分比或假成功。
- 执行中禁止编辑、切任务及用旧历史记录替换当前运行；停止要等后台返回确认状态。
- 从历史识别出的活跃运行，即使详情暂不可读也保持编辑锁并继续核查。
- 每个结果显示对应 revision；修改画布后标记旧版本，失败可定位节点。
- 定时计划冻结当前定义版本并默认暂停，启用/暂停/立即执行/核查恢复均使用后台真实响应。

## 验证与边界

本子任务的自动化验证覆盖节点默认值隔离、示例图与配置、非法连线/环、多路合并、结果字段、实际工作台 setup 的保存后执行、提交超时恢复、版本过期、历史锁及迟到响应。

自动化结果：`npm run verify:frontend` 全部通过，含94项治理测试、303项前端测试、生产构建与170个JavaScript文件Chrome 64+兼容检查。UI Guard为0错误、0警告，7项例外均来自继承的现场融合代码，本次没有新增例外。

真实后端执行与 Chrome 页面验收由整合后的环境完成；本前端测试不会被表述为纯 Java 引擎已经验证。
