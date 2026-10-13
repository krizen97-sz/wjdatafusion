import { mountOriginalTasks } from './native-task-host.mjs'

const send = (type, data = {}) => {
  if (window.parent !== window) window.parent.postMessage({ type, ...data }, window.location.origin)
}
let mounted
function rememberTask(id, readonly, isNewCreate = false) {
  const url = new URL(window.location.href)
  for (const key of ['task', 'readonly', 'new']) url.searchParams.delete(key)
  if (id) url.searchParams.set('task', String(id))
  if (id && readonly) url.searchParams.set('readonly', 'true')
  else if (id && isNewCreate) url.searchParams.set('new', '1')
  window.history.replaceState(null, '', url)
}
function ready() {
  document.documentElement.dataset.hdiHostState = 'ready'
  send('rynew-hdi:ready')
}
function fail() {
  mounted?.destroy()
  document.documentElement.dataset.hdiHostState = 'error'
  document.getElementById('app').textContent = '任务工作区暂不可用，请在平台页面重试。'
  send('rynew-hdi:error')
}
async function back() {
  if (!mounted) return fail()
  try {
    await mounted.backToTaskList()
    rememberTask(null, false)
    send('rynew-hdi:task', { taskId: null })
    ready()
  } catch { fail() }
}
const router = {
  push(target) {
    send('rynew-hdi:navigate', { name: target?.name || '', href: typeof target === 'string' ? target : target?.path || '', query: target?.query || {} })
    return Promise.resolve(false)
  },
  replace(target) { return this.push(target) },
  resolve(target) { return { href: typeof target === 'string' ? target : target?.path || '/governance/workspace' } },
  go(delta) { if (delta < 0) return back() },
  back
}

async function mount() {
  document.documentElement.dataset.hdiHostState = 'loading'
  try {
    const response = await fetch('/detl/_platform/context', { credentials: 'same-origin', cache: 'no-store' })
    if (!response.ok) throw new Error('平台会话已失效或任务服务不可用')
    const result = await response.json()
    if (result.code !== '0' || !result.data?.user || !result.data?.projectCode || !result.data?.workspaceId) throw new Error('任务工作区未配置')
    const query = new URLSearchParams(window.location.search)
    if (['task', 'readonly', 'new'].some(key => query.getAll(key).length > 1)) throw new Error('任务参数重复')
    const taskId = query.get('task')
    if (taskId && (!/^[1-9]\d{0,18}$/.test(taskId) || (taskId.length === 19 && taskId > '9223372036854775807'))) throw new Error('任务标识无效')
    mounted = await mountOriginalTasks({
      ...result.data,
      router,
      route: { query: {} },
      initialTaskId: taskId,
      readonly: query.get('readonly') === 'true',
      openTaskInWindow: ({ id, readonly, isNewCreate }) => {
        const target = new URL('/governance/workspace', window.location.origin)
        target.searchParams.set('hdiTask', id)
        if (readonly) target.searchParams.set('hdiReadonly', 'true')
        else if (isNewCreate) target.searchParams.set('hdiNew', '1')
        // Preserve task windows; a blocked popup falls back to the original component in this frame.
        return Boolean(window.open(`${target.pathname}${target.search}`, '_blank'))
      },
      onTaskOpened: ({ id, readonly, isNewCreate }) => {
        rememberTask(id, readonly, isNewCreate)
        send('rynew-hdi:task', { taskId: String(id), readonly, isNewCreate })
      },
      onTaskStatusChanged: () => send('rynew-hdi:status'),
      onError: fail
    }, '#detl-task-root')
    window.addEventListener('message', event => {
      if (event.source !== window.parent || event.origin !== window.location.origin) return
      if (event.data?.type === 'rynew-hdi:back') back()
    })
    ready()
  } catch { fail() }
}
mount()
