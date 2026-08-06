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
          <a-tag :color="wsConnected ? 'green' : 'red'" style="font-size: 12px">
            {{ wsConnected ? '🟢 WebSocket已连接' : '🔴 WebSocket未连接' }}
          </a-tag>
          <a-tag color="blue" style="font-size: 12px">
            WS消息: {{ wsMsgCount }}
          </a-tag>
          <a-tag style="font-size: 12px">
            ⏱ {{ nextRefreshSec }}s后自动刷新
          </a-tag>
          <span style="color: #8c8c8c; font-size: 12px">
            最后更新: {{ lastUpdateTime ? fmtTime(lastUpdateTime) : '-' }}
          </span>
          <a-button :loading="refreshing" @click="handleManualRefresh" :disabled="refreshing">
            <reload-outlined /> 刷新
          </a-button>
        </a-space>
      </template>

      <a-tabs v-model:activeKey="activeTab" type="card" style="margin-bottom: 0">
        <a-tab-pane key="app" tab="应用监控" />
      </a-tabs>

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
        :row-class-name="rowClassName"
        :scroll="{ x: 1400 }"
        @change="handleTableChange"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'projectName'">
            <a @click.prevent="openDetail(record)" style="font-weight:500;cursor:pointer;color:#1890ff">
              {{ record.projectName }}
            </a>
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
          <template v-if="column.key === 'processPid'">
            <span v-if="record.processStatus === 'N/A'" style="color:#bfbfbf">静态资源</span>
            <span v-else :style="{ fontWeight: 600, color: (record.processPid != null && record.processPid > 0) ? '#52c41a' : '#bfbfbf' }">
              {{ record.processPid != null && record.processPid > 0 ? record.processPid : '-' }}
            </span>
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
            <div v-if="record.processStatus === 'N/A'" style="font-size:11px;color:#bfbfbf">堆: -</div>
            <div v-else style="font-size:11px;color:#888">
              堆: {{ record.heapUsedMb || 0 }}/{{ record.heapMaxMb || 0 }}MB
              <a-tooltip v-if="record.xmxMb">
                <template #title>当前分配 {{ record.heapMaxMb || 0 }}MB，JVM 上限(-Xmx) {{ record.xmxMb }}MB。堆会根据需要自动扩展，最多到 -Xmx 设置值。</template>
                <info-circle-outlined style="font-size:12px;color:#1890ff;margin-left:4px;cursor:pointer" />
              </a-tooltip>
            </div>
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
            <a-dropdown :trigger="['click']">
              <a-button type="text" size="small" style="padding: 0 8px">
                <MoreOutlined style="font-size: 18px" />
              </a-button>
              <template #overlay>
                <a-menu @click="(info: any) => handleMenuAction(info.key, record)">
                  <a-menu-item key="start">
                    <PlayCircleOutlined style="color: #52c41a; margin-right: 6px" />启动
                  </a-menu-item>
                  <a-menu-item key="stop">
                    <PauseCircleOutlined style="color: #ff4d4f; margin-right: 6px" />停止
                  </a-menu-item>
                  <a-menu-item key="restart">
                    <ReloadOutlined style="color: #faad14; margin-right: 6px" />重启
                  </a-menu-item>
                  <a-menu-divider />
                  <a-menu-item key="probe">
                    <DashboardOutlined style="margin-right: 6px" />探针
                  </a-menu-item>
                  <a-menu-item key="detail">详情</a-menu-item>
                </a-menu>
              </template>
            </a-dropdown>
          </template>
        </template>
      </a-table>
      </div>
    </a-card>

    <!-- 实例详情 Drawer（应用监控 + Agent 状态 共用同一详情） -->
    <InstanceDetailDrawer
      :visible="drawerVisible"
      :record="drawerRecord"
      @close="drawerVisible = false"
    />

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
import type { AppMonitorDashboard, AppMonitorNodeInfo, ProjectHealthProbeModel } from '../types'
import {
  getAppDashboard, collectAppMonitor, getHealthProbe, saveHealthProbe,
  getMonitorCollectConfig,
  getCollectStatus
} from '../api/monitorApp'
import { getNodes } from '../api/node'
import { operateProjectNode, getProcessTaskStatus } from '../api/project'
import { DashboardOutlined, ReloadOutlined, PlayCircleOutlined, PauseCircleOutlined, MoreOutlined, InfoCircleOutlined } from '@ant-design/icons-vue'
import InstanceDetailDrawer from '../components/InstanceDetailDrawer.vue'
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
// 实例详情 Drawer
const drawerVisible = ref(false)
const drawerRecord = ref<any>(null)
const probeSaving = ref(false)
const probeProjectId = ref<number>()
const selectedRowKeys = ref<string[]>([])
const nodeIpMap = ref<Record<string, string>>({})
let collecting = false

// 行闪烁动效：记录刚更新的 rowKey，1.2秒后自动清除
const changedRowKeys = ref(new Set<string>())
let flashTimers: Map<string, ReturnType<typeof setTimeout>> = new Map()

function flashRow(rowKey: string) {
  changedRowKeys.value.add(rowKey)
  // 触发响应式更新
  changedRowKeys.value = new Set(changedRowKeys.value)
  if (flashTimers.has(rowKey)) clearTimeout(flashTimers.get(rowKey)!)
  flashTimers.set(rowKey, setTimeout(() => {
    changedRowKeys.value.delete(rowKey)
    changedRowKeys.value = new Set(changedRowKeys.value)
    flashTimers.delete(rowKey)
  }, 1200))
}

function rowClassName(record: any) {
  return changedRowKeys.value.has(record.rowKey) ? 'row-flash' : ''
}

// nodeId → [{ node, rowKey }] 索引，支持同节点多项目（用数组避免覆盖）
const nodeIndexMap = new Map<number, Array<{ node: any; rowKey: string }>>()
function rebuildNodeIndex() {
  nodeIndexMap.clear()
  for (const project of dashboard.value?.projects || []) {
    for (const node of project.nodes || []) {
      const list = nodeIndexMap.get(node.nodeId) || []
      list.push({
        node,
        rowKey: project.projectId + '-' + node.nodeId
      })
      nodeIndexMap.set(node.nodeId, list)
    }
  }
}

// 自动刷新倒计时
const nextRefreshSec = ref(0)
let refreshCountdownTimer: ReturnType<typeof setInterval> | null = null
let autoRefreshTimer: ReturnType<typeof setTimeout> | null = null
const wsMsgCount = ref(0)

// 分页配置
const activeTab = ref('app')

// WebSocket状态
const wsConnected = ref(false)
const lastUpdateTime = ref<number | null>(null)

// 采集任务状态
const collectTaskId = ref<string | null>(null)
const collectTaskStatus = ref<{ status: string; totalNodes: number; completedNodes: number } | null>(null)
let collectPollTimer: ReturnType<typeof setInterval> | null = null

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
  { title: '应用PID', key: 'processPid', width: 95, sorter: (a: MonitorTableRow, b: MonitorTableRow) => (a.processPid || 0) - (b.processPid || 0) },
  { title: '健康', key: 'healthStatus', width: 90, sorter: true },
  { title: 'CPU', key: 'cpu', width: 110, sorter: (a: MonitorTableRow, b: MonitorTableRow) => (a.hostCpuPercent || 0) - (b.hostCpuPercent || 0) },
  { title: '内存', key: 'memory', width: 120 },
  { title: '响应', key: 'responseMs', width: 100, sorter: (a: MonitorTableRow, b: MonitorTableRow) => (a.responseMs || 0) - (b.responseMs || 0), defaultSortOrder: 'descend' as const },
  { title: '采集时间', key: 'collectTime', width: 150 },
  { title: '操作', key: 'action', width: 70, fixed: 'right' as const }
]

// 实时数据直接 patch 到 dashboard 源数据，无需独立缓存层
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
  return rows
})

const tableTotal = computed(() => tableRows.value.length)

// 表格总数变化时同步更新分页
watch(tableTotal, (val) => { pagination.value.total = val }, { immediate: true })

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
  return ({ RUNNING: '运行中', STOPPED: '已停止', 'N/A': '静态资源', UNKNOWN: '未知' } as Record<string, string>)[status || ''] || '-'
}

// ====== 启动/停止/重启 ======
const actionLabel: Record<string, string> = { start: '启动', stop: '停止', restart: '重启' }

function handleMenuAction(key: string, record: MonitorTableRow) {
  if (key === 'start' || key === 'stop' || key === 'restart') {
    Modal.confirm({
      title: `确认${actionLabel[key]} ${record.projectName} (${record.nodeName})？`,
      okText: `确认${actionLabel[key]}`,
      okType: key === 'stop' ? 'danger' : 'primary',
      cancelText: '取消',
      onOk: () => operateNode(record, key as 'start' | 'stop' | 'restart')
    })
  } else if (key === 'probe') {
    openProbe(record.projectId)
  } else if (key === 'detail') {
    openDetail(record)
  }
}

async function operateNode(record: MonitorTableRow, action: 'start' | 'stop' | 'restart') {
  try {
    const res = await operateProjectNode(String(record.projectId), String(record.nodeId), action)
    const taskId = res.data?.taskId
    if (taskId) {
      // 异步操作：轮询任务状态
      message.loading(`${actionLabel[action]}指令已发送: ${record.projectName} / ${record.nodeName}，执行中...`)
      await pollProcessTask(taskId, action, record.projectName || '', record.nodeName || '')
    }
  } catch (e: any) {
    message.error(`${actionLabel[action]}失败: ` + (e?.message || '未知错误'))
  }
}

async function pollProcessTask(taskId: string, action: string, projectName: string, nodeName: string) {
  let retries = 0
  const maxRetries = 30 // 最多轮询 30 次（30 秒）

  while (retries < maxRetries) {
    await new Promise(resolve => setTimeout(resolve, 1000))
    try {
      const res = await getProcessTaskStatus(taskId)
      const status = res.data
      if (status.status === 'DONE') {
        message.success(`${actionLabel[action]}完成: ${projectName} / ${nodeName}`)
        return
      } else if (status.status === 'ERROR') {
        message.error(`${actionLabel[action]}失败: ${status.error || '未知错误'}`)
        return
      }
      // RUNNING 状态继续轮询
      retries++
    } catch {
      retries++
    }
  }
  // 超时但不一定失败（agent 可能还在执行）
  message.info(`${actionLabel[action]}指令已发送，正在执行中: ${projectName} / ${nodeName}`)
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
      const taskIds: { taskId: string; action: string; projectName: string; nodeName: string }[] = []

      // 并发发送所有指令（Promise.all）
      const results = await Promise.allSettled(
        rows.map(r => operateProjectNode(String(r.projectId), String(r.nodeId), action))
      )
      for (let i = 0; i < results.length; i++) {
        const result = results[i]
        if (result.status === 'fulfilled' && result.value.data?.taskId) {
          taskIds.push({
            taskId: result.value.data.taskId,
            action,
            projectName: rows[i].projectName || '',
            nodeName: rows[i].nodeName || ''
          })
          success++
        } else {
          fail++
        }
      }

      if (taskIds.length > 0) {
        message.loading(`批量${actionLabel[action]}指令已发送 ${taskIds.length} 个，执行中...`)

        // 并发轮询所有任务状态
        const completed = new Set<string>()
        let retries = 0
        while (completed.size < taskIds.length && retries < 30) {
          await new Promise(resolve => setTimeout(resolve, 1000))
          const pollResults = await Promise.allSettled(
            taskIds.filter(t => !completed.has(t.taskId)).map(t => getProcessTaskStatus(t.taskId))
          )
          for (const pr of pollResults) {
            if (pr.status === 'fulfilled' && (pr.value.data.status === 'DONE' || pr.value.data.status === 'ERROR')) {
              // 找到对应的 taskId（通过顺序对应）
              const idx = pollResults.indexOf(pr)
              const filteredTasks = taskIds.filter(t => !completed.has(t.taskId))
              if (filteredTasks[idx]) completed.add(filteredTasks[idx].taskId)
            }
          }
          retries++
        }

        message.success(`批量${actionLabel[action]}完成: ${success} 成功, ${fail} 失败`)
      } else {
        message.success(`批量${actionLabel[action]}完成: ${success} 成功, ${fail} 失败`)
      }

      selectedRowKeys.value = []
    }
  })
}

// ====== 数据获取 ======
async function fetchDashboard() {
  loading.value = true
  try {
    const res = await getAppDashboard()
    if (res.data?.collectIntervalSec) {
      collectIntervalSec.value = res.data.collectIntervalSec
    }
    // 合并而非替换：状态字段用 DB 权威值，实时指标保留 WS 已更新的最新值
    mergeDashboard(res.data)
    rebuildNodeIndex()
  } finally {
    loading.value = false
  }
}

/** 合并仪表盘数据：状态字段（进程/健康/PID）用新数据，实时指标（CPU/内存等）保留已有 */
function mergeDashboard(newData: any) {
  if (!dashboard.value) {
    dashboard.value = newData
    return
  }
  // 构建旧 node 索引（projectId_nodeId → node）
  const oldNodeMap = new Map<string, any>()
  for (const p of dashboard.value.projects || []) {
    for (const n of p.nodes || []) {
      oldNodeMap.set(p.projectId + '_' + n.nodeId, n)
    }
  }
  // 合并：每个新 node 的状态字段覆盖，实时指标保留旧值（WS 更实时）
  for (const p of newData.projects || []) {
    for (const n of p.nodes || []) {
      const key = p.projectId + '_' + n.nodeId
      const old = oldNodeMap.get(key)
      if (old) {
        // 状态字段用 DB（权威源）
        old.processStatus = n.processStatus
        old.processPid = n.processPid
        old.healthStatus = n.healthStatus
        old.healthDetail = n.healthDetail
        old.nodeName = n.nodeName
        old.jarName = n.jarName
        // 实时指标：DB 更新时才更新
        if (n.collectTime && (!old.collectTime || n.collectTime > old.collectTime)) {
          old.hostCpuPercent = n.hostCpuPercent
          old.cpuPercent = n.cpuPercent
          old.hostMemoryPercent = n.hostMemoryPercent
          old.memoryMb = n.memoryMb
          old.heapUsedMb = n.heapUsedMb
          old.heapMaxMb = n.heapMaxMb
          old.diskUsagePercent = n.diskUsagePercent
          old.responseMs = n.responseMs
          old.collectTime = n.collectTime
        }
        if (n.xmxMb !== undefined) old.xmxMb = n.xmxMb
        // 堆内存始终用 DB 值（WS 不推送应用的堆）
        old.heapUsedMb = n.heapUsedMb
        old.heapMaxMb = n.heapMaxMb
      }
      // 新节点直接追加（首次出现）
    }
  }
  // 更新顶层字段
  dashboard.value.collectIntervalSec = newData.collectIntervalSec
}

// ====== 数据获取 ======
async function loadNodeIps() {
  try {
    const res = await getNodes(1, 1000)
    const map: Record<string, string> = {}
    res.data.list.forEach((n: any) => { map[String(n.id)] = n.ip })
    nodeIpMap.value = map
  } catch { /* ignore */ }
}

// 轮询采集任务状态
async function pollCollectStatus(taskId: string) {
  // 防止重复轮询
  if (collectPollTimer) { clearInterval(collectPollTimer); collectPollTimer = null }

  collectTaskId.value = taskId
  collectTaskStatus.value = { status: 'RUNNING', totalNodes: 0, completedNodes: 0 }
  refreshing.value = true

  collectPollTimer = setInterval(async () => {
    try {
      const res = await getCollectStatus(taskId)
      const status = res.data
      if (!status) {
        // 任务不存在或已过期，停止轮询
        clearInterval(collectPollTimer!)
        collectPollTimer = null
        refreshing.value = false
        collecting = false
        collectTaskId.value = null
        collectTaskStatus.value = null
        return
      }

      collectTaskStatus.value = status

      if (status.status === 'DONE' || status.status === 'ERROR') {
        // 采集完成
        clearInterval(collectPollTimer!)
        collectPollTimer = null
        refreshing.value = false
        collecting = false
        lastCollectTime.value = Date.now()
        await fetchDashboard()

        if (status.status === 'DONE') {
          const pct = status.totalNodes > 0 ? Math.round(status.completedNodes / status.totalNodes * 100) : 100
          message.success(`监控数据已更新 (${status.completedNodes}/${status.totalNodes} 节点, ${pct}%)`)
        } else {
          message.error('采集异常: ' + (status.error || '未知错误'))
        }

        collectTaskId.value = null
        collectTaskStatus.value = null
      }
    } catch {
      // 轮询出错，停止轮询
      clearInterval(collectPollTimer!)
      collectPollTimer = null
      refreshing.value = false
      collecting = false
      collectTaskId.value = null
      collectTaskStatus.value = null
      message.error('查询采集状态失败')
    }
  }, 1000) // 每秒轮询一次
}

async function runCollectCycle(showToast = false) {
  if (collecting) return
  collecting = true
  refreshing.value = true
  try {
    const res = await collectAppMonitor()
    const taskId = res.data?.taskId
    if (taskId) {
      await pollCollectStatus(taskId)
    } else {
      // 兼容旧接口（不应发生）
      lastCollectTime.value = Date.now()
      await fetchDashboard()
      if (showToast) message.success('监控数据已更新')
      collecting = false
      refreshing.value = false
    }
  } catch (e: any) {
    message.error('采集失败: ' + (e?.message || '未知错误'))
    collecting = false
    refreshing.value = false
  }
}

async function handleManualRefresh() {
  refreshing.value = true
  try {
    await fetchDashboard()
    lastCollectTime.value = Date.now()
    lastUpdateTime.value = Date.now()
    // 重置倒计时
    nextRefreshSec.value = collectIntervalSec.value
    if (autoRefreshTimer) { clearTimeout(autoRefreshTimer); autoRefreshTimer = null }
    scheduleNextRefresh()
    message.success('数据已刷新')
  } finally {
    refreshing.value = false
  }
}

function handleTableChange(pag: any) {
  pagination.value.current = pag.current
  pagination.value.pageSize = pag.pageSize
}

function stopAutoCollect() {
  // 已废弃，保留兼容
}

// ====== WebSocket实时更新 ======
let ws: WebSocket | null = null
let wsReconnectTimer: ReturnType<typeof setTimeout> | null = null

function connectWebSocket() {
  try {
    // 构建WebSocket URL，带上token参数
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
    const token = localStorage.getItem('token') || ''
    const wsUrl = `${protocol}//${window.location.host}/api/ws/monitor?token=${encodeURIComponent(token)}`

    ws = new WebSocket(wsUrl)

    ws.onopen = () => {
      console.log('[Monitor WebSocket] Connected')
      wsConnected.value = true
    }

    ws.onmessage = (event) => {
      try {
        const data = JSON.parse(event.data)
        if (data.type === 'monitor_update' && data.nodeId && data.metrics) {
          wsMsgCount.value++
          // 实时更新监控指标（只更新 CPU/内存等高频数据，不覆盖状态字段）
          updateMonitorData(data.nodeId, data.metrics)
          lastUpdateTime.value = Date.now()
        }
      } catch (e) {
        console.warn('[Monitor WebSocket] Parse error:', e)
      }
    }

    ws.onclose = () => {
      console.log('[Monitor WebSocket] Disconnected, reconnecting in 5s...')
      wsConnected.value = false
      // 自动重连
      wsReconnectTimer = setTimeout(connectWebSocket, 5000)
    }

    ws.onerror = (error) => {
      console.error('[Monitor WebSocket] Error:', error)
      wsConnected.value = false
    }
  } catch (e) {
    console.error('[Monitor WebSocket] Connect failed:', e)
    wsConnected.value = false
    // 重试
    wsReconnectTimer = setTimeout(connectWebSocket, 5000)
  }
}

function disconnectWebSocket() {
  if (ws) {
    ws.close()
    ws = null
  }
  if (wsReconnectTimer) {
    clearTimeout(wsReconnectTimer)
    wsReconnectTimer = null
  }
}

function updateMonitorData(nodeId: number, metrics: Record<string, any>) {
  const entries = nodeIndexMap.get(nodeId)
  if (!entries || entries.length === 0) { console.warn('[Monitor] Node not found:', nodeId); return }

  for (const entry of entries) {
    const { node, rowKey } = entry

    // 检测数据是否有变化
    let changed = false
    if (node.hostCpuPercent !== metrics.cpuUsagePercent) changed = true
    if (node.hostMemoryPercent !== metrics.memoryUsagePercent) changed = true

    // 实时指标（CPU/内存/磁盘）— 主机级数据，同节点所有项目共享
    node.hostCpuPercent = metrics.cpuUsagePercent
    node.hostMemoryPercent = metrics.memoryUsagePercent
    node.diskUsagePercent = metrics.diskUsagePercent
    node.collectTime = Date.now()

    // 注意：不通过 WS 推送应用级数据（processPid/heapUsedMb/processStatus 等）
    // 同节点多项目时 WS computed 只含最后一个项目的数据，会串到其他项目
    // 应用级状态统一走 HTTP 轮询（fetchDashboard → mergeDashboard）

    // 数据变化时触发行闪烁
    if (changed) flashRow(rowKey)
  }

  lastCollectTime.value = Date.now()
  lastUpdateTime.value = Date.now()
}

async function openProbe(projectId: number) {
  probeProjectId.value = projectId
  const res = await getHealthProbe(projectId)
  if (res.data) Object.assign(probe, res.data)
  probe.projectId = projectId
  probeModalVisible.value = true
}

function openDetail(record: any) {
  drawerRecord.value = { ...record }
  drawerVisible.value = true
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
    if (cfg.data?.collectIntervalSec) collectIntervalSec.value = cfg.data.collectIntervalSec
  } catch { /* 默认 60 秒 */ }
  // 并行加载：节点IP 和 仪表盘数据 互不依赖，同时发起减少等待时间
  const [, dashboardResult] = await Promise.allSettled([
    loadNodeIps(),
    fetchDashboard()
  ])
  // fetchDashboard 失败不影响 WebSocket 连接和自动刷新
  if (dashboardResult.status === 'fulfilled') {
    // 数据已在 fetchDashboard 内通过 mergeDashboard 处理
  }
  // 连接WebSocket，接收实时更新
  connectWebSocket()
  // 启动兜底自动刷新（每 collectIntervalSec 秒轮询一次）
  startAutoRefreshCycle()
})

onUnmounted(() => {
  stopAutoCollect()
  stopAutoRefreshCycle()
  if (collectPollTimer) { clearInterval(collectPollTimer); collectPollTimer = null }
  // 断开WebSocket
  disconnectWebSocket()
})

// ====== 兜底自动刷新 + 倒计时 ======
function startAutoRefreshCycle() {
  stopAutoRefreshCycle()
  nextRefreshSec.value = collectIntervalSec.value
  console.log('[AutoRefresh] 启动，间隔', collectIntervalSec.value, '秒')
  refreshCountdownTimer = setInterval(() => {
    nextRefreshSec.value = Math.max(0, nextRefreshSec.value - 1)
  }, 1000)
  scheduleNextRefresh()
}

function scheduleNextRefresh() {
  if (autoRefreshTimer) { clearTimeout(autoRefreshTimer); autoRefreshTimer = null }
  autoRefreshTimer = setTimeout(async () => {
    try {
      // 智能跳过：WS 连接正常且近期收到过消息时，延长轮询间隔
      if (wsConnected.value && wsMsgCount.value > 0) {
        console.log('[AutoRefresh] WS 正常，跳过本轮轮询')
        nextRefreshSec.value = collectIntervalSec.value
      } else {
        console.log('[AutoRefresh] 执行轮询刷新，WS状态:', wsConnected.value, 'WS消息数:', wsMsgCount.value)
        await fetchDashboard()
        lastCollectTime.value = Date.now()
        nextRefreshSec.value = collectIntervalSec.value
      }
    } catch (e) {
      console.error('[AutoRefresh] 刷新失败:', e)
      nextRefreshSec.value = collectIntervalSec.value
    }
    scheduleNextRefresh()
  }, collectIntervalSec.value * 1000)
}

function stopAutoRefreshCycle() {
  if (refreshCountdownTimer) { clearInterval(refreshCountdownTimer); refreshCountdownTimer = null }
  if (autoRefreshTimer) { clearTimeout(autoRefreshTimer); autoRefreshTimer = null }
  nextRefreshSec.value = 0
}
</script>

<style scoped>
.error-text { color: #ff4d4f; font-size: 12px; }

/* 表格行数据更新闪烁：1.2s 柔和蓝色背景脉冲 */
:deep(.row-flash) {
  animation: row-flash-anim 1.2s ease-out;
}
@keyframes row-flash-anim {
  0%   { background-color: rgba(24, 144, 255, 0.12); }
  50%  { background-color: rgba(24, 144, 255, 0.05); }
  100% { background-color: transparent; }
}
</style>
