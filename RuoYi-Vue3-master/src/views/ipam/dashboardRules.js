import { getTargetTypeLabel } from './ipamCatalog.js'

const UNASSIGNED_STATION_NAME = '未分类'

export function buildStatusDistribution(summary = {}) {
  return [
    { name: '空闲', value: numberValue(summary.freeCount), itemStyle: { color: '#5b697a' } },
    { name: '保留', value: numberValue(summary.reservedCount), itemStyle: { color: '#f2b84b' } },
    { name: '已占用', value: numberValue(summary.allocatedCount), itemStyle: { color: '#39a0ff' } },
    { name: '已下发', value: numberValue(summary.issuedCount), itemStyle: { color: '#32c98c' } },
    { name: '禁用', value: numberValue(summary.disabledCount), itemStyle: { color: '#ff6374' } }
  ]
}

export function buildStationAllocation(networks = []) {
  const groups = new Map()
  for (const network of networks) {
    const name = String(network?.policeStationName || '').trim() || UNASSIGNED_STATION_NAME
    if (!groups.has(name)) {
      groups.set(name, { name, networkCount: 0, capacity: 0, occupied: 0, free: 0, reserved: 0, disabled: 0 })
    }
    const group = groups.get(name)
    group.networkCount += 1
    group.capacity += getNetworkCapacity(network)
    group.occupied += getNetworkOccupied(network)
    group.free += numberValue(network?.freeCount)
    group.reserved += numberValue(network?.reservedCount)
    group.disabled += numberValue(network?.disabledCount)
  }
  return [...groups.values()].sort((left, right) => {
    if (left.name === UNASSIGNED_STATION_NAME) return 1
    if (right.name === UNASSIGNED_STATION_NAME) return -1
    return right.occupied - left.occupied || left.name.localeCompare(right.name, 'zh-CN')
  })
}

export function buildNetworkUsage(networks = []) {
  return networks.map((network) => {
    const capacity = getNetworkCapacity(network)
    const occupied = getNetworkOccupied(network)
    return {
      networkId: network.networkId,
      name: network.networkName || `${network.startIp || ''} - ${network.endIp || ''}`,
      stationName: String(network.policeStationName || '').trim() || UNASSIGNED_STATION_NAME,
      gatewayIp: network.gatewayIp || '-',
      capacity,
      occupied,
      free: numberValue(network.freeCount),
      usage: capacity ? Math.min(100, Math.round((occupied / capacity) * 1000) / 10) : 0
    }
  })
}

export function buildNetworkPressureDistribution(rows = []) {
  const bands = [
    { name: '容量充足', range: '< 60%', value: 0, capacity: 0, occupied: 0, color: '#32c98c' },
    { name: '持续关注', range: '60% - 80%', value: 0, capacity: 0, occupied: 0, color: '#28c2d1' },
    { name: '容量预警', range: '80% - 90%', value: 0, capacity: 0, occupied: 0, color: '#f2b84b' },
    { name: '高负载', range: '>= 90%', value: 0, capacity: 0, occupied: 0, color: '#ff6374' }
  ]

  for (const row of rows) {
    const usage = numberValue(row?.usage)
    const band = usage >= 90 ? bands[3] : usage >= 80 ? bands[2] : usage >= 60 ? bands[1] : bands[0]
    band.value += 1
    band.capacity += numberValue(row?.capacity)
    band.occupied += numberValue(row?.occupied)
  }
  return bands
}

export function buildStationEfficiency(rows = []) {
  return rows.map((row) => {
    const capacity = numberValue(row?.capacity)
    const occupied = numberValue(row?.occupied)
    return {
      ...row,
      capacity,
      occupied,
      usage: capacity ? Math.min(100, Math.round((occupied / capacity) * 1000) / 10) : 0
    }
  }).sort((left, right) => right.usage - left.usage || right.occupied - left.occupied)
}

export function buildNetworkLoadMatrix(rows = []) {
  const groups = new Map()
  for (const row of rows) {
    const stationName = String(row?.stationName || '').trim() || UNASSIGNED_STATION_NAME
    if (!groups.has(stationName)) groups.set(stationName, [])
    groups.get(stationName).push(row)
  }

  const stations = [...groups.keys()].sort((left, right) => {
    if (left === UNASSIGNED_STATION_NAME) return 1
    if (right === UNASSIGNED_STATION_NAME) return -1
    return left.localeCompare(right, 'zh-CN')
  })
  const sortedGroups = stations.map((stationName) => [...groups.get(stationName)].sort((left, right) => {
    const leftValue = ipv4ToNumber(left?.gatewayIp)
    const rightValue = ipv4ToNumber(right?.gatewayIp)
    return (leftValue ?? Number.MAX_SAFE_INTEGER) - (rightValue ?? Number.MAX_SAFE_INTEGER)
  }))
  const columnCount = Math.max(1, ...sortedGroups.map((group) => group.length))
  const cells = []
  sortedGroups.forEach((group, stationIndex) => {
    group.forEach((row, columnIndex) => {
      cells.push({
        value: [columnIndex, stationIndex, numberValue(row?.usage)],
        networkId: row?.networkId,
        networkName: row?.name,
        gatewayIp: row?.gatewayIp,
        occupied: numberValue(row?.occupied),
        capacity: numberValue(row?.capacity)
      })
    })
  })

  return {
    stations,
    columns: Array.from({ length: columnCount }, (_, index) => `第${index + 1}段`),
    cells
  }
}

export function normalizeDimensionRows(rows = [], type = 'plain') {
  return rows.map((row) => ({
    name: type === 'targetType' ? getTargetTypeLabel(row?.name) : (String(row?.name || '').trim() || '未填写'),
    value: numberValue(row?.value)
  })).filter((row) => row.value > 0)
}

export function normalizeManufacturerRows(rows = []) {
  const buckets = new Map()
  for (const row of rows) {
    const rawName = String(row?.name || '').trim()
    const name = looksLikeCredential(rawName) ? '待核验' : (rawName || '未填写')
    buckets.set(name, (buckets.get(name) || 0) + numberValue(row?.value))
  }
  return [...buckets.entries()]
    .map(([name, value]) => ({ name, value }))
    .filter((row) => row.value > 0)
    .sort((left, right) => right.value - left.value)
}

export function filterCommunityOverview(rows = [], keyword = '') {
  const normalizedKeyword = String(keyword || '').trim().toLowerCase()
  if (!normalizedKeyword) return rows
  const queryIpValue = ipv4ToNumber(normalizedKeyword)
  return rows.filter((row) => {
    if (queryIpValue !== null) {
      const firstIpValue = ipv4ToNumber(row.firstIp)
      const lastIpValue = ipv4ToNumber(row.lastIp)
      const inAssignedSpan = firstIpValue !== null && lastIpValue !== null
        && queryIpValue >= firstIpValue && queryIpValue <= lastIpValue
      const exactIpReference = [row.networkNameSummary, row.internalIpSummary, row.mappingSummary]
        .some((value) => extractIpv4Values(value).includes(normalizedKeyword))
      if (inAssignedSpan || exactIpReference) return true
    }

    return [
      row.communityName,
      row.firstIp,
      row.lastIp,
      row.networkNameSummary,
      row.policeStationSummary,
      row.internalIpSummary,
      row.targetTypeSummary,
      row.manufacturerSummary,
      row.accessUnitSummary,
      row.ownerSummary
    ].some((value) => String(value || '').toLowerCase().includes(normalizedKeyword))
      && queryIpValue === null
  })
}

export function getNetworkCapacity(network) {
  return Math.max(numberValue(network?.totalCount) - 3, 0)
}

export function getNetworkOccupied(network) {
  return numberValue(network?.allocatedCount) + numberValue(network?.issuedCount)
}

function numberValue(value) {
  const normalized = Number(value)
  return Number.isFinite(normalized) ? normalized : 0
}

function ipv4ToNumber(value) {
  const parts = String(value || '').trim().split('.')
  if (parts.length !== 4 || parts.some((part) => !/^\d{1,3}$/.test(part) || Number(part) > 255)) return null
  return parts.reduce((total, part) => total * 256 + Number(part), 0)
}

function extractIpv4Values(value) {
  return String(value || '').toLowerCase().match(/(?:\d{1,3}\.){3}\d{1,3}/g) || []
}

function looksLikeCredential(value) {
  return /^(?:admin|root|user|test)\d*$/i.test(value) || /^[a-z]\d{5,}$/i.test(value)
}
