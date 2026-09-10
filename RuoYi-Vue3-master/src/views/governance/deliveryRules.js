const states = {
  NOT_REQUESTED: { label: '未配置交付', type: 'info', active: false },
  SKIPPED_EMPTY: { label: '空批次，已跳过交付', type: 'info', active: false },
  QUEUED: { label: '等待交付', type: 'info', active: true },
  RUNNING: { label: '正在上传与校验', type: 'warning', active: true },
  DELIVERED: { label: '交付已确认', type: 'success', active: false },
  FAILED: { label: '交付未完成', type: 'danger', active: false, retry: true },
  RECOVERY_REQUIRED: { label: '中断待核实', type: 'warning', active: false, retry: true }
}
export const deliveryState = status => states[status] || { label: '状态未确认', type: 'info', active: false }
export const deliverableRun = run => run?.status === 'SUCCEEDED' && run.cleanupConfirmed === true && run.artifactsManifestAvailable === true && run.artifactCount > 0
