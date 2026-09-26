import assert from 'node:assert/strict'
import test from 'node:test'
import { readFileSync } from 'node:fs'
import { parse } from '@vue/compiler-sfc'
import { accountKey, accountMutation, displayAccountName, orderAccounts } from '../components/equipmentAccounts.rules.js'

test('HIK display does not silently mutate the actual SSH username', () => {
  assert.equal(displayAccountName('hik'), 'HIK')
  assert.equal(displayAccountName('HIK'), 'HIK')
  assert.equal(displayAccountName('root'), 'root')
  const data = accountMutation(1, { username: 'hik', credentialId: 9, password: '' })
  assert.equal(data.username, 'hik')
  assert.equal(data.password, null)
})

test('legacy and stored credentials have stable different identities', () => {
  assert.notEqual(accountKey({ username: 'root' }), accountKey({ username: 'root', credentialId: 1 }))
  const data = accountMutation(1, { username: 'HIK', legacyRegistration: true, password: '  secret  ' })
  assert.equal(data.legacyRegistration, true)
  assert.equal(data.password, '  secret  ')
  assert.equal(data.credentialName, 'HIK账号')
  assert.equal('passwordCipher' in data, false)
  const accounts = [{ username: 'ops' }, { username: 'root' }, { username: 'hik' }]
  assert.deepEqual(orderAccounts(accounts).map(row => row.username), ['hik', 'root', 'ops'])
  assert.equal(accounts[0].username, 'ops')
})

test('configuration uses a centered action and visible edit/delete without a second credential dialog', () => {
  const read = name => readFileSync(new URL(name, import.meta.url), 'utf8')
  const cell = read('../components/EquipmentInventoryCell.vue')
  const workspace = read('../components/EquipmentRoom3DWorkspace.vue')
  const parent = read('../SiteConfigDialog.vue')
  assert.match(cell, /height: 100%/)
  assert.match(cell, /align-items: center/)
  assert.match(workspace, /@click="removeSelectedDevice\(\)">删除设备/)
  assert.doesNotMatch(workspace, /更多设备操作|manage-credentials|>管理账号</)
  assert.doesNotMatch(parent, /serverCredentialDialogOpen|服务器凭据档案/)
  assert.match(workspace, /room3d-device-location/)
  assert.match(workspace, /label="机房"/)
  assert.match(workspace, /label="机柜"/)
  assert.match(workspace, /label="U位"/)
  assert.equal(parse(workspace).errors.length, 0)
})

test('account panel keeps native controls, explicit plain-text permission and draft guards', () => {
  const component = readFileSync(new URL('../components/EquipmentAccountsPanel.vue', import.meta.url), 'utf8')
  assert.equal(parse(component).errors.length, 0)
  assert.match(component, /el-table/)
  assert.match(component, /el-form/)
  assert.match(component, /support:credential:viewPlain/)
  assert.match(component, /defineExpose\(\{ confirmClose \}\)/)
  assert.doesNotMatch(component, /localStorage|sessionStorage/)
})
