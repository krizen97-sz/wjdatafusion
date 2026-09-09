const PROPERTY_KEYS = ['Lookup Rows', 'Match Fields', 'Return Fields', 'Missing Match', 'Multiple Matches']
const MATCH_KEYS = ['input', 'lookup', 'type', 'operator']
const RETURN_KEYS = ['lookup', 'output', 'default']
const RULE_LIMIT = 64
const RULE_BYTES = 32 * 1024

export const LOOKUP_MISSING_OPTIONS = [
  { value: 'KEEP', label: '保留并填默认值（默认）' },
  { value: 'DROP', label: '丢弃未命中记录' },
  { value: 'FAIL', label: '整批失败' }
]
export const LOOKUP_MULTIPLE_OPTIONS = [
  { value: 'FAIL', label: '整批失败（默认）' },
  { value: 'FIRST', label: '取快照中的第一条' },
  { value: 'LAST', label: '取快照中的最后一条' }
]

export function lookupPropertyText(value, fallback = '[]') {
  return value == null ? fallback : typeof value === 'string' ? value : String(value)
}

function object(value) { return !!value && typeof value === 'object' && !Array.isArray(value) }

// JSON.parse is used for inspection only. This pass catches duplicate keys that it would overwrite.
function inspectJson(raw, maxBytes) {
  const text = lookupPropertyText(raw)
  if (text.length > maxBytes || new TextEncoder().encode(text).length > maxBytes) throw new Error(`JSON 超过 ${maxBytes / 1024} KiB 上限`)
  let parsed
  try { parsed = JSON.parse(text) } catch { throw new Error('JSON 语法有误，请在下方高级编辑中修复原文') }
  let cursor = 0
  let values = 0
  function whitespace() { while (/\s/.test(text[cursor] || '') && cursor < text.length) cursor++ }
  function string() {
    const start = cursor++
    while (cursor < text.length) {
      const char = text[cursor++]
      if (char === '\\') cursor++
      else if (char === '"') return JSON.parse(text.slice(start, cursor))
    }
  }
  function visit(depth) {
    whitespace()
    if (depth > 32 || ++values > 50000) throw new Error('JSON 层级或字段数量超过上限')
    const char = text[cursor]
    if (char === '"') { string(); return }
    if (char !== '{' && char !== '[') {
      while (cursor < text.length && !/[\s,\]}]/.test(text[cursor])) cursor++
      return
    }
    const isObject = char === '{'
    const close = isObject ? '}' : ']'
    const keys = new Set()
    cursor++; whitespace()
    while (text[cursor] !== close) {
      if (isObject) {
        const key = string()
        if (keys.has(key)) throw new Error('JSON 含重复字段名，请先修复，避免覆盖已有配置')
        keys.add(key); whitespace(); cursor++
      }
      visit(depth + 1); whitespace()
      if (text[cursor] === ',') { cursor++; whitespace() }
    }
    cursor++
  }
  visit(0)
  return parsed
}

export function parseLookupSnapshot(raw) {
  try {
    const rows = inspectJson(raw, 256 * 1024)
    if (!Array.isArray(rows) || rows.some(row => !object(row))) throw new Error('字典快照必须是 JSON 对象数组')
    if (rows.length > 1000) throw new Error('字典快照最多包含 1000 条记录')
    return { valid: true, count: rows.length, error: '' }
  } catch (error) { return { valid: false, count: null, error: error.message } }
}

export function parseLookupRules(raw, property) {
  try {
    if (!['Match Fields', 'Return Fields'].includes(property)) throw new Error('未知查表规则')
    const rows = inspectJson(raw, RULE_BYTES)
    if (!Array.isArray(rows) || rows.some(row => !object(row))) throw new Error('规则必须是 JSON 对象数组，请先修复高级配置')
    if (rows.length > RULE_LIMIT) throw new Error('最多配置 64 条规则，请在高级编辑中调整')
    const keys = property === 'Match Fields' ? MATCH_KEYS : RETURN_KEYS
    const advancedOnly = rows.some(row => Object.entries(row).some(([key, value]) =>
      !keys.includes(key) || (key === 'default' ? value !== null && typeof value !== 'string' : typeof value !== 'string')))
    return {
      valid: true, rows, advancedOnly, error: '',
      warning: advancedOnly ? '当前规则含数字、对象或其他高级配置。请直接编辑原始 JSON，保留数值精度和原有类型。' : ''
    }
  } catch (error) { return { valid: false, rows: [], advancedOnly: false, error: error.message, warning: '' } }
}

export function lookupRuleFieldError(row, field, property) {
  const value = row[field]
  if (field === 'input' && row.operator === 'IS_NOT_NULL') return ''
  if (field === 'input') {
    if (typeof value !== 'string' || !value.startsWith('/') || value.length > 1024 || /~(?![01])/.test(value)) return '请填写有效 JSON Pointer，例如 /vehicle/plate'
    if (value.split('/').length - 1 > 32) return '路径最多支持 32 层'
  } else if (field === 'lookup') {
    if (typeof value !== 'string' || !value.trim() || value.length > 128 || /[\u0000-\u001f\u007f-\u009f]/.test(value)) return '请输入有效的字典列名'
  } else if (field === 'output' && property === 'Return Fields') {
    if (typeof value !== 'string' || !/^[\p{L}_][\p{L}\p{N}_. -]{0,127}$/u.test(value)) return '请输入普通根字段名，不支持路径或数组下标'
  } else if (field === 'type' && !['STRING', 'NUMBER'].includes(value)) return '请选择字符串或数字'
  else if (field === 'operator' && !['EQ', 'IS_NOT_NULL'].includes(value)) return '请选择相等或字典列非空'
  return ''
}

export function lookupPropertyPatch(key, value) {
  if (!PROPERTY_KEYS.includes(key) || typeof value !== 'string') throw new Error('只允许更新已知的查表属性字符串')
  if (key === 'Missing Match' && !LOOKUP_MISSING_OPTIONS.some(option => option.value === value)) throw new Error('未知的未命中处理方式')
  if (key === 'Multiple Matches' && !LOOKUP_MULTIPLE_OPTIONS.some(option => option.value === value)) throw new Error('未知的重复匹配处理方式')
  return { key, value }
}

export function patchLookupRules(raw, property, operation) {
  const state = parseLookupRules(raw, property)
  if (!state.valid) throw new Error(state.error)
  if (state.advancedOnly) throw new Error(state.warning)
  const rows = state.rows.map(row => ({ ...row }))
  if (operation.type === 'add') {
    if (rows.length >= RULE_LIMIT) throw new Error('最多配置 64 条规则')
    rows.push(property === 'Match Fields'
      ? { input: '/field', lookup: 'lookup_column', type: 'STRING', operator: 'EQ' }
      : { lookup: 'lookup_column', output: `mapped_field${rows.length + 1}`, default: null })
  } else {
    const index = operation.index
    if (!Number.isInteger(index) || index < 0 || index >= rows.length) throw new Error('规则已变化，请重新选择')
    if (operation.type === 'remove') rows.splice(index, 1)
    else if (operation.type === 'field') {
      const keys = property === 'Match Fields' ? MATCH_KEYS : ['lookup', 'output']
      if (!keys.includes(operation.key) || typeof operation.value !== 'string') throw new Error('无效的规则字段')
      rows[index][operation.key] = operation.value
      if (property === 'Match Fields' && operation.key === 'operator') {
        if (operation.value === 'IS_NOT_NULL') delete rows[index].input
        else if (operation.value === 'EQ' && !Object.prototype.hasOwnProperty.call(rows[index], 'input')) rows[index].input = '/field'
      }
    } else if (operation.type === 'default' && property === 'Return Fields') {
      if (operation.kind === 'NULL') rows[index].default = null
      else if (operation.kind === 'STRING' && typeof operation.value === 'string') rows[index].default = operation.value
      else throw new Error('默认值仅支持字符串或空值，其他类型请使用高级编辑')
    } else throw new Error('未知的规则操作')
  }
  const value = JSON.stringify(rows)
  if (new TextEncoder().encode(value).length > RULE_BYTES) throw new Error('规则超过 32 KiB 上限')
  return lookupPropertyPatch(property, value)
}
