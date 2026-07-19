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
          <a-space wrap v-if="targetNodes.length > 0">
            <a-tag v-for="n in targetNodes" :key="n" color="blue">{{ n }}</a-tag>
          </a-space>
          <span v-else>
            <a-tag color="error">⚠️ 未配置节点</a-tag>
            <router-link to="/projects" style="font-size:12px; margin-left:4px">去配置 →</router-link>
          </span>
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
                    :disabled="!form.projectId || !form.versionId || !hasNodeIds || deploying">
            {{ deploying ? '⏳ 部署中...' : (form.deployMode === 'now' ? '🚀 执行部署' : '⏰ 创建定时计划') }}
          </a-button>
        </a-form-item>

      </a-form>
    </a-card>

    <!-- ========== 部署进度：每个节点独立卡片（实时 WebSocket 推送） ========== -->
    <a-card v-if="deploying" :bordered="false" style="border-radius: 8px; margin-bottom: 16px; border-left: 4px solid #1890ff">
      <template #title>
        <a-space>
          <loading-outlined spin style="color: #1890ff" />
          <span style="font-weight:600">部署进行中</span>
          <a-tag color="processing">{{ deployElapsed }}s</a-tag>
          <span style="font-size:12px;color:#888">（{{ doneNodeCount }}/{{ nodeProgressList.length }} 节点已完成）</span>
        </a-space>
      </template>

      <!-- 总体进度条 -->
      <a-progress :percent="overallPercent" :status="overallProgressStatus" size="small" style="margin-bottom: 16px" />

      <!-- 每个节点的状态卡片 -->
      <div v-for="(np, i) in nodeProgressList" :key="i" class="node-progress-item">
        <div class="node-progress-header">
          <a-space>
            <loading-outlined v-if="np.phase === 'running'" spin style="color:#1890ff" />
            <check-circle-outlined v-else-if="np.phase === 'done'" style="color:#52c41a" />
            <close-circle-outlined v-else-if="np.phase === 'failed'" style="color:#ff4d4f" />
            <clock-circle-outlined v-else style="color:#d9d9d9" />
            <span style="font-weight:500">{{ np.nodeName }}</span>
          </a-space>
          <a-space>
            <a-tag :color="nodePhaseColor(np.phase)">{{ nodePhaseText(np) }}</a-tag>
          </a-space>
        </div>
        <!-- 进行中的节点显示详细步骤 -->
        <div class="node-progress-steps">
          <a-steps :current="np.currentStep + 1" size="small" :status="np.phase === 'failed' ? 'error' : np.phase === 'running' ? 'process' : 'finish'">
            <a-step title="停止" />
            <a-step title="传输" />
            <a-step title="启动" />
            <a-step title="验证" />
          </a-steps>
          <div v-if="np.detail" style="font-size:12px;color:#888;margin-top:4px">{{ np.detail }}</div>
        </div>
      </div>
    </a-card>

    <!-- ========== 部署结果：每个节点详情 ========== -->
    <a-card v-if="lastResult && !deploying" :bordered="false" style="border-radius: 8px; margin-bottom: 16px"
            :class="'result-card-' + (lastResult.status === 1 || lastResult.status === 4 ? 'success' : lastResult.status === 5 ? 'schedule' : 'fail')">
      <template #title>
        <a-space>
          <check-circle-outlined v-if="lastResult.status === 1" style="color:#52c41a" />
          <clock-circle-outlined v-else-if="lastResult.status === 5" style="color:#faad14" />
          <close-circle-outlined v-else style="color:#ff4d4f" />
          <span style="font-weight:600">{{ lastResult.message }}</span>
        </a-space>
      </template>

      <!-- 多节点部署结果 -->
      <template v-if="lastResult.nodeResults && lastResult.nodeResults.length > 0">
        <div v-for="(nr, i) in lastResult.nodeResults" :key="i" class="node-result-item">
          <div class="node-result-header">
            <a-space>
              <check-circle-outlined v-if="nr.success" style="color:#52c41a" />
              <close-circle-outlined v-else style="color:#ff4d4f" />
              <span style="font-weight:500">{{ nr.nodeName || '节点#' + nr.nodeId }}</span>
            </a-space>
            <span :style="{ color: nr.success ? '#52c41a' : '#ff4d4f', fontWeight: 500 }">{{ nr.message }}</span>
          </div>
        </div>
      </template>

      <!-- 单节点回滚结果 steps -->
      <a-timeline v-else-if="lastResult.steps" style="margin:0">
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

    <!-- ========== 部署历史 ========== -->
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
          <template v-if="column.key === 'projectName'">
            <a-tag color="blue">{{ projectMap[String(record.projectId)] || '项目#' + record.projectId }}</a-tag>
          </template>
          <template v-if="column.key === 'status'">
            <a-badge :status="statusMap[record.status]?.badge" :text="statusMap[record.status]?.text" />
          </template>
          <template v-if="column.key === 'nodeName'">
            {{ getNodeName(record.nodeId) || '节点#' + record.nodeId }}
          </template>
          <template v-if="column.key === 'versionId'">
            <span style="font-weight:500">v{{ record.versionId }}</span>
          </template>
          <template v-if="column.key === 'startTime'">
            {{ fmtTime(record.startTime) }}
          </template>
          <template v-if="column.key === 'endTime'">
            {{ fmtTime(record.endTime) }}
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
import { message, Modal } from 'ant-design-vue'
import type { Dayjs } from 'dayjs'
import { getProjects } from '../api/project'
import { getNodes } from '../api/node'
import { getVersions } from '../api/version'
import { getDeployRecords, createDeploy, cancelScheduledDeploy } from '../api/deploy'
import {
  RocketOutlined, CheckCircleOutlined, CloseCircleOutlined, LoadingOutlined,
  ClockCircleOutlined, HistoryOutlined, StopOutlined
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

// ====== 每节点进度（由 WebSocket 实时更新） ======
interface NodeProgress {
  nodeId: string
  nodeName: string
  phase: 'waiting' | 'running' | 'done' | 'failed'
  currentStep: number  // 0-3
  detail: string
}
const nodeProgressList = ref<NodeProgress[]>([])
const deployElapsed = ref(0)
let elapsedTimer: any = null
let deployWs: WebSocket | null = null

const doneNodeCount = computed(() =>
  nodeProgressList.value.filter(n => n.phase === 'done' || n.phase === 'failed').length
)

const overallPercent = computed(() => {
  const total = nodeProgressList.value.length
  if (total === 0) return 0
  const done = doneNodeCount.value
  const running = nodeProgressList.value.filter(n => n.phase === 'running')
  let runningProgress = 0
  running.forEach(n => { runningProgress += ((n.currentStep + 1) / 4) * 25 })
  return Math.min(99, Math.round(((done * 100) + runningProgress) / total))
})

const overallProgressStatus = computed(() => {
  if (nodeProgressList.value.some(n => n.phase === 'failed')) return 'exception'
  if (doneNodeCount.value === nodeProgressList.value.length && nodeProgressList.value.length > 0) return 'success'
  return 'active'
})

function nodePhaseColor(phase: string): string {
  if (phase === 'done') return 'success'
  if (phase === 'failed') return 'error'
  if (phase === 'running') return 'processing'
  return 'default'
}

function nodePhaseText(np: NodeProgress): string {
  if (np.phase === 'done') return '✅ 完成'
  if (np.phase === 'failed') return '❌ 失败'
  if (np.phase === 'running') {
    const stepNames = ['停止旧进程', '传输文件', '启动应用', '健康检查']
    return '⏳ ' + (stepNames[np.currentStep] || '处理中')
  }
  return '⏳ 等待中'
}

// ====== WebSocket 连接 ======
function connectDeployWs(deployId: string) {
  const wsProtocol = location.protocol === 'https:' ? 'wss:' : 'ws:'
  const token = localStorage.getItem('token') || ''
  const wsUrl = `${wsProtocol}//${location.host}/api/ws/deploy?deployId=${deployId}&token=${encodeURIComponent(token)}`
  deployWs = new WebSocket(wsUrl)

  deployWs.onmessage = (event) => {
    try {
      const msg = JSON.parse(event.data)
      handleWsMessage(msg)
    } catch (e) {
      console.warn('WS parse error:', event.data)
    }
  }

  deployWs.onerror = () => {
    console.warn('Deploy WebSocket error')
  }

  deployWs.onclose = () => {
    deployWs = null
  }
}

function handleWsMessage(msg: any) {
  if (msg.type === 'node-step') {
    const np = nodeProgressList.value.find(n => n.nodeId === String(msg.nodeId))
    if (!np) return

    if (msg.status === 'running') {
      np.phase = 'running'
      np.currentStep = msg.stepIndex >= 0 ? msg.stepIndex : 0
      np.detail = msg.detail || ''
      // 如果前面有节点还是 waiting，激活第一个
      const firstWaiting = nodeProgressList.value.find(n => n.phase === 'waiting')
      if (firstWaiting && firstWaiting !== np) {
        // 保持 waiting，等当前节点完成后再激活
      }
    } else if (msg.status === 'done') {
      np.currentStep = msg.stepIndex >= 0 ? msg.stepIndex : np.currentStep
      np.detail = msg.detail || ''
      // 如果是最后一步（health）完成，标记节点完成
      if (msg.step === 'health' || msg.step === 'transfer') {
        np.phase = 'done'
        np.currentStep = 3
        // 激活下一个 waiting 节点
        const nextWaiting = nodeProgressList.value.find(n => n.phase === 'waiting')
        if (nextWaiting) nextWaiting.phase = 'running'
      }
    } else if (msg.status === 'failed') {
      np.phase = 'failed'
      np.detail = msg.detail || ''
      // 激活下一个 waiting 节点
      const nextWaiting = nodeProgressList.value.find(n => n.phase === 'waiting')
      if (nextWaiting) nextWaiting.phase = 'running'
    }
  } else if (msg.type === 'deploy-done') {
    // 部署完成
    lastResult.value = {
      status: msg.status,
      message: msg.message,
      nodeResults: msg.nodeResults || [],
      log: (msg.nodeResults || []).map((nr: any) => `${nr.nodeName}: ${nr.message}`).join('\n')
    }

    // 标记所有 running 的节点为最终状态
    nodeProgressList.value.forEach(np => {
      if (np.phase === 'running') {
        // 从 nodeResults 中找对应结果
        const nr = (msg.nodeResults || []).find((r: any) => String(r.nodeId) === np.nodeId)
        np.phase = nr?.success ? 'done' : 'failed'
        np.currentStep = 3
      }
    })

    const success = msg.status === 1 || msg.status === 4
    if (success) message.success('✅ 部署成功')
    else if (msg.status === 5) message.info('⏰ 定时部署计划已创建')
    else message.error('❌ 部署失败')

    deploying.value = false
    if (elapsedTimer) { clearInterval(elapsedTimer); elapsedTimer = null }
    if (deployWs) { deployWs.close(); deployWs = null }
    fetchHistory()
  }
}

function disconnectWs() {
  if (deployWs) {
    deployWs.close()
    deployWs = null
  }
}

// ====== 部署历史 ======
const deployHistory = ref<any[]>([])
const loadingHistory = ref(false)
const historyPagination = ref({
  current: 1,
  pageSize: 10,
  total: 0,
  showSizeChanger: true,
  showQuickJumper: true,
  pageSizeOptions: ['10', '20', '50'],
  showTotal: (total: number) => `共 ${total} 条`
})
const logVisible = ref(false)
const logContent = ref('')
const nowMs = ref(Date.now())
let countdownTimer: any = null

// ====== 计算属性 ======
const hasNodeIds = computed(() => {
  if (!form.value.projectId) return false
  const p = projects.value.find(p => p.id === form.value.projectId)
  return !!(p && p.nodeIds && String(p.nodeIds).trim())
})

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

const projectMap = ref<Record<string, string>>({})
// versionMap removed (unused)

const statusMap: Record<number, { badge: string; text: string }> = {
  0: { badge: 'processing', text: '⏳ 进行中' },
  1: { badge: 'success', text: '✅ 成功' },
  2: { badge: 'error', text: '❌ 失败' },
  3: { badge: 'default', text: '⛔ 已取消' },
  4: { badge: 'success', text: '↩️✅ 回滚成功' },
  5: { badge: 'warning', text: '⏰ 待部署' }
}

const historyColumns = [
  { title: 'ID', dataIndex: 'id', key: 'id', width: 60 },
  { title: '应用', dataIndex: 'projectId', key: 'projectName', width: 120 },
  { title: '节点', dataIndex: 'nodeName', key: 'nodeName', width: 130 },
  { title: '版本', dataIndex: 'versionId', key: 'versionId', width: 80 },
  { title: 'Jar包', dataIndex: 'jarName', key: 'jarName', width: 130, ellipsis: true },
  { title: '状态', dataIndex: 'status', key: 'status', width: 100 },
  { title: '开始时间', dataIndex: 'startTime', key: 'startTime', width: 160 },
  { title: '结束时间', dataIndex: 'endTime', key: 'endTime', width: 160 },
  { title: '操作', key: 'action', width: 100, fixed: 'right' as const }
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

function buildProjectMap() {
  const map: Record<string, string> = {}
  projects.value.forEach((p: any) => map[String(p.id)] = p.name)
  projectMap.value = map
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
  loadingHistory.value = true
  try {
    // 获取所有项目的部署历史（不按 projectId 过滤）
    const res = await getDeployRecords(
      undefined as any,
      historyPagination.value.current,
      historyPagination.value.pageSize
    )
    deployHistory.value = res.data.list
    historyPagination.value.total = res.data.total
    buildProjectMap()
  } finally { loadingHistory.value = false }
}

function handleTableChange(pag: any) {
  historyPagination.value.current = pag.current
  historyPagination.value.pageSize = pag.pageSize
  fetchHistory()
}

async function startDeploy() {
  if (deploying.value) {
    message.warning('部署正在进行中，请勿重复提交')
    return
  }

  if (!form.value.projectId || !form.value.versionId) { message.warning('请选择应用和版本'); return }
  if (form.value.deployMode === 'schedule') {
    if (!form.value.scheduleDate) { message.warning('请选择计划执行时间'); return }
    if (form.value.scheduleDate.valueOf() <= Date.now()) { message.warning('计划时间必须晚于当前时间'); return }
  }

  const p = projects.value.find(p => p.id === form.value.projectId)
  if (!p || !p.nodeIds) {
    Modal.warning({
      title: "⚠️ 应用未配置部署节点",
      content: `应用「${p?.name || ''}」尚未绑定任何部署节点，请先到【应用管理】→ 编辑应用 → 选择部署节点后再操作。`,
      okText: "我知道了"
    })
    return
  }

  // ====== 开始部署 ======
  deploying.value = true
  lastResult.value = null
  deployElapsed.value = 0

  // 解析节点列表并初始化每节点进度
  const nodeIds = p.nodeIds.split(',').map((s: string) => s.trim()).filter(Boolean)
  nodeProgressList.value = nodeIds.map((id: string, i: number) => ({
    nodeId: id,
    nodeName: nodeMap.value[id] || '节点#' + id,
    phase: i === 0 ? 'running' : 'waiting' as const,
    currentStep: 0,
    detail: ''
  }))

  // 启动计时器
  elapsedTimer = setInterval(() => { deployElapsed.value++ }, 1000)

  try {
    const scheduleTime = form.value.deployMode === 'schedule' && form.value.scheduleDate
      ? form.value.scheduleDate.valueOf() : undefined
    const res = await createDeploy(form.value.projectId, form.value.versionId, undefined, scheduleTime)
    const data = res.data

    if (data.status === 5) {
      // 定时部署，直接结束
      deploying.value = false
      if (elapsedTimer) { clearInterval(elapsedTimer); elapsedTimer = null }
      message.info('⏰ 定时部署计划已创建')
      fetchHistory()
      return
    }

    // 连接 WebSocket 获取实时进度
    if (data.deployId) {
      connectDeployWs(data.deployId)
    } else {
      // 没有 deployId（不应该发生），直接结束
      deploying.value = false
      if (elapsedTimer) { clearInterval(elapsedTimer); elapsedTimer = null }
    }
  } catch (e: any) {
    deploying.value = false
    if (elapsedTimer) { clearInterval(elapsedTimer); elapsedTimer = null }
    const errMsg = e?.response?.data?.message || e.message || '未知错误'
    lastResult.value = {
      status: 2,
      message: '❌ 部署请求失败',
      nodeResults: [{ nodeId: '?', nodeName: '?', success: false, message: errMsg }],
      log: errMsg
    }
    nodeProgressList.value.forEach(np => {
      if (np.phase === 'running') np.phase = 'failed'
    })
    message.error('❌ 部署失败: ' + errMsg)
  }
}

function showLog(record: any) {
  logContent.value = record.log || '暂无日志'
  logVisible.value = true
}

onMounted(async () => {
  await Promise.all([loadProjects(), loadNodes()])
  buildProjectMap()
  if (projects.value.length > 0) {
    form.value.projectId = projects.value[0].id
    await onProjectChange()
  }
  fetchHistory()
  countdownTimer = setInterval(() => { nowMs.value = Date.now() }, 1000)
})

onUnmounted(() => {
  if (countdownTimer) clearInterval(countdownTimer)
  if (elapsedTimer) clearInterval(elapsedTimer)
  disconnectWs()
})
</script>

<style scoped>
.node-progress-item {
  border: 1px solid var(--eo-border);
  border-radius: 6px;
  padding: 10px 14px;
  margin-bottom: 8px;
  background: var(--eo-bg-muted);
}
.node-progress-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 6px;
}
.node-progress-steps {
  margin-top: 4px;
}
.node-result-item {
  border: 1px solid var(--eo-border);
  border-radius: 6px;
  padding: 10px 14px;
  margin-bottom: 8px;
  background: var(--eo-bg-muted);
}
.node-result-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
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
