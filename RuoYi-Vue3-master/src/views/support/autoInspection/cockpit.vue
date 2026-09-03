<template>
  <div
    ref="cockpitRoot"
    class="app-container inspection-cockpit inspection-cockpit--full-chart"
    :class="{ 'is-fullscreen': isFullscreen }"
    data-design-seed="04a6e6a4"
  >
    <header class="cockpit-commandbar">
      <div class="cockpit-commandbar__identity">
        <h1>自动化巡检驾驶舱</h1>
        <div class="cockpit-commandbar__meta" role="status" aria-live="polite">
          <span>{{ displayUpdatedTime ? displayUpdatedTime.slice(0, 10) : dashboardDateKey() }}</span>
          <span>{{ displayUpdatedTime ? `更新于 ${displayUpdatedTime.slice(11)}` : '等待首次同步' }}</span>
          <el-text v-if="activeView === 'overview' && dashboardError && loadedOnce" type="warning" size="small">上次成功数据</el-text>
        </div>
      </div>
      <div class="cockpit-commandbar__actions">
        <div class="cockpit-refresh-control">
          <el-switch v-model="autoRefresh" aria-label="每30秒自动刷新驾驶舱" />
          <span>30秒刷新</span>
        </div>
        <el-space :size="8" wrap>
          <el-button class="motion-entry-action" data-motion-direction="forward" :icon="List" @click="openOverview()">巡检总览</el-button>
          <el-button class="motion-entry-action" data-motion-direction="forward" :icon="Setting" @click="openConfig">巡检配置</el-button>
          <el-tooltip v-if="isFullscreenSupported" :content="isFullscreen ? '退出全屏值守' : '全屏值守'" :append-to="cockpitRoot || undefined">
            <el-button
              :icon="isFullscreen ? ScaleToOriginal : FullScreen"
              :aria-label="isFullscreen ? '退出全屏值守' : '全屏值守'"
              @click="toggleFullscreen"
            />
          </el-tooltip>
          <el-button type="primary" :icon="Refresh" :loading="activeView === 'metrics' ? metricsRefreshing : refreshing" @click="refreshActiveView">刷新数据</el-button>
        </el-space>
      </div>
    </header>

    <el-tabs v-model="activeView" class="cockpit-views motion-tabs">
      <el-tab-pane name="metrics" lazy>
        <template #label>
          <span class="motion-control-label">
            <svg-icon icon-class="chart" class="motion-control-label__icon" />
            <span class="motion-control-label__text">计划指标</span>
          </span>
        </template>
        <PlanMetricDashboard
          ref="metricsRef"
          :palette="palette"
          :animate="animateCharts"
          :auto-refresh="autoRefresh"
          :active="activeView === 'metrics'"
          :overlay-container="cockpitRoot"
          @loading-change="metricsRefreshing = $event"
          @updated="metricsUpdatedTime = $event"
          @open-record="openOverview({ recordId: $event })"
        />
      </el-tab-pane>
      <el-tab-pane name="overview" lazy>
        <template #label>
          <span class="motion-control-label">
            <svg-icon icon-class="dashboard" class="motion-control-label__icon" />
            <span class="motion-control-label__text">运行总览</span>
          </span>
        </template>
    <el-alert
      v-if="dashboardError"
      class="cockpit-error-state"
      type="error"
      show-icon
      :closable="false"
      :title="dashboardError"
    >
      <el-button link type="primary" :disabled="refreshing" @click="loadDashboard">重新加载</el-button>
    </el-alert>

    <section v-loading="loading" class="cockpit-dashboard-grid" aria-label="自动化巡检图表看板">
      <article class="cockpit-chart-panel cockpit-chart-panel--status">
        <header class="cockpit-chart-panel__head">
          <div class="cockpit-chart-panel__title"><el-icon><PieChart /></el-icon><h2>今日健康构成</h2></div>
          <span>{{ scopeHealth.sites.length }} 现场 / {{ platformCount }} 主平台</span>
        </header>
        <AutoInspectionChart
          class="cockpit-chart cockpit-chart--status"
          :option="statusOption"
          :empty="!scopeHealth.sites.length"
          empty-description="今天暂无健康归属数据"
          aria-label="今日现场与主平台健康状态构成图"
          @chart-click="handleStatusClick"
        />
      </article>

      <article class="cockpit-chart-panel cockpit-chart-panel--trend">
        <header class="cockpit-chart-panel__head">
          <div class="cockpit-chart-panel__title"><el-icon><DataLine /></el-icon><h2>近七日巡检趋势</h2></div>
          <span>{{ trendPeriod }}</span>
        </header>
        <AutoInspectionChart
          class="cockpit-chart cockpit-chart--trend"
          :option="trendOption"
          :empty="!hasTrendData"
          empty-description="近七日暂无巡检数据"
          aria-label="近七日巡检健康与执行趋势图"
          @chart-click="handleTrendClick"
        />
      </article>

      <article class="cockpit-chart-panel cockpit-chart-panel--issues">
        <header class="cockpit-chart-panel__head">
          <div class="cockpit-chart-panel__title"><el-icon><Warning /></el-icon><h2>待处理问题分布</h2></div>
          <span>最新 {{ issueRows.length }} 项</span>
        </header>
        <AutoInspectionChart
          class="cockpit-chart cockpit-chart--issues"
          :option="issueOption"
          :empty="!issueRows.length"
          empty-description="今天暂未发现待处理问题"
          aria-label="自动化巡检待处理问题分布图"
          @chart-click="handleIssueClick"
        />
      </article>

      <article class="cockpit-chart-panel cockpit-chart-panel--scope">
        <header class="cockpit-chart-panel__head">
          <div class="cockpit-chart-panel__title"><el-icon><Histogram /></el-icon><h2>现场与主平台健康度</h2></div>
          <span>{{ scopeRows.length }} / {{ allScopeRows.length }} 个范围</span>
        </header>
        <div class="cockpit-scope-filters">
          <el-select v-model="scopeType" size="small" aria-label="健康范围层级" :append-to="cockpitRoot || undefined">
            <el-option label="全部范围" value="ALL" />
            <el-option label="现场" value="SITE" />
            <el-option label="主平台" value="MAIN_PLATFORM" />
          </el-select>
          <el-select v-model="scopeStatus" size="small" aria-label="健康范围状态" :append-to="cockpitRoot || undefined">
            <el-option label="全部状态" value="ALL" />
            <el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
          <el-button v-if="scopeType !== 'ALL' || scopeStatus !== 'ALL'" size="small" link type="primary" @click="resetScopeFilter">重置</el-button>
        </div>
        <AutoInspectionChart
          class="cockpit-chart cockpit-chart--scope"
          :view-key="`${scopeType}:${scopeStatus}`"
          :option="scopeOption"
          :empty="!scopeRows.length"
          empty-description="当前筛选下暂无健康范围"
          aria-label="现场与主平台健康度排行图"
          @chart-click="handleScopeClick"
        />
      </article>

      <article class="cockpit-chart-panel cockpit-chart-panel--plan">
        <header class="cockpit-chart-panel__head">
          <div class="cockpit-chart-panel__title"><el-icon><Finished /></el-icon><h2>计划执行完成度</h2></div>
          <span>{{ planRows.length }} 个当日计划</span>
        </header>
        <AutoInspectionChart
          class="cockpit-chart cockpit-chart--plan"
          :option="planOption"
          :empty="!planRows.length"
          empty-description="今天暂无计划执行数据"
          aria-label="巡检计划执行完成度图"
          @chart-click="handlePlanClick"
        />
      </article>

      <article class="cockpit-chart-panel cockpit-chart-panel--records">
        <header class="cockpit-chart-panel__head">
          <div class="cockpit-chart-panel__title"><el-icon><Clock /></el-icon><h2>最近执行时间轴</h2></div>
          <span>最近 {{ recordRows.length }} 次</span>
        </header>
        <AutoInspectionChart
          class="cockpit-chart cockpit-chart--records"
          :option="recordOption"
          :empty="!recordRows.length"
          empty-description="今天暂无逐次执行记录"
          aria-label="最近自动化巡检执行结果时间轴"
          @chart-click="handleRecordClick"
        />
      </article>
    </section>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup name="SupportAutoInspectionCockpit">
import { Clock, DataLine, Finished, FullScreen, Histogram, List, PieChart, Refresh, ScaleToOriginal, Setting, Warning } from '@element-plus/icons-vue'
import { useFullscreen, usePreferredReducedMotion } from '@vueuse/core'
import { color as chartColor } from 'echarts'
import { getAutoInspectionDashboard } from '@/api/support/autoInspection'
import useSettingsStore from '@/store/modules/settings'
import AutoInspectionChart from './components/AutoInspectionChart.vue'
import PlanMetricDashboard from './components/PlanMetricDashboard.vue'
import {
  RESULT_ABNORMAL, RESULT_NORMAL, RESULT_SKIP, RESULT_WARNING,
  buildIssueChartRows, buildPlanCompletionRows, buildRecentExecutionChartRows, buildScopeHealthChartRows,
  filterScopeChartRows, formatShortDate, groupPlanHealthByScope, healthStatusLabel,
  normalizeCockpitDashboard, resolvePlanChartRow, resolveScopeChartRow
} from './cockpitPresentation'
import { buildIssueOption, buildPlanOption, buildRecordOption, buildScopeOption, buildStatusOption, buildTrendOption } from './cockpitChartOptions'

const router = useRouter()
const route = useRoute()
const { proxy } = getCurrentInstance()
const settingsStore = useSettingsStore()
const cockpitRoot = ref(null)
const activeView = ref(route.query.view === 'overview' ? 'overview' : 'metrics')
const metricsRef = ref(null)
const metricsRefreshing = ref(false)
const metricsUpdatedTime = ref('')
const { isFullscreen, isSupported: isFullscreenSupported, toggle: toggleNativeFullscreen } = useFullscreen(cockpitRoot)
const preferredMotion = usePreferredReducedMotion()
const animateCharts = computed(() => preferredMotion.value !== 'reduce')
const refreshing = ref(false)
const loadedOnce = ref(false)
const loading = computed(() => refreshing.value && !loadedOnce.value)
const autoRefresh = ref(true)
const dashboardError = ref('')
const dashboard = ref(normalizeCockpitDashboard())
const displayUpdatedTime = computed(() => activeView.value === 'metrics' ? metricsUpdatedTime.value : dashboard.value.generatedTime)
const scopeType = ref('ALL')
const scopeStatus = ref('ALL')
const palette = shallowRef(readChartPalette())
const statusOptions = [RESULT_ABNORMAL, RESULT_WARNING, RESULT_NORMAL, RESULT_SKIP].map((value) => ({ value, label: healthStatusLabel(value) }))
let refreshTimer = null
let themeFrame = null
let active = true
let disposed = false

const scopeHealth = computed(() => groupPlanHealthByScope(dashboard.value.currentPlanHealth))
const platformCount = computed(() => scopeHealth.value.sites.reduce((count, site) => count + site.children.length, 0))
const scopeCount = computed(() => scopeHealth.value.sites.reduce((count, site) => count + 1 + site.children.length, 0))
const allScopeRows = computed(() => buildScopeHealthChartRows(scopeHealth.value.sites, scopeCount.value))
const scopeRows = computed(() => filterScopeChartRows(allScopeRows.value, scopeType.value, scopeStatus.value))
const planRows = computed(() => buildPlanCompletionRows(dashboard.value.currentPlanHealth, dashboard.value.currentPlanHealth.length))
const issueRows = computed(() => buildIssueChartRows(dashboard.value.latestIssues, 12))
const recordRows = computed(() => buildRecentExecutionChartRows(dashboard.value.recentRecords, 16))
const trendRows = computed(() => dashboard.value.combinedTrend)
const trendPeriod = computed(() => trendRows.value.length ? `${formatShortDate(trendRows.value[0].date)} - ${formatShortDate(trendRows.value.at(-1).date)}` : '近七日')
const hasTrendData = computed(() => trendRows.value.some((row) => Number(row.routineTotal || 0) + Number(row.frequentExpected || 0) + Number(row.frequentCompleted || 0) > 0))
const trendOption = computed(() => buildTrendOption(trendRows.value, palette.value, animateCharts.value))
const statusOption = computed(() => buildStatusOption(scopeHealth.value.sites, palette.value, { scopeType: scopeType.value, resultStatus: scopeStatus.value }, animateCharts.value))
const scopeOption = computed(() => buildScopeOption(scopeRows.value, palette.value, animateCharts.value))
const planOption = computed(() => buildPlanOption(planRows.value, palette.value, animateCharts.value))
const issueOption = computed(() => buildIssueOption(issueRows.value, palette.value, animateCharts.value))
const recordOption = computed(() => buildRecordOption(recordRows.value, palette.value, animateCharts.value))

async function loadDashboard() {
  if (refreshing.value || disposed || !active) return
  refreshing.value = true
  try {
    const response = await getAutoInspectionDashboard()
    if (disposed) return
    dashboard.value = normalizeCockpitDashboard(response.data || {})
    loadedOnce.value = true
    dashboardError.value = ''
  } catch {
    if (!disposed) {
      dashboardError.value = loadedOnce.value
        ? '数据刷新失败，当前保留上次成功结果。请检查连接后重试。'
        : '驾驶舱数据加载失败，请检查后端服务、登录状态和查询权限。'
    }
  } finally {
    if (!disposed) refreshing.value = false
  }
}

function stopAutoRefresh() {
  if (refreshTimer != null) clearInterval(refreshTimer)
  refreshTimer = null
}

function syncAutoRefresh() {
  stopAutoRefresh()
  if (!autoRefresh.value || !active || disposed) return
  refreshTimer = setInterval(() => {
    if (activeView.value === 'overview' && document.visibilityState === 'visible') loadDashboard()
  }, 30000)
}

function handleVisibilityChange() {
  if (activeView.value === 'overview' && document.visibilityState === 'visible' && autoRefresh.value && active) loadDashboard()
}

function readChartPalette() {
  const styles = getComputedStyle(cockpitRoot.value || document.documentElement)
  const read = (name) => styles.getPropertyValue(name).trim()
  const accent = read('--cockpit-accent') || read('--el-color-primary')
  const white = read('--el-color-white') || '#ffffff'
  const warning = read('--health-warning')
  const danger = read('--health-danger')
  return {
    heading: white, text: chartColor.modifyAlpha(white, 0.84), muted: chartColor.modifyAlpha(white, 0.58),
    grid: chartColor.modifyAlpha(accent, 0.14), surface: read('--cockpit-panel') || read('--surface-strong'), subtle: chartColor.modifyAlpha(accent, 0.07),
    normal: read('--health-normal'), warning, danger, idle: read('--health-idle'),
    primary: accent, warningSoft: chartColor.modifyAlpha(warning, 0.12), dangerSoft: chartColor.modifyAlpha(danger, 0.12),
    fontFamily: getComputedStyle(document.body).fontFamily
  }
}

function syncChartPalette() {
  if (themeFrame != null) cancelAnimationFrame(themeFrame)
  themeFrame = requestAnimationFrame(() => {
    themeFrame = null
    if (!disposed) palette.value = readChartPalette()
  })
}

watch(autoRefresh, syncAutoRefresh)
watch(activeView, (view) => {
  router.replace({ query: { ...route.query, view } })
  if (view === 'overview' && !loadedOnce.value) loadDashboard()
})
watch(() => route.query.view, (view) => { activeView.value = view === 'overview' ? 'overview' : 'metrics' })
watch(() => [settingsStore.isDark, settingsStore.theme], syncChartPalette, { flush: 'post' })
onMounted(() => {
  if (activeView.value === 'overview') loadDashboard()
  syncAutoRefresh()
  syncChartPalette()
  document.addEventListener('visibilitychange', handleVisibilityChange)
})
onActivated(() => { active = true; syncAutoRefresh(); syncChartPalette() })
onDeactivated(() => { active = false; stopAutoRefresh() })
onBeforeUnmount(() => {
  disposed = true
  active = false
  stopAutoRefresh()
  if (themeFrame != null) cancelAnimationFrame(themeFrame)
  document.removeEventListener('visibilitychange', handleVisibilityChange)
})

async function toggleFullscreen() {
  try { await toggleNativeFullscreen() } catch { proxy.$modal.msgWarning('当前浏览器无法切换全屏，请重试。') }
}

function refreshActiveView() {
  if (activeView.value === 'metrics') return metricsRef.value?.refresh()
  return loadDashboard()
}

function navigateModulePage(location) {
  window.location.assign(router.resolve(location).href)
}

function openOverview(extraQuery = {}) {
  navigateModulePage({ path: '/autoInspection/dashboard', query: { tab: 'dashboard', ...extraQuery } })
}

function openConfig() {
  navigateModulePage({ path: '/autoInspection/config', query: { tab: 'template' } })
}

function dashboardDateKey() {
  if (/^\d{4}-\d{2}-\d{2}/.test(dashboard.value.generatedTime)) return dashboard.value.generatedTime.slice(0, 10)
  const today = new Date()
  return [today.getFullYear(), String(today.getMonth() + 1).padStart(2, '0'), String(today.getDate()).padStart(2, '0')].join('-')
}

function openScopeDetail(scope) {
  openOverview({
    date: dashboardDateKey(), scopeKey: scope.scopeKey, siteId: scope.siteId,
    mainPlatformId: scope.mainPlatformId || undefined,
    openSamples: ['2', '4'].includes(scope.resultStatus) ? '1' : undefined,
    resultStatus: ['2', '4'].includes(scope.resultStatus) ? scope.resultStatus : undefined
  })
}

function handleTrendClick({ data, dataIndex }) {
  const date = data?.date || trendRows.value[dataIndex]?.date
  if (date) openOverview({ date })
}

function handleStatusClick({ data }) {
  if (!data?.scopeType || !statusOptions.some((item) => item.value === data.status)) return
  scopeType.value = data.scopeType
  scopeStatus.value = data.status
}

function resetScopeFilter() {
  scopeType.value = 'ALL'
  scopeStatus.value = 'ALL'
}

function handleScopeClick(params) {
  const row = resolveScopeChartRow(params, scopeRows.value)
  if (row) openScopeDetail(row)
}

function handlePlanClick(params) {
  const plan = resolvePlanChartRow(params, planRows.value)
  if (plan) openOverview({ date: dashboardDateKey(), planId: plan.planId, openSamples: '1' })
}

function handleIssueClick({ data }) {
  if (!Number.isInteger(data?.issueIndex)) return
  const item = issueRows.value[data.issueIndex]
  if (!item) return
  if (item.sourceMode === 'FREQUENT') openOverview({ date: item.healthDate || dashboardDateKey(), planId: item.planId, openSamples: '1' })
  else if (item.recordId) openOverview({ recordId: item.recordId })
}

function handleRecordClick({ data }) {
  if (data?.recordId) openOverview({ recordId: data.recordId })
}
</script>

<style scoped lang="scss">
.inspection-cockpit {
  --cockpit-background: #080f1a;
  --cockpit-panel: #111e2c;
  --cockpit-accent: #63d6f5;
  --app-heading: var(--el-color-white);
  --app-text: color-mix(in srgb, var(--el-color-white) 84%, transparent);
  --app-muted: color-mix(in srgb, var(--el-color-white) 58%, transparent);
  --surface-bg: var(--cockpit-panel);
  --surface-strong: var(--cockpit-panel);
  --surface-muted: var(--cockpit-background);
  --surface-hover: color-mix(in srgb, var(--cockpit-accent) 12%, var(--cockpit-panel));
  --surface-subtle: color-mix(in srgb, var(--cockpit-accent) 4%, transparent);
  --surface-border: color-mix(in srgb, var(--cockpit-accent) 18%, transparent);
  --surface-border-strong: color-mix(in srgb, var(--cockpit-accent) 38%, transparent);
  --loading-mask-bg: color-mix(in srgb, var(--cockpit-background) 88%, transparent);
  --el-bg-color: var(--cockpit-panel);
  --el-bg-color-overlay: var(--cockpit-panel);
  --el-fill-color-blank: var(--cockpit-panel);
  --el-fill-color: color-mix(in srgb, var(--cockpit-accent) 10%, var(--cockpit-panel));
  --el-fill-color-light: color-mix(in srgb, var(--cockpit-accent) 7%, var(--cockpit-panel));
  --el-fill-color-lighter: var(--el-fill-color-light);
  --el-fill-color-dark: var(--cockpit-background);
  --el-color-primary: var(--cockpit-accent);
  --el-color-primary-light-3: color-mix(in srgb, var(--cockpit-accent) 75%, var(--el-color-white));
  --el-color-primary-light-5: color-mix(in srgb, var(--cockpit-accent) 45%, var(--cockpit-panel));
  --el-color-primary-light-8: color-mix(in srgb, var(--cockpit-accent) 18%, var(--cockpit-panel));
  --el-color-primary-light-9: var(--el-fill-color-light);
  --el-color-primary-dark-2: color-mix(in srgb, var(--cockpit-accent) 80%, var(--el-color-black));
  --el-color-danger-light-9: color-mix(in srgb, var(--el-color-danger) 12%, var(--cockpit-panel));
  --el-color-warning-light-9: color-mix(in srgb, var(--el-color-warning) 12%, var(--cockpit-panel));
  --el-color-info-light-9: var(--cockpit-panel);
  --el-color-success-light-9: color-mix(in srgb, var(--el-color-success) 12%, var(--cockpit-panel));
  --el-text-color-primary: var(--app-heading);
  --el-text-color-regular: var(--app-text);
  --el-text-color-secondary: var(--app-muted);
  --el-text-color-placeholder: var(--app-muted);
  --el-text-color-disabled: color-mix(in srgb, var(--el-color-white) 30%, transparent);
  --el-border-color: var(--surface-border-strong);
  --el-border-color-light: var(--surface-border);
  --el-border-color-lighter: var(--surface-border);
  position: relative;
  color-scheme: dark;
  display: flex;
  flex-direction: column;
  gap: 12px;
  height: calc(100dvh - 84px);
  min-height: 640px;
  color: var(--app-text);
  background-color: var(--cockpit-background);
  background-image: linear-gradient(var(--surface-subtle) 1px, transparent 1px), linear-gradient(90deg, var(--surface-subtle) 1px, transparent 1px);
  background-size: 48px 48px;
}

.inspection-cockpit:fullscreen {
  height: 100dvh;
  min-height: 0;
  overflow: auto;
}

.cockpit-commandbar {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex: 0 0 auto;
  gap: 16px;
  min-height: 64px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--surface-border-strong);
}

.cockpit-commandbar::after {
  content: '';
  position: absolute;
  bottom: -1px;
  left: 0;
  width: 84px;
  height: 4px;
  background: var(--cockpit-accent);
  clip-path: polygon(0 0, 90% 0, 100% 100%, 0 100%);
}

.cockpit-commandbar__identity { min-width: 0; }

.cockpit-commandbar h1,
:deep(.cockpit-chart-panel__head h2) {
  margin: 0;
  color: var(--app-heading);
  letter-spacing: 0;
  overflow-wrap: anywhere;
}

.cockpit-commandbar h1 { font-size: 22px; font-weight: 600; }

.cockpit-commandbar__meta,
.cockpit-refresh-control {
  display: flex;
  align-items: center;
  gap: 12px;
  color: var(--app-muted);
  font-size: 12px;
  font-variant-numeric: tabular-nums;
}

.cockpit-commandbar__meta { flex-wrap: wrap; margin-top: 6px; }

.cockpit-commandbar__actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  flex-wrap: wrap;
  gap: 16px;
}

.cockpit-refresh-control { gap: 6px; white-space: nowrap; }
.cockpit-error-state { flex: 0 0 auto; }
.cockpit-views { flex: 1; display: flex; flex-direction: column; min-height: 0; }
.cockpit-views :deep(.el-tabs__header) { margin-bottom: 12px; }
.cockpit-views :deep(.el-tabs__content) { flex: 1; min-height: 0; overflow: auto; }
.cockpit-views :deep(.el-tab-pane) { display: flex; flex-direction: column; gap: 12px; height: 100%; }

.cockpit-dashboard-grid {
  display: grid;
  flex: 1;
  grid-template-columns: repeat(12, minmax(0, 1fr));
  grid-template-rows: repeat(2, minmax(230px, 1fr));
  grid-template-areas:
    'status status status trend trend trend trend trend trend issues issues issues'
    'scope scope scope scope plan plan plan plan records records records records';
  gap: 12px;
  min-width: 0;
  min-height: 0;
}

:deep(.cockpit-chart-panel) {
  display: flex;
  flex-direction: column;
  min-width: 0;
  min-height: 0;
  overflow: hidden;
  border: 1px solid var(--surface-border);
  border-radius: 2px;
  background: color-mix(in srgb, var(--cockpit-panel) 78%, transparent);
  clip-path: polygon(0 0, calc(100% - 10px) 0, 100% 10px, 100% 100%, 0 100%);
}

.cockpit-chart-panel--trend { grid-area: trend; }
.cockpit-chart-panel--status { grid-area: status; }
.cockpit-chart-panel--scope { grid-area: scope; }
.cockpit-chart-panel--plan { grid-area: plan; }
.cockpit-chart-panel--issues { grid-area: issues; }
.cockpit-chart-panel--records { grid-area: records; }

:deep(.cockpit-chart-panel__head) {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex: 0 0 auto;
  flex-wrap: wrap;
  gap: 6px 12px;
  min-height: 44px;
  padding: 10px 12px;
  border-bottom: 1px solid var(--surface-border);
  background: linear-gradient(90deg, var(--surface-subtle), transparent 70%);
}

:deep(.cockpit-chart-panel__title) { display: flex; align-items: center; gap: 6px; min-width: 0; }
:deep(.cockpit-chart-panel__title > .el-icon) { flex: 0 0 auto; color: var(--cockpit-accent); }
:deep(.cockpit-chart-panel__head h2) { font-size: 14px; font-weight: 600; }
:deep(.cockpit-chart-panel__head > span) { color: var(--app-muted); font-size: 11px; font-variant-numeric: tabular-nums; }
.cockpit-chart-panel--trend .cockpit-chart-panel__title > .el-icon { color: var(--el-color-primary); }
.cockpit-chart-panel--issues .cockpit-chart-panel__title > .el-icon { color: var(--health-warning); }
:deep(.cockpit-chart) { flex: 1; min-height: 0; }

.cockpit-scope-filters {
  display: flex;
  align-items: center;
  flex: 0 0 auto;
  gap: 8px;
  padding: 8px 12px 0;
}

.cockpit-scope-filters > .el-select { width: 104px; }

@media (max-width: 1280px) {
  .inspection-cockpit { height: auto; min-height: calc(100dvh - 84px); }
  .cockpit-views :deep(.el-tabs__content) { overflow: visible; }
  .cockpit-commandbar { align-items: flex-start; flex-wrap: wrap; }
  .cockpit-dashboard-grid {
    flex: auto;
    grid-template-rows: repeat(3, 340px);
    grid-template-areas:
      'trend trend trend trend trend trend trend trend status status status status'
      'scope scope scope scope scope scope plan plan plan plan plan plan'
      'issues issues issues issues issues issues records records records records records records';
  }
}

@media (max-width: 800px) {
  .cockpit-commandbar__actions { justify-content: flex-start; gap: 8px; }
  .cockpit-dashboard-grid {
    grid-template-columns: minmax(0, 1fr);
    grid-template-rows: repeat(6, 330px);
    grid-template-areas: 'trend' 'status' 'scope' 'plan' 'issues' 'records';
  }
}

@media (prefers-reduced-motion: reduce) {
  .inspection-cockpit * { scroll-behavior: auto !important; }
}
</style>
