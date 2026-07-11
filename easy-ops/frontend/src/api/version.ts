import request from '../utils/request'
import type { VersionModel, Result } from '../types'

/** 获取版本列表 */
export function getVersions(projectId: string, page = 1, pageSize = 20) {
  return request.get<any, Result<{ list: VersionModel[]; total: number }>>(`/versions`, {
    params: { projectId, page, pageSize }
  })
}

/** 上传Jar包或前端 zip */
export function uploadVersion(
  projectId: string,
  file: File,
  packageType: 'jar' | 'frontend' = 'jar',
  onProgress?: (percent: number, remainingSec?: number) => void
) {
  const formData = new FormData()
  formData.append('file', file)
  const startTime = Date.now()
  return request.post<any, Result<VersionModel>>('/versions/upload', formData, {
    params: { projectId, packageType },
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 600000,
    onUploadProgress: (e) => {
      if (!onProgress || !e.total) return
      const percent = Math.round((e.loaded / e.total) * 100)
      const elapsed = (Date.now() - startTime) / 1000
      const speed = e.loaded / Math.max(elapsed, 0.1)
      const remaining = (e.total - e.loaded) / Math.max(speed, 1)
      onProgress(percent, Math.ceil(remaining))
    }
  })
}

/** 删除版本 */
export function deleteVersion(id: string) {
  return request.delete<any, Result>(`/versions/${id}`)
}
