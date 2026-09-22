import request from '@/utils/request'

export function listServer(query) {
  return request({ url: '/support/server/list', method: 'get', params: query })
}

export function getServer(serverId) {
  return request({ url: '/support/server/' + serverId, method: 'get' })
}

export function updateServer(data) {
  return request({ url: '/support/server', method: 'put', data })
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
