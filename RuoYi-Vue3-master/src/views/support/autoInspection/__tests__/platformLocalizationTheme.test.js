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

test('all loading surfaces use the platform mark instead of circular upstream spinners', () => {
  const bootstrap = read('index.html')
  const theme = read('src/assets/styles/platform-theme.scss')
  const loadingUtility = read('src/utils/platformLoading.js')
  const modal = read('src/plugins/modal.js')
  const request = read('src/utils/request.js')
  const download = read('src/plugins/download.js')
  const routeProgress = read('src/utils/routeProgress.js')
  const documentLoadingSources = [
    read('src/views/document/editor/index.vue'),
    read('src/views/document/preview/index.vue'),
    read('src/views/document/components/DocumentUploadDialog.vue')
  ]

  assert.ok(bootstrap.includes('platform-bootstrap-loader'))
  assert.ok(bootstrap.includes('/favicon.svg'))
  assert.ok(bootstrap.includes('prefers-reduced-motion: reduce'))
  assert.ok(!bootstrap.includes('loader-section'))
  assert.ok(!bootstrap.includes('#7171C6'))
  assert.ok(!bootstrap.includes('@keyframes spin'))

  assert.ok(theme.includes("url('../logo/platform-logo.svg')"))
  assert.ok(theme.includes('.platform-loading-mark'))
  assert.ok(theme.includes('.el-button.is-loading'))
  assert.ok(theme.includes('platform-loading-line'))
  assert.ok(!theme.includes('platform-loading-spin'))

  assert.ok(loadingUtility.includes('ElLoading.service'))
  for (const source of [modal, request, download]) {
    assert.ok(source.includes('openPlatformLoading'))
    assert.ok(!source.includes('ElLoading.service'))
    assert.ok(!source.includes('rgba(0, 0, 0, 0.7)'))
  }
  assert.ok(modal.includes('loadingDepth'))
  assert.ok(routeProgress.includes('completionTimer'))
  assert.ok(routeProgress.includes('clearTimeout(completionTimer)'))

  for (const source of documentLoadingSources) {
    assert.ok(source.includes('platform-loading-mark'))
    assert.ok(!source.includes('<Loading />'))
  }
})

test('version summary uses one aligned Element Plus description surface', () => {
  const versionCenter = read('src/views/support/version/index.vue')
  const supportTheme = read('src/assets/styles/support.scss')

  assert.ok(versionCenter.includes('<el-descriptions'))
  assert.ok(versionCenter.includes('aria-label="版本摘要"'))
  assert.ok(versionCenter.includes('summaryColumnCount'))
  assert.ok(versionCenter.includes('latestReleaseSummaryValue'))
  assert.ok(versionCenter.includes('--version-selection-bg'))
  assert.ok(versionCenter.includes('.version-tree-item.is-active .version-tree-item__top strong'))
  assert.ok(!versionCenter.includes('background: linear-gradient(135deg'))
  assert.ok(!versionCenter.includes('.section-title span'))
  assert.ok(!versionCenter.includes('align-items: baseline'))
  assert.ok(!supportTheme.includes('html.dark .version-center-page .version-hero'))
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
  assert.ok(overview.includes('await openHealthSamples({ date: focusDate, group: plan })'))
  assert.ok(overview.includes('applyingOverviewDeepLink'))
  assert.ok(releaseNotes.includes("version: 'v3.14.0'"))
})
