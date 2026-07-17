<template>
  <div>
    <a-card :bordered="false" style="border-radius: 8px">
      <template #title>
        <a-space>
          <file-text-outlined style="color: #1890ff" />
          <span style="font-weight: 600">日志管理</span>
        </a-space>
      </template>
      <template #extra>
        <a-space>
          <a-button v-if="projectId" size="small" @click="openLogConfig">
            <setting-outlined /> 日志配置
          </a-button>
          <a-select
            v-model:value="projectId"
            style="width: 220px"
            placeholder="选择项目"
            @change="onProjectChange"
          >
            <a-select-option v-for="p in projects" :key="p.id" :value="Number(p.id)">
              {{ p.name }}
            </a-select-option>
          </a-select>
        </a-space>
      </template>

      <!-- 全局路径提示 -->
      <a-alert
        v-if="globalPaths"
        type="info"
        show-icon
        style="margin-bottom: 12px"
        :message="`全局日志目录规则: {部署目录}/${globalPaths.logSubDir}，当前应用部署目录见「应用管理」`"
      />

      <!-- 日志目录说明 -->
      <a-alert
        v-if="projectId && deployDirHint"
        type="info"
        show-icon
        style="margin-bottom: 12px"
      >
        <template #message>
          <div><strong>生产日志目录结构（Docker Agent 容器内）</strong></div>
          <div>① Agent 平台日志：<code>{{ agentLogDir || '/app/data/logs' }}</code>（agent.log、agent-error.log）</div>
          <div>② 业务应用部署目录：<code>{{ deployDirHint }}</code></div>
          <div>③ 业务应用日志目录：<code>{{ appLogDirHint }}</code>（部署应用后生成 *.log / *.out）</div>
          <div v-if="scannedDirs.length" style="margin-top: 4px">当前节点已扫描目录：{{ scannedDirs.join('、') }}</div>
        </template>
      </a-alert>
      <a-alert
        v-if="logFilesError"
        type="warning"
        :message="logFilesError"
        closable
        style="margin-bottom: 12px"
        @close="logFilesError = ''"
      />

      <a-tabs v-model:activeKey="activeTab">
        <a-tab-pane key="single" tab="单节点查看">
          <a-space style="margin-bottom: 12px">
            <a-select v-model:value="nodeId" style="width: 180px" placeholder="选择节点" :disabled="!projectId">
              <a-select-option v-for="n in projectNodes" :key="n.id" :value="Number(n.id)">
                {{ n.name }}
              </a-select-option>
            </a-select>
            <a-select v-model:value="filePath" style="width: 420px" placeholder="日志文件" :disabled="!nodeId" :loading="logFilesLoading" show-search option-filter-prop="label">
              <a-select-option v-for="f in logFiles" :key="f.path" :value="f.path" :label="`${f.name} ${f.path}`">
                <div class="file-option">
                  <span class="file-option-name">{{ f.name }}</span>
                  <span class="file-option-path">{{ f.path }}</span>
                </div>
              </a-select-option>
            </a-select>
            <a-button :loading="logFilesLoading" :disabled="!nodeId" @click="reloadLogFiles">
              刷新列表
            </a-button>
            <a-select v-model:value="logLevel" style="width: 120px" placeholder="级别">
              <a-select-option value="ALL">全部</a-select-option>
              <a-select-option value="ERROR">ERROR</a-select-option>
              <a-select-option value="WARN">WARN</a-select-option>
              <a-select-option value="INFO">INFO</a-select-option>
              <a-select-option value="DEBUG">DEBUG</a-select-option>
            </a-select>
            <a-button type="primary" :loading="viewLoading" @click="fetchRecentLogs">
              <search-outlined /> 查看最近
            </a-button>
            <a-button :disabled="viewMode !== 'page' || viewOffset <= 0" @click="prevPage">上一页</a-button>
            <a-button :disabled="viewMode !== 'page' || !viewHasMore" @click="nextPage">下一页</a-button>
            <a-button @click="switchToHistoryMode">从头浏览</a-button>
          </a-space>
          <div class="log-hint">
            自动扫描部署目录及 logs 子目录下所有 .log / .out 文件，不依赖固定文件名。
            <span v-if="discoverHint" class="discover-hint">{{ discoverHint }}</span>
          </div>
          <div v-if="currentLogPath" class="current-path">
            当前查看：<code>{{ currentLogPath }}</code>
          </div>
          <pre class="log-viewer"><span
            v-for="(line, idx) in logLines"
            :key="idx"
            :class="['log-line', logLineClass(line)]"
          >{{ line }}{{ idx < logLines.length - 1 ? '\n' : '' }}</span><template v-if="!logLines.length">暂无日志</template></pre>
        </a-tab-pane>

        <a-tab-pane key="aggregate" tab="多节点聚合">
          <a-space style="margin-bottom: 12px">
            <a-select
              v-model:value="aggNodeIds"
              mode="multiple"
              style="width: 360px"
              placeholder="选择节点（空=全部）"
              :disabled="!projectId"
            >
              <a-select-option v-for="n in projectNodes" :key="n.id" :value="Number(n.id)">
                {{ n.name }}
              </a-select-option>
            </a-select>
            <a-button type="primary" :loading="aggLoading" @click="fetchAggregate">
              <search-outlined /> 聚合
            </a-button>
            <a-select v-model:value="aggLogLevel" style="width: 120px">
              <a-select-option value="ALL">全部级别</a-select-option>
              <a-select-option value="ERROR">ERROR</a-select-option>
              <a-select-option value="WARN">WARN</a-select-option>
              <a-select-option value="INFO">INFO</a-select-option>
            </a-select>
          </a-space>
          <a-alert
            v-if="aggDescription"
            type="info"
            show-icon
            style="margin-bottom: 12px"
            :message="aggDescription"
          />
          <a-collapse v-if="aggScopes.length" ghost style="margin-bottom: 12px">
            <a-collapse-panel key="scope" header="查看聚合扫描范围详情">
              <div v-for="ns in aggScopes" :key="ns.nodeId" class="scope-node">
                <div class="scope-node-title">{{ ns.nodeName }}（节点 {{ ns.nodeId }}）</div>
                <div v-if="ns.deployDir">部署目录：<code>{{ ns.deployDir }}</code></div>
                <div v-if="ns.agentLogDir">Agent 日志：<code>{{ ns.agentLogDir }}</code></div>
                <div v-if="ns.scannedDirs?.length">扫描目录：{{ ns.scannedDirs.join('、') }}</div>
                <ul class="scope-file-list">
                  <li v-for="f in ns.files" :key="f.path"><code>{{ f.path }}</code></li>
                </ul>
              </div>
            </a-collapse-panel>
          </a-collapse>
          <div class="log-hint">聚合各节点发现的全部日志文件（每文件尾部 100 行），默认过滤近 7 天内有时间戳的行。</div>
          <a-table
            :columns="aggColumns"
            :data-source="aggEntries"
            :loading="aggLoading"
            :pagination="aggPagination"
            :row-key="(r: LogAggregateEntry) => `${r.nodeId}-${r.sourceFile}-${r.lineNo}`"
            size="small"
            @change="onAggPageChange"
          >
            <template #bodyCell="{ column, record }">
              <template v-if="column.key === 'timestamp'">
                {{ formatTimestamp(record.timestamp) }}
              </template>
              <template v-if="column.key === 'sourceFile'">
                <a-tooltip :title="record.sourcePath || record.sourceFile">
                  <span>{{ record.sourceFile }}</span>
                </a-tooltip>
              </template>
              <template v-if="column.key === 'content'">
                <div class="log-content-cell" @click="showLogDetail(record)">
                  {{ record.content }}
                </div>
              </template>
              <template v-if="column.key === 'action'">
                <a-button size="small" type="link" @click="showLogDetail(record)">详情</a-button>
              </template>
            </template>
          </a-table>
        </a-tab-pane>

        <a-tab-pane key="search" tab="关键词搜索">
          <a-space style="margin-bottom: 12px" wrap>
            <a-input
              v-model:value="searchKeyword"
              placeholder="搜索关键词"
              style="width: 240px"
              @press-enter="doSearch"
            />
            <a-select v-model:value="searchScope" style="width: 140px">
              <a-select-option value="AGGREGATE">聚合搜索</a-select-option>
              <a-select-option value="SINGLE">单节点</a-select-option>
            </a-select>
            <a-select
              v-if="searchScope === 'SINGLE'"
              v-model:value="searchNodeId"
              style="width: 180px"
              placeholder="选择节点"
              :disabled="!projectId"
            >
              <a-select-option v-for="n in projectNodes" :key="n.id" :value="Number(n.id)">
                {{ n.name }}
              </a-select-option>
            </a-select>
            <a-tooltip title="匹配行上下各显示 N 行，帮助理解日志发生的背景">
              <a-input-number v-model:value="contextLines" :min="0" :max="10" addon-before="上下文" />
            </a-tooltip>
            <a-button type="primary" :loading="searchLoading" @click="doSearch">
              <search-outlined /> 搜索
            </a-button>
            <a-select v-model:value="searchLogLevel" style="width: 120px">
              <a-select-option value="ALL">全部级别</a-select-option>
              <a-select-option value="ERROR">ERROR</a-select-option>
              <a-select-option value="WARN">WARN</a-select-option>
              <a-select-option value="INFO">INFO</a-select-option>
            </a-select>
            <a-checkbox v-if="activeTab === 'search' && filePath" v-model:checked="searchCurrentFileOnly">
              仅搜索当前选中文件
            </a-checkbox>
          </a-space>
          <a-alert
            v-if="searchDescription"
            type="info"
            show-icon
            style="margin-bottom: 12px"
            :message="searchDescription"
          />
          <a-collapse v-if="searchScopes.length" ghost style="margin-bottom: 12px">
            <a-collapse-panel key="scope" header="查看搜索扫描范围详情">
              <div v-for="ns in searchScopes" :key="ns.nodeId" class="scope-node">
                <div class="scope-node-title">{{ ns.nodeName }}（节点 {{ ns.nodeId }}）</div>
                <div v-if="ns.deployDir">部署目录：<code>{{ ns.deployDir }}</code></div>
                <div v-if="ns.agentLogDir">Agent 日志：<code>{{ ns.agentLogDir }}</code></div>
                <div v-if="ns.scannedDirs?.length">扫描目录：{{ ns.scannedDirs.join('、') }}</div>
                <ul class="scope-file-list">
                  <li v-for="f in ns.files" :key="f.path"><code>{{ f.path }}</code></li>
                </ul>
              </div>
            </a-collapse-panel>
          </a-collapse>
          <div class="log-hint">搜索会在各节点发现的全部日志文件中匹配关键词（含 Agent 日志与应用日志）。</div>
          <a-list :data-source="searchMatches" :loading="searchLoading">
            <template #renderItem="{ item }">
              <a-list-item>
                <a-list-item-meta>
                  <template #title>
                    <a-tag color="blue">{{ item.nodeName || item.nodeId }}</a-tag>
                    <span v-if="item.file" class="file-tag">{{ item.file }}:{{ item.lineNo }}</span>
                    <a-button size="small" type="link" @click="showSearchDetail(item)">详情</a-button>
                  </template>
                  <template #description>
                    <pre class="match-line">{{ item.matchedLine || item.content }}</pre>
                    <pre v-if="item.context?.length" class="context-lines">{{ item.context.join('\n') }}</pre>
                  </template>
                </a-list-item-meta>
              </a-list-item>
            </template>
            <template #footer>
              <span v-if="searchMatches.length">共 {{ searchMatches.length }} 条命中</span>
            </template>
          </a-list>
        </a-tab-pane>
      </a-tabs>

      <!-- 日志详情弹窗（聚合/搜索共用） -->
      <a-modal
        v-model:open="detailVisible"
        title="日志详情"
        :footer="null"
        width="720px"
      >
        <a-descriptions :column="1" size="small" bordered style="margin-bottom: 16px">
          <a-descriptions-item label="节点">{{ detailLog?.nodeName }}</a-descriptions-item>
          <a-descriptions-item label="文件路径">
            <code>{{ detailLog?.sourcePath || detailLog?.file || detailLog?.sourceFile }}</code>
          </a-descriptions-item>
          <a-descriptions-item v-if="detailLog?.sourceDir" label="所在目录">
            <code>{{ detailLog?.sourceDir }}</code>
          </a-descriptions-item>
          <a-descriptions-item label="行号">{{ detailLog?.lineNo }}</a-descriptions-item>
          <a-descriptions-item label="时间戳">{{ formatTimestamp(detailLog?.timestamp) }}</a-descriptions-item>
        </a-descriptions>
        <div v-if="detailContext.length" style="font-weight: 600; margin-bottom: 8px">上下文</div>
        <pre v-if="detailContext.length" class="detail-log-content">{{ detailContext.join('\n') }}</pre>
        <div style="font-weight: 600; margin-bottom: 8px">完整内容</div>
        <pre class="detail-log-content">{{ detailLog?.content || detailLog?.matchedLine }}</pre>
      </a-modal>
    </a-card>

    <!-- 日志配置 Drawer -->
    <a-drawer
      title="日志配置"
      :open="logConfigOpen"
      :width="480"
      @close="logConfigOpen = false"
    >
      <a-form :model="logProfileForm" layout="vertical">
        <a-form-item label="日志目录" required>
          <a-input v-model:value="logProfileForm.logDir" :placeholder="defaultLogDirHint" />
          <div class="form-help">业务应用日志目录，通常为 {部署目录}/logs</div>
        </a-form-item>
        <a-form-item label="主日志文件（可选）">
          <a-input v-model:value="logProfileForm.mainLogFile" placeholder="留空则自动选择最近更新的日志" />
          <div class="form-help">仅作聚合/搜索默认文件提示，单节点查看会自动扫描全部日志</div>
        </a-form-item>
        <a-form-item label="滚转匹配模式">
          <a-input v-model:value="logProfileForm.rollingPattern" placeholder="*.log" />
          <div class="form-help">兼容旧配置；智能扫描模式下会自动匹配多种日志文件名</div>
        </a-form-item>
        <a-form-item label="时间戳格式" required>
          <a-input v-model:value="logProfileForm.timestampFormat" placeholder="yyyy-MM-dd HH:mm:ss.SSS" />
        </a-form-item>
        <a-form-item label="单行最大长度">
          <a-input-number v-model:value="logProfileForm.maxLineLength" :min="512" :max="65536" style="width: 100%" />
        </a-form-item>
      </a-form>
      <template #extra>
        <a-space>
          <a-button @click="logConfigOpen = false">取消</a-button>
          <a-button type="primary" :loading="logConfigSaving" @click="saveLogConfig">保存</a-button>
        </a-space>
      </template>
    </a-drawer>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { message } from 'ant-design-vue'
import type { ProjectModel, NodeModel, LogFileInfo, LogAggregateEntry, ProjectLogProfileModel, LogNodeScope, LogSearchHit } from '../types'
import { getProjects } from '../api/project'
import { getNodes } from '../api/node'
import { discoverLogFiles, viewLog, aggregateLogs, searchLogs, getLogProfile, saveLogProfile } from '../api/logMgmt'
import { getGlobalPaths, type GlobalPaths } from '../api/system'
import { FileTextOutlined, SearchOutlined, SettingOutlined } from '@ant-design/icons-vue'
import dayjs from 'dayjs'

const projects = ref<ProjectModel[]>([])
const allNodes = ref<NodeModel[]>([])
const projectId = ref<number>()
const activeTab = ref('single')
const nodeId = ref<number>()
const filePath = ref<string>()
const logFiles = ref<LogFileInfo[]>([])
const logFilesLoading = ref(false)
const logFilesError = ref('')
const discoverHint = ref('')
const agentLogDir = ref('')
const scannedDirs = ref<string[]>([])
const logLines = ref<string[]>([])
const currentLogPath = ref('')
const viewOffset = ref(0)
const viewLoading = ref(false)
const viewMode = ref<'tail' | 'page'>('tail')
const viewHasMore = ref(false)
const logLevel = ref('ALL')
const pageSize = 200

const aggNodeIds = ref<number[]>([])
const aggEntries = ref<LogAggregateEntry[]>([])
const aggLoading = ref(false)
const aggLogLevel = ref('ALL')
const aggPagination = ref({ current: 1, pageSize: 100, total: 0 })
const aggScopes = ref<LogNodeScope[]>([])
const aggDescription = ref('')
const detailVisible = ref(false)
const detailLog = ref<(LogAggregateEntry & LogSearchHit) | null>(null)
const detailContext = ref<string[]>([])

const searchKeyword = ref('')
const searchScope = ref('AGGREGATE')
const searchNodeId = ref<number>()
const contextLines = ref(3)
const searchMatches = ref<LogSearchHit[]>([])
const searchLoading = ref(false)
const searchLogLevel = ref('ALL')
const searchScopes = ref<LogNodeScope[]>([])
const searchDescription = ref('')
const searchCurrentFileOnly = ref(false)

// 日志配置抽屉
const logConfigOpen = ref(false)
const logConfigSaving = ref(false)
const logProfileForm = ref<ProjectLogProfileModel>({
  projectId: 0,
  logDir: '',
  mainLogFile: '',
  rollingPattern: '',
  timestampFormat: 'yyyy-MM-dd HH:mm:ss.SSS',
  maxLineLength: 4096
})
const globalPaths = ref<GlobalPaths | null>(null)
const currentProfile = ref<ProjectLogProfileModel | null>(null)

const defaultLogDirHint = computed(() => {
  const sub = globalPaths.value?.logSubDir || 'logs'
  return `{部署目录}/${sub}`
})

const appLogDirHint = computed(() => {
  const proj = projects.value.find(p => Number(p.id) === projectId.value)
  const base = proj?.deployDir || globalPaths.value?.deployBaseDir || '/app/data/apps/{项目名}'
  const sub = globalPaths.value?.logSubDir || 'logs'
  if (base.includes('{')) {
    return `${base}/${sub}`
  }
  return `${base}/${sub}`.replace(/\/+/g, '/')
})

const deployDirHint = computed(() => {
  const proj = projects.value.find(p => Number(p.id) === projectId.value)
  if (proj?.deployDir) {
    return proj.deployDir
  }
  const name = proj?.name || '{项目名}'
  const base = globalPaths.value?.deployBaseDir || '/app/data/apps'
  return `${base}/${name}`.replace(/\/+/g, '/')
})

const aggColumns = [
  { title: '节点', dataIndex: 'nodeName', key: 'nodeName', width: 100 },
  { title: '时间', dataIndex: 'timestamp', key: 'timestamp', width: 180 },
  { title: '文件', dataIndex: 'sourceFile', key: 'sourceFile', width: 120 },
  { title: '内容', dataIndex: 'content', key: 'content' },
  { title: '操作', key: 'action', width: 80, fixed: 'right' as const }
]

const projectNodes = computed(() => {
  if (!projectId.value) return []
  const proj = projects.value.find(p => Number(p.id) === projectId.value)
  if (!proj?.nodeIds) return []
  const ids = proj.nodeIds.split(',').map(s => Number(s.trim()))
  return allNodes.value.filter(n => ids.includes(Number(n.id)))
})

watch(nodeId, async (nid) => {
  if (!projectId.value || !nid) return
  await reloadLogFiles()
})

async function reloadLogFiles() {
  if (!projectId.value || !nodeId.value) return
  logFilesError.value = ''
  discoverHint.value = ''
  logFilesLoading.value = true
  try {
    if (!currentProfile.value) {
      await loadProjectProfile()
    }
    const res = await discoverLogFiles(projectId.value, nodeId.value)
    const data = res.data
    logFiles.value = data?.files || []
    discoverHint.value = data?.hint || ''
    agentLogDir.value = (data as any)?.agentLogDir || ''
    scannedDirs.value = data?.scannedDirs || []
    if (data?.scannedDirs?.length) {
      const dirs = data.scannedDirs.join('、')
      discoverHint.value = discoverHint.value
        ? `${discoverHint.value}（已扫描: ${dirs}）`
        : `已扫描: ${dirs}`
    }

    const suggested = data?.suggestedMain
    const preferred = currentProfile.value?.mainLogFile
    if (suggested) {
      filePath.value = suggested
    } else if (preferred && logFiles.value.some(f => f.name === preferred || f.path === preferred)) {
      const hit = logFiles.value.find(f => f.name === preferred || f.path === preferred)
      filePath.value = hit?.path
    } else if (logFiles.value.length) {
      filePath.value = logFiles.value[0].path
    } else {
      filePath.value = undefined
      logLines.value = []
    }

    if (filePath.value) {
      viewMode.value = 'tail'
      viewOffset.value = 0
      await fetchLogView()
    }
  } catch (e: any) {
    logFiles.value = []
    filePath.value = undefined
    logLines.value = []
    const msg = e?.response?.data?.message || e?.message || '获取日志文件列表失败'
    logFilesError.value = msg
  } finally {
    logFilesLoading.value = false
  }
}


function onProjectChange() {
  nodeId.value = undefined
  logFiles.value = []
  logLines.value = []
  currentLogPath.value = ''
  aggEntries.value = []
  aggScopes.value = []
  aggDescription.value = ''
  searchMatches.value = []
  searchScopes.value = []
  searchDescription.value = ''
  logFilesError.value = ''
  currentProfile.value = null
  viewMode.value = 'tail'
  viewOffset.value = 0
  viewHasMore.value = false
}

function formatTimestamp(ts?: number | string) {
  if (ts == null || ts === '' || ts === 0 || ts === '0') return '-'
  const n = typeof ts === 'number' ? ts : Number(ts)
  if (!n || Number.isNaN(n)) return String(ts)
  return dayjs(n).format('YYYY-MM-DD HH:mm:ss.SSS')
}

async function loadProjectProfile() {
  if (!projectId.value) return
  try {
    const res = await getLogProfile(projectId.value)
    currentProfile.value = res.data || null
  } catch {
    currentProfile.value = null
  }
}

async function openLogConfig() {
  if (!projectId.value) return
  logConfigOpen.value = true
  await loadProjectProfile()
  try {
    const res = await getLogProfile(projectId.value)
    if (res.data) {
      logProfileForm.value = { ...res.data }
    }
  } catch {
    const sub = globalPaths.value?.logSubDir || 'logs'
    logProfileForm.value = {
      projectId: projectId.value || 0,
      logDir: `{部署目录}/${sub}`,
      mainLogFile: 'app.log',
      rollingPattern: '*.log',
      timestampFormat: 'yyyy-MM-dd HH:mm:ss.SSS',
      maxLineLength: 4096
    }
  }
}

async function saveLogConfig() {
  if (!projectId.value) return
  logConfigSaving.value = true
  try {
    logProfileForm.value.projectId = projectId.value || 0
    await saveLogProfile(logProfileForm.value)
    message.success('日志配置已保存')
    logConfigOpen.value = false
    // 重新加载日志文件列表
    if (nodeId.value) {
      await reloadLogFiles()
    }
  } catch (e: any) {
    message.error(e?.response?.data?.message || '保存失败')
  } finally {
    logConfigSaving.value = false
  }
}

async function fetchRecentLogs() {
  viewMode.value = 'tail'
  viewOffset.value = 0
  await fetchLogView()
}

async function fetchLogView() {
  if (!projectId.value || !nodeId.value) {
    message.warning('请先选择项目和节点')
    return
  }
  if (!filePath.value) {
    const hint = discoverHint.value || `请确认 Docker Agent 已启动。Agent 日志在 ${agentLogDir.value || '/app/data/logs'}`
    message.warning(`未找到可查看的日志文件。${hint}`)
    logLines.value = []
    return
  }
  viewLoading.value = true
  try {
    const level = logLevel.value === 'ALL' ? undefined : logLevel.value
    const res = await viewLog(
      projectId.value,
      nodeId.value,
      filePath.value,
      viewOffset.value,
      pageSize,
      level,
      viewMode.value
    )
    const data = res.data as any
    currentLogPath.value = data?.logPath || filePath.value || ''
    if (data?.content) {
      logLines.value = (data.content as string).split('\n').filter((l: string) => l.length > 0)
    } else if (Array.isArray(data?.lines)) {
      logLines.value = data.lines
    } else {
      logLines.value = []
    }
    viewHasMore.value = Boolean(data?.hasMore)
  } catch (e: any) {
    logLines.value = []
    message.error(e?.response?.data?.message || '获取日志失败')
  } finally {
    viewLoading.value = false
  }
}

function switchToHistoryMode() {
  viewMode.value = 'page'
  viewOffset.value = 0
  fetchLogView()
}

function logLineClass(line: string) {
  if (/\sERROR\s/.test(line)) return 'log-error'
  if (/\sWARN\s/.test(line)) return 'log-warn'
  if (/\sINFO\s/.test(line)) return 'log-info'
  if (/\sDEBUG\s/.test(line)) return 'log-debug'
  return ''
}

function prevPage() {
  viewOffset.value = Math.max(0, viewOffset.value - pageSize)
  fetchLogView()
}
function nextPage() {
  viewOffset.value += pageSize
  fetchLogView()
}

async function fetchAggregate() {
  if (!projectId.value) return
  aggLoading.value = true
  try {
    const level = aggLogLevel.value === 'ALL' ? undefined : aggLogLevel.value
    const res = await aggregateLogs(
      projectId.value,
      aggNodeIds.value.length ? aggNodeIds.value : undefined,
      aggPagination.value.current,
      aggPagination.value.pageSize,
      undefined,
      level
    )
    aggEntries.value = res.data?.lines || []
    aggPagination.value.total = res.data?.total || 0
    aggScopes.value = res.data?.nodeScopes || []
    aggDescription.value = res.data?.aggregateDescription || ''
  } catch (e: any) {
    aggEntries.value = []
    aggScopes.value = []
    aggDescription.value = ''
    message.error(e?.response?.data?.message || '聚合查询失败')
  } finally {
    aggLoading.value = false
  }
}

function onAggPageChange(pag: { current: number }) {
  aggPagination.value.current = pag.current
  fetchAggregate()
}

function showLogDetail(record: LogAggregateEntry) {
  detailLog.value = record
  detailContext.value = []
  detailVisible.value = true
}

function showSearchDetail(item: LogSearchHit) {
  detailLog.value = {
    ...item,
    content: item.matchedLine || item.content || "",
    sourcePath: item.file
  }
  detailContext.value = item.context || []
  detailVisible.value = true
}

async function doSearch() {
  if (!projectId.value || !searchKeyword.value) return
  if (searchScope.value === 'SINGLE' && !searchNodeId.value) {
    message.warning('单节点搜索请先选择节点')
    return
  }
  searchLoading.value = true
  try {
    const nodeIds = searchScope.value === 'SINGLE'
      ? (searchNodeId.value ? [searchNodeId.value] : undefined)
      : (aggNodeIds.value.length ? aggNodeIds.value : undefined)

    const res = await searchLogs({
      projectId: projectId.value,
      keyword: searchKeyword.value,
      scope: searchScope.value,
      nodeIds,
      contextLines: contextLines.value,
      level: searchLogLevel.value === 'ALL' ? undefined : searchLogLevel.value,
      filePath: searchCurrentFileOnly.value && filePath.value ? filePath.value : undefined
    })
    searchMatches.value = res.data?.hits || []
    searchScopes.value = res.data?.nodeScopes || []
    searchDescription.value = res.data?.searchDescription || ''
    if (!searchMatches.value.length) {
      message.info('未找到匹配结果，请确认关键词或查看上方扫描范围')
    }
  } catch (e: any) {
    searchMatches.value = []
    searchScopes.value = []
    searchDescription.value = ''
    message.error(e?.response?.data?.message || '搜索失败')
  } finally {
    searchLoading.value = false
  }
}

onMounted(async () => {
  try {
    const gp = await getGlobalPaths()
    globalPaths.value = gp.data
  } catch { /* ignore */ }
  const [pRes, nRes] = await Promise.all([getProjects(1, 100), getNodes(1, 200)])
  projects.value = pRes.data.list || []
  allNodes.value = nRes.data.list || []
})

watch(projectId, () => {
  loadProjectProfile()
})

watch([logLevel, filePath], () => {
  if (projectId.value && nodeId.value && filePath.value && viewMode.value === 'tail') {
    fetchLogView()
  }
})
</script>

<style scoped>
.log-viewer {
  background: #141414;
  padding: 12px;
  border-radius: 6px;
  font-family: 'JetBrains Mono', monospace;
  font-size: 12px;
  max-height: 520px;
  overflow: auto;
  color: #d4d4d8;
  margin: 0;
  white-space: pre-wrap;
  word-break: break-all;
}
.log-line.log-error { color: #f87171; }
.log-line.log-warn { color: #fbbf24; }
.log-line.log-info { color: #93c5fd; }
.log-line.log-debug { color: #a1a1aa; }
.file-meta { color: #71717a; font-size: 11px; margin-left: 4px; }
.file-option { display: flex; flex-direction: column; line-height: 1.3; }
.file-option-name { font-weight: 500; }
.file-option-path { font-size: 11px; color: #71717a; word-break: break-all; }
.current-path { margin-bottom: 8px; font-size: 12px; color: #52525b; }
.current-path code { font-size: 11px; }
.scope-node { margin-bottom: 12px; }
.scope-node-title { font-weight: 600; margin-bottom: 4px; }
.scope-file-list { margin: 4px 0 0; padding-left: 20px; font-size: 12px; }
.discover-hint { color: #a1a1aa; }
.match-line { font-family: monospace; font-size: 12px; margin: 0; }
.context-lines { font-family: monospace; font-size: 11px; color: #71717a; margin: 4px 0 0; }
.file-tag { color: #a1a1aa; font-size: 12px; margin-left: 8px; }
.form-help { font-size: 12px; color: #a1a1aa; margin-top: 4px; }
.log-content-cell {
  cursor: pointer;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 100%;
}
.log-content-cell:hover {
  color: #1890ff;
}
.detail-log-content {
  background: #141414;
  padding: 12px;
  border-radius: 6px;
  font-family: 'JetBrains Mono', monospace;
  font-size: 12px;
  color: #d4d4d8;
  white-space: pre-wrap;
  word-break: break-all;
  max-height: 400px;
  overflow: auto;
  margin: 0;
}
</style>
