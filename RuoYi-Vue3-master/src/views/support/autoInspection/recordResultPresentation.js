export function resultLabel(value) {
  return ({ 1: '正常', 2: '异常', 4: '关注' })[String(value)] || '未执行'
}

export function resultTone(value) {
  return ({ 1: 'success', 2: 'danger', 4: 'warning' })[String(value)] || 'info'
}

function stepKey(row, fallback) {
  if (row.stepResultId != null) return `result-${row.stepResultId}`
  if (row.stepId != null) return `step-${row.stepId}`
  return row.stepName ? `name-${row.stepName}` : fallback
}

export function getRecordResultGroups(record = {}) {
  const steps = Array.isArray(record.steps) ? record.steps : []
  const targets = Array.isArray(record.targetResults) ? record.targetResults : []
  const groups = steps.slice().sort((a, b) => Number(a.sortOrder || 0) - Number(b.sortOrder || 0))
    .map((step, index) => ({ ...step, key: stepKey(step, `step-index-${index}`), targets: [] }))
  const byKey = new Map(groups.map(group => [group.key, group]))
  targets.forEach((target, index) => {
    const key = stepKey(target, `unassigned-${index}`)
    let group = byKey.get(key)
    if (!group) {
      group = { ...target, key, stepName: target.stepName || '未归属步骤', targets: [] }
      groups.push(group)
      byKey.set(key, group)
    }
    group.targets.push({ ...target, toolCode: target.toolCode || group.toolCode })
  })
  // Keep skipped/failed steps even when execution produced no target results.
  return groups.map(group => ({
    ...group,
    abnormalCount: group.targets.filter(target => String(target.resultStatus) === '2').length
  }))
}
