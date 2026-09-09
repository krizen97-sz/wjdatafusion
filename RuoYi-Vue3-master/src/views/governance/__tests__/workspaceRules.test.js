import test from 'node:test'
import assert from 'node:assert/strict'
import { availabilityState, displayJson, isRunActive, optionalCount, parameterDefaults, parseTestRequest, runState, safeDesignerPath, SAMPLE_LIMIT } from '../workspaceRules.js'

test('engine links only accept the same-origin NiFi proxy and reject path escapes', () => {
  assert.equal(safeDesignerPath('/nifi/#/process-groups/group-1'), '/nifi/#/process-groups/group-1')
  for (const path of ['https://outside.invalid/nifi/', '//outside.invalid/nifi/', '/nifi/../../admin', '/nifi/%2e%2e/admin', '/nifi\\@outside.invalid', '/nifi/%5cadmin', 'javascript:alert(1)', '/nifievil/', '/nifi/\nadmin']) assert.equal(safeDesignerPath(path), '')
})

test('sample validation preserves the supplied JSON and rejects invalid or oversized requests', () => {
  assert.deepEqual(parseTestRequest('{"message":"示例"}', '{"requiredValue":""}'), { inputJson: '{"message":"示例"}', parameters: { requiredValue: '' } })
  for (const text of ['', '{broken']) assert.throws(() => parseTestRequest(text, '{}'))
  for (const parameters of ['null', '[]', 'true', '{broken']) assert.throws(() => parseTestRequest('{}', parameters))
  assert.throws(() => parseTestRequest(JSON.stringify('中'.repeat(SAMPLE_LIMIT / 2)), '{}'), /256 KB/)
})

test('unknown states and failed cleanup never become a successful or active run', () => {
  assert.equal(isRunActive({ status: 'QUEUED' }), true)
  assert.equal(isRunActive({ status: 'RUNNING' }), true)
  for (const status of ['FAILED', 'CANCELLED', 'CLEANUP_REQUIRED', 'UNSUPPORTED', 'new-server-status']) assert.equal(isRunActive({ status }), false)
  assert.equal(runState('SUCCEEDED', false).type, 'warning')
  assert.equal(runState('CLEANUP_REQUIRED').type, 'danger')
  assert.equal(availabilityState('ADAPTER_REQUIRED').usable, undefined)
  assert.equal(availabilityState('unknown').usable, false)
})

test('display helpers preserve measured zero, empty defaults and limit large output', () => {
  assert.equal(optionalCount(0), '0')
  assert.equal(optionalCount(null), '未返回')
  assert.deepEqual(parameterDefaults({ parametersSchema: { properties: { requiredValue: { default: '' }, limit: { default: 0 }, unset: {} } } }), { requiredValue: '', limit: 0 })
  assert.match(displayJson('long output', 4), /展示已截断/)
  assert.equal(displayJson(undefined), '')
})
