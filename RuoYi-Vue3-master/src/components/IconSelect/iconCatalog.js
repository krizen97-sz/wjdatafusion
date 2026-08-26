export const iconCatalog = {
  network: { label: '网络拓扑', keywords: '现场 IP 地址 连接 节点' },
  'map-pinned': { label: '现场位置', keywords: '现场 地图 定位 地址' },
  'panels-top-left': { label: '平台工作区', keywords: '平台 页面 工作台 模块' },
  'server-cog': { label: '服务器配置', keywords: '服务器 SSH 运维 设置' },
  'contact-round': { label: '组织联系人', keywords: '组织 人员 联系人 用户' },
  'file-clock': { label: '版本记录', keywords: '版本 历史 日志 时间 记录' },
  'shield-check': { label: '安全管控', keywords: '白名单 权限 安全 校验' },
  'car-front': { label: '车辆管理', keywords: '车辆 车牌 管控' },
  'list-filter': { label: '过滤清单', keywords: '过滤 筛选 列表 数据' },
  'scan-search': { label: '自动巡检', keywords: '巡检 检测 扫描 搜索' },
  gauge: { label: '驾驶舱', keywords: '驾驶舱 仪表盘 健康度 指标' },
  'chart-no-axes-combined': { label: '趋势总览', keywords: '图表 趋势 总览 统计' },
  workflow: { label: '流程编排', keywords: '模板 计划 编排 流程 配置' },
  route: { label: '路由配置', keywords: '路由 IP 网络 路径 配置' },
  'folder-tree': { label: '目录管理', keywords: '文档 目录 文件夹 树' },
  files: { label: '文件管理', keywords: '文件 文档 附件' },
  'database-check': { label: '数据库检测', keywords: '数据库 SQL 校验 检查' },
  cable: { label: '链路连接', keywords: '网络 链路 接口 连接' },
  waypoints: { label: '节点关系', keywords: '节点 关系 拓扑 流程' },
  warehouse: { label: '机房仓储', keywords: '机房 机柜 资产 仓库' },
  layers: { label: '资源分层', keywords: '分层 图层 资源 架构' },
  router: { label: '网络设备', keywords: '路由器 网关 网络 设备' },
  'hard-drive': { label: '存储设备', keywords: '磁盘 存储 硬盘 设备' },
  'users-round': { label: '人员群组', keywords: '用户 人员 组织 群组' },
  'data-analysis': { label: '数据分析', keywords: '驾驶舱 数据 图表 分析' }
}

export function iconLabel(name) {
  return iconCatalog[name]?.label || name
}

export function matchesIcon(name, keyword) {
  const query = String(keyword || '').trim().toLowerCase()
  if (!query) return true
  const meta = iconCatalog[name]
  return [name, meta?.label, meta?.keywords]
    .filter(Boolean)
    .join(' ')
    .toLowerCase()
    .includes(query)
}
