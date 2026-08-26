import assert from 'node:assert/strict'
import { readFileSync, readdirSync } from 'node:fs'
import test from 'node:test'
import { fileURLToPath } from 'node:url'
import { iconLabel, matchesIcon } from '../iconCatalog.js'

const iconDirectory = fileURLToPath(new URL('../../../assets/icons/svg/', import.meta.url))
const read = (file) => readFileSync(file, 'utf8')
const addedIcons = [
  'network', 'map-pinned', 'panels-top-left', 'server-cog', 'contact-round',
  'file-clock', 'shield-check', 'car-front', 'list-filter', 'scan-search',
  'gauge', 'chart-no-axes-combined', 'workflow', 'route', 'folder-tree',
  'files', 'database-check', 'cable', 'waypoints', 'warehouse', 'layers',
  'router', 'hard-drive', 'users-round', 'data-analysis'
]

test('new menu icons follow the local linear SVG contract', () => {
  const allIcons = readdirSync(iconDirectory).filter((file) => file.endsWith('.svg'))
  assert.ok(allIcons.length >= 115)

  for (const name of addedIcons) {
    const source = read(`${iconDirectory}/${name}.svg`)
    assert.ok(source.includes('viewBox="0 0 24 24"'), `${name} must use a 24px viewBox`)
    assert.ok(source.includes('stroke="currentColor"'), `${name} must inherit theme color`)
    assert.ok(source.includes('stroke-width="2"'), `${name} must use the shared stroke weight`)
  }
})

test('icon picker searches curated icons by Chinese business meaning', () => {
  assert.equal(iconLabel('gauge'), '驾驶舱')
  assert.equal(matchesIcon('network', '现场'), true)
  assert.equal(matchesIcon('folder-tree', '文档'), true)
  assert.equal(matchesIcon('server-cog', '巡检'), false)
})

test('missing icon names fall back and menu SQL uses bundled icons', () => {
  const registry = read('src/utils/iconRegistry.js')
  const svgIcon = read('src/components/SvgIcon/index.vue')
  const menuPage = read('src/views/system/menu/index.vue')
  const upgrade = read('../WDF100.0/sql/support_upgrade_20260826_menu_icons_v3_14_3.sql')
  const license = read('src/assets/icons/LUCIDE_LICENSE.txt')

  assert.ok(registry.includes("fallback = 'component'"))
  assert.ok(svgIcon.includes('resolveIconName(props.iconClass)'))
  assert.ok(menuPage.includes('@show="showSelectIcon"'))
  assert.ok(!menuPage.includes('@blur="showSelectIcon"'))
  for (const name of ['network', 'gauge', 'workflow', 'route', 'folder-tree', 'shield-check']) {
    assert.ok(upgrade.includes(`'${name}'`), `upgrade SQL must assign ${name}`)
  }
  assert.ok(license.includes('ISC License'))
})
