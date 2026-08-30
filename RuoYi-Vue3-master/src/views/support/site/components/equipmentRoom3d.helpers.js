export const DEFAULT_ROOM_WIDTH = 12
export const DEFAULT_ROOM_DEPTH = 8
export const CABINET_WIDTH = 0.8
export const CABINET_DEPTH = 1.1
export const CABINET_HEIGHT = 4.2
export const CABINET_GRID_STEP = 0.2
export const CABINET_CLEARANCE = 0.16

export function normalizeRoomSize(room = {}) {
  return {
    width: positiveNumber(room.roomWidth, DEFAULT_ROOM_WIDTH),
    depth: positiveNumber(room.roomDepth, DEFAULT_ROOM_DEPTH)
  }
}

export function resolveCabinetLayout(cabinet = {}, index = 0, room = {}) {
  const { width, depth } = normalizeRoomSize(room)
  const columns = Math.max(1, Math.floor((width - 0.8) / 1.4))
  const fallbackX = 0.8 + (index % columns) * 1.4
  const fallbackZ = 0.9 + Math.floor(index / columns) * 1.6
  const rotationY = normalizeRotation(cabinet.rotationY)
  const position = clampCabinetPosition({
    x: finiteNumber(cabinet.positionX, fallbackX),
    z: finiteNumber(cabinet.positionZ, fallbackZ)
  }, room, rotationY)
  return {
    x: position.x,
    z: position.z,
    rotationY
  }
}

export function clampCabinetPosition(position = {}, room = {}, rotationY = 0) {
  const { width, depth } = normalizeRoomSize(room)
  const { halfX: halfWidth, halfZ: halfDepth } = getCabinetHalfExtents(rotationY)
  return {
    x: clamp(snap(finiteNumber(position.x, halfWidth), CABINET_GRID_STEP), halfWidth, Math.max(halfWidth, width - halfWidth)),
    z: clamp(snap(finiteNumber(position.z, halfDepth), CABINET_GRID_STEP), halfDepth, Math.max(halfDepth, depth - halfDepth))
  }
}

export function findCabinetCollision(candidate = {}, cabinets = [], room = {}, excludeCabinetId = null) {
  const candidateIndex = Math.max(0, cabinets.findIndex((cabinet) => Number(cabinet.cabinetId) === Number(candidate.cabinetId)))
  const candidateLayout = resolveCabinetLayout(candidate, candidateIndex, room)
  const candidateExtents = getCabinetHalfExtents(candidateLayout.rotationY)
  return cabinets.find((cabinet, index) => {
    if (Number(cabinet.cabinetId) === Number(excludeCabinetId ?? candidate.cabinetId)) return false
    const layout = resolveCabinetLayout(cabinet, index, room)
    const extents = getCabinetHalfExtents(layout.rotationY)
    const overlapsX = Math.abs(candidateLayout.x - layout.x) < candidateExtents.halfX + extents.halfX + CABINET_CLEARANCE
    const overlapsZ = Math.abs(candidateLayout.z - layout.z) < candidateExtents.halfZ + extents.halfZ + CABINET_CLEARANCE
    return overlapsX && overlapsZ
  }) || null
}

export function countCabinetCollisions(cabinets = [], room = {}) {
  let count = 0
  cabinets.forEach((cabinet, index) => {
    const layout = resolveCabinetLayout(cabinet, index, room)
    const candidate = { ...cabinet, positionX: layout.x, positionZ: layout.z, rotationY: layout.rotationY }
    const remaining = cabinets.slice(index + 1)
    if (findCabinetCollision(candidate, remaining, room, null)) count += 1
  })
  return count
}

export function getDeviceRackTransform(device = {}, cabinet = {}) {
  const capacity = Math.max(1, Number(cabinet.uCapacity) || 45)
  const start = clamp(Math.floor(Number(device.rackUStart) || 1), 1, capacity)
  const end = clamp(Math.floor(Number(device.rackUEnd) || start), start, capacity)
  const unitHeight = 3.8 / capacity
  const span = end - start + 1
  return {
    start,
    end,
    span,
    height: Math.max(0.035, span * unitHeight - 0.012),
    y: 0.2 + (start - 1 + span / 2) * unitHeight
  }
}

export function getDeviceKey(sourceType, sourceId) {
  return `${String(sourceType || '').toUpperCase()}:${sourceId}`
}

export function getDeviceLinks(device = {}, links = []) {
  const sourceKey = getDeviceKey(device.sourceType, device.sourceId)
  return links.filter((link) =>
    getDeviceKey(link.sourceType, link.sourceId) === sourceKey ||
    getDeviceKey(link.targetType, link.targetId) === sourceKey
  )
}

export function summarizeOutgoingPorts(device = {}, links = []) {
  const sourceKey = getDeviceKey(device.sourceType, device.sourceId)
  return links.reduce((summary, link) => {
    if (getDeviceKey(link.sourceType, link.sourceId) !== sourceKey || link.status === '1') return summary
    const count = Math.max(0, Number(link.portCount) || 0)
    if (link.mediumType === 'OPTICAL') summary.optical += count
    if (link.mediumType === 'ELECTRICAL') summary.electrical += count
    return summary
  }, { optical: 0, electrical: 0 })
}

export function isDevicePlaced(device = {}) {
  return Boolean(device.roomId && device.cabinetId && Number(device.rackUStart) && Number(device.rackUEnd))
}

function normalizeRotation(value) {
  const rotation = finiteNumber(value, 0) % 360
  return rotation < 0 ? rotation + 360 : rotation
}

function getCabinetHalfExtents(rotationY) {
  const radians = normalizeRotation(rotationY) * Math.PI / 180
  const cosine = Math.abs(Math.cos(radians))
  const sine = Math.abs(Math.sin(radians))
  return {
    halfX: cosine * CABINET_WIDTH / 2 + sine * CABINET_DEPTH / 2,
    halfZ: sine * CABINET_WIDTH / 2 + cosine * CABINET_DEPTH / 2
  }
}

function positiveNumber(value, fallback) {
  const number = Number(value)
  return Number.isFinite(number) && number > 0 ? number : fallback
}

function finiteNumber(value, fallback) {
  const number = Number(value)
  return Number.isFinite(number) ? number : fallback
}

function snap(value, step) {
  return Math.round(value / step) * step
}

function clamp(value, min, max) {
  return Math.min(max, Math.max(min, value))
}
