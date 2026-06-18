import request from '../utils/request'
import type { DeployModel } from '../types'

/** 获取部署记录列表 */
export function getDeployRecords(projectId: string, page = 1, pageSize = 20) {
  return request.get<any, Result<DeployModel[]>>(`/deploy`, {
    params: { projectId, page, pageSize }
  })
}

/** 获取部署记录详情 */
export function getDeployRecord(id: number) {
  return request.get<any, Result<DeployModel>>(`/deploy/${id}`)
}

/** 创建部署 */
export function createDeploy(projectId: string, versionId: string, nodeId: string) {
  return request.post<any, Result<{ deployId: string }>>('/deploy', {
    projectId,
    versionId,
    nodeId
  })
}

/** 回滚部署 */
export function rollbackDeploy(id: number) {
  return request.post<any, Result>(`/deploy/${id}/rollback`)
}
