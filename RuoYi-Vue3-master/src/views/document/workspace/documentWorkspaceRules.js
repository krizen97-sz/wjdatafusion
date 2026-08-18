export const FILE_MANAGEMENT_PERMISSION = 'document:file:manage'

export const DEFAULT_FOLDER_COLOR = '#4F7CCF'

export const FOLDER_COLOR_OPTIONS = [
  { value: '#4F7CCF', label: '海蓝' },
  { value: '#2F8F6B', label: '松绿' },
  { value: '#A06A2B', label: '琥珀' },
  { value: '#8A63B8', label: '藤紫' },
  { value: '#C45D6A', label: '绯红' },
  { value: '#5C718A', label: '灰蓝' }
]

export const DOCUMENT_SCOPES = [
  { value: 'MY', label: '我的文档', icon: 'Document' },
  { value: 'SHARED', label: '与我共享', icon: 'User' },
  { value: 'RECENT', label: '最近', icon: 'Clock' },
  { value: 'ARCHIVED', label: '已归档', icon: 'Box' },
  { value: 'TRASH', label: '回收站', icon: 'Delete' }
]

export const PERMISSION_META = {
  OWNER: { label: '所有者', type: 'primary' },
  EDIT: { label: '可编辑', type: 'primary' },
  VIEW: { label: '仅查看', type: 'info' },
  ADMIN: { label: '管理员查看', type: 'warning' }
}

export const EXPIRY_OPTIONS = [
  { value: 'PERMANENT', label: '永久有效' },
  { value: '7D', label: '7 天' },
  { value: '30D', label: '30 天' },
  { value: 'CUSTOM', label: '自定义' }
]

export function buildFolderTree(folders = []) {
  const nodes = new Map(folders.map((folder) => [Number(folder.folderId), { ...folder, children: [] }]))
  const roots = []
  nodes.forEach((node) => {
    const parent = nodes.get(Number(node.parentId))
    if (parent) parent.children.push(node)
    else roots.push(node)
  })
  const sort = (items) => {
    items.sort((left, right) => Number(left.sortOrder || 0) - Number(right.sortOrder || 0)
      || String(left.folderName || '').localeCompare(String(right.folderName || ''), 'zh-CN'))
    items.forEach((item) => sort(item.children))
  }
  sort(roots)
  return roots
}

export function normalizeFolderColor(value) {
  const normalized = String(value || '').trim().toUpperCase()
  return FOLDER_COLOR_OPTIONS.some((option) => option.value === normalized) ? normalized : DEFAULT_FOLDER_COLOR
}

export function reorderSiblingFolderIds(folders = [], draggedFolderId, targetFolderId, position) {
  const dragged = folders.find((folder) => Number(folder.folderId) === Number(draggedFolderId))
  const target = folders.find((folder) => Number(folder.folderId) === Number(targetFolderId))
  if (!dragged || !target || Number(dragged.folderId) === Number(target.folderId)
    || Number(dragged.parentId || 0) !== Number(target.parentId || 0)
    || !['BEFORE', 'AFTER'].includes(position)) return []

  const siblings = folders
    .filter((folder) => Number(folder.parentId || 0) === Number(dragged.parentId || 0))
    .sort((left, right) => Number(left.sortOrder || 0) - Number(right.sortOrder || 0)
      || String(left.folderName || '').localeCompare(String(right.folderName || ''), 'zh-CN'))
  const orderedIds = siblings.map((folder) => Number(folder.folderId))
  orderedIds.splice(orderedIds.indexOf(Number(dragged.folderId)), 1)
  const targetIndex = orderedIds.indexOf(Number(target.folderId))
  orderedIds.splice(position === 'AFTER' ? targetIndex + 1 : targetIndex, 0, Number(dragged.folderId))
  return orderedIds
}

export function buildFolderBreadcrumb(folders = [], folderId) {
  if (!folderId) return []
  const nodes = new Map(folders.map((folder) => [Number(folder.folderId), folder]))
  const result = []
  const visited = new Set()
  let cursor = nodes.get(Number(folderId))
  while (cursor && !visited.has(Number(cursor.folderId)) && result.length < 32) {
    visited.add(Number(cursor.folderId))
    result.unshift(cursor)
    cursor = nodes.get(Number(cursor.parentId))
  }
  return result
}

export function resolveDocumentFolderPath(folders = [], document = {}) {
  const breadcrumb = buildFolderBreadcrumb(folders, document.folderId)
  if (breadcrumb.length) return breadcrumb.map((item) => item.folderName).filter(Boolean).join(' / ')
  return String(document.folderName || '').trim() || '根目录'
}

export function formatFileSize(value) {
  const size = Number(value || 0)
  if (size < 1024) return `${size} B`
  if (size < 1024 ** 2) return `${(size / 1024).toFixed(size < 10 * 1024 ? 1 : 0)} KB`
  return `${(size / 1024 ** 2).toFixed(size < 10 * 1024 ** 2 ? 1 : 0)} MB`
}

export function formatStorageMegabytes(value) {
  const megabytes = Math.max(0, Number(value || 0)) / 1024 ** 2
  if (!megabytes) return '0 MB'
  if (megabytes < 10) return `${megabytes.toFixed(2)} MB`
  if (megabytes < 100) return `${megabytes.toFixed(1)} MB`
  return `${Math.round(megabytes)} MB`
}

export function initials(value) {
  const text = String(value || '').trim()
  if (!text) return '用'
  return [...text].slice(-2).join('').toUpperCase()
}

export function collaboratorNames(value) {
  return String(value || '').split('、').map((item) => item.trim()).filter(Boolean)
}

export function isSpreadsheetFile(fileType) {
  return ['xls', 'xlsx'].includes(String(fileType || '').toLowerCase())
}

export function isArchiveFile(fileType) {
  return ['zip', 'rar'].includes(String(fileType || '').toLowerCase())
}

export function isPdfFile(fileType) {
  return String(fileType || '').toLowerCase() === 'pdf'
}

export function documentDropCapability(document = {}, target = {}) {
  if (!document?.documentId) return { allowed: false, reason: 'NO_DOCUMENT', action: '' }
  if (document.accessPermission !== 'OWNER') return { allowed: false, reason: 'NO_PERMISSION', action: '' }

  if (target.kind === 'FOLDER') {
    const folderId = Number(target.id || 0)
    if (!folderId) return { allowed: false, reason: 'ROOT_BLOCKED', action: '' }
    if (Number(document.folderId || 0) === folderId && document.lifecycleStatus === 'ACTIVE') {
      return { allowed: false, reason: 'SAME_FOLDER', action: '' }
    }
    return { allowed: true, reason: '', action: 'MOVE' }
  }

  if (target.kind === 'SCOPE' && target.id === 'ARCHIVED') {
    if (document.lifecycleStatus === 'ARCHIVED') return { allowed: false, reason: 'ALREADY_ARCHIVED', action: '' }
    return { allowed: true, reason: '', action: 'ARCHIVE' }
  }

  if (target.kind === 'SCOPE' && target.id === 'MY') {
    if (document.lifecycleStatus === 'ACTIVE') return { allowed: false, reason: 'ALREADY_ACTIVE', action: '' }
    return { allowed: true, reason: '', action: 'RESTORE' }
  }

  return { allowed: false, reason: 'UNSUPPORTED_SCOPE', action: '' }
}

export function formatLocalDateTime(value) {
  const date = value instanceof Date ? value : new Date(value)
  if (Number.isNaN(date.getTime())) return ''
  const pad = (part) => String(part).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}

export function expirationForMode(mode, now = new Date()) {
  if (mode === 'PERMANENT') return null
  const days = mode === '7D' ? 7 : mode === '30D' ? 30 : 0
  if (!days) return null
  return formatLocalDateTime(new Date(now.getTime() + days * 24 * 60 * 60 * 1000))
}

export function expirationState(expiresAt, now = new Date()) {
  if (!expiresAt) return { expired: false, label: '永久有效', type: 'info' }
  const parsed = new Date(String(expiresAt).replace(' ', 'T'))
  if (Number.isNaN(parsed.getTime())) return { expired: true, label: '时间格式无效', type: 'danger' }
  if (parsed.getTime() <= now.getTime()) return { expired: true, label: '已到期', type: 'danger' }
  return { expired: false, label: `有效至 ${formatLocalDateTime(parsed).slice(0, 16)}`, type: 'success' }
}
