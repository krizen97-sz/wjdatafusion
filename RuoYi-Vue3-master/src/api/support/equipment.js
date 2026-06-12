import request from '@/utils/request'

export function listEquipment(query) {
  return request({ url: '/support/equipment/list', method: 'get', params: query })
}

export function exportEquipment(data) {
  return request({ url: '/support/equipment/export', method: 'post', data, responseType: 'blob' })
}
