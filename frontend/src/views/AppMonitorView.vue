<template>
  <div>
    <a-card :bordered="false" style="border-radius: 8px">
      <template #title>
        <a-space>
          <dashboard-outlined style="color: #52c41a" />
          <span style="font-weight: 600">应用监控</span>
        </a-space>
      </template>
      <template #extra>
        <a-space>
          <a-select
            v-model:value="projectId"
            style="width: 220px"
            placeholder="选择项目"
            @change="fetchOverview"
          >
            <a-select-option v-for="p in projects" :key="p.id" :value="Number(p.id)">
              {{ p.name }}
            </a-select-option>
          </a-select>
          <a-button @click="probeModalVisible = true">
            <setting-outlined /> 探针配置
          </a-button>
          <a-button :loading="diagnosing" @click="handleDiagnose">
            <bulb-outlined /> AI 诊断
          </a-button>
        </a-space>
      </template>

      <a-row v-if="overview" :gutter="16" style="margin-bottom: 16px">
        <a-col :span="4">
          <a-statistic title="节点总数" :value="overview.summary.totalNodes" />
        </a-col>
        <a-col :span="4">
          <a-statistic title="健康" :value="overview.summary.upCount" value-style="color: #52c41a" />
        </a-col>
        <a-col :span="4">
          <a-statistic title="异常" :value="overview.summary.downCount" value-style="color: #ff4d4f" />
        </a-col>
        <a-col :span="4">
          <a-statistic title="降级" :value="overview.summary.degradedCount" value-style="color: #faad14" />
        </a-col>
        <a-col :span="4">
          <a-statistic title="平均响应(ms)" :value="overview.summary.avgResponseMs" />
        </a-col>
        <a-col :span="4">
          <a-statistic title="稳定性" :value="overview.summary.stabilityScore" suffix="/100" />
        </a-col>
      </a-row>

      <a-row :gutter="16">
        <a-col v-for="node in overview?.nodes || []" :key="node.nodeId" :span="8" style="margin-bottom: 16px">
          <a-card size="small" :class="['health-card', healthClass(node.healthStatus)]">
            <template #title>
              <a-space>
                <a-badge :status="badgeStatus(node.healthStatus)" />
                {{ node.nodeName || node.nodeId }}
              </a-space>
            </template>
            <a-descriptions :column="2" size="small">
              <a-descriptions-item label="进程">{{ node.processStatus || '-' }}</a-descriptions-item>
              <a-descriptions-item label="响应">{{ node.responseMs ?? '-' }}ms</a-descriptions-item>
              <a-descriptions-item label="CPU">{{ node.cpuPercent ?? '-' }}%</a-descriptions-item>
              <a-descriptions-item label="内存">{{ node.memoryMb ?? '-' }}MB</a-descriptions-item>
              <a-descriptions-item v-if="node.lastError" label="错误" :span="2">
                <span class="error-text">{{ node.lastError }}</span>
              </a-descriptions-item>
            </a-descriptions>
          </a-card>
        </a-col>
      </a-row>
    </a-card>

    <!-- 探针配置 -->
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
          <a-input-number v-model:value="probe.expectedStatus" :min="100" :max="599" />
        </a-form-item>
        <a-form-item label="响应包含">
          <a-input v-model:value="probe.bodyContains" placeholder="可选" />
        </a-form-item>
        <a-form-item label="超时(ms)">
          <a-input-number v-model:value="probe.timeoutMs" :min="500" :max="30000" />
        </a-form-item>
      </a-form>
    </a-modal>

    <!-- AI 诊断结果 -->
    <a-modal v-model:open="diagVisible" title="AI 诊断报告" :footer="null" width="640px">
      <a-spin :spinning="diagLoading">
        <pre v-if="diagReport" class="diag-report">{{ diagReport }}</pre>
      </a-spin>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import type { ProjectModel, AppMonitorOverview, ProjectHealthProbeModel } from '../types'
import { getProjects } from '../api/project'
import {
  getAppOverview, getHealthProbe, saveHealthProbe, triggerDiagnose, getDiagnosis
} from '../api/monitorApp'
import {
  DashboardOutlined, SettingOutlined, BulbOutlined
} from '@ant-design/icons-vue'

const projects = ref<ProjectModel[]>([])
const projectId = ref<number>()
const overview = ref<AppMonitorOverview | null>(null)
const probeModalVisible = ref(false)
const probeSaving = ref(false)
const diagnosing = ref(false)
const diagVisible = ref(false)
const diagLoading = ref(false)
const diagReport = ref('')
const probe = reactive<ProjectHealthProbeModel>({
  projectId: 0, enabled: 0, method: 'GET', url: '', expectedStatus: 200, timeoutMs: 5000
})

function healthClass(status: string) {
  return { UP: 'card-up', DOWN: 'card-down', DEGRADED: 'card-degraded' }[status] || ''
}
function badgeStatus(status: string): 'success' | 'error' | 'warning' | 'default' {
  return ({ UP: 'success', DOWN: 'error', DEGRADED: 'warning' } as Record<string, 'success' | 'error' | 'warning'>)[status] || 'default'
}

async function fetchOverview() {
  if (!projectId.value) return
  const res = await getAppOverview(projectId.value)
  overview.value = res.data
  const probeRes = await getHealthProbe(projectId.value)
  if (probeRes.data) Object.assign(probe, probeRes.data)
  probe.projectId = projectId.value
}

async function saveProbe() {
  probeSaving.value = true
  try {
    probe.projectId = projectId.value!
    await saveHealthProbe(probe)
    probeModalVisible.value = false
    message.success('探针配置已保存')
  } finally {
    probeSaving.value = false
  }
}

async function handleDiagnose() {
  if (!projectId.value) return
  diagnosing.value = true
  try {
    const res = await triggerDiagnose({ projectId: projectId.value, triggerType: 'MANUAL' })
    const diagId = res.data?.diagnosisId
    if (diagId) {
      diagVisible.value = true
      diagLoading.value = true
      const report = await getDiagnosis(diagId)
      diagReport.value = report.data?.diagnosis || report.data?.logSnippet || '诊断中...'
      diagLoading.value = false
    }
  } finally {
    diagnosing.value = false
  }
}

onMounted(async () => {
  const res = await getProjects(1, 100)
  projects.value = res.data.list || []
})
</script>

<style scoped>
.health-card { border-radius: 8px; }
.card-up { border-left: 3px solid #52c41a; }
.card-down { border-left: 3px solid #ff4d4f; }
.card-degraded { border-left: 3px solid #faad14; }
.error-text { color: #ff4d4f; font-size: 12px; }
.diag-report {
  background: #141414;
  padding: 12px;
  border-radius: 6px;
  font-size: 13px;
  white-space: pre-wrap;
  max-height: 400px;
  overflow: auto;
}
</style>
