import request from '../utils/request'
import type { Result } from '../types'

/** 获取部署记录列表 */
export function getDeployRecords(projectId: string, page = 1, pageSize = 20) {
  return request.get<any, Result<{ list: any[]; total: number }>>(`/deploy`, {
    params: { projectId, page, pageSize }
  })
}

/** 获取部署记录详情 */
export function getDeployRecord(id: number) {
  return request.get<any, Result<any>>(`/deploy/${id}`)
}

/** 创建部署（返回实时状态和日志） */
export function createDeploy(projectId: string, versionId: string, nodeId?: string, scheduleTime?: number) {
  const body: any = { projectId, versionId }
  if (nodeId) body.nodeId = nodeId
  if (scheduleTime) body.scheduleTime = scheduleTime
  // 多节点部署可能耗时较长，设置 5 分钟超时
  return request.post<any, Result<{ recordId: number; status: number; log: string }>>('/deploy', body, {
    timeout: 300000
  })
}

/** 回滚部署 */
export function rollbackDeploy(id: number) {
  return request.post<any, Result>(`/deploy/${id}/rollback`)
}

/** 取消定时部署 */
export function cancelScheduledDeploy(id: number) {
  return request.post<any, Result>(`/deploy/${id}/cancel`)
}
