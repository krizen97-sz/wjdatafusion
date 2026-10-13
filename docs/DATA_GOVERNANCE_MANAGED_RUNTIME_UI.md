# 平台内置数据流程：工作台接入说明

## 范围与组件复用

本次复用现有 Kettle 数据开发工作台、原生 XML、输入附件、预览、运行、结果和调度入口；不建立第二套画布，不改旧 NiFi 流程，也不自动执行导入或示例任务。

| 页面需要 | 既有实现 | 选用组件 | 参考文件 | 本次定制 |
| --- | --- | --- | --- | --- |
| 托管引擎准备、失败与就绪状态 | 工作台标题与提示区域 | ElText / ElTooltip / ElAlert / ElButton | governance/kettle/KettleWorkbench.vue | 单一运行态映射与启动期间状态刷新 |
| 初次上手 | 工作台空态、文档工作区空态 | ElEmpty / ElSpace / ElButton | document/workspace/index.vue | 从合成本地示例开始 |
| 创建方式说明 | 既有创建 Dialog、IPAM 引导表单 | ElDialog / ElForm / ElInput / ElRadioGroup / ElAlert | ipam/index.vue | 空白画布与本地示例二选一 |
| 配置和结果 | 原节点配置、执行结果 | KettleNodeConfig / KettleResults | governance/kettle | 沿用现有行为 |

未新增样式、全局组件、依赖、图标库或 UI 例外。新增按钮、状态、表单使用现有 Element Plus 与项目主题；未改变已有动效。

## 运行态契约

`GET /governance/kettle/status` 保留 `enabled`、`executionAvailable`、`draftStorageAvailable`，新增：

- `runtimeMode`: `managed` 或 `external`。
- `runtimeState`: `DISABLED`、`STARTING`、`READY`、`FAILED`、`STOPPED`。
- `runtimeMessage`: 后台生成的可读提示，不包含令牌、凭据或私有路径。

托管模式仅在 `enabled=true && executionAvailable=true && runtimeState=READY` 时允许预览和运行；仅进程启动不代表可执行。旧外部模式与没有新字段的后台仍沿用原健康判断。

`STARTING` 每 3 秒重新获取状态，变为就绪后刷新工具目录。离开页面清除轮询；刷新代次阻止迟到响应覆盖较新状态。失败、停止、接口不可用展示明确提示与刷新操作，不把已保存旧状态当成实时就绪。

默认工作区仍只挂载 KettleWorkbench。`LegacyWorkspace` 仅在用户显式进入“已有治理流程”时挂载，不为默认数据开发触发 NiFi 加载。

## 本地合成示例

入口为无任务空态的“从本地示例开始”，以及“新建”菜单。新建转换 Dialog 允许选择本地示例或空白画布。

示例使用已有原生插件：

1. `CsvInput` 从 `${INPUT_DIR}/sample.csv` 读取 `name`。
2. `ScriptValueMod` 将姓名去除首尾空白并转大写，生成 `cleaned_name`。
3. `SortRows` 按 `cleaned_name` 升序排序。
4. `TextFileOutput` 写 `${WORK_DIR}/result.csv`。

输入为 bob、alice、张三，共 3 行合成数据。初始定义没有数据库、HTTP、Kafka、FTP 等连接。模板由 `starterFlow.js` 导出，根级集成测试应直接使用同一份 XML 与 CSV，避免测试另一份手工副本。

创建依次调用已有的定义创建与附件上传接口。仅定义、附件都成功才提示“示例任务与输入数据已创建”，不会自动提交执行。上传超时或失败时保留真实已创建任务，提示“补齐示例输入”；补齐前先查询已有附件，避免首次请求实际成功后重复上传。仍引用缺失示例文件时不允许预览/运行。改成其他数据输入后，这个示例文件限制不继续影响修改后的流程。

## 验证与交付边界

- `npm run test:governance`：87 项通过，包括托管状态、创建/上传分阶段完成、上传不确定后附件核查与去重。
- `npm run ui:guard`：0 错误、0 警告；相对 origin/main 的 7 项例外来自继承的现场融合代码，本次没有新增例外。
- `npm run verify:frontend`：296 项前端测试、生产构建及 166 个 JavaScript 文件的 Chrome 64+ 检查全部通过。
- 原生 Kettle 行为与真实浏览器视觉验收由合并后的集成环境验证；前端合成脚本测试不代替原引擎执行证据。
