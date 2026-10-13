const HOST_PATH = '/detl/task-host.html'
const TASK_ID = /^[1-9]\d{0,18}$/

export function validHdiTaskId(value) {
  const id = String(value ?? '')
  return TASK_ID.test(id) && (id.length < 19 || id <= '9223372036854775807')
}

export function hdiFrameUrl(value, origin, taskId, readonly = false, isNewCreate = false) {
  if (typeof value !== 'string') throw new Error('任务工作区地址无效')
  const url = new URL(value, origin)
  if (url.origin !== origin || url.pathname !== HOST_PATH || url.username || url.password || url.search || url.hash) {
    throw new Error('任务工作区地址无效')
  }
  if (taskId != null && taskId !== '') {
    if (!validHdiTaskId(taskId)) throw new Error('任务标识无效')
    url.searchParams.set('task', String(taskId))
    if (readonly === true) url.searchParams.set('readonly', 'true')
    if (isNewCreate === true && readonly !== true) url.searchParams.set('new', '1')
  }
  return `${url.pathname}${url.search}`
}

/** Preserve native new-window actions, keeping the destination inside the platform. */
export function platformTaskUrl(value, origin, expectedWorkspace) {
  try {
    const url = new URL(value, `${origin}/detl/`)
    if (url.origin !== origin || url.username || url.password) return null
    let id, readonly = false, isNewCreate = false
    if (url.pathname === HOST_PATH && !url.hash) {
      if ([...url.searchParams.keys()].some(key => !['task', 'readonly', 'new'].includes(key))
        || ['task', 'readonly', 'new'].some(key => url.searchParams.getAll(key).length > 1)) return null
      id = url.searchParams.get('task')
      readonly = url.searchParams.get('readonly') === 'true'
      isNewCreate = url.searchParams.get('new') === '1'
    } else if (url.pathname === '/detl/' && !url.search) {
      const route = new URL(url.hash.slice(1), origin)
      if (route.origin !== origin || !['/task/taskWorkBench', '/task/task-workBench-view'].includes(route.pathname)) return null
      id = route.searchParams.get('id')
      if (route.searchParams.getAll('id').length !== 1 || route.searchParams.getAll('appCode').length !== 1
        || route.searchParams.get('appCode') !== String(expectedWorkspace)) return null
      readonly = route.pathname === '/task/task-workBench-view'
      isNewCreate = route.searchParams.get('isNewCreate') === '1'
    } else return null
    if (!validHdiTaskId(id)) return null
    const target = new URL('/governance/workspace', origin)
    target.searchParams.set('hdiTask', id)
    if (readonly) target.searchParams.set('hdiReadonly', 'true')
    else if (isNewCreate) target.searchParams.set('hdiNew', '1')
    return `${target.pathname}${target.search}`
  } catch { return null }
}

export function isHdiMessage(event, frameWindow, origin) {
  return Boolean(frameWindow && event.source === frameWindow && event.origin === origin
    && ['rynew-hdi:ready', 'rynew-hdi:error', 'rynew-hdi:task', 'rynew-hdi:navigate', 'rynew-hdi:status'].includes(event.data?.type))
}
