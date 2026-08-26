import request from '@/utils/request'

export function listKnowledgeSpaces() {
  return request({ url: '/knowledge/spaces', method: 'get' })
}

export function createKnowledgeSpace(data) {
  return request({ url: '/knowledge/spaces', method: 'post', data })
}

export function updateKnowledgeSpace(spaceId, data) {
  return request({ url: `/knowledge/spaces/${spaceId}`, method: 'put', data })
}

export function listKnowledgeTree(params) {
  return request({ url: '/knowledge/pages/tree', method: 'get', params })
}

export function searchKnowledgePages(params) {
  return request({ url: '/knowledge/pages/search', method: 'get', params })
}

export function createKnowledgeFolder(data) {
  return request({ url: '/knowledge/folders', method: 'post', data })
}

export function updateKnowledgeFolder(folderId, data) {
  return request({ url: `/knowledge/folders/${folderId}`, method: 'put', data })
}

export function removeKnowledgeFolder(folderId) {
  return request({ url: `/knowledge/folders/${folderId}`, method: 'delete' })
}

export function getKnowledgePage(pageId) {
  return request({ url: `/knowledge/pages/${pageId}`, method: 'get' })
}

export function createKnowledgePage(data) {
  return request({ url: '/knowledge/pages', method: 'post', data })
}

export function updateKnowledgePage(pageId, data) {
  return request({ url: `/knowledge/pages/${pageId}`, method: 'put', data })
}

export function archiveKnowledgePage(pageId, data) {
  return request({ url: `/knowledge/pages/${pageId}/archive`, method: 'put', data })
}

export function trashKnowledgePage(pageId, data) {
  return request({ url: `/knowledge/pages/${pageId}/trash`, method: 'put', data })
}

export function restoreKnowledgePage(pageId, data) {
  return request({ url: `/knowledge/pages/${pageId}/restore`, method: 'put', data })
}

export function listKnowledgeVersions(pageId) {
  return request({ url: `/knowledge/pages/${pageId}/versions`, method: 'get' })
}

export function getKnowledgeVersion(pageId, versionNo) {
  return request({ url: `/knowledge/pages/${pageId}/versions/${versionNo}`, method: 'get' })
}

export function restoreKnowledgeVersion(pageId, versionNo, data) {
  return request({ url: `/knowledge/pages/${pageId}/versions/${versionNo}/restore`, method: 'post', data })
}

export function listKnowledgeDocumentCandidates(params) {
  return request({ url: '/knowledge/document-candidates', method: 'get', params })
}
