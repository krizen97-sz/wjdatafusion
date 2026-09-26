import assert from 'node:assert/strict'
import test from 'node:test'
import { readFileSync } from 'node:fs'
import { parse } from '@vue/compiler-sfc'

const read = file => readFileSync(new URL(file, import.meta.url), 'utf8')
const attribute = (node, name) => node.props?.find(prop => prop.type === 6 && prop.name === name)?.value?.content
function find(node, match) {
  if (match(node)) return node
  for (const child of node.children || []) {
    const result = find(child, match)
    if (result) return result
  }
}

test('site columns start with region, code, name and retain the other business columns', () => {
  const ast = parse(read('../index.vue')).descriptor.template.ast
  const table = find(ast, node => node.tag === 'el-table')
  const columns = table.children.filter(node => node.tag === 'el-table-column')
  assert.equal(attribute(columns[0], 'type'), 'selection')
  assert.deepEqual(columns.slice(1).map(node => attribute(node, 'label')),
    ['行政区', '现场编码', '现场名称', '现场ID', '详细地址', '状态', '操作'])
  assert.ok(find(columns[1], node => node.tag === 'el-tooltip'))
})

test('canvas search uses a sibling native button with visible text and the same Enter action', () => {
  const ast = parse(read('../SiteConfigDialog.vue')).descriptor.template.ast
  const search = find(ast, node => attribute(node, 'class') === 'fusion-workbench__search')
  const children = search.children.filter(node => node.type === 1)
  assert.deepEqual(children.map(node => node.tag), ['el-input', 'el-button'])
  assert.equal(attribute(search, 'role'), 'search')
  assert.equal(attribute(children[0], 'aria-label'), '搜索平台名称')
  assert.ok(children[1].children.some(node => node.content === '搜索'))
  assert.ok(children[0].props.some(prop => prop.name === 'on' && prop.exp.content === 'loadAll'))
  assert.ok(children[1].props.some(prop => prop.name === 'on' && prop.exp.content === 'loadAll'))
})

test('narrow canvas toolbar resets row flex bases when switched to a column', () => {
  const source = read('../SiteConfigDialog.vue')
  const css = source.slice(source.indexOf('@media (max-width: 992px)'))
  assert.match(css, /\.fusion-workbench__actions,[\s\S]*?\.fusion-workbench__search\s*\{[^}]*flex:\s*none/)
  assert.match(css, /\.fusion-workbench__title\s*\{[^}]*flex:\s*none/)
  assert.match(source, /\.fusion-workbench__search :deep\(\.el-input\)\s*\{[^}]*min-width:\s*0;[^}]*flex:\s*1 1 0/)
  assert.match(source, /\.fusion-workbench__search :deep\(\.el-button\)\s*\{[^}]*flex:\s*0 0 auto/)
})
