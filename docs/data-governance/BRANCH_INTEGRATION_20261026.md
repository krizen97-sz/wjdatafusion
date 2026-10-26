# 2026-10-26 主线整合核对

核对基线：`3716d67`（v4.17.2）。覆盖 188 个本地/远端引用、155 个已有 worktree；后续新建的本次集成分支单独验收。

## 分支处理

| 处理 | 引用数 | 依据 |
| --- | ---: | --- |
| 已直接进入主线 | 123 | 分支提交已是 main 的祖先 |
| 等价补丁已进入主线 | 54 | git cherry 全部为等价补丁，补齐合并历史 |
| 语义相同、基线不同 | 2 | HDI 保存状态对应 8cfa1be；文档上传对应 ce977f4，保留后续修复 |
| 补入遗漏的交付文件 | 1 | 800968e、2b1f719：v4.16.0 到 v4.17.0 检查 SQL、部署说明和测试，保留密文默认值脱敏 |
| 已被新版实现替代 | 8 | 早期内置本地流程/托管 Kettle 候选，合并历史并归档，不恢复旧入口 |

历史整合记录不会把当前源码替换为旧分支整棵目录。已等价或已替代分支使用保留当前树的合并记录；遗漏交付文件进行了真实内容合入。原分支继续保留，可追溯原始提交。

## 以哪套实现为准

当前 `src/views/governance/workspace/index.vue` 只挂载 `HdiTaskWorkspace`。这是 49ebd64 及后续原生 HDI 编译、身份、连接、运行和计划实现的整合结果。

早期分支的 `DataGovernanceLocalEngine/LocalFlowWorkbench` 是另一套基础执行器，不是当前原生任务内核的后续版本；托管 Kettle 候选也是旧工作区的替代运行方案。将其工作区文件覆盖回来会移除当前原生任务入口，因此本次保留它们的历史与私有快照，不重新启用。

## 未提交工作

7 个 worktree 的源码改动已保存到本机受控快照，原工作目录及暂存状态未清理、未覆盖。

| worktree | 处理 |
| --- | --- |
| data-governance-etl-results-panel-polish | 改动已由主线布局覆盖；主线还有全宽结果、局部加载、深色选中态修复 |
| data-governance-etl-workbench-regression | interaction helper 与主线一致；保留主线后续深色选中态 |
| data-governance-kafka-batch-v1 | protocol/records 及相关测试与主线一致；主线多出运行意图绑定、禁止提前释放的保护 |
| data-governance-bundled-local-runtime | 旧托管 Kettle 草稿，保留快照 |
| data-governance-managed-runtime-lifecycle-tests | 旧托管 Kettle 生命周期草稿，保留快照 |
| data-governance-native-basic-flow-ui | 旧基础执行器草稿，保留快照 |
| data-governance-native-local-flows | 旧基础流程入口、调度和 v4.18.0 草稿，保留快照；不是已发布版本 |

完整引用、逐分支判断、原始 diff 与未跟踪源码在本机 `rynew-local-debug/integration-20261026-from-v4.17.0/`，不进入源码发布包。备份可能含内部实现信息，应保持私有。

## 数据库范围

以完整 v4.17.0 为起点，新业务 DDL 只有数据治理原生任务的两份升级 SQL：7 张任务/版本/运行/计划表，以及 3 张连接/连接版本/对象标识表。v4.17.1 扫描审计修复与 v4.17.2 周月报汇总均无新增业务表结构。

新交付包单独包含累计增量、只读核对、原始脚本和来源校验。v4.17.0 凭据池迁移作为未完成该基线环境的可选前置，不能把“从 v4.17.0 升级”误解为要再次初始化全部业务表。
