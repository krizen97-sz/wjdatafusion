import assert from 'node:assert/strict'
import test from 'node:test'
import { readFileSync } from 'node:fs'
import { parse } from '@vue/compiler-sfc'
import { parse as parseScript } from '@babel/parser'
import { computed, reactive, ref } from 'vue'
import { LOCATION_FIELDS, SECRET_FIELDS, WORKBOOK_FIELDS, setWorkbookValue, validateWorkbookDraft, workbookPayload, workbookRowChanges } from '../components/equipmentWorkbook.rules.js'

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

test('entering edit defaults plaintext to the existing permission, including an already-visible list', async () => {
  const script = parse(readFileSync(new URL('../components/EquipmentInventory.vue', import.meta.url), 'utf8')).descriptor.scriptSetup.content
  const start = parseScript(script, { sourceType: 'module' }).program.body.find(node => node.type === 'FunctionDeclaration' && node.id.name === 'startEditing')
  for (const allowed of [true, false]) {
    for (const alreadyVisible of [true, false]) {
      const { original } = fixture()
      const state = {
        editing: ref(false), starting: ref(false), canEditAny: ref(true), canViewPlain: ref(allowed),
        showPasswords: ref(alreadyVisible), saveError: ref(''), selectedKeys: ref([]), editKeys: ref([]),
        originals: ref({}), page: { pageNum: 2 }, fieldGroup: ref('ALL'), editRequest: 0,
        props: { siteId: 9, devices: [original] }, emit() {}, inventoryDeviceKey: row => row.deviceKey,
        getEquipmentWorkbook: async () => ({ data: { rows: [original] } }),
        installSnapshot: snapshot => { state.originals.value = Object.fromEntries(snapshot.rows.map(row => [row.deviceKey, row])) },
        clearPasswords: () => { state.cleared = true }
      }
      const run = new Function(...Object.keys(state), 'return (' + script.slice(start.start, start.end) + ')')(...Object.values(state))
      await run()
      assert.equal(state.editing.value, true)
      assert.equal(state.showPasswords.value, allowed)
      assert.equal(state.cleared, true)
      assert.equal(state.starting.value, false)
      assert.equal(state.page.pageNum, 1)
    }
  }
  assert.match(script, /watch\(\[showPasswords, canViewPlain, editing, /)
  assert.match(script, /function finishEditing\(\)[\s\S]*?showPasswords.value = false\s+clearPasswords\(\)/)
  assert.match(script, /async function rebaseDraft\(\)[\s\S]*?await loadPasswords\(\)/)
})

test('revealed credentials remain display data and never become pending password changes', () => {
  const { original, draft } = fixture()
  draft._showPasswords = true
  draft._plainValues = { hikPassword: 'fixture-only-hik', rootPassword: 'fixture-only-root' }
  assert.deepEqual(workbookPayload(9, { [original.deviceKey]: original }, [draft]).rows, [])
  setWorkbookValue(draft, 'rootPassword', draft._plainValues.rootPassword)
  assert.deepEqual(workbookRowChanges(original, draft), {})
})

test('credentials show the existing SSH column without changing other field groups or password permissions', () => {
  const source = readFileSync(new URL('../components/EquipmentInventory.vue', import.meta.url), 'utf8')
  const script = parse(source).descriptor.scriptSetup.content
  let predicate
  function visit(node) {
    if (!node || typeof node !== 'object') return
    if (node.type === 'CallExpression' && node.callee?.object?.name === 'WORKBOOK_FIELDS' && node.callee?.property?.name === 'filter') predicate = node.arguments[0]
    for (const value of Object.values(node)) {
      if (Array.isArray(value)) value.forEach(visit)
      else if (value && typeof value === 'object') visit(value)
    }
  }
  visit(parseScript(script, { sourceType: 'module' }))
  assert.ok(predicate)
  const filter = new Function('visibleDevices', 'canViewPlain', 'editing', 'fieldGroup', 'SECRET_FIELDS', 'LOCATION_FIELDS',
    'return (' + script.slice(predicate.start, predicate.end) + ')')
  const fields = (group, type = 'SERVER', plain = true, editing = false) => WORKBOOK_FIELDS.filter(filter(
    { value: [{ sourceType: type }] }, { value: plain }, { value: editing }, { value: group }, SECRET_FIELDS, LOCATION_FIELDS))
  for (const group of ['CREDENTIAL', 'PROFILE', 'ALL']) assert.ok(fields(group).some(field => field.prop === 'sshPort'))
  for (const group of ['LOCATION', 'EXTENDED']) assert.ok(!fields(group).some(field => field.prop === 'sshPort'))
  assert.ok(!fields('CREDENTIAL', 'HARDWARE').some(field => field.prop === 'sshPort'))
  assert.ok(fields('CREDENTIAL', 'SERVER', false).some(field => field.prop === 'sshPort'))
  assert.ok(!fields('CREDENTIAL', 'SERVER', false).some(field => field.kind === 'password'))
  assert.ok(fields('CREDENTIAL', 'SERVER', false, true).some(field => field.prop === 'sshPort'))
  const port = fields('CREDENTIAL').find(field => field.prop === 'sshPort')
  assert.equal(port.name, 'SSH端口')
  assert.equal(port.kind, 'number')
  assert.ok(WORKBOOK_FIELDS.indexOf(port) < WORKBOOK_FIELDS.findIndex(field => field.prop === 'hikPassword'))
})

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
