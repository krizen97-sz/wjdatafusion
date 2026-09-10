const labels = {
  INTENT_PERSISTED: ['已记录执行请求', 'info'], STARTING: ['正在启动引擎', 'info'], FINISHING: ['正在结束执行', 'info'], QUEUED: ['等待执行', 'info'], PREPARING: ['准备执行', 'info'], RUNNING: ['运行中', 'warning'], STOPPING: ['正在停止', 'warning'],
  SUCCEEDED: ['运行成功', 'success'], PREVIEW_COMPLETE: ['节点预览完成', 'success'], FAILED: ['运行失败', 'danger'], STOPPED: ['已停止', 'info'],
  TIMED_OUT: ['运行超时', 'warning'], UNKNOWN: ['执行状态待核查', 'warning'], SUBMITTING: ['正在提交', 'info'],
  VALIDATION_FAILED: ['原引擎校验未通过', 'danger'], PREPARATION_FAILED: ['准备失败', 'danger'], SUBMISSION_REJECTED: ['提交被拒绝', 'danger'],
  SUBMISSION_UNKNOWN: ['提交结果待核查', 'warning'], WORKER_UNAVAILABLE: ['执行服务暂不可用', 'warning'], RECOVERY_REQUIRED: ['中断待核查', 'warning']
}
export const runStatus = state => ({ label: labels[state]?.[0] || '状态未确认', type: labels[state]?.[1] || 'info' })
export const runActive = state => ['INTENT_PERSISTED', 'STARTING', 'QUEUED', 'PREPARING', 'RUNNING', 'STOPPING', 'SUBMITTING'].includes(state)
export function mergeEvents(previous, incoming, maximum = 3000) {
  const seen = new Set(previous.map(event => event.seq))
  return [...previous, ...incoming.filter(event => !seen.has(event.seq))].sort((a, b) => a.seq - b.seq).slice(-maximum)
}
export function nodeRows(events, node, direction) { return events.filter(event => event.type === 'row' && (!node || event.node === node) && event.direction === direction) }
export function rowColumns(rows) {
  const columns = new Map()
  for (const row of rows) {
    for (const field of row.fieldsMeta || []) if (!columns.has(field.name)) columns.set(field.name, field)
    for (const name of Object.keys(row.fields || {})) if (!columns.has(name)) columns.set(name, { name, type: '' })
  }
  return [...columns.values()]
}

export function runFinishing(run) { return Boolean(run && ['SUCCEEDED', 'PREVIEW_COMPLETE', 'FAILED', 'STOPPED', 'TIMED_OUT'].includes(run.state) && run.exitCode == null && run.finishedAt == null && run.finalized !== true) }

export function runNeedsReview(run) {
  if (!run) return false
  if (['VALIDATION_FAILED', 'PREPARATION_FAILED', 'SUBMISSION_REJECTED'].includes(run.state)) return false
  if (run.workerStateAvailable === false) return true
  return !runActive(run.state) && !['SUCCEEDED', 'PREVIEW_COMPLETE', 'FAILED', 'STOPPED', 'TIMED_OUT'].includes(run.state)
}

export function jobNodeStates(events, runState) {
  const latest = new Map()
  for (const event of events) {
    if (event.type !== 'job-entry' || !event.node) continue
    const key = `${event.node}\u0000${event.copy ?? 0}`, previous = latest.get(key)
    if (previous && Number(previous.execution) > Number(event.execution)) continue
    let status = event.phase === 'BEFORE' ? 'RUNNING' : Number(event.errors) > 0 || event.resultBoolean !== true ? 'FAILED' : 'SUCCEEDED'
    if (status === 'RUNNING' && ['FAILED', 'TIMED_OUT', 'STOPPED'].includes(runState)) status = runState
    if (status === 'FAILED' && Number(event.errors) === 0 && runState === 'STOPPED') status = 'STOPPED'
    latest.set(key, { ...event, status })
  }
  return [...latest.values()]
}
