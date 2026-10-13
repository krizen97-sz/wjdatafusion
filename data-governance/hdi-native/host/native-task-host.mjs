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
  if (!host?.user || !host.router || !target || !host.projectCode) {
    throw new Error('缺少平台用户、项目或导航上下文')
  }
  const { Vue, VueI18n, hui, components } = await loadOriginalModules()
  const locale = host.locale || 'zh_CN'
  Vue.use(VueI18n)
  const i18n = new VueI18n({ locale, fallbackLocale: locale, messages: {} })
  Vue.use(hui, { i18n: (key, args) => i18n.t(key, args) })
  window.THE_DFP_PROJECT_CODE = host.projectCode
  Vue.prototype.$router = host.router
  const route = Vue.observable({ ...(host.route || { query: {} }) })
  Object.defineProperty(Vue.prototype, '$route', { configurable: true, get: () => route })
  // These original factories obtain real CSRF, translations and workspace from /detl.
  // Do not synthesize a success envelope or skip their bootstrap/permission checks.
  const TaskList = await components.AsyncTaskList(i18n, locale)
  const DagMain = await components.AsyncDagMain(i18n, locale)
  if (!TaskList || !DagMain || !Vue.prototype.$store) {
    throw new Error('HDI 原版任务接口初始化失败')
  }
  const store = Vue.prototype.$store
  store.commit('SET_USER', host.user)
  store.commit('SET_USER_INFO', host.user)
  const vm = new Vue({
    i18n,
    data: () => ({ taskId: host.initialTaskId || null, readonly: Boolean(host.readonly) }),
    methods: {
      openTask(row, readonly) {
        if (!row?.id) throw new Error('原任务返回的标识为空')
        this.taskId = row.id
        this.readonly = readonly
        host.onTaskOpened?.({ id: row.id, readonly })
      }
    },
    render(h) {
      const project = { projectCode: host.projectCode, projectName: host.projectName, projectList: host.projectList || [] }
      return this.taskId ? h(DagMain, {
        key: String(this.taskId),
        props: { ...project, flowId: this.taskId, isView: this.readonly, isShowTaskName: true, isDagTask: false },
        on: {
          changeTaskStatus: value => host.onTaskStatusChanged?.(value),
          updateTaskInfo: value => host.onTaskInfoChanged?.(value)
        }
      }) : h(TaskList, {
        props: { ...project, user: host.user, folderId: host.folderId },
        on: {
          editTask: row => this.openTask(row, false),
          viewTask: row => this.openTask(row, true),
          createNewTask: row => this.openTask(row, false)
        }
      })
    }
  }).$mount(target)
  let destroyed = false
  const destroy = () => {
    if (destroyed) return
    destroyed = true
    window.removeEventListener('pagehide', onPageHide)
    vm.$destroy()
  }
  const onPageHide = event => { if (!event.persisted) destroy() }
  window.addEventListener('pagehide', onPageHide)
  return {
    backToTaskList: () => { vm.taskId = null },
    setRoute(next) {
      const snapshot = { ...next }
      Object.keys(route).forEach(key => Vue.delete(route, key))
      Object.keys(snapshot).forEach(key => Vue.set(route, key, snapshot[key]))
    },
    destroy
  }
}
