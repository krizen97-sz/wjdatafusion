import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { EQUIPMENT_GRID_THEMES, buildEquipmentSheets, filterEquipmentSheet, inventoryDeviceKey, inventoryPasswordCells } from '../components/equipmentInventory.rules.js'
import { normalizeEndpointUrl, copyEndpointPassword } from '../endpointNavigation.js'

const platforms = [
  { platformId: 1, platformLevel: 'MAIN', platformName: 'A' },
  { platformId: 2, parentPlatformId: 1, platformLevel: 'SUB', displayName: 'A / 门户' },
  { platformId: 3, parentPlatformId: 4, platformLevel: 'SUB', displayName: 'B / 门户' }
]
const shared = { sourceType: 'SERVER', sourceId: 1, platformIds: [2, 3] }
const hardware = { sourceType: 'HARDWARE', sourceId: 1, platformIds: [] }
test('grid theme uses the library theme API so inline compact defaults cannot override dark mode', () => {
  assert.equal(EQUIPMENT_GRID_THEMES[0].tokens.text, 'var(--el-text-color-primary)')
  assert.equal(EQUIPMENT_GRID_THEMES[0].tokens.background, 'var(--el-bg-color)')
})
test('sheets use stable IDs, preserve duplicate names, and retain public and unassigned assets', () => {
  assert.deepEqual(buildEquipmentSheets(platforms, 1).map(s => s.value), ['ALL', '2', 'OTHER'])
  assert.equal(buildEquipmentSheets(platforms).length, 4)
  assert.deepEqual(filterEquipmentSheet([shared, hardware], 'OTHER', platforms), [hardware])
  assert.equal(filterEquipmentSheet([shared, hardware], '2', platforms)[0], shared)
  assert.equal(filterEquipmentSheet([shared, hardware], '3', platforms)[0], shared)
  assert.notEqual(inventoryDeviceKey(shared), inventoryDeviceKey(hardware))
})
test('masking never places secrets in hidden spreadsheet cells', () => {
  const credentials = [{ username: 'root', password: 'test-secret' }, { username: 'ops', password: 'other-test' }]
  const masked = inventoryPasswordCells(shared, credentials, false)
  assert.equal(JSON.stringify(masked).includes('test-secret'), false)
  assert.equal(inventoryPasswordCells(shared, credentials, true).rootPassword, 'test-secret')
  assert.equal(inventoryPasswordCells(shared, credentials, true).otherUsername, 'ops')
  assert.equal(inventoryPasswordCells(shared, [], true).rootPassword, '-')
  assert.equal(inventoryPasswordCells(hardware, [{ username: 'admin', password: 'hw-test' }], true).loginPassword, 'hw-test')
})
test('navigation accepts intranet host ports but rejects executable URLs and credentials', () => {
  assert.equal(normalizeEndpointUrl('10.0.0.1:8080/a'), 'http://10.0.0.1:8080/a')
  assert.equal(normalizeEndpointUrl('https://example.com/a'), 'https://example.com/a')
  for (const url of ['', 'javascript:alert(1)', 'data:text/html,x', 'file:///tmp/a', 'https://user:pass@example.com']) {
    assert.throws(() => normalizeEndpointUrl(url))
  }
})
test('copy-and-open requires successful copy and exposes popup blocking without losing copy result', async () => {
  const calls = []
  const request = {
    url: 'http://example.test',
    readPassword: async () => { calls.push('read'); return 'test-password' },
    copy: async () => { calls.push('copy'); return true },
    open: () => { calls.push('open'); return false }
  }
  assert.equal((await copyEndpointPassword(request)).opened, false)
  assert.deepEqual(calls, ['read', 'copy', 'open'])
  calls.length = 0
  await assert.rejects(copyEndpointPassword({ ...request, copy: async () => false }))
  assert.deepEqual(calls, ['read'])
  await assert.rejects(copyEndpointPassword({ ...request, readPassword: async () => '' }))
})
test('workspace defaults to list, inspector defaults closed, and native shared forms replace duplicate editors', () => {
  const site = readFileSync(new URL('../SiteConfigDialog.vue', import.meta.url), 'utf8')
  const workspace = readFileSync(new URL('../components/EquipmentRoom3DWorkspace.vue', import.meta.url), 'utf8')
  assert.match(site, /const inspectorOpen = ref\(false\)/)
  assert.match(site, /<el-drawer v-model="inspectorOpen"/)
  assert.doesNotMatch(site, /grid-template-columns: minmax\(0, 1fr\) (300|330)px/)
  assert.match(workspace, /const viewMode = ref\('list'\)/)
  assert.match(workspace, /<main v-if="viewMode === 'map'"/)
  assert.doesNotMatch(workspace, /v-model="deviceDrawerOpen"/)
  assert.equal((site.match(/<SiteEntityForm /g) || []).length, 4)
  assert.doesNotMatch(site, /<div class="editor-hero/)
})
