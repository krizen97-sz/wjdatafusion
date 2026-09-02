import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

const sqlRoot = '../../../../../../WDF100.0/sql/'
const cumulative = readFileSync(new URL(`${sqlRoot}support_upgrade_20260902_auto_inspection_v4_1_1_to_v4_2_1_all.sql`, import.meta.url), 'utf8')
const migration = readFileSync(new URL(`${sqlRoot}support_migrate_20260902_auto_inspection_scope_data_v4_2_1.sql`, import.meta.url), 'utf8')
const releaseNotes = readFileSync(new URL('../../version/releaseNotes.js', import.meta.url), 'utf8')
const readme = readFileSync(new URL('../../../../../../README.md', import.meta.url), 'utf8')

function executableSql(source) {
  return source
    .split('\n')
    .filter((line) => !line.trim().startsWith('--'))
    .join('\n')
}

test('v4.1.1 to v4.2.1 cumulative SQL contains every structural change repeatably', () => {
  for (const column of ['comparison_scope', 'window_key', 'window_start', 'window_end']) {
    assert.ok(cumulative.includes(`COLUMN_NAME = '${column}'`), `cumulative SQL misses ${column}`)
  }
  for (const column of ['scope_type', 'site_id', 'site_name', 'main_platform_id', 'main_platform_name']) {
    assert.ok(cumulative.includes(`COLUMN_NAME = '${column}'`), `cumulative SQL misses ${column}`)
  }
  for (const index of [
    'idx_sup_auto_plan_site_status',
    'idx_sup_auto_plan_main_status',
    'idx_sup_auto_record_site_time',
    'idx_sup_auto_record_main_time',
    'idx_sup_auto_health_site_date',
    'idx_sup_auto_health_main_date'
  ]) {
    assert.ok(cumulative.includes(`INDEX_NAME = '${index}'`), `cumulative SQL misses ${index}`)
  }
  assert.ok(cumulative.includes('历史执行模式兼容字段（ROUTINE/FREQUENT）'))
  assert.ok(cumulative.includes('自动化巡检计划每日健康汇总'))
  assert.ok(!/\bSOURCE\b/i.test(executableSql(cumulative)), 'cumulative SQL must not depend on client-side SOURCE commands')
  assert.ok(!/\b(drop|truncate|delete)\b/i.test(executableSql(cumulative)), 'cumulative SQL must not delete schema or data')
})

test('v4.2.1 migration infers only evidence-backed scopes and preserves ambiguous plans', () => {
  for (const marker of [
    'SET @auto_apply_scope_migration = IFNULL(@auto_apply_scope_migration, 1)',
    'tmp_auto_scope_override',
    'RECORD_SNAPSHOT',
    'HEALTH_SNAPSHOT',
    'TEMPLATE_SERVER',
    'RESULT_SERVER',
    'READY_MAIN_PLATFORM',
    'READY_SITE',
    'CONFLICT_MULTI_SITE',
    'NO_EVIDENCE',
    'INVALID_OVERRIDE',
    'START TRANSACTION',
    'INSERT IGNORE INTO sup_auto_inspection_health_daily',
    'COMMIT'
  ]) {
    assert.ok(migration.includes(marker), `migration SQL misses ${marker}`)
  }
  assert.ok(migration.includes("preview.migration_status IN ('READY_OVERRIDE', 'READY_MAIN_PLATFORM', 'READY_SITE')"))
  assert.ok(migration.includes("plan_row.scope_type IN ('SITE', 'MAIN_PLATFORM')"))
  assert.ok(migration.includes('只能覆盖到主平台'))
  assert.ok(migration.includes('禁止自动迁移'))
  assert.ok(!/plan_name\s+like|template_name\s+like|label_name\s+like/i.test(executableSql(migration)), 'migration must not infer scope from names or labels')
  assert.ok(!/password|secret_cipher|password_cipher/i.test(executableSql(migration)), 'migration must not read credential fields')
  assert.ok(!/\b(delete|truncate)\b/i.test(executableSql(migration)), 'migration must not delete business data')
  assert.ok(!/drop\s+table/i.test(executableSql(migration)), 'migration may drop temporary tables only')
})

test('v4.2.1 release documentation lists the cumulative and migration scripts in order', () => {
  assert.ok(releaseNotes.includes("version: 'v4.2.1'"))
  assert.ok(releaseNotes.includes('support_upgrade_20260902_auto_inspection_v4_1_1_to_v4_2_1_all.sql'))
  assert.ok(releaseNotes.includes('support_migrate_20260902_auto_inspection_scope_data_v4_2_1.sql'))
  assert.ok(readme.includes('先备份 `sup_auto_inspection_*`'))
  assert.ok(readme.includes('@auto_apply_scope_migration = 0'))
  assert.ok(readme.includes('@auto_apply_scope_migration = 1'))
})
