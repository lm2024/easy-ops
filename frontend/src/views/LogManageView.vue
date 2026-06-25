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
      </template>

      <a-tabs v-model:activeKey="activeTab">
        <a-tab-pane key="single" tab="单节点查看">
          <a-space style="margin-bottom: 12px">
            <a-select v-model:value="nodeId" style="width: 180px" placeholder="选择节点" :disabled="!projectId">
              <a-select-option v-for="n in projectNodes" :key="n.id" :value="Number(n.id)">
                {{ n.name }}
              </a-select-option>
            </a-select>
            <a-select v-model:value="fileName" style="width: 240px" placeholder="日志文件" :disabled="!nodeId">
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
            row-key="line"
            size="small"
            @change="onAggPageChange"
          />
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
            <a-input-number v-model:value="contextLines" :min="0" :max="10" addon-before="上下文" />
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
                    <span v-if="item.fileName" class="file-tag">{{ item.fileName }}:{{ item.lineNumber }}</span>
                  </template>
                  <template #description>
                    <pre class="match-line">{{ item.line }}</pre>
                    <pre v-if="item.context?.length" class="context-lines">{{ item.context.join('\n') }}</pre>
                  </template>
                </a-list-item-meta>
              </a-list-item>
            </template>
          </a-list>
        </a-tab-pane>
      </a-tabs>
    </a-card>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import type { ProjectModel, NodeModel, LogFileInfo, LogAggregateEntry } from '../types'
import { getProjects } from '../api/project'
import { getNodes } from '../api/node'
import { listLogFiles, viewLog, aggregateLogs, searchLogs } from '../api/logMgmt'
import { FileTextOutlined, SearchOutlined } from '@ant-design/icons-vue'

const projects = ref<ProjectModel[]>([])
const allNodes = ref<NodeModel[]>([])
const projectId = ref<number>()
const activeTab = ref('single')
const nodeId = ref<number>()
const fileName = ref<string>()
const logFiles = ref<LogFileInfo[]>([])
const logLines = ref<string[]>([])
const viewOffset = ref(0)
const viewLoading = ref(false)
const pageSize = 200

const aggNodeIds = ref<number[]>([])
const aggEntries = ref<LogAggregateEntry[]>([])
const aggLoading = ref(false)
const aggPagination = ref({ current: 1, pageSize: 100, total: 0 })

const searchKeyword = ref('')
const searchScope = ref('AGGREGATE')
const contextLines = ref(3)
const searchMatches = ref<Array<{ nodeId: number; nodeName?: string; fileName?: string; lineNumber?: number; line: string; context?: string[] }>>([])
const searchLoading = ref(false)

const aggColumns = [
  { title: '节点', dataIndex: 'nodeName', key: 'nodeName', width: 120 },
  { title: '时间', dataIndex: 'timestamp', key: 'timestamp', width: 180 },
  { title: '内容', dataIndex: 'line', key: 'line', ellipsis: true }
]

const projectNodes = computed(() => {
  if (!projectId.value) return []
  const proj = projects.value.find(p => Number(p.id) === projectId.value)
  if (!proj?.nodeIds) return []
  const ids = proj.nodeIds.split(',').map(s => s.trim())
  return allNodes.value.filter(n => ids.includes(n.id))
})

watch(nodeId, async (nid) => {
  if (!projectId.value || !nid) return
  const res = await listLogFiles(projectId.value, nid)
  logFiles.value = res.data || []
  if (logFiles.value.length) fileName.value = logFiles.value[0].name
})

function onProjectChange() {
  nodeId.value = undefined
  logFiles.value = []
  logLines.value = []
  aggEntries.value = []
  searchMatches.value = []
}

async function fetchLogView() {
  if (!projectId.value || !nodeId.value) return
  viewLoading.value = true
  try {
    const res = await viewLog(projectId.value, nodeId.value, fileName.value, viewOffset.value, pageSize)
    logLines.value = res.data?.lines || []
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
    aggEntries.value = res.data?.list || []
    aggPagination.value.total = res.data?.total || 0
  } finally {
    aggLoading.value = false
  }
}

function onAggPageChange(pag: { current: number }) {
  aggPagination.value.current = pag.current
  fetchAggregate()
}

async function doSearch() {
  if (!projectId.value || !searchKeyword.value) return
  searchLoading.value = true
  try {
    const res = await searchLogs({
      projectId: projectId.value,
      keyword: searchKeyword.value,
      scope: searchScope.value,
      nodeIds: aggNodeIds.value.length ? aggNodeIds.value : undefined,
      contextLines: contextLines.value
    })
    searchMatches.value = res.data?.matches || []
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
</style>
