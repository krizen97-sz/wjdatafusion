import request from '@/utils/request'

export const createHdiSession = (renew = false) => request({
  url: '/governance/hdi/session',
  method: 'post',
  data: { renew: renew === true },
  timeout: 30000,
  headers: { repeatSubmit: false }
})

const runUrl = id => `/governance/hdi/runs/${encodeURIComponent(id)}`
export const listHdiRuns = taskId => request({ url: `/governance/hdi/tasks/${encodeURIComponent(taskId)}/runs`, method: 'get' })
export const getHdiRun = id => request({ url: runUrl(id), method: 'get' })
export const getHdiArtifacts = id => request({ url: `${runUrl(id)}/artifacts`, method: 'get' })
export const getHdiLogs = (id, cursor = 0) => request({ url: `${runUrl(id)}/logs`, method: 'get', params: { cursor, limit: 32768 } })
export const downloadHdiArtifact = (id, artifactId) => request({ url: `${runUrl(id)}/artifacts/${encodeURIComponent(artifactId)}/download`, method: 'get', responseType: 'blob' })
