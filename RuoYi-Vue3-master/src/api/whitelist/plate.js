import request from '@/utils/request'

export function listPlate(query) {
  return request({
    url: '/whitelist/plate/list',
    method: 'get',
    params: query
  })
}

export function getPlate(vehiclePlate) {
  return request({
    url: '/whitelist/plate/' + encodeURIComponent(vehiclePlate),
    method: 'get'
  })
}

export function addPlate(data) {
  return request({
    url: '/whitelist/plate',
    method: 'post',
    data
  })
}

export function updatePlate(data) {
  return request({
    url: '/whitelist/plate',
    method: 'put',
    data
  })
}

export function changePlateStatus(data) {
  return request({
    url: '/whitelist/plate/changeStatus',
    method: 'put',
    data
  })
}

export function delPlate(vehiclePlate) {
  return request({
    url: '/whitelist/plate/' + vehiclePlate.split(',').map((item) => encodeURIComponent(item)).join(','),
    method: 'delete'
  })
}
