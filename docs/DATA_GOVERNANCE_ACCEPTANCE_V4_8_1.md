# v4.8.1 本地候选验收

日期：2026-09-16。基线：`312d406` / v4.8.0。本轮用户要求基于本地代码进行分析和开发，没有部署到250，也没有数据库升级。

## 实施范围

- 工具目录分为常用、全部可用、完整目录；默认不混入不可执行项，搜索可扩展至非常用工具。能力说明区分专用表单、原生参数、仅目录、加载失败、未开放和服务未连接，不把类加载成功当业务验收。
- SortRows 增加专用排序字段表单，默认进入排序字段页；参数键与新增行默认值按原 Meta 模板核对。9类常用表单将低频字段折叠，高级和未知XML保留。
- 桌面 Grid 使用受限行及子项最小高度，配置和结果分别滚动；短视口工作区和结果高度随可用空间调整，提供vh与dvh回退。
- 获取字段按实际 validationScope/fieldsResolved/fieldDiagnostics 反馈；诊断含节点、输入/输出位置、异常与原因，可定位节点；作业结构检查不再称为字段解析成功。
- 转换 validate 与 save/load 将 inputFiles 传入同一受控输入目录；保存依然只作离线XML加载，不隐式启动转换或访问业务连接。
- Broker 的可信1–3600秒预算写入运行意图、原生JVM与watchdog。默认保持120秒，重复runId继续使用已冻结预算。Linux journal v4将预算纳入身份，旧v1–v3恢复沿用原公式。超时明确返回TIMED_OUT并保留部分产物。

实现提交已整合到 `codex/data-governance-etl-usability-phase1`；原子提交包括 `a0b47e3`（Worker上下文/预算）、`494158a`（前端）、`b292217`（超时测试补强）、`6bc3b2f`（短视口）。最终版本记录提交由本文件所在分支历史提供。

## 自动化检查

| 范围 | 结果 |
| --- | --- |
| 完整前端 `npm run verify:frontend` | 196项通过，0失败/跳过，其中新增11项工具筛选/能力/校验状态规则测试 |
| 整合后 UI Guard | 0 errors / 0 warnings / 0 exceptions |
| 前端候选生产构建及兼容检查 | 通过；148份JavaScript目标Chrome64+ |
| 原Worker基础 | 21项通过 |
| 原生操作上下文/预算 | 4项通过 |
| 原生恢复 | 4项通过 |
| Linux dispatcher / HTTP协议 | 18项通过 |
| Linux adapter | 53项通过 |
| 网络smoke模拟 / 镜像导出 | 15项 / 8项通过 |

Worker相关去重总计123项。独立审查者另外复跑的53+18属于同一集合，没有重复计入。超时断言补强后，只定向重跑8秒用例通过；未重复运行长Job。本轮平台Java API实现没有变化，未重复声称Maven回归通过。

## 原引擎实测

在独立 macOS Seatbelt 运行目录使用原海康JAR：

1. HTTP字段校验收到上传CSV和二进制两个文件，INPUT_DIR里的字节和SHA一致；没有RUNNING/行事件/输出文件，load仍断网。
2. 150秒预算的原生Job实际运行126.81秒后SUCCEEDED，输出123行，TRANS只执行一次；确实越过旧120秒限制。
3. 1200秒预算的短Job成功，原引擎回传1200，未被旧900秒钳制；不代表实测运行了1200秒。
4. 8秒预算返回TIMED_OUT，真实 `result.csv` 为105字节/7条数据且partial=true。以30秒配置重建Worker后，同runId仍冻结8秒；重复请求未调用Popen，运行目录、spawnedAt、record及events均不改变。

证据：`rynew-runtime/data-governance-worker-contract-20260916/acceptance-summary.json` 及该目录四类proof；补强证据为 `timeout-stop-proof-fbe6252df49a41c6b94d3faf99faba36.json`。

Linux本轮只完成预算身份、篡改拒绝、旧记录恢复的模拟测试；没有在250运行新版本。以后发布必须同步升级Python broker、Linux adapter和新Java classes，不能只换其中一个文件。

## Chrome操作验收

本地候选入口：`http://localhost:18086/governance/workspace`。前端来自本分支；API继续使用既有18083，原生Broker在核对身份、无活跃任务后升级，既有私有运行记录保留。

| 验收 | 结果与证据 |
| --- | --- |
| 排序表单 | 同一V3四节点图打开SortRows即显示字段name和升序；原配置未改。截图10 |
| 真实节点预览 | run `81963ade-08b6-4947-a67a-aaaa81dba6a5`成功，显示web:ALICE、web:BOB、web:张三；文件输出节点未执行。截图11及candidate-layout-and-preview.json |
| 长表单布局 | 1728×996下，body756/main754，结果top645/bottom965；基线main1301、结果top1192/bottom1512。修复后整块结果可见 |
| 常用工具 | 输入组从基线47项收敛至6个常用项；Excel搜索仍能找到非常用可执行工具。截图12 |
| 完整目录 | HBP四项仍禁用并提供能力边界，未伪装为可运行。截图13 |
| 真实字段失败 | 合成连接指向未授权loopback端口9，元数据检查返回KettleStepException；UI显示1项问题、节点和原因。截图15及candidate-diagnostics.txt |
| 诊断定位 | 点击定位节点后，选中“受限连接的表输入”，右侧打开TableInput参数面板 |
| 短视口 | 1366×768下body528、结果约245.76，底部737低于视口768，画布仍约155px高；面板内部可滚动。截图16及candidate-compact-viewport.json |

截图和DOM证据在 `rynew-runtime/research/20260916-etl-usability/`。Chrome控制曾有中断，恢复后才继续；未把工具超时算成产品耗时。没有做容量、P95或用户完成率统计。

另保留一个有用的未完成边界：未绑定连接的TableInput原Meta可返回空schema并宣称字段解析成功。因此“字段元数据检查”仍不能替代连接与必填参数预检查；界面已明确其范围，但完整语义预检属于后续建设。首次样本的真实结果保留在截图14，没有改写为失败验收通过。

## 运行与回退边界

本轮没有重启平台API、没有修改连接许可、没有触发生产ETL或定时计划。旧broker确认无直接引擎子进程、无活跃运行记录后，以精确进程身份停止；新Java classes先编译成功再替换，新服务健康后才写入进程记录。没有使用强制SIGKILL。

本地备份：`rynew-runtime/research/20260916-etl-usability/runtime-upgrade/backup-20260916145527`，包含旧broker记录、manifest和运行记录；旧classes保留在 `data-governance-kettle-worker-v2/classes-before-usability-221eb04b59ce46348752ef3b80086d35`。如需回退，先核对当前broker身份与活跃状态，再恢复该classes/manifest并使用旧进程记录中的启动参数；不删除新运行证据。

本地候选不等于生产发布。当前主集成目录仍保留原基线；本轮代码在专用分支和工作树，便于独立审查与后续合并。

## 尚未覆盖

连接中心/库表元数据、输入自动推断字段、输入输出并排比较、完整编辑历史、项目协作、持久队列、发布版本库、全局运维、漏跑补数/告警、交付核查、HBP bundle兼容和持续CDC仍按研究报告的路线推进。本轮未重跑完整生产业务图、未完成Linux实机复验，也未完成深色模式、200节点性能及屏幕阅读器验收。
