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

test('interactive audit catches unnamed controls, missing button type, image alt and unsafe dialog submit', () => {
  const source = `<template>
  <div>
    <el-button link icon="Edit"></el-button>
    <button><el-icon><Close /></el-icon></button>
    <img src="/preview.png">
    <el-dialog v-model="open">
      <template #footer>
        <el-button type="primary" @click="submitForm">确 定</el-button>
        <el-button @click="cancel">取 消</el-button>
      </template>
    </el-dialog>
  </div>
</template>`
  const findings = analyzeVueFile({
    file: 'src/views/device/index.vue',
    currentContent: source,
    addedLineNumbers: allLines(source)
  })
  const rules = new Set(findings.map((finding) => finding.rule))
  assert.ok(rules.has('icon-button-name'))
  assert.ok(rules.has('native-button-type'))
  assert.ok(rules.has('native-button-name'))
  assert.ok(rules.has('image-alt'))
  assert.ok(rules.has('dialog-footer-order'))
  assert.ok(rules.has('dialog-submit-loading'))
})

test('interactive audit accepts visible labels and explicit accessible names', () => {
  const source = `<template>
  <div>
    <el-button link icon="Edit">修改</el-button>
    <button type="button" aria-label="关闭"><el-icon><Close /></el-icon></button>
    <img src="/preview.png" alt="预览图">
    <el-dialog v-model="open">
      <template #footer>
        <el-button @click="cancel">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitForm">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>`
  const findings = analyzeVueFile({
    file: 'src/views/device/index.vue',
    currentContent: source,
    addedLineNumbers: allLines(source)
  })
  const auditedRules = new Set(['icon-button-name', 'native-button-type', 'native-button-name', 'image-alt', 'dialog-footer-order', 'dialog-submit-loading'])
  assert.equal(findings.some((finding) => auditedRules.has(finding.rule)), false)
})

test('a visual tooltip does not replace an icon button accessible name', () => {
  const source = `<template>
  <el-tooltip content="删除">
    <el-button circle icon="Delete" :disabled="index >= rows.length - 1" />
  </el-tooltip>
</template>`
  const findings = analyzeVueFile({
    file: 'src/views/device/index.vue',
    currentContent: source,
    addedLineNumbers: allLines(source)
  })
  assert.ok(findings.some((finding) => finding.rule === 'icon-button-name'))
})

test('Element Plus Radio values use value instead of deprecated label binding', () => {
  const legacy = `<template><el-radio label="Y">启用</el-radio></template>`
  const current = `<template><el-radio value="Y">启用</el-radio></template>`
  const legacyFindings = analyzeVueFile({
    file: 'src/views/device/index.vue',
    currentContent: legacy,
    addedLineNumbers: allLines(legacy)
  })
  const currentFindings = analyzeVueFile({
    file: 'src/views/device/index.vue',
    currentContent: current,
    addedLineNumbers: allLines(current)
  })
  assert.ok(legacyFindings.some((finding) => finding.rule === 'deprecated-radio-value'))
  assert.equal(currentFindings.some((finding) => finding.rule === 'deprecated-radio-value'), false)
})

test('a Dialog custom header keeps an explicit accessible name', () => {
  const unnamed = `<template><el-dialog v-model="open"><template #header><h3>编辑设备</h3></template></el-dialog></template>`
  const named = `<template><el-dialog v-model="open"><template #header="{ titleId, titleClass }"><h3 :id="titleId" :class="titleClass">编辑设备</h3></template></el-dialog></template>`
  const unnamedDrawer = `<template><el-drawer v-model="open"><template #header><h3>修改记录</h3></template></el-drawer></template>`
  const untitled = `<template><el-dialog v-model="open"><p>设备表单</p></el-dialog></template>`
  const unnamedFindings = analyzeVueFile({
    file: 'src/views/device/index.vue',
    currentContent: unnamed,
    addedLineNumbers: allLines(unnamed)
  })
  const namedFindings = analyzeVueFile({
    file: 'src/views/device/index.vue',
    currentContent: named,
    addedLineNumbers: allLines(named)
  })
  const drawerFindings = analyzeVueFile({
    file: 'src/views/device/HistoryDrawer.vue',
    currentContent: unnamedDrawer,
    addedLineNumbers: allLines(unnamedDrawer)
  })
  const untitledFindings = analyzeVueFile({
    file: 'src/views/device/index.vue',
    currentContent: untitled,
    addedLineNumbers: allLines(untitled)
  })
  assert.ok(unnamedFindings.some((finding) => finding.rule === 'dialog-accessible-name'))
  assert.ok(drawerFindings.some((finding) => finding.rule === 'dialog-accessible-name'))
  assert.ok(untitledFindings.some((finding) => finding.rule === 'dialog-accessible-name'))
  assert.equal(namedFindings.some((finding) => finding.rule === 'dialog-accessible-name'), false)
})

test('interactive audit also covers shared components outside business views', () => {
  const source = `<template>
  <div @click="toggle">
    <button><svg-icon icon-class="menu" /></button>
    <img src="/avatar.png">
  </div>
</template>`
  const findings = analyzeVueFile({
    file: 'src/components/GlobalToolbar/index.vue',
    currentContent: source,
    addedLineNumbers: allLines(source)
  })
  const rules = new Set(findings.map((finding) => finding.rule))
  assert.ok(rules.has('nonsemantic-click-target'))
  assert.ok(rules.has('native-button-type'))
  assert.ok(rules.has('native-button-name'))
  assert.ok(rules.has('image-alt'))
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
    thresholds: { hardcodedColorCount: 4, hardcodedColorUnique: 3, hardcodedMetricCount: 8, spacingUniqueCount: 4 }
  })
  const rules = new Set(findings.map((finding) => finding.rule))
  assert.ok(rules.has('hardcoded-colors'))
  assert.ok(rules.has('hardcoded-shape-spacing'))
  assert.ok(rules.has('custom-core-control-style'))
})

test('style markup inside a script string does not count as component CSS', () => {
  const source = `<template><div /></template>
<script setup>
const report = '<style>body{color:#111;background:#fff;border-color:#ddd;outline:#abc}</style>'
</script>
<style scoped>
.panel { color: var(--app-text); }
</style>`
  const findings = analyzeVueFile({
    file: 'src/views/report/index.vue',
    currentContent: source,
    addedLineNumbers: allLines(source),
    thresholds: { hardcodedColorCount: 4, hardcodedColorUnique: 3 }
  })
  assert.equal(findings.some((finding) => finding.rule === 'hardcoded-colors'), false)
})

test('CSS icon detection distinguishes icon synthesis from textual field prefixes', () => {
  const source = `<template><div /></template>
<style scoped>
.custom-icon::before { content: '×'; }
.field-label::before { content: '空间'; }
</style>`
  const findings = analyzeVueFile({
    file: 'src/views/device/index.vue',
    currentContent: source,
    addedLineNumbers: allLines(source)
  })
  assert.equal(findings.filter((finding) => finding.rule === 'css-drawn-icon').length, 1)
})

test('core control audit permits layout-only adjustments but rejects oversized visual restyling', () => {
  const source = `<template><div /></template>
<style scoped>
.toolbar .el-button { justify-self: end; min-width: 32px; }
.custom-dialog .el-dialog { border-radius: 30px; background: #fff; }
</style>`
  const findings = analyzeVueFile({
    file: 'src/views/device/index.vue',
    currentContent: source,
    addedLineNumbers: allLines(source)
  })
  assert.equal(findings.filter((finding) => finding.rule === 'custom-core-control-style').length, 1)
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
