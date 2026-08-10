import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createRouter, createWebHistory } from 'vue-router'
import { createPinia } from 'pinia'
import WatchView from '@/views/WatchView.vue'
import VideoPlayer from '@/components/VideoPlayer.vue'

const mockFetch = vi.fn()
global.fetch = mockFetch

beforeEach(() => {
  mockFetch.mockReset()
  localStorage.clear()
})

async function createRouterWithQuery(query: Record<string, string>) {
  const router = createRouter({
    history: createWebHistory(),
    routes: [{ path: '/watch', name: 'watch', component: WatchView }],
  })
  await router.push({ path: '/watch', query })
  await router.isReady()
  return router
}

describe('WatchView.vue', () => {
  it('shows loading state on mount', async () => {
    mockFetch.mockResolvedValueOnce({
      ok: true,
      json: () => Promise.resolve({ url: 'https://stream.example.com/video.mp4', key: 'videos/abc' }),
    })

    const router = await createRouterWithQuery({ id: '42' })
    const wrapper = mount(WatchView, {
      global: { plugins: [router, createPinia()] },
    })

    expect(wrapper.text()).toContain('Cargando video')
  })

  it('renders VideoPlayer when stream URL is loaded', async () => {
    mockFetch.mockResolvedValueOnce({
      ok: true,
      json: () => Promise.resolve({ url: 'https://stream.example.com/video.mp4', key: 'videos/abc' }),
    })

    const router = await createRouterWithQuery({ id: '42' })
    const wrapper = mount(WatchView, {
      global: { plugins: [router, createPinia()] },
    })

    await flushPromises()

    expect(wrapper.findComponent(VideoPlayer).exists()).toBe(true)
    expect(wrapper.findComponent(VideoPlayer).props('src')).toBe('https://stream.example.com/video.mp4')
  })

  it('shows the title, description and uploader returned by the video detail API', async () => {
    mockFetch
      .mockResolvedValueOnce({
        ok: true,
        json: () => Promise.resolve({ url: 'https://stream.example.com/video.mp4', key: 'videos/abc' }),
      })
      .mockResolvedValueOnce({
        ok: true,
        json: () => Promise.resolve({
          id: '42',
          key: 'videos/abc',
          title: 'Una emisión especial',
          description: 'Descripción del contenido.',
          author_id: 'user-1',
          author_username: 'daniel',
          author_name: 'Daniel',
        }),
      })

    const router = await createRouterWithQuery({ id: '42' })
    const wrapper = mount(WatchView, {
      global: { plugins: [router, createPinia()] },
    })

    await flushPromises()

    expect(wrapper.find('#watch-video-title').text()).toBe('Una emisión especial')
    expect(wrapper.text()).toContain('Descripción del contenido.')
    expect(wrapper.text()).toContain('Daniel')
    expect(wrapper.text()).toContain('@daniel')
  })

  it('shows error when no id is provided', async () => {
    const router = await createRouterWithQuery({})
    const wrapper = mount(WatchView, {
      global: { plugins: [router, createPinia()] },
    })

    await flushPromises()

    expect(wrapper.text()).toContain('No se especificó ningún video')
  })

  it('shows error when stream URL fetch fails', async () => {
    mockFetch.mockRejectedValueOnce(new Error('Not found'))

    const router = await createRouterWithQuery({ id: '42' })
    const wrapper = mount(WatchView, {
      global: { plugins: [router, createPinia()] },
    })

    await flushPromises()

    expect(wrapper.find('.state-box.error').exists()).toBe(true)
  })
})
