import test from 'node:test'
import assert from 'node:assert/strict'
import {
  COMPARISON_SCOPE_CONTINUOUS,
  COMPARISON_SCOPE_DAY,
  COMPARISON_SCOPE_HOUR,
  comparisonScopeOptions,
  normalizeComparisonScope
} from '../comparisonScopeConfig.js'

test('comparison scope options are available before page form initialization', () => {
  assert.deepEqual(comparisonScopeOptions.map((item) => item.value), [
    COMPARISON_SCOPE_CONTINUOUS,
    COMPARISON_SCOPE_DAY,
    COMPARISON_SCOPE_HOUR
  ])
})

test('dated count targets infer a daily scope without touching continuous counters', () => {
  assert.equal(normalizeComparisonScope(undefined, 'HTTP_COUNT', {
    target: { extraParams: { startTime: '${todayStart}', endTime: '${endTime}' } }
  }), COMPARISON_SCOPE_DAY)
  assert.equal(normalizeComparisonScope(undefined, 'KAFKA_LAG', {
    stepParams: { query: '${today}' }
  }), COMPARISON_SCOPE_CONTINUOUS)
})

test('an explicitly saved scope always wins over inference', () => {
  assert.equal(normalizeComparisonScope(COMPARISON_SCOPE_HOUR, 'HTTP_COUNT', {
    target: { url: '/api?date=${today}' }
  }), COMPARISON_SCOPE_HOUR)
})
