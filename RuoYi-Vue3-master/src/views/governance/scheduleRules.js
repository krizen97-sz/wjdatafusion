import { isRunActive, parseTestRequest } from './workspaceRules.js'

const SCHEDULE_STATES = {
  PAUSED: { label: '已暂停', type: 'info' },
  READY: { label: '等待触发', type: 'success' },
  STARTING: { label: '正在提交', type: 'warning' },
  ENABLED: { label: '等待触发', type: 'success' },
  SCHEDULED: { label: '等待触发', type: 'success' },
  WAITING: { label: '等待触发', type: 'success' },
  RUNNING: { label: '正在执行', type: 'warning' },
  QUEUED: { label: '等待执行', type: 'warning' },
  RECOVERY_REQUIRED: { label: '需要恢复', type: 'danger' },
  CLEANUP_REQUIRED: { label: '需要清理', type: 'danger' },
  ERROR: { label: '调度异常', type: 'danger' },
  EXHAUSTED: { label: '无后续触发时间', type: 'info' }
}

export function scheduleState(schedule) {
  if (schedule?.recoveryRequired) return SCHEDULE_STATES.RECOVERY_REQUIRED
  const status = String(schedule?.status || '').toUpperCase()
  return SCHEDULE_STATES[status] || { label: status ? `未知状态：${status}` : '未返回状态', type: 'info' }
}

export function canRunSchedule(schedule, engineReady = true) {
  return Boolean(engineReady && schedule?.id && !schedule.activeRunId && !schedule.recoveryRequired && ['PAUSED', 'READY', 'EXHAUSTED'].includes(schedule.status))
}

export function shouldPollSchedules(schedules, run, attempts, limit = 120) {
  return attempts < limit && (isRunActive(run) || schedules.some((item) => Boolean(item.activeRunId || item.enabled)))
}

export function releaseRequest(flowId, name, inputText, parameterText) {
  if (!flowId) throw new Error('请先选择流程。')
  const trimmedName = String(name || '').trim()
  if (!trimmedName || trimmedName.length > 80) throw new Error('版本名称需要 1–80 个字符。')
  return { flowId: String(flowId), name: trimmedName, ...parseTestRequest(inputText, parameterText) }
}

export function scheduleRequest(form) {
  const name = String(form.name || '').trim()
  const cron = String(form.cron || '').trim().replace(/\s+/g, ' ')
  const timeZone = String(form.timeZone || '').trim()
  if (!name || name.length > 80) throw new Error('任务名称需要 1–80 个字符。')
  if (!form.releaseId) throw new Error('请选择已发布版本。')
  if (![6, 7].includes(cron.split(' ').length)) throw new Error('Cron 表达式应包含 6 或 7 个字段，请使用表达式生成器检查。')
  if (!timeZone || timeZone.length > 80) throw new Error('请填写有效的时区，例如 Asia/Shanghai。')
  return { name, releaseId: String(form.releaseId), cron, timeZone, ...(form.id ? { revision: form.revision } : {}) }
}

export function formatScheduleTime(value, timeZone = 'Asia/Shanghai') {
  if (!value) return '未返回'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return String(value)
  try {
    return new Intl.DateTimeFormat('zh-CN', { timeZone, year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false }).format(date)
  } catch { return String(value) }
}
