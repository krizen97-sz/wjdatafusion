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

const designRoot = (flowId) => `${root}/flows/${resourceId(flowId)}/design`
export const getGovernanceDesign = (flowId) => request({ url: designRoot(flowId), method: 'get', timeout: 30000 })
export const listGovernanceDesignNodeTypes = () => request({ url: `${root}/design/node-types`, method: 'get' })
export const createGovernanceDesignNode = (flowId, data) => request({ url: `${designRoot(flowId)}/nodes`, method: 'post', data, timeout: 30000 })
export const updateGovernanceDesignNode = (flowId, nodeId, data) => request({ url: `${designRoot(flowId)}/nodes/${resourceId(nodeId)}`, method: 'put', data, timeout: 30000 })
export const deleteGovernanceDesignNode = (flowId, nodeId, version) => request({ url: `${designRoot(flowId)}/nodes/${resourceId(nodeId)}`, method: 'delete', params: { version }, timeout: 30000 })
export const createGovernanceDesignConnection = (flowId, data) => request({ url: `${designRoot(flowId)}/connections`, method: 'post', data, timeout: 30000 })
export const deleteGovernanceDesignConnection = (flowId, connectionId, version) => request({ url: `${designRoot(flowId)}/connections/${resourceId(connectionId)}`, method: 'delete', params: { version }, timeout: 30000 })
