import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import {
  buildMetricChangeOption, buildMetricComparisonOption, buildMetricDurationOption, buildMetricRangeOption,
  buildMetricStatusOption, buildMetricTrendOption, comparisonValues, formatMetricValue, metricExecutionPoints,
  metricNumber, metricPoints, normalizeMetricDashboard
} from '../planMetricsPresentation.js'

const palette = { heading: '#fff', text: '#ddd', muted: '#999', grid: '#345', surface: '#123', primary: '#6df', normal: '#3b8', warning: '#ea5', danger: '#e66', idle: '#789' }
const metric = {
  metricKey: 'database:rows', metricName: '数据库数量', targetName: '过车记录', unit: '条', sampleCount: 3, numericCount: 2,
  minimum: '0', average: '1', maximum: '2', statusCounts: { 1: 2, 2: 1, 3: 0, 4: 0 },
  latest: { recordId: 3, value: '2', previousValue: '0', changeValue: '2', baselineFlag: 'N' },
  points: [
    { resultId: 1, recordId: 1, sampleTime: '2026-09-02 08:00:00', value: '0', previousValue: '0', changeValue: '0', resultStatus: '1', baselineFlag: 'Y' },
    { resultId: 2, recordId: 2, sampleTime: '2026-09-02 08:02:00', value: null, previousValue: null, changeValue: null, resultStatus: '2', baselineFlag: 'N' },
    { resultId: 3, recordId: 3, sampleTime: '2026-09-02 08:10:00', value: '2', previousValue: '0', changeValue: '2', resultStatus: '1', baselineFlag: 'N' }
  ]
}

test('metric numbers distinguish missing values from measured zero and keep display precision', () => {
  assert.equal(metricNumber(null), null)
  assert.equal(metricNumber(''), null)
  assert.equal(metricNumber('0'), 0)
  assert.equal(metricNumber('not-a-number'), null)
  assert.equal(formatMetricValue('9007199254740993.12'), '9,007,199,254,740,993.12')
  assert.equal(formatMetricValue('-0.50'), '-0.50')
  assert.equal(formatMetricValue(null), '--')
})

test('metric samples preserve gaps and never display a first baseline as a zero change', () => {
  const points = metricPoints(metric)
  assert.equal(points[0].valueNumber, 0)
  assert.equal(points[0].previousNumber, null)
  assert.equal(points[0].changeNumber, null)
  assert.equal(points[1].valueNumber, null)
  assert.equal(points[2].changeNumber, 2)
  assert.equal(points[2].timestamp - points[1].timestamp, 8 * 60000)
  assert.equal(metric.points[0].changeValue, '0')
})

test('trend and change charts retain exact source record identities and gap semantics', () => {
  const trend = buildMetricTrendOption(metric, palette, false)
  assert.equal(trend.xAxis.type, 'time')
  assert.equal(trend.series[0].connectNulls, false)
  assert.equal(trend.series[0].data[2].recordId, 3)
  assert.equal(trend.series[1].data[0].value[1], null)
  assert.equal(trend.animation, false)
  const change = buildMetricChangeOption(metric, palette, true)
  assert.equal(change.series[0].data[0].value[1], null)
  assert.equal(change.series[0].data[2].value[1], 2)
  assert.equal(change.animationDurationUpdate, 240)
})

test('comparison and range figures stay in one selected metric unit', () => {
  const comparison = buildMetricComparisonOption(metric, palette)
  assert.deepEqual(comparisonValues(metric), ['0', '2'])
  assert.deepEqual(comparison.series[0].data.map((row) => row.value), [0, 2])
  assert.equal(comparison.yAxis.name, '条')
  assert.deepEqual(comparisonValues({ latest: { value: '15', previousValue: '0', baselineFlag: 'Y' } }), [null, '15'])
  const ranges = buildMetricRangeOption(metric, palette)
  assert.deepEqual(ranges.series[0].data.map((row) => row.rawValue), ['0', '1', '2'])
})

test('judgement and duration charts keep failed samples and valid zero-duration runs', () => {
  const status = buildMetricStatusOption(metric, palette)
  assert.equal(status.title.text, '3')
  assert.equal(status.series[0].data.reduce((sum, row) => sum + row.value, 0), 3)
  const executions = [
    { recordId: 1, sampleTime: '2026-09-02 08:00:00', durationMs: 0, resultStatus: '1' },
    { recordId: 2, sampleTime: '2026-09-02 08:02:00', durationMs: null, resultStatus: '2' },
    { recordId: 3, sampleTime: '2026-09-02 08:10:00', durationMs: 1500, resultStatus: '2' }
  ]
  assert.equal(metricExecutionPoints(executions).length, 2)
  assert.deepEqual(buildMetricDurationOption(executions, palette).series[0].data.map((row) => row.recordId), [1, 3])
})

test('metric tooltips escape external names and expose raw precision instead of chart-rounded values', () => {
  const unsafe = { ...metric, targetName: '<img src=x>', points: [{ ...metric.points[2], value: '9007199254740993.12', evaluationRule: '<script>alert(1)</script>' }] }
  const option = buildMetricTrendOption(unsafe, palette)
  const tooltip = option.tooltip.formatter([{ data: { resultId: 3 } }])
  assert.ok(tooltip.includes('9,007,199,254,740,993.12'))
  assert.ok(tooltip.includes('&lt;img'))
  assert.ok(!tooltip.includes('<script>'))
})

test('plan metrics use a new read-only endpoint, native filters, six charts, and stale-request guards', () => {
  const component = readFileSync(new URL('../components/PlanMetricDashboard.vue', import.meta.url), 'utf8')
  const cockpit = readFileSync(new URL('../cockpit.vue', import.meta.url), 'utf8')
  const api = readFileSync(new URL('../../../../api/support/autoInspection/index.js', import.meta.url), 'utf8')
  assert.equal(component.match(/<AutoInspectionChart/g)?.length, 6)
  assert.ok(component.includes('sequence !== requestSequence'))
  assert.ok(component.includes('snapshotDays === days.value'))
  assert.ok(component.includes('当前统计仅包含最近'))
  assert.ok(component.includes('append-to=".inspection-cockpit"'))
  assert.ok(cockpit.includes('name="metrics"'))
  assert.ok(cockpit.includes('name="overview"'))
  assert.ok(api.includes("url: '/support/autoInspection/dashboard/metrics', method: 'get'"))
  assert.deepEqual(normalizeMetricDashboard().metrics, [])
})
