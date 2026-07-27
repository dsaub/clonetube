import {
  listVideos,
  listVideosWithMetadata,
  type VideoAuthor,
  type VideoCatalogItem,
} from '@/api/video'
import { listFollowing } from '@/api/social'

const API = '/api/v1/video'

/** Pesos del ranking de respaldo en cliente (equivalentes a los del backend). */
export const RECENCY_HALF_LIFE_HOURS = 72
export const AUTHOR_DIVERSITY_PENALTY = 0.45

export interface FeedVideo extends VideoCatalogItem {
  createdAt: string
  likes: number
  score: number
  fromFollowedAuthor: boolean
}

export interface FeedOptions {
  onlyFollowing?: boolean
  limit?: number
}

interface FeedApiItem {
  id: string
  filename: string
  title: string
  description: string
  author_id: string
  author_username: string
  author_name: string
  created_at: string
  likes: number
  score: number
  from_followed_author: boolean
}

function withBearer(token?: string): HeadersInit {
  return token ? { Authorization: `Bearer ${token}` } : {}
}

/** Pide al backend el feed ya ordenado por el algoritmo de seguidores. */
export async function fetchFeed(token?: string, options: FeedOptions = {}): Promise<FeedApiItem[]> {
  const params = new URLSearchParams()
  if (options.onlyFollowing) params.set('only_following', 'true')
  if (options.limit) params.set('limit', String(options.limit))
  const query = params.toString()

  const response = await fetch(`${API}/feed${query ? `?${query}` : ''}`, {
    headers: withBearer(token),
  })
  if (!response.ok) {
    const status = response.status ? ` (${response.status})` : ''
    throw new Error(`Error al cargar el feed${status}`)
  }

  const data = await response.json() as { videos: FeedApiItem[] }
  return data.videos
}

/**
 * Feed personalizado listo para pintar: mantiene el orden que decide el backend
 * y le añade el tamaño real de cada vídeo almacenado.
 */
export async function listFeedVideos(
  token?: string,
  options: FeedOptions = {},
): Promise<FeedVideo[]> {
  const [items, stored] = await Promise.all([
    fetchFeed(token, options),
    listVideos().catch(() => []),
  ])
  const storedByKey = new Map(stored.map((video) => [video.key, video]))

  return items.map((item) => {
    const object = storedByKey.get(item.filename)
    const author: VideoAuthor = {
      id: item.author_id,
      username: item.author_username,
      displayName: item.author_name,
    }
    return {
      id: item.id,
      key: item.filename,
      size: object?.size ?? 0,
      last_modified: object?.last_modified ?? item.created_at,
      original_filename: object?.original_filename ?? item.title,
      title: item.title || item.filename,
      description: item.description,
      author,
      createdAt: item.created_at,
      likes: item.likes,
      score: item.score,
      fromFollowedAuthor: item.from_followed_author,
    }
  })
}

/**
 * Carga la portada: personalizada cuando hay sesión y, si el feed falla,
 * el catálogo público reordenado en cliente con los mismos criterios.
 */
export async function loadHomeVideos(
  token?: string,
  options: FeedOptions = {},
): Promise<FeedVideo[]> {
  // Sin sesión no hay a quién seguir: el catálogo público ya es el mejor orden.
  if (!token) return options.onlyFollowing ? [] : catalogAsFeed()

  try {
    return await listFeedVideos(token, options)
  } catch {
    const [videos, following] = await Promise.all([
      catalogAsFeed(),
      listFollowing(token).catch(() => []),
    ])
    const usernames = following.map((user) => user.username)
    const ranked = rankByFollowedAuthors(videos, usernames)
    return options.onlyFollowing ? ranked.filter((video) => video.fromFollowedAuthor) : ranked
  }
}

async function catalogAsFeed(): Promise<FeedVideo[]> {
  const videos = await listVideosWithMetadata()
  return videos.map((video) => ({
    ...video,
    createdAt: video.last_modified,
    likes: 0,
    score: 0,
    fromFollowedAuthor: false,
  }))
}

/** Novedad en (0, 1]: 1 recién subido, 0.5 al cumplirse la semivida. */
export function recencyScore(publishedAt: string, now: Date = new Date()): number {
  const published = new Date(publishedAt).getTime()
  if (Number.isNaN(published)) return 0

  const ageHours = Math.max(now.getTime() - published, 0) / 3_600_000
  return 0.5 ** (ageHours / RECENCY_HALF_LIFE_HOURS)
}

/**
 * Reordena en cliente priorizando a los autores seguidos.
 *
 * Reproduce el criterio del backend: los seguidos forman un bloque que va
 * siempre delante, dentro de cada bloque manda la novedad y se penaliza al
 * autor que ya ha colocado vídeos para no monopolizar la portada.
 */
export function rankByFollowedAuthors(
  videos: FeedVideo[],
  followedUsernames: Iterable<string>,
  now: Date = new Date(),
): FeedVideo[] {
  const followed = new Set(followedUsernames)
  const remaining: FeedVideo[] = videos.map((video) => ({
    ...video,
    fromFollowedAuthor: Boolean(video.author && followed.has(video.author.username)),
    score: recencyScore(video.createdAt || video.last_modified, now),
  }))

  const ranked: FeedVideo[] = []
  const perAuthor = new Map<string, number>()

  while (remaining.length > 0) {
    let bestIndex = 0
    for (let index = 1; index < remaining.length; index += 1) {
      if (compare(remaining[index]!, remaining[bestIndex]!, perAuthor) < 0) bestIndex = index
    }

    const chosen = remaining.splice(bestIndex, 1)[0]!
    const authorId = chosen.author?.id ?? ''
    perAuthor.set(authorId, (perAuthor.get(authorId) ?? 0) + 1)
    ranked.push(chosen)
  }

  return ranked
}

function compare(a: FeedVideo, b: FeedVideo, perAuthor: Map<string, number>): number {
  if (a.fromFollowedAuthor !== b.fromFollowedAuthor) return a.fromFollowedAuthor ? -1 : 1

  const difference = adjustedScore(b, perAuthor) - adjustedScore(a, perAuthor)
  return difference !== 0 ? difference : a.key.localeCompare(b.key)
}

function adjustedScore(video: FeedVideo, perAuthor: Map<string, number>): number {
  const placed = perAuthor.get(video.author?.id ?? '') ?? 0
  return video.score - AUTHOR_DIVERSITY_PENALTY * placed
}
