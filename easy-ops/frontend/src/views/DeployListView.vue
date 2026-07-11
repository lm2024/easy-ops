<template>
  <div>
    <a-card :bordered="false" style="border-radius: 8px; margin-bottom: 16px">
      <template #title>
        <a-space>
          <rocket-outlined style="color: #fa541c" />
          <span style="font-weight: 600">🚀 一键部署</span>
        </a-space>
      </template>

      <a-form layout="inline" :model="form">
        <a-form-item label="选择应用">
          <a-select v-model:value="form.projectId" style="width: 220px" placeholder="选择要部署的应用" @change="onProjectChange">
            <a-select-option v-for="p in projects" :key="p.id" :value="p.id">
              {{ p.name }}
              <template #suffixIcon>
                <info-circle-outlined />
              </template>
            </a-select-option>
          </a-select>
        </a-form-item>

        <a-form-item label="选择版本">
          <a-select v-model:value="form.versionId" style="width: 220px" placeholder="选择版本包" :disabled="!form.projectId">
            <a-select-option v-for="v in versionList" :key="v.id" :value="v.id">
              {{ v.version || v.jarName }}
              <span style="color:#888;font-size:12px"> — {{ v.jarName }}</span>
            </a-select-option>
          </a-select>
        </a-form-item>

        <a-form-item label="部署节点">
          <a-space wrap>
            <a-tag v-for="n in targetNodes" :key="n" color="blue">{{ n }}</a-tag>
          </a-space>
        </a-form-item>

        <a-form-item label="部署方式">
          <a-radio-group v-model:value="form.deployMode">
            <a-radio-button value="now">🚀 立即</a-radio-button>
            <a-radio-button value="schedule">⏰ 定时</a-radio-button>
          </a-radio-group>
        </a-form-item>

        <a-form-item v-if="form.deployMode === 'schedule'">
          <a-date-picker v-model:value="form.scheduleDate" show-time style="width: 200px"
                         :disabled-date="(d: any) => d && d.valueOf() < Date.now() - 86400000"
                         placeholder="选择时间" />
        </a-form-item>

        <a-form-item>
          <a-button type="primary" @click="startDeploy" :loading="deploying"
                    :disabled="!form.projectId || !form.versionId">
            {{ form.deployMode === 'now' ? '🚀 执行部署' : '⏰ 创建定时计划' }}
          </a-button>
        </a-form-item>
      </a-form>
    </a-card>

    <!-- 部署结果区域 -->
    <a-card v-if="lastResult" :bordered="false" style="border-radius: 8px; margin-bottom: 16px"
            :class="'result-card-' + (lastResult.status === 1 || lastResult.status === 4 ? 'success' : lastResult.status === 5 ? 'schedule' : 'fail')">
      <template #title>
        <a-space>
          <check-circle-outlined v-if="lastResult.status === 1" style="color:#52c41a" />
          <clock-circle-outlined v-else-if="lastResult.status === 5" style="color:#faad14" />
          <close-circle-outlined v-else style="color:#ff4d4f" />
          <span style="font-weight:600">{{ lastResult.message }}</span>
        </a-space>
      </template>

      <a-timeline v-if="lastResult.steps" style="margin:0">
        <a-timeline-item v-for="(step, i) in lastResult.steps" :key="i"
                         :color="step.success ? 'green' : 'red'">
          <template #dot>
            <check-circle-outlined v-if="step.success" style="color:#52c41a" />
            <close-circle-outlined v-else style="color:#ff4d4f" />
          </template>
          <strong>{{ step.name }}</strong>
          <pre class="step-detail">{{ step.detail }}</pre>
        </a-timeline-item>
      </a-timeline>
      <a-collapse ghost style="margin-top:8px">
        <a-collapse-panel header="📄 查看完整日志">
          <pre class="log-pre">{{ lastResult.log }}</pre>
        </a-collapse-panel>
      </a-collapse>
    </a-card>

    <!-- 部署历史 -->
    <a-card :bordered="false" style="border-radius: 8px">
      <template #title>
        <a-space>
          <history-outlined />
          <span style="font-weight:600">部署历史</span>
          <a-tag v-if="pendingCount > 0" color="orange">{{ pendingCount }} 个待部署</a-tag>
        </a-space>
      </template>

      <a-table :columns="historyColumns" :data-source="deployHistory" :loading="loadingHistory"
               :pagination="historyPagination" row-key="id" size="small"
               @change="handleTableChange">
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'status'">
            <a-badge :status="statusMap[record.status]?.badge" :text="statusMap[record.status]?.text" />
          </template>
          <template v-if="column.key === 'nodeName'">
            {{ getNodeName(record.nodeId) || '节点#' + record.nodeId }}
          </template>
          <template v-if="column.key === 'startTime'">
            {{ fmtTime(record.startTime) }}
          </template>
          <template v-if="column.key === 'scheduleTime'">
            <template v-if="record.scheduleTime && record.scheduleTime > 0">
              <div>{{ fmtTime(record.scheduleTime) }}</div>
              <div v-if="record.status === 5" style="font-size:12px;color:#faad14;font-weight:600">
                ⏳ {{ getCountdown(record.scheduleTime) }}
              </div>
            </template>
            <span v-else>-</span>
          </template>
          <template v-if="column.key === 'action'">
            <a-button type="link" size="small" @click="showLog(record)">📋 日志</a-button>
            <a-button type="link" size="small" v-if="record.status === 1" @click="rollback(record)">↩️ 回滚</a-button>
            <a-popconfirm v-if="record.status === 5" title="确定取消此定时部署?" ok-text="确定" cancel-text="取消" @confirm="cancelSchedule(record.id)">
              <a-button type="link" size="small" danger><stop-outlined /> 取消</a-button>
            </a-popconfirm>
          </template>
        </template>
      </a-table>
    </a-card>

    <!-- 日志弹窗 -->
    <a-modal title="部署日志" v-model:open="logVisible" width="780px" :footer="null">
      <pre class="log-pre" style="max-height:500px">{{ logContent }}</pre>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { message } from 'ant-design-vue'
import type { Dayjs } from 'dayjs'
import { getProjects } from '../api/project'
import { getNodes } from '../api/node'
import { getVersions } from '../api/version'
import { getDeployRecords, createDeploy, rollbackDeploy, cancelScheduledDeploy } from '../api/deploy'
import {
  RocketOutlined, CheckCircleOutlined, CloseCircleOutlined,
  ClockCircleOutlined, HistoryOutlined, InfoCircleOutlined, StopOutlined
} from '@ant-design/icons-vue'

// ====== 表单状态 ======
const form = ref({
  projectId: undefined as string | undefined,
  versionId: undefined as string | undefined,
  deployMode: 'now' as string,
  scheduleDate: undefined as Dayjs | undefined
})

const projects = ref<any[]>([])
const versionList = ref<any[]>([])
const nodeList = ref<any[]>([])
const nodeMap = ref<Record<string, string>>({})
const deploying = ref(false)
const lastResult = ref<any>(null)

// ====== 部署历史 ======
const deployHistory = ref<any[]>([])
const loadingHistory = ref(false)
const historyPagination = ref({ current: 1, pageSize: 20, total: 0 })
const logVisible = ref(false)
const logContent = ref('')
const nowMs = ref(Date.now())
let refreshTimer: any = null
let countdownTimer: any = null

// ====== 计算属性 ======
const targetNodes = computed(() => {
  if (!form.value.projectId) return []
  const p = projects.value.find(p => p.id === form.value.projectId)
  if (!p || !p.nodeIds) return []
  const ids = p.nodeIds.split(',').map((s: string) => s.trim()).filter(Boolean)
  return ids.map((id: string) => nodeMap.value[id] || '节点#' + id)
})

const pendingCount = computed(() =>
  deployHistory.value.filter(r => r.status === 5).length
)

const statusMap: Record<number, { badge: string; text: string }> = {
  0: { badge: 'processing', text: '⏳ 进行中' },
  1: { badge: 'success', text: '✅ 部署成功' },
  2: { badge: 'error', text: '❌ 部署失败' },
  3: { badge: 'default', text: '⛔ 已取消' },
  4: { badge: 'success', text: '↩️✅ 回滚成功' },
  5: { badge: 'warning', text: '⏰ 待部署' }
}

const historyColumns = [
  { title: 'ID', dataIndex: 'id', key: 'id', width: 60 },
  { title: '版本', dataIndex: 'versionId', key: 'versionId', width: 70 },
  { title: 'Jar包', dataIndex: 'jarName', key: 'jarName', width: 130, ellipsis: true },
  { title: '节点', dataIndex: 'nodeName', key: 'nodeName', width: 130 },
  { title: '状态', dataIndex: 'status', key: 'status', width: 110 },
  { title: '执行时间', dataIndex: 'scheduleTime', key: 'scheduleTime', width: 170 },
  { title: '创建时间', dataIndex: 'startTime', key: 'startTime', width: 160 },
  { title: '操作', key: 'action', width: 180, fixed: 'right' as const }
]

// ====== 方法 ======
function fmtTime(ts: any): string {
  if (!ts) return '-'
  const d = new Date(typeof ts === 'string' ? Number(ts) : ts)
  if (isNaN(d.getTime())) return String(ts)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth()+1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

function getNodeName(nodeId: any): string {
  return nodeId ? nodeMap.value[String(nodeId)] || '' : ''
}

function getCountdown(scheduleTime: number): string {
  const diff = scheduleTime - nowMs.value
  if (diff <= 0) return '即将执行...'
  const sec = Math.floor(diff / 1000)
  const d = Math.floor(sec / 86400)
  const h = Math.floor((sec % 86400) / 3600)
  const m = Math.floor((sec % 3600) / 60)
  const s = sec % 60
  if (d > 0) return `${d}天${h}时${m}分${s}秒`
  if (h > 0) return `${h}时${m}分${s}秒`
  if (m > 0) return `${m}分${s}秒`
  return `${s}秒`
}

async function cancelSchedule(id: number) {
  try {
    await cancelScheduledDeploy(id)
    message.success('⛔ 定时部署已取消')
    fetchHistory()
  } catch (e: any) {
    message.error('取消失败: ' + (e.message || ''))
  }
}

async function loadProjects() {
  const res = await getProjects()
  projects.value = res.data.list
}

async function loadNodes() {
  const res = await getNodes()
  nodeList.value = res.data.list
  const map: Record<string, string> = {}
  res.data.list.forEach((n: any) => map[String(n.id)] = n.name + ' (' + n.ip + ')')
  nodeMap.value = map
}

async function onProjectChange() {
  form.value.versionId = undefined
  lastResult.value = null
  if (!form.value.projectId) { versionList.value = []; return }
  const res = await getVersions(form.value.projectId)
  versionList.value = res.data.list || []
  fetchHistory()
}

async function fetchHistory() {
  if (!form.value.projectId) return
  loadingHistory.value = true
  try {
    const res = await getDeployRecords(form.value.projectId, historyPagination.value.current, historyPagination.value.pageSize)
    deployHistory.value = res.data.list
    historyPagination.value.total = res.data.total
  } finally { loadingHistory.value = false }
}

function handleTableChange(pag: any) {
  historyPagination.value.current = pag.current
  historyPagination.value.pageSize = pag.pageSize
  fetchHistory()
}

async function startDeploy() {
  if (!form.value.projectId || !form.value.versionId) { message.warning('请选择应用和版本'); return }
  if (form.value.deployMode === 'schedule') {
    if (!form.value.scheduleDate) { message.warning('请选择计划执行时间'); return }
    if (form.value.scheduleDate.valueOf() <= Date.now()) { message.warning('计划时间必须晚于当前时间'); return }
  }

  // 从应用配置中获取目标节点（后端会自动分发到所有节点）
  const p = projects.value.find(p => p.id === form.value.projectId)
  if (!p || !p.nodeIds) { message.warning('该应用未配置部署节点'); return }

  deploying.value = true
  lastResult.value = null
  try {
    const scheduleTime = form.value.deployMode === 'schedule' && form.value.scheduleDate
      ? form.value.scheduleDate.valueOf() : undefined
    const res = await createDeploy(form.value.projectId, form.value.versionId, undefined, scheduleTime)
    lastResult.value = res.data
    if (res.data.status === 1) message.success('✅ 部署成功')
    else if (res.data.status === 5) message.info('⏰ 定时部署计划已创建')
    else message.error('❌ 部署失败')
    fetchHistory()
  } catch (e: any) {
    lastResult.value = {
      status: 2,
      message: '❌ 部署请求失败',
      steps: [{ name: '❌ 异常', success: false, detail: e.message || '未知错误' }],
      log: e.message || '未知错误'
    }
  } finally { deploying.value = false }
}

function showLog(record: any) {
  logContent.value = record.log || '暂无日志'
  logVisible.value = true
}

async function rollback(record: any) {
  try {
    const res = await rollbackDeploy(record.id)
    lastResult.value = res.data
    message.info('回滚请求已执行')
    fetchHistory()
  } catch (e: any) {
    message.error('回滚失败: ' + (e.message || '未知错误'))
  }
}

onMounted(async () => {
  await Promise.all([loadProjects(), loadNodes()])
  if (projects.value.length > 0) {
    form.value.projectId = projects.value[0].id
    await onProjectChange()
  }
  // 每秒更新倒计时
  countdownTimer = setInterval(() => {
    nowMs.value = Date.now()
  }, 1000)
})

onUnmounted(() => {
  if (refreshTimer) clearInterval(refreshTimer)
  if (countdownTimer) clearInterval(countdownTimer)
})
</script>

<style scoped>
.step-detail {
  background: var(--eo-code-bg);
  color: var(--eo-code-text);
  border: 1px solid var(--eo-border);
  padding: 10px;
  border-radius: 8px;
  font-size: 12px;
  font-family: 'JetBrains Mono', 'Fira Code', monospace;
  line-height: 1.5;
  white-space: pre-wrap;
  margin: 6px 0 0;
  max-height: 180px;
  overflow: auto;
}
.log-pre {
  background: var(--eo-code-bg);
  color: var(--eo-code-text);
  border: 1px solid var(--eo-border);
  padding: 14px;
  border-radius: 8px;
  font-size: 12px;
  font-family: 'JetBrains Mono', 'Fira Code', monospace;
  line-height: 1.6;
  max-height: 400px;
  overflow: auto;
  white-space: pre-wrap;
  margin: 0;
}
.result-card-success { border-left: 4px solid #52c41a; }
.result-card-fail { border-left: 4px solid #ff4d4f; }
.result-card-schedule { border-left: 4px solid #faad14; }
</style>
