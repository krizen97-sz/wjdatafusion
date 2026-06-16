import request from '@/utils/request'

export function listServer(query) {
  return request({ url: '/support/server/list', method: 'get', params: query })
}

export function getServer(serverId) {
  return request({ url: '/support/server/' + serverId, method: 'get' })
}

export function addServer(data) {
  return request({ url: '/support/server', method: 'post', data })
}

export function updateServer(data) {
  return request({ url: '/support/server', method: 'put', data })
}

export function delServer(serverId) {
  return request({ url: '/support/server/' + serverId, method: 'delete' })
}

export function viewServerPlain(serverId) {
  return request({ url: '/support/server/plain/' + serverId, method: 'get' })
}

export function listServerCredentials(serverId) {
  return request({ url: '/support/server/credential/list/' + serverId, method: 'get' })
}

export function addServerCredential(data) {
  return request({ url: '/support/server/credential', method: 'post', data })
}

export function updateServerCredential(data) {
  return request({ url: '/support/server/credential', method: 'put', data })
}

export function delServerCredential(credentialId) {
  return request({ url: '/support/server/credential/' + credentialId, method: 'delete' })
}

export function viewServerCredentialPlain(credentialId) {
  return request({ url: '/support/server/credential/plain/' + credentialId, method: 'get' })
}

export function listServerCredentialPlainSummaries(serverIds) {
  return request({ url: '/support/server/credential/plainSummary', method: 'post', data: serverIds })
}

export function previewServerImport(file) {
  const data = new FormData()
  data.append('file', file)
  return request({
    url: '/support/server/importPreview',
    method: 'post',
    data,
    headers: {
      'Content-Type': 'multipart/form-data',
      repeatSubmit: false
    }
  })
}
