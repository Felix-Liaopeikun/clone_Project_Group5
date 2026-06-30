import request from './request'

/** 多模型并行对比 */
export function compareModels(data) {
  return request.post('/qa/compare', data)
}

/** 用户投票 */
export function voteComparison(id, winner) {
  return request.post(`/qa/compare/${id}/vote`, { winner })
}

/** 对比历史 */
export function listComparisonHistory(params) {
  return request.get('/qa/compare/history', { params })
}

/** 对比统计 */
export function fetchCompareStats() {
  return request.get('/qa/compare/stats')
}
