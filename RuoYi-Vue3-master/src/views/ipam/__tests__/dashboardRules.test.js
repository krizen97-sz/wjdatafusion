import test from 'node:test'
import assert from 'node:assert/strict'
import {
  buildNetworkLoadMatrix,
  buildNetworkPressureDistribution,
  buildNetworkUsage,
  buildStationEfficiency,
  buildStationAllocation,
  buildStatusDistribution,
  filterCommunityOverview,
  normalizeDimensionRows,
  normalizeManufacturerRows
} from '../dashboardRules.js'

test('dashboard groups network capacity and occupation by police station', () => {
  const rows = buildStationAllocation([
    { policeStationName: '湖塘', totalCount: 256, freeCount: 100, reservedCount: 3, allocatedCount: 120, issuedCount: 30, disabledCount: 3 },
    { policeStationName: '湖塘', totalCount: 256, freeCount: 200, reservedCount: 3, allocatedCount: 50, issuedCount: 0, disabledCount: 3 },
    { policeStationName: '', totalCount: 256, freeCount: 253, reservedCount: 3 }
  ])

  assert.equal(rows[0].name, '湖塘')
  assert.equal(rows[0].networkCount, 2)
  assert.equal(rows[0].capacity, 506)
  assert.equal(rows[0].occupied, 200)
  assert.equal(rows[0].disabled, 6)
  assert.equal(rows.at(-1).name, '未分类')
})

test('network utilization keeps the current fixed three-address reservation rule', () => {
  const [row] = buildNetworkUsage([
    { networkId: 1, networkName: '湖塘一号网段', totalCount: 256, allocatedCount: 100, issuedCount: 25, gatewayIp: '2.57.1.1' }
  ])

  assert.equal(row.capacity, 253)
  assert.equal(row.occupied, 125)
  assert.equal(row.usage, 49.4)
})

test('community search covers names, IP, networks, devices and brands', () => {
  const rows = [
    { communityName: '湖塘花园', firstIp: '2.57.1.2', lastIp: '2.57.1.7', networkNameSummary: '湖塘网段', manufacturerSummary: '海康' },
    { communityName: '湖塘新村', firstIp: '2.57.1.20', lastIp: '2.57.1.29', networkNameSummary: '湖塘网段', manufacturerSummary: '大华' },
    { communityName: '鸣凰新村', firstIp: '2.57.4.20', lastIp: '2.57.4.22', targetTypeSummary: 'CVR', manufacturerSummary: '宇视' }
  ]

  assert.deepEqual(filterCommunityOverview(rows, '2.57.4'), [rows[2]])
  assert.deepEqual(filterCommunityOverview(rows, '2.57.1.2'), [rows[0]])
  assert.deepEqual(filterCommunityOverview(rows, '海康'), [rows[0]])
  assert.deepEqual(filterCommunityOverview(rows, 'CVR'), [rows[2]])
})

test('dashboard status and device dimensions normalize backend numbers and labels', () => {
  const status = buildStatusDistribution({ freeCount: '20', allocatedCount: 5 })
  const targetTypes = normalizeDimensionRows([{ name: 'RECORDER', value: '3' }, { name: 'CVR', value: 2 }], 'targetType')

  assert.equal(status[0].value, 20)
  assert.equal(status[2].value, 5)
  assert.deepEqual(targetTypes, [{ name: '录像机', value: 3 }, { name: 'CVR', value: 2 }])
})

test('dashboard pressure bands keep warning boundaries stable', () => {
  const bands = buildNetworkPressureDistribution([
    { usage: 20, capacity: 253, occupied: 50 },
    { usage: 60, capacity: 253, occupied: 152 },
    { usage: 80, capacity: 253, occupied: 203 },
    { usage: 90, capacity: 253, occupied: 228 }
  ])

  assert.deepEqual(bands.map((band) => band.value), [1, 1, 1, 1])
  assert.equal(bands[3].occupied, 228)
})

test('dashboard matrix sorts gateways inside each police station', () => {
  const matrix = buildNetworkLoadMatrix([
    { networkId: 2, stationName: '湖塘', gatewayIp: '2.57.27.1', usage: 80 },
    { networkId: 1, stationName: '湖塘', gatewayIp: '2.57.1.1', usage: 40 },
    { networkId: 3, stationName: '鸣凰', gatewayIp: '2.57.4.1', usage: 60 }
  ])

  assert.deepEqual(matrix.stations, ['湖塘', '鸣凰'])
  assert.deepEqual(matrix.columns, ['第1段', '第2段'])
  assert.equal(matrix.cells[0].networkId, 1)
  assert.deepEqual(matrix.cells[0].value, [0, 0, 40])
})

test('dashboard station efficiency calculates utilization from capacity', () => {
  const rows = buildStationEfficiency([
    { name: '湖塘', capacity: 500, occupied: 250 },
    { name: '鸣凰', capacity: 200, occupied: 180 }
  ])

  assert.equal(rows[0].name, '鸣凰')
  assert.equal(rows[0].usage, 90)
  assert.equal(rows[1].usage, 50)
})

test('dashboard masks credential-like manufacturer values without changing source data', () => {
  const rows = normalizeManufacturerRows([
    { name: '海康', value: 10 },
    { name: 'admin123', value: 3 },
    { name: 'a1234567', value: 2 },
    { name: '', value: 4 }
  ])

  assert.deepEqual(rows, [
    { name: '海康', value: 10 },
    { name: '待核验', value: 5 },
    { name: '未填写', value: 4 }
  ])
})
