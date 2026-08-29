import request from '../utils/request'
import type { Result } from '../types'
import type {
  ArthasSession,
  ArthasCommandResult,
  ArthasDiagnoseRecord,
  ArthasDiagnoseResult
} from '../types/arthas'

/** 启动诊断会话 */
export function startArthasDiagnose(data: { projectId: number; nodeId: number; pid: number }) {
  return request.post<any, Result<ArthasSession>>('/arthas/diagnose/start', data)
}

/** 结束诊断会话 */
export function stopArthasDiagnose(data: { sessionId: string }) {
  return request.post<any, Result<{ sessionId: string; status: string; durationMs: number }>>('/arthas/diagnose/stop', data)
}

/** 查询会话状态 */
export function getArthasDiagnoseStatus(sessionId: string) {
  return request.get<any, Result<ArthasSession>>('/arthas/diagnose/status', { params: { sessionId } })
}

/** 执行命令 */
export function execArthasCommand(data: { sessionId: string; command: string; timeoutMs?: number }) {
  return request.post<any, Result<ArthasCommandResult>>('/arthas/diagnose/exec', data)
}

/** 诊断历史列表 */
export function getDiagnoseHistory(params: {
  projectId?: number
  nodeId?: number
  status?: string
  startTime?: number
  endTime?: number
  page: number
  pageSize: number
}) {
  return request.get<any, Result<{ list: ArthasDiagnoseRecord[]; total: number; page: number; pageSize: number }>>(
    '/arthas/diagnose/history',
    { params }
  )
}

/** 诊断详情 */
export function getDiagnoseDetail(id: number) {
  return request.get<any, Result<ArthasDiagnoseRecord & { results: ArthasDiagnoseResult[] }>>(
    '/arthas/diagnose/detail',
    { params: { id } }
  )
}

/** 删除诊断记录 */
export function deleteDiagnoseRecord(id: number) {
  return request.delete<any, Result<null>>('/arthas/diagnose/delete', { params: { id } })
}

/** 火焰图历史文件列表 */
export function getFlamegraphList(sessionId: string) {
  return request.get<any, Result<{
    list: Array<{ fileName: string; filePath: string; size: number; lastModified: number }>
    total: number
    downloadBaseUrl: string
  }>>('/arthas/diagnose/flamegraph-list', { params: { sessionId } })
}

/** 一键诊断（自动分析内存问题） */
export function autoDiagnose(data: { sessionId: string; type?: string }) {
  return request.post<any, Result<any>>('/arthas/diagnose/auto', data)
}
