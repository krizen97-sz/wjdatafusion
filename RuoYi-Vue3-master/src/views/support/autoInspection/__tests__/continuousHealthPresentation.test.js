import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import {
  groupDailyHealthRows,
  healthStatusLabel,
  summarizeDailyHealth
} from '../continuousHealthPresentation.js'

const workspaceSource = readFileSync(new URL('../index.vue', import.meta.url), 'utf8')
const panelSource = readFileSync(new URL('../components/ContinuousHealthPanel.vue', import.meta.url), 'utf8')
const apiSource = readFileSync(new URL('../../../../api/support/autoInspection/index.js', import.meta.url), 'utf8')

test('daily health groups multiple plans into one date and preserves abnormal history', () => {
  const rows = groupDailyHealthRows([
    { healthDate: '2026-08-24', planId: 1, planName: 'Kafka', expectedCount: 10, completedCount: 10, normalCount: 8, abnormalCount: 2, dayStatus: '2', lastResultStatus: '1' },
    { healthDate: '2026-08-24', planId: 2, planName: 'MQTT', expectedCount: 10, completedCount: 9, normalCount: 9, missingCount: 1, dayStatus: '4', lastResultStatus: '1' }
  ])

  assert.equal(rows.length, 1)
  assert.equal(rows[0].plans.length, 2)
  assert.equal(rows[0].dayStatus, '2')
  assert.equal(rows[0].recovered, true)
  assert.equal(rows[0].healthScore, 85)
  assert.equal(healthStatusLabel(rows[0].dayStatus, rows[0].recovered), '异常已恢复')
})

test('monthly summary is weighted by expected slots', () => {
  const summary = summarizeDailyHealth([
    { expectedCount: 100, normalCount: 90, dayStatus: '2' },
    { expectedCount: 10, normalCount: 10, dayStatus: '1' }
  ])
  assert.equal(summary.dayCount, 2)
  assert.equal(summary.healthScore, 90.9)
  assert.equal(summary.abnormalDays, 1)
})

test('workspace exposes plan mode, daily health and activity tools', () => {
  for (const marker of ['PLAN_MODE_ROUTINE', 'PLAN_MODE_FREQUENT', 'ContinuousHealthPanel', 'TOOL_KAFKA_TOPIC_ACTIVITY', 'TOOL_KAFKA_CONSUMER_PROGRESS', 'TOOL_MQTT_TOPIC_ACTIVITY']) {
    assert.ok(workspaceSource.includes(marker), `missing high-frequency workspace marker: ${marker}`)
  }
  assert.ok(apiSource.includes("/support/autoInspection/health/daily"))
  assert.ok(apiSource.includes("/support/autoInspection/health/samples"))
  assert.ok(apiSource.includes('healthConfig: stringifyConfig(data.healthConfig)'))
  assert.ok(panelSource.includes('>查看</el-button>'))
  assert.ok(!panelSource.includes('查看当日结果'))
  assert.ok(!panelSource.includes('v-for="plan in scope.row.plans'))
  assert.ok(workspaceSource.includes('type="expand"'))
  assert.ok(workspaceSource.includes(':expand-row-keys="healthSampleExpandedKeys"'))
  assert.ok(workspaceSource.includes('handleHealthSampleExpand'))
  assert.ok(!workspaceSource.includes('<article v-for="sample in healthSampleRows"'))
  assert.ok(workspaceSource.includes('targetScope.row.previousValue'))
  assert.ok(workspaceSource.includes('targetScope.row.changeValue'))
  assert.ok(!workspaceSource.includes('进入关注</span>'))
  assert.ok(!workspaceSource.includes('确认异常</span>'))
  assert.ok(!workspaceSource.includes('恢复确认</span>'))
})

test('unexecuted state is distinct from a healthy comparison baseline', () => {
  assert.equal(healthStatusLabel('3'), '尚未执行')
  assert.ok(workspaceSource.includes('基线已建立'))
  assert.ok(workspaceSource.includes('首次执行建立基线并按正常计入'))
})
