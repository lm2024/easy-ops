import request from '../utils/request'
import type { Result, KbShareLinkModel } from '../types'

/** 分享文档访问响应（包含文档内容与元信息） */
export interface ShareAccessResult extends KbShareLinkModel {
  content: string
  title?: string
  updateTime?: number
}

/** 创建分享链接 */
export function createShareLink(data: { documentId: number; password?: string; expireTime?: number }) {
  return request.post<any, Result<KbShareLinkModel>>('/kb/share-links', data)
}

/** 获取分享链接信息 */
export function getShareLink(id: number) {
  return request.get<any, Result<KbShareLinkModel>>(`/kb/share-links/${id}`)
}

/** 删除分享链接 */
export function deleteShareLink(id: number) {
  return request.delete<any, Result<void>>(`/kb/share-links/${id}`)
}

/** 通过 token 访问分享文档 */
export function accessByToken(token: string, password?: string) {
  return request.get<any, Result<ShareAccessResult>>(`/kb/share/access/${token}`, {
    params: { password }
  })
}
