import http from './http'
import type { NotificationItem, UnreadCount } from './types'

export function listNotifications(): Promise<NotificationItem[]> {
  return http.get('/notifications')
}

export function unreadCount(): Promise<UnreadCount> {
  return http.get('/notifications/unread-count')
}

export function markNotificationRead(notificationId: string): Promise<NotificationItem> {
  return http.post(`/notifications/${notificationId}/read`)
}
