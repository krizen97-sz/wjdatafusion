import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { DEFAULT_NEW_SERVER_SSH_PORT } from '../components/equipmentIntake.rules.js'
import { resolveIntakePlatform, buildEquipmentCreatePayload, getDeviceIntakeGaps } from '../components/equipmentIntake.rules.js'

test('device controls stay at equipment level and server entry provides all three intake modes', () => {
  const site = readFileSync(new URL('../index.vue', import.meta.url), 'utf8')
  const config = readFileSync(new URL('../SiteConfigDialog.vue', import.meta.url), 'utf8')
  const intake = readFileSync(new URL('../components/EquipmentIntakeDialog.vue', import.meta.url), 'utf8')
  assert.doesNotMatch(site.split('<script')[0], />新增设备<|>设备管理</)
  const toolbarEnd = config.indexOf('</header>')
  assert.ok(toolbarEnd > 0)
  assert.doesNotMatch(config.slice(0, toolbarEnd), />新增设备<|>设备管理</)
  for (const label of ['单台新增', '批量新增', '模板导入']) assert.ok(intake.includes(`label="${label}"`))
  assert.ok(intake.includes('accept=".xlsx"'))
  assert.ok(intake.includes('previewServers'))
  assert.equal(DEFAULT_NEW_SERVER_SSH_PORT, 55555)
  assert.ok(intake.includes('sshPort: DEFAULT_NEW_SERVER_SSH_PORT'))
})

test('site-wide intake never inherits a stale platform and servers require a child platform', () => {
  const platforms = [{ platformId: 1, platformLevel: 'MAIN' }, { platformId: 2, platformLevel: 'SUB' }]
  assert.equal(resolveIntakePlatform(platforms, null, 'SERVER'), null)
  assert.equal(resolveIntakePlatform(platforms, 1, 'SERVER'), null)
  assert.equal(resolveIntakePlatform(platforms, '2', 'SERVER'), 2)
  assert.equal(resolveIntakePlatform(platforms, 1, 'SWITCH'), 1)
  assert.equal(resolveIntakePlatform(platforms, 999, 'SWITCH'), null)
})

test('server creation maps address and credentials without mutating passwords or sending unrelated data', () => {
  const payload = buildEquipmentCreatePayload(7, {
    assetType: 'SERVER', assetName: ' A ', ipAddress: ' host.internal ', platformId: 2,
    sshPort: 2222, status: '0', loginUsername: ' ops ', loginPassword: ' secret ',
    rootPassword: 'root secret', serverId: 999, equipmentRoom: 'old room'
  })
  assert.equal(payload.server.serverAddress, 'host.internal')
  assert.equal(payload.server.otherPassword, ' secret ')
  assert.equal(payload.server.otherUsername, 'ops')
  assert.equal(payload.platformId, 2)
  assert.equal(payload.server.serverId, undefined)
  assert.equal(payload.server.equipmentRoom, undefined)
  assert.equal(payload.hardware, undefined)
})

test('hardware creation carries only editable intake fields and explicit public scope', () => {
  const payload = buildEquipmentCreatePayload(7, { assetType: 'SWITCH', assetName: '交换机', ipAddress: '10.0.0.1',
    networkEnv: 'POLICE', loginPassword: ' pwd ', assetId: 8, loginPasswordCipher: 'cipher' })
  assert.equal(payload.platformId, null)
  assert.equal(payload.hardware.loginPassword, ' pwd ')
  assert.equal(payload.hardware.assetId, undefined)
  assert.equal(payload.hardware.loginPasswordCipher, undefined)
  assert.equal(payload.server, undefined)
})

test('incomplete filters use typed device identity for uplinks and distinguish placement and ownership', () => {
  const device = { sourceType: 'SERVER', sourceId: 1, cabinetId: 2, rackUStart: 1, rackUEnd: 2, platformIds: [4] }
  assert.deepEqual(getDeviceIntakeGaps(device, [{ sourceType: 'HARDWARE', sourceId: 1 }]), ['NO_UPLINK'])
  assert.deepEqual(getDeviceIntakeGaps(device, [{ sourceType: 'SERVER', sourceId: 1 }]), [])
  assert.deepEqual(getDeviceIntakeGaps({ sourceType: 'SERVER', sourceId: 1 }), ['UNPLACED', 'UNBOUND', 'NO_UPLINK'])
})
