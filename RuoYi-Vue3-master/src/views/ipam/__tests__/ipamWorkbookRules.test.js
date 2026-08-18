import test from 'node:test'
import assert from 'node:assert/strict'
import {
  buildCommunityScopeTree,
  buildNetworkScopeTree,
  buildNetworkWorkbookRows,
  buildWorkbookCommitBatches,
  filterScopeTree,
  markWorkbookRowsDirty,
  toWorkbookCommitRow,
  validateWorkbookRows,
  workbookRowMatches
} from '../workbook/ipamWorkbookRules.js'

test('network scope groups stations and sorts each group by gateway address', () => {
  const tree = buildNetworkScopeTree([
    { networkId: 2, policeStationName: '湖塘', networkName: 'B', gatewayIp: '2.57.10.1', startIp: '2.57.10.0', endIp: '2.57.10.255', totalCount: 256 },
    { networkId: 1, policeStationName: '湖塘', networkName: 'A', gatewayIp: '2.57.2.1', startIp: '2.57.2.0', endIp: '2.57.2.255', totalCount: 256 }
  ])

  assert.equal(tree.length, 1)
  assert.deepEqual(tree[0].children.map((item) => item.value.networkId), [1, 2])
  assert.equal(filterScopeTree(tree, '2.57.2.188')[0].children[0].value.networkId, 1)
})

test('community scope keeps exact community records and searchable summaries', () => {
  const tree = buildCommunityScopeTree([
    { communityName: '湖塘花园', policeStationSummary: '湖塘', networkNameSummary: '2.57.1网段', addressCount: 4 },
    { communityName: '星河国际', policeStationSummary: '鸣凰', networkNameSummary: '2.57.3网段', addressCount: 2 }
  ])

  assert.equal(filterScopeTree(tree, '2.57.3')[0].children[0].label, '星河国际')
  assert.equal(filterScopeTree(tree, '不存在').length, 0)
})

test('network rows keep boundary and gateway addresses read only', () => {
  const rows = buildNetworkWorkbookRows([
    { ipAddress: '2.57.1.0', status: 'RESERVED', boundaryAddress: true },
    { ipAddress: '2.57.1.1', status: 'RESERVED', reservedReason: '网关' },
    { ipAddress: '2.57.1.2', status: 'FREE' }
  ], { networkId: 7, networkName: '测试网段' })

  assert.equal(rows[0]._locked, true)
  assert.equal(rows[1]._locked, true)
  assert.equal(rows[2]._locked, false)
})

test('free row becomes allocated only after business data is entered', () => {
  const empty = toWorkbookCommitRow({ statusCode: 'FREE', ipAddress: '2.57.1.2' })
  const configured = toWorkbookCommitRow({
    statusCode: 'FREE',
    ipAddress: '2.57.1.2',
    communityName: '湖塘花园',
    targetTypeLabel: '录像机'
  })

  assert.equal(empty, null)
  assert.equal(configured.status, 'ALLOCATED')
  assert.equal(configured.targetType, 'RECORDER')
})

test('only community or project name is required for edited business rows', () => {
  assert.equal(validateWorkbookRows([{ ipAddress: '2.57.1.2', statusCode: 'FREE', targetName: '录像机1' }]).length, 1)
  assert.equal(validateWorkbookRows([{ ipAddress: '2.57.1.2', statusCode: 'FREE', communityName: '湖塘花园' }]).length, 0)
  assert.equal(validateWorkbookRows([{ ipAddress: '2.57.1.3', statusCode: 'ALLOCATED', communityName: '' }]).length, 1)
  assert.match(validateWorkbookRows([{ ipAddress: '10.0.0.2', statusCode: 'FREE', targetName: '平台1' }], '项目名称')[0], /项目名称/)
})

test('dirty rows are grouped by network and chunked to existing commit limit', () => {
  const rows = Array.from({ length: 257 }, (_, index) => ({
    _rowKey: `1:${index}`,
    _locked: false,
    networkId: 1,
    ipAddress: `2.57.1.${index}`,
    statusCode: 'FREE',
    communityName: '湖塘花园'
  }))
  const batches = buildWorkbookCommitBatches(rows, new Set(rows.map((row) => row._rowKey)))

  assert.deepEqual(batches.map((batch) => batch.rows.length), [256, 1])
})

test('grid edit events and row search work for single and range edits', () => {
  const singleRow = { _rowKey: '1:2.57.1.2' }
  const rangeRows = { 1: { _rowKey: 'a' }, 2: { _rowKey: 'b' } }
  assert.deepEqual(markWorkbookRowsDirty({ model: singleRow }), ['1:2.57.1.2'])
  assert.equal(singleRow._dirty, true)
  assert.deepEqual(markWorkbookRowsDirty({ data: rangeRows }), ['a', 'b'])
  assert.equal(rangeRows[1]._dirty, true)
  assert.equal(rangeRows[2]._dirty, true)
  assert.equal(workbookRowMatches({ communityName: '湖塘花园', ipAddress: '2.57.1.2' }, '花园'), true)
  assert.equal(workbookRowMatches({ communityName: '湖塘花园', ipAddress: '2.57.1.2' }, '2.57.9'), false)
})
