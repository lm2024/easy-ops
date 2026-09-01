<template>
  <div>
    <a-card :bordered="false" style="border-radius: 8px">
      <template #title>
        <a-space>
          <folder-open-outlined style="color: #1890ff" />
          <span style="font-weight: 600">文件管理</span>
        </a-space>
      </template>
      <template #extra>
        <a-space>
          <a-select
            v-model:value="nodeId"
            style="width: 200px"
            placeholder="选择节点"
            @change="onNodeChange"
          >
            <a-select-option v-for="n in nodes" :key="n.id" :value="Number(n.id)">
              {{ n.name }} ({{ n.ip }})
            </a-select-option>
          </a-select>
          <a-button :loading="loading" @click="refresh">
            <reload-outlined /> 刷新
          </a-button>
          <a-upload :show-upload-list="false" :before-upload="handleUpload" :disabled="!nodeId">
            <a-button :disabled="!nodeId"><upload-outlined /> 上传</a-button>
          </a-upload>
          <a-badge :count="tasks.length" :offset="[-2, 0]">
            <a-button type="primary" @click="taskPanelOpen = true">
              <download-outlined /> 下载列表
            </a-button>
          </a-badge>
        </a-space>
      </template>

      <a-alert
        v-if="!nodeId"
        type="info"
        show-icon
        style="margin-bottom: 12px"
        message="请先选择节点。文件管理操作 Agent 节点磁盘（默认可访问根 /app/data）。"
      />

      <!-- 面包屑路径 -->
      <a-space v-if="nodeId" style="margin-bottom: 12px" wrap>
        <a-breadcrumb>
          <a-breadcrumb-item>
            <a @click="goRoot"><home-outlined /> 根目录</a>
          </a-breadcrumb-item>
          <a-breadcrumb-item v-for="(seg, i) in breadcrumbs" :key="i">
            <a v-if="i < breadcrumbs.length - 1" @click="goToCrumb(i)">{{ seg }}</a>
            <span v-else>{{ seg }}</span>
          </a-breadcrumb-item>
        </a-breadcrumb>
        <span class="current-path"><code>{{ currentPath || '/' }}</code></span>
        <a-button size="small" :disabled="!currentPath" @click="goParent"><arrow-up-outlined /> 上级</a-button>
        <a-button
          type="primary"
          size="small"
          :disabled="selectedPaths.length === 0"
          @click="packDownload"
        >
          <file-zip-outlined /> 打包下载（{{ selectedPaths.length }}）
        </a-button>
      </a-space>

      <a-table
        :columns="columns"
        :data-source="items"
        :loading="loading"
        :pagination="false"
        :row-selection="nodeId ? { selectedRowKeys: selectedKeys, onChange: onSelectChange } : undefined"
        :row-key="(r: FileItem) => r.path"
        size="small"
        @dblclick:row="onRowDblClick"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'name'">
            <a-space>
              <folder-outlined v-if="record.dir" style="color: #faad14" />
              <file-outlined v-else style="color: #52c41a" />
              <a @click="record.dir && enterDir(record)">{{ record.name }}</a>
            </a-space>
          </template>
          <template v-else-if="column.key === 'size'">
            <span v-if="!record.dir">{{ formatSize(record.size) }}</span>
            <span v-else class="muted">-</span>
          </template>
          <template v-else-if="column.key === 'mtime'">
            {{ formatTime(record.mtime) }}
          </template>
          <template v-else-if="column.key === 'action'">
            <a-space>
              <a-button
                v-if="!record.dir"
                size="small"
                type="link"
                :loading="downloadingPath === record.path"
                @click="downloadFile(record)"
              >下载</a-button>
              <a-button
                v-if="record.dir"
                size="small"
                type="link"
                @click="packDownload([record])"
              >打包</a-button>
            </a-space>
          </template>
        </template>
        <template #emptyText>
          <a-empty description="暂无文件（目录为空）" />
        </template>
      </a-table>
    </a-card>

    <!-- 下载任务面板 -->
    <a-drawer
      title="下载任务"
      :open="taskPanelOpen"
      :width="640"
      @close="taskPanelOpen = false"
    >
      <a-alert
        type="info"
        show-icon
        style="margin-bottom: 12px"
        message="大文件/文件夹自动压缩为 ZIP 分卷（每卷 ≤300MB，3 小时后自动删除）。用 7-Zip 打开 .z01 首卷或 .zip 末卷即可自动合并解压还原完整文件。"
      />
      <a-empty v-if="!tasks.length" description="暂无下载任务" />
      <a-list v-else :data-source="tasks" size="small">
        <template #renderItem="{ item }">
          <a-list-item style="flex-direction: column; align-items: stretch">
            <a-space style="justify-content: space-between; width: 100%">
              <a-space>
                <a-tag :color="statusColor(item.status)">{{ statusText(item.status) }}</a-tag>
                <span style="font-weight: 500">{{ item.name }}</span>
                <span class="muted">{{ formatTime(item.createTime) }}</span>
              </a-space>
              <a-space>
                <a-popconfirm
                  v-if="canCancel(item)"
                  title="确定停止该任务？已生成的分卷将被清理。"
                  @confirm="doCancel(item)"
                >
                  <a-button size="small" danger><stop-outlined /> 停止</a-button>
                </a-popconfirm>
                <a-popconfirm title="删除该任务？产物将立即清理。" @confirm="doDelete(item)">
                  <a-button size="small"><delete-outlined /> 删除</a-button>
                </a-popconfirm>
              </a-space>
            </a-space>

            <!-- 压缩中：进度条 -->
            <div v-if="item.status === 'COMPRESSING' || item.status === 'PENDING'">
              <a-progress
                v-if="item.status === 'COMPRESSING'"
                :percent="item.progressPct"
                size="small"
              />
              <div v-else class="muted" style="font-size: 12px">排队中（并发压缩=1）...</div>
            </div>

            <!-- 就绪：分卷列表 -->
            <div v-if="item.status === 'READY' || item.status === 'COMPLETED'" style="margin-top: 8px">
              <div class="muted" style="margin-bottom: 4px; font-size: 12px">
                已下载 {{ downloadedCount(item) }} / {{ item.parts.length }} 卷，共 {{ formatSize(totalPartSize(item)) }}
              </div>
              <a-space wrap>
                <template v-for="p in item.parts" :key="p.index">
                  <a-button
                    size="small"
                    :type="isPartDownloaded(item, p.index) ? 'primary' : 'default'"
                    :loading="downloadingPart === `${item.id}-${p.index}`"
                    @click="downloadPart(item, p)"
                  >
                    {{ p.name }}
                    <template v-if="isPartDownloaded(item, p.index)">✓</template>
                  </a-button>
                  <span
                    v-if="partProgress[`${item.id}-${p.index}`] != null"
                    class="muted"
                    style="font-size: 11px"
                  >{{ partProgress[`${item.id}-${p.index}`] }}%</span>
                </template>
              </a-space>
              <div class="muted" style="margin-top: 4px; font-size: 12px">
                提示：所有分卷下载到同一文件夹，用 7-Zip 打开 {{ item.name }}.z01（首卷）即可自动合并解压还原完整文件。
              </div>
            </div>

            <div
              v-if="item.status === 'CANCELLED' || item.status === 'FAILED'"
              class="muted"
              style="font-size: 12px"
            >{{ item.message || (item.status === 'CANCELLED' ? '已停止，产物已清理' : '压缩失败') }}</div>
          </a-list-item>
        </template>
      </a-list>
    </a-drawer>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { message } from 'ant-design-vue'
import type { NodeModel } from '../types'
import { getNodes } from '../api/node'
import {
  listRoots, listDir, createTask, listTasks, cancelTask, deleteTask,
  uploadFile, downloadDirect, downloadTaskPart, saveBlob
} from '../api/fileMgr'
import type { FileItem, DownloadTask } from '../api/fileMgr'
import {
  FolderOpenOutlined, FolderOutlined, FileOutlined, ReloadOutlined,
  UploadOutlined, DownloadOutlined, HomeOutlined, ArrowUpOutlined,
  FileZipOutlined, StopOutlined, DeleteOutlined
} from '@ant-design/icons-vue'

const MAX_DIRECT = 300 * 1024 * 1024 // 300MB：单文件小于该值直接下载，否则走 ZIP 分卷任务

const nodes = ref<NodeModel[]>([])
const nodeId = ref<number>()
const roots = ref<string[]>([])
const currentPath = ref('')
const items = ref<FileItem[]>([])
const loading = ref(false)
const selectedKeys = ref<(string | number)[]>([])
const selectedPaths = computed(() => selectedKeys.value as string[])
const downloadingPath = ref('')

const taskPanelOpen = ref(false)
const tasks = ref<DownloadTask[]>([])
const downloadingPart = ref('')
const partProgress = ref<Record<string, number>>({})
const downloadedMap = ref<Record<string, number[]>>({}) // taskId -> downloaded index list

const columns = [
  { title: '名称', dataIndex: 'name', key: 'name' },
  { title: '大小', dataIndex: 'size', key: 'size', width: 120 },
  { title: '修改时间', dataIndex: 'mtime', key: 'mtime', width: 180 },
  { title: '操作', key: 'action', width: 140 }
]

const breadcrumbs = computed(() => {
  const base = currentPath.value || ''
  if (!base) return []
  const segs = base.split('/').filter(Boolean)
  return segs
})

function goRoot() {
  currentPath.value = roots.value[0] || ''
  refresh()
}

function goToCrumb(idx: number) {
  const segs = breadcrumbs.value.slice(0, idx + 1)
  currentPath.value = '/' + segs.join('/')
  refresh()
}

function goParent() {
  if (!currentPath.value) return
  const p = currentPath.value.replace(/\/+$/, '')
  const idx = p.lastIndexOf('/')
  if (idx <= 0) {
    currentPath.value = roots.value[0] || ''
  } else {
    currentPath.value = p.substring(0, idx)
  }
  refresh()
}

function enterDir(item: FileItem) {
  currentPath.value = item.path
  refresh()
}

function onRowDblClick(_e: any, record: FileItem) {
  if (record.dir) enterDir(record)
}

function onSelectChange(keys: (string | number)[]) {
  selectedKeys.value = keys
}

async function onNodeChange() {
  selectedKeys.value = []
  items.value = []
  tasks.value = []
  if (!nodeId.value) return
  try {
    const r = await listRoots(nodeId.value)
    roots.value = r.data || []
    currentPath.value = roots.value[0] || ''
    refresh()
    refreshTasks()
  } catch {
    roots.value = []
  }
}

async function refresh() {
  if (!nodeId.value) return
  loading.value = true
  try {
    const r = await listDir(nodeId.value, currentPath.value || undefined)
    items.value = r.data?.items || []
    selectedKeys.value = []
  } catch (e: any) {
    items.value = []
    message.error(e?.response?.data?.message || '获取目录失败')
  } finally {
    loading.value = false
  }
}

// ==================== 下载 ====================

async function downloadFile(item: FileItem) {
  if (!nodeId.value) return
  // 大文件直接进入 ZIP 分卷任务
  if (item.size >= MAX_DIRECT) {
    message.info('文件较大（≥300MB），已创建 ZIP 分卷压缩任务，请到下载列表下载')
    await packDownload([item])
    return
  }
  downloadingPath.value = item.path
  try {
    const blob = await downloadDirect(nodeId.value, item.path)
    saveBlob(blob, item.name)
    message.success('下载完成')
  } catch (e: any) {
    message.error(e?.message || '下载失败')
  } finally {
    downloadingPath.value = ''
  }
}

async function packDownload(list?: FileItem[]) {
  if (!nodeId.value) return
  const targets = list || items.value.filter((it) => selectedPaths.value.includes(it.path))
  if (!targets.length) {
    message.warning('请先选择要打包的文件或目录')
    return
  }
  const paths = targets.map((t) => t.path)
  const baseName = targets.length === 1 ? targets[0].name : 'download'
  try {
    await createTask(nodeId.value, paths, baseName)
    message.success('已创建压缩下载任务')
    taskPanelOpen.value = true
    refreshTasks()
  } catch (e: any) {
    message.error(e?.response?.data?.message || '创建任务失败')
  }
}

// ==================== 任务 ====================

let pollTimer: number | null = null

async function refreshTasks() {
  if (!nodeId.value) return
  try {
    const r = await listTasks(nodeId.value)
    tasks.value = r.data || []
  } catch { /* 节点离线等静默 */ }
}

async function doCancel(item: DownloadTask) {
  if (!nodeId.value) return
  try {
    await cancelTask(nodeId.value, item.id)
    message.success('已停止')
    await refreshTasks()
  } catch (e: any) {
    message.error(e?.message || '取消失败')
  }
}

async function doDelete(item: DownloadTask) {
  if (!nodeId.value) return
  try {
    await deleteTask(nodeId.value, item.id)
    const key = `fm-dl-${nodeId.value}-${item.id}`
    localStorage.removeItem(key)
    message.success('已删除')
    await refreshTasks()
  } catch (e: any) {
    message.error(e?.message || '删除失败')
  }
}

async function downloadPart(item: DownloadTask, part: { index: number; name: string }) {
  if (!nodeId.value) return
  if (isPartDownloaded(item, part.index)) {
    message.info('该分卷已下载')
    return
  }
  const key = `${item.id}-${part.index}`
  downloadingPart.value = key
  partProgress.value[key] = 0
  try {
    const blob = await downloadTaskPart(nodeId.value, item.id, part.index, (p) => {
      partProgress.value[key] = p
    })
    saveBlob(blob, part.name)
    markPartDownloaded(item, part.index)
    message.success(`${part.name} 下载完成`)
  } catch (e: any) {
    message.error(e?.message || '下载失败')
  } finally {
    downloadingPart.value = ''
    delete partProgress.value[key]
  }
}

// 已下载分卷本地追踪（localStorage 持久化）
function markPartDownloaded(item: DownloadTask, index: number) {
  const key = `fm-dl-${nodeId.value}-${item.id}`
  const list = downloadedMap.value[key] || []
  if (!list.includes(index)) list.push(index)
  downloadedMap.value[key] = list
  localStorage.setItem(key, JSON.stringify(list))
}

function isPartDownloaded(item: DownloadTask, index: number): boolean {
  const key = `fm-dl-${nodeId.value}-${item.id}`
  const list = downloadedMap.value[key] || []
  return list.includes(index)
}

function downloadedCount(item: DownloadTask): number {
  const key = `fm-dl-${nodeId.value}-${item.id}`
  return (downloadedMap.value[key] || []).length
}

function totalPartSize(item: DownloadTask): number {
  return item.parts.reduce((s, p) => s + p.size, 0)
}

function canCancel(item: DownloadTask) {
  return item.status === 'COMPRESSING' || item.status === 'PENDING'
}

function statusText(s: string) {
  return ({ PENDING: '排队中', COMPRESSING: '压缩中', READY: '待下载', COMPLETED: '完成', CANCELLED: '已停止', FAILED: '失败' } as any)[s] || s
}

function statusColor(s: string) {
  return ({ PENDING: 'default', COMPRESSING: 'blue', READY: 'green', COMPLETED: 'green', CANCELLED: 'red', FAILED: 'red' } as any)[s] || 'default'
}

// ==================== 上传 ====================

async function handleUpload(file: File) {
  if (!nodeId.value) return
  if (!currentPath.value) {
    message.warning('请先进入目录')
    return false
  }
  try {
    await uploadFile(nodeId.value, currentPath.value, file)
    message.success(`已上传 ${file.name}`)
    refresh()
  } catch (e: any) {
    message.error(e?.response?.data?.message || '上传失败')
  }
  return false // 阻止 antd 默认上传
}

// ==================== 工具 ====================

function formatSize(n: number) {
  if (n == null || n < 0) return '-'
  if (n < 1024) return n + ' B'
  if (n < 1024 * 1024) return (n / 1024).toFixed(1) + ' KB'
  if (n < 1024 * 1024 * 1024) return (n / 1024 / 1024).toFixed(1) + ' MB'
  return (n / 1024 / 1024 / 1024).toFixed(2) + ' GB'
}

function formatTime(t: number) {
  if (!t) return '-'
  const d = new Date(t)
  const pad = (x: number) => String(x).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

onMounted(async () => {
  // 恢复已下载分卷记录
  for (let i = 0; i < localStorage.length; i++) {
    const k = localStorage.key(i)
    if (k && k.startsWith('fm-dl-')) {
      try {
        downloadedMap.value[k] = JSON.parse(localStorage.getItem(k) || '[]')
      } catch { /* ignore */ }
    }
  }
  const r = await getNodes(1, 200)
  nodes.value = r.data.list || []
  pollTimer = window.setInterval(() => {
    if (taskPanelOpen.value && nodeId.value) refreshTasks()
  }, 3000)
})

onUnmounted(() => {
  if (pollTimer) window.clearInterval(pollTimer)
})
</script>

<style scoped>
.current-path {
  font-size: 12px;
  color: #52525b;
}
.current-path code {
  font-size: 11px;
}
.muted {
  color: #8c8c8c;
}
</style>
