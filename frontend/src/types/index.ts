// 通用类型定义

/** 统一响应格式 */
export interface Result<T = any> {
  code: number
  message: string
  data: T
}

/** 用户类型 */
export interface UserModel {
  id: number
  username: string
  password?: string
  role: 'ADMIN' | 'OPERATOR'
  status: number
  createTime?: string
  updateTime?: string
}

/** 节点类型 */
export interface NodeModel {
  id: string
  name: string
  ip: string
  port: number
  token: string
  status: number // 0: OFFLINE, 1: ONLINE
  osInfo?: string
  javaVersion?: string
  lastHeartbeat?: string
  createTime?: string
  updateTime?: string
  /** 标签（逗号分隔，如 "dev,前端服务"） */
  tags?: string
  /** CPU 逻辑核数 */
  cpuCores?: number
  /** 总内存（MB） */
  totalMemoryMb?: number
  /** 总磁盘（MB） */
  totalDiskMb?: number
  /** 系统架构 */
  osArch?: string
}

/** 项目类型 */
export interface ProjectModel {
  id: string
  name: string
  startScript?: string
  stopScript?: string
  restartScript?: string
  jvmOpts?: string
  envVars?: string
  jarName?: string
  nodeIds?: string
  createTime?: string
  updateTime?: string
}

/** 版本包类型 */
export interface VersionModel {
  id: string
  projectId: string
  versionId?: string
  version: string
  jarName: string
  filePath: string
  fileSize?: number
  sha256?: string
  remark?: string
  createTime?: string
}

/** 部署记录类型 */
export interface DeployModel {
  id: number
  projectId: string
  versionId?: string
  nodeId?: string
  status: number // 0: PROCESSING, 1: SUCCESS, 2: FAILED, 3: ROLLBACK
  jarName?: string
  log?: string
  startTime?: string
  endTime?: string
  createTime?: string
}

/** 告警类型 */
export interface AlarmModel {
  id: number
  projectId?: string
  nodeId?: string
  type: string
  content: string
  sendResult?: string
  sendTime?: string
  createTime?: string
}

/** 操作审计日志类型 */
export interface OperationLogModel {
  id: number
  userId?: number
  module: string
  action: string
  content: string
  ip?: string
  createTime?: string
}

/** 文件访问日志类型 */
export interface FileAccessLogModel {
  id: number
  userId?: number
  nodeId?: string
  fileType: 'YML' | 'LOG' | 'JAR'
  filePath: string
  action: 'VIEW' | 'EDIT' | 'DOWNLOAD'
  contentSummary?: string
  ip?: string
  createTime?: string
}

/** 告警配置类型 */
export interface AlarmConfigModel {
  id: number
  type: string
  enabled: boolean
  smtpHost?: string
  smtpPort?: number
  smtpUser?: string
  smtpPassword?: string
  smtpSsl?: boolean
  receiveAddress?: string
  createTime?: string
  updateTime?: string
}

/** 系统配置类型 */
export interface SysConfigModel {
  id: number
  configKey: string
  configValue: string
  remark?: string
  createTime?: string
  updateTime?: string
}
