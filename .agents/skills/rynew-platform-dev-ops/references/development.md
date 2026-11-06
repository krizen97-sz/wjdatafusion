# 若依原生约定与当前开发要求

原生机制以 [官网开发文档与FAQ](official-ruoyi.md) 为基础，再用本地模板和当前实现核对版本。公共/业务组件的状态、事件和领域约束见 [本地组件契约](local-components.md)，离线工具与依赖冻结见 [离线开发](offline-development.md)。

## 技术基线与源码位置

以下是核验提交的依赖，不是升级建议；以当前 `package.json` 和 POM 为准。

| 层 | 当前实现 | 位置 |
| --- | --- | --- |
| 主前端 | Vue 3.5.26、Element Plus 2.13.1、Vite 6.4.1、Pinia 3.0.4 | `RuoYi-Vue3-master` |
| 遗留前端 | Vue 2 / Element UI | `WDF100.0/wjdatafusion-ui`，普通任务不混用或迁移 |
| Java | Java 17、Spring Boot 4.0.3、MyBatis 4.0.1、Druid | `WDF100.0/pom.xml` |
| 启动/API聚合 | `RuoYiApplication`、系统控制器 | `WDF100.0/wjdatafusion-admin` |
| 框架/系统 | Security、Token、数据范围；用户角色菜单字典 | `wjdatafusion-framework`、`wjdatafusion-system` |
| 业务 | 业务 Controller、BO/VO、Service、Mapper/XML | `wjdatafusion-manage` |
| 调度 | Quartz、巡检计划控制器、文档权限到期任务 | `wjdatafusion-quartz` |
| 生成模板 | 本地模板是原生 CRUD 最直接依据 | `wjdatafusion-generator/src/main/resources/vm` |

Java Servlet/校验使用项目现有 `jakarta.*`；不要照抄旧版 `javax.servlet` 或旧 Security 配置。

## 若依原生开发链路

1. 先找同业务的接口、领域模型、Mapper/XML，再对照本地生成模板。普通业务落在 `com.hm.manage`；系统能力继续复用 system/framework/common。
2. Controller 继承 `BaseController`。分页查询紧接 `startPage()` 执行目标查询，再用 `getDataTable(list)` 返回 `{code,msg,rows,total}`；中间不要插入会被分页的无关查询。详情/动作使用 `success(data)`、`toAjax(rows)` 或现有 `AjaxResult` 结构。
3. 新接口明确 BO/查询对象、VO 和校验；参照自动巡检现有 BO/VO，不把数据库字段、内部 Map 或密文无差别暴露给前端。
4. 用 `@PreAuthorize("@ss.hasPermi('模块:资源:动作')")` 保留后端授权。写入/导入/导出按现有 `@Log` 与 `BusinessType` 审计；请求级日志与业务变更记录各有用途。
5. 业务规则放 Service：归属、状态流转、引用约束、额度、冲突检测。多表写入使用现有事务边界，并单独处理文件和远端副作用；数据库回滚不会自动撤销磁盘文件、消息确认或远程输出。
6. Mapper 接口与 XML 的 namespace、方法、`@Param` 对齐；复用 `#{}` 绑定值。动态列名/排序先走现有白名单/排序校验，不能拼接用户输入。审计字段、逻辑删除和状态编码按本模块现有约定维护。
7. 现有数据范围能力看 `DataScopeAspect` 和系统用户/角色的 Service。不要假定每个业务表自动有数据隔离：文档 owner/ACL、HDI 用户域等仍由各 Service 明确校验。
8. 复用 `ExcelUtil`、现有文件/下载能力和字典服务。业务专用工作簿有独立事务/预览/提交契约时，沿用该契约，不换成普通 CRUD 导入覆盖。

最小权限闭环：菜单/按钮 `sys_menu.perms` → 角色授权 → `/getInfo` 权限集合 → `v-hasPermi` → Controller 授权 → Service 对象级检查。菜单路径、Vue组件字符串和 RouterVo 契约改动作为共享文件单独审查。

来源：`vm/java/controller.java.vm`、`vm/xml/mapper.xml.vm`、`vm/vue/v3/index.vue.vm`、`BaseController.java`、`SupportSiteController.java`、`SysUserServiceImpl.java`。

## 当前前端要求

开始页面编码前完整读取：

- `docs/frontend/DESIGN.md`
- `docs/frontend/COMPONENT-CATALOG.md`
- `docs/frontend/REFERENCE-PAGES.md`
- 当前 `RuoYi-Vue3-master/package.json` 和 `src/components`
- 本模块及至少两份相似页面；每个计划使用的核心组件都找到现有用法。

复用顺序：本模块 → 公共组件 → 若依页面 → 已安装 Element Plus → 图标/主题变量 → 经重复需求证明的新公共组件 → 页面自绘。

编码前写明：

| 页面需要 | 现有实现 | 选择组件 | 参考文件 | 必需自定义 |
| --- | --- | --- | --- | --- |

有截图/原型时再读 `PROTOTYPE-ADAPTATION.md`，列出“原型区域、产品目标、项目组件、参考页、适配方式”。保留业务目标和层级，视觉表达服从当前组件系统。

常规列表沿用 `.app-container`、查询 `el-form`、`RightToolbar`、`el-table`、`Pagination` 和表单弹窗。状态字典用 `DictTag`，固定状态用 `el-tag`，可修改布尔值用 `el-switch`，详情用 `el-descriptions`，长上下文用 `el-drawer`。API 封装放 `src/api`，继续走 `@/utils/request`，不要旁路 Token/重复提交/业务错误处理。

主题用 `--app-*`、`--surface-*`、`--el-*` 等已有语义变量；主色用于行动、选择、焦点和真实状态。当前动效方向为 Precision Rail：Tabs 原生活动条，Segmented 原生选中面；动作跟随真实 loading/success/error，尊重 reduced-motion。

禁止新建第二套 UI/图标体系、业务页 inline SVG、Emoji 图标、切片截图交互、普通表单 Canvas、页面级全局控件重写。真实3D、图表、工作流/表格例外走 `UI-EXCEPTIONS.md` 和 Guard allowlist；历史偏离不自动构成新例外。

## 工作树、版本和SQL

- 按 `docs/CODEX_WORKTREES.md` 创建独立模块 worktree；跨模块方案分清实现边界，不把别的任务脏文件混入。
- 共享文件（releaseNotes、router、package、POM、全量SQL、application配置）在集成时逐项审查。本技能目录是平台级共享文档路径，也须审查。
- 页面/功能改动做小版本提交并维护 `src/views/support/version/releaseNotes.js` 对应记录；纯技能文档维护不虚增业务版本。
- 表变化先给模块独立升级SQL与检查/回退说明；已有库按基线选入口。`CREATE TABLE IF NOT EXISTS` 不能修复已有错误结构。
- 全平台结构快照及完整初始化由 `tools/database/schema-latest.mjs`、`init-latest.mjs` 维护。实际结构变化时按当前 SQL README 更新来源并重新生成/验证，不能只改快照掩盖缺少迁移。
- 显式路径暂存，检查 `git diff --cached --name-only` 和 `git diff --cached --check`。提交技能/源码，不提交运行数据、图谱、dist、JAR、截图和交付包。

## 验证命令按改动选择

后端在 Maven 多模块根目录运行，例如：

```bash
mvn -f WDF100.0/pom.xml -pl wjdatafusion-manage -am \
  -Dtest=SupportEquipmentServiceImplTest -Dsurefire.failIfNoSpecifiedTests=false test
mvn -f WDF100.0/pom.xml -pl wjdatafusion-admin -am package -DskipTests
```

后者只证明构建，不证明测试。将测试名替换为当前真实类；涉及 Quartz 的接口同时纳入 quartz 模块测试。不要批量启动名称含 Live/本地只读库的环境测试；先核对测试开关和目标。

前端从 `RuoYi-Vue3-master` 运行：

```bash
npm run ui:guard
npm run test:site-fusion
npm run verify:frontend
```

第二条替换为受影响模块已有脚本；完整前端交付在依赖可用时执行 `verify:frontend`，其中含构建和 Chrome 64+ 兼容扫描。文档模块目前没有单独 npm script，使用现有 `node --test src/views/document/__tests__/*.test.js`。仅技能文档改动无须业务构建。

真实页面变更还要验证路由、权限、失败恢复、长文本、浅/暗色、键盘、控制台。声称“已同步主目录/已发布”前核对实际集成提交、来源文件摘要和运行进程路径；只推送功能分支不能称已集成主线。
