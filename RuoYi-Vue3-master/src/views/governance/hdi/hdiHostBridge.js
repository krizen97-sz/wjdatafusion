export function hdiFrameUrl(value, origin, taskId, readonly = false) {
  const url = new URL(value, origin)
  if (url.origin !== origin || url.pathname !== '/detl/task-host.html' || url.username || url.password || url.hash) {
    throw new Error('任务工作区地址无效')
  }
  url.search = ''
  if (taskId != null && taskId !== '') {
    if (!/^[1-9]\d*$/.test(String(taskId))) throw new Error('任务标识无效')
    url.searchParams.set('task', String(taskId))
    if (readonly === true) url.searchParams.set('readonly', 'true')
  }
  return `${url.pathname}${url.search}`
}

export function isHdiMessage(event, frameWindow, origin) {
  return Boolean(frameWindow && event.source === frameWindow && event.origin === origin
    && ['rynew-hdi:ready', 'rynew-hdi:error', 'rynew-hdi:task', 'rynew-hdi:navigate', 'rynew-hdi:status'].includes(event.data?.type))
}
