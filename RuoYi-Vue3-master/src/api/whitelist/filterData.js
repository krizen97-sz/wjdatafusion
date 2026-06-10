import request from '@/utils/request'

export function listFilterData(query) {
  return request({
    url: '/whitelist/filterData/list',
    method: 'get',
    params: query
  })
}

export function getFilterDashboardSummary() {
  return request({
    url: '/whitelist/filterData/dashboardSummary',
    method: 'get'
  })
}

export function getFilterData(id) {
  return request({
    url: '/whitelist/filterData/' + id,
    method: 'get'
  })
}

export function pullFilterData() {
  return request({
    url: '/whitelist/filterData/pullOnce',
    method: 'post',
    timeout: 30000
  })
}

export function publishFilterData(data) {
  return request({
    url: '/whitelist/filterData/publish',
    method: 'post',
    data
  })
}

export function delFilterData(id) {
  return request({
    url: '/whitelist/filterData/' + id,
    method: 'delete'
  })
}
