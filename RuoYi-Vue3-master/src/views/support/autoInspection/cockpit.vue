<template>
  <!--
    THESIS: one daily health ledger joins routine runs and frequent samples instead of presenting two dashboards.
    OWN-WORLD: restrained operations surfaces, ruled status bands, semantic state color, Element Plus controls, ECharts evidence.
    STORY: read today's conclusion, trace the seven-day change, identify affected plans, then open the exact issue or record.
    FIRST VIEWPORT: compact command bar, combined health gauge, routine and frequent facts, followed immediately by the shared timeline.
    FORM: unified health ledger, assigned surface candidate 4, seed 04a6e6a4.
    FINISH: unreviewed and undocumented is unfinished; this build ends with the finish review, the verdict, and DESIGN.md.
  -->
  <div class="app-container inspection-cockpit" data-design-seed="04a6e6a4">
    <header class="cockpit-commandbar">
      <div>
        <h1>自动化巡检驾驶舱</h1>
        <p>按现场和主平台查看今日巡检健康，数据更新于 {{ dashboard.generatedTime || '-' }}</p>
      </div>
      <div class="cockpit-commandbar__actions">
        <el-button class="motion-entry-action" data-motion-direction="forward" :icon="List" @click="openOverview()">巡检总览</el-button>
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

    <section v-loading="loading" class="cockpit-health-band cockpit-scope-band">
      <div class="cockpit-scope-band__title">
        <strong>今日健康范围</strong>
        <span>健康度按现场和主平台分别计算，不再用一个全局分数覆盖差异。</span>
      </div>
      <div class="cockpit-scope-metrics" aria-label="今日现场与主平台健康范围">
        <div class="cockpit-scope-metric">
          <el-icon><OfficeBuilding /></el-icon>
          <span><em>现场范围</em><strong>{{ scopeSummary.siteCount }}<small>个</small></strong></span>
        </div>
        <div class="cockpit-scope-metric">
          <el-icon><Monitor /></el-icon>
          <span><em>主平台范围</em><strong>{{ scopeSummary.platformCount }}<small>个</small></strong></span>
        </div>
        <button
          type="button"
          class="cockpit-scope-metric cockpit-scope-metric--action is-danger"
          :disabled="!scopeSummary.abnormalSiteCount"
          @click="openScopeIssue('2')"
        >
          <el-icon><WarningFilled /></el-icon>
          <span><em>异常现场</em><strong>{{ scopeSummary.abnormalSiteCount }}<small>个</small></strong></span>
          <el-icon class="cockpit-scope-metric__arrow"><ArrowRight /></el-icon>
        </button>
        <button
          type="button"
          class="cockpit-scope-metric cockpit-scope-metric--action is-warning"
          :disabled="!scopeSummary.warningSiteCount"
          @click="openScopeIssue('4')"
        >
          <el-icon><BellFilled /></el-icon>
          <span><em>关注现场</em><strong>{{ scopeSummary.warningSiteCount }}<small>个</small></strong></span>
          <el-icon class="cockpit-scope-metric__arrow"><ArrowRight /></el-icon>
        </button>
        <button
          type="button"
          class="cockpit-scope-metric cockpit-scope-metric--action is-warning"
          :disabled="!scopeSummary.unassignedPlanCount"
          @click="openUnassignedPlans"
        >
          <el-icon><Link /></el-icon>
          <span><em>待归属计划</em><strong>{{ scopeSummary.unassignedPlanCount }}<small>个</small></strong></span>
          <el-icon class="cockpit-scope-metric__arrow"><ArrowRight /></el-icon>
        </button>
      </div>
    </section>

    <section class="cockpit-chart-grid">
      <article class="cockpit-panel cockpit-panel--trend">
        <header class="cockpit-panel__head">
          <div>
            <h2>近七日执行趋势</h2>
            <span class="cockpit-panel__description">展示全部计划每日应执行、已完成和异常变化，现场差异请在下方健康排行查看</span>
          </div>
          <div class="cockpit-status-legend" aria-label="健康状态图例">
            <span><i class="is-normal"></i>健康</span>
            <span><i class="is-warning"></i>需关注</span>
            <span><i class="is-danger"></i>异常</span>
            <span><i class="is-idle"></i>未执行</span>
          </div>
        </header>
        <div v-if="hasTrendData" ref="trendChartRef" class="cockpit-chart cockpit-chart--trend" role="img" aria-label="近七日巡检健康与执行趋势图"></div>
        <el-empty v-else class="cockpit-chart-empty" description="近七日还没有可汇总的巡检数据" :image-size="48">
          <el-button class="motion-entry-action" data-motion-direction="forward" type="primary" plain :icon="Setting" @click="openConfig">配置巡检计划</el-button>
        </el-empty>
        <div v-if="hasTrendData" class="cockpit-day-track">
          <button
            v-for="day in dashboard.combinedTrend"
            :key="day.date"
            type="button"
            :class="`is-${day.status || '3'}`"
            :title="`${day.date}：${healthStatusLabel(day.status)}，健康度 ${normalizeHealthScore(day.healthScore)}%`"
          >
            <span>{{ formatShortDate(day.date) }}</span>
            <strong>{{ normalizeHealthScore(day.healthScore) }}%</strong>
          </button>
        </div>
      </article>

      <article class="cockpit-panel cockpit-panel--distribution">
        <header class="cockpit-panel__head">
          <div>
            <h2>今日健康构成</h2>
            <span class="cockpit-panel__description">按现场结论统计健康、关注、异常与未执行数量</span>
          </div>
        </header>
        <div v-if="hasPlanData" ref="distributionChartRef" class="cockpit-chart cockpit-chart--distribution" role="img" aria-label="今日现场健康状态构成图"></div>
        <el-empty v-else class="cockpit-chart-empty cockpit-chart-empty--small" description="今天没有需要执行的巡检计划" :image-size="48">
          <el-button class="motion-entry-action" data-motion-direction="forward" type="primary" plain :icon="Setting" @click="openConfig">新增执行计划</el-button>
        </el-empty>
        <div class="cockpit-coverage">
          <span><el-icon><OfficeBuilding /></el-icon><strong>{{ activePlanCount }}</strong><em>现场范围</em></span>
          <span><el-icon><CircleCheckFilled /></el-icon><strong>{{ checkedPlanCount }}</strong><em>已形成结论</em></span>
        </div>
      </article>
    </section>

    <section class="cockpit-chart-grid cockpit-chart-grid--secondary">
      <article class="cockpit-panel cockpit-panel--scope-ranking">
        <header class="cockpit-panel__head">
          <div>
            <h2>现场与主平台健康排行</h2>
            <span class="cockpit-panel__description">优先展示异常和低健康度范围，点击图形可进入对应明细</span>
          </div>
          <el-tag v-if="scopeChartRows.length" effect="plain" type="info">{{ scopeChartRows.length }} 个范围</el-tag>
        </header>
        <div v-if="hasScopeChartData" ref="scopeRankChartRef" class="cockpit-chart cockpit-chart--scope-ranking" role="img" aria-label="现场与主平台健康度排行图"></div>
        <el-empty v-else class="cockpit-chart-empty cockpit-chart-empty--ranking" description="今天暂无现场或主平台健康数据" :image-size="48" />
      </article>

      <article class="cockpit-panel cockpit-panel--completion">
        <header class="cockpit-panel__head">
          <div>
            <h2>计划执行完成度</h2>
            <span class="cockpit-panel__description">已完成与待执行数量对照，点击计划可查看当天执行结果</span>
          </div>
          <el-tag v-if="planCompletionRows.length" effect="plain" type="info">{{ planCompletionRows.length }} 个计划</el-tag>
        </header>
        <div v-if="hasPlanCompletionData" ref="planCompletionChartRef" class="cockpit-chart cockpit-chart--completion" role="img" aria-label="巡检计划执行完成度图"></div>
        <el-empty v-else class="cockpit-chart-empty cockpit-chart-empty--ranking" description="今天暂无计划执行数据" :image-size="48" />
      </article>
    </section>

    <section class="cockpit-panel cockpit-plan-panel">
      <header class="cockpit-panel__head cockpit-panel__head--controls">
        <div>
          <h2>现场与主平台明细</h2>
          <span class="cockpit-panel__description">图表用于值守判断，展开明细后可按现场、主平台、计划或模板检索取证</span>
        </div>
        <div class="cockpit-plan-filters">
          <el-input v-if="scopeTableVisible" v-model="planKeyword" clearable :prefix-icon="Search" placeholder="搜索现场、主平台、计划或模板" />
          <el-button :icon="List" @click="scopeTableVisible = !scopeTableVisible">
            {{ scopeTableVisible ? '收起明细' : '展开明细' }}
          </el-button>
        </div>
      </header>
      <el-collapse-transition>
      <div v-show="scopeTableVisible" class="cockpit-plan-table-wrap">
      <el-table :data="filteredScopeHealth" row-key="scopeKey" default-expand-all class="cockpit-plan-table" empty-text="今天没有已归属的巡检计划">
        <el-table-column label="现场 / 主平台" min-width="220">
          <template #default="scope">
            <div class="cockpit-plan-name">
              <strong>{{ scope.row.scopeName }}</strong>
              <span>{{ scope.row.scopeType === 'SITE' ? '现场健康' : `所属现场：${scope.row.siteName}` }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="104" align="center">
          <template #default="scope">
            <el-tag :type="healthStatusType(scope.row.resultStatus)" effect="plain">
              {{ healthStatusLabel(scope.row.resultStatus) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="巡检计划" min-width="240" show-overflow-tooltip>
          <template #default="scope">{{ scope.row.plans.map((plan) => plan.planName || '未命名计划').join('、') || '未配置计划' }}</template>
        </el-table-column>
        <el-table-column label="健康度" width="190">
          <template #default="scope">
            <div class="cockpit-health-cell">
              <el-progress
                :percentage="normalizeHealthScore(scope.row.healthScore)"
                :stroke-width="7"
                :show-text="false"
                :color="statusColor(scope.row.resultStatus)"
              />
              <strong>{{ normalizeHealthScore(scope.row.healthScore) }}%</strong>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="完成情况" width="140" align="center">
          <template #default="scope">{{ scope.row.completedCount || 0 }} / {{ scope.row.expectedCount || 0 }}</template>
        </el-table-column>
        <el-table-column label="当前结论" prop="issueSummary" min-width="240" show-overflow-tooltip>
          <template #default="scope">{{ scope.row.issueSummary || '当前未记录异常' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="90" fixed="right" align="center">
          <template #default="scope">
            <el-button link type="primary" :icon="View" @click="openScopeDetail(scope.row)">查看</el-button>
          </template>
        </el-table-column>
      </el-table>
      </div>
      </el-collapse-transition>
    </section>

    <section class="cockpit-bottom-grid">
      <article class="cockpit-panel cockpit-issue-panel">
        <header class="cockpit-panel__head">
          <div>
            <h2>待处理问题</h2>
            <span class="cockpit-panel__description">计划异常、目标异常与缺失执行统一排列</span>
          </div>
          <el-button link type="primary" @click="openOverview()">查看全部记录</el-button>
        </header>
        <el-empty v-if="!dashboard.latestIssues.length" description="今天暂未发现需要处理的问题" :image-size="54" />
        <div v-else class="cockpit-issue-list">
          <button v-for="item in dashboard.latestIssues" :key="issueKey(item)" type="button" @click="openIssue(item)">
            <span class="cockpit-status-marker" :class="`is-${item.resultStatus || '3'}`"></span>
            <div>
              <strong>{{ item.issueTitle || '未命名问题' }}</strong>
              <p>{{ item.issueDetail || '暂无问题详情' }}</p>
            </div>
            <em>{{ item.sourceMode === 'FREQUENT' ? '计划健康' : '执行记录' }} · {{ item.inspectionTime || '今日' }}</em>
            <el-icon><ArrowRight /></el-icon>
          </button>
        </div>
      </article>

      <article class="cockpit-panel cockpit-recent-panel">
        <header class="cockpit-panel__head">
          <div>
            <h2>最近执行记录</h2>
            <span class="cockpit-panel__description">用于快速回到完整步骤和目标明细</span>
          </div>
        </header>
        <el-empty v-if="!dashboard.recentRecords.length" description="今天暂无执行记录" :image-size="54" />
        <div v-else class="cockpit-recent-list">
          <button v-for="record in dashboard.recentRecords" :key="record.recordId" type="button" @click="openRecord(record)">
            <span class="cockpit-status-marker" :class="`is-${record.resultStatus || '3'}`"></span>
            <div>
              <strong>{{ record.templateName || '未命名模板' }}</strong>
              <small>{{ record.planName || '手动执行' }}</small>
            </div>
            <em>{{ record.inspectionTime || '-' }}</em>
          </button>
        </div>
      </article>
    </section>
  </div>
</template>

<script setup name="SupportAutoInspectionCockpit">
import * as echarts from 'echarts'
import {
  ArrowRight,
  BellFilled,
  CircleCheckFilled,
  Link,
  List,
  Monitor,
  OfficeBuilding,
  Refresh,
  Search,
  Setting,
  View,
  Warning,
  WarningFilled
} from '@element-plus/icons-vue'
import { getAutoInspectionDashboard } from '@/api/support/autoInspection'
import useSettingsStore from '@/store/modules/settings'
import {
  RESULT_SKIP,
  buildCurrentStatusDistribution,
  buildPlanCompletionRows,
  buildScopeHealthChartRows,
  filterScopeHealth,
  formatShortDate,
  groupPlanHealthByScope,
  healthStatusColor,
  healthStatusLabel,
  healthStatusType,
  normalizeCockpitDashboard,
  normalizeHealthScore,
  summarizeScopeHealth
} from './cockpitPresentation'

const router = useRouter()
const settingsStore = useSettingsStore()
const loading = ref(false)
const dashboardError = ref('')
const dashboard = ref(normalizeCockpitDashboard())
const planKeyword = ref('')
const scopeTableVisible = ref(false)
const trendChartRef = ref(null)
const distributionChartRef = ref(null)
const scopeRankChartRef = ref(null)
const planCompletionChartRef = ref(null)
const charts = {}

const scopeHealth = computed(() => groupPlanHealthByScope(dashboard.value.currentPlanHealth))
const scopeSummary = computed(() => summarizeScopeHealth(scopeHealth.value.sites, scopeHealth.value.unassigned))
const filteredScopeHealth = computed(() => filterScopeHealth(scopeHealth.value.sites, planKeyword.value))
const statusDistribution = computed(() => buildCurrentStatusDistribution(scopeHealth.value.sites))
const scopeChartRows = computed(() => buildScopeHealthChartRows(scopeHealth.value.sites, 10))
const planCompletionRows = computed(() => buildPlanCompletionRows(dashboard.value.currentPlanHealth, 8))
const activePlanCount = computed(() => scopeHealth.value.sites.length)
const checkedPlanCount = computed(() => scopeHealth.value.sites.filter((row) => row.resultStatus !== RESULT_SKIP).length)
const hasPlanData = computed(() => activePlanCount.value > 0)
const hasScopeChartData = computed(() => scopeChartRows.value.length > 0)
const hasPlanCompletionData = computed(() => planCompletionRows.value.length > 0)
const hasTrendData = computed(() => dashboard.value.combinedTrend.some((row) => (
  Number(row.frequentExpected || 0)
) > 0))

watch(() => settingsStore.isDark, () => nextTick(renderCharts))
watch([statusDistribution, scopeChartRows, planCompletionRows], () => nextTick(renderCharts), { deep: true })

onMounted(() => {
  window.addEventListener('resize', resizeCharts)
  loadDashboard()
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', resizeCharts)
  Object.values(charts).forEach((chart) => chart?.dispose())
})

function loadDashboard() {
  loading.value = true
  dashboardError.value = ''
  return getAutoInspectionDashboard()
    .then((res) => {
      dashboard.value = normalizeCockpitDashboard(res.data || {})
      nextTick(renderCharts)
    })
    .catch(() => {
      dashboard.value = normalizeCockpitDashboard()
      dashboardError.value = '请检查后端服务、登录状态和巡检查询权限后重试。'
      nextTick(renderCharts)
    })
    .finally(() => { loading.value = false })
}

function readThemeToken(name, fallback) {
  const value = getComputedStyle(document.documentElement).getPropertyValue(name).trim()
  return value || fallback
}

function chartPalette() {
  return {
    heading: readThemeToken('--app-heading', settingsStore.isDark ? '#f4f8ff' : '#17314d'),
    text: readThemeToken('--app-text', settingsStore.isDark ? '#d8e2f0' : '#35506d'),
    muted: readThemeToken('--app-muted', settingsStore.isDark ? '#91a1b6' : '#6d8298'),
    grid: readThemeToken('--chart-grid', settingsStore.isDark ? '#263547' : '#e4eaf1'),
    normal: readThemeToken('--health-normal', '#2f9b73'),
    warning: readThemeToken('--health-warning', '#c88824'),
    danger: readThemeToken('--health-danger', '#d55353'),
    idle: readThemeToken('--health-idle', '#8a9aad'),
    primary: readThemeToken('--el-color-primary', '#409eff')
  }
}

function ensureChart(key, element) {
  if (!element) return null
  if (!charts[key] || charts[key].isDisposed()) charts[key] = echarts.init(element)
  return charts[key]
}

function renderCharts() {
  renderTrendChart()
  renderDistributionChart()
  renderScopeRankChart()
  renderPlanCompletionChart()
}

function renderTrendChart() {
  if (!hasTrendData.value) {
    charts.trend?.clear()
    return
  }
  const chart = ensureChart('trend', trendChartRef.value)
  if (!chart) return
  const palette = chartPalette()
  const rows = dashboard.value.combinedTrend
  chart.setOption({
    animation: false,
    color: [palette.primary, palette.normal, palette.warning],
    tooltip: { trigger: 'axis', confine: true, backgroundColor: readThemeToken('--surface-strong', '#fff'), borderColor: palette.grid, textStyle: { color: palette.text } },
    legend: { top: 0, right: 0, textStyle: { color: palette.muted }, itemWidth: 12, itemHeight: 8 },
    grid: { left: 42, right: 44, top: 36, bottom: 30 },
    xAxis: { type: 'category', data: rows.map((row) => formatShortDate(row.date)), axisLine: { lineStyle: { color: palette.grid } }, axisLabel: { color: palette.muted }, axisTick: { show: false } },
    yAxis: [
      { type: 'value', min: 0, max: 100, axisLabel: { color: palette.muted, formatter: '{value}%' }, splitLine: { lineStyle: { color: palette.grid } } },
      { type: 'value', minInterval: 1, axisLabel: { color: palette.muted }, splitLine: { show: false } }
    ],
    series: [
      { name: '按计划健康度', type: 'line', yAxisIndex: 0, smooth: 0.25, symbolSize: 7, lineStyle: { width: 3 }, data: rows.map((row) => normalizeHealthScore(row.healthScore)) },
      { name: '应执行', type: 'bar', yAxisIndex: 1, barMaxWidth: 18, itemStyle: { color: palette.warning, opacity: 0.58 }, data: rows.map((row) => Number(row.frequentExpected || 0)) },
      { name: '已完成', type: 'bar', yAxisIndex: 1, barMaxWidth: 18, itemStyle: { color: palette.normal, opacity: 0.72 }, data: rows.map((row) => Number(row.frequentCompleted || 0)) }
    ]
  }, true)
}

function renderDistributionChart() {
  if (!hasPlanData.value) {
    charts.distribution?.clear()
    return
  }
  const chart = ensureChart('distribution', distributionChartRef.value)
  if (!chart) return
  const palette = chartPalette()
  const rows = statusDistribution.value
  chart.setOption({
    animation: false,
    tooltip: { trigger: 'item', confine: true, backgroundColor: readThemeToken('--surface-strong', '#fff'), borderColor: palette.grid, textStyle: { color: palette.text }, formatter: '{b}：{c} 个现场（{d}%）' },
    legend: { bottom: 8, left: 'center', textStyle: { color: palette.muted }, itemWidth: 10, itemHeight: 10 },
    title: { text: String(activePlanCount.value), subtext: '现场', left: 'center', top: '34%', textStyle: { color: palette.heading, fontSize: 26 }, subtextStyle: { color: palette.muted, fontSize: 11 } },
    series: [{
      type: 'pie',
      radius: ['54%', '75%'],
      center: ['50%', '43%'],
      avoidLabelOverlap: true,
      label: { color: palette.text, formatter: '{b}\n{c}' },
      labelLine: { lineStyle: { color: palette.grid } },
      data: rows.map((row) => ({ name: row.name, value: row.value, status: row.status, itemStyle: { color: healthStatusColor(row.status, palette) } }))
    }]
  }, true)
  chart.off('click')
  chart.on('click', ({ data }) => {
    if (data?.status) openScopeIssue(data.status)
  })
}

function renderScopeRankChart() {
  if (!hasScopeChartData.value) {
    charts.scopeRank?.clear()
    return
  }
  const chart = ensureChart('scopeRank', scopeRankChartRef.value)
  if (!chart) return
  const palette = chartPalette()
  const rows = [...scopeChartRows.value].reverse()
  chart.setOption({
    animation: false,
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      confine: true,
      backgroundColor: readThemeToken('--surface-strong', '#fff'),
      borderColor: palette.grid,
      textStyle: { color: palette.text },
      formatter: (params = []) => {
        const row = rows[params[0]?.dataIndex]
        if (!row) return ''
        return `${row.scopePath}<br/>健康度：${normalizeHealthScore(row.healthScore)}%<br/>状态：${healthStatusLabel(row.resultStatus)}<br/>完成：${row.completedCount || 0} / ${row.expectedCount || 0}`
      }
    },
    grid: { left: 174, right: 54, top: 18, bottom: 24 },
    xAxis: { type: 'value', min: 0, max: 100, axisLabel: { color: palette.muted, formatter: '{value}%' }, splitLine: { lineStyle: { color: palette.grid } } },
    yAxis: { type: 'category', data: rows.map((row) => row.chartName), axisLine: { show: false }, axisTick: { show: false }, axisLabel: { color: palette.text, width: 154, overflow: 'truncate' } },
    series: [{
      type: 'bar',
      barMaxWidth: 14,
      showBackground: true,
      backgroundStyle: { color: readThemeToken('--surface-subtle', palette.grid) },
      label: { show: true, position: 'right', color: palette.muted, formatter: '{c}%' },
      data: rows.map((row) => ({ value: normalizeHealthScore(row.healthScore), itemStyle: { color: healthStatusColor(row.resultStatus, palette), borderRadius: [0, 4, 4, 0] } }))
    }]
  }, true)
  chart.off('click')
  chart.on('click', ({ dataIndex }) => {
    const row = rows[dataIndex]
    if (row) openScopeDetail(row, ['2', '4'].includes(row.resultStatus) ? row.resultStatus : undefined)
  })
}

function renderPlanCompletionChart() {
  if (!hasPlanCompletionData.value) {
    charts.planCompletion?.clear()
    return
  }
  const chart = ensureChart('planCompletion', planCompletionChartRef.value)
  if (!chart) return
  const palette = chartPalette()
  const rows = [...planCompletionRows.value].reverse()
  chart.setOption({
    animation: false,
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      confine: true,
      backgroundColor: readThemeToken('--surface-strong', '#fff'),
      borderColor: palette.grid,
      textStyle: { color: palette.text },
      formatter: (params = []) => {
        const row = rows[params[0]?.dataIndex]
        if (!row) return ''
        return `${row.chartName}<br/>完成度：${row.completionRate}%<br/>已完成：${row.completedCount} / ${row.expectedCount}<br/>异常：${row.abnormalCount || 0}，关注：${row.warningCount || 0}，缺失：${row.missingCount || 0}`
      }
    },
    grid: { left: 148, right: 54, top: 18, bottom: 24 },
    xAxis: { type: 'value', min: 0, max: 100, axisLabel: { color: palette.muted, formatter: '{value}%' }, splitLine: { lineStyle: { color: palette.grid } } },
    yAxis: { type: 'category', data: rows.map((row) => row.chartName), axisLine: { show: false }, axisTick: { show: false }, axisLabel: { color: palette.text, width: 128, overflow: 'truncate' } },
    series: [{
      type: 'bar',
      barMaxWidth: 14,
      showBackground: true,
      backgroundStyle: { color: readThemeToken('--surface-subtle', palette.grid) },
      label: { show: true, position: 'right', color: palette.muted, formatter: '{c}%' },
      data: rows.map((row) => ({ value: row.completionRate, itemStyle: { color: healthStatusColor(row.resultStatus, palette), borderRadius: [0, 4, 4, 0] } }))
    }]
  }, true)
  chart.off('click')
  chart.on('click', ({ dataIndex }) => {
    const row = rows[dataIndex]
    if (row) openPlanDetail(row)
  })
}

function resizeCharts() {
  Object.values(charts).forEach((chart) => chart?.resize())
}

function statusColor(status) {
  return healthStatusColor(status, chartPalette())
}

function openOverview(extraQuery = {}) {
  navigateModulePage({ path: '/autoInspection/dashboard', query: { tab: 'dashboard', ...extraQuery } })
}

function openConfig() {
  navigateModulePage({ path: '/autoInspection/config', query: { tab: 'template' } })
}

function navigateModulePage(location) {
  window.location.assign(router.resolve(location).href)
}

function openRecord(record) {
  openOverview({ recordId: record.recordId })
}

function todayDateKey() {
  const today = new Date()
  return [today.getFullYear(), String(today.getMonth() + 1).padStart(2, '0'), String(today.getDate()).padStart(2, '0')].join('-')
}

function openScopeDetail(scope, resultStatus) {
  openOverview({
    date: todayDateKey(),
    scopeKey: scope.scopeKey,
    siteId: scope.siteId,
    mainPlatformId: scope.mainPlatformId || undefined,
    openSamples: resultStatus ? '1' : undefined,
    resultStatus: resultStatus || undefined
  })
}

function openPlanDetail(plan) {
  openOverview({
    date: todayDateKey(),
    planId: plan.planId,
    openSamples: '1'
  })
}

function openScopeIssue(status) {
  const scope = scopeHealth.value.sites.find((item) => item.resultStatus === status)
  if (scope) openScopeDetail(scope, status)
}

function openUnassignedPlans() {
  navigateModulePage({ path: '/autoInspection/config', query: { tab: 'plan' } })
}

function openIssue(item) {
  if (item.sourceMode === 'FREQUENT') {
    openOverview({ view: 'frequent', planId: item.planId, date: item.healthDate, openSamples: '1' })
  }
  else openOverview({ recordId: item.recordId })
}

function issueKey(item) {
  return [item.sourceMode, item.recordId, item.resultId, item.planId, item.issueTitle].filter(Boolean).join('-')
}
</script>

<style scoped lang="scss">
.inspection-cockpit {
  display: grid;
  gap: 14px;
  color: var(--app-text);
}

.cockpit-commandbar,
.cockpit-panel,
.cockpit-health-band {
  border: 1px solid var(--surface-border);
  border-radius: 8px;
  background: var(--surface-strong);
}

.cockpit-commandbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  min-height: 72px;
  padding: 14px 18px;
}

.cockpit-commandbar h1,
.cockpit-panel__head h2 {
  margin: 0;
  color: var(--app-heading);
  letter-spacing: 0;
}

.cockpit-commandbar h1 {
  font-size: 20px;
}

.cockpit-commandbar p,
.cockpit-panel__description {
  margin: 4px 0 0;
  color: var(--app-muted);
  font-size: 12px;
}

.cockpit-commandbar__actions,
.cockpit-plan-filters,
.cockpit-status-legend {
  display: flex;
  align-items: center;
  gap: 8px;
}

.cockpit-error-state {
  display: grid;
  grid-template-columns: 30px minmax(0, 1fr) auto;
  align-items: center;
  gap: 12px;
  min-height: 66px;
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

.cockpit-health-band {
  display: grid;
  grid-template-columns: 360px minmax(0, 1fr);
  min-height: 178px;
  overflow: hidden;
}

.cockpit-scope-band {
  grid-template-columns: 270px minmax(0, 1fr);
  min-height: 112px;
}

.cockpit-scope-band__title {
  display: grid;
  align-content: center;
  gap: 7px;
  padding: 18px 22px;
  border-right: 1px solid var(--surface-border);
}

.cockpit-scope-band__title strong {
  color: var(--app-heading);
  font-size: 17px;
}

.cockpit-scope-band__title span {
  color: var(--app-muted);
  font-size: 12px;
  line-height: 1.6;
}

.cockpit-scope-metrics {
  display: grid;
  grid-template-columns: repeat(5, minmax(128px, 1fr));
  min-width: 0;
}

.cockpit-scope-metric {
  display: grid;
  grid-template-columns: 28px minmax(0, 1fr);
  align-items: center;
  gap: 10px;
  min-width: 0;
  padding: 14px 16px;
  border: 0;
  border-right: 1px solid var(--surface-border);
  background: transparent;
  color: var(--app-text);
  font: inherit;
  text-align: left;
}

.cockpit-scope-metric:last-child {
  border-right: 0;
}

.cockpit-scope-metric > .el-icon {
  color: var(--app-muted);
  font-size: 21px;
}

.cockpit-scope-metric > span {
  display: grid;
  gap: 5px;
  min-width: 0;
}

.cockpit-scope-metric em,
.cockpit-scope-metric small {
  color: var(--app-muted);
  font-style: normal;
  font-weight: 500;
}

.cockpit-scope-metric em {
  overflow: hidden;
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.cockpit-scope-metric strong {
  color: var(--app-heading);
  font-size: 22px;
  line-height: 1;
}

.cockpit-scope-metric small {
  margin-left: 4px;
  font-size: 11px;
}

.cockpit-scope-metric--action {
  grid-template-columns: 28px minmax(0, 1fr) 16px;
  cursor: pointer;
  transition: background-color 160ms ease;
}

.cockpit-scope-metric--action:hover:not(:disabled),
.cockpit-scope-metric--action:focus-visible {
  background: var(--surface-muted);
}

.cockpit-scope-metric--action:focus-visible {
  outline: 2px solid var(--el-color-primary-light-5);
  outline-offset: -2px;
}

.cockpit-scope-metric--action:disabled {
  cursor: default;
  opacity: .62;
}

.cockpit-scope-metric--action.is-danger > .el-icon:first-child,
.cockpit-scope-metric--action.is-danger strong {
  color: var(--health-danger);
}

.cockpit-scope-metric--action.is-warning > .el-icon:first-child,
.cockpit-scope-metric--action.is-warning strong {
  color: var(--health-warning);
}

.cockpit-scope-metric__arrow {
  font-size: 13px !important;
}

.cockpit-chart-grid,
.cockpit-bottom-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.7fr) minmax(320px, .8fr);
  gap: 14px;
}

.cockpit-chart-grid--secondary {
  grid-template-columns: minmax(0, 1.15fr) minmax(0, 1fr);
}

.cockpit-panel {
  min-width: 0;
  overflow: hidden;
}

.cockpit-panel__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  min-height: 58px;
  padding: 12px 16px;
  border-bottom: 1px solid var(--surface-border);
}

.cockpit-panel__head h2 {
  font-size: 15px;
}

.cockpit-panel__head--controls {
  min-height: 66px;
}

.cockpit-plan-filters .el-input {
  width: 240px;
}

.cockpit-plan-mode :deep(.el-segmented__item) {
  min-width: 92px;
}

.cockpit-status-legend span {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  color: var(--app-muted);
  font-size: 11px;
}

.cockpit-status-legend i,
.cockpit-status-marker {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--health-idle);
}

.cockpit-status-legend .is-normal,
.cockpit-status-marker.is-1 { background: var(--health-normal); }
.cockpit-status-legend .is-warning,
.cockpit-status-marker.is-4 { background: var(--health-warning); }
.cockpit-status-legend .is-danger,
.cockpit-status-marker.is-2 { background: var(--health-danger); }
.cockpit-status-legend .is-idle,
.cockpit-status-marker.is-3 { background: var(--health-idle); }

.cockpit-chart {
  width: 100%;
}

.cockpit-chart--trend {
  height: 268px;
  padding: 8px 10px 0;
}

.cockpit-chart--distribution {
  height: 260px;
  padding: 10px 12px;
}

.cockpit-chart--scope-ranking,
.cockpit-chart--completion {
  height: 318px;
  padding: 8px 12px 4px;
}

.cockpit-chart-empty {
  display: grid;
  align-content: center;
  min-height: 268px;
}

.cockpit-chart-empty--small {
  min-height: 260px;
}

.cockpit-chart-empty--ranking {
  min-height: 318px;
}

.cockpit-day-track {
  display: grid;
  grid-template-columns: repeat(7, minmax(0, 1fr));
  border-top: 1px solid var(--surface-border);
}

.cockpit-day-track button {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 42px;
  padding: 0 10px;
  border: 0;
  border-right: 1px solid var(--surface-border);
  background: var(--surface-muted);
  color: var(--app-muted);
  cursor: default;
  font: inherit;
}

.cockpit-day-track button:last-child {
  border-right: 0;
}

.cockpit-day-track button strong {
  color: var(--app-heading);
  font-size: 12px;
}

.cockpit-day-track button.is-1 { box-shadow: inset 0 2px var(--health-normal); }
.cockpit-day-track button.is-4 { box-shadow: inset 0 2px var(--health-warning); }
.cockpit-day-track button.is-2 { box-shadow: inset 0 2px var(--health-danger); }
.cockpit-day-track button.is-3 { box-shadow: inset 0 2px var(--health-idle); }

.cockpit-coverage {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  border-top: 1px solid var(--surface-border);
}

.cockpit-coverage span {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 7px;
  padding: 12px;
  color: var(--app-muted);
  font-size: 12px;
}

.cockpit-coverage .el-icon {
  color: var(--app-muted);
  font-size: 16px;
}

.cockpit-coverage em {
  color: var(--app-muted);
  font-style: normal;
}

.cockpit-coverage span + span {
  border-left: 1px solid var(--surface-border);
}

.cockpit-coverage strong {
  color: var(--app-heading);
  font-size: 19px;
}

.cockpit-plan-table :deep(.el-table__cell) {
  padding: 9px 0;
}

.cockpit-plan-table-wrap {
  min-width: 0;
}

.cockpit-plan-table :deep(.el-table__expand-icon) {
  margin-right: 6px;
}

.cockpit-plan-name {
  display: grid;
  gap: 2px;
}

.cockpit-plan-name strong,
.cockpit-issue-list strong,
.cockpit-recent-list strong {
  color: var(--app-heading);
  font-size: 13px;
}

.cockpit-plan-name span,
.cockpit-recent-list small {
  color: var(--app-muted);
  font-size: 11px;
}

.cockpit-health-cell {
  display: grid;
  grid-template-columns: minmax(80px, 1fr) 46px;
  align-items: center;
  gap: 9px;
}

.cockpit-health-cell strong {
  color: var(--app-text);
  font-size: 12px;
  text-align: right;
}

.cockpit-issue-list,
.cockpit-recent-list {
  max-height: 304px;
  overflow-y: auto;
}

.cockpit-issue-list button,
.cockpit-recent-list button {
  display: grid;
  align-items: center;
  width: 100%;
  min-height: 58px;
  border: 0;
  border-bottom: 1px solid var(--surface-border);
  background: var(--surface-strong);
  color: var(--app-text);
  cursor: pointer;
  font: inherit;
  text-align: left;
}

.cockpit-issue-list button {
  grid-template-columns: 10px minmax(0, 1fr) 172px 20px;
  gap: 10px;
  padding: 10px 14px;
}

.cockpit-recent-list button {
  grid-template-columns: 10px minmax(0, 1fr) 154px;
  gap: 10px;
  padding: 9px 14px;
}

.cockpit-issue-list button:hover,
.cockpit-recent-list button:hover {
  background: var(--surface-hover);
}

.cockpit-issue-list button:last-child,
.cockpit-recent-list button:last-child {
  border-bottom: 0;
}

.cockpit-issue-list div,
.cockpit-recent-list div {
  display: grid;
  min-width: 0;
  gap: 2px;
}

.cockpit-issue-list p {
  overflow: hidden;
  margin: 0;
  color: var(--app-muted);
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.cockpit-issue-list em,
.cockpit-recent-list em {
  color: var(--app-muted);
  font-size: 11px;
  font-style: normal;
  text-align: right;
}

.cockpit-status-marker {
  flex: 0 0 auto;
}

@media (max-width: 900px) {
  .cockpit-panel__head--controls {
    align-items: stretch;
    flex-direction: column;
    gap: 10px;
  }

  .cockpit-plan-filters {
    width: 100%;
  }

  .cockpit-plan-filters .el-input {
    width: auto;
    min-width: 0;
    flex: 1 1 auto;
  }
}

@media (max-width: 620px) {
  .cockpit-plan-filters {
    align-items: stretch;
    flex-direction: column;
  }

  .cockpit-plan-mode,
  .cockpit-plan-filters .el-input {
    width: 100%;
  }
}

@media (prefers-reduced-motion: reduce) {
  .inspection-cockpit * {
    scroll-behavior: auto !important;
  }
}
</style>
