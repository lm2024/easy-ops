// Arthas 诊断相关类型定义

export interface ArthasSession {
  sessionId: string
  recordId: number
  pid: number
  projectId: number
  nodeId: number
  arthasVersion: string
  attachTime: number
  status: string
}

export interface ArthasCommandResult {
  success: boolean
  results: any[]
  commandType: string
  durationMs: number
  errorMsg?: string
}

export interface ArthasDiagnoseRecord {
  id: number
  sessionId: string
  projectId: number
  nodeId: number
  pid: number
  jarName: string
  status: string
  triggerBy: string
  arthasVersion: string
  startTime: number
  endTime?: number
  durationMs?: number
  summary?: string
  exception?: string
  tenantId?: number
}

export interface ArthasDiagnoseResult {
  id: number
  recordId: number
  command: string
  commandType: string
  resultJson?: string
  resultFile?: string
  resultSizeKb?: number
  execTime: number
  durationMs: number
  success: boolean
  errorMsg?: string
}

export interface ArthasDiagnoseTarget {
  projectId: number
  nodeId: number
  pid: number
  projectName: string
  nodeName: string
}

// dashboard 解析后的数据
export interface DashboardData {
  thread: {
    total: number
    runnable: number
    timedWaiting: number
    waiting: number
    blocked: number
    deadlock: number
  }
  memory: {
    heapUsed: number
    heapMax: number
    oldGenUsed: number
    oldGenMax: number
    edenUsed: number
    edenMax: number
    survivorUsed: number
    survivorMax: number
    metaspaceUsed: number
    metaspaceMax: number
  }
  gc: {
    youngCount: number
    youngTimeMs: number
    fullCount: number
    fullTimeMs: number
  }
  runtime: {
    osName: string
    javaVersion: string
    processCpuPercent: number
    systemCpuPercent: number
    uptime: number
  }
}
