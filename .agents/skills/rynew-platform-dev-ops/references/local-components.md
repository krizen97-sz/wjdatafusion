# 本地新增业务与组件契约

所有前端路径相对 `RuoYi-Vue3-master/src`。先看全局注册和本模块规则，再选择组件。静态索引在迁移包 `knowledge/local-source-index.json`；本轮覆盖162个Vue组件、150个前端模块、621份Java源和51个MyBatis XML，记录哈希、imports/exports、props/emits、API及表引用。它帮助定位，不替代运行验证。

## 公共组件的实际契约

`main.js`全局注册DictTag、Pagination、RightToolbar、Editor、FileUpload、ImageUpload、ImagePreview和SvgIcon；其他组件从实际路径引入。不存在统一“表格壳/详情壳”不代表需要补造。

| 能力 | 输入/输出及内部逻辑 | 维护注意 |
| --- | --- | --- |
| DictTag | options、value、showValue、separator；将数值/字符串/数组规范化后匹配字典标签 | 空值/未知值语义与字典类型一致；历史写法不是新增Vue3语法依据 |
| Pagination | total、page、limit、pageSizes等；发出update:page/update:limit及pagination({page,limit}) | 改页大小检查页码是否越界；父组件据事件请求后端，不二次全量分页 |
| RightToolbar | showSearch、columns、search、showColumnsType、gutter；发出update:showSearch和queryTable | columns可数组或对象，visible是同一模型的列状态；不另画刷新/列显隐工具栏 |
| FileUpload/ImageUpload | modelValue、action、limit、fileSize/fileType等；统一Token头、格式/体积/数量检查和模型回传 | 是通用上传器，不代替文档模块额度/ACL/版本接口；Token快照、失败和批次完成需按代码处理 |
| Editor | Quill封装与图片上传/粘贴 | 前后端内容清理不能旁路；不直接再装第二种富文本引擎 |
| Crontab | 表达式编辑与回显，子时间字段协同 | `updatePolicy.js`控制是否保留独立字段；不能把上一个业务场景的重置策略套到全部计划 |
| SvgIcon/IconSelect | 图标注册、回退、分类与中文/拼音查找 | 保持menu.icon语义；复用现有本地图标和Keyline目录 |
| $modal/openPlatformLoading | Element Plus消息/确认与平台遮罩；modal.loading用depth管理嵌套调用 | 每次打开与关闭配对；不能新建全屏加载服务导致嵌套请求互相关闭 |

注意真实实现中的边界：FileUpload虽声明modelValue可为Object，但当前watch的非数组分支调用split，不能据prop类型推断任意对象均可传入；沿用已有字符串/数组模型，若需支持对象应明确补契约与测试。类似历史残留只能作为排障线索，不作为新组件写法依据。

主题和动效见现有UI技能：motionRipple是有界动作反馈，负责按压/快速重入/禁用/减少动态效果清理；Tabs/Segmented保留原生指示器。需要改这些能力时同时查共享CSS、指令和对应测试，不在业务页复制机制。

## 现场融合：表格、3D和账号共用数据

`SiteConfigDialog.vue`协调现场数据与设备工作区。`EquipmentRoom3DWorkspace.vue`、设备清单/工作簿和服务器/硬件API须使用同一设备身份和归属模型。

- `equipmentInventory.rules.js`以设备来源和ID生成deviceKey，构建平台/设备目录及筛选；不能拿IP当唯一稳定身份。
- `equipmentWorkbook.rules.js`按SERVER/HARDWARE定义可编辑字段，`workbookRowChanges`仅提交变化，`workbookPayload`携带sourceType/sourceId/revision/changes。
- 密码显示值、输入值和待保存秘密分开；加载明文不应产生待提交修改，`******`不能保存成新密码。字段规范化不允许把密码trim或改大小写。
- 切机房会清空机柜/U位，切机柜会清空U位；位置必须完整、属于同一现场/机房且不超容量。前端校验不能代替后端占位/引用检查。
- `equipmentAccounts.rules.js`集中账号标识、名称规范、展示排序与mutation；服务器账号、设备密码与扫描候选池不能混为同一实体。
- 扫描/入库复核通过 `serverIntakeReview.rules.js`生成选中行payload及签名，结合后端指纹确认、有限尝试、归属和revision规则。改模板时要查跨模块导入和历史绑定兼容测试。

回归优先equipmentWorkbook、equipmentAccounts、serverScan、serverIntakeReview及后端对应Service，不以3D画面正常代替保存和导出一致性。

## 自动巡检：配置契约和结果展示分离

- `toolTargetContract.js`集中工具code→targetType约定；前端表单需与后端 `AutoInspectionToolContract`、目标BO、工具元数据一致。
- `comparisonScopeConfig.js`、`databaseTargetConfig.js`处理比较周期和数据库目标配置；时间窗、取值和只读限制仍以后端规则为准。
- `recordResultPresentation.js`统一结果标签/色调及步骤分组；`InspectionRecordResults.vue`接收record和formatters，将目标的本次/前值/变化、规则、原始调用证据和状态并列展示。不能只保留总状态而丢掉子项。
- `continuousHealthPresentation.js`按日期→现场→主平台/计划聚合；分母为max(expectedCount,completedCount)，保留缺失/跳过/异常及已恢复状态。页面显示不是后端历史基线更新入口。
- `AutoInspectionChart.vue`接收option/empty/viewKey并发出chart-click，同viewKey保留图表视角；挂载/激活渲染，失活/卸载断开ResizeObserver、取消帧并dispose。禁止对隐藏/零尺寸容器盲目初始化图表。
- cockpit/PlanMetricDashboard/ContinuousHealthPanel复用上述映射和组件，修改统计口径同时核对指标Service、窗口SQL、导出与下钻参数。

## 文档、知识、IPAM和原生任务宿主

| 模块 | 本地组件/规则 | 必须保持的行为 |
| --- | --- | --- |
| 文档 | `views/document/workspace/documentWorkspaceRules.js`、DocumentUploadDialog/ShareDrawer/StorageDrawer/RecordsDrawer | 文件种类、额度、剩余空间、scope、ACL期限、目录排序；413不显示为上传成功，前端“无限制”不代表Nginx/Servlet无限制 |
| 知识 | KnowledgeNavigation、KnowledgeHistoryDrawer、KnowledgeDocumentSelector | 目录/文章类型、版本恢复和引用文档可读性；不要在前端复制文件权限判断作为后端授权 |
| IPAM | IpamConfigWorkspace、Ipv4Input、IpamNetworkTree、工作簿及规则 | CIDR/地址状态/发放权限与批量事务；复用现有RevoGrid，不把探测在线改成已分配 |
| HDI宿主 | `views/governance/hdi/HdiTaskWorkspace.vue`、`hdiHostBridge.js` | 只接收同origin、指定frameWindow和规定消息类型；任务ID和内部路径严格验证，不把外部跳转转成任意平台URL |

原生任务保存队列实现在 `data-governance/hdi-native/host/native-task-host.mjs`：快速编辑先快照并串行提交，回读只更新权威身份/版本，保留更新的本地草稿；外来编辑/保存失败停止队列，显式成功重载后才恢复。不能用任意服务端回读覆盖画布，也不能在冲突后自动提升revision重试。`hdiSaveQueue.test.js`是重要契约依据。

前后端联动读取本技能对应backend参考。所有纯规则函数、组件props/events和后端权限变化同步验证；“官网有相似组件”不等于允许替换这些本地语义。

## 重新生成本地索引

先完成锁定依赖的离线安装，再运行：

```bash
node <skill-dir>/scripts/build_local_index.mjs --root <仓库> --output <离线包>/knowledge/local-source-index.json
```

依赖已有的 `@babel/parser` 和 `@vue/compiler-sfc`，不新增npm包。索引只输出结构元数据，不输出业务数据、配置值或方法正文；更换源码提交后必须重新生成。
