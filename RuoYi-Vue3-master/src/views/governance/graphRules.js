export const NODE_WIDTH = 144
export const NODE_HEIGHT = 120
export const TILE_SIZE = 64
export const POSITION_SCALE = 2

const number = (value, fallback = 0) => Number.isFinite(Number(value)) ? Number(value) : fallback
export const clamp = (value, min, max) => Math.min(max, Math.max(min, value))
export function canvasPosition(node) {
  return { x: number(node?.position?.x) / POSITION_SCALE, y: number(node?.position?.y) / POSITION_SCALE }
}
export function enginePosition(point) {
  return { x: Math.round(clamp(number(point.x), -10000, 10000) * POSITION_SCALE), y: Math.round(clamp(number(point.y), -10000, 10000) * POSITION_SCALE) }
}
export function designSignature(graph) {
  const nodes = (graph.nodes || []).map(({ id, type, role, properties = {} }) => ({ id, type, role, properties: Object.fromEntries(Object.entries(properties).sort(([a], [b]) => a.localeCompare(b))) })).sort((a, b) => a.id.localeCompare(b.id))
  const connections = (graph.connections || []).map(({ sourceId, targetId, relationships = [] }) => ({ sourceId, targetId, relationships: [...relationships].sort() }))
    .sort((a, b) => JSON.stringify(a).localeCompare(JSON.stringify(b)))
  return JSON.stringify({ nodes, connections })
}
export function graphBounds(nodes) {
  if (!nodes?.length) return { x: 0, y: 0, width: 480, height: 260 }
  const points = nodes.map(canvasPosition)
  const x = Math.min(...points.map(p => p.x)), y = Math.min(...points.map(p => p.y))
  return { x, y, width: Math.max(...points.map(p => p.x)) - x + NODE_WIDTH, height: Math.max(...points.map(p => p.y)) - y + NODE_HEIGHT }
}
export function portPosition(node, direction) {
  const point = canvasPosition(node)
  return { x: point.x + NODE_WIDTH / 2 + (direction === 'out' ? TILE_SIZE / 2 : -TILE_SIZE / 2), y: point.y + TILE_SIZE / 2 }
}
export function relationshipLabel(value) {
  return ({ success: '成功', matched: '匹配', unmatched: '未匹配', failure: '异常', accepted: '满足条件', empty: '空批次' })[value] || value || '未指定关系'
}
export function edgeGeometry(edge, nodes, index = 0) {
  const source = nodes.find(n => n.id === edge.sourceId), target = nodes.find(n => n.id === edge.targetId)
  if (!source || !target) return null
  const a = portPosition(source, 'out'), b = portPosition(target, 'in')
  const distance = Math.max(44, Math.abs(b.x - a.x) * 0.5)
  const parallel = index ? (index % 2 ? 1 : -1) * Math.ceil(index / 2) * 32 : 0
  const midY = (a.y + b.y) / 2 + parallel
  const middleNodes = nodes.filter(n => n.id !== source.id && n.id !== target.id).map(canvasPosition)
    .filter(p => p.x + NODE_WIDTH / 2 > a.x && p.x + NODE_WIDTH / 2 < b.x)
  if (b.x <= a.x || middleNodes.length) {
    const lane = Math.min(a.y, b.y, ...middleNodes.map(p => p.y)) - 58 - index * 34
    const points = [a, { x: a.x + 26, y: a.y }, { x: a.x + 26, y: lane }, { x: b.x - 26, y: lane }, { x: b.x - 26, y: b.y }, b]
    return { path: roundedPath(points), label: { x: (a.x + b.x) / 2, y: lane - 10 }, bounds: { minY: lane - 24, maxY: Math.max(a.y, b.y) } }
  }
  if (Math.abs(a.y - b.y) < 2 && !parallel && b.x > a.x) {
    return { path: `M ${a.x} ${a.y} L ${b.x} ${b.y}`, label: { x: (a.x + b.x) / 2, y: a.y - 12 } }
  }
  return { path: `M ${a.x} ${a.y} C ${a.x + distance} ${a.y + parallel}, ${b.x - distance} ${b.y + parallel}, ${b.x} ${b.y}`, label: { x: (a.x + b.x) / 2, y: midY - 12 } }
}

function roundedPath(points) {
  let path = `M ${points[0].x} ${points[0].y}`
  for (let i = 1; i < points.length - 1; i++) {
    const a = points[i - 1], b = points[i], c = points[i + 1]
    const incoming = Math.hypot(b.x - a.x, b.y - a.y), outgoing = Math.hypot(c.x - b.x, c.y - b.y)
    const radius = Math.min(8, incoming / 2, outgoing / 2)
    if (!incoming || !outgoing) continue
    const before = { x: b.x - (b.x - a.x) * radius / incoming, y: b.y - (b.y - a.y) * radius / incoming }
    const after = { x: b.x + (c.x - b.x) * radius / outgoing, y: b.y + (c.y - b.y) * radius / outgoing }
    path += ` L ${before.x} ${before.y} Q ${b.x} ${b.y} ${after.x} ${after.y}`
  }
  const end = points[points.length - 1]
  return `${path} L ${end.x} ${end.y}`
}

export function connectionIssue(nodes, connections, sourceId, targetId, relationship) {
  const source = nodes.find(n => n.id === sourceId), target = nodes.find(n => n.id === targetId)
  if (!source || !target) return '连接节点已变化，请刷新流程后重试。'
  if (sourceId === targetId) return '不能连接到节点自身。'
  if (source.role === 'CAPTURE') return '结果输出是流程终点，不能再添加下游节点。'
  if (target.type?.endsWith('.GenerateFlowFile')) return '样本输入是流程起点，不能接收上游连接。'
  if (connections.length >= 24) return '当前隔离测试最多支持 24 条连接。'
  if (relationship && connections.some(e => e.sourceId === sourceId && e.targetId === targetId && e.relationships?.includes(relationship))) return '这条分支连接已经存在。'
  const pending = [targetId], seen = new Set()
  while (pending.length) {
    const id = pending.pop()
    if (id === sourceId) return '这条连接会形成环路，请调整节点顺序。'
    if (seen.has(id)) continue
    seen.add(id)
    connections.filter(e => e.sourceId === id).forEach(e => pending.push(e.targetId))
  }
  return ''
}

export function arrangeNodes(nodes, connections) {
  const ids = new Set(nodes.map(n => n.id)), outgoing = new Map(nodes.map(n => [n.id, new Set()]))
  const incoming = new Map(nodes.map(n => [n.id, 0])), depth = new Map(nodes.map(n => [n.id, 0]))
  connections.forEach(e => {
    if (ids.has(e.sourceId) && ids.has(e.targetId) && !outgoing.get(e.sourceId).has(e.targetId)) {
      outgoing.get(e.sourceId).add(e.targetId)
      incoming.set(e.targetId, incoming.get(e.targetId) + 1)
    }
  })
  const queue = nodes.filter(n => !incoming.get(n.id)).map(n => n.id)
  let visited = 0
  while (queue.length) {
    const id = queue.shift(); visited++
    outgoing.get(id).forEach(next => {
      depth.set(next, Math.max(depth.get(next), depth.get(id) + 1))
      incoming.set(next, incoming.get(next) - 1)
      if (!incoming.get(next)) queue.push(next)
    })
  }
  if (visited !== nodes.length) throw new Error('流程包含环路，无法自动整理。')
  const rows = new Map()
  return nodes.map(n => {
    const column = depth.get(n.id), row = rows.get(column) || 0
    rows.set(column, row + 1)
    return { ...n, position: enginePosition({ x: column * 232, y: row * 164 + 60 }) }
  })
}
