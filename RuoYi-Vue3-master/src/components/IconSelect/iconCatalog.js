import { pinyin } from 'pinyin-pro'
import { keylineAliases, keylineKeywords } from './keylineData.js'

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

export const iconSources = [
  { value: 'platform', label: '平台图标' },
  { value: 'keyline', label: '扩展图标' }
]

const platformCategories = [
  { value: 'business', label: '业务语义', keywords: '平台 业务 现场 巡检 文档 网络' },
  { value: 'base', label: '基础通用', keywords: '系统 基础 通用 若依' }
]

const keylineCategories = [
  { value: 'arrows', label: '方向箭头', match: /^(arrow|bracket-arrow|caret|chevron|expand|refresh|rotate)/, keywords: '方向 箭头 移动 返回 前进 后退 展开 收起 调整尺寸' },
  { value: 'git', label: 'Git版本', match: /^git-/, keywords: 'Git 分支 提交 合并 冲突 拉取请求 版本管理' },
  { value: 'files', label: '文件文档', match: /^(file|folder|copy|paperclip|bin|archive|pen|(square|circle)-pen)/, keywords: '文件 文档 文件夹 附件 复制 删除 回收站 归档 编辑' },
  { value: 'time', label: '日期时间', match: /^(calendar|clock)/, keywords: '日期 时间 日历 时钟 计划 调度' },
  { value: 'mail', label: '消息通知', match: /^(mail|message|bell|inbox|reply|forward|at$)/, keywords: '邮件 消息 通知 提醒 收件箱 回复 转发' },
  { value: 'commerce', label: '交易物流', match: /^(shopping-|handbag|receipt|credit-card|tag|package|truck|gift|coupon)/, keywords: '购物 交易 账单 卡片 标签 包裹 物流 运输 礼物' },
  { value: 'maps', label: '地图位置', match: /^(map|compass|building|route)/, keywords: '地图 定位 地址 坐标 路线 现场 建筑 导航' },
  { value: 'media', label: '影音媒体', match: /^(play|pause|stop|record|skip-|fast-forward|rewind|repeat|volume|audio-lines|mic|headphones|headset|shuffle|music-note|list-music|list-video|camera|image|cast|subtitles|captions|picture-in-picture|gallery-|podcast|queue)/, keywords: '播放 暂停 停止 录制 音量 音频 麦克风 耳机 音乐 视频 图片 媒体' },
  { value: 'charts', label: '图表指标', match: /^(bar-chart|trending|signal|progress|loader|activity)/, keywords: '图表 趋势 信号 进度 加载 活跃 健康 指标 统计 分析' },
  { value: 'devices', label: '设备开发', match: /^(smartphone|monitor|terminal|database|server|battery|bluetooth|code)/, keywords: '设备 手机 屏幕 终端 数据库 服务器 电池 蓝牙 代码 开发' },
  { value: 'pointers', label: '指针交互', match: /^cursor/, keywords: '鼠标 指针 光标 点击 选择 输入' },
  { value: 'layout', label: '布局面板', match: /^(panel|layout|grid|list|align|menu|maximize|minimize|fullscreen)/, keywords: '布局 面板 网格 列表 对齐 菜单 全屏 最大化 最小化' },
  { value: 'users', label: '用户人员', match: /^(user|scan-face)/, keywords: '用户 人员 账号 头像 成员 人脸 认证' },
  { value: 'actions', label: '常用操作', match: /^(check|double-check|plus|minus|x|more|lock|unlock|download|upload|filter|eye|star|heart|alert|octagon-alert|triangle-alert|info|question)/, keywords: '确认 新增 删除 更多 锁定 解锁 下载 上传 筛选 查看 收藏 告警 信息 帮助' },
  { value: 'controls', label: '控件状态', match: /^(toggle|slider)/, keywords: '开关 滑块 控件 状态 设置' },
  { value: 'sport', label: '奖项排名', match: /^(trophy|award|podium|medal)/, keywords: '奖杯 奖项 排名 成绩 冠军' },
  { value: 'shapes', label: '形状进度', match: /^(circle|square|triangle|shapes|dashed|dice|flower|full|half|quarter|three-quarter)/, keywords: '圆形 方形 三角 形状 虚线 骰子 进度 完成度' },
  { value: 'web', label: '网络网页', match: /^(globe|link|share|navigation|home|search|settings|bookmark|wifi)/, keywords: '网络 网页 全球 链接 分享 导航 首页 搜索 设置 收藏 无线' },
  { value: 'weather', label: '天气主题', match: /^(sun|moon|cloud)/, keywords: '天气 太阳 月亮 云 晴天 夜间 主题 深色 浅色' },
  { value: 'other', label: '其他', match: /.*/, keywords: '其他 扩展' }
]

const localizedSearchRules = [
  { match: /activity/, terms: '活跃 活动 心跳 健康 监控' },
  { match: /alert|exclamation/, terms: '告警 警告 异常 注意' },
  { match: /archive/, terms: '归档 档案' },
  { match: /audio|volume|mic|headphone|headset|music|podcast/, terms: '音频 音量 声音 麦克风 耳机 音乐 播客' },
  { match: /battery/, terms: '电池 电量' },
  { match: /bell/, terms: '通知 提醒 告警' },
  { match: /bin/, terms: '删除 回收站 垃圾桶' },
  { match: /bookmark|star|heart/, terms: '收藏 关注 喜欢' },
  { match: /building/, terms: '建筑 楼宇 现场' },
  { match: /calendar|clock/, terms: '日历 日期 时间 时钟 计划' },
  { match: /camera|image|gallery/, terms: '相机 图片 图像 照片 图库' },
  { match: /cloud/, terms: '云 云端 离线 同步' },
  { match: /code|terminal/, terms: '代码 终端 命令行 开发' },
  { match: /copy/, terms: '复制 克隆' },
  { match: /credit-card|receipt|shopping|package|truck/, terms: '支付 账单 购物 包裹 物流 运输' },
  { match: /cursor/, terms: '鼠标 指针 光标 点击' },
  { match: /database/, terms: '数据库 数据 存储 SQL' },
  { match: /download/, terms: '下载 接收 保存' },
  { match: /eye/, terms: '查看 显示 预览 可见 隐藏' },
  { match: /file|folder|paperclip/, terms: '文件 文档 文件夹 目录 附件' },
  { match: /filter/, terms: '筛选 过滤 查询 条件' },
  { match: /git-/, terms: 'Git 版本 分支 提交 合并 代码管理' },
  { match: /globe|wifi|signal|link/, terms: '网络 互联网 无线 信号 连接 链路' },
  { match: /grid|layout|panel|align|list/, terms: '网格 布局 面板 对齐 列表' },
  { match: /lock|unlock/, terms: '锁定 解锁 安全 权限 密码' },
  { match: /mail|message|inbox|reply/, terms: '邮件 消息 收件箱 回复 通知' },
  { match: /map|route|compass|navigation|pin/, terms: '地图 路线 导航 定位 地址 坐标' },
  { match: /monitor/, terms: '显示器 屏幕 监控 工作站' },
  { match: /search/, terms: '搜索 查找 查询 检索' },
  { match: /server/, terms: '服务器 主机 后端 机架 基础设施' },
  { match: /settings|sliders/, terms: '设置 配置 参数 调整' },
  { match: /smartphone/, terms: '手机 移动设备 终端' },
  { match: /user|scan-face/, terms: '用户 人员 账号 成员 人脸 认证' },
  { match: /upload/, terms: '上传 发送 提交 发布' },
  { match: /check/, terms: '确认 完成 成功 校验 通过' },
  { match: /plus|create/, terms: '新增 添加 创建' },
  { match: /minus/, terms: '移除 减少 删除' },
  { match: /-x$|^x$|conflict/, terms: '关闭 取消 删除 失败 冲突' },
  { match: /off/, terms: '关闭 禁用 离线 不可用' },
  { match: /open/, terms: '打开 展开 已读' },
  { match: /close|collapse|minimize/, terms: '关闭 收起 折叠 最小化' },
  { match: /maximize|fullscreen|expand/, terms: '展开 放大 全屏 最大化' },
  { match: /left/, terms: '左 向左 返回' },
  { match: /right/, terms: '右 向右 前进' },
  { match: /up/, terms: '上 向上 提升' },
  { match: /down/, terms: '下 向下 降低' },
  { match: /horizontal/, terms: '水平 横向' },
  { match: /vertical/, terms: '垂直 纵向' },
  { match: /circle/, terms: '圆形 圆圈' },
  { match: /square/, terms: '方形 方框' },
  { match: /dashed/, terms: '虚线 未完成' },
  { match: /full/, terms: '完整 全部 满' },
  { match: /half/, terms: '一半 百分之五十' },
  { match: /quarter/, terms: '四分之一 进度' },
  { match: /high/, terms: '高 强' },
  { match: /medium/, terms: '中等 中' },
  { match: /low/, terms: '低 弱' }
]

const compiledKeylineAliases = keylineAliases.map((item) => ({
  match: new RegExp(item.match),
  terms: item.terms
}))
const searchIndexCache = new Map()

function keylineName(name) {
  return name.replace(/^keyline-/, '')
}

function keylineBaseName(name) {
  return keylineName(name).replace(/^(square|circle)-/, '')
}

function displayKeylineName(name) {
  const specialLabels = { git: 'Git', xml: 'XML', ccw: 'CCW', cw: 'CW' }
  return keylineName(name)
    .split('-')
    .map((part) => {
      if (/^\d/.test(part)) return part.toUpperCase()
      if (specialLabels[part]) return specialLabels[part]
      return `${part.charAt(0).toUpperCase()}${part.slice(1)}`
    })
    .join(' ')
}

function compact(value) {
  return String(value || '').toLowerCase().replace(/[\s_-]+/g, '')
}

function pinyinTerms(value) {
  return [
    pinyin(value, { toneType: 'none' }).replace(/\s+/g, ''),
    pinyin(value, { pattern: 'first', toneType: 'none' }).replace(/\s+/g, '')
  ]
}

function keylineCategory(name) {
  const baseName = keylineBaseName(name)
  return keylineCategories.find((category) => category.match.test(baseName)) || keylineCategories.at(-1)
}

function buildSearchIndex(name) {
  const cached = searchIndexCache.get(name)
  if (cached) return cached

  const metadata = iconCatalog[name]
  let terms = [name, metadata?.label, metadata?.keywords]

  if (iconSource(name) === 'keyline') {
    const fullName = keylineName(name)
    const baseName = keylineBaseName(name)
    const category = keylineCategory(name)
    const officialAliases = compiledKeylineAliases
      .filter((item) => item.match.test(baseName))
      .flatMap((item) => item.terms)
    const localizedTerms = localizedSearchRules
      .filter((item) => item.match.test(fullName))
      .map((item) => item.terms)

    terms = [
      ...terms,
      fullName,
      baseName,
      displayKeylineName(name),
      category.value,
      category.label,
      ...(keylineKeywords[baseName] || []),
      ...officialAliases,
      ...localizedTerms
    ]
  } else {
    const category = platformCategories.find((item) => item.value === iconCategory(name))
    terms.push(category?.label)
  }

  const normalizedTerms = terms.filter(Boolean).map(String)
  const text = normalizedTerms.join(' ').toLowerCase()
  const chineseText = normalizedTerms.filter((term) => /[\u3400-\u9fff]/.test(term)).join(' ')
  const index = `${text} ${compact(text)} ${pinyinTerms(chineseText).join(' ')}`
  searchIndexCache.set(name, index)
  return index
}

export function iconSource(name) {
  return String(name || '').startsWith('keyline-') ? 'keyline' : 'platform'
}

export function iconCategory(name) {
  if (iconSource(name) === 'keyline') return keylineCategory(name).value
  return iconCatalog[name] ? 'business' : 'base'
}

export function iconCategoryLabel(name) {
  if (iconSource(name) === 'keyline') return keylineCategory(name).label
  return platformCategories.find((item) => item.value === iconCategory(name))?.label || '基础通用'
}

export function categoriesForSource(source) {
  const categories = source === 'keyline' ? keylineCategories : platformCategories
  return categories.map(({ value, label }) => ({ value, label }))
}

export function iconLabel(name) {
  if (iconCatalog[name]?.label) return iconCatalog[name].label
  if (iconSource(name) === 'keyline') return displayKeylineName(name)
  return name
}

export function matchesIcon(name, keyword) {
  const queryTerms = String(keyword || '')
    .trim()
    .toLowerCase()
    .split(/\s+/)
    .filter(Boolean)
  if (!queryTerms.length) return true

  const searchIndex = buildSearchIndex(name)
  return queryTerms.every((term) => searchIndex.includes(term) || searchIndex.includes(compact(term)))
}
