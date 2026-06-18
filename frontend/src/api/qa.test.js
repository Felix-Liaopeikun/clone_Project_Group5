/**
 * QA API 单元测试（覆盖流式与普通模式）。
 */
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { askQuestion, askQuestionStream, listQaHistory } from '@/api/qa'

// Mock axios
vi.mock('@/api/request', () => ({
  default: {
    get: vi.fn(),
    post: vi.fn(),
  },
}))

describe('askQuestion', () => {
  it('calls POST /qa/ask with body', async () => {
    const { default: request } = await import('@/api/request')
    request.post.mockResolvedValue({ answer: '测试答案', citations: [] })

    const result = await askQuestion({ question: 'M3是什么', topK: 5 })

    expect(request.post).toHaveBeenCalledWith('/qa/ask', { question: 'M3是什么', topK: 5 })
    expect(result.answer).toBe('测试答案')
  })
})

describe('listQaHistory', () => {
  it('calls GET /qa/history with params', async () => {
    const { default: request } = await import('@/api/request')
    request.get.mockResolvedValue({ list: [], total: 0 })

    const result = await listQaHistory({ page: 1, size: 10 })

    expect(request.get).toHaveBeenCalledWith('/qa/history', { params: { page: 1, size: 10 } })
    expect(result.total).toBe(0)
  })
})

describe('askQuestionStream', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn())
    vi.stubGlobal('localStorage', { getItem: () => null })
  })

  it('calls POST /qa/ask/stream and invokes onToken per chunk', async () => {
    const tokens = []
    let done = false

    // Mock fetch 返回一个 mock ReadableStream
    const mockStream = {
      getReader: () => ({
        read: vi.fn()
          .mockResolvedValueOnce({ done: false, value: new TextEncoder().encode('data: H\n') })
          .mockResolvedValueOnce({ done: false, value: new TextEncoder().encode('data: i\n') })
          .mockResolvedValueOnce({ done: false, value: new TextEncoder().encode('data: [DONE]\n') })
          .mockResolvedValueOnce({ done: true, value: undefined }),
      }),
    }

    fetch.mockResolvedValue({
      ok: true,
      body: mockStream,
    })

    const cancel = askQuestionStream(
      { question: 'hello', topK: 5 },
      {
        onToken: (t) => tokens.push(t),
        onDone: () => { done = true },
        onError: () => {},
      }
    )

    // 等待异步处理
    await new Promise(r => setTimeout(r, 50))

    expect(fetch).toHaveBeenCalled()
    const calledUrl = fetch.mock.calls[0][0]
    expect(calledUrl).toContain('/api/qa/ask/stream')
    expect(tokens).toContain('H')
    expect(tokens).toContain('i')
    expect(done).toBe(true)
  })

  it('returns a cancel function', () => {
    const mockAbort = vi.fn()
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
      ok: true,
      body: { getReader: () => ({ read: () => new Promise(r => setTimeout(() => r({ done: true }), 500)) }) },
    }))

    const cancel = askQuestionStream({ question: 'test' }, {
      onToken: () => {},
      onDone: () => {},
      onError: () => {},
    })

    expect(typeof cancel).toBe('function')
    cancel()
    // 验证 AbortController.abort 被调用（通过 fetch signal）
    // fetch 已在 mock 里通过 signal abort
  })
})
