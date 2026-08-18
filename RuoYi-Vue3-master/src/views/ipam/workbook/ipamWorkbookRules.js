import { DEVICE_TYPE_OPTIONS, getStatusMeta, getTargetTypeLabel } from '../ipamCatalog.js'

export const WORKBOOK_PAGE_SIZE = 1024
export const WORKBOOK_COMMIT_SIZE = 256

const ASSIGNED_STATUSES = new Set(['ALLOCATED', 'ISSUED'])
const LOCKED_STATUSES = new Set(['RESERVED', 'DISABLED'])
const BUSINESS_FIELDS = [
  'communityName',
  'internalIpAddress',
  'targetTypeLabel',
  'targetName',
  'manufacturer',
  'loginUsername',
  'accessUnit',
  'purpose',
  'mappingAddress',
  'mappingPort',
  'mappingDescription',
  'ownerName',
  'ownerPhone',
  'remark'
]

const DEVICE_TYPE_VALUE_BY_LABEL = new Map(
  DEVICE_TYPE_OPTIONS.flatMap((option) => [[option.label, option.value], [option.value, option.value]])
)

function text(value) {
  return value == null ? '' : String(value).trim()
}

function ipToNumber(value) {
  const octets = String(value || '').split('.').map(Number)
  if (octets.length !== 4 || octets.some((item) => !Number.isInteger(item) || item < 0 || item > 255)) {
    return Number.MAX_SAFE_INTEGER
  }
  return (((octets[0] * 256 + octets[1]) * 256 + octets[2]) * 256 + octets[3])
}

function splitSummary(value) {
  return String(value || '').split('、').map((item) => item.trim()).filter(Boolean)
}

function normalizeTargetType(value) {
  const parts = String(value || '').split(/[、,，\s]+/).map((item) => item.trim()).filter(Boolean)
  if (!parts.length) return null
  return parts.map((item) => DEVICE_TYPE_VALUE_BY_LABEL.get(item) || item).join(',')
}

function buildRow(raw, context = {}) {
  const statusCode = raw.status || 'FREE'
  const boundaryAddress = Boolean(raw.boundaryAddress)
  const gatewayAddress = raw.reservedReason === '网关'
  const locked = boundaryAddress || gatewayAddress || LOCKED_STATUSES.has(statusCode)
  const networkId = Number(raw.networkId || context.networkId)

  return {
    _rowKey: `${networkId}:${raw.ipAddress}`,
    _locked: locked,
    _dirty: false,
    addressId: raw.addressId || null,
    networkId,
    segmentId: raw.segmentId || context.segmentId || null,
    ipAddress: raw.ipAddress || '',
    statusCode,
    statusLabel: getStatusMeta(statusCode).label,
    connectivityLabel: raw.connectivityStatus === 'ONLINE'
      ? '在线'
      : raw.connectivityStatus === 'OFFLINE'
        ? '离线'
        : raw.connectivityStatus
          ? '异常'
          : '未扫描',
    policeStationName: raw.policeStationName || context.policeStationName || '',
    networkName: raw.networkName || context.networkName || '',
    communityName: raw.communityName || '',
    internalIpAddress: raw.internalIpAddress || '',
    targetTypeLabel: raw.targetType ? getTargetTypeLabel(raw.targetType) : '',
    targetName: raw.targetName || '',
    manufacturer: raw.manufacturer || '',
    loginUsername: raw.loginUsername || '',
    credentialState: raw.credentialConfigured ? '已配置' : '未配置',
    credentialConfigured: Boolean(raw.credentialConfigured),
    accessUnit: raw.accessUnit || '',
    purpose: raw.purpose || '',
    mappingAddress: raw.mappingAddress || '',
    mappingPort: raw.mappingPort || '',
    mappingDescription: raw.mappingDescription || '',
    ownerName: raw.ownerName || '',
    ownerPhone: raw.ownerPhone || '',
    remark: raw.remark || '',
    reservedReason: raw.reservedReason || '',
    lastScanTime: raw.lastScanTime || ''
  }
}

export function buildNetworkWorkbookRows(rows, network) {
  return (rows || []).map((row) => buildRow(row, network))
}

export function buildCommunityWorkbookRows(rows) {
  return (rows || []).map((row) => buildRow(row))
}

export function buildNetworkScopeTree(networks) {
  const groups = new Map()
  ;(networks || []).forEach((network) => {
    const stationName = text(network.policeStationName) || '未分类'
    if (!groups.has(stationName)) groups.set(stationName, [])
    groups.get(stationName).push(network)
  })

  return [...groups.entries()]
    .sort(([left], [right]) => left === '未分类' ? 1 : right === '未分类' ? -1 : left.localeCompare(right, 'zh-CN'))
    .map(([stationName, items]) => ({
      key: `station:${stationName}`,
      label: stationName,
      kind: 'group',
      count: items.length,
      children: items
        .slice()
        .sort((left, right) => ipToNumber(left.gatewayIp || left.startIp) - ipToNumber(right.gatewayIp || right.startIp))
        .map((network) => ({
          key: `network:${network.networkId}`,
          label: network.networkName || `${network.startIp} - ${network.endIp}`,
          description: network.gatewayIp || network.startIp || '',
          count: Number(network.totalCount || 0),
          kind: 'network',
          value: network
        }))
    }))
}

export function buildCommunityScopeTree(communities) {
  const groups = new Map()
  ;(communities || []).forEach((community) => {
    const stationNames = splitSummary(community.policeStationSummary)
    const stationName = stationNames.length === 1 ? stationNames[0] : stationNames.length > 1 ? '跨辖区' : '未分类'
    if (!groups.has(stationName)) groups.set(stationName, [])
    groups.get(stationName).push(community)
  })

  return [...groups.entries()]
    .sort(([left], [right]) => left === '未分类' ? 1 : right === '未分类' ? -1 : left.localeCompare(right, 'zh-CN'))
    .map(([stationName, items]) => ({
      key: `community-group:${stationName}`,
      label: stationName,
      kind: 'group',
      count: items.length,
      children: items
        .slice()
        .sort((left, right) => text(left.communityName).localeCompare(text(right.communityName), 'zh-CN'))
        .map((community) => ({
          key: `community:${community.communityName}`,
          label: community.communityName,
          description: community.networkNameSummary || community.firstIp || '',
          count: Number(community.addressCount || 0),
          kind: 'community',
          value: community
        }))
    }))
}

export function filterScopeTree(tree, keyword) {
  const normalized = text(keyword).toLocaleLowerCase('zh-CN')
  if (!normalized) return tree
  const searchedIp = ipToNumber(normalized)
  const isIpv4Search = searchedIp !== Number.MAX_SAFE_INTEGER

  return (tree || []).flatMap((group) => {
    const groupMatches = `${group.label} ${group.description || ''}`.toLocaleLowerCase('zh-CN').includes(normalized)
    const children = groupMatches
      ? group.children
      : (group.children || []).filter((item) => {
          const source = `${item.label} ${item.description || ''} ${item.value?.startIp || ''} ${item.value?.endIp || ''} ${item.value?.firstIp || ''} ${item.value?.lastIp || ''}`
          if (source.toLocaleLowerCase('zh-CN').includes(normalized)) return true
          if (!isIpv4Search || item.kind !== 'network') return false
          return searchedIp >= ipToNumber(item.value?.startIp) && searchedIp <= ipToNumber(item.value?.endIp)
        })
    return children.length ? [{ ...group, children }] : []
  })
}

export function workbookRowMatches(row, keyword) {
  const normalized = text(keyword).toLocaleLowerCase('zh-CN')
  if (!normalized) return true
  return [
    row.ipAddress,
    row.statusLabel,
    row.connectivityLabel,
    row.policeStationName,
    row.networkName,
    row.communityName,
    row.internalIpAddress,
    row.targetTypeLabel,
    row.targetName,
    row.manufacturer,
    row.loginUsername,
    row.accessUnit,
    row.purpose,
    row.mappingAddress,
    row.mappingPort,
    row.mappingDescription,
    row.ownerName,
    row.ownerPhone,
    row.remark
  ].some((value) => String(value || '').toLocaleLowerCase('zh-CN').includes(normalized))
}

export function markWorkbookRowsDirty(detail) {
  const changedRows = detail?.model
    ? [detail.model]
    : detail?.data
      ? Object.values(detail.data)
      : []
  return changedRows.flatMap((row) => {
    if (!row?._rowKey) return []
    row._dirty = true
    return [row._rowKey]
  })
}

export function validateWorkbookRows(rows, subjectNameLabel = '小区名称') {
  const errors = []
  ;(rows || []).forEach((row) => {
    const hasBusinessContent = BUSINESS_FIELDS.some((field) => text(row[field]))
    if ((ASSIGNED_STATUSES.has(row.statusCode) || hasBusinessContent) && !text(row.communityName)) {
      errors.push(`${row.ipAddress}：${row.statusCode === 'FREE' ? '填写设备信息前' : '已占用地址'}必须填写${subjectNameLabel}`)
    }
  })
  return errors
}

export function toWorkbookCommitRow(row) {
  const hasBusinessContent = BUSINESS_FIELDS.some((field) => text(row[field]))
  if (row.statusCode === 'FREE' && !hasBusinessContent) return null

  return {
    addressId: row.addressId || null,
    ipAddress: row.ipAddress,
    status: row.statusCode === 'FREE' ? 'ALLOCATED' : row.statusCode,
    communityName: text(row.communityName) || null,
    internalIpAddress: text(row.internalIpAddress) || null,
    targetType: normalizeTargetType(row.targetTypeLabel),
    targetName: text(row.targetName) || null,
    manufacturer: text(row.manufacturer) || null,
    loginUsername: text(row.loginUsername) || null,
    accessUnit: text(row.accessUnit) || null,
    purpose: text(row.purpose) || null,
    mappingAddress: text(row.mappingAddress) || null,
    mappingPort: text(row.mappingPort) || null,
    mappingDescription: text(row.mappingDescription) || null,
    ownerName: text(row.ownerName) || null,
    ownerPhone: text(row.ownerPhone) || null,
    remark: text(row.remark) || null
  }
}

export function buildWorkbookCommitBatches(rows, dirtyKeys, batchSize = WORKBOOK_COMMIT_SIZE) {
  const grouped = new Map()
  ;(rows || []).forEach((row) => {
    if (!dirtyKeys.has(row._rowKey) || row._locked) return
    const payload = toWorkbookCommitRow(row)
    if (!payload) return
    if (!grouped.has(row.networkId)) grouped.set(row.networkId, [])
    grouped.get(row.networkId).push(payload)
  })

  const batches = []
  grouped.forEach((networkRows, networkId) => {
    for (let index = 0; index < networkRows.length; index += batchSize) {
      batches.push({ networkId, rows: networkRows.slice(index, index + batchSize) })
    }
  })
  return batches
}
