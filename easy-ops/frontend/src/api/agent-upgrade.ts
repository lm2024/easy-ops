import request from '../utils/request'
import type { Result } from '../types'

/** Agent 升级包信息 */
export interface AgentPackage {
  version: string
  fileName: string
  size: number
  lastModified?: number
  sha256?: string
}

/** Agent 节点信息 */
export interface AgentNode {
  id: number
  name: string
  ip: string
  port: number
  status: number
  agentVersion: string
  lastHeartbeat: number
}

/** 升级记录 */
export interface AgentUpgradeRecord {
  id: number
  upgradeBatchId: string
  targetVersion: string
  nodeId: number
  nodeName: string
  oldVersion: string
  status: number  // 0:待升级 1:升级中 2:成功 3:失败 4:已回滚
  errorMessage: string
  startTime: number
  endTime: number
  createTime: number
}

/** 升级状态 */
export interface AgentUpgradeStatus {
  batchId: string
  targetVersion: string
  total: number
  success: number
  failed: number
  processing: number
  pending: number
  completed: number
  status: string
  details: AgentUpgradeRecord[]
}

/** 上传 Agent 升级包 */
export function uploadAgentPackage(file: File, version?: string) {
  const formData = new FormData()
  formData.append('file', file)
  if (version) {
    formData.append('version', version)
  }
  return request.post<any, Result<AgentPackage>>('/agent-upgrade/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 600000
  })
}

/** 获取已上传的 Agent 包列表 */
export function getAgentPackages() {
  return request.get<any, Result<AgentPackage[]>>('/agent-upgrade/packages')
}

/** 删除指定版本的升级包 */
export function deleteAgentPackage(version: string) {
  return request.delete<any, Result>(`/agent-upgrade/packages/${version}`)
}

/** 获取节点列表（含版本信息） */
export function getAgentUpgradeNodes() {
  return request.get<any, Result<AgentNode[]>>('/agent-upgrade/nodes')
}

/** 执行 Agent 升级 */
export function upgradeAgentNodes(version: string, nodeIds: number[]) {
  return request.post<any, Result<{ batchId: string; version: string; nodeCount: number; status: string }>>('/agent-upgrade/upgrade', {
    version,
    nodeIds
  })
}

/** 查询升级状态 */
export function getAgentUpgradeStatus(batchId: string) {
  return request.get<any, Result<AgentUpgradeStatus>>(`/agent-upgrade/status/${batchId}`)
}

/** 查询升级历史 */
export function getAgentUpgradeRecords(page = 1, pageSize = 50) {
  return request.get<any, Result<{ list: AgentUpgradeRecord[]; total: number }>>('/agent-upgrade/records', {
    params: { page, pageSize }
  })
}
