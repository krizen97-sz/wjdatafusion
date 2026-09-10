import test from 'node:test'
import assert from 'node:assert/strict'
import { NODE_KINDS, nodeKind, nodeSummary, createNodeDraft, nodeDraftPayload, nodeDraftChanged, nodeDraftRowsError } from '../nodeCatalog.js'

function node(key, properties = {}) {
  const kind = NODE_KINDS.find(item => item.key === key)
  return { id: 'node-1', version: 0, name: kind.label, type: kind.type, role: kind.role, properties }
}

test('capture role remains distinct from a transform with the same NiFi type', () => {
  assert.equal(NODE_KINDS.length, 9)
  assert.equal(nodeKind({ type: 'com.hm.governance.nifi.JsonLookupSnapshot', role: 'PROCESSOR' }).key, 'lookup')
  assert.equal(nodeKind(node('record-transform')).category, 'transform')
  assert.equal(nodeKind(node('attributes')).category, 'transform')
  assert.equal(nodeKind(node('capture')).key, 'capture')
  assert.equal(nodeKind({ type: 'external.Plugin', role: 'PROCESSOR' }).key, 'unknown')
  assert.equal(nodeKind(null).key, 'unknown')
})

test('catalog uses the verified Jolt chain enum and explicitly selects text count semantics', () => {
  const jolt = nodeKind(node('jolt'))
  assert.equal(jolt.type, 'org.apache.nifi.processors.jolt.JoltTransformJSON')
  assert.equal(jolt.defaults['Jolt Transform'], 'jolt-transform-chain')
  assert.equal(jolt.defaults['JSON Source'], 'FLOW_FILE')
  assert.equal(nodeKind(node('delimited')).defaults['Count Basis'], 'DATA_RECORDS')
  assert.equal(nodeKind(node('source')).defaults['Custom Text'], undefined)
})

test('opening and saving a name change preserves every unedited null and engine default', () => {
  const original = node('jsonpath', { Destination: 'flowfile-attribute', 'Return Type': 'json', 'Max String Length': '20 MB', 'sample.value': '$.records', 'sample.optional': null, 'Future Default': null })
  const draft = createNodeDraft(original)
  assert.equal(nodeDraftChanged(original, draft), false)
  assert.deepEqual(nodeDraftPayload(original, draft).properties, original.properties)
  draft.name = '重新命名'
  assert.equal(nodeDraftChanged(original, draft), true)
  assert.deepEqual(nodeDraftPayload(original, draft).properties, original.properties)
  assert.equal(original.name, 'JSON 解析')
})

test('renamed and removed dynamic fields disappear from expected properties while fixed settings remain', () => {
  const original = node('jsonpath', { Destination: 'flowfile-attribute', 'Return Type': 'scalar', 'sample.old': '$.old', 'sample.keep': '$.keep' })
  const draft = createNodeDraft(original)
  draft.rows[0].name = 'sample.new'
  draft.rows[0].value = '$.new'
  draft.rows.splice(1, 1)
  assert.deepEqual(nodeDraftPayload(original, draft).properties, { Destination: 'flowfile-attribute', 'Return Type': 'scalar', 'sample.new': '$.new' })
  assert.equal(original.properties['sample.old'], '$.old')
  assert.equal(original.properties['sample.keep'], '$.keep')
})

test('route rename retains strategy and keeps unfinished expressions as draft values', () => {
  const original = node('route', { 'Routing Strategy': 'Route to Property name', accepted: '${sample.value:isEmpty():not()}' })
  const draft = createNodeDraft(original)
  draft.rows[0] = { name: 'next', value: '' }
  assert.equal(nodeDraftRowsError(original, draft.rows), '')
  assert.deepEqual(nodeDraftPayload(original, draft).properties, { 'Routing Strategy': 'Route to Property name', next: '' })
})

test('duplicate names and fixed-property collisions report errors instead of silently losing fields', () => {
  const route = node('route')
  assert.match(nodeDraftRowsError(route, [{ name: 'same' }, { name: 'same' }]), /重复/)
  for (const name of ['Routing Strategy', 'unmatched', 'failure']) assert.match(nodeDraftRowsError(route, [{ name }]), /保留/)
  assert.match(nodeDraftRowsError(node('attributes'), [{ name: 'filename' }]), /sample/)
  assert.match(nodeDraftRowsError(node('jsonpath'), [{ name: '' }]), /名称/)
})

test('discarding reconstructs the original draft and zero counts are preserved in summaries', () => {
  const original = node('delimited', { 'Field Order': 'message,picture', 'Split Limit': '0', 'Include Header': 'false' })
  const draft = createNodeDraft(original)
  draft.properties['Include Header'] = 'true'
  assert.equal(nodeDraftChanged(original, draft), true)
  assert.equal(nodeDraftChanged(original, createNodeDraft(original)), false)
  assert.match(nodeSummary(original), /2 个字段.*不按条数分片/)
  assert.equal(nodeSummary(node('jolt', { 'Jolt Specification': '[invalid' })), '转换规则待检查')
})
