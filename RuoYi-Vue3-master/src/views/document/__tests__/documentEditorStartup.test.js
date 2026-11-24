import test from 'node:test'
import assert from 'node:assert/strict'
import { createEditorApiLoader, prepareDocumentEditor } from '../editor/documentEditorStartup.js'

function fakeBrowser() {
  const scripts = []
  const timers = new Set()
  const host = {
    document: {
      createElement() { return { remove() { scripts.splice(scripts.indexOf(this), 1) } } },
      head: { appendChild(script) { scripts.push(script) } }
    },
    setTimeout(fn) { timers.add(fn); return fn },
    clearTimeout(fn) { timers.delete(fn) }
  }
  return { host, scripts, timers }
}

const apiJsUrl = '/onlyoffice/web-apps/apps/api/documents/api.js'

test('editor uses the details returned with the signed configuration on first open', async () => {
  const documentInfo = { documentId: 5, title: '协作计划', contentVersion: 3, accessPermission: 'VIEW' }
  const api = { DocEditor() {} }
  const prepared = await prepareDocumentEditor({ documentInfo, apiJsUrl }, async (url) => {
    assert.equal(url, apiJsUrl)
    return api
  }, () => assert.fail('unexpected duplicate detail request'))
  assert.deepEqual(prepared, { api, documentInfo })
})

test('old configuration starts loading the editor API while version details are pending', async () => {
  const { host, scripts } = fakeBrowser()
  const documentInfo = { contentVersion: 1 }
  let resolveDetail
  const prepared = prepareDocumentEditor({ apiJsUrl }, createEditorApiLoader(host),
    () => new Promise((resolve) => { resolveDetail = resolve }))
  assert.equal(scripts[0].src, apiJsUrl)
  host.DocsAPI = { DocEditor() {} }
  scripts[0].onload()
  resolveDetail(documentInfo)
  assert.equal((await prepared).documentInfo, documentInfo)
})

test('concurrent startup requests share one API script; successful retries reuse the loaded API', async () => {
  const { host, scripts, timers } = fakeBrowser()
  const load = createEditorApiLoader(host)
  const first = load(apiJsUrl)
  assert.equal(first, load(apiJsUrl))
  assert.equal(scripts.length, 1)
  host.DocsAPI = { DocEditor() {} }
  scripts[0].onload()
  assert.equal(await first, host.DocsAPI)
  assert.equal(await load(apiJsUrl), host.DocsAPI)
  assert.equal(timers.size, 0)
  assert.equal(scripts.length, 1)
})

test('network failure, empty script and timeout each permit an immediate fresh retry', async () => {
  const { host, scripts, timers } = fakeBrowser()
  const load = createEditorApiLoader(host)
  for (const fail of [() => scripts[0].onerror(), () => scripts[0].onload(), () => [...timers][0]()]) {
    const attempt = load(apiJsUrl)
    assert.equal(scripts.length, 1)
    fail()
    await assert.rejects(attempt, /内网编辑器|编辑器脚本/)
    assert.equal(timers.size, 0)
    assert.equal(scripts.length, 0)
  }
  const retry = load(apiJsUrl)
  host.DocsAPI = { DocEditor() {} }
  scripts[0].onload()
  assert.equal(await retry, host.DocsAPI)
})
