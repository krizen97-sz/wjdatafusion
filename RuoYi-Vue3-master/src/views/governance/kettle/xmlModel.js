// Keep the native document: editing one field must not discard unrecognised plugin configuration.
export const direct = (element, name) => Array.from(element?.children || []).filter(child => child.tagName === name)
export function at(element, path) { return path.split('/').filter(Boolean).reduce((node, name) => direct(node, name)[0], element) }
export const textAt = (element, path, fallback = '') => at(element, path)?.textContent ?? fallback
export function ensure(element, path) {
  return path.split('/').filter(Boolean).reduce((parent, name) => {
    let child = direct(parent, name)[0]
    if (!child) { child = parent.ownerDocument.createElement(name); parent.appendChild(child) }
    return child
  }, element)
}
export function setText(element, path, value) { ensure(element, path).textContent = String(value ?? '') }
export function xmlText(element) { return new XMLSerializer().serializeToString(element) }
export function parseXml(xml) {
  if (typeof xml !== 'string' || /<!DOCTYPE|<!ENTITY/i.test(xml)) throw new Error('文件包含不支持的XML声明')
  const doc = new DOMParser().parseFromString(xml, 'application/xml')
  if (doc.getElementsByTagName('parsererror').length || !['transformation', 'job'].includes(doc.documentElement.tagName)) throw new Error('请选择可解析的Kettle转换（.ktr）或作业（.kjb）')
  return doc
}
function uuid() {
  const bytes = new Uint8Array(16); crypto.getRandomValues(bytes)
  bytes[6] = (bytes[6] & 15) | 64; bytes[8] = (bytes[8] & 63) | 128
  const hex = Array.from(bytes, value => value.toString(16).padStart(2, '0')).join('')
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`
}
export function elementId(element) {
  if (!element.getAttribute('data-rynew-id')) element.setAttribute('data-rynew-id', uuid())
  return element.getAttribute('data-rynew-id')
}
export function emptyDocument(kind, name) {
  const doc = parseXml(kind === 'job' ? '<job><name/><entries/><hops/><parameters/></job>' : '<transformation><info><name/><directory>/</directory><trans_type>Normal</trans_type><parameters/></info><order/></transformation>')
  setText(doc.documentElement, kind === 'job' ? 'name' : 'info/name', name)
  doc.documentElement.setAttribute('data-rynew-timezone', 'Asia/Shanghai')
  return doc
}
const jobKind = doc => doc.documentElement.tagName === 'job'
const nodesContainer = doc => jobKind(doc) ? ensure(doc.documentElement, 'entries') : doc.documentElement
const nodeTag = doc => jobKind(doc) ? 'entry' : 'step'
const hopsContainer = doc => ensure(doc.documentElement, jobKind(doc) ? 'hops' : 'order')
export function graphFromXml(doc) {
  const isJob = jobKind(doc)
  const nodes = direct(nodesContainer(doc), nodeTag(doc)).map(element => ({
    id: elementId(element), name: textAt(element, 'name'), type: textAt(element, 'type'), pluginId: textAt(element, 'type'),
    role: 'PROCESSOR', editable: true, element, copyNr: textAt(element, 'nr', '0'),
    position: { x: Number(textAt(element, isJob ? 'xloc' : 'GUI/xloc', '0')) * 2, y: Number(textAt(element, isJob ? 'yloc' : 'GUI/yloc', '0')) * 2 },
    copies: textAt(element, 'copies', '1'), issues: []
  }))
  const connections = direct(hopsContainer(doc), 'hop').map(element => {
    const source = nodes.find(node => node.name === textAt(element, 'from') && (!isJob || node.copyNr === textAt(element, 'from_nr', '0')))
    const target = nodes.find(node => node.name === textAt(element, 'to') && (!isJob || node.copyNr === textAt(element, 'to_nr', '0')))
    let label = isJob ? textAt(element, 'unconditional') === 'Y' ? '无条件' : textAt(element, 'evaluation') === 'Y' ? '成功' : '失败' : '数据流'
    if (!isJob && source?.type === 'FilterRows') label = textAt(source.element, 'send_true_to') === target?.name ? '满足条件' : textAt(source.element, 'send_false_to') === target?.name ? '不满足条件' : label
    if (!isJob && source?.type === 'SwitchCase') {
      const matched = direct(at(source.element, 'cases'), 'case').filter(row => textAt(row, 'target_step') === target?.name).map(row => textAt(row, 'value'))
      label = matched.length ? matched.join(' / ') : textAt(source.element, 'default_target_step') === target?.name ? '默认' : label
    }
    if (textAt(element, 'enabled', 'Y') === 'N') label += ' · 已停用'
    return { id: elementId(element), sourceId: source?.id, targetId: target?.id, relationships: [label], enabled: textAt(element, 'enabled', 'Y') !== 'N', element }
  })
  return { kind: isJob ? 'job' : 'transformation', name: textAt(doc.documentElement, isJob ? 'name' : 'info/name'), nodes, connections }
}
export function setPosition(doc, node, position) {
  setText(node.element, jobKind(doc) ? 'xloc' : 'GUI/xloc', Math.round(position.x / 2))
  setText(node.element, jobKind(doc) ? 'yloc' : 'GUI/yloc', Math.round(position.y / 2))
}
export function addNode(doc, plugin, position = { x: 80, y: 100 }) {
  const node = doc.createElement(nodeTag(doc))
  const raw = plugin.defaultXmlBase64 ? fromBase64(plugin.defaultXmlBase64) : plugin.defaultXml || ''
  if (raw) {
    const parsed = new DOMParser().parseFromString(`<native>${raw.replace(/<\?xml[^?]*\?>/g, '')}</native>`, 'application/xml')
    if (parsed.getElementsByTagName('parsererror').length) throw new Error('原插件默认配置无法解析，请刷新工具目录')
    const wrapper = direct(parsed.documentElement, nodeTag(doc))[0]
    for (const child of Array.from((wrapper || parsed.documentElement).childNodes)) node.appendChild(doc.importNode(child, true))
  }
  const graph = graphFromXml(doc), base = plugin.label || plugin.name || plugin.id
  let name = base, index = 2
  while (graph.nodes.some(item => item.name === name)) name = `${base} ${index++}`
  setText(node, 'name', name); setText(node, 'type', plugin.id || plugin.pluginId)
  if (plugin.id === 'FTP_PUT') setText(node, 'localDirectory', '${WORK_DIR}')
  if (plugin.id === 'TextFileOutput') setText(node, 'file/name', '${WORK_DIR}/result')
  if (jobKind(doc)) { setText(node, 'nr', '0'); setText(node, 'draw', 'Y'); setText(node, 'parallel', 'N') }
  else { setText(node, 'copies', '1'); setText(node, 'distribute', 'Y'); setText(node, 'GUI/draw', 'Y') }
  nodesContainer(doc).appendChild(node)
  const result = { id: elementId(node), element: node }; setPosition(doc, result, position)
  return result.id
}
export function renameNode(doc, node, name) {
  name = name.trim()
  if (!name || name.length > 200) throw new Error('步骤名称不能为空且最多200字')
  if (graphFromXml(doc).nodes.some(item => item.id !== node.id && item.name === name && (!jobKind(doc) || item.copyNr === node.copyNr))) throw new Error('同一任务的步骤名称不能重复')
  const previous = node.name
  for (const hop of direct(hopsContainer(doc), 'hop')) for (const key of ['from', 'to']) if (textAt(hop, key) === previous && (!jobKind(doc) || textAt(hop, key + '_nr', '0') === node.copyNr)) setText(hop, key, name)
  if (!jobKind(doc)) for (const element of Array.from(doc.getElementsByTagName('*'))) if (['target_step', 'default_target_step', 'send_true_to', 'send_false_to'].includes(element.tagName) && element.textContent === previous) element.textContent = name
  setText(node.element, 'name', name)
}
export function removeNode(doc, node) {
  for (const edge of graphFromXml(doc).connections) if (edge.sourceId === node.id || edge.targetId === node.id) edge.element.remove()
  if (!jobKind(doc)) for (const element of Array.from(doc.getElementsByTagName('*'))) {
    if (['default_target_step', 'send_true_to', 'send_false_to'].includes(element.tagName) && element.textContent === node.name) element.textContent = ''
    if (element.tagName === 'target_step' && element.textContent === node.name) element.parentElement.remove()
  }
  node.element.remove()
}
export function connect(doc, sourceId, targetId, condition = 'unconditional') {
  const graph = graphFromXml(doc), source = graph.nodes.find(node => node.id === sourceId), target = graph.nodes.find(node => node.id === targetId)
  if (!source || !target || sourceId === targetId) throw new Error('请选择两个不同的有效节点')
  if (graph.connections.some(edge => edge.sourceId === sourceId && edge.targetId === targetId)) throw new Error('此连接已存在')
  const hop = doc.createElement('hop'); setText(hop, 'from', source.name); setText(hop, 'to', target.name); setText(hop, 'enabled', 'Y')
  if (jobKind(doc)) { setText(hop, 'from_nr', source.copyNr); setText(hop, 'to_nr', target.copyNr); setText(hop, 'unconditional', condition === 'unconditional' ? 'Y' : 'N'); setText(hop, 'evaluation', condition === 'failure' ? 'N' : 'Y') }
  hopsContainer(doc).appendChild(hop); return elementId(hop)
}
export function syncBranchConnections(doc, node) {
  if (!['FilterRows', 'SwitchCase'].includes(node.pluginId)) return
  const names = node.pluginId === 'FilterRows'
    ? [textAt(node.element, 'send_true_to'), textAt(node.element, 'send_false_to')]
    : [textAt(node.element, 'default_target_step'), ...rowsAt(node.element, 'cases/case').map(row => textAt(row, 'target_step'))]
  const targets = new Set(names.filter(Boolean)), graph = graphFromXml(doc)
  for (const name of targets) if (!graph.nodes.some(item => item.id !== node.id && item.name === name)) throw new Error(`分支目标“${name}”不存在或指向自身`)
  for (const edge of graph.connections.filter(item => item.sourceId === node.id)) {
    const target = graph.nodes.find(item => item.id === edge.targetId)
    if (!targets.has(target?.name)) edge.element.remove()
  }
  const existing = new Set(graphFromXml(doc).connections.filter(item => item.sourceId === node.id).map(item => item.targetId))
  for (const target of graph.nodes.filter(item => targets.has(item.name))) if (!existing.has(target.id)) connect(doc, node.id, target.id)
}
export function removeConnection(doc, edge) {
  const graph = graphFromXml(doc), source = graph.nodes.find(node => node.id === edge.sourceId), target = graph.nodes.find(node => node.id === edge.targetId)
  if (source && target) {
    for (const path of ['send_true_to', 'send_false_to', 'default_target_step']) if (textAt(source.element, path) === target.name) setText(source.element, path, '')
    for (const row of rowsAt(source.element, 'cases/case')) if (textAt(row, 'target_step') === target.name) row.remove()
  }
  edge.element.remove()
}
export function rowsAt(element, path) { const parts = path.split('/'); const tag = parts.pop(); return direct(at(element, parts.join('/')) || (parts.length ? null : element), tag) }
export function appendRow(element, path, values = {}) {
  const parts = path.split('/'), tag = parts.pop(), parent = ensure(element, parts.join('/')), row = element.ownerDocument.createElement(tag)
  for (const [name, value] of Object.entries(values)) setText(row, name, value)
  parent.appendChild(row); return row
}
export function toBase64(value) {
  const bytes = new TextEncoder().encode(value); let text = ''
  for (let start = 0; start < bytes.length; start += 8192) text += String.fromCharCode(...bytes.subarray(start, start + 8192))
  return btoa(text)
}
export function fromBase64(value) { return new TextDecoder().decode(Uint8Array.from(atob(value), character => character.charCodeAt(0))) }
