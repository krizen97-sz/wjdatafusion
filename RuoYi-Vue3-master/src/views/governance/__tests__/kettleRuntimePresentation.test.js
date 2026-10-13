import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import vm from 'node:vm'
import { runtimePresentation } from '../kettle/runtimePresentation.js'
import { STARTER_CSV, STARTER_FILENAME, starterFlowXml, starterInputForm } from '../kettle/starterFlow.js'

test('managed readiness requires the ready process state and a healthy execution service', () => {
  for (const runtimeState of ['DISABLED', 'STARTING', 'FAILED', 'STOPPED', 'unexpected', undefined]) {
    assert.equal(runtimePresentation({ runtimeMode: 'managed', runtimeState, enabled: true, executionAvailable: true }).ready, false)
  }
  assert.equal(runtimePresentation({ runtimeMode: 'managed', runtimeState: 'READY', enabled: true, executionAvailable: true }).ready, true)
  assert.equal(runtimePresentation({ runtimeMode: 'managed', runtimeState: 'READY', enabled: true, executionAvailable: false }).ready, false)
  assert.equal(runtimePresentation({ enabled: true, executionAvailable: true }).ready, true, 'existing external configurations remain supported')
  assert.equal(runtimePresentation({ enabled: false, executionAvailable: true }).ready, false)
})

test('runtime messages separate automatic preparation, failure, stopped state and unavailable draft storage', () => {
  const starting = runtimePresentation({ runtimeMode: 'managed', runtimeState: 'STARTING' })
  assert.equal(starting.poll, true)
  assert.match(starting.guidance, /自动更新/)
  const failure = runtimePresentation({ runtimeMode: 'managed', runtimeState: 'FAILED', runtimeMessage: '引擎组件校验失败', draftStorageAvailable: false })
  assert.equal(failure.type, 'error')
  assert.match(failure.guidance, /引擎组件校验失败/)
  assert.match(failure.guidance, /任务存储也不可用/)
  assert.equal(failure.poll, false)
  assert.match(runtimePresentation({ runtimeMode: 'managed', runtimeState: 'STOPPED' }).label, /已停止/)
})

test('the starter carries native local file boundaries and an executable synthetic cleaning rule', async () => {
  const xml = starterFlowXml('示例<&"')
  assert.match(xml, /<name>示例&lt;&amp;&quot;<\/name>/)
  assert.deepEqual([...xml.matchAll(/<type>(CsvInput|ScriptValueMod|SortRows|TextFileOutput)<\/type>/g)].map(item => item[1]), ['CsvInput', 'ScriptValueMod', 'SortRows', 'TextFileOutput'])
  assert.match(xml, /<filename>\$\{INPUT_DIR\}\/sample.csv<\/filename>/)
  assert.match(xml, /<name>\$\{WORK_DIR\}\/result<\/name>/)
  assert.equal((xml.match(/<hop>/g) || []).length, 3)
  assert.doesNotMatch(xml, /<connection>|<server>|https?:\/\//)
  const script = xml.match(/<jsScript_script>(.*?)<\/jsScript_script>/s)[1]
  const context = { name: ' alice ' }
  vm.runInNewContext(script, context, { timeout: 1000 })
  assert.equal(context.cleaned_name, 'ALICE')
  const input = starterInputForm().get('file')
  assert.equal(input.name, STARTER_FILENAME)
  assert.equal(await input.text(), STARTER_CSV)
})

test('default data development does not mount the legacy NiFi workspace', () => {
  const source = readFileSync(new URL('../workspace/index.vue', import.meta.url), 'utf8')
  assert.match(source, /route\.query\.tab && route\.query\.tab !== 'kettle' \? 'legacy' : 'kettle'/)
  assert.match(source, /<LegacyWorkspace v-if="workspace === 'legacy'"/)
  assert.doesNotMatch(source, /getOverview|governanceOverview|onMounted/)
})
