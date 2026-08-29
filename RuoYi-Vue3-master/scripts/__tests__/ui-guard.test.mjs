import assert from 'node:assert/strict'
import test from 'node:test'

import {
  analyzePackage,
  analyzeVueFile,
  applyAllowlist,
  classifyDependency,
  diffAddedLineNumbers,
  globToRegExp
} from '../ui-guard.mjs'

function allLines(source) {
  return new Set(source.split(/\r?\n/).map((_, index) => index + 1))
}

const baseAllowlist = {
  version: 1,
  approvedDependencies: {
    frameworks: ['element-plus'],
    icons: ['@element-plus/icons-vue'],
    runtimeUi: ['echarts']
  },
  entries: []
}

test('diffAddedLineNumbers returns only inserted and changed current lines', () => {
  const before = 'one\ntwo\nthree'
  const after = 'one\nchanged\nthree\nfour'
  assert.deepEqual([...diffAddedLineNumbers(before, after)], [2, 4])
  assert.deepEqual([...diffAddedLineNumbers('same\n', 'same\n')], [])
})

test('business views reject inline SVG, Canvas, data SVG, and Emoji operations', () => {
  const source = `<template>
  <div>
    <el-button @click="remove">🗑️ 删除</el-button>
    <svg viewBox="0 0 24 24"><path d="M0 0" /></svg>
    <canvas class="fake-table"></canvas>
    <img src="data:image/svg+xml;base64,abc" />
  </div>
</template>`
  const findings = analyzeVueFile({
    file: 'src/views/device/index.vue',
    currentContent: source,
    addedLineNumbers: allLines(source)
  })
  const rules = new Set(findings.map((finding) => finding.rule))
  assert.ok(rules.has('emoji-operation-icon'))
  assert.ok(rules.has('business-inline-svg'))
  assert.ok(rules.has('canvas-ui-simulation'))
  assert.ok(rules.has('data-svg-uri'))
})

test('the approved SvgIcon component tag is not mistaken for inline SVG', () => {
  const source = `<template>
  <svg-icon icon-class="edit" />
</template>`
  const findings = analyzeVueFile({
    file: 'src/views/device/index.vue',
    currentContent: source,
    addedLineNumbers: allLines(source)
  })
  assert.equal(findings.some((finding) => finding.rule === 'business-inline-svg'), false)
})

test('explicitly marked visualization becomes a visible allowlist exception', () => {
  const source = `<template>
  <svg data-ui-guard="chart" viewBox="0 0 100 100"></svg>
</template>`
  const findings = analyzeVueFile({
    file: 'src/views/support/autoInspection/cockpit.vue',
    currentContent: source,
    addedLineNumbers: allLines(source)
  })
  const reviewed = applyAllowlist(findings, {
    ...baseAllowlist,
    entries: [{
      id: 'UIX-test',
      rule: 'business-inline-svg',
      paths: ['src/views/support/**/cockpit.vue'],
      linePattern: 'data-ui-guard=["\\\']chart["\\\']',
      reason: 'Test chart exception',
      expires: null
    }]
  })
  assert.equal(reviewed[0].severity, 'exception')
  assert.equal(reviewed[0].allowlistId, 'UIX-test')
})

test('large page-local token sets and core control overrides warn', () => {
  const source = `<template><div /></template>
<style scoped>
.panel { color: #123456; background: #abcdef; border-color: #111111; box-shadow: 0 2px 8px #222222; }
.panel { margin: 8px; padding: 12px; gap: 4px; border-radius: 10px; font-size: 14px; }
.other { margin-top: 6px; padding-bottom: 9px; gap: 3px; }
.el-button { border-radius: 999px; }
</style>`
  const findings = analyzeVueFile({
    file: 'src/views/device/index.vue',
    currentContent: source,
    addedLineNumbers: allLines(source),
    thresholds: { hardcodedColorCount: 4, hardcodedColorUnique: 3, hardcodedMetricCount: 8 }
  })
  const rules = new Set(findings.map((finding) => finding.rule))
  assert.ok(rules.has('hardcoded-colors'))
  assert.ok(rules.has('hardcoded-shape-spacing'))
  assert.ok(rules.has('custom-core-control-style'))
})

test('package analysis blocks second UI and icon systems and warns on other runtime additions', () => {
  const current = JSON.stringify({
    dependencies: {
      'element-plus': '2.13.1',
      'ant-design-vue': '4.0.0',
      'lucide-vue-next': '1.0.0',
      lodash: '4.17.21'
    }
  })
  const base = JSON.stringify({ dependencies: { 'element-plus': '2.13.1' } })
  const findings = analyzePackage({ file: 'package.json', currentContent: current, baseContent: base, allowlist: baseAllowlist })
  const rules = findings.map((finding) => finding.rule)
  assert.ok(rules.includes('unapproved-ui-framework'))
  assert.ok(rules.includes('unapproved-icon-library'))
  assert.ok(rules.includes('new-runtime-dependency'))
  assert.equal(classifyDependency('echarts', baseAllowlist), 'approved-runtime-ui')
  assert.equal(classifyDependency('@headlessui/vue', baseAllowlist), 'ui-framework')
})

test('glob matching respects single and recursive path segments', () => {
  assert.ok(globToRegExp('src/views/**/cockpit.vue').test('src/views/support/autoInspection/cockpit.vue'))
  assert.ok(globToRegExp('src/views/*/index.vue').test('src/views/ipam/index.vue'))
  assert.equal(globToRegExp('src/views/*/index.vue').test('src/views/ipam/deep/index.vue'), false)
})
