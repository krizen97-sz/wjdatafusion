import { baseOption, escapeHtml, tooltipStyle } from './cockpitChartOptions.js'
import { healthStatusColor, healthStatusLabel } from './cockpitPresentation.js'

export function normalizeMetricDashboard(data = {}) {
  return {
    planId: data.planId || null,
    plans: Array.isArray(data.plans) ? data.plans : [],
    metrics: Array.isArray(data.metrics) ? data.metrics : [],
    executions: Array.isArray(data.executions) ? data.executions : [],
    beginTime: data.beginTime || '', endTime: data.endTime || '', generatedTime: data.generatedTime || '',
    sampleLimit: Number(data.sampleLimit || 5000), sampleCount: Number(data.sampleCount || 0), truncated: Boolean(data.truncated)
  }
}

export function metricNumber(value) {
  if (value === null || value === undefined || String(value).trim() === '') return null
  const number = Number(value)
  return Number.isFinite(number) ? number : null
}

export function formatMetricValue(value) {
  if (value === null || value === undefined || value === '') return '--'
  const text = String(value)
  if (!/^-?\d+(\.\d+)?$/.test(text)) return text
  const [integer, decimal] = text.split('.')
  const negative = integer.startsWith('-')
  const unsignedInteger = (negative ? integer.slice(1) : integer).replace(/^0+(?=\d)/, '')
  const groupedInteger = unsignedInteger.replace(/\B(?=(\d{3})+(?!\d))/g, ',')
  return `${negative ? '-' : ''}${groupedInteger}${decimal ? `.${decimal}` : ''}`
}

export function metricPoints(metric) {
  return (metric?.points || []).map((point) => ({
    ...point,
    timestamp: Date.parse(String(point.sampleTime || '').replace(' ', 'T')),
    valueNumber: metricNumber(point.value),
    previousNumber: point.baselineFlag === 'Y' ? null : metricNumber(point.previousValue),
    changeNumber: point.baselineFlag === 'Y' ? null : metricNumber(point.changeValue)
  })).filter((point) => Number.isFinite(point.timestamp)).sort((a, b) => a.timestamp - b.timestamp)
}

export function metricExecutionPoints(executions = []) {
  return executions.map((point) => ({
    ...point,
    timestamp: Date.parse(String(point.sampleTime || '').replace(' ', 'T')),
    duration: metricNumber(point.durationMs)
  })).filter((point) => Number.isFinite(point.timestamp) && point.duration !== null && point.duration >= 0)
    .sort((a, b) => a.timestamp - b.timestamp)
}

export function comparisonValues(metric) {
  const latest = metric?.latest || {}
  return [latest.baselineFlag === 'Y' ? null : latest.previousValue ?? null, latest.value ?? null]
}

const chartNumber = (value) => new Intl.NumberFormat('zh-CN', {
  notation: Math.abs(value) >= 1000000 ? 'compact' : 'standard', maximumSignificantDigits: 6
}).format(value)

function timeAxes(palette, unit = '', scale = true) {
  return {
    grid: { left: 58, right: 18, top: 28, bottom: 44 },
    xAxis: {
      type: 'time', splitNumber: 4, axisLine: { lineStyle: { color: palette.grid } }, axisTick: { show: false },
      axisLabel: { color: palette.muted, hideOverlap: true, formatter: (value) => {
        const time = new Date(value)
        return `${String(time.getMonth() + 1).padStart(2, '0')}/${String(time.getDate()).padStart(2, '0')}\n${String(time.getHours()).padStart(2, '0')}:${String(time.getMinutes()).padStart(2, '0')}`
      } }, splitLine: { show: false }
    },
    yAxis: { type: 'value', scale, name: unit, nameTextStyle: { color: palette.muted }, axisLabel: { color: palette.muted, formatter: chartNumber }, splitLine: { lineStyle: { color: palette.grid, type: 'dashed' } } }
  }
}

function sourceTooltip(point, metric) {
  if (!point) return ''
  const unit = escapeHtml(metric?.unit || '')
  const baseline = point.baselineFlag === 'Y'
  return `${escapeHtml(metric?.targetName || metric?.metricName)}<br/>${escapeHtml(point.sampleTime)}<br/>实测：${escapeHtml(formatMetricValue(point.value))} ${unit}<br/>上次：${baseline ? '建立基线' : escapeHtml(formatMetricValue(point.previousValue))}<br/>变化：${baseline ? '暂无可比基线' : escapeHtml(formatMetricValue(point.changeValue))}<br/>结论：${healthStatusLabel(point.resultStatus)}${point.evaluationRule ? `<br/>${escapeHtml(point.evaluationRule)}` : ''}`
}

export function buildMetricTrendOption(metric, palette, animate = false) {
  const points = metricPoints(metric)
  return {
    ...baseOption(palette, animate), ...timeAxes(palette, metric?.unit),
    color: [palette.primary, palette.muted],
    legend: { top: 0, right: 12, itemWidth: 12, itemHeight: 6, textStyle: { color: palette.muted } },
    tooltip: { trigger: 'axis', ...tooltipStyle(palette), formatter: (params = []) => sourceTooltip(points.find((point) => point.resultId === params[0]?.data?.resultId), metric) },
    series: [
      { id: 'metric-actual', name: '实测值', type: 'line', connectNulls: false, symbolSize: 5, sampling: 'lttb', lineStyle: { width: 2 }, areaStyle: { opacity: 0.12 }, data: points.map((point) => ({ resultId: point.resultId, recordId: point.recordId, value: [point.timestamp, point.valueNumber], itemStyle: { color: healthStatusColor(point.resultStatus, palette) } })) },
      { id: 'metric-previous', name: '上次值', type: 'line', connectNulls: false, showSymbol: false, lineStyle: { width: 1, type: 'dashed', opacity: 0.6 }, data: points.map((point) => ({ resultId: point.resultId, recordId: point.recordId, value: [point.timestamp, point.previousNumber] })) }
    ]
  }
}

export function buildMetricComparisonOption(metric, palette, animate = false) {
  const values = comparisonValues(metric)
  return {
    ...baseOption(palette, animate),
    grid: { left: 54, right: 16, top: 30, bottom: 28 },
    tooltip: { trigger: 'item', ...tooltipStyle(palette), formatter: ({ data }) => `${escapeHtml(data.name)}<br/>${escapeHtml(formatMetricValue(data.rawValue))} ${escapeHtml(metric?.unit || '')}` },
    xAxis: { type: 'category', data: ['上次采样', '最新采样'], axisLine: { lineStyle: { color: palette.grid } }, axisTick: { show: false }, axisLabel: { color: palette.text } },
    yAxis: { type: 'value', name: metric?.unit || '', nameTextStyle: { color: palette.muted }, axisLabel: { color: palette.muted, formatter: chartNumber }, splitLine: { lineStyle: { color: palette.grid, type: 'dashed' } } },
    series: [{
      id: 'metric-comparison', type: 'bar', barMaxWidth: 34,
      label: { show: true, position: 'top', color: palette.text, formatter: ({ value }) => value == null ? '--' : chartNumber(value) },
      data: values.map((value, index) => ({ name: index ? '最新采样' : '上次采样', rawValue: value, value: metricNumber(value), recordId: metric?.latest?.recordId, itemStyle: { color: index ? palette.primary : palette.muted, opacity: index ? 1 : 0.45 } }))
    }]
  }
}

export function buildMetricChangeOption(metric, palette, animate = false) {
  const points = metricPoints(metric)
  return {
    ...baseOption(palette, animate), ...timeAxes(palette, metric?.unit, false),
    tooltip: { trigger: 'axis', ...tooltipStyle(palette), formatter: (params = []) => sourceTooltip(points.find((point) => point.resultId === params[0]?.data?.resultId), metric) },
    series: [{
      id: 'metric-change', name: '变化量', type: 'bar', barMaxWidth: 10,
      data: points.map((point) => ({ resultId: point.resultId, recordId: point.recordId, value: [point.timestamp, point.changeNumber], itemStyle: { color: point.resultStatus === '2' ? palette.danger : point.resultStatus === '4' ? palette.warning : palette.primary } }))
    }]
  }
}

export function buildMetricRangeOption(metric, palette, animate = false) {
  const values = [metric?.minimum, metric?.average, metric?.maximum]
  const names = ['最小值', '平均值', '最大值']
  return {
    ...baseOption(palette, animate),
    grid: { left: 60, right: 78, top: 24, bottom: 24 },
    tooltip: { trigger: 'item', ...tooltipStyle(palette), formatter: ({ data }) => `${data.name}<br/>${escapeHtml(formatMetricValue(data.rawValue))} ${escapeHtml(metric?.unit || '')}` },
    xAxis: { type: 'value', axisLabel: { color: palette.muted, formatter: chartNumber, hideOverlap: true }, splitLine: { lineStyle: { color: palette.grid, type: 'dashed' } } },
    yAxis: { type: 'category', data: names, inverse: true, axisLine: { show: false }, axisTick: { show: false }, axisLabel: { color: palette.text } },
    series: [{
      id: 'metric-range', type: 'bar', barMaxWidth: 14,
      label: { show: true, position: 'right', color: palette.text, formatter: ({ value }) => value == null ? '--' : chartNumber(value) },
      data: values.map((value, index) => ({ name: names[index], rawValue: value, value: metricNumber(value), itemStyle: { color: [palette.muted, palette.primary, palette.normal][index], opacity: 0.9 } }))
    }]
  }
}

export function buildMetricStatusOption(metric, palette, animate = false) {
  return {
    ...baseOption(palette, animate),
    tooltip: { trigger: 'item', ...tooltipStyle(palette), formatter: '{b}：{c} 次（{d}%）' },
    title: { text: String(metric?.sampleCount || 0), subtext: '次采样', left: 'center', top: '34%', textStyle: { color: palette.heading, fontSize: 28 }, subtextStyle: { color: palette.muted, fontSize: 11 } },
    legend: { bottom: 4, left: 'center', selectedMode: false, itemWidth: 8, itemHeight: 8, itemGap: 10, textStyle: { color: palette.muted, fontSize: 11 } },
    series: [{ id: 'metric-status', type: 'pie', radius: ['49%', '65%'], center: ['50%', '45%'], label: { show: false }, itemStyle: { borderColor: palette.surface, borderWidth: 2 }, data: ['1', '4', '2', '3'].map((status) => ({ name: healthStatusLabel(status), value: Number(metric?.statusCounts?.[status] || 0), itemStyle: { color: healthStatusColor(status, palette) } })) }]
  }
}

export function buildMetricDurationOption(executions, palette, animate = false) {
  const points = metricExecutionPoints(executions)
  return {
    ...baseOption(palette, animate), ...timeAxes(palette, 'ms'),
    tooltip: { trigger: 'axis', ...tooltipStyle(palette), formatter: (params = []) => {
      const point = points.find((item) => item.recordId === params[0]?.data?.recordId)
      return point ? `${escapeHtml(point.sampleTime)}<br/>计划耗时：${formatMetricValue(point.durationMs)} ms<br/>结论：${healthStatusLabel(point.resultStatus)}` : ''
    } },
    series: [{ id: 'plan-duration', name: '执行耗时', type: 'line', connectNulls: false, symbolSize: 5, lineStyle: { color: palette.normal, width: 2 }, areaStyle: { color: palette.normal, opacity: 0.06 }, data: points.map((point) => ({ recordId: point.recordId, value: [point.timestamp, point.duration], itemStyle: { color: healthStatusColor(point.resultStatus, palette) } })) }]
  }
}
