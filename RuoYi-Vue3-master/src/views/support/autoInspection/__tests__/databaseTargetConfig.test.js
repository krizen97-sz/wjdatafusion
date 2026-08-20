import assert from 'node:assert/strict'
import test from 'node:test'
import { hydrateDatabaseTarget, normalizeDatabaseTargetConfig } from '../databaseTargetConfig.js'

test('restores the persisted database type, SQL and result mode', () => {
  const target = hydrateDatabaseTarget({
    targetId: 6,
    password: '******',
    extraParams: JSON.stringify({
      databaseType: 'POSTGRESQL',
      query: 'select count(*) as total from vehicle',
      resultMode: 'FIRST_VALUE'
    })
  }, {
    databaseConfig: { databaseType: 'MYSQL', query: '', resultMode: 'FIRST_VALUE' }
  })

  assert.deepEqual(target.databaseConfig, {
    databaseType: 'POSTGRESQL',
    query: 'select count(*) as total from vehicle',
    resultMode: 'FIRST_VALUE'
  })
  assert.equal(target.password, '******')
  assert.equal(target._passwordVisible, false)
})

test('keeps current form edits ahead of the persisted JSON', () => {
  assert.deepEqual(normalizeDatabaseTargetConfig({
    extraParams: '{"databaseType":"MYSQL","query":"select 1","resultMode":"FIRST_VALUE"}',
    databaseConfig: {
      databaseType: 'POSTGRESQL',
      query: 'select 2',
      resultMode: 'ROW_COUNT'
    }
  }), {
    databaseType: 'POSTGRESQL',
    query: 'select 2',
    resultMode: 'ROW_COUNT'
  })
})
