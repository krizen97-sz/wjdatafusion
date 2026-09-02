import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import {
  RESULT_ABNORMAL,
  RESULT_NORMAL,
  RESULT_SKIP,
  RESULT_WARNING,
  buildCurrentStatusDistribution,
  buildIssueChartRows,
  buildPlanCompletionRows,
  buildRecentExecutionChartRows,
  buildScopeHealthChartRows,
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
const chartSource = readFileSync(new URL('../components/AutoInspectionChart.vue', import.meta.url), 'utf8')

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

test('full cockpit chart rows retain scope plan issue and record drill-down data', () => {
  const scopeRows = buildScopeHealthChartRows([{
    scopeKey: 'SITE:2', scopeName: '武进分局', resultStatus: RESULT_WARNING, healthScore: 82, expectedCount: 10, completedCount: 9,
    children: [{ scopeKey: 'MAIN_PLATFORM:19', scopeName: 'TIM平台', resultStatus: RESULT_ABNORMAL, healthScore: 60, expectedCount: 5, completedCount: 3 }]
  }])
  assert.deepEqual(scopeRows.map((row) => row.scopeKey), ['MAIN_PLATFORM:19', 'SITE:2'])

  const planRows = buildPlanCompletionRows([
    { planId: 1, planName: '正常计划', siteId: 2, resultStatus: RESULT_NORMAL, expectedCount: 10, completedCount: 10 },
    { planId: 2, planName: '待归属计划', resultStatus: RESULT_WARNING, expectedCount: 10, completedCount: 6 }
  ])
  assert.equal(planRows[0].chartName, '待归属 · 待归属计划')
  assert.equal(planRows[0].completionRate, 60)

  const issues = buildIssueChartRows([{ issueTitle: 'Kafka停滞', resultStatus: RESULT_ABNORMAL, planId: 2 }])
  assert.equal(issues[0].chartValue, 3)
  assert.equal(issues[0].planId, 2)

  const records = buildRecentExecutionChartRows([{ recordId: 9, planName: '巡检计划', inspectionTime: '2026-09-02 14:10:00', resultStatus: RESULT_NORMAL }])
  assert.equal(records[0].timeLabel, '14:10')
  assert.equal(records[0].statusLabel, '健康')
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

test('scope aggregation uses dashboard health scores when normal counts are absent', () => {
  const grouped = groupPlanHealthByScope([
    { planId: 1, planName: '现场计划', scopeType: 'SITE', siteId: 2, siteName: '武进分局', expectedCount: 10, completedCount: 8, healthScore: 80, resultStatus: RESULT_WARNING },
    { planId: 2, planName: '平台计划', scopeType: 'MAIN_PLATFORM', siteId: 2, siteName: '武进分局', mainPlatformId: 19, mainPlatformName: 'TIM平台', expectedCount: 10, completedCount: 6, healthScore: 60, resultStatus: RESULT_ABNORMAL }
  ])

  assert.equal(grouped.sites[0].healthScore, 70)
  assert.equal(grouped.sites[0].children[0].healthScore, 60)
})

test('cockpit is a chart-only dashboard with six drill-down charts', () => {
  for (const title of ['近七日巡检趋势', '今日健康构成', '现场与主平台健康度', '计划执行完成度', '待处理问题分布', '最近执行时间轴']) {
    assert.ok(cockpitSource.includes(`<h2>${title}</h2>`), `missing chart title: ${title}`)
  }
  assert.equal(cockpitSource.match(/<AutoInspectionChart/g)?.length, 6)
  assert.ok(cockpitSource.includes('@chart-click="handleTrendClick"'))
  assert.ok(cockpitSource.includes('@chart-click="handleStatusClick"'))
  assert.ok(cockpitSource.includes('@chart-click="handleScopeClick"'))
  assert.ok(cockpitSource.includes('@chart-click="handlePlanClick"'))
  assert.ok(cockpitSource.includes('@chart-click="handleIssueClick"'))
  assert.ok(cockpitSource.includes('@chart-click="handleRecordClick"'))
  assert.ok(!cockpitSource.includes('<el-table'))
  assert.ok(!cockpitSource.includes('cockpit-issue-list'))
  assert.ok(!cockpitSource.includes('cockpit-recent-list'))
  assert.ok(!cockpitSource.includes('cockpit-health-facts'))
  assert.ok(chartSource.includes("import * as echarts from 'echarts'"))
  assert.ok(chartSource.includes('ResizeObserver'))
  assert.ok(chartSource.includes("emit('chart-click', params)"))
  assert.ok(chartSource.includes('role="img"'))
})

test('cockpit keeps desktop command navigation and theme-safe chart layout', () => {
  assert.ok(cockpitSource.includes('@click="openOverview"'))
  assert.ok(cockpitSource.includes('@click="openConfig"'))
  assert.ok(cockpitSource.includes('grid-template-columns: repeat(12, minmax(0, 1fr))'))
  assert.ok(cockpitSource.includes('settingsStore.isDark'))
  assert.ok(cockpitSource.includes('animation: false'))
  assert.ok(cockpitSource.includes('@media (max-width: 1280px)'))
})
