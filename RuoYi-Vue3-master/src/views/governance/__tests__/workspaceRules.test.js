import test from 'node:test'
import assert from 'node:assert/strict'
import { availabilityState, createFlowDraftStore, displayJson, isRunActive, optionalCount, parseTestRequest, runState, safeDesignerPath, SAMPLE_LIMIT } from '../workspaceRules.js'

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

test('sample roots and array bounds match the object or zero-to-one-hundred item contract', () => {
  assert.deepEqual(parseTestRequest('[]', '').parameters, {})
  assert.deepEqual(parseTestRequest('{}', '  \n ').parameters, {})
  assert.equal(JSON.parse(parseTestRequest(JSON.stringify(Array.from({ length: 100 }, () => ({}))), '{}').inputJson).length, 100)
  assert.throws(() => parseTestRequest(JSON.stringify(Array.from({ length: 101 }, () => ({}))), '{}'), /100/)
  for (const input of ['null', 'true', '12', '"sample"']) assert.throws(() => parseTestRequest(input, '{}'), /JSON 对象/)
})

test('unknown states and failed cleanup never become a successful or active run', () => {
  assert.equal(isRunActive({ status: 'QUEUED' }), true)
  assert.equal(isRunActive({ status: 'RUNNING' }), true)
  for (const status of ['FAILED', 'CANCELLED', 'CLEANUP_REQUIRED', 'UNSUPPORTED', 'EMPTY', 'new-server-status']) assert.equal(isRunActive({ status }), false)
  assert.equal(runState('SUCCEEDED', false).type, 'warning')
  assert.equal(runState('CLEANUP_REQUIRED').type, 'danger')
  assert.equal(availabilityState('ADAPTER_REQUIRED').usable, undefined)
  assert.equal(availabilityState('unknown').usable, false)
  assert.equal(availabilityState('ENGINE_REQUIRED').usable, false)
  assert.equal(availabilityState('ENGINE_REQUIRED').label, '引擎组件未就绪')
})

test('only the reported EMPTY state confirms an empty batch and cleanup remains independent', () => {
  assert.equal(runState('EMPTY', true).type, 'success')
  assert.match(runState('EMPTY', true).label, /空批次/)
  assert.equal(runState('EMPTY', false).type, 'warning')
  assert.match(runState('EMPTY', false).label, /清理待确认/)
  assert.doesNotMatch(runState(undefined).label, /空批次/)
  assert.doesNotMatch(runState('SUCCEEDED', true).label, /空批次/)
})

test('template examples seed drafts while parameter defaults never override the canvas implicitly', () => {
  const drafts = createFlowDraftStore()
  const template = { parametersSchema: { inputExample: '[{"message":"示例","picture":""}]', properties: { jsonPath: { default: '$.message' }, requiredValue: { default: '' } } } }
  const draft = drafts.read('writer', template)
  assert.equal(draft.inputText, template.parametersSchema.inputExample)
  assert.equal(draft.parameterText, '{}')
  assert.deepEqual(parseTestRequest(draft.inputText, draft.parameterText).parameters, {})
})

test('switching flows preserves each draft and returned copies cannot change saved drafts', () => {
  const drafts = createFlowDraftStore()
  const first = { inputText: '{"message":"edited"}', parameterText: '{"jsonPath":"$.other"}' }
  drafts.write('first', first)
  drafts.write('second', { inputText: '[]', parameterText: '' })
  assert.deepEqual(drafts.read('first'), first)
  assert.deepEqual(drafts.read('second'), { inputText: '[]', parameterText: '' })
  const copy = drafts.read('first')
  copy.inputText = 'changed without save'
  assert.deepEqual(drafts.read('first'), first)
})

test('display helpers preserve measured zero and describe output only as bounded preview', () => {
  assert.equal(optionalCount(0), '0')
  assert.equal(optionalCount(null), '未返回')
  assert.match(displayJson('long output', 4), /预览已截断/)
  assert.doesNotMatch(displayJson('long output', 4), /下载|运行产物/)
  assert.equal(displayJson(undefined), '')
})
