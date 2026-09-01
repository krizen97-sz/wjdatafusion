import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

const sqlRoot = '../../../../../../WDF100.0/sql/'
const upgrade = readFileSync(new URL(`${sqlRoot}support_upgrade_20260901_auto_inspection_scope_health_v4_2_0.sql`, import.meta.url), 'utf8')
const full = readFileSync(new URL(`${sqlRoot}support_v1.sql`, import.meta.url), 'utf8')
const deploy = readFileSync(new URL(`${sqlRoot}support_deploy_all.sql`, import.meta.url), 'utf8')

test('v4.2.0 stores site and main-platform scope snapshots repeatably', () => {
  for (const column of ['scope_type', 'site_id', 'site_name', 'main_platform_id', 'main_platform_name']) {
    assert.ok(upgrade.includes(`COLUMN_NAME = '${column}'`), `upgrade does not guard ${column}`)
    assert.ok(full.includes(column), `full SQL misses ${column}`)
    assert.ok(deploy.includes(column), `deployment SQL misses ${column}`)
  }
  for (const index of [
    'idx_sup_auto_plan_site_status',
    'idx_sup_auto_plan_main_status',
    'idx_sup_auto_record_site_time',
    'idx_sup_auto_record_main_time',
    'idx_sup_auto_health_site_date',
    'idx_sup_auto_health_main_date'
  ]) {
    assert.ok(upgrade.includes(`INDEX_NAME = '${index}'`), `upgrade does not guard ${index}`)
  }
  assert.ok(upgrade.includes('INSERT IGNORE INTO sup_auto_inspection_health_daily'))
  assert.ok(upgrade.includes("r.source_type = 'AUTO'"))
  assert.ok(upgrade.includes('p.scope_type IS NOT NULL'))
  assert.equal((full.match(/v4\.2\.0 自动化巡检现场与主平台健康归属/g) || []).length, 1)
  assert.equal((deploy.match(/v4\.2\.0 自动化巡检现场与主平台健康归属/g) || []).length, 1)
})

test('v4.2.0 preserves source records and inspection details', () => {
  const executable = upgrade
    .split('\n')
    .filter((line) => !line.trim().startsWith('--'))
    .join('\n')
  assert.ok(!/\b(drop|truncate|delete)\b/i.test(executable))
  assert.ok(!/update\s+sup_auto_inspection_(step_result|target_result|template|template_step|target)\b/i.test(executable))
  assert.ok(!/set\s+(result_status|actual_value|result_detail)\s*=/i.test(executable))
})
