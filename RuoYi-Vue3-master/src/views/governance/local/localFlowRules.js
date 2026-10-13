export const LOCAL_NODE_TYPES = [
  { type: 'CSV_INPUT', label: 'CSV输入', group: '输入', icon: 'Document', category: 'source', config: { content: 'name,city\nalice,苏州\nbob,上海\n', delimiter: ',', header: true } },
  { type: 'JSON_INPUT', label: 'JSON输入', group: '输入', icon: 'Document', category: 'source', config: { content: '[{"name":"alice","city":"苏州"}]' } },
  { type: 'FIELD_MAP', label: '字段映射', group: '处理', icon: 'EditPen', category: 'transform', config: { fields: [{ source: 'name', target: 'name', trim: true }], keepUnmapped: true } },
  { type: 'FILTER', label: '条件过滤', group: '处理', icon: 'Filter', category: 'route', config: { field: 'name', operator: 'IS_NOT_NULL', value: '', valueType: 'STRING' } },
  { type: 'SORT', label: '排序', group: '处理', icon: 'Sort', category: 'transform', config: { fields: [{ field: 'name', direction: 'ASC', valueType: 'STRING' }] } },
  { type: 'DEDUP', label: '去重', group: '处理', icon: 'Operation', category: 'transform', config: { fields: ['name'] } },
  { type: 'LOOKUP', label: '字典关联', group: '处理', icon: 'Search', category: 'transform', config: { rows: [{ city: '苏州', code: '3205' }], matches: [{ inputField: 'city', lookupField: 'city' }], returns: [{ lookupField: 'code', outputField: 'city_code' }], missing: 'KEEP', multiple: 'FAIL' } },
  { type: 'UNION', label: '合并数据流', group: '处理', icon: 'Connection', category: 'transform', config: {} },
  { type: 'OUTPUT', label: '文件输出', group: '输出', icon: 'Download', category: 'output', config: { format: 'CSV', filename: 'result.csv' } }
]
export const cloneFlowValue = value => JSON.parse(JSON.stringify(value))
export function flowId() {
  const bytes = new Uint8Array(16); crypto.getRandomValues(bytes)
  bytes[6] = (bytes[6] & 15) | 64; bytes[8] = (bytes[8] & 63) | 128
  const hex = Array.from(bytes, value => value.toString(16).padStart(2, '0')).join('')
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`
}
export const localNodeKind = node => LOCAL_NODE_TYPES.find(item => item.type === node.type) || { label: node.type, icon: 'Operation', category: 'transform' }
export const localSource = node => ['CSV_INPUT', 'JSON_INPUT'].includes(node.type)
export const localActive = run => ['QUEUED', 'RUNNING', 'STOPPING'].includes(run?.state)
export const localRunState = state => ({ QUEUED: ['等待执行', 'info'], RUNNING: ['运行中', 'warning'], STOPPING: ['停止中', 'warning'], SKIPPED: ['未执行', 'info'], PENDING: ['等待上游', 'info'], SUCCEEDED: ['成功', 'success'], FAILED: ['失败', 'danger'], STOPPED: ['已停止', 'info'], INTERRUPTED: ['运行中断', 'warning'] })[state] || ['状态待确认', 'warning']
export function localNodeSummary(node) {
  const config = node.config || {}
  if (node.type === 'OUTPUT') return config.filename || '设置输出文件'
  if (node.type === 'FIELD_MAP') return `${config.fields?.length || 0} 个字段映射`
  if (node.type === 'FILTER') return config.field || '选择过滤字段'
  if (node.type === 'SORT') return (config.fields || []).map(item => item.field).join('、') || '设置排序字段'
  if (node.type === 'DEDUP') return (config.fields || []).join('、') || '设置去重字段'
  if (node.type === 'LOOKUP') return `${config.rows?.length || 0} 条字典数据`
  return localNodeKind(node).label
}
export function newLocalNode(type, nodes = [], position = { x: 160, y: 240 }) {
  const entry = LOCAL_NODE_TYPES.find(item => item.type === type)
  if (!entry) throw new Error('不支持的节点类型')
  let name = entry.label, number = 2
  while (nodes.some(node => node.name === name)) name = `${entry.label} ${number++}`
  return { id: flowId(), type, name, position: { ...position }, config: cloneFlowValue(entry.config) }
}
export function localStarterGraph() {
  const nodes = ['CSV_INPUT', 'FIELD_MAP', 'FILTER', 'SORT', 'OUTPUT'].map((type, index) => newLocalNode(type, [], { x: 160 + index * 440, y: 240 }))
  nodes[0].config.content = 'name,city,amount\n bob ,上海,30\n alice ,苏州,10\n 张三 ,苏州,20\n'
  nodes[1].config = { fields: [{ source: 'name', target: '姓名', trim: true }, { source: 'city', target: '城市', trim: true }, { source: 'amount', target: '金额', trim: true }], keepUnmapped: false }
  nodes[2].config = { field: '城市', operator: 'EQ', value: '苏州', valueType: 'STRING' }
  nodes[3].config = { fields: [{ field: '金额', direction: 'DESC', valueType: 'NUMBER' }] }
  return { nodes, connections: nodes.slice(1).map((node, index) => ({ id: flowId(), sourceId: nodes[index].id, targetId: node.id })) }
}
export function connectLocalNodes(graph, sourceId, targetId) {
  if (sourceId === targetId) throw new Error('节点不能连接到自身')
  const source = graph.nodes.find(item => item.id === sourceId), target = graph.nodes.find(item => item.id === targetId)
  if (!source || !target) throw new Error('请选择有效节点')
  if (source.type === 'OUTPUT' || localSource(target)) throw new Error('输入节点不能接收上游连接，输出节点不能再连接下游')
  if (graph.connections.some(item => item.sourceId === sourceId && item.targetId === targetId)) throw new Error('这两个节点已经连接')
  if (target.type !== 'UNION' && graph.connections.some(item => item.targetId === targetId)) throw new Error('多个输入请先通过“合并数据流”节点汇合')
  const visited = new Set(), pending = [targetId]
  while (pending.length) { const id = pending.pop(); if (id === sourceId) throw new Error('流程不能形成循环'); if (visited.has(id)) continue; visited.add(id); for (const edge of graph.connections) if (edge.sourceId === id) pending.push(edge.targetId) }
  return { id: flowId(), sourceId, targetId }
}
export function localResultFields(step) {
  return step?.fields?.length ? step.fields : [...new Set((step?.sampleRows || []).flatMap(row => Object.keys(row)))]
}
