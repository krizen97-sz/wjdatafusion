import request from '@/utils/request'

export function listOrg(query) {
  return request({ url: '/support/org/list', method: 'get', params: query })
}

export function getOrg(orgId) {
  return request({ url: '/support/org/' + orgId, method: 'get' })
}

export function addOrg(data) {
  return request({ url: '/support/org', method: 'post', data })
}

export function updateOrg(data) {
  return request({ url: '/support/org', method: 'put', data })
}

export function delOrg(orgId) {
  return request({ url: '/support/org/' + orgId, method: 'delete' })
}

export function listOrgPlatforms(orgId) {
  return request({ url: '/support/org/platforms/' + orgId, method: 'get' })
}
