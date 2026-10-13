/* Host original DETL exports without modifying vendor UI, store or task protocols. */
const containers = new WeakMap()
const requestClients = new WeakMap()

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
function originalRequestClient(surface = window) {
  if (requestClients.has(surface)) return requestClients.get(surface)
  let runtime
  const chunks = surface.webpackChunkdetl_web
  if (!Array.isArray(chunks)) throw new Error('原任务请求组件未加载')
  chunks.push([['rynew-platform-publish-boundary'], {}, require => { runtime = require }])
  const client = runtime?.(43620)?.A
  if (!client?.interceptors?.request?.use || !client?.interceptors?.response?.use) throw new Error('原任务请求组件版本不匹配')
  requestClients.set(surface, client)
  return client
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
  const current = () => store.state.flowVo
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
  const store = Vue.prototype.$store
  if (!TaskList || !DagMain || !store || String(store.state.currentWorkspaceId) !== String(host.workspaceId)) {
    throw new Error('原任务组件未取得当前平台工作区')
  }
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
      }) : h(TaskList, {
        props: { ...project, user: host.user, folderId: host.folderId },
        on: {
          editTask: row => this.openTask(row, false).catch(() => host.onError?.()),
          viewTask: row => this.openTask(row, true).catch(() => host.onError?.()),
          createNewTask: row => this.openTask(row, false, true).catch(() => host.onError?.())
        }
      })
    }
  }).$mount(target)
  let destroyBoundary = () => {}
  const destroy = () => {
    if (destroyed) return
    destroyed = true
    destroyBoundary()
    window.removeEventListener('pagehide', onPageHide)
    vm.$destroy()
  }
  const onPageHide = event => { if (!event.persisted) destroy() }
  try {
    destroyBoundary = bindOriginalPublishBoundary(store, originalRequestClient(), host, window.location.origin)
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
