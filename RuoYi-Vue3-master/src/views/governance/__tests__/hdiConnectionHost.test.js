import test from 'node:test'
import assert from 'node:assert/strict'
import { withOriginalConnectionManagement } from '../../../../../data-governance/hdi-native/host/native-task-host.mjs'

test('original source list keeps its render and methods while reading actual platform capacity', async () => {
  const original = { name: 'DatasourceTable', render() {}, methods: { queryList() {} } }
  const calls = [], value = { used: 2, total: 100, usedDbType: ['FTP'] }
  const wrapper = withOriginalConnectionManagement(original, { async get(path) { calls.push(path); return { code: '0', data: value } } })
  assert.equal(wrapper.extends, original)
  assert.equal(wrapper.render, undefined)
  const view = { licenseData: { usedDbType: [] } }
  await wrapper.methods.getDatasourceLicense.call(view)
  assert.equal(view.licenseData, value)
  assert.deepEqual(calls, ['/detl/_platform/connections/usage'])
})

test('unavailable platform capacity preserves the previous observation instead of manufacturing authorization', async () => {
  const original = { name: 'DatasourceTable', methods: { queryList() {} } }
  const previous = { used: 2, total: 100, usedDbType: ['FTP'] }, errors = []
  const view = { licenseData: previous, $message: { error(value) { errors.push(value) } } }
  const wrapper = withOriginalConnectionManagement(original, { async get() { throw new Error('unavailable') } })
  await wrapper.methods.getDatasourceLicense.call(view)
  assert.equal(view.licenseData, previous)
  assert.equal(errors.length, 1)
  assert.throws(() => withOriginalConnectionManagement({ name: 'Different' }, {}))
})
