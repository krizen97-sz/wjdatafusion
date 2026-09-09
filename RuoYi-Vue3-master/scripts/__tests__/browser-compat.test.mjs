import assert from 'node:assert/strict'
import fs from 'node:fs'
import test from 'node:test'

const packageJson = JSON.parse(fs.readFileSync(new URL('../../package.json', import.meta.url), 'utf8'))
const viteConfig = fs.readFileSync(new URL('../../vite.config.js', import.meta.url), 'utf8')
const mainSource = fs.readFileSync(new URL('../../src/main.js', import.meta.url), 'utf8')
const polyfillSource = fs.readFileSync(new URL('../../src/polyfills/browserCompatibility.js', import.meta.url), 'utf8')
const metricSource = fs.readFileSync(new URL('../../src/views/support/autoInspection/planMetricsPresentation.js', import.meta.url), 'utf8')

test('production build explicitly targets the Vite 6 native ESM compatibility floor', () => {
  assert.match(viteConfig, /target:\s*['"]chrome64['"]/)
  assert.match(packageJson.scripts['verify:frontend'], /build:prod && npm run check:browser-compat/)
  assert.equal(packageJson.dependencies['core-js'], '3.50.0')
  assert.equal(packageJson.devDependencies['@babel/parser'], '7.29.0')
})

test('compatibility bootstrap runs before Vue and covers runtime methods used by the platform', () => {
  assert.ok(mainSource.indexOf("import './polyfills/browserCompatibility'") < mainSource.indexOf("from 'vue'"))
  for (const feature of ['global-this', 'array/at', 'array/flat', 'array/flat-map', 'object/from-entries', 'string/replace-all']) {
    assert.match(polyfillSource, new RegExp(`core-js/actual/${feature}`))
  }
  assert.match(polyfillSource, /typeof globalThis\.WeakRef === 'undefined'/)
  assert.match(polyfillSource, /deref\(\)/)
})

test('compatibility bootstrap restores APIs missing from older Chrome versions', async () => {
  const targets = [
    [Array.prototype, 'at'],
    [Array.prototype, 'flat'],
    [Array.prototype, 'flatMap'],
    [Object, 'fromEntries'],
    [String.prototype, 'replaceAll'],
    [globalThis, 'WeakRef']
  ]
  const descriptors = targets.map(([target, key]) => [target, key, Object.getOwnPropertyDescriptor(target, key)])

  try {
    for (const [target, key] of targets) delete target[key]
    await import(`../../src/polyfills/browserCompatibility.js?test=${Date.now()}`)
    assert.equal([1, 2, 3].at(-1), 3)
    assert.deepEqual([1, [2, 3]].flat(), [1, 2, 3])
    assert.deepEqual([1, 2].flatMap((value) => [value, value]), [1, 1, 2, 2])
    assert.deepEqual(Object.fromEntries([['ready', true]]), { ready: true })
    assert.equal('a.b'.replaceAll('.', '-'), 'a-b')
    const target = {}
    assert.equal(new globalThis.WeakRef(target).deref(), target)
  } finally {
    for (const [target, key, descriptor] of descriptors) {
      if (descriptor) Object.defineProperty(target, key, descriptor)
      else delete target[key]
    }
  }
})

test('exact metric formatting no longer requires BigInt support', () => {
  assert.doesNotMatch(metricSource, /\bBigInt\s*\(|\b\d+n\b/)
  assert.match(metricSource, /groupedInteger/)
})
