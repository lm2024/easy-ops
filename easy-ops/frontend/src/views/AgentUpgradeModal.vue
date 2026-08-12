<template>
  <a-modal
    v-model:open="visible"
    title="🤖 Agent 升级管理"
    width="860px"
    :footer="null"
    :body-style="{ maxHeight: '65vh', overflowY: 'auto', padding: '16px 24px' }"
    @cancel="handleClose"
  >
    <a-tabs v-model:activeKey="activeTab" size="small">
      <!-- Tab 1: 上传升级包 -->
      <a-tab-pane key="upload" tab="📦 上传升级包">
        <a-upload
          :file-list="fileList"
          :before-upload="beforeUpload"
          accept=".jar"
          :max-count="1"
        >
          <a-button><upload-outlined /> 选择 Agent JAR 包</a-button>
          <span style="margin-left: 8px; color: #999; font-size: 12px">版本号自动使用文件大小</span>
        </a-upload>
        <a-button type="primary" style="margin-top: 8px" :loading="uploading" :disabled="fileList.length === 0" @click="handleUpload" block>
          <upload-outlined /> 上传
        </a-button>

        <a-divider style="margin: 12px 0">已上传的升级包</a-divider>
        <a-table
          :columns="packageColumns"
          :data-source="packages"
          :loading="packagesLoading"
          size="small"
          row-key="version"
          :pagination="{ pageSize: 5, size: 'small' }"
          :scroll="{ y: 200 }"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'size'">
              {{ formatSize(record.size) }}
            </template>
            <template v-if="column.key === 'lastModified'">
              {{ formatDate(record.lastModified) }}
            </template>
            <template v-if="column.key === 'action'">
              <a-popconfirm title="确定删除此升级包?" @confirm="handleDeletePackage(record.version)">
                <a-button type="link" size="small" danger>删除</a-button>
              </a-popconfirm>
            </template>
          </template>
        </a-table>
      </a-tab-pane>

      <!-- Tab 2: 执行升级 -->
      <a-tab-pane key="upgrade" tab="🚀 执行升级">
        <a-space direction="vertical" style="width: 100%" :size="12">
          <a-select v-model:value="selectedVersion" placeholder="选择目标版本" style="width: 100%" size="small">
            <a-select-option v-for="pkg in packages" :key="pkg.version" :value="pkg.version">
              {{ pkg.version }} ({{ formatSize(pkg.size) }})
            </a-select-option>
          </a-select>

          <a-space>
            <a-button size="small" @click="selectAllOnline">全选在线</a-button>
            <a-button size="small" @click="clearSelection">清除</a-button>
            <span style="color: #8c8c8c; font-size: 12px">已选 {{ selectedNodeIds.length }} 个节点</span>
          </a-space>

          <a-table
            :columns="nodeColumns"
            :data-source="nodes"
            :loading="nodesLoading"
            size="small"
            row-key="id"
            :row-selection="{ selectedRowKeys: selectedNodeIds, onChange: onSelectionChange }"
            :pagination="{ pageSize: 8, size: 'small' }"
            :scroll="{ y: 280 }"
          >
            <template #bodyCell="{ column, record }">
              <template v-if="column.key === 'status'">
                <a-badge :status="record.status === 1 ? 'success' : 'error'"
                         :text="record.status === 1 ? '在线' : '离线'" />
              </template>
              <template v-if="column.key === 'agentVersion'">
                <a-tag :color="record.agentVersion === selectedVersion ? 'green' : 'orange'" style="font-size: 11px">
                  {{ record.agentVersion || '未知' }}
                </a-tag>
              </template>
            </template>
          </a-table>

          <a-button type="primary" :loading="upgrading"
                    :disabled="selectedNodeIds.length === 0 || !selectedVersion"
                    @click="handleUpgrade" block>
            🚀 开始升级 ({{ selectedNodeIds.length }} 个节点)
          </a-button>
        </a-space>
      </a-tab-pane>

      <!-- Tab 3: 升级状态 -->
      <a-tab-pane key="status" tab="📊 升级状态">
        <template v-if="currentBatchId">
          <a-descriptions bordered size="small" :column="2" style="margin-bottom: 12px">
            <a-descriptions-item label="批次">{{ upgradeStatus?.batchId }}</a-descriptions-item>
            <a-descriptions-item label="版本">{{ upgradeStatus?.targetVersion }}</a-descriptions-item>
            <a-descriptions-item label="状态">
              <a-tag :color="upgradeStatus?.status === 'COMPLETED' ? 'green' : upgradeStatus?.status === 'PARTIAL_FAILED' ? 'red' : 'blue'">
                {{ upgradeStatus?.status === 'COMPLETED' ? '已完成' : upgradeStatus?.status === 'PARTIAL_FAILED' ? '部分失败' : '进行中' }}
              </a-tag>
            </a-descriptions-item>
            <a-descriptions-item label="进度">
              {{ upgradeStatus?.completed }}/{{ upgradeStatus?.total }}
              <a-progress :percent="upgradeStatus?.total ? Math.round((upgradeStatus?.completed / upgradeStatus?.total) * 100) : 0" size="small" style="margin-top: 4px" />
            </a-descriptions-item>
          </a-descriptions>

          <a-table
            :columns="statusColumns"
            :data-source="upgradeStatus?.details || []"
            size="small"
            row-key="id"
            :pagination="{ pageSize: 8, size: 'small' }"
            :scroll="{ y: 280 }"
          >
            <template #bodyCell="{ column, record }">
              <template v-if="column.key === 'status'">
                <a-tag :color="getStatusColor(record.status)" style="font-size: 11px">
                  {{ getStatusText(record.status) }}
                </a-tag>
              </template>
              <template v-if="column.key === 'duration'">
                <span style="font-size: 12px">
                  {{ record.endTime && record.startTime ? ((record.endTime - record.startTime) / 1000).toFixed(1) + 's' : '-' }}
                </span>
              </template>
            </template>
          </a-table>
        </template>
        <a-empty v-else description="暂无升级记录，请先执行升级" />
      </a-tab-pane>
    </a-tabs>
  </a-modal>
</template>

<script setup lang="ts">
import { ref, watch, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import { UploadOutlined } from '@ant-design/icons-vue'
import {
  uploadAgentPackage,
  getAgentPackages,
  deleteAgentPackage,
  getAgentUpgradeNodes,
  upgradeAgentNodes,
  getAgentUpgradeStatus
} from '../api/agent-upgrade'
import type { AgentPackage, AgentNode, AgentUpgradeStatus } from '../api/agent-upgrade'

const props = defineProps<{
  open: boolean
}>()

const emit = defineEmits<{
  (e: 'update:open', value: boolean): void
  (e: 'upgraded'): void
}>()

const visible = ref(props.open)
watch(() => props.open, (val) => { visible.value = val })
watch(visible, (val) => { emit('update:open', val) })

const activeTab = ref('upload')
const fileList = ref<any[]>([])
const uploadVersion = ref('')
const uploading = ref(false)
const packagesLoading = ref(false)
const packages = ref<AgentPackage[]>([])

const selectedVersion = ref('')
const nodesLoading = ref(false)
const nodes = ref<AgentNode[]>([])
const selectedNodeIds = ref<number[]>([])
const upgrading = ref(false)

const currentBatchId = ref('')
const upgradeStatus = ref<AgentUpgradeStatus | null>(null)
let statusTimer: ReturnType<typeof setInterval> | null = null

const packageColumns = [
  { title: '版本', dataIndex: 'version', key: 'version', sorter: (a: any, b: any) => (a.version || '').localeCompare(b.version || '') },
  { title: '大小', dataIndex: 'size', key: 'size', width: 100, sorter: (a: any, b: any) => (a.size || 0) - (b.size || 0) },
  { title: '上传时间', dataIndex: 'lastModified', key: 'lastModified', width: 180, sorter: (a: any, b: any) => (a.lastModified || 0) - (b.lastModified || 0) },
  { title: '操作', key: 'action', width: 80 }
]

const nodeColumns = [
  { title: '节点名称', dataIndex: 'name', key: 'name', sorter: (a: any, b: any) => (a.name || '').localeCompare(b.name || '') },
  { title: 'IP', dataIndex: 'ip', key: 'ip', width: 120, sorter: (a: any, b: any) => (a.ip || '').localeCompare(b.ip || '') },
  { title: '状态', dataIndex: 'status', key: 'status', width: 70, sorter: (a: any, b: any) => (a.status || '').localeCompare(b.status || '') },
  { title: '当前版本', dataIndex: 'agentVersion', key: 'agentVersion', width: 110 }
]

const statusColumns = [
  { title: '节点', dataIndex: 'nodeName', key: 'nodeName' },
  { title: '旧版本', dataIndex: 'oldVersion', key: 'oldVersion', width: 110 },
  { title: '状态', dataIndex: 'status', key: 'status', width: 80 },
  { title: '耗时', key: 'duration', width: 70 },
  { title: '错误信息', dataIndex: 'errorMessage', key: 'errorMessage', ellipsis: true }
]

watch(visible, (val) => {
  if (val) {
    loadPackages()
    loadNodes()
  } else {
    stopStatusTimer()
  }
})

onMounted(() => {
  if (visible.value) {
    loadPackages()
    loadNodes()
  }
})

async function loadPackages() {
  packagesLoading.value = true
  try {
    const res = await getAgentPackages()
    packages.value = res.data || []
  } catch {
    // ignore
  }
  packagesLoading.value = false
}

async function handleDeletePackage(version: string) {
  try {
    await deleteAgentPackage(version)
    message.success('✅ 已删除: ' + version)
    await loadPackages()
  } catch (e: any) {
    message.error('删除失败: ' + (e?.message || '未知错误'))
  }
}

async function loadNodes() {
  nodesLoading.value = true
  try {
    const res = await getAgentUpgradeNodes()
    nodes.value = res.data || []
  } catch {
    // ignore
  }
  nodesLoading.value = false
}

function beforeUpload(file: any) {
  fileList.value = [file]
  return false
}

async function handleUpload() {
  if (fileList.value.length === 0) return
  uploading.value = true
  try {
    await uploadAgentPackage(fileList.value[0], uploadVersion.value || undefined)
    message.success('✅ 升级包上传成功')
    fileList.value = []
    uploadVersion.value = ''
    await loadPackages()
  } catch (e: any) {
    message.error('上传失败: ' + (e?.message || '未知错误'))
  }
  uploading.value = false
}

function onSelectionChange(keys: number[]) {
  selectedNodeIds.value = keys
}

function selectAllOnline() {
  selectedNodeIds.value = nodes.value.filter(n => n.status === 1).map(n => n.id)
}

function clearSelection() {
  selectedNodeIds.value = []
}

async function handleUpgrade() {
  if (!selectedVersion.value || selectedNodeIds.value.length === 0) {
    message.warning('请选择版本和节点')
    return
  }
  upgrading.value = true
  try {
    const res = await upgradeAgentNodes(selectedVersion.value, selectedNodeIds.value)
    currentBatchId.value = res.data.batchId
    activeTab.value = 'status'
    message.success('🚀 升级已启动')
    startStatusTimer()
    emit('upgraded')
  } catch (e: any) {
    message.error('升级失败: ' + (e?.message || '未知错误'))
  }
  upgrading.value = false
}

function startStatusTimer() {
  stopStatusTimer()
  fetchStatus()
  statusTimer = setInterval(fetchStatus, 3000)
}

function stopStatusTimer() {
  if (statusTimer) {
    clearInterval(statusTimer)
    statusTimer = null
  }
}

async function fetchStatus() {
  if (!currentBatchId.value) return
  try {
    const res = await getAgentUpgradeStatus(currentBatchId.value)
    upgradeStatus.value = res.data
    if (res.data.status === 'COMPLETED' || res.data.status === 'PARTIAL_FAILED') {
      stopStatusTimer()
      if (res.data.status === 'COMPLETED') {
        message.success('✅ 所有节点升级完成')
      } else {
        message.warning('⚠️ 部分节点升级失败，请查看详情')
      }
    }
  } catch {
    // ignore
  }
}

function handleClose() {
  stopStatusTimer()
  activeTab.value = 'upload'
  selectedNodeIds.value = []
  currentBatchId.value = ''
  upgradeStatus.value = null
}

function formatSize(bytes?: number): string {
  if (!bytes) return '-'
  if (bytes >= 1048576) return (bytes / 1048576).toFixed(1) + ' MB'
  if (bytes >= 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return bytes + ' B'
}

function formatDate(ts?: number): string {
  if (!ts) return '-'
  return new Date(ts).toLocaleString()
}

function getStatusColor(status: number): string {
  switch (status) {
    case 0: return 'default'
    case 1: return 'processing'
    case 2: return 'success'
    case 3: return 'error'
    case 4: return 'warning'
    default: return 'default'
  }
}

function getStatusText(status: number): string {
  switch (status) {
    case 0: return '待升级'
    case 1: return '升级中'
    case 2: return '成功'
    case 3: return '失败'
    case 4: return '已回滚'
    default: return '未知'
  }
}
</script>
