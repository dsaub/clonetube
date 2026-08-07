const POINTS_API = '/api/v1/points'
const NOTIFICATIONS_API = '/api/v1/notifications'

export interface PointsView {
  points: number
}

export interface NotificationItem {
  id: number
  type: string
  title: string
  body: string
  isRead: boolean
  createdAt: string
}

export interface Donation {
  id: number
  donor: string
  recipient: string
  points: number
  message: string
  createdAt: string
}

interface NotificationApiItem {
  id: number
  type: string
  title: string
  body: string
  isRead: boolean
  createdAt: string
}

function withBearer(token?: string): HeadersInit {
  return token ? { Authorization: `Bearer ${token}` } : {}
}

function errorMessage(response: Response, context: string): Error {
  const status = response.status ? ` (${response.status})` : ''
  return new Error(`${context}${status}`)
}

export async function getPoints(token: string): Promise<number> {
  const response = await fetch(POINTS_API, { headers: withBearer(token) })
  if (!response.ok) throw errorMessage(response, 'Error al cargar los puntos')
  const data = await response.json() as PointsView
  return data.points ?? 0
}

export async function donatePoints(
  token: string,
  recipientUsername: string,
  points: number,
  message: string,
): Promise<void> {
  const response = await fetch(`${POINTS_API}/donate`, {
    method: 'POST',
    headers: { ...withBearer(token), 'Content-Type': 'application/json' },
    body: JSON.stringify({ recipientUsername, points, message }),
  })
  if (!response.ok) throw errorMessage(response, 'Error al realizar la donación')
}

export async function listNotifications(token: string): Promise<NotificationItem[]> {
  const response = await fetch(NOTIFICATIONS_API, { headers: withBearer(token) })
  if (!response.ok) throw errorMessage(response, 'Error al cargar las notificaciones')
  const data = await response.json() as NotificationApiItem[]
  return data.map((item) => ({
    id: item.id,
    type: item.type,
    title: item.title,
    body: item.body,
    isRead: item.isRead,
    createdAt: item.createdAt,
  }))
}

export async function unreadNotificationsCount(token: string): Promise<number> {
  const response = await fetch(`${NOTIFICATIONS_API}/unread-count`, { headers: withBearer(token) })
  if (!response.ok) throw errorMessage(response, 'Error al cargar las notificaciones')
  const data = await response.json() as { unread: number }
  return data.unread ?? 0
}

export async function markNotificationRead(token: string, id: number): Promise<void> {
  const response = await fetch(`${NOTIFICATIONS_API}/${id}/read`, {
    method: 'POST',
    headers: withBearer(token),
  })
  if (!response.ok) throw errorMessage(response, 'Error al marcar la notificación')
}
