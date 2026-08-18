import request from '@/utils/request'

export function getEquipmentLocationLayout(siteId) {
  return request({ url: `/support/equipmentLocation/layout/${siteId}`, method: 'get' })
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

export function delEquipmentCabinet(cabinetId) {
  return request({ url: `/support/equipmentLocation/cabinet/${cabinetId}`, method: 'delete' })
}
