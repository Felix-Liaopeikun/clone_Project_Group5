import request from './request'

/** 运行自动评测 */
export function runEvaluation(params) {
  return request.post('/evaluation/run', null, { params })
}

/** 获取评测报告列表 */
export function listReports() {
  return request.get('/evaluation/reports')
}

/** 获取评测报告详情 */
export function getReport(id) {
  return request.get(`/evaluation/reports/${id}`)
}
