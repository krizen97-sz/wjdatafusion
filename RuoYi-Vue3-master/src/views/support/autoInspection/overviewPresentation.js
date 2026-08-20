const RESULT_NORMAL = '1'
const RESULT_ABNORMAL = '2'
const RESULT_UNKNOWN = '3'
const WEEKDAY_LABELS = ['周日', '周一', '周二', '周三', '周四', '周五', '周六']

function pad(value) {
  return String(value).padStart(2, '0')
}

function localDateKey(date) {
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}

function parseDateKey(value) {
  const match = /^(\d{4})-(\d{2})-(\d{2})/.exec(String(value || '').trim())
  return match ? `${match[1]}-${match[2]}-${match[3]}` : ''
}

function dateFromKey(dateKey) {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(dateKey)
  if (!match) return null
  const date = new Date(Number(match[1]), Number(match[2]) - 1, Number(match[3]))
  return Number.isNaN(date.getTime()) ? null : date
}

function displayDayLabel(dateKey, todayKey, yesterdayKey) {
  if (!dateKey) return '日期未记录'
  if (dateKey === todayKey) return '今天'
  if (dateKey === yesterdayKey) return '昨天'
  const date = dateFromKey(dateKey)
  return date ? `${date.getMonth() + 1}月${date.getDate()}日` : dateKey
}

export function formatInspectionClock(value) {
  const match = /(?:\s|T)(\d{2}:\d{2})(?::\d{2})?/.exec(String(value || ''))
  return match?.[1] || '--:--'
}

export function groupInspectionRecordsByDay(rows = [], referenceDate = new Date()) {
  const today = new Date(referenceDate)
  today.setHours(0, 0, 0, 0)
  const yesterday = new Date(today)
  yesterday.setDate(today.getDate() - 1)
  const todayKey = localDateKey(today)
  const yesterdayKey = localDateKey(yesterday)
  const groups = new Map()

  ;(Array.isArray(rows) ? rows : []).forEach((row) => {
    const dateKey = parseDateKey(row?.inspectionTime)
    const key = dateKey || 'unknown'
    if (!groups.has(key)) {
      groups.set(key, {
        key,
        dateKey,
        records: [],
        normalCount: 0,
        abnormalCount: 0,
        unknownCount: 0,
        manualCount: 0,
        autoCount: 0
      })
    }
    const group = groups.get(key)
    const status = String(row?.resultStatus || RESULT_UNKNOWN)
    group.records.push(row)
    if (status === RESULT_NORMAL) group.normalCount += 1
    else if (status === RESULT_ABNORMAL) group.abnormalCount += 1
    else group.unknownCount += 1
    if (row?.sourceType === 'MANUAL') group.manualCount += 1
    else group.autoCount += 1
  })

  return Array.from(groups.values())
    .sort((left, right) => {
      if (!left.dateKey) return 1
      if (!right.dateKey) return -1
      return right.dateKey.localeCompare(left.dateKey)
    })
    .map((group) => {
      const total = group.records.length
      const day = dateFromKey(group.dateKey)
      return {
        ...group,
        label: displayDayLabel(group.dateKey, todayKey, yesterdayKey),
        weekday: day ? WEEKDAY_LABELS[day.getDay()] : '',
        status: group.abnormalCount > 0
          ? RESULT_ABNORMAL
          : (total > 0 && group.normalCount === total ? RESULT_NORMAL : RESULT_UNKNOWN),
        successRate: total ? `${Math.round((group.normalCount / total) * 100)}%` : '0%',
        records: [...group.records].sort((left, right) => String(right?.inspectionTime || '').localeCompare(String(left?.inspectionTime || '')))
      }
    })
}

export function buildWeekResultDistribution(summary = {}) {
  const total = Number(summary.recordCount || 0)
  const normal = Math.max(Number(summary.normalCount || 0), 0)
  const abnormal = Math.max(Number(summary.abnormalCount || 0), 0)
  const unknown = Math.max(total - normal - abnormal, 0)
  if (!total) return [{ name: '暂无记录', value: 1, empty: true }]
  return [
    { name: '正常', value: normal },
    { name: '异常', value: abnormal },
    { name: '未检测', value: unknown }
  ].filter((item) => item.value > 0)
}
