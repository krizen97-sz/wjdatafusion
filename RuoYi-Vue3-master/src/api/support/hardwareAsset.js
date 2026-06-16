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

export function addHardwareAsset(data) {
  return request({ url: '/support/hardwareAsset', method: 'post', data })
}

export function updateHardwareAsset(data) {
  return request({ url: '/support/hardwareAsset', method: 'put', data })
}

export function delHardwareAsset(assetId) {
  return request({ url: '/support/hardwareAsset/' + assetId, method: 'delete' })
}

export function bindHardwareAssetPlatform(params) {
  return request({ url: '/support/hardwareAsset/bindPlatform', method: 'post', params })
}

export function unbindHardwareAssetPlatform(params) {
  return request({ url: '/support/hardwareAsset/unbindPlatform', method: 'delete', params })
}
