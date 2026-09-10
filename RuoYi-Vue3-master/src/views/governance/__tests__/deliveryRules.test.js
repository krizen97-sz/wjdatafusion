import test from 'node:test'
import assert from 'node:assert/strict'
import { deliveryState, deliverableRun } from '../deliveryRules.js'
test('only successful cleaned runs with a complete manifest are deliverable', () => {
  const run = { status: 'SUCCEEDED', cleanupConfirmed: true, artifactsManifestAvailable: true, artifactCount: 2 }
  assert.equal(deliverableRun(run), true)
  for (const patch of [{ status: 'FAILED' }, { status: 'EMPTY' }, { cleanupConfirmed: false }, { artifactsManifestAvailable: false }, { artifactCount: 0 }]) assert.equal(deliverableRun({ ...run, ...patch }), false)
  assert.equal(deliverableRun(null), false)
})
test('delivery completion follows the server state, and recovery never loops automatically', () => {
  assert.equal(deliveryState('RUNNING').active, true)
  assert.equal(deliveryState('DELIVERED').type, 'success')
  assert.equal(deliveryState('RECOVERY_REQUIRED').retry, true)
  assert.equal(deliveryState('RECOVERY_REQUIRED').active, false)
  assert.equal(deliveryState('future-status').active, false)
})
