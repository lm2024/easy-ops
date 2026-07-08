import request from '../utils/request'
import type { Result, ProjectConfigFileModel, ConfigSnapshotResult, ConfigCompareResult } from '../types'

/** 查询项目配置文件列表 */
export function listConfigFiles(projectId: number) {
  return request.get<any, Result<ProjectConfigFileModel[]>>('/config/files', {
    params: { projectId }
  })
}

/** 新增配置文件定义 */
export function createConfigFile(model: ProjectConfigFileModel) {
  return request.post<any, Result<ProjectConfigFileModel>>('/config/files', model)
}

/** 更新配置文件定义 */
export function updateConfigFile(id: number, model: ProjectConfigFileModel) {
  return request.put<any, Result<ProjectConfigFileModel>>(`/config/files/${id}`, model)
}

/** 删除配置文件定义 */
export function deleteConfigFile(id: number, projectId: number) {
  return request.delete<any, Result>(`/config/files/${id}`, { params: { projectId } })
}

/** 获取各节点配置快照 */
export function getConfigSnapshot(projectId: number, configFileId: number) {
  return request.get<any, Result<ConfigSnapshotResult>>('/config/snapshot', {
    params: { projectId, configFileId }
  })
}

/** 读取指定节点配置内容 */
export function getConfigContent(projectId: number, nodeId: number, configFileId: number) {
  return request.get<any, Result<string>>('/config/content', {
    params: { projectId, nodeId, configFileId }
  })
}

/** 多节点配置对比 */
export function compareConfig(params: {
  projectId: number
  configFileId: number
  baseNodeId: number
  targetNodeIds: number[]
}) {
  return request.post<any, Result<ConfigCompareResult>>('/config/compare', params)
}

/** 批量/单独分发配置 */
export function distributeConfig(params: {
  projectId: number
  configFileId: number
  content: string
  targetNodeIds: number[]
  distributeType?: string
  restartAfter?: boolean
}) {
  return request.post<any, Result>('/config/distribute', params)
}

/** 刷新所有节点快照哈希 */
export function refreshConfigSnapshots(projectId: number, configFileId: number) {
  return request.post<any, Result>('/config/refresh', { projectId, configFileId })
}
