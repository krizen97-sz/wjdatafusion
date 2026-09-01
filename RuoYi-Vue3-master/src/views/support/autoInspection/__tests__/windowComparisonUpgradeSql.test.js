import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

const sqlRoot = '../../../../../../WDF100.0/sql/'
const upgrade = readFileSync(new URL(`${sqlRoot}support_upgrade_20260901_auto_inspection_window_unified_plan_v4_1_0.sql`, import.meta.url), 'utf8')
const full = readFileSync(new URL(`${sqlRoot}support_v1.sql`, import.meta.url), 'utf8')
const deploy = readFileSync(new URL(`${sqlRoot}support_deploy_all.sql`, import.meta.url), 'utf8')

test('v4.1.0 upgrade stores comparison window evidence repeatably', () => {
  for (const column of ['comparison_scope', 'window_key', 'window_start', 'window_end']) {
    assert.ok(upgrade.includes(`COLUMN_NAME = '${column}'`), `upgrade does not guard ${column}`)
    assert.ok(full.includes(column), `full SQL misses ${column}`)
    assert.ok(deploy.includes(column), `deployment SQL misses ${column}`)
  }
  assert.ok(upgrade.includes('ROUTINE逐次记录 FREQUENT每日汇总'))
  assert.equal((full.match(/v4\.1\.0 自动化巡检统计窗口与统一计划语义/g) || []).length, 1)
  assert.equal((deploy.match(/v4\.1\.0 自动化巡检统计窗口与统一计划语义/g) || []).length, 1)
})

test('v4.1.0 upgrade preserves inspection records and plan mode values', () => {
  const executable = upgrade
    .split('\n')
    .filter((line) => !line.trim().startsWith('--'))
    .join('\n')
  assert.ok(!/\b(drop|truncate|delete)\b/i.test(executable))
  assert.ok(!/update\s+sup_auto_inspection_(record|template|template_step|target)\b/i.test(executable))
  assert.ok(!/set\s+plan_mode\s*=/i.test(executable))
})
