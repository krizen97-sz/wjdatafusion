/* Host original DETL exports without modifying vendor UI, store or task protocols. */
const containers = new WeakMap()

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
  const destroy = () => {
    if (destroyed) return
    destroyed = true
    window.removeEventListener('pagehide', onPageHide)
    vm.$destroy()
  }
  const onPageHide = event => { if (!event.persisted) destroy() }
  try {
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
