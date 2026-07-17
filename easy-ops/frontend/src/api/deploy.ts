import request from '../utils/request'
import type { Result } from '../types'

/** 获取部署记录列表（projectId 为空则返回所有项目的记录） */
export function getDeployRecords(projectId?: string, page = 1, pageSize = 20) {
  const params: any = { page, pageSize }
  if (projectId) params.projectId = projectId
  return request.get<any, Result<{ list: any[]; total: number }>>(`/deploy`, { params })
}

/** 获取部署记录详情 */
export function getDeployRecord(id: number) {
  return request.get<any, Result<any>>(`/deploy/${id}`)
}

/** 创建部署（立即返回 deployId，通过 WebSocket 获取实时进度） */
export function createDeploy(projectId: string, versionId: string, nodeId?: string, scheduleTime?: number) {
  const body: any = { projectId, versionId }
  if (nodeId) body.nodeId = nodeId
  if (scheduleTime) body.scheduleTime = scheduleTime
  return request.post<any, Result<{ deployId: string; status: number; message: string }>>('/deploy', body, {
    timeout: 30000
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

/** 强制释放部署锁（卡住时使用） */
export function forceUnlockDeploy(projectId: string) {
  return request.post<any, Result<string>>(`/deploy/unlock/${projectId}`)
}
