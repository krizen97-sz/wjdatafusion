# Codex 多窗口与 Git Worktree 开发规范

## 目标

避免多个 Codex 窗口在同一个目录中切换分支、覆盖文件或把不同模块的未提交修改混入同一个版本。核心关系固定为：

```text
一个可写 Codex 任务 = 一个独立 Worktree = 一个独立功能分支 = 一个模块修改范围
```

主仓库目录只承担集成、验收和发布，不承担并行功能开发。

## 推荐目录

```text
2026projects/
├── rynew/                         # 集成目录，最终保持 main 干净
└── rynew-worktrees/
    ├── auto-inspection-任务名/
    ├── platform-ui-任务名/
    ├── site-fusion-任务名/
    ├── document-management-任务名/
    ├── knowledge-center-任务名/
    └── ipam-任务名/
```

每个 Codex 窗口只打开其中一个目录。即使属于同一模块，两个同时写代码的窗口也必须使用不同 Worktree 和不同分支。

## 创建任务

优先在 Codex 新任务输入框选择 `Worktree`，起始分支选择干净的 `origin/main`。长期模块可以创建 Permanent Worktree。命令行也可以执行：

```bash
./tools/codex-worktree.sh create platform-ui icon-library
./tools/codex-worktree.sh create auto-inspection http-result-condition
./tools/codex-worktree.sh create site-fusion equipment-filter
./tools/codex-worktree.sh create document-management pdf-preview
./tools/codex-worktree.sh create knowledge-center article-versioning
./tools/codex-worktree.sh create ipam subnet-scan
```

脚本会先更新 `origin/main`，再创建唯一分支和相邻 Worktree，不会携带当前共享目录里的未提交修改。

进入 Worktree 后先验证：

```bash
./tools/codex-worktree.sh verify auto-inspection
```

验证失败时不得继续编辑。先处理基线落后、分支错误或跨模块文件污染。

## 模块边界

### 平台通用前端

- 图标系统：`src/assets/icons`、`IconSelect`、`SvgIcon`、`iconRegistry.js`
- 系统菜单中的通用图标选择入口及图标离线导入脚本
- 平台前端设计规范与图标库使用文档
- 普通任务不包含具体业务模块页面；业务页面中的单模块改动仍归对应业务模块
- 用户明确授权“全前端设计治理”时，可在同一 `platform-ui` 工作树中治理 `src/views`、`src/components`、`src/layout` 和 `src/assets/styles`，但不得改动 API、路由契约、权限字符、字段语义、后端或数据库，并须附全量 Guard、构建和浏览器审计证据

### 自动化巡检

- 前端：`src/views/support/autoInspection`、`src/api/support/autoInspection`
- 后端：`SupportAutoInspection*`、自动化巡检 Quartz 任务
- SQL：文件名包含 `auto_inspection` 或 `auto_version`

### 现场融合管理

- 前端：现场、平台、服务器、设备资产、组织人员相关页面与 API
- 后端：`SupportSite*`、`SupportServer*`、`SupportHardware*`、`SupportEquipment*`
- SQL：现场融合、服务器、设备资产独立升级脚本

### 文档管理

- 前端：`src/views/document`、`src/api/document`
- 后端：`Document*`、`Doc*`
- SQL：文件名以 `document_management` 开头

### IP 分配管控

- 前端：`src/views/ipam`、`src/api/ipam`
- 后端：`Ipam*`
- SQL：文件名以 `ipam_` 开头

### 知识中心

- 前端：`src/views/knowledge`、`src/api/knowledge`
- 后端：`Knowledge*`、`Kb*`
- SQL：文件名以 `knowledge_center` 开头
- 文档：文件名包含 `KNOWLEDGE_CENTER` 或“知识中心”

## 共享文件

以下文件是高冲突区，允许模块修改，但提交前必须单独复核，不能通过整文件覆盖解决冲突：

- `RuoYi-Vue3-master/src/views/support/version/releaseNotes.js`
- `RuoYi-Vue3-master/src/router/index.js`
- `RuoYi-Vue3-master/package.json`
- 各级 `pom.xml`
- `application.yml`
- `support_v1.sql`、`support_deploy_all.sql`
- `RouterVo.java`、`SysMenuServiceImpl.java`、全局异常处理

功能分支优先新增独立升级 SQL。全量 SQL 在合并阶段统一维护，避免多个模块同时修改同一大文件。

## 提交前检查

```bash
./tools/codex-worktree.sh verify <module>
git status --short
git add <明确文件列表>
git diff --cached --name-only
git diff --cached --check
```

禁止使用 `git add .`。生成目录、截图、交付包、构建产物和本地缓存不进入功能提交。

## 合并流程

1. 模块 Worktree 完成测试、构建和浏览器验收。
2. 提交版本记录和独立升级 SQL，推送模块分支。
3. 在干净集成 Worktree 或 GitHub PR 中检查共享文件冲突。
4. 合并到 `main` 后重新执行前后端构建。
5. 从合并后的 `main` 提交或版本标签创建 detached Worktree 制作部署包。

任何部署包都必须记录源码提交号，并确认：

```bash
git status --porcelain
git rev-parse HEAD
```

第一条命令必须无输出。不得再从包含多个模块未提交修改的共享目录直接打包。

## Codex 窗口启动提示

建议每个新任务都以这段话开头：

```text
开始修改前先执行仓库 AGENTS.md 的 Mandatory preflight，并运行
tools/codex-worktree.sh verify <当前模块>。
如果目录、分支、origin/main 基线或模块边界不符合要求，停止编辑并先完成隔离。
```
