<template>
  <div ref="chartRef" class="ipam-dashboard-chart" role="img" :aria-label="ariaLabel" />
</template>

<script setup>
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as echarts from 'echarts'

const props = defineProps({
  option: { type: Object, required: true },
  ariaLabel: { type: String, default: 'IP分配统计图表' }
})
const emit = defineEmits(['chart-click'])

const chartRef = ref(null)
let chartInstance = null
let resizeObserver = null

function renderChart() {
  if (!chartRef.value) return
  if (!chartInstance) {
    chartInstance = echarts.init(chartRef.value)
    chartInstance.on('click', (params) => emit('chart-click', params))
  }
  chartInstance.setOption(props.option, { notMerge: true, lazyUpdate: true })
}

onMounted(() => {
  renderChart()
  resizeObserver = new ResizeObserver(() => chartInstance?.resize())
  resizeObserver.observe(chartRef.value)
})

watch(() => props.option, () => nextTick(renderChart), { deep: true })

onBeforeUnmount(() => {
  resizeObserver?.disconnect()
  chartInstance?.dispose()
  chartInstance = null
})
</script>

<style scoped>
.ipam-dashboard-chart {
  width: 100%;
  height: 100%;
  min-height: 280px;
}
</style>
