import request from '../utils/request'
import type { Result } from '../types'

/** 获取日志文件内容 */
export function getLogFile(nodeId: string, logPath: string, offset = 0, lines = 100) {
  return request.get<any, Result<string>>('/files/log', {
    params: { nodeId, logPath, offset, lines }
  })
}

/** 获取YML配置文件内容 */
export function getConfigFile(nodeId: string, configPath: string) {
  return request.get<any, Result<string>>('/files/config', {
    params: { nodeId, configPath }
  })
}

/** 保存YML配置文件 */
export function saveConfigFile(nodeId: string, configPath: string, content: string) {
  return request.post<any, Result>('/files/config', {
    nodeId,
    configPath,
    content
  })
}

/** 批量下载文件 */
export function batchDownload(nodeId: string, filePaths: string[]) {
  return request.post<any, Blob>(`/files/batch-download/${nodeId}`, filePaths, {
    responseType: 'blob'
  })
}
