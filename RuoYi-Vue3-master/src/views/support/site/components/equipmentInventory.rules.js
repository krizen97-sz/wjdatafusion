export const EQUIPMENT_GRID_THEMES = [{
  name: 'equipment', extends: 'compact',
  tokens: {
    primary: 'var(--el-color-primary)',
    text: 'var(--el-text-color-primary)',
    foreground: 'var(--el-text-color-primary)',
    background: 'var(--el-bg-color)',
    headerBg: 'var(--el-fill-color-light)',
    headerColor: 'var(--el-text-color-regular)',
    rowHeadersBg: 'var(--el-fill-color-light)',
    rowHeadersColor: 'var(--el-text-color-regular)',
    divider: 'var(--el-border-color)',
    border: 'var(--el-border-color)',
    headerBorder: 'var(--el-border-color)',
    cellBorder: 'var(--el-border-color-lighter)',
    cellVerticalBorder: 'var(--el-border-color-lighter)',
    focusedBg: 'var(--el-fill-color)',
    rowHover: 'var(--el-fill-color-light)',
    cellDisabledBg: 'var(--el-bg-color)',
    fontFamily: 'var(--el-font-family)'
  }
}]

export function inventoryDeviceKey(device) {
  return device.deviceKey || device.sourceType + ':' + device.sourceId
}

export function filterEquipmentNode(devices, nodeKey, platforms) {
  const knownIds = new Set(platforms.map(item => Number(item.platformId)))
  const [kind, value] = String(nodeKey || 'ALL').split(':')
  const id = Number(value)
  const platform = platforms.find(item => Number(item.platformId) === id)
  const childIds = new Set(platforms.filter(item => Number(item.parentPlatformId) === id).map(item => Number(item.platformId)))
  const unique = [...new Map(devices.map(device => [inventoryDeviceKey(device), device])).values()]
  return unique.filter(device => {
    const ids = (device.platformIds || []).map(Number)
    if (kind === 'ALL') return true
    if (kind === 'OTHER') return !ids.some(key => knownIds.has(key))
    if (kind === 'DIRECT') return ids.includes(id)
    if (kind !== 'PLATFORM' || !platform) return false
    return ids.includes(id) || (platform.platformLevel === 'MAIN' &&
      (ids.some(key => childIds.has(key)) || (device.mainPlatformIds || []).map(Number).includes(id)))
  })
}

export function buildEquipmentTree(platforms, devices, scopePlatformId, siteName = '全部设备') {
  const scope = platforms.find(item => Number(item.platformId) === Number(scopePlatformId))
  const rows = scopePlatformId ? filterEquipmentNode(devices, 'PLATFORM:' + scopePlatformId, platforms)
    : filterEquipmentNode(devices, 'ALL', platforms)
  const allowed = item => !scopePlatformId || Number(item.platformId) === Number(scopePlatformId)
    || (scope?.platformLevel === 'MAIN' && Number(item.parentPlatformId) === Number(scopePlatformId))
    || (scope?.platformLevel === 'SUB' && Number(item.platformId) === Number(scope.parentPlatformId))
  const available = platforms.filter(allowed)
  const makeNode = platform => ({
    id: 'PLATFORM:' + platform.platformId,
    platformId: platform.platformId,
    kind: platform.platformLevel === 'MAIN' ? 'main' : 'sub',
    label: platform.platformName || platform.displayName || '平台 ' + platform.platformId,
    count: filterEquipmentNode(rows, 'PLATFORM:' + platform.platformId, platforms).length
  })
  const mains = available.filter(item => item.platformLevel === 'MAIN')
  const mainIds = new Set(mains.map(item => Number(item.platformId)))
  const children = mains.map(main => {
    const node = makeNode(main)
    node.children = available.filter(item => item.platformLevel === 'SUB' && Number(item.parentPlatformId) === Number(main.platformId)).map(makeNode)
    const directCount = filterEquipmentNode(rows, 'DIRECT:' + main.platformId, platforms).length
    if (directCount) node.children.push({
      id: 'DIRECT:' + main.platformId, platformId: main.platformId, kind: 'direct',
      label: '主平台直属设备', count: directCount
    })
    return node
  })
  children.push(...available.filter(item => item.platformLevel === 'SUB' && !mainIds.has(Number(item.parentPlatformId))).map(makeNode))
  children.push({ id: 'OTHER', kind: 'other', label: '公共 / 未归属平台', count: filterEquipmentNode(rows, 'OTHER', platforms).length })
  return [{ id: 'ALL', kind: 'site', label: siteName || '全部设备', count: rows.length, children }]
}

export function findEquipmentTreeNode(tree, key) {
  for (const node of tree) {
    if (node.id === key) return node
    const child = findEquipmentTreeNode(node.children || [], key)
    if (child) return child
  }
  return null
}

export function inventoryPasswordCells(device, credentials, visible) {
  const server = device.sourceType === 'SERVER'
  if (!visible) return {
    loginPassword: server ? '-' : '******',
    hikPassword: server ? '******' : '-',
    rootPassword: server ? '******' : '-',
    otherUsername: '', otherPassword: server ? '******' : '-'
  }
  if (!credentials) return { loginPassword: '加载中', hikPassword: server ? '加载中' : '-', rootPassword: server ? '加载中' : '-', otherPassword: '' }
  const lookup = name => credentials.filter(item => item.username === name).map(item => item.password || '-').join(' / ') || '-'
  const other = credentials.filter(item => !['hik', 'root'].includes(item.username))
  return {
    loginPassword: server ? '-' : credentials.map(item => item.password || '-').join(' / ') || '-',
    hikPassword: server ? lookup('hik') : '-',
    rootPassword: server ? lookup('root') : '-',
    otherUsername: server ? other.map(item => item.username || '-').join(' / ') : '',
    otherPassword: server ? other.map(item => item.password || '-').join(' / ') || '-' : '-'
  }
}
