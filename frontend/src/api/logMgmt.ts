import request from '../utils/request'
import type {
  Result, ProjectLogProfileModel, LogFileInfo, LogViewResult,
  LogAggregateEntry, LogSearchResult
} from '../types'

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

/** 列出节点日志文件 */
export function listLogFiles(projectId: number, nodeId: number) {
  return request.get<any, Result<LogFileInfo[]>>('/logs/files', {
    params: { projectId, nodeId }
  })
}

/** 单节点分页查看日志 */
export function viewLog(projectId: number, nodeId: number, fileName?: string, offset = 0, lines = 200) {
  return request.get<any, Result<LogViewResult>>('/logs/view', {
    params: { projectId, nodeId, fileName, offset, lines }
  })
}

/** 多节点聚合日志 */
export function aggregateLogs(
  projectId: number,
  nodeIds?: number[],
  page = 1,
  pageSize = 100,
  since?: number
) {
  return request.get<any, Result<{ list: LogAggregateEntry[]; total: number }>>('/logs/aggregate', {
    params: { projectId, nodeIds, page, pageSize, since }
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
}) {
  return request.post<any, Result<LogSearchResult>>('/logs/search', params)
}
