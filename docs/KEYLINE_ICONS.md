# 平台图标库使用规范

平台图标库由两部分组成：115 个既有平台图标，以及 547 个 Keyline Stroke 扩展图标。所有资源均随前端包离线发布，不在运行时访问外部站点。

## 使用优先级

1. Element Plus 控件自身的新增、删除、搜索、关闭等图标，继续使用 `@element-plus/icons-vue`。
2. 菜单、业务模块和自定义操作优先复用已有平台 SVG。
3. 现有平台图标缺少合适语义时，从 `keyline-*` 扩展图标中选择。
4. 不为单个页面手工绘制、拼接或复制新的 SVG 图标。

## 使用方式

Keyline 源文件存放在：

```text
RuoYi-Vue3-master/src/assets/icons/svg/keyline/<name>.svg
```

Vite 构建时会把目录名加入 Symbol ID，因此组件中使用 `keyline-` 前缀：

```vue
<svg-icon icon-class="keyline-server" />
<svg-icon icon-class="keyline-file-check" />
<svg-icon icon-class="keyline-layout-dashboard" />
```

菜单配置保存的也是完整名称，例如 `keyline-server`。既有菜单图标名称不需要迁移。

## 查找图标

系统管理的菜单维护弹层按来源和分类展示图标，支持中文、英文、完整拼音和拼音首字母搜索。扩展图标分类包括：

- 方向箭头、Git版本、文件文档、日期时间、消息通知
- 交易物流、地图位置、影音媒体、图表指标、设备开发
- 指针交互、布局面板、用户人员、常用操作、控件状态
- 奖项排名、形状进度、网络网页、天气主题

设计和开发时应先在图标选择器中确认最终图形，避免只按文件名猜测语义。

## 来源和版本

- 上游项目：<https://github.com/keyline-icons/keyline-icons>
- 导入版本：`@keyline-icons/cli 0.1.4`
- 固定提交：`403f023d0861d01807cdec045b5fb3fec984468d`
- 导入范围：547 个 Stroke SVG
- 本地协议：`RuoYi-Vue3-master/src/assets/icons/KEYLINE_LICENSE.txt`
- 来源记录：`RuoYi-Vue3-master/src/assets/icons/KEYLINE_NOTICE.txt`
- 完整性摘要：`RuoYi-Vue3-master/src/assets/icons/KEYLINE_MANIFEST.json`

不引入 Keyline React、CLI 或 MCP 运行依赖。CLI 版本号只用于记录上游分发版本。

## 更新方式

更新前先审核上游版本、许可证、文件数量、命名变更和视觉差异。确认后修改导入脚本中的固定版本与提交，再从已检出的上游仓库执行：

```bash
cd RuoYi-Vue3-master
node scripts/import-keyline-icons.mjs --source /absolute/path/to/keyline-icons
```

导入脚本会验证 Git 提交、版本、547 个文件的 `24×24`、`currentColor`、安全内容和命名约定，并重新生成许可证、来源记录、搜索数据及聚合 SHA-256。

更新完成后至少执行：

```bash
npm run test:icons
npm run build:prod
```

未经单独确认，不批量替换既有 `sys_menu.icon`，不修改 Keyline SVG 几何路径，也不同时引入 Duotone 或 Fill 版本。
