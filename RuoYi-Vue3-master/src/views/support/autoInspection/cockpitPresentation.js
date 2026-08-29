export const RESULT_NORMAL = '1'
export const RESULT_ABNORMAL = '2'
export const RESULT_SKIP = '3'
export const RESULT_WARNING = '4'

export function normalizeHealthScore(value) {
  const score = Number(value)
  if (!Number.isFinite(score)) return 0
  return Math.max(0, Math.min(100, Math.round(score * 10) / 10))
}

export function formatHealthScore(value, status) {
  if (status === RESULT_SKIP || value === null || value === undefined || value === '') return '--'
  return `${normalizeHealthScore(value)}%`
}

export function healthStatusLabel(status) {
  if (status === RESULT_NORMAL) return '健康'
  if (status === RESULT_ABNORMAL) return '异常'
  if (status === RESULT_WARNING) return '需关注'
  return '未执行'
}

export function healthStatusType(status) {
  if (status === RESULT_NORMAL) return 'success'
  if (status === RESULT_ABNORMAL) return 'danger'
  if (status === RESULT_WARNING) return 'warning'
  return 'info'
}

export function healthStatusColor(status, palette) {
  if (status === RESULT_NORMAL) return palette.normal
  if (status === RESULT_ABNORMAL) return palette.danger
  if (status === RESULT_WARNING) return palette.warning
  return palette.idle
}

export function normalizeCockpitDashboard(data = {}) {
  return {
    healthOverview: data.healthOverview || {},
    routineSummary: data.summary || {},
    frequentSummary: data.frequentSummary || {},
    combinedTrend: Array.isArray(data.combinedTrend) ? [...data.combinedTrend] : [],
    currentPlanHealth: Array.isArray(data.currentPlanHealth) ? [...data.currentPlanHealth] : [],
    latestIssues: Array.isArray(data.latestIssues) ? [...data.latestIssues] : [],
    recentRecords: Array.isArray(data.recentRecords) ? [...data.recentRecords] : [],
    generatedTime: data.generatedTime || ''
  }
}

export function filterPlanHealth(rows = [], mode = 'ALL', keyword = '') {
  const normalizedKeyword = String(keyword || '').trim().toLowerCase()
  return rows.filter((row) => {
    if (mode !== 'ALL' && row.planMode !== mode) return false
    if (!normalizedKeyword) return true
    return [row.planName, row.templateName, row.labelName]
      .filter(Boolean)
      .some((value) => String(value).toLowerCase().includes(normalizedKeyword))
  })
}

export function buildCurrentStatusDistribution(rows = []) {
  const counts = {
    [RESULT_NORMAL]: 0,
    [RESULT_WARNING]: 0,
    [RESULT_ABNORMAL]: 0,
    [RESULT_SKIP]: 0
  }
  rows.forEach((row) => {
    const status = counts[row.resultStatus] === undefined ? RESULT_SKIP : row.resultStatus
    counts[status] += 1
  })
  return [
    { name: '健康', status: RESULT_NORMAL, value: counts[RESULT_NORMAL] },
    { name: '需关注', status: RESULT_WARNING, value: counts[RESULT_WARNING] },
    { name: '异常', status: RESULT_ABNORMAL, value: counts[RESULT_ABNORMAL] },
    { name: '未执行', status: RESULT_SKIP, value: counts[RESULT_SKIP] }
  ]
}

export function formatShortDate(value) {
  const text = String(value || '')
  if (!/^\d{4}-\d{2}-\d{2}$/.test(text)) return text || '-'
  return text.slice(5).replace('-', '/')
}
