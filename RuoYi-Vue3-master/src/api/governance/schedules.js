import request from '@/utils/request'

const root = '/governance'
const resourceId = (id) => encodeURIComponent(String(id))
const write = (url, data, method = 'post') => request({ url, data, method, timeout: 30000, headers: { repeatSubmit: false } })

export const listGovernanceReleases = () => request({ url: `${root}/releases`, method: 'get' })
export const getGovernanceRelease = (id) => request({ url: `${root}/releases/${resourceId(id)}`, method: 'get' })
export const createGovernanceRelease = (data) => write(`${root}/releases`, data)
export const listGovernanceSchedules = () => request({ url: `${root}/schedules`, method: 'get' })
export const createGovernanceSchedule = (data) => write(`${root}/schedules`, data)
export const updateGovernanceSchedule = (id, data) => write(`${root}/schedules/${resourceId(id)}`, data, 'put')
export const changeGovernanceScheduleState = (id, data) => write(`${root}/schedules/${resourceId(id)}/state`, data)
export const runGovernanceSchedule = (id) => write(`${root}/schedules/${resourceId(id)}/run`)
export const recoverGovernanceSchedule = (id) => write(`${root}/schedules/${resourceId(id)}/recover`)

// Loaded only by the schedule editor; the server applies flow:edit and owner scope.
export const listGovernanceScheduleDeliveryTargets = () => request({ url: `${root}/ftp-connections`, method: 'get' })
