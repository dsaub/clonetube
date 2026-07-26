import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import IndexView from '@/views/IndexView.vue'
import { useUserStore } from '@/stores/user'

const mockFetch = vi.fn()
global.fetch = mockFetch

const storedObjects = [
  { key: 'videos/a.mp4', size: 2048, last_modified: '2026-07-10T12:00:00Z', original_filename: 'a.mp4' },
  { key: 'videos/b.mp4', size: 4096, last_modified: '2026-07-24T12:00:00Z', original_filename: 'b.mp4' },
]

const feedItems = [
  {
    id: 'a',
    filename: 'videos/a.mp4',
    title: 'Emisión de quien sigo',
    description: '',
    author_id: 'user-1',
    author_username: 'ana',
    author_name: 'Ana',
    created_at: '2026-07-10T12:00:00Z',
    likes: 3,
    score: 4.9,
    from_followed_author: true,
  },
  {
    id: 'b',
    filename: 'videos/b.mp4',
    title: 'Emisión de un desconocido',
    description: '',
    author_id: 'user-2',
    author_username: 'bea',
    author_name: 'Bea',
    created_at: '2026-07-24T12:00:00Z',
    likes: 0,
    score: 1.9,
    from_followed_author: false,
  },
]

function jsonResponse(body: unknown) {
  return { ok: true, json: () => Promise.resolve(body) }
}

function respond(url: string) {
  if (url.startsWith('/api/v1/video/feed')) {
    const onlyFollowing = url.includes('only_following=true')
    const videos = onlyFollowing
      ? feedItems.filter((item) => item.from_followed_author)
      : feedItems
    return Promise.resolve(jsonResponse({
      videos,
      following_count: 1,
      personalized: true,
    }))
  }
  if (url.startsWith('/api/v1/video/list')) {
    return Promise.resolve(jsonResponse({ videos: storedObjects }))
  }
  return Promise.resolve(jsonResponse({ videos: [] }))
}

async function mountLoggedIn() {
  const pinia = createPinia()
  setActivePinia(pinia)
  const store = useUserStore()
  store.token = { access_token: 'jwt-123', token_type: 'bearer' }
  store.logged_in = true
  store.user = { id: 'user-9', username: 'espectadora', full_name: 'Espectadora', email: 'e@example.com' }

  const wrapper = mount(IndexView, {
    global: {
      plugins: [pinia],
      stubs: { RouterLink: { template: '<a><slot /></a>' } },
    },
  })
  await flushPromises()
  return wrapper
}

beforeEach(() => {
  localStorage.clear()
  mockFetch.mockReset()
  mockFetch.mockImplementation(respond)
})

describe('IndexView feed personalizado', () => {
  it('requests the personalized feed with the session token', async () => {
    await mountLoggedIn()

    const feedCall = mockFetch.mock.calls.find((call) => String(call[0]).startsWith('/api/v1/video/feed'))
    expect(feedCall?.[1]).toMatchObject({ headers: { Authorization: 'Bearer jwt-123' } })
  })

  it('keeps the backend order, so followed channels come first', async () => {
    const wrapper = await mountLoggedIn()

    const cards = wrapper.findAll('.video-card')
    expect(cards[0]?.text()).toContain('Emisión de quien sigo')
    expect(cards[1]?.text()).toContain('Emisión de un desconocido')
  })

  it('marks the videos coming from followed channels', async () => {
    const wrapper = await mountLoggedIn()

    const badges = wrapper.findAll('.following-pill')
    expect(badges).toHaveLength(1)
    expect(wrapper.findAll('.video-card')[0]?.find('.following-pill').exists()).toBe(true)
    expect(wrapper.text()).toContain('1 de tus canales seguidos encabezan tu feed.')
  })

  it('switches to the following tab and reloads only those videos', async () => {
    const wrapper = await mountLoggedIn()

    await wrapper.findAll('.feed-tab')[1]?.trigger('click')
    await flushPromises()

    const urls = mockFetch.mock.calls.map((call) => String(call[0]))
    expect(urls.some((url) => url.includes('only_following=true'))).toBe(true)
    expect(wrapper.findAll('.video-card')).toHaveLength(1)
    expect(wrapper.text()).toContain('Emisión de quien sigo')
  })

  it('does not reload when the active tab is clicked again', async () => {
    const wrapper = await mountLoggedIn()
    const callsBefore = mockFetch.mock.calls.length

    await wrapper.findAll('.feed-tab')[0]?.trigger('click')
    await flushPromises()

    expect(mockFetch.mock.calls).toHaveLength(callsBefore)
  })

  it('invites the user to follow channels when the following tab is empty', async () => {
    mockFetch.mockImplementation((url: string) => {
      if (url.startsWith('/api/v1/video/feed') && url.includes('only_following=true')) {
        return Promise.resolve(jsonResponse({ videos: [], following_count: 0, personalized: false }))
      }
      return respond(url)
    })

    const wrapper = await mountLoggedIn()
    await wrapper.findAll('.feed-tab')[1]?.trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('Todavía no sigues a nadie')
  })

  it('hides the tabs and skips the feed endpoint for anonymous visitors', async () => {
    const wrapper = mount(IndexView, {
      global: {
        plugins: [createPinia()],
        stubs: { RouterLink: { template: '<a><slot /></a>' } },
      },
    })
    await flushPromises()

    expect(wrapper.findAll('.feed-tab')).toHaveLength(0)
    const urls = mockFetch.mock.calls.map((call) => String(call[0]))
    expect(urls.some((url) => url.startsWith('/api/v1/video/feed'))).toBe(false)
  })
})
