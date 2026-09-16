import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { compileScript, parse } from '@vue/compiler-sfc'
import { computed, effectScope, markRaw, nextTick, ref } from 'vue'
import * as xmlModel from '../kettle/xmlModel.js'
import * as schemas from '../kettle/nodeSchemas.js'
import * as runRules from '../kettle/runRules.js'
import * as workbenchRules from '../kettle/workbenchRules.js'
import * as interaction from '../kettle/workbenchInteraction.js'
import * as graphRules from '../graphRules.js'
import * as workspaceRules from '../workspaceRules.js'

// Run the actual setup handlers and computed guards. Only environment ports (API,
// lifecycle, router, XML codec) are replaced; this is not an XML or rendering test.
const { descriptor } = parse(readFileSync(new URL('../kettle/KettleWorkbench.vue', import.meta.url), 'utf8'))
const compiled = compileScript(descriptor, { id: 'workbench-async-test', genDefaultAs: 'component' }).content.replace(/^import .*$/gm, '')
const deferred = () => {
  let resolve, reject
  const promise = new Promise((yes, no) => { resolve = yes; reject = no })
  return { promise, resolve, reject }
}
const detail = (id, revision = 1, name = id) => ({ id, revision, xmlBase64: JSON.stringify({ graph: { name, kind: 'transformation', nodes: [], connections: [] } }) })

function setup(t, methods = {}, confirm = async () => {}) {
  const calls = [], warnings = [], unmount = [], scope = effectScope()
  const api = new Proxy({}, { get: (_, name) => (...args) => {
    calls.push({ name, args })
    assert.equal(typeof methods[name], 'function', `Unexpected API call: ${name}`)
    return methods[name](...args)
  } })
  const bindings = {
    computed, markRaw, nextTick, ref,
    ...xmlModel, ...schemas, ...runRules, ...workbenchRules, ...interaction, ...graphRules, ...workspaceRules,
    removeGraphConnection: xmlModel.removeConnection,
    parseXml: JSON.parse, xmlText: JSON.stringify, fromBase64: value => value, toBase64: value => value,
    graphFromXml: doc => doc.graph,
    getCurrentInstance: () => ({ proxy: { $auth: { hasPermi: () => true }, $modal: { confirm, msgWarning: value => warnings.push(value), msgSuccess() {}, msgError() {} } } }),
    useRoute: () => ({ query: {} }), useRouter: () => ({ replace: async () => {} }),
    onMounted() {}, onBeforeUnmount: callback => unmount.push(callback),
    localStorage: { getItem: () => null, removeItem() {} },
    window: { removeEventListener() {} }, cancelAnimationFrame() {},
    api, saveAs() {}, FlowDiagram: {}, KettleNodeConfig: {}, KettleResults: {}, KettleSchedules: {}, KettleValueInput: {}
  }
  const component = new Function(...Object.keys(bindings), `${compiled}\nreturn component`)(...Object.values(bindings))
  let view
  scope.run(() => { view = component.setup({}, { expose() {} }) })
  t.after(() => { unmount.forEach(callback => callback()); scope.stop() })
  view.setDocument(detail('task-a'))
  return { view, calls, warnings }
}

test('removing an input captures its task, blocks concurrent editing, and ignores a late response after switching tasks', async t => {
  const approval = deferred(), deletion = deferred(), inputs = deferred()
  const { view, calls } = setup(t, {
    deleteKettleInputFile: () => deletion.promise,
    kettleInputFiles: () => inputs.promise
  }, () => approval.promise)
  const removal = view.removeFile({ id: 'input-a', name: 'a.csv' })
  approval.resolve()
  await nextTick()
  assert.equal(view.uploading.value, true)
  assert.equal(view.canChange.value, false)
  assert.equal(await view.leaveDraft(), false)
  view.setDocument(detail('task-b'))
  view.files.value = [{ id: 'input-b', name: 'b.csv' }]
  deletion.resolve({ code: 200 })
  await nextTick()
  assert.deepEqual(calls.map(call => [call.name, ...call.args]), [
    ['deleteKettleInputFile', 'task-a', 'input-a'], ['kettleInputFiles', 'task-a']
  ])
  inputs.resolve({ data: [] })
  await removal
  assert.deepEqual(view.files.value, [{ id: 'input-b', name: 'b.csv' }])
  assert.equal(view.uploading.value, false)

  const nextApproval = deferred()
  const other = setup(t, {}, () => nextApproval.promise)
  const cancelledOwner = other.view.removeFile({ id: 'input-a', name: 'a.csv' })
  other.view.setDocument(detail('task-b'))
  nextApproval.resolve()
  await cancelledOwner
  assert.equal(other.calls.length, 0, 'a confirmation for another task must not delete anything')
})

test('history selection cannot replace an active run and unlock its editor', async t => {
  const historical = { id: 'old-run', state: 'PREVIEW_COMPLETE', finalized: true }
  const { view, calls, warnings } = setup(t, {
    kettleRun: async () => ({ data: historical }),
    kettleEvents: async () => ({ data: { events: [], nextCursor: 0 } })
  })
  view.run.value = { id: 'active-run', state: 'RUNNING' }
  view.historyOpen.value = true
  await view.openRecordedRun(historical)
  assert.equal(view.run.value.id, 'active-run')
  assert.equal(view.historyOpen.value, true)
  assert.equal(view.canChange.value, false)
  assert.equal(calls.length, 0)
  assert.equal(warnings.length, 1)
  view.run.value = { id: 'active-run', state: 'SUCCEEDED', finalized: true }
  await view.openRecordedRun(historical)
  assert.equal(view.run.value.id, 'old-run')
  assert.equal(view.historyOpen.value, false)
  assert.equal(view.canChange.value, true)
  assert.deepEqual(calls.map(call => call.name), ['kettleRun', 'kettleEvents'])
})

test('late history and stop responses cannot overwrite the newly selected task or run', async t => {
  const history = deferred(), stopping = deferred()
  const { view, calls } = setup(t, {
    kettleRunHistory: () => history.promise,
    stopKettleRun: () => stopping.promise
  })
  view.run.value = { id: 'run-a', state: 'RUNNING' }
  const loading = view.loadHistory(), stop = view.stop()
  assert.equal(view.stopping.value, true)
  assert.equal(view.canChange.value, false)
  view.setDocument(detail('task-b'))
  view.run.value = { id: 'run-b', state: 'SUCCEEDED', finalized: true }
  view.history.value = [{ id: 'run-b' }]
  history.resolve({ data: [{ id: 'run-a' }] })
  stopping.resolve({ data: { id: 'run-a', state: 'STOPPED', finalized: true } })
  await Promise.all([loading, stop])
  assert.deepEqual(view.history.value, [{ id: 'run-b' }])
  assert.equal(view.run.value.id, 'run-b')
  assert.equal(view.stopping.value, false)
  assert.equal(view.historyLoading.value, false)
  assert.deepEqual(calls.map(call => [call.name, ...call.args]), [['kettleRunHistory', 'task-a'], ['stopKettleRun', 'run-a']])
})

test('save applies the current node draft before locking the editor and retains the selected panel after saving', async t => {
  const saved = deferred()
  let applied = 0, payload
  const { view } = setup(t, {
    saveKettleDefinition: (id, data) => { assert.equal(id, 'task-a'); payload = data; return saved.promise },
    kettleDefinitions: async () => ({ data: [detail('task-a', 2, 'edited')] })
  })
  view.configDirty.value = true
  view.selectedNodeId.value = 'selected-node'
  view.inspectorOpen.value = false
  view.resultsExpanded.value = true
  view.nodeEditor.value = { apply() {
    assert.equal(view.canChange.value, true, 'the node form must still be editable while applying its draft')
    applied++
    view.document.value.graph.name = 'edited'
    view.configDirty.value = false
    view.changed()
    return true
  } }
  const saving = view.save()
  assert.equal(applied, 1)
  assert.equal(view.saving.value, true)
  assert.equal(view.canChange.value, false)
  assert.equal(payload.name, 'edited')
  assert.equal(JSON.parse(payload.xmlBase64).graph.name, 'edited')
  assert.equal(await view.save(), false, 'a second save must not submit while the first is pending')
  saved.resolve({ data: detail('task-a', 2, 'edited') })
  assert.equal(await saving, true)
  assert.equal(view.definition.value.revision, 2)
  assert.equal(view.selectedNodeId.value, 'selected-node')
  assert.equal(view.inspectorOpen.value, false)
  assert.equal(view.resultsExpanded.value, true)
  assert.equal(view.dirty.value, false)
  assert.equal(view.saving.value, false)
})

test('a field-check response for an old document cannot become the current validation result', async t => {
  const validation = deferred()
  const { view, calls } = setup(t, { validateKettleDefinition: () => validation.promise })
  const checking = view.validate()
  await nextTick()
  assert.equal(view.validating.value, true)
  assert.equal(view.canChange.value, false)
  view.setDocument(detail('task-b'))
  validation.resolve({ data: { valid: true, fieldsRequested: true, fieldsResolved: true, nodes: [] } })
  await checking
  assert.equal(view.validation.value, null)
  assert.equal(view.validating.value, false)
  assert.deepEqual(calls.map(call => [call.name, ...call.args]), [['validateKettleDefinition', 'task-a']])
})
