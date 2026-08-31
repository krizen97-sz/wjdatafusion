export const KNOWLEDGE_PERMISSIONS = {
  LIST: 'knowledge:page:list',
  WRITE: 'knowledge:page:write',
  SPACE_MANAGE: 'knowledge:space:manage',
  REMOVE: 'knowledge:page:remove',
  DOCUMENT: 'document:file:manage'
}

export const KNOWLEDGE_SCOPES = [
  { value: 'ACTIVE', label: '当前知识', icon: 'keyline-file-text' },
  { value: 'ARCHIVED', label: '已归档', icon: 'keyline-archive' },
  { value: 'TRASH', label: '回收站', icon: 'keyline-bin' }
]

export function buildKnowledgeTree(items = []) {
  const nodes = new Map(items.map((item) => [Number(item.pageId), { ...item, children: [] }]))
  const roots = []
  nodes.forEach((node) => {
    const parent = nodes.get(Number(node.parentId))
    if (parent && parent !== node) parent.children.push(node)
    else roots.push(node)
  })
  const sort = (list, ancestry = new Set()) => {
    list.sort((left, right) => Number(left.sortOrder || 0) - Number(right.sortOrder || 0)
      || (left.pageType === right.pageType ? 0 : left.pageType === 'FOLDER' ? -1 : 1)
      || String(left.title || '').localeCompare(String(right.title || ''), 'zh-CN'))
    list.forEach((item) => {
      if (ancestry.has(Number(item.pageId))) {
        item.children = []
        return
      }
      sort(item.children, new Set([...ancestry, Number(item.pageId)]))
    })
  }
  sort(roots)
  return roots
}

export function normalizeKnowledgeTags(values = []) {
  return [...new Set(values.flatMap((value) => String(value || '').split(/[，,]/))
    .map((value) => value.trim()).filter(Boolean))].slice(0, 8)
}

export function documentAccessMessage(document = {}) {
  const messages = {
    NO_MODULE_PERMISSION: '当前账号没有文档管理访问权限',
    NO_ACCESS: '文档不存在或当前账号无权访问',
    TRASH: '文档已进入回收站，暂时无法打开'
  }
  return messages[document.accessStatus] || ''
}

export function resolveKnowledgeDocumentAction(document = {}) {
  if (!['AVAILABLE', 'ARCHIVED'].includes(document.accessStatus)) return 'NONE'
  const fileType = String(document.fileType || '').toLowerCase()
  if (fileType === 'pdf') return 'PREVIEW'
  if (['zip', 'rar'].includes(fileType)) return 'DOWNLOAD'
  return 'EDITOR'
}

export function knowledgeFileMark(fileType) {
  const normalized = String(fileType || '').toLowerCase()
  if (normalized === 'pdf') return 'P'
  if (['xls', 'xlsx'].includes(normalized)) return 'X'
  if (['zip', 'rar'].includes(normalized)) return normalized.slice(0, 1).toUpperCase()
  return 'W'
}

export function versionChangeLabels(value) {
  const labels = {
    CREATE: '创建', TITLE: '标题', SUMMARY: '摘要', CONTENT: '正文', DIRECTORY: '目录',
    TAGS: '标签', DOCUMENTS: '关联文档', STATUS: '状态', SAVE: '保存'
  }
  return String(value || '').split(',').map((item) => labels[item] || item).filter(Boolean)
}

export function outlineFromHtml(html = '') {
  if (typeof document === 'undefined') return []
  const container = document.createElement('div')
  container.innerHTML = html
  return [...container.querySelectorAll('h1,h2,h3')].map((heading, index) => ({
    id: `knowledge-heading-${index}`,
    level: Number(heading.tagName.slice(1)),
    text: heading.textContent?.trim() || ''
  })).filter((item) => item.text)
}
