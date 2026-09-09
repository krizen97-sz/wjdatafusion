const RUN_STATES = {
  QUEUED: { label: '等待执行', type: 'info', active: true },
  RUNNING: { label: '正在执行', type: 'warning', active: true },
  SUCCEEDED: { label: '执行成功', type: 'success' },
  EMPTY: { label: '空批次（引擎已确认）', type: 'success' },
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
  NOT_INSTALLED: { label: '未安装', type: 'info' },
  ENGINE_REQUIRED: { label: '引擎组件未就绪', type: 'info', usable: false }
}

export const SAMPLE_LIMIT = 256 * 1024

export function runState(status, cleanupConfirmed) {
  const value = String(status || '').toUpperCase()
  if (['SUCCEEDED', 'EMPTY'].includes(value) && cleanupConfirmed === false) {
    return { label: value === 'EMPTY' ? '空批次，清理待确认' : '执行结束，清理待确认', type: 'warning', active: false }
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
  let input
  try { input = JSON.parse(inputText) } catch { throw new Error('样本不是有效的 JSON，请检查括号、引号和逗号。') }
  if (input === null || typeof input !== 'object' || (Array.isArray(input) && input.length > 100)) {
    throw new Error('样本必须为 JSON 对象或最多 100 条记录的数组（允许空数组）。')
  }
  let parameters
  try { parameters = JSON.parse(String(parameterText || '').trim() || '{}') } catch { throw new Error('可选参数不是有效的 JSON 对象。') }
  if (!parameters || typeof parameters !== 'object' || Array.isArray(parameters)) throw new Error('模板参数必须是 JSON 对象。')
  return { inputJson: inputText, parameters }
}

// Drafts live only in this workspace's memory. Schema defaults are documentation,
// not submitted overrides of the definition saved in the engine canvas.
export function createFlowDraftStore() {
  const drafts = new Map()
  return {
    read(flowId, template) {
      const example = template?.parametersSchema?.inputExample
      return { ...(drafts.get(String(flowId)) || {
        inputText: typeof example === 'string' && example.trim() ? example : '{\n  "message": "样本消息"\n}',
        parameterText: '{}'
      }) }
    },
    write(flowId, draft) {
      if (flowId) drafts.set(String(flowId), { inputText: draft.inputText, parameterText: draft.parameterText })
    }
  }
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
    try { JSON.parse(value); text = formatJsonTokens(value, limit) } catch { /* Plain or incomplete output is also valid. */ }
  }
  return text.length > limit ? `${text.slice(0, limit)}\n[预览已截断；当前仅保留有界采样，不代表完整内容]` : text
}

// Inspect syntax without reserializing parsed numbers: dictionary IDs and decimals may exceed JS precision.
function formatJsonTokens(source, limit) {
  let output = '', depth = 0, quoted = false, escaped = false
  for (let i = 0; i < source.length; i++) {
    const char = source[i]
    if (quoted) {
      output += char
      if (escaped) escaped = false
      else if (char === '\\') escaped = true
      else if (char === '"') quoted = false
    } else if (char === '"') { quoted = true; output += char }
    else if (/\s/.test(char)) continue
    else if (char === '{' || char === '[') {
      if (++depth > 40) return source
      output += char
      let next = i + 1
      while (/\s/.test(source[next] || '') && next < source.length) next++
      if (source[next] === (char === '{' ? '}' : ']')) { output += source[next]; i = next; depth-- }
      else output += '\n' + '  '.repeat(depth)
    } else if (char === '}' || char === ']') output += '\n' + '  '.repeat(--depth) + char
    else if (char === ',') output += ',\n' + '  '.repeat(depth)
    else if (char === ':') output += ': '
    else output += char
    if (output.length > limit) return output
  }
  return output
}

export function optionalCount(value) {
  return value === undefined || value === null ? '未返回' : String(value)
}

export function errorMessage(error, fallback = '请求失败，请重试。') {
  return typeof error === 'string' ? error : error?.message || fallback
}
