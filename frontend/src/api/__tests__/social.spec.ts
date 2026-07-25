import { beforeEach, describe, expect, it, vi } from 'vitest'
import { followUser, getFollowState, listFollowing, unfollowUser } from '@/api/social'

const mockFetch = vi.fn()
global.fetch = mockFetch

function jsonResponse(body: unknown) {
  return { ok: true, json: () => Promise.resolve(body) }
}

beforeEach(() => {
  mockFetch.mockReset()
})

describe('getFollowState', () => {
  it('reads the follow state without a token', async () => {
    mockFetch.mockResolvedValueOnce(
      jsonResponse({ username: 'ana', following: false, followers: 3 }),
    )

    const state = await getFollowState('ana')

    expect(state).toEqual({ username: 'ana', following: false, followers: 3 })
    expect(mockFetch).toHaveBeenCalledWith('/api/v1/users/ana/follow', {
      method: 'GET',
      headers: {},
    })
  })

  it('sends the bearer token when the visitor is logged in', async () => {
    mockFetch.mockResolvedValueOnce(
      jsonResponse({ username: 'ana', following: true, followers: 4 }),
    )

    await getFollowState('ana', 'jwt-123')

    expect(mockFetch).toHaveBeenCalledWith('/api/v1/users/ana/follow', {
      method: 'GET',
      headers: { Authorization: 'Bearer jwt-123' },
    })
  })

  it('escapes usernames with reserved characters', async () => {
    mockFetch.mockResolvedValueOnce(
      jsonResponse({ username: 'a/b', following: false, followers: 0 }),
    )

    await getFollowState('a/b')

    expect(mockFetch.mock.calls[0]?.[0]).toBe('/api/v1/users/a%2Fb/follow')
  })

  it('throws with the status code on failure', async () => {
    mockFetch.mockResolvedValueOnce({ ok: false, status: 404 })

    await expect(getFollowState('fantasma')).rejects.toThrow(
      'Error al consultar el seguimiento (404)',
    )
  })
})

describe('followUser / unfollowUser', () => {
  it('follows with POST and returns the new state', async () => {
    mockFetch.mockResolvedValueOnce(
      jsonResponse({ username: 'ana', following: true, followers: 1 }),
    )

    const state = await followUser('ana', 'jwt-123')

    expect(state.following).toBe(true)
    expect(mockFetch).toHaveBeenCalledWith('/api/v1/users/ana/follow', {
      method: 'POST',
      headers: { Authorization: 'Bearer jwt-123' },
    })
  })

  it('unfollows with DELETE', async () => {
    mockFetch.mockResolvedValueOnce(
      jsonResponse({ username: 'ana', following: false, followers: 0 }),
    )

    const state = await unfollowUser('ana', 'jwt-123')

    expect(state.following).toBe(false)
    expect(mockFetch.mock.calls[0]?.[1]).toMatchObject({ method: 'DELETE' })
  })

  it('reports errors when following fails', async () => {
    mockFetch.mockResolvedValueOnce({ ok: false, status: 400 })

    await expect(followUser('yo-mismo', 'jwt-123')).rejects.toThrow('Error al seguir al usuario')
  })
})

describe('listFollowing', () => {
  it('returns the followed users', async () => {
    const users = [{ id: 'u1', username: 'ana', full_name: 'Ana' }]
    mockFetch.mockResolvedValueOnce(jsonResponse({ users }))

    expect(await listFollowing('jwt-123')).toEqual(users)
    expect(mockFetch).toHaveBeenCalledWith('/api/v1/users/me/following', {
      headers: { Authorization: 'Bearer jwt-123' },
    })
  })

  it('throws when the request fails', async () => {
    mockFetch.mockResolvedValueOnce({ ok: false, status: 401 })

    await expect(listFollowing('jwt-123')).rejects.toThrow('Error al cargar a quién sigues (401)')
  })
})
