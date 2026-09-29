export const SECRET_FIELDS = ['loginPassword', 'hikPassword', 'rootPassword', 'otherPassword']
export const LOCATION_FIELDS = ['roomId', 'cabinetId', 'rackUStart', 'rackUEnd']
export const FIELD_LENGTHS = {"assetName":120,"ipAddress":255,"manageIp":255,"osType":64,"networkEnv":100,"macAddress":64,"manufacturer":100,"assetModel":120,"serialNo":120,"ownerOrg":160,"ownerContact":80,"loginUsername":128,"otherUsername":128,"remark":500,"outputType":80,"terminalType":80,"department":120,"useLocation":200,"switchLevel":80,"uplinkDevice":160,"vlanInfo":500,"gatewayMode":80,"gatewayDirection":120,"gatewayBandwidth":80,"securityZone":200}
const common = ['assetName', 'ipAddress', 'status', 'remark', 'platformIds', ...LOCATION_FIELDS]
const server = ['sshPort', 'rootDirectLogin', 'osType', 'otherUsername', 'hikPassword', 'rootPassword', 'otherPassword']
export const HARDWARE_EXTRA_FIELDS = [
  { prop: 'networkEnv', name: '网络环境', size: 130, kind: 'network' },
  { prop: 'macAddress', name: 'MAC地址', size: 160 },
  { prop: 'serialNo', name: '序列号', size: 160 },
  { prop: 'ownerOrg', name: '归属组织', size: 170 },
  { prop: 'ownerContact', name: '责任人', size: 130 },
  { prop: 'channelCount', name: '通道数', size: 110, kind: 'number', assetTypes: ['DECODER'] },
  { prop: 'outputType', name: '输出类型', size: 140, assetTypes: ['DECODER'] },
  { prop: 'terminalType', name: '终端类型', size: 140, assetTypes: ['TERMINAL'] },
  { prop: 'department', name: '使用部门', size: 160, assetTypes: ['TERMINAL'] },
  { prop: 'useLocation', name: '使用位置', size: 160, assetTypes: ['TERMINAL'] },
  { prop: 'switchLevel', name: '交换层级', size: 130, assetTypes: ['SWITCH'] },
  { prop: 'portCount', name: '端口数', size: 110, kind: 'number', assetTypes: ['SWITCH'] },
  { prop: 'uplinkDevice', name: '历史上联记录', size: 160, assetTypes: ['SWITCH'] },
  { prop: 'vlanInfo', name: 'VLAN说明', size: 180, assetTypes: ['SWITCH'] },
  { prop: 'gatewayMode', name: '网闸模式', size: 140, assetTypes: ['GATEWAY'] },
  { prop: 'gatewayDirection', name: '数据流向', size: 140, assetTypes: ['GATEWAY'] },
  { prop: 'gatewayBandwidth', name: '带宽', size: 120, assetTypes: ['GATEWAY'] },
  { prop: 'securityZone', name: '安全域', size: 170, assetTypes: ['GATEWAY'] }
].map(field => ({ ...field, source: 'HARDWARE', group: 'EXTENDED' }))
const hardware = ['manageIp', 'manufacturer', 'assetModel', 'loginUsername', 'loginPassword', ...HARDWARE_EXTRA_FIELDS.map(field => field.prop)]

export const WORKBOOK_FIELDS = [
  { prop: 'assetName', name: '设备名称', size: 190 },
  { prop: 'assetTypeLabel', name: '类型', size: 85, readonly: true },
  { prop: 'ipAddress', name: 'IP / 地址', size: 160 },
  { prop: 'sshPort', name: 'SSH端口', size: 110, kind: 'number', source: 'SERVER' },
  { prop: 'osType', name: '操作系统', size: 120, source: 'SERVER' },
  { prop: 'platformIds', name: '平台归属', size: 250, kind: 'platforms' },
  { prop: 'roomId', name: '机房', size: 180, kind: 'room' },
  { prop: 'cabinetId', name: '机柜', size: 135, kind: 'cabinet' },
  { prop: 'rackUStart', name: '起始U位', size: 105, kind: 'number' },
  { prop: 'rackUEnd', name: '结束U位', size: 105, kind: 'number' },
  { prop: 'loginUsername', name: '登录账号', size: 135, source: 'HARDWARE' },
  { prop: 'loginPassword', name: '设备密码', size: 190, kind: 'password', source: 'HARDWARE' },
  { prop: 'hikPassword', name: 'hik密码', size: 190, kind: 'password', source: 'SERVER' },
  { prop: 'rootPassword', name: 'root密码', size: 190, kind: 'password', source: 'SERVER' },
  { prop: 'rootDirectLogin', name: 'root直接登录', size: 145, kind: 'boolean', source: 'SERVER' },
  { prop: 'otherUsername', name: '其他账号', size: 145, source: 'SERVER' },
  { prop: 'otherPassword', name: '其他密码', size: 190, kind: 'password', source: 'SERVER' },
  { prop: 'manageIp', name: '管理IP', size: 150, source: 'HARDWARE' },
  { prop: 'manufacturer', name: '厂商', size: 125, source: 'HARDWARE' },
  { prop: 'assetModel', name: '型号', size: 145, source: 'HARDWARE' },
  { prop: 'status', name: '运行状态', size: 110, kind: 'status' },
  { prop: 'remark', name: '备注', size: 210 },
  ...HARDWARE_EXTRA_FIELDS
]

export function editableFields(row) {
  return [...common, ...(row.sourceType === 'SERVER' ? server : hardware)]
}
function normalized(field, value) {
  if (field === 'rootDirectLogin') return value === true || value === 1
  if (field === 'platformIds') return [...new Set((value || []).map(Number))].sort((a, b) => a - b)
  if (LOCATION_FIELDS.includes(field) || ['sshPort', 'channelCount', 'portCount'].includes(field)) {
    if (value === '' || value == null) return null
    return Number.isFinite(Number(value)) ? Number(value) : String(value)
  }
  return String(value ?? '').trim()
}
export function workbookRowChanges(original, draft) {
  const changes = {}
  for (const field of editableFields(draft)) {
    if (SECRET_FIELDS.includes(field)) {
      if (Object.prototype.hasOwnProperty.call(draft._secrets || {}, field)) changes[field] = draft._secrets[field]
    } else if (JSON.stringify(normalized(field, original[field])) !== JSON.stringify(normalized(field, draft[field]))) {
      changes[field] = normalized(field, draft[field])
    }
  }
  return changes
}
export function setWorkbookValue(row, field, value) {
  if (!row.canEdit || !editableFields(row).includes(field)) return
  if (SECRET_FIELDS.includes(field)) {
    row._passwordInputs = row._passwordInputs || {}
    row._passwordInputs[field] = value || ''
    const secrets = { ...row._secrets }
    if (value === '' || (value !== null && value === row._plainValues?.[field])) delete secrets[field]
    else secrets[field] = value
    row._secrets = secrets
    return
  }
  if (['roomId', 'cabinetId'].includes(field) && normalized(field, row[field]) === normalized(field, value)) return
  row[field] = value
  if (field === 'roomId') {
    row.cabinetId = null
    row.rackUStart = null
    row.rackUEnd = null
  } else if (field === 'cabinetId') {
    row.rackUStart = null
    row.rackUEnd = null
  }
}
export function workbookPayload(siteId, originals, rows) {
  return {
    siteId,
    rows: rows.filter(row => row.canEdit).flatMap(row => {
      const changes = workbookRowChanges(originals[row.deviceKey], row)
      return Object.keys(changes).length
        ? [{ sourceType: row.sourceType, sourceId: row.sourceId, revision: row.revision, changes }] : []
    })
  }
}
export function validateWorkbookDraft(originals, rows, { rooms = [], cabinets = [], platforms = [] } = {}) {
  const errors = []
  const add = (row, field, message) => errors.push({ key: row.deviceKey, field, message: row.assetName + ' (' + (row.ipAddress || row.deviceKey) + ')：' + message })
  for (const row of rows) {
    const changes = workbookRowChanges(originals[row.deviceKey], row)
    if (!row.canEdit || !Object.keys(changes).length) continue
    for (const [field, value] of Object.entries(changes)) {
      if (FIELD_LENGTHS[field] && String(value ?? '').length > FIELD_LENGTHS[field]) add(row, field, '字段长度超过限制')
    }
    if (!String(row.assetName || '').trim()) add(row, 'assetName', '设备名称不能为空')
    if (row.assetName?.length > 120) add(row, 'assetName', '设备名称不能超过120个字符')
    if (row.sourceType === 'SERVER' && !String(row.ipAddress || '').trim()) add(row, 'ipAddress', '服务器IP/地址不能为空')
    if (row.ipAddress && /\s/.test(row.ipAddress.trim())) add(row, 'ipAddress', 'IP/地址不能包含空格')
    if ('sshPort' in changes && (!Number.isInteger(Number(row.sshPort)) || Number(row.sshPort) < 1 || Number(row.sshPort) > 65535)) add(row, 'sshPort', 'SSH端口必须为1至65535的整数')
    for (const field of ['channelCount', 'portCount']) {
      if (field in changes && changes[field] !== null && (!Number.isInteger(changes[field]) || changes[field] < 0 || changes[field] > 65535)) add(row, field, '端口数、通道数必须是0至65535的整数')
    }
    if (!['0', '1'].includes(String(row.status))) add(row, 'status', '请选择运行状态')
    if ('platformIds' in changes && (row.platformIds || []).some(id => !platforms.some(p => Number(p.platformId) === Number(id) && (row.sourceType !== 'SERVER' || p.platformLevel === 'SUB')))) add(row, 'platformIds', '服务器只能归属当前现场的子平台')
    if (LOCATION_FIELDS.some(field => field in changes)) {
      const values = LOCATION_FIELDS.map(field => normalized(field, row[field]))
      if (values.some(value => value != null)) {
        const cabinet = cabinets.find(item => Number(item.cabinetId) === Number(row.cabinetId))
        const room = rooms.find(item => Number(item.roomId) === Number(row.roomId))
        if (!room || !cabinet || Number(cabinet.roomId) !== Number(row.roomId) || values.some(value => value == null)) add(row, 'cabinetId', '机房、机柜和U位需要完整配置')
        else if (!values.every(Number.isInteger) || row.rackUStart < 1 || row.rackUEnd < row.rackUStart || row.rackUEnd > (cabinet.uCapacity || 45)) add(row, 'rackUEnd', 'U位范围超出机柜容量或顺序不正确')
      }
    }
    for (const field of SECRET_FIELDS) {
      if (field in changes && changes[field] !== null && (changes[field].length > 128 || changes[field] === '******')) add(row, field, '新密码不能超过128个字符，也不能是占位符')
    }
    if (row.sourceType === 'SERVER' && ('otherUsername' in changes || 'otherPassword' in changes)
      && (!String(row.otherUsername || '').trim() || ['hik', 'root'].includes(String(row.otherUsername).toLowerCase()))) add(row, 'otherUsername', '其他账号必须填写非hik/root的用户名')
  }
  return errors
}
