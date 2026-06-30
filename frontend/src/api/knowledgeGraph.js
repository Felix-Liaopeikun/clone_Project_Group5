import request from './request'

/** 获取知识图谱数据 */
export function fetchKnowledgeGraph() {
  return request.get('/documents/knowledge-graph')
}
