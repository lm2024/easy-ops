<template>
  <div>
    <a-card :bordered="false" style="border-radius: 8px">
      <template #title>
        <a-space>
          <dashboard-outlined style="color: #52c41a" />
          <span style="font-weight: 600">应用监控</span>
          <a-tag color="blue">监控应用管理中的全部应用</a-tag>
          <a-tag v-if="autoCollectEnabled" color="green">
            自动采集中 · 每 {{ collectIntervalSec }} 秒
          </a-tag>
        </a-space>
      </template>
      <template #extra>
        <a-space>
          <span style="color: #8c8c8c; font-size: 13px">采集频率</span>
          <a-select
            v-model:value="collectIntervalSec"
            style="width: 120px"
            @change="onIntervalChange"
          >
            <a-select-option :value="1">1 秒</a-select-option>
            <a-select-option :value="3">3 秒</a-select-option>
            <a-select-option :value="5">5 秒</a-select-option>
            <a-select-option :value="60">1 分钟</a-select-option>
          </a-select>
          <a-button :loading="refreshing" @click="handleManualRefresh">
            <reload-outlined /> 立即采集
          </a-button>
          <a-select
            v-model:value="filterProjectId"
            allow-clear
            style="width: 200px"
            placeholder="筛选应用"
          >
            <a-select-option v-for="p in dashboard?.projects || []" :key="p.projectId" :value="p.projectId">
              {{ p.projectName }}
            </a-select-option>
          </a-select>
        </a-space>
      </template>

      <a-row v-if="dashboard" :gutter="16" style="margin-bottom: 16px">
        <a-col :span="4">
          <a-statistic title="应用数" :value="dashboard.summary.totalProjects" />
        </a-col>
        <a-col :span="4">
          <a-statistic title="部署实例" :value="dashboard.summary.totalInstances" />
        </a-col>
        <a-col :span="4">
          <a-statistic title="健康" :value="dashboard.summary.upCount" value-style="color: #52c41a" />
        </a-col>
        <a-col :span="4">
          <a-statistic title="异常" :value="dashboard.summary.downCount" value-style="color: #ff4d4f" />
        </a-col>
        <a-col :span="4">
          <a-statistic title="降级" :value="dashboard.summary.degradedCount" value-style="color: #faad14" />
        </a-col>
        <a-col :span="4">
          <a-statistic title="最后采集" :value="lastCollectLabel" />
        </a-col>
      </a-row>

      <a-table
        :columns="columns"
        :data-source="tableRows"
        :loading="loading"
        row-key="rowKey"
        :pagination="{ pageSize: 20 }"
        size="middle"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'healthStatus'">
            <a-badge :status="badgeStatus(record.healthStatus)" :text="healthLabel(record.healthStatus)" />
          </template>
          <template v-if="column.key === 'processStatus'">
            <a-tag :color="record.processStatus === 'RUNNING' ? 'green' : record.processStatus === 'STOPPED' ? 'red' : 'default'">
              {{ processLabel(record.processStatus) }}
            </a-tag>
          </template>
          <template v-if="column.key === 'cpuPercent'">
            {{ formatCpu(record) }}
          </template>
          <template v-if="column.key === 'memoryMb'">
            {{ formatMemory(record) }}
          </template>
          <template v-if="column.key === 'checkMethods'">
            {{ formatCheckMethods(record) }}
          </template>
          <template v-if="column.key === 'healthDetail'">
            <span :class="{ 'error-text': record.healthStatus === 'DOWN' }">
              {{ record.healthDetail || record.lastError || '-' }}
            </span>
          </template>
          <template v-if="column.key === 'action'">
            <a-button type="link" size="small" @click="openProbe(record.projectId)">探针配置</a-button>
          </template>
        </template>
      </a-table>
    </a-card>

    <a-modal v-model:open="probeModalVisible" title="HTTP 健康探针配置" @ok="saveProbe" :confirm-loading="probeSaving">
      <a-form layout="vertical">
        <a-form-item label="启用">
          <a-switch :checked="probe.enabled === 1" @change="(v: boolean) => probe.enabled = v ? 1 : 0" />
        </a-form-item>
        <a-form-item label="请求方法">
          <a-select v-model:value="probe.method">
            <a-select-option value="GET">GET</a-select-option>
            <a-select-option value="POST">POST</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="URL">
          <a-input v-model:value="probe.url" placeholder="http://127.0.0.1:8080/health" />
        </a-form-item>
        <a-form-item label="期望状态码">
          <a-input-number v-model:value="probe.expectedStatus" :min="100" :max="599" style="width: 100%" />
        </a-form-item>
        <a-form-item label="响应包含">
          <a-input v-model:value="probe.bodyContains" placeholder="可选" />
        </a-form-item>
        <a-form-item label="超时(ms)">
          <a-input-number v-model:value="probe.timeoutMs" :min="500" :max="30000" style="width: 100%" />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, onUnmounted } from 'vue'
import { message } from 'ant-design-vue'
import type { AppMonitorDashboard, AppMonitorNodeInfo, ProjectHealthProbeModel } from '../types'
import {
  getAppDashboard, collectAppMonitor, getHealthProbe, saveHealthProbe,
  getMonitorCollectConfig, saveMonitorCollectConfig
} from '../api/monitorApp'
import { DashboardOutlined, ReloadOutlined } from '@ant-design/icons-vue'
import dayjs from 'dayjs'

interface MonitorTableRow extends AppMonitorNodeInfo {
  rowKey: string
  projectId: number
  projectName: string
  jarName?: string
}

const dashboard = ref<AppMonitorDashboard | null>(null)
const loading = ref(false)
const refreshing = ref(false)
const filterProjectId = ref<number>()
const lastCollectTime = ref<number>()
const collectIntervalSec = ref(60)
const autoCollectEnabled = ref(true)
const probeModalVisible = ref(false)
const probeSaving = ref(false)
const probeProjectId = ref<number>()
let autoTimer: ReturnType<typeof setInterval> | null = null
let collecting = false

const probe = reactive<ProjectHealthProbeModel>({
  projectId: 0, enabled: 0, method: 'GET', url: '', expectedStatus: 200, timeoutMs: 5000
})

const columns = [
  { title: '应用', dataIndex: 'projectName', key: 'projectName', width: 140 },
  { title: '节点', dataIndex: 'nodeName', key: 'nodeName', width: 120 },
  { title: 'Jar包', dataIndex: 'jarName', key: 'jarName', width: 160, ellipsis: true },
  { title: '进程', key: 'processStatus', width: 90 },
  { title: '健康', key: 'healthStatus', width: 90 },
  { title: '检测方式', key: 'checkMethods', width: 130 },
  { title: '健康说明', key: 'healthDetail', ellipsis: true },
  { title: 'CPU', key: 'cpuPercent', width: 80 },
  { title: '内存', key: 'memoryMb', width: 90 },
  { title: '响应', dataIndex: 'responseMs', key: 'responseMs', width: 70, customRender: ({ text }: { text?: number }) => text != null ? text + 'ms' : '-' },
  { title: '采集时间', dataIndex: 'collectTime', key: 'collectTime', width: 150, customRender: ({ text }: { text?: number }) => text ? dayjs(text).format('YYYY-MM-DD HH:mm:ss') : '-' },
  { title: '操作', key: 'action', width: 100 },
]

const tableRows = computed(() => {
  const rows: MonitorTableRow[] = []
  for (const project of dashboard.value?.projects || []) {
    if (filterProjectId.value && project.projectId !== filterProjectId.value) continue
    for (const node of project.nodes || []) {
      rows.push({
        ...node,
        rowKey: project.projectId + '-' + node.nodeId,
        projectId: project.projectId,
        projectName: project.projectName,
        jarName: project.jarName,
      })
    }
  }
  return rows
})

const lastCollectLabel = computed(() => {
  if (!lastCollectTime.value) return '-'
  return dayjs(lastCollectTime.value).format('HH:mm:ss')
})

function formatCpu(record: AppMonitorNodeInfo) {
  if (record.cpuPercent != null) return Number(record.cpuPercent).toFixed(1) + '%'
  return '-'
}

function formatMemory(record: AppMonitorNodeInfo) {
  if (record.memoryMb != null) return record.memoryMb + 'MB'
  return '-'
}

function badgeStatus(status: string): 'success' | 'error' | 'warning' | 'default' {
  return ({ UP: 'success', DOWN: 'error', DEGRADED: 'warning' } as Record<string, 'success' | 'error' | 'warning'>)[status] || 'default'
}

function healthLabel(status: string) {
  return ({ UP: '健康', DOWN: '异常', DEGRADED: '降级', UNKNOWN: '未采集' } as Record<string, string>)[status] || status
}

function processLabel(status?: string) {
  return ({ RUNNING: '运行中', STOPPED: '已停止', UNKNOWN: '未知' } as Record<string, string>)[status || ''] || '-'
}

function formatCheckMethods(node: { extraJson?: string }) {
  if (!node.extraJson) return 'Shell(ps)'
  try {
    const extra = JSON.parse(node.extraJson)
    const methods = extra.checkMethods as string[] | undefined
    if (!methods?.length) return 'Shell(ps)'
    return methods.map(m => m === 'PS_GREP' ? 'Shell(ps)' : 'HTTP探针').join(' + ')
  } catch {
    return 'Shell(ps)'
  }
}

async function fetchDashboard() {
  loading.value = true
  try {
    const res = await getAppDashboard()
    dashboard.value = res.data
    if (res.data?.collectIntervalSec) {
      collectIntervalSec.value = res.data.collectIntervalSec
    }
  } finally {
    loading.value = false
  }
}

async function runCollectCycle(showToast = false) {
  if (collecting) return
  collecting = true
  refreshing.value = true
  try {
    await collectAppMonitor()
    lastCollectTime.value = Date.now()
    await fetchDashboard()
    if (showToast) message.success('监控数据已更新')
  } finally {
    collecting = false
    refreshing.value = false
  }
}

async function handleManualRefresh() {
  await runCollectCycle(true)
}

function stopAutoCollect() {
  if (autoTimer) {
    clearInterval(autoTimer)
    autoTimer = null
  }
}

function startAutoCollect() {
  stopAutoCollect()
  const ms = collectIntervalSec.value * 1000
  autoTimer = setInterval(() => {
    runCollectCycle(false)
  }, ms)
  autoCollectEnabled.value = true
}

async function onIntervalChange(sec: number) {
  try {
    const res = await saveMonitorCollectConfig(sec)
    collectIntervalSec.value = res.data?.collectIntervalSec ?? sec
    startAutoCollect()
    message.success(`已切换为每 ${collectIntervalSec.value} 秒自动采集`)
  } catch {
    message.error('保存采集频率失败')
  }
}

async function openProbe(projectId: number) {
  probeProjectId.value = projectId
  const res = await getHealthProbe(projectId)
  if (res.data) Object.assign(probe, res.data)
  probe.projectId = projectId
  probeModalVisible.value = true
}

async function saveProbe() {
  probeSaving.value = true
  try {
    probe.projectId = probeProjectId.value!
    await saveHealthProbe(probe)
    probeModalVisible.value = false
    message.success('探针配置已保存')
    await runCollectCycle(false)
  } finally {
    probeSaving.value = false
  }
}

onMounted(async () => {
  try {
    const cfg = await getMonitorCollectConfig()
    if (cfg.data?.collectIntervalSec) {
      collectIntervalSec.value = cfg.data.collectIntervalSec
    }
  } catch {
    // 使用默认 60 秒
  }
  await runCollectCycle(false)
  startAutoCollect()
})

onUnmounted(() => {
  stopAutoCollect()
})
</script>

<style scoped>
.error-text { color: #ff4d4f; font-size: 12px; }
</style>
