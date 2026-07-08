import request from '../utils/request'
import type { Result, KbTagModel, KbDocumentTagModel } from '../types'

/** 获取所有标签 */
export function listTags() {
  return request.get<any, Result<KbTagModel[]>>('/kb/tags')
}

/** 创建标签 */
export function createTag(data: { name: string; color?: string }) {
  return request.post<any, Result<KbTagModel>>('/kb/tags', data)
}

/** 更新标签 */
export function updateTag(id: number, data: { name?: string; color?: string }) {
  return request.put<any, Result<void>>(`/kb/tags/${id}`, data)
}

/** 删除标签 */
export function deleteTag(id: number) {
  return request.delete<any, Result<void>>(`/kb/tags/${id}`)
}

/** 为文档添加标签 */
export function addDocumentTag(documentId: number, tagId: number) {
  return request.post<any, Result<KbDocumentTagModel>>(`/kb/documents/${documentId}/tags`, { tagId })
}

/** 移除文档标签 */
export function removeDocumentTag(documentId: number, tagId: number) {
  return request.delete<any, Result<void>>(`/kb/documents/${documentId}/tags/${tagId}`)
}

/** 获取文档标签 */
export function getDocumentTags(documentId: number) {
  return request.get<any, Result<KbTagModel[]>>(`/kb/documents/${documentId}/tags`)
}
