import request from '@/utils/request'

function stringifyConfig(value) {
  if (value === undefined || value === null || value === '') return value ?? ''
  return typeof value === 'string' ? value : JSON.stringify(value)
}

function normalizeTargetPayload(data = {}) {
  return {
    ...data,
    extraParams: stringifyConfig(data.extraParams)
  }
}

function normalizeTemplatePayload(data = {}) {
  return {
    ...data,
    steps: (data.steps || []).map((step) => ({
      ...step,
      stepParams: stringifyConfig(step.stepParams)
    }))
  }
}

function normalizePlanPayload(data = {}) {
  return {
    ...data,
    cronConfig: stringifyConfig(data.cronConfig)
  }
}

export function listAutoInspectionTools(query) {
  return request({ url: '/support/autoInspection/tools', method: 'get', params: query })
}

export function listAutoInspectionTargets(query) {
  return request({ url: '/support/autoInspection/targets', method: 'get', params: query })
}

export function getAutoInspectionTarget(targetId) {
  return request({ url: `/support/autoInspection/targets/${targetId}`, method: 'get' })
}

export function listAutoInspectionServerAssets() {
  return request({ url: '/support/autoInspection/targets/server-assets', method: 'get' })
}

export function getAutoInspectionServerCredential(serverId, username) {
  return request({ url: `/support/autoInspection/targets/server-credentials/${serverId}`, method: 'get', params: { username } })
}

export function batchAutoInspectionServerCredentials(serverIds, username) {
  return request({ url: '/support/autoInspection/targets/server-credentials/batch', method: 'post', data: { serverIds, username } })
}

export function addAutoInspectionTarget(data) {
  return request({ url: '/support/autoInspection/targets', method: 'post', data: normalizeTargetPayload(data) })
}

export function updateAutoInspectionTarget(data) {
  return request({ url: '/support/autoInspection/targets', method: 'put', data: normalizeTargetPayload(data) })
}

export function deleteAutoInspectionTarget(targetId) {
  return request({ url: `/support/autoInspection/targets/${targetId}`, method: 'delete' })
}

export function testAutoInspectionTarget(data) {
  return request({ url: '/support/autoInspection/targets/test', method: 'post', data, headers: { repeatSubmit: false, interval: 3000 } })
}

export function previewAutoInspectionTarget(data) {
  return request({ url: '/support/autoInspection/targets/preview', method: 'post', data, headers: { repeatSubmit: false, interval: 3000 } })
}

export function viewAutoInspectionTargetPlain(targetId) {
  return request({ url: `/support/autoInspection/targets/plain/${targetId}`, method: 'get' })
}

export function listAutoInspectionTemplates(query) {
  return request({ url: '/support/autoInspection/templates', method: 'get', params: query })
}

export function getAutoInspectionTemplate(templateId) {
  return request({ url: `/support/autoInspection/templates/${templateId}`, method: 'get' })
}

export function addAutoInspectionTemplate(data) {
  return request({ url: '/support/autoInspection/templates', method: 'post', data: normalizeTemplatePayload(data) })
}

export function updateAutoInspectionTemplate(data) {
  return request({ url: '/support/autoInspection/templates', method: 'put', data: normalizeTemplatePayload(data) })
}

export function copyAutoInspectionTemplate(templateId) {
  return request({ url: `/support/autoInspection/templates/${templateId}/copy`, method: 'post' })
}

export function deleteAutoInspectionTemplate(templateId) {
  return request({ url: `/support/autoInspection/templates/${templateId}`, method: 'delete' })
}

export function runAutoInspectionTemplate(templateId) {
  return request({ url: `/support/autoInspection/templates/${templateId}/run`, method: 'post', headers: { repeatSubmit: false, interval: 5000 } })
}

export function listAutoInspectionPlans(query) {
  return request({ url: '/support/autoInspection/plans', method: 'get', params: query })
}

export function getAutoInspectionPlan(planId) {
  return request({ url: `/support/autoInspection/plans/${planId}`, method: 'get' })
}

export function addAutoInspectionPlan(data) {
  return request({ url: '/support/autoInspection/plans', method: 'post', data: normalizePlanPayload(data) })
}

export function updateAutoInspectionPlan(data) {
  return request({ url: '/support/autoInspection/plans', method: 'put', data: normalizePlanPayload(data) })
}

export function changeAutoInspectionPlanStatus(data) {
  return request({ url: '/support/autoInspection/plans/status', method: 'put', data })
}

export function runAutoInspectionPlan(planId) {
  return request({ url: `/support/autoInspection/plans/${planId}/run`, method: 'post', headers: { repeatSubmit: false, interval: 5000 } })
}

export function deleteAutoInspectionPlan(planId) {
  return request({ url: `/support/autoInspection/plans/${planId}`, method: 'delete' })
}

export function getAutoInspectionDashboard(query) {
  return request({ url: '/support/autoInspection/dashboard', method: 'get', params: query })
}

export function listAutoInspectionRecords(query) {
  return request({ url: '/support/autoInspection/records', method: 'get', params: query })
}

export function getAutoInspectionRecord(recordId) {
  return request({ url: `/support/autoInspection/records/${recordId}`, method: 'get' })
}

// Compatibility names keep the mature inspection workspace decoupled from the
// resource-style URL migration introduced by the typed backend.
export const listAutoInspectionTool = listAutoInspectionTools
export const listAutoInspectionTarget = listAutoInspectionTargets
export const listAutoInspectionServerAssetTree = listAutoInspectionServerAssets
export const getAutoInspectionServerCredentialPlain = getAutoInspectionServerCredential
export const batchAutoInspectionServerCredentialPlain = batchAutoInspectionServerCredentials
export const delAutoInspectionTarget = deleteAutoInspectionTarget
export const listAutoInspectionTemplate = listAutoInspectionTemplates
export const delAutoInspectionTemplate = deleteAutoInspectionTemplate
export const listAutoInspectionPlan = listAutoInspectionPlans
export const delAutoInspectionPlan = deleteAutoInspectionPlan
export const listAutoInspectionRecord = listAutoInspectionRecords
