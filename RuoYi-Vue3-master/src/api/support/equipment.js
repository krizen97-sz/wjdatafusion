import request from '@/utils/request'

const scanUrl = '/support/equipment/servers/scan'
export function getServerScanOptions(siteId) { return request({ url: scanUrl + '/options/' + siteId, method: 'get' }) }
export function saveServerScanCredential(data) { return request({ url: scanUrl + '/credentials', method: 'post', data, headers: { repeatSubmit: false } }) }
export function deleteServerScanCredential(siteId, id) { return request({ url: scanUrl + '/credentials/' + siteId + '/' + id, method: 'delete' }) }
export function startServerScan(data) { return request({ url: scanUrl + '/tasks', method: 'post', data, timeout: 30000, headers: { repeatSubmit: false } }) }
export function getServerScan(id) { return request({ url: scanUrl + '/tasks/' + id, method: 'get' }) }
export function getActiveServerScan(siteId) { return request({ url: scanUrl + '/active/' + siteId, method: 'get' }) }
export function confirmServerScan(id, data) { return request({ url: scanUrl + '/tasks/' + id + '/confirm', method: 'post', data, headers: { repeatSubmit: false } }) }
export function cancelServerScan(id) { return request({ url: scanUrl + '/tasks/' + id + '/cancel', method: 'post' }) }
export function getServerScanProfiles(siteId) { return request({ url: scanUrl + '/profiles/' + siteId, method: 'get' }) }

export function checkServerLogin(data) {
  return request({ url: '/support/equipment/servers/loginCheck', method: 'post', data,
    timeout: 90000, headers: { repeatSubmit: false } })
}

export function getEquipmentWorkbook(siteId) {
  return request({ url: '/support/equipment/workbook/' + siteId, method: 'get' })
}

export function saveEquipmentWorkbook(data) {
  return request({ url: '/support/equipment/workbook', method: 'put', data,
    headers: { repeatSubmit: false } })
}

export function readEquipmentPasswords(data) {
  return request({ url: '/support/equipment/credentials/plain', method: 'post', data,
    headers: { repeatSubmit: false } })
}

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
