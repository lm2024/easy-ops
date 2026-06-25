import request from '../utils/request'
import type { Result, NotificationRecordModel } from '../types'

/** 通知列表 */
export function listNotifications(page = 1, pageSize = 20) {
  return request.get<any, Result<{ list: NotificationRecordModel[]; total: number }>>('/notifications', {
    params: { page, pageSize }
  })
}

/** 未读数量 */
export function getUnreadCount() {
  return request.get<any, Result<{ count: number }>>('/notifications/unread-count')
}

/** 未确认告警 */
export function getUnackedAlerts() {
  return request.get<any, Result<NotificationRecordModel[]>>('/notifications/unacked-alerts')
}

/** 标记已读 */
export function markNotificationRead(id: number) {
  return request.post<any, Result>(`/notifications/${id}/read`)
}

/** 确认关闭告警 */
export function ackNotification(id: number) {
  return request.post<any, Result>(`/notifications/${id}/ack`)
}
