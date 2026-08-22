import assert from 'node:assert/strict'
import test from 'node:test'
import {
  buildAbnormalTopOption,
  buildAbnormalTopRows,
  buildCalendarHeatOption,
  buildInspectionInsight,
  buildResultCompositionOption,
  buildToolHealthOption,
  buildWeekTrendOption,
  normalizeToolHealthRows
} from '../inspectionChartSystem.js'

test('inspection insight leads with the operational conclusion', () => {
  assert.deepEqual(buildInspectionInsight({}, '本周'), {
    status: '3',
    title: '本周尚无巡检记录',
    detail: '执行模板或等待计划运行后，这里会给出趋势与异常结论。'
  })
  const abnormal = buildInspectionInsight({ recordCount: 8, abnormalTargetCount: 3, successRate: '75%' }, '本周')
  assert.equal(abnormal.status, '2')
  assert.match(abnormal.title, /3 个异常子项/)
})

test('weekly trend keeps volume and anomalies as different encodings', () => {
  const option = buildWeekTrendOption([
    { date: '2026-08-17', total: 4, abnormal: 0 },
    { date: '2026-08-18', total: 6, abnormal: 2 }
  ])
  assert.deepEqual(option.series.map((item) => item.type), ['bar', 'line'])
  assert.equal(option.series[1].markPoint.data[0].coord[1], 2)
  assert.equal(option.xAxis.data[1], '8/18')
})

test('result composition uses a directly labelled stack instead of a pie', () => {
  const option = buildResultCompositionOption({ normalCount: 6, abnormalCount: 2, skippedCount: 1 })
  assert.ok(option.series.every((item) => item.type === 'bar' && item.stack === 'result'))
  assert.deepEqual(option.series.map((item) => item.data[0]), [6, 2, 1])
  assert.equal(option.series[0].itemStyle.borderRadius[0], 5)
  assert.equal(option.series[2].itemStyle.borderRadius[1], 5)
})

test('tool health sorts the weakest tools first and keeps a threshold line', () => {
  const rows = normalizeToolHealthRows([
    { toolName: '接口', healthRate: '98%', totalCount: 9 },
    { toolName: '磁盘', healthRate: '72%', totalCount: 4 }
  ])
  assert.deepEqual(rows.map((item) => item.name), ['磁盘', '接口'])
  const option = buildToolHealthOption(rows.map((item) => ({ toolName: item.name, healthRate: item.value, totalCount: item.total })))
  assert.equal(option.series[0].markLine.data[0].xAxis, 90)
})

test('abnormal top groups repeated step names and uses ranked bars', () => {
  const rows = buildAbnormalTopRows([
    { stepName: 'FTP目录' },
    { stepName: '数据库查询' },
    { stepName: 'FTP目录' }
  ])
  assert.deepEqual(rows, [
    { name: 'FTP目录', value: 2 },
    { name: '数据库查询', value: 1 }
  ])
  assert.equal(buildAbnormalTopOption(rows.flatMap((item) => Array.from({ length: item.value }, () => ({ stepName: item.name })))).series[0].type, 'bar')
})

test('calendar heat keeps count and abnormal values in every real date cell', () => {
  const option = buildCalendarHeatOption({
    month: '2026-08',
    days: [
      { date: '2026-08-01', total: 3, abnormal: 0 },
      { date: '2026-08-02', total: 4, abnormal: 1 }
    ]
  })
  assert.equal(option.calendar.range, '2026-08')
  assert.deepEqual(option.series[1].data[0].value, ['2026-08-02', 4, 1])
  assert.match(option.series[1].label.formatter({ value: ['2026-08-02', 4, 1] }), /1异/)
  assert.equal(option.visualMap.length, 2)
})
