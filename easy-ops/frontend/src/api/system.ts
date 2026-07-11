import request from '../utils/request'
import type { Result } from '../types'

export interface GlobalPaths {
  deployBaseDir: string
  logSubDir: string
  configSubDir: string
  frontendSubDir: string
}

/** 获取全局路径配置 */
export function getGlobalPaths() {
  return request.get<any, Result<GlobalPaths>>('/system/paths')
}
