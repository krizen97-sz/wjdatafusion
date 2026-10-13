import test from 'node:test'
import assert from 'node:assert/strict'
import { LOCAL_NODE_TYPES, newLocalNode, localStarterGraph, connectLocalNodes, localResultFields, localRunState } from '../local/localFlowRules.js'

test('new nodes copy defaults and allocate independent identities', () => {
  const first = newLocalNode('FIELD_MAP'), second = newLocalNode('FIELD_MAP', [first])
  first.config.fields[0].target = 'changed'
  assert.equal(second.name, '字段映射 2')
  assert.equal(second.config.fields[0].target, 'name')
  assert.notEqual(first.id, second.id)
  assert.equal(LOCAL_NODE_TYPES.find(item => item.type === 'FIELD_MAP').config.fields[0].target, 'name')
  assert.throws(() => newLocalNode('SHELL'), /不支持/)
})

test('the starter is a connected five-node local graph with editable processing configuration', () => {
  const first = localStarterGraph(), second = localStarterGraph()
  assert.deepEqual(first.nodes.map(node => node.type), ['CSV_INPUT', 'FIELD_MAP', 'FILTER', 'SORT', 'OUTPUT'])
  assert.equal(first.connections.length, 4)
  for (let i = 0; i < 4; i++) { assert.equal(first.connections[i].sourceId, first.nodes[i].id); assert.equal(first.connections[i].targetId, first.nodes[i + 1].id) }
  assert.equal(first.nodes[2].config.value, '苏州')
  assert.equal(first.nodes[3].config.fields[0].valueType, 'NUMBER')
  assert.notEqual(first.nodes[0].id, second.nodes[0].id)
  assert.doesNotMatch(JSON.stringify(first), /https?:|kettle|nifi|python/i)
})

test('connections reject cycles, duplicate hops and invalid source/sink directions', () => {
  const graph = localStarterGraph()
  assert.throws(() => connectLocalNodes(graph, graph.nodes[0].id, graph.nodes[1].id), /已经连接/)
  assert.throws(() => connectLocalNodes(graph, graph.nodes[4].id, graph.nodes[1].id), /输出节点/)
  assert.throws(() => connectLocalNodes(graph, graph.nodes[1].id, graph.nodes[0].id), /输入节点/)
  const a = newLocalNode('UNION'), b = newLocalNode('UNION'), c = newLocalNode('UNION')
  const cycle = { nodes: [a, b, c], connections: [{ sourceId: a.id, targetId: b.id }, { sourceId: b.id, targetId: c.id }] }
  assert.throws(() => connectLocalNodes(cycle, c.id, a.id), /循环/)
  assert.throws(() => connectLocalNodes(cycle, a.id, a.id), /自身/)
})

test('multiple inputs require an explicit union and sample fields retain later-row keys', () => {
  const a = newLocalNode('CSV_INPUT'), b = newLocalNode('JSON_INPUT'), sort = newLocalNode('SORT'), union = newLocalNode('UNION')
  const graph = { nodes: [a, b, sort, union], connections: [{ sourceId: a.id, targetId: sort.id }, { sourceId: a.id, targetId: union.id }] }
  assert.throws(() => connectLocalNodes(graph, b.id, sort.id), /合并数据流/)
  assert.equal(connectLocalNodes(graph, b.id, union.id).targetId, union.id)
  assert.deepEqual(localResultFields({ sampleRows: [{ a: 0 }, { b: null }] }), ['a', 'b'])
  assert.equal(localRunState('UNRECOGNIZED')[1], 'warning')
})
