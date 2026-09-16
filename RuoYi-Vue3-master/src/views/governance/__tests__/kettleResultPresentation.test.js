import test from 'node:test'
import assert from 'node:assert/strict'
import { formatResultTime, preferredResultTab, resultLogText, resultMessageSummary, resultMessageText, resultViewState, sampleEmptyText } from '../kettle/resultPresentation.js'

const complete = extra => ({ state: 'SUCCEEDED', finalized: true, ...extra })
const fields = { valid: true, fieldsRequested: true, fieldsResolved: true }

test('completed jobs start with actual step metrics, while failed runs open diagnostics logs', () => {
  assert.equal(preferredResultTab(complete({ kind: 'job' })), 'metrics')
  assert.equal(preferredResultTab(complete({ mode: 'job' })), 'metrics')
  assert.equal(preferredResultTab(complete({ mode: 'preview' })), 'data')
  assert.equal(preferredResultTab({ kind: 'job', state: 'FAILED' }), 'logs')
  assert.equal(preferredResultTab({ mode: 'preview', state: 'TIMED_OUT' }), 'logs')
  assert.equal(preferredResultTab({ state: 'SUBMISSION_UNKNOWN' }), 'logs')
  assert.equal(preferredResultTab(complete({ workerStateAvailable: false })), 'logs')
})

test('checking fields does not display an older run success or expose historical metadata as current', () => {
  const current = resultViewState({ run: complete({ revision: 2 }), validation: fields, tab: 'fields', validationKind: 'transformation' })
  assert.equal(current.title, '字段检查')
  assert.equal(current.status.label, '检查完成')
  const stale = resultViewState({ run: complete({ revision: 2 }), validation: fields, validationStale: true, tab: 'fields' })
  assert.equal(stale.status.type, 'warning')
  assert.equal(stale.status.label, '结果已过期')
  const runView = resultViewState({ run: complete({ mode: 'preview' }), validation: fields, validationStale: true, tab: 'data' })
  assert.equal(runView.validationView, false)
  assert.equal(runView.title, '节点预览结果')
})

test('terminal events without process finalization and unavailable workers never show green success', () => {
  assert.equal(resultViewState({ run: { state: 'SUCCEEDED' }, tab: 'data' }).status.label, '正在结束执行')
  assert.equal(resultViewState({ run: complete({ workerStateAvailable: false }), tab: 'data' }).status.label, '执行服务暂不可用')
  assert.equal(resultViewState({ tab: 'data' }).status.label, '尚未运行')
})

test('empty samples distinguish jobs, running, failed, unobserved error rows and unstarted flows', () => {
  assert.match(sampleEmptyText(complete({ kind: 'job' })), /不提供行样本/)
  assert.doesNotMatch(sampleEmptyText(complete({ kind: 'job' })), /尚未执行|请先/)
  assert.match(sampleEmptyText({ state: 'RUNNING' }, 'read'), /执行中.*输入样本/)
  assert.match(sampleEmptyText({ state: 'FAILED', finalized: true }, 'written'), /运行日志和异常原因/)
  assert.match(sampleEmptyText(complete(), 'error'), /不代表没有错误/)
  assert.match(sampleEmptyText(null), /尚未执行/)
})

test('message summaries remain bounded while full structured causes and multiline logs are preserved', () => {
  const message = `数据库连接失败\n${'查询执行路径/'.repeat(100)}\n根因：拒绝连接`
  assert.ok(resultMessageSummary(message).length <= 181)
  assert.match(resultMessageSummary(message), /…$/)
  assert.equal(resultMessageText(message), message)
  assert.equal(resultLogText({ type: 'error', error: message }), message)
  const structured = { error: 'SQL', details: { position: 0, cause: '列不存在' } }
  assert.deepEqual(JSON.parse(resultMessageText(structured)), structured)
})

test('missing native job counters and times do not turn into invented zeroes or epoch values', () => {
  assert.equal(resultLogText({ type: 'job-entry', phase: 'AFTER', resultBoolean: true }), '完成 · 错误 未返回 · 文件 未返回')
  assert.equal(resultLogText({ type: 'job-entry', phase: 'AFTER', resultBoolean: true, errors: 0, files: 0 }), '完成 · 错误 0 · 文件 0')
  assert.equal(formatResultTime(null), '未返回')
  assert.equal(formatResultTime(''), '未返回')
  assert.equal(formatResultTime('invalid'), '时间格式无法识别')
  assert.equal(formatResultTime(1789010200), formatResultTime(1789010200000))
})
