import test from 'node:test'
import assert from 'node:assert/strict'
import { mountOriginalTasks, withOriginalListDefaults, registerOriginalToolbar, registerOriginalAdapterDrawer } from '../../../../../data-governance/hdi-native/host/native-task-host.mjs'

// Contract proof for our thin host only. The original vendor bundles are not simulated as execution evidence.
async function withHost(options, run) {
  const previousWindow = globalThis.window, previousStorage = globalThis.localStorage
  const listeners = new Map(), instances = [], localeMessages = []
  const TaskList = { name: 'OriginalTaskList', methods: { handleCachePreSearch() { this.popoverSearchForm = { ...this.searchForm } } } }, DagMain = { name: 'OriginalDagMain' }
  const store = { state: { currentWorkspaceId: options.workspace ?? '11' }, values: {}, dispatch() {}, subscribeAction() { return () => {} }, commit(key, value) { this.values[key] = value } }
  store._modules = { root: { context: { dispatch: store.dispatch } } }
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
    static component() {}
    static extend() { return class { constructor() { this._setupState = { AdaptiveButtonContainer: { __name: 'AdaptiveButtonContainer', setup() {}, render() {} } } } $destroy() {} } }
    static observable(value) { return value }
    static delete(target, key) { delete target[key] }
    static set(target, key, value) { target[key] = value }
  }
  const components = {
    AsyncTaskList: async () => { Vue.prototype.$store = store; return TaskList },
    AsyncDagMain: async () => DagMain,
    AsyncDagMainForView() {}, AsyncAddBasicInfo() {}
  }
  const http = { get() {}, interceptors: { request: { use() { return 1 }, eject() {} }, response: { use() { return 1 }, eject() {} } } }
  const chunks = []
  const runtime = id => {
    if (id === 43620) return { A: http }
    if (id === 4780) return { Drawer: { render() {}, methods: {} } }
    if (id === 54147) return { Go: value => ({ ...value, methods: { ...value.methods } }) }
    if (id === 30930) return { Ay: { t: value => value } }
    assert.equal(id, 39136)
    return { TheDataStandardTransformerProcessPluginInfo: { anchors: [{ component: { __name: 'StandardConversion', setup() {} } }] } }
  }
  runtime.e = async id => assert.equal(id, 136)
  chunks.push = ([, , register]) => register(runtime)
  globalThis.window = {
    location: { origin: 'https://platform.example' }, webpackChunkdetl_web: chunks,
    GLOBAL_HUI: {},
    detlModule: {
      async init(scope) {
        scope.vue = { '2.7.16': { get: async () => () => Vue } }
        scope['vue-i18n'] = { '7.8.1': { get: async () => () => class I18n { t(value) { return value } mergeLocaleMessage(locale, values) { localeMessages.push({ locale, values }) } } } }
      },
      async get(name) { assert.equal(name, './DFPModules'); return () => components }
    },
    addEventListener(type, fn) { listeners.set(type, fn) },
    removeEventListener(type, fn) { if (listeners.get(type) === fn) listeners.delete(type) }
  }
  globalThis.localStorage = { setItem() {} }
  try { await run({ store, instances, listeners, TaskList, DagMain, localeMessages }) }
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


test('host registers the exact original toolbar object and destroys its unmounted setup context', async () => {
  let destroyed = 0, loads = 0, registered
  const toolbar = { __name: 'AdaptiveButtonContainer', setup() {}, render() {} }
  const anchor = { __name: 'StandardConversion', setup() {} }
  const Vue = { extend(value) {
    assert.equal(value, anchor)
    return class { constructor(options) { assert.deepEqual(options.propsData.currentNode.transformFieldVoList, []); this._setupState = { AdaptiveButtonContainer: toolbar } }
      $destroy() { destroyed++ } $mount() { assert.fail('The discovery context must never mount or run business UI') } }
  }, component(name, component) { assert.equal(name, 'adaptive-button-container'); registered = component } }
  const runtime = id => { assert.equal(id, 39136); return { TheDataStandardTransformerProcessPluginInfo: { anchors: [{ component: anchor }] } } }
  runtime.e = async id => { assert.equal(id, 136); loads++ }
  assert.equal(await registerOriginalToolbar(Vue, {}, runtime), toolbar)
  assert.equal(registered, toolbar); assert.equal(destroyed, 1)
  await registerOriginalToolbar(Vue, {}, runtime); assert.equal(loads, 1)
})

test('missing original toolbar export fails instead of registering a pretend update handler', async () => {
  let destroyed = false
  const Vue = { extend() { return class { constructor() { this._setupState = {} } $destroy() { destroyed = true } } }, component() { assert.fail('No substitute component') } }
  const runtime = () => ({ TheDataStandardTransformerProcessPluginInfo: { anchors: [{ component: { __name: 'StandardConversion', setup() {} } }] } })
  runtime.e = async () => {}
  await assert.rejects(registerOriginalToolbar(Vue, {}, runtime), /共享工具栏/)
  assert.equal(destroyed, true)
})


test('node drawer keeps original HUI render and factory resize behavior without starting the original app', () => {
  const previousDocument = globalThis.document
  globalThis.document = { body: { clientHeight: 600, clientWidth: 1000, style: {} } }
  let registered, mounted = 0, title
  const source = { render() {}, mounted() { mounted++ }, methods: { drag() {} } }, originalDrag = source.methods.drag
  const runtime = id => {
    if (id === 4780) return { Drawer: source }
    if (id === 54147) return { Go: value => ({ ...value, methods: { ...value.methods } }) }
    if (id === 30930) return { Ay: { t: key => { assert.equal(key, 'common.close'); return '关闭' } } }
    assert.fail('Must not load the original management bootstrap')
  }
  try {
    registerOriginalAdapterDrawer({ component(name, component) { assert.equal(name, 'TheAdapterDrawer'); registered = component } }, runtime)
    assert.notEqual(registered, source); assert.equal(registered.render, source.render); assert.equal(source.methods.drag, originalDrag)
    registered.mounted.call({ $nextTick(fn) { fn() }, $el: { querySelector() { return { setAttribute(name, value) { title = [name, value] } } } } })
    assert.equal(mounted, 1); assert.deepEqual(title, ['title', '关闭'])
    for (const [direction, axis, amount] of [['l', 'width', 900], ['r', 'width', 100], ['t', 'height', 450], ['b', 'height', 150]]) {
      const sizes = [], events = [], instance = { $refs: { drag: {} }, dragDirection: direction,
        calculateSize(...args) { sizes.push(args) }, $emit(...args) { events.push(args) } }
      registered.methods.drag.call(instance); instance.$refs.drag.onmousedown()
      document.onmousemove({ clientX: 100, clientY: 150 })
      assert.deepEqual(sizes, [[axis, amount]]); assert.equal(events[0][0], 'drag')
      document.onmouseup(); assert.equal(document.body.style.pointerEvents, null)
      assert.equal(document.onmousemove, null)
    }
  } finally { globalThis.document = previousDocument }
})


test('queued test submission uses the exact flat original locale key without claiming execution success', async () => {
  await withHost({}, async ({ localeMessages }) => {
    const host = await mountOriginalTasks(context, '#target')
    assert.deepEqual(localeMessages.at(-1), { locale: 'zh_CN', values: { 'detl.testRunSuccess.name': '试运行已提交' } })
    host.destroy()
  })
})
