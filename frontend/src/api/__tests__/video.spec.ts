import { describe, it, expect, vi, beforeEach } from 'vitest'
import {
  completeMultipart,
  getStreamUrl,
  listVideos,
  listVideosWithMetadata,
  signChunk,
  startMultipart,
  updateVideoMetadataByKey,
  uploadChunk,
} from '@/api/video'

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

describe('video catalog metadata', () => {
  it('joins stored objects with their title, description and author', async () => {
    mockFetch
      .mockResolvedValueOnce({
        ok: true,
        json: () => Promise.resolve({
          videos: [{
            key: 'videos/abc',
            size: 1234,
            last_modified: '2026-07-21',
            original_filename: 'original.mp4',
          }],
        }),
      })
      .mockResolvedValueOnce({
        ok: true,
        json: () => Promise.resolve({
          videos: [{
            id: 'video-1',
            filename: 'videos/abc',
            title: 'Título público',
            description: 'Una descripción',
            author_id: 'user-1',
            author_username: 'ana',
            author_name: 'Ana',
          }],
        }),
      })

    const result = await listVideosWithMetadata()

    expect(result[0]).toMatchObject({
      title: 'Título público',
      description: 'Una descripción',
      author: { username: 'ana', displayName: 'Ana' },
    })
    expect(mockFetch).toHaveBeenNthCalledWith(2, '/api/v1/video/catalog')
  })

  it('finds the uploaded video and updates its metadata with authentication', async () => {
    mockFetch
      .mockResolvedValueOnce({
        ok: true,
        json: () => Promise.resolve({
          videos: [{
            id: '08cb6579-48f8-44c8-845c-e783d86f7584',
            filename: 'videos/abc',
            title: 'original.mp4',
            description: '',
            author_id: 'user-1',
            author_username: 'ana',
            author_name: 'Ana',
          }],
        }),
      })
      .mockResolvedValueOnce({
        ok: true,
        json: () => Promise.resolve({
          id: '08cb6579-48f8-44c8-845c-e783d86f7584',
          key: 'videos/abc',
          size: 1234,
          last_modified: '2026-07-21',
          original_filename: 'original.mp4',
          title: 'Nuevo título',
          description: 'Nueva descripción',
          visibility: 'public',
          allowed_users: [],
        }),
      })

    const updated = await updateVideoMetadataByKey(
      'videos/abc',
      'Nuevo título',
      'Nueva descripción',
      'token-123',
    )

    expect(updated.title).toBe('Nuevo título')
    expect(mockFetch).toHaveBeenNthCalledWith(
      2,
      '/api/v1/video/08cb6579-48f8-44c8-845c-e783d86f7584',
      expect.objectContaining({
        method: 'PATCH',
        headers: {
          'Content-Type': 'application/json',
          Authorization: 'Bearer token-123',
        },
        body: JSON.stringify({
          title: 'Nuevo título',
          description: 'Nueva descripción',
          visibility: 'public',
          allowed_users: [],
        }),
      }),
    )
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

describe('multipart upload', () => {
  it('starts the upload through the proxy and sends the Bearer token', async () => {
    mockFetch.mockResolvedValueOnce({
      ok: true,
      json: () => Promise.resolve({
        uploadId: 'upload-1',
        key: 'videos/video-1.mp4',
        original_filename: 'mi video.mp4',
      }),
    })

    await startMultipart('mi video.mp4', 'token-123')

    expect(mockFetch).toHaveBeenCalledWith(
      '/api/v1/video/start-multipart?original_filename=mi+video.mp4',
      {
        method: 'POST',
        headers: { Authorization: 'Bearer token-123' },
      },
    )
  })

  it('signs and uploads a chunk preserving the returned ETag', async () => {
    mockFetch
      .mockResolvedValueOnce({
        ok: true,
        json: () => Promise.resolve({ url: 'https://storage.example/chunk' }),
      })
      .mockResolvedValueOnce({
        ok: true,
        headers: new Headers({ ETag: '"etag-1"' }),
      })

    const url = await signChunk('videos/video-1.mp4', 'upload-1', 1, 'token-123')
    const chunk = new Blob(['video data'])
    const part = await uploadChunk(url, chunk, 1)

    expect(mockFetch).toHaveBeenNthCalledWith(
      1,
      '/api/v1/video/sign-chunk?filename=videos%2Fvideo-1.mp4&upload_id=upload-1&chunk_number=1',
      { headers: { Authorization: 'Bearer token-123' } },
    )
    expect(mockFetch).toHaveBeenNthCalledWith(
      2,
      'https://storage.example/chunk',
      { method: 'PUT', body: chunk },
    )
    expect(part).toEqual({ PartNumber: 1, ETag: '"etag-1"' })
  })

  it('completes the multipart upload with the OpenAPI body shape', async () => {
    mockFetch.mockResolvedValueOnce({
      ok: true,
      json: () => Promise.resolve({
        status: 'completed',
        location: null,
        key: 'videos/video-1.mp4',
        original_filename: 'video.mp4',
      }),
    })

    const parts = [{ PartNumber: 1, ETag: '"etag-1"' }]
    await completeMultipart('videos/video-1.mp4', 'upload-1', parts, 'token-123')

    expect(mockFetch).toHaveBeenCalledWith('/api/v1/video/complete-multipart', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: 'Bearer token-123',
      },
      body: JSON.stringify({
        filename: 'videos/video-1.mp4',
        uploadId: 'upload-1',
        parts,
      }),
    })
  })
})
