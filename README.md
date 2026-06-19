# wjdatafusion

武进信息融合平台，基于 RuoYi / Spring Boot / Vue 3 改造，当前仓库以“现场融合管理”和“自动化巡检”为核心业务扩展，覆盖现场关系配置、服务器资产、组织人员、留言操作记录，以及可配置巡检模板、计划和报告等能力。

## 项目结构

```text
.
├── WDF100.0/                         # 后端工程和数据库脚本
│   ├── wjdatafusion-admin/            # 后端启动模块
│   ├── wjdatafusion-manage/           # 业务模块，现场融合管理和自动化巡检主要后端代码
│   ├── wjdatafusion-system/           # 系统、权限、用户等基础能力
│   ├── wjdatafusion-framework/        # 安全、配置、通用框架能力
│   ├── sql/                           # 初始化脚本、现场融合和自动化巡检升级脚本
│   └── doc/                           # 操作手册与截图资产
├── RuoYi-Vue3-master/                 # Vue 3 + Element Plus 前端工程
│   └── src/views/support/             # 现场融合管理、自动化巡检前端页面
├── docs/                              # 过程文档或交付文档
├── DESIGN.md                          # 现场融合管理前端设计规范
└── README.md                          # 当前说明文件
```

## 核心功能

- 现场管理：维护现场基础信息、行政区划、现场编码和配置画布。
- 融合关系画布：以横向树/纵向树展示现场、主平台、子平台、页面、服务器、人员之间的关系，支持全屏操作。
- 平台管理：区分主平台、子平台和网络环境，网络环境支持公安网、图像网、政务网、二类区、党政军、私网等内置类型。
- 设备资产管理：统一维护服务器、解码器、终端、交换机、网闸等现场设备；服务器保留单个添加、批量添加、xlsx 模板导入、批量导出、批量删除和密码查看能力，非服务器设备支持登录账号和密码配置。
- 组织与人员：支持组织、联系人、联系人角色维护，并在画布人员标签中展示角色信息。
- 操作记录：记录现场融合管理中的新增、修改、删除操作，并在画布侧边展示详情。
- 现场导入导出：按选中现场导出完整 zip 数据包，每个现场一个 xlsx；导入 zip 后按新现场重建关系数据。
- 现场留言板：支持按现场发布留言、详情列表查看、弹幕展示和轻量轮询刷新。
- 自动化巡检：以 Kafka、HTTP、FTP、服务器目录、磁盘检测等基础工具配置巡检目标、巡检模板和巡检计划，定期执行后生成详情、Excel 和 Word 报告。
- 版本记录：维护现场融合与自动化巡检功能版本号、修改内容、提交时间和涉及 SQL 脚本，画布自动显示最新版本号。

## 技术栈

- 后端：Java 17、Spring Boot 4、Spring Security、MyBatis、Druid、PageHelper、Redis、MySQL。
- 前端：Vue 3、Vite、Element Plus、Pinia、Axios。
- 文档与数据包：Apache POI、xlsx、zip。

## 本地启动

### 1. 准备环境

- JDK 17+
- Maven 3.8+
- Node.js 18+
- MySQL
- Redis

### 2. 初始化数据库

后端默认使用 MySQL。新环境可先导入基础若依脚本，再导入现场融合脚本：

```bash
mysql -u root -p rynew < WDF100.0/sql/ry_20260320.sql
mysql -u root -p rynew < WDF100.0/sql/quartz.sql
mysql -u root -p rynew < WDF100.0/sql/support_deploy_all.sql
```

如果是已有环境升级，优先按 `WDF100.0/sql/support_upgrade_*.sql` 的版本顺序执行对应升级脚本。`support_deploy_all.sql` 是现场融合管理的整合部署脚本，执行前建议先备份数据库。

### 3. 后端启动

按实际环境调整：

- `WDF100.0/wjdatafusion-admin/src/main/resources/application.yml`
- `WDF100.0/wjdatafusion-admin/src/main/resources/application-druid.yml`

启动命令：

```bash
cd WDF100.0
mvn -pl wjdatafusion-admin -am spring-boot:run
```

默认后端端口为 `8080`。

### 4. 前端启动

```bash
cd RuoYi-Vue3-master
npm install
npm run dev
```

前端开发服务端口在 `RuoYi-Vue3-master/vite.config.js` 中配置，当前默认是 `80`，接口代理到 `http://localhost:8080`。

## 构建验证

后端编译：

```bash
cd WDF100.0
mvn -pl wjdatafusion-manage -am -DskipTests compile
```

前端生产构建：

```bash
cd RuoYi-Vue3-master
npm run build:prod
```

## 现场融合管理入口

前端主要页面：

- `RuoYi-Vue3-master/src/views/support/site/index.vue`
- `RuoYi-Vue3-master/src/views/support/site/SiteConfigDialog.vue`
- `RuoYi-Vue3-master/src/views/support/server/index.vue`
- `RuoYi-Vue3-master/src/views/support/org/index.vue`
- `RuoYi-Vue3-master/src/views/support/version/index.vue`

后端主要接口：

- `WDF100.0/wjdatafusion-manage/src/main/java/com/hm/manage/controller/SupportSiteController.java`
- `WDF100.0/wjdatafusion-manage/src/main/java/com/hm/manage/controller/SupportPlatformController.java`
- `WDF100.0/wjdatafusion-manage/src/main/java/com/hm/manage/controller/SupportServerController.java`
- `WDF100.0/wjdatafusion-manage/src/main/java/com/hm/manage/controller/SupportOrgController.java`
- `WDF100.0/wjdatafusion-manage/src/main/java/com/hm/manage/controller/SupportSiteMessageController.java`

数据库脚本：

- `WDF100.0/sql/support_v1.sql`
- `WDF100.0/sql/support_deploy_all.sql`
- `WDF100.0/sql/support_upgrade_*.sql`

## 权限说明

现场融合管理使用 `support:*` 权限字符控制菜单和按钮。拥有 `datafusion` 权限字符的用户，默认具备现场融合管理全部权限。

常用权限包括：

- `support:site:*`：现场查询、新增、修改、删除、导入、导出。
- `support:platform:*`：平台查询、新增、修改、删除、导出。
- `support:server:*`：服务器查询、新增、修改、删除、导出。
- `support:org:*`：组织和联系人查询、新增、修改、删除、导出。
- `support:message:list`：留言查看。
- `support:message:add`：留言发布。
- `support:hardwareAsset:query`：硬件资产查询。
- `support:hardwareAsset:add`：硬件资产新增。
- `support:hardwareAsset:edit`：硬件资产修改。
- `support:hardwareAsset:remove`：硬件资产删除。
- `support:hardwareAsset:export`：硬件资产导出。
- `support:equipment:query`：统一设备资产清单查询。
- `support:equipment:export`：统一设备资产清单导出。
- `support:credential:viewPlain`：敏感凭据明文查看。

## 文档

- 前端设计规范：`DESIGN.md`
- 现场管理操作手册：`WDF100.0/doc/现场管理功能操作手册.md`
- Word 版操作手册：`WDF100.0/doc/现场管理功能操作手册.docx`
- 手册截图资产：`WDF100.0/doc/site-management-manual-assets/`
- 自动化巡检操作手册：`WDF100.0/doc/自动化巡检功能操作手册.md`
- 自动化巡检手册截图资产：`WDF100.0/doc/auto-inspection-manual-assets/`

## 当前版本

当前功能版本为 `v3.6.15`，该版本为自动化巡检补充可随部署携带的完整操作手册，并在巡检配置页新增操作指引入口，帮助用户按模板、步骤、计划、记录的顺序完成配置。

## 版本提交规范

后续每次整理成版本号并提交到 GitHub 时，需要同步补齐版本描述信息，避免只有代码提交和标签，缺少部署与业务说明。

每次版本提交至少包含：

- 更新 `RuoYi-Vue3-master/src/views/support/version/releaseNotes.js`，补充版本号、提交时间、版本类型、标题、概要、修改明细、影响范围、数据库说明和 SQL 脚本路径。
- 如果涉及数据库调整，新增独立升级脚本，文件名带版本号后缀，例如 `support_upgrade_yyyyMMdd_xxx_v2_3_1.sql`，并同步维护 `support_v1.sql` 和 `support_deploy_all.sql`。
- 如果涉及前端页面调整，按 `DESIGN.md` 检查布局、权限分流、空态、错误态、长文本、窄屏和画布交互。
- Git commit message 写清版本或功能主题，例如 `feat: release support message board v2.3.0`、`fix: release site message fixes v2.3.1`。
- Git tag 使用版本号，例如 `v2.3.1`，并在 GitHub Release 中补充对应描述。
- Release 描述需要包含：功能概要、主要修改、涉及 SQL、验证结果和部署注意事项。

建议的 Release 描述格式：

```markdown
## 概要

一句话说明本版本解决的问题。

## 主要修改

- 修改点 1
- 修改点 2

## 数据库脚本

- WDF100.0/sql/xxx.sql

## 验证结果

- 后端编译通过
- 前端构建通过

## 部署说明

执行脚本前先备份数据库；无数据库变更时写“无”。
```
