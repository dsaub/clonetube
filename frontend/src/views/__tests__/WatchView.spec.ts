import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createRouter, createWebHistory } from 'vue-router'
import WatchView from '@/views/WatchView.vue'

const mockFetch = vi.fn()
global.fetch = mockFetch

beforeEach(() => {
  mockFetch.mockReset()
})

function createRouterWithQuery(query: Record<string, string>) {
  const router = createRouter({
    history: createWebHistory(),
    routes: [{ path: '/watch', name: 'watch', component: WatchView }],
  })
  router.push({ path: '/watch', query })
  return router
}

describe('WatchView.vue', () => {
  it('shows loading state on mount', async () => {
    mockFetch.mockResolvedValueOnce({
      ok: true,
      json: () => Promise.resolve({ url: 'https://stream.example.com/video.mp4', key: 'videos/abc' }),
    })

    const router = createRouterWithQuery({ key: 'videos/abc' })
    const wrapper = mount(WatchView, {
      global: { plugins: [router] },
    })

    expect(wrapper.text()).toContain('Cargando video')
    await new Promise(r => setTimeout(r, 50))
  })

  it('renders VideoPlayer when stream URL is loaded', async () => {
    mockFetch.mockResolvedValueOnce({
      ok: true,
      json: () => Promise.resolve({ url: 'https://stream.example.com/video.mp4', key: 'videos/abc' }),
    })

    const router = createRouterWithQuery({ key: 'videos/abc' })
    const wrapper = mount(WatchView, {
      global: { plugins: [router] },
    })

    await new Promise(r => setTimeout(r, 50))

    const player = wrapper.findComponent({ name: 'VideoPlayer' })
    expect(player.exists()).toBe(true)
    expect(player.props('src')).toBe('https://stream.example.com/video.mp4')
  })

  it('shows error when no key is provided', async () => {
    const router = createRouterWithQuery({})
    const wrapper = mount(WatchView, {
      global: { plugins: [router] },
    })

    expect(wrapper.text()).toContain('No se especificó ningún video')
  })

  it('shows error when stream URL fetch fails', async () => {
    mockFetch.mockRejectedValueOnce(new Error('Not found'))

    const router = createRouterWithQuery({ key: 'videos/abc' })
    const wrapper = mount(WatchView, {
      global: { plugins: [router] },
    })

    await new Promise(r => setTimeout(r, 50))

    expect(wrapper.find('.state-box.error').exists()).toBe(true)
  })
})
