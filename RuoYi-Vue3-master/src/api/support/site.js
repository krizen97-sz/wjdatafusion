import request from '@/utils/request'

export function listSite(query) {
  return request({ url: '/support/site/list', method: 'get', params: query })
}

export function getSite(siteId) {
  return request({ url: '/support/site/' + siteId, method: 'get' })
}

export function getSiteOverview(siteId) {
  return request({ url: '/support/site/overview/' + siteId, method: 'get' })
}

export function getSiteWorkbench(siteId) {
  return request({ url: '/support/site/workbench/' + siteId, method: 'get' })
}

export function listChangeLog(query) {
  return request({ url: '/support/changeLog/list', method: 'get', params: query })
}

export function previewSiteCode(data) {
  return request({
    url: '/support/site/code-preview',
    method: 'post',
    data,
    headers: {
      repeatSubmit: false
    }
  })
}

export function addSite(data) {
  return request({ url: '/support/site', method: 'post', data })
}

export function updateSite(data) {
  return request({ url: '/support/site', method: 'put', data })
}

export function delSite(siteId) {
  return request({ url: '/support/site/' + siteId, method: 'delete' })
}
