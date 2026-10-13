import test from 'node:test'
import assert from 'node:assert/strict'
import { hdiFrameUrl, isHdiMessage, platformTaskUrl } from '../hdi/hdiHostBridge.js'

test('task frame cannot navigate to a remote service or another same-origin page', () => {
  const origin = 'https://platform.example'
  const entry = '/detl/#/task/taskList?appCode=9007199254740993&flag=1&menuflag=1'
  assert.equal(hdiFrameUrl(entry, origin, '12'), '/detl/#/task/taskWorkBench?appCode=9007199254740993&flag=1&menuflag=1&id=12')
  assert.equal(hdiFrameUrl(entry, origin, '12', true), '/detl/#/task/task-workBench-view?appCode=9007199254740993&flag=1&menuflag=1&id=12')
  assert.equal(hdiFrameUrl(entry, origin, null, true), entry)
  assert.equal(hdiFrameUrl(entry, origin, '12', false, true), '/detl/#/task/taskWorkBench?appCode=9007199254740993&flag=1&menuflag=1&id=12&isNewCreate=1')
  for (const value of ['https://remote.example/detl/#/task/taskList?appCode=1', '/login', '//remote.example/detl/',
    'https://user@platform.example/detl/', '/detl/#/task/taskList?appCode=1&appCode=2', '/detl/#/other?appCode=1',
    '/detl/?token=not-carried#/task/taskList?appCode=1']) {
    assert.throws(() => hdiFrameUrl(value, origin))
  }
  for (const task of ['../1', '0', '-1', '1&run=true', 'javascript:alert(1)']) {
    assert.throws(() => hdiFrameUrl(entry, origin, task))
  }
})

test('native new task windows keep their exact IDs and readonly mode inside the platform', () => {
  const origin = 'https://platform.example'
  assert.equal(platformTaskUrl('/detl/#/task/taskWorkBench?id=9007199254740993&appCode=11&flag=1', origin, '11'),
    '/governance/workspace?hdiTask=9007199254740993')
  assert.equal(platformTaskUrl('/detl/#/task/task-workBench-view?id=5&appCode=11', origin, '11'),
    '/governance/workspace?hdiTask=5&hdiReadonly=true')
  assert.equal(platformTaskUrl('/detl/#/task/taskWorkBench?id=5&appCode=11&isNewCreate=1', origin, '11'),
    '/governance/workspace?hdiTask=5&hdiNew=1')
  for (const value of ['/detl/#/task/taskWorkBench?id=1&appCode=12', '/detl/#/task/taskWorkBench?id=1&id=2&appCode=11',
    'https://remote.example/detl/#/task/taskWorkBench?id=1&appCode=11', '/detl/#/login?id=1&appCode=11']) {
    assert.equal(platformTaskUrl(value, origin, '11'), null)
  }
})

test('forged iframe state cannot clear loading, change the task or trigger navigation', () => {
  const frame = {}, stranger = {}, origin = 'https://platform.example'
  const event = { source: frame, origin, data: { type: 'rynew-hdi:ready' } }
  assert.equal(isHdiMessage(event, frame, origin), true)
  assert.equal(isHdiMessage({ ...event, source: stranger }, frame, origin), false)
  assert.equal(isHdiMessage({ ...event, origin: 'https://remote.example' }, frame, origin), false)
  assert.equal(isHdiMessage({ ...event, data: { type: 'unknown' } }, frame, origin), false)
  assert.equal(isHdiMessage({ ...event, source: null }, null, origin), false)
})
