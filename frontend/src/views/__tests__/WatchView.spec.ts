import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createRouter, createWebHistory } from 'vue-router'
import WatchView from '@/views/WatchView.vue'
import VideoPlayer from '@/components/VideoPlayer.vue'

const mockFetch = vi.fn()
global.fetch = mockFetch

beforeEach(() => {
  mockFetch.mockReset()
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

    const router = await createRouterWithQuery({ key: 'videos/abc' })
    const wrapper = mount(WatchView, {
      global: { plugins: [router] },
    })

    expect(wrapper.text()).toContain('Cargando video')
  })

  it('renders VideoPlayer when stream URL is loaded', async () => {
    mockFetch.mockResolvedValueOnce({
      ok: true,
      json: () => Promise.resolve({ url: 'https://stream.example.com/video.mp4', key: 'videos/abc' }),
    })

    const router = await createRouterWithQuery({ key: 'videos/abc' })
    const wrapper = mount(WatchView, {
      global: { plugins: [router] },
    })

    await flushPromises()

    expect(wrapper.findComponent(VideoPlayer).exists()).toBe(true)
    expect(wrapper.findComponent(VideoPlayer).props('src')).toBe('https://stream.example.com/video.mp4')
  })

  it('shows error when no key is provided', async () => {
    const router = await createRouterWithQuery({})
    const wrapper = mount(WatchView, {
      global: { plugins: [router] },
    })

    await flushPromises()

    expect(wrapper.text()).toContain('No se especificó ningún video')
  })

  it('shows error when stream URL fetch fails', async () => {
    mockFetch.mockRejectedValueOnce(new Error('Not found'))

    const router = await createRouterWithQuery({ key: 'videos/abc' })
    const wrapper = mount(WatchView, {
      global: { plugins: [router] },
    })

    await flushPromises()

    expect(wrapper.find('.state-box.error').exists()).toBe(true)
  })
})
