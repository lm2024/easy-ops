<template>
  <div class="dashboard-page">
    <!-- ====== 顶部统计卡片 ====== -->
    <a-row :gutter="16" style="margin-bottom: 16px">
      <a-col :span="6">
        <a-card class="stat-card" :bordered="false">
          <a-statistic title="应用总数" :value="stats.totalApps">
            <template #prefix>
              <rocket-outlined style="color: #fa541c" />
            </template>
          </a-statistic>
        </a-card>
      </a-col>
      <a-col :span="6">
        <a-card class="stat-card stat-card-success" :bordered="false">
          <a-statistic title="运行正常" :value="stats.healthyApps">
            <template #prefix>
              <check-circle-outlined style="color: #52c41a" />
            </template>
          </a-statistic>
        </a-card>
      </a-col>
      <a-col :span="6">
        <a-card class="stat-card stat-card-danger" :bordered="false">
          <a-statistic title="运行异常" :value="stats.unhealthyApps">
            <template #prefix>
              <warning-outlined style="color: #ff4d4f" />
            </template>
          </a-statistic>
        </a-card>
      </a-col>
      <a-col :span="6">
        <a-card class="stat-card stat-card-info" :bordered="false">
          <a-statistic title="在线节点" :value="stats.onlineNodes + ' / ' + stats.totalNodes">
            <template #prefix>
              <cluster-outlined style="color: #1890ff" />
            </template>
          </a-statistic>
        </a-card>
      </a-col>
    </a-row>

    <!-- ====== 应用状态总览 ====== -->
    <a-card title="🚀 应用状态" :bordered="false" style="border-radius: 12px; margin-bottom: 16px">
      <template #extra>
        <a-button size="small" @click="fetchData" :loading="loading">
          <reload-outlined /> 刷新
        </a-button>
      </template>

      <a-table :data-source="appList" :columns="appColumns" :pagination="false" row-key="id" size="small"
               :loading="loading" :scroll="{ x: 900 }">
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'name'">
            <router-link :to="`/projects/${record.id}`" style="font-weight:500">
              {{ record.name }}
            </router-link>
          </template>
          <template v-if="column.key === 'nodes'">
            <a-space wrap>
              <a-tooltip v-for="n in record.nodeDetails" :key="n.nodeId" :title="n.nodeIp">
                <a-tag :color="n.online ? 'green' : 'red'" size="small">
                  {{ n.nodeName }}
                </a-tag>
              </a-tooltip>
              <span v-if="!record.nodeDetails || record.nodeDetails.length === 0" style="color:#999">未绑定节点</span>
            </a-space>
          </template>
          <template v-if="column.key === 'health'">
            <template v-if="record.nodeDetails && record.nodeDetails.length > 0">
              <a-space>
                <a-badge v-for="n in record.nodeDetails" :key="n.nodeId"
                         :status="n.healthStatus === 'UP' ? 'success' : n.healthStatus === 'DOWN' ? 'error' : 'default'"
                         :text="n.nodeName" />
              </a-space>
            </template>
            <span v-else style="color:#999">-</span>
          </template>
          <template v-if="column.key === 'lastDeploy'">
            <template v-if="record.lastDeployTime">
              <div>{{ fmtTime(record.lastDeployTime) }}</div>
              <a-tag :color="record.lastDeployStatus === 1 ? 'green' : record.lastDeployStatus === 2 ? 'red' : 'default'" size="small">
                {{ statusText(record.lastDeployStatus) }}
              </a-tag>
            </template>
            <span v-else style="color:#999">未部署过</span>
          </template>
          <template v-if="column.key === 'action'">
            <a-space>
              <router-link :to="`/projects/${record.id}`">
                <a-button type="link" size="small">详情</a-button>
              </router-link>
              <router-link :to="`/deploy`">
                <a-button type="link" size="small">部署</a-button>
              </router-link>
            </a-space>
          </template>
        </template>
      </a-table>
    </a-card>

    <!-- ====== 底部：节点状态 + 最近部署 ====== -->
    <a-row :gutter="16">
      <a-col :span="12">
        <a-card title="🖥️ 节点状态" :bordered="false" style="border-radius: 12px">
          <a-table :data-source="nodeList" :columns="nodeColumns" :pagination="false" row-key="id" size="small">
            <template #bodyCell="{ column, record }">
              <template v-if="column.key === 'status'">
                <a-badge :status="record.status === 1 ? 'success' : 'default'" :text="record.status === 1 ? '在线' : '离线'" />
              </template>
              <template v-if="column.key === 'ip'">
                <code>{{ record.ip }}:{{ record.port }}</code>
              </template>
            </template>
          </a-table>
        </a-card>
      </a-col>
      <a-col :span="12">
        <a-card title="📋 最近部署" :bordered="false" style="border-radius: 12px">
          <a-table :data-source="recentDeploys" :columns="recentColumns" :pagination="false" row-key="id" size="small">
            <template #bodyCell="{ column, record }">
              <template v-if="column.key === 'project'">
                {{ projectNameMap[String(record.projectId)] || '项目#' + record.projectId }}
              </template>
              <template v-if="column.key === 'status'">
                <a-badge :status="statusBadge(record.status)" :text="statusText(record.status)" />
              </template>
              <template v-if="column.key === 'time'">
                {{ fmtTime(record.startTime) }}
              </template>
            </template>
          </a-table>
        </a-card>
      </a-col>
    </a-row>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { getProjects } from '../api/project'
import { getNodes } from '../api/node'
import { getDeployRecords } from '../api/deploy'
import { getAppDashboard } from '../api/monitorApp'
import {
  RocketOutlined, CheckCircleOutlined, WarningOutlined,
  ClusterOutlined, ReloadOutlined
} from '@ant-design/icons-vue'

// ====== 统计 ======
const stats = ref({
  totalApps: 0, healthyApps: 0, unhealthyApps: 0,
  totalNodes: 0, onlineNodes: 0
})

const loading = ref(false)

// ====== 应用列表 ======
const appList = ref<any[]>([])
const appColumns = [
  { title: '应用名称', key: 'name', dataIndex: 'name', width: 140 },
  { title: '部署节点', key: 'nodes', width: 220 },
  { title: '健康状态', key: 'health', width: 200 },
  { title: '最近部署', key: 'lastDeploy', width: 180 },
  { title: '操作', key: 'action', width: 120 }
]

// ====== 节点列表 ======
const nodeList = ref<any[]>([])
const nodeColumns = [
  { title: '节点', dataIndex: 'name', key: 'name', width: 120 },
  { title: '地址', key: 'ip', width: 160 },
  { title: '状态', key: 'status', width: 80 }
]

// ====== 最近部署 ======
const recentDeploys = ref<any[]>([])
const recentColumns = [
  { title: '应用', key: 'project', width: 120 },
  { title: '节点', key: 'nodeName', dataIndex: 'nodeName', width: 120 },
  { title: '状态', key: 'status', width: 100 },
  { title: '时间', key: 'time', width: 160 }
]

const projectNameMap = ref<Record<string, string>>({})

// ====== 方法 ======
function fmtTime(ts: any): string {
  if (!ts) return '-'
  const d = new Date(typeof ts === 'string' ? Number(ts) : ts)
  if (isNaN(d.getTime())) return String(ts)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${pad(d.getMonth()+1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

function statusText(s: number): string {
  const map: Record<number, string> = { 0: '进行中', 1: '成功', 2: '失败', 3: '已取消', 4: '回滚成功', 5: '待部署' }
  return map[s] || '未知'
}

function statusBadge(s: number): string {
  const map: Record<number, string> = { 0: 'processing', 1: 'success', 2: 'error', 3: 'default', 4: 'success', 5: 'warning' }
  return map[s] || 'default'
}

async function fetchData() {
  loading.value = true
  try {
    const [nodeRes, projRes, deployRes] = await Promise.all([
      getNodes(1, 1000),
      getProjects(1, 1000),
      getDeployRecords(undefined as any, 1, 20)
    ])

    const nodes = nodeRes.data.list || []
    const projects = projRes.data.list || []
    const deploys = deployRes.data.list || []

    nodeList.value = nodes
    recentDeploys.value = deploys.slice(0, 10)

    // 构建项目名映射
    const pMap: Record<string, string> = {}
    projects.forEach((p: any) => { pMap[String(p.id)] = p.name })
    projectNameMap.value = pMap

    // 构建节点状态映射
    const nodeStatusMap: Record<string, { name: string; ip: string; online: boolean }> = {}
    nodes.forEach((n: any) => {
      nodeStatusMap[String(n.id)] = { name: n.name, ip: n.ip, online: n.status === 1 }
    })

    // 尝试获取应用监控数据（可能没有采集过）
    let monitorData: any = null
    try {
      const monitorRes = await getAppDashboard()
      monitorData = monitorRes.data
    } catch {
      // 监控数据不可用，用基础数据
    }

    // 构建应用列表
    let healthy = 0, unhealthy = 0
    appList.value = projects.map((p: any) => {
      const nodeIds = p.nodeIds ? p.nodeIds.split(',').map((s: string) => s.trim()).filter(Boolean) : []
      const nodeDetails = nodeIds.map((id: string) => {
        const node = nodeStatusMap[id]
        // 从监控数据中找健康状态
        let healthStatus = 'UNKNOWN'
        if (monitorData && monitorData.projects) {
          const mp = monitorData.projects.find((mp: any) => mp.projectId === p.id)
          if (mp && mp.nodes) {
            const mn = mp.nodes.find((mn: any) => String(mn.nodeId) === id)
            if (mn) healthStatus = mn.healthStatus || 'UNKNOWN'
          }
        }
        return {
          nodeId: id,
          nodeName: node?.name || '节点#' + id,
          nodeIp: node?.ip || '',
          online: node?.online || false,
          healthStatus
        }
      })

      // 应用健康判断：所有节点都 UP = 健康，任一 DOWN = 不健康
      const allUp = nodeDetails.length > 0 && nodeDetails.every((n: any) => n.healthStatus === 'UP')
      const anyDown = nodeDetails.some((n: any) => n.healthStatus === 'DOWN')
      if (allUp) healthy++
      else if (anyDown) unhealthy++

      // 最近部署记录
      const lastDeploy = deploys.find((d: any) => d.projectId === p.id)

      return {
        ...p,
        nodeDetails,
        lastDeployTime: lastDeploy?.startTime,
        lastDeployStatus: lastDeploy?.status
      }
    })

    stats.value = {
      totalApps: projects.length,
      healthyApps: healthy,
      unhealthyApps: unhealthy,
      totalNodes: nodes.length,
      onlineNodes: nodes.filter((n: any) => n.status === 1).length
    }
  } finally {
    loading.value = false
  }
}

onMounted(fetchData)
</script>

<style scoped>
.dashboard-page {
  min-height: 100%;
  padding-bottom: 24px;
}
.stat-card {
  border-radius: 12px;
  transition: transform 0.2s, box-shadow 0.2s;
}
.stat-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
}
.stat-card :deep(.ant-statistic-title) {
  color: #8c8c8c;
  font-size: 14px;
}
.stat-card :deep(.ant-statistic-content) {
  font-size: 28px;
  font-weight: 600;
}
</style>
