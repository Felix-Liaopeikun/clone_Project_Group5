/**
 * 格式化工具单元测试。
 */
import { describe, it, expect } from 'vitest'
import { relativeTime, formatFileSize } from '@/utils/format'

describe('relativeTime', () => {
  it('returns "刚刚" for recent time', () => {
    const now = new Date()
    expect(relativeTime(now.toISOString())).toBe('刚刚')
  })

  it('returns "N分钟前" for minutes ago', () => {
    const d = new Date(Date.now() - 5 * 60 * 1000)
    expect(relativeTime(d.toISOString())).toContain('分钟前')
  })

  it('returns "N小时前" for hours ago', () => {
    const d = new Date(Date.now() - 3 * 60 * 60 * 1000)
    expect(relativeTime(d.toISOString())).toContain('小时前')
  })

  it('returns "N天前" for days ago', () => {
    const d = new Date(Date.now() - 2 * 24 * 60 * 60 * 1000)
    expect(relativeTime(d.toISOString())).toContain('天前')
  })

  it('handles null gracefully', () => {
    expect(relativeTime(null)).toBe('')
  })

  it('handles invalid date', () => {
    expect(relativeTime('not-a-date')).toBe('')
  })
})

describe('formatFileSize', () => {
  it('returns 0 B for zero', () => {
    expect(formatFileSize(0)).toBe('0 B')
  })

  it('returns bytes for small files', () => {
    expect(formatFileSize(500)).toBe('500 B')
  })

  it('returns KB for kilobytes', () => {
    expect(formatFileSize(1024)).toMatch(/KB/)
    expect(formatFileSize(2048)).toMatch(/KB/)
  })

  it('returns MB for megabytes', () => {
    expect(formatFileSize(1024 * 1024)).toMatch(/MB/)
    expect(formatFileSize(5 * 1024 * 1024)).toMatch(/MB/)
  })

  it('returns GB for gigabytes', () => {
    expect(formatFileSize(1024 * 1024 * 1024)).toMatch(/GB/)
  })

  it('handles null gracefully', () => {
    expect(formatFileSize(null)).toBe('-')
    expect(formatFileSize(NaN)).toBe('-')
  })
})
