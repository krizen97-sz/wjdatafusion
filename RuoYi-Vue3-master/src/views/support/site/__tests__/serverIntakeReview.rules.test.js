import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { serverIntakePayload, serverIntakeSignature, serverIntakeCounts } from '../components/serverIntakeReview.rules.js'

const context = { siteId: 1, platformId: 2 }
const rows = [{ state: 'CREATE', data: { rowNumber: 2, serverAddress: '198.18.0.1', sshPort: '55555', rootPassword: ' secret ' } }]

test('review payload only sends editable fields and never trusts existing IDs or row statuses', () => {
  const payload = serverIntakePayload(context, [{ ...rows[0], existingServerId: 999, data: { ...rows[0].data, serverId: 999, siteId: 99 } }], true)
  assert.equal(payload.siteId, 1)
  assert.equal(payload.platformId, 2)
  assert.equal(payload.reuseExisting, true)
  assert.equal(payload.rows[0].rootPassword, ' secret ')
  assert.equal(payload.rows[0].serverId, undefined)
  assert.equal(payload.rows[0].siteId, undefined)
  assert.equal(payload.rows[0].existingServerId, undefined)
})

test('changing a field, target, row list or reuse policy invalidates confirmation', () => {
  const signature = serverIntakeSignature(context, rows, false)
  assert.notEqual(signature, serverIntakeSignature(context, [{ ...rows[0], data: { ...rows[0].data, sshPort: '22' } }], false))
  assert.notEqual(signature, serverIntakeSignature({ ...context, platformId: 3 }, rows, false))
  assert.notEqual(signature, serverIntakeSignature(context, [], false))
  assert.notEqual(signature, serverIntakeSignature(context, rows, true))
  assert.equal(signature, serverIntakeSignature(context, [{ ...rows[0], state: 'ERROR', errors: ['test'] }], false))
})

test('counts distinguish creation, reuse, skip and invalid rows', () => {
  assert.deepEqual(serverIntakeCounts(['CREATE', 'REUSE', 'SKIP', 'ERROR'].map(state => ({ state }))),
    { total: 4, create: 1, reuse: 1, skip: 1, error: 1 })
})

test('template selection uses native upload and automatically opens validation, with no persistence API in selection', () => {
  const intake = readFileSync(new URL('../components/EquipmentIntakeDialog.vue', import.meta.url), 'utf8')
  assert.match(intake, /<el-upload[^>]*drag/)
  assert.match(intake, /:auto-upload="false"/)
  assert.match(intake, /async function selectImportFile[\s\S]*?await submit\(\)/)
  assert.ok(intake.includes('previewEquipmentServerFile'))
  assert.ok(!intake.includes('commitEquipmentServers'))
  const review = readFileSync(new URL('../components/ServerIntakeReviewDialog.vue', import.meta.url), 'utf8')
  assert.ok(review.includes('commitEquipmentServers'))
  assert.ok(review.includes('确认上传'))
  assert.ok(review.includes('busy || dirty || !!counts.error'))
  assert.ok(review.includes('<Pagination'))
})

test('retired dialogs, per-row persistence loops and raw upload pages are removed', () => {
  const config = readFileSync(new URL('../SiteConfigDialog.vue', import.meta.url), 'utf8')
  for (const retired of ['hardwareAssetDialogOpen', 'bindServerDialogOpen', 'equipmentAddTypeOpen', 'serverBatchConfirmOpen',
    'serverImportDialogOpen', 'createAndBindServers', 'parseServerAddressText', 'previewServerImport']) {
    assert.ok(!config.includes(retired), retired)
  }
  const catalog = readFileSync(new URL('../../server/index.vue', import.meta.url), 'utf8')
  assert.ok(!catalog.includes('v-if="false"'))
  assert.ok(!catalog.includes('function submitForm'))
})
