import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { EQUIPMENT_GRID_THEMES, buildEquipmentTree, filterEquipmentNode, findEquipmentTreeNode, inventoryDeviceKey, inventoryPasswordCells } from '../components/equipmentInventory.rules.js'
import { normalizeEndpointUrl, copyEndpointPassword } from '../endpointNavigation.js'

const platforms = [
  { platformId: 1, platformLevel: 'MAIN', platformName: 'A' },
  { platformId: 4, platformLevel: 'MAIN', platformName: 'B' },
  { platformId: 2, parentPlatformId: 1, platformLevel: 'SUB', displayName: 'A / 门户' },
  { platformId: 3, parentPlatformId: 4, platformLevel: 'SUB', displayName: 'B / 门户' }
]
const shared = { sourceType: 'SERVER', sourceId: 1, platformIds: [2, 3] }
const hardware = { sourceType: 'HARDWARE', sourceId: 1, platformIds: [] }
test('grid theme uses the library theme API so inline compact defaults cannot override dark mode', () => {
  assert.equal(EQUIPMENT_GRID_THEMES[0].tokens.text, 'var(--el-text-color-primary)')
  assert.equal(EQUIPMENT_GRID_THEMES[0].tokens.background, 'var(--el-bg-color)')
})
test('tree preserves main-child relationships and counts shared assets once at the root', () => {
  const tree = buildEquipmentTree(platforms, [shared, hardware, shared], null, '现场')
  assert.equal(tree[0].count, 2)
  assert.deepEqual(tree[0].children.map(node => node.id), ['PLATFORM:1', 'PLATFORM:4', 'OTHER'])
  assert.equal(findEquipmentTreeNode(tree, 'PLATFORM:1').children[0].id, 'PLATFORM:2')
  assert.equal(findEquipmentTreeNode(tree, 'PLATFORM:4').children[0].id, 'PLATFORM:3')
  assert.equal(findEquipmentTreeNode(tree, 'PLATFORM:1').count, 1)
  assert.equal(findEquipmentTreeNode(tree, 'OTHER').count, 1)
  assert.equal(filterEquipmentNode([shared, hardware], 'PLATFORM:2', platforms)[0], shared)
  assert.equal(filterEquipmentNode([shared, hardware], 'PLATFORM:3', platforms)[0], shared)
  assert.notEqual(inventoryDeviceKey(shared), inventoryDeviceKey(hardware))
})
test('current platform scope keeps its ancestor path and excludes unrelated branches', () => {
  const tree = buildEquipmentTree(platforms, [shared, hardware], 2)
  assert.equal(tree[0].count, 1)
  assert.equal(findEquipmentTreeNode(tree, 'PLATFORM:1').count, 1)
  assert.equal(findEquipmentTreeNode(tree, 'PLATFORM:2').count, 1)
  assert.equal(findEquipmentTreeNode(tree, 'PLATFORM:4'), null)
  assert.equal(findEquipmentTreeNode(tree, 'OTHER').count, 0)
})
test('main counts deduplicate shared children and preserve directly owned hardware', () => {
  const peers = [...platforms, { platformId: 5, parentPlatformId: 1, platformLevel: 'SUB', platformName: '共用子平台' }]
  const sharedChild = { ...shared, platformIds: [2, 5] }
  const direct = { ...hardware, platformIds: [1] }
  const tree = buildEquipmentTree(peers, [sharedChild, direct, sharedChild], 1)
  assert.equal(findEquipmentTreeNode(tree, 'PLATFORM:1').count, 2)
  assert.equal(findEquipmentTreeNode(tree, 'PLATFORM:2').count, 1)
  assert.equal(findEquipmentTreeNode(tree, 'PLATFORM:5').count, 1)
  assert.equal(findEquipmentTreeNode(tree, 'DIRECT:1').count, 1)
  assert.deepEqual(filterEquipmentNode([sharedChild, direct], 'DIRECT:1', peers), [direct])
})
test('zero platforms remain visible and orphaned or unknown associations are not lost', () => {
  const orphan = { platformId: 6, parentPlatformId: 99, platformLevel: 'SUB', platformName: '孤立平台' }
  const unknown = { ...hardware, platformIds: [999] }
  const tree = buildEquipmentTree([...platforms, orphan], [unknown], null)
  assert.equal(findEquipmentTreeNode(tree, 'PLATFORM:6').count, 0)
  assert.equal(findEquipmentTreeNode(tree, 'OTHER').count, 1)
  assert.equal(findEquipmentTreeNode(buildEquipmentTree(platforms, []), 'PLATFORM:1').count, 0)
  assert.deepEqual(filterEquipmentNode([shared], 'invalid', platforms), [])
})
test('inventory has no sheet tabs and the view switch stays in the mode-independent topbar', () => {
  const inventory = readFileSync(new URL('../components/EquipmentInventory.vue', import.meta.url), 'utf8')
  const workspace = readFileSync(new URL('../components/EquipmentRoom3DWorkspace.vue', import.meta.url), 'utf8')
  const switchIndex = workspace.indexOf('data-testid="equipment-view-switch"')
  const controlsIndex = workspace.indexOf('<div class="room3d-controls">')
  assert.ok(switchIndex > 0 && switchIndex < controlsIndex)
  assert.ok(workspace.includes('<EquipmentRelationTree'))
  assert.doesNotMatch(inventory, /el-tabs|activeSheet|全选当前 Sheet/)
  assert.match(inventory, /watch\(\(\) => props.contextKey/)
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
