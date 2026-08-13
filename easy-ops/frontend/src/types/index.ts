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
  role: string
  status: number
  createTime?: string
  updateTime?: string
  /** 当前生效租户（transient） */
  tenantId?: number
  /** 租户内角色：TENANT_ADMIN / OPERATOR / VIEWER（transient） */
  tenantRole?: string
  /** 租户名称（transient） */
  tenantName?: string
}

/** 租户类型 */
export interface TenantModel {
  id: number
  code: string
  name: string
  status: number
  createTime?: number
  updateTime?: number
  /** 统计字段 */
  nodeCount?: number
  projectCount?: number
  memberCount?: number
}

/** 租户成员类型 */
export interface TenantMemberModel {
  id: number
  tenantId: number
  userId: number
  username?: string
  role: string
  status: number
  createTime?: number
  updateTime?: number
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
  /** Agent 版本号（心跳上报） */
  agentVersion?: string
  /** 所属租户（隔离归属） */
  tenantId?: number
  /** 所属租户名称（列表展示） */
  tenantName?: string
  /** 是否可认领池节点（default 归属且非当前租户） */
  claimable?: boolean
}

/** 节点认领/转移申请 */
export interface NodeTransferApplicationModel {
  id: number
  nodeId: number
  nodeName?: string
  applicantId?: number
  applicantUsername?: string
  targetTenantId?: number
  targetTenantName?: string
  sourceTenantId?: number
  status: string
  remark?: string
  createTime?: number
  updateTime?: number
  approveTime?: number
  approverId?: number
  approverUsername?: string
}

/** 项目类型 */
export interface ProjectModel {
  id: string
  name: string
  projectType?: 'backend' | 'frontend'
  startScript?: string
  stopScript?: string
  restartScript?: string
  jvmOpts?: string
  envVars?: string
  jarName?: string
  deployDir?: string
  frontendDirName?: string
  frontendDeployDir?: string
  nodeIds?: string
  healthCheckEnabled?: boolean
  healthCheckPort?: number
  healthCheckPath?: string
  healthCheckKeyword?: string
  monitorIntervalSec?: number
  status?: number
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
  packageType?: 'jar' | 'frontend'
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
  id?: number
  type?: string
  enabled?: boolean
  smtpHost?: string
  smtpPort?: number
  smtpUser?: string
  smtpPassword?: string
  smtpSsl?: boolean
  receiveAddress?: string
  createTime?: string
  healthCheckEnabled?: boolean
  cpuEnabled?: boolean
  cpuThreshold?: number
  responseEnabled?: boolean
  responseThreshold?: number
  nodeOfflineEnabled?: boolean
  cooldownMinutes?: number
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

/** 全局脚本文件定义（不绑定项目，管理所有 Agent 节点的脚本） */
export interface GlobalScriptFileModel {
  id?: number
  projectId?: number      // 项目ID（可选，全局脚本为空）
  fileName: string
  filePath: string        // 文件路径（Agent 节点上的绝对路径）
  fileType?: string       // 文件类型：sh/conf/cron/service/yaml/yml/properties/other
  description?: string
  isExecutable?: number   // 是否需要可执行权限：0-否 1-是
  autoBackup?: number     // 分发前是否自动备份：0-否 1-是
  createTime?: number
  updateTime?: number
}

/** 全局脚本快照查询结果 */
export interface GlobalScriptSnapshotResult {
  scriptFile: GlobalScriptFileModel
  nodes: GlobalNodeScriptSnapshotModel[]
  allSame: boolean
}

/** 全局脚本节点快照 */
export interface GlobalNodeScriptSnapshotModel {
  id?: number
  nodeId: number
  scriptFileId: number
  contentHash?: string
  contentSize?: number
  fileMode?: number       // 文件权限（八进制）
  syncStatus?: number     // 0-未知 1-一致 2-差异 3-定制
  lastSyncTime?: number
  updateTime?: number
  nodeName?: string
  nodeIp?: string
  nodeStatus?: number     // 节点状态：0-离线 1-在线
}

/** 脚本分发结果 */
export interface ScriptDistributeResult {
  recordId: number
  totalNodes: number
  successCount: number
  failCount: number
  results: Array<{
    nodeId: number
    nodeName?: string
    success: boolean
    error?: string
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

/** Nginx 访问日志采集源 */
export interface NginxAccessSourceModel {
  id?: number
  nodeId: number
  name: string
  logPath: string
  logFormat?: string
  enabled?: number
  slowThresholdSec?: number
  maxKeysPerMinute?: number
  lastOffset?: number
  lastInode?: number
  lastReportTime?: number
  lastError?: string
  createTime?: number
  updateTime?: number
}

/** Nginx 流量告警规则 */
export interface NginxTrafficAlarmRuleModel {
  id?: number
  sourceId?: number
  ruleType: string
  enabled?: number
  windowMinutes?: number
  threshold?: number
  level?: 'CRITICAL' | 'WARNING' | 'INFO' | string
  cooldownMinutes?: number
  requireAck?: number
  createTime?: number
  updateTime?: number
}

/** Nginx 日志源白名单（查询侧排除，不参与统计/告警） */
export interface NginxSourceWhitelistModel {
  /** 前端临时 key，用于列表行标识 */
  __key?: number
  id?: number
  sourceId?: number
  /** 维度：IP / URI / URI_PREFIX / METHOD */
  type: string
  /** 匹配值（METHOD 为 GET/POST...） */
  matchValue: string
  /** 匹配方式：EXACT / PREFIX / CONTAINS */
  matchMode?: string
  enabled?: number
  remark?: string
  createTime?: number
  updateTime?: number
}

/** 日志文件信息 */
export interface LogFileInfo {
  name: string
  path: string
  size?: number
  lastModified?: number
  sourceDir?: string
  category?: 'app' | 'agent' | string
}

/** 日志发现结果 */
export interface LogDiscoverResult {
  files: LogFileInfo[]
  hint?: string
  scannedDirs?: string[]
  suggestedMain?: string
  agentLogDir?: string
  deployDir?: string
  total?: number
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

/** 节点日志扫描范围 */
export interface LogNodeScope {
  nodeId: number
  nodeName?: string
  deployDir?: string
  agentLogDir?: string
  scannedDirs?: string[]
  files?: LogFileInfo[]
  fileCount?: number
}

/** 日志聚合条目 */
export interface LogAggregateEntry {
  nodeId: number
  nodeName?: string
  timestamp?: number
  content: string
  lineNo?: number
  sourceFile?: string
  sourcePath?: string
  sourceDir?: string
}

export interface LogAggregateResult {
  lines: LogAggregateEntry[]
  total: number
  pageSize: number
  page: number
  nodeScopes?: LogNodeScope[]
  aggregateDescription?: string
}

/** 日志搜索结果（后端返回 { hits: [...], totalHits, keyword }） */
export interface LogSearchResult {
  hits: LogSearchHit[]
  totalHits: number
  keyword?: string
  scope?: string
  nodeScopes?: LogNodeScope[]
  searchDescription?: string
}

/** 单条搜索命中 */
export interface LogSearchHit {
  nodeId: number
  nodeName?: string
  file?: string
  fileName?: string
  lineNo?: number
  matchedLine?: string
  content?: string
  timestamp?: number
  context?: string[]
}

/** 应用监控节点信息 */
export interface AppMonitorNodeInfo {
  nodeId: number
  nodeName?: string
  ip?: string
  port?: number
  healthStatus: 'UP' | 'DOWN' | 'DEGRADED' | string
  healthDetail?: string
  processStatus?: string
  processPid?: number
  agentPid?: number
  cpuPercent?: number
  memoryMb?: number
  heapUsedMb?: number
  heapMaxMb?: number
  xmxMb?: number
  gcCount?: number
  gcTimeMs?: number
  hostCpuPercent?: number
  hostMemoryPercent?: number
  diskUsagePercent?: number
  responseMs?: number
  collectTime?: number
  lastError?: string
  extraJson?: string
}

/** 应用监控总览 */
export interface AppMonitorOverview {
  projectId: number
  projectName: string
  jarName?: string
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

/** 全部应用监控仪表盘 */
export interface AppMonitorDashboard {
  summary: {
    totalProjects: number
    totalInstances: number
    upCount: number
    downCount: number
    degradedCount: number
  }
  projects: AppMonitorOverview[]
  collectIntervalSec?: number
}

/** 监控采集配置 */
export interface MonitorCollectConfig {
  collectIntervalSec: number
  minIntervalSec?: number
  maxIntervalSec?: number
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

/** 线程 CPU Top 项 */
export interface ThreadTopItem {
  tid: number
  name?: string
  javaName?: string
  cpuPercent: number
  memPercent?: number
  state?: string
}

/** 线程 CPU Top 结果 */
export interface ThreadTopResult {
  pid: number
  totalThreads: number
  totalCpuPercent: number
  topThreads: ThreadTopItem[]
  stateDistribution: Record<string, number>
}

/** 线程栈信息 */
export interface ThreadDetailItem {
  name: string
  state: string
  stack?: string[]
}

/** 线程详情结果 */
export interface ThreadInfoResult {
  pid: number
  totalThreads: number
  stateDistribution: Record<string, number>
  deadlock: {
    detected: boolean
    threads: string[]
    detail?: string
  }
  threads: ThreadDetailItem[]
}

/** JVM 详情结果 */
export interface JvmDetailResult {
  pid: number
  heapUsedMb?: number
  heapMaxMb?: number
  edenUsedMb?: number
  edenCapacityMb?: number
  survivorUsedMb?: number
  oldUsedMb?: number
  oldCapacityMb?: number
  metaspaceUsedMb?: number
  metaspaceCapacityMb?: number
  compressedClassUsedMb?: number
  compressedClassCapacityMb?: number
  gcYoungCount?: number
  gcFullCount?: number
  gcYoungTimeMs?: number
  gcFullTimeMs?: number
  gcTotalTimeMs?: number
  classLoaded?: number
  classUnloaded?: number
  jitCompileTimeMs?: number
  threadCount?: number
  procThreadCount?: number
  fdCount?: number
  fdLimit?: number
  rssKb?: number
  vmSizeKb?: number
  vmPeakKb?: number
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
  projectName?: string
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


/** Agent 状态项（监控页专用） */
export interface AgentStatusItem {
  nodeId: number
  nodeName: string
  ip: string
  port: number
  status: number
  lastHeartbeat?: number
  collectTime?: number
  osInfo?: string
  cpuCores?: number
  totalMemoryMb?: number
  totalDiskMb?: number
  agentVersion?: string
  hostCpuPercent?: number
  hostMemoryPercent?: number
  diskUsagePercent?: number
  agentPid?: number
}

/** Agent 状态列表响应 */
export interface AgentStatusResult {
  list: AgentStatusItem[]
  total: number
}