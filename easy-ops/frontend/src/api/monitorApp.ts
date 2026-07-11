import request from '../utils/request'
import type {
  Result, AppMonitorOverview, AppMonitorDashboard, AppMonitorNodeInfo,
  MonitorSnapshotModel, ProjectHealthProbeModel, AIDiagnosisRecordModel,
  MonitorCollectConfig
} from '../types'

/** 获取监控采集配置 */
export function getMonitorCollectConfig() {
  return request.get<any, Result<MonitorCollectConfig>>('/monitor/app/config')
}

/** 保存监控采集配置 */
export function saveMonitorCollectConfig(collectIntervalSec: number) {
  return request.post<any, Result<MonitorCollectConfig>>('/monitor/app/config', { collectIntervalSec })
}

/** 全部应用监控总览（应用管理中的每个项目） */
export function getAppDashboard() {
  return request.get<any, Result<AppMonitorDashboard>>('/monitor/app/dashboard')
}

/** 立即采集全部应用监控数据 */
export function collectAppMonitor() {
  return request.post<any, Result<string>>('/monitor/app/collect')
}

/** 项目应用监控总览 */
export function getAppOverview(projectId: number) {
  return request.get<any, Result<AppMonitorOverview>>('/monitor/app/overview', {
    params: { projectId }
  })
}

/** 单节点详细指标 */
export function getAppNodeDetail(projectId: number, nodeId: number) {
  return request.get<any, Result<AppMonitorNodeInfo>>('/monitor/app/node', {
    params: { projectId, nodeId }
  })
}

/** 指标历史曲线 */
export function getAppHistory(
  projectId: number,
  nodeId?: number,
  startTime?: number,
  endTime?: number,
  limit = 500
) {
  return request.get<any, Result<MonitorSnapshotModel[]>>('/monitor/app/history', {
    params: { projectId, nodeId, startTime, endTime, limit }
  })
}

/** 7 天稳定性评分 */
export function getAppStability(projectId: number, nodeId?: number) {
  return request.get<any, Result<{ projectId: number; nodeId?: number; stabilityScore: number; periodDays: number }>>(
    '/monitor/app/stability',
    { params: { projectId, nodeId } }
  )
}

/** 获取探针配置 */
export function getHealthProbe(projectId: number) {
  return request.get<any, Result<ProjectHealthProbeModel>>('/monitor/health-probe', {
    params: { projectId }
  })
}

/** 保存探针配置 */
export function saveHealthProbe(probe: ProjectHealthProbeModel) {
  return request.post<any, Result<ProjectHealthProbeModel>>('/monitor/health-probe', probe)
}

/** 触发 AI 诊断 */
export function triggerDiagnose(params: {
  projectId: number
  nodeId?: number
  triggerType?: string
  question?: string
  logPath?: string
}) {
  return request.post<any, Result<{ diagnosisId: number; status: string }>>('/ai/diagnose', params)
}

/** 获取诊断报告 */
export function getDiagnosis(id: number) {
  return request.get<any, Result<AIDiagnosisRecordModel>>(`/ai/diagnose/${id}`)
}
