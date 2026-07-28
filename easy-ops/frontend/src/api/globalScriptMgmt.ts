import request from '../utils/request'
import type { Result, GlobalScriptFileModel, GlobalScriptSnapshotResult, ScriptDistributeResult } from '../types'

/** 查询全局脚本文件列表 */
export function listGlobalScriptFiles() {
  return request.get<any, Result<GlobalScriptFileModel[]>>('/global-script/files')
}

/** 新增全局脚本文件定义 */
export function createGlobalScriptFile(model: GlobalScriptFileModel) {
  return request.post<any, Result<GlobalScriptFileModel>>('/global-script/files', model)
}

/** 更新全局脚本文件定义 */
export function updateGlobalScriptFile(id: number, model: GlobalScriptFileModel) {
  return request.put<any, Result<GlobalScriptFileModel>>(`/global-script/files/${id}`, model)
}

/** 删除全局脚本文件定义 */
export function deleteGlobalScriptFile(id: number) {
  return request.delete<any, Result>(`/global-script/files/${id}`)
}

/** 扫描指定目录下的脚本文件（扫描所有在线 Agent 节点） */
export function scanGlobalScriptFiles(scanDir: string) {
  return request.post<any, Result<GlobalScriptFileModel[]>>('/global-script/scan', null, {
    params: { scanDir }
  })
}

/** 读取脚本文件内容 */
export function getGlobalScriptContent(nodeId: number, scriptFileId: number) {
  return request.get<any, Result<string>>('/global-script/content', {
    params: { nodeId, scriptFileId }
  })
}

/** 自动选在线节点读取脚本内容 */
export function getGlobalScriptContentAuto(scriptFileId: number) {
  return request.get<any, Result<{ content: string; nodeId: number; nodeName: string; nodeIp: string }>>('/global-script/content/auto', {
    params: { scriptFileId }
  })
}

/** 获取各节点脚本快照 */
export function getGlobalScriptSnapshot(scriptFileId: number) {
  return request.get<any, Result<GlobalScriptSnapshotResult>>('/global-script/snapshot', {
    params: { scriptFileId }
  })
}

/** 分发脚本文件到指定节点 */
export function distributeGlobalScript(params: {
  scriptFileId: number
  content: string
  targetNodeIds: number[]
  setExecutable?: boolean
  autoBackup?: boolean
}) {
  return request.post<any, Result<ScriptDistributeResult>>('/global-script/distribute', params)
}

/** 刷新所有节点快照哈希 */
export function refreshGlobalScriptSnapshots(scriptFileId: number) {
  return request.post<any, Result>('/global-script/refresh', { scriptFileId })
}
