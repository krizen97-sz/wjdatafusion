import test from 'node:test'
import assert from 'node:assert/strict'
import {
  buildInspectionRecordTableRows,
  buildLabelTreeOptions,
  buildWeekResultDistribution,
  collectLabelNames,
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

test('table rows merge the ownership date for consecutive same-day records', () => {
  const rows = buildInspectionRecordTableRows([
    { recordId: 1, inspectionTime: '2026-08-20 10:20:00', resultStatus: '1' },
    { recordId: 2, inspectionTime: '2026-08-20 08:30:00', resultStatus: '2' },
    { recordId: 3, inspectionTime: '2026-08-19 09:10:00', resultStatus: '1' }
  ], new Date(2026, 7, 20, 12, 0, 0))

  assert.deepEqual(rows.map((item) => item.ownershipRowspan), [2, 0, 1])
  assert.equal(rows[0].ownershipDateLabel, '今天')
  assert.equal(rows[0].ownershipAbnormalCount, 1)
  assert.equal(rows[2].ownershipDateLabel, '昨天')
})

test('label tree groups templates and plans into directory nodes', () => {
  const tree = buildLabelTreeOptions([
    { templateId: 2, templateName: '磁盘巡检', labelName: '服务器' },
    { templateId: 1, templateName: '接口巡检', labelName: '平台接口' },
    { templateId: 3, templateName: '未分类模板', labelName: '' }
  ], { idKey: 'templateId', nameKey: 'templateName' })

  assert.deepEqual(tree.map((item) => item.label), ['服务器', '平台接口', '未分类'])
  assert.equal(tree[0].isDirectory, true)
  assert.equal(tree[0].disabled, undefined)
  assert.equal(tree[0].children[0].value, 2)
  assert.deepEqual(collectLabelNames(
    [{ labelName: '服务器' }, { labelName: '平台接口' }],
    [{ labelName: '服务器' }, { labelName: '' }]
  ), ['服务器', '平台接口'])
})
