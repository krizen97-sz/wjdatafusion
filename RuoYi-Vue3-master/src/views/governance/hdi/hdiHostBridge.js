export function hdiFrameUrl(value, origin, taskId, readonly = false, isNewCreate = false) {
  const url = new URL(value, origin)
  if (url.origin !== origin || url.pathname !== '/detl/' || url.username || url.password || url.search) {
    throw new Error('任务工作区地址无效')
  }
  const hash = new URL(url.hash.slice(1), origin)
  if (hash.origin !== origin || hash.pathname !== '/task/taskList') throw new Error('任务入口无效')
  const workspace = hash.searchParams.get('appCode')
  if (!/^[1-9]\d*$/.test(workspace || '') || hash.searchParams.getAll('appCode').length !== 1) throw new Error('任务工作区无效')
  const query = new URLSearchParams({ appCode: workspace, flag: '1', menuflag: '1' })
  let path = '/task/taskList'
  if (taskId != null && taskId !== '') {
    if (!/^[1-9]\d*$/.test(String(taskId))) throw new Error('任务标识无效')
    query.set('id', String(taskId))
    path = readonly === true ? '/task/task-workBench-view' : '/task/taskWorkBench'
    if (isNewCreate === true && readonly !== true) query.set('isNewCreate', '1')
  }
  return `/detl/#${path}?${query}`
}

/** Keep the vendor's new-window action, wrapping its task page with the platform shell. */
export function platformTaskUrl(value, origin, expectedWorkspace) {
  try {
    const url = new URL(value, `${origin}/detl/`)
    if (url.origin !== origin || url.pathname !== '/detl/' || url.username || url.password) return null
    const route = new URL(url.hash.slice(1), origin)
    if (!['/task/taskWorkBench', '/task/task-workBench-view'].includes(route.pathname)) return null
    const id = route.searchParams.get('id')
    if (!/^[1-9]\d*$/.test(id || '') || route.searchParams.getAll('id').length !== 1
      || route.searchParams.getAll('appCode').length !== 1 || route.searchParams.get('appCode') !== String(expectedWorkspace)) return null
    const target = new URL('/governance/workspace', origin)
    target.searchParams.set('hdiTask', id)
    if (route.pathname === '/task/task-workBench-view') target.searchParams.set('hdiReadonly', 'true')
    else if (route.searchParams.get('isNewCreate') === '1') target.searchParams.set('hdiNew', '1')
    return `${target.pathname}${target.search}`
  } catch { return null }
}

export function isHdiMessage(event, frameWindow, origin) {
  return Boolean(frameWindow && event.source === frameWindow && event.origin === origin
    && ['rynew-hdi:ready', 'rynew-hdi:error', 'rynew-hdi:task', 'rynew-hdi:navigate', 'rynew-hdi:status'].includes(event.data?.type))
}
