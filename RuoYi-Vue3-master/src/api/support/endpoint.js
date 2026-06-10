import request from '@/utils/request'

export function listEndpoint(query) {
  return request({ url: '/support/endpoint/list', method: 'get', params: query })
}

export function getEndpoint(endpointId) {
  return request({ url: '/support/endpoint/' + endpointId, method: 'get' })
}

export function addEndpoint(data) {
  return request({ url: '/support/endpoint', method: 'post', data })
}

export function updateEndpoint(data) {
  return request({ url: '/support/endpoint', method: 'put', data })
}

export function delEndpoint(endpointId) {
  return request({ url: '/support/endpoint/' + endpointId, method: 'delete' })
}

export function viewEndpointPlain(endpointId) {
  return request({ url: '/support/endpoint/plain/' + endpointId, method: 'get' })
}
