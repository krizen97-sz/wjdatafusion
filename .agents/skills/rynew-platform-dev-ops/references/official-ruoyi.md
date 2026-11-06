# 若依官方文档作为开发基础

官方入口为 https://doc.ruoyi.vip/ ，当前平台对应 **RuoYi-Vue 后端 + RuoYi-Vue3 JavaScript 前端**。在2026-11-06核对的官网中，后端同时维护Boot2/3/4，前端有Vue2/Vue3/TS；官网有多代示例，不应把页面底部旧的最低环境要求直接作为本平台基线。

## 离线资料及覆盖

`official-docs-index.json`保存本轮官方页面URL、抓取摘要和小节数；完整公开HTML、可检索文本及可用配图位于离线迁移包的 `official-docs/`，从 `index.html` 打开。远端技能保存自编规则、来源目录及采集脚本，不把网站文档源码许可推定为平台代码的MIT许可。

本轮覆盖四个系列公开导航下的74个页面：RuoYi15、RuoYi-Vue13、Cloud26、App20；含四份FAQ共209个二级条目。付费视频、赠送的文档源码、外站教程和任意外部链接不属于本归档。正文抓取失败为0；6张App文档外站配图的域名已失效，离线清单保留缺项。官网App中一条目录式链接规范化为其已存在的HTML页，不把404另存成正文。

| 官方主题 | 原始入口 | 本平台读取与适配 |
| --- | --- | --- |
| 项目/模块 | https://doc.ruoyi.vip/ruoyi-vue/document/xmjs.html | 对照本地com.hm、七个子模块；计划控制器在quartz |
| 后端开发 | https://doc.ruoyi.vip/ruoyi-vue/document/htsc.html | 响应、分页、权限、审计、数据范围、字典、多数据源、生成模板；再核对当前Boot4/jakarta实现 |
| 前端开发 | https://doc.ruoyi.vip/ruoyi-vue/document/qdsc.html | 路由、请求、权限、字典、构建；按本地Vue3/Pinia/Vite/Element Plus适配 |
| 功能组件 | https://doc.ruoyi.vip/ruoyi-vue/document/zjwd.html | 优先本地已注册组件与真实props/emits，不照搬Vue2 API |
| 集成扩展 | https://doc.ruoyi.vip/ruoyi-vue/document/cjjc.html | 仅需求确实涉及时使用，依赖版本、网络/安全和事务按本地工程复核 |
| 环境与FAQ | https://doc.ruoyi.vip/ruoyi-vue/document/hjbs.html 与 https://doc.ruoyi.vip/ruoyi-vue/other/faq.html | 用于定位症状；不能凭FAQ建议移除授权、降低驱动或切换旧工具链 |

## 使用顺序

1. 在官方主题和FAQ中找到原生机制及适用版本。
2. 到本地生成模板、framework/system/common及前端公共能力核对真实实现。
3. 再到新增业务Service/Mapper、模块组件/规则函数核对本平台约束。规则冲突按用户当前要求与仓库规范处理，并说明适配原因。

必须适配的差异：官网旧FAQ可出现 `antMatchers`、Vue2实例/过滤器、Vue CLI变量和Webpack配置；当前分别对应SecurityFilterChain/requestMatchers、Vue3组合式API、Vite/import.meta.env。Shiro/Thymeleaf属于不分离系列，Cloud网关/Nacos属于微服务系列，不作为当前单体平台的默认依赖。

## 离线查找

已设置 `RYNEW_OFFLINE_HOME` 时：

```bash
node <skill-dir>/scripts/search_official_docs.mjs --query 数据权限
node <skill-dir>/scripts/search_official_docs.mjs --docs <离线包>/official-docs --query 重复提交
```

搜索返回来源URL、当地文本文件和行号，随后只读取与任务有关的段落，不一次装入全站文档。更新镜像由有网准备机执行 `fetch_official_docs.py --output <空目录>`；断网机只读已有镜像。
