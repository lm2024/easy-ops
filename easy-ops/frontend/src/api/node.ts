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
