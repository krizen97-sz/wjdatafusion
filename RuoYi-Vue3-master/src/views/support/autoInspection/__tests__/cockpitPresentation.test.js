import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import {
  RESULT_ABNORMAL,
  RESULT_NORMAL,
  RESULT_SKIP,
  RESULT_WARNING,
  buildCurrentStatusDistribution,
  filterPlanHealth,
  filterScopeHealth,
  formatHealthScore,
  groupPlanHealthByScope,
  healthStatusLabel,
  normalizeCockpitDashboard,
  normalizeHealthScore,
  summarizeScopeHealth
} from '../cockpitPresentation.js'

const cockpitSource = readFileSync(new URL('../cockpit.vue', import.meta.url), 'utf8')

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

test('plan filters keep per-run and daily-summary plans in one searchable collection', () => {
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

test('cockpit groups today plans by site and main platform', () => {
  const grouped = groupPlanHealthByScope([
    { planId: 1, planName: '现场公共巡检', scopeType: 'SITE', siteId: 2, siteName: '武进分局', expectedCount: 1, completedCount: 1, normalCount: 1, resultStatus: RESULT_NORMAL },
    { planId: 2, planName: 'TIM巡检', scopeType: 'MAIN_PLATFORM', siteId: 2, siteName: '武进分局', mainPlatformId: 19, mainPlatformName: 'TIM平台', expectedCount: 12, completedCount: 11, normalCount: 10, warningCount: 1, resultStatus: RESULT_WARNING },
    { planId: 3, planName: '待归属计划', expectedCount: 1, completedCount: 1, normalCount: 1, resultStatus: RESULT_NORMAL }
  ])

  assert.equal(grouped.sites.length, 1)
  assert.equal(grouped.sites[0].children.length, 1)
  assert.equal(grouped.sites[0].resultStatus, RESULT_WARNING)
  assert.equal(grouped.unassigned.length, 1)
  assert.equal(filterScopeHealth(grouped.sites, 'TIM').length, 1)
  assert.deepEqual(summarizeScopeHealth(grouped.sites, grouped.unassigned), {
    siteCount: 1,
    platformCount: 1,
    abnormalSiteCount: 0,
    warningSiteCount: 1,
    unassignedPlanCount: 1
  })
})

test('cockpit presents today health by site and main platform', () => {
  assert.ok(cockpitSource.includes('<h2>今日现场状态</h2>'))
  assert.ok(cockpitSource.includes('<h2>今日现场与主平台健康</h2>'))
  assert.ok(cockpitSource.includes('row-key="scopeKey"'))
  assert.ok(cockpitSource.includes('scope.row.scopeName'))
  assert.ok(cockpitSource.includes('主平台为最深健康层级'))
  assert.ok(cockpitSource.includes('待归属计划不参与现场健康计算'))
  assert.ok(!cockpitSource.includes('今日计划健康清单'))
})

test('cockpit navigation and compact plan filters remain clean at narrow widths', () => {
  assert.equal(cockpitSource.match(/@click="openOverview\(\)"/g)?.length, 2)
  assert.ok(!cockpitSource.includes('@click="openOverview"'))
  assert.ok(cockpitSource.includes('@media (max-width: 900px)'))
  assert.ok(cockpitSource.includes('.cockpit-panel__head--controls'))
  assert.ok(cockpitSource.includes('flex-direction: column'))
})
