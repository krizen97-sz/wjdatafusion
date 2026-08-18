import test from 'node:test'
import assert from 'node:assert/strict'
import {
  MAX_CONFIG_SELECTION,
  UNASSIGNED_STATION_NAME,
  appendSelectionRange,
  buildNetworkStationTree,
  normalizeIpv4Octet,
  splitIpv4Value,
  storeRevealedCredential
} from '../ipamRules.js'

test('network tree groups by police station and sorts children by gateway address', () => {
  const tree = buildNetworkStationTree([
    { networkId: 3, policeStationName: '湖塘派出所', gatewayIp: '2.57.3.1', freeCount: 10 },
    { networkId: 1, policeStationName: '鸣凰派出所', gatewayIp: '2.57.1.1', freeCount: 20 },
    { networkId: 2, policeStationName: '湖塘派出所', gatewayIp: '2.57.2.1', allocatedCount: 3 },
    { networkId: 5, policeStationName: '星辰派出所', gatewayIp: '2.57.5.1' }
  ])

  assert.deepEqual(tree.map((group) => group.stationName), ['鸣凰派出所', '湖塘派出所', '星辰派出所'])
  assert.deepEqual(tree[1].children.map((item) => item.network.networkId), [2, 3])
  assert.equal(tree[1].networkCount, 2)
  assert.equal(tree[1].freeCount, 10)
  assert.equal(tree[1].occupiedCount, 3)
})

test('network tree keeps blank legacy ownership in an unassigned group at the end', () => {
  const tree = buildNetworkStationTree([
    { networkId: 1, policeStationName: '  ', gatewayIp: '2.57.0.1' },
    { networkId: 2, policeStationName: '湖塘派出所', gatewayIp: '2.57.10.1' }
  ])

  assert.equal(tree.at(-1).stationName, UNASSIGNED_STATION_NAME)
  assert.equal(tree.at(-1).isUnassigned, true)
  assert.equal(tree.at(-1).children[0].network.networkId, 1)
})

test('invalid IPv4 octet is preserved for validation instead of clamped', () => {
  assert.equal(normalizeIpv4Octet('999'), '999')
  assert.deepEqual(splitIpv4Value('2.57.1.999'), ['2', '57', '1', '999'])
})

test('IPv4 octet strips non-digits without changing a valid number', () => {
  assert.equal(normalizeIpv4Octet(' 025x '), '025')
})

test('selection range is capped and keeps stable IP order', () => {
  const addresses = Array.from({ length: 300 }, (_, index) => ({ ipAddress: `2.57.1.${index}` }))
  const selected = appendSelectionRange(new Set(), addresses, 0, 299)

  assert.equal(MAX_CONFIG_SELECTION, 256)
  assert.equal(selected.size, 256)
  assert.equal([...selected][0], '2.57.1.0')
  assert.equal([...selected][255], '2.57.1.255')
})

test('range removal does not rebuild addresses outside the selected window', () => {
  const addresses = Array.from({ length: 10 }, (_, index) => ({ ipAddress: `2.57.1.${index}` }))
  const initial = new Set(addresses.map((item) => item.ipAddress))
  const selected = appendSelectionRange(initial, addresses, 3, 5, false)

  assert.deepEqual([...selected], [
    '2.57.1.0', '2.57.1.1', '2.57.1.2', '2.57.1.6', '2.57.1.7', '2.57.1.8', '2.57.1.9'
  ])
})

test('viewed credential never contaminates the editable password field', () => {
  const row = { loginPassword: null }

  assert.equal(storeRevealedCredential(row, 'view-only-secret'), 'view-only-secret')
  assert.equal(row._revealedPassword, 'view-only-secret')
  assert.equal(row.loginPassword, null)
})
