import assert from 'node:assert/strict'
import { createHash } from 'node:crypto'
import { readFileSync, readdirSync } from 'node:fs'
import test from 'node:test'
import { fileURLToPath } from 'node:url'
import {
  categoriesForSource,
  iconCategory,
  iconCategoryLabel,
  iconLabel,
  iconSource,
  matchesIcon
} from '../iconCatalog.js'

const iconDirectory = fileURLToPath(new URL('../../../assets/icons/svg/', import.meta.url))
const keylineDirectory = fileURLToPath(new URL('../../../assets/icons/svg/keyline/', import.meta.url))
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
  assert.equal(allIcons.length, 115)

  for (const name of addedIcons) {
    const source = read(`${iconDirectory}/${name}.svg`)
    assert.ok(source.includes('viewBox="0 0 24 24"'), `${name} must use a 24px viewBox`)
    assert.ok(source.includes('stroke="currentColor"'), `${name} must inherit theme color`)
    assert.ok(source.includes('stroke-width="2"'), `${name} must use the shared stroke weight`)
  }
})

test('the complete pinned Keyline stroke library is bundled safely', () => {
  const keylineIcons = readdirSync(keylineDirectory)
    .filter((file) => file.endsWith('.svg'))
    .sort((left, right) => left.localeCompare(right))
  const fillOnlyDots = new Set(['more-horizontal.svg', 'more-vertical.svg'])
  const aggregateHash = createHash('sha256')

  assert.equal(keylineIcons.length, 547)

  for (const fileName of keylineIcons) {
    const source = read(`${keylineDirectory}/${fileName}`)
    assert.match(fileName, /^[a-z0-9]+(?:-[a-z0-9]+)*\.svg$/)
    assert.ok(source.includes('viewBox="0 0 24 24"'), `${fileName} must use a 24px viewBox`)
    assert.ok(source.includes('currentColor'), `${fileName} must inherit theme color`)
    assert.doesNotMatch(source, /<(?:script|foreignObject|image|style)\b|\b(?:href|xlink:href|class|id)=/i)
    if (!fillOnlyDots.has(fileName)) {
      assert.ok(source.includes('stroke="currentColor"'), `${fileName} must inherit its stroke color`)
      assert.ok(source.includes('stroke-width="2"'), `${fileName} must use the shared stroke weight`)
    }
    aggregateHash.update(fileName)
    aggregateHash.update('\0')
    aggregateHash.update(source)
    aggregateHash.update('\0')
  }

  const manifest = JSON.parse(read('src/assets/icons/KEYLINE_MANIFEST.json'))
  assert.equal(manifest.packageVersion, '0.1.4')
  assert.equal(manifest.commit, '403f023d0861d01807cdec045b5fb3fec984468d')
  assert.equal(manifest.iconCount, 547)
  assert.equal(manifest.symbolPrefix, 'keyline-')
  assert.equal(manifest.aggregateSha256, aggregateHash.digest('hex'))
  assert.match(read('src/assets/icons/KEYLINE_LICENSE.txt'), /MIT License/)
  assert.match(read('src/assets/icons/KEYLINE_NOTICE.txt'), /Imported scope: 547 stroke SVG icons/)
})

test('icon picker searches curated icons by Chinese business meaning', () => {
  assert.equal(iconLabel('gauge'), '驾驶舱')
  assert.equal(matchesIcon('network', '现场'), true)
  assert.equal(matchesIcon('folder-tree', '文档'), true)
  assert.equal(matchesIcon('server-cog', '巡检'), false)
})

test('Keyline icons are namespaced, categorized and searchable in Chinese, English and pinyin', () => {
  assert.equal(iconSource('server'), 'platform')
  assert.equal(iconSource('keyline-server'), 'keyline')
  assert.equal(iconLabel('keyline-git-branch'), 'Git Branch')
  assert.equal(iconCategory('keyline-server'), 'devices')
  assert.equal(iconCategory('keyline-circle-arrow-down'), 'arrows')
  assert.equal(iconCategoryLabel('keyline-server'), '设备开发')
  assert.equal(categoriesForSource('keyline').length, 20)
  assert.equal(matchesIcon('keyline-server', '服务器'), true)
  assert.equal(matchesIcon('keyline-server', 'fuwuqi'), true)
  assert.equal(matchesIcon('keyline-server', 'fwq'), true)
  assert.equal(matchesIcon('keyline-settings', 'gear'), true)
  assert.equal(matchesIcon('keyline-file-check', '文件 完成'), true)
  assert.equal(matchesIcon('keyline-server', '音乐'), false)
})

test('missing icon names fall back and menu SQL uses bundled icons', () => {
  const registry = read('src/utils/iconRegistry.js')
  const svgIcon = read('src/components/SvgIcon/index.vue')
  const iconPicker = read('src/components/IconSelect/index.vue')
  const menuPage = read('src/views/system/menu/index.vue')
  const packageJson = JSON.parse(read('package.json'))
  const releaseNotes = read('src/views/support/version/releaseNotes.js')
  const upgrade = read('../WDF100.0/sql/support_upgrade_20260826_menu_icons_v3_14_3.sql')
  const license = read('src/assets/icons/LUCIDE_LICENSE.txt')

  assert.ok(registry.includes("fallback = 'component'"))
  assert.ok(registry.includes("from 'virtual:svg-icons-names'"))
  assert.ok(!registry.includes('import.meta.glob'))
  assert.ok(svgIcon.includes('resolveIconName(props.iconClass)'))
  assert.ok(iconPicker.includes('<el-segmented'))
  assert.ok(iconPicker.includes('<el-select'))
  assert.ok(iconPicker.includes('<el-scrollbar'))
  assert.ok(iconPicker.includes('<el-empty'))
  assert.ok(!iconPicker.includes('<button'))
  assert.ok(iconPicker.includes(':aria-pressed="activeIcon === item"'))
  assert.ok(!iconPicker.includes('document.body.click'))
  assert.ok(menuPage.includes('@show="showSelectIcon"'))
  assert.ok(menuPage.includes('ref="iconPopoverRef"'))
  assert.ok(menuPage.includes('iconPopoverRef.value?.hide()'))
  assert.ok(!menuPage.includes('@blur="showSelectIcon"'))
  assert.equal(packageJson.version, '3.15.2')
  assert.ok(releaseNotes.includes("version: 'v3.15.2'"))
  assert.ok(releaseNotes.includes('平台离线扩展图标库全量接入'))
  for (const name of ['network', 'gauge', 'workflow', 'route', 'folder-tree', 'shield-check']) {
    assert.ok(upgrade.includes(`'${name}'`), `upgrade SQL must assign ${name}`)
  }
  assert.ok(license.includes('ISC License'))
})
