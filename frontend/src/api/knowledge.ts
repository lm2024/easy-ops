import request from '../utils/request'
import type { Result, KbCategoryModel, KbDocumentModel, KbCommentModel, KbImageModel } from '../types'

/** 获取分类树 */
export function listCategories(projectId?: number) {
  return request.get<any, Result<KbCategoryModel[]>>('/kb/categories', {
    params: { projectId }
  })
}

/** 创建分类 */
export function createCategory(category: Partial<KbCategoryModel>) {
  return request.post<any, Result<KbCategoryModel>>('/kb/categories', category)
}

/** 更新分类 */
export function updateCategory(id: number, category: Partial<KbCategoryModel>) {
  return request.put<any, Result<KbCategoryModel>>(`/kb/categories/${id}`, category)
}

/** 删除分类 */
export function deleteCategory(id: number) {
  return request.delete<any, Result>(`/kb/categories/${id}`)
}

/** 文档列表 */
export function listDocuments(categoryId: number, page = 1, pageSize = 20) {
  return request.get<any, Result<{ list: KbDocumentModel[]; total: number }>>('/kb/documents', {
    params: { categoryId, page, pageSize }
  })
}

/** 创建文档 */
export function createDocument(doc: Partial<KbDocumentModel>) {
  return request.post<any, Result<KbDocumentModel>>('/kb/documents', doc)
}

/** 获取文档详情 */
export function getDocument(id: number) {
  return request.get<any, Result<KbDocumentModel>>(`/kb/documents/${id}`)
}

/** 更新文档 */
export function updateDocument(id: number, body: {
  title?: string
  content?: string
  categoryId?: number
  status?: number
  versionNo?: number
  changeNote?: string
}) {
  return request.put<any, Result<KbDocumentModel>>(`/kb/documents/${id}`, body)
}

/** 删除文档 */
export function deleteDocument(id: number) {
  return request.delete<any, Result>(`/kb/documents/${id}`)
}

/** 锁定文档 */
export function lockDocument(id: number) {
  return request.post<any, Result>(`/kb/documents/${id}/lock`)
}

/** 解锁文档 */
export function unlockDocument(id: number) {
  return request.post<any, Result>(`/kb/documents/${id}/unlock`)
}

/** 获取评论列表 */
export function listComments(documentId: number) {
  return request.get<any, Result<KbCommentModel[]>>(`/kb/documents/${documentId}/comments`)
}

/** 添加评论 */
export function addComment(documentId: number, comment: { content: string; rating?: number }) {
  return request.post<any, Result<KbCommentModel>>(`/kb/documents/${documentId}/comments`, comment)
}

/** 上传图片 */
export function uploadDocumentImage(documentId: number, file: File) {
  const formData = new FormData()
  formData.append('file', file)
  return request.post<any, Result<KbImageModel>>(`/kb/documents/${documentId}/images`, formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

/** 获取图片 URL */
export function getImageUrl(imageId: number) {
  const token = localStorage.getItem('token')
  return `/api/kb/images/${imageId}${token ? `?token=${token}` : ''}`
}

/** 搜索文档 */
export function searchDocuments(q: string, page = 1, pageSize = 20) {
  return request.get<any, Result<{ list: KbDocumentModel[]; total: number }>>('/kb/search', {
    params: { q, page, pageSize }
  })
}

/** 导出文档 */
export async function exportDocument(id: number, format: 'md' | 'zip' = 'md') {
  const token = localStorage.getItem('token')
  const response = await fetch(`/api/kb/documents/${id}/export?format=${format}`, {
    headers: { Authorization: `Bearer ${token}` }
  })
  if (!response.ok) throw new Error('导出失败')
  const blob = await response.blob()
  const disposition = response.headers.get('Content-Disposition') || ''
  const match = disposition.match(/filename="(.+)"/)
  const fileName = match ? match[1] : `document.${format}`
  const url = window.URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = fileName
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  window.URL.revokeObjectURL(url)
}
