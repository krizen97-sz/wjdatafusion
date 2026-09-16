import test from 'node:test'
import assert from 'node:assert/strict'
import { fieldDiagnostics, groupTools, toolCapability, validationFeedback } from '../kettle/workbenchRules.js'
import { nodeFieldGroups, nodeSchemas, schemaFor } from '../kettle/nodeSchemas.js'

const plugin = (id, extras = {}) => ({ id, name: id, kind: 'step', loadable: true, executable: true, ...extras })
const ids = groups => groups.flatMap(group => group.items.map(item => item.id))

test('common tools hide unready catalog entries while retaining native sorting', () => {
  const entries = [plugin('CsvInput'), plugin('SortRows'), plugin('ExcelInput'), plugin('TableInput', { loadable: false, executable: false }), plugin('FTP_PUT', { executable: false })]
  assert.deepEqual(ids(groupTools(entries)), ['CsvInput', 'SortRows'])
  assert.deepEqual(ids(groupTools(entries, { scope: 'available' })), ['CsvInput', 'SortRows', 'ExcelInput'])
  assert.equal(ids(groupTools(entries, { scope: 'catalog' })).length, 5)
})

test('search discovers non-common executable tools without silently opening disabled ones', () => {
  const entries = [plugin('ExcelInput', { aliases: ['XLSInput'], categoryLabel: '输入', label: '电子表格输入' }), plugin('CsvInput', { loadable: false, executable: false, status: 'LOAD_FAILED' })]
  for (const search of ['excel', ' XLSINPUT ', '电子表格']) assert.deepEqual(ids(groupTools(entries, { search })), ['ExcelInput'])
  assert.deepEqual(ids(groupTools(entries, { search: 'csv' })), [])
  assert.deepEqual(ids(groupTools(entries, { search: 'csv', scope: 'catalog' })), ['CsvInput'])
  assert.deepEqual(ids(groupTools(entries, { search: '  ' })), [])
})

test('capability labels distinguish forms, execution gates, load failure and offline inventory', () => {
  assert.equal(toolCapability(plugin('SortRows')).label, '专用表单')
  assert.equal(toolCapability(plugin('ExcelInput')).label, '原生参数')
  assert.equal(toolCapability(plugin('SHELL', { executable: false, executionSupported: false })).label, '未开放')
  assert.equal(toolCapability(plugin('CsvInput', { executable: false, loadable: false, status: 'LOAD_FAILED' })).label, '加载失败')
  assert.equal(toolCapability(plugin('CsvInput', { executable: false, loadable: false, status: 'DISCOVERED' })).label, '仅目录')
  assert.equal(toolCapability(plugin('CsvInput'), false).label, '服务未连接')
  assert.match(toolCapability(plugin('CsvInput')).detail, /仍需/)
})

test('loaded metadata does not become successful field discovery even when valid is true', () => {
  const result = { valid: true, metadataLoaded: true, fieldsRequested: true, fieldsResolved: false, validationScope: 'metadata-only', fieldDiagnostics: [{ node: '数据库输入', direction: 'output', errorClass: 'KettleStepException', message: '连接未配置' }] }
  const feedback = validationFeedback(result)
  assert.equal(feedback.type, 'warning')
  assert.match(feedback.title, /未完成/)
  assert.equal(feedback.diagnostics[0].node, '数据库输入')
  assert.equal(feedback.diagnostics[0].message, '连接未配置')
})

test('explicit native diagnostics outrank inconsistent success flags and keep input/output separate', () => {
  const result = { valid: true, fieldsRequested: true, fieldsResolved: true, fieldDiagnostics: [{ node: '关联', direction: 'input', errorClass: 'KettleStepException', message: '上游字段不可用' }], nodes: [{ name: '关联', inputFieldError: 'KettleStepException', inputFieldErrorMessage: '旧消息', fieldError: 'SQLException', fieldErrorMessage: '目标连接不可达' }] }
  const feedback = validationFeedback(result)
  assert.equal(feedback.type, 'warning')
  assert.deepEqual(feedback.diagnostics.map(row => [row.directionLabel, row.message]), [['输入字段', '上游字段不可用'], ['输出字段', '目标连接不可达']])
})

test('native metadata error payload remains an error with legacy node diagnostics supported', () => {
  const result = { valid: false, metadataLoaded: true, fieldsRequested: true, fieldsResolved: false, validationScope: 'metadata-only', nodes: [{ name: '表输入', fieldError: 'KettleStepException', fieldErrorMessage: '无法取得查询字段' }] }
  assert.equal(validationFeedback(result).type, 'error')
  assert.deepEqual(fieldDiagnostics(result), [{ node: '表输入', direction: 'output', directionLabel: '输出字段', errorClass: 'KettleStepException', message: '无法取得查询字段' }])
})

test('job structure checking and XML-only loading never claim field discovery', () => {
  const job = { valid: true, metadataLoaded: true, fieldsRequested: false, validationScope: 'job-structure', kind: 'job', nodes: [] }
  assert.equal(validationFeedback(job, 'job').title, '作业结构检查通过')
  assert.match(validationFeedback(job, 'job').description, /尚未执行/)
  for (const result of [{ valid: true }, { valid: true, fieldsRequested: false, validationScope: 'xml-load' }, undefined]) {
    assert.equal(validationFeedback(result).type, 'info')
    assert.doesNotMatch(validationFeedback(result).title, /已取得/)
  }
})

test('successful field metadata remains distinct from a tested execution result', () => {
  const feedback = validationFeedback({ valid: true, metadataLoaded: true, fieldsRequested: true, fieldsResolved: true, validationScope: 'metadata-only', fieldDiagnostics: [] })
  assert.equal(feedback.type, 'success')
  assert.equal(feedback.title, '已取得原引擎字段结构')
  assert.match(feedback.description, /尚未验证数据处理和写入结果/)
  assert.equal(validationFeedback({ valid: true, metadataLoaded: false, fieldsResolved: true }).type, 'error')
})

test('advanced presentation preserves every native schema field without changing definition or order', () => {
  for (const id of ['CsvInput', 'TextFileInput', 'TableInput', 'KafkaConsumer', 'KafkaProducer', 'TextFileOutput', 'TableOutput', 'FTP_PUT', 'SortRows']) {
    const original = JSON.stringify(nodeSchemas[id]), sourceFields = schemaFor(id).fields
    const grouped = nodeFieldGroups(id), merged = [...grouped.primary, ...grouped.advanced]
    assert.equal(new Set(merged.map(field => field.key)).size, sourceFields.length)
    assert.deepEqual(merged.map(field => field.key).sort(), sourceFields.map(field => field.key).sort())
    assert.ok(merged.every(field => sourceFields.includes(field)))
    assert.equal(JSON.stringify(nodeSchemas[id]), original)
  }
  assert.deepEqual(nodeFieldGroups('unknown'), { primary: [], advanced: [] })
})

test('destructive and delivery settings stay visible while tuning fields fold away', () => {
  const primary = id => nodeFieldGroups(id).primary.map(field => field.key)
  assert.ok(primary('TableOutput').includes('truncate'))
  assert.ok(primary('TableOutput').includes('ignore_errors'))
  for (const key of ['only_new', 'remove', 'rename']) assert.ok(primary('FTP_PUT').includes(key))
  assert.ok(primary('KafkaConsumer').includes('KAFKA/auto.commit.enable'))
  assert.ok(primary('TextFileOutput').includes('file/rename_file_name'))
  assert.ok(nodeFieldGroups('CsvInput').advanced.some(field => field.key === 'buffer_size'))
})

test('SortRows edits real native field names and preserves allocate(1) boolean defaults', () => {
  // Captured from SortRowsMeta in kettle-6.1.0.7.36.jar (native metadata readback).
  const nativeTemplateFields = { name: '', ascending: 'N', case_sensitive: 'N', presorted: 'N' }
  const schema = schemaFor('SortRows'), table = schema.tables[0]
  assert.equal(schema.initialTab, 'table-0')
  assert.equal(table.path, 'fields/field')
  assert.deepEqual(table.columns.map(column => column.key), Object.keys(nativeTemplateFields))
  assert.deepEqual(table.defaults, nativeTemplateFields)
  assert.deepEqual(nodeFieldGroups('SortRows').primary.map(field => field.key), ['unique_rows'])
  assert.deepEqual(table.columns.find(column => column.key === 'ascending').options.map(option => option.value), ['Y', 'N'])
})
