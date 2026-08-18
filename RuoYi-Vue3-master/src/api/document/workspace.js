import request from '@/utils/request'

export function listDocumentFolders() {
  return request({ url: '/document/workspace/folders', method: 'get' })
}

export function getDocumentWorkspaceSummary() {
  return request({ url: '/document/workspace/summary', method: 'get' })
}

export function listDocumentStorageUsers() {
  return request({ url: '/document/workspace/admin/storage-users', method: 'get' })
}

export function updateDocumentStoragePolicy(userId, data) {
  return request({ url: `/document/workspace/admin/storage-users/${userId}`, method: 'put', data })
}

export function createDocumentFolder(data) {
  return request({ url: '/document/workspace/folders', method: 'post', data })
}

export function updateDocumentFolder(folderId, data) {
  return request({ url: `/document/workspace/folders/${folderId}`, method: 'put', data })
}

export function reorderDocumentFolders(data) {
  return request({ url: '/document/workspace/folders/reorder', method: 'put', data })
}

export function deleteDocumentFolder(folderId) {
  return request({ url: `/document/workspace/folders/${folderId}`, method: 'delete' })
}

export function listDocuments(params) {
  return request({ url: '/document/workspace/documents', method: 'get', params })
}

export function getDocument(documentId) {
  return request({ url: `/document/workspace/documents/${documentId}`, method: 'get' })
}

export function getDocumentPreview(documentId, onDownloadProgress) {
  return request({
    url: `/document/workspace/documents/${documentId}/preview`,
    method: 'get',
    responseType: 'blob',
    timeout: 0,
    onDownloadProgress
  })
}

export function createDocument(data) {
  return request({ url: '/document/workspace/documents', method: 'post', data })
}

export function copyDocument(documentId) {
  return request({ url: `/document/workspace/documents/${documentId}/copy`, method: 'post' })
}

export function uploadDocument(file, folderId) {
  const data = new FormData()
  data.append('file', file)
  data.append('folderId', Number(folderId))
  return request({
    url: '/document/workspace/documents/upload',
    method: 'post',
    data,
    headers: { 'Content-Type': 'multipart/form-data', repeatSubmit: false },
    timeout: 120000
  })
}

export function updateDocument(documentId, data) {
  return request({ url: `/document/workspace/documents/${documentId}`, method: 'put', data })
}

export function trashDocument(documentId) {
  return request({ url: `/document/workspace/documents/${documentId}`, method: 'delete' })
}

export function listDocumentCollaborators(documentId) {
  return request({ url: `/document/workspace/documents/${documentId}/collaborators`, method: 'get' })
}

export function saveDocumentCollaborators(documentId, entries) {
  return request({
    url: `/document/workspace/documents/${documentId}/collaborators`,
    method: 'put',
    data: { entries }
  })
}

export function listCollaboratorCandidates(documentId, keyword) {
  return request({
    url: `/document/workspace/documents/${documentId}/collaborator-candidates`,
    method: 'get',
    params: { keyword }
  })
}

export function listDocumentVersions(documentId) {
  return request({ url: `/document/workspace/documents/${documentId}/versions`, method: 'get' })
}

export function listDocumentOperations(documentId) {
  return request({ url: `/document/workspace/documents/${documentId}/operations`, method: 'get' })
}

export function getDocumentEditorConfig(documentId) {
  return request({ url: `/document/workspace/documents/${documentId}/editor-config`, method: 'get' })
}

export function forceSaveDocument(documentId) {
  return request({ url: `/document/workspace/documents/${documentId}/force-save`, method: 'post' })
}
