import request from './request'

export function listConversations() {
  return request.get('/qa/conversations')
}

export function getConversation(id) {
  return request.get(`/qa/conversations/${id}`)
}

export function deleteConversation(id) {
  return request.delete(`/qa/conversations/${id}`)
}

export function exportConversation(id, format = 'md') {
  return request.get(`/qa/conversations/${id}/export`, {
    params: { format },
    responseType: 'blob'
  })
}
