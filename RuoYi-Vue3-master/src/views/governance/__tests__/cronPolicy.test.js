import test from 'node:test'
import assert from 'node:assert/strict'
import { shouldApplyCronUpdate } from '../../../components/Crontab/updatePolicy.js'

test('independent ETL cron fields do not reset previously selected minute or second values', () => {
  assert.equal(shouldApplyCronUpdate('min', 'hour', true), false)
  assert.equal(shouldApplyCronUpdate('second', 'min', true), false)
  assert.equal(shouldApplyCronUpdate('month', 'year', true), false)
  assert.equal(shouldApplyCronUpdate('hour', 'hour', true), true)
})
test('day/week exclusivity and legacy caller defaults remain intact', () => {
  assert.equal(shouldApplyCronUpdate('day', 'week', true), true)
  assert.equal(shouldApplyCronUpdate('week', 'day', true), true)
  assert.equal(shouldApplyCronUpdate('min', 'hour'), true)
  assert.equal(shouldApplyCronUpdate('second', 'day', false), true)
})
