import test from 'node:test'
import assert from 'node:assert/strict'
import { jobNodeStates, mergeEvents, runActive, runFinishing, runNeedsReview, runStatus, nodeRows, rowColumns } from '../kettle/runRules.js'

test('native terminal messages do not release the execution barrier before process cleanup', () => {
  for (const state of ['SUCCEEDED', 'PREVIEW_COMPLETE', 'FAILED', 'STOPPED', 'TIMED_OUT']) {
    assert.equal(runFinishing({ state }), true)
    assert.equal(runFinishing({ state, exitCode: 0 }), false)
    assert.equal(runFinishing({ state, finishedAt: 1789010200 }), false)
    assert.equal(runFinishing({ state, finalized: true }), false)
  }
  for (const state of ['INTENT_PERSISTED', 'STARTING', 'PREPARING', 'RUNNING']) assert.equal(runActive(state), true)
  for (const state of ['SUBMISSION_UNKNOWN', 'RECOVERY_REQUIRED', 'WORKER_UNAVAILABLE']) {
    assert.equal(runActive(state), false)
    assert.equal(runFinishing({ state }), false)
    assert.equal(runStatus(state).type, 'warning')
  }
})
test('overlapping event polling is ordered, deduplicated and bounded without mixing node directions', () => {
  const events = Array.from({ length: 9 }, (_, index) => ({ seq: index + 1, type: 'row', node: index % 2 ? '输出' : '输入', direction: index % 2 ? 'read' : 'written', fields: { name: index === 8 ? '张三' : null }, fieldsMeta: [{ name: 'name', type: 'String', length: -1, precision: -1, origin: '输入' }] }))
  const result = mergeEvents(events.slice(0, 6), events.slice(4), 5)
  assert.deepEqual(result.map(row => row.seq), [5, 6, 7, 8, 9])
  assert.deepEqual(nodeRows(result, '输入', 'written').map(row => row.seq), [5, 7, 9])
  assert.equal(rowColumns(result)[0].origin, '输入')
  assert.equal(result.at(-1).fields.name, '张三')
})

test('new engine states and unavailable workers retain a manual review barrier', () => {
  for (const state of ['NEW_ENGINE_STAGE', 'RECOVERY_REQUIRED', 'SUBMISSION_UNKNOWN', undefined]) assert.equal(runNeedsReview({ state }), true)
  assert.equal(runNeedsReview({ state: 'SUCCEEDED', finalized: true, workerStateAvailable: false }), true)
  assert.equal(runNeedsReview({ state: 'RUNNING', workerStateAvailable: true }), false)
  assert.equal(runNeedsReview({ state: 'PREPARATION_FAILED', workerStateAvailable: false }), false)
})

test('job badges follow original entry results and copies without promoting skipped entries', () => {
  const rows = [
    {type:'job-entry',node:'开始',copy:0,execution:1,phase:'AFTER',resultBoolean:true,errors:0},
    {type:'job-entry',node:'转换',copy:0,execution:2,phase:'BEFORE'},
    {type:'job-entry',node:'转换',copy:0,execution:2,phase:'AFTER',resultBoolean:false,originalResultBoolean:true,errors:1},
    {type:'job-entry',node:'转换',copy:1,execution:3,phase:'BEFORE'}
  ]
  assert.deepEqual(jobNodeStates(rows, 'RUNNING').map(row=>row.status), ['SUCCEEDED','FAILED','RUNNING'])
  assert.equal(jobNodeStates(rows, 'FAILED').length, 3)
  assert.equal(jobNodeStates(rows, 'FAILED').some(row=>row.node==='FTP'), false)
  assert.equal(jobNodeStates(rows, 'STOPPED')[2].status, 'STOPPED')
})
