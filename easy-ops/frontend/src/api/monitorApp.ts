import request from '../utils/request'
import type {
  Result, AppMonitorOverview, AppMonitorDashboard, AppMonitorNodeInfo,
  MonitorSnapshotModel, ProjectHealthProbeModel, AIDiagnosisRecordModel,
  MonitorCollectConfig, AgentStatusResult, ThreadTopResult, ThreadInfoResult,
  JvmDetailResult
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

/** 立即采集全部应用监控数据（异步，返回 taskId） */
export function collectAppMonitor() {
  return request.post<any, Result<{ taskId: string }>>('/monitor/app/collect')
}

/** 查询采集任务进度 */
export function getCollectStatus(taskId: string) {
  return request.get<any, Result<{
    status: string
    startTime: number
    totalNodes: number
    completedNodes: number
    error: string | null
  }>>('/monitor/app/collect/status', { params: { taskId } })
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

/** 实时重新采集单个节点（不落库、不告警），保证详情页拿到 Agent 当前真实 PID */
export function refreshAppNodeDetail(projectId: number, nodeId: number) {
  return request.get<any, Result<AppMonitorNodeInfo>>('/monitor/app/node/refresh', {
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


/** 选择性采集监控数据（异步，返回 taskId） */
export function collectAppMonitorFiltered(projectIds?: number[], nodeIds?: number[]) {
  return request.post<any, Result<{ taskId: string }>>('/monitor/app/collect-filtered', { projectIds, nodeIds })
}

/** Agent 状态列表（分页，含系统资源信息） */
export function getAgentStatus(page = 1, pageSize = 20, keyword?: string) {
  return request.get<any, Result<AgentStatusResult>>('/monitor/agent/status', {
    params: { page, pageSize, keyword }
  })
}

/** 线程 CPU 使用率排名（按需加载，仅用户点击查看时调用） */
export function getThreadTop(nodeId: number, pid: number, top = 20) {
  return request.get<any, Result<ThreadTopResult>>(`/agent/${nodeId}/process/thread-top`, {
    params: { pid, top }
  })
}

/** 线程详情：状态分布 + 死锁检测 + 栈摘要（按需加载） */
export function getThreadInfo(nodeId: number, pid: number, maxStack = 5) {
  return request.get<any, Result<ThreadInfoResult>>(`/agent/${nodeId}/process/thread-info`, {
    params: { pid, maxStack }
  })
}

/** JVM 详情：堆分区 + 非堆 + GC 详情 + 类加载 + fd（按需加载） */
export function getJvmDetail(nodeId: number, pid: number) {
  return request.get<any, Result<JvmDetailResult>>(`/agent/${nodeId}/process/jvm-detail`, {
    params: { pid }
  })
}