import assert from 'node:assert/strict'
import test from 'node:test'
import { canRunSchedule, formatScheduleTime, releaseRequest, scheduleRequest, scheduleState, shouldPollSchedules } from '../scheduleRules.js'

test('发布沿用样本边界并保留空参数，不自动覆盖画布配置', () => {
  assert.deepEqual(releaseRequest('flow', '  发布版  ', '[]', '{}'), { flowId: 'flow', name: '发布版', inputJson: '[]', parameters: {} })
  assert.throws(() => releaseRequest('flow', '版本', '[invalid]', '{}'), /有效的 JSON/)
  assert.throws(() => releaseRequest('flow', '版本', JSON.stringify(Array(101).fill({})), '{}'), /100 条/)
  assert.throws(() => releaseRequest('flow', '版本', '{}', '[]'), /JSON 对象/)
  assert.throws(() => releaseRequest('', '版本', '{}', '{}'), /选择流程/)
})

test('调度保存携带已读取的修订号，不自行启用新计划', () => {
  const form = { name: '  每日样本 ', releaseId: 'release-1', cron: '0  0 8 * * ?', timeZone: ' Asia/Shanghai ' }
  assert.deepEqual(scheduleRequest(form), { name: '每日样本', releaseId: 'release-1', cron: '0 0 8 * * ?', timeZone: 'Asia/Shanghai' })
  assert.equal(scheduleRequest({ ...form, id: 'schedule-1', revision: 7 }).revision, 7)
  assert.throws(() => scheduleRequest({ ...form, cron: '0 8 * * *' }), /6 或 7/)
  assert.throws(() => scheduleRequest({ ...form, timeZone: '' }), /时区/)
  assert.throws(() => scheduleRequest({ ...form, releaseId: '' }), /已发布/)
})

test('运行和恢复状态以服务端结果为准，未知状态不变成成功', () => {
  assert.equal(scheduleState({ enabled: true }).label, '未返回状态')
  assert.equal(scheduleState({ status: 'PAUSED', recoveryRequired: true }).type, 'danger')
  assert.match(scheduleState({ status: 'future-state' }).label, /未知状态/)
  assert.equal(canRunSchedule({ id: '1', status: 'PAUSED' }), true)
  assert.equal(canRunSchedule({ id: '1', status: 'future-state' }), false)
  for (const blocked of [{ activeRunId: 'r1' }, { recoveryRequired: true }, { status: 'CLEANUP_REQUIRED' }]) {
    assert.equal(canRunSchedule({ id: '1', ...blocked }), false)
  }
  assert.equal(canRunSchedule({ id: '1' }, false), false)
})

test('轮询覆盖已启用计划和手动运行且有固定上限', () => {
  assert.equal(shouldPollSchedules([{ enabled: true }], null, 0), true)
  assert.equal(shouldPollSchedules([{ enabled: false, activeRunId: 'r1' }], null, 0), true)
  assert.equal(shouldPollSchedules([], { status: 'QUEUED' }, 0), true)
  assert.equal(shouldPollSchedules([{ enabled: false }], { status: 'SUCCEEDED' }, 0), false)
  assert.equal(shouldPollSchedules([{ enabled: true }], { status: 'RUNNING' }, 120), false)
})

test('触发时间按计划时区显示，异常值不伪造本地时间', () => {
  assert.match(formatScheduleTime('2026-09-09T00:00:00Z', 'Asia/Shanghai'), /08:00:00/)
  assert.equal(formatScheduleTime(null), '未返回')
  assert.equal(formatScheduleTime('not-a-time'), 'not-a-time')
  assert.equal(formatScheduleTime('2026-09-09T00:00:00Z', 'invalid-zone'), '2026-09-09T00:00:00Z')
})
