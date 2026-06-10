import request from '@/utils/request'

export function listPlatform(query) {
  return request({ url: '/support/platform/list', method: 'get', params: query })
}

export function getPlatform(platformId) {
  return request({ url: '/support/platform/' + platformId, method: 'get' })
}

export function listPlatformTree(siteId) {
  return request({ url: '/support/platform/tree/' + siteId, method: 'get' })
}

export function addPlatform(data) {
  return request({ url: '/support/platform', method: 'post', data })
}

export function updatePlatform(data) {
  return request({ url: '/support/platform', method: 'put', data })
}

export function delPlatform(platformId) {
  return request({ url: '/support/platform/' + platformId, method: 'delete' })
}

export function bindServer(params) {
  return request({ url: '/support/platform/bindServer', method: 'post', params })
}

export function unbindServer(params) {
  return request({ url: '/support/platform/unbindServer', method: 'delete', params })
}

export function listPlatformServers(platformId) {
  return request({ url: '/support/platform/servers/' + platformId, method: 'get' })
}

export function bindContact(params) {
  return request({ url: '/support/platform/bindContact', method: 'post', params })
}

export function unbindContact(params) {
  return request({ url: '/support/platform/unbindContact', method: 'delete', params })
}

export function listPlatformContacts(platformId) {
  return request({ url: '/support/platform/contacts/' + platformId, method: 'get' })
}
