<template>
  <div>
    <a-card :bordered="false" style="border-radius: 8px">
      <template #title>
        <a-space>
          <dashboard-outlined style="color: #52c41a" />
          <span style="font-weight: 600">Agent 状态</span>
        </a-space>
      </template>
      <template #extra>
        <a-space>
          <a-button :loading="agentLoading" @click="fetchAgentStatus">
            <reload-outlined /> 刷新
          </a-button>
        </a-space>
      </template>

      <!-- 统计 -->
      <a-row :gutter="16" style="margin-bottom: 16px">
        <a-col :span="6"><a-statistic title="Agent 总数" :value="agentPagination.total" /></a-col>
        <a-col :span="6"><a-statistic title="在线" :value="agentOnlineCount" value-style="color: #52c41a" /></a-col>
        <a-col :span="6"><a-statistic title="离线" :value="agentOfflineCount" value-style="color: #ff4d4f" /></a-col>
        <a-col :span="6"><a-statistic title="最近上报" :value="agentLastRefreshLabel" /></a-col>
      </a-row>

      <!-- Agent 列表 -->
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
          <template v-if="column.key === 'ip'">
            <span style="font-family: monospace">{{ record.ip }}:{{ record.port }}</span>
          </template>
          <template v-if="column.key === 'agentPid'">
            <a-tooltip title="容器内 PID：Docker 中 Agent 即 1 号进程（agent.jar 为容器主进程），属正常现象；被监控应用的真实 PID 见「应用监控」页">
              <span style="font-family: monospace">{{ record.agentPid }}</span>
            </a-tooltip>
          </template>
          <template v-if="column.key === 'status'">
            <a-tag :color="record.status === 1 ? 'green' : 'red'">
              {{ record.status === 1 ? '在线' : '离线' }}
            </a-tag>
          </template>
          <template v-if="column.key === 'hostCpuPercent'">
            <div class="metric-cell">
              <span class="metric-val" :style="{ color: metricTextColor(record.hostCpuPercent, 80, 90) }">{{ formatPercent(record.hostCpuPercent) }}</span>
              <a-progress :percent="clampPct(record.hostCpuPercent)" :show-info="false" :stroke-color="barColor(record.hostCpuPercent, 80, 90)" size="small" :stroke-width="6" style="width:70px; margin:0" />
            </div>
          </template>
          <template v-if="column.key === 'hostMemoryPercent'">
            <div class="metric-cell">
              <span class="metric-val" :style="{ color: metricTextColor(record.hostMemoryPercent, 80, 90) }">{{ record.hostMemoryPercent != null ? record.hostMemoryPercent + '%' : '-' }}</span>
              <a-progress :percent="clampPct(record.hostMemoryPercent)" :show-info="false" :stroke-color="barColor(record.hostMemoryPercent, 80, 90)" size="small" :stroke-width="6" style="width:70px; margin:0" />
            </div>
          </template>
          <template v-if="column.key === 'diskUsagePercent'">
            <div class="metric-cell">
              <span class="metric-val" :style="{ color: metricTextColor(record.diskUsagePercent, 90, 95) }">{{ record.diskUsagePercent != null ? record.diskUsagePercent + '%' : '-' }}</span>
              <a-progress :percent="clampPct(record.diskUsagePercent)" :show-info="false" :stroke-color="barColor(record.diskUsagePercent, 90, 95)" size="small" :stroke-width="6" style="width:70px; margin:0" />
            </div>
          </template>
          <template v-if="column.key === 'lastSync'">
            <a-tooltip :title="syncInfo(record.lastHeartbeat, record.collectTime).stale ? '心跳超过 2 分钟未更新，Agent 可能已离线' : '上次上报时间：' + syncInfo(record.lastHeartbeat, record.collectTime).abs">
              <span :style="{ color: syncInfo(record.lastHeartbeat, record.collectTime).color, fontWeight: 600 }">
                {{ syncInfo(record.lastHeartbeat, record.collectTime).text }}
              </span>
            </a-tooltip>
          </template>
          <template v-if="column.key === 'totalMemoryMb'">
            <span v-if="record.totalMemoryMb">{{ (record.totalMemoryMb / 1024).toFixed(1) }} GB</span>
            <span v-else>-</span>
          </template>
          <template v-if="column.key === 'action'">
            <a-button type="link" size="small" @click="openAgentDetail(record)">详情</a-button>
          </template>
        </template>
      </a-table>
    </a-card>

    <!-- 实例详情 Drawer -->
    <InstanceDetailDrawer
      :visible="drawerVisible"
      :record="drawerRecord"
      @close="drawerVisible = false"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import type { AgentStatusItem } from '../types'
import { getAgentStatus } from '../api/monitorApp'
import { DashboardOutlined, ReloadOutlined } from '@ant-design/icons-vue'
import InstanceDetailDrawer from '../components/InstanceDetailDrawer.vue'
import dayjs from 'dayjs'

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
// 用于让「x 秒前」相对时间保持新鲜，每 5 秒 tick 一次
const nowTs = ref<number>(Date.now())
let syncTimer: ReturnType<typeof setInterval> | null = null

const agentOnlineCount = computed(() => agentList.value.filter(a => a.status === 1).length)
const agentOfflineCount = computed(() => agentList.value.filter(a => a.status !== 1).length)
const agentLastRefreshLabel = computed(() => {
  if (!agentLastRefreshTime.value) return '未同步'
  return dayjs(agentLastRefreshTime.value).format('HH:mm:ss')
})

// 指标文字颜色：正常时继承主题默认色（适配亮/暗主题），超阈值才变色
function metricTextColor(v: number | undefined, warn: number, danger: number): string {
  const x = v ?? 0
  if (x >= danger) return '#ff4d4f'
  if (x >= warn) return '#faad14'
  return 'inherit'  // 继承主题文字颜色，亮/暗主题自适应
}
// 进度条颜色：绿/橙/红，直观表达健康度
function barColor(v: number | undefined, warn: number, danger: number): string {
  const x = v ?? 0
  if (x >= danger) return '#ff4d4f'
  if (x >= warn) return '#faad14'
  return '#52c41a'
}
function clampPct(v?: number): number {
  if (v == null) return 0
  return Math.max(0, Math.min(100, v))
}
// 同步时间展示：优先 collectTime，回退 lastHeartbeat；超过 2 分钟视为异常
const SYNC_STALE_MS = 2 * 60 * 1000
function syncInfo(lastHeartbeat?: number, collectTime?: number) {
  const ts = (collectTime ?? lastHeartbeat ?? 0) as number
  if (!ts) return { text: '从未同步', abs: '—', color: '#ff4d4f', stale: true }
  const age = nowTs.value - ts
  const stale = age > SYNC_STALE_MS
  const abs = dayjs(ts).format('MM-DD HH:mm:ss')
  // 正常时显示绝对时间，异常时显示"XX分钟前"
  const rel = age < 60000 ? Math.floor(age / 1000) + '秒前'
           : age < 3600000 ? Math.floor(age / 60000) + '分钟前'
           : Math.floor(age / 3600000) + '小时前'
  return { text: stale ? rel : abs, abs, color: stale ? '#ff4d4f' : '#52c41a', stale }
}

const agentColumns = [
  { title: 'Agent 名称', dataIndex: 'nodeName', key: 'nodeName', width: 140, sorter: (a: any, b: any) => (a.nodeName || '').localeCompare(b.nodeName || '') },
  { title: 'IP / 端口', dataIndex: 'ip', key: 'ip', width: 160, sorter: (a: any, b: any) => (a.ip || '').localeCompare(b.ip || '') },
  { title: '状态', key: 'status', width: 70, sorter: (a: any, b: any) => a.status - b.status },
  { title: 'CPU', key: 'hostCpuPercent', width: 80, sorter: (a: any, b: any) => (a.hostCpuPercent || 0) - (b.hostCpuPercent || 0) },
  { title: '内存', key: 'hostMemoryPercent', width: 80, sorter: (a: any, b: any) => (a.hostMemoryPercent || 0) - (b.hostMemoryPercent || 0) },
  { title: '磁盘', key: 'diskUsagePercent', width: 90, sorter: (a: any, b: any) => (a.diskUsagePercent || 0) - (b.diskUsagePercent || 0) },
  { title: '最后同步', key: 'lastSync', width: 160, sorter: (a: any, b: any) => ((a.collectTime ?? a.lastHeartbeat ?? 0) as number) - ((b.collectTime ?? b.lastHeartbeat ?? 0) as number) },
  { title: '总内存', key: 'totalMemoryMb', width: 90, sorter: (a: any, b: any) => (a.totalMemoryMb || 0) - (b.totalMemoryMb || 0) },
  { title: 'CPU 核数', dataIndex: 'cpuCores', key: 'cpuCores', width: 80, sorter: (a: any, b: any) => (a.cpuCores || 0) - (b.cpuCores || 0) },
  { title: '系统', dataIndex: 'osInfo', key: 'osInfo', width: 160, ellipsis: true, sorter: (a: any, b: any) => (a.osInfo || '').localeCompare(b.osInfo || '') },
  { title: '版本', dataIndex: 'agentVersion', key: 'agentVersion', width: 80, sorter: (a: any, b: any) => (a.agentVersion || '').localeCompare(b.agentVersion || '') },
  { title: 'Agent PID', dataIndex: 'agentPid', key: 'agentPid', width: 80, sorter: (a: any, b: any) => (a.agentPid || 0) - (b.agentPid || 0) },
  { title: '操作', key: 'action', width: 80, fixed: 'right' as const },
]

// 实例详情 Drawer
const drawerVisible = ref(false)
const drawerRecord = ref<any>(null)

function openAgentDetail(record: AgentStatusItem) {
  drawerRecord.value = {
    nodeId: record.nodeId,
    nodeName: record.nodeName,
    ip: record.ip,
    port: record.port,
    projectName: '(Agent 自身)',
    projectId: undefined as unknown as number, // 无 project，跳过实时刷新
    processPid: record.agentPid ?? null,        // 进程即 Agent 自己
    agentPid: record.agentPid ?? null,
    processStatus: record.status === 1 ? 'RUNNING' : 'STOPPED',
    healthStatus: record.status === 1 ? 'UP' : 'DOWN',
    healthDetail: record.status === 1 ? 'Agent 在线' : 'Agent 离线',
    hostCpuPercent: record.hostCpuPercent,
    hostMemoryPercent: record.hostMemoryPercent,
    diskUsagePercent: record.diskUsagePercent,
    cpuPercent: null,
    heapUsedMb: null,
    heapMaxMb: null,
    memoryMb: null,
    gcCount: null,
    gcTimeMs: null,
    responseMs: null,
  } as any
  drawerVisible.value = true
}

function formatPercent(v?: number): string {
  return v != null ? Number(v).toFixed(1) + '%' : '-'
}

async function fetchAgentStatus() {
  agentLoading.value = true
  try {
    const res = await getAgentStatus(agentPagination.value.current, agentPagination.value.pageSize)
    const list = (res.data?.list || []) as AgentStatusItem[]
    agentList.value = list
    agentPagination.value.total = res.data?.total || 0
    nowTs.value = Date.now()
    // 真实同步时间 = 所有节点上报时间的最大值（优先 collectTime，回退 lastHeartbeat）
    let maxTs = 0
    for (const r of list) {
      const ts = (r.collectTime ?? r.lastHeartbeat ?? 0) as number
      if (ts > maxTs) maxTs = ts
    }
    agentLastRefreshTime.value = maxTs || nowTs.value
  } finally {
    agentLoading.value = false
  }
}

function handleAgentTableChange(pag: any) {
  agentPagination.value.current = pag.current
  agentPagination.value.pageSize = pag.pageSize
  fetchAgentStatus()
}

onMounted(() => {
  fetchAgentStatus()
  // 每 5 秒刷新相对时间（x 秒前），让「最后同步」列保持新鲜
  syncTimer = setInterval(() => { nowTs.value = Date.now() }, 5000)
})

onUnmounted(() => {
  if (syncTimer) clearInterval(syncTimer)
})
</script>

<style scoped>
.metric-cell { display: flex; align-items: center; gap: 8px; }
.metric-val { font-weight: 600; min-width: 50px; font-variant-numeric: tabular-nums; }
</style>
