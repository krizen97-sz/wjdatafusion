import request from '@/utils/request'

export function listNetwork(query) {
  return request({ url: '/ipam/network/list', method: 'get', params: query })
}

export function listNetworkTree(query) {
  return request({ url: '/ipam/network/tree', method: 'get', params: query })
}

export function getIpamDashboard(query) {
  return request({ url: '/ipam/dashboard', method: 'get', params: query })
}

export function listCommunityAddressDetail(query) {
  return request({ url: '/ipam/dashboard/community/detail', method: 'get', params: query })
}

export function getScenarioSetting() {
  return request({ url: '/ipam/settings/scenario', method: 'get' })
}

export function updateScenarioSetting(data) {
  return request({ url: '/ipam/settings/scenario', method: 'put', data })
}

export function addNetwork(data) {
  return request({ url: '/ipam/network', method: 'post', data })
}

export function updateNetwork(data) {
  return request({ url: '/ipam/network', method: 'put', data })
}

export function delNetwork(networkIds) {
  return request({ url: '/ipam/network/' + networkIds, method: 'delete' })
}

export function getNetworkOverview(query) {
  return request({ url: '/ipam/network/overview', method: 'get', params: query })
}

export function getAddressGrid(networkId, pageNum = 1, pageSize = 256) {
  return request({ url: '/ipam/address/grid', method: 'get', params: { networkId, pageNum, pageSize } })
}

export function startNetworkScan(networkId) {
  return request({ url: `/ipam/scan/network/${networkId}`, method: 'post' })
}

export function getScanJob(scanId) {
  return request({ url: `/ipam/scan/job/${scanId}`, method: 'get' })
}

export function getLatestNetworkScan(networkId) {
  return request({ url: `/ipam/scan/network/${networkId}/latest`, method: 'get' })
}

export function listAddress(query) {
  return request({ url: '/ipam/address/list', method: 'get', params: query })
}

export function getAddressCredential(addressId) {
  return request({ url: `/ipam/address/${addressId}/credential`, method: 'get' })
}

export function commitConfigSheet(data) {
  return request({
    url: '/ipam/config/commit',
    method: 'post',
    headers: { repeatSubmit: false },
    data
  })
}
