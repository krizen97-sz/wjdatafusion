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
  return '暂无数据'
}

export function healthStatusType(status) {
  if (status === '2') return 'danger'
  if (status === '4') return 'warning'
  if (status === '1') return 'success'
  return 'info'
}

export function groupDailyHealthRows(rows = []) {
  const groups = new Map()
  rows.forEach((source) => {
    const date = normalizeHealthDate(source.healthDate)
    if (!date) return
    const plan = {
      ...source,
      healthDate: date,
      healthScore: clampHealthScore(source.healthScore),
      expectedCount: Number(source.expectedCount || 0),
      completedCount: Number(source.completedCount || 0),
      normalCount: Number(source.normalCount || 0),
      warningCount: Number(source.warningCount || 0),
      abnormalCount: Number(source.abnormalCount || 0),
      skippedCount: Number(source.skippedCount || 0),
      missingCount: Number(source.missingCount || 0)
    }
    if (!groups.has(date)) {
      groups.set(date, {
        healthDate: date,
        dayStatus: '3',
        plans: [],
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
      })
    }
    const group = groups.get(date)
    group.plans.push(plan)
    ;['expectedCount', 'completedCount', 'normalCount', 'warningCount', 'abnormalCount', 'skippedCount', 'missingCount']
      .forEach((key) => { group[key] += plan[key] })
    if ((STATUS_PRIORITY[plan.dayStatus] || 0) > (STATUS_PRIORITY[group.dayStatus] || 0)) group.dayStatus = plan.dayStatus
    if (!group.abnormalSummary && plan.abnormalSummary) group.abnormalSummary = plan.abnormalSummary
  })

  return Array.from(groups.values()).map((group) => {
    const denominator = Math.max(group.expectedCount, group.completedCount)
    group.healthScore = denominator > 0 ? Number(((group.normalCount / denominator) * 100).toFixed(2)) : 0
    group.recovered = group.dayStatus === '2' && group.plans.every((plan) => plan.lastResultStatus !== '2')
    group.plans.sort((a, b) => (STATUS_PRIORITY[b.dayStatus] || 0) - (STATUS_PRIORITY[a.dayStatus] || 0) || String(a.planName || '').localeCompare(String(b.planName || ''), 'zh-CN'))
    return group
  }).sort((a, b) => b.healthDate.localeCompare(a.healthDate))
}

export function summarizeDailyHealth(groups = []) {
  const active = groups.filter((item) => Number(item.completedCount || 0) > 0 || Number(item.expectedCount || 0) > 0)
  const expected = active.reduce((sum, item) => sum + Number(item.expectedCount || 0), 0)
  const normal = active.reduce((sum, item) => sum + Number(item.normalCount || 0), 0)
  return {
    dayCount: active.length,
    healthScore: expected > 0 ? Number(((normal / expected) * 100).toFixed(1)) : 0,
    abnormalDays: active.filter((item) => item.dayStatus === '2').length,
    warningDays: active.filter((item) => item.dayStatus === '4').length
  }
}
