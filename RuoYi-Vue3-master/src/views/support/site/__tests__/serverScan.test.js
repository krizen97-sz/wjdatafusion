import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { parse } from '@vue/compiler-sfc'
import { WORKBOOK_FIELDS, editableFields } from '../components/equipmentWorkbook.rules.js'
const read = file => readFileSync(new URL(file, import.meta.url), 'utf8')

test('scanner uses native controls and explicit fingerprint confirmation', () => {
  const source = read('../components/EquipmentServerScanDialog.vue')
  assert.deepEqual(parse(source).errors, [])
  for (const control of ['el-dialog', 'el-form', 'el-table', 'el-steps', 'el-drawer', 'Pagination']) assert.ok(source.includes('<' + control))
  assert.match(source, /ports: \[22, 2343, 55555\]/)
  assert.match(source, /fingerprintsConfirmed: true/)
  assert.match(source, /指纹/)
  assert.match(source, /attemptLimit: 1/)
  assert.doesNotMatch(source, /v-html|localStorage|sessionStorage|setInterval/)
})
test('candidate passwords bypass duplicate-submit storage and remain absent from result columns', () => {
  const source = read('../../../../api/support/equipment.js')
  assert.match(source, /saveServerScanCredential[^\n]*repeatSubmit: false/)
  const dialog = read('../components/EquipmentServerScanDialog.vue')
  assert.doesNotMatch(dialog, /row\.(rootPassword|hikPassword|rootCipher|hikCipher)/)
  assert.match(dialog, /clearCredential\(\)/)
  assert.match(dialog, /onBeforeUnmount\(stopPolling\)/)
})
test('scan metadata is read only and belongs to a separate field group', () => {
  const scanned = WORKBOOK_FIELDS.filter(field => field.group === 'SCAN')
  assert.equal(scanned.length, 5)
  for (const field of scanned) {
    assert.equal(field.source, 'SERVER'); assert.equal(field.readonly, true)
    assert.ok(!editableFields({ sourceType: 'SERVER' }).includes(field.prop))
  }
  const inventory = read('../components/EquipmentInventory.vue')
  assert.match(inventory, /label="扫描资料" value="SCAN"/)
  assert.match(inventory, /EquipmentServerScanDialog/)
  assert.match(inventory, /getServerScanProfiles/)
})

test('server directory reflects unified credentials rather than only legacy password fields', () => {
  const server = read('../../server/index.vue')
  assert.match(server, /serverAccounts\(row\)/)
  assert.match(server, /row.hikCredentialConfigured/)
  assert.match(server, /row.rootCredentialConfigured/)
  assert.doesNotMatch(server, /label="系统密码".*prop="osPassword"/)
})
