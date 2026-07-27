import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import UploadModal from '@/components/UploadModal.vue'

const mockFetch = vi.fn()
global.fetch = mockFetch

beforeEach(() => {
  mockFetch.mockReset()
  localStorage.clear()
})

function createFile(name = 'video.mp4', size = 1024 * 1024) {
  return new File(['x'.repeat(size)], name, { type: 'video/mp4' })
}

function mountModal() {
  return mount(UploadModal, {
    global: {
      stubs: { Teleport: true },
    },
  })
}

describe('UploadModal.vue', () => {
  it('emits close when overlay is clicked', async () => {
    vi.useFakeTimers()
    const wrapper = mountModal()
    await wrapper.find('.tv-modal-backdrop').trigger('mousedown')
    await vi.runAllTimersAsync()
    expect(wrapper.emitted('close')).toBeTruthy()
    vi.useRealTimers()
  })

  it('emits close when close button is clicked', async () => {
    vi.useFakeTimers()
    const wrapper = mountModal()
    await wrapper.find('.tv-modal-close').trigger('click')
    await vi.runAllTimersAsync()
    expect(wrapper.emitted('close')).toBeTruthy()
    vi.useRealTimers()
  })

  it('shows idle state by default', () => {
    const wrapper = mountModal()
    expect(wrapper.find('.file-selector').exists()).toBe(true)
    expect(wrapper.find('.btn.primary').exists()).toBe(true)
  })

  it('disables upload button when no file is selected', () => {
    const wrapper = mountModal()
    const btn = wrapper.find('.btn.primary')
    expect(btn.attributes('disabled')).toBeDefined()
  })

  it('enables upload button after file selection', async () => {
    const wrapper = mountModal()
    const file = createFile()
    const input = wrapper.find('input[type="file"]')
    Object.defineProperty(input.element, 'files', { value: [file] })
    await input.trigger('change')
    const btn = wrapper.find('.btn.primary')
    expect(btn.attributes('disabled')).toBeUndefined()
  })

  it('handles file selection and shows file name', async () => {
    const wrapper = mountModal()
    const file = createFile('my-video.mp4')
    const input = wrapper.find('input[type="file"]')
    Object.defineProperty(input.element, 'files', { value: [file] })
    await input.trigger('change')
    expect(wrapper.text()).toContain('my-video.mp4')
    expect(wrapper.get<HTMLInputElement>('#upload-video-title').element.value).toBe('my-video')
  })

  it('shows uploading state when startUpload fails', async () => {
    mockFetch.mockRejectedValueOnce(new Error('Network error'))
    const wrapper = mountModal()
    const file = createFile()
    const input = wrapper.find('input[type="file"]')
    Object.defineProperty(input.element, 'files', { value: [file] })
    await input.trigger('change')
    await wrapper.find('.btn.primary').trigger('click')

    expect(wrapper.find('.result.error').exists()).toBe(true)
  })

  it('shows error state when start-multipart fails', async () => {
    mockFetch.mockResolvedValueOnce({ ok: false, text: () => Promise.resolve('Bad request') })
    const wrapper = mountModal()
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
        json: () => Promise.resolve({ PartNumber: 1, ETag: etag }),
      })
      .mockResolvedValueOnce({
        ok: true,
        json: () => Promise.resolve({ location: null, key: videoKey }),
      })

    const wrapper = mountModal()
    const file = createFile('test.mp4', 1024)
    const input = wrapper.find('input[type="file"]')
    Object.defineProperty(input.element, 'files', { value: [file] })
    await input.trigger('change')
    await wrapper.find('.btn.primary').trigger('click')

    await new Promise(r => setTimeout(r, 50))

    expect(wrapper.find('.result.success').exists()).toBe(true)
    expect(wrapper.text()).toContain('¡Video subido con éxito!')

    // El fragmento va a la API, no a un almacenamiento externo: sin esto el
    // navegador vuelve a intentar alcanzar el bucket y falla con un 404.
    const [chunkUrl, chunkInit] = mockFetch.mock.calls[1] ?? []
    expect(chunkUrl).toContain('/api/v1/video/upload-chunk')
    expect(chunkInit?.method).toBe('PUT')
  })

  it('saves the selected title and description after completing the upload', async () => {
    localStorage.setItem('token', 'token-123')
    const uploadId = 'upload-123'
    const videoKey = 'videos/uuid-456'

    mockFetch
      .mockResolvedValueOnce({
        ok: true,
        json: () => Promise.resolve({ uploadId, key: videoKey, original_filename: 'test.mp4' }),
      })
      .mockResolvedValueOnce({
        ok: true,
        json: () => Promise.resolve({ PartNumber: 1, ETag: '"abc123"' }),
      })
      .mockResolvedValueOnce({
        ok: true,
        json: () => Promise.resolve({ location: null, key: videoKey }),
      })
      .mockResolvedValueOnce({
        ok: true,
        json: () => Promise.resolve({
          videos: [{
            id: '08cb6579-48f8-44c8-845c-e783d86f7584',
            filename: videoKey,
            title: 'test.mp4',
            description: '',
            author_id: 'user-1',
            author_username: 'ana',
            author_name: 'Ana',
          }],
        }),
      })
      .mockResolvedValueOnce({
        ok: true,
        json: () => Promise.resolve({
          id: '08cb6579-48f8-44c8-845c-e783d86f7584',
          key: videoKey,
          title: 'Mi estreno',
          description: 'Una descripción elegida al subir.',
        }),
      })

    const wrapper = mountModal()
    const file = createFile('test.mp4', 1024)
    const input = wrapper.find('input[type="file"]')
    Object.defineProperty(input.element, 'files', { value: [file] })
    await input.trigger('change')
    await wrapper.get('#upload-video-title').setValue('Mi estreno')
    await wrapper.get('#upload-video-description').setValue('Una descripción elegida al subir.')
    await wrapper.find('.btn.primary').trigger('click')
    await new Promise(resolve => setTimeout(resolve, 50))

    expect(wrapper.find('.result.success').exists()).toBe(true)
    expect(wrapper.find('.metadata-warning').exists()).toBe(false)
    const [updateUrl, updateInit] = mockFetch.mock.calls[4] ?? []
    expect(updateUrl).toBe('/api/v1/video/08cb6579-48f8-44c8-845c-e783d86f7584')
    expect(updateInit?.method).toBe('PATCH')
    expect(JSON.parse(updateInit?.body as string)).toEqual({
      title: 'Mi estreno',
      description: 'Una descripción elegida al subir.',
      visibility: 'public',
      allowed_users: [],
    })
  })
})
