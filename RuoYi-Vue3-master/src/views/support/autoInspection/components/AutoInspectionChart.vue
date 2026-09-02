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
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as echarts from 'echarts'

const props = defineProps({
  option: { type: Object, default: () => ({}) },
  empty: { type: Boolean, default: false },
  emptyDescription: { type: String, default: '暂无可展示的数据' },
  ariaLabel: { type: String, default: '自动化巡检统计图表' }
})

const emit = defineEmits(['chart-click'])
const chartRef = ref(null)
let chartInstance = null
let resizeObserver = null

function renderChart() {
  if (props.empty || !chartRef.value) return
  if (!chartInstance) {
    chartInstance = echarts.init(chartRef.value)
    chartInstance.on('click', (params) => emit('chart-click', params))
  }
  chartInstance.setOption(props.option, { notMerge: true, lazyUpdate: true })
  chartInstance.resize()
}

function disposeChart() {
  chartInstance?.dispose()
  chartInstance = null
}

function observeChart() {
  if (!chartRef.value || typeof ResizeObserver === 'undefined') return
  if (!resizeObserver) resizeObserver = new ResizeObserver(() => chartInstance?.resize())
  resizeObserver.disconnect()
  resizeObserver.observe(chartRef.value)
}

watch(() => [props.option, props.empty], () => {
  if (props.empty) {
    disposeChart()
    return
  }
  nextTick(() => {
    renderChart()
    observeChart()
  })
}, { deep: true })

onMounted(() => {
  renderChart()
  observeChart()
})

onBeforeUnmount(() => {
  resizeObserver?.disconnect()
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
