import test from 'node:test'
import assert from 'node:assert/strict'
import { lookupPropertyText, lookupPropertyPatch, parseLookupSnapshot, parseLookupRules, patchLookupRules, lookupRuleFieldError } from '../lookupRules.js'

const match = '[{"input":"/plate","lookup":"plate","type":"STRING","operator":"EQ"}]'
const returns = '[{"lookup":"code","output":"mapped_code","default":"0"}]'

test('dictionary inspection reports rows without round-tripping long decimal literals', () => {
  const raw = '[{"id":9007199254740993.10000000000000000001},{"id":9007199254740993.10000000000000000002}]'
  assert.deepEqual(parseLookupSnapshot(raw), { valid: true, count: 2, error: '' })
  assert.equal(lookupPropertyPatch('Lookup Rows', raw).value, raw)
  assert.equal(lookupPropertyText(undefined), '[]')
  assert.equal(parseLookupSnapshot('null').valid, false)
  assert.equal(parseLookupSnapshot('[1]').valid, false)
  assert.equal(parseLookupSnapshot(`[${Array(1001).fill('{}').join(',')}]`).valid, false)
})

test('invalid JSON is never silently replaced by an empty rule list', () => {
  for (const raw of ['[{"input":', '{"input":"/plate"}', '[null]', '']) {
    assert.equal(parseLookupRules(raw, 'Match Fields').valid, false)
    assert.throws(() => patchLookupRules(raw, 'Match Fields', { type: 'add' }))
    assert.throws(() => patchLookupRules(raw, 'Match Fields', { type: 'remove', index: 0 }))
    assert.equal(lookupPropertyPatch('Match Fields', raw).value, raw)
  }
})

test('non-string defaults and unknown configuration require lossless advanced editing', () => {
  for (const value of ['90071992547409931234', '0.10000000000000000001', 'true', '{"value":90071992547409931234}', '[1,2]']) {
    const raw = `[{"lookup":"code","output":"code","default":${value}}]`
    const state = parseLookupRules(raw, 'Return Fields')
    assert.equal(state.valid, true)
    assert.equal(state.advancedOnly, true)
    assert.throws(() => patchLookupRules(raw, 'Return Fields', { type: 'field', index: 0, key: 'output', value: 'new_code' }))
    assert.equal(lookupPropertyPatch('Return Fields', raw).value, raw)
  }
  const unknown = '[{"lookup":"code","output":"code","futureOption":"keep-me"}]'
  assert.equal(parseLookupRules(unknown, 'Return Fields').advancedOnly, true)
  assert.throws(() => patchLookupRules(unknown, 'Return Fields', { type: 'add' }))
})

test('default string zero, empty string and null remain distinct through edits', () => {
  const renamed = patchLookupRules(returns, 'Return Fields', { type: 'field', index: 0, key: 'output', value: 'new_code' })
  assert.equal(JSON.parse(renamed.value)[0].default, '0')
  const empty = patchLookupRules(renamed.value, 'Return Fields', { type: 'default', index: 0, kind: 'STRING', value: '' })
  assert.equal(JSON.parse(empty.value)[0].default, '')
  const cleared = patchLookupRules(empty.value, 'Return Fields', { type: 'default', index: 0, kind: 'NULL' })
  assert.equal(JSON.parse(cleared.value)[0].default, null)
  assert.equal(parseLookupRules('[{"lookup":"code","output":"code"}]', 'Return Fields').advancedOnly, false)
})

test('non-null predicates remove only their input path and all other rules remain intact', () => {
  const raw = `[${match.slice(1, -1)},{"input":"/id","lookup":"id","type":"NUMBER","operator":"EQ"}]`
  const changed = patchLookupRules(raw, 'Match Fields', { type: 'field', index: 0, key: 'operator', value: 'IS_NOT_NULL' })
  const rows = JSON.parse(changed.value)
  assert.equal(Object.prototype.hasOwnProperty.call(rows[0], 'input'), false)
  assert.equal(rows[0].lookup, 'plate')
  assert.deepEqual(rows[1], JSON.parse(raw)[1])
  const restored = patchLookupRules(changed.value, 'Match Fields', { type: 'field', index: 0, key: 'operator', value: 'EQ' })
  assert.equal(JSON.parse(restored.value)[0].input, '/field')
})

test('add and remove stay within the field budget and reject stale indexes', () => {
  const added = patchLookupRules(match, 'Match Fields', { type: 'add' })
  assert.equal(JSON.parse(added.value).length, 2)
  assert.equal(patchLookupRules(added.value, 'Match Fields', { type: 'remove', index: 1 }).value, match)
  assert.throws(() => patchLookupRules(match, 'Match Fields', { type: 'remove', index: -1 }))
  const full = `[${Array(64).fill(match.slice(1, -1)).join(',')}]`
  assert.throws(() => patchLookupRules(full, 'Match Fields', { type: 'add' }))
})

test('duplicate keys and overly deep JSON cannot enter structural editing', () => {
  const duplicate = '[{"lookup":"code","output":null,"output":"code"}]'
  assert.equal(parseLookupRules(duplicate, 'Return Fields').valid, false)
  assert.throws(() => patchLookupRules(duplicate, 'Return Fields', { type: 'remove', index: 0 }))
  assert.equal(parseLookupSnapshot('[{"id":1,"\\u0069d":2}]').valid, false)
  const nested = '[{"value":' + '['.repeat(34) + '0' + ']'.repeat(34) + '}]'
  assert.equal(parseLookupSnapshot(nested).valid, false)
})

test('validation distinguishes JSON Pointer reads from plain output field names', () => {
  assert.equal(lookupRuleFieldError({ input: '/a~1b/~0key/0', operator: 'EQ' }, 'input', 'Match Fields'), '')
  assert.notEqual(lookupRuleFieldError({ input: '/a~2', operator: 'EQ' }, 'input', 'Match Fields'), '')
  assert.equal(lookupRuleFieldError({ operator: 'IS_NOT_NULL' }, 'input', 'Match Fields'), '')
  assert.equal(lookupRuleFieldError({ output: '编码_value' }, 'output', 'Return Fields'), '')
  for (const output of ['/code', 'rows[0]', '']) assert.notEqual(lookupRuleFieldError({ output }, 'output', 'Return Fields'), '')
  assert.throws(() => lookupPropertyPatch('Sensitive Metadata', 'x'))
  assert.throws(() => lookupPropertyPatch('Lookup Rows', []))
  assert.throws(() => lookupPropertyPatch('Missing Match', 'SILENT'))
})
