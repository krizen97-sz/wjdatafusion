import request from '@/utils/request'

export function createEquipment(data) {
  return request({ url: '/support/equipment', method: 'post', data })
}

export function deleteEquipmentBatch(data) {
  return request({ url: '/support/equipment/batch', method: 'delete', data })
}

export function bindEquipmentPlatform(data) {
  return request({ url: '/support/equipment/platform/bind', method: 'put', data })
}

export function unbindEquipmentPlatform(data) {
  return request({ url: '/support/equipment/platform/unbind', method: 'put', data })
}

export function previewEquipmentServers(data) {
  return request({ url: '/support/equipment/servers/preview', method: 'post', data, headers: { repeatSubmit: false } })
}

export function previewEquipmentServerFile(siteId, platformId, file) {
  const data = new FormData()
  data.append('siteId', siteId)
  data.append('platformId', platformId)
  data.append('file', file)
  return request({ url: '/support/equipment/servers/importPreview', method: 'post', data,
    headers: { 'Content-Type': 'multipart/form-data', repeatSubmit: false } })
}

export function commitEquipmentServers(data) {
  return request({ url: '/support/equipment/servers/commit', method: 'post', data })
}
