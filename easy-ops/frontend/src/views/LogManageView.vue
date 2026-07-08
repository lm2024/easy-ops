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

      <!-- 日志配置缺失/错误提示 -->
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
            <a-select v-model:value="fileName" style="width: 240px" placeholder="日志文件" :disabled="!nodeId" :loading="logFilesLoading">
              <a-select-option v-for="f in logFiles" :key="f.name" :value="f.name">{{ f.name }}</a-select-option>
            </a-select>
            <a-button type="primary" :loading="viewLoading" @click="fetchLogView">
              <search-outlined /> 查看
            </a-button>
            <a-button :disabled="viewOffset <= 0" @click="prevPage">上一页</a-button>
            <a-button @click="nextPage">下一页</a-button>
          </a-space>
          <pre class="log-viewer">{{ logLines.join('\n') || '暂无日志' }}</pre>
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
          </a-space>
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

          <!-- 日志详情弹窗 -->
          <a-modal
            v-model:open="detailVisible"
            title="日志详情"
            :footer="null"
            width="720px"
          >
            <a-descriptions :column="2" size="small" bordered style="margin-bottom: 16px">
              <a-descriptions-item label="节点">{{ detailLog?.nodeName }}</a-descriptions-item>
              <a-descriptions-item label="来源文件">{{ detailLog?.sourceFile }}</a-descriptions-item>
              <a-descriptions-item label="行号">{{ detailLog?.lineNo }}</a-descriptions-item>
              <a-descriptions-item label="时间戳">{{ detailLog?.timestamp }}</a-descriptions-item>
            </a-descriptions>
            <div style="font-weight: 600; margin-bottom: 8px">完整内容</div>
            <pre class="detail-log-content">{{ detailLog?.content }}</pre>
          </a-modal>
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
          </a-space>
          <a-list :data-source="searchMatches" :loading="searchLoading">
            <template #renderItem="{ item }">
              <a-list-item>
                <a-list-item-meta>
                  <template #title>
                    <a-tag color="blue">{{ item.nodeName || item.nodeId }}</a-tag>
                    <span v-if="item.file" class="file-tag">{{ item.file }}:{{ item.lineNo }}</span>
                  </template>
                  <template #description>
                    <pre class="match-line">{{ item.matchedLine }}</pre>
                    <pre v-if="item.context?.length" class="context-lines">{{ item.context.join('\n') }}</pre>
                  </template>
                </a-list-item-meta>
              </a-list-item>
            </template>
          </a-list>
        </a-tab-pane>
      </a-tabs>
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
          <a-input v-model:value="logProfileForm.logDir" placeholder="/app/data/logs" />
        </a-form-item>
        <a-form-item label="主日志文件" required>
          <a-input v-model:value="logProfileForm.mainLogFile" placeholder="agent.log" />
        </a-form-item>
        <a-form-item label="滚转匹配模式">
          <a-input v-model:value="logProfileForm.rollingPattern" placeholder="例: agent-%d{yyyy-MM-dd}.log" />
          <div class="form-help">用于匹配历史滚转日志，留空则忽略历史日志</div>
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
import type { ProjectModel, NodeModel, LogFileInfo, LogAggregateEntry, ProjectLogProfileModel } from '../types'
import { getProjects } from '../api/project'
import { getNodes } from '../api/node'
import { listLogFiles, viewLog, aggregateLogs, searchLogs, getLogProfile, saveLogProfile } from '../api/logMgmt'
import { FileTextOutlined, SearchOutlined, SettingOutlined } from '@ant-design/icons-vue'

const projects = ref<ProjectModel[]>([])
const allNodes = ref<NodeModel[]>([])
const projectId = ref<number>()
const activeTab = ref('single')
const nodeId = ref<number>()
const fileName = ref<string>()
const logFiles = ref<LogFileInfo[]>([])
const logFilesLoading = ref(false)
const logFilesError = ref('')
const logLines = ref<string[]>([])
const viewOffset = ref(0)
const viewLoading = ref(false)
const pageSize = 200

const aggNodeIds = ref<number[]>([])
const aggEntries = ref<LogAggregateEntry[]>([])
const aggLoading = ref(false)
const aggPagination = ref({ current: 1, pageSize: 100, total: 0 })
const detailVisible = ref(false)
const detailLog = ref<LogAggregateEntry | null>(null)

const searchKeyword = ref('')
const searchScope = ref('AGGREGATE')
const searchNodeId = ref<number>()
const contextLines = ref(3)
const searchMatches = ref<any[]>([])
const searchLoading = ref(false)

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

const aggColumns = [
  { title: '节点', dataIndex: 'nodeName', key: 'nodeName', width: 100 },
  { title: '时间', dataIndex: 'timestamp', key: 'timestamp', width: 180 },
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
  logFilesError.value = ''
  logFilesLoading.value = true
  try {
    const res = await listLogFiles(projectId.value, nid)
    logFiles.value = res.data || []
    if (logFiles.value.length) {
      fileName.value = logFiles.value[0].name
    } else {
      fileName.value = undefined
    }
  } catch (e: any) {
    logFiles.value = []
    fileName.value = undefined
    const msg = e?.response?.data?.message || e?.message || '获取日志文件列表失败'
    logFilesError.value = `无法加载日志文件：${msg}。请检查「日志配置」中的日志目录是否正确。`
  } finally {
    logFilesLoading.value = false
  }
})

function onProjectChange() {
  nodeId.value = undefined
  logFiles.value = []
  logLines.value = []
  aggEntries.value = []
  searchMatches.value = []
  logFilesError.value = ''
}

async function openLogConfig() {
  if (!projectId.value) return
  logConfigOpen.value = true
  try {
    const res = await getLogProfile(projectId.value)
    if (res.data) {
      logProfileForm.value = { ...res.data }
    }
  } catch {
    // 使用默认值
    logProfileForm.value = {
      projectId: projectId.value || 0,
      logDir: '/app/data/logs',
      mainLogFile: 'agent.log',
      rollingPattern: '',
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
      const res = await listLogFiles(projectId.value, nodeId.value)
      logFiles.value = res.data || []
      if (logFiles.value.length) fileName.value = logFiles.value[0].name
    }
  } catch (e: any) {
    message.error(e?.response?.data?.message || '保存失败')
  } finally {
    logConfigSaving.value = false
  }
}

async function fetchLogView() {
  if (!projectId.value || !nodeId.value) return
  viewLoading.value = true
  try {
    const res = await viewLog(projectId.value, nodeId.value, fileName.value, viewOffset.value, pageSize)
    const data = res.data as any
    // 后端返回 content 字符串，前端按行分割
    if (data?.content) {
      logLines.value = (data.content as string).split('\n')
    } else if (Array.isArray(data?.lines)) {
      logLines.value = data.lines
    } else {
      logLines.value = []
    }
  } catch (e: any) {
    logLines.value = []
    message.error(e?.response?.data?.message || '获取日志失败')
  } finally {
    viewLoading.value = false
  }
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
    const res = await aggregateLogs(
      projectId.value,
      aggNodeIds.value.length ? aggNodeIds.value : undefined,
      aggPagination.value.current,
      aggPagination.value.pageSize
    )
    aggEntries.value = res.data?.lines || []
    aggPagination.value.total = res.data?.total || 0
  } catch (e: any) {
    aggEntries.value = []
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
    // SINGLE 模式传单个 nodeId，AGGREGATE 模式不传或传多个
    const nodeIds = searchScope.value === 'SINGLE'
      ? (searchNodeId.value ? [searchNodeId.value] : undefined)
      : (aggNodeIds.value.length ? aggNodeIds.value : undefined)

    const res = await searchLogs({
      projectId: projectId.value,
      keyword: searchKeyword.value,
      scope: searchScope.value,
      nodeIds,
      contextLines: contextLines.value
    })
    // 后端返回 { hits: [...], totalHits, keyword }，字段: matchedLine/file/lineNo
    searchMatches.value = (res.data as any)?.hits || []
  } catch (e: any) {
    searchMatches.value = []
    message.error(e?.response?.data?.message || '搜索失败')
  } finally {
    searchLoading.value = false
  }
}

onMounted(async () => {
  const [pRes, nRes] = await Promise.all([getProjects(1, 100), getNodes(1, 200)])
  projects.value = pRes.data.list || []
  allNodes.value = nRes.data.list || []
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
}
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
