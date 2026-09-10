import test from 'node:test'
import assert from 'node:assert/strict'
import { canExecuteReceipt, canReleaseReceipt, canAcknowledgeReceipt, kafkaState, kafkaExecutionState } from '../kafkaRules.js'
test('a reserved or associated source batch cannot be casually released or run again', () => {
  const received = { status: 'RECEIVED', leaseHeld: true }
  assert.equal(canExecuteReceipt(received), true)
  assert.equal(canReleaseReceipt(received), true)
  for (const status of ['EXECUTION_PLANNED', 'EXECUTION_ATTACHED', 'COMMIT_UNKNOWN', 'COMMITTED']) {
    assert.equal(canExecuteReceipt({ ...received, status }), false)
    assert.equal(canReleaseReceipt({ ...received, status }), false)
  }
})
test('offset acknowledgement requires completed processing or an explicit unknown-commit recheck', () => {
  const receipt = { status: 'EXECUTION_ATTACHED', leaseHeld: true }
  assert.equal(canAcknowledgeReceipt(receipt, { status: 'DELIVERING' }), false)
  assert.equal(canAcknowledgeReceipt(receipt, { status: 'READY_TO_ACK' }), true)
  assert.equal(canAcknowledgeReceipt({ ...receipt, status: 'COMMIT_UNKNOWN' }, null), true)
  assert.equal(canAcknowledgeReceipt({ ...receipt, leaseHeld: false }, { status: 'READY_TO_ACK' }), false)
  assert.equal(kafkaState('unknown').type, 'info')
})
test('acknowledged batches no longer ask for acknowledgement and processing failures remain distinct', () => {
  assert.equal(kafkaExecutionState({ status: 'COMMITTED' }, { status: 'READY_TO_ACK' }).label, '处理完成，位点已确认')
  assert.equal(kafkaExecutionState({ status: 'EXECUTION_ATTACHED' }, { status: 'READY_TO_ACK' }).label, '处理完成，待确认位点')
  assert.equal(kafkaExecutionState({}, { status: 'FAILED' }).label, '处理失败')
  assert.equal(kafkaState('FAILED').label, '取批失败')
})
