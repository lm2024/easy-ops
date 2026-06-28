import request from '../utils/request'
import type { Result, KbTemplateModel } from '../types'

/** 获取模板列表 */
export function listTemplates(category?: string) {
  return request.get<any, Result<KbTemplateModel[]>>('/kb/templates', {
    params: { category }
  })
}

/** 创建模板 */
export function createTemplate(data: { name: string; description?: string; content?: string; icon?: string; category?: string }) {
  return request.post<any, Result<KbTemplateModel>>('/kb/templates', data)
}

/** 更新模板 */
export function updateTemplate(id: number, data: { name?: string; description?: string; content?: string; icon?: string; category?: string }) {
  return request.put<any, Result<void>>(`/kb/templates/${id}`, data)
}

/** 删除模板 */
export function deleteTemplate(id: number) {
  return request.delete<any, Result<void>>(`/kb/templates/${id}`)
}

/** 从模板创建文档 */
export function createFromTemplate(templateId: number, categoryId: number) {
  return request.post<any, Result<number>>('/kb/templates/create-document', { templateId, categoryId })
}
