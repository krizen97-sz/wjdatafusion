import assert from 'node:assert/strict'
import test from 'node:test'
import { readFileSync } from 'node:fs'
import { parse } from '@vue/compiler-sfc'
import { computed, reactive } from 'vue'
import { setWorkbookValue, validateWorkbookDraft, workbookPayload, workbookRowChanges } from '../components/equipmentWorkbook.rules.js'

function fixture(sourceType = 'SERVER') {
  const original = {
    deviceKey: sourceType + ':1', sourceType, sourceId: 1, revision: 'opaque-revision',
    assetName: '测试设备', ipAddress: '192.0.2.1', sshPort: 22, osType: 'Linux',
    status: '0', platformIds: [10], roomId: 1, cabinetId: 2, rackUStart: 10, rackUEnd: 12,
    otherUsername: 'ops'
  }
  return { original, draft: { ...original, platformIds: [10], canEdit: true, _secrets: {}, _passwordInputs: {} } }
}
const options = { rooms: [{ roomId: 1 }], cabinets: [{ roomId: 1, cabinetId: 2, uCapacity: 45 }],
  platforms: [{ platformId: 10, platformLevel: 'SUB' }, { platformId: 11, platformLevel: 'MAIN' }] }

test('root direct login is off by default, editable for servers only and sent as a boolean', () => {
  const { original, draft } = fixture()
  setWorkbookValue(draft, 'rootDirectLogin', false)
  assert.deepEqual(workbookRowChanges(original, draft), {})
  setWorkbookValue(draft, 'rootDirectLogin', true)
  assert.deepEqual(workbookRowChanges(original, draft), { rootDirectLogin: true })
  const hardware = fixture('HARDWARE')
  setWorkbookValue(hardware.draft, 'rootDirectLogin', true)
  assert.equal(hardware.draft.rootDirectLogin, undefined)
})

test('login check stays in credentials, reuses native switch and sends only saved device identity', () => {
  const read = path => readFileSync(new URL(path, import.meta.url), 'utf8')
  const component = read('../components/EquipmentLoginCheck.vue')
  const workspace = read('../components/EquipmentRoom3DWorkspace.vue')
  assert.equal(parse(component).errors.length, 0)
  assert.match(component, /el-switch/)
  assert.match(component, /support:credential:verify/)
  assert.match(component, /serverId: props.device.sourceId/)
  assert.doesNotMatch(component, /readEquipmentPasswords|localStorage|sessionStorage/)
  assert.match(component, /HOST_KEY_REQUIRED/)
  assert.match(workspace, /deviceConfigTab === 'credentials'/)
  assert.match(component, /saveEquipmentWorkbook/)
})

test('workbook sends only changes and never turns existing SSH ports into defaults', () => {
  const { original, draft } = fixture()
  setWorkbookValue(draft, 'assetName', '新设备名')
  const payload = workbookPayload(9, { [original.deviceKey]: original }, [draft])
  assert.deepEqual(payload.rows[0].changes, { assetName: '新设备名' })
  assert.equal(payload.rows[0].revision, 'opaque-revision')
  assert.equal(draft.sshPort, 22)
  setWorkbookValue(draft, 'assetName', original.assetName)
  assert.equal(workbookPayload(9, { [original.deviceKey]: original }, [draft]).rows.length, 0)
})

test('room and cabinet edits clear dependent U values instead of retaining a wrong rack', () => {
  const { original, draft } = fixture()
  setWorkbookValue(draft, 'roomId', 3)
  assert.equal(draft.cabinetId, null)
  assert.equal(draft.rackUStart, null)
  assert.equal(draft.rackUEnd, null)
  assert.ok(validateWorkbookDraft({ [draft.deviceKey]: original }, [draft], options).length)
  setWorkbookValue(draft, 'roomId', null)
  assert.deepEqual(workbookRowChanges(original, draft), { roomId: null, cabinetId: null, rackUStart: null, rackUEnd: null })
  assert.equal(validateWorkbookDraft({ [draft.deviceKey]: original }, [draft], options).length, 0)
})

test('invalid numeric input stays dirty and is not confused with an empty original', () => {
  const { original, draft } = fixture()
  original.rackUStart = null
  draft.rackUStart = 'invalid'
  assert.equal(workbookRowChanges(original, draft).rackUStart, 'invalid')
  assert.ok(validateWorkbookDraft({ [draft.deviceKey]: original }, [draft], options).length)
  setWorkbookValue(draft, 'sshPort', '0')
  assert.ok(validateWorkbookDraft({ [draft.deviceKey]: original }, [draft], options).some(item => item.field === 'sshPort'))
})

test('password replacement, explicit clear and undo have different payload meanings', () => {
  const { original, draft } = fixture()
  assert.deepEqual(workbookRowChanges(original, draft), {})
  setWorkbookValue(draft, 'rootPassword', 'test-only-secret')
  assert.equal(workbookRowChanges(original, draft).rootPassword, 'test-only-secret')
  setWorkbookValue(draft, 'rootPassword', '')
  assert.deepEqual(workbookRowChanges(original, draft), {})
  setWorkbookValue(draft, 'rootPassword', null)
  assert.deepEqual(workbookRowChanges(original, draft), { rootPassword: null })
  setWorkbookValue(draft, 'rootPassword', '******')
  assert.ok(validateWorkbookDraft({ [draft.deviceKey]: original }, [draft], options).some(item => item.field === 'rootPassword'))
  assert.equal(JSON.stringify(original).includes('test-only-secret'), false)
})

test('password-only edits immediately update Vue dirty counts, including added and removed secret keys', () => {
  const { original, draft } = fixture()
  const row = reactive(draft)
  const pending = computed(() => workbookPayload(9, { [original.deviceKey]: original }, [row]).rows.length)
  assert.equal(pending.value, 0)
  setWorkbookValue(row, 'rootPassword', 'test-secret')
  assert.equal(pending.value, 1)
  setWorkbookValue(row, 'rootPassword', '')
  assert.equal(pending.value, 0)
  setWorkbookValue(row, 'rootPassword', null)
  assert.equal(pending.value, 1)
})

test('server platform choices reject main platforms and field permissions do not leak across device types', () => {
  const { original, draft } = fixture()
  setWorkbookValue(draft, 'platformIds', [11])
  assert.ok(validateWorkbookDraft({ [draft.deviceKey]: original }, [draft], options).some(item => item.field === 'platformIds'))
  setWorkbookValue(draft, 'manufacturer', 'ignored')
  assert.equal(draft.manufacturer, undefined)
  draft.canEdit = false
  setWorkbookValue(draft, 'assetName', 'blocked')
  assert.equal(draft.assetName, original.assetName)
  assert.equal(workbookPayload(9, { [draft.deviceKey]: original }, [draft]).rows.length, 0)
})

test('pagination independent drafts preserve multiple typed device identities', () => {
  const a = fixture(), b = fixture('HARDWARE')
  setWorkbookValue(a.draft, 'sshPort', '55555')
  setWorkbookValue(b.draft, 'manufacturer', '测试厂商')
  const originals = { [a.original.deviceKey]: a.original, [b.original.deviceKey]: b.original }
  const rows = workbookPayload(9, originals, [a.draft, b.draft]).rows
  assert.equal(rows.length, 2)
  assert.deepEqual(rows.map(row => row.sourceType), ['SERVER', 'HARDWARE'])
  assert.deepEqual(rows[1].changes, { manufacturer: '测试厂商' })
})

test('editing and unified configuration keep native controls, close guards and no plaintext request cache', () => {
  const read = path => readFileSync(new URL(path, import.meta.url), 'utf8')
  const inventory = read('../components/EquipmentInventory.vue')
  const actions = read('../components/EquipmentInventoryCell.vue')
  const workspace = read('../components/EquipmentRoom3DWorkspace.vue')
  const api = read('../../../../api/support/equipment.js')
  assert.equal(parse(inventory).errors.length, 0)
  assert.equal(parse(workspace).errors.length, 0)
  assert.match(inventory, /正在编辑/)
  assert.match(inventory, /onBeforeRouteLeave\(confirmClose\)/)
  assert.match(inventory, /beforeunload/)
  assert.match(workspace, /if \(inventoryEditing.value\) return/)
  assert.match(workspace, /document.visibilityState === 'visible' && !editorOpen.value/)
  assert.match(actions, />配置<\/el-button>/)
  assert.doesNotMatch(actions, />详情<|>编辑<|>位置</)
  assert.match(api, /saveEquipmentWorkbook[\s\S]*?repeatSubmit: false/)
})
