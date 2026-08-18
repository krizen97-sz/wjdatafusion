import { computed, ref } from 'vue'
import { getIpamDashboard, getScenarioSetting } from '@/api/ipam'
import {
  buildNetworkLoadMatrix,
  buildNetworkPressureDistribution,
  buildNetworkUsage,
  buildStationAllocation,
  buildStationEfficiency,
  buildStatusDistribution,
  normalizeDimensionRows,
  normalizeManufacturerRows
} from './dashboardRules.js'

const CHART_TEXT = '#9fb0c3'
const CHART_LINE = 'rgba(159, 176, 195, 0.16)'
const CHART_COLORS = ['#39a0ff', '#32c98c', '#f2b84b', '#a68cff', '#28c2d1', '#ff6374', '#64758a']
const UNASSIGNED_STATION_NAME = '未分类'
const REDUCE_MOTION = typeof window !== 'undefined' && window.matchMedia('(prefers-reduced-motion: reduce)').matches
const BASE_ANIMATION = REDUCE_MOTION ? { animation: false } : {
  animation: true,
  animationDuration: 720,
  animationDurationUpdate: 420,
  animationEasing: 'cubicOut',
  animationEasingUpdate: 'cubicOut'
}

export function useIpamDashboard() {
  const loading = ref(false)
  const dashboardError = ref(null)
  const dashboardData = ref(emptyDashboard())
  const scenarioType = ref('SOCIAL')
  const refreshedAt = ref(null)
  const selectedStation = ref('')
  const stationOptions = ref([])
  let dashboardRequestId = 0

  const summary = computed(() => dashboardData.value.summary || {})
  const subjectShortLabel = computed(() => scenarioType.value === 'INTERNAL' ? '项目' : '小区')
  const usagePercent = computed(() => {
    const capacity = Number(summary.value.assignableCount || 0)
    const occupied = Number(summary.value.occupiedCount || 0)
    return capacity ? Math.min(100, Math.round((occupied / capacity) * 1000) / 10) : 0
  })
  const stationRows = computed(() => buildStationAllocation(dashboardData.value.networks))
  const stationEfficiencyRows = computed(() => buildStationEfficiency(stationRows.value))
  const networkRows = computed(() => buildNetworkUsage(dashboardData.value.networks).map((row) => {
    const network = dashboardData.value.networks.find((item) => item.networkId === row.networkId) || {}
    return { ...network, ...row }
  }))
  const pressureRows = computed(() => buildNetworkPressureDistribution(networkRows.value))
  const networkMatrix = computed(() => buildNetworkLoadMatrix(networkRows.value))
  const alertNetworkCount = computed(() => networkRows.value.filter((row) => row.usage >= 80).length)
  const communityRankingRows = computed(() => [...dashboardData.value.communities]
    .sort((left, right) => Number(right.addressCount || 0) - Number(left.addressCount || 0))
    .slice(0, 10))

  const statusOption = computed(() => buildStatusOption(summary.value))
  const stationOption = computed(() => buildStationOption(stationRows.value))
  const networkOption = computed(() => buildNetworkOption(networkRows.value))
  const pressureOption = computed(() => buildPressureOption(pressureRows.value))
  const communityOption = computed(() => buildCommunityOption(communityRankingRows.value, subjectShortLabel.value))
  const targetTypeOption = computed(() => buildHorizontalDimensionOption(
    normalizeDimensionRows(dashboardData.value.targetTypes, 'targetType'),
    '#39a0ff'
  ))
  const manufacturerOption = computed(() => buildManufacturerOption(
    normalizeManufacturerRows(dashboardData.value.manufacturers)
  ))
  const stationEfficiencyOption = computed(() => buildStationEfficiencyOption(stationEfficiencyRows.value))
  const networkMatrixOption = computed(() => buildNetworkMatrixOption(networkMatrix.value))

  async function loadDashboard() {
    const requestId = ++dashboardRequestId
    loading.value = true
    dashboardError.value = null
    try {
      const query = selectedStation.value ? { policeStationName: selectedStation.value } : undefined
      const [dashboardResponse, scenarioResponse] = await Promise.all([
        getIpamDashboard(query),
        getScenarioSetting().catch(() => ({ scenarioType: 'SOCIAL' }))
      ])
      if (requestId !== dashboardRequestId) return
      dashboardData.value = dashboardResponse.data || emptyDashboard()
      scenarioType.value = normalizeScenarioType(scenarioResponse.scenarioType)
      refreshedAt.value = new Date()
      if (!selectedStation.value || stationOptions.value.length === 0) {
        stationOptions.value = buildStationOptions(dashboardData.value.networks)
      }
    } catch (error) {
      if (requestId === dashboardRequestId) {
        dashboardError.value = error?.message || 'IP分配总览加载失败'
      }
    } finally {
      if (requestId === dashboardRequestId) loading.value = false
    }
  }

  function handleStationChange() {
    loadDashboard()
  }

  function focusStation(stationName) {
    const normalizedName = String(stationName || '').trim()
    if (!normalizedName || normalizedName === UNASSIGNED_STATION_NAME || normalizedName === selectedStation.value) return
    selectedStation.value = normalizedName
    handleStationChange()
  }

  loadDashboard()

  return {
    loading,
    dashboardError,
    dashboardData,
    scenarioType,
    refreshedAt,
    summary,
    subjectShortLabel,
    usagePercent,
    selectedStation,
    stationOptions,
    networkRows,
    alertNetworkCount,
    statusOption,
    stationOption,
    networkOption,
    pressureOption,
    communityOption,
    targetTypeOption,
    manufacturerOption,
    stationEfficiencyOption,
    networkMatrixOption,
    loadDashboard,
    handleStationChange,
    focusStation
  }
}

function buildStatusOption(summary) {
  const occupied = Number(summary.occupiedCount || 0)
  const capacity = Number(summary.assignableCount || 0)
  const usage = capacity ? Math.round((occupied / capacity) * 1000) / 10 : 0
  return {
    ...BASE_ANIMATION,
    color: CHART_COLORS,
    aria: { enabled: true, label: { description: 'IP地址按已使用、空闲、保留和禁用状态分布图' } },
    tooltip: { ...darkTooltip(), trigger: 'item', formatter: '{b}<br/>{c} 个地址（{d}%）' },
    title: {
      text: `${usage}%`,
      subtext: '地址占用率',
      left: '38%',
      top: '40%',
      textAlign: 'center',
      textStyle: { color: '#edf5ff', fontSize: 22, fontWeight: 680 },
      subtextStyle: { color: CHART_TEXT, fontSize: 10, lineHeight: 20 }
    },
    legend: {
      orient: 'vertical',
      right: 0,
      top: 'center',
      itemWidth: 9,
      itemHeight: 9,
      itemGap: 12,
      textStyle: { color: CHART_TEXT }
    },
    series: [{
      name: '地址状态',
      type: 'pie',
      radius: ['55%', '78%'],
      center: ['38%', '52%'],
      startAngle: 90,
      clockwise: true,
      itemStyle: { borderColor: '#121922', borderWidth: 2 },
      label: { show: false },
      emphasis: { scaleSize: 8, label: { show: true, color: '#f7fbff', fontSize: 12, fontWeight: 600 } },
      data: buildStatusDistribution(summary)
    }]
  }
}

function buildStationOption(rows) {
  const data = [...rows].reverse()
  return {
    ...BASE_ANIMATION,
    aria: { enabled: true, label: { description: '各派出所已使用、空闲、保留和禁用地址数量对比图' } },
    color: ['#39a0ff', '#5b697a', '#f2b84b', '#ff6374'],
    tooltip: {
      ...darkTooltip(),
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      formatter(params) {
        const index = params[0]?.dataIndex ?? 0
        const row = data[index] || {}
        return `${row.name}<br/>管理网段：${row.networkCount || 0} 个<br/>已使用：${row.occupied || 0}<br/>空闲：${row.free || 0}<br/>保留：${row.reserved || 0}<br/>禁用：${row.disabled || 0}`
      }
    },
    legend: { top: 0, right: 0, itemWidth: 9, itemHeight: 9, itemGap: 14, textStyle: { color: CHART_TEXT } },
    grid: { left: 10, right: 14, top: 36, bottom: 8, containLabel: true },
    xAxis: valueAxis(),
    yAxis: {
      type: 'category',
      data: data.map((item) => item.name),
      axisTick: { show: false },
      axisLine: { show: false },
      axisLabel: { color: CHART_TEXT, width: 84, overflow: 'truncate', interval: 0, fontSize: 10 }
    },
    series: [
      stationBarSeries('已使用', data.map((item) => item.occupied), 0),
      stationBarSeries('空闲', data.map((item) => item.free), 1),
      stationBarSeries('保留', data.map((item) => item.reserved), 2),
      stationBarSeries('禁用', data.map((item) => item.disabled), 3)
    ]
  }
}

function stationBarSeries(name, data, seriesIndex) {
  return {
    name,
    type: 'bar',
    stack: 'total',
    data,
    barMaxWidth: 12,
    cursor: 'pointer',
    animationDelay: (index) => index * 18 + seriesIndex * 35,
    emphasis: { focus: 'series' }
  }
}

function buildNetworkOption(rows) {
  const startValue = Math.max(0, rows.length - 12)
  return {
    ...BASE_ANIMATION,
    aria: { enabled: true, label: { description: '各网段地址占用率图' } },
    tooltip: {
      ...darkTooltip(),
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      formatter(params) {
        const row = rows[params[0]?.dataIndex] || {}
        return `${row.name}<br/>派出所：${row.stationName}<br/>网关：${row.gatewayIp}<br/>已使用：${row.occupied}/${row.capacity}<br/>占用率：${row.usage}%`
      }
    },
    grid: { left: 12, right: 22, top: 26, bottom: rows.length > 12 ? 50 : 22, containLabel: true },
    xAxis: {
      type: 'category',
      data: rows.map((item) => item.gatewayIp),
      axisLabel: { color: CHART_TEXT, rotate: 35, hideOverlap: true },
      axisLine: { lineStyle: { color: CHART_LINE } },
      axisTick: { show: false }
    },
    yAxis: { ...valueAxis(), max: 100, axisLabel: { color: CHART_TEXT, formatter: '{value}%' } },
    dataZoom: rows.length > 12 ? [
      { type: 'inside', startValue, endValue: rows.length - 1 },
      {
        type: 'slider',
        height: 14,
        bottom: 4,
        borderColor: 'transparent',
        backgroundColor: '#1b2530',
        fillerColor: 'rgba(57, 160, 255, 0.24)',
        handleStyle: { color: '#39a0ff' },
        textStyle: { color: CHART_TEXT },
        startValue,
        endValue: rows.length - 1
      }
    ] : [],
    series: [{
      name: '占用率',
      type: 'bar',
      barMaxWidth: 22,
      animationDelay: (index) => index * 24,
      data: rows.map((item) => ({
        value: item.usage,
        itemStyle: {
          color: item.usage >= 90 ? '#ff6374' : item.usage >= 80 ? '#f2b84b' : item.usage >= 60 ? '#28c2d1' : '#39a0ff',
          borderRadius: [3, 3, 0, 0]
        }
      })),
      label: { show: true, position: 'top', color: CHART_TEXT, fontSize: 10, formatter: '{c}%' },
      markLine: {
        silent: true,
        symbol: 'none',
        label: { color: CHART_TEXT, fontSize: 9, formatter: '{b}' },
        lineStyle: { type: 'dashed', width: 1 },
        data: [
          { name: '预警线', yAxis: 80, lineStyle: { color: '#f2b84b' } },
          { name: '高危线', yAxis: 90, lineStyle: { color: '#ff6374' } }
        ]
      }
    }]
  }
}

function buildPressureOption(rows) {
  const maxValue = Math.max(1, ...rows.map((item) => Number(item.value || 0)))
  return {
    ...BASE_ANIMATION,
    aria: { enabled: true, label: { description: '网段按占用率分层统计图' } },
    tooltip: {
      ...darkTooltip(),
      trigger: 'item',
      formatter(params) {
        const row = params.data || {}
        return `${row.name} ${row.range}<br/>网段：${row.value || 0} 个<br/>已使用：${row.occupied || 0}/${row.capacity || 0}`
      }
    },
    grid: { left: 10, right: 44, top: 18, bottom: 16, containLabel: true },
    xAxis: { ...valueAxis(), max: Math.ceil(maxValue * 1.2), axisLabel: { show: false }, splitLine: { show: false } },
    yAxis: {
      type: 'category',
      inverse: true,
      data: rows.map((item) => item.name),
      axisTick: { show: false },
      axisLine: { show: false },
      axisLabel: { color: CHART_TEXT, fontSize: 10 }
    },
    series: [{
      name: '网段数量',
      type: 'bar',
      barWidth: 18,
      showBackground: true,
      backgroundStyle: { color: '#1b2631', borderRadius: 3 },
      data: rows.map((item) => ({
        ...item,
        itemStyle: { color: item.color, borderRadius: [0, 3, 3, 0] }
      })),
      label: { show: true, position: 'right', color: '#dce8f5', fontSize: 10, formatter: '{c} 段' },
      animationDelay: (index) => index * 70
    }]
  }
}

function buildCommunityOption(rows, subjectShortLabel) {
  return {
    ...BASE_ANIMATION,
    aria: { enabled: true, label: { description: `${subjectShortLabel}占用IP数量排行图` } },
    tooltip: { ...darkTooltip(), trigger: 'axis', axisPointer: { type: 'shadow' }, formatter: '{b}<br/>{c} 个已使用IP' },
    grid: { left: 10, right: 38, top: 10, bottom: 10, containLabel: true },
    xAxis: valueAxis(),
    yAxis: {
      type: 'category',
      inverse: true,
      data: rows.map((item) => item.communityName),
      axisTick: { show: false },
      axisLine: { show: false },
      axisLabel: { color: CHART_TEXT, width: 144, lineHeight: 14, formatter: (value) => wrapLabel(value, 9) }
    },
    series: [{
      type: 'bar',
      data: rows.map((item, index) => ({
        value: Number(item.addressCount || 0),
        itemStyle: { color: index < 3 ? '#39a0ff' : '#2676b5', borderRadius: [0, 3, 3, 0] }
      })),
      barMaxWidth: 13,
      animationDelay: (index) => index * 34,
      label: { show: true, position: 'right', color: '#dce8f5', fontSize: 11 }
    }]
  }
}

function buildHorizontalDimensionOption(rows, color) {
  const data = [...rows].slice(0, 10).reverse()
  return {
    ...BASE_ANIMATION,
    aria: { enabled: true },
    tooltip: { ...darkTooltip(), trigger: 'axis', axisPointer: { type: 'shadow' }, formatter: '{b}<br/>{c} 个IP' },
    grid: { left: 8, right: 30, top: 8, bottom: 8, containLabel: true },
    xAxis: valueAxis(),
    yAxis: {
      type: 'category',
      data: data.map((item) => item.name),
      axisTick: { show: false },
      axisLine: { show: false },
      axisLabel: { color: CHART_TEXT, width: 72, lineHeight: 14, formatter: (value) => wrapLabel(value, 6) }
    },
    series: [{
      type: 'bar',
      data: data.map((item) => item.value),
      itemStyle: { color, borderRadius: [0, 3, 3, 0] },
      barMaxWidth: 12,
      animationDelay: (index) => index * 30,
      label: { show: true, position: 'right', color: '#dce8f5', fontSize: 10 }
    }]
  }
}

function buildManufacturerOption(rows) {
  const data = collapseDimensionRows(rows, 9)
  return {
    ...BASE_ANIMATION,
    color: CHART_COLORS,
    aria: { enabled: true, label: { description: '设备品牌占用IP分布图' } },
    tooltip: { ...darkTooltip(), trigger: 'item', formatter: '{b}<br/>{c} 个IP（{d}%）' },
    series: [{
      type: 'pie',
      radius: ['28%', '73%'],
      center: ['50%', '52%'],
      roseType: 'area',
      data,
      itemStyle: { borderColor: '#121922', borderWidth: 2, borderRadius: 3 },
      label: { color: CHART_TEXT, fontSize: 9, formatter: (params) => wrapLabel(params.name, 6) },
      labelLine: { length: 7, length2: 5, lineStyle: { color: '#526478' } },
      emphasis: { scaleSize: 8 }
    }]
  }
}

function buildStationEfficiencyOption(rows) {
  return {
    ...BASE_ANIMATION,
    aria: { enabled: true, label: { description: '派出所地址占用率与已使用地址数量关系图' } },
    tooltip: {
      ...darkTooltip(),
      formatter(params) {
        const row = params.data || {}
        return `${row.name}<br/>占用率：${row.value?.[0] || 0}%<br/>已使用：${row.value?.[1] || 0}<br/>管理网段：${row.value?.[2] || 0} 个<br/>可分配：${row.value?.[3] || 0}`
      }
    },
    grid: { left: 12, right: 22, top: 20, bottom: 14, containLabel: true },
    xAxis: {
      type: 'value',
      min: 0,
      max: 100,
      name: '占用率',
      nameTextStyle: { color: CHART_TEXT },
      axisLabel: { color: CHART_TEXT, formatter: '{value}%' },
      splitLine: { lineStyle: { color: CHART_LINE } },
      axisLine: { show: false }
    },
    yAxis: { ...valueAxis(), name: '已使用IP', nameTextStyle: { color: CHART_TEXT } },
    series: [{
      type: 'scatter',
      data: rows.map((row) => ({
        name: row.name,
        value: [row.usage, row.occupied, row.networkCount, row.capacity],
        itemStyle: { color: row.usage >= 90 ? '#ff6374' : row.usage >= 80 ? '#f2b84b' : '#39a0ff' }
      })),
      symbolSize: (value) => Math.min(42, 13 + Math.sqrt(Number(value?.[2] || 1)) * 7),
      label: { show: true, position: 'top', color: '#dce8f5', fontSize: 9, formatter: '{b}' },
      labelLayout: { hideOverlap: true },
      emphasis: { focus: 'self', scale: 1.2 },
      animationDelay: (index) => index * 36,
      markLine: {
        silent: true,
        symbol: 'none',
        label: { color: CHART_TEXT, fontSize: 9 },
        data: [
          { name: '预警', xAxis: 80, lineStyle: { color: '#f2b84b', type: 'dashed' } },
          { name: '高危', xAxis: 90, lineStyle: { color: '#ff6374', type: 'dashed' } }
        ]
      }
    }]
  }
}

function buildNetworkMatrixOption(matrix) {
  return {
    ...BASE_ANIMATION,
    aria: { enabled: true, label: { description: '各派出所网段占用率矩阵图' } },
    tooltip: {
      ...darkTooltip(),
      formatter(params) {
        const row = params.data || {}
        return `${row.networkName || '-'}<br/>网关：${row.gatewayIp || '-'}<br/>已使用：${row.occupied || 0}/${row.capacity || 0}<br/>占用率：${row.value?.[2] || 0}%`
      }
    },
    grid: { left: 14, right: 28, top: 48, bottom: 16, containLabel: true },
    xAxis: {
      type: 'category',
      data: matrix.columns,
      position: 'top',
      axisTick: { show: false },
      axisLine: { lineStyle: { color: CHART_LINE } },
      axisLabel: { color: CHART_TEXT, fontSize: 10 }
    },
    yAxis: {
      type: 'category',
      inverse: true,
      data: matrix.stations,
      axisTick: { show: false },
      axisLine: { lineStyle: { color: CHART_LINE } },
      axisLabel: { color: CHART_TEXT, fontSize: 10 }
    },
    visualMap: {
      min: 0,
      max: 100,
      dimension: 2,
      orient: 'horizontal',
      top: 0,
      right: 0,
      itemWidth: 10,
      itemHeight: 90,
      calculable: false,
      text: ['高负载', '低负载'],
      textStyle: { color: CHART_TEXT, fontSize: 9 },
      inRange: { color: ['#1a3852', '#1e80b5', '#28c2d1', '#f2b84b', '#ff6374'] }
    },
    series: [{
      type: 'scatter',
      symbol: 'roundRect',
      symbolSize: [64, 18],
      data: matrix.cells,
      label: { show: true, color: '#f6fbff', fontSize: 9, formatter: (params) => `${params.value?.[2] || 0}%` },
      itemStyle: { borderColor: 'rgba(222, 238, 255, 0.22)', borderWidth: 1 },
      emphasis: { scale: 1.14, itemStyle: { borderColor: '#e9f7ff', borderWidth: 1 } },
      animationDelay: (index) => index * 24
    }]
  }
}

function valueAxis() {
  return {
    type: 'value',
    splitLine: { lineStyle: { color: CHART_LINE } },
    axisLabel: { color: CHART_TEXT },
    axisLine: { show: false },
    axisTick: { show: false }
  }
}

function darkTooltip() {
  return {
    backgroundColor: 'rgba(8, 12, 17, 0.96)',
    borderColor: '#344354',
    textStyle: { color: '#edf5ff', fontSize: 12 },
    extraCssText: 'box-shadow: 0 10px 30px rgba(0,0,0,.28); border-radius: 6px;'
  }
}

function buildStationOptions(networks) {
  return [...new Set(networks
    .map((network) => String(network?.policeStationName || '').trim())
    .filter(Boolean))]
    .sort((left, right) => left.localeCompare(right, 'zh-CN'))
}

function wrapLabel(value, maxLength) {
  const text = String(value || '')
  if (text.length <= maxLength) return text
  const lines = []
  for (let index = 0; index < text.length; index += maxLength) {
    lines.push(text.slice(index, index + maxLength))
  }
  return lines.join('\n')
}

function collapseDimensionRows(rows, limit) {
  if (rows.length <= limit) return rows
  const visible = rows.slice(0, limit - 1)
  const remaining = rows.slice(limit - 1).reduce((total, row) => total + Number(row.value || 0), 0)
  return [...visible, { name: '其他', value: remaining }]
}

function normalizeScenarioType(value) {
  return String(value || '').toUpperCase() === 'INTERNAL' ? 'INTERNAL' : 'SOCIAL'
}

function emptyDashboard() {
  return { summary: {}, networks: [], communities: [], targetTypes: [], manufacturers: [] }
}
