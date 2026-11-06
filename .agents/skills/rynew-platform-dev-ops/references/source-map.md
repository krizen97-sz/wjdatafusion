# 源码索引、使用与维护

## 技能位置和使用

项目内入口为 `.agents/skills/rynew-platform-dev-ops/SKILL.md`，自动发现保持开启。在含本技能的仓库中可直接要求“使用 $rynew-platform-dev-ops …”。

远端源码库：`https://github.com/krizen97-sz/wjdatafusion`。技能随本次功能分支发布，未合并前需打开包含它的分支；不要把分支发布说成main集成。迁移到其他Codex环境时复制**整个技能目录**到该环境支持的skills目录，仍须把 `--root` 指向实际RYNEW源码，不能只复制SKILL.md丢失references/scripts。

本技能没有生产凭据、连接参数、业务备份或厂商程序包；不要求GitHub/MCP账号用于本地读取。

官方文档离线位置和版本适配见 [官方资料](official-ruoyi.md)，自编组件/规则见 [本地组件契约](local-components.md)。Windows工具、锁文件、专用Maven/npm库及复核脚本见 [离线开发](offline-development.md)。依赖二进制和官网镜像在本地迁移包中，不作为Git源码变更推送。

## 只读检查器

```bash
python3 .agents/skills/rynew-platform-dev-ops/scripts/inspect_platform.py --root . --module all --check
python3 .agents/skills/rynew-platform-dev-ops/scripts/inspect_platform.py --root . --module auto-inspection --check
```

仅需Python3标准库和Git。输出当前提交/分支、origin/main祖先关系、脏项数量、版本、npm脚本名、表名、YAML键名、入口文件SHA-256及missing_sources；不输出YAML值、环境值、配置文件内容或原始remote认证信息。它不fetch、不写仓库、不运行SQL、不启动服务。

`--check`在入口文件丢失时非零退出，帮助发现重命名或旧版本仓库；文件存在和摘要有值不证明业务行为正确。键名扫描是缩进映射清单，不是完整YAML求值器；模块代码中的 `@ConfigurationProperties/@Value` 仍须按任务追踪。

## 路径简写与定位

| 简写 | 仓库相对目录 |
| --- | --- |
| BJAVA | `WDF100.0/wjdatafusion-manage/src/main/java/com/hm/manage` |
| BXML | `WDF100.0/wjdatafusion-manage/src/main/resources/mapper` |
| QJAVA | `WDF100.0/wjdatafusion-quartz/src/main/java/com/hm/quartz` |
| FJAVA | `WDF100.0/wjdatafusion-framework/src/main/java/com/hm/framework` |
| UI | `RuoYi-Vue3-master/src` |

| 问题 | 直接定位 |
| --- | --- |
| 若依CRUD/校验/响应 | `WDF100.0/wjdatafusion-generator/src/main/resources/vm`；common的 `BaseController`、`AjaxResult`、`TableDataInfo` |
| 登录与权限 | FJAVA `web/service/SysLoginService.java`、`TokenService.java`、`PermissionService.java`；`config/SecurityConfig.java`、`aspectj/DataScopeAspect.java`；UI `utils/request.js` |
| 现场聚合/主子约束 | BJAVA `controller/SupportSiteController.java`、`service/impl/SupportSiteServiceImpl.java`、`SupportPlatformServiceImpl.java`；BXML `support` |
| 设备工作簿/拓扑 | BJAVA `service/impl/SupportEquipmentWorkbookService.java`、`SupportEquipmentTopologyServiceImpl.java`；UI `views/support/site` |
| 扫描/认证/同步 | BJAVA `service/impl/SupportServerScanService.java`、`SupportServerScanSyncService.java`；`service/support/SupportServerPasswordPoolMatcher.java`、`SupportServerLoginVerifier.java` |
| 凭据加密 | BJAVA `service/support/CredentialCryptoService.java`、`config/SupportCredentialProperties.java` |
| 巡检执行/规则 | BJAVA `service/impl/SupportAutoInspectionServiceImpl.java`、`AutoInspectionValueComparison.java`、`AutoInspectionComparisonWindow.java`、`AutoInspectionToolContract.java` |
| 计划不执行 | QJAVA `controller/SupportAutoInspectionPlanController.java`、`service/SupportAutoInspectionPlanScheduleService.java`、`task/SupportAutoInspectionTask.java` |
| 日健康/驾驶舱/报表 | BJAVA `service/impl/AutoInspectionPlanHealthConfig.java`、`SupportAutoInspectionMetricsServiceImpl.java`、`AutoInspectionPeriodReport.java`；BXML `support/SupportAutoInspection*Mapper.xml` |
| 文档权限/额度 | BJAVA `service/impl/DocumentWorkspaceServiceImpl.java`；BXML `document/DocumentWorkspaceMapper.xml` |
| 文档文件/编辑器 | BJAVA `service/document/DocumentStorageService.java`、`OnlyOfficeDocumentEditorProvider.java`、`DocumentJwtService.java`；`config/DocumentManagementProperties.java` |
| 知识版本/文档引用 | BJAVA `service/impl/KnowledgeCenterServiceImpl.java`、`service/knowledge/KnowledgeHtmlSanitizer.java`；BXML `knowledge` |
| 原生任务/连接/运行 | BJAVA `service/governance/hdi/HdiTaskStore.java`、`HdiCoreConnections.java`、`HdiCoreRuns.java`、`HdiCoreSchedules.java`；`data-governance/hdi-native` |
| IPAM配置提交/扫描 | BJAVA `service/impl/IpamServiceImpl.java`、`IpamWorkbookServiceImpl.java`、`IpamScanServiceImpl.java`；BXML `ipam` |
| 白名单外部库/消息 | BJAVA `service/impl/WhitelistPlateServiceImpl.java`、`WhitelistFilterDataServiceImpl.java`；`config/WhitelistExternalDataSourceConfig.java` |
| 当前SQL入口 | `WDF100.0/sql/README.md`、`rynew_init_latest.sql`、`rynew_schema_latest.sql`；`tools/database` |
| 版本与工程规范 | `AGENTS.md`、`docs/CODEX_WORKTREES.md`、`docs/frontend`；UI `views/support/version/releaseNotes.js` |

精确搜索优先 `rg --files <模块>`、`rg -n '<符号>' <已定位目录>`。表结构追踪到对应Mapper及独立升级SQL；不要对整个磁盘搜索凭据或复制application配置作为解释证据。

## 维护技能

业务发生实际变更时更新受影响参考及检查器入口，而不是将整份源码或历史手册拷入技能。修改基线说明须重新核验代码；版本号仍从源码提取，不把过去的测试数字写成固定要求。

维护后运行可用skill-creator的 `scripts/quick_validate.py` 检查frontmatter/脚手架；执行本检查器 `--module all --check`，检查相对引用、代码路径和示例是否仍有效。用下列真实请求检视决策是否正确：

| 请求 | 应得到的行为 |
| --- | --- |
| 在已有业务库上“初始化” | 识别已有库与升级基线，不执行空库完整入口 |
| 普通现场部署要不要Kafka | 主部署不加全局Kafka；按确切启用工具另读业务依赖 |
| 新增若依页面 | 读UI治理、做复用计划，前后端/菜单权限闭环，不另建组件体系 |
| 巡检计划有行但不触发 | 跨到quartz的计划同步和任务，核对job/cron/有效窗口 |
| HDI试运行可否随意测试 | 说明真实输出/消费副作用，使用授权测试目标并回读 |
| IPAM密钥怎么填 | 核对当前消费者；此基线不编造IPAM_CREDENTIAL_KEY要求 |
| 检查脚本对着非RYNEW目录 | 非零退出，不扫描或修改其内容 |

发布前显式暂存技能目录与必要工作流接入，审查diff及秘密，推送后核对远端branch commit。图谱输出、运行检查JSON和其他生成制品不提交。
