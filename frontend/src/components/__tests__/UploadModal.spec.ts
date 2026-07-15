import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import UploadModal from '@/components/UploadModal.vue'

const mockFetch = vi.fn()
global.fetch = mockFetch

beforeEach(() => {
  mockFetch.mockReset()
})

function createFile(name = 'video.mp4', size = 1024 * 1024) {
  return new File(['x'.repeat(size)], name, { type: 'video/mp4' })
}

describe('UploadModal.vue', () => {
  it('emits close when overlay is clicked', async () => {
    const wrapper = mount(UploadModal)
    await wrapper.find('.modal-overlay').trigger('click')
    expect(wrapper.emitted('close')).toBeTruthy()
  })

  it('emits close when close button is clicked', async () => {
    const wrapper = mount(UploadModal)
    await wrapper.find('.close-btn').trigger('click')
    expect(wrapper.emitted('close')).toBeTruthy()
  })

  it('shows idle state by default', () => {
    const wrapper = mount(UploadModal)
    expect(wrapper.find('.file-selector').exists()).toBe(true)
    expect(wrapper.find('.btn.primary').exists()).toBe(true)
  })

  it('disables upload button when no file is selected', () => {
    const wrapper = mount(UploadModal)
    const btn = wrapper.find('.btn.primary')
    expect(btn.attributes('disabled')).toBeDefined()
  })

  it('enables upload button after file selection', async () => {
    const wrapper = mount(UploadModal)
    const file = createFile()
    const input = wrapper.find('input[type="file"]')
    Object.defineProperty(input.element, 'files', { value: [file] })
    await input.trigger('change')
    const btn = wrapper.find('.btn.primary')
    expect(btn.attributes('disabled')).toBeUndefined()
  })

  it('handles file selection and shows file name', async () => {
    const wrapper = mount(UploadModal)
    const file = createFile('my-video.mp4')
    const input = wrapper.find('input[type="file"]')
    Object.defineProperty(input.element, 'files', { value: [file] })
    await input.trigger('change')
    expect(wrapper.text()).toContain('my-video.mp4')
  })

  it('shows uploading state when startUpload fails', async () => {
    mockFetch.mockRejectedValueOnce(new Error('Network error'))
    const wrapper = mount(UploadModal)
    const file = createFile()
    const input = wrapper.find('input[type="file"]')
    Object.defineProperty(input.element, 'files', { value: [file] })
    await input.trigger('change')
    await wrapper.find('.btn.primary').trigger('click')

    expect(wrapper.find('.result.error').exists()).toBe(true)
  })

  it('shows error state when start-multipart fails', async () => {
    mockFetch.mockResolvedValueOnce({ ok: false, text: () => Promise.resolve('Bad request') })
    const wrapper = mount(UploadModal)
    const file = createFile()
    const input = wrapper.find('input[type="file"]')
    Object.defineProperty(input.element, 'files', { value: [file] })
    await input.trigger('change')
    await wrapper.find('.btn.primary').trigger('click')

    const errorMsg = wrapper.find('.error-msg')
    expect(errorMsg.exists()).toBe(true)
  })

  it('completes full upload flow successfully', async () => {
    const uploadId = 'upload-123'
    const videoKey = 'videos/uuid-456'
    const etag = '"abc123"'

    mockFetch
      .mockResolvedValueOnce({
        ok: true,
        json: () => Promise.resolve({ uploadId, key: videoKey, original_filename: 'test.mp4' }),
      })
      .mockResolvedValueOnce({
        ok: true,
        json: () => Promise.resolve({ url: 'https://presigned.example.com/chunk1' }),
      })
      .mockResolvedValueOnce({
        ok: true,
        headers: new Headers({ ETag: etag }),
      })
      .mockResolvedValueOnce({
        ok: true,
        json: () => Promise.resolve({ location: 'https://s3.example.com/video.mp4', key: videoKey }),
      })

    const wrapper = mount(UploadModal)
    const file = createFile('test.mp4', 1024)
    const input = wrapper.find('input[type="file"]')
    Object.defineProperty(input.element, 'files', { value: [file] })
    await input.trigger('change')
    await wrapper.find('.btn.primary').trigger('click')

    await new Promise(r => setTimeout(r, 50))

    expect(wrapper.find('.result.success').exists()).toBe(true)
    expect(wrapper.text()).toContain('¡Video subido con éxito!')
  })
})
