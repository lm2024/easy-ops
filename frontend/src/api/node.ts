import request from '../utils/request'
import type { NodeModel } from '../types'

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
