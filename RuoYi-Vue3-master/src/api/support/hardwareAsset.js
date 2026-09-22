import request from '@/utils/request'

export function listHardwareAsset(query) {
  return request({ url: '/support/hardwareAsset/list', method: 'get', params: query })
}

export function getHardwareAsset(assetId) {
  return request({ url: '/support/hardwareAsset/' + assetId, method: 'get' })
}

export function viewHardwareAssetPlain(assetId) {
  return request({ url: '/support/hardwareAsset/plain/' + assetId, method: 'get' })
}

export function updateHardwareAsset(data) {
  return request({ url: '/support/hardwareAsset', method: 'put', data })
}
