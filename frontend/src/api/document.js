import request from './request'

/**
 * 上传文档
 * @param {File} file
 * @returns {Promise<{id:number, status:string}>}
 */
export function uploadDocument(file) {
  const form = new FormData()
  form.append('file', file)
  return request.post('/documents/upload', form, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

/**
 * 分页查询文档
 * @param {{page?:number,size?:number,keyword?:string,category?:string}} params
 */
export function listDocuments(params = {}) {
  return request.get('/documents', { params })
}

/**
 * 文档详情
 * @param {number|string} id
 */
export function getDocument(id) {
  return request.get(`/documents/${id}`)
}

/**
 * 删除文档
 * @param {number|string} id
 */
export function deleteDocument(id) {
  return request.delete(`/documents/${id}`)
}

/**
 * 分类聚合（去重字符串数组）
 * @returns {Promise<string[]>}
 */
export function listCategories() {
  return request.get('/documents/categories')
}

/**
 * 检索
 * @param {{q:string, topK?:number}} params
 */
export function searchDocuments(params) {
  return request.get('/documents/search', { params })
}