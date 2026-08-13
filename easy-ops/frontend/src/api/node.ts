import request from '../utils/request'
import type { NodeModel, NodeTransferApplicationModel, Result } from '../types'

/** 获取节点列表 */
export function getNodes(page = 1, pageSize = 20, keyword = '', status?: string, sortField?: string, sortOrder?: string) {
  return request.get('/nodes', {
    params: { page, pageSize, keyword, status, sortField, sortOrder }
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

// ==================== 节点认领 / 转移工作流 ====================

/** 租户用户申请认领池节点 */
export function claimNode(id: string, remark?: string) {
  return request.post(`/nodes/${id}/claim`, { remark }) as Promise<Result<void>>
}

/** 认领申请列表（平台管理员全量，租户看本租户） */
export function listNodeTransfers(status?: string) {
  return request.get('/nodes/node-transfers', {
    params: { status }
  }) as Promise<Result<NodeTransferApplicationModel[]>>
}

/** 平台管理员批准认领申请 */
export function approveTransfer(id: number) {
  return request.post(`/nodes/node-transfers/${id}/approve`) as Promise<Result<void>>
}

/** 平台管理员拒绝认领申请 */
export function rejectTransfer(id: number) {
  return request.post(`/nodes/node-transfers/${id}/reject`) as Promise<Result<void>>
}

/** 申请人取消待审批申请 */
export function cancelTransfer(id: number) {
  return request.post(`/nodes/node-transfers/${id}/cancel`) as Promise<Result<void>>
}

/** 平台管理员直接分配节点给指定租户 */
export function assignNode(id: string, targetTenantId: number) {
  return request.post(`/nodes/${id}/assign`, { targetTenantId }) as Promise<Result<void>>
}

/** 平台管理员收回节点（回到默认池） */
export function releaseNode(id: string) {
  return request.post(`/nodes/${id}/release`) as Promise<Result<void>>
}
