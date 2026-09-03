<template>
  <div class="plan-metric-dashboard">
    <el-form :inline="true" class="plan-metric-query" @submit.prevent>
      <el-form-item label="巡检计划">
        <el-select v-model="selectedPlanId" filterable class="metric-plan-select" placeholder="请选择计划" aria-label="统计巡检计划" append-to=".inspection-cockpit" @change="loadMetrics">
          <el-option v-for="plan in data.plans" :key="plan.planId" :value="plan.planId" :label="`${plan.planName}${plan.status === '1' ? '（暂停）' : ''}`" />
        </el-select>
      </el-form-item>
      <el-form-item label="时间范围">
        <el-segmented v-model="days" class="motion-segmented" :options="rangeOptions" aria-label="统计时间范围" @change="loadMetrics">
          <template #default="{ item }">
            <span class="motion-control-label">
              <svg-icon icon-class="date" class="motion-control-label__icon" />
              <span class="motion-control-label__text">{{ item.label }}</span>
            </span>
          </template>
        </el-segmented>
      </el-form-item>
      <el-form-item label="统计项目">
        <el-select v-model="selectedMetricKey" filterable class="metric-item-select" placeholder="暂无统计项目" aria-label="计划统计项目" append-to=".inspection-cockpit" @change="syncQuery">
          <el-option v-for="metric in data.metrics" :key="metric.metricKey" :value="metric.metricKey" :label="`${metric.metricName} · ${metric.unit || '未标注单位'}`" />
        </el-select>
      </el-form-item>
    </el-form>

    <el-alert v-if="error" :title="error" type="error" show-icon :closable="false">
      <el-button link type="primary" :disabled="loading" @click="loadMetrics">重新加载</el-button>
    </el-alert>
    <el-alert v-if="data.truncated" type="warning" show-icon :closable="false" :title="`当前统计仅包含最近${data.sampleLimit}条目标采样和执行记录。`" />

    <div class="plan-metric-context">
      <span class="plan-metric-context__scope">{{ selectedPlanScope }}</span>
      <span>{{ selectedMetric?.sampleCount || 0 }} 次采样 · {{ selectedMetric?.numericCount || 0 }} 次数值 · {{ comparisonLabel }}</span>
      <el-button link type="primary" :icon="View" :disabled="!selectedMetric?.latest?.recordId" @click="openRecord(selectedMetric.latest)">最新源记录</el-button>
    </div>

    <el-empty v-if="!loading && !error && !data.plans.length" description="暂无巡检计划" />
    <section v-else v-loading="loading" class="plan-metric-grid" aria-label="计划统计项目图表">
      <article class="cockpit-chart-panel metric-panel--trend">
        <header class="cockpit-chart-panel__head">
          <div class="cockpit-chart-panel__title"><el-icon><DataLine /></el-icon><h2>统计项目数值趋势</h2></div>
          <span>{{ selectedMetric?.unit || '未标注单位' }}</span>
        </header>
        <div class="metric-current-reading">
          <el-tooltip :content="formatMetricValue(selectedMetric?.latest?.value)" append-to=".inspection-cockpit">
            <strong>{{ formatMetricValue(selectedMetric?.latest?.value) }}</strong>
          </el-tooltip>
          <span>{{ selectedMetric?.targetName || '未选择项目' }}<small>{{ selectedMetric?.latest?.sampleTime || '暂无采样时间' }}</small></span>
        </div>
        <AutoInspectionChart class="cockpit-chart" :view-key="viewKey" :option="trendOption" :empty="!hasValues" empty-description="该项目在当前范围内没有数值采样" aria-label="计划统计项目数值时间趋势" @chart-click="openChartRecord" />
      </article>
      <article class="cockpit-chart-panel metric-panel--comparison">
        <header class="cockpit-chart-panel__head">
          <div class="cockpit-chart-panel__title"><el-icon><Histogram /></el-icon><h2>实测与上次对比</h2></div>
          <span>{{ selectedMetric?.unit || '' }}</span>
        </header>
        <AutoInspectionChart class="cockpit-chart" :view-key="viewKey" :option="comparisonOption" :empty="!hasComparison" empty-description="暂无可比较的数值" aria-label="最新采样与上次采样值对比" @chart-click="openChartRecord" />
      </article>
      <article class="cockpit-chart-panel metric-panel--status">
        <header class="cockpit-chart-panel__head">
          <div class="cockpit-chart-panel__title"><el-icon><PieChart /></el-icon><h2>项目判定分布</h2></div>
        </header>
        <AutoInspectionChart class="cockpit-chart" :view-key="viewKey" :option="statusOption" :empty="!selectedMetric?.sampleCount" empty-description="暂无判定记录" aria-label="当前统计项目采样判定状态分布" />
      </article>
      <article class="cockpit-chart-panel metric-panel--change">
        <header class="cockpit-chart-panel__head">
          <div class="cockpit-chart-panel__title"><el-icon><TrendCharts /></el-icon><h2>项目变化趋势</h2></div>
          <span>{{ selectedMetric?.unit || '' }}</span>
        </header>
        <AutoInspectionChart class="cockpit-chart" :view-key="viewKey" :option="changeOption" :empty="!hasChanges" empty-description="暂无可比变化量，首次采样仅建立基线" aria-label="统计项目相邻采样变化量趋势" @chart-click="openChartRecord" />
      </article>
      <article class="cockpit-chart-panel metric-panel--range">
        <header class="cockpit-chart-panel__head">
          <div class="cockpit-chart-panel__title"><el-icon><DataAnalysis /></el-icon><h2>区间极值统计</h2></div>
          <span>{{ selectedMetric?.unit || '' }}</span>
        </header>
        <AutoInspectionChart class="cockpit-chart" :view-key="viewKey" :option="rangeOption" :empty="!hasValues" empty-description="暂无数值统计" aria-label="当前项目最小值平均值最大值" />
      </article>
      <article class="cockpit-chart-panel metric-panel--duration">
        <header class="cockpit-chart-panel__head">
          <div class="cockpit-chart-panel__title"><el-icon><Timer /></el-icon><h2>计划执行耗时</h2></div>
          <span>{{ durationPoints.length }} 次执行 · ms</span>
        </header>
        <AutoInspectionChart class="cockpit-chart" :view-key="`${data.planId}:${days}`" :option="durationOption" :empty="!durationPoints.length" empty-description="暂无计划耗时记录" aria-label="当前计划执行耗时趋势" @chart-click="openChartRecord" />
      </article>
    </section>
  </div>
</template>

<script setup name="PlanMetricDashboard">
import { DataAnalysis, DataLine, Histogram, PieChart, Timer, TrendCharts, View } from '@element-plus/icons-vue'
import { getAutoInspectionMetrics } from '@/api/support/autoInspection'
import AutoInspectionChart from './AutoInspectionChart.vue'
import {
  buildMetricChangeOption, buildMetricComparisonOption, buildMetricDurationOption, buildMetricRangeOption,
  buildMetricStatusOption, buildMetricTrendOption, comparisonValues, formatMetricValue, metricExecutionPoints,
  metricNumber, metricPoints, normalizeMetricDashboard
} from '../planMetricsPresentation.js'

const props = defineProps({
  palette: { type: Object, required: true },
  animate: { type: Boolean, default: false },
  autoRefresh: { type: Boolean, default: true },
  active: { type: Boolean, default: true }
})
const emit = defineEmits(['open-record', 'loading-change', 'updated'])
const route = useRoute()
const router = useRouter()
const parsePlanId = (value) => Number.isSafeInteger(Number(value)) && Number(value) > 0 ? Number(value) : null
const parseDays = (value) => [1, 7, 30].includes(Number(value)) ? Number(value) : 7
const selectedPlanId = ref(parsePlanId(route.query.metricPlanId))
const days = ref(parseDays(route.query.metricDays))
const selectedMetricKey = ref(String(route.query.metricKey || ''))
const data = ref(normalizeMetricDashboard())
const loading = ref(false)
const error = ref('')
const rangeOptions = [{ label: '今天', value: 1 }, { label: '近7天', value: 7 }, { label: '近30天', value: 30 }]
let snapshotDays = 7
let requestSequence = 0
let refreshTimer = null
let mounted = false
let pageActive = true

const selectedMetric = computed(() => data.value.metrics.find((metric) => metric.metricKey === selectedMetricKey.value) || null)
const selectedPlan = computed(() => data.value.plans.find((plan) => plan.planId === selectedPlanId.value))
const selectedPlanScope = computed(() => selectedPlan.value
  ? [selectedPlan.value.siteName, selectedPlan.value.mainPlatformName].filter(Boolean).join(' / ') || '待归属计划'
  : '未选择计划')
const comparisonLabel = computed(() => ({ DAY: '按天累计', HOUR: '按小时累计', CONTINUOUS: '连续累计' }[selectedMetric.value?.comparisonScope] || '未选择统计项目'))
const points = computed(() => metricPoints(selectedMetric.value))
const hasValues = computed(() => points.value.some((point) => point.valueNumber !== null))
const hasChanges = computed(() => points.value.some((point) => point.changeNumber !== null))
const hasComparison = computed(() => comparisonValues(selectedMetric.value).some((value) => metricNumber(value) !== null))
const durationPoints = computed(() => metricExecutionPoints(data.value.executions))
const viewKey = computed(() => `${data.value.planId}:${days.value}:${selectedMetricKey.value}`)
const trendOption = computed(() => buildMetricTrendOption(selectedMetric.value, props.palette, props.animate))
const comparisonOption = computed(() => buildMetricComparisonOption(selectedMetric.value, props.palette, props.animate))
const changeOption = computed(() => buildMetricChangeOption(selectedMetric.value, props.palette, props.animate))
const rangeOption = computed(() => buildMetricRangeOption(selectedMetric.value, props.palette, props.animate))
const statusOption = computed(() => buildMetricStatusOption(selectedMetric.value, props.palette, props.animate))
const durationOption = computed(() => buildMetricDurationOption(data.value.executions, props.palette, props.animate))

async function loadMetrics() {
  if (!mounted || !props.active || !pageActive) return
  const sequence = ++requestSequence
  const query = { planId: selectedPlanId.value || undefined, days: days.value }
  const sameSnapshot = data.value.planId === selectedPlanId.value && snapshotDays === days.value
  if (!sameSnapshot) {
    data.value = normalizeMetricDashboard({ plans: data.value.plans })
    emit('updated', '')
  }
  loading.value = true
  error.value = ''
  emit('loading-change', true)
  try {
    const response = await getAutoInspectionMetrics(query)
    if (sequence !== requestSequence || !mounted) return
    const result = normalizeMetricDashboard(response.data || {})
    data.value = result
    selectedPlanId.value = result.planId
    snapshotDays = query.days
    if (!result.metrics.some((metric) => metric.metricKey === selectedMetricKey.value)) selectedMetricKey.value = result.metrics[0]?.metricKey || ''
    emit('updated', result.generatedTime)
    syncQuery()
  } catch (failure) {
    if (sequence !== requestSequence || !mounted) return
    error.value = failure?.response?.status === 404
      ? '计划指标接口尚未就绪，请确认本地后端已更新。'
      : sameSnapshot && data.value.generatedTime ? '指标刷新失败，当前保留上次成功的数据。' : '计划指标加载失败，请检查后端连接和巡检查询权限。'
  } finally {
    if (sequence === requestSequence && mounted) {
      loading.value = false
      emit('loading-change', false)
    }
  }
}

function syncQuery() {
  if (!props.active || !mounted) return
  router.replace({ query: {
    ...route.query, view: 'metrics', metricPlanId: selectedPlanId.value || undefined,
    metricDays: days.value === 7 ? undefined : days.value, metricKey: selectedMetricKey.value || undefined
  } })
}

function openRecord(point) { if (point?.recordId) emit('open-record', point.recordId) }
function openChartRecord({ data: point }) { openRecord(point) }

function stopRefresh() {
  if (refreshTimer != null) clearInterval(refreshTimer)
  refreshTimer = null
}

function syncRefresh() {
  stopRefresh()
  if (!mounted || !props.autoRefresh || !props.active || !pageActive) return
  refreshTimer = setInterval(() => {
    if (!loading.value && document.visibilityState === 'visible') loadMetrics()
  }, 30000)
}

function onVisibility() {
  if (props.autoRefresh && props.active && !loading.value && document.visibilityState === 'visible') loadMetrics()
}

watch(() => [props.active, props.autoRefresh], () => {
  syncRefresh()
  if (props.active && !loading.value && !data.value.generatedTime) loadMetrics()
})
watch(() => [route.query.metricPlanId, route.query.metricDays], ([plan, range]) => {
  if (!props.active) return
  const nextPlan = parsePlanId(plan)
  const nextDays = parseDays(range)
  if (nextPlan === selectedPlanId.value && nextDays === days.value) return
  selectedPlanId.value = nextPlan
  days.value = nextDays
  loadMetrics()
})
watch(() => route.query.metricKey, (key) => {
  if (data.value.metrics.some((metric) => metric.metricKey === key)) selectedMetricKey.value = key
})
onMounted(() => { mounted = true; loadMetrics(); syncRefresh(); document.addEventListener('visibilitychange', onVisibility) })
onActivated(() => { pageActive = true; syncRefresh() })
onDeactivated(() => { pageActive = false; stopRefresh() })
onBeforeUnmount(() => { mounted = false; requestSequence++; stopRefresh(); document.removeEventListener('visibilitychange', onVisibility) })
defineExpose({ refresh: loadMetrics })
</script>

<style scoped lang="scss">
.plan-metric-dashboard { display: flex; flex-direction: column; gap: 10px; height: 100%; min-height: 0; }
.plan-metric-query { display: flex; flex-wrap: wrap; gap: 8px 16px; flex: 0 0 auto; }
.plan-metric-query :deep(.el-form-item) { margin: 0; }
.metric-plan-select { width: 270px; }
.metric-item-select { width: 310px; }
.plan-metric-context { display: flex; align-items: center; flex-wrap: wrap; gap: 8px 20px; flex: 0 0 auto; min-height: 26px; color: var(--app-muted); font-size: 12px; }
.plan-metric-context__scope { color: var(--app-text); margin-right: auto; }
.plan-metric-grid { display: grid; flex: 1; min-height: 0; grid-template-columns: repeat(12, minmax(0, 1fr)); grid-template-rows: repeat(2, minmax(220px, 1fr)); gap: 12px; }
.metric-panel--trend { grid-column: span 6; }
.metric-panel--comparison, .metric-panel--status { grid-column: span 3; }
.metric-panel--change, .metric-panel--range, .metric-panel--duration { grid-column: span 4; }
.metric-current-reading { display: flex; align-items: center; gap: 14px; padding: 8px 14px 0; min-height: 46px; flex: 0 0 auto; }
.metric-current-reading strong { max-width: 48%; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; color: var(--cockpit-accent); font-size: 24px; font-weight: 600; font-variant-numeric: tabular-nums; }
.metric-current-reading > span { min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; color: var(--app-text); font-size: 12px; }
.metric-current-reading small { display: block; margin-top: 3px; color: var(--app-muted); font-size: 11px; }
@media (max-width: 1280px) {
  .plan-metric-dashboard { height: auto; }
  .plan-metric-grid { flex: auto; grid-template-rows: repeat(3, 310px); }
  .metric-panel--trend { grid-column: span 8; }
  .metric-panel--status { grid-column: 9 / span 4; grid-row: 1; }
  .metric-panel--comparison, .metric-panel--change, .metric-panel--range, .metric-panel--duration { grid-column: span 6; }
}
@media (max-width: 800px) {
  .plan-metric-query { flex-direction: column; }
  .plan-metric-query :deep(.el-form-item__content) { min-width: 0; }
  .metric-plan-select, .metric-item-select { width: 100%; }
  .plan-metric-grid { grid-template-columns: minmax(0, 1fr); grid-template-rows: repeat(6, 320px); }
  .metric-panel--trend, .metric-panel--status, .metric-panel--comparison, .metric-panel--change, .metric-panel--range, .metric-panel--duration { grid-column: 1; grid-row: auto; }
  .metric-current-reading { gap: 8px; }
  .metric-current-reading strong { font-size: 20px; }
}
</style>
