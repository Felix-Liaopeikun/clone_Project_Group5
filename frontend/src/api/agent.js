import request from './request'

/** 执行智能体推理 */
export function executeAgent(data) {
  return request.post('/qa/agent', data)
}

/** 获取可用工具列表 */
export function listAgentTools() {
  return request.get('/qa/agent/tools')
}
