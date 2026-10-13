import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { compileScript, parse } from '@vue/compiler-sfc'
import { computed, effectScope, nextTick, ref } from 'vue'
import * as rules from '../local/localFlowRules.js'
import * as graphRules from '../graphRules.js'
import * as workspaceRules from '../workspaceRules.js'
const { descriptor } = parse(readFileSync(new URL('../local/LocalFlowWorkbench.vue', import.meta.url), 'utf8'))
const compiled = compileScript(descriptor, { id: 'local-flow-contract-test', genDefaultAs: 'component' }).content.replace(/^import .*$/gm, '')
const detail = (id = 'flow-a', revision = 1) => ({ id, revision, name: 'test flow', graph: rules.localStarterGraph() })
const deferred = () => { let resolve, reject; const promise = new Promise((yes, no) => { resolve = yes; reject = no }); return { promise, resolve, reject } }
function setup(t, methods = {}) {
  const calls = [], successes = [], warnings = [], callbacks = [], storage = new Map(), scope = effectScope()
  const api = new Proxy({}, { get: (_, name) => (...args) => { calls.push({ name, args }); assert.equal(typeof methods[name], 'function', `Unexpected API: ${name}`); return methods[name](...args) } })
  const bindings = { computed, nextTick, ref, ...rules, ...graphRules, ...workspaceRules, api, saveAs() {},
    getCurrentInstance: () => ({ proxy: { $auth: { hasPermi: () => true }, $modal: { confirm: async () => {}, msgWarning: text => warnings.push(text), msgSuccess: text => successes.push(text) } } }),
    useRoute: () => ({ query: {} }), useRouter: () => ({ replace: async () => {} }),
    onMounted() {}, onBeforeUnmount: callback => callbacks.push(callback), window: { removeEventListener() {} },
    localStorage: { getItem: key => storage.get(key) || null, setItem: (key, value) => storage.set(key, value), removeItem: key => storage.delete(key) },
    FlowDiagram: {}, LocalFlowNodeConfig: {}, LocalFlowSchedules: {}
  }
  const component = new Function(...Object.keys(bindings), `${compiled}\nreturn component`)(...Object.values(bindings))
  let view; scope.run(() => { view = component.setup({}, { expose() {} }) })
  t.after(() => { callbacks.forEach(callback => callback()); scope.stop() })
  view.setDefinition(detail()); view.status.value = { enabled: true, executionAvailable: true }
  return { view, calls, successes, warnings, storage }
}

test('starter creation sends a native graph without implicitly executing it', async t => {
  const { view, calls, successes } = setup(t, { createLocalDefinition: async payload => ({ data: { ...detail('created'), graph: payload.graph } }) })
  await view.openCreate(true); await view.create()
  assert.equal(view.definition.value.id, 'created')
  assert.deepEqual(calls.map(item => item.name), ['createLocalDefinition'])
  assert.equal(calls[0].args[0].graph.nodes.length, 5)
  assert.equal(successes.length, 1)
  assert.equal(view.dirty.value, false)
})

test('execution saves the edited graph then submits the returned revision once', async t => {
  let latest
  const { view, calls, storage } = setup(t, {
    saveLocalDefinition: async (id, payload) => { latest = { id, name: payload.name, revision: 2, graph: payload.graph }; return { data: latest } },
    localRunByRequest: async () => ({ data: null }), localDefinition: async () => ({ data: latest }),
    runLocalDefinition: async (id, payload) => ({ data: { id: 'run-a', definitionId: id, revision: payload.revision, state: 'SUCCEEDED', steps: [], outputs: [] } })
  })
  view.graph.value.nodes[2].config.value = '上海'; view.changed()
  await view.execute('run')
  assert.equal(calls.find(item => item.name === 'saveLocalDefinition').args[1].graph.nodes[2].config.value, '上海')
  assert.equal(calls.find(item => item.name === 'runLocalDefinition').args[1].revision, 2)
  assert.equal(view.run.value.state, 'SUCCEEDED'); assert.equal(storage.size, 0)
})

test('an uncertain submission retains its request and recovers read-only without replaying a known run', async t => {
  let found = null
  const { view, calls, storage } = setup(t, {
    localRunByRequest: async () => ({ data: found }), localDefinition: async () => ({ data: { revision: 1 } }),
    runLocalDefinition: async () => { throw new Error('connection lost') }
  })
  await view.execute('run')
  const requestId = view.pending.value.requestId
  assert.equal(storage.size, 1); assert.equal(view.editable.value, false)
  await view.execute('run')
  found = { id: 'accepted-before-timeout', revision: 1, requestId, state: 'SUCCEEDED', steps: [], outputs: [] }
  await view.submitPending()
  assert.equal(view.run.value.id, 'accepted-before-timeout')
  assert.equal(calls.filter(item => item.name === 'runLocalDefinition').length, 1)
  assert.equal(view.pending.value, null); assert.equal(storage.size, 0)
})

test('a stale pending revision is rechecked and released without starting a replacement run', async t => {
  const { view, calls, storage } = setup(t, {
    localRunByRequest: async () => ({ data: null }), localDefinition: async () => ({ data: { revision: 2 } })
  })
  await view.execute('run')
  assert.equal(view.pending.value, null); assert.equal(storage.size, 0)
  assert.match(view.error.value, /原提交版本已过期/)
  assert.equal(calls.filter(item => item.name === 'localRunByRequest').length, 2)
  assert.equal(calls.filter(item => item.name === 'runLocalDefinition').length, 0)
})

test('active run cannot be hidden by historical selection or allow edits', async t => {
  const { view, calls } = setup(t)
  view.run.value = { id: 'active', revision: 1, state: 'RUNNING' }
  const original = view.graph.value.nodes[0].name
  view.updateNode({ ...view.graph.value.nodes[0], name: 'blocked' })
  await view.viewRun({ id: 'historical' })
  assert.equal(view.graph.value.nodes[0].name, original)
  assert.equal(view.run.value.id, 'active'); assert.equal(calls.length, 0)
})

test('late polling cannot replace a different task run', async t => {
  const response = deferred()
  const { view } = setup(t, { localRun: () => response.promise })
  view.run.value = { id: 'old-run', revision: 1, state: 'RUNNING' }
  const polling = view.poll()
  view.setDefinition(detail('flow-b')); view.run.value = { id: 'new-run', revision: 1, state: 'SUCCEEDED' }
  response.resolve({ data: { id: 'old-run', state: 'FAILED' } }); await polling
  assert.equal(view.definition.value.id, 'flow-b'); assert.equal(view.run.value.id, 'new-run')
})

test('a known active history record retains its execution lock when detail recovery fails', async t => {
  const { view } = setup(t, {
    localDefinition: async () => ({ data: detail('flow-b') }),
    localRunHistory: async () => ({ data: [{ id: 'known-active', definitionId: 'flow-b', revision: 1, state: 'RUNNING' }] }),
    localRun: async () => { throw new Error('temporarily unavailable') }
  })
  await view.openDefinition('flow-b')
  assert.equal(view.run.value.id, 'known-active')
  assert.equal(view.active.value, true)
  assert.equal(view.editable.value, false)
})
