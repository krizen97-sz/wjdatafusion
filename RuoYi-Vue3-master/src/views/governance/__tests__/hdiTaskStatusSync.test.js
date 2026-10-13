import test from 'node:test'
import assert from 'node:assert/strict'
import { bindOriginalTaskStatus } from '../../../../../data-governance/hdi-native/host/native-task-host.mjs'

const host = { user: { id: '7' }, workspaceId: '11', projectCode: 'rynew-user-7' }
const defer = () => { let resolve, reject; const promise = new Promise((yes, no) => { resolve = yes; reject = no }); return { promise, resolve, reject } }
function fixture() {
  let taskId = '42', pending = false, watch, removed = false
  const timers = new Map(), events = new Map(), warnings = [], changes = [], requests = [], waiters = []
  let serial = 0
  const surface = { document: { hidden: false, addEventListener(type, fn) { events.set(type, fn) }, removeEventListener(type) { events.delete(type) } },
    addEventListener(type, fn) { events.set(type, fn) }, removeEventListener(type) { events.delete(type) }, AbortController }
  const flow = { id: 42, _platformRevision: 8, userId: 7, groupId: 11, projectCode: host.projectCode, flowStatus: 2, folderId: -1,
    flowDisplayName: 'live title', autoArrange: 'horizontal', scheduleVo: { flowIncrStatus: 1, enablePeriod: null } }
  const nodes = { 1: { id: 1, nodeName: 'live node' } }, links = [{ source: 1, target: 2 }], params = [{ paramName: 'p', paramValue: 'live' }]
  const store = { state: { flowData: { flowVo: flow, nodesMap: nodes, links, paramVoList: params } },
    watch(getter, callback) { watch = callback; return () => { removed = true; watch = null } },
    commit(type, value) { assert.equal(type, 'setFlowVo'); this.state.flowData.flowVo = value; watch?.() } }
  const client = { get(url, config) { requests.push({ url, config }); const waiter = defer(); waiters.push(waiter); return waiter.promise } }
  const sync = bindOriginalTaskStatus(store, client, host, { surface, getTaskId: () => taskId, isSavePending: () => pending,
    schedule(fn, delay) { assert.equal(delay, 2000); const id = ++serial; timers.set(id, fn); return id }, cancel(id) { timers.delete(id) },
    onFailure: message => warnings.push(message), onStateChanged: state => changes.push(state) })
  return { store, surface, requests, waiters, timers, events, warnings, changes, nodes, links, params, sync,
    fire() { const [id, callback] = timers.entries().next().value || []; assert.ok(callback, 'Expected scheduled status query'); timers.delete(id); return callback() },
    reply(index, patch = {}) { waiters[index].resolve({ code: '0', data: { ...flow, flowDisplayName: 'old server title', autoArrange: null, ...patch } }) },
    navigate(value) { taskId = value; sync.refresh() }, saving(value) { pending = value }, removed: () => removed }
}

test('an active same-revision task reaches the real terminal state without changing the live diagram or business fields', async () => {
  const f = fixture(), result = f.fire(); assert.equal(f.requests[0].url, '/detl/flow/getFlowById/42')
  f.reply(0, { flowStatus: 3 }); await result
  assert.equal(f.store.state.flowData.flowVo.flowStatus, 3); assert.equal(f.store.state.flowData.flowVo.flowDisplayName, 'live title')
  assert.equal(f.store.state.flowData.flowVo.folderId, -1); assert.equal(f.store.state.flowData.flowVo.autoArrange, 'horizontal')
  assert.equal(f.store.state.flowData.nodesMap, f.nodes); assert.equal(f.store.state.flowData.links, f.links); assert.equal(f.store.state.flowData.paramVoList, f.params)
  assert.deepEqual(f.changes, [3]); assert.equal(f.timers.size, 0); assert.equal(f.warnings.length, 0)
  f.sync.destroy(); assert.equal(f.removed(), true)
})

test('different revision or owner/workspace identities never replace the current state', async () => {
  for (const patch of [{ _platformRevision: 9 }, { userId: 8 }, { groupId: 12 }, { projectCode: 'other' }, { id: 43 }]) {
    const f = fixture(), result = f.fire(); f.reply(0, { flowStatus: 3, ...patch }); await result
    assert.equal(f.store.state.flowData.flowVo.flowStatus, 2); assert.equal(f.store.state.flowData.flowVo._platformRevision, 8)
    assert.equal(f.warnings.length, 1); f.sync.destroy()
  }
})

test('a pending save prevents both starting queries and applying responses that arrived during the save', async () => {
  const f = fixture(); f.saving(true); await f.fire(); assert.equal(f.requests.length, 0)
  f.saving(false); const response = f.fire(); f.saving(true); f.reply(0, { flowStatus: 3 }); await response
  assert.equal(f.store.state.flowData.flowVo.flowStatus, 2); assert.equal(f.warnings.length, 0)
  f.saving(false); const later = f.fire(); f.reply(1, { flowStatus: 3 }); await later
  assert.equal(f.store.state.flowData.flowVo.flowStatus, 3); f.sync.destroy()
})

test('hidden pages pause, navigation invalidates in-flight state, and destruction cancels all work', async () => {
  const f = fixture(), response = f.fire()
  f.surface.document.hidden = true; f.events.get('visibilitychange')(); assert.equal(f.requests[0].config.signal.aborted, true)
  f.reply(0, { flowStatus: 3 }); await response
  assert.equal(f.store.state.flowData.flowVo.flowStatus, 2); assert.equal(f.timers.size, 0)
  f.surface.document.hidden = false; f.events.get('visibilitychange')(); assert.equal(f.timers.size, 1)
  const second = f.fire(); f.navigate(null); f.reply(1, { flowStatus: 3 }); await second
  assert.equal(f.store.state.flowData.flowVo.flowStatus, 2); assert.equal(f.timers.size, 0)
  f.navigate('42'); const third = f.fire(); f.sync.destroy(); f.reply(2, { flowStatus: 3 }); await third
  assert.equal(f.store.state.flowData.flowVo.flowStatus, 2); assert.equal(f.timers.size, 0); assert.equal(f.events.size, 0)
})

test('read failures warn once, retain the last state, and recover only from a later verified response', async () => {
  const f = fixture()
  for (let index = 0; index < 2; index++) { const response = f.fire(); f.waiters[index].reject(new Error('Network Error')); await response }
  assert.equal(f.warnings.length, 1); assert.equal(f.store.state.flowData.flowVo.flowStatus, 2); assert.equal(f.changes.length, 0)
  const recovered = f.fire(); f.reply(2, { flowStatus: 4 }); await recovered
  assert.equal(f.store.state.flowData.flowVo.flowStatus, 4); assert.deepEqual(f.changes, [4]); assert.equal(f.timers.size, 0); f.sync.destroy()
})
