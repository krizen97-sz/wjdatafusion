import { runActive, runFinishing, runNeedsReview, runStatus } from './runRules.js'
import { validationFeedback } from './workbenchRules.js'

const failedStates = new Set(['FAILED', 'TIMED_OUT', 'VALIDATION_FAILED', 'PREPARATION_FAILED', 'SUBMISSION_REJECTED'])
export const sampleDirectionLabel = direction => ({ read: '输入', written: '输出', error: '错误行' })[direction] || '数据'

export function preferredResultTab(run) {
  if (!run) return 'data'
  if (failedStates.has(run.state) || runNeedsReview(run)) return 'logs'
  return run.kind === 'job' || run.mode === 'job' ? 'metrics' : 'data'
}

export function resultViewState({ run, validation, validationKind, validationStale, tab }) {
  const validationView = Boolean(validation) && ['validation', 'fields'].includes(tab)
  if (validationView) {
    const feedback = validationFeedback(validation, validationKind)
    const status = validationStale ? { label: '结果已过期', type: 'warning' }
      : { label: ({ success: '检查完成', warning: '需要处理', error: '检查失败', info: '待确认' })[feedback.type], type: feedback.type === 'error' ? 'danger' : feedback.type }
    return { title: validationKind === 'job' ? '作业结构检查' : '字段检查', validationView, status }
  }
  let state = run?.state
  if (run?.workerStateAvailable === false) state = 'WORKER_UNAVAILABLE'
  else if (runFinishing(run)) state = 'FINISHING'
  return { title: !run ? '测试与运行结果' : run.mode === 'preview' ? '节点预览结果' : '任务运行结果', validationView, status: run ? runStatus(state) : { label: '尚未运行', type: 'info' } }
}

export function sampleEmptyText(run, direction = 'written') {
  if (!run) return '尚未执行预览或运行。选择画布节点后点击“预览节点”，即可查看样本。'
  if (run.kind === 'job' || run.mode === 'job') return '作业节点不提供行样本，请查看步骤指标、运行日志或输出文件。'
  const label = sampleDirectionLabel(direction)
  if (runActive(run.state) || runFinishing(run)) return `执行中，尚未收到所选节点的${label}样本。`
  if (failedStates.has(run.state) || runNeedsReview(run)) return `本次未取得所选节点的${label}样本，请查看运行日志和异常原因。`
  if (direction === 'error') return '未记录错误行样本，不代表没有错误；请同时核对步骤指标和运行日志。'
  return `本次未记录所选节点的${label}样本，可切换节点或输入 / 输出方向查看。`
}

export function resultMessageText(value) {
  if (value == null) return ''
  return typeof value === 'string' ? value : JSON.stringify(value, null, 2)
}

export function resultMessageSummary(value, limit = 180) {
  const message = resultMessageText(value).replace(/\s+/g, ' ').trim()
  return message.length > limit ? `${message.slice(0, limit)}…` : message
}

export function resultLogText(event) {
  if (event.type === 'job-entry') return event.phase === 'BEFORE' ? '开始执行' : `${event.resultBoolean ? '完成' : '未成功'} · 错误 ${event.errors ?? '未返回'} · 文件 ${event.files ?? '未返回'}`
  return resultMessageText(event.message || event.line || event.error || event.state || event.status || (event.errors != null ? `错误数 ${event.errors}` : event))
}

export function formatResultTime(value) {
  if (value == null || value === '') return '未返回'
  const date = new Date(typeof value === 'number' && value < 1e12 ? value * 1000 : value)
  return Number.isNaN(date.getTime()) ? '时间格式无法识别' : date.toLocaleString()
}
