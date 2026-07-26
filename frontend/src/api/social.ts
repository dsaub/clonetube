const API = '/api/v1/users'

export interface FollowState {
  username: string
  following: boolean
  followers: number
}

export interface PublicUser {
  id: string
  username: string
  full_name: string
}

function withBearer(token?: string): HeadersInit {
  return token ? { Authorization: `Bearer ${token}` } : {}
}

function errorMessage(response: Response, context: string): Error {
  const status = response.status ? ` (${response.status})` : ''
  return new Error(`${context}${status}`)
}

async function followRequest(
  username: string,
  method: 'GET' | 'POST' | 'DELETE',
  context: string,
  token?: string,
): Promise<FollowState> {
  const response = await fetch(`${API}/${encodeURIComponent(username)}/follow`, {
    method,
    headers: withBearer(token),
  })
  if (!response.ok) throw errorMessage(response, context)
  return response.json() as Promise<FollowState>
}

export async function getFollowState(username: string, token?: string): Promise<FollowState> {
  return followRequest(username, 'GET', 'Error al consultar el seguimiento', token)
}

export async function followUser(username: string, token: string): Promise<FollowState> {
  return followRequest(username, 'POST', 'Error al seguir al usuario', token)
}

export async function unfollowUser(username: string, token: string): Promise<FollowState> {
  return followRequest(username, 'DELETE', 'Error al dejar de seguir al usuario', token)
}

export async function listFollowing(token: string): Promise<PublicUser[]> {
  const response = await fetch(`${API}/me/following`, { headers: withBearer(token) })
  if (!response.ok) throw errorMessage(response, 'Error al cargar a quién sigues')

  const data = await response.json() as { users: PublicUser[] }
  return data.users
}
