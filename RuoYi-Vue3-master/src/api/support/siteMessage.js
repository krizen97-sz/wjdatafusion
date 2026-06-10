import request from '@/utils/request'

export function listSiteMessage(query) {
  return request({ url: '/support/siteMessage/list', method: 'get', params: query })
}

export function latestSiteMessage(query) {
  return request({ url: '/support/siteMessage/latest', method: 'get', params: query })
}

export function addSiteMessage(data) {
  return request({ url: '/support/siteMessage', method: 'post', data })
}
