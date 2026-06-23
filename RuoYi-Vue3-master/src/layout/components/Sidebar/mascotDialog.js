export const mascotDialogConfig = {
  enabled: true,
  idleInterval: 12000,
  messages: {
    hoverSelf: '我在左下角值守，菜单收起或移动端会自动让出空间。',
    modelReady: '本地 Live2D 模型已上线，我会继续盯着现场和巡检状态。',
    modelError: '本地模型加载失败，请检查离线包里的 live2d 资源是否完整。',
    bodyTap: '收到，我切到当前页面的操作指引。'
  },
  play: {
    enabled: true,
    enter: '我变大啦。现在是摸摸、戳戳、猜我下一句的时间。',
    exit: '我缩小回角落啦，下次再一起玩。',
    hint: '点点不同位置，我会用不同语气回应你。',
    hover: '你靠近啦，我假装没有紧张。',
    regions: {
      head: [
        '头顶被发现了。给你一朵小云，今天也要轻一点。',
        '摸头加成启动，我现在心情很好。',
        '这里是灵感开关，轻轻一点就会亮。'
      ],
      face: [
        '被你看到了。那我也眨一下眼回应你。',
        '靠这么近说话，会有点不好意思。',
        '我正在认真盯着你，轮到你做个表情。'
      ],
      body: [
        '这里不能乱戳，我会轻轻躲一下。',
        '哎呀，差点被你点得跳起来。',
        '收到一个轻拍，我回你一个小小鞠躬。'
      ],
      hand: [
        '要牵手吗？我可以陪你晃一晃。',
        '碰到手边啦，击掌成功。',
        '我们来玩石头剪刀布，我先偷偷出布。'
      ],
      bubble: [
        '你点到我的话啦，它们会自己蹦一下。',
        '这颗气泡今天很活泼，轻轻一碰就冒出来。',
        '悄悄告诉你，我把下一句话藏在气泡边上。'
      ],
      around: [
        '点偏啦，不过我假装这是一个暗号。',
        '我在这里转个小圈，你再试试。',
        '空气被你点醒了，我也跟着精神起来。'
      ],
      idle: [
        '你在旁边忙，我在这里晃脚等你。',
        '我悄悄探头看一眼，又缩回来了。',
        '你点哪里，我就把注意力跟过去一点点。'
      ]
    }
  },
  greetings: [
    { before: 6, message: '夜间值守中，异常信息优先看巡检记录和服务状态。' },
    { before: 9, message: '早上好，先看今日运行状态和待处理异常。' },
    { before: 12, message: '上午适合核对现场、平台、服务器配置链路。' },
    { before: 18, message: '下午继续盯紧现场融合管理和自动化巡检结果。' },
    { before: 24, message: '今天的变更记得留痕，版本记录中心以后能帮上忙。' }
  ],
  topics: [
    {
      key: 'site',
      name: '现场融合',
      enabled: true,
      messages: [
        '现场融合管理建议按“现场、主平台、子平台、服务器”顺序核对。',
        '进入现场配置前，先确认组织、联系人和现场对接人是否完整。',
        '现场融合关系画布可以看清平台和服务器挂载关系，节点异常先从这里找。',
        '新增主平台后，记得补齐子平台入口和服务器归属。',
        '设备资产台账最好补齐型号、序列号、安装位置和质保到期时间。',
        '画布节点太密时，先重置视图，再切换横向或纵向布局。'
      ]
    },
    {
      key: 'inspection',
      name: '自动巡检',
      enabled: true,
      messages: [
        '自动化巡检先选测试目标，再配置工具和巡检步骤。',
        'HTTP 健康检测适合看服务接口，TCP 端口检测适合看基础连通性。',
        '服务器服务状态检测要对准服务器，不要把现场、平台、服务器层级混在一起。',
        '巡检记录里的调用信息很重要，排错时别只看成功或失败。',
        '模板改动不会改写历史报告，报告快照可以放心回溯。',
        '今日异常要优先处理，再看最近 7 天趋势。'
      ]
    },
    {
      key: 'ops',
      name: '平台运维',
      enabled: true,
      messages: [
        '版本记录中心可以回查菜单、接口、SQL 和前端改动。',
        '如果页面报错，先看网络请求，再看后端日志，最后定位到服务和表。',
        '白名单管理先确认名单状态和车牌规则，再处理导入导出。',
        '表格批量操作前，先确认筛选条件和当前页数据范围。',
        '离线部署时，前端资源必须全部随包发布，不能依赖外网。',
        '变更上线后，记得用真实账号做一次菜单和核心页面烟测。'
      ]
    }
  ],
  guides: [
    {
      key: 'autoInspection',
      match: ['/support/autoInspection', '/support/inspection', '/inspection'],
      title: '自动化巡检操作指引',
      enabled: true,
      steps: [
        '第一步：先确认巡检目标归属现场、平台或服务器，目标层级选错会影响后续工具配置。',
        '第二步：创建或复制模板，再按工具类型添加步骤；服务器类工具要优先绑定服务器目标。',
        '第三步：保存前用手动验证跑一次，确认账号、端口、路径和阈值都能通过。',
        '第四步：配置巡检计划后，到巡检总览查看最新记录和异常子项。'
      ]
    },
    {
      key: 'siteFusion',
      match: ['/support/site', '/support/platform', '/support/server', '/support/org'],
      title: '现场融合操作指引',
      enabled: true,
      steps: [
        '第一步：先建现场和组织联系人，确保现场负责人、对接人和网络环境清楚。',
        '第二步：进入现场配置，按主平台、子平台、服务器顺序补齐关系。',
        '第三步：打开关系画布核对挂载方向，发现孤立节点先回到配置表修正归属。',
        '第四步：设备资产变更后同步检查质保、安装位置和关联现场。'
      ]
    },
    {
      key: 'whitelist',
      match: ['/whitelist'],
      title: '白名单操作指引',
      enabled: true,
      steps: [
        '第一步：先确认车牌或名单规则，再新增、导入或筛选数据。',
        '第二步：批量导入后检查失败行和重复数据，避免把异常规则带入后续流程。',
        '第三步：发布或写入前核对当前筛选条件，确认操作范围不是仅当前页误判。',
        '第四步：处理违法图片或过滤数据时，优先保留来源、时间和现场线索。'
      ]
    },
    {
      key: 'version',
      match: ['/version', '/support/version'],
      title: '版本记录操作指引',
      enabled: true,
      steps: [
        '第一步：先用大版本筛选定位范围，例如 v3.6 或 v3.4 及以下。',
        '第二步：再用模块快捷标签过滤自动巡检、现场融合、白名单或页面优化。',
        '第三步：需要上线回溯时，重点查看 SQL、脚本和数据库变更说明。',
        '第四步：完成新修改后记得追加版本记录，方便后续部署交接。'
      ]
    },
    {
      key: 'default',
      match: ['*'],
      title: '平台操作指引',
      enabled: true,
      steps: [
        '先确认当前页面所属模块，再看顶部筛选、表格状态和右侧操作按钮。',
        '遇到异常页面，优先检查接口请求和权限，再回到版本记录确认最近改动。',
        '涉及现场人员操作时，建议按“确认现场、核对平台、保存配置、查看反馈”的顺序走。'
      ]
    }
  ],
  interactions: [
    {
      selector: '.sidebar-container .el-sub-menu__title',
      enabled: true,
      event: 'both',
      template: '这里是“{text}”目录，展开后再进入具体页面。'
    },
    {
      selector: '.sidebar-container .el-menu-item',
      enabled: true,
      event: 'both',
      template: '准备进入“{text}”页面，我继续在左下角值守。'
    },
    {
      selector: '#hamburger-container',
      enabled: true,
      event: 'both',
      template: '收起左侧菜单后，我会自动隐藏，给工作区让位置。'
    },
    {
      selector: '.tags-view-container',
      enabled: true,
      event: 'hover',
      template: '页签栏可以在已打开页面之间切换，排查问题时很省时间。'
    },
    {
      selector: '.el-table__row',
      enabled: true,
      event: 'hover',
      template: '这行记录可以重点看状态、更新时间和关联现场。'
    },
    {
      selector: '.el-button--primary',
      enabled: true,
      event: 'both',
      template: '即将执行“{text}”操作，先确认当前页面和表单内容。'
    },
    {
      selector: '.el-dialog',
      enabled: true,
      event: 'hover',
      template: '弹窗里的配置通常会影响现场、平台或巡检规则，保存前再扫一遍。'
    }
  ]
}

export function getMascotGreeting(config = mascotDialogConfig) {
  const hour = new Date().getHours()
  const greeting = config.greetings.find((item) => hour < item.before)
  return greeting?.message || config.greetings[config.greetings.length - 1]?.message || ''
}

export function getMascotTopics(config = mascotDialogConfig) {
  return (config.topics || []).filter((topic) => topic.enabled !== false && topic.messages?.length)
}

export function getMascotInteractions(config = mascotDialogConfig) {
  return (config.interactions || []).filter((item) => item.enabled !== false && item.selector && item.template)
}

export function getMascotPlayMessage(region = 'around', config = mascotDialogConfig) {
  const play = config.play || {}
  const messages = play.regions?.[region] || play.regions?.around || []
  if (!messages.length) {
    return play.hint || ''
  }
  return messages[Math.floor(Math.random() * messages.length)]
}

export function getMascotGuide(path = '', config = mascotDialogConfig) {
  const guides = (config.guides || []).filter((guide) => guide.enabled !== false && guide.steps?.length)
  return guides.find((guide) => guide.match?.some((matcher) => matcher !== '*' && path.startsWith(matcher)))
    || guides.find((guide) => guide.match?.includes('*'))
    || null
}

export function renderMascotTemplate(template = '', data = {}) {
  return template.replace(/\{(\w+)\}/g, (_, key) => data[key] || data.fallback || '当前')
}
