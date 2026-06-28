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

/** 项目配置文件定义 */
export interface ProjectConfigFileModel {
  id?: number
  projectId: number
  fileName: string
  relativePath: string
  isPrimary?: number
  remark?: string
  createTime?: number
  updateTime?: number
}

/** 配置快照查询结果 */
export interface ConfigSnapshotResult {
  configFile: ProjectConfigFileModel
  nodes: NodeConfigSnapshotModel[]
  allSame: boolean
}

/** 节点配置快照 */
export interface NodeConfigSnapshotModel {
  id?: number
  projectId: number
  nodeId: number
  configFileId: number
  contentHash?: string
  contentSize?: number
  syncStatus?: number
  lastSyncTime?: number
  updateTime?: number
  nodeName?: string
}

/** 配置对比结果 */
export interface ConfigCompareResult {
  baseNodeId: number
  diffs: Array<{
    nodeId: number
    nodeName?: string
    identical: boolean
    diffLines?: string[]
  }>
}

/** 项目日志配置 */
export interface ProjectLogProfileModel {
  id?: number
  projectId: number
  logDir?: string
  mainLogFile?: string
  rollingPattern?: string
  timestampRegex?: string
  timestampFormat?: string
  maxLineLength?: number
  createTime?: number
  updateTime?: number
}

/** 日志文件信息 */
export interface LogFileInfo {
  name: string
  size?: number
  lastModified?: number
}

/** 日志查看结果 */
export interface LogViewResult {
  content: string
  lines: number
  offset: number
  logPath: string
  totalLines?: number
  fileName?: string
}

/** 日志聚合条目 */
export interface LogAggregateEntry {
  nodeId: number
  nodeName?: string
  timestamp?: string
  content: string
  lineNo?: number
  sourceFile?: string
}

export interface LogAggregateResult {
  lines: LogAggregateEntry[]
  total: number
  pageSize: number
  page: number
}

/** 日志搜索结果（后端返回 { hits: [...], totalHits, keyword }） */
export interface LogSearchResult {
  hits: LogSearchHit[]
  totalHits: number
  keyword?: string
}

/** 单条搜索命中 */
export interface LogSearchHit {
  nodeId: number
  nodeName?: string
  file?: string
  lineNo?: number
  matchedLine?: string
  timestamp?: number
  context?: string[]
}

/** 应用监控节点信息 */
export interface AppMonitorNodeInfo {
  nodeId: number
  nodeName?: string
  healthStatus: 'UP' | 'DOWN' | 'DEGRADED' | string
  processStatus?: string
  cpuPercent?: number
  memoryMb?: number
  heapUsedMb?: number
  heapMaxMb?: number
  hostCpuPercent?: number
  hostMemoryPercent?: number
  diskUsagePercent?: number
  responseMs?: number
  collectTime?: number
  lastError?: string
}

/** 应用监控总览 */
export interface AppMonitorOverview {
  projectId: number
  projectName: string
  summary: {
    totalNodes: number
    upCount: number
    downCount: number
    degradedCount: number
    avgResponseMs: number
    stabilityScore: number
  }
  nodes: AppMonitorNodeInfo[]
}

/** 监控快照历史 */
export interface MonitorSnapshotModel {
  id?: number
  projectId: number
  nodeId: number
  healthStatus?: string
  processStatus?: string
  cpuPercent?: number
  memoryMb?: number
  responseMs?: number
  collectTime?: number
}

/** HTTP 健康探针配置 */
export interface ProjectHealthProbeModel {
  id?: number
  projectId: number
  enabled?: number
  method?: string
  url?: string
  headers?: string
  body?: string
  expectedStatus?: number
  bodyContains?: string
  timeoutMs?: number
  createTime?: number
  updateTime?: number
}

/** AI 诊断记录 */
export interface AIDiagnosisRecordModel {
  id: number
  projectId: number
  nodeId?: number
  triggerType?: string
  status?: string
  question?: string
  logSnippet?: string
  diagnosis?: string
  createTime?: number
}

/** 知识库分类 */
export interface KbCategoryModel {
  id: number
  parentId?: number
  name: string
  icon?: string
  color?: string
  sortOrder?: number
  projectId?: number
  children?: KbCategoryModel[]
  createTime?: number
  updateTime?: number
}

/** 知识库文档 */
export interface KbDocumentModel {
  id?: number
  categoryId: number
  title: string
  summary?: string
  content?: string
  contentSize?: number
  sourceType?: string
  sourceId?: number
  projectId?: number
  authorId?: number
  lastEditorId?: number
  versionNo?: number
  status?: number
  viewCount?: number
  yjsState?: ArrayBuffer | null
  createTime?: number
  updateTime?: number
}

/** 知识库评论 */
export interface KbCommentModel {
  id?: number
  documentId?: number
  parentId?: number
  replyToId?: number
  userId?: number
  content: string
  mentionUserIds?: string
  likes?: number
  type?: 'COMMENT' | 'ANNOTATION'
  annotationId?: string
  rating?: number
  createTime?: number
  updateTime?: number
}

/** 知识库图片 */
export interface KbImageModel {
  id: number
  documentId: number
  fileName?: string
  mimeType?: string
  url?: string
}

/** 自愈策略 */
export interface SelfHealPolicyModel {
  id?: number
  projectId: number
  enabled?: number
  maxRetries?: number
  retryIntervalSec?: number
  checkIntervalSec?: number
  circuitBreaker?: number
  circuitBreakTime?: number
  notifyEmail?: number
  notifyPopup?: number
  autoAiDiagnose?: number
  projectName?: string
  createTime?: number
  updateTime?: number
}

/** 自愈事件 */
export interface SelfHealEventModel {
  id: number
  projectId: number
  nodeId?: number
  nodeName?: string
  eventType: string
  retryCount?: number
  maxRetries?: number
  detail?: string
  processPid?: number
  createTime?: number
}

/** 知识库标签 */
export interface KbTagModel {
  id: number
  name: string
  color?: string
  createTime?: number
}

/** 文档-标签关联 */
export interface KbDocumentTagModel {
  id: number
  documentId: number
  tagId: number
  createTime?: number
}

/** 文档权限 */
export interface KbDocumentPermissionModel {
  id: number
  targetId: number
  targetType: 'CATEGORY' | 'DOCUMENT'
  userId: number
  permissionLevel: 'VIEW' | 'EDIT' | 'MANAGE'
  createTime?: number
}

/** 知识库模板 */
export interface KbTemplateModel {
  id: number
  name: string
  description?: string
  content?: string
  icon?: string
  category?: string
  userId?: number
  isSystem?: number
  createTime?: number
  updateTime?: number
}

/** 知识库收藏 */
export interface KbFavoriteModel {
  id: number
  documentId: number
  userId: number
  createTime?: number
}

/** 知识库最近访问 */
export interface KbRecentAccessModel {
  id: number
  documentId: number
  userId: number
  accessType?: 'VIEW' | 'EDIT'
  createTime?: number
}

/** 知识库外链分享 */
export interface KbShareLinkModel {
  id: number
  documentId: number
  token: string
  password?: string
  expireTime?: number
  createUserId?: number
  createTime?: number
}

/** 站内通知 */
export interface NotificationRecordModel {
  id: number
  type: string
  level: string
  title: string
  content?: string
  projectId?: number
  nodeId?: number
  sourceType?: string
  sourceId?: number
  requireAck?: number
  broadcast?: number
  createTime?: number
  expireTime?: number
  readStatus?: number
  ackStatus?: number
}
