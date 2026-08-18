export const RECORD_FILTERS = [
  { value: 'ALL', label: '全部' },
  { value: 'PERMISSION', label: '权限' },
  { value: 'LIFECYCLE', label: '归档' },
  { value: 'ACCESS', label: '访问' }
]

const OPERATION_LABELS = {
  OPEN: '打开文档',
  RENAME: '重命名',
  MOVE: '移动目录',
  ARCHIVED: '归档文档',
  ACTIVE: '恢复文档',
  TRASH: '移入回收站',
  ACL_GRANT: '新增授权',
  ACL_CHANGE: '调整权限',
  ACL_REVOKE: '移除授权',
  ACL_EXPIRE: '权限到期'
}

const HIDDEN_OPERATION_ACTIONS = new Set([
  'CREATE', 'FORCE_SAVE', 'FINAL_SAVE', 'VERSION_DOWNLOAD', 'VERSION_RESTORE', 'DOWNLOAD'
])

export function operationLabel(action) {
  return OPERATION_LABELS[action] || action || '文档操作'
}

export function permissionSnapshotLabel(value) {
  if (!value) return ''
  const [permission, expiry] = String(value).split('|')
  const label = ({ EDIT: '可编辑', VIEW: '仅查看' })[permission] || permission
  if (!expiry) return label
  return expiry === 'PERMANENT' ? `${label}（永久）` : `${label}（至 ${expiry.slice(0, 16)}）`
}

export function operationSummary(operation = {}) {
  const actor = operation.operatorName || '系统'
  const target = operation.targetUserName || '用户'
  if (operation.actionType === 'ACL_GRANT') {
    return `${actor} 授予 ${target} ${permissionSnapshotLabel(operation.currentValue)}权限`
  }
  if (operation.actionType === 'ACL_CHANGE') {
    return `${actor} 将 ${target} 从${permissionSnapshotLabel(operation.previousValue)}调整为${permissionSnapshotLabel(operation.currentValue)}`
  }
  if (operation.actionType === 'ACL_REVOKE' || operation.actionType === 'ACL_EXPIRE') {
    const verb = operation.actionType === 'ACL_EXPIRE' ? '自动移除了' : '移除了'
    return `${actor} ${verb} ${target} 的${permissionSnapshotLabel(operation.previousValue)}权限`
  }
  return `${actor} · ${operation.detailContent || '已完成操作'}`
}

export function operationCategory(action) {
  if (String(action || '').startsWith('ACL_')) return 'PERMISSION'
  if (['RENAME', 'MOVE', 'ARCHIVED', 'ACTIVE', 'TRASH'].includes(action)) return 'LIFECYCLE'
  return 'ACCESS'
}

export function filterOperations(operations = [], category = 'ALL') {
  const visible = operations.filter((item) => !HIDDEN_OPERATION_ACTIONS.has(item.actionType))
  return category === 'ALL' ? visible : visible.filter((item) => operationCategory(item.actionType) === category)
}
