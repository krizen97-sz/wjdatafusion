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

export function presentInspectionDate(value, referenceDate = new Date()) {
  const current = new Date(referenceDate)
  current.setHours(0, 0, 0, 0)
  const yesterday = new Date(current)
  yesterday.setDate(current.getDate() - 1)
  const dateKey = parseDateKey(value)
  const date = dateFromKey(dateKey)
  return {
    dateKey,
    label: displayDayLabel(dateKey, localDateKey(current), localDateKey(yesterday)),
    weekday: date ? WEEKDAY_LABELS[date.getDay()] : ''
  }
}

export function formatInspectionClock(value) {
  const match = /(?:\s|T)(\d{2}:\d{2})(?::\d{2})?/.exec(String(value || ''))
  return match?.[1] || '--:--'
}

export function groupInspectionRecordsByDay(rows = [], referenceDate = new Date()) {
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
      const presentation = presentInspectionDate(group.dateKey, referenceDate)
      return {
        ...group,
        label: presentation.label,
        weekday: presentation.weekday,
        status: group.abnormalCount > 0
          ? RESULT_ABNORMAL
          : (total > 0 && group.normalCount === total ? RESULT_NORMAL : RESULT_UNKNOWN),
        successRate: total ? `${Math.round((group.normalCount / total) * 100)}%` : '0%',
        records: [...group.records].sort((left, right) => String(right?.inspectionTime || '').localeCompare(String(left?.inspectionTime || '')))
      }
    })
}

export function buildInspectionRecordTableRows(rows = [], referenceDate = new Date()) {
  return groupInspectionRecordsByDay(rows, referenceDate).flatMap((group) => (
    group.records.map((record, index) => ({
      ...record,
      ownershipDateKey: group.dateKey,
      ownershipDateLabel: group.label,
      ownershipWeekday: group.weekday,
      ownershipRecordCount: group.records.length,
      ownershipNormalCount: group.normalCount,
      ownershipAbnormalCount: group.abnormalCount,
      ownershipSuccessRate: group.successRate,
      ownershipRowspan: index === 0 ? group.records.length : 0
    }))
  ))
}

export function buildLabelTreeOptions(items = [], options = {}) {
  const {
    idKey = 'id',
    nameKey = 'name',
    labelKey = 'labelName',
    uncategorizedLabel = '未分类'
  } = options
  const groups = new Map()

  ;(Array.isArray(items) ? items : []).forEach((item) => {
    const value = item?.[idKey]
    const name = String(item?.[nameKey] || '').trim()
    if (value === undefined || value === null || !name) return
    const labelName = String(item?.[labelKey] || '').trim() || uncategorizedLabel
    if (!groups.has(labelName)) groups.set(labelName, [])
    groups.get(labelName).push({
      value,
      label: name,
      isLeaf: true,
      source: item
    })
  })

  return Array.from(groups.entries())
    .sort(([left], [right]) => {
      if (left === uncategorizedLabel) return 1
      if (right === uncategorizedLabel) return -1
      return left.localeCompare(right, 'zh-Hans-CN')
    })
    .map(([labelName, children], index) => ({
      value: `label-directory-${index}`,
      label: labelName,
      isDirectory: true,
      children: children.sort((left, right) => left.label.localeCompare(right.label, 'zh-Hans-CN'))
    }))
}

export function collectLabelNames(...collections) {
  const labels = new Set()
  collections.flat().forEach((item) => {
    const labelName = String(item?.labelName || '').trim()
    if (labelName) labels.add(labelName)
  })
  return Array.from(labels).sort((left, right) => left.localeCompare(right, 'zh-Hans-CN'))
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
    { name: '未执行', value: unknown }
  ].filter((item) => item.value > 0)
}
