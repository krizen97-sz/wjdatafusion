import test from 'node:test'
import assert from 'node:assert/strict'
import { mountOriginalTasks, withOriginalListDefaults } from '../../../../../data-governance/hdi-native/host/native-task-host.mjs'

// Contract proof for our thin host only. The original vendor bundles are not simulated as execution evidence.
async function withHost(options, run) {
  const previousWindow = globalThis.window, previousStorage = globalThis.localStorage
  const listeners = new Map(), instances = []
  const TaskList = { name: 'OriginalTaskList', methods: { handleCachePreSearch() { this.popoverSearchForm = { ...this.searchForm } } } }, DagMain = { name: 'OriginalDagMain' }
  const store = { state: { currentWorkspaceId: options.workspace ?? '11' }, values: {}, subscribeAction() { return () => {} }, commit(key, value) { this.values[key] = value } }
  class Vue {
    constructor(config) {
      Object.assign(this, config.data())
      this.$children = [{ _isMounted: options.childMounted !== false }]
      this.$el = { isConnected: true, nodeType: options.elementType ?? 1 }
      for (const [name, method] of Object.entries(config.methods)) this[name] = method.bind(this)
      this.render = () => { this.vnode = config.render.call(this, (component, props) => ({ component, ...props })) }
      instances.push(this)
    }
    $mount() { this._isMounted = true; this.render(); return this }
    async $nextTick() { this.render() }
    $destroy() { this.destroyed = true }
    static use() {}
    static observable(value) { return value }
    static delete(target, key) { delete target[key] }
    static set(target, key, value) { target[key] = value }
  }
  const components = {
    AsyncTaskList: async () => { Vue.prototype.$store = store; return TaskList },
    AsyncDagMain: async () => DagMain,
    AsyncDagMainForView() {}, AsyncAddBasicInfo() {}
  }
  const http = { interceptors: { request: { use() { return 1 }, eject() {} }, response: { use() { return 1 }, eject() {} } } }
  const chunks = []
  chunks.push = ([, , register]) => register(id => { assert.equal(id, 43620); return { A: http } })
  globalThis.window = {
    location: { origin: 'https://platform.example' }, webpackChunkdetl_web: chunks,
    GLOBAL_HUI: {},
    detlModule: {
      async init(scope) {
        scope.vue = { '2.7.16': { get: async () => () => Vue } }
        scope['vue-i18n'] = { '7.8.1': { get: async () => () => class I18n { t(value) { return value } } } }
      },
      async get(name) { assert.equal(name, './DFPModules'); return () => components }
    },
    addEventListener(type, fn) { listeners.set(type, fn) },
    removeEventListener(type, fn) { if (listeners.get(type) === fn) listeners.delete(type) }
  }
  globalThis.localStorage = { setItem() {} }
  try { await run({ store, instances, listeners, TaskList, DagMain }) }
  finally { globalThis.window = previousWindow; globalThis.localStorage = previousStorage }
}
const context = { user: { id: '7' }, workspaceId: '11', projectCode: 'platform-project', projectList: [], router: {} }

test('host mounts resolved component instances and keeps original forms and task props', async () => {
  await withHost({}, async ({ store, instances, listeners, TaskList, DagMain }) => {
    const opened = []
    const host = await mountOriginalTasks({ ...context, onTaskOpened: value => opened.push(value) }, '#target')
    const vm = instances[0]
    assert.equal(vm.vnode.component.extends, TaskList)
    assert.equal(store.values.SET_USER, context.user)
    await vm.openTask({ id: '42' }, true)
    assert.equal(vm.vnode.component, DagMain)
    assert.equal(vm.vnode.props.flowId, '42')
    assert.equal(vm.vnode.props.isView, true)
    assert.deepEqual(opened, [{ id: '42', readonly: true, isNewCreate: false }])
    await host.backToTaskList()
    assert.equal(vm.vnode.component.extends, TaskList)
    assert.equal(listeners.has('pagehide'), true)
    host.destroy()
    host.destroy()
    assert.equal(vm.destroyed, true)
    assert.equal(listeners.size, 0)
    assert.equal('checkLicenseAuthorization' in window, false)
  })
})

test('workspace mismatch or an unmounted child cannot report ready', async () => {
  await withHost({ workspace: '12' }, async ({ instances }) => {
    await assert.rejects(mountOriginalTasks(context, '#target'), /工作区/)
    assert.equal(instances.length, 0)
  })
  await withHost({ childMounted: false }, async ({ instances }) => {
    await assert.rejects(mountOriginalTasks(context, '#target'), /挂载/)
    assert.equal(instances[0].destroyed, true)
  })
  await withHost({ elementType: 8 }, async () => {
    await assert.rejects(mountOriginalTasks(context, '#target'), /挂载/)
  })
})

test('successful task windows keep the list; blocked windows fall back without losing task identity', async () => {
  await withHost({}, async ({ instances, TaskList, DagMain }) => {
    let popupOpened = true
    const windows = []
    const host = await mountOriginalTasks({ ...context, readOnly: true,
      openTaskInWindow: value => { windows.push(value); return popupOpened } }, '#target')
    const vm = instances[0]
    await vm.openTask({ id: '42' }, false)
    assert.equal(vm.vnode.component.extends, TaskList)
    assert.equal(windows[0].readonly, true)
    popupOpened = false
    await vm.openTask({ id: '43' }, false, true)
    assert.equal(vm.vnode.component, DagMain)
    assert.equal(vm.vnode.props.flowId, '43')
    assert.equal(vm.vnode.props.isView, true)
    await assert.rejects(vm.openTask({ id: '9223372036854775808' }, false), /标识/)
    host.destroy()
  })
})


test('host initializes the original cached filter before any multiple select renders', () => {
  const original = { name: 'TaskList', methods: { handleCachePreSearch() { this.popoverSearchForm = { ...this.searchForm } } }, render() {} }
  const wrapper = withOriginalListDefaults(original)
  const instance = { searchForm: { flowStatusList: [], folderId: -1 }, popoverSearchForm: {} }
  wrapper.beforeMount.call(instance)
  assert.deepEqual(instance.popoverSearchForm, { flowStatusList: [], folderId: -1 })
  assert.equal(wrapper.extends, original)
  assert.equal(wrapper.render, undefined)
  assert.equal(original.beforeMount, undefined)
})
