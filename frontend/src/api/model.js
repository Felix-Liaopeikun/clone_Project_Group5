import request from './request'

/**
 * 获取所有可用模型列表。
 */
export function listModels() {
  return request.get('/models')
}

/**
 * 获取当前激活模型。
 */
export function getActiveModel() {
  return request.get('/models/active')
}

/**
 * 切换当前激活模型。
 * @param {string} model 模型名称
 */
export function switchModel(model) {
  return request.post(`/models/switch?model=${encodeURIComponent(model)}`)
}
