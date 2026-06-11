import request from '@/utils/request'

export function listTimInspection(query) {
  return request({ url: '/support/timInspection/list', method: 'get', params: query })
}

export function getTimInspection(inspectionId) {
  return request({ url: '/support/timInspection/' + inspectionId, method: 'get' })
}

export function runTimInspection() {
  return request({ url: '/support/timInspection/run', method: 'post', headers: { repeatSubmit: false, interval: 5000 } })
}

export function getTimInspectionConfig() {
  return request({ url: '/support/timInspection/config', method: 'get' })
}

export function updateTimInspectionItem(data) {
  return request({ url: '/support/timInspection/config/item', method: 'put', data })
}

export function listTimInspectionTarget(query) {
  return request({ url: '/support/timInspection/config/target/list', method: 'get', params: query })
}

export function getTimInspectionTarget(targetId) {
  return request({ url: '/support/timInspection/config/target/' + targetId, method: 'get' })
}

export function addTimInspectionTarget(data) {
  return request({ url: '/support/timInspection/config/target', method: 'post', data })
}

export function updateTimInspectionTarget(data) {
  return request({ url: '/support/timInspection/config/target', method: 'put', data })
}

export function delTimInspectionTarget(targetId) {
  return request({ url: '/support/timInspection/config/target/' + targetId, method: 'delete' })
}

export function testTimInspectionTarget(data) {
  return request({ url: '/support/timInspection/config/target/test', method: 'post', data, headers: { repeatSubmit: false, interval: 3000 } })
}

export function viewTimInspectionTargetPlain(targetId) {
  return request({ url: '/support/timInspection/config/target/plain/' + targetId, method: 'get' })
}

export function listTimInspectionPlan(query) {
  return request({ url: '/support/timInspection/plan/list', method: 'get', params: query })
}

export function getTimInspectionPlan(planId) {
  return request({ url: '/support/timInspection/plan/' + planId, method: 'get' })
}

export function getTimInspectionPlanTemplate() {
  return request({ url: '/support/timInspection/plan/template', method: 'get' })
}

export function addTimInspectionPlan(data) {
  return request({ url: '/support/timInspection/plan', method: 'post', data })
}

export function updateTimInspectionPlan(data) {
  return request({ url: '/support/timInspection/plan', method: 'put', data })
}

export function changeTimInspectionPlanStatus(data) {
  return request({ url: '/support/timInspection/plan/changeStatus', method: 'put', data })
}

export function runTimInspectionPlan(planId) {
  return request({ url: '/support/timInspection/plan/run/' + planId, method: 'post', headers: { repeatSubmit: false, interval: 5000 } })
}

export function delTimInspectionPlan(planId) {
  return request({ url: '/support/timInspection/plan/' + planId, method: 'delete' })
}
