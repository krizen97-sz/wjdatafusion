import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { localDefinitionPayload } from '../../../api/governance/localPayload.js'
import { localActive, localRunState } from '../local/localFlowRules.js'

test('base64 graph transport preserves Chinese, markup, quoted JSON and control delimiters exactly', () => {
  const graph = { nodes: [{ id: 'input', name: '中文输入', type: 'CSV_INPUT', config: { content: 'name,payload\r\n张三,"<tag>原始&数据</tag>"\r\n', delimiter: '\t' } }, { id: 'json', config: { content: '[{"text":"<script>data only</script>","path":"a\\\\b"}]' } }], connections: [] }
  const payload = localDefinitionPayload({ name: '流程', revision: 7, graph })
  assert.equal(payload.graph, undefined)
  assert.match(payload.graphBase64, /^[A-Za-z0-9+/]*={0,2}$/)
  const restored = JSON.parse(new TextDecoder('utf-8', { fatal: true }).decode(Uint8Array.from(atob(payload.graphBase64), character => character.charCodeAt(0))))
  assert.deepEqual(restored, graph)
  assert.equal(payload.revision, 7)
  assert.throws(() => localDefinitionPayload({ name: 'invalid', graph: null }), /流程定义/)
})

test('both definition HTTP writes transform the graph while run and schedule bodies are unchanged', () => {
  const source = readFileSync(new URL('../../../api/governance/local.js', import.meta.url), 'utf8').replace(/^import .*$/gm, '').replace(/export const /g, 'const ')
  const calls = []
  const api = new Function('request', 'localDefinitionPayload', `${source}\nreturn { createLocalDefinition, saveLocalDefinition, runLocalDefinition, createLocalSchedule }`)(value => { calls.push(value); return value }, localDefinitionPayload)
  const graph = { nodes: [], connections: [] }
  api.createLocalDefinition({ name: 'new', graph }); api.saveLocalDefinition('id', { name: 'saved', revision: 2, graph })
  const run = { revision: 2, mode: 'preview', requestId: 'fixed' }, schedule = { name: 'daily', cron: '0 0 8 * * ?' }
  api.runLocalDefinition('id', run); api.createLocalSchedule('id', schedule)
  for (const call of calls.slice(0, 2)) { assert.ok(call.data.graphBase64); assert.equal(Object.hasOwn(call.data, 'graph'), false) }
  assert.deepEqual(calls[2].data, run); assert.deepEqual(calls[3].data, schedule)
})

test('STOPPING remains an active execution barrier until the server confirms a terminal state', () => {
  assert.equal(localActive({ state: 'STOPPING' }), true)
  assert.equal(localRunState('STOPPING')[0], '停止中')
  assert.equal(localActive({ state: 'STOPPED' }), false)
})
