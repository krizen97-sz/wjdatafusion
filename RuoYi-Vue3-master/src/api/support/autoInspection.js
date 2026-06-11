import request from '@/utils/request'

export function listAutoInspectionTool() {
  return request({ url: '/support/autoInspection/tool/list', method: 'get' })
}

export function listAutoInspectionTarget(query) {
  return request({ url: '/support/autoInspection/target/list', method: 'get', params: query })
}

export function getAutoInspectionTarget(targetId) {
  return request({ url: '/support/autoInspection/target/' + targetId, method: 'get' })
}

export function addAutoInspectionTarget(data) {
  return request({ url: '/support/autoInspection/target', method: 'post', data })
}

export function updateAutoInspectionTarget(data) {
  return request({ url: '/support/autoInspection/target', method: 'put', data })
}

export function delAutoInspectionTarget(targetId) {
  return request({ url: '/support/autoInspection/target/' + targetId, method: 'delete' })
}

export function testAutoInspectionTarget(data) {
  return request({ url: '/support/autoInspection/target/test', method: 'post', data, headers: { repeatSubmit: false, interval: 3000 } })
}

export function viewAutoInspectionTargetPlain(targetId) {
  return request({ url: '/support/autoInspection/target/plain/' + targetId, method: 'get' })
}

export function listAutoInspectionTemplate(query) {
  return request({ url: '/support/autoInspection/template/list', method: 'get', params: query })
}

export function getAutoInspectionTemplate(templateId) {
  return request({ url: '/support/autoInspection/template/' + templateId, method: 'get' })
}

export function addAutoInspectionTemplate(data) {
  return request({ url: '/support/autoInspection/template', method: 'post', data })
}

export function updateAutoInspectionTemplate(data) {
  return request({ url: '/support/autoInspection/template', method: 'put', data })
}

export function delAutoInspectionTemplate(templateId) {
  return request({ url: '/support/autoInspection/template/' + templateId, method: 'delete' })
}

export function runAutoInspectionTemplate(templateId) {
  return request({ url: '/support/autoInspection/template/run/' + templateId, method: 'post', headers: { repeatSubmit: false, interval: 5000 } })
}

export function listAutoInspectionPlan(query) {
  return request({ url: '/support/autoInspection/plan/list', method: 'get', params: query })
}

export function getAutoInspectionPlan(planId) {
  return request({ url: '/support/autoInspection/plan/' + planId, method: 'get' })
}

export function addAutoInspectionPlan(data) {
  return request({ url: '/support/autoInspection/plan', method: 'post', data })
}

export function updateAutoInspectionPlan(data) {
  return request({ url: '/support/autoInspection/plan', method: 'put', data })
}

export function changeAutoInspectionPlanStatus(data) {
  return request({ url: '/support/autoInspection/plan/changeStatus', method: 'put', data })
}

export function runAutoInspectionPlan(planId) {
  return request({ url: '/support/autoInspection/plan/run/' + planId, method: 'post', headers: { repeatSubmit: false, interval: 5000 } })
}

export function delAutoInspectionPlan(planId) {
  return request({ url: '/support/autoInspection/plan/' + planId, method: 'delete' })
}

export function listAutoInspectionRecord(query) {
  return request({ url: '/support/autoInspection/record/list', method: 'get', params: query })
}

export function getAutoInspectionRecord(recordId) {
  return request({ url: '/support/autoInspection/record/' + recordId, method: 'get' })
}
