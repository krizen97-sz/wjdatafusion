/** Server readiness is authoritative; managed process startup alone is not execution readiness. */
export function runtimePresentation(status = {}) {
  const managed = status.runtimeMode === 'managed'
  const ready = status.enabled === true && status.executionAvailable === true && (!managed || status.runtimeState === 'READY')
  const states = {
    DISABLED: ['内置引擎未启用', '请由管理员启用平台内置引擎后运行任务。', 'info'],
    STARTING: ['内置引擎准备中', '平台正在准备本机执行环境，状态会自动更新。', 'info'],
    FAILED: ['内置引擎启动失败', '请由管理员检查平台运行日志及本机引擎安装配置，处理后刷新状态。', 'error'],
    STOPPED: ['内置引擎已停止', '请由管理员恢复平台本机执行环境后刷新状态。', 'warning']
  }
  const unavailable = managed ? states[status.runtimeState] || ['内置引擎尚未就绪', '正在等待平台确认执行服务可用，请刷新状态。', 'info']
    : ['执行服务未连接', '请由管理员检查本机执行服务，连接恢复后刷新状态。', 'info']
  const label = ready ? managed ? '内置引擎已就绪' : '执行服务已连接' : unavailable[0]
  const guidance = ready ? '可预览节点或运行任务；数据源和节点配置仍需检查与测试。'
    : `${status.runtimeMessage || status.message || unavailable[1]} ${status.draftStorageAvailable === false ? '任务存储也不可用，请联系管理员。' : '可以先新建、导入和编辑任务，暂不能预览或运行。'}`
  return { ready, managed, label, guidance, type: ready ? 'success' : unavailable[2], poll: managed && status.runtimeState === 'STARTING' }
}
