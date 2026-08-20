import test from 'node:test'
import assert from 'node:assert/strict'
import {
  buildWeekResultDistribution,
  formatInspectionClock,
  groupInspectionRecordsByDay
} from '../overviewPresentation.js'

test('records are grouped by day with daily result totals', () => {
  const rows = [
    { recordId: 1, inspectionTime: '2026-08-19 09:10:00', resultStatus: '1', sourceType: 'AUTO' },
    { recordId: 2, inspectionTime: '2026-08-20 08:30:00', resultStatus: '2', sourceType: 'MANUAL' },
    { recordId: 3, inspectionTime: '2026-08-20 10:20:00', resultStatus: '1', sourceType: 'AUTO' }
  ]

  const groups = groupInspectionRecordsByDay(rows, new Date(2026, 7, 20, 12, 0, 0))

  assert.equal(groups.length, 2)
  assert.equal(groups[0].label, '今天')
  assert.equal(groups[0].weekday, '周四')
  assert.equal(groups[0].status, '2')
  assert.equal(groups[0].normalCount, 1)
  assert.equal(groups[0].abnormalCount, 1)
  assert.equal(groups[0].successRate, '50%')
  assert.deepEqual(groups[0].records.map((item) => item.recordId), [3, 2])
  assert.equal(groups[1].label, '昨天')
})

test('unknown dates stay visible and do not mutate source rows', () => {
  const rows = [{ recordId: 8, inspectionTime: '', resultStatus: '3', sourceType: 'MANUAL' }]
  const snapshot = structuredClone(rows)
  const groups = groupInspectionRecordsByDay(rows, new Date(2026, 7, 20))

  assert.equal(groups[0].label, '日期未记录')
  assert.equal(groups[0].status, '3')
  assert.equal(groups[0].manualCount, 1)
  assert.deepEqual(rows, snapshot)
})

test('week distribution keeps normal abnormal and untested results', () => {
  assert.deepEqual(buildWeekResultDistribution({ recordCount: 6, normalCount: 3, abnormalCount: 2 }), [
    { name: '正常', value: 3 },
    { name: '异常', value: 2 },
    { name: '未检测', value: 1 }
  ])
  assert.equal(buildWeekResultDistribution({})[0].empty, true)
  assert.equal(formatInspectionClock('2026-08-20T09:26:31'), '09:26')
})
