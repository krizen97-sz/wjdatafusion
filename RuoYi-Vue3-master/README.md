# 华东信息融合平台前端

平台前端基于 Vue 3、Vite、Element Plus、Pinia 和 ECharts，主要承载现场融合管理、自动化巡检、文档管理、IP 分配管控与系统运维能力。

## 本地开发

```bash
npm install
npm run dev
```

开发端口默认使用 `80`，接口默认代理到 `http://127.0.0.1:8080`。独立 worktree 联调时可通过环境变量覆盖：

```bash
VITE_DEV_PORT=5173 VITE_PROXY_TARGET=http://127.0.0.1:18080 npm run dev
```

## 构建

```bash
npm run build:prod
```

生产产物输出到 `dist/`。部署时应完整替换旧静态目录，避免新旧哈希文件混用。

## 设计与交付

- 项目设计规范：`../DESIGN.md`
- 自动化巡检操作手册：`public/docs/auto-inspection/auto-inspection-manual.html`
- 多窗口开发规范：`../docs/CODEX_WORKTREES.md`

前端修改提交前至少执行相关 Node 测试、生产构建，并分别检查浅色与深色主题。
