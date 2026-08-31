import test from 'node:test'
import assert from 'node:assert/strict'
import {
  clampCabinetPosition,
  countCabinetCollisions,
  findCabinetCollision,
  getDeviceKey,
  getDeviceLinks,
  getDeviceRackTransform,
  isDevicePlaced,
  resolveCabinetLayout,
  summarizeOutgoingPorts
} from '../components/equipmentRoom3d.helpers.js'

test('legacy cabinets receive stable in-room fallback coordinates', () => {
  const first = resolveCabinetLayout({}, 0, { roomWidth: 6, roomDepth: 4 })
  const second = resolveCabinetLayout({}, 1, { roomWidth: 6, roomDepth: 4 })
  assert.deepEqual(first, { x: 0.8, z: 1, rotationY: 0 })
  assert.deepEqual(second, { x: 2.2, z: 1, rotationY: 0 })
})

test('cabinet drag positions snap to grid and stay inside room', () => {
  assert.deepEqual(
    clampCabinetPosition({ x: -3, z: 99 }, { roomWidth: 10, roomDepth: 8 }),
    { x: 0.4, z: 7.45 }
  )
})

test('cabinet placement detects overlap and respects rotated footprint', () => {
  const cabinets = [
    { cabinetId: 1, positionX: 2, positionZ: 2, rotationY: 0 },
    { cabinetId: 2, positionX: 5, positionZ: 2, rotationY: 90 }
  ]
  assert.equal(findCabinetCollision({ cabinetId: 3, positionX: 2.5, positionZ: 2, rotationY: 0 }, cabinets, { roomWidth: 10, roomDepth: 8 })?.cabinetId, 1)
  assert.equal(findCabinetCollision({ cabinetId: 3, positionX: 3.5, positionZ: 2, rotationY: 0 }, cabinets, { roomWidth: 10, roomDepth: 8 }), null)
  assert.equal(countCabinetCollisions(cabinets, { roomWidth: 10, roomDepth: 8 }), 0)
})

test('rack transform maps U ranges from bottom to top', () => {
  const transform = getDeviceRackTransform({ rackUStart: 10, rackUEnd: 12 }, { uCapacity: 42 })
  assert.equal(transform.start, 10)
  assert.equal(transform.end, 12)
  assert.equal(transform.span, 3)
  assert.ok(transform.y > 1)
  assert.ok(transform.height > 0)
})

test('links and optical/electrical counts use typed device ids', () => {
  const device = { sourceType: 'SERVER', sourceId: 8 }
  const links = [
    { sourceType: 'SERVER', sourceId: 8, targetType: 'HARDWARE', targetId: 3, mediumType: 'OPTICAL', portCount: 2, status: '0' },
    { sourceType: 'SERVER', sourceId: 8, targetType: 'HARDWARE', targetId: 3, mediumType: 'ELECTRICAL', portCount: 1, status: '0' },
    { sourceType: 'HARDWARE', sourceId: 3, targetType: 'HARDWARE', targetId: 4, mediumType: 'OPTICAL', portCount: 4, status: '0' }
  ]
  assert.equal(getDeviceKey('server', 8), 'SERVER:8')
  assert.equal(getDeviceLinks(device, links).length, 2)
  assert.deepEqual(summarizeOutgoingPorts(device, links), { optical: 2, electrical: 1 })
  assert.equal(isDevicePlaced({ roomId: 1, cabinetId: 2, rackUStart: 3, rackUEnd: 4 }), true)
})
