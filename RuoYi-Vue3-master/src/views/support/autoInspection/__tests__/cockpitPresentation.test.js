import test from 'node:test'
import assert from 'node:assert/strict'
import {
  RESULT_ABNORMAL,
  RESULT_NORMAL,
  RESULT_SKIP,
  RESULT_WARNING,
  buildCurrentStatusDistribution,
  filterPlanHealth,
  formatHealthScore,
  healthStatusLabel,
  normalizeCockpitDashboard,
  normalizeHealthScore
} from '../cockpitPresentation.js'

test('cockpit normalizes mixed dashboard payloads without mutating source data', () => {
  const source = {
    healthOverview: { healthScore: 93.4 },
    currentPlanHealth: [{ planId: 1, resultStatus: RESULT_NORMAL }]
  }
  const result = normalizeCockpitDashboard(source)

  assert.equal(result.healthOverview.healthScore, 93.4)
  assert.equal(result.currentPlanHealth.length, 1)
  assert.deepEqual(result.combinedTrend, [])
  assert.notEqual(result.currentPlanHealth, source.currentPlanHealth)
})

test('health score and labels support normal warning abnormal and missing states', () => {
  assert.equal(normalizeHealthScore(103.48), 100)
  assert.equal(normalizeHealthScore(-3), 0)
  assert.equal(normalizeHealthScore('87.26'), 87.3)
  assert.equal(formatHealthScore(null, RESULT_SKIP), '--')
  assert.equal(formatHealthScore(0, RESULT_NORMAL), '0%')
  assert.equal(formatHealthScore(87.26, RESULT_NORMAL), '87.3%')
  assert.equal(healthStatusLabel(RESULT_NORMAL), '健康')
  assert.equal(healthStatusLabel(RESULT_WARNING), '需关注')
  assert.equal(healthStatusLabel(RESULT_ABNORMAL), '异常')
  assert.equal(healthStatusLabel(RESULT_SKIP), '未执行')
})

test('plan filters keep routine and frequent plans in one searchable collection', () => {
  const rows = [
    { planName: '每日平台巡检', templateName: '平台模板', labelName: '平台', planMode: 'ROUTINE' },
    { planName: 'Kafka分钟监测', templateName: '消息模板', labelName: '消息', planMode: 'FREQUENT' }
  ]

  assert.equal(filterPlanHealth(rows, 'ALL', '').length, 2)
  assert.equal(filterPlanHealth(rows, 'FREQUENT', '').length, 1)
  assert.equal(filterPlanHealth(rows, 'ALL', '消息').length, 1)
})

test('current status distribution always exposes all four business states', () => {
  const result = buildCurrentStatusDistribution([
    { resultStatus: RESULT_NORMAL },
    { resultStatus: RESULT_NORMAL },
    { resultStatus: RESULT_WARNING },
    { resultStatus: RESULT_ABNORMAL }
  ])

  assert.deepEqual(result.map((item) => item.value), [2, 1, 1, 0])
})
