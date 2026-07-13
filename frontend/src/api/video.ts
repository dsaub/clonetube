const API = "/api/v1/video"

export interface VideoListItem {
  key: string
  size: number
  last_modified: string
  original_filename: string
}

export interface VideoListResponse {
  videos: VideoListItem[]
}

export interface StreamUrlResponse {
  url: string
  key: string
}

export async function listVideos(): Promise<VideoListItem[]> {
  const res = await fetch(`${API}/list`)
  if (!res.ok) {
    throw new Error(`Error al listar videos: ${await res.text()}`)
  }
  const data: VideoListResponse = await res.json()
  return data.videos
}

export async function getStreamUrl(key: string): Promise<string> {
  const params = new URLSearchParams({ key })
  const res = await fetch(`${API}/stream-url?${params}`)
  if (!res.ok) {
    throw new Error(`Error al obtener URL de streaming: ${await res.text()}`)
  }
  const data: StreamUrlResponse = await res.json()
  return data.url
}
