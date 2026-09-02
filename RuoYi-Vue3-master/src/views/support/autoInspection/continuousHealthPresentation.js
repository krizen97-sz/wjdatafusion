const STATUS_PRIORITY = { '2': 4, '4': 3, '1': 2, '3': 1 }

export function normalizeHealthDate(value) {
  if (!value) return ''
  const text = String(value)
  const matched = text.match(/^\d{4}-\d{2}-\d{2}/)
  return matched ? matched[0] : text
}

export function clampHealthScore(value) {
  const number = Number(value || 0)
  return Math.max(0, Math.min(100, Number.isFinite(number) ? number : 0))
}

export function healthStatusLabel(status, recovered = false) {
  if (status === '2') return recovered ? '异常已恢复' : '异常持续中'
  if (status === '4') return '需要关注'
  if (status === '1') return '正常'
  return '尚未执行'
}

export function healthStatusType(status) {
  if (status === '2') return 'danger'
  if (status === '4') return 'warning'
  if (status === '1') return 'success'
  return 'info'
}

function normalizePlanRow(source) {
  return {
    ...source,
    healthDate: normalizeHealthDate(source.healthDate),
    healthScore: clampHealthScore(source.healthScore),
    expectedCount: Number(source.expectedCount || 0),
    completedCount: Number(source.completedCount || 0),
    normalCount: Number(source.normalCount || 0),
    warningCount: Number(source.warningCount || 0),
    abnormalCount: Number(source.abnormalCount || 0),
    skippedCount: Number(source.skippedCount || 0),
    missingCount: Number(source.missingCount || 0)
  }
}

function aggregatePlanRows(plans = []) {
  const result = {
    plans,
    dayStatus: '3',
    expectedCount: 0,
    completedCount: 0,
    normalCount: 0,
    warningCount: 0,
    abnormalCount: 0,
    skippedCount: 0,
    missingCount: 0,
    healthScore: 0,
    abnormalSummary: '',
    recovered: false
  }
  plans.forEach((plan) => {
    ;['expectedCount', 'completedCount', 'normalCount', 'warningCount', 'abnormalCount', 'skippedCount', 'missingCount']
      .forEach((key) => { result[key] += Number(plan[key] || 0) })
    if ((STATUS_PRIORITY[plan.dayStatus] || 0) > (STATUS_PRIORITY[result.dayStatus] || 0)) result.dayStatus = plan.dayStatus
  })
  const denominator = Math.max(result.expectedCount, result.completedCount)
  result.healthScore = denominator > 0 ? Number(((result.normalCount / denominator) * 100).toFixed(2)) : 0
  result.recovered = result.dayStatus === '2' && plans.length > 0 && plans.every((plan) => plan.lastResultStatus !== '2')
  const summaryPlan = plans.find((plan) => plan.dayStatus === '2' && plan.lastResultStatus === '2' && plan.abnormalSummary)
    || plans.find((plan) => plan.dayStatus === '2' && plan.abnormalSummary)
    || plans.find((plan) => plan.dayStatus === '4' && plan.abnormalSummary)
    || plans.find((plan) => plan.abnormalSummary)
  result.abnormalSummary = summaryPlan?.abnormalSummary || ''
  return result
}

function compareHealthRows(left, right) {
  const statusDifference = (STATUS_PRIORITY[right.dayStatus] || 0) - (STATUS_PRIORITY[left.dayStatus] || 0)
  if (statusDifference) return statusDifference
  const ongoingDifference = Number(right.lastResultStatus === '2') - Number(left.lastResultStatus === '2')
  if (ongoingDifference) return ongoingDifference
  return String(left.name || left.planName || '').localeCompare(String(right.name || right.planName || ''), 'zh-CN')
}

export function groupDailyHealthRows(rows = []) {
  const groups = new Map()
  rows.forEach((source) => {
    const date = normalizeHealthDate(source.healthDate)
    if (!date) return
    const plan = normalizePlanRow(source)
    if (!groups.has(date)) {
      groups.set(date, {
        healthDate: date,
        plans: [],
        siteMap: new Map(),
        unassignedPlans: []
      })
    }
    const group = groups.get(date)
    group.plans.push(plan)
    const siteId = Number(plan.siteId || 0)
    if (!siteId) {
      group.unassignedPlans.push(plan)
      return
    }
    if (!group.siteMap.has(siteId)) {
      group.siteMap.set(siteId, {
        siteId,
        siteName: plan.siteName || `现场 ${siteId}`,
        name: plan.siteName || `现场 ${siteId}`,
        sitePlans: [],
        platformMap: new Map()
      })
    }
    const site = group.siteMap.get(siteId)
    const mainPlatformId = Number(plan.mainPlatformId || 0)
    if (plan.scopeType === 'MAIN_PLATFORM' && mainPlatformId) {
      if (!site.platformMap.has(mainPlatformId)) {
        site.platformMap.set(mainPlatformId, {
          mainPlatformId,
          mainPlatformName: plan.mainPlatformName || `主平台 ${mainPlatformId}`,
          name: plan.mainPlatformName || `主平台 ${mainPlatformId}`,
          plans: []
        })
      }
      site.platformMap.get(mainPlatformId).plans.push(plan)
    } else {
      site.sitePlans.push(plan)
    }
  })

  return Array.from(groups.values()).map((group) => {
    const sites = Array.from(group.siteMap.values()).map((site) => {
      const platforms = Array.from(site.platformMap.values()).map((platform) => ({
        ...platform,
        ...aggregatePlanRows(platform.plans.sort(compareHealthRows))
      })).sort(compareHealthRows)
      const allPlans = [...site.sitePlans, ...platforms.flatMap((platform) => platform.plans)]
      return {
        ...site,
        platforms,
        ...aggregatePlanRows(allPlans.sort(compareHealthRows))
      }
    }).sort(compareHealthRows)
    const assignedPlans = sites.flatMap((site) => site.plans)
    return {
      healthDate: group.healthDate,
      sites,
      unassignedPlans: group.unassignedPlans.sort(compareHealthRows),
      ...aggregatePlanRows(assignedPlans),
      planCount: group.plans.length,
      assignedPlanCount: assignedPlans.length
    }
  }).sort((a, b) => b.healthDate.localeCompare(a.healthDate))
}

export function summarizeDailyHealth(groups = []) {
  const active = groups.filter((item) => Number(item.completedCount || 0) > 0 || Number(item.expectedCount || 0) > 0)
  const siteKeys = new Set()
  const platformKeys = new Set()
  const abnormalSites = new Set()
  const warningSites = new Set()
  const unassignedPlans = new Set()
  groups.forEach((group) => {
    group.sites?.forEach((site) => {
      siteKeys.add(site.siteId)
      if (site.dayStatus === '2') abnormalSites.add(site.siteId)
      if (site.dayStatus === '4') warningSites.add(site.siteId)
      site.platforms?.forEach((platform) => platformKeys.add(`${site.siteId}:${platform.mainPlatformId}`))
    })
    group.unassignedPlans?.forEach((plan) => unassignedPlans.add(plan.planId || plan.planName))
  })
  return {
    dayCount: active.length,
    siteCount: siteKeys.size,
    platformCount: platformKeys.size,
    abnormalSiteCount: abnormalSites.size,
    warningSiteCount: warningSites.size,
    unassignedPlanCount: unassignedPlans.size
  }
}

export function paginateDailyHealthRows(rows = [], page = 1, pageSize = 20) {
  const source = Array.isArray(rows) ? rows : []
  const currentPage = Math.max(1, Math.trunc(Number(page) || 1))
  const currentPageSize = Math.max(1, Math.trunc(Number(pageSize) || 20))
  const start = (currentPage - 1) * currentPageSize
  return source.slice(start, start + currentPageSize)
}
