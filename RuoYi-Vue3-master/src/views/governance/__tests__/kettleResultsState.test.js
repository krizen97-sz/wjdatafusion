import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { compileScript, parse } from '@vue/compiler-sfc'
import { computed, effectScope, nextTick, reactive, ref, watch } from 'vue'
import * as runRules from '../kettle/runRules.js'
import * as workbenchRules from '../kettle/workbenchRules.js'
import * as presentation from '../kettle/resultPresentation.js'

// Exercise the real component watchers without a browser or execution service.
const source = readFileSync(new URL('../kettle/KettleResults.vue', import.meta.url), 'utf8')
const { descriptor } = parse(source)
const compiled = compileScript(descriptor, { id: 'kettle-results-test', genDefaultAs: 'component' }).content.replace(/^import .*$/gm, '')
const bindings = { computed, ref, watch, ...runRules, ...workbenchRules, ...presentation }
const component = new Function(...Object.keys(bindings), `${compiled}\nreturn component`)(...Object.values(bindings))
function setup(run) {
  const scope = effectScope()
  const props = reactive({ run, events: [], schema: [], expanded: false, validation: null, validationKind: 'transformation', validationStale: false })
  let view
  scope.run(() => { view = component.setup(props, { expose() {}, emit() {} }) })
  return { scope, props, view }
}

test('polling follows failure until the user selects a tab; a different run gets its own useful default', async () => {
  const { scope, props, view } = setup({ id: 'job-a', kind: 'job', state: 'RUNNING' })
  try {
    assert.equal(view.tab.value, 'metrics')
    props.run = { ...props.run, state: 'FAILED', finalized: true }
    await nextTick()
    assert.equal(view.tab.value, 'logs')
    view.selectTab('files')
    props.run = { ...props.run, state: 'RECOVERY_REQUIRED' }
    await nextTick()
    assert.equal(view.tab.value, 'files')
    props.run = { id: 'job-b', kind: 'job', state: 'RUNNING' }
    await nextTick()
    assert.equal(view.tab.value, 'metrics')
  } finally { scope.stop() }
})

test('fresh field checks retain their context while old runs poll, and expired checks cannot show current success', async () => {
  const { scope, props, view } = setup({ id: 'old-run', kind: 'transformation', state: 'RUNNING' })
  try {
    props.validation = { valid: true, fieldsRequested: true, fieldsResolved: true }
    await nextTick()
    assert.equal(view.tab.value, 'validation')
    props.run = { ...props.run, state: 'FAILED', finalized: true }
    await nextTick()
    assert.equal(view.tab.value, 'validation')
    view.selectTab('fields')
    props.validationStale = true
    await nextTick()
    assert.equal(view.viewState.value.status.label, '结果已过期')
    assert.equal(view.viewState.value.status.type, 'warning')
  } finally { scope.stop() }
})
