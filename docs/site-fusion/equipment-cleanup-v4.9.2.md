# v4.9.2 设备管理走查与统一

实施日期：2026-09-23。基线：v4.9.1 / 6b6c40a。

## 走查结论

| 问题 | 证据与处理 |
| --- | --- |
| 旧设备池、类型选择、服务器管理窗口已无打开入口 | 通过模板和脚本引用检查确认，删除旧实现及依赖状态。 |
| 旧导入、确认和服务器目录编辑窗口重复保留 | 共移除6处旧弹窗实现；确认清单统一到独立 ServerIntakeReviewDialog。 |
| 现场配置大文件保留大量旧录入代码 | 删除137个旧状态、方法、常量等顶层绑定；不清理无关组织人员逻辑。 |
| 两套IP展开、模板解析和重复检查 | IP文本展开、模板读取和逐行校验由同一个后端录入服务负责。 |
| 浏览器逐台新增再绑定，可能半途失败 | 统一确认接口重新校验整批清单，同一事务内保存，失败回滚。 |
| 预览之后数据库可能变化 | 确认时重新读取数据库；同现场的服务器写入使用行锁串行化，不依赖旧预览结果。 |
| 文件只检查扩展名，错误提示无法逐行修正 | 统一限制XLSX、5MB、512行，校验表头、IP、端口、状态、长度和账号密码配对，字段错误返回清单。 |
| 校验详情被凭据列挤出首屏 | 将详情和已有归属前移，空凭据列按需展开；已有输入的凭据默认显示，仍按明文核对。 |
| 服务器目录使用旧单密码接口 | 与设备图共用hik/root/其他账号凭据汇总；硬件设备也保留受权限保护的密码查看。 |
| 删除按钮与后端权限不一致 | 按设备来源逐项校验删除权限；仅服务器权限不能删除硬件，混合清单整体检查。 |
| 导出权限存在类型边界 | 统一设备导出只包含操作者有导出权限的设备类型。 |
| 窄屏机柜标签相互遮挡，远景设备标签误显示 | 文字标签按优先级避让，修复CSS2D渲染覆盖display控制的问题；不修改物理布局数据。 |

保留的现场关系画布样式逐规则比对，没有新增或修改原有CSS规则内容；仅删除不再被使用的旧设备窗口样式。新导入界面使用Element Plus原生组件和现有主题变量。

服务器档案编辑移除重复预览和装饰标题，保留原字段、密码显隐权限与保存逻辑，增加原生双列表单和窄屏约束。

## 统一后的页面

1. 现场列表与配置页顶部不放设备新增、管理按钮。
2. 从设备资产层进入管控图；设备清单仍使用原生抽屉、表格和Pagination。
3. 单台、批量、模板录入共用EquipmentIntakeDialog。
4. 模板页选定目标子平台后可下载模板、拖放或选择XLSX；选文件后自动执行只读校验。
5. 校验结果弹出统一清单，支持分页、修改、删除、已有归属提示及显式复用选项。
6. 修改清单或复用策略后必须重新校验；有错误时不能确认上传。
7. 只有点击“确认上传”才保存。成功后刷新设备清单；失败保留内容，不自动重试写入。

复用已有服务器只新增必要的平台归属，不覆盖其名称、端口、凭据等档案。SSH新增默认仍为55555，历史端口保持不变。

## 接口分工

| 职责 | 接口 |
| --- | --- |
| 统一单台新增 | POST /support/equipment |
| 模板下载 | POST /support/equipment/servers/template |
| 文件只读解析与校验 | POST /support/equipment/servers/importPreview |
| IP文本或编辑后清单校验 | POST /support/equipment/servers/preview |
| 确认保存 | POST /support/equipment/servers/commit |
| 统一设备删除 | DELETE /support/equipment/batch |
| 统一平台归属 | PUT /support/equipment/platform/bind、unbind |
| 统一现场设备导出 | POST /support/equipment/export |
| 画布和清单的设备数据 | GET /support/equipmentLocation/topology/{siteId} |

撤除11个已被替代的旧入口：

- POST /support/server
- DELETE /support/server/{serverIds}
- GET /support/server/plain/{serverId}
- POST /support/server/importTemplate
- POST /support/server/importPreview
- POST /support/hardwareAsset
- DELETE /support/hardwareAsset/{assetIds}
- POST /support/hardwareAsset/export
- POST /support/hardwareAsset/bindPlatform
- DELETE /support/hardwareAsset/unbindPlatform
- GET /support/equipment/list

分类详情、编辑与凭据接口继续保留，字段模型本来不同，不重复造通用编辑接口。跨现场服务器目录的查询和导出、平台管理仍在调用的服务器关联接口继续保留。

机房、机柜、U位、链路及布局工作簿属于空间配置，不是重复设备录入。其现有功能和接口保留；本次模板自动校验确认针对服务器模板，不重写机房布局工作簿更新流程。

## 组件与实现

| 页面需要 | 组件与既有模式 |
| --- | --- |
| 录入方式 | el-tabs + motion-tabs + 平台图标 |
| 模板选择与解析状态 | el-upload drag、el-button、el-alert |
| 确认流程 | el-steps、el-dialog、el-descriptions |
| 校验清单 | el-table、el-input、el-select、el-tag、Pagination |
| 重复处理 | 显式el-checkbox，默认不复用 |
| 加载与提交 | 真实loading、错误保留、防重复点击、失效响应保护 |

没有新UI框架、图标库、全局主题覆盖或新增UI例外。UI Guard沿用UIX-004现场画布历史例外；标准录入组件不需要例外。

## 数据与部署

- 不修改业务表结构，不迁移或批量修改已有设备，不需要数据库升级SQL。
- 行锁通过现有现场表的主键查询实现，不增加业务表或索引。
- 保持datafusion现有权限规则；删除、导出按服务器和硬件类型分别校验。
- 密码沿用现有加密服务；敏感请求体和明文查询响应不写入通用操作日志。
- 本版本前后端必须配套更新，不能只部署前端继续调用已删除的旧接口。
- 本地候选前端5179、后端18091；原8080及250生产服务未重启。

## 验证边界

后端测试覆盖IP列表与区间、重复与已有归属、所有错误行返回、模板回读、Multipart接口、JSON绑定、确认重验、默认端口、凭据保留、权限类型边界和Spring事务回滚调用。事务测试验证Spring拦截器触发回滚，不等同于生产数据库故障注入。

浏览器走查使用本地数据，业务现场只做预览检查；写入验收使用测试现场临时子平台和设备，完成后清理。截图与脚本留在output/equipment-cleanup，不进入源码提交。

后端回归包含Multipart控制器请求、JSON请求契约、事务拦截回滚及删除与录入共用现场锁；现场前端规则18项。浏览器实测覆盖重复提示、已有归属、删行后失效、重验解锁、确认保存两台服务器、服务器编辑、硬件新增与密码查询分支、三台混合设备删除。模板下载接口实测返回200和XLSX类型。临时子平台和三台设备均已清理，审计记录保留。

自动化浏览器选取本地文件此前受工具权限限制，因此不将服务端Multipart测试表述为浏览器原生文件选择器的完整实测。
