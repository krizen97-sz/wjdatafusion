import test from 'node:test'
import assert from 'node:assert/strict'
import { hdiFrameUrl, isHdiMessage, platformTaskUrl, validHdiTaskId } from '../hdi/hdiHostBridge.js'

const origin = 'https://platform.example'
const entry = '/detl/task-host.html'

test('DFP frame only uses the exact platform-owned host and carries no credentials', () => {
  assert.equal(hdiFrameUrl(entry, origin, '12'), `${entry}?task=12`)
  assert.equal(hdiFrameUrl(entry, origin, '12', true), `${entry}?task=12&readonly=true`)
  assert.equal(hdiFrameUrl(entry, origin, null, true), entry)
  assert.equal(hdiFrameUrl(entry, origin, '12', false, true), `${entry}?task=12&new=1`)
  for (const value of [undefined, 'https://remote.example/detl/task-host.html', '/login', '//remote.example/detl/',
    'https://user@platform.example/detl/task-host.html', '/detl/#/task/taskList?appCode=1',
    `${entry}?token=not-carried`, `${entry}#unexpected`]) assert.throws(() => hdiFrameUrl(value, origin))
})

test('task identities remain exact positive Long strings in navigation', () => {
  for (const task of ['../1', '0', '-1', '1&run=true', 'javascript:alert(1)', '9223372036854775808', '1'.repeat(100)]) {
    assert.equal(validHdiTaskId(task), false)
    assert.throws(() => hdiFrameUrl(entry, origin, task))
  }
  assert.equal(hdiFrameUrl(entry, origin, '9007199254740993'), `${entry}?task=9007199254740993`)
  assert.equal(validHdiTaskId('9223372036854775807'), true)
})

test('both original and DFP task windows retain exact IDs and mode in the platform', () => {
  assert.equal(platformTaskUrl('/detl/#/task/taskWorkBench?id=9007199254740993&appCode=11&flag=1', origin, '11'),
    '/governance/workspace?hdiTask=9007199254740993')
  assert.equal(platformTaskUrl('/detl/#/task/task-workBench-view?id=5&appCode=11', origin, '11'),
    '/governance/workspace?hdiTask=5&hdiReadonly=true')
  assert.equal(platformTaskUrl(`${entry}?task=5&new=1`, origin, '11'), '/governance/workspace?hdiTask=5&hdiNew=1')
  assert.equal(platformTaskUrl(`${entry}?task=5&readonly=true&new=1`, origin, '11'), '/governance/workspace?hdiTask=5&hdiReadonly=true')
  for (const value of ['/detl/#/task/taskWorkBench?id=1&appCode=12', '/detl/#/task/taskWorkBench?id=1&id=2&appCode=11',
    'https://remote.example/detl/#/task/taskWorkBench?id=1&appCode=11', '/detl/#/login?id=1&appCode=11',
    `${entry}?task=1&task=2`, `${entry}?task=1&token=secret`, `${entry}?task=0`, `${entry}?task=1#unexpected`]) {
    assert.equal(platformTaskUrl(value, origin, '11'), null)
  }
})

test('forged iframe messages cannot change parent state', () => {
  const frame = {}, stranger = {}
  const event = { source: frame, origin, data: { type: 'rynew-hdi:ready' } }
  assert.equal(isHdiMessage(event, frame, origin), true)
  assert.equal(isHdiMessage({ ...event, source: stranger }, frame, origin), false)
  assert.equal(isHdiMessage({ ...event, origin: 'https://remote.example' }, frame, origin), false)
  assert.equal(isHdiMessage({ ...event, data: { type: 'unknown' } }, frame, origin), false)
  assert.equal(isHdiMessage({ ...event, source: null }, null, origin), false)
})
