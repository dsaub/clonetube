import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import FollowButton from '@/components/FollowButton.vue'
import { useUserStore } from '@/stores/user'

const mockFetch = vi.fn()
global.fetch = mockFetch

function jsonResponse(body: unknown) {
  return { ok: true, json: () => Promise.resolve(body) }
}

function state(following: boolean, followers: number, username = 'ana') {
  return jsonResponse({ username, following, followers })
}

async function mountButton(username = 'ana') {
  const wrapper = mount(FollowButton, {
    props: { username },
    global: { plugins: [createPinia()] },
  })
  await flushPromises()
  return wrapper
}

function logIn(username = 'espectadora') {
  const pinia = createPinia()
  setActivePinia(pinia)
  const store = useUserStore()
  store.token = { access_token: 'jwt-123', token_type: 'bearer' }
  store.logged_in = true
  store.user = { id: 'user-9', username, full_name: 'Espectadora', email: 'e@example.com' }
  return pinia
}

async function mountLoggedIn(username = 'ana', viewer = 'espectadora') {
  const pinia = logIn(viewer)
  const wrapper = mount(FollowButton, {
    props: { username },
    global: { plugins: [pinia] },
  })
  await flushPromises()
  return wrapper
}

beforeEach(() => {
  localStorage.clear()
  mockFetch.mockReset()
})

describe('FollowButton', () => {
  it('shows the current state and the follower count', async () => {
    mockFetch.mockResolvedValueOnce(state(true, 12))

    const wrapper = await mountButton()

    expect(wrapper.find('.follow-button').text()).toBe('Siguiendo')
    expect(wrapper.text()).toContain('12 seguidores')
    expect(wrapper.find('.follow-button').attributes('aria-pressed')).toBe('true')
  })

  it('uses the singular for a single follower', async () => {
    mockFetch.mockResolvedValueOnce(state(false, 1))

    const wrapper = await mountButton()

    expect(wrapper.text()).toContain('1 seguidor')
    expect(wrapper.text()).not.toContain('1 seguidores')
  })

  it('asks anonymous visitors to log in instead of calling the API', async () => {
    mockFetch.mockResolvedValueOnce(state(false, 0))

    const wrapper = await mountButton()
    await wrapper.find('.follow-button').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('Inicia sesión para seguir a este canal.')
    expect(mockFetch).toHaveBeenCalledTimes(1)
  })

  it('follows the channel and updates the counter', async () => {
    mockFetch
      .mockResolvedValueOnce(state(false, 4))
      .mockResolvedValueOnce(state(true, 5))

    const wrapper = await mountLoggedIn()
    await wrapper.find('.follow-button').trigger('click')
    await flushPromises()

    expect(mockFetch.mock.calls[1]?.[1]).toMatchObject({ method: 'POST' })
    expect(wrapper.find('.follow-button').text()).toBe('Siguiendo')
    expect(wrapper.text()).toContain('5 seguidores')
    expect(wrapper.emitted('change')).toEqual([[true]])
  })

  it('unfollows when it was already following', async () => {
    mockFetch
      .mockResolvedValueOnce(state(true, 5))
      .mockResolvedValueOnce(state(false, 4))

    const wrapper = await mountLoggedIn()
    await wrapper.find('.follow-button').trigger('click')
    await flushPromises()

    expect(mockFetch.mock.calls[1]?.[1]).toMatchObject({ method: 'DELETE' })
    expect(wrapper.find('.follow-button').text()).toBe('Seguir')
    expect(wrapper.emitted('change')).toEqual([[false]])
  })

  it('surfaces API errors without changing the state', async () => {
    mockFetch
      .mockResolvedValueOnce(state(false, 4))
      .mockResolvedValueOnce({ ok: false, status: 400 })

    const wrapper = await mountLoggedIn()
    await wrapper.find('.follow-button').trigger('click')
    await flushPromises()

    expect(wrapper.find('.follow-error').text()).toContain('Error al seguir al usuario')
    expect(wrapper.find('.follow-button').text()).toBe('Seguir')
  })

  it('hides the button on your own channel but keeps the counter', async () => {
    mockFetch.mockResolvedValueOnce(state(false, 7, 'espectadora'))

    const wrapper = await mountLoggedIn('espectadora', 'espectadora')

    expect(wrapper.find('.follow-button').exists()).toBe(false)
    expect(wrapper.text()).toContain('7 seguidores')
  })

  it('reloads the state when the channel changes', async () => {
    mockFetch
      .mockResolvedValueOnce(state(true, 3))
      .mockResolvedValueOnce(state(false, 8, 'bea'))

    const wrapper = await mountButton()
    await wrapper.setProps({ username: 'bea' })
    await flushPromises()

    expect(mockFetch.mock.calls[1]?.[0]).toBe('/api/v1/users/bea/follow')
    expect(wrapper.text()).toContain('8 seguidores')
  })
})
