import test from 'node:test'
import assert from 'node:assert/strict'
import { getRecordResultGroups, resultLabel, resultTone } from '../recordResultPresentation.js'

test('groups results by stable step identity, not duplicate tool or step names', () => {
  const record = {
    steps: [
      { stepResultId: 2, stepName: '磁盘检查', toolCode: 'SERVER_DISK', sortOrder: 2 },
      { stepResultId: 1, stepName: '磁盘检查', toolCode: 'SERVER_DISK', sortOrder: 1 }
    ],
    targetResults: [
      { stepResultId: 2, targetName: '/data', resultStatus: '2' },
      { stepResultId: 1, targetName: '/', resultStatus: '1' }
    ]
  }
  const before = JSON.stringify(record)
  const groups = getRecordResultGroups(record)
  assert.deepEqual(groups.map(group => group.stepResultId), [1, 2])
  assert.equal(groups[0].targets[0].targetName, '/')
  assert.equal(groups[1].abnormalCount, 1)
  assert.equal(groups[1].targets[0].toolCode, 'SERVER_DISK')
  assert.equal(JSON.stringify(record), before)
})

test('does not hide disabled steps or failures that have no child results', () => {
  const groups = getRecordResultGroups({ steps: [
    { stepResultId: 1, resultStatus: '3', resultSummary: '步骤未启用' },
    { stepResultId: 2, resultStatus: '2', errorMessage: '目标配置缺失' }
  ] })
  assert.equal(groups.length, 2)
  assert.equal(groups[1].errorMessage, '目标配置缺失')
  assert.deepEqual(groups[1].targets, [])
})

test('preserves orphan targets and old records without steps', () => {
  assert.deepEqual(getRecordResultGroups(), [])
  const groups = getRecordResultGroups({ targetResults: [
    { stepResultId: 6, targetName: 'legacy', resultStatus: 2 },
    { stepResultId: 6, targetName: 'second', resultStatus: 1 },
    { targetName: 'unknown' }
  ] })
  assert.equal(groups.length, 2)
  assert.equal(groups[0].targets.length, 2)
  assert.equal(groups[0].abnormalCount, 1)
  assert.equal(groups[1].stepName, '未归属步骤')
})

test('status text and tone agree for numeric and string states', () => {
  for (const value of ['1', 1]) {
    assert.equal(resultLabel(value), '正常')
    assert.equal(resultTone(value), 'success')
  }
  assert.equal(resultLabel('4'), '关注')
  assert.equal(resultTone('4'), 'warning')
  assert.equal(resultLabel(null), '未执行')
  assert.equal(resultTone(null), 'info')
})
