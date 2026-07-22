const API = '/api/v1/video'
const GRAPHQL_API = '/graphql'

export interface VideoListItem {
  key: string
  size: number
  last_modified: string
  original_filename: string
}

export interface VideoListResponse {
  videos: VideoListItem[]
}

export interface VideoAuthor {
  id: string
  username: string
  displayName: string
}

export interface VideoMetadata {
  id: string
  filename: string
  title: string
  description: string
  authorId: string
  author: VideoAuthor | null
}

export type VideoVisibility = 'public' | 'unlisted' | 'private'

export interface StudioVideoItem extends VideoListItem {
  id: string
  title: string
  description: string
  visibility: VideoVisibility
  allowed_users: string[]
}

export interface VideoCatalogItem extends VideoListItem {
  id: string | null
  title: string
  description: string
  author: VideoAuthor | null
}

export interface StreamUrlResponse {
  url: string
  key: string
}

export interface StartMultipartResponse {
  uploadId: string
  key: string
  original_filename: string
}

export interface SignChunkResponse {
  url: string
}

export interface MultipartPart {
  PartNumber: number
  ETag: string
}

export interface CompleteMultipartResponse {
  status: string
  location: string | null
  key: string
  original_filename: string
}

function withBearer(token?: string): HeadersInit {
  return token ? { Authorization: `Bearer ${token}` } : {}
}

function errorMessage(response: Response, context: string): Error {
  const status = response.status ? ` (${response.status})` : ''
  return new Error(`${context}${status}`)
}

interface GraphQLError {
  message: string
}

interface GraphQLResponse<T> {
  data?: T
  errors?: GraphQLError[]
}

async function graphqlRequest<T>(
  query: string,
  variables: Record<string, unknown> = {},
  token?: string,
): Promise<T> {
  const response = await fetch(GRAPHQL_API, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      ...withBearer(token),
    },
    body: JSON.stringify({ query, variables }),
  })

  if (!response.ok) throw errorMessage(response, 'Error al consultar los datos de video')

  const result = await response.json() as GraphQLResponse<T>
  if (result.errors?.length) {
    throw new Error(result.errors.map(({ message }) => message).join('. '))
  }
  if (!result.data) throw new Error('La API no devolvió datos de video')
  return result.data
}

export async function listVideos(): Promise<VideoListItem[]> {
  const response = await fetch(`${API}/list`)
  if (!response.ok) throw errorMessage(response, 'Error al listar videos')

  const data: VideoListResponse = await response.json()
  return data.videos
}

export async function listVideoMetadata(): Promise<VideoMetadata[]> {
  const data = await graphqlRequest<{
    videos: Omit<VideoMetadata, 'author'>[]
    channels: VideoAuthor[]
  }>(`
    query VideoCatalog {
      videos {
        id
        filename
        title
        description
        authorId
      }
      channels {
        id
        username
        displayName
      }
    }
  `)

  const authors = new Map(data.channels.map((channel) => [channel.id, channel]))
  return data.videos.map((video) => ({
    ...video,
    author: authors.get(video.authorId) ?? null,
  }))
}

export async function listVideosWithMetadata(): Promise<VideoCatalogItem[]> {
  const [storedVideos, metadata] = await Promise.all([
    listVideos(),
    listVideoMetadata().catch(() => []),
  ])
  const metadataByKey = new Map(metadata.map((video) => [video.filename, video]))

  return storedVideos.map((video) => {
    const details = metadataByKey.get(video.key)
    return {
      ...video,
      id: details?.id ?? null,
      title: details?.title || video.original_filename,
      description: details?.description ?? '',
      author: details?.author ?? null,
    }
  })
}

export async function getVideoMetadataByKey(key: string): Promise<VideoMetadata | null> {
  const videos = await listVideoMetadata()
  return videos.find((video) => video.filename === key) ?? null
}

export async function getAccessibleVideoMetadataByKey(
  key: string,
  token?: string,
): Promise<VideoMetadata> {
  const params = new URLSearchParams({ key })
  const response = await fetch(`${API}/detail?${params}`, { headers: withBearer(token) })
  if (!response.ok) throw errorMessage(response, 'Error al obtener los datos del video')
  const video = await response.json() as {
    id: string
    key: string
    title: string
    description: string
    author_id: string
    author_username: string
    author_name: string
  }
  return {
    id: video.id,
    filename: video.key,
    title: video.title,
    description: video.description,
    authorId: video.author_id,
    author: {
      id: video.author_id,
      username: video.author_username,
      displayName: video.author_name,
    },
  }
}

export async function updateVideoMetadataByKey(
  key: string,
  title: string,
  description: string,
  token: string,
): Promise<VideoMetadata> {
  const current = await getVideoMetadataByKey(key)
  if (!current) throw new Error('No se encontró el video recién subido en la base de datos')

  const data = await graphqlRequest<{ updateVideo: Omit<VideoMetadata, 'author'> }>(`
    mutation UpdateVideoMetadata($id: UUID!, $title: String!, $description: String!) {
      updateVideo(id: $id, title: $title, description: $description) {
        id
        filename
        title
        description
        authorId
      }
    }
  `, {
    id: current.id,
    title,
    description,
  }, token)

  return { ...data.updateVideo, author: current.author }
}

export async function getStreamUrl(key: string, token?: string): Promise<string> {
  const params = new URLSearchParams({ key })
  const response = token
    ? await fetch(`${API}/stream-url?${params}`, { headers: withBearer(token) })
    : await fetch(`${API}/stream-url?${params}`)
  if (!response.ok) throw errorMessage(response, 'Error al obtener URL de streaming')

  const data: StreamUrlResponse = await response.json()
  return data.url
}

export async function listStudioVideos(token: string): Promise<StudioVideoItem[]> {
  const response = await fetch(`${API}/studio`, { headers: withBearer(token) })
  if (!response.ok) throw errorMessage(response, 'Error al cargar Clonetube Studio')
  const data = await response.json() as { videos: StudioVideoItem[] }
  return data.videos
}

export async function updateStudioVideo(
  video: Pick<StudioVideoItem, 'id' | 'title' | 'description' | 'visibility' | 'allowed_users'>,
  token: string,
): Promise<StudioVideoItem> {
  const response = await fetch(`${API}/${video.id}`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json', ...withBearer(token) },
    body: JSON.stringify({
      title: video.title,
      description: video.description,
      visibility: video.visibility,
      allowed_users: video.allowed_users,
    }),
  })
  if (!response.ok) throw errorMessage(response, 'Error al guardar el video')
  return response.json() as Promise<StudioVideoItem>
}

export async function deleteStudioVideo(id: string, token: string): Promise<void> {
  const response = await fetch(`${API}/${id}`, {
    method: 'DELETE',
    headers: withBearer(token),
  })
  if (!response.ok) throw errorMessage(response, 'Error al eliminar el video')
}

export async function startMultipart(
  originalFilename: string,
  token?: string,
): Promise<StartMultipartResponse> {
  const params = new URLSearchParams({ original_filename: originalFilename })
  const response = await fetch(`${API}/start-multipart?${params}`, {
    method: 'POST',
    headers: withBearer(token),
  })

  if (!response.ok) throw errorMessage(response, 'Error al iniciar la subida')
  return response.json() as Promise<StartMultipartResponse>
}

export async function signChunk(
  filename: string,
  uploadId: string,
  chunkNumber: number,
  token?: string,
): Promise<string> {
  const params = new URLSearchParams({
    filename,
    upload_id: uploadId,
    chunk_number: String(chunkNumber),
  })
  const response = await fetch(`${API}/sign-chunk?${params}`, {
    headers: withBearer(token),
  })

  if (!response.ok) {
    throw errorMessage(response, `Error al firmar el fragmento ${chunkNumber}`)
  }

  const data: SignChunkResponse = await response.json()
  return data.url
}

export async function uploadChunk(
  presignedUrl: string,
  chunk: Blob,
  chunkNumber: number,
): Promise<MultipartPart> {
  const response = await fetch(presignedUrl, { method: 'PUT', body: chunk })
  if (!response.ok) {
    throw errorMessage(response, `Error al subir el fragmento ${chunkNumber}`)
  }

  const etag = response.headers.get('ETag')
  if (!etag) {
    throw new Error(
      'S3 no devolvió el ETag. Comprueba que ETag esté incluido en Access-Control-Expose-Headers.',
    )
  }

  return { PartNumber: chunkNumber, ETag: etag }
}

export async function completeMultipart(
  filename: string,
  uploadId: string,
  parts: MultipartPart[],
  token?: string,
): Promise<CompleteMultipartResponse> {
  const response = await fetch(`${API}/complete-multipart`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      ...withBearer(token),
    },
    body: JSON.stringify({ filename, uploadId, parts }),
  })

  if (!response.ok) throw errorMessage(response, 'Error al completar la subida')
  return response.json() as Promise<CompleteMultipartResponse>
}

export async function cancelMultipart(
  filename: string,
  uploadId: string,
  token?: string,
): Promise<void> {
  const params = new URLSearchParams({ filename, upload_id: uploadId })
  const response = await fetch(`${API}/cancel-multipart?${params}`, {
    method: 'DELETE',
    headers: withBearer(token),
  })

  if (!response.ok && response.status !== 404) {
    throw errorMessage(response, 'Error al cancelar la subida')
  }
}
