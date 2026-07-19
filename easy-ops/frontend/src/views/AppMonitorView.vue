<template>
  <div>
    <a-card :bordered="false" style="border-radius: 8px">
      <template #title>
        <a-space>
          <dashboard-outlined style="color: #52c41a" />
          <span style="font-weight: 600">监控中心</span>
          <a-tag v-if="activeTab === 'app' && autoCollectEnabled" color="green">
            自动采集中 · 每 {{ collectIntervalSec }} 秒
          </a-tag>
        </a-space>
      </template>
      <template #extra>
        <a-space v-if="activeTab === 'app'">
          <span style="color: #8c8c8c; font-size: 13px">采集频率</span>
          <a-select v-model:value="collectIntervalSec" style="width: 120px" @change="onIntervalChange">
            <a-select-option :value="1">1 秒</a-select-option>
            <a-select-option :value="3">3 秒</a-select-option>
            <a-select-option :value="5">5 秒</a-select-option>
            <a-select-option :value="60">1 分钟</a-select-option>
          </a-select>
          <a-button :loading="refreshing" @click="handleManualRefresh">
            <reload-outlined /> 立即采集
          </a-button>
        </a-space>
        <a-space v-if="activeTab === 'agent'">
          <a-button :loading="agentLoading" @click="fetchAgentStatus">
            <reload-outlined /> 刷新
          </a-button>
        </a-space>
      </template>

      <a-tabs v-model:activeKey="activeTab" type="card" style="margin-bottom: 0">
        <a-tab-pane key="agent" tab="Agent 状态" />
        <a-tab-pane key="app" tab="应用监控" />
      </a-tabs>

      <!-- ===== Agent 状态 ===== -->
      <div v-if="activeTab === 'agent'" style="margin-top: 16px">
        <a-row :gutter="16" style="margin-bottom: 16px">
          <a-col :span="6"><a-statistic title="Agent 总数" :value="agentPagination.total" /></a-col>
          <a-col :span="6"><a-statistic title="在线" :value="agentOnlineCount" value-style="color: #52c41a" /></a-col>
          <a-col :span="6"><a-statistic title="离线" :value="agentOfflineCount" value-style="color: #ff4d4f" /></a-col>
          <a-col :span="6"><a-statistic title="最后刷新" :value="agentLastRefreshLabel" /></a-col>
        </a-row>
        <a-table
          :columns="agentColumns"
          :data-source="agentList"
          :loading="agentLoading"
          row-key="nodeId"
          :pagination="agentPagination"
          size="middle"
          :scroll="{ x: 1000 }"
          @change="handleAgentTableChange"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'nodeName'">
              <span style="font-weight:500">{{ record.nodeName }}</span>
            </template>
            <template v-if="column.key === 'status'">
              <a-tag :color="record.status === 1 ? 'green' : 'red'">
                {{ record.status === 1 ? '在线' : '离线' }}
              </a-tag>
            </template>
            <template v-if="column.key === 'hostCpuPercent'">
              <span :style="{ color: (record.hostCpuPercent || 0) > 80 ? '#ff4d4f' : '#333', fontWeight: 600 }">
                {{ formatPercent(record.hostCpuPercent) }}
              </span>
            </template>
            <template v-if="column.key === 'hostMemoryPercent'">
              <span :style="{ color: (record.hostMemoryPercent || 0) > 80 ? '#ff4d4f' : (record.hostMemoryPercent || 0) > 60 ? '#faad14' : '#333', fontWeight: 600 }">
                {{ record.hostMemoryPercent != null ? record.hostMemoryPercent + '%' : '-' }}
              </span>
            </template>
            <template v-if="column.key === 'diskUsagePercent'">
              <span :style="{ color: (record.diskUsagePercent || 0) > 90 ? '#ff4d4f' : '#333' }">
                {{ record.diskUsagePercent != null ? record.diskUsagePercent + '%' : '-' }}
              </span>
            </template>
            <template v-if="column.key === 'totalMemoryMb'">
              <span v-if="record.totalMemoryMb">{{ (record.totalMemoryMb / 1024).toFixed(1) }} GB</span>
              <span v-else>-</span>
            </template>
          </template>
        </a-table>
      </div>

      <!-- ===== 应用监控 ===== -->
      <div v-if="activeTab === 'app'">
      <!-- 统计 -->
      <a-row v-if="dashboard" :gutter="16" style="margin-bottom: 16px; margin-top: 16px">
        <a-col :span="4"><a-statistic title="应用数" :value="dashboard.summary.totalProjects" /></a-col>
        <a-col :span="4"><a-statistic title="部署实例" :value="dashboard.summary.totalInstances" /></a-col>
        <a-col :span="4"><a-statistic title="健康" :value="dashboard.summary.upCount" value-style="color: #52c41a" /></a-col>
        <a-col :span="4"><a-statistic title="异常" :value="dashboard.summary.downCount" value-style="color: #ff4d4f" /></a-col>
        <a-col :span="4"><a-statistic title="降级" :value="dashboard.summary.degradedCount" value-style="color: #faad14" /></a-col>
        <a-col :span="4"><a-statistic title="最后采集" :value="lastCollectLabel" /></a-col>
      </a-row>

      <!-- 选择性采集 -->
      <a-space style="margin-bottom: 8px">
        <a-button size="small" :loading="filteredCollecting" @click="collectSelected" :disabled="selectedRowKeys.length === 0">
          <reload-outlined /> 采集选中 ({{ selectedRowKeys.length }})
        </a-button>
        <a-button size="small" :loading="filteredCollecting" @click="collectCurrentPage">
          <reload-outlined /> 采集当前页
        </a-button>
        <a-button size="small" :loading="filteredCollecting" @click="collectFilteredProject">
          <reload-outlined /> 采集筛选应用
        </a-button>
      </a-space>
      <!-- 批量操作 -->
      <a-space style="margin-bottom: 12px">
        <a-button size="small" :disabled="selectedRowKeys.length === 0" @click="batchOperate('start')" style="color: #52c41a">
          <play-circle-outlined /> 批量启动 ({{ selectedRowKeys.length }})
        </a-button>
        <a-button size="small" danger :disabled="selectedRowKeys.length === 0" @click="batchOperate('stop')">
          <pause-circle-outlined /> 批量停止 ({{ selectedRowKeys.length }})
        </a-button>
        <a-button size="small" :disabled="selectedRowKeys.length === 0" @click="batchOperate('restart')" style="color: #faad14">
          <reload-outlined /> 批量重启 ({{ selectedRowKeys.length }})
        </a-button>
        <a-select v-model:value="filterProjectId" allow-clear style="width: 200px" placeholder="筛选应用">
          <a-select-option v-for="p in dashboard?.projects || []" :key="p.projectId" :value="p.projectId">
            {{ p.projectName }}
          </a-select-option>
        </a-select>
      </a-space>

      <!-- 主表格 -->
      <a-table
        :columns="columns"
        :data-source="tableRows"
        :loading="loading"
        row-key="rowKey"
        :pagination="pagination"
        :row-selection="{ selectedRowKeys, onChange: (keys: any) => selectedRowKeys = keys }"
        size="middle"
        :scroll="{ x: 1400 }"
        @change="handleTableChange"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'projectName'">
            <router-link :to="`/projects/${record.projectId}`" style="font-weight:500">
              {{ record.projectName }}
            </router-link>
          </template>
          <template v-if="column.key === 'nodeName'">
            <div>{{ record.nodeName }}</div>
            <div style="font-size:11px;color:#888">{{ record.nodeIp || '' }}</div>
          </template>
          <template v-if="column.key === 'processStatus'">
            <a-tag :color="record.processStatus === 'RUNNING' ? 'green' : record.processStatus === 'STOPPED' ? 'red' : 'default'" size="small">
              {{ processLabel(record.processStatus) }}
            </a-tag>
          </template>
          <template v-if="column.key === 'healthStatus'">
            <div>
              <a-badge :status="badgeStatus(record.healthStatus)" :text="healthLabel(record.healthStatus)" />
            </div>
            <div v-if="record.healthStatus !== 'UP' && record.healthDetail" style="font-size:11px;color:#ff4d4f;margin-top:2px;max-width:220px;line-height:1.3">
              {{ record.healthDetail }}
            </div>
            <div v-else-if="record.healthDetail && !record.healthDetail.includes('全部通过')" style="font-size:11px;color:#888;margin-top:2px;max-width:220px;line-height:1.3">
              {{ record.healthDetail }}
            </div>
          </template>
          <template v-if="column.key === 'cpu'">
            <div>总: <b :style="{ color: (record.hostCpuPercent || 0) > 80 ? '#ff4d4f' : '#333' }">{{ formatPercent(record.hostCpuPercent) }}</b></div>
            <div style="font-size:11px;color:#888">进程: {{ formatPercent(record.cpuPercent) }}</div>
          </template>
          <template v-if="column.key === 'memory'">
            <div>总: <b>{{ record.hostMemoryPercent != null ? record.hostMemoryPercent + '%' : '-' }}</b></div>
            <div style="font-size:11px;color:#888">堆: {{ record.heapUsedMb || 0 }}/{{ record.heapMaxMb || 0 }}MB</div>
          </template>
          <template v-if="column.key === 'responseMs'">
            <span :style="{ color: (record.responseMs || 0) > 3000 ? '#ff4d4f' : (record.responseMs || 0) > 1000 ? '#faad14' : '#52c41a', fontWeight: 600 }">
              {{ record.responseMs != null ? record.responseMs + 'ms' : '-' }}
            </span>
          </template>
          <template v-if="column.key === 'collectTime'">
            {{ record.collectTime ? fmtTime(record.collectTime) : '-' }}
          </template>
          <template v-if="column.key === 'action'">
            <a-space>
              <a-popconfirm :title="`确认启动 ${record.projectName} (${record.nodeName})？`" @confirm="operateNode(record, 'start')">
                <a-button type="link" size="small" style="color: #52c41a">启动</a-button>
              </a-popconfirm>
              <a-popconfirm :title="`确认停止 ${record.projectName} (${record.nodeName})？`" @confirm="operateNode(record, 'stop')">
                <a-button type="link" size="small" danger>停止</a-button>
              </a-popconfirm>
              <a-popconfirm :title="`确认重启 ${record.projectName} (${record.nodeName})？`" @confirm="operateNode(record, 'restart')">
                <a-button type="link" size="small" style="color: #faad14">重启</a-button>
              </a-popconfirm>
              <a-button type="link" size="small" @click="openProbe(record.projectId)">探针</a-button>
            </a-space>
          </template>
        </template>
      </a-table>
      </div>
    </a-card>

    <!-- 探针配置弹窗 -->
    <a-modal v-model:open="probeModalVisible" title="HTTP 健康探针配置" @ok="saveProbe" :confirm-loading="probeSaving">
      <a-form layout="vertical">
        <a-form-item label="启用"><a-switch :checked="probe.enabled === 1" @change="(v: boolean) => probe.enabled = v ? 1 : 0" /></a-form-item>
        <a-form-item label="请求方法"><a-select v-model:value="probe.method"><a-select-option value="GET">GET</a-select-option><a-select-option value="POST">POST</a-select-option></a-select></a-form-item>
        <a-form-item label="URL"><a-input v-model:value="probe.url" placeholder="http://127.0.0.1:8080/health" /></a-form-item>
        <a-form-item label="期望状态码"><a-input-number v-model:value="probe.expectedStatus" :min="100" :max="599" style="width: 100%" /></a-form-item>
        <a-form-item label="响应包含"><a-input v-model:value="probe.bodyContains" placeholder="可选" /></a-form-item>
        <a-form-item label="超时(ms)"><a-input-number v-model:value="probe.timeoutMs" :min="500" :max="30000" style="width: 100%" /></a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, onUnmounted, watch } from 'vue'
import { message, Modal } from 'ant-design-vue'
import type { AppMonitorDashboard, AppMonitorNodeInfo, ProjectHealthProbeModel, AgentStatusItem } from '../types'
import {
  getAppDashboard, collectAppMonitor, getHealthProbe, saveHealthProbe,
  getMonitorCollectConfig, saveMonitorCollectConfig,
  collectAppMonitorFiltered, getAgentStatus
} from '../api/monitorApp'
import { getNodes } from '../api/node'
import { operateProjectNode } from '../api/project'
import { DashboardOutlined, ReloadOutlined, PlayCircleOutlined, PauseCircleOutlined } from '@ant-design/icons-vue'
import dayjs from 'dayjs'

interface MonitorTableRow extends AppMonitorNodeInfo {
  rowKey: string
  projectId: number
  projectName: string
  jarName?: string
  nodeIp?: string
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
const selectedRowKeys = ref<string[]>([])
const nodeIpMap = ref<Record<string, string>>({})
let autoTimer: ReturnType<typeof setInterval> | null = null
let collecting = false

// 分页配置
const activeTab = ref('app')

// Agent 状态
const agentList = ref<AgentStatusItem[]>([])
const agentLoading = ref(false)
const agentPagination = ref({
  current: 1,
  pageSize: 20,
  total: 0,
  showSizeChanger: true,
  pageSizeOptions: ['10', '20', '50'],
  showTotal: (total: number) => `共 ${total} 条`
})

const agentLastRefreshTime = ref<number>(0)

const agentOnlineCount = computed(() => agentList.value.filter(a => a.status === 1).length)
const agentOfflineCount = computed(() => agentList.value.filter(a => a.status !== 1).length)
const agentLastRefreshLabel = computed(() => {
  if (!agentLastRefreshTime.value) return '未刷新'
  return dayjs(agentLastRefreshTime.value).format('HH:mm:ss')
})

const agentColumns = [
  { title: 'Agent 名称', dataIndex: 'nodeName', key: 'nodeName', width: 140 },
  { title: 'IP', dataIndex: 'ip', key: 'ip', width: 130 },
  { title: '状态', key: 'status', width: 70 },
  { title: 'CPU', key: 'hostCpuPercent', width: 80 },
  { title: '内存', key: 'hostMemoryPercent', width: 80 },
  { title: '磁盘', key: 'diskUsagePercent', width: 80 },
  { title: '总内存', key: 'totalMemoryMb', width: 90 },
  { title: 'CPU 核数', dataIndex: 'cpuCores', key: 'cpuCores', width: 80 },
  { title: '系统', dataIndex: 'osInfo', key: 'osInfo', width: 160, ellipsis: true },
  { title: '版本', dataIndex: 'agentVersion', key: 'agentVersion', width: 80 },
]

const filteredCollecting = ref(false)

const pagination = ref({
  current: 1,
  pageSize: 50,
  total: 0,
  showSizeChanger: true,
  pageSizeOptions: ['50', '100', '200'],
  showTotal: (total: number) => `共 ${total} 条`
})

const probe = reactive<ProjectHealthProbeModel>({
  projectId: 0, enabled: 0, method: 'GET', url: '', expectedStatus: 200, timeoutMs: 5000
})

const columns = [
  { title: '应用', dataIndex: 'projectName', key: 'projectName', width: 120, fixed: 'left' as const },
  { title: '节点 / IP', key: 'nodeName', width: 140 },
  { title: '进程', key: 'processStatus', width: 80 },
  { title: '健康', key: 'healthStatus', width: 90, sorter: true },
  { title: 'CPU', key: 'cpu', width: 110, sorter: (a: MonitorTableRow, b: MonitorTableRow) => (a.hostCpuPercent || 0) - (b.hostCpuPercent || 0) },
  { title: '内存', key: 'memory', width: 120 },
  { title: '响应', key: 'responseMs', width: 100, sorter: (a: MonitorTableRow, b: MonitorTableRow) => (a.responseMs || 0) - (b.responseMs || 0), defaultSortOrder: 'descend' as const },
  { title: '采集时间', key: 'collectTime', width: 150 },
  { title: '操作', key: 'action', width: 130, fixed: 'right' as const }
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
        nodeIp: nodeIpMap.value[String(node.nodeId)] || ''
      })
    }
  }
  // 默认按响应时间降序
  rows.sort((a, b) => (b.responseMs || 0) - (a.responseMs || 0))
  pagination.value.total = rows.length
  return rows
})

const lastCollectLabel = computed(() => {
  if (!lastCollectTime.value) return '-'
  return dayjs(lastCollectTime.value).format('HH:mm:ss')
})

function fmtTime(ts: number): string {
  return dayjs(ts).format('MM-DD HH:mm:ss')
}

function formatPercent(v?: number): string {
  return v != null ? Number(v).toFixed(1) + '%' : '-'
}

function badgeStatus(status: string) {
  return ({ UP: 'success', DOWN: 'error', DEGRADED: 'warning' } as Record<string, any>)[status] || 'default'
}
function healthLabel(status: string) {
  return ({ UP: '健康', DOWN: '异常', DEGRADED: '降级', UNKNOWN: '未采集' } as Record<string, string>)[status] || status
}
function processLabel(status?: string) {
  return ({ RUNNING: '运行中', STOPPED: '已停止', UNKNOWN: '未知' } as Record<string, string>)[status || ''] || '-'
}

// ====== 启动/停止/重启 ======
const actionLabel: Record<string, string> = { start: '启动', stop: '停止', restart: '重启' }

async function operateNode(record: MonitorTableRow, action: 'start' | 'stop' | 'restart') {
  try {
    await operateProjectNode(String(record.projectId), String(record.nodeId), action)
    message.success(`${actionLabel[action]}指令已发送: ${record.projectName} / ${record.nodeName}`)
  } catch (e: any) {
    message.error(`${actionLabel[action]}失败: ` + (e?.message || '未知错误'))
  }
}

function batchOperate(action: 'start' | 'stop' | 'restart') {
  const rows = tableRows.value.filter(r => selectedRowKeys.value.includes(r.rowKey))
  if (rows.length === 0) return

  Modal.confirm({
    title: `确认批量${actionLabel[action]} ${rows.length} 个实例？`,
    content: rows.map(r => `${r.projectName} / ${r.nodeName}`).join('、'),
    okText: `确认${actionLabel[action]}`,
    okType: action === 'stop' ? 'danger' : 'primary',
    async onOk() {
      let success = 0, fail = 0
      for (const r of rows) {
        try {
          await operateProjectNode(String(r.projectId), String(r.nodeId), action)
          success++
        } catch {
          fail++
        }
      }
      message.success(`批量${actionLabel[action]}完成: ${success} 成功, ${fail} 失败`)
      selectedRowKeys.value = []
    }
  })
}

// ====== 数据获取 ======
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

// ====== Agent 状态 ======
async function fetchAgentStatus() {
  agentLoading.value = true
  try {
    const res = await getAgentStatus(agentPagination.value.current, agentPagination.value.pageSize)
    agentList.value = res.data?.list || []
    agentPagination.value.total = res.data?.total || 0
    agentLastRefreshTime.value = Date.now()
  } finally {
    agentLoading.value = false
  }
}

function handleAgentTableChange(pag: any) {
  agentPagination.value.current = pag.current
  agentPagination.value.pageSize = pag.pageSize
  fetchAgentStatus()
}

// ====== 选择性采集 ======
async function collectSelected() {
  const rows = tableRows.value.filter(r => selectedRowKeys.value.includes(r.rowKey))
  if (rows.length === 0) return
  filteredCollecting.value = true
  try {
    const pids = [...new Set(rows.map(r => r.projectId))]
    const nids = [...new Set(rows.map(r => r.nodeId))]
    await collectAppMonitorFiltered(pids, nids)
    lastCollectTime.value = Date.now()
    await fetchDashboard()
    message.success(`已采集 ${rows.length} 个实例`)
  } catch (e: any) {
    message.error('采集失败: ' + (e?.message || '未知错误'))
  } finally {
    filteredCollecting.value = false
  }
}

async function collectCurrentPage() {
  const rows = tableRows.value
  if (rows.length === 0) return
  filteredCollecting.value = true
  try {
    const pids = [...new Set(rows.map(r => r.projectId))]
    const nids = [...new Set(rows.map(r => r.nodeId))]
    await collectAppMonitorFiltered(pids, nids)
    lastCollectTime.value = Date.now()
    await fetchDashboard()
    message.success(`已采集当前页 ${rows.length} 个实例`)
  } catch (e: any) {
    message.error('采集失败: ' + (e?.message || '未知错误'))
  } finally {
    filteredCollecting.value = false
  }
}

async function collectFilteredProject() {
  if (!filterProjectId.value) {
    message.warning('请先在上方筛选中选择一个应用')
    return
  }
  filteredCollecting.value = true
  try {
    await collectAppMonitorFiltered([filterProjectId.value])
    lastCollectTime.value = Date.now()
    await fetchDashboard()
    message.success('已采集筛选应用的监控数据')
  } catch (e: any) {
    message.error('采集失败: ' + (e?.message || '未知错误'))
  } finally {
    filteredCollecting.value = false
  }
}

async function loadNodeIps() {
  try {
    const res = await getNodes(1, 1000)
    const map: Record<string, string> = {}
    res.data.list.forEach((n: any) => { map[String(n.id)] = n.ip })
    nodeIpMap.value = map
  } catch { /* ignore */ }
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

function handleTableChange(pag: any) {
  pagination.value.current = pag.current
  pagination.value.pageSize = pag.pageSize
}

function stopAutoCollect() {
  if (autoTimer) { clearInterval(autoTimer); autoTimer = null }
}

function startAutoCollect() {
  stopAutoCollect()
  autoTimer = setInterval(() => runCollectCycle(false), collectIntervalSec.value * 1000)
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

// 切换 Tab 时刷新对应数据
watch(activeTab, (tab) => {
  if (tab === 'agent') fetchAgentStatus()
})

onMounted(async () => {
  try {
    const cfg = await getMonitorCollectConfig()
    if (cfg.data?.collectIntervalSec) collectIntervalSec.value = cfg.data.collectIntervalSec
  } catch { /* 默认 60 秒 */ }
  await loadNodeIps()
  await runCollectCycle(false)
  startAutoCollect()
})

onUnmounted(() => { stopAutoCollect() })
</script>

<style scoped>
.error-text { color: #ff4d4f; font-size: 12px; }
</style>
