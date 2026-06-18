import request from './request'

/**
 * 健康检查
 * @returns {Promise<{status:string, m3Reachable:boolean, m3Model:string}>}
 */
export function checkHealth() {
  return request.get('/health')
}