import assert from 'node:assert/strict'
import test from 'node:test'
import { readFileSync } from 'node:fs'
import { compileString } from 'sass-embedded'

const source = readFileSync(new URL('../../../../assets/styles/motion.scss', import.meta.url), 'utf8')
const css = compileString(source).css

test('button icon feedback excludes disabled, loading and dense link/text actions', () => {
  assert.match(css, /\.el-button:not\(\.motion-entry-action\):not\(\.is-disabled\):not\(\.is-loading\):not\(\.is-link\):not\(\.is-text\):not\(:active\):hover > \.el-icon/)
  assert.match(css, /data-motion-direction=up[\s\S]*?--motion-button-icon-y: -2px/)
  assert.match(css, /data-motion-direction=down[\s\S]*?--motion-button-icon-y: 2px/)
})

test('real loading uses an internal rail and leaves native loading shield intact', () => {
  assert.match(css, /\.el-button.is-loading:not\(\.is-link\):not\(\.is-text\):not\(\.is-circle\)::after\s*\{[^}]*pointer-events: none/)
  assert.doesNotMatch(css, /is-loading::before/)
  assert.doesNotMatch(css, /\.el-button\s*\{[^}]*overflow:\s*hidden/)
  assert.match(css, /\.el-button.is-motion-success \.motion-action-state__icon/)
})

test('reduced motion removes displacement and loading loops, not just transition time', () => {
  const reduced = css.slice(css.indexOf('@media (prefers-reduced-motion: reduce)'))
  assert.match(reduced, /\.el-button,[\s\S]*?transform: none !important/)
  assert.match(reduced, /\.el-button \.el-icon,[\s\S]*?animation: none !important/)
  assert.match(reduced, /\.el-button.is-loading[^}]*::after\s*\{[^}]*display: none !important/)
  assert.match(css, /\.el-table \.el-button,[\s\S]*?\.el-input-group__append \.el-button,[^}]*transform: none !important/)
})
