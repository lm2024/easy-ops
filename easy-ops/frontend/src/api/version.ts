import request from '../utils/request'
import type { VersionModel, Result } from '../types'

/** 获取版本列表 */
export function getVersions(projectId: string, page = 1, pageSize = 20) {
  return request.get<any, Result<{ list: VersionModel[]; total: number }>>(`/versions`, {
    params: { projectId, page, pageSize }
  })
}

/** 上传Jar包 */
export function uploadVersion(projectId: string, file: File) {
  const formData = new FormData()
  formData.append('file', file)
  return request.post<any, Result<VersionModel>>('/versions/upload', formData, {
    params: { projectId },
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 60000
  })
}

/** 删除版本 */
export function deleteVersion(id: string) {
  return request.delete<any, Result>(`/versions/${id}`)
}
