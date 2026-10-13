import { mountOriginalTasks } from './native-task-host.mjs'

const send = (type, data = {}) => {
  if (window.parent !== window) window.parent.postMessage({ type, ...data }, window.location.origin)
}
let mounted
function rememberTask(id, readonly) {
  const url = new URL(window.location.href)
  if (id) url.searchParams.set('task', String(id))
  else url.searchParams.delete('task')
  if (id && readonly) url.searchParams.set('readonly', 'true')
  else url.searchParams.delete('readonly')
  window.history.replaceState(null, '', url)
}
function back() {
  mounted?.backToTaskList()
  rememberTask(null, false)
  send('rynew-hdi:task', { taskId: null })
}
const router = {
  push(target) {
    send('rynew-hdi:navigate', { name: target?.name || '', query: target?.query || {} })
    return Promise.resolve()
  },
  replace(target) { return this.push(target) },
  resolve() { return { href: '/governance/workspace' } },
  go(delta) { if (delta < 0) back() },
  back
}

async function mount() {
try {
  const response = await fetch('/detl/_platform/context', { credentials: 'same-origin', cache: 'no-store' })
  if (!response.ok) throw new Error('平台会话已失效或任务服务不可用')
  const result = await response.json()
  if (result.code !== '0' || !result.data?.user || !result.data?.projectCode) throw new Error('任务工作区未配置')
  const query = new URLSearchParams(window.location.search)
  const taskId = query.get('task')
  if (taskId && !/^[1-9]\d*$/.test(taskId)) throw new Error('任务标识无效')
  mounted = await mountOriginalTasks({
    ...result.data,
    router,
    route: { query: {} },
    initialTaskId: taskId,
    readonly: query.get('readonly') === 'true',
    onTaskOpened: ({ id, readonly }) => {
      rememberTask(id, readonly)
      send('rynew-hdi:task', { taskId: String(id), readonly })
    },
    onTaskStatusChanged: () => send('rynew-hdi:status')
  }, '#detl-task-root')
  window.addEventListener('message', event => {
    if (event.source !== window.parent || event.origin !== window.location.origin) return
    if (event.data?.type === 'rynew-hdi:back') back()
  })
  send('rynew-hdi:ready', { taskId: taskId || null })
} catch {
  // Never reflect native exception text, task contents or upstream responses into the UI.
  document.getElementById('app').textContent = '任务工作区暂不可用，请在平台页面重试。'
  send('rynew-hdi:error')
}
}
mount()
