import test from 'node:test'
import assert from 'node:assert/strict'
import { hdiFrameUrl, isHdiMessage } from '../hdi/hdiHostBridge.js'

test('task frame cannot navigate to a remote service or another same-origin page', () => {
  const origin = 'https://platform.example'
  assert.equal(hdiFrameUrl('/detl/task-host.html', origin, '12'), '/detl/task-host.html?task=12')
  assert.equal(hdiFrameUrl('/detl/task-host.html', origin, '12', true), '/detl/task-host.html?task=12&readonly=true')
  assert.equal(hdiFrameUrl('/detl/task-host.html', origin, null, true), '/detl/task-host.html')
  assert.equal(hdiFrameUrl('/detl/task-host.html?token=not-carried', origin), '/detl/task-host.html')
  for (const value of ['https://remote.example/detl/task-host.html', '/login', '//remote.example/detl/task-host.html',
    'https://user@platform.example/detl/task-host.html', '/detl/task-host.html#javascript']) {
    assert.throws(() => hdiFrameUrl(value, origin))
  }
  for (const task of ['../1', '0', '-1', '1&run=true', 'javascript:alert(1)']) {
    assert.throws(() => hdiFrameUrl('/detl/task-host.html', origin, task))
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
