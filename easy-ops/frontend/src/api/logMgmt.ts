import request from '../utils/request'
import type {
  Result, ProjectLogProfileModel, LogDiscoverResult, LogViewResult,
  LogAggregateResult, LogSearchResult
} from '../types'

function triggerDownload(blob: Blob, fileName: string) {
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = fileName
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  URL.revokeObjectURL(url)
}

/** 获取项目日志配置 */
export function getLogProfile(projectId: number) {
  return request.get<any, Result<ProjectLogProfileModel>>('/logs/profile', {
    params: { projectId }
  })
}

/** 保存项目日志配置 */
export function saveLogProfile(profile: ProjectLogProfileModel) {
  return request.post<any, Result<ProjectLogProfileModel>>('/logs/profile', profile)
}

/** 智能发现节点日志文件 */
export function discoverLogFiles(projectId: number, nodeId: number) {
  return request.get<any, Result<LogDiscoverResult>>('/logs/files', {
    params: { projectId, nodeId }
  })
}

/** @deprecated 使用 discoverLogFiles */
export function listLogFiles(projectId: number, nodeId: number) {
  return discoverLogFiles(projectId, nodeId)
}

/** 单节点分页查看日志（filePath 传完整路径或文件名） */
export function viewLog(
  projectId: number,
  nodeId: number,
  filePath?: string,
  offset = 0,
  lines = 200,
  level?: string,
  mode: 'tail' | 'page' = 'tail'
) {
  return request.get<any, Result<LogViewResult>>('/logs/view', {
    params: { projectId, nodeId, fileName: filePath, offset, lines, level, mode }
  })
}

/** 多节点聚合日志 */
export function aggregateLogs(
  projectId: number,
  nodeIds?: number[],
  page = 1,
  pageSize = 100,
  since?: number,
  level?: string
) {
  return request.get<any, Result<LogAggregateResult>>('/logs/aggregate', {
    params: { projectId, nodeIds, page, pageSize, since, level }
  })
}

/** 关键词搜索日志 */
export function searchLogs(params: {
  projectId: number
  keyword: string
  scope?: string
  nodeIds?: number[]
  contextLines?: number
  maxResults?: number
  level?: string
  filePath?: string
}) {
  return request.post<any, Result<LogSearchResult>>('/logs/search', params)
}

/** 下载日志（单节点或聚合，默认最新1000条） */
export async function downloadLogs(params: {
  projectId: number
  nodeId?: number
  filePath?: string
  lines?: number
  level?: string
  mode?: 'single' | 'aggregate'
}) {
  const res = await request.get('/logs/download', {
    params: {
      projectId: params.projectId,
      nodeId: params.nodeId,
      filePath: params.filePath,
      lines: params.lines || 1000,
      level: params.level,
      mode: params.mode || 'aggregate'
    },
    responseType: 'blob'
  })
  const fileName = `logs_${params.projectId}_${Date.now()}.txt`
  triggerDownload(res as any as Blob, fileName)
  return true
}
