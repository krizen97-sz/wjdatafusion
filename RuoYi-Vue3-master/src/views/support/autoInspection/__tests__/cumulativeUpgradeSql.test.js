import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'

const source = readFileSync('../WDF100.0/sql/support_upgrade_20260826_v3_13_3_to_v3_14_3_all.sql', 'utf8')

test('v3.13.3 to v3.14.3 cumulative SQL contains every database change', () => {
  for (const marker of [
    'v3.14.0',
    'v3.14.1 无数据库修改',
    'v3.14.2 无数据库修改',
    'v3.14.3',
    "2307, '巡检驾驶舱'",
    "r.role_key = 'datafusion'",
    "WHEN 2307 THEN 'gauge'",
    'START TRANSACTION;',
    'COMMIT;'
  ]) {
    assert.ok(source.includes(marker), `missing cumulative SQL marker: ${marker}`)
  }
})

test('cumulative SQL does not alter schemas or delete business data', () => {
  const executableSql = source.replace(/^\s*--.*$/gm, '')
  assert.ok(!/\bALTER\s+TABLE\b/i.test(executableSql))
  assert.ok(!/\bDELETE\s+FROM\b/i.test(executableSql))
  assert.ok(!/\b(?:DROP|TRUNCATE)\b/i.test(executableSql))
  assert.ok(!/\b(?:UPDATE|INSERT\s+INTO)\s+sup_/i.test(executableSql))
})
