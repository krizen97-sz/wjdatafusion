import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { parse } from '@vue/compiler-sfc'
import { WORKBOOK_FIELDS, setWorkbookValue, workbookPayload } from '../components/equipmentWorkbook.rules.js'
import { canEditWorkbookCell, prepareWorkbookRange, workbookCopyValue, workbookEditValue } from '../components/equipmentWorkbook.grid.js'

const field = prop => WORKBOOK_FIELDS.find(item => item.prop === prop)
const options = { rooms: [{ roomId: 1, roomName: '一号机房' }], cabinets: [{ roomId: 1, cabinetId: 2, cabinetNo: 'A01', uCapacity: 45 }], platforms: [{ platformId: 3, platformName: '子平台', platformLevel: 'SUB' }, { platformId: 4, platformName: '主平台', platformLevel: 'MAIN' }] }
const row = (id = 1, extra = {}) => ({ deviceKey: 'SERVER:' + id, sourceType: 'SERVER', sourceId: id, assetName: '设备' + id, sshPort: 22, canEdit: true, _editing: true, _canPlace: true, _canBind: true, _secrets: {}, _passwordInputs: {}, ...extra })

test('range fill prepares all rows atomically, keeps draft save semantics and typed numbers', () => {
  const rows = [row(1), row(2)], originals = Object.fromEntries(rows.map(r => [r.deviceKey, { ...r }]))
  assert.throws(() => prepareWorkbookRange({ 0: { sshPort: '55555' }, 1: { sshPort: '65536' } }, rows, options), /65535/)
  assert.equal(rows[0].sshPort, 22)
  const changes = prepareWorkbookRange({ 0: { sshPort: '55555' }, 1: { sshPort: '55555' } }, rows, options)
  changes.forEach(c => setWorkbookValue(rows.find(r => r.deviceKey === c.key), c.field, c.value))
  assert.deepEqual(workbookPayload(1, originals, rows).rows.map(r => r.changes), [{ sshPort: 55555 }, { sshPort: 55555 }])
})

test('readonly, device type, placement, binding and credential permissions apply to range edits', () => {
  const rows = [row(1, { _canPlace: false, _canBind: false, rootPasswordEditable: false }), row(2, { canEdit: false }), row(3, { sourceType: 'HARDWARE', assetType: 'TERMINAL' })]
  const changes = prepareWorkbookRange({ 0: { assetTypeLabel: 'x', platformIds: '[3]', roomId: 1, rootPassword: 'x' }, 1: { sshPort: 30 }, 2: { sshPort: 40, portCount: 5 } }, rows, options)
  assert.deepEqual(changes, [])
  assert.equal(canEditWorkbookCell(row(1, { _saving: true }), field('sshPort')), false)
})

test('location and platform copies keep typed relationships and dependent resets', () => {
  const rows = [row()]
  const changes = prepareWorkbookRange({ 0: { platformIds: '[3]', roomId: '一号机房', cabinetId: 'A01', rackUStart: '2', rackUEnd: '4' } }, rows, options)
  changes.forEach(c => setWorkbookValue(rows[0], c.field, c.value))
  assert.deepEqual(rows[0].platformIds, [3])
  assert.deepEqual([rows[0].roomId, rows[0].cabinetId, rows[0].rackUStart, rows[0].rackUEnd], [1, 2, 2, 4])
  assert.throws(() => prepareWorkbookRange({ 0: { platformIds: '[4]' } }, rows, options), /子平台/)
  assert.throws(() => prepareWorkbookRange({ 0: { cabinetId: 99 } }, rows, options), /机柜/)
  assert.throws(() => prepareWorkbookRange({ 0: { rackUEnd: 46 } }, rows, options), /45/)
})

test('masked passwords never copy as secrets and pasted placeholders are rejected', () => {
  const r = row(1, { rootPasswordConfigured: true, _plainValues: { rootPassword: 'fixture-secret' } })
  assert.throws(() => workbookCopyValue(r, field('rootPassword')), /明文/)
  r._showPasswords = true
  assert.equal(workbookCopyValue(r, field('rootPassword')), 'fixture-secret')
  setWorkbookValue(r, 'rootPassword', 'new-fixture')
  assert.equal(workbookEditValue(r, field('rootPassword')), 'new-fixture')
  assert.throws(() => prepareWorkbookRange({ 0: { rootPassword: '******' } }, [r], options), /占位符/)
  setWorkbookValue(r, 'rootPassword', null)
  assert.throws(() => workbookCopyValue(r, field('rootPassword')), /清空/)
  assert.equal(workbookCopyValue(row(1, { sourceType: 'HARDWARE' }), field('rootPassword')), '')
})

test('copying the same room or cabinet preserves existing rack placement', () => {
  const r = row(1, { roomId: 1, cabinetId: 2, rackUStart: 4, rackUEnd: 6 })
  const edits = prepareWorkbookRange({ 0: { roomId: '1', cabinetId: '2' } }, [r], options)
  edits.forEach(c => setWorkbookValue(r, c.field, c.value))
  assert.deepEqual([r.roomId, r.cabinetId, r.rackUStart, r.rackUEnd], [1, 2, 4, 6])
})

test('copied arrays are detached and boolean/status values round trip', () => {
  const r = row(1, { platformIds: [3], rootDirectLogin: true })
  const ids = workbookEditValue(r, field('platformIds'))
  ids.push(4)
  assert.deepEqual(r.platformIds, [3])
  assert.equal(workbookCopyValue(r, field('platformIds')), '[3]')
  const edits = prepareWorkbookRange({ 0: { rootDirectLogin: workbookCopyValue(r, field('rootDirectLogin')), status: '停用' } }, [r], options)
  assert.equal(edits.find(c => c.field === 'rootDirectLogin').value, true)
  assert.equal(edits.find(c => c.field === 'status').value, '1')
  assert.throws(() => prepareWorkbookRange({ 0: { status: '未知' } }, [r], options), /状态/)
  assert.throws(() => prepareWorkbookRange({ 99: { sshPort: 22 } }, [r], options), /失效/)
})

test('workbook uses native range editing with full-cell Element Plus editors', () => {
  const inventory = readFileSync(new URL('../components/EquipmentInventory.vue', import.meta.url), 'utf8')
  const cell = readFileSync(new URL('../components/EquipmentWorkbookCell.vue', import.meta.url), 'utf8')
  for (const source of [inventory, cell]) assert.deepEqual(parse(source).errors, [])
  assert.match(inventory, /rangeFill: true/)
  assert.match(inventory, /@beforerangeedit="editRange"/)
  assert.match(inventory, /@copy.capture="copyRange"/)
  assert.match(inventory, /event.clipboardData.setData\('text\/plain'/)
  assert.match(inventory, /@beforeautofill="autoFill"/)
  assert.match(cell, /Boolean\(props.save\)/)
  assert.match(cell, /props.close\(\)/)
  assert.doesNotMatch(cell, /<el-input[^>]*size="small"/)
})
