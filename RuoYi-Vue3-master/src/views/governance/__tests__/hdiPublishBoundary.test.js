import test from 'node:test'
import assert from 'node:assert/strict'
import { bindOriginalPublishBoundary } from '../../../../../data-governance/hdi-native/host/native-task-host.mjs'

const origin = 'https://platform.example'
const context = { user: { id: '7' }, workspaceId: '11' }
function fixture() {
  let actionHooks
  const requestHooks = new Map(), responseHooks = new Map()
  const manager = hooks => ({ use(fn) { const id = hooks.size + 1; hooks.set(id, fn); return id }, eject(id) { hooks.delete(id) } })
  const client = { interceptors: { request: manager(requestHooks), response: manager(responseHooks) } }
  const store = { state: { flowData: { flowVo: { id: 42, userId: 7, groupId: 11, _platformRevision: 3 } } },
    subscribeAction(hooks) { actionHooks = hooks; return () => { actionHooks = null } } }
  const destroy = bindOriginalPublishBoundary(store, client, context, origin)
  const action = (id = 42, revision = 3) => ({ type: 'pushFlowTask', payload: { flowVo: { id, _platformRevision: revision } } })
  return { store, destroy, action, requestHooks, responseHooks,
    before(value) { actionHooks.before(value) }, after(value) { actionHooks.after(value) }, error(value) { actionHooks.error(value) },
    request(id = 42) {
      // Original Axios executes its registered request interceptors in a promise chain.
      return Promise.resolve({ method: 'get', url: `/detl/flow/getStartConfig?flowId=${id}` }).then(requestHooks.values().next().value)
    },
    response(config, revision) { return responseHooks.values().next().value({ code: '0', config, data: { schedule: { _platformRevision: revision } } }) }
  }
}

test('a run-config request begun before same-tick original publish waits for its actual visual readback', async () => {
  const f = fixture(), a = f.action(); let sent = false
  const pending = f.request().then(value => { sent = true; return value })
  f.before(a)
  await Promise.resolve(); await Promise.resolve(); await Promise.resolve()
  assert.equal(sent, false)
  f.store.state.flowData.flowVo._platformRevision = 4; f.after(a)
  const config = await pending
  assert.deepEqual(config._rynewPublishBoundary, { taskId: '42', revision: '4' })
  assert.equal(f.response(config, 4).data.schedule._platformRevision, 4)
  f.destroy()
})

test('publish failure rejects this window settings read without granting a new revision', async () => {
  const f = fixture(), a = f.action(); f.before(a)
  const result = assert.rejects(f.request(), /版本已变化或发布未完成/)
  f.error(a); await result
  assert.equal(f.store.state.flowData.flowVo._platformRevision, 3)
  f.destroy()
})

test('another window or another task cannot satisfy this publish boundary', async () => {
  const first = fixture(), second = fixture(), a = first.action()
  first.before(a)
  const pending = assert.rejects(first.request(), /版本已变化/)
  const independent = await second.request()
  assert.equal(independent._rynewPublishBoundary, undefined)
  // The actual GET visual returned a later revision written by a different window.
  first.store.state.flowData.flowVo._platformRevision = 5; first.after(a)
  await pending
  first.destroy(); second.destroy()
})

test('response revision is checked again if another window edits after publish readback', async () => {
  const f = fixture(), a = f.action(); f.before(a)
  const pending = f.request()
  await Promise.resolve(); f.store.state.flowData.flowVo._platformRevision = 4; f.after(a)
  const config = await pending
  assert.throws(() => f.response(config, 5), /版本已变化/)
  f.destroy()
})

test('a pure read and a different task are not bound to an unrelated publish', async () => {
  const f = fixture()
  assert.equal((await f.request())._rynewPublishBoundary, undefined)
  const a = f.action(); f.before(a)
  assert.equal((await f.request(43))._rynewPublishBoundary, undefined)
  f.error(a); f.destroy()
})

test('changed workspace/user and destroyed frames reject waiting requests and remove subscriptions', async () => {
  for (const field of ['userId', 'groupId']) {
    const f = fixture(), a = f.action(); f.before(a)
    const pending = assert.rejects(f.request(), /版本已变化/)
    f.store.state.flowData.flowVo._platformRevision = 4; f.store.state.flowData.flowVo[field] = 999; f.after(a)
    await pending; f.destroy()
  }
  const f = fixture(), a = f.action(); f.before(a)
  const pending = assert.rejects(f.request(), /版本已变化/)
  await Promise.resolve(); await Promise.resolve(); f.destroy(); f.destroy(); await pending
  assert.equal(f.requestHooks.size, 0); assert.equal(f.responseHooks.size, 0)
})


test('an earlier completed toolbar publish cannot bind a later independent settings read', async () => {
  const f = fixture(), a = f.action(); f.before(a)
  f.store.state.flowData.flowVo._platformRevision = 4; f.after(a)
  await new Promise(resolve => setTimeout(resolve, 1))
  f.store.state.flowData.flowVo._platformRevision = 5
  const config = await f.request()
  assert.equal(config._rynewPublishBoundary, undefined)
  assert.equal(f.response(config, 5).data.schedule._platformRevision, 5)
  f.destroy()
})
