const RUN_STATES = {
  QUEUED: { label: '等待执行', type: 'info', active: true },
  RUNNING: { label: '正在执行', type: 'warning', active: true },
  SUCCEEDED: { label: '执行成功', type: 'success' },
  FAILED: { label: '执行失败', type: 'danger' },
  CANCELLED: { label: '已取消', type: 'info' },
  TIMED_OUT: { label: '执行超时', type: 'danger' },
  UNSUPPORTED: { label: '暂不支持测试', type: 'warning' },
  CLEANUP_REQUIRED: { label: '需要清理测试资源', type: 'danger' }
}

const AVAILABILITY = {
  AVAILABLE: { label: '可用', type: 'success', usable: true },
  READY: { label: '可用', type: 'success', usable: true },
  ADAPTER_REQUIRED: { label: '待适配', type: 'warning' },
  UNAVAILABLE: { label: '不可用', type: 'info' },
  UNSUPPORTED: { label: '暂不支持', type: 'info' },
  NOT_INSTALLED: { label: '未安装', type: 'info' }
}

export const SAMPLE_LIMIT = 256 * 1024

export function runState(status, cleanupConfirmed) {
  const value = String(status || '').toUpperCase()
  if (value === 'SUCCEEDED' && cleanupConfirmed === false) {
    return { label: '执行结束，清理待确认', type: 'warning', active: false }
  }
  return RUN_STATES[value] || { label: value ? `未知状态：${value}` : '未返回状态', type: 'info', active: false }
}

export function isRunActive(run) {
  return Boolean(RUN_STATES[String(run?.status || '').toUpperCase()]?.active)
}

export function availabilityState(value) {
  return AVAILABILITY[String(value || '').toUpperCase()] || { label: '未确认可用性', type: 'info', usable: false }
}

export function catalogCategory(value) {
  return ({ GENERAL: '通用组件', COMMON: '通用组件', HIKVISION: '海康兼容', HIKVISION_COMPATIBILITY: '海康兼容', ADAPTER_REQUIRED: '待适配', PLANNED: '待适配' })[String(value || '').toUpperCase()] || String(value || '其他')
}

export function parseTestRequest(inputText, parameterText) {
  if (!String(inputText || '').trim()) throw new Error('请填写 JSON 样本。')
  if (new TextEncoder().encode(inputText).length > SAMPLE_LIMIT) throw new Error('样本不能超过 256 KB，请缩小测试数据。')
  try { JSON.parse(inputText) } catch { throw new Error('样本不是有效的 JSON，请检查括号、引号和逗号。') }
  let parameters
  try { parameters = JSON.parse(parameterText || '{}') } catch { throw new Error('模板参数不是有效的 JSON 对象。') }
  if (!parameters || typeof parameters !== 'object' || Array.isArray(parameters)) throw new Error('模板参数必须是 JSON 对象。')
  return { inputJson: inputText, parameters }
}

export function parameterDefaults(template) {
  const schema = template?.parametersSchema
  const properties = schema?.properties || {}
  return Object.fromEntries(Object.entries(properties).filter(([, field]) => field && Object.hasOwn(field, 'default')).map(([name, field]) => [name, field.default]))
}

// Engine links remain inside the approved proxy prefix. Never attach credentials.
export function safeDesignerPath(path) {
  if (typeof path !== 'string' || !/^\/nifi(?:\/|#|$)/.test(path) || /[\\\u0000-\u0020]/.test(path)) return ''
  try {
    const base = 'https://governance.invalid'
    const url = new URL(path, base)
    const decoded = decodeURIComponent(url.pathname)
    if (url.origin !== base || !/^\/nifi(?:\/|$)/.test(url.pathname) || !/^\/nifi(?:\/|$)/.test(decoded) || /[\\\u0000-\u0020]/.test(decoded)) return ''
    return `${url.pathname}${url.search}${url.hash}`
  } catch { return '' }
}

export function displayJson(value, limit = 20000) {
  if (value === undefined || value === null) return ''
  let text = typeof value === 'string' ? value : JSON.stringify(value, null, 2)
  if (typeof value === 'string') {
    try { text = JSON.stringify(JSON.parse(value), null, 2) } catch { /* Plain output is also valid. */ }
  }
  return text.length > limit ? `${text.slice(0, limit)}\n[展示已截断；完整内容请以运行产物为准]` : text
}

export function optionalCount(value) {
  return value === undefined || value === null ? '未返回' : String(value)
}

export function errorMessage(error, fallback = '请求失败，请重试。') {
  return typeof error === 'string' ? error : error?.message || fallback
}
