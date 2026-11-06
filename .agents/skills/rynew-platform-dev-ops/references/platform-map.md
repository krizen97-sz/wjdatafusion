# 平台功能地图

功能入口以登录后的 `/getRouters` 和当前用户权限为准。下表中的API路径是**后端路径**，不等于浏览器路由；前端由 `src/api` 包装，在生产代理下通常加 `/prod-api`。查具体页面时再核对菜单component、Router和目标Vue文件。

## 从功能找到代码

路径简写：前端根为 `RuoYi-Vue3-master/src`；业务Java根为 `WDF100.0/wjdatafusion-manage/src/main/java/com/hm/manage`。

| 功能 | 前端 | 后端入口 | 数据/业务核心 |
| --- | --- | --- | --- |
| 用户、角色、部门、岗位、菜单、字典、参数 | `views/system`、`api/system` | admin模块 `web/controller/system` | system模块；`sys_user/role/menu/dept/post/dict/config`及关系表 |
| 登录、审计、在线会话、缓存、任务 | `views/monitor` | admin监控、framework认证、quartz | Redis登录会话、`sys_logininfor/oper_log/job/job_log`、`QRTZ_*` |
| 现场融合 | `views/support/site`；`api/support/site.js` | `/support/site` → `SupportSiteController` | `sup_site`，工作台聚合平台/设备/人员/关系 |
| 主子平台、组织联系人、地址入口 | `views/support/platform`及现场配置组件 | `/support/platform`等 `SupportPlatform/Org/Contact/SubplatformEndpointController` | `sup_platform/org/contact/subplatform_endpoint`和关系表 |
| 设备、机房、机柜、拓扑和工作簿 | `views/support/site`中的设备与3D工作区 | `SupportEquipment/EquipmentLocation/HardwareAssetController` | `sup_server/hardware_asset/equipment_room/equipment_cabinet/equipment_link` |
| 服务器账号、登录检查、密码池扫描 | 现场工作台中的设备/扫描入口 | `SupportServerController`、`SupportServerScanController` | 服务器、账号、扫描资料、独立候选密码；见现场参考 |
| 自动巡检 | `views/support/autoInspection`、`api/support/autoInspection` | `/support/autoInspection`；计划在quartz的 `/support/autoInspection/plans` | 11张 `sup_auto_inspection_*`，见巡检参考 |
| 文档工作台、协作和编辑 | `views/document`、`api/document/workspace.js` | `/document/workspace` → `DocumentWorkspaceController` | `doc_document/folder/version/acl/user_quota/operation_log`及磁盘文件 |
| 知识中心 | `views/knowledge`、`api/knowledge` | `/knowledge` → `KnowledgeCenterController` | 空间、目录/文章、版本、标签、文档引用六表 |
| 数据治理原生任务 | `views/governance/workspace`、`views/governance/hdi`、`api/governance/hdi.js` | `DataGovernanceHdiCore*Controller`、`/detl/**`适配和 `/governance/hdi`运行记录 | `service/governance/hdi`、十张 `dg_hdi_*`、私有状态/产物 |
| 数据治理保留入口 | `views/governance`、`api/governance` | `/governance`、`/governance/kettle`相关控制器 | NiFi/Kettle/Kafka/计划交付，不能与原生任务链混用 |
| IPAM | `views/ipam`、`api/ipam` | `/ipam`、`/ipam/workbook`、`/ipam/scan`、`/ipam/dashboard` | 网络、网段、地址状态、工作簿提交、租约扫描；13张表 |
| 白名单（按交付启用） | `views/whitelist`、`api/whitelist` | `/whitelist/plate`、`/whitelist/filterData` | 外部PostgreSQL白名单，本地MySQL `wl_filter_data`，独立Kafka操作 |
| 旧巡检（兼容） | 不作为新巡检入口开发 | `SupportTimInspection*` | 八张 `sup_tim_inspection*`；新库对应菜单隐藏停用，不误当新计划 |

## 常见操作路径

- 基础管理：建立部门/岗位/用户 → 配置角色菜单与按钮 → 用户重新登录验证权限 → 查看操作/登录日志。
- 现场：建现场 → 建主平台/网络环境 → 建子平台 → 配置设备、组织和主平台联系人 → 维护机房/机柜/U位/链路 → 检查归属和导出工作簿。
- 巡检：识别工具元数据 → 配置目标 → 配置模板有序步骤及目标绑定 → 建计划/范围/时间窗/健康规则 → 手动或周期运行 → 记录钻取到目标结果 → 日健康/周月报。
- 文档：设额度 → 建目录/上传或新建文件 → 分享指定权限及期限 → 在线查看/编辑 → 查版本、下载和审计。知识中心可引用当前可读文档，引用不授予文档权限。
- 数据治理：配置执行资源/连接策略 → 建用户所属连接 → 真实元数据选表/主题 → 画布任务保存/发布 → 试运行/正式运行 → 查冻结版本、原日志、产物及目标回读。

功能讲解应给出本次用户的菜单路径、权限和最小真实操作。没有运行环境时清楚标注来自源码；不要编造已登录页面、截图或业务验收。
