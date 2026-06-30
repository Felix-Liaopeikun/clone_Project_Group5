import request from './request'

/**
 * 提问
 * @param {{question:string, topK?:number, model?:string}} body
 */
export function askQuestion(body) {
  return request.post('/qa/ask', body)
}

/**
 * 流式提问 — 返回一个 ReadableStream，逐 token yield。
 * @param {{question:string, topK?:number}} body
 * @param {(token:string)=>void} onToken  每个 token 到达时的回调
 * @param {()=>void} onDone         流结束时的回调
 * @param {(err:Error)=>void} onError   错误回调
 * @returns {()=>void} cancel 函数，调用可中断请求
 */
export function askQuestionStream(body, { onToken, onDone, onError }) {
  const apiBase = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8081'
  const url = `${apiBase}/api/qa/ask/stream`

  const controller = new AbortController()
  // 从 localStorage 读 JWT token（与 request.js axios 拦截器保持一致）
  const token = localStorage.getItem('auth_token')

  fetch(url, {
    method: 'POST',
    signal: controller.signal,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { 'Authorization': `Bearer ${token}` } : {}),
    },
    body: JSON.stringify(body),
  })
    .then(async res => {
      if (!res.ok) {
        // 尝试解析后端错误消息
        let msg = `HTTP ${res.status}`
        try {
          const json = await res.json()
          if (json?.message) msg = json.message
        } catch { /* ignore */ }
        throw Object.assign(new Error(msg), { status: res.status })
      }
      const reader = res.body.getReader()
      const decoder = new TextDecoder('utf-8')
      let buffer = ''

      function pump() {
        reader.read().then(({ done, value }) => {
          if (done) {
            onDone()
            return
          }
          buffer += decoder.decode(value, { stream: true })
          // 逐行处理 SSE data: 事件
          const lines = buffer.split('\n')
          buffer = lines.pop() // 最后一个可能不完整，留着
          for (const line of lines) {
            const trimmed = line.trim()
            if (trimmed.startsWith('data: ')) {
              const data = trimmed.slice('data: '.length).trim()
              if (data === '[DONE]') {
                onDone()
                return
              }
              // 直接把原始 token 发给回调（token 就是 M3 返回的增量文本）
              onToken(data)
            }
          }
          pump()
        }).catch(onError)
      }
      pump()
    })
    .catch(err => {
      if (err.name !== 'AbortError') {
        onError(err)
      }
    })

  // 返回取消函数
  return () => controller.abort()
}

/**
 * 分页查询问答历史
 * @param {{page?:number, size?:number}} params
 */
export function listQaHistory(params = {}) {
  return request.get('/qa/history', { params })
}

/**
 * 评测问答回答
 * @param {number} id 问答历史 ID
 * @param {{rating?:number, useful?:boolean, feedback?:string}} body
 */
export function evaluateAnswer(id, body) {
  return request.post(`/qa/evaluate/${id}`, body)
}

/**
 * 删除问答历史记录
 * @param {number} id 问答历史 ID
 */
export function deleteQaHistory(id) {
  return request.delete(`/qa/history/${id}`)
}

/**
 * AI 自动评测问答回答
 * @param {number} id 问答历史 ID
 * @param {string} [model] 可选模型名
 */
export function autoEvaluateAnswer(id, model) {
  const params = model ? { model } : {}
  return request.post(`/qa/evaluate/auto/${id}`, null, { params })
}

/**
 * 获取评测统计数据
 */
export function fetchEvaluationStats() {
  return request.get('/qa/evaluation-stats')
}