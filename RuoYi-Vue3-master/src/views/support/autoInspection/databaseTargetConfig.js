const DEFAULT_DATABASE_CONFIG = Object.freeze({
  databaseType: 'MYSQL',
  query: '',
  resultMode: 'FIRST_VALUE'
})

function parseObject(value) {
  if (!value) return {}
  if (typeof value === 'object' && !Array.isArray(value)) return value
  try {
    const parsed = JSON.parse(value)
    return parsed && typeof parsed === 'object' && !Array.isArray(parsed) ? parsed : {}
  } catch {
    return {}
  }
}

export function normalizeDatabaseTargetConfig(target = {}) {
  const persisted = parseObject(target.extraParams)
  const edited = parseObject(target.databaseConfig)
  const config = {
    ...DEFAULT_DATABASE_CONFIG,
    ...persisted,
    ...edited
  }
  return {
    databaseType: String(config.databaseType || DEFAULT_DATABASE_CONFIG.databaseType).toUpperCase(),
    query: String(config.query || ''),
    resultMode: String(config.resultMode || DEFAULT_DATABASE_CONFIG.resultMode).toUpperCase()
  }
}

export function hydrateDatabaseTarget(target = {}, defaults = {}) {
  return {
    ...defaults,
    ...target,
    databaseConfig: normalizeDatabaseTargetConfig(target),
    _passwordVisible: false
  }
}
