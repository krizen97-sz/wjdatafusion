export const COMPARISON_SCOPE_CONTINUOUS = 'CONTINUOUS'
export const COMPARISON_SCOPE_DAY = 'DAY'
export const COMPARISON_SCOPE_HOUR = 'HOUR'

export const comparisonScopeOptions = Object.freeze([
  { label: '连续累计', value: COMPARISON_SCOPE_CONTINUOUS },
  { label: '每天重新累计', value: COMPARISON_SCOPE_DAY },
  { label: '每小时重新累计', value: COMPARISON_SCOPE_HOUR }
])

const validScopes = new Set(comparisonScopeOptions.map((item) => item.value))
const dailyPlaceholderTools = new Set(['HTTP_COUNT', 'DATABASE_QUERY'])
const dailyPlaceholders = ['${today}', '${todayStart}', '${todayEnd}', '${yyyyMMdd}']

export function containsDailyComparisonPlaceholder(value) {
  const text = JSON.stringify(value || {})
  return dailyPlaceholders.some((placeholder) => text.includes(placeholder))
}

export function inferComparisonScope(toolCode, context) {
  return dailyPlaceholderTools.has(toolCode) && containsDailyComparisonPlaceholder(context)
    ? COMPARISON_SCOPE_DAY
    : COMPARISON_SCOPE_CONTINUOUS
}

export function normalizeComparisonScope(value, toolCode = '', context = undefined) {
  return validScopes.has(value) ? value : inferComparisonScope(toolCode, context)
}
