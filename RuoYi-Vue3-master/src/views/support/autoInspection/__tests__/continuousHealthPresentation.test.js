import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import {
  groupDailyHealthRows,
  healthStatusLabel,
  paginateDailyHealthRows,
  summarizeDailyHealth
} from '../continuousHealthPresentation.js'

const workspaceSource = readFileSync(new URL('../index.vue', import.meta.url), 'utf8')
const panelSource = readFileSync(new URL('../components/ContinuousHealthPanel.vue', import.meta.url), 'utf8')
const apiSource = readFileSync(new URL('../../../../api/support/autoInspection/index.js', import.meta.url), 'utf8')

test('daily health groups multiple plans into one date and preserves abnormal history', () => {
  const rows = groupDailyHealthRows([
    { healthDate: '2026-08-24', planId: 1, planName: 'Kafka', scopeType: 'SITE', siteId: 2, siteName: '武进分局', expectedCount: 10, completedCount: 10, normalCount: 8, abnormalCount: 2, dayStatus: '2', lastResultStatus: '1' },
    { healthDate: '2026-08-24', planId: 2, planName: 'MQTT', scopeType: 'MAIN_PLATFORM', siteId: 2, siteName: '武进分局', mainPlatformId: 19, mainPlatformName: 'TIM平台', expectedCount: 10, completedCount: 9, normalCount: 9, missingCount: 1, dayStatus: '4', lastResultStatus: '1' }
  ])

  assert.equal(rows.length, 1)
  assert.equal(rows[0].plans.length, 2)
  assert.equal(rows[0].dayStatus, '2')
  assert.equal(rows[0].recovered, true)
  assert.equal(rows[0].healthScore, 85)
  assert.equal(healthStatusLabel(rows[0].dayStatus, rows[0].recovered), '异常已恢复')
})

test('monthly summary counts managed sites and excludes unassigned plans from health', () => {
  const groups = groupDailyHealthRows([
    { healthDate: '2026-08-24', planId: 1, scopeType: 'SITE', siteId: 2, siteName: '武进分局', expectedCount: 10, completedCount: 10, normalCount: 9, abnormalCount: 1, dayStatus: '2' },
    { healthDate: '2026-08-24', planId: 2, scopeType: 'MAIN_PLATFORM', siteId: 2, siteName: '武进分局', mainPlatformId: 19, mainPlatformName: 'TIM平台', expectedCount: 10, completedCount: 10, normalCount: 10, dayStatus: '1' },
    { healthDate: '2026-08-25', planId: 3, scopeType: 'SITE', siteId: 3, siteName: '市局', expectedCount: 1, completedCount: 1, normalCount: 1, dayStatus: '1' },
    { healthDate: '2026-08-25', planId: 4, planName: '历史待归属', expectedCount: 1, completedCount: 1, normalCount: 1, dayStatus: '1' }
  ])
  const summary = summarizeDailyHealth(groups)
  assert.equal(summary.dayCount, 2)
  assert.equal(summary.siteCount, 2)
  assert.equal(summary.platformCount, 1)
  assert.equal(summary.abnormalSiteCount, 1)
  assert.equal(summary.unassignedPlanCount, 1)
})

test('daily health pagination keeps complete date groups together', () => {
  const rows = Array.from({ length: 31 }, (_, index) => ({
    healthDate: `2026-08-${String(31 - index).padStart(2, '0')}`
  }))

  assert.deepEqual(
    paginateDailyHealthRows(rows, 2, 20).map((item) => item.healthDate),
    rows.slice(20).map((item) => item.healthDate)
  )
  assert.equal(paginateDailyHealthRows(rows, 1, 10).length, 10)
})

test('a multi-plan day stays abnormal while any plan remains abnormal', () => {
  const [group] = groupDailyHealthRows([
    { healthDate: '2026-08-29', planId: 1, planName: 'Kafka', scopeType: 'SITE', siteId: 2, siteName: '武进分局', expectedCount: 20, completedCount: 20, normalCount: 18, abnormalCount: 2, dayStatus: '2', lastResultStatus: '1', abnormalSummary: 'Kafka异常已恢复' },
    { healthDate: '2026-08-29', planId: 2, planName: '数据库', scopeType: 'MAIN_PLATFORM', siteId: 2, siteName: '武进分局', mainPlatformId: 19, mainPlatformName: 'TIM平台', expectedCount: 10, completedCount: 10, normalCount: 8, abnormalCount: 2, dayStatus: '2', lastResultStatus: '2', abnormalSummary: '数据库异常仍在持续' }
  ])

  assert.equal(group.plans.length, 2)
  assert.equal(group.recovered, false)
  assert.equal(healthStatusLabel(group.dayStatus, group.recovered), '异常持续中')
  assert.equal(group.abnormalSummary, '数据库异常仍在持续')
  assert.equal(group.plans[0].planName, '数据库')
})

test('overview uses one scope health path and keeps execution records in a drawer', () => {
  assert.ok(workspaceSource.includes('<strong>巡检总览</strong>'))
  assert.ok(workspaceSource.includes('按日期查看现场与主平台健康'))
  assert.ok(workspaceSource.includes('v-model="recordArchiveDrawerOpen"'))
  assert.ok(workspaceSource.includes('>全部执行记录</el-button>'))
  assert.ok(workspaceSource.includes(':scope-options="inspectionScopeTree"'))
  assert.ok(!workspaceSource.includes('<el-tabs v-model="recordViewMode"'))
})

test('workspace exposes plan mode, daily health and activity tools', () => {
  for (const marker of ['PLAN_MODE_ROUTINE', 'PLAN_MODE_FREQUENT', 'ContinuousHealthPanel', 'TOOL_KAFKA_TOPIC_ACTIVITY', 'TOOL_KAFKA_CONSUMER_PROGRESS', 'TOOL_MQTT_TOPIC_ACTIVITY']) {
    assert.ok(workspaceSource.includes(marker), `missing high-frequency workspace marker: ${marker}`)
  }
  assert.ok(apiSource.includes("/support/autoInspection/health/daily"))
  assert.ok(apiSource.includes("/support/autoInspection/health/samples"))
  assert.ok(apiSource.includes("/support/autoInspection/scopes"))
  assert.ok(apiSource.includes('healthConfig: stringifyConfig(data.healthConfig)'))
  assert.ok(panelSource.includes('>查看</el-button>'))
  assert.ok(!panelSource.includes('查看当日结果'))
  assert.ok(panelSource.includes('v-for="site in scope.row.sites"'))
  assert.ok(panelSource.includes('v-for="plan in platformScope.row.plans"'))
  assert.ok(panelSource.includes('planId: plan.planId'))
  assert.ok(panelSource.includes('continuous-health-plan-link'))
  assert.ok(panelSource.includes('QuestionFilled'))
  assert.ok(panelSource.includes('statusGuide'))
  assert.ok(panelSource.includes('width="94"'))
  assert.ok(panelSource.includes('class="auto-table record-table record-table--daily continuous-health-table"'))
  assert.ok(panelSource.includes('label="归属日期"'))
  assert.ok(panelSource.includes('class="record-date-cell"'))
  assert.ok(panelSource.includes('class="site-health-block"'))
  assert.ok(panelSource.includes('class="record-count-cell"'))
  assert.ok(panelSource.includes('presentInspectionDate'))
  assert.ok(panelSource.includes(':data="pagedRows"'))
  assert.ok(panelSource.includes('v-model:page="pageNum"'))
  assert.ok(panelSource.includes('v-model:limit="pageSize"'))
  assert.ok(panelSource.includes('@pagination="handlePagination"'))
  assert.ok(workspaceSource.includes('type="expand"'))
  assert.ok(workspaceSource.includes(':expand-row-keys="healthSampleExpandedKeys"'))
  assert.ok(workspaceSource.includes('handleHealthSampleExpand'))
  assert.ok(workspaceSource.includes('planId: planId ?? dailyHealthPlanId.value'))
  assert.ok(!workspaceSource.includes('<article v-for="sample in healthSampleRows"'))
  assert.ok(workspaceSource.includes('targetScope.row.previousValue'))
  assert.ok(workspaceSource.includes('targetScope.row.changeValue'))
  assert.ok(workspaceSource.includes('grid-template-columns: minmax(0, 1fr);'))
  assert.ok(!workspaceSource.includes('health-target-result__action'))
  assert.ok(workspaceSource.includes('<el-popover placement="left" :width="520" trigger="hover" :show-after="250" :hide-after="80">'))
  assert.ok(workspaceSource.includes('<el-scrollbar max-height="260px">'))
  assert.ok(!workspaceSource.includes('进入关注</span>'))
  assert.ok(!workspaceSource.includes('确认异常</span>'))
  assert.ok(!workspaceSource.includes('恢复确认</span>'))
})

test('unexecuted state is distinct from a healthy comparison baseline', () => {
  assert.equal(healthStatusLabel('3'), '尚未执行')
  assert.ok(workspaceSource.includes('基线已建立'))
  assert.ok(workspaceSource.includes('新周期首次取值建立基线并按正常计入'))
  assert.ok(workspaceSource.includes('每天重新累计'))
  assert.ok(workspaceSource.includes('comparisonScopeOptions'))
  assert.ok(!workspaceSource.includes("if (planForm.value.planMode === PLAN_MODE_FREQUENT) cfg.type = 'interval'"))
})
