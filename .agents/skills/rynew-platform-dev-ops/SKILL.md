---
name: rynew-platform-dev-ops
description: 为 RYNEW 华东信息融合平台提供若依原生开发约定、当前工程规范、初始化部署、功能运维及后端业务链路指导。用于本仓库的开发、排障、交付或功能讲解；其他若依项目不自动沿用本平台的业务规则。
---

# RYNEW 平台开发与功能运维

把需求落到本平台的代码、权限、数据和运行入口。先识别任务属于开发、讲解、诊断还是实际运维，再读取对应参考；不要一次加载所有业务文档。

## 开始工作

1. 定位当前 Git 根目录，读取其 `AGENTS.md`；用户当轮指令和当前仓库规则优先于本技能快照。
2. 执行仓库规定的 preflight，报告目录、分支、HEAD、`origin/main`、工作区状态和 worktree。开发使用最新 `origin/main` 派生的独立 worktree，执行 `tools/codex-worktree.sh verify <module>`。只读讲解不必创建分支。
3. 运行本技能的只读检查器（`<skill-dir>` 换成当前技能目录）：

   ```bash
   python3 <skill-dir>/scripts/inspect_platform.py --root . --module all
   ```

   输出只含版本、配置键名、源码路径和摘要，不连接数据库、不启动应用、不打印配置值。可用 `--module site-fusion` 等缩小范围。详细位置见 [源码索引](references/source-map.md)。
4. 本技能核验基线为 **2026-11-06、`928a5ed2`、应用发布记录 v4.17.2**。POM 为 3.9.1、前端 package 为 3.15.2，三者不是同一个版本号。不同提交上重新核对相关方法、Mapper、SQL 和发布记录，不能靠目录名判断最新。
5. 跨模块追踪优先用已有 Graphify 报告/图谱定位；不存在时选相关子目录建立结构索引或直接做聚焦源码追踪。图谱仅供导航，业务结论回到源码验证。

## 按任务读取

| 任务 | 必读参考 |
| --- | --- |
| 新增/修改接口、页面、表、权限、版本 | [开发要求](references/development.md)；再读对应业务参考 |
| 初始化、Java 外部配置、JAR 启动、dist/Nginx、升级 | [部署初始化](references/deployment.md) |
| 了解平台功能、查页面/API/表的对应 | [平台功能地图](references/platform-map.md) |
| 现场、主子平台、设备机房、服务器扫描、密码池 | [现场融合业务](references/backend-site-fusion.md) |
| 模板/目标/计划、执行结果、健康统计、周月报 | [自动巡检业务](references/backend-auto-inspection.md) |
| 文件权限/额度/编辑器、知识目录/文章/版本 | [文档与知识业务](references/backend-content.md) |
| 原生 HDI、连接、执行版本、计划、产物、旧引擎边界 | [数据治理业务](references/backend-data-governance.md) |
| IPAM、白名单、旧巡检兼容代码 | [其他业务边界](references/backend-other.md) |
| 登录/权限/数据库/文件/任务故障、备份回退、交付 | [功能运维](references/operations.md) |

前端任务同时读取仓库内 `.agents/skills/ruoyi-element-plus-ui/SKILL.md`（存在时），遵守现有组件治理；没有该文件时仍须读取 `docs/frontend` 的规范。本技能不替换 UI 专项技能。

## 开发与运维的关键约束

- 若依原生结构：`Controller → Service → Mapper/XML → MySQL`；复用 `BaseController`、`AjaxResult`、`TableDataInfo`、`@PreAuthorize`、`@Log`、字典与现有菜单。参考本仓库代码生成模板，不套旧版 Spring/Vue 教程。
- 新接口权限、前端 `v-hasPermi`、菜单按钮 SQL 和文件/对象级权限须一致；隐藏按钮不构成授权。计划调度、归属检查、凭据脱敏和只读 SQL 限制不得为方便而绕开。
- 实际部署或数据库写入先确认本次授权目标、版本、停写/备份及核验回退。用户已明确授权的同一操作不重复询问；讲解和诊断不等于授权迁移、重启、扫描或执行业务任务。
- 主部署步骤只保留 MySQL、Redis、有效密钥、外部 Spring 配置和前端代理。根据本次启用模块再读其依赖，不把 Kafka、HDI、OnlyOffice 等一概当作基础启动条件。
- 发布按本次明确的模块清单裁剪。用户排除的模块需同时检查程序、菜单、SQL、任务、配置与手册；历史裁剪交付包不等于当前全平台源码已经永久移除该模块。
- 仓库/技能中只放无秘密示例。运行口令、初始化密码哈希、业务数据、加密密文、私有运行配置和构建制品不随技能提交。

## 交付结果

开发报告包括改动链路、所复用能力、独立升级 SQL/版本记录（适用时）、实际验证结果、提交和同步状态。解释/诊断报告给出代码路径、关键方法及实际证据，说明尚未验证的运行环节。部署报告区分“制品已构建、SQL已验证、目标已部署、页面和业务已验收”，不能互相替代。

可直接使用的请求：

- “用 $rynew-platform-dev-ops 按当前若依要求新增一个现场查询接口。”
- “用 $rynew-platform-dev-ops 准备一个新环境，先给出最简外部配置和初始化步骤。”
- “用 $rynew-platform-dev-ops 解释巡检计划为什么没有生成当日健康记录，先只读排查。”
- “用 $rynew-platform-dev-ops 追踪原生任务从发布到执行、产物下载的后端逻辑。”
