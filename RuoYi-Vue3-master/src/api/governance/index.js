import request from '@/utils/request'

const root = '/governance'
const resourceId = (id) => encodeURIComponent(String(id))

export const getGovernanceOverview = () => request({ url: `${root}/overview`, method: 'get' })
export const listGovernanceCatalog = () => request({ url: `${root}/catalog`, method: 'get' })
export const listGovernanceProjects = () => request({ url: `${root}/projects`, method: 'get' })
export const createGovernanceProject = (data) => request({ url: `${root}/projects`, method: 'post', data, timeout: 30000 })
export const listGovernanceFlows = (projectId) => request({ url: `${root}/flows`, method: 'get', params: { projectId } })
export const createGovernanceFlow = (data) => request({ url: `${root}/flows`, method: 'post', data, timeout: 30000 })
export const listGovernanceTemplates = () => request({ url: `${root}/templates`, method: 'get' })
export const startGovernanceTest = (id, data) => request({
  url: `${root}/flows/${resourceId(id)}/tests`, method: 'post', data,
  headers: { repeatSubmit: false }, timeout: 30000
})
export const listGovernanceTestRuns = (flowId) => request({ url: `${root}/test-runs`, method: 'get', params: { flowId } })
export const getGovernanceTestRun = (id) => request({ url: `${root}/test-runs/${resourceId(id)}`, method: 'get' })
export const cancelGovernanceTestRun = (id) => request({ url: `${root}/test-runs/${resourceId(id)}/cancel`, method: 'post' })
