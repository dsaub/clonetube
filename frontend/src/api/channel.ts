import type { VideoVisibility } from '@/api/video'

const API = '/api/v1/users'

/** Vídeos por página del canal (el backend usa el mismo valor por defecto). */
export const CHANNEL_PAGE_SIZE = 20

export interface Channel {
  id: string
  username: string
  fullName: string
  following: boolean
  followers: number
  followingCount: number
  videoCount: number
}

export interface ChannelVideo {
  id: string
  key: string
  title: string
  description: string
  visibility: VideoVisibility
  createdAt: string
  likes: number
}

export interface ChannelVideoPage {
  videos: ChannelVideo[]
  page: number
  pageSize: number
  total: number
  pages: number
}

interface ChannelApiResponse {
  id: string
  username: string
  full_name: string
  following: boolean
  followers: number
  following_count: number
  video_count: number
}

interface ChannelVideoApiItem {
  id: string
  key: string
  title: string
  description: string
  visibility: VideoVisibility
  created_at: string
  likes: number
}

interface ChannelVideosApiResponse {
  videos: ChannelVideoApiItem[]
  page: number
  page_size: number
  total: number
  pages: number
}

function withBearer(token?: string): HeadersInit {
  return token ? { Authorization: `Bearer ${token}` } : {}
}

function errorMessage(response: Response, context: string): Error {
  const status = response.status ? ` (${response.status})` : ''
  return new Error(`${context}${status}`)
}

/** Ruta canónica del canal: `/channel/@usuario`. */
export function channelPath(username: string): string {
  return `/channel/@${encodeURIComponent(username)}`
}

/** El nombre de usuario sin la arroba que lleva la URL del canal. */
export function usernameFromHandle(handle: string): string {
  return handle.startsWith('@') ? handle.slice(1) : handle
}

export async function getChannel(username: string, token?: string): Promise<Channel> {
  const response = await fetch(`${API}/${encodeURIComponent(username)}/channel`, {
    headers: withBearer(token),
  })
  if (!response.ok) {
    throw errorMessage(
      response,
      response.status === 404 ? 'No existe el canal' : 'Error al cargar el canal',
    )
  }

  const data = await response.json() as ChannelApiResponse
  return {
    id: data.id,
    username: data.username,
    fullName: data.full_name,
    following: data.following,
    followers: data.followers,
    followingCount: data.following_count,
    videoCount: data.video_count,
  }
}

export async function listChannelVideos(
  username: string,
  page = 1,
  token?: string,
  pageSize: number = CHANNEL_PAGE_SIZE,
): Promise<ChannelVideoPage> {
  const params = new URLSearchParams({ page: String(page), page_size: String(pageSize) })
  const response = await fetch(
    `${API}/${encodeURIComponent(username)}/videos?${params}`,
    { headers: withBearer(token) },
  )
  if (!response.ok) throw errorMessage(response, 'Error al cargar los vídeos del canal')

  const data = await response.json() as ChannelVideosApiResponse
  return {
    videos: data.videos.map((video) => ({
      id: video.id,
      key: video.key,
      title: video.title,
      description: video.description,
      visibility: video.visibility,
      createdAt: video.created_at,
      likes: video.likes,
    })),
    page: data.page,
    pageSize: data.page_size,
    total: data.total,
    pages: data.pages,
  }
}
