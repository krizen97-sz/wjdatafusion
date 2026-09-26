import assert from 'node:assert/strict'
import test from 'node:test'
import { readFileSync } from 'node:fs'
import { compileString } from 'sass-embedded'
import { parse } from '@vue/compiler-sfc'

const read = path => readFileSync(new URL('../../../../' + path, import.meta.url), 'utf8')
const css = compileString(read('assets/styles/motion-surfaces.scss')).css
const transitions = read('assets/styles/transition.scss')

test('shared surface motion loads globally and uses platform duration tokens', () => {
  assert.match(read('assets/styles/index.scss'), /@use '\.\/motion-surfaces.scss'/)
  assert.match(css, /--motion-duration-overlay/)
  assert.match(css, /--motion-duration-exit/)
  assert.doesNotMatch(css, /infinite|transition:\s*all|will-change|#[0-9a-f]{3,8}\b/i)
})

test('panel motion preserves draggable and fullscreen coordinate systems', () => {
  assert.match(css, /\.dialog-fade-enter-active \.el-dialog:not\(\.is-fullscreen\):not\(\.is-draggable\)/)
  assert.match(css, /\.fade-in-linear-enter-active \.el-message-box:not\(\.is-draggable\)/)
  assert.match(css, /\.dialog-fade-enter-active \.el-overlay-dialog,[^}]*animation: none/)
  assert.match(css, /\.el-drawer:not\(\.is-dragging\)\s*\{[^}]*transition: transform/)
  assert.doesNotMatch(css, /\.el-dialog\s*\{[^}]*transform:/)
})

test('popper position, radio dot and checkbox tick remain native state geometry', () => {
  assert.match(css, /\.el-zoom-in-top-enter-active,[^}]*transition-duration:/)
  assert.doesNotMatch(css, /\.el-zoom-in-top-enter-active,[^}]*transform:/)
  assert.doesNotMatch(css, /\.el-checkbox__inner::after,[^}]*transform:/)
  assert.doesNotMatch(css, /\.el-radio__inner::after\s*\{[^}]*transform:/)
  assert.match(css, /\.el-checkbox__input.is-disabled[^}]*transition: none/)
})

test('reduced motion covers panels and native controls without clearing checked transforms', () => {
  const reduced = css.slice(css.indexOf('@media (prefers-reduced-motion: reduce)'))
  for (const name of ['.el-dialog', '.el-message-box', '.el-drawer', '.el-popper',
    '.el-switch__action', '.el-checkbox__inner::after', '.el-radio__inner::after',
    '.el-upload-list__item', '.el-progress-bar__inner']) assert.ok(reduced.includes(name), name)
  assert.match(reduced, /animation: none !important/)
  assert.match(reduced, /transition-duration: 1ms !important/)
  assert.doesNotMatch(reduced, /\.el-checkbox__inner::after,[^}]*transform:/)
})

test('route exits cannot intercept clicks and reduced motion suppresses page displacement', () => {
  assert.match(transitions, /\.fade-transform-leave-active\s*\{[^}]*pointer-events: none/)
  assert.match(transitions, /prefers-reduced-motion: reduce/)
  assert.doesNotMatch(transitions, /translateX\(-?30px\)|transition: all/)
})

test('shared icon swaps keep native controls, state labels and stable one-cell frames', () => {
  for (const file of ['Hamburger', 'Screenfull', 'RightToolbar']) {
    const source = read('components/' + file + '/index.vue')
    assert.equal(parse(source).errors.length, 0)
    assert.match(source, /class="motion-icon-swap/)
    assert.match(source, /<Transition name="motion-icon-swap">/)
    assert.match(source, /:key="(?:isActive|isFullscreen|showSearch)"/)
    assert.match(source, /:aria-(?:expanded|pressed)=/)
    assert.doesNotMatch(source, /setTimeout|setInterval/)
  }
  assert.match(css, /\.motion-icon-swap\s*\{[^}]*display: inline-grid/)
  assert.match(css, /grid-area: 1\/1/)
  assert.doesNotMatch(read('components/Hamburger/index.vue'), /<svg|<path/)
})
