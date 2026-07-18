import request from '../utils/request'
import type { OperationLogModel, Result } from '../types'

/** 获取操作日志列表 */
export function getOperationLogs(module = '', userId?: number, page = 1, pageSize = 20) {
  const params: any = { module, page, pageSize }
  if (userId) params.userId = userId
  return request.get<any, Result<{ list: OperationLogModel[]; total: number }>>(`/auth/operations`, {
    params
  })
}
