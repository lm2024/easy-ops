import request from '../utils/request'
import type { OperationLogModel } from '../types'

/** 获取操作日志列表 */
export function getOperationLogs(module = '', page = 1, pageSize = 20) {
  return request.get<any, Result<OperationLogModel[]>>(`/operations`, {
    params: { module, page, pageSize }
  })
}
