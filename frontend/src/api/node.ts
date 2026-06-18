import request from '../utils/request'
import type { NodeModel } from '../types'

/** 获取节点列表 */
export function getNodes(page = 1, pageSize = 20, keyword = '') {
  return request.get<any, Result<NodeModel[]>>('/nodes', {
    params: { page, pageSize, keyword }
  })
}

/** 获取节点详情 */
export function getNode(id: string) {
  return request.get<any, Result<NodeModel>>(`/nodes/${id}`)
}

/** 新增节点 */
export function createNode(node: NodeModel) {
  return request.post<any, Result<NodeModel>>('/nodes', node)
}

/** 更新节点 */
export function updateNode(id: string, node: NodeModel) {
  return request.put<any, Result<NodeModel>>(`/nodes/${id}`, node)
}

/** 删除节点 */
export function deleteNode(id: string) {
  return request.delete<any, Result>(`/nodes/${id}`)
}
