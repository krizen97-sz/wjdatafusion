import assert from 'node:assert/strict'
import test from 'node:test'
import { getRippleGeometry } from '../../../../directive/common/motionRipple.js'

test('ripple geometry expands from the pointer to every button corner', () => {
  assert.deepEqual(
    getRippleGeometry({ left: 20, top: 10, width: 100, height: 40 }, 45, 20),
    { x: 25, y: 10, size: 162 }
  )
})

test('ripple geometry falls back to the control center for keyboard-style input', () => {
  assert.deepEqual(
    getRippleGeometry({ left: 20, top: 10, width: 100, height: 40 }, undefined, undefined),
    { x: 50, y: 20, size: 108 }
  )
})
