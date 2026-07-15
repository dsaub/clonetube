import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import DevView from '@/views/DevView.vue'

const mockFetch = vi.fn()
global.fetch = mockFetch

beforeEach(() => {
  mockFetch.mockReset()
})

describe('DevView.vue', () => {
  it('renders the dev page', () => {
    mockFetch.mockResolvedValueOnce({ ok: true, json: () => Promise.resolve({ videos: [] }) })
    const wrapper = mount(DevView)
    expect(wrapper.find('.dev-page').exists()).toBe(true)
    expect(wrapper.find('.badge').text()).toBe('DEV')
  })

  it('renders upload button', () => {
    mockFetch.mockResolvedValueOnce({ ok: true, json: () => Promise.resolve({ videos: [] }) })
    const wrapper = mount(DevView)
    expect(wrapper.find('.btn-upload').exists()).toBe(true)
    expect(wrapper.text()).toContain('Subir video')
  })

  it('shows empty state when no videos', async () => {
    mockFetch.mockResolvedValueOnce({ ok: true, json: () => Promise.resolve({ videos: [] }) })
    const wrapper = mount(DevView)
    await new Promise(r => setTimeout(r, 50))
    expect(wrapper.text()).toContain('No hay videos subidos')
  })

  it('shows video list when videos exist', async () => {
    mockFetch.mockResolvedValueOnce({
      ok: true,
      json: () => Promise.resolve({
        videos: [
          { key: 'videos/abc', size: 1048576, last_modified: '2025-01-01', original_filename: 'test.mp4' },
        ],
      }),
    })
    const wrapper = mount(DevView)
    await new Promise(r => setTimeout(r, 50))
    expect(wrapper.text()).toContain('test.mp4')
    expect(wrapper.text()).toContain('1.0 MB')
  })

  it('shows error state when fetch fails', async () => {
    mockFetch.mockRejectedValueOnce(new Error('Network error'))
    const wrapper = mount(DevView)
    await new Promise(r => setTimeout(r, 50))
    expect(wrapper.find('.list-error').exists()).toBe(true)
  })

  it('opens modal when upload button is clicked', async () => {
    mockFetch.mockResolvedValueOnce({ ok: true, json: () => Promise.resolve({ videos: [] }) })
    const wrapper = mount(DevView)
    await wrapper.find('.btn-upload').trigger('click')
    const modal = wrapper.findComponent({ name: 'UploadModal' })
    expect(modal.exists()).toBe(true)
  })

  it('formatSize returns correct units', async () => {
    mockFetch.mockResolvedValueOnce({ ok: true, json: () => Promise.resolve({ videos: [] }) })
    const wrapper = mount(DevView)
    expect(wrapper.vm.formatSize(500)).toBe('500 B')
    expect(wrapper.vm.formatSize(2048)).toBe('2.0 KB')
    expect(wrapper.vm.formatSize(3145728)).toBe('3.0 MB')
    expect(wrapper.vm.formatSize(3221225472)).toBe('3.00 GB')
  })

  it('renders flow diagram', () => {
    mockFetch.mockResolvedValueOnce({ ok: true, json: () => Promise.resolve({ videos: [] }) })
    const wrapper = mount(DevView)
    expect(wrapper.find('.flow-diagram').exists()).toBe(true)
    expect(wrapper.findAll('.flow-step').length).toBe(4)
  })
})
