import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

const sqlRoot = '../../../../../../WDF100.0/sql/'
const upgrade = readFileSync(new URL(`${sqlRoot}support_upgrade_20260829_auto_inspection_value_comparison_v3_16_0.sql`, import.meta.url), 'utf8')
const full = readFileSync(new URL(`${sqlRoot}support_v1.sql`, import.meta.url), 'utf8')
const deploy = readFileSync(new URL(`${sqlRoot}support_deploy_all.sql`, import.meta.url), 'utf8')

test('v3.16.0 upgrade stores explainable comparison evidence repeatably', () => {
  for (const column of ['evaluation_mode', 'previous_value', 'change_value', 'evaluation_rule', 'baseline_flag']) {
    assert.ok(upgrade.includes(`COLUMN_NAME = '${column}'`), `upgrade does not guard ${column}`)
    assert.ok(full.includes(column), `full SQL misses ${column}`)
    assert.ok(deploy.includes(column), `deployment SQL misses ${column}`)
  }
  assert.ok(upgrade.includes("tool_name = 'Kafka消费组指标检测'"))
  assert.ok(upgrade.includes("tool_code IN ('KAFKA_TOPIC_ACTIVITY', 'KAFKA_CONSUMER_PROGRESS')"))
})

test('v3.16.0 upgrade preserves historical inspection data', () => {
  const executable = upgrade
    .split('\n')
    .filter((line) => !line.trim().startsWith('--'))
    .join('\n')
  assert.ok(!/\b(drop|truncate|delete)\b/i.test(executable))
  assert.ok(!/update\s+sup_auto_inspection_(record|template|template_step|target)\b/i.test(executable))
})
