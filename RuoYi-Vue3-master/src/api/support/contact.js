import request from '@/utils/request'

export function listContact(query) {
  return request({ url: '/support/contact/list', method: 'get', params: query })
}

export function getContact(contactId) {
  return request({ url: '/support/contact/' + contactId, method: 'get' })
}

export function addContact(data) {
  return request({ url: '/support/contact', method: 'post', data })
}

export function updateContact(data) {
  return request({ url: '/support/contact', method: 'put', data })
}

export function delContact(contactId) {
  return request({ url: '/support/contact/' + contactId, method: 'delete' })
}
