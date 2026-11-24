import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { compileScript, parse } from '@vue/compiler-sfc'
import { computed, reactive, ref } from 'vue'
import * as rules from '../workspace/documentWorkspaceRules.js'

// Exercise the workspace's real event handlers, with only lifecycle and HTTP
// replaced so that responses can be delivered in an explicit order.
const { descriptor } = parse(readFileSync(new URL('../workspace/index.vue', import.meta.url), 'utf8'))
const compiled = compileScript(descriptor, { id: 'document-favorites', genDefaultAs: 'component' })
const components = Object.fromEntries(Object.entries(compiled.imports)
  .filter(([, binding]) => binding.source.endsWith('.vue')).map(([name]) => [name, {}]))
const script = compiled.content
  .replace(/^import [\s\S]*? from '[^']+'$/gm, '')

function workspace(setDocumentFavorite, listDocumentsRequest) {
  const bindings = {
    ...components, ...rules, setDocumentFavorite, listDocumentsRequest, computed, reactive, ref,
    useRouter: () => ({}), checkRole: () => false, checkPermi: () => true,
    getCurrentInstance: () => ({ proxy: { $modal: { msgSuccess() {} } } }),
    window: { localStorage: { getItem: () => null } }, watch() {}, onMounted() {}, onBeforeUnmount() {}
  }
  const component = new Function(...Object.keys(bindings), script + '\nreturn component')(...Object.values(bindings))
  return component.setup({}, { expose() {} })
}

test('duplicate clicks send a single mutation and unstar clears the removed selection only after success', async () => {
  let complete
  const requests = []
  const page = workspace((id, favorite) => {
    requests.push([id, favorite])
    return new Promise(resolve => { complete = resolve })
  })
  page.query.scope = 'FAVORITES'
  page.documents.value = [{ documentId: 10, favorite: true }, { documentId: 20, favorite: true }]
  page.selectedDocumentIds.value = [10, 20]
  const row = page.documents.value[0]
  const save = page.toggleFavorite(row)
  await page.toggleFavorite(row)
  assert.equal(page.favoriteButtonProps(row).disabled, true)
  assert.equal(page.documents.value.length, 2)
  complete()
  await save
  assert.deepEqual(requests, [[10, false]])
  assert.deepEqual(page.documents.value.map(item => item.documentId), [20])
  assert.deepEqual(page.selectedDocumentIds.value, [20])
})

test('a late list response from before the star change cannot overwrite the new favorite status', async () => {
  let stale
  let calls = 0
  const row = { documentId: 7, favorite: false }
  const page = workspace(async () => {}, () => ++calls === 1
    ? new Promise(resolve => { stale = resolve })
    : Promise.resolve({ data: [{ ...row, favorite: true }] }))
  page.documents.value = [row]
  const loading = page.loadDocuments()
  await page.toggleFavorite(page.documents.value[0])
  stale({ data: [{ documentId: 7, favorite: false }] })
  await loading
  assert.equal(page.documents.value[0].favorite, true)
  assert.equal(page.loading.documents, false)
})

test('failed writes reconcile lost access and a favorites category change reads the current query', async () => {
  let complete
  const queries = []
  const page = workspace(() => new Promise((_, reject) => { complete = reject }), async params => {
    queries.push(params)
    return { data: [] }
  })
  page.documents.value = [{ documentId: 10, favorite: false }]
  const save = page.toggleFavorite(page.documents.value[0])
  page.selectScope('FAVORITES')
  complete(new Error('Permission revoked'))
  await save
  assert.equal(page.pendingFavorites.size, 0)
  assert.deepEqual(page.documents.value, [])
  assert.equal(queries[0].scope, 'FAVORITES')
  assert.equal(queries[0].folderId, null)
})
