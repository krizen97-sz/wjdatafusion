import request from '@/utils/request'
const root = '/governance/connections'
const id = value => encodeURIComponent(String(value))
export const listGovernanceConnections = () => request({ url: root, method: 'get' })
export const saveGovernanceConnection = (key, data) => request({ url: key ? `${root}/${id(key)}` : root, method: key ? 'put' : 'post', data })
export const testGovernanceConnection = key => request({ url: `${root}/${id(key)}/test`, method: 'post', timeout: 30000 })
export const readGovernanceSnapshot = (key, data) => request({ url: `${root}/${id(key)}/snapshot`, method: 'post', data, timeout: 30000 })
