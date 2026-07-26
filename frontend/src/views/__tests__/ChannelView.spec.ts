import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createRouter, createWebHistory } from 'vue-router'
import { createPinia, setActivePinia } from 'pinia'
import ChannelView from '@/views/ChannelView.vue'
import FollowButton from '@/components/FollowButton.vue'
import { useUserStore } from '@/stores/user'

const mockFetch = vi.fn()
global.fetch = mockFetch

const TOTAL_VIDEOS = 25

function jsonResponse(body: unknown) {
  return { ok: true, json: () => Promise.resolve(body) }
}

function videosPage(page: number, total = TOTAL_VIDEOS) {
  const pageSize = 20
  const start = (page - 1) * pageSize
  const count = Math.max(Math.min(total - start, pageSize), 0)
  return {
    videos: Array.from({ length: count }, (_, index) => ({
      id: `video-${start + index + 1}`,
      key: `videos/${start + index + 1}.mp4`,
      title: `Vídeo ${start + index + 1}`,
      description: '',
      visibility: 'public',
      created_at: '2026-07-20T12:00:00+00:00',
      likes: 0,
    })),
    page,
    page_size: pageSize,
    total,
    pages: Math.max(Math.ceil(total / pageSize), 1),
  }
}

function respondWith(total = TOTAL_VIDEOS) {
  mockFetch.mockImplementation((url: string) => {
    if (url.includes('/channel')) {
      return Promise.resolve(jsonResponse({
        id: 'user-1',
        username: 'ana',
        full_name: 'Ana Directo',
        following: false,
        followers: 12,
        following_count: 2,
        video_count: total,
      }))
    }
    if (url.includes('/videos')) {
      const page = Number(new URL(url, 'http://test').searchParams.get('page') ?? 1)
      return Promise.resolve(jsonResponse(videosPage(page, total)))
    }
    return Promise.resolve(jsonResponse({ username: 'ana', following: false, followers: 12 }))
  })
}

function buildRouter() {
  return createRouter({
    history: createWebHistory(),
    routes: [
      { path: '/', name: 'index', component: { template: '<div />' } },
      { path: '/watch', name: 'watch', component: { template: '<div />' } },
      { path: '/studio', name: 'studio', component: { template: '<div />' } },
      { path: '/channel/:handle', name: 'channel', component: ChannelView },
    ],
  })
}

async function mountChannel(path = '/channel/@ana', viewer?: string) {
  const pinia = createPinia()
  setActivePinia(pinia)
  if (viewer) {
    const store = useUserStore()
    store.logged_in = true
    store.token = { access_token: 'jwt-123', token_type: 'bearer' }
    store.user = { id: 'user-1', username: viewer, full_name: 'Ana Directo', email: 'a@b.c' }
  }

  const router = buildRouter()
  await router.push(path)
  await router.isReady()

  const wrapper = mount(ChannelView, { global: { plugins: [router, pinia] } })
  await flushPromises()
  return { wrapper, router }
}

beforeEach(() => {
  localStorage.clear()
  mockFetch.mockReset()
  respondWith()
})

describe('ChannelView', () => {
  it('shows the channel identity and the video count', async () => {
    const { wrapper } = await mountChannel()

    expect(wrapper.text()).toContain('Ana Directo')
    expect(wrapper.text()).toContain('@ana')
    expect(wrapper.text()).toContain('25 vídeos')
  })

  it('renders the subscribe button for the channel', async () => {
    const { wrapper } = await mountChannel()

    const button = wrapper.findComponent(FollowButton)
    expect(button.exists()).toBe(true)
    expect(button.props('username')).toBe('ana')
  })

  it('paginates twenty videos per page', async () => {
    const { wrapper } = await mountChannel()

    expect(wrapper.findAll('.video-card')).toHaveLength(20)
    expect(wrapper.text()).toContain('Página 1 de 2')
    expect(wrapper.text()).toContain('Mostrando 1–20 de 25')
    expect(mockFetch).toHaveBeenCalledWith(
      '/api/v1/users/ana/videos?page=1&page_size=20',
      expect.anything(),
    )
  })

  it('loads the page written in the URL', async () => {
    const { wrapper } = await mountChannel('/channel/@ana?page=2')

    expect(wrapper.findAll('.video-card')).toHaveLength(5)
    expect(wrapper.text()).toContain('Página 2 de 2')
    expect(wrapper.text()).toContain('Mostrando 21–25 de 25')
  })

  it('moves to the next page and keeps it in the URL', async () => {
    const { wrapper, router } = await mountChannel()

    await wrapper.findAll('.page-button')[1]?.trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.query.page).toBe('2')
    expect(wrapper.findAll('.video-card')).toHaveLength(5)
  })

  it('jumps to a page from the number list', async () => {
    const { wrapper, router } = await mountChannel()

    const second = wrapper.findAll('.page-number')[1]
    await second?.trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.query.page).toBe('2')
    expect(wrapper.find('.page-number.current').text()).toBe('2')
  })

  it('disables the previous button on the first page', async () => {
    const { wrapper } = await mountChannel()

    const buttons = wrapper.findAll('.page-button')
    expect(buttons[0]?.attributes('disabled')).toBeDefined()
    expect(buttons[1]?.attributes('disabled')).toBeUndefined()
  })

  it('hides the pagination when a single page is enough', async () => {
    respondWith(4)

    const { wrapper } = await mountChannel()

    expect(wrapper.find('.pagination').exists()).toBe(false)
    expect(wrapper.findAll('.video-card')).toHaveLength(4)
  })

  it('shows an empty state for a channel without videos', async () => {
    respondWith(0)

    const { wrapper } = await mountChannel()

    expect(wrapper.text()).toContain('Este canal aún no tiene vídeos')
    expect(wrapper.find('.pagination').exists()).toBe(false)
  })

  it('invites the owner to upload from Studio', async () => {
    respondWith(0)

    const { wrapper } = await mountChannel('/channel/@ana', 'ana')

    expect(wrapper.text()).toContain('Sube tu primer vídeo desde Studio')
  })

  it('reports a channel that does not exist', async () => {
    mockFetch.mockResolvedValue({ ok: false, status: 404 })

    const { wrapper } = await mountChannel('/channel/@fantasma')

    expect(wrapper.text()).toContain('No existe el canal')
    expect(wrapper.find('.video-grid').exists()).toBe(false)
  })

  it('rewrites a handle without the at sign to the canonical URL', async () => {
    const { router } = await mountChannel('/channel/ana')

    expect(router.currentRoute.value.path).toBe('/channel/@ana')
  })

  it('links each video to its watch page', async () => {
    const { wrapper } = await mountChannel()

    const link = wrapper.find('a.video-card')
    expect(link.attributes('href')).toContain('/watch?key=videos/1.mp4')
  })
})
