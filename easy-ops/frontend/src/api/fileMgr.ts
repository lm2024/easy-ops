import request from '../utils/request'
import type { Result } from '../types'

export interface FileItem {
  name: string
  path: string
  dir: boolean
  size: number
  mtime: number
}

export interface PartInfo {
  index: number
  name: string
  size: number
}

export interface DownloadTask {
  id: string
  name: string
  status: string
  totalSize: number
  processedBytes: number
  progressPct: number
  createTime: number
  message?: string
  parts: PartInfo[]
}

/** 可访问根目录 */
export function listRoots(nodeId: number) {
  return request.get<any, Result<string[]>>('/filemgr/roots', { params: { nodeId } })
}

/** 列目录 */
export function listDir(nodeId: number, path?: string) {
  return request.get<any, Result<{ path: string; name: string; items: FileItem[] }>>('/filemgr/list', {
    params: { nodeId, path }
  })
}

/** 文件/目录信息 */
export function getInfo(nodeId: number, path: string) {
  return request.get<any, Result<FileItem>>('/filemgr/info', { params: { nodeId, path } })
}

/** 创建压缩下载任务 */
export function createTask(nodeId: number, paths: string[], baseName?: string) {
  return request.post<any, Result<DownloadTask>>('/filemgr/task/create', { nodeId, paths, baseName })
}

/** 节点下载任务列表 */
export function listTasks(nodeId: number) {
  return request.get<any, Result<DownloadTask[]>>('/filemgr/task/list', { params: { nodeId } })
}

/** 取消任务 */
export function cancelTask(nodeId: number, id: string) {
  return request.post<any, Result<DownloadTask>>(`/filemgr/task/${id}/cancel`, null, { params: { nodeId } })
}

/** 删除任务 */
export function deleteTask(nodeId: number, id: string) {
  return request.post<any, Result<boolean>>(`/filemgr/task/${id}/delete`, null, { params: { nodeId } })
}

/** 上传文件到节点目录 */
export function uploadFile(nodeId: number, dir: string, file: File) {
  const form = new FormData()
  form.append('file', file)
  return request.post<any, Result<{ path: string; size: number }>>('/filemgr/upload', form, {
    params: { nodeId, dir },
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 600000
  })
}

/** 下载单文件（小文件），返回 blob + 进度 */
export async function downloadDirect(nodeId: number, path: string, onProgress?: (p: number) => void): Promise<Blob> {
  const res = await request.get<any, any>('/filemgr/direct', {
    params: { nodeId, path },
    responseType: 'blob',
    timeout: 0,
    onDownloadProgress: (e: any) => {
      if (onProgress && e.total) onProgress(Math.round((e.loaded * 100) / e.total))
    }
  })
  return res as unknown as Blob
}

/** 下载分卷（ZIP 分卷），返回 blob + 进度 */
export async function downloadTaskPart(
  nodeId: number,
  taskId: string,
  index: number,
  onProgress?: (p: number) => void
): Promise<Blob> {
  const res = await request.get<any, any>(`/filemgr/task/${taskId}/part/${index}`, {
    params: { nodeId },
    responseType: 'blob',
    timeout: 0,
    onDownloadProgress: (e: any) => {
      if (onProgress && e.total) onProgress(Math.round((e.loaded * 100) / e.total))
    }
  })
  return res as unknown as Blob
}

/** 触发浏览器保存 blob */
export function saveBlob(blob: Blob, fileName: string) {
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = fileName
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  setTimeout(() => URL.revokeObjectURL(url), 10000)
}
