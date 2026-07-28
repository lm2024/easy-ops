import request from '../utils/request'
import type { Result, GlobalScriptFileModel, GlobalScriptSnapshotResult, ScriptDistributeResult } from '../types'

/** 查询项目脚本文件列表 */
export function listScriptFiles(projectId: number) {
  return request.get<any, Result<GlobalScriptFileModel[]>>('/script/files', {
    params: { projectId }
  })
}

/** 新增脚本文件定义 */
export function createScriptFile(model: GlobalScriptFileModel) {
  return request.post<any, Result<GlobalScriptFileModel>>('/script/files', model)
}

/** 更新脚本文件定义 */
export function updateScriptFile(id: number, model: GlobalScriptFileModel) {
  return request.put<any, Result<GlobalScriptFileModel>>(`/script/files/${id}`, model)
}

/** 删除脚本文件定义 */
export function deleteScriptFile(id: number, projectId: number) {
  return request.delete<any, Result>(`/script/files/${id}`, { params: { projectId } })
}

/** 扫描指定目录下的脚本文件 */
export function scanScriptFiles(projectId: number, scanDir: string) {
  return request.post<any, Result<GlobalScriptFileModel[]>>('/script/scan', null, {
    params: { projectId, scanDir }
  })
}

/** 读取脚本文件内容 */
export function getScriptContent(projectId: number, nodeId: number, scriptFileId: number) {
  return request.get<any, Result<string>>('/script/content', {
    params: { projectId, nodeId, scriptFileId }
  })
}

/** 自动选在线节点读取脚本内容 */
export function getScriptContentAuto(projectId: number, scriptFileId: number) {
  return request.get<any, Result<{ content: string; nodeId: number; nodeName: string; nodeIp: string }>>('/script/content/auto', {
    params: { projectId, scriptFileId }
  })
}

/** 获取各节点脚本快照 */
export function getScriptSnapshot(projectId: number, scriptFileId: number) {
  return request.get<any, Result<GlobalScriptSnapshotResult>>('/script/snapshot', {
    params: { projectId, scriptFileId }
  })
}

/** 分发脚本文件 */
export function distributeScript(params: {
  projectId: number
  scriptFileId: number
  content: string
  targetNodeIds: number[]
  setExecutable?: boolean
  autoBackup?: boolean
}) {
  return request.post<any, Result<ScriptDistributeResult>>('/script/distribute', params)
}

/** 刷新所有节点快照哈希 */
export function refreshScriptSnapshots(projectId: number, scriptFileId: number) {
  return request.post<any, Result>('/script/refresh', { projectId, scriptFileId })
}
