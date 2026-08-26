import test from 'node:test'
import assert from 'node:assert/strict'
import fs from 'node:fs'

const read = (file) => fs.readFileSync(file, 'utf8')

test('production and staging builds expose only the platform brand', () => {
  const production = read('.env.production')
  const staging = read('.env.staging')
  const packageSource = read('package.json')

  assert.ok(production.includes('VITE_APP_TITLE = 华东信息融合平台'))
  assert.ok(staging.includes('VITE_APP_TITLE = 华东信息融合平台'))
  assert.ok(packageSource.includes('"name": "wjdatafusion-web"'))
  assert.ok(!packageSource.includes('"nprogress"'))
  assert.ok(!packageSource.includes('若依管理系统'))
})

test('route loading and error pages use local platform presentation', () => {
  const permission = read('src/permission.js')
  const routeProgress = read('src/utils/routeProgress.js')
  const notFound = read('src/views/error/404.vue')
  const forbidden = read('src/views/error/401.vue')

  assert.ok(permission.includes('startRouteProgress'))
  assert.ok(routeProgress.includes('platform-route-progress'))
  assert.ok(!permission.includes('NProgress'))
  assert.ok(notFound.includes('platform-logo.svg'))
  assert.ok(forbidden.includes('platform-logo.svg'))
  assert.ok(!notFound.includes('404_cloud'))
  assert.ok(!forbidden.includes('401.gif'))
})

test('global theme contract includes complete light and dark semantic surfaces', () => {
  const variables = read('src/assets/styles/variables.module.scss')
  const theme = read('src/assets/styles/platform-theme.scss')
  const navbar = read('src/layout/components/Navbar.vue')
  const settings = read('src/layout/components/Settings/index.vue')
  const transition = read('src/utils/themeTransition.js')

  for (const marker of [
    '--surface-strong',
    '--surface-muted',
    '--surface-subtle',
    '--surface-hover',
    '--surface-border-strong',
    '--loading-mask-bg',
    '--health-warning'
  ]) {
    assert.ok(variables.includes(marker), `missing theme token: ${marker}`)
  }
  assert.ok(variables.includes('html.dark'))
  assert.ok(theme.includes('.platform-loading-mask'))
  assert.ok(theme.includes('.el-table--enable-row-hover'))
  assert.ok(navbar.includes('runThemeTransition'))
  assert.ok(settings.includes('runThemeTransition'))
  assert.ok(transition.includes('document.startViewTransition'))
  assert.ok(transition.includes('prefers-reduced-motion: reduce'))
})

test('async route pages do not use the blank-prone out-in transition mode', () => {
  const appMain = read('src/layout/components/AppMain.vue')

  assert.ok(appMain.includes('Component && !route.meta.link'))
  assert.ok(!appMain.includes('mode="out-in"'))
})

test('cockpit survives production compilation contract', () => {
  const cockpit = read('src/views/support/autoInspection/cockpit.vue')
  const overview = read('src/views/support/autoInspection/index.vue')
  const releaseNotes = read('src/views/support/version/releaseNotes.js')

  assert.ok(cockpit.includes('data-design-seed="04a6e6a4"'))
  assert.ok(cockpit.includes('combinedTrend'))
  assert.ok(cockpit.includes('currentPlanHealth'))
  assert.ok(cockpit.includes("openSamples: '1'"))
  assert.ok(overview.includes('await openHealthSamples({ date: focusDate, plan })'))
  assert.ok(overview.includes('applyingOverviewDeepLink'))
  assert.ok(releaseNotes.includes("version: 'v3.14.0'"))
})
