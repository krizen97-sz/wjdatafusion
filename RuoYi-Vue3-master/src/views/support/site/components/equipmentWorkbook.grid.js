import { FIELD_LENGTHS, LOCATION_FIELDS, SECRET_FIELDS, WORKBOOK_FIELDS } from './equipmentWorkbook.rules.js'

export function canEditWorkbookCell(row, field) {
  return Boolean(row?._editing && row.canEdit && !row._saving && field && !field.readonly
    && (!field.source || field.source === row.sourceType)
    && (!field.assetTypes || field.assetTypes.includes(row.assetType))
    && (field.kind !== 'password' || row[field.prop + 'Editable'] !== false)
    && (field.prop !== 'platformIds' || row._canBind)
    && (!LOCATION_FIELDS.includes(field.prop) || row._canPlace))
}

export function workbookEditValue(row, field) {
  if (field.kind === 'password') return row._passwordInputs?.[field.prop] ?? row._plainValues?.[field.prop] ?? ''
  if (field.kind === 'boolean') return row[field.prop] === true || row[field.prop] === 1
  return Array.isArray(row[field.prop]) ? [...row[field.prop]] : row[field.prop] ?? ''
}

export function workbookCopyValue(row, field) {
  if (!field || (field.source && field.source !== row.sourceType)
    || (field.assetTypes && !field.assetTypes.includes(row.assetType))) return ''
  if (field.kind === 'password') {
    if (!row._editing) return row[field.prop] || ''
    if (!row._showPasswords || row[field.prop + 'Editable'] === false) throw new Error('请先开启全部明文显示，再复制密码；多份凭据请在配置中维护')
    if (row._secrets?.[field.prop] === null) throw new Error('待清空的密码不能复制，请逐项确认清空')
    return workbookEditValue(row, field)
  }
  if (field.kind === 'platforms') return JSON.stringify(row.platformIds || [])
  if (field.kind === 'boolean') return workbookEditValue(row, field) ? '开启' : '关闭'
  return row[field.prop] ?? ''
}

function matchOption(value, options, id, label, message) {
  if (value === '' || value == null) return null
  const matches = options.filter(item => String(item[id]) === String(value) || item[label] === value)
  if (matches.length !== 1) throw new Error(message)
  return matches[0][id]
}

export function parseWorkbookPaste(row, field, value, options = {}) {
  if (field.kind === 'platforms') {
    let ids = value
    if (!Array.isArray(ids)) {
      if (String(value).trim().startsWith('[')) {
        try { ids = JSON.parse(value) } catch { throw new Error('平台归属格式不正确') }
      } else {
        const id = matchOption(value, options.platforms || [], 'platformId', 'platformName', '平台名称不存在或重名，请使用平台选择器')
        ids = id == null ? [] : [id]
      }
    }
    if (ids.some(id => !(options.platforms || []).some(p => Number(p.platformId) === Number(id)
      && (row.sourceType !== 'SERVER' || p.platformLevel === 'SUB')))) throw new Error('服务器只能归属当前现场的子平台')
    return [...new Set(ids.map(Number))]
  }
  if (field.kind === 'room') return matchOption(value, options.rooms || [], 'roomId', 'roomName', '机房不存在或名称重复')
  if (field.kind === 'cabinet') return matchOption(value, (options.cabinets || []).filter(c => Number(c.roomId) === Number(row.roomId)), 'cabinetId', 'cabinetNo', '机柜不属于当前行的机房')
  if (field.kind === 'boolean') {
    if ([true, 1, '1', 'true', '开启', '是'].includes(value)) return true
    if ([false, 0, '0', 'false', '关闭', '否'].includes(value)) return false
    throw new Error('请选择开启或关闭')
  }
  if (field.kind === 'status') {
    const status = ({ 正常: '0', 停用: '1' })[value] ?? String(value)
    if (!['0', '1'].includes(status)) throw new Error('运行状态只能是正常或停用')
    return status
  }
  if (field.kind === 'network') return matchOption(value, options.networks || [], 'value', 'label', '网络环境不存在')
  if (field.kind === 'number') {
    if (value === '' || value == null) {
      if (field.prop === 'sshPort') throw new Error('SSH端口不能为空')
      return null
    }
    const num = Number(value), min = ['channelCount', 'portCount'].includes(field.prop) ? 0 : 1
    const max = ['rackUStart', 'rackUEnd'].includes(field.prop)
      ? (options.cabinets || []).find(c => Number(c.cabinetId) === Number(row.cabinetId))?.uCapacity || 45 : 65535
    if (!Number.isInteger(num) || num < min || num > max) throw new Error(field.name + '必须为' + min + '至' + max + '的整数')
    return num
  }
  if (SECRET_FIELDS.includes(field.prop) && value === null) return null
  const text = String(value ?? '')
  if (text.length > (FIELD_LENGTHS[field.prop] || 128)) throw new Error(field.name + '长度超过限制')
  if (SECRET_FIELDS.includes(field.prop) && ['******', '已配置', '未配置', '待清空', '多份凭据'].includes(text)) throw new Error('不能把密码占位符当作密码粘贴')
  return text
}

// Prepare the whole range before touching drafts; a bad value must not leave a half-pasted column.
export function prepareWorkbookRange(data, rows, options) {
  const changes = [], working = new Map()
  for (const [index, values] of Object.entries(data)) {
    const row = rows[Number(index)]
    if (!row) throw new Error('复制范围已失效，请重新选择单元格')
    const candidate = working.get(row.deviceKey) || { ...row }
    working.set(row.deviceKey, candidate)
    for (const field of WORKBOOK_FIELDS) {
      if (!Object.prototype.hasOwnProperty.call(values, field.prop) || !canEditWorkbookCell(row, field)) continue
      const value = parseWorkbookPaste(candidate, field, values[field.prop], options)
      const sameLocation = String(candidate[field.prop] ?? '') === String(value ?? '')
      candidate[field.prop] = value
      if (field.prop === 'roomId' && !sameLocation) { candidate.cabinetId = null; candidate.rackUStart = null; candidate.rackUEnd = null }
      if (field.prop === 'cabinetId' && !sameLocation) { candidate.rackUStart = null; candidate.rackUEnd = null }
      changes.push({ key: row.deviceKey, field: field.prop, value })
    }
  }
  return changes
}
