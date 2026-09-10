const labels = {
  RECEIVING: ['正在取批', 'warning'], RECEIVED: ['待执行', 'info'], EMPTY: ['空源批次', 'info'],
  EXECUTION_PLANNED: ['执行已保留', 'warning'], EXECUTION_ATTACHED: ['已关联运行', 'info'],
  COMMITTING: ['正在确认', 'warning'], COMMITTED: ['位点已确认', 'success'], RELEASED: ['已放弃取批', 'info'],
  FAILED: ['取批失败', 'danger'], COMMIT_UNKNOWN: ['确认结果待核查', 'warning'], OFFSET_CONFLICT: ['位点冲突', 'danger'], COMMIT_BLOCKED: ['确认被阻止', 'warning'],
  RUN_PLANNED: ['准备执行', 'info'], RUNNING: ['处理中', 'warning'], DELIVERING: ['正在交付', 'warning'], READY_TO_ACK: ['处理完成，待确认位点', 'success'], RECOVERY_REQUIRED: ['需要核查', 'warning']
}
export const kafkaState = value => ({ label: labels[value]?.[0] || '状态未确认', type: labels[value]?.[1] || 'info' })
export const kafkaExecutionState = (receipt, execution) => {
  if (receipt?.status === 'COMMITTED' && execution?.status === 'READY_TO_ACK') return { label: '处理完成，位点已确认', type: 'success' }
  if (execution?.status === 'FAILED') return { label: '处理失败', type: 'danger' }
  return kafkaState(execution?.status)
}
export const canExecuteReceipt = value => value?.leaseHeld === true && !value.runId && ['RECEIVED', 'EMPTY'].includes(value.status)
export const canReleaseReceipt = value => value?.leaseHeld === true && !value.runId && ['RECEIVED', 'EMPTY', 'FAILED'].includes(value.status)
export const canAcknowledgeReceipt = (receipt, execution) => receipt?.leaseHeld === true && (execution?.status === 'READY_TO_ACK' || ['COMMIT_UNKNOWN', 'COMMIT_BLOCKED'].includes(receipt?.status))
