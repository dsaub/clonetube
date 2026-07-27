import { beforeEach, describe, expect, it, vi } from 'vitest'
import {
  CHANNEL_PAGE_SIZE,
  channelPath,
  getChannel,
  listChannelVideos,
  usernameFromHandle,
} from '@/api/channel'

const mockFetch = vi.fn()
global.fetch = mockFetch

function jsonResponse(body: unknown) {
  return { ok: true, json: () => Promise.resolve(body) }
}

const channelBody = {
  id: 'user-1',
  username: 'ana',
  full_name: 'Ana Directo',
  following: true,
  followers: 12,
  following_count: 3,
  video_count: 25,
}

const videoBody = {
  videos: [
    {
      id: 'video-1',
      key: 'videos/uno.mp4',
      title: 'Primer vídeo',
      description: 'Una descripción',
      visibility: 'public',
      created_at: '2026-07-20T12:00:00+00:00',
      likes: 4,
    },
  ],
  page: 2,
  page_size: 20,
  total: 25,
  pages: 2,
}

beforeEach(() => {
  mockFetch.mockReset()
})

describe('channelPath', () => {
  it('builds the canonical channel URL with the at sign', () => {
    expect(channelPath('ana')).toBe('/channel/@ana')
  })

  it('encodes names that would break the URL', () => {
    expect(channelPath('ana perez')).toBe('/channel/@ana%20perez')
  })
})

describe('usernameFromHandle', () => {
  it('drops the leading at sign', () => {
    expect(usernameFromHandle('@ana')).toBe('ana')
  })

  it('accepts a handle without the at sign', () => {
    expect(usernameFromHandle('ana')).toBe('ana')
  })
})

describe('getChannel', () => {
  it('maps the API payload to camelCase', async () => {
    mockFetch.mockResolvedValueOnce(jsonResponse(channelBody))

    const channel = await getChannel('ana')

    expect(channel).toEqual({
      id: 'user-1',
      username: 'ana',
      fullName: 'Ana Directo',
      following: true,
      followers: 12,
      followingCount: 3,
      videoCount: 25,
    })
    expect(mockFetch).toHaveBeenCalledWith('/api/v1/users/ana/channel', { headers: {} })
  })

  it('sends the bearer token when the visitor is logged in', async () => {
    mockFetch.mockResolvedValueOnce(jsonResponse(channelBody))

    await getChannel('ana', 'jwt-123')

    expect(mockFetch).toHaveBeenCalledWith('/api/v1/users/ana/channel', {
      headers: { Authorization: 'Bearer jwt-123' },
    })
  })

  it('reports an unknown channel', async () => {
    mockFetch.mockResolvedValueOnce({ ok: false, status: 404 })

    await expect(getChannel('fantasma')).rejects.toThrow('No existe el canal (404)')
  })

  it('reports any other failure', async () => {
    mockFetch.mockResolvedValueOnce({ ok: false, status: 500 })

    await expect(getChannel('ana')).rejects.toThrow('Error al cargar el canal (500)')
  })
})

describe('listChannelVideos', () => {
  it('asks for the first page of twenty videos by default', async () => {
    mockFetch.mockResolvedValueOnce(jsonResponse(videoBody))

    await listChannelVideos('ana')

    expect(CHANNEL_PAGE_SIZE).toBe(20)
    expect(mockFetch).toHaveBeenCalledWith(
      '/api/v1/users/ana/videos?page=1&page_size=20',
      { headers: {} },
    )
  })

  it('requests the page it is given', async () => {
    mockFetch.mockResolvedValueOnce(jsonResponse(videoBody))

    const result = await listChannelVideos('ana', 2, 'jwt-123')

    expect(mockFetch).toHaveBeenCalledWith(
      '/api/v1/users/ana/videos?page=2&page_size=20',
      { headers: { Authorization: 'Bearer jwt-123' } },
    )
    expect(result).toEqual({
      videos: [
        {
          id: 'video-1',
          key: 'videos/uno.mp4',
          title: 'Primer vídeo',
          description: 'Una descripción',
          visibility: 'public',
          createdAt: '2026-07-20T12:00:00+00:00',
          likes: 4,
        },
      ],
      page: 2,
      pageSize: 20,
      total: 25,
      pages: 2,
    })
  })

  it('surfaces API failures', async () => {
    mockFetch.mockResolvedValueOnce({ ok: false, status: 503 })

    await expect(listChannelVideos('ana')).rejects.toThrow(
      'Error al cargar los vídeos del canal (503)',
    )
  })
})
