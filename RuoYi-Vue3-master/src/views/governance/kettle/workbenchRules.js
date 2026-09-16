import { hasDedicatedForm, schemaFor } from './nodeSchemas.js'

// This is a discoverability shortlist, not a claim of business acceptance.
const commonTools = new Set(['CsvInput', 'TextFileInput', 'TableInput', 'KafkaConsumer', 'JsonInput', 'RowGenerator', 'ScriptValueMod', 'DBLookup', 'SelectValues', 'SortRows', 'SwitchCase', 'FilterRows', 'Constant', 'Dummy', 'TextFileOutput', 'TableOutput', 'InsertUpdate', 'KafkaProducer', 'WriteToLog', 'SPECIAL', 'TRANS', 'FTP_PUT'])

export function toolCapability(plugin, workerAvailable = true) {
  if (!workerAvailable) return { label: '服务未连接', type: 'info', detail: '运行服务未连接，尚未确认此工具在当前环境的执行能力。' }
  if (plugin.loadable !== true) return plugin.status === 'LOAD_FAILED'
    ? { label: '加载失败', type: 'warning', detail: '原引擎未能加载此插件，请检查插件及依赖；当前不能新增执行节点。' }
    : { label: '仅目录', type: 'info', detail: '原包中已发现此工具，当前运行服务尚未提供对应插件；不能新增执行节点。' }
  if (plugin.executable !== true) return { label: '未开放', type: 'info', detail: '插件可以加载，但当前服务尚未开放此工具的执行。' }
  return hasDedicatedForm(plugin.id)
    ? { label: '专用表单', type: 'info', detail: '已提供专用配置表单并开放执行；仍需结合实际数据验证配置和结果。' }
    : { label: '原生参数', type: 'info', detail: '已开放执行，需通过原生参数配置；尚无专用表单，不代表业务已经验收。' }
}

export function groupTools(entries, { scope = 'common', search = '', kind = 'transformation' } = {}) {
  const query = search.trim().toLowerCase(), groups = new Map()
  for (const plugin of entries) {
    if (scope !== 'catalog' && (plugin.executable !== true || plugin.loadable !== true)) continue
    if (!query && scope === 'common' && !commonTools.has(plugin.id)) continue
    if (query && ![plugin.id, plugin.name, plugin.label, plugin.category, plugin.categoryLabel, ...(plugin.aliases || [])].filter(Boolean).join(' ').toLowerCase().includes(query)) continue
    let group = schemaFor(plugin.id).group
    if (group === '其他') { const category = String(plugin.categoryLabel || plugin.category || ''); group = kind === 'job' ? '作业' : /Input|输入/i.test(category) ? '输入' : /Output|输出/i.test(category) ? '输出' : '处理' }
    if (!groups.has(group)) groups.set(group, [])
    groups.get(group).push(plugin)
  }
  return ['输入', '处理', '输出', '作业'].filter(name => groups.has(name)).map(name => ({ name, items: groups.get(name) }))
}

export function fieldDiagnostics(result) {
  const diagnostics = [], seen = new Set()
  const add = item => {
    if (!item || typeof item !== 'object') return
    const node = String(item.node || ''), direction = item.direction === 'input' ? 'input' : item.direction === 'output' ? 'output' : ''
    const key = JSON.stringify([node, direction])
    if (seen.has(key)) return
    seen.add(key)
    diagnostics.push({ node, direction, directionLabel: direction === 'input' ? '输入字段' : direction === 'output' ? '输出字段' : '字段结构', errorClass: String(item.errorClass || ''), message: String(item.message || item.errorClass || '未取得字段结构，请检查此节点及上游配置') })
  }
  for (const item of Array.isArray(result?.fieldDiagnostics) ? result.fieldDiagnostics : []) add(item)
  for (const node of Array.isArray(result?.nodes) ? result.nodes : []) {
    if (node.inputFieldError) add({ node: node.name, direction: 'input', errorClass: node.inputFieldError, message: node.inputFieldErrorMessage })
    if (node.fieldError) add({ node: node.name, direction: 'output', errorClass: node.fieldError, message: node.fieldErrorMessage })
  }
  return diagnostics
}

export function validationFeedback(result, kind = 'transformation') {
  const diagnostics = fieldDiagnostics(result)
  if (result?.valid === false || result?.metadataLoaded === false) return { type: 'error', title: diagnostics.length ? `字段获取未完成：${diagnostics.length} 项问题` : '原引擎检查未通过', description: result.error || '请检查下方诊断和节点配置；本次检查未执行任务。', diagnostics }
  if (diagnostics.length || result?.fieldsResolved === false) return { type: 'warning', title: '原生结构已加载，字段获取未完成', description: '请定位问题节点后重新获取字段；未取得的字段不能作为预览或运行通过的依据。', diagnostics }
  if (result?.valid === true && result.validationScope === 'job-structure' && kind === 'job') return { type: 'success', title: '作业结构检查通过', description: '仅检查作业结构，尚未执行子转换、FTP 或其他作业节点。', diagnostics }
  if (result?.valid === true && result.fieldsResolved === true && result.fieldsRequested !== false) return { type: 'success', title: '已取得原引擎字段结构', description: '本次仅获取字段元数据，尚未验证数据处理和写入结果。', diagnostics }
  return { type: 'info', title: '尚未确认字段结构', description: '当前响应没有字段解析成功的确认，请重新获取字段或查看运行服务状态。', diagnostics }
}
