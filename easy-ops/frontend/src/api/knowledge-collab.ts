import request from '../utils/request'
import type { Result } from '../types'

/** 保存协作状态（Yjs Doc → Markdown） */
export function saveCollabState(documentId: number) {
  return request.post<any, Result<void>>(`/kb/collab/${documentId}/save`)
}

/** 获取在线协作用户 */
export function getOnlineUsers(documentId: number) {
  return request.get<any, Result<number[]>>(`/kb/collab/${documentId}/online`)
}

/** 版本回滚 */
export function rollbackVersion(documentId: number, versionNo: number) {
  return request.post<any, Result<void>>(`/kb/collab/${documentId}/rollback`, { versionNo })
}

/** 获取版本 Diff */
export function getVersionDiff(documentId: number, oldVersion: number, newVersion: number) {
  return request.get<any, Result<{ oldContent: string; newContent: string; diffHtml: string }>>(
    `/kb/collab/${documentId}/diff`,
    { params: { oldVersion, newVersion } }
  )
}
