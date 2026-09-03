import {
  RESULT_ABNORMAL,
  RESULT_NORMAL,
  RESULT_SKIP,
  RESULT_WARNING,
  buildCurrentStatusDistribution,
  formatShortDate,
  healthStatusColor,
  healthStatusLabel,
  normalizeHealthScore
} from './cockpitPresentation.js'

function escapeHtml(value) {
  return String(value ?? '').replaceAll('&', '&amp;').replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;').replaceAll('"', '&quot;').replaceAll("'", '&#39;')
}

function baseOption(palette, animate) {
  return {
    animation: animate,
    animationDuration: 240,
    animationDurationUpdate: 240,
    animationEasing: 'cubicOut',
    animationEasingUpdate: 'cubicOut',
    aria: { enabled: true },
    textStyle: { fontFamily: palette.fontFamily, color: palette.text, fontSize: 11 }
  }
}

function tooltipStyle(palette) {
  return {
    confine: true,
    backgroundColor: palette.surface,
    borderColor: palette.grid,
    padding: [10, 12],
    textStyle: { color: palette.text, fontSize: 12 },
    extraCssText: 'max-width:320px;white-space:normal;word-break:break-word;line-height:1.7;'
  }
}

function rankingZoom(id, count, palette) {
  if (count <= 6) return []
  return [
    {
      id: `${id}-slider`, type: 'slider', yAxisIndex: 0, orient: 'vertical',
      right: 4, top: 12, bottom: 26, width: 8, startValue: 0, endValue: 5,
      showDetail: false, brushSelect: false, borderColor: 'transparent',
      backgroundColor: palette.subtle, fillerColor: palette.grid,
      handleStyle: { color: palette.muted, borderWidth: 0 }, moveHandleSize: 0
    },
    {
      id: `${id}-inside`, type: 'inside', yAxisIndex: 0,
      startValue: 0, endValue: 5, zoomOnMouseWheel: false, moveOnMouseWheel: true
    }
  ]
}

export function buildTrendOption(rows, palette, animate = false) {
  return {
    ...baseOption(palette, animate),
    color: [palette.primary, palette.idle, palette.normal, palette.danger],
    tooltip: { trigger: 'axis', ...tooltipStyle(palette) },
    legend: { top: 6, right: 4, itemWidth: 10, itemHeight: 7, itemGap: 12, textStyle: { color: palette.muted, fontSize: 11 } },
    grid: { left: 42, right: 36, top: 42, bottom: 26 },
    xAxis: {
      type: 'category', data: rows.map((row) => formatShortDate(row.date)),
      axisLine: { lineStyle: { color: palette.grid } }, axisTick: { show: false },
      axisLabel: { color: palette.muted, hideOverlap: true }
    },
    yAxis: [
      { type: 'value', min: 0, max: 100, interval: 25, axisLabel: { color: palette.muted, formatter: '{value}%' }, splitLine: { lineStyle: { color: palette.grid, type: 'dashed' } } },
      { type: 'value', minInterval: 1, splitNumber: 4, axisLabel: { color: palette.muted }, splitLine: { show: false } }
    ],
    series: [
      {
        id: 'health', name: '健康度', type: 'line', yAxisIndex: 0,
        smooth: 0.2, symbol: 'circle', symbolSize: 6, lineStyle: { width: 2.5 },
        areaStyle: { color: palette.primary, opacity: 0.07 }, emphasis: { focus: 'series' },
        data: rows.map((row) => ({ value: normalizeHealthScore(row.healthScore), date: row.date }))
      },
      { id: 'expected', name: '应执行', type: 'bar', yAxisIndex: 1, barMaxWidth: 12, itemStyle: { opacity: 0.45, borderRadius: [2, 2, 0, 0] }, data: rows.map((row) => ({ value: Number(row.frequentExpected || 0), date: row.date })) },
      { id: 'completed', name: '已完成', type: 'bar', yAxisIndex: 1, barMaxWidth: 12, itemStyle: { opacity: 0.9, borderRadius: [2, 2, 0, 0] }, data: rows.map((row) => ({ value: Number(row.frequentCompleted || 0), date: row.date })) },
      {
        id: 'issues', name: '问题数', type: 'line', yAxisIndex: 1, symbol: 'diamond',
        symbolSize: 7, lineStyle: { width: 1.5, type: 'dashed' }, emphasis: { focus: 'series' },
        data: rows.map((row) => ({ value: Number(row.routineAbnormal || 0) + Number(row.frequentAbnormal || 0) + Number(row.frequentWarning || 0) + Number(row.frequentMissing || 0), date: row.date }))
      }
    ]
  }
}

export function buildStatusOption(sites, palette, selection = {}, animate = false) {
  const platforms = sites.flatMap((site) => site.children || [])
  const segments = (rows, scopeType) => buildCurrentStatusDistribution(rows).map((row) => ({
    ...row,
    scopeType,
    selected: selection.scopeType === scopeType && selection.resultStatus === row.status,
    itemStyle: { color: healthStatusColor(row.status, palette), borderColor: palette.surface, borderWidth: 2 }
  }))
  return {
    ...baseOption(palette, animate),
    tooltip: { trigger: 'item', formatter: '{a}<br/>{b}：{c}（{d}%）', ...tooltipStyle(palette) },
    legend: { bottom: 4, left: 'center', selectedMode: false, itemWidth: 8, itemHeight: 8, itemGap: 12, textStyle: { color: palette.muted, fontSize: 11 } },
    title: {
      text: String(sites.length + platforms.length), subtext: '健康范围',
      left: 'center', top: '36%', textStyle: { color: palette.heading, fontSize: 28, fontWeight: 600 },
      subtextStyle: { color: palette.muted, fontSize: 11 }
    },
    series: [
      {
        id: 'sites', name: '现场', type: 'pie', radius: ['29%', '44%'], center: ['50%', '47%'],
        selectedMode: 'single', selectedOffset: 3, label: { show: false },
        emphasis: { scaleSize: 3 }, data: segments(sites, 'SITE')
      },
      {
        id: 'platforms', name: '主平台', type: 'pie', radius: ['54%', '70%'], center: ['50%', '47%'],
        selectedMode: 'single', selectedOffset: 3, minShowLabelAngle: 12,
        label: { color: palette.text, fontSize: 11, formatter: ({ name, value }) => Number(value) > 0 ? `${name} ${value}` : '' },
        labelLine: { length: 6, length2: 5, lineStyle: { color: palette.grid } },
        emphasis: { scaleSize: 3 }, data: segments(platforms, 'MAIN_PLATFORM')
      }
    ]
  }
}

function rankingAxes(rows, palette, key, labelWidth) {
  const labels = new Map(rows.map((row) => [String(row[key]), row.chartName]))
  return {
    grid: { left: labelWidth + 12, right: 48, top: 12, bottom: 26 },
    xAxis: { type: 'value', min: 0, max: 100, interval: 25, axisLabel: { color: palette.muted, formatter: '{value}%' }, splitLine: { lineStyle: { color: palette.grid, type: 'dashed' } } },
    yAxis: {
      type: 'category', inverse: true, triggerEvent: true, data: rows.map((row) => String(row[key])),
      axisLine: { show: false }, axisTick: { show: false },
      axisLabel: { color: palette.text, width: labelWidth, overflow: 'truncate', formatter: (value) => labels.get(value) || value }
    }
  }
}

export function buildScopeOption(rows, palette, animate = false) {
  return {
    ...baseOption(palette, animate),
    ...rankingAxes(rows, palette, 'scopeKey', 132),
    dataZoom: rankingZoom('scope', rows.length, palette),
    tooltip: {
      trigger: 'axis', axisPointer: { type: 'shadow' }, ...tooltipStyle(palette),
      formatter: (params = []) => {
        const row = rows.find((item) => item.scopeKey === params[0]?.data?.scopeKey)
        return row ? `${escapeHtml(row.scopePath)}<br/>健康度：${normalizeHealthScore(row.healthScore)}%<br/>结论：${healthStatusLabel(row.resultStatus)}<br/>完成：${row.completedCount || 0} / ${row.expectedCount || 0}` : ''
      }
    },
    series: [{
      id: 'scope-health', name: '健康度', type: 'bar', barMaxWidth: 12,
      showBackground: true, backgroundStyle: { color: palette.subtle, borderRadius: 3 },
      label: { show: true, position: 'right', color: palette.muted, formatter: '{c}%' },
      data: rows.map((row) => ({ id: row.scopeKey, name: row.chartName, scopeKey: row.scopeKey, value: normalizeHealthScore(row.healthScore), itemStyle: { color: healthStatusColor(row.resultStatus, palette), borderRadius: [0, 3, 3, 0] } }))
    }]
  }
}

export function buildPlanOption(rows, palette, animate = false) {
  return {
    ...baseOption(palette, animate),
    ...rankingAxes(rows, palette, 'planId', 124),
    dataZoom: rankingZoom('plan', rows.length, palette),
    tooltip: {
      trigger: 'axis', axisPointer: { type: 'shadow' }, ...tooltipStyle(palette),
      formatter: (params = []) => {
        const row = rows.find((item) => String(item.planId) === String(params[0]?.data?.planId))
        return row ? `${escapeHtml(row.chartName)}<br/>完成度：${row.completionRate}%<br/>已完成：${row.completedCount} / ${row.expectedCount}<br/>异常：${row.abnormalCount || 0}，关注：${row.warningCount || 0}，缺失：${row.missingCount || 0}` : ''
      }
    },
    series: [{
      id: 'plan-completion', name: '完成度', type: 'bar', barMaxWidth: 12,
      showBackground: true, backgroundStyle: { color: palette.subtle, borderRadius: 3 },
      label: { show: true, position: 'right', color: palette.muted, formatter: '{c}%' },
      data: rows.map((row) => ({ id: String(row.planId), name: row.chartName, planId: row.planId, value: row.completionRate, itemStyle: { color: healthStatusColor(row.resultStatus, palette), borderRadius: [0, 3, 3, 0] } }))
    }]
  }
}

export function buildIssueOption(rows, palette, animate = false) {
  return {
    ...baseOption(palette, animate),
    tooltip: {
      trigger: 'item', ...tooltipStyle(palette),
      formatter: ({ data }) => `${escapeHtml(data?.name)}<br/>${healthStatusLabel(data?.resultStatus)}<br/>${escapeHtml(data?.detail || '暂无问题详情')}<br/>来源：${escapeHtml(data?.source)}`
    },
    series: [{
      id: 'issues', type: 'treemap', roam: false, nodeClick: false,
      left: 8, right: 8, top: 10, bottom: 10, breadcrumb: { show: false },
      label: { show: true, color: palette.heading, fontSize: 11, lineHeight: 18, overflow: 'truncate', formatter: ({ data }) => `${healthStatusLabel(data.resultStatus)}\n${data.name}` },
      upperLabel: { show: false },
      itemStyle: { borderColor: palette.surface, borderWidth: 4, gapWidth: 3 },
      data: rows.map((row, index) => ({
        id: `${row.sourceMode}:${row.planId || row.recordId || index}`,
        name: row.chartName, value: row.chartValue, detail: row.issueDetail,
        source: row.sourceMode === 'FREQUENT' ? '计划健康' : '执行记录',
        resultStatus: row.resultStatus, issueIndex: index,
        itemStyle: {
          color: row.resultStatus === RESULT_ABNORMAL ? palette.dangerSoft : palette.warningSoft,
          borderColor: healthStatusColor(row.resultStatus, palette), borderWidth: 1
        }
      }))
    }]
  }
}

export function buildRecordOption(rows, palette, animate = false) {
  const statuses = [RESULT_SKIP, RESULT_NORMAL, RESULT_WARNING, RESULT_ABNORMAL].map(healthStatusLabel)
  return {
    ...baseOption(palette, animate),
    tooltip: {
      trigger: 'item', ...tooltipStyle(palette),
      formatter: ({ data }) => {
        const row = rows.find((item) => item.recordId === data?.recordId)
        return row ? `${escapeHtml(row.chartName)}<br/>${escapeHtml(row.inspectionTime)}<br/>结果：${row.statusLabel}<br/>${escapeHtml(row.abnormalSummary || row.summary || '本次检测已完成')}` : ''
      }
    },
    grid: { left: 50, right: 16, top: 24, bottom: 32 },
    xAxis: {
      type: 'time', splitNumber: 4, axisLine: { lineStyle: { color: palette.grid } }, axisTick: { show: false },
      axisLabel: { color: palette.muted, hideOverlap: true, formatter: (value) => {
        const date = new Date(value)
        return `${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`
      } }, splitLine: { show: false }
    },
    yAxis: { type: 'category', data: statuses, axisLine: { show: false }, axisTick: { show: false }, axisLabel: { color: palette.text }, splitLine: { show: true, lineStyle: { color: palette.grid, type: 'dashed' } } },
    series: [{
      id: 'execution-records', name: '执行记录', type: 'scatter', symbolSize: 13,
      emphasis: { scale: 1.3 },
      data: rows.map((row) => ({ id: String(row.recordId), recordId: row.recordId, value: [row.timestamp, row.statusLabel], symbol: row.resultStatus === RESULT_ABNORMAL ? 'diamond' : 'circle', itemStyle: { color: healthStatusColor(row.resultStatus, palette) } }))
    }]
  }
}

// Preserve only view state, never the previous data or status of a refreshed chart.
export function preserveCockpitChartView(option, previous = {}) {
  const next = { ...option }
  if (option.legend && option.legend.selectedMode !== false && previous.legend?.[0]?.selected) {
    next.legend = { ...option.legend, selected: { ...previous.legend[0].selected } }
  }
  if (Array.isArray(option.dataZoom)) {
    next.dataZoom = option.dataZoom.map((zoom) => {
      const saved = previous.dataZoom?.find((item) => item.id === zoom.id)
      if (!Number.isFinite(saved?.start) || !Number.isFinite(saved?.end)) return zoom
      const { startValue, endValue, ...config } = zoom
      return { ...config, start: saved.start, end: saved.end }
    })
  }
  return next
}
