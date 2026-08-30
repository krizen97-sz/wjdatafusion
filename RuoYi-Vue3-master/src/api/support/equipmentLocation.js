import request from '@/utils/request'

export function getEquipmentLocationLayout(siteId) {
  return request({ url: `/support/equipmentLocation/layout/${siteId}`, method: 'get' })
}

export function getEquipmentTopology(siteId) {
  return request({
    url: `/support/equipmentLocation/topology/${siteId}`,
    method: 'get'
  })
}

export function addEquipmentRoom(data) {
  return request({ url: '/support/equipmentLocation/room', method: 'post', data })
}

export function updateEquipmentRoom(data) {
  return request({ url: '/support/equipmentLocation/room', method: 'put', data })
}

export function delEquipmentRoom(roomId) {
  return request({ url: `/support/equipmentLocation/room/${roomId}`, method: 'delete' })
}

export function addEquipmentCabinet(data) {
  return request({ url: '/support/equipmentLocation/cabinet', method: 'post', data })
}

export function updateEquipmentCabinet(data) {
  return request({ url: '/support/equipmentLocation/cabinet', method: 'put', data })
}

export function updateEquipmentCabinetLayout(data) {
  return request({
    url: '/support/equipmentLocation/cabinet/layout',
    method: 'put',
    data
  })
}

export function delEquipmentCabinet(cabinetId) {
  return request({ url: `/support/equipmentLocation/cabinet/${cabinetId}`, method: 'delete' })
}

export function addEquipmentLink(data) {
  return request({
    url: '/support/equipmentLocation/link',
    method: 'post',
    data
  })
}

export function updateEquipmentLink(data) {
  return request({
    url: '/support/equipmentLocation/link',
    method: 'put',
    data
  })
}

export function delEquipmentLink(linkId) {
  return request({
    url: `/support/equipmentLocation/link/${linkId}`,
    method: 'delete'
  })
}
