import test from 'node:test'
import assert from 'node:assert/strict'
import { bindOriginalSaveQueue } from '../../../../../data-governance/hdi-native/host/native-task-host.mjs'
const origin = 'https://platform.example'
const host = { user: { id: '7' }, workspaceId: '11', projectCode: 'rynew-user-7' }
const copy = value => JSON.parse(JSON.stringify(value))
const deferred = () => { let resolve, reject; const promise = new Promise((yes, no) => { resolve = yes; reject = no }); return { promise, resolve, reject } }
function fixture() {
  const requests = [], gates = [], readers = [], notices = [], hooks = new Map()
  let stamp = 1000, originalReloads = 0, serverRevision = 1, failSave = false, foreignRead = false
  const business = { fieldA: 'initial', fieldB: 'initial', fieldC: 'initial' }
  const store = { state: { flowData: { flowVo: { id: 42, userId: 7, groupId: 11, projectCode: host.projectCode, _platformRevision: 1, flowDisplayName: 'initial' }, nodes: business, links: [], paramVoList: [] } },
    commit(type, value) { if (type === 'setFlowVo') Object.assign(this.state.flowData.flowVo, value); else this.state.flowData[type] = value } }
  const visual = () => ({ flowVo: { ...store.state.flowData.flowVo, flowDisplayName: 'persisted earlier title', _platformRevision: serverRevision, flowStatus: 0 },
    nodes: { ...business, fieldA: 'server earlier value' }, links: [], paramVoList: [], flowVersion: serverRevision })
  const client = { interceptors: { request: { use(fn) { const id = hooks.size + 1; hooks.set(id, fn); return id }, eject(id) { hooks.delete(id) } } },
    async get(url, config) { readers.push({ url, config }); if (foreignRead) serverRevision++; return { code: '0', data: visual() } } }
  const request = async config => { for (const hook of [...hooks.values()].reverse()) config = await hook(config); return config }
  store.dispatch = async function(type, payload) {
    if (type === 'initFlowDataByTaskId') { originalReloads++; const data = visual(); this.state.flowData.flowVo = copy(data.flowVo); this.state.flowData.nodes = copy(data.nodes); return data }
    if (!['saveFlowTask', 'pushFlowTask'].includes(type)) return { original: true }
    const config = await request({ method: 'post', url: `/detl/flow/saveFlowVisual/${type === 'pushFlowTask' ? 'push' : 'save'}`, timeStamp: ++stamp,
      data: { visualBase64: Buffer.from(encodeURIComponent(JSON.stringify(payload)), 'binary').toString('base64') } })
    requests.push(copy(payload))
    const gate = deferred(); gates.push(gate); await gate.promise
    if (failSave) throw new Error('Conflict')
    assert.equal(payload.flowVo._platformRevision, serverRevision)
    serverRevision++
    // Mirror Vuex's retained root action context, rather than looking up the public dispatch dynamically.
    return new Promise(resolve => {
      // Original saveFlowTask has no return/catch for this nested then; the host must surface read failure itself.
      store._modules.get(['flowData']).context.dispatch('initFlowDataByTaskId', { taskId: 42, timeStamp: config.timeStamp }).then(value => resolve(value || { historyId: null }))
    })
  }
  const originalDispatch = store.dispatch
  const flowModule = { state: store.state.flowData, context: { dispatch: originalDispatch } }
  store._modules = { root: { context: { dispatch: originalDispatch } }, get(path) { assert.deepEqual(path, ['flowData']); return flowModule } }
  const destroy = bindOriginalSaveQueue(store, client, host, origin, message => notices.push(message))
  const payload = () => ({ flowVo: store.state.flowData.flowVo, nodes: store.state.flowData.nodes, links: store.state.flowData.links, paramVoList: store.state.flowData.paramVoList })
  return { store, client, requests, gates, readers, notices, request, destroy, payload, originalDispatch,
    originalReloads: () => originalReloads, foreign() { foreignRead = true }, fail() { failSave = true }, serverRevision: () => serverRevision }
}
const turn = () => new Promise(resolve => setImmediate(resolve))

test('three rapid edits are snapshotted, serialized and preserved while earlier reads acknowledge only authority fields', async () => {
  const f = fixture()
  f.store.state.flowData.nodes.fieldA = 'first'; const first = f.store.dispatch('saveFlowTask', f.payload())
  f.store.state.flowData.nodes.fieldB = 'second'; const second = f.store.dispatch('saveFlowTask', f.payload())
  f.store.state.flowData.nodes.fieldC = 'third'; f.store.state.flowData.flowVo.flowDisplayName = 'latest draft'; const third = f.store.dispatch('saveFlowTask', f.payload())
  await turn(); assert.equal(f.requests.length, 1)
  f.gates[0].resolve(); await first; await turn()
  assert.equal(f.requests.length, 2); assert.equal(f.store.state.flowData.nodes.fieldB, 'second'); assert.equal(f.store.state.flowData.nodes.fieldC, 'third')
  assert.equal(f.store.state.flowData.flowVo.flowDisplayName, 'latest draft')
  f.gates[1].resolve(); await second; await turn(); assert.equal(f.requests.length, 3)
  f.gates[2].resolve(); await third
  assert.deepEqual(f.requests.map(value => value.flowVo._platformRevision), [1, 2, 3])
  assert.equal(f.requests[0].nodes.fieldB, 'initial'); assert.equal(f.requests[1].nodes.fieldB, 'second'); assert.equal(f.requests[2].nodes.fieldC, 'third')
  assert.equal(f.store.state.flowData.flowVo._platformRevision, 4); assert.equal(f.originalReloads(), 0)
  f.destroy(); assert.equal(f.store.dispatch, f.originalDispatch); assert.equal(f.store._modules.get(['flowData']).context.dispatch, f.originalDispatch)
})

test('a foreign edit in readback rejects this queue and never upgrades following writes', async () => {
  const f = fixture(); f.foreign()
  const first = assert.rejects(f.store.dispatch('saveFlowTask', f.payload()), /保存未完成/)
  f.store.state.flowData.nodes.fieldB = 'retained'; const second = assert.rejects(f.store.dispatch('saveFlowTask', f.payload()), /保存未完成/)
  await turn(); f.gates[0].resolve(); await Promise.all([first, second])
  assert.equal(f.requests.length, 1); assert.equal(f.notices.length, 1); assert.match(f.notices[0], /保留在画布/); assert.equal(f.store.state.flowData.flowVo._platformRevision, 1); assert.equal(f.store.state.flowData.nodes.fieldB, 'retained')
  f.destroy()
})

test('a failed save stops following work; only an explicit successful original reload permits a new attempt', async () => {
  const f = fixture(); f.fail()
  const first = assert.rejects(f.store.dispatch('saveFlowTask', f.payload()), /Conflict/)
  const second = assert.rejects(f.store.dispatch('saveFlowTask', f.payload()), /保存未完成/)
  await turn(); f.gates[0].resolve(); await Promise.all([first, second])
  await assert.rejects(f.store.dispatch('saveFlowTask', f.payload()), /保存未完成/)
  assert.equal(f.requests.length, 1)
  await f.store.dispatch('initFlowDataByTaskId', { taskId: 42, timeStamp: 0 })
  assert.equal(f.originalReloads(), 1); assert.equal(f.store.state.flowData.nodes.fieldA, 'server earlier value')
  f.destroy()
})

test('ordinary explicit reload uses the original loader and is never mistaken for an own save readback', async () => {
  const f = fixture()
  await f.store.dispatch('initFlowDataByTaskId', { taskId: 42, historyId: null, timeStamp: 0 })
  assert.equal(f.originalReloads(), 1); assert.equal(f.readers.length, 0)
  assert.deepEqual(await f.store.dispatch('unrelated', {}), { original: true })
  f.destroy()
})

test('run settings wait for an already queued publish after an earlier automatic save', async () => {
  const f = fixture()
  const saved = f.store.dispatch('saveFlowTask', f.payload()); const pushed = f.store.dispatch('pushFlowTask', f.payload())
  let passed = false
  const config = f.request({ method: 'get', url: '/detl/flow/getStartConfig?flowId=42' }).then(value => { passed = true; return value })
  await turn(); assert.equal(passed, false); f.gates[0].resolve(); await saved; await turn(); assert.equal(passed, false)
  f.gates[1].resolve(); await pushed
  assert.deepEqual((await config)._rynewPublishBoundary, { taskId: '42', revision: '3' })
  f.destroy()
})
