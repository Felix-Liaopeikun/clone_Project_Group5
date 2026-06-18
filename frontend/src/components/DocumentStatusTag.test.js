/**
 * DocumentStatusTag 组件单元测试。
 */
import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import DocumentStatusTag from '@/components/DocumentStatusTag.vue'
import { nextTick } from 'vue'

const mountTag = (status) => {
  return mount(DocumentStatusTag, { props: { status } })
}

describe('DocumentStatusTag', () => {
  it('renders pending as warning tag', async () => {
    const w = mountTag('pending')
    await nextTick()
    const el = w.find('el-tag')
    expect(el.exists()).toBe(true)
    expect(el.text()).toContain('处理中')
  })

  it('renders processing as info tag', async () => {
    const w = mountTag('processing')
    await nextTick()
    const el = w.find('el-tag')
    expect(el.exists()).toBe(true)
    expect(el.text()).toContain('处理中')
  })

  it('renders done as success tag', async () => {
    const w = mountTag('done')
    await nextTick()
    const el = w.find('el-tag')
    expect(el.exists()).toBe(true)
    expect(el.text()).toContain('完成')
  })

  it('renders failed as danger tag', async () => {
    const w = mountTag('failed')
    await nextTick()
    const el = w.find('el-tag')
    expect(el.exists()).toBe(true)
    expect(el.text()).toContain('失败')
  })

  it('renders unknown as default tag', async () => {
    const w = mountTag('unknown_status')
    await nextTick()
    const el = w.find('el-tag')
    expect(el.exists()).toBe(true)
    expect(el.text()).toContain('unknown_status')
  })
})
