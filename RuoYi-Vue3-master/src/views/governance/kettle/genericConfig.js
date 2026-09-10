// The DOM is the source of truth. Describing controls never rebuilds or mutates it.
const labels = {
  fields: '字段配置', field: '字段', meta: '类型转换', remove: '移除字段', name: '名称', type: '类型', value: '值',
  rename: '重命名', length: '长度', precision: '精度', format: '格式', encoding: '编码', separator: '分隔符',
  enclosure: '包围符', header: '包含表头', footer: '包含表尾', filename: '文件名', file: '文件设置', path: '路径',
  connection: '数据库连接', schema: '数据库模式', table: '数据表', sql: 'SQL 语句', query: '查询',
  username: '用户名', password: '密码', servername: '服务器', server: '服务器', hostname: '主机名', port: '端口',
  directory: '目录', timeout: '超时', limit: '读取上限', batch_size: '批次大小', commit: '提交批次', use_batch: '批量写入',
  ascending: '升序', case_sensitive: '区分大小写', presorted: '已经排序', column_name: '数据库列', stream_name: '输入流字段',
  date_format_lenient: '宽松日期解析', lenient_string_to_number: '宽松数字解析', conversion_mask: '转换格式',
  parameters: '参数', parameter: '参数项', arguments: '参数传递', argument: '参数项', properties: '属性', property: '属性项',
  attributes: '扩展属性', attribute: '属性项', key: '键', mappings: '字段映射', mapping: '映射',
  lookup: '查找设置', keys: '匹配键', values: '返回值', cases: '分支', case: '分支项', target_step: '目标步骤',
  scripts: '脚本', script: '脚本内容', jsScripts: 'JavaScript 脚本', jsScript: '脚本项',
  jsScript_script: '脚本内容', jsScript_name: '脚本名称', jsScript_type: '脚本阶段',
  group: '分组', groups: '分组项', sorts: '排序', sort: '排序项', columns: '列', column: '列',
  enabled: '启用', include: '包含', exclude: '排除', trim_type: '空格处理', replace: '替换字段',
  decimal: '小数符号', group_symbol: '分组符号', currency: '货币符号', default: '默认值', default_value: '默认值'
}
const managed = new Set(['name', 'type', 'description', 'GUI', 'xloc', 'yloc', 'draw', 'nr', 'copy_nr', 'copies', 'distribute', 'partitioning'])
const rowNames = new Set(['field', 'meta', 'remove', 'column', 'key', 'value', 'parameter', 'argument', 'attribute', 'property', 'mapping', 'rule', 'case', 'jsScript', 'item', 'entry', 'sort', 'group'])
const booleans = new Set(['enabled', 'ascending', 'case_sensitive', 'presorted', 'use_batch', 'header', 'footer', 'replace', 'parallel', 'lazy_conversion', 'include_filename', 'rename_file_name', 'add_date', 'add_time', 'do_not_open_new_file_init', 'date_format_lenient', 'lenient_string_to_number'])
const containers = new Set(['fields', 'parameters', 'arguments', 'properties', 'attributes', 'columns', 'mappings', 'cases', 'jsScripts'])
const keys = new WeakMap()
const templates = new WeakMap()
let sequence = 0
export const children = element => Array.from(element?.childNodes || []).filter(node => node.nodeType === 1)
export const nodeKey = element => { if (!keys.has(element)) keys.set(element, `native-${++sequence}`); return keys.get(element) }
export const directText = element => Array.from(element?.childNodes || []).filter(node => node.nodeType === 3 || node.nodeType === 4).map(node => node.data).join('')
export const nativeLabel = name => {
  for (const key of [name, name?.toLowerCase()]) if (Object.prototype.hasOwnProperty.call(labels, key)) return labels[key]
  return name || '文本内容'
}
const privateAttribute = name => name.startsWith('data-rynew-') || name === 'xmlns' || name.startsWith('xmlns:')
const secretName = name => /password|passwd|secret|token|access.?key|private.?key|credential|jaas/i.test(name || '') || name?.toLowerCase() === 'pwd'
function secretElement(element) {
  for (let node = element; node; node = node.parentElement) if (secretName(node.tagName)) return true
  return ['value', 'attribute'].includes(element?.tagName) && children(element.parentElement).some(node => ['name', 'code', 'key'].includes(node.tagName) && secretName(directText(node)))
}
const rootNode = element => ['step', 'entry'].includes(element?.tagName) && (!element.parentElement || element.parentElement.tagName === 'transformation' || (element.parentElement.tagName === 'entries' && element.parentElement.parentElement?.tagName === 'job'))

export function parseTemplate(template, element) {
  if (!template) return null
  if (template.nodeType === 1) return templateRoot(template, element)
  if (typeof template !== 'string' || /<!DOCTYPE|<!ENTITY/i.test(template)) throw new Error('原生配置模板包含不支持的 XML 声明')
  const cached = element && templates.get(element)
  if (cached?.source === template) return cached.root
  const doc = new DOMParser().parseFromString(`<native>${template.replace(/<\?xml[^?]*\?>/g, '')}</native>`, 'application/xml')
  if (doc.getElementsByTagName('parsererror').length) throw new Error('原生配置模板无法解析')
  const root = templateRoot(doc.documentElement, element)
  if (element) templates.set(element, { source: template, root })
  return root
}
function templateRoot(root, element) {
  while (root.tagName === 'native' && children(root).length === 1 && children(root)[0].tagName === 'native') root = children(root)[0]
  return root.tagName === element?.tagName ? root : children(root).find(node => node.tagName === element?.tagName) || root
}
export function fieldType(name, value, templateValue = '', element = null) {
  if (secretName(name) || secretElement(element) || value.startsWith('__RYNEW_SECRET_')) return 'password'
  const literal = value || templateValue
  if (booleans.has(name) && ['Y', 'N'].includes(literal)) return 'boolean'
  if (booleans.has(name) && ['true', 'false'].includes(literal)) return 'truefalse'
  if (/sql|query|script|expression/i.test(name)) return 'code'
  if (value.includes('\n') || value.length > 160 || Array.from(element?.childNodes || []).some(node => node.nodeType === 4)) return 'textarea'
  return 'text' // Do not invent number, enum or boolean encodings for unknown plugin parameters.
}
function scalar(parent, element, template, attribute = null, ownText = false) {
  const name = attribute ? attribute.name : ownText ? '#text' : (element || template).tagName
  const value = attribute ? attribute.value : directText(element)
  const type = fieldType(name, value, directText(template), element || template)
  return { key: attribute ? `@${name}` : name, name, label: attribute ? `${nativeLabel(name)}（属性）` : nativeLabel(name === '#text' ? '' : name), type, parent, element, template, attribute, ownText }
}
export function describe(element, templateXml = null) {
  if (!element) return { fields: [], groups: [] }
  const template = parseTemplate(templateXml, element), actual = children(element), hints = children(template)
  const fields = [], groups = [], names = [...new Set([...actual, ...hints].map(node => node.tagName))]
  for (const attr of Array.from(element.attributes || [])) if (!privateAttribute(attr.name)) fields.push(scalar(element, element, null, attr))
  if (actual.length && directText(element).trim()) fields.push(scalar(element, element, null, null, true))
  for (const name of names) {
    if (rootNode(element) && managed.has(name)) continue
    const rows = actual.filter(node => node.tagName === name), templates = hints.filter(node => node.tagName === name), hint = templates[0] || null
    const complex = [...rows, ...templates].some(node => children(node).length)
    const repeating = rows.length > 1 || templates.length > 1 || (!rootNode(element) && rowNames.has(name) && (hint || rows[0]) && (complex || element.tagName.toLowerCase() === `${name.toLowerCase()}s`))
    if (repeating) {
      const rowTemplate = hint || rows[0] || null
      const columns = [], seen = new Set()
      for (const row of [rowTemplate, ...rows].filter(Boolean)) {
        for (const field of describeRow(row)) if (!seen.has(field.key)) { columns.push({ key: field.key, label: field.label, template: field.element, attribute: field.attribute }); seen.add(field.key) }
      }
      groups.push({ key: `${nodeKey(element)}:${name}`, name, label: nativeLabel(name), kind: 'rows', parent: element, rows, template: rowTemplate, columns, nativeTemplate: Boolean(hint) })
    } else if (complex || containers.has(name)) groups.push({ key: `${nodeKey(element)}:${name}`, name, label: nativeLabel(name), kind: 'group', parent: element, element: rows[0] || null, template: hint })
    else fields.push(scalar(element, rows[0] || null, hint))
  }
  return { fields, groups }
}
function describeRow(row) {
  const result = []
  for (const attr of Array.from(row.attributes || [])) if (!privateAttribute(attr.name)) result.push(scalar(row, row, null, attr))
  if (!children(row).length) result.push(scalar(row, row, null, null, true))
  else for (const node of children(row)) if (!children(node).length) result.push(scalar(row, node, null))
  return result
}
export function rowField(row, column, template) {
  if (column.key === '#text') return scalar(row, row, template, null, true)
  if (column.key.startsWith('@')) {
    const name = column.key.slice(1), attr = row.getAttributeNode(name)
    return attr ? scalar(row, row, template, attr) : { key: column.key, name, label: column.label, type: fieldType(name, ''), parent: row, element: row, missingAttribute: name, templateAttribute: template?.getAttributeNode(name) || column.attribute }
  }
  return scalar(row, children(row).find(node => node.tagName === column.key) || null, children(template).find(node => node.tagName === column.key) || column.template)
}
export const fieldValue = field => field.attribute ? field.attribute.value : field.missingAttribute ? '' : directText(field.element)
const editable = options => { if (options?.readonly) throw new Error('当前配置只读') }
export function writeField(field, value, options) {
  editable(options); value = String(value ?? '')
  if (field.attribute) { field.attribute.value = value; return }
  if (field.missingAttribute) {
    if (field.templateAttribute) { const attr = field.parent.ownerDocument.importNode(field.templateAttribute, true); attr.value = value; field.parent.setAttributeNodeNS(attr) }
    else field.parent.setAttribute(field.missingAttribute, value)
    return
  }
  let target = field.element
  if (!target) { if (!field.template) throw new Error('该参数没有可用的原生结构'); target = field.parent.ownerDocument.importNode(field.template, true); sanitizeClone(target); field.parent.appendChild(target) }
  if (children(target).length && !field.ownText) throw new Error('复合配置不能作为普通文本覆盖')
  const text = Array.from(target.childNodes).filter(node => node.nodeType === 3 || node.nodeType === 4)
  if (text.length) { text[0].data = value; for (const remainder of text.slice(1)) remainder.data = '' }
  else target.appendChild(target.ownerDocument.createTextNode(value))
}
function sanitizeClone(element) {
  for (const node of [element, ...Array.from(element.getElementsByTagName('*'))]) {
    for (const attr of Array.from(node.attributes || [])) {
      if (attr.name === 'data-rynew-id' || attr.name.startsWith('data-rynew-secret-')) node.removeAttributeNode(attr)
      else if (secretName(attr.name) || attr.value.startsWith('__RYNEW_SECRET_')) attr.value = ''
    }
    if (!children(node).length && (secretElement(node) || directText(node).startsWith('__RYNEW_SECRET_'))) {
      for (const text of Array.from(node.childNodes).filter(n => n.nodeType === 3 || n.nodeType === 4)) text.data = ''
    }
  }
}
export function addRow(group, options) {
  editable(options)
  if (!group.template) throw new Error('此集合没有可用的原生行模板')
  const row = group.parent.ownerDocument.importNode(group.template, true); sanitizeClone(row)
  const matches = children(group.parent).filter(node => node.tagName === group.name), previous = matches[matches.length - 1]
  group.parent.insertBefore(row, previous ? previous.nextSibling : null)
  return row
}
export function removeRow(group, row, options) {
  editable(options)
  if (row?.parentNode !== group.parent || row.tagName !== group.name) throw new Error('所选行不属于当前集合')
  group.parent.removeChild(row)
}
export function addGroup(group, options) {
  editable(options)
  if (children(group.parent).some(node => node.tagName === group.name)) throw new Error('该配置组已存在')
  if (!group.template) throw new Error('该配置组没有可用的原生模板')
  const element = group.parent.ownerDocument.importNode(group.template, true); sanitizeClone(element); group.parent.appendChild(element); return element
}
