/* Host original DETL exports without modifying vendor UI, store or task protocols. */
const containers = new WeakMap()
const originalRuntimes = new WeakMap()
const sharedUi = new WeakMap()

export const originalVersions = Object.freeze({ vue: '2.7.16', 'vue-i18n': '7.8.1' })

export function loadOriginalModules(container = window.detlModule) {
  if (!container || typeof container.init !== 'function' || typeof container.get !== 'function') {
    return Promise.reject(new Error('HDI 原版任务资源未安装'))
  }
  if (!containers.has(container)) {
    const loading = (async () => {
      const scope = {}
      await container.init(scope)
      const shared = async name => {
        const registration = scope[name]?.[originalVersions[name]]
        if (!registration) throw new Error(`HDI 原版依赖缺失：${name}`)
        const module = (await registration.get())()
        return module.default || module
      }
      const Vue = await shared('vue')
      const VueI18n = await shared('vue-i18n')
      const components = (await container.get('./DFPModules'))()
      for (const name of ['AsyncTaskList', 'AsyncDagMain', 'AsyncDagMainForView', 'AsyncAddBasicInfo']) {
        if (typeof components[name] !== 'function') throw new Error('HDI 原版任务导出不完整')
      }
      const hui = window.GLOBAL_HUI
      if (!hui) throw new Error('HDI 原版 HUI 未加载')
      return { Vue, VueI18n, hui: hui.default || hui, components }
    })()
    containers.set(container, loading)
    loading.catch(() => containers.delete(container))
  }
  return containers.get(container)
}

// The verified original build exposes this Axios module through its own webpack runtime.
// Registration uses the runtime extension API; no vendor module/function is replaced.
function originalRuntime(surface = window) {
  if (originalRuntimes.has(surface)) return originalRuntimes.get(surface)
  let runtime
  const chunks = surface.webpackChunkdetl_web
  if (!Array.isArray(chunks)) throw new Error('原任务请求组件未加载')
  chunks.push([['rynew-platform-publish-boundary'], {}, require => { runtime = require }])
  if (typeof runtime !== 'function') throw new Error('原任务运行时扩展接口不可用')
  originalRuntimes.set(surface, runtime)
  return runtime
}

function originalRequestClient(surface = window) {
  const client = originalRuntime(surface)(43620)?.A
  if (!client?.interceptors?.request?.use || !client?.interceptors?.response?.use) throw new Error('原任务请求组件版本不匹配')
  return client
}

/** The original exported anchor exposes the shared toolbar component; do not reconstruct its render/setup. */
export function registerOriginalToolbar(Vue, i18n, runtime) {
  if (sharedUi.has(Vue)) return sharedUi.get(Vue)
  const loading = (async () => {
    await runtime.e(136)
    const exports = runtime(39136)
    const plugins = exports.default || exports
    const anchor = plugins.TheDataStandardTransformerProcessPluginInfo?.anchors?.[0]?.component
    if (anchor?.__name !== 'StandardConversion' || typeof anchor.setup !== 'function' || typeof Vue.extend !== 'function') {
      throw new Error('原共享工具栏组件导出版本不匹配')
    }
    // Normal Vue setup context, deliberately never mounted: this verified anchor initializes local fields only.
    const Probe = Vue.extend(anchor)
    const probe = new Probe({ i18n, propsData: { currentNode: { transformFieldVoList: [], runtimeJson: '' }, viewTask: true, flowId: null } })
    let component
    try {
      component = probe._setupState?.AdaptiveButtonContainer
      if (component?.__name !== 'AdaptiveButtonContainer' || typeof component.setup !== 'function' || typeof component.render !== 'function') {
        throw new Error('原共享工具栏组件不可用')
      }
    } finally { probe.$destroy() }
    Vue.component('adaptive-button-container', component)
    return component
  })()
  sharedUi.set(Vue, loading)
  loading.catch(() => sharedUi.delete(Vue))
  return loading
}

function originalFlowState(store) {
  const state = store?.state?.flowData
  if (!state || typeof state !== 'object') throw new Error('原任务flowData模块不可用')
  return state
}

/** Serialize this iframe's original save actions; never replay a failed or foreign revision. */
export function bindOriginalSaveQueue(store, client, host, origin, onFailure) {
  const dispatch = store.dispatch, module = store._modules?.get?.(['flowData']), flowContext = module?.context
  if (module?.state !== originalFlowState(store) || flowContext?.dispatch !== dispatch) throw new Error('原任务动作上下文版本不匹配')
  if (typeof dispatch !== 'function' || typeof store.commit !== 'function' || typeof client.get !== 'function') throw new Error('原任务保存接口不可用')
  const queues = new Map(), running = new Map()
  let closed = false
  const fail = () => new Error('任务保存未完成，后续修改仍保留在画布中；请重新打开任务核对版本后再保存')
  const decimal = value => /^[1-9]\d*$/.test(String(value ?? '')) && Number.isSafeInteger(Number(value)) ? String(value) : null
  const matches = flow => String(flow?.groupId) === String(host.workspaceId) && String(flow?.userId) === String(host.user.id) && flow?.projectCode === host.projectCode
  const original = (type, payload) => dispatch.call(store, type, payload)
  const acknowledge = async (record, payload) => {
    const response = await client.get(`/detl/flow/getFlowVisualById/${record.id}`, { params: { timeStamp: payload.timeStamp }, timeStamp: payload.timeStamp })
    const visual = response?.data, flow = visual?.flowVo, next = Number(record.revision) + 1
    if (closed || response?.code !== '0' || !Number.isSafeInteger(next) || decimal(flow?.id) !== record.id
        || decimal(flow?._platformRevision) !== String(next) || decimal(visual?.flowVersion) !== String(next) || !matches(flow) || decimal(originalFlowState(store).flowVo?.id) !== record.id) throw fail()
    const authoritative = {}
    for (const field of ['id', 'groupId', 'userId', 'projectCode', '_platformRevision', 'flowStatus', 'flowLock', 'readOnly', 'createTime', 'updateTime']) {
      if (Object.prototype.hasOwnProperty.call(flow, field)) authoritative[field] = flow[field]
    }
    // Original full readback would replace nodes/links/params and erase edits made while the request was running.
    // Keep the live editing draft; only an explicit original load/refresh replaces the diagram.
    store.commit('setFlowVo', { ...originalFlowState(store).flowVo, ...authoritative })
    store.commit('setFlowVersion', visual.flowVersion)
    if (visual.visualVersion != null) store.commit('setVisualVersion', visual.visualVersion)
    record.acknowledged = String(next)
    return visual
  }
  store.dispatch = function(type, payload) {
    if (type === 'initFlowDataByTaskId') {
      const id = decimal(payload?.taskId), record = running.get(id)
      if (record?.timeStamp != null && String(payload?.timeStamp) === String(record.timeStamp) && !payload?.historyId && !payload?.version) {
        // The original save action omits a catch on its nested read promise. Drain that private callback,
        // then reject the public queued action below; no caller receives success without a verified readback.
        return acknowledge(record, payload).catch(error => { record.readbackError = error; return null })
      }
      const queue = queues.get(id)
      // Explicit loads still run the complete original action, after in-flight writes have settled.
      return Promise.resolve(queue?.tail).then(() => original(type, payload)).then(result => {
        if (queue && queue.pending === 0 && decimal(originalFlowState(store).flowVo?.id) === id && matches(originalFlowState(store).flowVo)) queues.delete(id)
        return result
      })
    }
    if (!['saveFlowTask', 'pushFlowTask'].includes(type)) return original(type, payload)
    if (closed) return Promise.reject(fail())
    const id = decimal(payload?.flowVo?.id), revision = decimal(payload?.flowVo?._platformRevision)
    if (!id || !revision || payload.flowVo.readOnly || originalFlowState(store).loadTemplate) return original(type, payload)
    let snapshot
    try { snapshot = JSON.parse(JSON.stringify(payload)) } catch { return Promise.reject(fail()) }
    let queue = queues.get(id)
    if (!queue) { queue = { revision, accepted: new Set([revision]), pending: 0, pushes: 0, tail: Promise.resolve(), error: null }; queues.set(id, queue) }
    if (queue.error || queue.pending >= 128) return Promise.reject(fail())
    queue.pending++; if (type === 'pushFlowTask') queue.pushes++
    const work = queue.tail.then(async () => {
      if (closed || queue.error || !queue.accepted.has(revision) || decimal(originalFlowState(store).flowVo?.id) !== id
          || decimal(originalFlowState(store).flowVo?._platformRevision) !== queue.revision) throw fail()
      snapshot.flowVo._platformRevision = Number(queue.revision)
      const record = { id, revision: queue.revision, type, timeStamp: null, acknowledged: null }
      running.set(id, record)
      try {
        const result = await original(type, snapshot)
        if (record.readbackError) throw record.readbackError
        if (!record.acknowledged) throw fail()
        queue.revision = record.acknowledged; queue.accepted.add(record.acknowledged)
        return result
      } finally { if (running.get(id) === record) running.delete(id) }
    }).catch(error => {
      if (!queue.error) { queue.error = fail(); try { onFailure?.(queue.error.message) } catch {} }
      throw error
    }).finally(() => {
      queue.pending--; if (type === 'pushFlowTask') queue.pushes--
      if (!queue.pending && !queue.error && queues.get(id) === queue) queues.delete(id)
    })
    queue.tail = work.catch(() => {})
    return work
  }
  const wrapped = store.dispatch
  flowContext.dispatch = wrapped
  const requestId = client.interceptors.request.use(async config => {
    let url
    try { url = new URL(config.url, origin) } catch { return config }
    if (url.origin !== origin) return config
    const method = String(config.method || 'get').toLowerCase()
    if (method === 'post' && /^\/detl\/flow\/saveFlowVisual\/(save|push)$/.test(url.pathname)) {
      let body = config.data
      try {
        if (typeof body === 'string') body = JSON.parse(body)
        if (typeof body?.visualBase64 === 'string') body = JSON.parse(decodeURIComponent(atob(body.visualBase64)))
      } catch { throw fail() }
      const record = running.get(decimal(body?.flowVo?.id))
      if (record) {
        const operation = record.type === 'pushFlowTask' ? 'push' : 'save'
        if (!url.pathname.endsWith(`/${operation}`) || decimal(body.flowVo._platformRevision) !== record.revision || !decimal(config.timeStamp)) throw fail()
        record.timeStamp = config.timeStamp
      }
    }
    if (method === 'get' && url.pathname === '/detl/flow/getStartConfig') {
      const ids = url.searchParams.getAll('flowId')
      await Promise.resolve()
      const queue = ids.length === 1 ? queues.get(decimal(ids[0])) : null
      if (queue?.error) throw fail()
      if (queue?.pushes) {
        const boundary = queue.tail
        await boundary
        if (closed || queue.error) throw fail()
        config._rynewPublishBoundary = { taskId: ids[0], revision: queue.revision }
      }
    }
    return config
  })
  return () => {
    closed = true
    if (store.dispatch === wrapped) store.dispatch = dispatch
    if (flowContext.dispatch === wrapped) flowContext.dispatch = dispatch
    client.interceptors.request.eject(requestId)
    for (const queue of queues.values()) queue.error = fail()
  }
}

/** Wait for this iframe's original publish action before reading its run settings. Never invent/rewrite a revision. */
export function bindOriginalPublishBoundary(store, client, host, origin) {
  if (typeof store.subscribeAction !== 'function') throw new Error('原任务发布事件接口不可用')
  const byTask = new Map(), byAction = new WeakMap()
  let closed = false
  const stale = () => new Error('任务版本已变化或发布未完成，请重新打开运行配置')
  const decimal = value => {
    const text = String(value ?? ''), number = Number(text)
    return /^[1-9]\d*$/.test(text) && Number.isSafeInteger(number) ? text : null
  }
  const current = () => originalFlowState(store).flowVo
  const forgetSettled = record => setTimeout(() => {
    if (byTask.get(record.id) === record) byTask.delete(record.id)
  }, 0) // Keep only the current action turn; an earlier toolbar publish cannot bind a later pure read.
  const unsubscribe = store.subscribeAction({
    before(action) {
      if (closed || action.type !== 'pushFlowTask') return
      const flow = action.payload?.flowVo, id = decimal(flow?.id), revision = decimal(flow?._platformRevision)
      if (!id || !revision) return // The backend rejects invalid/missing revision; the host cannot grant one.
      let resolve, reject
      const promise = new Promise((yes, no) => { resolve = yes; reject = no })
      promise.catch(() => {}) // A toolbar publish may have no run-settings reader.
      const record = { id, revision, promise, resolve, reject }
      byTask.get(id)?.reject(stale())
      byTask.set(id, record); byAction.set(action, record)
    },
    after(action) {
      const record = byAction.get(action)
      if (!record) return
      byAction.delete(action)
      forgetSettled(record)
      const flow = current(), next = Number(record.revision) + 1
      if (closed || byTask.get(record.id) !== record || !Number.isSafeInteger(next)
          || decimal(flow?.id) !== record.id || decimal(flow?._platformRevision) !== String(next)
          || String(flow?.groupId) !== String(host.workspaceId) || String(flow?.userId) !== String(host.user.id)) {
        record.reject(stale()); return
      }
      record.resolve(String(next))
    },
    error(action) { const record = byAction.get(action); if (record) { byAction.delete(action); record.reject(stale()); forgetSettled(record) } }
  })
  const requestId = client.interceptors.request.use(async config => {
    if (closed || String(config.method || 'get').toLowerCase() !== 'get') return config
    let url
    try { url = new URL(config.url, origin) } catch { return config }
    if (url.origin !== origin || url.pathname !== '/detl/flow/getStartConfig') return config
    const ids = url.searchParams.getAll('flowId')
    if (ids.length !== 1 || !decimal(ids[0])) return config
    // Axios request interceptors run asynchronously. A same-tick original startTask dispatch registers publish first.
    await Promise.resolve()
    const record = byTask.get(ids[0])
    if (!record) return config
    try {
      const revision = await record.promise
      if (closed) throw stale()
      config._rynewPublishBoundary = { taskId: record.id, revision }
      return config
    } finally { if (byTask.get(record.id) === record) byTask.delete(record.id) }
  })
  const responseId = client.interceptors.response.use(response => {
    const expected = response?.config?._rynewPublishBoundary
    if (expected && (closed || String(response.data?.schedule?._platformRevision) !== expected.revision)) throw stale()
    return response
  })
  return () => {
    if (closed) return
    closed = true; unsubscribe()
    client.interceptors.request.eject(requestId); client.interceptors.response.eject(responseId)
    for (const record of byTask.values()) record.reject(stale())
    byTask.clear()
  }
}

// Verbatim factory registration from static/js/188.15d552eb.js, module 25418.
// SHA256: 3ed9bc64f696cb331efe748828880af980a483af0d61594744b99bffc465fd9a
// Only this UI registration is copied; evaluating 25418 would start the retired management application.
export function registerOriginalAdapterDrawer(Vue, runtime) {
  const o = () => Vue, r = runtime(4780), p = runtime(54147), g = runtime(30930)
  if (!r?.Drawer?.render || typeof p?.Go !== 'function' || !g?.Ay?.t) throw new Error('原节点抽屉组件不可用')
  const f=(0,p.Go)(r.Drawer);f.name="TheAdapterDrawer";const b=f.mounted||null;f.mounted=function(){b&&b.call(this),this.$nextTick((()=>{const e=this.$el.querySelector(".el-drawer__close-btn");e&&e.setAttribute("title",g.Ay.t("common.close"))}))},f.methods.drag=function(){this.$refs.drag.onmousedown=()=>{const e=document.body.clientHeight,t=document.body.clientWidth;document.onmousemove=s=>{switch(document.body.style.pointerEvents="none",this.emitParams={},this.dragDirection){case"r":{const e=s.clientX;this.calculateSize("width",e);break}case"l":{const e=t-s.clientX;this.calculateSize("width",e);break}case"t":{const t=e-s.clientY;this.calculateSize("height",t);break}case"b":{const e=s.clientY;this.calculateSize("height",e);break}default:{const e=s.clientX;this.calculateSize("width",e)}}this.$emit("drag",this.emitParams),document.onmouseup=e=>{document.body.style.pointerEvents=null,document.onmousemove=null,document.onmouseup=null}}}},o().component("TheAdapterDrawer",f);
}

export function withOriginalListDefaults(TaskList) {
  const initialize = TaskList?.methods?.handleCachePreSearch
  if (typeof initialize !== 'function') throw new Error('原任务列表初始化接口版本不匹配')
  return {
    extends: TaskList,
    beforeMount() { initialize.call(this) }
  }
}

/**
 * Called only inside the isolated, same-origin iframe after backend authorization.
 * This context is presentation data, NEVER proof of server-side authorization.
 * Every /detl API and resource request must independently check the platform session.
 */
export async function mountOriginalTasks(host, target) {
  if (!host?.user || !host.router || !target || !host.projectCode || !host.workspaceId) {
    throw new Error('缺少平台用户、工作区或导航上下文')
  }
  const { Vue, VueI18n, hui, components } = await loadOriginalModules()
  const locale = host.locale || 'zh_CN'
  Vue.use(VueI18n)
  const i18n = new VueI18n({ locale, fallbackLocale: locale, messages: {} })
  Vue.use(hui, { i18n: (key, args) => i18n.t(key, args) })
  window.THE_DFP_PROJECT_CODE = host.projectCode
  localStorage.setItem('languageId', locale)
  Vue.prototype.$router = host.router
  const route = Vue.observable({ ...(host.route || { query: {} }) })
  Object.defineProperty(Vue.prototype, '$route', { configurable: true, get: () => route })
  // The original factories consume the platform's genuine CSRF and persisted task domain.
  const TaskList = await components.AsyncTaskList(i18n, locale)
  const DagMain = await components.AsyncDagMain(i18n, locale)
  i18n.mergeLocaleMessage(locale, { 'detl.testRunSuccess.name': locale === 'en_US' ? 'Test run submitted' : '试运行已提交' })
  const store = Vue.prototype.$store
  if (!TaskList || !DagMain || !store || String(store.state.currentWorkspaceId) !== String(host.workspaceId)) {
    throw new Error('原任务组件未取得当前平台工作区')
  }
  registerOriginalAdapterDrawer(Vue, originalRuntime())
  await registerOriginalToolbar(Vue, i18n, originalRuntime())
  const TaskListHost = withOriginalListDefaults(TaskList)
  store.commit('SET_USER', host.user)
  store.commit('SET_USER_INFO', host.user)
  let destroyed = false
  const ensureMounted = vm => {
    const child = vm.$children?.[0]
    if (destroyed || !vm._isMounted || !child?._isMounted || !vm.$el?.isConnected || vm.$el.nodeType !== 1) {
      throw new Error('原任务组件尚未挂载')
    }
  }
  const vm = new Vue({
    i18n,
    data: () => ({ taskId: host.initialTaskId || null, readonly: host.readOnly === true || host.readonly === true }),
    methods: {
      async openTask(row, readonly, isNewCreate = false) {
        const id = String(row?.id ?? '')
        if (!/^[1-9]\d{0,18}$/.test(id) || (id.length === 19 && id > '9223372036854775807')) throw new Error('原任务返回的标识无效')
        if (host.openTaskInWindow?.({ id, readonly: host.readOnly === true || readonly, isNewCreate })) return
        this.taskId = id
        this.readonly = host.readOnly === true || readonly
        await this.$nextTick()
        ensureMounted(this)
        host.onTaskOpened?.({ id, readonly: this.readonly, isNewCreate })
      }
    },
    render(h) {
      const project = { projectCode: host.projectCode, projectName: host.projectName, projectList: host.projectList || [] }
      return this.taskId ? h(DagMain, {
        key: `${this.taskId}:${this.readonly ? 'view' : 'edit'}`,
        props: { ...project, flowId: this.taskId, isView: this.readonly, isShowTaskName: true, isDagTask: false },
        on: {
          changeTaskStatus: value => host.onTaskStatusChanged?.(value),
          updateTaskInfo: value => host.onTaskInfoChanged?.(value)
        }
      }) : h(TaskListHost, {
        props: { ...project, user: host.user, folderId: host.folderId },
        on: {
          editTask: row => this.openTask(row, false).catch(() => host.onError?.()),
          viewTask: row => this.openTask(row, true).catch(() => host.onError?.()),
          createNewTask: row => this.openTask(row, false, true).catch(() => host.onError?.())
        }
      })
    }
  }).$mount(target)
  let destroyBoundary = () => {}, destroySaveQueue = () => {}
  const destroy = () => {
    if (destroyed) return
    destroyed = true
    destroyBoundary()
    destroySaveQueue()
    window.removeEventListener('pagehide', onPageHide)
    vm.$destroy()
  }
  const onPageHide = event => { if (!event.persisted) destroy() }
  try {
    const client = originalRequestClient()
    destroySaveQueue = bindOriginalSaveQueue(store, client, host, window.location.origin, message => {
      originalRuntime()(4780).Message?.error({ message, duration: 6000, showClose: true })
    })
    destroyBoundary = bindOriginalPublishBoundary(store, client, host, window.location.origin)
    await vm.$nextTick()
    ensureMounted(vm)
  } catch (error) { destroy(); throw error }
  window.addEventListener('pagehide', onPageHide)
  return {
    async backToTaskList() { vm.taskId = null; await vm.$nextTick(); ensureMounted(vm) },
    setRoute(next) {
      const snapshot = { ...next }
      Object.keys(route).forEach(key => Vue.delete(route, key))
      Object.keys(snapshot).forEach(key => Vue.set(route, key, snapshot[key]))
    },
    destroy
  }
}
