export const MAX_CONFIG_SELECTION = 256
export const UNASSIGNED_STATION_NAME = '未分类'

function ipv4SortValue(value) {
  const parts = String(value ?? '').trim().split('.')
  if (parts.length !== 4 || parts.some((part) => !/^\d{1,3}$/.test(part) || Number(part) > 255)) {
    return Number.MAX_SAFE_INTEGER
  }
  return parts.reduce((total, part) => (total * 256) + Number(part), 0)
}

function compareSortValue(left, right) {
  if (left === right) return 0
  return left < right ? -1 : 1
}

export function buildNetworkStationTree(networks) {
  const groups = new Map()

  for (const network of Array.isArray(networks) ? networks : []) {
    const normalizedStationName = String(network?.policeStationName ?? '').trim()
    const stationName = normalizedStationName || UNASSIGNED_STATION_NAME
    if (!groups.has(stationName)) {
      groups.set(stationName, {
        nodeId: `station:${stationName}`,
        nodeType: 'station',
        label: stationName,
        stationName,
        isUnassigned: !normalizedStationName,
        networkCount: 0,
        freeCount: 0,
        occupiedCount: 0,
        firstGatewaySortValue: Number.MAX_SAFE_INTEGER,
        children: []
      })
    }

    const group = groups.get(stationName)
    const gatewaySortValue = ipv4SortValue(network?.gatewayIp || network?.startIp)
    group.networkCount += 1
    group.freeCount += Number(network?.freeCount || 0)
    group.occupiedCount += Number(network?.allocatedCount || 0) + Number(network?.issuedCount || 0)
    group.firstGatewaySortValue = Math.min(group.firstGatewaySortValue, gatewaySortValue)
    group.children.push({
      nodeId: `network:${network?.networkId}`,
      nodeType: 'network',
      label: network?.networkName || network?.gatewayIp || '未命名网段',
      gatewaySortValue,
      network
    })
  }

  const tree = [...groups.values()]
  for (const group of tree) {
    group.children.sort((left, right) => (
      compareSortValue(left.gatewaySortValue, right.gatewaySortValue)
      || compareSortValue(ipv4SortValue(left.network?.startIp), ipv4SortValue(right.network?.startIp))
      || Number(left.network?.networkId || 0) - Number(right.network?.networkId || 0)
    ))
  }

  return tree.sort((left, right) => {
    if (left.isUnassigned !== right.isUnassigned) return left.isUnassigned ? 1 : -1
    return compareSortValue(left.firstGatewaySortValue, right.firstGatewaySortValue)
      || left.stationName.localeCompare(right.stationName, 'zh-CN')
  })
}

export function normalizeIpv4Octet(value) {
  return String(value ?? '').replace(/\D/g, '').slice(0, 3)
}

export function isIpv4OctetValid(value) {
  return /^\d{1,3}$/.test(String(value ?? '')) && Number(value) <= 255
}

export function splitIpv4Value(value) {
  if (value == null || String(value).trim() === '') return ['', '', '', '']
  const parts = String(value).trim().split('.').slice(0, 4)
  while (parts.length < 4) parts.push('')
  return parts.map(normalizeIpv4Octet)
}

export function storeRevealedCredential(row, password) {
  if (!row) return null
  const value = password == null || String(password) === '' ? null : String(password)
  row._revealedPassword = value
  return value
}

export function appendSelectionRange(current, addresses, startIndex, endIndex, shouldSelect = true, canSelect = () => true) {
  const next = new Set(current)
  const lower = Math.max(0, Math.min(startIndex, endIndex))
  const upper = Math.min(addresses.length - 1, Math.max(startIndex, endIndex))
  for (let index = lower; index <= upper; index += 1) {
    const ipAddress = addresses[index]?.ipAddress
    if (!ipAddress || !canSelect(addresses[index])) continue
    if (!shouldSelect) {
      next.delete(ipAddress)
      continue
    }
    if (next.size >= MAX_CONFIG_SELECTION && !next.has(ipAddress)) break
    next.add(ipAddress)
  }
  return next
}
