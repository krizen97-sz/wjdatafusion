import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import {
  buildRecentExecutionChartRows, buildScopeHealthChartRows, filterScopeChartRows,
  resolvePlanChartRow, resolveScopeChartRow
} from '../cockpitPresentation.js'
import {
  buildIssueOption, buildPlanOption, buildRecordOption, buildScopeOption,
  buildStatusOption, buildTrendOption, preserveCockpitChartView
} from '../cockpitChartOptions.js'

const palette = {
  heading: '#102030', text: '#304050', muted: '#607080', grid: '#dddddd',
  surface: '#ffffff', subtle: '#eeeeee', normal: '#008040', warning: '#b08020',
  danger: '#b04040', idle: '#888888', primary: '#2080c0',
  warningSoft: '#fff4dd', dangerSoft: '#ffe8e8', fontFamily: 'sans-serif'
}

test('health composition counts every site and platform independently of ranking size', () => {
  const sites = Array.from({ length: 8 }, (_, index) => ({
    scopeKey: `SITE:${index}`, scopeType: 'SITE', scopeName: `现场${index}`, resultStatus: '1', healthScore: 100,
    children: Array.from({ length: 3 }, (_, child) => ({
      scopeKey: `MAIN_PLATFORM:${index}-${child}`, scopeType: 'MAIN_PLATFORM', scopeName: `平台${child}`, resultStatus: '4', healthScore: 80
    }))
  }))
  assert.equal(buildScopeHealthChartRows(sites).length, 12)
  const all = buildScopeHealthChartRows(sites, 32)
  assert.equal(all.length, 32)
  const option = buildStatusOption(sites, palette, { scopeType: 'MAIN_PLATFORM', resultStatus: '4' })
  assert.equal(option.title.text, '32')
  assert.equal(option.title.subtext, '健康范围')
  assert.equal(option.series[1].data.find((item) => item.status === '4').value, 24)
  assert.equal(option.series[1].data.find((item) => item.status === '4').selected, true)
  assert.equal(filterScopeChartRows(all, 'MAIN_PLATFORM', '4').length, 24)
  assert.equal(filterScopeChartRows(all, 'SITE', '2').length, 0)
})

test('zero bars and duplicate names drill down using stable IDs including axis labels', () => {
  const scopes = [{ scopeKey: 'SITE:1', chartName: '同名', healthScore: 0, resultStatus: '4' }]
  const option = buildScopeOption(scopes, palette)
  assert.equal(option.yAxis.triggerEvent, true)
  assert.deepEqual(option.yAxis.data, ['SITE:1'])
  assert.equal(resolveScopeChartRow({ componentType: 'yAxis', value: 'SITE:1' }, scopes), scopes[0])
  assert.equal(resolveScopeChartRow({ data: option.series[0].data[0] }, scopes), scopes[0])
  assert.equal(resolveScopeChartRow({ componentType: 'xAxis', value: 'SITE:1' }, scopes), undefined)

  const plans = [{ planId: 1, chartName: '同名', completionRate: 0 }, { planId: 2, chartName: '同名', completionRate: 80 }]
  const planOption = buildPlanOption(plans, palette)
  assert.deepEqual(planOption.yAxis.data, ['1', '2'])
  assert.equal(resolvePlanChartRow({ componentType: 'yAxis', value: '2' }, plans), plans[1])
  assert.equal(resolvePlanChartRow({}, plans), undefined)
})

test('rankings retain all rows and provide scrolling instead of silently truncating the source', () => {
  const rows = Array.from({ length: 20 }, (_, index) => ({ scopeKey: `SITE:${index}`, chartName: `现场${index}`, healthScore: 80 }))
  const option = buildScopeOption(rows, palette)
  assert.equal(option.series[0].data.length, 20)
  assert.equal(option.dataZoom[0].endValue, 5)
  assert.equal(buildScopeOption(rows.slice(0, 6), palette).dataZoom.length, 0)
})

test('recent execution chart uses real timestamp intervals and rejects invalid times', () => {
  const rows = buildRecentExecutionChartRows([
    { recordId: 3, inspectionTime: '2026-09-03 10:20:00', resultStatus: '2' },
    { recordId: 1, inspectionTime: '2026-09-03 10:00:00', resultStatus: '1' },
    { recordId: 4, inspectionTime: 'not-a-date', resultStatus: '1' },
    { recordId: 2, inspectionTime: '2026-09-03 10:01:00', resultStatus: '1' }
  ])
  assert.deepEqual(rows.map((row) => row.recordId), [1, 2, 3])
  assert.equal(rows[1].timestamp - rows[0].timestamp, 60000)
  assert.equal(rows[2].timestamp - rows[1].timestamp, 19 * 60000)
  const option = buildRecordOption(rows, palette)
  assert.equal(option.xAxis.type, 'time')
  assert.equal(option.series[0].data[2].recordId, 3)
  assert.equal(option.series[0].data[2].symbol, 'diamond')
})

test('refresh preserves legend and zoom without retaining stale chart data', () => {
  const option = { legend: { data: ['健康度'] }, dataZoom: [{ id: 'scope-slider', startValue: 0, endValue: 5 }], series: [{ data: [99] }] }
  const previous = { legend: [{ selected: { 健康度: false } }], dataZoom: [{ id: 'scope-slider', start: 20, end: 70 }], series: [{ data: [1] }] }
  const next = preserveCockpitChartView(option, previous)
  assert.deepEqual(next.legend.selected, { 健康度: false })
  assert.equal(next.dataZoom[0].start, 20)
  assert.equal(next.dataZoom[0].startValue, undefined)
  assert.deepEqual(next.series[0].data, [99])
  assert.equal(option.dataZoom[0].startValue, 0)
  assert.equal(option.legend.selected, undefined)
})

test('chart motion can be disabled and tooltip data is escaped', () => {
  assert.equal(buildTrendOption([], palette, false).animation, false)
  assert.equal(buildTrendOption([], palette, true).animationDurationUpdate, 240)
  const issue = buildIssueOption([], palette)
  const tooltip = issue.tooltip.formatter({ data: { name: '<img src=x>', detail: '<script>', source: '&', resultStatus: '2' } })
  assert.ok(tooltip.includes('&lt;img'))
  assert.ok(!tooltip.includes('<script>'))
})

test('cockpit preserves chart-only workflow and has guarded refresh and responsive fullscreen', () => {
  const source = readFileSync(new URL('../cockpit.vue', import.meta.url), 'utf8')
  assert.equal(source.match(/<AutoInspectionChart/g)?.length, 6)
  assert.ok(!source.includes('<el-table'))
  assert.ok(source.includes('refreshing.value || disposed || !active'))
  assert.ok(source.includes("document.visibilityState === 'visible'"))
  assert.ok(source.includes('stopAutoRefresh()'))
  assert.ok(source.includes('上次成功数据'))
  assert.ok(source.includes('@media (max-width: 800px)'))
  assert.ok(source.includes('.inspection-cockpit:fullscreen'))
  const statusHandler = source.split('function handleStatusClick')[1].split('function resetScopeFilter')[0]
  assert.ok(statusHandler.includes('scopeType.value = data.scopeType'))
  assert.ok(!statusHandler.includes('openScopeDetail'))
  assert.ok(source.includes('if (!Number.isInteger(data?.issueIndex)) return'))
})
