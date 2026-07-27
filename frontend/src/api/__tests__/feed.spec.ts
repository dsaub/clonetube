import { beforeEach, describe, expect, it, vi } from 'vitest'
import {
  fetchFeed,
  listFeedVideos,
  loadHomeVideos,
  rankByFollowedAuthors,
  recencyScore,
  RECENCY_HALF_LIFE_HOURS,
  type FeedVideo,
} from '@/api/feed'

const mockFetch = vi.fn()
global.fetch = mockFetch

const NOW = new Date('2026-07-25T12:00:00Z')

function hoursAgo(hours: number): string {
  return new Date(NOW.getTime() - hours * 3_600_000).toISOString()
}

function feedApiItem(overrides: Record<string, unknown> = {}) {
  return {
    id: 'video-1',
    filename: 'videos/uno.mp4',
    title: 'Un video',
    description: '',
    author_id: 'user-1',
    author_username: 'ana',
    author_name: 'Ana',
    created_at: hoursAgo(1),
    likes: 0,
    score: 4.9,
    from_followed_author: true,
    ...overrides,
  }
}

function feedVideo(overrides: Partial<FeedVideo> = {}): FeedVideo {
  return {
    id: 'video-1',
    key: 'videos/uno.mp4',
    size: 100,
    last_modified: hoursAgo(1),
    original_filename: 'uno.mp4',
    title: 'Un video',
    description: '',
    author: { id: 'user-1', username: 'ana', displayName: 'Ana' },
    createdAt: hoursAgo(1),
    likes: 0,
    score: 0,
    fromFollowedAuthor: false,
    ...overrides,
  }
}

function jsonResponse(body: unknown) {
  return { ok: true, json: () => Promise.resolve(body) }
}

beforeEach(() => {
  mockFetch.mockReset()
})

describe('fetchFeed', () => {
  it('requests the plain feed without parameters', async () => {
    mockFetch.mockResolvedValueOnce(jsonResponse({ videos: [] }))

    await fetchFeed()

    expect(mockFetch).toHaveBeenCalledWith('/api/v1/video/feed', { headers: {} })
  })

  it('forwards the token, the limit and the only_following filter', async () => {
    mockFetch.mockResolvedValueOnce(jsonResponse({ videos: [] }))

    await fetchFeed('jwt-123', { onlyFollowing: true, limit: 10 })

    expect(mockFetch).toHaveBeenCalledWith('/api/v1/video/feed?only_following=true&limit=10', {
      headers: { Authorization: 'Bearer jwt-123' },
    })
  })

  it('throws with the status code on failure', async () => {
    mockFetch.mockResolvedValueOnce({ ok: false, status: 500 })

    await expect(fetchFeed()).rejects.toThrow('Error al cargar el feed (500)')
  })
})

describe('listFeedVideos', () => {
  it('keeps the backend order and merges the size stored in S3', async () => {
    mockFetch
      .mockResolvedValueOnce(jsonResponse({
        videos: [
          feedApiItem({ id: 'a', filename: 'videos/a.mp4', title: 'De quien sigo' }),
          feedApiItem({
            id: 'b',
            filename: 'videos/b.mp4',
            title: 'De un desconocido',
            from_followed_author: false,
            author_username: 'bea',
          }),
        ],
      }))
      .mockResolvedValueOnce(jsonResponse({
        videos: [
          { key: 'videos/a.mp4', size: 2048, last_modified: hoursAgo(3), original_filename: 'a.mp4' },
        ],
      }))

    const videos = await listFeedVideos('jwt-123')

    expect(videos.map((video) => video.title)).toEqual(['De quien sigo', 'De un desconocido'])
    expect(videos[0]).toMatchObject({
      key: 'videos/a.mp4',
      size: 2048,
      fromFollowedAuthor: true,
      author: { username: 'ana', displayName: 'Ana' },
    })
  })

  it('falls back to the publication date when the object is missing in S3', async () => {
    mockFetch
      .mockResolvedValueOnce(jsonResponse({ videos: [feedApiItem({ created_at: hoursAgo(5) })] }))
      .mockResolvedValueOnce(jsonResponse({ videos: [] }))

    const [video] = await listFeedVideos()

    expect(video?.size).toBe(0)
    expect(video?.last_modified).toBe(hoursAgo(5))
  })

  it('still returns the feed when listing S3 fails', async () => {
    mockFetch
      .mockResolvedValueOnce(jsonResponse({ videos: [feedApiItem()] }))
      .mockResolvedValueOnce({ ok: false, status: 500 })

    expect(await listFeedVideos()).toHaveLength(1)
  })
})

describe('loadHomeVideos', () => {
  it('uses the public catalog when there is no session', async () => {
    mockFetch.mockResolvedValue(jsonResponse({ videos: [] }))

    await loadHomeVideos()

    const urls = mockFetch.mock.calls.map((call) => call[0])
    expect(urls).not.toContain('/api/v1/video/feed')
    expect(urls).toContain('/api/v1/video/list')
  })

  it('returns nothing for the following tab without a session', async () => {
    expect(await loadHomeVideos(undefined, { onlyFollowing: true })).toEqual([])
    expect(mockFetch).not.toHaveBeenCalled()
  })

  it('falls back to the catalog ranked in the browser when the feed endpoint fails', async () => {
    mockFetch.mockImplementation((url: string) => {
      if (url.startsWith('/api/v1/video/feed')) return Promise.resolve({ ok: false, status: 500 })
      if (url.startsWith('/api/v1/video/list')) {
        return Promise.resolve(jsonResponse({
          videos: [
            { key: 'videos/a.mp4', size: 1, last_modified: hoursAgo(200), original_filename: 'a.mp4' },
            { key: 'videos/b.mp4', size: 1, last_modified: hoursAgo(1), original_filename: 'b.mp4' },
          ],
        }))
      }
      if (url.startsWith('/api/v1/video/catalog')) {
        return Promise.resolve(jsonResponse({
          videos: [
            {
              id: 'a', filename: 'videos/a.mp4', title: 'De quien sigo', description: '',
              author_id: 'user-1', author_username: 'ana', author_name: 'Ana',
            },
            {
              id: 'b', filename: 'videos/b.mp4', title: 'De un desconocido', description: '',
              author_id: 'user-2', author_username: 'bea', author_name: 'Bea',
            },
          ],
        }))
      }
      return Promise.resolve(jsonResponse({
        users: [{ id: 'user-1', username: 'ana', full_name: 'Ana' }],
      }))
    })

    const videos = await loadHomeVideos('jwt-123')

    expect(videos.map((video) => video.title)).toEqual(['De quien sigo', 'De un desconocido'])
    expect(videos[0]?.fromFollowedAuthor).toBe(true)
  })

  it('filters the fallback down to followed authors on the following tab', async () => {
    mockFetch.mockImplementation((url: string) => {
      if (url.startsWith('/api/v1/video/feed')) return Promise.resolve({ ok: false, status: 500 })
      if (url.startsWith('/api/v1/video/list')) {
        return Promise.resolve(jsonResponse({
          videos: [
            { key: 'videos/a.mp4', size: 1, last_modified: hoursAgo(2), original_filename: 'a.mp4' },
            { key: 'videos/b.mp4', size: 1, last_modified: hoursAgo(1), original_filename: 'b.mp4' },
          ],
        }))
      }
      if (url.startsWith('/api/v1/video/catalog')) {
        return Promise.resolve(jsonResponse({
          videos: [
            {
              id: 'a', filename: 'videos/a.mp4', title: 'De quien sigo', description: '',
              author_id: 'user-1', author_username: 'ana', author_name: 'Ana',
            },
            {
              id: 'b', filename: 'videos/b.mp4', title: 'De un desconocido', description: '',
              author_id: 'user-2', author_username: 'bea', author_name: 'Bea',
            },
          ],
        }))
      }
      return Promise.resolve(jsonResponse({
        users: [{ id: 'user-1', username: 'ana', full_name: 'Ana' }],
      }))
    })

    const videos = await loadHomeVideos('jwt-123', { onlyFollowing: true })

    expect(videos.map((video) => video.title)).toEqual(['De quien sigo'])
  })
})

describe('recencyScore', () => {
  it('scores a brand new video with 1', () => {
    expect(recencyScore(NOW.toISOString(), NOW)).toBeCloseTo(1)
  })

  it('halves the score after the half-life', () => {
    expect(recencyScore(hoursAgo(RECENCY_HALF_LIFE_HOURS), NOW)).toBeCloseTo(0.5)
  })

  it('never exceeds 1 for future dates', () => {
    const future = new Date(NOW.getTime() + 3_600_000).toISOString()
    expect(recencyScore(future, NOW)).toBeCloseTo(1)
  })

  it('returns 0 for an unparseable date', () => {
    expect(recencyScore('no-es-una-fecha', NOW)).toBe(0)
  })
})

describe('rankByFollowedAuthors', () => {
  it('puts followed authors first even if their videos are older', () => {
    const followed = feedVideo({
      key: 'videos/a.mp4',
      createdAt: hoursAgo(500),
      author: { id: 'user-1', username: 'ana', displayName: 'Ana' },
    })
    const stranger = feedVideo({
      key: 'videos/b.mp4',
      createdAt: hoursAgo(1),
      author: { id: 'user-2', username: 'bea', displayName: 'Bea' },
    })

    const ranked = rankByFollowedAuthors([stranger, followed], ['ana'], NOW)

    expect(ranked.map((video) => video.key)).toEqual(['videos/a.mp4', 'videos/b.mp4'])
    expect(ranked[0]?.fromFollowedAuthor).toBe(true)
    expect(ranked[1]?.fromFollowedAuthor).toBe(false)
  })

  it('falls back to recency when nobody is followed', () => {
    const old = feedVideo({ key: 'videos/a.mp4', createdAt: hoursAgo(100) })
    const fresh = feedVideo({ key: 'videos/b.mp4', createdAt: hoursAgo(1) })

    const ranked = rankByFollowedAuthors([old, fresh], [], NOW)

    expect(ranked.map((video) => video.key)).toEqual(['videos/b.mp4', 'videos/a.mp4'])
  })

  it('interleaves a prolific author so one channel does not take over', () => {
    const ana = { id: 'user-1', username: 'ana', displayName: 'Ana' }
    const bea = { id: 'user-2', username: 'bea', displayName: 'Bea' }
    const videos = [
      feedVideo({ key: 'videos/a1.mp4', createdAt: hoursAgo(1), author: ana }),
      feedVideo({ key: 'videos/a2.mp4', createdAt: hoursAgo(2), author: ana }),
      feedVideo({ key: 'videos/a3.mp4', createdAt: hoursAgo(3), author: ana }),
      feedVideo({ key: 'videos/b1.mp4', createdAt: hoursAgo(10), author: bea }),
    ]

    const ranked = rankByFollowedAuthors(videos, ['ana', 'bea'], NOW)

    expect(ranked.map((video) => video.author?.username)).toEqual(['ana', 'bea', 'ana', 'ana'])
  })

  it('never demotes a followed author below a stranger', () => {
    const ana = { id: 'user-1', username: 'ana', displayName: 'Ana' }
    const bea = { id: 'user-2', username: 'bea', displayName: 'Bea' }
    const videos = [
      feedVideo({ key: 'videos/b1.mp4', createdAt: hoursAgo(1), author: bea }),
      feedVideo({ key: 'videos/a1.mp4', createdAt: hoursAgo(400), author: ana }),
      feedVideo({ key: 'videos/a2.mp4', createdAt: hoursAgo(500), author: ana }),
      feedVideo({ key: 'videos/a3.mp4', createdAt: hoursAgo(600), author: ana }),
    ]

    const ranked = rankByFollowedAuthors(videos, ['ana'], NOW)

    expect(ranked.map((video) => video.fromFollowedAuthor)).toEqual([true, true, true, false])
  })

  it('is deterministic for videos with the same date', () => {
    const videos = [
      feedVideo({ key: 'videos/b.mp4', createdAt: hoursAgo(1), author: null }),
      feedVideo({ key: 'videos/a.mp4', createdAt: hoursAgo(1), author: null }),
    ]

    const forward = rankByFollowedAuthors(videos, [], NOW).map((video) => video.key)
    const backward = rankByFollowedAuthors([...videos].reverse(), [], NOW).map((v) => v.key)

    expect(forward).toEqual(['videos/a.mp4', 'videos/b.mp4'])
    expect(forward).toEqual(backward)
  })

  it('handles an empty list', () => {
    expect(rankByFollowedAuthors([], ['ana'], NOW)).toEqual([])
  })

  it('keeps every video exactly once', () => {
    const videos = Array.from({ length: 6 }, (_, index) =>
      feedVideo({ key: `videos/${index}.mp4`, createdAt: hoursAgo(index) }),
    )

    const ranked = rankByFollowedAuthors(videos, ['ana'], NOW)

    expect(new Set(ranked.map((video) => video.key)).size).toBe(6)
  })
})
