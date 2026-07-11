import request from '../utils/request'
import type { NodeModel, Result } from '../types'

/** 获取节点列表 */
export function getNodes(page = 1, pageSize = 20, keyword = '', status?: string) {
  return request.get('/nodes', {
    params: { page, pageSize, keyword, status }
  }) as Promise<Result<{ list: NodeModel[]; total: number }>>
}

/** 获取节点详情 */
export function getNode(id: string) {
  return request.get(`/nodes/${id}`) as Promise<Result<NodeModel>>
}

/** 新增节点 */
export function createNode(node: NodeModel) {
  return request.post('/nodes', node) as Promise<Result<NodeModel>>
}

/** 更新节点 */
export function updateNode(id: string, node: NodeModel) {
  return request.put(`/nodes/${id}`, node) as Promise<Result<NodeModel>>
}

/** 删除节点 */
export function deleteNode(id: string) {
  return request.delete(`/nodes/${id}`) as Promise<Result<void>>
}

/** 更新节点标签 */
export function updateNodeTags(id: string, tags: string) {
  return request.put(`/nodes/${id}/tags`, { tags }) as Promise<Result<void>>
}

/** 导出节点CSV（通过fetch下载，自动携带token） */
export async function exportNodesCsv() {
  const token = localStorage.getItem('token')
  const response = await fetch('/api/nodes/export', {
    headers: { Authorization: `Bearer ${token}` }
  })
  if (!response.ok) throw new Error('导出失败')
  const blob = await response.blob()
  const url = window.URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = 'nodes.csv'
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  window.URL.revokeObjectURL(url)
}

/** 导入节点CSV */
export function importNodesCsv(file: File) {
  const formData = new FormData()
  formData.append('file', file)
  return request.post('/nodes/import', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  }) as Promise<Result<{ imported: number }>>
}

/** 上传 Agent 升级包到 Server */
export function uploadAgentPackage(file: File) {
  const formData = new FormData()
  formData.append('file', file)
  return request.post('/nodes/agent/package', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  }) as Promise<Result<{ exists: boolean; path: string; size?: number; sha256?: string }>>
}

/** 获取 Server 端 Agent 升级包信息 */
export function getAgentPackageInfo() {
  return request.get('/nodes/agent/package') as Promise<Result<{ exists: boolean; path: string; size?: number }>>
}

/** 升级单个节点 Agent */
export function upgradeNodeAgent(nodeId: string) {
  return request.post(`/nodes/${nodeId}/agent/upgrade`) as Promise<Result<Record<string, unknown>>>
}

/** 批量升级 Agent（nodeIds 为空则升级全部在线节点） */
export function batchUpgradeAgents(nodeIds?: string[]) {
  return request.post('/nodes/agent/upgrade/batch', nodeIds ? { nodeIds } : {}) as Promise<
    Result<{ success: number; failed: number; total: number; results: Array<Record<string, unknown>> }>
  >
}
