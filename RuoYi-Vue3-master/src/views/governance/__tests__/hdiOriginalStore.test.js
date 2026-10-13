import test from 'node:test'
import assert from 'node:assert/strict'
import fs from 'node:fs'
import path from 'node:path'
import vm from 'node:vm'
import { createHash } from 'node:crypto'
import { bindOriginalSaveQueue, bindOriginalPublishBoundary } from '../../../../../data-governance/hdi-native/host/native-task-host.mjs'

// Run explicitly with the verified private bundle. Original Vue, Vuex, store actions and mutations execute unchanged.
// Only HTTP, display helpers and unrelated integrations are controlled; this is not an engine execution test.
const bundle = process.env.HDI_CORE_BUNDLE_DIR
const options = { skip: !bundle }
const origin = 'https://platform.example'
const host = { user: { id: '7' }, workspaceId: '11', projectCode: 'rynew-user-7' }
const copy = value => JSON.parse(JSON.stringify(value))
const turn = () => new Promise(resolve => setImmediate(resolve))
const defer = () => { let resolve; const promise = new Promise(yes => { resolve = yes }); return { promise, resolve } }
function fixture() {
  const manifest = JSON.parse(fs.readFileSync(path.join(bundle, 'bundle-manifest.json'), 'utf8'))
  const source = relative => {
    const item = manifest.files.find(row => row.path === relative)
    assert.ok(item, `Missing vendor manifest entry ${relative}`)
    const bytes = fs.readFileSync(path.join(bundle, relative))
    assert.equal(createHash('sha256').update(bytes).digest('hex'), item.sha256)
    return bytes.toString('utf8')
  }
  const context = vm.createContext({ self: { webpackChunkdetl_web: [] }, console, setTimeout, clearTimeout, Promise, Map, Set })
  for (const file of ['chunk-vendors.10072682.js', 'chunk-common.60591115.js']) vm.runInContext(source(`public/static/js/${file}`), context)
  const modules = Object.assign({}, ...context.self.webpackChunkdetl_web.map(chunk => chunk[1])), cache = {}
  const requireVendor = id => {
    if (cache[id]) return cache[id].exports
    assert.ok(modules[id], `Missing original module ${id}`)
    const module = { exports: {} }; cache[id] = module; modules[id](module, module.exports, requireVendor); return module.exports
  }
  requireVendor.d = (target, descriptors) => Object.entries(descriptors).forEach(([key, get]) => Object.defineProperty(target, key, { get, enumerable: true }))
  requireVendor.r = value => Object.defineProperty(value, '__esModule', { value: true })
  requireVendor.n = value => { const get = () => value.__esModule ? value.default : value; requireVendor.d(get, { a: get }); return get }
  requireVendor.o = (value, key) => Object.hasOwn(value, key); requireVendor.g = context
  const vueModule = requireVendor(99340), Vue = vueModule.default || vueModule, vuex = requireVendor(53578)
  assert.equal(Vue.version, '2.7.16'); assert.equal(vuex.Ay.version, '3.6.2')
  const hooks = new Map(), responses = new Map(), gates = [], requests = [], notices = []
  const manager = values => ({ use(fn) { const id = values.size + 1; values.set(id, fn); return id }, eject(id) { values.delete(id) } })
  const request = async config => { for (const fn of [...hooks.values()].reverse()) config = await fn(config); return config }
  let revision = 1, stamp = 100, foreign = false, ordinaryReads = 0
  let persisted = { flowVo: { id: 42, userId: 7, groupId: 11, projectCode: host.projectCode, _platformRevision: revision, flowStatus: 0, folderId: -1, scheduleVo: {} },
    nodes: { '101': { id: 101, adapterType: 'input', adapterName: 'dataGeneratorInput', nodeName: 'initial' } }, links: [], streamVoList: [], paramVoList: [], flowVersion: revision }
  const read = () => { const data = copy(persisted); if (foreign) { data.flowVo._platformRevision++; data.flowVersion++ } return { code: '0', data } }
  const client = { interceptors: { request: manager(hooks), response: manager(responses) }, get: async () => read() }
  const persist = operation => async payload => {
    const config = await request({ method: 'post', url: `/detl/flow/saveFlowVisual/${operation}`, timeStamp: ++stamp,
      data: { visualBase64: Buffer.from(encodeURIComponent(JSON.stringify(payload)), 'binary').toString('base64') } })
    requests.push(copy(payload)); const gate = defer(); gates.push(gate); await gate.promise
    if (payload.flowVo._platformRevision !== revision) throw new Error('Conflict')
    persisted = copy(payload); persisted.flowVo._platformRevision = ++revision; persisted.flowVersion = revision
    return { code: '0', data: 42, config }
  }
  const dummy = new Proxy(function() { return dummy }, { get: (_, key) => key === Symbol.toPrimitive ? () => '' : dummy })
  const io = { 54613: vueModule, 53578: vuex, 54147: { Go: copy }, 30930: { Ay: { t: key => key } },
    40510: { pz: async () => { ordinaryReads++; return read() }, dc: persist('save'), s$: persist('push') }, 63971: { IN: async () => ({ data: [] }) } }
  context.window = { localStorage: { getItem: () => null, setItem() {} } }
  const requireStore = id => io[id] || dummy
  requireStore.d = requireVendor.d; requireStore.n = requireVendor.n
  const exports = {}; modules[54098]({}, exports, requireStore); const store = exports.A
  assert.equal(store.state.flowVo, undefined)
  assert.ok(store.state.flowData && store.state.kettle)
  const payload = () => ({ flowVo: store.state.flowData.flowVo, nodes: store.state.flowData.nodesMap, links: store.state.flowData.links, streamVoList: store.state.flowData.streamList, paramVoList: store.state.flowData.paramVoList })
  return { Vue, store, client, gates, requests, notices, payload, request, responses, ordinaryReads: () => ordinaryReads, foreign: () => { foreign = true } }
}

test('original module 54098 in original Vuex flowData saves twice and publishes without replacing newer edits', options, async () => {
  const f = fixture(); await f.store.dispatch('initFlowDataByTaskId', { taskId: 42 })
  const rootDispatch = f.store._modules.root.context.dispatch
  const stopQueue = bindOriginalSaveQueue(f.store, f.client, host, origin, value => f.notices.push(value))
  const stopPublish = bindOriginalPublishBoundary(f.store, f.client, host, origin)
  assert.equal(f.store._modules.root.context.dispatch, rootDispatch)
  assert.equal(f.store._modules.get(['flowData']).context.dispatch, f.store.dispatch)
  f.store.state.flowData.nodesMap['101'].nodeName = 'first'
  const first = f.store.dispatch('saveFlowTask', f.payload())
  f.store.state.flowData.nodesMap['101'].nodeName = 'second'
  const second = f.store.dispatch('saveFlowTask', f.payload())
  f.store.state.flowData.nodesMap['101'].nodeName = 'third'
  const third = f.store.dispatch('pushFlowTask', f.payload())
  let settingsSent = false
  const settings = f.request({ method: 'get', url: '/detl/flow/getStartConfig?flowId=42' }).then(value => { settingsSent = true; return value })
  await turn(); assert.equal(f.requests.length, 1); assert.equal(settingsSent, false)
  f.gates[0].resolve(); await first; await turn(); assert.equal(f.store.state.flowData.nodesMap['101'].nodeName, 'third')
  f.gates[1].resolve(); await second; await turn(); f.gates[2].resolve(); await third
  assert.deepEqual(f.requests.map(row => row.flowVo._platformRevision), [1, 2, 3])
  assert.deepEqual(f.requests.map(row => row.nodes['101'].nodeName), ['first', 'second', 'third'])
  assert.equal(f.store.state.flowData.flowVo._platformRevision, 4); assert.equal(f.ordinaryReads(), 1)
  assert.deepEqual((await settings)._rynewPublishBoundary, { taskId: '42', revision: '4' }); assert.deepEqual(f.notices, [])
  await f.store.dispatch('initFlowDataByTaskId', { taskId: 42, timeStamp: 0 }); assert.equal(f.ordinaryReads(), 2)
  stopPublish(); stopQueue(); assert.equal(f.store._modules.get(['flowData']).context.dispatch, rootDispatch)
})

test('original nested save readback failure rejects the queued action instead of hanging or rebasing a foreign revision', options, async () => {
  const f = fixture(); await f.store.dispatch('initFlowDataByTaskId', { taskId: 42 })
  const destroy = bindOriginalSaveQueue(f.store, f.client, host, origin, value => f.notices.push(value))
  f.foreign()
  const first = assert.rejects(f.store.dispatch('saveFlowTask', f.payload()), /保存未完成/)
  const second = assert.rejects(f.store.dispatch('saveFlowTask', f.payload()), /保存未完成/)
  await turn(); f.gates[0].resolve(); await Promise.all([first, second])
  assert.equal(f.requests.length, 1); assert.equal(f.store.state.flowData.flowVo._platformRevision, 1); assert.equal(f.notices.length, 1)
  destroy()
})
