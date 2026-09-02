<template>
  <div class="app-container inspection-cockpit inspection-cockpit--full-chart" data-design-seed="04a6e6a4">
    <header class="cockpit-commandbar">
      <div>
        <h1>自动化巡检驾驶舱</h1>
        <p>以图表汇总现场、主平台、计划、问题和执行记录，数据更新于 {{ dashboard.generatedTime || '-' }}</p>
      </div>
      <div class="cockpit-commandbar__actions">
        <el-button class="motion-entry-action" data-motion-direction="forward" :icon="List" @click="openOverview">巡检总览</el-button>
        <el-button class="motion-entry-action" data-motion-direction="forward" :icon="Setting" @click="openConfig">巡检配置</el-button>
        <el-button type="primary" :icon="Refresh" :loading="loading" @click="loadDashboard">刷新数据</el-button>
      </div>
    </header>

    <section v-if="dashboardError" class="cockpit-error-state" role="alert">
      <el-icon><Warning /></el-icon>
      <div>
        <strong>驾驶舱数据加载失败</strong>
        <span>{{ dashboardError }}</span>
      </div>
      <el-button type="primary" plain :icon="Refresh" @click="loadDashboard">重新加载</el-button>
    </section>

    <section v-loading="loading" class="cockpit-dashboard-grid" aria-label="自动化巡检图表看板">
      <article class="cockpit-chart-panel cockpit-chart-panel--trend">
        <header class="cockpit-chart-panel__head">
          <div>
            <h2>近七日巡检趋势</h2>
            <span>健康度、应执行、已完成和问题数量统一对照</span>
          </div>
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

      <article class="cockpit-chart-panel cockpit-chart-panel--status">
        <header class="cockpit-chart-panel__head">
          <div>
            <h2>今日健康构成</h2>
            <span>内环为现场，外环为主平台</span>
          </div>
        </header>
        <AutoInspectionChart
          class="cockpit-chart cockpit-chart--status"
          :option="statusOption"
          :empty="!scopeRows.length"
          empty-description="今天暂无现场或主平台健康数据"
          aria-label="今日现场与主平台健康状态构成图"
          @chart-click="handleStatusClick"
        />
      </article>

      <article class="cockpit-chart-panel cockpit-chart-panel--scope">
        <header class="cockpit-chart-panel__head">
          <div>
            <h2>现场与主平台健康度</h2>
            <span>异常优先、健康度从低到高排列</span>
          </div>
        </header>
        <AutoInspectionChart
          class="cockpit-chart cockpit-chart--scope"
          :option="scopeOption"
          :empty="!scopeRows.length"
          empty-description="今天暂无可归属的健康数据"
          aria-label="现场与主平台健康度排行图"
          @chart-click="handleScopeClick"
        />
      </article>

      <article class="cockpit-chart-panel cockpit-chart-panel--plan">
        <header class="cockpit-chart-panel__head">
          <div>
            <h2>计划执行完成度</h2>
            <span>包含已归属与待归属计划，异常计划优先</span>
          </div>
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

      <article class="cockpit-chart-panel cockpit-chart-panel--issues">
        <header class="cockpit-chart-panel__head">
          <div>
            <h2>待处理问题分布</h2>
            <span>面积表示问题严重程度，点击进入问题来源</span>
          </div>
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

      <article class="cockpit-chart-panel cockpit-chart-panel--records">
        <header class="cockpit-chart-panel__head">
          <div>
            <h2>最近执行时间轴</h2>
            <span>按执行时间和结果状态展示最近巡检记录</span>
          </div>
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
  </div>
</template>

<script setup name="SupportAutoInspectionCockpit">
import { List, Refresh, Setting, Warning } from '@element-plus/icons-vue'
import { getAutoInspectionDashboard } from '@/api/support/autoInspection'
import useSettingsStore from '@/store/modules/settings'
import AutoInspectionChart from './components/AutoInspectionChart.vue'
import {
  RESULT_ABNORMAL,
  RESULT_NORMAL,
  RESULT_SKIP,
  RESULT_WARNING,
  buildCurrentStatusDistribution,
  buildIssueChartRows,
  buildPlanCompletionRows,
  buildRecentExecutionChartRows,
  buildScopeHealthChartRows,
  formatShortDate,
  groupPlanHealthByScope,
  healthStatusColor,
  healthStatusLabel,
  normalizeCockpitDashboard,
  normalizeHealthScore
} from './cockpitPresentation'

const router = useRouter()
const settingsStore = useSettingsStore()
const loading = ref(false)
const dashboardError = ref('')
const dashboard = ref(normalizeCockpitDashboard())

const scopeHealth = computed(() => groupPlanHealthByScope(dashboard.value.currentPlanHealth))
const scopeRows = computed(() => buildScopeHealthChartRows(scopeHealth.value.sites, 12))
const planRows = computed(() => buildPlanCompletionRows(dashboard.value.currentPlanHealth, 10))
const issueRows = computed(() => buildIssueChartRows(dashboard.value.latestIssues, 12))
const recordRows = computed(() => buildRecentExecutionChartRows(dashboard.value.recentRecords, 16))
const trendRows = computed(() => dashboard.value.combinedTrend || [])
const hasTrendData = computed(() => trendRows.value.some((row) => (
  Number(row.routineTotal || 0)
  + Number(row.frequentExpected || 0)
  + Number(row.frequentCompleted || 0)
) > 0))
const siteStatusRows = computed(() => buildCurrentStatusDistribution(scopeHealth.value.sites))
const platformStatusRows = computed(() => buildCurrentStatusDistribution(scopeHealth.value.sites.flatMap((site) => site.children || [])))

onMounted(loadDashboard)

function loadDashboard() {
  loading.value = true
  dashboardError.value = ''
  return getAutoInspectionDashboard()
    .then((res) => { dashboard.value = normalizeCockpitDashboard(res.data || {}) })
    .catch(() => {
      dashboard.value = normalizeCockpitDashboard()
      dashboardError.value = '请检查后端服务、登录状态和巡检查询权限后重试。'
    })
    .finally(() => { loading.value = false })
}

function readThemeToken(name, fallback) {
  const value = getComputedStyle(document.documentElement).getPropertyValue(name).trim()
  return value || fallback
}

function chartPalette() {
  const isDark = settingsStore.isDark
  return {
    heading: isDark ? '#f4f8ff' : readThemeToken('--app-heading', '#17314d'),
    text: isDark ? '#d8e2f0' : readThemeToken('--app-text', '#35506d'),
    muted: isDark ? '#91a1b6' : readThemeToken('--app-muted', '#6d8298'),
    grid: isDark ? '#263547' : readThemeToken('--chart-grid', '#e4eaf1'),
    surface: isDark ? '#151d29' : readThemeToken('--surface-strong', '#ffffff'),
    subtle: isDark ? '#1a2533' : readThemeToken('--surface-subtle', '#eef2f6'),
    normal: readThemeToken('--health-normal', '#2f9b73'),
    warning: readThemeToken('--health-warning', '#c88824'),
    danger: readThemeToken('--health-danger', '#d55353'),
    idle: readThemeToken('--health-idle', '#8a9aad'),
    primary: readThemeToken('--el-color-primary', '#409eff')
  }
}

function tooltipStyle(palette) {
  return {
    confine: true,
    backgroundColor: palette.surface,
    borderColor: palette.grid,
    textStyle: { color: palette.text }
  }
}

function escapeHtml(value) {
  return String(value ?? '')
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#39;')
}

const trendOption = computed(() => {
  const palette = chartPalette()
  const rows = trendRows.value
  return {
    animation: false,
    aria: { enabled: true },
    color: [palette.primary, palette.warning, palette.normal, palette.danger],
    tooltip: { trigger: 'axis', ...tooltipStyle(palette) },
    legend: { top: 8, right: 12, textStyle: { color: palette.muted }, itemWidth: 12, itemHeight: 8 },
    grid: { left: 48, right: 48, top: 48, bottom: 34 },
    xAxis: { type: 'category', data: rows.map((row) => formatShortDate(row.date)), axisLine: { lineStyle: { color: palette.grid } }, axisTick: { show: false }, axisLabel: { color: palette.muted } },
    yAxis: [
      { type: 'value', min: 0, max: 100, axisLabel: { color: palette.muted, formatter: '{value}%' }, splitLine: { lineStyle: { color: palette.grid } } },
      { type: 'value', minInterval: 1, axisLabel: { color: palette.muted }, splitLine: { show: false } }
    ],
    series: [
      { name: '健康度', type: 'line', yAxisIndex: 0, smooth: 0.25, symbolSize: 7, lineStyle: { width: 3 }, data: rows.map((row) => normalizeHealthScore(row.healthScore)) },
      { name: '应执行', type: 'bar', yAxisIndex: 1, barMaxWidth: 17, itemStyle: { opacity: 0.58 }, data: rows.map((row) => Number(row.frequentExpected || 0)) },
      { name: '已完成', type: 'bar', yAxisIndex: 1, barMaxWidth: 17, itemStyle: { opacity: 0.76 }, data: rows.map((row) => Number(row.frequentCompleted || 0)) },
      {
        name: '问题数',
        type: 'line',
        yAxisIndex: 1,
        symbol: 'diamond',
        symbolSize: 8,
        lineStyle: { width: 2, type: 'dashed' },
        data: rows.map((row) => Number(row.routineAbnormal || 0) + Number(row.frequentAbnormal || 0) + Number(row.frequentWarning || 0) + Number(row.frequentMissing || 0))
      }
    ]
  }
})

const statusOption = computed(() => {
  const palette = chartPalette()
  const colors = (rows) => rows.map((row) => ({
    name: row.name,
    value: row.value,
    status: row.status,
    itemStyle: { color: healthStatusColor(row.status, palette) }
  }))
  const total = scopeRows.value.length
  return {
    animation: false,
    aria: { enabled: true },
    tooltip: { trigger: 'item', formatter: '{a}<br/>{b}：{c}（{d}%）', ...tooltipStyle(palette) },
    legend: { bottom: 8, left: 'center', textStyle: { color: palette.muted }, itemWidth: 10, itemHeight: 10 },
    title: { text: String(total), subtext: '健康范围', left: 'center', top: '37%', textStyle: { color: palette.heading, fontSize: 27 }, subtextStyle: { color: palette.muted, fontSize: 11 } },
    series: [
      {
        name: '现场',
        type: 'pie',
        radius: ['30%', '47%'],
        center: ['50%', '44%'],
        label: { show: false },
        data: colors(siteStatusRows.value)
      },
      {
        name: '主平台',
        type: 'pie',
        radius: ['55%', '72%'],
        center: ['50%', '44%'],
        label: { color: palette.text, formatter: ({ name, value }) => Number(value) > 0 ? `${name}\n${value}` : '' },
        labelLine: { lineStyle: { color: palette.grid } },
        data: colors(platformStatusRows.value)
      }
    ]
  }
})

const scopeOption = computed(() => {
  const palette = chartPalette()
  const rows = [...scopeRows.value].reverse()
  return {
    animation: false,
    aria: { enabled: true },
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      formatter: (params = []) => {
        const row = rows[params[0]?.dataIndex]
        if (!row) return ''
        return `${escapeHtml(row.scopePath)}<br/>健康度：${normalizeHealthScore(row.healthScore)}%<br/>结论：${healthStatusLabel(row.resultStatus)}<br/>完成：${row.completedCount || 0} / ${row.expectedCount || 0}`
      },
      ...tooltipStyle(palette)
    },
    grid: { left: 182, right: 54, top: 18, bottom: 28 },
    xAxis: { type: 'value', min: 0, max: 100, axisLabel: { color: palette.muted, formatter: '{value}%' }, splitLine: { lineStyle: { color: palette.grid } } },
    yAxis: { type: 'category', data: rows.map((row) => row.chartName), axisLine: { show: false }, axisTick: { show: false }, axisLabel: { color: palette.text, width: 162, overflow: 'truncate' } },
    series: [{
      type: 'bar',
      barMaxWidth: 14,
      showBackground: true,
      backgroundStyle: { color: palette.subtle },
      label: { show: true, position: 'right', color: palette.muted, formatter: '{c}%' },
      data: rows.map((row) => ({ value: normalizeHealthScore(row.healthScore), itemStyle: { color: healthStatusColor(row.resultStatus, palette), borderRadius: [0, 4, 4, 0] } }))
    }]
  }
})

const planOption = computed(() => {
  const palette = chartPalette()
  const rows = [...planRows.value].reverse()
  return {
    animation: false,
    aria: { enabled: true },
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      formatter: (params = []) => {
        const row = rows[params[0]?.dataIndex]
        if (!row) return ''
        return `${escapeHtml(row.chartName)}<br/>完成度：${row.completionRate}%<br/>已完成：${row.completedCount} / ${row.expectedCount}<br/>异常：${row.abnormalCount || 0}，关注：${row.warningCount || 0}，缺失：${row.missingCount || 0}`
      },
      ...tooltipStyle(palette)
    },
    grid: { left: 166, right: 54, top: 18, bottom: 28 },
    xAxis: { type: 'value', min: 0, max: 100, axisLabel: { color: palette.muted, formatter: '{value}%' }, splitLine: { lineStyle: { color: palette.grid } } },
    yAxis: { type: 'category', data: rows.map((row) => row.chartName), axisLine: { show: false }, axisTick: { show: false }, axisLabel: { color: palette.text, width: 146, overflow: 'truncate' } },
    series: [{
      type: 'bar',
      barMaxWidth: 14,
      showBackground: true,
      backgroundStyle: { color: palette.subtle },
      label: { show: true, position: 'right', color: palette.muted, formatter: '{c}%' },
      data: rows.map((row) => ({ value: row.completionRate, itemStyle: { color: healthStatusColor(row.resultStatus, palette), borderRadius: [0, 4, 4, 0] } }))
    }]
  }
})

const issueOption = computed(() => {
  const palette = chartPalette()
  return {
    animation: false,
    aria: { enabled: true },
    tooltip: {
      trigger: 'item',
      formatter: ({ data }) => `${escapeHtml(data?.name || '未命名问题')}<br/>${escapeHtml(data?.detail || '暂无问题详情')}<br/>来源：${escapeHtml(data?.source || '巡检结果')}`,
      ...tooltipStyle(palette)
    },
    series: [{
      type: 'treemap',
      roam: false,
      nodeClick: false,
      breadcrumb: { show: false },
      label: { show: true, color: palette.text, overflow: 'truncate', formatter: '{b}' },
      upperLabel: { show: false },
      itemStyle: { borderColor: palette.surface, borderWidth: 3, gapWidth: 2 },
      data: issueRows.value.map((row) => ({
        name: row.chartName,
        value: row.chartValue,
        detail: row.issueDetail,
        source: row.sourceMode === 'FREQUENT' ? '计划健康' : '执行记录',
        itemStyle: { color: healthStatusColor(row.resultStatus, palette), opacity: 0.88 }
      }))
    }]
  }
})

const recordOption = computed(() => {
  const palette = chartPalette()
  const statuses = [healthStatusLabel(RESULT_SKIP), healthStatusLabel(RESULT_NORMAL), healthStatusLabel(RESULT_WARNING), healthStatusLabel(RESULT_ABNORMAL)]
  return {
    animation: false,
    aria: { enabled: true },
    tooltip: {
      trigger: 'item',
      formatter: ({ dataIndex }) => {
        const row = recordRows.value[dataIndex]
        if (!row) return ''
        return `${escapeHtml(row.chartName)}<br/>执行时间：${escapeHtml(row.inspectionTime || '-')}<br/>结果：${row.statusLabel}<br/>${escapeHtml(row.abnormalSummary || row.summary || '本次检测已完成')}`
      },
      ...tooltipStyle(palette)
    },
    grid: { left: 72, right: 30, top: 24, bottom: 48 },
    xAxis: { type: 'category', data: recordRows.value.map((_, index) => index), axisLine: { lineStyle: { color: palette.grid } }, axisTick: { show: false }, axisLabel: { color: palette.muted, formatter: (value) => recordRows.value[Number(value)]?.timeLabel || '-' } },
    yAxis: { type: 'category', data: statuses, axisLine: { show: false }, axisTick: { show: false }, axisLabel: { color: palette.text }, splitLine: { show: true, lineStyle: { color: palette.grid } } },
    series: [{
      type: 'scatter',
      symbolSize: 18,
      data: recordRows.value.map((row, index) => ({ value: [index, row.statusLabel], itemStyle: { color: healthStatusColor(row.resultStatus, palette) } }))
    }]
  }
})

function navigateModulePage(location) {
  window.location.assign(router.resolve(location).href)
}

function openOverview(extraQuery = {}) {
  navigateModulePage({ path: '/autoInspection/dashboard', query: { tab: 'dashboard', ...extraQuery } })
}

function openConfig() {
  navigateModulePage({ path: '/autoInspection/config', query: { tab: 'template' } })
}

function todayDateKey() {
  const today = new Date()
  return [today.getFullYear(), String(today.getMonth() + 1).padStart(2, '0'), String(today.getDate()).padStart(2, '0')].join('-')
}

function openScopeDetail(scope) {
  openOverview({
    date: todayDateKey(),
    scopeKey: scope.scopeKey,
    siteId: scope.siteId,
    mainPlatformId: scope.mainPlatformId || undefined,
    openSamples: ['2', '4'].includes(scope.resultStatus) ? '1' : undefined,
    resultStatus: ['2', '4'].includes(scope.resultStatus) ? scope.resultStatus : undefined
  })
}

function openPlanDetail(plan) {
  openOverview({ date: todayDateKey(), planId: plan.planId, openSamples: '1' })
}

function openIssue(item) {
  if (item.sourceMode === 'FREQUENT') {
    openOverview({ date: item.healthDate || todayDateKey(), planId: item.planId, openSamples: '1' })
  } else {
    openOverview({ recordId: item.recordId })
  }
}

function openRecord(record) {
  openOverview({ recordId: record.recordId })
}

function handleTrendClick({ dataIndex }) {
  const row = trendRows.value[dataIndex]
  if (row?.date) openOverview({ date: row.date })
}

function handleStatusClick({ seriesName, data }) {
  const status = data?.status
  const candidates = seriesName === '主平台'
    ? scopeRows.value.filter((row) => row.scopeType === 'MAIN_PLATFORM')
    : scopeRows.value.filter((row) => row.scopeType === 'SITE')
  const row = candidates.find((item) => item.resultStatus === status)
  if (row) openScopeDetail(row)
}

function handleScopeClick({ dataIndex }) {
  const rows = [...scopeRows.value].reverse()
  if (rows[dataIndex]) openScopeDetail(rows[dataIndex])
}

function handlePlanClick({ dataIndex }) {
  const rows = [...planRows.value].reverse()
  if (rows[dataIndex]) openPlanDetail(rows[dataIndex])
}

function handleIssueClick({ dataIndex }) {
  if (issueRows.value[dataIndex]) openIssue(issueRows.value[dataIndex])
}

function handleRecordClick({ dataIndex }) {
  if (recordRows.value[dataIndex]) openRecord(recordRows.value[dataIndex])
}
</script>

<style scoped lang="scss">
.inspection-cockpit {
  display: grid;
  gap: 12px;
  color: var(--app-text);
}

.cockpit-commandbar,
.cockpit-chart-panel {
  border: 1px solid var(--surface-border);
  border-radius: 8px;
  background: var(--surface-strong);
}

.cockpit-commandbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  min-height: 70px;
  padding: 13px 18px;
}

.cockpit-commandbar h1,
.cockpit-chart-panel__head h2 {
  margin: 0;
  color: var(--app-heading);
  letter-spacing: 0;
}

.cockpit-commandbar h1 {
  font-size: 20px;
}

.cockpit-commandbar p,
.cockpit-chart-panel__head span {
  margin: 4px 0 0;
  color: var(--app-muted);
  font-size: 12px;
}

.cockpit-commandbar__actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.cockpit-error-state {
  display: grid;
  grid-template-columns: 30px minmax(0, 1fr) auto;
  align-items: center;
  gap: 12px;
  min-height: 64px;
  padding: 10px 14px;
  border: 1px solid var(--el-color-danger-light-5);
  border-radius: 8px;
  background: var(--el-color-danger-light-9);
}

.cockpit-error-state > .el-icon {
  color: var(--el-color-danger);
  font-size: 22px;
}

.cockpit-error-state > div {
  display: grid;
  gap: 3px;
}

.cockpit-error-state strong {
  color: var(--app-heading);
  font-size: 13px;
}

.cockpit-error-state span {
  color: var(--app-text);
  font-size: 12px;
}

.cockpit-dashboard-grid {
  display: grid;
  grid-template-columns: repeat(12, minmax(0, 1fr));
  gap: 12px;
  min-width: 0;
}

.cockpit-chart-panel {
  min-width: 0;
  overflow: hidden;
}

.cockpit-chart-panel--trend { grid-column: span 8; }
.cockpit-chart-panel--status { grid-column: span 4; }
.cockpit-chart-panel--scope { grid-column: span 7; }
.cockpit-chart-panel--plan { grid-column: span 5; }
.cockpit-chart-panel--issues,
.cockpit-chart-panel--records { grid-column: span 6; }

.cockpit-chart-panel__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  min-height: 56px;
  padding: 10px 15px;
  border-bottom: 1px solid var(--surface-border);
}

.cockpit-chart-panel__head h2 {
  font-size: 15px;
}

.cockpit-chart {
  display: block;
  min-height: 300px;
}

.cockpit-chart--trend,
.cockpit-chart--status {
  height: 322px;
}

.cockpit-chart--scope,
.cockpit-chart--plan {
  height: 344px;
}

.cockpit-chart--issues,
.cockpit-chart--records {
  height: 304px;
}

@media (max-width: 1280px) {
  .cockpit-chart-panel--trend,
  .cockpit-chart-panel--scope { grid-column: span 7; }

  .cockpit-chart-panel--status,
  .cockpit-chart-panel--plan { grid-column: span 5; }
}

@media (prefers-reduced-motion: reduce) {
  .inspection-cockpit * {
    scroll-behavior: auto !important;
  }
}
</style>
