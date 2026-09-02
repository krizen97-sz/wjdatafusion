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

const STATUS_PRIORITY = {
  [RESULT_ABNORMAL]: 4,
  [RESULT_WARNING]: 3,
  [RESULT_NORMAL]: 2,
  [RESULT_SKIP]: 1
}

function aggregateScopePlans(plans = []) {
  const expectedCount = plans.reduce((sum, row) => sum + Number(row.expectedCount || 0), 0)
  const completedCount = plans.reduce((sum, row) => sum + Number(row.completedCount || 0), 0)
  const normalCount = plans.reduce((sum, row) => sum + Number(row.normalCount || 0), 0)
  const abnormalCount = plans.reduce((sum, row) => sum + Number(row.abnormalCount || 0), 0)
  const warningCount = plans.reduce((sum, row) => sum + Number(row.warningCount || 0), 0)
  const missingCount = plans.reduce((sum, row) => sum + Number(row.missingCount || 0), 0)
  const resultStatus = plans.reduce((status, row) => (
    (STATUS_PRIORITY[row.resultStatus] || 0) > (STATUS_PRIORITY[status] || 0) ? row.resultStatus : status
  ), RESULT_SKIP)
  return {
    plans,
    planCount: plans.length,
    expectedCount,
    completedCount,
    normalCount,
    abnormalCount,
    warningCount,
    missingCount,
    resultStatus,
    healthScore: expectedCount > 0 ? normalizeHealthScore((normalCount / expectedCount) * 100) : 0,
    issueSummary: plans.find((row) => row.resultStatus === RESULT_ABNORMAL)?.issueSummary
      || plans.find((row) => row.resultStatus === RESULT_WARNING)?.issueSummary
      || '当前未记录异常'
  }
}

export function groupPlanHealthByScope(rows = []) {
  const sites = new Map()
  const unassigned = []
  rows.forEach((row) => {
    const siteId = Number(row.siteId || 0)
    if (!siteId) {
      unassigned.push(row)
      return
    }
    if (!sites.has(siteId)) {
      sites.set(siteId, {
        scopeKey: `SITE:${siteId}`,
        scopeType: 'SITE',
        siteId,
        siteName: row.siteName || `现场 ${siteId}`,
        scopeName: row.siteName || `现场 ${siteId}`,
        sitePlans: [],
        platformMap: new Map()
      })
    }
    const site = sites.get(siteId)
    const mainPlatformId = Number(row.mainPlatformId || 0)
    if (row.scopeType === 'MAIN_PLATFORM' && mainPlatformId) {
      if (!site.platformMap.has(mainPlatformId)) {
        site.platformMap.set(mainPlatformId, {
          scopeKey: `MAIN_PLATFORM:${mainPlatformId}`,
          scopeType: 'MAIN_PLATFORM',
          siteId,
          siteName: site.siteName,
          mainPlatformId,
          mainPlatformName: row.mainPlatformName || `主平台 ${mainPlatformId}`,
          scopeName: row.mainPlatformName || `主平台 ${mainPlatformId}`,
          plans: []
        })
      }
      site.platformMap.get(mainPlatformId).plans.push(row)
    } else {
      site.sitePlans.push(row)
    }
  })

  const result = Array.from(sites.values()).map((site) => {
    const children = Array.from(site.platformMap.values()).map((platform) => ({
      ...platform,
      ...aggregateScopePlans(platform.plans)
    })).sort((left, right) => (STATUS_PRIORITY[right.resultStatus] || 0) - (STATUS_PRIORITY[left.resultStatus] || 0))
    const plans = [...site.sitePlans, ...children.flatMap((platform) => platform.plans)]
    return {
      ...site,
      children,
      ...aggregateScopePlans(plans)
    }
  }).sort((left, right) => {
    const statusDifference = (STATUS_PRIORITY[right.resultStatus] || 0) - (STATUS_PRIORITY[left.resultStatus] || 0)
    return statusDifference || left.scopeName.localeCompare(right.scopeName, 'zh-CN')
  })
  return { sites: result, unassigned }
}

export function filterScopeHealth(rows = [], keyword = '') {
  const normalizedKeyword = String(keyword || '').trim().toLowerCase()
  if (!normalizedKeyword) return rows
  return rows.reduce((result, site) => {
    const siteMatched = [site.scopeName, ...site.plans.map((plan) => plan.planName), ...site.plans.map((plan) => plan.templateName)]
      .filter(Boolean)
      .some((value) => String(value).toLowerCase().includes(normalizedKeyword))
    const children = site.children.filter((platform) => [platform.scopeName, ...platform.plans.map((plan) => plan.planName), ...platform.plans.map((plan) => plan.templateName)]
      .filter(Boolean)
      .some((value) => String(value).toLowerCase().includes(normalizedKeyword)))
    if (siteMatched || children.length) result.push({ ...site, children: siteMatched ? site.children : children })
    return result
  }, [])
}

export function summarizeScopeHealth(sites = [], unassigned = []) {
  return {
    siteCount: sites.length,
    platformCount: sites.reduce((sum, site) => sum + site.children.length, 0),
    abnormalSiteCount: sites.filter((site) => site.resultStatus === RESULT_ABNORMAL).length,
    warningSiteCount: sites.filter((site) => site.resultStatus === RESULT_WARNING).length,
    unassignedPlanCount: unassigned.length
  }
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

export function buildScopeHealthChartRows(sites = [], limit = 10) {
  const rows = []
  sites.forEach((site) => {
    rows.push({
      ...site,
      chartName: site.scopeName,
      scopePath: site.scopeName
    })
    site.children?.forEach((platform) => {
      rows.push({
        ...platform,
        chartName: `${site.scopeName} / ${platform.scopeName}`,
        scopePath: `${site.scopeName} / ${platform.scopeName}`
      })
    })
  })
  return rows
    .sort((left, right) => {
      const statusDifference = (STATUS_PRIORITY[right.resultStatus] || 0) - (STATUS_PRIORITY[left.resultStatus] || 0)
      if (statusDifference) return statusDifference
      const scoreDifference = normalizeHealthScore(left.healthScore) - normalizeHealthScore(right.healthScore)
      return scoreDifference || left.chartName.localeCompare(right.chartName, 'zh-CN')
    })
    .slice(0, Math.max(1, Number(limit) || 10))
}

export function buildPlanCompletionRows(rows = [], limit = 8) {
  return rows.map((row) => {
    const expectedCount = Number(row.expectedCount || 0)
    const completedCount = Number(row.completedCount || 0)
    return {
      ...row,
      chartName: row.planName || '未命名计划',
      expectedCount,
      completedCount,
      pendingCount: Math.max(expectedCount - completedCount, 0),
      completionRate: expectedCount > 0 ? normalizeHealthScore((completedCount / expectedCount) * 100) : 0
    }
  }).sort((left, right) => {
    const statusDifference = (STATUS_PRIORITY[right.resultStatus] || 0) - (STATUS_PRIORITY[left.resultStatus] || 0)
    if (statusDifference) return statusDifference
    const rateDifference = left.completionRate - right.completionRate
    return rateDifference || left.chartName.localeCompare(right.chartName, 'zh-CN')
  }).slice(0, Math.max(1, Number(limit) || 8))
}

export function formatShortDate(value) {
  const text = String(value || '')
  if (!/^\d{4}-\d{2}-\d{2}$/.test(text)) return text || '-'
  return text.slice(5).replace('-', '/')
}
