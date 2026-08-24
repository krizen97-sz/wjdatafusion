const TOOL_TARGET_TYPE_MAP = Object.freeze({
  KAFKA_LAG: 'KAFKA',
  KAFKA_TOPIC_ACTIVITY: 'KAFKA',
  KAFKA_CONSUMER_PROGRESS: 'KAFKA',
  MQTT_TOPIC_ACTIVITY: 'MQTT',
  HTTP_COUNT: 'HTTP',
  HTTP_HEALTH: 'HTTP',
  HTTP_API_TEST: 'HTTP',
  DATABASE_QUERY: 'DATABASE',
  FTP_FILE_COUNT: 'FTP',
  SERVER_FILE_COUNT: 'SERVER',
  SERVER_DISK: 'SERVER',
  BIG_DATA_SERVER_DISK: 'BIG_DATA_SERVER',
  TCP_PORT_CHECK: 'SERVER',
  SERVER_SERVICE_STATUS: 'SERVER'
})

const SUPPORTED_TARGET_TYPES = new Set([
  'KAFKA',
  'MQTT',
  'HTTP',
  'DATABASE',
  'FTP',
  'SERVER',
  'BIG_DATA_SERVER'
])

function normalizeCode(value) {
  return String(value || '').trim().toUpperCase()
}

export function resolveInspectionToolTargetType(toolCode, declaredTargetType = '') {
  const code = normalizeCode(toolCode)
  const localTargetType = TOOL_TARGET_TYPE_MAP[code] || ''
  const remoteTargetType = normalizeCode(declaredTargetType)
  if (remoteTargetType && !SUPPORTED_TARGET_TYPES.has(remoteTargetType)) return ''
  if (localTargetType && remoteTargetType && localTargetType !== remoteTargetType) return ''
  return remoteTargetType || localTargetType
}

export function getInspectionToolContractIssue(toolCode, declaredTargetType = '') {
  const code = normalizeCode(toolCode)
  if (!code) return '请选择巡检工具'
  const localTargetType = TOOL_TARGET_TYPE_MAP[code] || ''
  const remoteTargetType = normalizeCode(declaredTargetType)
  if (remoteTargetType && !SUPPORTED_TARGET_TYPES.has(remoteTargetType)) {
    return `巡检工具 ${code} 返回了不支持的目标类型 ${remoteTargetType}`
  }
  if (localTargetType && remoteTargetType && localTargetType !== remoteTargetType) {
    return `巡检工具 ${code} 的前后端配置类型不一致（前端 ${localTargetType}，后端 ${remoteTargetType}）`
  }
  if (!localTargetType && !remoteTargetType) {
    return `巡检工具 ${code} 尚未配置对应的数据来源表单`
  }
  return ''
}

export function getInspectionToolTargetTypeMap() {
  return { ...TOOL_TARGET_TYPE_MAP }
}
