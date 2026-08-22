export const INSPECTION_CHART_TOKENS = Object.freeze({
  ink: '#17324d',
  text: '#36536f',
  muted: '#6f8499',
  faint: '#9aabba',
  grid: '#e7edf3',
  surface: '#ffffff',
  blue: '#3378be',
  blueSoft: '#dbe9f6',
  green: '#3d9a68',
  greenSoft: '#d9eee2',
  red: '#d95757',
  redSoft: '#f7dddd',
  amber: '#b97924',
  amberSoft: '#f6e8d2',
  unknown: '#a9b5c1'
})

const FONT_FAMILY = '-apple-system, BlinkMacSystemFont, "Segoe UI", "PingFang SC", "Microsoft YaHei", sans-serif'

function number(value) {
  const parsed = Number(value)
  return Number.isFinite(parsed) ? parsed : 0
}

function percent(value) {
  const parsed = Number(String(value || '0').replace('%', ''))
  return Math.max(0, Math.min(100, Number.isFinite(parsed) ? parsed : 0))
}

function shortDate(value) {
  const match = /^(\d{4})-(\d{2})-(\d{2})/.exec(String(value || ''))
  return match ? `${Number(match[2])}/${Number(match[3])}` : String(value || '-')
}

function emptyGraphic(text = '暂无数据') {
  return [{
    type: 'text',
    left: 'center',
    top: 'middle',
    silent: true,
    style: {
      text,
      fill: INSPECTION_CHART_TOKENS.muted,
      font: `12px ${FONT_FAMILY}`
    }
  }]
}

function axisTooltip(formatter) {
  return {
    trigger: 'axis',
    appendToBody: true,
    borderWidth: 1,
    borderColor: INSPECTION_CHART_TOKENS.grid,
    backgroundColor: INSPECTION_CHART_TOKENS.surface,
    textStyle: { color: INSPECTION_CHART_TOKENS.ink, fontFamily: FONT_FAMILY, fontSize: 12 },
    extraCssText: 'box-shadow:0 8px 24px rgba(23,50,77,.12);border-radius:6px;',
    formatter
  }
}

export function buildInspectionInsight(summary = {}, periodLabel = '本周') {
  const total = number(summary.recordCount)
  const abnormal = number(summary.abnormalCount)
  const abnormalTargets = number(summary.abnormalTargetCount)
  const successRate = summary.successRate || `${percent(summary.successRate)}%`
  if (!total) {
    return {
      status: '3',
      title: `${periodLabel}尚无巡检记录`,
      detail: '执行模板或等待计划运行后，这里会给出趋势与异常结论。'
    }
  }
  if (abnormal > 0 || abnormalTargets > 0) {
    return {
      status: '2',
      title: `${periodLabel}发现 ${abnormalTargets || abnormal} 个异常子项`,
      detail: `共运行 ${total} 次，正常率 ${successRate}，建议先查看异常 TopN。`
    }
  }
  return {
    status: '1',
    title: `${periodLabel}巡检运行稳定`,
    detail: `共运行 ${total} 次，正常率 ${successRate}，暂未发现异常子项。`
  }
}

export function buildWeekTrendOption(rows = [], options = {}) {
  const compact = Boolean(options.compact)
  const safeRows = Array.isArray(rows) ? rows : []
  const totals = safeRows.map((item) => number(item.total))
  const abnormals = safeRows.map((item) => number(item.abnormal))
  const peakValue = Math.max(...abnormals, 0)
  const peakIndex = peakValue > 0 ? abnormals.indexOf(peakValue) : -1
  return {
    animationDuration: 240,
    animationEasing: 'quarticOut',
    aria: { enabled: true },
    color: [INSPECTION_CHART_TOKENS.blue, INSPECTION_CHART_TOKENS.red],
    grid: compact
      ? { top: 7, right: 7, bottom: 17, left: 22, containLabel: false }
      : { top: 28, right: 18, bottom: 30, left: 38 },
    tooltip: axisTooltip((params = []) => {
      const title = params[0]?.axisValue || '-'
      const total = params.find((item) => item.seriesName === '巡检次数')?.value || 0
      const abnormal = params.find((item) => item.seriesName === '异常次数')?.value || 0
      return `<strong>${title}</strong><br/>巡检次数：${total}<br/>异常次数：${abnormal}`
    }),
    legend: compact ? undefined : {
      top: 0,
      right: 0,
      itemWidth: 12,
      itemHeight: 7,
      textStyle: { color: INSPECTION_CHART_TOKENS.muted, fontFamily: FONT_FAMILY, fontSize: 11 }
    },
    xAxis: {
      type: 'category',
      data: safeRows.map((item) => shortDate(item.date)),
      axisTick: { show: false },
      axisLine: { lineStyle: { color: INSPECTION_CHART_TOKENS.grid } },
      axisLabel: { color: INSPECTION_CHART_TOKENS.muted, fontFamily: FONT_FAMILY, fontSize: compact ? 9 : 11 }
    },
    yAxis: {
      type: 'value',
      minInterval: 1,
      axisLabel: { show: !compact, color: INSPECTION_CHART_TOKENS.faint, fontFamily: FONT_FAMILY, fontSize: 10 },
      splitLine: { lineStyle: { color: INSPECTION_CHART_TOKENS.grid, type: 'dashed' } }
    },
    series: [
      {
        name: '巡检次数',
        type: 'bar',
        barMaxWidth: compact ? 11 : 18,
        showBackground: !compact,
        backgroundStyle: { color: '#f2f5f8', borderRadius: 3 },
        itemStyle: { color: INSPECTION_CHART_TOKENS.blue, borderRadius: [3, 3, 0, 0] },
        data: totals
      },
      {
        name: '异常次数',
        type: 'line',
        smooth: 0.28,
        symbol: 'circle',
        symbolSize: compact ? 4 : 7,
        lineStyle: { color: INSPECTION_CHART_TOKENS.red, width: compact ? 2 : 2.5 },
        itemStyle: { color: INSPECTION_CHART_TOKENS.red, borderColor: '#fff', borderWidth: 1 },
        data: abnormals,
        markPoint: compact || peakIndex < 0 ? undefined : {
          symbol: 'pin',
          symbolSize: 34,
          label: { color: '#fff', fontFamily: FONT_FAMILY, fontSize: 10, formatter: `${peakValue}` },
          itemStyle: { color: INSPECTION_CHART_TOKENS.red },
          data: [{ coord: [peakIndex, peakValue], name: '异常峰值' }]
        }
      }
    ]
  }
}

export function buildResultCompositionOption(summary = {}) {
  const normal = Math.max(number(summary.normalCount), 0)
  const abnormal = Math.max(number(summary.abnormalCount), 0)
  const unknown = Math.max(number(summary.skippedCount), 0)
  const total = normal + abnormal + unknown
  const values = [
    { name: '正常', value: normal, color: INSPECTION_CHART_TOKENS.green },
    { name: '异常', value: abnormal, color: INSPECTION_CHART_TOKENS.red },
    { name: '未检测', value: unknown, color: INSPECTION_CHART_TOKENS.unknown }
  ]
  const visibleIndexes = values.map((item, index) => item.value > 0 ? index : -1).filter((index) => index >= 0)
  const firstVisible = visibleIndexes[0]
  const lastVisible = visibleIndexes[visibleIndexes.length - 1]
  return {
    animationDuration: 220,
    aria: { enabled: true },
    graphic: total ? [] : emptyGraphic('今日暂无巡检'),
    grid: { top: 32, right: 16, bottom: 42, left: 16 },
    tooltip: {
      trigger: 'item',
      appendToBody: true,
      formatter: (params) => `${params.seriesName}：${params.value} 次`
    },
    legend: {
      bottom: 2,
      left: 'center',
      itemWidth: 11,
      itemHeight: 7,
      textStyle: { color: INSPECTION_CHART_TOKENS.muted, fontFamily: FONT_FAMILY, fontSize: 11 }
    },
    xAxis: { type: 'value', max: total || 1, show: false },
    yAxis: { type: 'category', data: ['今日'], show: false },
    series: values.map((item, index) => ({
      name: item.name,
      type: 'bar',
      stack: 'result',
      barWidth: 28,
      itemStyle: {
        color: item.color,
        borderRadius: index === firstVisible && index === lastVisible
          ? 5
          : (index === firstVisible ? [5, 0, 0, 5] : (index === lastVisible ? [0, 5, 5, 0] : 0))
      },
      label: {
        show: total > 0 && item.value > 0,
        position: 'inside',
        color: '#fff',
        fontFamily: FONT_FAMILY,
        fontSize: 11,
        formatter: `${item.value}`
      },
      data: [item.value]
    }))
  }
}

export function normalizeToolHealthRows(rows = []) {
  return (Array.isArray(rows) ? rows : [])
    .map((item) => ({
      name: item.toolName || item.toolCode || '未命名工具',
      value: percent(item.healthRate),
      total: number(item.totalCount || item.recordCount || item.total)
    }))
    .sort((left, right) => left.value - right.value || right.total - left.total)
    .slice(0, 8)
}

export function buildToolHealthOption(rows = []) {
  const normalized = normalizeToolHealthRows(rows)
  return {
    animationDuration: 260,
    aria: { enabled: true },
    graphic: normalized.length ? [] : emptyGraphic('暂无工具健康数据'),
    grid: { top: 18, right: 52, bottom: 20, left: 126 },
    tooltip: axisTooltip((params = []) => {
      const row = normalized[params[0]?.dataIndex] || {}
      return `<strong>${row.name || '-'}</strong><br/>正常率：${row.value || 0}%<br/>检测次数：${row.total || 0}`
    }),
    xAxis: {
      type: 'value',
      min: 0,
      max: 100,
      axisLabel: { color: INSPECTION_CHART_TOKENS.faint, fontFamily: FONT_FAMILY, fontSize: 10, formatter: '{value}%' },
      splitLine: { lineStyle: { color: INSPECTION_CHART_TOKENS.grid, type: 'dashed' } }
    },
    yAxis: {
      type: 'category',
      inverse: true,
      data: normalized.map((item) => item.name),
      axisTick: { show: false },
      axisLine: { show: false },
      axisLabel: { color: INSPECTION_CHART_TOKENS.text, fontFamily: FONT_FAMILY, fontSize: 11, width: 108, overflow: 'truncate' }
    },
    series: [{
      name: '正常率',
      type: 'bar',
      barWidth: 11,
      showBackground: true,
      backgroundStyle: { color: '#f1f4f7', borderRadius: 6 },
      itemStyle: {
        borderRadius: 6,
        color: (params) => {
          const value = number(params.value)
          if (value < 80) return INSPECTION_CHART_TOKENS.red
          if (value < 95) return INSPECTION_CHART_TOKENS.amber
          return INSPECTION_CHART_TOKENS.green
        }
      },
      label: { show: true, position: 'right', color: INSPECTION_CHART_TOKENS.text, fontFamily: FONT_FAMILY, fontSize: 11, formatter: '{c}%' },
      markLine: {
        silent: true,
        symbol: 'none',
        lineStyle: { color: INSPECTION_CHART_TOKENS.amber, type: 'dashed', width: 1 },
        label: { formatter: '关注线 90%', color: INSPECTION_CHART_TOKENS.amber, fontFamily: FONT_FAMILY, fontSize: 10 },
        data: [{ xAxis: 90 }]
      },
      data: normalized.map((item) => item.value)
    }]
  }
}

export function buildAbnormalTopRows(rows = []) {
  const grouped = new Map()
  ;(Array.isArray(rows) ? rows : []).forEach((item) => {
    const name = item.stepName || item.toolName || item.targetName || '未命名子项'
    grouped.set(name, (grouped.get(name) || 0) + 1)
  })
  return Array.from(grouped.entries())
    .map(([name, value]) => ({ name, value }))
    .sort((left, right) => right.value - left.value || left.name.localeCompare(right.name, 'zh-Hans-CN'))
    .slice(0, 8)
}

export function buildAbnormalTopOption(rows = []) {
  const normalized = buildAbnormalTopRows(rows)
  return {
    animationDuration: 240,
    aria: { enabled: true },
    graphic: normalized.length ? [] : emptyGraphic('今日暂无异常'),
    grid: { top: 14, right: 38, bottom: 18, left: 118 },
    tooltip: axisTooltip((params = []) => `${params[0]?.name || '-'}<br/>异常次数：${params[0]?.value || 0}`),
    xAxis: {
      type: 'value',
      minInterval: 1,
      axisLabel: { color: INSPECTION_CHART_TOKENS.faint, fontFamily: FONT_FAMILY, fontSize: 10 },
      splitLine: { lineStyle: { color: INSPECTION_CHART_TOKENS.grid, type: 'dashed' } }
    },
    yAxis: {
      type: 'category',
      inverse: true,
      data: normalized.map((item) => item.name),
      axisTick: { show: false },
      axisLine: { show: false },
      axisLabel: { color: INSPECTION_CHART_TOKENS.text, fontFamily: FONT_FAMILY, fontSize: 11, width: 102, overflow: 'truncate' }
    },
    series: [{
      name: '异常次数',
      type: 'bar',
      barWidth: 12,
      showBackground: true,
      backgroundStyle: { color: '#f4f1f1', borderRadius: 6 },
      itemStyle: { color: INSPECTION_CHART_TOKENS.red, borderRadius: [0, 6, 6, 0] },
      label: { show: true, position: 'right', color: INSPECTION_CHART_TOKENS.red, fontFamily: FONT_FAMILY, fontSize: 11 },
      data: normalized.map((item) => item.value)
    }]
  }
}

export function buildCalendarHeatOption(calendar = {}) {
  const days = Array.isArray(calendar.days) ? calendar.days : []
  const monthKey = String(calendar.month || calendar.monthKey || days[0]?.date || '').slice(0, 7)
  const values = days.map((day) => {
    const total = number(day.total)
    const abnormal = number(day.abnormal)
    return {
      value: [day.date, total, abnormal],
      itemStyle: { borderColor: '#fff', borderWidth: 4, borderRadius: 5 },
      label: { color: total ? INSPECTION_CHART_TOKENS.ink : INSPECTION_CHART_TOKENS.faint }
    }
  })
  const normalValues = values.filter((item) => number(item.value[2]) === 0)
  const abnormalValues = values.filter((item) => number(item.value[2]) > 0)
  const maxNormal = Math.max(...normalValues.map((item) => number(item.value[1])), 1)
  const maxAbnormal = Math.max(...abnormalValues.map((item) => number(item.value[1])), 1)
  const label = {
    show: true,
    fontFamily: FONT_FAMILY,
    fontSize: 10,
    lineHeight: 14,
    formatter: (params) => {
      const [date, total, abnormal] = params.value || []
      const day = Number(String(date || '').slice(-2))
      if (!total) return `${day}`
      return abnormal > 0 ? `${day}\n${abnormal}异` : `${day}\n${total}次`
    }
  }
  return {
    animationDuration: 220,
    aria: { enabled: true },
    graphic: values.length ? [] : emptyGraphic('当月暂无日历数据'),
    tooltip: {
      appendToBody: true,
      formatter: (params) => {
        const [date, total, abnormal] = params.value || []
        const result = abnormal > 0 ? `${abnormal} 个异常` : (total > 0 ? '全部正常' : '无记录')
        return `<strong>${date || '-'}</strong><br/>巡检次数：${total || 0}<br/>结果：${result}`
      }
    },
    visualMap: [
      {
        show: false,
        min: 0,
        max: maxNormal,
        dimension: 1,
        seriesIndex: 0,
        inRange: { color: ['#f2f5f7', INSPECTION_CHART_TOKENS.greenSoft, INSPECTION_CHART_TOKENS.green] }
      },
      {
        show: false,
        min: 0,
        max: maxAbnormal,
        dimension: 1,
        seriesIndex: 1,
        inRange: { color: [INSPECTION_CHART_TOKENS.redSoft, '#e98989', INSPECTION_CHART_TOKENS.red] }
      }
    ],
    calendar: {
      top: 34,
      left: 42,
      right: 14,
      bottom: 16,
      range: monthKey || undefined,
      cellSize: ['auto', 38],
      splitLine: { show: false },
      itemStyle: { color: '#f7f9fb', borderWidth: 4, borderColor: '#fff' },
      dayLabel: { firstDay: 1, nameMap: ['日', '一', '二', '三', '四', '五', '六'], color: INSPECTION_CHART_TOKENS.muted, fontFamily: FONT_FAMILY, fontSize: 10 },
      monthLabel: { show: false },
      yearLabel: { show: false }
    },
    series: [
      { name: '正常或无记录', type: 'heatmap', coordinateSystem: 'calendar', data: normalValues, label },
      { name: '异常', type: 'heatmap', coordinateSystem: 'calendar', data: abnormalValues, label }
    ]
  }
}
