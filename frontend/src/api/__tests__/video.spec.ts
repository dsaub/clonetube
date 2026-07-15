import { describe, it, expect, vi, beforeEach } from 'vitest'
import { listVideos, getStreamUrl } from '@/api/video'

const mockFetch = vi.fn()
global.fetch = mockFetch

beforeEach(() => {
  mockFetch.mockReset()
})

describe('listVideos', () => {
  it('returns video list on success', async () => {
    const fakeVideos = [
      { key: 'videos/abc', size: 1234, last_modified: '2025-01-01', original_filename: 'test.mp4' },
    ]
    mockFetch.mockResolvedValueOnce({
      ok: true,
      json: () => Promise.resolve({ videos: fakeVideos }),
    })

    const result = await listVideos()
    expect(result).toEqual(fakeVideos)
    expect(mockFetch).toHaveBeenCalledWith('/api/v1/video/list')
  })

  it('throws on error response', async () => {
    mockFetch.mockResolvedValueOnce({
      ok: false,
      text: () => Promise.resolve('Not found'),
    })

    await expect(listVideos()).rejects.toThrow('Error al listar videos')
  })
})

describe('getStreamUrl', () => {
  it('returns stream URL on success', async () => {
    mockFetch.mockResolvedValueOnce({
      ok: true,
      json: () => Promise.resolve({ url: 'https://stream.example.com/video.mp4', key: 'videos/abc' }),
    })

    const url = await getStreamUrl('videos/abc')
    expect(url).toBe('https://stream.example.com/video.mp4')
    expect(mockFetch).toHaveBeenCalledWith('/api/v1/video/stream-url?key=videos%2Fabc')
  })

  it('throws on error response', async () => {
    mockFetch.mockResolvedValueOnce({
      ok: false,
      text: () => Promise.resolve('Server error'),
    })

    await expect(getStreamUrl('videos/abc')).rejects.toThrow('Error al obtener URL de streaming')
  })
})
