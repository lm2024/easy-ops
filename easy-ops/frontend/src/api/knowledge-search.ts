import request from '../utils/request'
import type { Result, KbDocumentModel } from '../types'

/** 全文搜索文档 */
export function fullTextSearch(query: string, page?: number, size?: number) {
  return request.get<any, Result<{ list: KbDocumentModel[]; total: number; highlights?: string[] }>>('/kb/search', {
    params: { q: query, page, size }
  })
}

/** 高级搜索（含分类、标签筛选） */
export function advancedSearch(params: {
  query?: string
  categoryId?: number
  tags?: string
  page?: number
  size?: number
}) {
  return request.get<any, Result<{ list: KbDocumentModel[]; total: number }>>('/kb/search/advanced', {
    params
  })
}

/** 按标签搜索 */
export function searchByTag(tagId: number, page?: number, size?: number) {
  return request.get<any, Result<{ list: KbDocumentModel[]; total: number }>>('/kb/search/tag/' + tagId, {
    params: { page, size }
  })
}
