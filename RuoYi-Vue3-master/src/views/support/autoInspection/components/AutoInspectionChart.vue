<template>
  <div class="auto-inspection-chart">
    <div
      v-if="!empty"
      ref="chartRef"
      class="auto-inspection-chart__canvas"
      role="img"
      :aria-label="ariaLabel"
    />
    <el-empty
      v-else
      class="auto-inspection-chart__empty"
      :description="emptyDescription"
      :image-size="48"
    />
  </div>
</template>

<script setup>
import { nextTick, onActivated, onBeforeUnmount, onDeactivated, onMounted, ref, watch } from 'vue'
import * as echarts from 'echarts'
import { preserveCockpitChartView } from '../cockpitChartOptions.js'

const props = defineProps({
  option: { type: Object, default: () => ({}) },
  empty: { type: Boolean, default: false },
  emptyDescription: { type: String, default: '暂无可展示的数据' },
  ariaLabel: { type: String, default: '自动化巡检统计图表' },
  viewKey: { type: String, default: '' }
})

const emit = defineEmits(['chart-click'])
const chartRef = ref(null)
let chartInstance = null
let resizeObserver = null
let renderedViewKey = null
let renderFrame = null
let active = true

function renderChart() {
  if (!active || props.empty || !chartRef.value?.clientWidth || !chartRef.value.clientHeight) return
  const preserveView = chartInstance && renderedViewKey === props.viewKey
  const option = preserveView ? preserveCockpitChartView(props.option, chartInstance.getOption()) : props.option
  if (!chartInstance) {
    chartInstance = echarts.init(chartRef.value)
    chartInstance.on('click', (params) => emit('chart-click', params))
  }
  chartInstance.setOption(option, { notMerge: !preserveView, replaceMerge: ['series', 'dataZoom'], lazyUpdate: true })
  renderedViewKey = props.viewKey
  chartInstance.resize()
}

function disposeChart() {
  resizeObserver?.disconnect()
  if (renderFrame != null) cancelAnimationFrame(renderFrame)
  renderFrame = null
  chartInstance?.dispose()
  chartInstance = null
  renderedViewKey = null
}

function observeChart() {
  if (!chartRef.value || typeof ResizeObserver === 'undefined') return
  if (!resizeObserver) resizeObserver = new ResizeObserver(() => {
    if (!active || !chartRef.value?.clientWidth || !chartRef.value.clientHeight) return
    if (chartInstance) chartInstance.resize()
    else renderChart()
  })
  resizeObserver.disconnect()
  resizeObserver.observe(chartRef.value)
}

function queueRender() {
  if (props.empty) {
    disposeChart()
    return
  }
  nextTick(() => {
    if (!active) return
    if (renderFrame != null) cancelAnimationFrame(renderFrame)
    renderFrame = requestAnimationFrame(() => {
      renderFrame = null
      renderChart()
      observeChart()
    })
  })
}

watch(() => [props.option, props.empty, props.viewKey], queueRender, { deep: true, flush: 'post' })

onMounted(queueRender)
onActivated(() => {
  active = true
  queueRender()
})
onDeactivated(() => {
  active = false
  disposeChart()
})

onBeforeUnmount(() => {
  active = false
  disposeChart()
})
</script>

<style scoped>
.auto-inspection-chart,
.auto-inspection-chart__canvas {
  width: 100%;
  height: 100%;
  min-height: inherit;
}

.auto-inspection-chart__empty {
  display: grid;
  align-content: center;
  height: 100%;
  min-height: inherit;
}
</style>
