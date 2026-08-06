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
        <a-col :span="6"><a-statistic title="最后刷新" :value="agentLastRefreshLabel" /></a-col>
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
import { ref, computed, onMounted } from 'vue'
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
  { title: 'Agent PID', dataIndex: 'agentPid', key: 'agentPid', width: 80 },
  { title: '操作', key: 'action', width: 80, fixed: 'right' as const },
]

// 实例详情 Drawer
const drawerVisible = ref(false)
const drawerRecord = ref<any>(null)

function openAgentDetail(record: AgentStatusItem) {
  drawerRecord.value = {
    nodeId: record.nodeId,
    nodeName: record.nodeName,
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

onMounted(() => {
  fetchAgentStatus()
})
</script>
