import test from 'node:test'
import assert from 'node:assert/strict'
import { arrangeNodes, canvasPosition, connectionIssue, designSignature, edgeGeometry, enginePosition, graphBounds, visibleConnections } from '../graphRules.js'

const nodes = [
  { id: 'input', type: 'org.apache.nifi.processors.standard.GenerateFlowFile', position: { x: 0, y: 100 } },
  { id: 'route', position: { x: 520, y: 100 } },
  { id: 'result', role: 'CAPTURE', position: { x: 1040, y: 100 } }
]
test('canvas coordinates preserve engine positions and bound invalid movement', () => {
  assert.deepEqual(enginePosition(canvasPosition(nodes[1])), nodes[1].position)
  assert.deepEqual(enginePosition({ x: Infinity, y: 50000 }), { x: 0, y: 20000 })
  assert.equal(graphBounds(nodes).width, 664)
})
test('connections reject cycles, duplicates, missing nodes and invalid terminals', () => {
  const edges = [{ sourceId: 'input', targetId: 'route', relationships: ['success'] }]
  assert.ok(connectionIssue(nodes, edges, 'route', 'input'))
  assert.ok(connectionIssue(nodes, edges, 'route', 'route'))
  assert.ok(connectionIssue(nodes, edges, 'result', 'route'))
  assert.ok(connectionIssue(nodes, edges, 'input', 'route', 'success'))
  assert.ok(connectionIssue(nodes, edges, 'missing', 'route'))
  assert.equal(connectionIssue(nodes, edges, 'route', 'result', 'accepted'), '')
  const ordinary = nodes.map(n => ({ ...n, type: 'UpdateAttribute', role: 'PROCESSOR' }))
  assert.ok(connectionIssue(ordinary, edges, 'route', 'input', 'success').includes('环路'))
})
test('layout treats parallel relationships as one graph dependency', () => {
  const edges = [{ sourceId: 'input', targetId: 'route' }, { sourceId: 'route', targetId: 'result' }, { sourceId: 'route', targetId: 'result' }]
  const result = arrangeNodes(nodes, edges)
  assert.ok(result[0].position.x < result[1].position.x)
  assert.ok(result[1].position.x < result[2].position.x)
  assert.deepEqual(nodes[1].position, { x: 520, y: 100 })
  assert.throws(() => arrangeNodes(nodes, [...edges, { sourceId: 'result', targetId: 'input' }]), /环路/)
})
test('parallel connection curves remain distinguishable and missing endpoints do not render', () => {
  const edge = { sourceId: 'input', targetId: 'route' }
  assert.notEqual(edgeGeometry(edge, nodes, 0).path, edgeGeometry(edge, nodes, 1).path)
  assert.equal(edgeGeometry({ sourceId: 'unknown', targetId: 'route' }, nodes), null)
})
test('a connection skipping a node routes outside its tile and retains measurable bounds', () => {
  const geometry = edgeGeometry({ sourceId: 'input', targetId: 'result' }, nodes)
  assert.ok(geometry.bounds.minY < canvasPosition(nodes[1]).y)
  assert.ok(geometry.path.includes(' Q '))
  assert.ok(!geometry.path.includes('NaN'))
})
test('definition comparison ignores engine ordering and geometry but detects actual configuration changes', () => {
  const graph = { nodes: nodes.map(n => ({ ...n, properties: { b: '2', a: '1' } })), connections: [{ sourceId: 'input', targetId: 'route', relationships: ['success'] }] }
  const submitted = designSignature(graph)
  const reordered = { ...graph, nodes: [...graph.nodes].reverse().map(n => ({ ...n, position: { x: 900, y: 600 }, properties: { a: '1', b: '2' } })) }
  assert.equal(designSignature(reordered), submitted)
  reordered.nodes[0].properties.a = 'changed after submission'
  assert.notEqual(designSignature(reordered), submitted)
  assert.equal(submitted, designSignature(graph))
})
test('downward branch labels stay clear of an upper-node caption at a narrow viewport', () => {
  const branch = { id: 'lower', position: { x: 1040, y: 428 } }
  const geometry = edgeGeometry({ sourceId: 'route', targetId: 'lower' }, [...nodes, branch])
  const zoom = 0.57
  const upperCaptionLeft = canvasPosition(nodes[2]).x + 72 - 116 / (2 * zoom)
  assert.ok(geometry.label.x + 48 / (2 * zoom) < upperCaptionLeft)
})
test('long graphs fold without overlapping nodes or changing execution topology', () => {
  const chain = Array.from({ length: 14 }, (_, i) => ({ id: String(i), properties: { sample: String(i) } }))
  const edges = chain.slice(1).map((node, i) => ({ sourceId: String(i), targetId: node.id, relationships: ['success'] }))
  const signature = designSignature({ nodes: chain, connections: edges })
  const arranged = arrangeNodes(chain, edges)
  assert.equal(new Set(arranged.map(node => JSON.stringify(node.position))).size, chain.length)
  assert.ok(graphBounds(arranged).width < 1000)
  assert.ok(graphBounds(arranged).height > 500)
  assert.equal(designSignature({ nodes: arranged, connections: edges }), signature)
  const wrap = edgeGeometry(edges[3], arranged)
  assert.ok(wrap.bounds.minY >= canvasPosition(arranged[3]).y)
  assert.ok(wrap.label.y > canvasPosition(arranged[3]).y + 120)
})
test('main-flow display preserves all real edges and only collapses pure empty or failure routes', () => {
  const edges = [
    { sourceId: 'a', relationships: ['success'] },
    { sourceId: 'a', relationships: ['failure'] },
    { sourceId: 'b', relationships: ['empty'] },
    { sourceId: 'b', relationships: ['failure', 'accepted'] },
    { sourceId: 'c', relationships: [] }
  ]
  assert.deepEqual(visibleConnections(edges, false, ''), [edges[0], edges[3], edges[4]])
  assert.deepEqual(visibleConnections(edges, false, 'a'), [edges[0], edges[1], edges[3], edges[4]])
  assert.deepEqual(visibleConnections(edges, true, ''), edges)
  assert.equal(edges.length, 5)
})
