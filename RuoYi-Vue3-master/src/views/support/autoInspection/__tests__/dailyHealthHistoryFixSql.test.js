import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

const sqlRoot = '../../../../../../WDF100.0/sql/'
const upgrade = readFileSync(new URL(`${sqlRoot}support_upgrade_20260829_auto_inspection_daily_history_fix_v3_16_2.sql`, import.meta.url), 'utf8')
const full = readFileSync(new URL(`${sqlRoot}support_v1.sql`, import.meta.url), 'utf8')
const deploy = readFileSync(new URL(`${sqlRoot}support_deploy_all.sql`, import.meta.url), 'utf8')

test('v3.16.2 only repairs evidenced high-frequency baseline history', () => {
  for (const marker of [
    "r.run_mode = 'FREQUENT'",
    "r.source_type = 'AUTO'",
    "tr.result_status = '3'",
    "tr.result_detail LIKE '%已建立首次采样基线%'",
    "tr.result_detail LIKE '%已重新建立基线%'",
    "tr.baseline_flag = 'Y'",
    "tr.evaluation_mode = 'PREVIOUS'",
    't.plan_id = ps.plan_id',
    't.target_id = ps.target_id',
    "r.abnormal_summary = '无异常'",
    'sup_auto_inspection_health_daily'
  ]) {
    assert.ok(upgrade.includes(marker), `missing guarded history repair marker: ${marker}`)
  }
  assert.ok(!/DELETE\s+FROM/i.test(upgrade))
  assert.ok(!/TRUNCATE\s+TABLE/i.test(upgrade))
  assert.ok(!/DROP\s+TABLE(?!\s+IF\s+EXISTS\s+tmp_)/i.test(upgrade))
})

test('v3.16.2 history repair is synchronized into full deployment SQL', () => {
  const marker = '-- v3.16.2 自动化巡检高频历史基线修复'
  assert.ok(full.includes(marker))
  assert.ok(deploy.includes(marker))
  assert.ok(upgrade.includes("COLUMN_NAME = 'baseline_flag'"))
})
