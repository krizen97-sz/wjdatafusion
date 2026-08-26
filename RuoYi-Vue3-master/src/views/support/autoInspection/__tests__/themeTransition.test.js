import assert from 'node:assert/strict'
import test from 'node:test'
import { resolveThemeTransitionGeometry } from '../../../../utils/themeTransition.js'

test('theme transition expands from the actual trigger point', () => {
  const geometry = resolveThemeTransitionGeometry(
    { clientX: 100, clientY: 40 },
    { width: 1000, height: 600 }
  )

  assert.equal(geometry.x, 100)
  assert.equal(geometry.y, 40)
  assert.equal(geometry.radius, Math.hypot(900, 560))
})

test('theme transition uses the viewport center when no pointer event exists', () => {
  const geometry = resolveThemeTransitionGeometry(undefined, { width: 1200, height: 800 })

  assert.deepEqual(geometry, {
    x: 600,
    y: 400,
    radius: Math.hypot(600, 400)
  })
})
