const STANDARD = 'org.apache.nifi.processors.standard.'
const UPDATE = 'org.apache.nifi.processors.attributes.UpdateAttribute'

/** Browser authoring exposes only processors supported by the isolated test engine. */
export const NODE_KINDS = Object.freeze([
  { key: 'source', label: '样本输入', type: `${STANDARD}GenerateFlowFile`, role: 'PROCESSOR', category: 'source', icon: 'DocumentAdd', description: '从测试区读取 JSON 样本，作为流程的起点。', defaults: { 'Batch Size': '1', 'Data Format': 'Text', 'Unique FlowFiles': 'false' }, relationships: ['success'] },
  { key: 'jsonpath', label: 'JSON 解析', type: `${STANDARD}EvaluateJsonPath`, role: 'PROCESSOR', category: 'transform', icon: 'Connection', description: '按 JSONPath 提取字段，保留原始 JSON 内容。', defaults: { Destination: 'flowfile-attribute', 'Return Type': 'scalar', 'sample.value': '$.message' }, relationships: ['matched', 'unmatched', 'failure'] },
  { key: 'route', label: '条件分流', type: `${STANDARD}RouteOnAttribute`, role: 'PROCESSOR', category: 'route', icon: 'Share', description: '根据字段条件选择分支，未命中数据进入默认分支。', defaults: { 'Routing Strategy': 'Route to Property name', accepted: '${sample.value:isEmpty():not()}' }, relationships: ['accepted', 'unmatched'] },
  { key: 'attributes', label: '字段属性', type: UPDATE, role: 'PROCESSOR', category: 'transform', icon: 'SetUp', description: '设置 sample.* 字段属性，供后续条件和转换使用。', defaults: {}, relationships: ['success'] },
  { key: 'lookup', label: '快照查表', type: 'com.hm.governance.nifi.JsonLookupSnapshot', role: 'PROCESSOR', category: 'lookup', icon: 'Coin', description: '用固定字典快照执行多条件查表，支持默认值和重复匹配处理。', defaults: { 'Lookup Rows': '[]', 'Match Fields': '[]', 'Return Fields': '[]', 'Missing Match': 'KEEP', 'Multiple Matches': 'FAIL' }, relationships: ['success', 'empty', 'failure'] },
  { key: 'record-transform', label: '字段处理', type: 'com.hm.governance.nifi.JsonRecordTransform', role: 'PROCESSOR', category: 'transform', icon: 'Operation', description: '用受控规则提取字段、处理字符串、广播写回及过滤记录。', defaults: { Operations: '[{"op":"trim","input":"/message","output":"message","mode":"BOTH"}]' }, relationships: ['success', 'empty', 'failure'] },
  { key: 'jolt', label: 'JSON 转换', type: 'org.apache.nifi.processors.jolt.JoltTransformJSON', role: 'PROCESSOR', category: 'transform', icon: 'Switch', description: '通过内联转换规则重组 JSON 字段和结构。', defaults: { 'Jolt Transform': 'jolt-transform-chain', 'Jolt Specification': '[\n  { "operation": "shift", "spec": { "*": "&" } }\n]', 'JSON Source': 'FLOW_FILE' }, relationships: ['success', 'failure'] },
  { key: 'delimited', label: '协议文本', type: 'com.hm.governance.nifi.DelimitedTextWriter', role: 'PROCESSOR', category: 'output', icon: 'Document', description: '将 JSON 数组编码为文本分片，配置字段顺序、分隔符和表头。', defaults: { 'Field Order': 'message,picture', 'Delimiter Hex': '7C 1F', 'Include Header': 'true', 'Split Limit': '75', 'Count Basis': 'DATA_RECORDS', 'Maximum File Age Millis': '0', 'Arrival Time Field': null, 'Filename Prefix': 'dataset' }, relationships: ['success', 'empty', 'failure'] },
  { key: 'capture', label: '结果输出', type: UPDATE, role: 'CAPTURE', category: 'output', icon: 'DataAnalysis', description: '观察到达终点的内容、字段和执行结果。', defaults: {}, relationships: [] }
].map(kind => Object.freeze({ ...kind, defaults: Object.freeze(kind.defaults), relationships: Object.freeze(kind.relationships) })))

const UNKNOWN = Object.freeze({ key: 'unknown', label: '未适配节点', category: 'transform', icon: 'Operation', description: '此节点尚未接入浏览器配置面板。', defaults: Object.freeze({}), relationships: Object.freeze([]) })
const FIXED_ROUTE_PROPERTIES = new Set(['Routing Strategy'])

export function nodeKind(node) {
  return NODE_KINDS.find(kind => kind.type === node?.type && kind.role === (node?.role || 'PROCESSOR')) || UNKNOWN
}

export function nodeSummary(node) {
  const properties = node?.properties || {}
  switch (nodeKind(node).key) {
    case 'source': return '测试区注入 JSON 样本'
    case 'jsonpath': return `${dynamicEntries(node).length} 个提取字段`
    case 'route': return `${dynamicEntries(node).length} 个条件分支`
    case 'attributes': return `${dynamicEntries(node).length} 个字段属性`
    case 'lookup': return '多条件 · 批次快照'
    case 'record-transform': return '提取 · 派生 · 过滤'
    case 'jolt': {
      try {
        const spec = JSON.parse(properties['Jolt Specification'] || 'null')
        return Array.isArray(spec) ? `${spec.length} 条转换规则` : '待配置转换规则'
      } catch { return '转换规则待检查' }
    }
    case 'delimited': {
      const fields = String(properties['Field Order'] || '').split(',').filter(field => field.trim()).length
      const limit = properties['Split Limit'] ?? '75'
      return `${fields} 个字段 · ${String(limit) === '0' ? '不按条数分片' : `每片计数 ${limit}`}`
    }
    case 'capture': return '查看内容与运行结果'
    default: return '待适配'
  }
}

function isDynamicProperty(kind, key) {
  if (kind.key === 'route') return !FIXED_ROUTE_PROPERTIES.has(key)
  return ['jsonpath', 'attributes'].includes(kind.key) && key.startsWith('sample.')
}

function dynamicEntries(node) {
  const kind = nodeKind(node)
  return Object.entries(node?.properties || {}).filter(([key]) => isDynamicProperty(kind, key))
}

export function createNodeDraft(node) {
  return {
    name: node?.name || '',
    properties: { ...(node?.properties || {}) },
    rows: dynamicEntries(node).map(([name, value]) => ({ name, value }))
  }
}

/** Return the full expected map; the server computes deletions from omitted keys. */
export function nodeDraftPayload(node, draft) {
  const kind = nodeKind(node)
  const properties = Object.fromEntries([
    ...Object.entries(draft.properties).filter(([key]) => !isDynamicProperty(kind, key)),
    ...draft.rows.map(row => [row.name, row.value])
  ])
  return { name: draft.name, properties }
}

export function nodeDraftChanged(node, draft) {
  if (!node) return false
  const current = nodeDraftPayload(node, draft)
  const original = node.properties || {}
  const keys = Object.keys(current.properties)
  return current.name !== (node.name || '') || keys.length !== Object.keys(original).length || keys.some(key => !Object.prototype.hasOwnProperty.call(original, key) || original[key] !== current.properties[key])
    || new Set(draft.rows.map(row => row.name)).size !== draft.rows.length
}

export function nodeDraftRowsError(node, rows) {
  const kind = nodeKind(node)
  if (!['jsonpath', 'attributes', 'route'].includes(kind.key)) return ''
  const names = rows.map(row => row.name)
  if (names.some(name => !name.trim())) return '请填写字段或分支名称'
  if (new Set(names).size !== names.length) return '字段或分支名称不能重复'
  if (kind.key === 'route' && names.some(name => FIXED_ROUTE_PROPERTIES.has(name) || ['unmatched', 'failure'].includes(name))) return '分支名称不能使用引擎保留名称'
  if (kind.key !== 'route' && names.some(name => !/^sample\.[A-Za-z0-9_.-]{1,64}$/.test(name))) return '字段名需使用 sample. 前缀，后接字母、数字、点、短横线或下划线'
  return ''
}
