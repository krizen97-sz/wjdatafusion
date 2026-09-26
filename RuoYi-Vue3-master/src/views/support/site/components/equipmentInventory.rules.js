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

export function buildEquipmentSheets(platforms, scopePlatformId) {
  const scoped = platforms.filter(item => !scopePlatformId || Number(item.platformId) === Number(scopePlatformId)
    || Number(item.parentPlatformId) === Number(scopePlatformId))
  return [
    { value: 'ALL', label: '全部设备' },
    ...scoped.filter(item => item.platformLevel === 'SUB').map(item => ({
      value: String(item.platformId), label: item.displayName || item.platformName
    })),
    { value: 'OTHER', label: '公共 / 未归属子平台' }
  ]
}

export function filterEquipmentSheet(devices, sheet, platforms) {
  const subIds = new Set(platforms.filter(item => item.platformLevel === 'SUB').map(item => Number(item.platformId)))
  return devices.filter(device => {
    const ids = (device.platformIds || []).map(Number)
    if (sheet === 'ALL') return true
    if (sheet === 'OTHER') return !ids.some(id => subIds.has(id))
    return ids.includes(Number(sheet))
  })
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
