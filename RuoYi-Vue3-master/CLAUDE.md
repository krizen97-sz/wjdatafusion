# CLAUDE.md

此文件为 Claude Code (claude.ai/code) 在此代码库中工作时提供指导。

## 项目概述

RuoYi-Vue3 是一个基于 Vue 3 + Element Plus + Vite 的现代化前端管理系统。它是 RuoYi 快速开发框架系列的一部分，专为前后端分离设计。后端通常连接到 Spring Boot API 服务器。

## 关键技术与依赖

- Vue 3.5.26
- Element Plus 2.13.1 (UI 框架)
- Vite 6.4.1 (构建工具)
- Pinia 3.0.4 (状态管理)
- Vue Router 4.6.4
- Axios 1.13.2 (HTTP 客户端)
- Sass Embedded 1.97.2
- ECharts 5.6.0
- JS-Cookie 3.0.5

## 架构结构

### 核心目录
- `/src/api` - API 请求定义和 axios 配置
- `/src/components` - 可复用的 Vue 组件
- `/src/layout` - 页面布局组件
- `/src/router` - 路由定义和导航守卫
- `/src/store` - 用于状态管理的 Pinia 存储
- `/src/utils` - 工具函数和帮助器
- `/src/views` - 页面组件
- `/vite` - Vite 插件配置

### 状态管理
- 使用 Pinia 进行全局状态管理
- 存储模块：`app`, `dict`, `permission`, `settings`, `tagsView`, `user`
- 认证和用户数据存储在用户存储中

### 路由与导航
- 基于用户权限的动态路由生成
- 基于角色和权限的访问控制
- 路由元字段用于标题、图标和权限
- `src/permission.js` 中的导航守卫处理身份验证检查

### 构建配置
- `vite.config.js` 中的 Vite 配置
- 代理配置用于转发 API 请求到后端服务器
- 多种环境配置（开发、预发布、生产）

## 开发命令

### 运行应用程序
```bash
# 安装依赖
yarn --registry=https://registry.npmmirror.com

# 启动开发服务器（运行在 http://localhost:80）
yarn dev

# 构建生产版本
yarn build:prod

# 构建预发布版本
yarn build:stage

# 预览构建的应用程序
yarn preview
```

### 后端 API
- 默认情况下，前端期望后端 API 位于 `http://localhost:8080`
- 开发 API 调用从 `/dev-api` 代理到后端
- 在 `vite.config.js` 中配置后端 URL（baseUrl 变量）

### 环境变量
- `.env.development` - 开发环境设置
- `.env.production` - 生产环境设置
- `.env.staging` - 预发布环境设置
- 主要变量：`VITE_APP_BASE_API` 设置 API 前缀

## 关键功能与模式

### 认证流程
- 基于 token 的认证，使用 localStorage/cookies
- `src/utils/request.js` 中的拦截器处理认证头
- `src/permission.js` 中的登录重定向和 token 验证

### 权限系统
- 基于角色的访问控制（RBAC）
- 权限指令用于 UI 元素可见性
- 基于用户权限的动态菜单加载
- 路由级别保护在路由守卫中实现

### 全局组件
- 分页组件
- 文件和图像上传组件
- 富文本编辑器组件
- 用于动态标签的字典标签组件
- SVG 图标系统

### API 集成
- `src/utils/request.js` 中标准化的请求/响应包装器
- 错误处理和通知系统
- 加载状态管理

## 重要文件

- `src/main.js` - 应用程序入口点和全局注册
- `src/permission.js` - 路由导航守卫和认证逻辑
- `src/settings.js` - 全局应用程序设置
- `src/utils/request.js` - Axios 配置和拦截器
- `src/utils/auth.js` - 认证实用函数
- `vite.config.js` - 构建配置和 API 代理设置