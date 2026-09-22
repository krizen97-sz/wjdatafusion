export const equipmentIntakeTypes = [
  { value: 'SERVER', label: '服务器' },
  { value: 'SWITCH', label: '交换机' },
  { value: 'DECODER', label: '解码器' },
  { value: 'TERMINAL', label: '终端' },
  { value: 'GATEWAY', label: '网闸' }
]

export function resolveIntakePlatform(platforms, platformId, assetType) {
  const platform = platforms.find((item) => Number(item.platformId) === Number(platformId))
  return platform && (assetType !== 'SERVER' || platform.platformLevel === 'SUB') ? platform.platformId : null
}

export function buildEquipmentCreatePayload(siteId, form) {
  const text = (value) => String(value || '').trim()
  const result = { siteId, platformId: form.platformId || null }
  if (form.assetType === 'SERVER') {
    result.server = {
      serverName: text(form.assetName), serverAddress: text(form.ipAddress),
      sshPort: form.sshPort, osType: text(form.osType), status: form.status,
      hikPassword: form.hikPassword || null, rootPassword: form.rootPassword || null,
      otherUsername: text(form.loginUsername), otherPassword: form.loginPassword || null,
      remark: text(form.remark)
    }
  } else {
    result.hardware = Object.fromEntries([
      'assetName', 'assetType', 'networkEnv', 'ipAddress', 'manageIp', 'macAddress',
      'manufacturer', 'assetModel', 'serialNo', 'ownerOrg', 'ownerContact', 'loginUsername', 'remark', 'status'
    ].map((key) => [key, text(form[key])]))
    result.hardware.loginPassword = form.loginPassword || null
  }
  return result
}

export function getDeviceIntakeGaps(device, links = []) {
  const gaps = []
  if (!device.cabinetId || !device.rackUStart || !device.rackUEnd) gaps.push('UNPLACED')
  if (!device.platformIds?.length && !device.platformId) gaps.push('UNBOUND')
  if (!links.some((link) => link.sourceType === device.sourceType && Number(link.sourceId) === Number(device.sourceId))) gaps.push('NO_UPLINK')
  return gaps
}
